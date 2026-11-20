package com.fourvplayer.clx.model

import java.util.UUID

enum class AspectRatioMode(val label: String, val ratio: Float) {
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f)
}

enum class ExportResolution(val label: String, val shortSide: Int, val longSide: Int) {
    RES_720P("720p", 720, 1280),
    RES_1080P("1080p", 1080, 1920)
}

enum class ExportFps(val fps: Int) {
    FPS_30(30),
    FPS_60(60)
}

data class ExportConfig(
    val aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_9_16,
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val fps: ExportFps = ExportFps.FPS_60,
    val loopAudio: Boolean = true
)

enum class LayerType(val displayName: String, val defaultPillColor: Long) {
    COMPOSITION("Composition", 0xFF232730),
    TEXT("Text", 0xFF232730),
    BARS("Bars", 0xFF232730),
    PARTICLES("Particles", 0xFF232730),
    IMAGE("Image", 0xFF232730),
    AUDIO_PROVIDER("AudioProvider", 0xFF232730)
}

enum class LayerBlendMode(val label: String) {
    NORMAL("Normal"),
    ADD("Add"),
    SCREEN("Screen"),
    LIGHTEN("Lighten")
}

enum class MeasureTarget(val label: String) {
    NOTHING("Nothing"),
    BEAT("Beat"),
    BASS("Bass")
}

enum class BarStyle(val label: String) {
    CENTER_SYMMETRIC("HorizontalLine"),
    BOTTOM_UP("BottomLine"),
    CIRCULAR("Circle"),
    WAVE_LINE("WaveLine"),
    DOTS_CAPS("DotsLine")
}

enum class SegmentStyle(val label: String) {
    NONE("None"),
    ROUND_BARS("RoundBars"),
    SHARP_BARS("SharpBars"),
    DOTS("Dots"),
    LINE("Line")
}

enum class FrequencyBand(val label: String, val defaultMinHz: Int, val defaultMaxHz: Int) {
    ALL("All", 20, 16000),
    BASS("Bass", 20, 250),
    MID("Mid", 250, 4000),
    TREBLE("Treble", 4000, 16000)
}

enum class EffectType(val displayName: String) {
    WAVE_WARP("Wave Warp"),
    GLOW("Glow"),
    MOTION_BLUR("Motion Blur"),
    BLUR("Gaussian Blur"),
    RGB_SPLIT("RGB Split"),
    MIRROR("Mirror")
}

enum class MirrorAxis(val label: String) {
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    QUAD("Quad")
}

data class EffectItem(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    var angle: Float = 0f,
    var spacing: Float = 20f,
    var magnitude: Float = 4.0f,
    var speed: Float = 1.0f,
    var beatSync: Float = 1.0f,
    var tintColorHex: Long = 0xFF9FF3D6,
    var mirrorAxis: MirrorAxis = MirrorAxis.HORIZONTAL,
    var enabled: Boolean = true,
    var expanded: Boolean = true
)

data class LayerItem(
    val id: String = UUID.randomUUID().toString(),
    val type: LayerType,
    var name: String = type.displayName,
    val pillColorHex: Long = type.defaultPillColor,
    var isVisible: Boolean = true,

    // General & Measure
    var blendMode: LayerBlendMode = LayerBlendMode.ADD,
    var measurePos: MeasureTarget = MeasureTarget.NOTHING,
    var measureScale: MeasureTarget = MeasureTarget.NOTHING,
    var scaleUniform: Boolean = true,

    // Transform
    var posX: Float = 0f,
    var posY: Float = 0f,
    var scale: Float = 1.0f,
    var rotation: Float = 0f,
    var alpha: Float = 1.0f,

    // Audio & Bars Shape
    var frequencyBand: FrequencyBand = FrequencyBand.ALL,
    var minFreqHz: Int = 20,
    var maxFreqHz: Int = 16000,
    var magnitude: Float = 1.2f,
    var beatPulseAmount: Float = 0.25f,
    var flipInput: Boolean = false,
    var heightScale: Float = 1.0f,
    var minHeightScale: Float = 0.0f,
    var maxHeightScale: Float = 1.0f,
    var barStyle: BarStyle = BarStyle.CENTER_SYMMETRIC,
    var colorBlendOffset: Float = 0.0f,

    // Segment 1
    var segment1Style: SegmentStyle = SegmentStyle.ROUND_BARS,
    var colorHex: Long = 0xFFFFFFFF,
    var secondaryColorHex: Long = 0xFFFFFFFF,
    var useGradient: Boolean = true,
    var fixedHeight: Float = 0.0f,
    var barHeightMultiplier: Float = 1.0f,
    var barWidthRatio: Float = 0.55f,
    var barSpacingGap: Float = 0.0f,
    var barWidthAffectedByShape: Boolean = false,
    var mirrorBars: Boolean = true,

    // Segment 2 (Optional Peak Dots / Secondary Cap)
    var segment2Style: SegmentStyle = SegmentStyle.NONE,
    var seg2ColorHex: Long = 0xFF9FF3D6,
    var peakFallSpeed: Float = 0.045f,

    // Behavior
    var barCount: Int = 64,
    var softness: Int = 2,
    var reactionDelay: Int = 0,

    // Text & Media
    var textContent: String = "CLX VISUALIZER",
    var mediaUriString: String? = null,

    // Effects
    val effects: MutableList<EffectItem> = mutableListOf()
) {
    fun deepCopy(): LayerItem {
        return copy(
            id = UUID.randomUUID().toString(),
            name = "$name Copy",
            effects = effects.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList()
        )
    }
}
