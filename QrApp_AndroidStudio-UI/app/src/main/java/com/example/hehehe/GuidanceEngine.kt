package com.example.hehehe
import android.graphics.Rect
class GuidanceEngine(
    private val frameWidth: Int,
    private val frameHeight: Int
) {

    fun decide(boundingBox: Rect?): Instruction {

        // 1. If no QR detected
        if (boundingBox == null) {
            return Instruction.NOT_FOUND
        }
        val left = boundingBox.left
        val right = boundingBox.right
        val top = boundingBox.top
        val bottom = boundingBox.bottom

        val boxWidth = boundingBox.width()
        val boxHeight = boundingBox.height()

        val frameArea = frameWidth * frameHeight
        val boxArea = boxWidth * boxHeight

        val areaRatio = boxArea.toFloat() / frameArea.toFloat()

        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()

        val frameCenterX = frameWidth / 2f
        val frameCenterY = frameHeight / 2f

        val offsetX = (centerX - frameCenterX) / frameWidth
        val offsetY = (centerY - frameCenterY) / frameHeight


        // 1. Distance FIRST
        if (areaRatio > 0.4f) return Instruction.PULL_BACK
        if (areaRatio < 0.08f) return Instruction.MOVE_CLOSER

// 2. Edge clipping (proportional tolerance)
        val edgeTolerance = (frameWidth * 0.05f).toInt()

        if (left <= edgeTolerance) return Instruction.MOVE_LEFT
        if (right >= frameWidth - edgeTolerance) return Instruction.MOVE_RIGHT
        if (top <= edgeTolerance) return Instruction.MOVE_UP
        if (bottom >= frameHeight - edgeTolerance) return Instruction.MOVE_DOWN

// 3. Center alignment
        if (offsetX < -0.15f) return Instruction.MOVE_LEFT
        if (offsetX > 0.15f) return Instruction.MOVE_RIGHT
        if (offsetY < -0.15f) return Instruction.MOVE_UP
        if (offsetY > 0.15f) return Instruction.MOVE_DOWN

        return Instruction.ALIGNED
    }
}