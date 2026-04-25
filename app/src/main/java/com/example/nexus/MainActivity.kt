package com.example.hehehe
import com.xyz.hehehe.DigitClassifier
import android.os.Handler
import android.os.Looper
import android.graphics.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var digitClassifier: DigitClassifier

    enum class Screen { HOME, AMOUNT, PIN }
    private var drawingEnabled = true

    private val handler = Handler(Looper.getMainLooper())
    private var amountTimer: Runnable? = null
    private var idleTimer: Runnable? = null

    private var gesturesEnabled = true

    private lateinit var tts: TextToSpeech
    private lateinit var gesture: GestureDetector
    private lateinit var drawView: DrawView

    private val EDGE_ZONE = 120
    private val SWIPE_THRESHOLD = 350

    private var screen = Screen.HOME
    private var amount = ""
    private var pin = ""

    private fun resetIdleTimer() {

        gesturesEnabled = false
        drawingEnabled = true   // allow drawing while active

        idleTimer?.let { handler.removeCallbacks(it) }

        idleTimer = Runnable {

            gesturesEnabled = true
            drawingEnabled = false   // LOCK drawing after idle

            speak("Swipe right to confirm or left to re enter")
        }

        handler.postDelayed(idleTimer!!, 5000)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        drawView = DrawView()
        setContentView(drawView)
        digitClassifier = DigitClassifier(this)

        tts = TextToSpeech(this) {
            tts.language = Locale.US
            speak("Swipe right to scan QR. Swipe left to check balance.")
        }

        gesture = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {

                    if (!gesturesEnabled) return false
                    if (e1 == null) return false

                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y

                    if (Math.abs(dx) < SWIPE_THRESHOLD) return false
                    if (Math.abs(dx) < Math.abs(dy)) return false

                    val startX = e1.x
                    val endX = e2.x

                    val fullRightSwipe =
                        startX < EDGE_ZONE && endX > drawView.width - EDGE_ZONE

                    val fullLeftSwipe =
                        startX > drawView.width - EDGE_ZONE && endX < EDGE_ZONE

                    if (!fullRightSwipe && !fullLeftSwipe) return false

                    when (screen) {

                        Screen.HOME -> {
                            if (fullRightSwipe) {
                                screen = Screen.AMOUNT

                                drawingEnabled = true
                                gesturesEnabled = false
                                resetIdleTimer()   // ← this is the fix

                                speak("Enter the amount")

                            } else if (fullLeftSwipe) {
                                speak("Checking balance")
                            }
                        }

                        Screen.AMOUNT -> {
                            if (fullRightSwipe && amount.isNotEmpty()) {
                                screen = Screen.PIN

                                drawingEnabled = true
                                gesturesEnabled = false
                                resetIdleTimer()   // ← this is the fix


                                speak("Enter pin")
                            }


                            if (fullLeftSwipe) {
                                amount = ""
                                drawingEnabled = true
                                speak("Re enter the amount")
                            }
                        }

                        Screen.PIN -> {
                            if (fullRightSwipe) successFlow()

                            if (fullLeftSwipe) {
                                pin = ""
                                drawingEnabled = true
                                speak("Re enter pin")
                            }
                        }
                    }

                    return true
                }
            })
    }

    inner class DrawView : View(this) {

        private val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 55f            // ← critical change
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND  // ← makes digits blob-like
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        private val path = Path()
        private val points = mutableListOf<PointF>()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)
        }
        fun getBitmap(): Bitmap {

            // Create tight bitmap same size as view
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // IMPORTANT: draw ONLY path, not background UI
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)

            return bmp
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {

            val gestureHandled = gesture.onTouchEvent(e)
            if (gestureHandled) return true

            if (!drawingEnabled) return true

            if (screen != Screen.AMOUNT && screen != Screen.PIN)
                return true


            when (e.action) {

                MotionEvent.ACTION_DOWN -> {
                    path.moveTo(e.x, e.y)
                    points.clear()
                    points.add(PointF(e.x, e.y))
                }

                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(e.x, e.y)
                    points.add(PointF(e.x, e.y))
                }

                MotionEvent.ACTION_UP -> {

                    if (points.size < 8) {
                        path.reset()
                        return true
                    }

                    val digit = recognizeStroke(points)

                    if (screen == Screen.AMOUNT) {
                        amount += digit
                        speak("Amount $amount")
                    }

                    if (screen == Screen.PIN) {
                        pin += digit
                        speak("Digit entered")
                    }

                    resetIdleTimer()
                    path.reset()
                }
            }

            invalidate()
            return true
        }
    }

    private fun recognizeStroke(points: List<PointF>): String {

        // 1. Get full canvas bitmap
        val fullBitmap = drawView.getBitmap()

        // 2. Create bounding box of drawn points (CRITICAL FIX)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = 0f
        var maxY = 0f

        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        // add padding around digit
        val padding = 40
        val left = (minX - padding).toInt().coerceAtLeast(0)
        val top = (minY - padding).toInt().coerceAtLeast(0)
        val right = (maxX + padding).toInt().coerceAtMost(fullBitmap.width)
        val bottom = (maxY + padding).toInt().coerceAtMost(fullBitmap.height)

        val width = right - left
        val height = bottom - top

        val cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height)

        // DEBUG THIS instead of full canvas
        showDebugBitmap(cropped)

        val digit = digitClassifier.classify(cropped)
        return digit.toString()
    }
    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    private fun showDebugBitmap(bitmap: Bitmap) {
        val iv = android.widget.ImageView(this)
        iv.setImageBitmap(bitmap)

        android.app.AlertDialog.Builder(this)
            .setTitle("MODEL INPUT")
            .setView(iv)
            .setPositiveButton("OK", null)
            .show()
    }
    private fun successFlow() {

        tts.speak(
            "Payment of $amount rupees successful",
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )

        handler.postDelayed({

            tts.speak(
                "Redirecting to home page",
                TextToSpeech.QUEUE_ADD,
                null,
                null
            )

            handler.postDelayed({

                screen = Screen.HOME
                amount = ""
                pin = ""

                tts.speak(
                    "Swipe right to scan QR and left to check balance",
                    TextToSpeech.QUEUE_ADD,
                    null,
                    null
                )

            }, 2000)

        }, 2000)
    }


    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
