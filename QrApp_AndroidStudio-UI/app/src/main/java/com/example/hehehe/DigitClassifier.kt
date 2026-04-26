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
        color = Color.parseColor("#00FFFF") // Accent Cyan
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
    }

    private val transparentPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val marginX = w * 0.10f
        val marginY = h * 0.20f

        val rect = RectF(marginX, marginY, w - marginX, h - marginY)
        val cornerRadius = 40f

        // ---- DARK MASK OUTSIDE FRAME ----
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, transparentPaint)
        canvas.restoreToCount(layer)

        // ---- ACCENT ROUNDED BORDER ----
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, framePaint)
    }
}
CameraFrameOverlay