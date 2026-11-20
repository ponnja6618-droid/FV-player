package com.fourvplayer.clx.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import com.fourvplayer.clx.model.FrequencyBand
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class AudioVisualizerManager {

    companion object {
        private const val FFT_SIZE = 1024
        private const val TARGET_FPS = 86.1328125 // 44100 / 512 exact frame rate
    }

    private var boundPlayer: MediaPlayer? = null
    private var decodeThread: Thread? = null
    private val decodeGeneration = AtomicInteger(0)

    private val decodedFrames = CopyOnWriteArrayList<FloatArray>()

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var spectrumBins: FloatArray = FloatArray(FFT_SIZE / 2)
        private set

    @Volatile
    var beatPulse: Float = 0f
        private set

    @Volatile
    var bassEnergy: Float = 0f
        private set

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    private var runningBassAvg: Float = 0.08f
    private var lastFrameIndex: Int = -1
    private var lastPlayerPosMs: Int = -1
    private var lastPlayerPollWallMs: Long = 0L

    fun attachMediaPlayer(player: MediaPlayer?) {
        boundPlayer = player
        lastPlayerPosMs = -1
        lastPlayerPollWallMs = SystemClock.elapsedRealtime()
        lastFrameIndex = -1
    }

    fun startCapture(@Suppress("UNUSED_PARAMETER") context: Context) {
        isActive = boundPlayer != null && decodedFrames.isNotEmpty()
    }

    fun stopCapture() {
        isActive = false
    }

    /**
     * Decodes audio file directly into 16-bit PCM -> 1024-point FFT timeline.
     * Zero microphone capture, zero background noise, zero latency.
     */
    fun loadAudioUri(context: Context, uri: Uri) {
        val gen = decodeGeneration.incrementAndGet()
        decodedFrames.clear()
        spectrumBins = FloatArray(FFT_SIZE / 2)
        beatPulse = 0f
        bassEnergy = 0f
        runningBassAvg = 0.08f
        lastFrameIndex = -1
        lastPlayerPosMs = -1

        decodeThread = Thread({
            var extractor: MediaExtractor? = null
            var codec: MediaCodec? = null
            try {
                extractor = MediaExtractor().apply {
                    setDataSource(context, uri, null)
                }

                var audioTrackIndex = -1
                var inputFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        inputFormat = fmt
                        break
                    }
                }

                if (audioTrackIndex < 0 || inputFormat == null) return@Thread
                extractor.selectTrack(audioTrackIndex)

                val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@Thread
                var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100
                var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(inputFormat, null, null, 0)
                    start()
                }

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false

                val rollingPcm = FloatArray(FFT_SIZE)
                val real = FloatArray(FFT_SIZE)
                val imag = FloatArray(FFT_SIZE)
                var hopCounter = 0
                var hopSamples = max(128, (sampleRate / TARGET_FPS).toInt())

                var channelAcc = 0f
                var chIdx = 0

                isActive = true

                while (!sawOutputEOS && gen == decodeGeneration.get()) {
                    if (!sawInputEOS) {
                        val inIdx = codec.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIdx, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    sawInputEOS = true
                                } else {
                                    val pts = extractor.sampleTime
                                    codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }

                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val totalShorts = shortBuf.remaining()

                            for (s in 0 until totalShorts) {
                                val normSample = shortBuf.get(s) / 32768f
                                channelAcc += normSample
                                chIdx++
                                if (chIdx >= channelCount) {
                                    val monoSample = channelAcc / channelCount
                                    channelAcc = 0f
                                    chIdx = 0

                                    // Shift rolling PCM buffer left by 1 sample
                                    System.arraycopy(rollingPcm, 1, rollingPcm, 0, FFT_SIZE - 1)
                                    rollingPcm[FFT_SIZE - 1] = monoSample
                                    hopCounter++

                                    if (hopCounter >= hopSamples) {
                                        hopCounter = 0
                                        for (i in 0 until FFT_SIZE) {
                                            real[i] = rollingPcm[i] * hannWindow[i]
                                            imag[i] = 0f
                                        }
                                        fftInPlace(real, imag)
                                        decodedFrames.add(computeFrameBins(real, imag))
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            hopSamples = max(128, (sampleRate / TARGET_FPS).toInt())
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = max(1, newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    codec?.stop()
                    codec?.release()
                } catch (_: Throwable) {
                }
                try {
                    extractor?.release()
                } catch (_: Throwable) {
                }
            }
        }, "DirectPcmFftDecoder").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    private fun computeFrameBins(real: FloatArray, imag: FloatArray): FloatArray {
        val half = FFT_SIZE / 2
        val bins = FloatArray(half)
        for (k in 1 until half) {
            val mag = hypot(real[k], imag[k]) / (FFT_SIZE * 0.22f)
            val freqRatio = k.toFloat() / half
            // Perceptual high-frequency lift so vocals, snares & hats shine
            val tilted = mag * (1.0f + freqRatio.pow(0.52f) * 3.4f)
            val level = (log10(1f + tilted * 16f) / 1.38f).coerceIn(0f, 1f)
            bins[k] = level.pow(1.12f)
        }
        return bins
    }

    /**
     * Called on every 60fps/120fps canvas draw frame for exact 0ms-latency sync with playback.
     */
    fun updateFrame() {
        val mp = boundPlayer
        val frames = decodedFrames
        if (mp == null || frames.isEmpty()) {
            decaySmoothly()
            return
        }

        val playing = try {
            mp.isPlaying
        } catch (_: Throwable) {
            false
        }

        if (!playing) {
            decaySmoothly()
            return
        }

        val rawPosMs = try {
            mp.currentPosition
        } catch (_: Throwable) {
            0
        }

        val now = SystemClock.elapsedRealtime()
        if (rawPosMs != lastPlayerPosMs) {
            lastPlayerPosMs = rawPosMs
            lastPlayerPollWallMs = now
        }

        // Sub-millisecond interpolation between MediaPlayer position ticks
        val extrapolatedMs = rawPosMs + (now - lastPlayerPollWallMs).coerceIn(0L, 45L)
        val exactFrame = (extrapolatedMs / 1000.0) * TARGET_FPS
        val idx0 = exactFrame.toInt().coerceIn(0, frames.size - 1)
        val idx1 = min(frames.size - 1, idx0 + 1)
        val frac = (exactFrame - idx0).toFloat().coerceIn(0f, 1f)

        val f0 = frames[idx0]
        val f1 = frames[idx1]
        val prevBins = spectrumBins
        val half = prevBins.size
        val nextBins = FloatArray(half)

        for (k in 1 until half) {
            val target = f0[k] * (1f - frac) + f1[k] * frac
            val prev = prevBins[k]
            // Ultra-snappy attack (0.86) and smooth decay (0.38)
            nextBins[k] = if (target > prev) {
                prev * 0.14f + target * 0.86f
            } else {
                prev * 0.62f + target * 0.38f
            }
        }
        spectrumBins = nextBins
        lastFrameIndex = idx0

        // Punchy sub-bass & kick beat extraction (45Hz - 280Hz)
        var bassSum = 0f
        var bassMax = 0f
        for (b in 1..6) {
            val v = nextBins[b]
            bassSum += v
            if (v > bassMax) bassMax = v
        }
        val currentBass = ((bassSum / 6f) * 0.42f + bassMax * 0.58f).coerceIn(0f, 1f)
        bassEnergy = currentBass

        val flux = max(0f, currentBass - runningBassAvg * 1.08f)
        runningBassAvg = runningBassAvg * 0.83f + currentBass * 0.17f

        val nextBeat = if (flux > 0.016f) {
            max(beatPulse * 0.78f, min(1f, flux * 6.2f))
        } else {
            beatPulse * 0.78f
        }
        beatPulse = nextBeat.coerceIn(0f, 1f)
    }

    private fun decaySmoothly() {
        val prevBins = spectrumBins
        var hasEnergy = false
        for (v in prevBins) {
            if (v > 0.002f) {
                hasEnergy = true
                break
            }
        }
        if (hasEnergy) {
            spectrumBins = FloatArray(prevBins.size) { i -> prevBins[i] * 0.76f }
        }
        beatPulse *= 0.75f
        bassEnergy *= 0.75f
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr

                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val ang = (-2.0 * PI / len).toFloat()
            val wStepReal = cos(ang)
            val wStepImag = sin(ang)

            var i = 0
            while (i < n) {
                var wReal = 1f
                var wImag = 0f
                for (k in 0 until halfLen) {
                    val uReal = real[i + k]
                    val uImag = imag[i + k]
                    val vReal = real[i + k + halfLen] * wReal - imag[i + k + halfLen] * wImag
                    val vImag = real[i + k + halfLen] * wImag + imag[i + k + halfLen] * wReal

                    real[i + k] = uReal + vReal
                    imag[i + k] = uImag + vImag
                    real[i + k + halfLen] = uReal - vReal
                    imag[i + k + halfLen] = uImag - vImag

                    val nextWReal = wReal * wStepReal - wImag * wStepImag
                    wImag = wReal * wStepImag + wImag * wStepReal
                    wReal = nextWReal
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun hzToMel(hz: Float): Float = (2595.0 * log10(1.0 + hz / 700.0)).toFloat()

    private fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

    fun getFrequencySlice(minHz: Int, maxHz: Int, barCount: Int): FloatArray {
        val count = barCount.coerceAtLeast(1)
        val out = FloatArray(count)
        val bins = spectrumBins
        if (bins.size <= 4) return out

        val nyquist = 22050f
        val effectiveMin = if (minHz <= 20 && maxHz >= 16000) 45f else minHz.coerceIn(25, 12000).toFloat()
        val effectiveMax = if (minHz <= 20 && maxHz >= 16000) 9000f else maxHz.coerceIn(100, nyquist.toInt()).toFloat()

        val melMin = hzToMel(effectiveMin)
        val melMax = hzToMel(effectiveMax)
        val melSpan = max(1f, melMax - melMin)

        val rawSlice = FloatArray(count)
        for (i in 0 until count) {
            val t = if (count == 1) 0f else i.toFloat() / (count - 1)
            val freqHz = melToHz(melMin + t * melSpan)
            val exactBin = ((freqHz / nyquist) * (bins.size - 1)).coerceIn(1f, (bins.size - 2).toFloat())

            val b0 = exactBin.toInt()
            val b1 = min(bins.size - 1, b0 + 1)
            val frac = exactBin - b0
            val mu = (1f - cos(frac * PI.toFloat())) * 0.5f
            rawSlice[i] = bins[b0] * (1f - mu) + bins[b1] * mu
        }

        for (i in 0 until count) {
            val left = rawSlice[max(0, i - 1)]
            val center = rawSlice[i]
            val right = rawSlice[min(count - 1, i + 1)]
            out[i] = (left * 0.15f + center * 0.70f + right * 0.15f).coerceIn(0f, 1f)
        }
        return out
    }

    fun getRangeEnergy(minHz: Int, maxHz: Int): Float {
        val slice = getFrequencySlice(minHz, maxHz, 16)
        if (slice.isEmpty()) return 0f
        var sum = 0f
        var peak = 0f
        for (v in slice) {
            sum += v
            if (v > peak) peak = v
        }
        return ((sum / slice.size) * 0.5f + peak * 0.5f).coerceIn(0f, 1f)
    }

    fun getBandLevel(band: FrequencyBand): Float {
        return getRangeEnergy(band.defaultMinHz, band.defaultMaxHz)
    }

    fun release() {
        decodeGeneration.incrementAndGet()
        boundPlayer = null
        decodedFrames.clear()
        isActive = false
    }
}
