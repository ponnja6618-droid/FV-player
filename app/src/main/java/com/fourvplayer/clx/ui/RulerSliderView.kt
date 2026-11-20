package com.fourvplayer.clx.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.floor

/**
 * Compact Alight Motion Parameter Control Row:
 * - Drag Ruler Left/Right to scrub smoothly
 * - Tap right-side number box to type exact value via keyboard
 */
class RulerSliderView(
    context: Context,
    private val label: String,
    private val minVal: Float,
    private val maxVal: Float,
    private val defaultVal: Float,
    initialVal: Float,
    private val formatValue: (Float) -> String,
    private val onValueChanged: (Float) -> Unit
) : LinearLayout(context) {

    var currentValue: Float = initialVal.coerceIn(minVal, maxVal)
        private set

    private val valueTv: TextView
    private val rulerTrack: VernierTrackView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = context.resources.displayMetrics.density
        val rowH = (44 * density).toInt()
        val corner = 8f * density

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowH).apply {
            setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
        }

        // 1. Compact Left Label Box
        val labelBox = TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(Color.parseColor("#E5E7EB"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#23252B"))
                setStroke(1, Color.parseColor("#323640"))
                cornerRadius = corner
            }
            setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            layoutParams = LayoutParams((88 * density).toInt(), LayoutParams.MATCH_PARENT)
        }

        // 2. Center Vernier Ruler Track with Theme Green Needle
        rulerTrack = VernierTrackView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            }
        }

        // 3. Compact Right Value Box (Tap to open numeric keyboard)
        valueTv = TextView(context).apply {
            text = formatValue(currentValue)
            textSize = 12.5f
            gravity = Gravity.CENTER
            isSingleLine = true
            setTextColor(Color.parseColor("#9FF3D6"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#23252B"))
                setStroke(1, Color.parseColor("#354542"))
                cornerRadius = corner
            }
            layoutParams = LayoutParams((70 * density).toInt(), LayoutParams.MATCH_PARENT)
            setOnClickListener {
                showKeyboardInputDialog()
            }
        }

        addView(labelBox)
        addView(rulerTrack)
        addView(valueTv)
    }

    private fun showKeyboardInputDialog() {
        val formattedClean = String.format(Locale.US, "%.2f", currentValue)
            .trimEnd('0')
            .trimEnd('.')

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(formattedClean)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setSelection(0, text.length)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(32, 28, 32, 28)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(label)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val typed = editText.text.toString().trim().toFloatOrNull()
                if (typed != null) {
                    setValue(typed, notify = true)
                }
            }
            .setNeutralButton("Reset") { _, _ ->
                setValue(defaultVal, notify = true)
            }
            .setNegativeButton("Cancel", null)
            .create()

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val typed = editText.text.toString().trim().toFloatOrNull()
                if (typed != null) {
                    setValue(typed, notify = true)
                }
                dialog.dismiss()
                true
            } else {
                false
            }
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    fun setValue(newVal: Float, notify: Boolean = false) {
        currentValue = newVal.coerceIn(minVal, maxVal)
        valueTv.text = formatValue(currentValue)
        rulerTrack.invalidate()
        if (notify) {
            onValueChanged(currentValue)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class VernierTrackView(ctx: Context) : View(ctx) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15161A")
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#383B45")
            strokeWidth = 1.3f * ctx.resources.displayMetrics.density
        }
        private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#565B6B")
            strokeWidth = 1.7f * ctx.resources.displayMetrics.density
        }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9FF3D6")
            strokeWidth = 2.0f * ctx.resources.displayMetrics.density
        }

        private var downX = 0f
        private var startVal = 0f

        private fun getVirtualSpanPx(): Float = width.coerceAtLeast(1) * 4.8f

        init {
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        downX = event.x
                        startVal = currentValue
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val span = maxVal - minVal
                        val deltaVal = (-dx / getVirtualSpanPx()) * span
                        setValue(startVal + deltaVal, notify = true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> false
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val density = resources.displayMetrics.density
            val tickSpacing = 7.0f * density
            val span = (maxVal - minVal).coerceAtLeast(0.001f)
            val normPos = (currentValue - minVal) / span

            val virtualScrollPx = normPos * getVirtualSpanPx()
            val phase = ((virtualScrollPx % tickSpacing) + tickSpacing) % tickSpacing
            val baseTickIndex = floor(virtualScrollPx / tickSpacing).toInt()

            val tickTop = h * 0.20f
            val tickBottom = h * 0.80f

            var x = -phase
            var idx = baseTickIndex
            while (x <= w + tickSpacing) {
                val isMajor = (idx % 5 == 0)
                canvas.drawLine(
                    x,
                    if (isMajor) tickTop * 0.65f else tickTop,
                    x,
                    if (isMajor) h - tickTop * 0.65f else tickBottom,
                    if (isMajor) majorTickPaint else tickPaint
                )
                x += tickSpacing
                idx++
            }

            val cx = w / 2f
            canvas.drawLine(cx, 0f, cx, h, needlePaint)
        }
    }
}
