package com.fourvplayer.clx.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.fourvplayer.clx.audio.AudioVisualizerManager
import com.fourvplayer.clx.model.AspectRatioMode
import com.fourvplayer.clx.model.BarStyle
import com.fourvplayer.clx.model.EffectItem
import com.fourvplayer.clx.model.EffectType
import com.fourvplayer.clx.model.LayerBlendMode
import com.fourvplayer.clx.model.LayerItem
import com.fourvplayer.clx.model.LayerType
import com.fourvplayer.clx.model.MeasureTarget
import com.fourvplayer.clx.model.MirrorAxis
import com.fourvplayer.clx.model.SegmentStyle
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PreviewCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var visualizerManager: AudioVisualizerManager? = null
    var aspectRatioMode: AspectRatioMode = AspectRatioMode.RATIO_9_16
        set(value) {
            field = value
            invalidate()
        }

    var selectedLayerId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var onLayerSelectedByTouch: ((LayerItem) -> Unit)? = null
    var onLayerTransformChanged: (() -> Unit)? = null

    private var layers: List<LayerItem> = emptyList()
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    private var offscreenLayerBmp: Bitmap? = null
    private var offscreenLayerCanvas: Canvas? = null
    private var blurTempBmp: Bitmap? = null
    private var blurTempCanvas: Canvas? = null
    private var blurWideBmp: Bitmap? = null
    private var blurWideCanvas: Canvas? = null

    private val prevLayerPos = mutableMapOf<String, FloatArray>()
    private val delayRingBuffers = mutableMapOf<String, ArrayDeque<FloatArray>>()
    private val peakHoldArrays = mutableMapOf<String, FloatArray>()

    private val outerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#08080A")
    }
    private val canvasBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13131D")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A35")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#525866")
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seg2Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePath = Path()
    private val tmpRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val additivePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 62f
        isFakeBoldText = true
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDraggingLayer = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val active = layers.firstOrNull { it.id == selectedLayerId && it.isVisible } ?: return false
            active.scale = (active.scale * detector.scaleFactor).coerceIn(0.1f, 6.0f)
            onLayerTransformChanged?.invoke()
            invalidate()
            return true
        }
    })

    fun submitLayers(newLayers: List<LayerItem>) {
        layers = newLayers.toList()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (layers.isEmpty()) return false
        scaleDetector.onTouchEvent(event)

        val bounds = computeAspectCanvasRect()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDraggingLayer = true

                val hit = layers.firstOrNull { layer ->
                    if (!layer.isVisible) return@firstOrNull false
                    val lcx = bounds.centerX() + layer.posX
                    val lcy = bounds.centerY() + layer.posY
                    val hitRadius = 150f * layer.scale.coerceIn(0.4f, 2.5f)
                    hypot(event.x - lcx, event.y - lcy) <= hitRadius
                }
                if (hit != null && hit.id != selectedLayerId) {
                    selectedLayerId = hit.id
                    onLayerSelectedByTouch?.invoke(hit)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingLayer && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val active = layers.firstOrNull { it.id == selectedLayerId && it.isVisible }
                    if (active != null) {
                        active.posX += (event.x - lastTouchX)
                        active.posY += (event.y - lastTouchY)
                        onLayerTransformChanged?.invoke()
                        invalidate()
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingLayer = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun ensureOffscreenBuffers(w: Int, h: Int) {
        val safeW = w.coerceIn(64, 1080)
        val safeH = h.coerceIn(64, 1920)
        if (offscreenLayerBmp == null || offscreenLayerBmp?.width != safeW || offscreenLayerBmp?.height != safeH) {
            offscreenLayerBmp?.recycle()
            offscreenLayerBmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            offscreenLayerCanvas = Canvas(offscreenLayerBmp!!)
        }
        val bw = max(32, safeW / 4)
        val bh = max(32, safeH / 4)
        if (blurTempBmp == null || blurTempBmp?.width != bw || blurTempBmp?.height != bh) {
            blurTempBmp?.recycle()
            blurTempBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            blurTempCanvas = Canvas(blurTempBmp!!)
        }
        val ww = max(16, safeW / 12)
        val wh = max(16, safeH / 12)
        if (blurWideBmp == null || blurWideBmp?.width != ww || blurWideBmp?.height != wh) {
            blurWideBmp?.recycle()
            blurWideBmp = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888)
            blurWideCanvas = Canvas(blurWideBmp!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        visualizerManager?.updateFrame()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), outerBgPaint)

        val canvasRect = computeAspectCanvasRect()
        canvas.save()
        canvas.clipRect(canvasRect)
        canvas.drawRect(canvasRect, canvasBgPaint)

        if (layers.isEmpty()) {
            canvas.drawText(
                "Tap + below to add a Layer",
                canvasRect.centerX(),
                canvasRect.centerY(),
                hintPaint
            )
        } else {
            for (layer in layers.asReversed()) {
                if (!layer.isVisible) continue
                drawLayerWithEffects(canvas, canvasRect, layer)
            }
        }

        canvas.restore()
        canvas.drawRect(canvasRect, borderPaint)

        postInvalidateOnAnimation()
    }

    private fun computeAspectCanvasRect(): RectF {
        val pad = 12f
        val availW = max(1f, width - pad * 2)
        val availH = max(1f, height - pad * 2)
        val targetRatio = aspectRatioMode.ratio
        val availRatio = availW / availH

        val drawW: Float
        val drawH: Float
        if (availRatio > targetRatio) {
            drawH = availH
            drawW = drawH * targetRatio
        } else {
            drawW = availW
            drawH = drawW / targetRatio
        }

        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun toPorterDuffMode(blend: LayerBlendMode): PorterDuff.Mode? {
        return when (blend) {
            LayerBlendMode.NORMAL -> null
            LayerBlendMode.ADD -> PorterDuff.Mode.ADD
            LayerBlendMode.SCREEN -> PorterDuff.Mode.SCREEN
            LayerBlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
        }
    }

    private fun drawLayerWithEffects(canvas: Canvas, bounds: RectF, layer: LayerItem) {
        val vis = visualizerManager
        val beat = vis?.beatPulse ?: 0f
        val bass = vis?.bassEnergy ?: 0f

        val scaleMeasureVal = when (layer.measureScale) {
            MeasureTarget.NOTHING -> 0f
            MeasureTarget.BEAT -> beat * 0.25f
            MeasureTarget.BASS -> bass * 0.28f
        }
        val posMeasureOffset = when (layer.measurePos) {
            MeasureTarget.NOTHING -> 0f
            MeasureTarget.BEAT -> -beat * 28f
            MeasureTarget.BASS -> -bass * 32f
        }

        val cx = bounds.centerX() + layer.posX
        val cy = bounds.centerY() + layer.posY + posMeasureOffset
        val finalScale = (layer.scale * (1f + scaleMeasureVal)).coerceIn(0.05f, 8f)
        val baseColor = layer.colorHex.toInt()

        val prev = prevLayerPos[layer.id]
        val velX = if (prev != null) (cx - prev[0]) else 0f
        val velY = if (prev != null) (cy - prev[1]) else 0f
        prevLayerPos[layer.id] = floatArrayOf(cx, cy)

        val blendModePd = toPorterDuffMode(layer.blendMode)
        val saveLayerCount = if (blendModePd != null) {
            val blendPaint = Paint().apply { xfermode = PorterDuffXfermode(blendModePd) }
            canvas.saveLayer(bounds, blendPaint)
        } else {
            -1
        }

        val activeEffects = layer.effects.filter { it.enabled }
        if (activeEffects.isEmpty()) {
            renderSinglePass(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, layer.alpha, false, false, false)
            if (saveLayerCount >= 0) canvas.restoreToCount(saveLayerCount)
            return
        }

        var waveWarpFx: EffectItem? = null
        var glowFx: EffectItem? = null
        var motionBlurFx: EffectItem? = null
        var blurFx: EffectItem? = null
        var rgbFx: EffectItem? = null
        var mirrorFx: EffectItem? = null

        for (fx in activeEffects) {
            when (fx.type) {
                EffectType.WAVE_WARP -> waveWarpFx = fx
                EffectType.GLOW -> glowFx = fx
                EffectType.MOTION_BLUR -> motionBlurFx = fx
                EffectType.BLUR -> blurFx = fx
                EffectType.RGB_SPLIT -> rgbFx = fx
                EffectType.MIRROR -> mirrorFx = fx
            }
        }

        if (glowFx != null) {
            val beatMult = 1f + beat * glowFx.beatSync * 0.9f
            val glowStrength = ((glowFx.magnitude / 9f) * beatMult).coerceIn(0.1f, 2.8f)
            val spread = 1f + (glowFx.spacing / 100f) * 0.28f
            renderAlightGlowPass(canvas, bounds, layer, vis, cx, cy, finalScale, glowFx.tintColorHex.toInt(), glowStrength, spread)
        }

        if (motionBlurFx != null) {
            val beatMult = 1f + beat * motionBlurFx.beatSync * 0.95f
            val shutterLen = (motionBlurFx.magnitude * 4.2f * beatMult).coerceIn(0f, 110f)
            val rad = Math.toRadians(motionBlurFx.angle.toDouble())
            val dirBlurX = cos(rad).toFloat() * shutterLen
            val dirBlurY = sin(rad).toFloat() * shutterLen
            val totalDx = velX * (motionBlurFx.magnitude * 0.35f) + dirBlurX
            val totalDy = velY * (motionBlurFx.magnitude * 0.35f) + dirBlurY

            val samples = 12
            for (s in 0 until samples) {
                val t = (s.toFloat() / (samples - 1)) - 0.5f
                val weight = (1f - kotlin.math.abs(t) * 1.35f).coerceIn(0.15f, 1f)
                val sampleAlpha = (layer.alpha * 0.22f * weight).coerceIn(0.02f, 0.36f)
                renderSinglePass(
                    canvas, bounds, layer, vis,
                    cx + totalDx * t,
                    cy + totalDy * t,
                    finalScale, baseColor, sampleAlpha, false, false, false
                )
            }
        }

        if (rgbFx != null) {
            renderAlightRgbSplitPass(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, rgbFx, beat)
        } else if (waveWarpFx != null && waveWarpFx.magnitude > 0.05f) {
            renderAlightWaveWarpMesh(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, waveWarpFx, beat)
        } else if (blurFx != null && blurFx.spacing > 0.5f) {
            renderAlightGaussianBlur(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, blurFx.spacing)
        } else {
            renderSinglePass(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, layer.alpha, false, false, false)
        }

        if (mirrorFx != null) {
            renderAlightMirrorPass(canvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorFx)
        }

        if (saveLayerCount >= 0) {
            canvas.restoreToCount(saveLayerCount)
        }
    }

    private fun renderAlightWaveWarpMesh(
        targetCanvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        baseColor: Int,
        fx: EffectItem,
        beat: Float
    ) {
        val cw = bounds.width().toInt().coerceAtLeast(64)
        val ch = bounds.height().toInt().coerceAtLeast(64)
        ensureOffscreenBuffers(cw, ch)
        val fullBmp = offscreenLayerBmp ?: return
        val fullCvs = offscreenLayerCanvas ?: return

        fullBmp.eraseColor(Color.TRANSPARENT)
        val localBounds = RectF(0f, 0f, cw.toFloat(), ch.toFloat())
        renderSinglePass(fullCvs, localBounds, layer, vis, cx - bounds.left, cy - bounds.top, finalScale, baseColor, layer.alpha, false, false, false)

        val beatMult = 1f + beat * fx.beatSync * 0.90f
        val ampPx = (fx.magnitude * 3.6f * beatMult).coerceIn(0f, 110f)
        val wavelength = max(18f, fx.spacing * 5.2f)
        val k = (2f * Math.PI.toFloat()) / wavelength
        val phase = (SystemClock.uptimeMillis() * 0.0065f * fx.speed) + (beat * fx.beatSync * 1.4f)

        val rad = Math.toRadians(fx.angle.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()
        val perpX = -dirY
        val perpY = dirX

        val cols = 20
        val rows = 36
        val verts = FloatArray((cols + 1) * (rows + 1) * 2)
        val halfW = cw * 0.5f
        val halfH = ch * 0.5f

        var idx = 0
        for (r in 0..rows) {
            val baseY = (r.toFloat() / rows) * ch
            val relY = baseY - halfH
            for (c in 0..cols) {
                val baseX = (c.toFloat() / cols) * cw
                val relX = baseX - halfW

                val alongWave = relX * dirX + relY * dirY
                val waveDisp = sin(alongWave * k + phase) * ampPx

                verts[idx++] = bounds.left + baseX + perpX * waveDisp
                verts[idx++] = bounds.top + baseY + perpY * waveDisp
            }
        }

        imagePaint.alpha = 255
        imagePaint.colorFilter = null
        targetCanvas.drawBitmapMesh(fullBmp, cols, rows, verts, 0, null, 0, imagePaint)
    }

    private fun renderAlightRgbSplitPass(
        targetCanvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        baseColor: Int,
        fx: EffectItem,
        beat: Float
    ) {
        val cw = bounds.width().toInt().coerceAtLeast(64)
        val ch = bounds.height().toInt().coerceAtLeast(64)
        ensureOffscreenBuffers(cw, ch)
        val fullBmp = offscreenLayerBmp ?: return
        val fullCvs = offscreenLayerCanvas ?: return

        fullBmp.eraseColor(Color.TRANSPARENT)
        val localBounds = RectF(0f, 0f, cw.toFloat(), ch.toFloat())
        renderSinglePass(fullCvs, localBounds, layer, vis, cx - bounds.left, cy - bounds.top, finalScale, baseColor, layer.alpha, false, false, false)

        val beatMult = 1f + beat * fx.beatSync * 1.15f
        val splitDist = (fx.magnitude * 3.4f * beatMult).coerceIn(0f, 95f)
        val rad = Math.toRadians(fx.angle.toDouble())
        val dx = cos(rad).toFloat() * splitDist
        val dy = sin(rad).toFloat() * splitDist

        val saveCount = targetCanvas.saveLayer(bounds, null)

        additivePaint.alpha = 255
        additivePaint.colorFilter = PorterDuffColorFilter(0xFFFF0000.toInt(), PorterDuff.Mode.MULTIPLY)
        targetCanvas.drawBitmap(fullBmp, bounds.left - dx, bounds.top - dy, additivePaint)

        additivePaint.colorFilter = PorterDuffColorFilter(0xFF00FF00.toInt(), PorterDuff.Mode.MULTIPLY)
        targetCanvas.drawBitmap(fullBmp, bounds.left, bounds.top, additivePaint)

        additivePaint.colorFilter = PorterDuffColorFilter(0xFF0000FF.toInt(), PorterDuff.Mode.MULTIPLY)
        targetCanvas.drawBitmap(fullBmp, bounds.left + dx, bounds.top + dy, additivePaint)

        additivePaint.colorFilter = null
        targetCanvas.restoreToCount(saveCount)
    }

    private fun renderAlightGlowPass(
        targetCanvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        glowColor: Int,
        strength: Float,
        spread: Float
    ) {
        val cw = bounds.width().toInt().coerceAtLeast(64)
        val ch = bounds.height().toInt().coerceAtLeast(64)
        ensureOffscreenBuffers(cw, ch)
        val fullBmp = offscreenLayerBmp ?: return
        val fullCvs = offscreenLayerCanvas ?: return
        val tightBmp = blurTempBmp ?: return
        val tightCvs = blurTempCanvas ?: return
        val wideBmp = blurWideBmp ?: return
        val wideCvs = blurWideCanvas ?: return

        fullBmp.eraseColor(Color.TRANSPARENT)
        val localBounds = RectF(0f, 0f, cw.toFloat(), ch.toFloat())
        renderSinglePass(fullCvs, localBounds, layer, vis, cx - bounds.left, cy - bounds.top, finalScale, glowColor, 1.0f, false, false, true)

        tightBmp.eraseColor(Color.TRANSPARENT)
        imagePaint.alpha = 255
        imagePaint.colorFilter = PorterDuffColorFilter(glowColor, PorterDuff.Mode.SRC_IN)
        tightCvs.drawBitmap(fullBmp, null, RectF(0f, 0f, tightBmp.width.toFloat(), tightBmp.height.toFloat()), imagePaint)

        wideBmp.eraseColor(Color.TRANSPARENT)
        wideCvs.drawBitmap(tightBmp, null, RectF(0f, 0f, wideBmp.width.toFloat(), wideBmp.height.toFloat()), imagePaint)
        imagePaint.colorFilter = null

        additivePaint.colorFilter = null
        additivePaint.alpha = (min(1f, strength * 0.65f) * 255).toInt()
        targetCanvas.drawBitmap(tightBmp, null, bounds, additivePaint)

        targetCanvas.save()
        targetCanvas.scale(spread, spread, cx, cy)
        additivePaint.alpha = (min(1f, strength * 0.75f) * 255).toInt()
        targetCanvas.drawBitmap(wideBmp, null, bounds, additivePaint)
        targetCanvas.restore()
    }

    private fun renderAlightGaussianBlur(
        targetCanvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        baseColor: Int,
        blurRadiusVal: Float
    ) {
        val cw = bounds.width().toInt().coerceAtLeast(64)
        val ch = bounds.height().toInt().coerceAtLeast(64)
        ensureOffscreenBuffers(cw, ch)
        val fullBmp = offscreenLayerBmp ?: return
        val fullCvs = offscreenLayerCanvas ?: return
        val smallBmp = blurWideBmp ?: return
        val smallCvs = blurWideCanvas ?: return

        fullBmp.eraseColor(Color.TRANSPARENT)
        val localBounds = RectF(0f, 0f, cw.toFloat(), ch.toFloat())
        renderSinglePass(fullCvs, localBounds, layer, vis, cx - bounds.left, cy - bounds.top, finalScale, baseColor, layer.alpha, false, false, false)

        smallBmp.eraseColor(Color.TRANSPARENT)
        val normBlur = (blurRadiusVal / 100f).coerceIn(0.05f, 1.0f)
        val downFactor = 1.0f + normBlur * 3.5f
        val activeW = max(12f, smallBmp.width / downFactor)
        val activeH = max(12f, smallBmp.height / downFactor)
        val subRect = RectF(0f, 0f, activeW, activeH)

        imagePaint.alpha = 255
        imagePaint.colorFilter = null
        smallCvs.drawBitmap(fullBmp, null, subRect, imagePaint)

        val srcRect = Rect(0, 0, activeW.toInt(), activeH.toInt())
        val r = normBlur * 18f
        val d = r * 0.707f

        imagePaint.alpha = 115
        targetCanvas.drawBitmap(smallBmp, srcRect, bounds, imagePaint)

        imagePaint.alpha = 52
        val cardinals = arrayOf(
            floatArrayOf(-r, 0f), floatArrayOf(r, 0f),
            floatArrayOf(0f, -r), floatArrayOf(0f, r)
        )
        for (o in cardinals) {
            tmpRect.set(bounds.left + o[0], bounds.top + o[1], bounds.right + o[0], bounds.bottom + o[1])
            targetCanvas.drawBitmap(smallBmp, srcRect, tmpRect, imagePaint)
        }

        imagePaint.alpha = 36
        val diagonals = arrayOf(
            floatArrayOf(-d, -d), floatArrayOf(d, -d),
            floatArrayOf(-d, d), floatArrayOf(d, d)
        )
        for (o in diagonals) {
            tmpRect.set(bounds.left + o[0], bounds.top + o[1], bounds.right + o[0], bounds.bottom + o[1])
            targetCanvas.drawBitmap(smallBmp, srcRect, tmpRect, imagePaint)
        }
    }

    private fun renderAlightMirrorPass(
        targetCanvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        baseColor: Int,
        fx: EffectItem
    ) {
        val offsetPx = (fx.spacing - 50f) * 3.8f
        val mirrorAlpha = (layer.alpha * (fx.magnitude / 12f).coerceIn(0.35f, 1.0f)).coerceIn(0.15f, 1.0f)
        val seamX = bounds.centerX() + offsetPx
        val seamY = bounds.centerY() + offsetPx

        when (fx.mirrorAxis) {
            MirrorAxis.HORIZONTAL -> {
                targetCanvas.save()
                targetCanvas.clipRect(seamX, bounds.top, bounds.right, bounds.bottom)
                targetCanvas.scale(-1f, 1f, seamX, bounds.centerY())
                renderSinglePass(targetCanvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorAlpha, false, false, false)
                targetCanvas.restore()
            }

            MirrorAxis.VERTICAL -> {
                targetCanvas.save()
                targetCanvas.clipRect(bounds.left, seamY, bounds.right, bounds.bottom)
                targetCanvas.scale(1f, -1f, bounds.centerX(), seamY)
                renderSinglePass(targetCanvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorAlpha, false, false, false)
                targetCanvas.restore()
            }

            MirrorAxis.QUAD -> {
                targetCanvas.save()
                targetCanvas.clipRect(seamX, bounds.top, bounds.right, seamY)
                targetCanvas.scale(-1f, 1f, seamX, seamY)
                renderSinglePass(targetCanvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorAlpha, false, false, false)
                targetCanvas.restore()

                targetCanvas.save()
                targetCanvas.clipRect(bounds.left, seamY, seamX, bounds.bottom)
                targetCanvas.scale(1f, -1f, seamX, seamY)
                renderSinglePass(targetCanvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorAlpha, false, false, false)
                targetCanvas.restore()

                targetCanvas.save()
                targetCanvas.clipRect(seamX, seamY, bounds.right, bounds.bottom)
                targetCanvas.scale(-1f, -1f, seamX, seamY)
                renderSinglePass(targetCanvas, bounds, layer, vis, cx, cy, finalScale, baseColor, mirrorAlpha, false, false, false)
                targetCanvas.restore()
            }
        }
    }

    private fun renderSinglePass(
        canvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        cx: Float,
        cy: Float,
        finalScale: Float,
        colorInt: Int,
        alphaFloat: Float,
        mirrorFlipX: Boolean,
        mirrorFlipY: Boolean,
        forceSolidColor: Boolean
    ) {
        val alphaInt = (alphaFloat.coerceIn(0f, 1f) * 255).toInt()
        if (alphaInt <= 1) return

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(layer.rotation)
        canvas.scale(
            if (mirrorFlipX) -finalScale else finalScale,
            if (mirrorFlipY) -finalScale else finalScale
        )

        when (layer.type) {
            LayerType.BARS -> drawAveeBarsStyled(canvas, bounds, layer, vis, colorInt, alphaInt, forceSolidColor)
            LayerType.TEXT -> drawTextLayer(canvas, layer, colorInt, alphaInt)
            LayerType.IMAGE -> drawImageLayer(canvas, bounds, layer, colorInt, alphaInt)
            LayerType.PARTICLES -> drawParticlesLayer(canvas, bounds, layer, vis, colorInt, alphaInt)
            else -> {
                shapePaint.reset()
                shapePaint.isAntiAlias = true
                shapePaint.color = colorInt
                shapePaint.alpha = alphaInt
                val r = min(bounds.width(), bounds.height()) * 0.16f
                tmpRect.set(-r, -r * 0.45f, r, r * 0.45f)
                canvas.drawRoundRect(tmpRect, 28f, 28f, shapePaint)
            }
        }

        canvas.restore()
    }

    private fun drawAveeBarsStyled(
        canvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        colorInt: Int,
        alphaInt: Int,
        forceSolidColor: Boolean
    ) {
        val count = layer.barCount.coerceIn(8, 128)
        val rawLive = vis?.getFrequencySlice(layer.minFreqHz, layer.maxFreqHz, count) ?: FloatArray(count) { 0.04f }

        val delayFrames = layer.reactionDelay.coerceIn(0, 12)
        val activeSlice = if (delayFrames > 0) {
            val ring = delayRingBuffers.getOrPut(layer.id) { ArrayDeque() }
            ring.addLast(rawLive.copyOf())
            while (ring.size > delayFrames + 1) {
                ring.removeFirst()
            }
            ring.first()
        } else {
            rawLive
        }

        val flipped = FloatArray(count) { i ->
            if (layer.flipInput) activeSlice[count - 1 - i] else activeSlice[i]
        }

        var values = flipped
        val passes = layer.softness.coerceIn(0, 6)
        repeat(passes) {
            val next = FloatArray(count)
            for (i in 0 until count) {
                val l = values[max(0, i - 1)]
                val c = values[i]
                val r = values[min(count - 1, i + 1)]
                next[i] = l * 0.22f + c * 0.56f + r * 0.22f
            }
            values = next
        }

        val peaks = peakHoldArrays.getOrPut(layer.id) { FloatArray(count) }.let { existing ->
            if (existing.size != count) FloatArray(count) else existing
        }
        peakHoldArrays[layer.id] = peaks

        shapePaint.reset()
        shapePaint.isAntiAlias = true
        seg2Paint.reset()
        seg2Paint.isAntiAlias = true
        seg2Paint.color = layer.seg2ColorHex.toInt()
        seg2Paint.alpha = alphaInt

        val maxBarH = bounds.height() * 0.32f
        val offsetShift = layer.colorBlendOffset * maxBarH
        if (layer.useGradient && !forceSolidColor && colorInt != layer.secondaryColorHex.toInt()) {
            shapePaint.shader = LinearGradient(
                0f, -maxBarH + offsetShift, 0f, maxBarH + offsetShift,
                colorInt, layer.secondaryColorHex.toInt(),
                Shader.TileMode.CLAMP
            )
        } else {
            shapePaint.shader = null
            shapePaint.color = colorInt
        }
        shapePaint.alpha = alphaInt

        fun computeBarAmplitude(rawVal: Float): Float {
            val clampedInput = rawVal.coerceIn(layer.minHeightScale, max(layer.minHeightScale, layer.maxHeightScale))
            val dynamicPart = clampedInput * layer.heightScale * layer.barHeightMultiplier
            val totalNorm = (layer.fixedHeight * 0.35f) + dynamicPart
            val compressed = totalNorm / (1f + totalNorm * 0.32f)
            return max(4f, maxBarH * compressed * 1.28f)
        }

        fun drawSegmentBar(left: Float, top: Float, right: Float, bottom: Float, style: SegmentStyle, paint: Paint) {
            val w = right - left
            when (style) {
                SegmentStyle.NONE -> Unit
                SegmentStyle.ROUND_BARS -> {
                    tmpRect.set(left, top, right, bottom)
                    canvas.drawRoundRect(tmpRect, w * 0.5f, w * 0.5f, paint)
                }
                SegmentStyle.SHARP_BARS -> {
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                SegmentStyle.DOTS -> {
                    val cy = (top + bottom) * 0.5f
                    val cx = (left + right) * 0.5f
                    canvas.drawCircle(cx, cy, max(2f, w * 0.5f), paint)
                }
                SegmentStyle.LINE -> {
                    val cx = (left + right) * 0.5f
                    paint.strokeWidth = max(1.5f, w * 0.35f)
                    canvas.drawLine(cx, top, cx, bottom, paint)
                }
            }
        }

        when (layer.barStyle) {
            BarStyle.CENTER_SYMMETRIC -> {
                val totalW = bounds.width() * 0.86f
                val step = totalW / count
                val gapPx = layer.barSpacingGap.coerceIn(0f, step * 0.6f)
                val barW = max(2f, (step - gapPx) * layer.barWidthRatio.coerceIn(0.10f, 0.95f))
                val startX = -totalW / 2f + (step - barW) / 2f

                for (i in 0 until count) {
                    val h = computeBarAmplitude(values[i])
                    if (h >= peaks[i]) {
                        peaks[i] = h
                    } else {
                        peaks[i] = max(h, peaks[i] - maxBarH * layer.peakFallSpeed)
                    }

                    val left = startX + i * step
                    val topY = -h * 0.5f
                    val botY = if (layer.mirrorBars) h * 0.5f else 0f

                    drawSegmentBar(left, topY, left + barW, botY, layer.segment1Style, shapePaint)

                    if (layer.segment2Style != SegmentStyle.NONE) {
                        val capH = max(3f, barW)
                        val peakTop = -peaks[i] * 0.5f - capH - 3f
                        drawSegmentBar(left, peakTop, left + barW, peakTop + capH, layer.segment2Style, seg2Paint)
                        if (layer.mirrorBars) {
                            val peakBot = peaks[i] * 0.5f + 3f
                            drawSegmentBar(left, peakBot, left + barW, peakBot + capH, layer.segment2Style, seg2Paint)
                        }
                    }
                }
            }

            BarStyle.BOTTOM_UP -> {
                val totalW = bounds.width() * 0.86f
                val step = totalW / count
                val gapPx = layer.barSpacingGap.coerceIn(0f, step * 0.6f)
                val barW = max(2f, (step - gapPx) * layer.barWidthRatio.coerceIn(0.10f, 0.95f))
                val startX = -totalW / 2f + (step - barW) / 2f
                val baseLine = maxBarH * 0.55f

                for (i in 0 until count) {
                    val h = computeBarAmplitude(values[i]) * 1.18f
                    if (h >= peaks[i]) {
                        peaks[i] = h
                    } else {
                        peaks[i] = max(h, peaks[i] - maxBarH * layer.peakFallSpeed)
                    }

                    val left = startX + i * step
                    val topY = baseLine - h
                    val botY = if (layer.mirrorBars) baseLine + h * 0.45f else baseLine

                    drawSegmentBar(left, topY, left + barW, botY, layer.segment1Style, shapePaint)

                    if (layer.segment2Style != SegmentStyle.NONE) {
                        val capH = max(3f, barW)
                        val peakTop = baseLine - peaks[i] - capH - 3f
                        drawSegmentBar(left, peakTop, left + barW, peakTop + capH, layer.segment2Style, seg2Paint)
                    }
                }
            }

            BarStyle.CIRCULAR -> {
                val innerRadius = min(bounds.width(), bounds.height()) * 0.18f
                val baseBarW = max(2.5f, (2f * Math.PI.toFloat() * innerRadius / count) * layer.barWidthRatio)

                for (i in 0 until count) {
                    val barLen = computeBarAmplitude(values[i]) * 0.78f
                    if (barLen >= peaks[i]) {
                        peaks[i] = barLen
                    } else {
                        peaks[i] = max(barLen, peaks[i] - maxBarH * layer.peakFallSpeed)
                    }

                    val barW = if (layer.barWidthAffectedByShape) baseBarW * (1f + barLen / maxBarH * 0.45f) else baseBarW
                    val angleDeg = (360f * i) / count
                    canvas.save()
                    canvas.rotate(angleDeg)
                    val innerEdge = if (layer.mirrorBars) -innerRadius + barLen * 0.35f else -innerRadius
                    drawSegmentBar(-barW / 2f, -innerRadius - barLen, barW / 2f, innerEdge, layer.segment1Style, shapePaint)
                    if (layer.segment2Style != SegmentStyle.NONE) {
                        val capH = max(3f, barW)
                        val rOut = -innerRadius - peaks[i] - 3f
                        drawSegmentBar(-barW / 2f, rOut - capH, barW / 2f, rOut, layer.segment2Style, seg2Paint)
                    }
                    canvas.restore()
                }
            }

            BarStyle.WAVE_LINE -> {
                val totalW = bounds.width() * 0.86f
                val step = totalW / max(1, count - 1)
                val startX = -totalW / 2f

                wavePath.reset()
                wavePath.moveTo(startX, 0f)
                for (i in 0 until count) {
                    val h = computeBarAmplitude(values[i]) * 0.65f
                    wavePath.lineTo(startX + i * step, -h)
                }
                if (layer.mirrorBars) {
                    for (i in (count - 1) downTo 0) {
                        val h = computeBarAmplitude(values[i]) * 0.65f
                        wavePath.lineTo(startX + i * step, h)
                    }
                } else {
                    wavePath.lineTo(startX + (count - 1) * step, 0f)
                }
                wavePath.close()
                canvas.drawPath(wavePath, shapePaint)
            }

            BarStyle.DOTS_CAPS -> {
                val totalW = bounds.width() * 0.86f
                val step = totalW / count
                val dotSize = max(3.5f, step * layer.barWidthRatio.coerceIn(0.2f, 0.9f))
                val startX = -totalW / 2f + (step - dotSize) / 2f

                for (i in 0 until count) {
                    val h = computeBarAmplitude(values[i])
                    val blocks = max(1, (h / (dotSize * 1.45f)).toInt())
                    val left = startX + i * step
                    for (b in 0 until blocks) {
                        val offsetY = b * (dotSize * 1.45f)
                        drawSegmentBar(left, -offsetY - dotSize, left + dotSize, -offsetY, SegmentStyle.ROUND_BARS, shapePaint)
                        if (layer.mirrorBars) {
                            drawSegmentBar(left, offsetY, left + dotSize, offsetY + dotSize, SegmentStyle.ROUND_BARS, shapePaint)
                        }
                    }
                }
            }
        }
    }

    private fun drawTextLayer(
        canvas: Canvas,
        layer: LayerItem,
        colorInt: Int,
        alphaInt: Int
    ) {
        textPaint.color = colorInt
        textPaint.alpha = alphaInt
        val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(layer.textContent, 0f, -yOffset, textPaint)
    }

    private fun drawParticlesLayer(
        canvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        vis: AudioVisualizerManager?,
        colorInt: Int,
        alphaInt: Int
    ) {
        val beat = vis?.beatPulse ?: 0f
        val energy = vis?.getRangeEnergy(layer.minFreqHz, layer.maxFreqHz) ?: 0.08f
        val radius = min(bounds.width(), bounds.height()) * (0.20f + (beat * 0.14f + energy * 0.12f) * layer.magnitude)
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = colorInt
        shapePaint.alpha = alphaInt

        val count = 32
        val timeSpin = (SystemClock.uptimeMillis() % 12000L) / 12000.0 * 2.0 * Math.PI
        for (i in 0 until count) {
            val angle = (2.0 * Math.PI * i) / count + timeSpin
            val px = (cos(angle) * radius).toFloat()
            val py = (sin(angle) * radius).toFloat()
            canvas.drawCircle(px, py, 5f + beat * 8f, shapePaint)
        }
    }

    private fun getCachedBitmap(uriStr: String): Bitmap? {
        bitmapCache[uriStr]?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        val decoded = try {
            val uri = Uri.parse(uriStr)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sampleSize > 1080) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
        if (decoded != null) {
            bitmapCache[uriStr] = decoded
        }
        return decoded
    }

    private fun drawImageLayer(
        canvas: Canvas,
        bounds: RectF,
        layer: LayerItem,
        colorInt: Int,
        alphaInt: Int
    ) {
        val uriStr = layer.mediaUriString
        if (uriStr != null) {
            val bmp = getCachedBitmap(uriStr)
            if (bmp != null && !bmp.isRecycled) {
                val size = min(bounds.width(), bounds.height()) * 0.48f
                tmpRect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
                imagePaint.alpha = alphaInt
                imagePaint.colorFilter = null
                canvas.drawBitmap(bmp, null, tmpRect, imagePaint)
                return
            }
        }

        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = colorInt
        shapePaint.alpha = alphaInt
        val boxW = bounds.width() * 0.34f
        val boxH = bounds.height() * 0.18f
        tmpRect.set(-boxW, -boxH, boxW, boxH)
        canvas.drawRoundRect(tmpRect, 28f, 28f, shapePaint)
    }
}
