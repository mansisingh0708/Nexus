package com.example.hehehe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CameraFrameOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
    }

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val transparentPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 🔥 FULLSCREEN RECTANGLE (not square)
        val marginX = w * 0.05f   // 5% side margin
        val marginY = h * 0.10f   // 10% top & bottom margin

        val left = marginX
        val top = marginY
        val right = w - marginX
        val bottom = h - marginY

        val rect = RectF(left, top, right, bottom)

        // ---- DARK MASK OUTSIDE FRAME ----
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.drawRect(rect, transparentPaint)
        canvas.restoreToCount(layer)

        // ---- WHITE RECTANGLE BORDER ----
        canvas.drawRect(rect, framePaint)
    }
}
