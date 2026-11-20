package com.fourvplayer.clx.effects

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.fourvplayer.clx.model.EffectItem
import com.fourvplayer.clx.model.EffectType
import com.fourvplayer.clx.model.MirrorAxis
import com.fourvplayer.clx.ui.RulerSliderView
import java.util.Locale

/**
 * Dedicated per-effect UI & parameter builders so each effect behaves
 * independently with its own clean controls and Alight Motion feel.
 */
object EffectModules {

    private val SWATCH_COLORS = longArrayOf(
        0xFFFFFFFF,
        0xFF9FF3D6,
        0xFF7CE8FF,
        0xFFF7E859,
        0xFFFF4D6D,
        0xFFA855F7,
        0xFF00F59B,
        0xFFFF9F1C,
        0xFF3B82F6
    )

    fun buildEffectControls(
        context: Context,
        effect: EffectItem,
        onChanged: () -> Unit,
        onRequestRebuild: () -> Unit
    ): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }

        when (effect.type) {
            EffectType.WAVE_WARP -> buildWaveWarpModule(context, box, effect, onChanged)
            EffectType.GLOW -> buildGlowModule(context, box, effect, onChanged, onRequestRebuild)
            EffectType.MOTION_BLUR -> buildMotionBlurModule(context, box, effect, onChanged)
            EffectType.BLUR -> buildGaussianBlurModule(context, box, effect, onChanged)
            EffectType.RGB_SPLIT -> buildRgbSplitModule(context, box, effect, onChanged)
            EffectType.MIRROR -> buildMirrorModule(context, box, effect, onChanged, onRequestRebuild)
        }

        // Common audio beat reactivity slider per effect
        box.addView(
            RulerSliderView(
                context, "Beat Sync", 0f, 3.0f, 1.0f, effect.beatSync,
                { String.format(Locale.US, "%.2f", it) }
            ) {
                effect.beatSync = it
                onChanged()
            }
        )

        return box
    }

    private fun buildWaveWarpModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit
    ) {
        parent.addView(
            RulerSliderView(
                context, "Angle", 0f, 360f, 0f, fx.angle,
                { String.format(Locale.US, "%.1f°", it) }
            ) { fx.angle = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Spacing", 2f, 100f, 20f, fx.spacing,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.spacing = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Magnitude", 0f, 25f, 4.0f, fx.magnitude,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.magnitude = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Speed", 0f, 5f, 1.0f, fx.speed,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.speed = it; onChanged() }
        )
    }

    private fun buildGlowModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit,
        onRequestRebuild: () -> Unit
    ) {
        parent.addView(
            RulerSliderView(
                context, "Radius", 0f, 100f, 25f, fx.spacing,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.spacing = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Intensity", 0f, 20f, 7.5f, fx.magnitude,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.magnitude = it; onChanged() }
        )
        parent.addView(
            buildColorRow(context, "Glow Color", fx.tintColorHex) { chosen ->
                fx.tintColorHex = chosen
                onChanged()
                onRequestRebuild()
            }
        )
    }

    private fun buildMotionBlurModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit
    ) {
        parent.addView(
            RulerSliderView(
                context, "Angle", 0f, 360f, 0f, fx.angle,
                { String.format(Locale.US, "%.1f°", it) }
            ) { fx.angle = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Magnitude", 0f, 25f, 6.0f, fx.magnitude,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.magnitude = it; onChanged() }
        )
    }

    private fun buildGaussianBlurModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit
    ) {
        parent.addView(
            RulerSliderView(
                context, "Strength", 0f, 100f, 25f, fx.spacing,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.spacing = it; onChanged() }
        )
    }

    private fun buildRgbSplitModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit
    ) {
        parent.addView(
            RulerSliderView(
                context, "Angle", 0f, 360f, 0f, fx.angle,
                { String.format(Locale.US, "%.1f°", it) }
            ) { fx.angle = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Distance", 0f, 25f, 5.0f, fx.magnitude,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.magnitude = it; onChanged() }
        )
    }

    private fun buildMirrorModule(
        context: Context,
        parent: LinearLayout,
        fx: EffectItem,
        onChanged: () -> Unit,
        onRequestRebuild: () -> Unit
    ) {
        val axisBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1C22"))
                setStroke(1, Color.parseColor("#2D323D"))
                cornerRadius = 10f
            }
            setPadding(12, 8, 12, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 3, 0, 3) }
        }
        axisBox.addView(TextView(context).apply {
            text = "Mirror Axis"
            textSize = 11.5f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(4, 0, 0, 6)
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (axis in MirrorAxis.values()) {
            val active = (axis == fx.mirrorAxis)
            val chip = TextView(context).apply {
                text = axis.label
                textSize = 11f
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextColor(if (active) Color.parseColor("#111216") else Color.parseColor("#E5E7EB"))
                background = GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#9FF3D6") else Color.parseColor("#23262F"))
                    cornerRadius = 8f
                }
                setPadding(6, 10, 6, 10)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
                setOnClickListener {
                    fx.mirrorAxis = axis
                    onChanged()
                    onRequestRebuild()
                }
            }
            row.addView(chip)
        }
        axisBox.addView(row)
        parent.addView(axisBox)

        parent.addView(
            RulerSliderView(
                context, "Offset", 0f, 100f, 50f, fx.spacing,
                { String.format(Locale.US, "%.1f", it - 50f) }
            ) { fx.spacing = it; onChanged() }
        )
        parent.addView(
            RulerSliderView(
                context, "Alpha", 0f, 15f, 8.0f, fx.magnitude,
                { String.format(Locale.US, "%.1f", it) }
            ) { fx.magnitude = it; onChanged() }
        )
    }

    private fun buildColorRow(
        context: Context,
        title: String,
        currentColor: Long,
        onColorSelected: (Long) -> Unit
    ): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1C22"))
                setStroke(1, Color.parseColor("#2D323D"))
                cornerRadius = 10f
            }
            setPadding(14, 8, 14, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 3, 0, 3) }
        }
        val hexStr = String.format(Locale.US, "%08X", currentColor.toInt())
        box.addView(TextView(context).apply {
            text = "$title   #$hexStr"
            textSize = 11.5f
            setTextColor(Color.parseColor("#D1D5DB"))
            setPadding(0, 0, 0, 7)
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (c in SWATCH_COLORS) {
            val active = (c == currentColor)
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c.toInt())
                    if (active) setStroke(3, Color.parseColor("#9FF3D6"))
                }
                layoutParams = LinearLayout.LayoutParams(0, 36, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
                setOnClickListener { onColorSelected(c) }
            }
            row.addView(dot)
        }
        box.addView(row)
        return box
    }
}
