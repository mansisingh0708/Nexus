package com.xyz.hehehe

import android.content.Context
import android.graphics.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DigitClassifier(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 28
    private val outputBuffer = Array(1) { FloatArray(10) }

    private val inputBuffer =
        ByteBuffer.allocateDirect(4 * inputSize * inputSize)
            .order(ByteOrder.nativeOrder())

    init {
        val model = context.assets.open("mnist_model.tflite").readBytes()
        val buffer = ByteBuffer.allocateDirect(model.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(model)
        interpreter = Interpreter(buffer)
    }
    fun classify(srcBitmap: Bitmap): Int? {

        val processed = preprocess(srcBitmap)
        interpreter.run(processed, outputBuffer)

        val probs = outputBuffer[0]
        var maxIndex = 0
        var maxProb = probs[0]
        for (i in probs.indices) {
            if (probs[i] > maxProb) {
                maxProb = probs[i]
                maxIndex = i
            }
        }
        //  Confidence threshold
        if (maxProb < 0.99f) { return null }
        return maxIndex
    }
    private fun preprocess(src: Bitmap): ByteBuffer {

        // 1️⃣ Convert to grayscale
        val gray = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 2️⃣ Invert colors (MNIST = white digit, black bg)
        val pixels = IntArray(gray.width * gray.height)
        gray.getPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)

        var minX = gray.width
        var minY = gray.height
        var maxX = 0
        var maxY = 0

        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val i = y * gray.width + x
                val value = 255 - Color.red(pixels[i])
                pixels[i] = Color.rgb(value, value, value)

                if (value > 30) {  // detect digit pixels
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        gray.setPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)

        // 3️⃣ Crop tight bounding box
        val cropped = Bitmap.createBitmap(
            gray,
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        )

        // 4️⃣ Put digit inside 20x20 box (MNIST rule!)
        // 4️⃣ Resize digit to fit inside 20x20 while keeping aspect ratio
        val maxDim = maxOf(cropped.width, cropped.height)
        val scale = 20f / maxDim
        val newW = (cropped.width * scale).toInt()
        val newH = (cropped.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, newW, newH, true)

// 5️⃣ Compute CENTER OF MASS of digit
        val pixelsScaled = IntArray(newW * newH)
        scaled.getPixels(pixelsScaled, 0, newW, 0, 0, newW, newH)

        var sumX = 0f
        var sumY = 0f
        var sumPixels = 0f

        for (y in 0 until newH) {
            for (x in 0 until newW) {

                val gray = Color.red(pixelsScaled[y * newW + x])

                // 🔥 HARD THRESHOLD (this is the missing piece)
                val v = if (gray > 40) 1f else 0f

                sumPixels += v
                sumX += x * v
                sumY += y * v
            }
        }

        val centerX = sumX / sumPixels
        val centerY = sumY / sumPixels

// 6️⃣ Place digit in 28x28 so its CENTER OF MASS is centered
        val padded = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
        val canvasPad = Canvas(padded)
        canvasPad.drawColor(Color.BLACK)

        val dx = 14 - centerX
        val dy = 14 - centerY

        canvasPad.drawBitmap(scaled, dx, dy, null)

        // 5️⃣ Convert to normalized float buffer
        inputBuffer.rewind()

        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = padded.getPixel(x, y)
                val value = Color.red(pixel) / 255f   // 0 → 1 float
                inputBuffer.putFloat(value)
            }
        }

        return inputBuffer
    }
}