package com.fourvplayer.clx.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

enum class MockIconType {
    EXIT_BACK,
    SETTINGS_GEAR,
    EXPORT_SHARE,
    UNDO,
    REDO,
    SKIP_START,
    PLAY_LOOP,
    PAUSE_LOOP,
    SKIP_END,
    DUPLICATE,
    FIT_SCREEN,
    EYE_OPEN,
    EYE_CLOSED,
    PLUS,
    TAB_TRANSFORM,
    TAB_AUDIO,
    TAB_EFFECTS,
    TRASH_DELETE
}

class MockupIconDrawable(
    private val type: MockIconType,
    private val tintColor: Int = Color.WHITE
) : Drawable() {

    private val primaryStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val accentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 115
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val s = minOf(w, h)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()

        primaryStroke.color = tintColor
        primaryStroke.strokeWidth = s * 0.075f
        accentStroke.color = tintColor
        accentStroke.alpha = 110
        accentStroke.strokeWidth = s * 0.075f
        fillPaint.color = tintColor

        when (type) {
            MockIconType.EXIT_BACK -> {
                val frame = Path().apply {
                    moveTo(cx - s * 0.04f, cy - s * 0.34f)
                    lineTo(cx + s * 0.24f, cy - s * 0.34f)
                    quadTo(cx + s * 0.34f, cy - s * 0.34f, cx + s * 0.34f, cy - s * 0.24f)
                    lineTo(cx + s * 0.34f, cy + s * 0.24f)
                    quadTo(cx + s * 0.34f, cy + s * 0.34f, cx + s * 0.24f, cy + s * 0.34f)
                    lineTo(cx - s * 0.04f, cy + s * 0.34f)
                }
                canvas.drawPath(frame, accentStroke)

                val arrow = Path().apply {
                    moveTo(cx + s * 0.14f, cy)
                    lineTo(cx - s * 0.32f, cy)
                    moveTo(cx - s * 0.16f, cy - s * 0.16f)
                    lineTo(cx - s * 0.32f, cy)
                    lineTo(cx - s * 0.16f, cy + s * 0.16f)
                }
                canvas.drawPath(arrow, primaryStroke)
            }

            MockIconType.SETTINGS_GEAR -> {
                canvas.drawCircle(cx, cy, s * 0.13f, primaryStroke)
                canvas.drawCircle(cx, cy, s * 0.27f, accentStroke)
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, cx, cy)
                    canvas.drawLine(cx, cy - s * 0.27f, cx, cy - s * 0.37f, primaryStroke)
                    canvas.restore()
                }
            }

            MockIconType.EXPORT_SHARE -> {
                val box = Path().apply {
                    moveTo(cx + s * 0.02f, cy - s * 0.30f)
                    lineTo(cx - s * 0.20f, cy - s * 0.30f)
                    quadTo(cx - s * 0.30f, cy - s * 0.30f, cx - s * 0.30f, cy - s * 0.20f)
                    lineTo(cx - s * 0.30f, cy + s * 0.20f)
                    quadTo(cx - s * 0.30f, cy + s * 0.30f, cx - s * 0.20f, cy + s * 0.30f)
                    lineTo(cx + s * 0.20f, cy + s * 0.30f)
                    quadTo(cx + s * 0.30f, cy + s * 0.30f, cx + s * 0.30f, cy + s * 0.20f)
                    lineTo(cx + s * 0.30f, cy - s * 0.02f)
                }
                canvas.drawPath(box, accentStroke)

                val arrow = Path().apply {
                    moveTo(cx - s * 0.04f, cy + s * 0.04f)
                    lineTo(cx + s * 0.32f, cy - s * 0.32f)
                    moveTo(cx + s * 0.12f, cy - s * 0.32f)
                    lineTo(cx + s * 0.32f, cy - s * 0.32f)
                    lineTo(cx + s * 0.32f, cy - s * 0.12f)
                }
                canvas.drawPath(arrow, primaryStroke)
            }

            MockIconType.UNDO -> {
                val p = Path().apply {
                    moveTo(cx - s * 0.14f, cy - s * 0.24f)
                    lineTo(cx - s * 0.32f, cy - s * 0.08f)
                    lineTo(cx - s * 0.14f, cy + s * 0.08f)
                    moveTo(cx - s * 0.30f, cy - s * 0.08f)
                    lineTo(cx + s * 0.12f, cy - s * 0.08f)
                    quadTo(cx + s * 0.32f, cy - s * 0.08f, cx + s * 0.32f, cy + s * 0.10f)
                    quadTo(cx + s * 0.32f, cy + s * 0.26f, cx + s * 0.12f, cy + s * 0.26f)
                }
                canvas.drawPath(p, primaryStroke)
            }

            MockIconType.REDO -> {
                val p = Path().apply {
                    moveTo(cx + s * 0.14f, cy - s * 0.24f)
                    lineTo(cx + s * 0.32f, cy - s * 0.08f)
                    lineTo(cx + s * 0.14f, cy + s * 0.08f)
                    moveTo(cx + s * 0.30f, cy - s * 0.08f)
                    lineTo(cx - s * 0.12f, cy - s * 0.08f)
                    quadTo(cx - s * 0.32f, cy - s * 0.08f, cx - s * 0.32f, cy + s * 0.10f)
                    quadTo(cx - s * 0.32f, cy + s * 0.26f, cx - s * 0.12f, cy + s * 0.26f)
                }
                canvas.drawPath(p, primaryStroke)
            }

            MockIconType.SKIP_START -> {
                canvas.drawLine(cx - s * 0.24f, cy - s * 0.24f, cx - s * 0.24f, cy + s * 0.24f, primaryStroke)
                val p = Path().apply {
                    moveTo(cx + s * 0.26f, cy)
                    lineTo(cx - s * 0.10f, cy)
                    moveTo(cx + s * 0.06f, cy - s * 0.18f)
                    lineTo(cx - s * 0.12f, cy)
                    lineTo(cx + s * 0.06f, cy + s * 0.18f)
                }
                canvas.drawPath(p, primaryStroke)
            }

            MockIconType.SKIP_END -> {
                canvas.drawLine(cx + s * 0.24f, cy - s * 0.24f, cx + s * 0.24f, cy + s * 0.24f, primaryStroke)
                val p = Path().apply {
                    moveTo(cx - s * 0.26f, cy)
                    lineTo(cx + s * 0.10f, cy)
                    moveTo(cx - s * 0.06f, cy - s * 0.18f)
                    lineTo(cx + s * 0.12f, cy)
                    lineTo(cx - s * 0.06f, cy + s * 0.18f)
                }
                canvas.drawPath(p, primaryStroke)
            }

            MockIconType.PLAY_LOOP -> {
                val rect = RectF(cx - s * 0.31f, cy - s * 0.25f, cx + s * 0.31f, cy + s * 0.25f)
                canvas.drawRoundRect(rect, s * 0.10f, s * 0.10f, accentStroke)
                val tri = Path().apply {
                    moveTo(cx - s * 0.08f, cy - s * 0.12f)
                    lineTo(cx + s * 0.13f, cy)
                    lineTo(cx - s * 0.08f, cy + s * 0.12f)
                    close()
                }
                canvas.drawPath(tri, fillPaint)
            }

            MockIconType.PAUSE_LOOP -> {
                val rect = RectF(cx - s * 0.31f, cy - s * 0.25f, cx + s * 0.31f, cy + s * 0.25f)
                canvas.drawRoundRect(rect, s * 0.10f, s * 0.10f, primaryStroke)
                canvas.drawLine(cx - s * 0.08f, cy - s * 0.11f, cx - s * 0.08f, cy + s * 0.11f, primaryStroke)
                canvas.drawLine(cx + s * 0.08f, cy - s * 0.11f, cx + s * 0.08f, cy + s * 0.11f, primaryStroke)
            }

            MockIconType.DUPLICATE -> {
                val front = RectF(cx - s * 0.08f, cy - s * 0.26f, cx + s * 0.28f, cy + s * 0.10f)
                canvas.drawRoundRect(front, s * 0.07f, s * 0.07f, primaryStroke)
                val back = Path().apply {
                    moveTo(cx - s * 0.10f, cy - s * 0.10f)
                    lineTo(cx - s * 0.24f, cy - s * 0.10f)
                    lineTo(cx - s * 0.24f, cy + s * 0.26f)
                    lineTo(cx + s * 0.12f, cy + s * 0.26f)
                    lineTo(cx + s * 0.12f, cy + s * 0.12f)
                }
                canvas.drawPath(back, accentStroke)
                canvas.drawLine(cx - s * 0.28f, cy + s * 0.16f, cx - s * 0.12f, cy + s * 0.16f, primaryStroke)
                canvas.drawLine(cx - s * 0.20f, cy + s * 0.08f, cx - s * 0.20f, cy + s * 0.24f, primaryStroke)
            }

            MockIconType.FIT_SCREEN -> {
                val d = s * 0.29f
                val arm = s * 0.12f
                val p = Path().apply {
                    moveTo(cx - d, cy - d + arm); lineTo(cx - d, cy - d); lineTo(cx - d + arm, cy - d)
                    moveTo(cx + d - arm, cy - d); lineTo(cx + d, cy - d); lineTo(cx + d, cy - d + arm)
                    moveTo(cx - d, cy + d - arm); lineTo(cx - d, cy + d); lineTo(cx - d + arm, cy + d)
                    moveTo(cx + d - arm, cy + d); lineTo(cx + d, cy + d); lineTo(cx + d, cy + d - arm)
                }
                canvas.drawPath(p, primaryStroke)
                val inner = RectF(cx - s * 0.12f, cy - s * 0.12f, cx + s * 0.12f, cy + s * 0.12f)
                canvas.drawRoundRect(inner, s * 0.03f, s * 0.03f, accentStroke)
            }

            MockIconType.EYE_OPEN -> {
                val eyePath = Path().apply {
                    moveTo(cx - s * 0.36f, cy)
                    quadTo(cx, cy - s * 0.28f, cx + s * 0.36f, cy)
                    quadTo(cx, cy + s * 0.28f, cx - s * 0.36f, cy)
                    close()
                }
                primaryStroke.color = Color.parseColor("#232732")
                canvas.drawPath(eyePath, primaryStroke)
                fillPaint.color = Color.parseColor("#5C4033")
                canvas.drawCircle(cx, cy, s * 0.145f, fillPaint)
                fillPaint.color = Color.parseColor("#111318")
                canvas.drawCircle(cx, cy, s * 0.075f, fillPaint)
                fillPaint.color = Color.WHITE
                canvas.drawCircle(cx - s * 0.045f, cy - s * 0.045f, s * 0.028f, fillPaint)
            }

            MockIconType.EYE_CLOSED -> {
                primaryStroke.color = Color.parseColor("#7D8597")
                val eyePath = Path().apply {
                    moveTo(cx - s * 0.34f, cy - s * 0.04f)
                    quadTo(cx, cy + s * 0.20f, cx + s * 0.34f, cy - s * 0.04f)
                }
                canvas.drawPath(eyePath, primaryStroke)
                canvas.drawLine(cx - s * 0.24f, cy + s * 0.22f, cx + s * 0.24f, cy - s * 0.22f, primaryStroke)
            }

            MockIconType.PLUS -> {
                val arm = s * 0.26f
                primaryStroke.strokeWidth = s * 0.08f
                canvas.drawLine(cx - arm, cy, cx + arm, cy, primaryStroke)
                canvas.drawLine(cx, cy - arm, cx, cy + arm, primaryStroke)
            }

            MockIconType.TAB_TRANSFORM -> {
                canvas.drawLine(cx - s * 0.28f, cy, cx + s * 0.28f, cy, primaryStroke)
                canvas.drawLine(cx, cy - s * 0.28f, cx, cy + s * 0.28f, primaryStroke)
            }

            MockIconType.TAB_AUDIO -> {
                val bars = floatArrayOf(-0.22f, -0.11f, 0f, 0.11f, 0.22f)
                val heights = floatArrayOf(0.12f, 0.26f, 0.18f, 0.30f, 0.14f)
                for (i in bars.indices) {
                    val x = cx + s * bars[i]
                    val hh = s * heights[i]
                    canvas.drawLine(x, cy - hh, x, cy + hh, primaryStroke)
                }
            }

            MockIconType.TAB_EFFECTS -> {
                val p = Path().apply {
                    moveTo(cx, cy - s * 0.30f)
                    lineTo(cx + s * 0.08f, cy - s * 0.08f)
                    lineTo(cx + s * 0.30f, cy)
                    lineTo(cx + s * 0.08f, cy + s * 0.08f)
                    lineTo(cx, cy + s * 0.30f)
                    lineTo(cx - s * 0.08f, cy + s * 0.08f)
                    lineTo(cx - s * 0.30f, cy)
                    lineTo(cx - s * 0.08f, cy - s * 0.08f)
                    close()
                }
                canvas.drawPath(p, primaryStroke)
            }

            MockIconType.TRASH_DELETE -> {
                // Lid & handle
                canvas.drawLine(cx - s * 0.28f, cy - s * 0.22f, cx + s * 0.28f, cy - s * 0.22f, primaryStroke)
                canvas.drawLine(cx - s * 0.10f, cy - s * 0.32f, cx + s * 0.10f, cy - s * 0.32f, primaryStroke)
                canvas.drawLine(cx - s * 0.10f, cy - s * 0.32f, cx - s * 0.10f, cy - s * 0.22f, primaryStroke)
                canvas.drawLine(cx + s * 0.10f, cy - s * 0.32f, cx + s * 0.10f, cy - s * 0.22f, primaryStroke)
                // Bin body
                val bin = Path().apply {
                    moveTo(cx - s * 0.22f, cy - s * 0.22f)
                    lineTo(cx - s * 0.18f, cy + s * 0.30f)
                    lineTo(cx + s * 0.18f, cy + s * 0.30f)
                    lineTo(cx + s * 0.22f, cy - s * 0.22f)
                }
                canvas.drawPath(bin, primaryStroke)
                canvas.drawLine(cx - s * 0.07f, cy - s * 0.10f, cx - s * 0.06f, cy + s * 0.18f, primaryStroke)
                canvas.drawLine(cx + s * 0.07f, cy - s * 0.10f, cx + s * 0.06f, cy + s * 0.18f, primaryStroke)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        primaryStroke.alpha = alpha
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        primaryStroke.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
