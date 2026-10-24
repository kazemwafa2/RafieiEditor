package com.rafiei.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Simple line icons drawn with Canvas so that no image resources are needed. */
object Icons {

    private fun star(c: Canvas, cx: Float, cy: Float, ro: Float, ri: Float, p: Paint, n: Int = 5) {
        val path = Path()
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) ro else ri
            val a = Math.toRadians(-90.0 + i * 180.0 / n)
            val x = cx + (Math.cos(a) * r).toFloat()
            val y = cy + (Math.sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }

    fun draw(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, p: Paint) {
        val sw = Math.max(1.6f, s * 0.085f)
        p.strokeWidth = sw
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        // From here the icon box is -0.5..0.5; compensate the stroke width for the scale.
        p.strokeWidth = sw / s
        when (kind) {
            "back" -> {
                c.drawLine(0.4f, 0f, -0.38f, 0f, p)
                c.drawLine(-0.38f, 0f, -0.1f, -0.28f, p)
                c.drawLine(-0.38f, 0f, -0.1f, 0.28f, p)
            }
            "help" -> {
                c.drawCircle(-0.04f, -0.04f, 0.3f, p)
                c.drawLine(0.18f, 0.18f, 0.38f, 0.38f, p)
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                p.textSize = 0.4f
                c.drawText("?", -0.04f, 0.1f, p)
                p.style = Paint.Style.STROKE
            }
            "undo", "redo" -> {
                if (kind == "redo") c.scale(-1f, 1f)
                val r = RectF(-0.3f, -0.18f, 0.32f, 0.44f)
                c.drawArc(r, 200f, 230f, false, p)
                c.drawLine(-0.34f, -0.02f, -0.34f, -0.3f, p)
                c.drawLine(-0.34f, -0.02f, -0.06f, -0.02f, p)
            }
            "play" -> {
                p.style = Paint.Style.FILL
                val path = Path()
                path.moveTo(-0.22f, -0.36f); path.lineTo(0.38f, 0f); path.lineTo(-0.22f, 0.36f); path.close()
                c.drawPath(path, p)
            }
            "pause" -> {
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(-0.28f, -0.34f, -0.08f, 0.34f), 0.04f, 0.04f, p)
                c.drawRoundRect(RectF(0.08f, -0.34f, 0.28f, 0.34f), 0.04f, 0.04f, p)
            }
            "fullscreen" -> {
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    c.drawLine(sx * 0.38f, sy * 0.38f, sx * 0.38f, sy * 0.16f, p)
                    c.drawLine(sx * 0.38f, sy * 0.38f, sx * 0.16f, sy * 0.38f, p)
                }
            }
            "plus" -> {
                c.drawLine(-0.36f, 0f, 0.36f, 0f, p); c.drawLine(0f, -0.36f, 0f, 0.36f, p)
            }
            "check" -> {
                c.drawLine(-0.36f, 0.02f, -0.1f, 0.28f, p); c.drawLine(-0.1f, 0.28f, 0.4f, -0.28f, p)
            }
            "close" -> {
                c.drawLine(-0.3f, -0.3f, 0.3f, 0.3f, p); c.drawLine(0.3f, -0.3f, -0.3f, 0.3f, p)
            }
            "canvas" -> {
                c.drawRoundRect(RectF(-0.28f, -0.28f, 0.28f, 0.28f), 0.05f, 0.05f, p)
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    c.drawLine(sx * 0.44f, sy * 0.44f, sx * 0.44f, sy * 0.3f, p)
                    c.drawLine(sx * 0.44f, sy * 0.44f, sx * 0.3f, sy * 0.44f, p)
                }
            }
            "audio", "music" -> {
                p.style = Paint.Style.FILL
                c.drawCircle(-0.15f, 0.28f, 0.14f, p)
                c.drawCircle(0.25f, 0.2f, 0.14f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(-0.02f, 0.28f, -0.02f, -0.36f, p)
                c.drawLine(0.38f, 0.2f, 0.38f, -0.44f, p)
                c.drawLine(-0.02f, -0.36f, 0.38f, -0.44f, p)
            }
            "sticker" -> {
                c.drawCircle(0f, 0f, 0.38f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(-0.14f, -0.08f, 0.04f, p); c.drawCircle(0.14f, -0.08f, 0.04f, p)
                p.style = Paint.Style.STROKE
                c.drawArc(RectF(-0.2f, -0.06f, 0.2f, 0.26f), 20f, 140f, false, p)
            }
            "text" -> {
                c.drawLine(-0.34f, -0.32f, 0.34f, -0.32f, p)
                c.drawLine(0f, -0.32f, 0f, 0.36f, p)
                c.drawLine(-0.34f, -0.32f, -0.34f, -0.18f, p)
                c.drawLine(0.34f, -0.32f, 0.34f, -0.18f, p)
            }
            "effect" -> {
                p.style = Paint.Style.FILL
                star(c, 0f, 0.02f, 0.42f, 0.18f, p)
            }
            "filter" -> {
                c.drawCircle(-0.16f, -0.12f, 0.24f, p)
                c.drawCircle(0.16f, -0.12f, 0.24f, p)
                c.drawCircle(0f, 0.16f, 0.24f, p)
            }
            "pip" -> {
                c.drawRoundRect(RectF(-0.4f, -0.34f, 0.4f, 0.26f), 0.06f, 0.06f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(0.0f, 0.0f, 0.44f, 0.38f), 0.05f, 0.05f, p)
            }
            "adjust" -> {
                val ys = floatArrayOf(-0.25f, 0f, 0.25f)
                val xs = floatArrayOf(-0.12f, 0.15f, -0.02f)
                for (i in 0 until 3) {
                    c.drawLine(-0.4f, ys[i], 0.4f, ys[i], p)
                    p.style = Paint.Style.FILL
                    c.drawCircle(xs[i], ys[i], 0.08f, p)
                    p.style = Paint.Style.STROKE
                }
            }
            "speed" -> {
                c.drawArc(RectF(-0.42f, -0.34f, 0.42f, 0.5f), 180f, 180f, false, p)
                c.drawLine(0f, 0.16f, 0.22f, -0.12f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(0f, 0.16f, 0.06f, p)
                p.style = Paint.Style.STROKE
            }
            "volume" -> {
                p.style = Paint.Style.FILL
                val path = Path()
                path.moveTo(-0.38f, -0.12f); path.lineTo(-0.2f, -0.12f); path.lineTo(0f, -0.3f)
                path.lineTo(0f, 0.3f); path.lineTo(-0.2f, 0.12f); path.lineTo(-0.38f, 0.12f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                c.drawArc(RectF(-0.08f, -0.2f, 0.2f, 0.2f), -50f, 100f, false, p)
                c.drawArc(RectF(-0.08f, -0.34f, 0.38f, 0.34f), -50f, 100f, false, p)
            }
            "mute" -> {
                p.style = Paint.Style.FILL
                val path = Path()
                path.moveTo(-0.38f, -0.12f); path.lineTo(-0.2f, -0.12f); path.lineTo(0f, -0.3f)
                path.lineTo(0f, 0.3f); path.lineTo(-0.2f, 0.12f); path.lineTo(-0.38f, 0.12f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                c.drawLine(0.12f, -0.12f, 0.4f, 0.16f, p); c.drawLine(0.4f, -0.12f, 0.12f, 0.16f, p)
            }
            "split" -> {
                c.drawLine(0f, -0.42f, 0f, 0.42f, p)
                c.drawRoundRect(RectF(-0.4f, -0.2f, -0.1f, 0.2f), 0.04f, 0.04f, p)
                c.drawRoundRect(RectF(0.1f, -0.2f, 0.4f, 0.2f), 0.04f, 0.04f, p)
            }
            "trim" -> {
                c.drawLine(-0.3f, -0.36f, -0.3f, 0.36f, p); c.drawLine(-0.3f, -0.36f, -0.14f, -0.36f, p); c.drawLine(-0.3f, 0.36f, -0.14f, 0.36f, p)
                c.drawLine(0.3f, -0.36f, 0.3f, 0.36f, p); c.drawLine(0.3f, -0.36f, 0.14f, -0.36f, p); c.drawLine(0.3f, 0.36f, 0.14f, 0.36f, p)
                c.drawLine(-0.12f, 0f, 0.12f, 0f, p)
            }
            "delete" -> {
                c.drawLine(-0.36f, -0.24f, 0.36f, -0.24f, p)
                c.drawLine(-0.12f, -0.24f, -0.12f, -0.36f, p); c.drawLine(0.12f, -0.24f, 0.12f, -0.36f, p)
                c.drawLine(-0.12f, -0.36f, 0.12f, -0.36f, p)
                val path = Path()
                path.moveTo(-0.26f, -0.24f); path.lineTo(-0.2f, 0.4f); path.lineTo(0.2f, 0.4f); path.lineTo(0.26f, -0.24f)
                c.drawPath(path, p)
                c.drawLine(-0.07f, -0.1f, -0.07f, 0.28f, p); c.drawLine(0.07f, -0.1f, 0.07f, 0.28f, p)
            }
            "copy" -> {
                c.drawRoundRect(RectF(-0.14f, -0.14f, 0.38f, 0.38f), 0.06f, 0.06f, p)
                val path = Path()
                path.moveTo(-0.14f, 0.18f); path.lineTo(-0.38f, 0.18f); path.lineTo(-0.38f, -0.38f); path.lineTo(0.18f, -0.38f); path.lineTo(0.18f, -0.14f)
                c.drawPath(path, p)
            }
            "rotate" -> {
                c.drawArc(RectF(-0.34f, -0.34f, 0.34f, 0.34f), -60f, 280f, false, p)
                c.drawLine(0.17f, -0.3f, 0.36f, -0.34f, p); c.drawLine(0.17f, -0.3f, 0.3f, -0.12f, p)
            }
            "flip" -> {
                c.drawLine(0f, -0.42f, 0f, 0.42f, p)
                val a = Path(); a.moveTo(-0.1f, -0.3f); a.lineTo(-0.4f, 0.3f); a.lineTo(-0.1f, 0.3f); a.close()
                c.drawPath(a, p)
                p.style = Paint.Style.FILL
                val b = Path(); b.moveTo(0.1f, -0.3f); b.lineTo(0.4f, 0.3f); b.lineTo(0.1f, 0.3f); b.close()
                c.drawPath(b, p)
                p.style = Paint.Style.STROKE
            }
            "crop" -> {
                c.drawLine(-0.2f, -0.42f, -0.2f, 0.2f, p); c.drawLine(-0.42f, -0.2f, 0.2f, -0.2f, p)
                c.drawLine(0.2f, -0.2f, 0.2f, 0.42f, p); c.drawLine(-0.2f, 0.2f, 0.42f, 0.2f, p)
            }
            "freeze" -> {
                for (i in 0 until 3) {
                    val a = Math.toRadians(i * 60.0 + 90.0)
                    val x = (Math.cos(a) * 0.4).toFloat()
                    val y = (Math.sin(a) * 0.4).toFloat()
                    c.drawLine(-x, -y, x, y, p)
                }
            }
            "anim" -> {
                c.drawCircle(-0.1f, 0.06f, 0.26f, p)
                p.style = Paint.Style.FILL
                star(c, 0.26f, -0.26f, 0.16f, 0.07f, p, 4)
                val path = Path(); path.moveTo(-0.16f, -0.04f); path.lineTo(0.02f, 0.06f); path.lineTo(-0.16f, 0.16f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
            }
            "mic" -> {
                c.drawRoundRect(RectF(-0.12f, -0.4f, 0.12f, 0.1f), 0.12f, 0.12f, p)
                c.drawArc(RectF(-0.28f, -0.18f, 0.28f, 0.28f), 0f, 180f, false, p)
                c.drawLine(0f, 0.28f, 0f, 0.42f, p); c.drawLine(-0.14f, 0.42f, 0.14f, 0.42f, p)
            }
            "tts" -> {
                c.drawRoundRect(RectF(-0.4f, -0.36f, 0.4f, 0.16f), 0.1f, 0.1f, p)
                c.drawLine(-0.12f, 0.16f, -0.2f, 0.4f, p); c.drawLine(-0.2f, 0.4f, 0.04f, 0.16f, p)
                c.drawLine(-0.22f, -0.14f, 0.22f, -0.14f, p); c.drawLine(-0.22f, 0f, 0.1f, 0f, p)
            }
            "sfx" -> {
                val hs = floatArrayOf(0.12f, 0.3f, 0.5f, 0.24f, 0.4f, 0.16f)
                for (i in hs.indices) {
                    val x = -0.34f + i * 0.136f
                    c.drawLine(x, -hs[i] / 2f, x, hs[i] / 2f, p)
                }
            }
            "ratio" -> {
                c.drawRoundRect(RectF(-0.4f, -0.3f, 0.4f, 0.3f), 0.05f, 0.05f, p)
                c.drawLine(-0.22f, 0.1f, -0.22f, -0.1f, p); c.drawLine(-0.22f, -0.1f, 0.0f, -0.1f, p)
            }
            "bg" -> {
                c.drawRoundRect(RectF(-0.4f, -0.4f, 0.4f, 0.4f), 0.06f, 0.06f, p)
                p.style = Paint.Style.FILL
                c.drawRect(-0.4f, -0.4f, 0f, 0f, p); c.drawRect(0f, 0f, 0.4f, 0.4f, p)
                p.style = Paint.Style.STROKE
            }
            "more" -> {
                p.style = Paint.Style.FILL
                c.drawCircle(0f, -0.28f, 0.06f, p); c.drawCircle(0f, 0f, 0.06f, p); c.drawCircle(0f, 0.28f, 0.06f, p)
                p.style = Paint.Style.STROKE
            }
            "crown" -> {
                val path = Path()
                path.moveTo(-0.4f, 0.28f); path.lineTo(-0.4f, -0.2f); path.lineTo(-0.2f, 0f); path.lineTo(0f, -0.3f)
                path.lineTo(0.2f, 0f); path.lineTo(0.4f, -0.2f); path.lineTo(0.4f, 0.28f); path.close()
                p.style = Paint.Style.FILL
                c.drawPath(path, p)
            }
            "gear" -> {
                c.drawCircle(0f, 0f, 0.16f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val ca = Math.cos(a).toFloat()
                    val sa = Math.sin(a).toFloat()
                    c.drawLine(ca * 0.27f, sa * 0.27f, ca * 0.42f, sa * 0.42f, p)
                }
                c.drawCircle(0f, 0f, 0.28f, p)
            }
            "trans" -> {
                c.drawRoundRect(RectF(-0.44f, -0.22f, -0.08f, 0.22f), 0.04f, 0.04f, p)
                c.drawRoundRect(RectF(0.08f, -0.22f, 0.44f, 0.22f), 0.04f, 0.04f, p)
                c.drawLine(-0.04f, 0f, 0.04f, 0f, p)
            }
            "import" -> {
                c.drawLine(0f, -0.4f, 0f, 0.14f, p)
                c.drawLine(0f, 0.14f, -0.2f, -0.06f, p); c.drawLine(0f, 0.14f, 0.2f, -0.06f, p)
                c.drawLine(-0.36f, 0.3f, 0.36f, 0.3f, p)
            }
            "share" -> {
                p.style = Paint.Style.FILL
                c.drawCircle(-0.28f, 0f, 0.1f, p); c.drawCircle(0.26f, -0.28f, 0.1f, p); c.drawCircle(0.26f, 0.28f, 0.1f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(-0.28f, 0f, 0.26f, -0.28f, p); c.drawLine(-0.28f, 0f, 0.26f, 0.28f, p)
            }
            "reverse" -> {
                c.drawLine(-0.4f, -0.12f, 0.36f, -0.12f, p); c.drawLine(-0.4f, -0.12f, -0.22f, -0.3f, p)
                c.drawLine(0.4f, 0.14f, -0.36f, 0.14f, p); c.drawLine(0.4f, 0.14f, 0.22f, 0.32f, p)
            }
            "replace" -> {
                c.drawLine(-0.4f, -0.14f, 0.3f, -0.14f, p); c.drawLine(0.3f, -0.14f, 0.14f, -0.3f, p)
                c.drawLine(0.4f, 0.14f, -0.3f, 0.14f, p); c.drawLine(-0.3f, 0.14f, -0.14f, 0.3f, p)
            }
            "brush" -> {
                c.drawLine(0.3f, -0.36f, -0.1f, 0.1f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(-0.2f, 0.24f, 0.16f, p)
                p.style = Paint.Style.STROKE
            }
            "palette" -> {
                c.drawCircle(0f, 0f, 0.4f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(-0.14f, -0.12f, 0.05f, p); c.drawCircle(0.12f, -0.16f, 0.05f, p); c.drawCircle(0.2f, 0.06f, 0.05f, p); c.drawCircle(-0.2f, 0.1f, 0.05f, p)
                p.style = Paint.Style.STROKE
            }
            "font" -> {
                c.drawLine(-0.36f, 0.36f, -0.1f, -0.36f, p); c.drawLine(-0.1f, -0.36f, 0.16f, 0.36f, p); c.drawLine(-0.26f, 0.1f, 0.06f, 0.1f, p)
                c.drawLine(0.22f, 0.36f, 0.22f, -0.04f, p)
            }
            "align_left", "align_center", "align_right" -> {
                val ws = floatArrayOf(0.8f, 0.5f, 0.7f, 0.4f)
                for (i in 0 until 4) {
                    val y = -0.3f + i * 0.2f
                    val w = ws[i]
                    val x0 = when (kind) {
                        "align_left" -> -0.4f
                        "align_right" -> 0.4f - w
                        else -> -w / 2f
                    }
                    c.drawLine(x0, y, x0 + w, y, p)
                }
            }
            "bold" -> {
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER; p.textSize = 0.8f; p.isFakeBoldText = true
                c.drawText("B", 0f, 0.28f, p)
                p.isFakeBoldText = false
                p.style = Paint.Style.STROKE
            }
            "italic" -> {
                c.drawLine(0.16f, -0.34f, -0.1f, 0.34f, p)
                c.drawLine(0.02f, -0.34f, 0.3f, -0.34f, p); c.drawLine(-0.24f, 0.34f, 0.04f, 0.34f, p)
            }
            "outline" -> {
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER; p.textSize = 0.8f
                c.drawText("A", 0f, 0.28f, p)
                p.style = Paint.Style.STROKE
            }
            "opacity" -> {
                c.drawCircle(0f, 0f, 0.38f, p)
                p.style = Paint.Style.FILL
                c.drawArc(RectF(-0.38f, -0.38f, 0.38f, 0.38f), 90f, 180f, true, p)
                p.style = Paint.Style.STROKE
            }
            "keyboard" -> {
                c.drawRoundRect(RectF(-0.42f, -0.26f, 0.42f, 0.26f), 0.06f, 0.06f, p)
                p.style = Paint.Style.FILL
                for (i in 0 until 4) c.drawCircle(-0.27f + i * 0.18f, -0.1f, 0.035f, p)
                c.drawRect(-0.24f, 0.06f, 0.24f, 0.13f, p)
                p.style = Paint.Style.STROKE
            }
            "shadow" -> {
                c.drawRoundRect(RectF(-0.32f, -0.32f, 0.16f, 0.16f), 0.04f, 0.04f, p)
                p.style = Paint.Style.FILL
                p.alpha = 110
                c.drawRoundRect(RectF(-0.16f, -0.16f, 0.34f, 0.34f), 0.04f, 0.04f, p)
                p.alpha = 255
                p.style = Paint.Style.STROKE
            }
            "layers" -> {
                for (i in 0 until 3) {
                    val y = -0.2f + i * 0.2f
                    val path = Path()
                    path.moveTo(-0.4f, y); path.lineTo(0f, y - 0.16f); path.lineTo(0.4f, y); path.lineTo(0f, y + 0.16f); path.close()
                    c.drawPath(path, p)
                }
            }
            "wand" -> {
                c.drawLine(-0.36f, 0.36f, 0.12f, -0.12f, p)
                p.style = Paint.Style.FILL
                star(c, 0.22f, -0.22f, 0.2f, 0.08f, p, 4)
                p.style = Paint.Style.STROKE
            }
            "zoom" -> {
                c.drawCircle(-0.06f, -0.06f, 0.28f, p)
                c.drawLine(0.14f, 0.14f, 0.4f, 0.4f, p)
                c.drawLine(-0.16f, -0.06f, 0.04f, -0.06f, p); c.drawLine(-0.06f, -0.16f, -0.06f, 0.04f, p)
            }
            "home" -> {
                val path = Path()
                path.moveTo(-0.4f, 0.04f); path.lineTo(0f, -0.38f); path.lineTo(0.4f, 0.04f)
                c.drawPath(path, p)
                c.drawRect(-0.28f, 0.04f, 0.28f, 0.38f, p)
            }
            "photo" -> {
                c.drawRoundRect(RectF(-0.4f, -0.34f, 0.4f, 0.34f), 0.06f, 0.06f, p)
                c.drawCircle(-0.18f, -0.1f, 0.07f, p)
                val path = Path(); path.moveTo(-0.4f, 0.26f); path.lineTo(-0.1f, 0.0f); path.lineTo(0.1f, 0.18f); path.lineTo(0.22f, 0.06f); path.lineTo(0.4f, 0.24f)
                c.drawPath(path, p)
            }
            "video" -> {
                c.drawRoundRect(RectF(-0.4f, -0.3f, 0.4f, 0.3f), 0.06f, 0.06f, p)
                p.style = Paint.Style.FILL
                val path = Path(); path.moveTo(-0.1f, -0.14f); path.lineTo(0.16f, 0f); path.lineTo(-0.1f, 0.14f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
            }
            "collage" -> {
                c.drawRoundRect(RectF(-0.4f, -0.4f, 0.4f, 0.4f), 0.05f, 0.05f, p)
                c.drawLine(-0.04f, -0.4f, -0.04f, 0.4f, p)
                c.drawLine(-0.04f, 0.0f, 0.4f, 0.0f, p)
            }
            else -> c.drawCircle(0f, 0f, 0.3f, p)
        }
        c.restore()
    }
}

class IconView(ctx: Context, var kind: String, var tint: Int = 0xFFFFFFFF.toInt()) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var padFrac = 0.18f

    fun set(kind: String, tint: Int) {
        this.kind = kind
        this.tint = tint
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = tint
        val s = Math.min(width, height) * (1f - padFrac * 2f)
        Icons.draw(canvas, kind, width / 2f, height / 2f, s, paint)
    }
}
