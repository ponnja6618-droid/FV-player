package com.fourvplayer.clx.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fourvplayer.clx.effects.EffectModules
import com.fourvplayer.clx.model.BarStyle
import com.fourvplayer.clx.model.EffectItem
import com.fourvplayer.clx.model.EffectType
import com.fourvplayer.clx.model.FrequencyBand
import com.fourvplayer.clx.model.LayerBlendMode
import com.fourvplayer.clx.model.LayerItem
import com.fourvplayer.clx.model.LayerType
import com.fourvplayer.clx.model.MeasureTarget
import com.fourvplayer.clx.model.SegmentStyle
import java.util.Locale
import java.util.UUID

object AdjustBottomSheet {

    private enum class InspectorTab(val title: String, val icon: MockIconType) {
        TRANSFORM("General & Move", MockIconType.TAB_TRANSFORM),
        AUDIO_STYLE("Bars & Segments", MockIconType.TAB_AUDIO),
        EFFECTS("Effects", MockIconType.TAB_EFFECTS)
    }

    private val SWATCH_COLORS = longArrayOf(
        0xFFFFFFFF,
        0xFF9FF3D6,
        0xFF7CE8FF,
        0xFFF7E859,
        0xFFFF4D6D,
        0xFFA855F7,
        0xFF00F59B,
        0xFFFF9F1C,
        0xFF3B82F6,
        0xFF1F2937
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        context: Context,
        layer: LayerItem,
        onPickImageForLayer: (() -> Unit)? = null,
        onDeleteLayer: (() -> Unit)? = null,
        onChanged: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var currentTab = InspectorTab.AUDIO_STYLE
        val screenHeight = context.resources.displayMetrics.heightPixels

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111216"))
                setStroke(2, Color.parseColor("#2B3534"))
                cornerRadii = floatArrayOf(36f, 36f, 36f, 36f, 0f, 0f, 0f, 0f)
            }
            setPadding(20, 12, 20, 16)
        }

        val dragPill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#353844"))
                cornerRadius = 10f
            }
            layoutParams = LinearLayout.LayoutParams(84, 7).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 4, 0, 10)
            }
        }
        root.addView(dragPill)

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 10)
        }

        val colorPill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2028"))
                setStroke(2, Color.parseColor("#9FF3D6"))
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(28, 16).apply {
                setMargins(0, 0, 12, 0)
            }
        }

        val titleTv = TextView(context).apply {
            text = "${layer.name}  ✎"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val input = EditText(context).apply {
                    setText(layer.name)
                    setTextColor(Color.WHITE)
                }
                AlertDialog.Builder(context)
                    .setTitle("Rename Layer")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            layer.name = newName
                            text = "$newName  ✎"
                            onChanged()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        val deleteBtn = TextView(context).apply {
            text = "Delete"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#25191D"))
                setStroke(1, Color.parseColor("#4A262D"))
                cornerRadius = 12f
            }
            setPadding(18, 9, 18, 9)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 8, 0) }
            setOnClickListener {
                dialog.dismiss()
                mainHandler.post { onDeleteLayer?.invoke() }
            }
        }

        val closeBtn = TextView(context).apply {
            text = "Done"
            textSize = 11.5f
            setTextColor(Color.parseColor("#111216"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#9FF3D6"))
                cornerRadius = 12f
            }
            setPadding(22, 9, 22, 9)
            setOnClickListener { dialog.dismiss() }
        }

        header.addView(colorPill)
        header.addView(titleTv)
        header.addView(deleteBtn)
        header.addView(closeBtn)
        root.addView(header)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.56f).toInt()
            )
        }
        val bodyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 14)
        }
        scroll.addView(bodyContainer)
        root.addView(scroll)

        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1B20"))
                cornerRadius = 16f
            }
            setPadding(5, 5, 5, 5)
        }

        val tabContainers = mutableListOf<LinearLayout>()
        val tabLabels = mutableListOf<TextView>()
        val tabs = InspectorTab.values()
        var rebuildBody: () -> Unit = {}

        fun addSectionHeader(parent: LinearLayout, title: String) {
            val tv = TextView(context).apply {
                text = title.uppercase(Locale.US)
                textSize = 11f
                setTextColor(Color.parseColor("#9FF3D6"))
                setPadding(6, 12, 6, 4)
            }
            parent.addView(tv)
        }

        fun addToggleRow(
            parent: LinearLayout,
            title: String,
            checked: Boolean,
            onToggle: (Boolean) -> Unit
        ) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1C22"))
                    setStroke(1, Color.parseColor("#2D323D"))
                    cornerRadius = 10f
                }
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 3, 0, 3) }
                setOnClickListener {
                    onToggle(!checked)
                    onChanged()
                    mainHandler.post { rebuildBody() }
                }
            }
            val labelTv = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(Color.parseColor("#E5E7EB"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val badgeTv = TextView(context).apply {
                text = if (checked) "ON" else "OFF"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (checked) Color.parseColor("#111216") else Color.parseColor("#8C93A4"))
                background = GradientDrawable().apply {
                    setColor(if (checked) Color.parseColor("#9FF3D6") else Color.parseColor("#252830"))
                    cornerRadius = 8f
                }
                setPadding(16, 5, 16, 5)
            }
            row.addView(labelTv)
            row.addView(badgeTv)
            parent.addView(row)
        }

        fun <T> addChipSelectorRow(
            parent: LinearLayout,
            title: String,
            options: Array<T>,
            selected: T,
            labelOf: (T) -> String,
            onSelected: (T) -> Unit
        ) {
            val box = LinearLayout(context).apply {
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
            box.addView(TextView(context).apply {
                text = title
                textSize = 11.5f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(4, 0, 0, 6)
            })
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (opt in options) {
                val active = (opt == selected)
                val chip = TextView(context).apply {
                    text = labelOf(opt)
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
                        onSelected(opt)
                        onChanged()
                        mainHandler.post { rebuildBody() }
                    }
                }
                row.addView(chip)
            }
            box.addView(row)
            parent.addView(box)
        }

        fun addColorSwatchRow(
            parent: LinearLayout,
            title: String,
            currentColor: Long,
            onColorSelected: (Long) -> Unit
        ) {
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
                    layoutParams = LinearLayout.LayoutParams(0, 38, 1f).apply {
                        setMargins(4, 0, 4, 0)
                    }
                    setOnClickListener {
                        onColorSelected(c)
                        onChanged()
                        mainHandler.post { rebuildBody() }
                    }
                }
                row.addView(dot)
            }
            box.addView(row)
            parent.addView(box)
        }

        rebuildBody = {
            bodyContainer.clearFocus()
            bodyContainer.removeAllViews()

            tabContainers.forEachIndexed { idx, box ->
                val selected = tabs[idx] == currentTab
                box.background = GradientDrawable().apply {
                    setColor(if (selected) Color.parseColor("#252830") else Color.TRANSPARENT)
                    if (selected) setStroke(1, Color.parseColor("#9FF3D6"))
                    cornerRadius = 12f
                }
                tabLabels[idx].setTextColor(if (selected) Color.parseColor("#9FF3D6") else Color.parseColor("#8C93A4"))
            }

            when (currentTab) {
                InspectorTab.TRANSFORM -> {
                    addSectionHeader(bodyContainer, "General")

                    addChipSelectorRow(
                        bodyContainer,
                        "Blend Mode",
                        LayerBlendMode.values(),
                        layer.blendMode,
                        { it.label }
                    ) { layer.blendMode = it }

                    bodyContainer.addView(
                        RulerSliderView(
                            context, "Pos X", -450f, 450f, 0f, layer.posX,
                            { String.format(Locale.US, "%.1f", it) }
                        ) { layer.posX = it; onChanged() }
                    )

                    bodyContainer.addView(
                        RulerSliderView(
                            context, "Pos Y", -700f, 700f, 0f, layer.posY,
                            { String.format(Locale.US, "%.1f", it) }
                        ) { layer.posY = it; onChanged() }
                    )

                    addChipSelectorRow(
                        bodyContainer,
                        "Measure Pos",
                        MeasureTarget.values(),
                        layer.measurePos,
                        { it.label }
                    ) { layer.measurePos = it }

                    bodyContainer.addView(
                        RulerSliderView(
                            context, "Scale", 10f, 300f, 100f, layer.scale * 100f,
                            { String.format(Locale.US, "%.0f%%", it) }
                        ) { layer.scale = maxOf(0.1f, it / 100f); onChanged() }
                    )

                    addChipSelectorRow(
                        bodyContainer,
                        "Measure Scale",
                        MeasureTarget.values(),
                        layer.measureScale,
                        { it.label }
                    ) { layer.measureScale = it }

                    bodyContainer.addView(
                        RulerSliderView(
                            context, "Rotation", -180f, 180f, 0f, layer.rotation,
                            { String.format(Locale.US, "%.1f°", it) }
                        ) { layer.rotation = it; onChanged() }
                    )

                    bodyContainer.addView(
                        RulerSliderView(
                            context, "Opacity", 0f, 100f, 100f, layer.alpha * 100f,
                            { String.format(Locale.US, "%.0f%%", it) }
                        ) { layer.alpha = it / 100f; onChanged() }
                    )
                }

                InspectorTab.AUDIO_STYLE -> {
                    addSectionHeader(bodyContainer, "Frequency Band")
                    addChipSelectorRow(
                        bodyContainer,
                        "Freq Band",
                        FrequencyBand.values(),
                        layer.frequencyBand,
                        { it.label }
                    ) { band ->
                        layer.frequencyBand = band
                        layer.minFreqHz = band.defaultMinHz
                        layer.maxFreqHz = band.defaultMaxHz
                    }

                    if (layer.type == LayerType.BARS) {
                        addSectionHeader(bodyContainer, "Bars")

                        addToggleRow(bodyContainer, "Flip Input", layer.flipInput) {
                            layer.flipInput = it
                        }

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Height Scale", 0.1f, 3.0f, 1.0f, layer.heightScale,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.heightScale = it; onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Min Height", 0.0f, 0.5f, 0.0f, layer.minHeightScale,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.minHeightScale = it; onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Max Height", 0.2f, 2.5f, 1.0f, layer.maxHeightScale,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.maxHeightScale = it; onChanged() }
                        )

                        addChipSelectorRow(
                            bodyContainer,
                            "Shape Path",
                            BarStyle.values(),
                            layer.barStyle,
                            { it.label }
                        ) { layer.barStyle = it }

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Blend Offset", -1.0f, 1.0f, 0.0f, layer.colorBlendOffset,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.colorBlendOffset = it; onChanged() }
                        )

                        addSectionHeader(bodyContainer, "Segment 1")

                        addChipSelectorRow(
                            bodyContainer,
                            "Segment 1 Type",
                            arrayOf(
                                SegmentStyle.ROUND_BARS,
                                SegmentStyle.SHARP_BARS,
                                SegmentStyle.DOTS,
                                SegmentStyle.LINE
                            ),
                            layer.segment1Style,
                            { it.label }
                        ) { layer.segment1Style = it }

                        addColorSwatchRow(bodyContainer, "Color From", layer.colorHex) {
                            layer.colorHex = it
                        }
                        addColorSwatchRow(bodyContainer, "Color To", layer.secondaryColorHex) {
                            layer.secondaryColorHex = it
                            layer.useGradient = true
                        }

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Fixed Height", 0.0f, 1.0f, 0.0f, layer.fixedHeight,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.fixedHeight = it; onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Height Mult", 0.1f, 3.0f, 1.0f, layer.barHeightMultiplier,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.barHeightMultiplier = it; onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Bar Width", 0.10f, 0.95f, 0.55f, layer.barWidthRatio,
                                { String.format(Locale.US, "%.3f", it) }
                            ) { layer.barWidthRatio = it.coerceIn(0.10f, 0.95f); onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Bar Gap", 0.0f, 12.0f, 0.0f, layer.barSpacingGap,
                                { String.format(Locale.US, "%.1f", it) }
                            ) { layer.barSpacingGap = it; onChanged() }
                        )

                        addToggleRow(
                            bodyContainer,
                            "Bar Width Affected By Shape",
                            layer.barWidthAffectedByShape
                        ) { layer.barWidthAffectedByShape = it }

                        addToggleRow(bodyContainer, "Mirror", layer.mirrorBars) {
                            layer.mirrorBars = it
                        }

                        addSectionHeader(bodyContainer, "Segment 2 (Peak Caps)")

                        addChipSelectorRow(
                            bodyContainer,
                            "Segment 2 Type",
                            arrayOf(
                                SegmentStyle.NONE,
                                SegmentStyle.DOTS,
                                SegmentStyle.ROUND_BARS,
                                SegmentStyle.LINE
                            ),
                            layer.segment2Style,
                            { it.label }
                        ) { layer.segment2Style = it }

                        if (layer.segment2Style != SegmentStyle.NONE) {
                            addColorSwatchRow(bodyContainer, "Segment 2 Color", layer.seg2ColorHex) {
                                layer.seg2ColorHex = it
                            }
                            bodyContainer.addView(
                                RulerSliderView(
                                    context, "Peak Fall", 0.01f, 0.25f, 0.045f, layer.peakFallSpeed,
                                    { String.format(Locale.US, "%.3f", it) }
                                ) { layer.peakFallSpeed = it; onChanged() }
                            )
                        }

                        addSectionHeader(bodyContainer, "Behavior")

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Bar Count", 8f, 128f, 64f, layer.barCount.toFloat(),
                                { "${it.toInt()}" }
                            ) { layer.barCount = it.toInt().coerceIn(8, 128); onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Softness", 0f, 6f, 2f, layer.softness.toFloat(),
                                { "${it.toInt()}" }
                            ) { layer.softness = it.toInt().coerceIn(0, 6); onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Reaction Delay", 0f, 12f, 0f, layer.reactionDelay.toFloat(),
                                { "${it.toInt()}" }
                            ) { layer.reactionDelay = it.toInt().coerceIn(0, 12); onChanged() }
                        )
                    } else {
                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Magnitude", 0f, 4.0f, 1.2f, layer.magnitude,
                                { String.format(Locale.US, "%.2f", it) }
                            ) { layer.magnitude = it; onChanged() }
                        )

                        bodyContainer.addView(
                            RulerSliderView(
                                context, "Beat Pulse", 0f, 1.5f, 0.25f, layer.beatPulseAmount,
                                { String.format(Locale.US, "%.2f", it) }
                            ) { layer.beatPulseAmount = it; onChanged() }
                        )

                        if (layer.type == LayerType.TEXT) {
                            addColorSwatchRow(bodyContainer, "Text Color", layer.colorHex) {
                                layer.colorHex = it
                            }
                            val editBox = EditText(context).apply {
                                setText(layer.textContent)
                                setTextColor(Color.WHITE)
                                setHintTextColor(Color.GRAY)
                                hint = "Enter text..."
                                background = GradientDrawable().apply {
                                    setColor(Color.parseColor("#23252B"))
                                    cornerRadius = 12f
                                }
                                setPadding(18, 14, 18, 14)
                            }
                            val applyTextBtn = TextView(context).apply {
                                text = "Apply Text"
                                textSize = 13f
                                gravity = Gravity.CENTER
                                setTextColor(Color.BLACK)
                                background = GradientDrawable().apply {
                                    setColor(Color.parseColor("#9FF3D6"))
                                    cornerRadius = 12f
                                }
                                setPadding(18, 14, 18, 14)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(0, 8, 0, 8) }
                                setOnClickListener {
                                    layer.textContent = editBox.text.toString()
                                    onChanged()
                                }
                            }
                            bodyContainer.addView(editBox)
                            bodyContainer.addView(applyTextBtn)
                        }

                        if (layer.type == LayerType.IMAGE) {
                            val pickImgBtn = TextView(context).apply {
                                text = if (layer.mediaUriString == null) "Select Image from Gallery" else "Change Image from Gallery"
                                textSize = 13f
                                gravity = Gravity.CENTER
                                setTextColor(Color.BLACK)
                                background = GradientDrawable().apply {
                                    setColor(Color.parseColor("#9FF3D6"))
                                    cornerRadius = 12f
                                }
                                setPadding(18, 16, 18, 16)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(0, 8, 0, 8) }
                                setOnClickListener {
                                    dialog.dismiss()
                                    mainHandler.post { onPickImageForLayer?.invoke() }
                                }
                            }
                            bodyContainer.addView(pickImgBtn)
                        }

                        if (layer.type == LayerType.PARTICLES || layer.type == LayerType.COMPOSITION) {
                            addColorSwatchRow(bodyContainer, "Layer Color", layer.colorHex) {
                                layer.colorHex = it
                            }
                        }
                    }
                }

                InspectorTab.EFFECTS -> {
                    val addFxBtn = TextView(context).apply {
                        text = "+  Add Standalone Effect"
                        textSize = 13.5f
                        gravity = Gravity.CENTER
                        setTextColor(Color.BLACK)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#9FF3D6"))
                            cornerRadius = 14f
                        }
                        setPadding(20, 18, 20, 18)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 4, 0, 12) }
                        setOnClickListener {
                            showEffectGridPicker(context) { chosen ->
                                layer.effects.add(
                                    EffectItem(
                                        type = chosen,
                                        angle = 0f,
                                        spacing = 20f,
                                        magnitude = 4.0f,
                                        beatSync = 1.0f,
                                        tintColorHex = layer.secondaryColorHex
                                    )
                                )
                                mainHandler.post {
                                    rebuildBody()
                                    onChanged()
                                }
                            }
                        }
                    }
                    bodyContainer.addView(addFxBtn)

                    for (fx in layer.effects.toList()) {
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#16181E"))
                                setStroke(2, if (fx.enabled) Color.parseColor("#354542") else Color.parseColor("#252730"))
                                cornerRadius = 16f
                            }
                            setPadding(14, 12, 14, 16)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 8, 0, 8) }
                        }

                        val fxHeader = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(6, 6, 6, 10)
                        }

                        val arrowTv = TextView(context).apply {
                            text = if (fx.expanded) "▼  " else "▶  "
                            textSize = 13f
                            setTextColor(Color.WHITE)
                        }
                        val fxNameTv = TextView(context).apply {
                            text = fx.type.displayName
                            textSize = 15.5f
                            setTextColor(if (fx.enabled) Color.WHITE else Color.GRAY)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val titleClickArea = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            addView(arrowTv)
                            addView(fxNameTv)
                            setOnClickListener {
                                fx.expanded = !fx.expanded
                                mainHandler.post { rebuildBody() }
                            }
                        }

                        val enableChip = TextView(context).apply {
                            text = if (fx.enabled) "ON" else "OFF"
                            textSize = 10.5f
                            setTextColor(if (fx.enabled) Color.parseColor("#111216") else Color.parseColor("#9CA3AF"))
                            background = GradientDrawable().apply {
                                setColor(if (fx.enabled) Color.parseColor("#9FF3D6") else Color.parseColor("#252830"))
                                cornerRadius = 8f
                            }
                            setPadding(14, 5, 14, 5)
                            setOnClickListener {
                                fx.enabled = !fx.enabled
                                mainHandler.post {
                                    rebuildBody()
                                    onChanged()
                                }
                            }
                        }

                        val trashIcon = ImageView(context).apply {
                            val px = (20 * context.resources.displayMetrics.density).toInt()
                            setImageDrawable(MockupIconDrawable(MockIconType.TRASH_DELETE, Color.WHITE))
                            layoutParams = LinearLayout.LayoutParams(px, px).apply {
                                setMargins(12, 0, 6, 0)
                            }
                            setOnClickListener {
                                layer.effects.removeAll { it.id == fx.id }
                                mainHandler.post {
                                    rebuildBody()
                                    onChanged()
                                }
                            }
                        }

                        fxHeader.addView(titleClickArea)
                        fxHeader.addView(enableChip)
                        fxHeader.addView(trashIcon)
                        card.addView(fxHeader)

                        if (fx.expanded) {
                            card.addView(
                                EffectModules.buildEffectControls(
                                    context = context,
                                    effect = fx,
                                    onChanged = onChanged,
                                    onRequestRebuild = { mainHandler.post { rebuildBody() } }
                                )
                            )
                        }

                        bodyContainer.addView(card)
                    }
                }
            }
        }

        val iconPx = (15 * context.resources.displayMetrics.density).toInt()
        for (t in tabs) {
            val tabBox = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentTab = t
                    mainHandler.post { rebuildBody() }
                }
            }
            val iv = ImageView(context).apply {
                setImageDrawable(MockupIconDrawable(t.icon, Color.parseColor("#9FF3D6")))
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx).apply {
                    setMargins(0, 0, 6, 0)
                }
            }
            val tv = TextView(context).apply {
                text = t.title
                textSize = 11f
                isSingleLine = true
            }
            tabBox.addView(iv)
            tabBox.addView(tv)
            tabContainers.add(tabBox)
            tabLabels.add(tv)
            tabBar.addView(tabBox)
        }

        root.addView(tabBar)
        rebuildBody()

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.12f)
        }
        dialog.show()
    }

    fun showEffectGridPicker(context: Context, onSelected: (EffectType) -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(24, 28, 24, 32)
        }

        val effects = EffectType.values()
        var idx = 0
        while (idx < effects.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 10, 0, 10) }
            }
            for (col in 0 until 2) {
                if (idx < effects.size) {
                    val fx = effects[idx]
                    val card = TextView(context).apply {
                        text = fx.displayName.uppercase(Locale.US)
                        textSize = 13.5f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#E5E7EB"))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#181A20"))
                            setStroke(2, Color.parseColor("#354542"))
                            cornerRadius = 14f
                        }
                        setPadding(16, 42, 16, 42)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(8, 0, 8, 0)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            mainHandler.post { onSelected(fx) }
                        }
                    }
                    row.addView(card)
                    idx++
                } else {
                    row.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    })
                }
            }
            root.addView(row)
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}
