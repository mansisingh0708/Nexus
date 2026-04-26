package com.example.hehehe
import android.content.Intent

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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.hehehe.ScanActivity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private fun switchScreen(newScreen: Screen) {
        idleTimer?.let { handler.removeCallbacks(it) }
        screen = newScreen
        gesturesEnabled = false
        drawingEnabled = true
        drawView.clearCanvas()
        updateScreenUI()
    }
    private fun cancelIdleTimer() {
        idleTimer?.let { handler.removeCallbacks(it) }
        idleTimer = null
    }
    private lateinit var digitClassifier: DigitClassifier
    private val PIN_LENGTH = 4
    private var pinReadyForConfirm = false
    private lateinit var promptText: TextView
    private lateinit var successText: TextView
    private lateinit var successTick: ImageView
    private var authStarted = false
    private var pendingToPin = false
    private var firstLaunch = true
    enum class Screen { HOME, AMOUNT, PIN, SUCCESS }
    private var drawingEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private var amountTimer: Runnable? = null
    private var idleTimer: Runnable? = null
    private var gesturesEnabled = false
    private lateinit var tts: TextToSpeech
    private lateinit var gesture: GestureDetector
    private lateinit var drawView: DrawView
    private val EDGE_ZONE = 120
    private val SWIPE_THRESHOLD = 350
    private var screen = Screen.HOME
    private var amount = ""
    private var pin = ""
    private val scanLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val qr = result.data?.getStringExtra("qr_value") ?: return@registerForActivityResult

                speak("QR detected")

                switchScreen(Screen.AMOUNT)
                resetIdleTimer()
                speak("Enter amount")
            }
        }

    private fun authenticateUser(forPin: Boolean = false) {

        gesturesEnabled = false   // 🔒 lock UI during auth

        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)

                    if (forPin) {
                        switchScreen(Screen.PIN)
                        resetIdleTimer()
                        speak("Enter pin")
                    } else {
                        gesturesEnabled = true
                        speak("Authentication successful. Swipe right to scan QR. Swipe left to check balance.")
                    }
                }

                override fun onAuthenticationFailed() {
                    speak("Authentication failed")
                }

                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    speak("Authentication cancelled. Please try again.")
                    authStarted = false
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Fingerprint required")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun resetIdleTimer() {

        if(pinReadyForConfirm) return

        gesturesEnabled = false
        drawingEnabled = true   // allow drawing while active

        idleTimer?.let { handler.removeCallbacks(it) }

        idleTimer = Runnable {

            gesturesEnabled = true
            drawingEnabled = false   // LOCK drawing after idle

            speak("Swipe right to confirm or left to re enter")
        }

        handler.postDelayed(idleTimer!!, 6000)
    }

    private fun updateScreenUI() {
        val homeDashboard = findViewById<android.widget.RelativeLayout>(R.id.homeDashboard)
        val drawingPromptLayer = findViewById<android.widget.RelativeLayout>(R.id.drawingPromptLayer)
        val successCard = findViewById<android.widget.LinearLayout>(R.id.successCard)

        when (screen) {

            Screen.HOME -> {
                homeDashboard.visibility = View.VISIBLE
                drawingPromptLayer.visibility = View.GONE
                successCard.visibility = View.GONE
            }

            Screen.AMOUNT -> {
                homeDashboard.visibility = View.GONE
                drawingPromptLayer.visibility = View.VISIBLE
                promptText.text = "DRAW AMOUNT"
                successCard.visibility = View.GONE
            }

            Screen.PIN -> {
                homeDashboard.visibility = View.GONE
                drawingPromptLayer.visibility = View.VISIBLE
                promptText.text = "DRAW SECURE PIN"
                successCard.visibility = View.GONE
            }

            Screen.SUCCESS -> {
                homeDashboard.visibility = View.GONE
                drawingPromptLayer.visibility = View.GONE
                successCard.visibility = View.VISIBLE
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (firstLaunch) {
            firstLaunch = false

            handler.postDelayed({
                authenticateUser()
            }, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        promptText = findViewById(R.id.promptText)
        successText = findViewById(R.id.successText)
        successTick = findViewById(R.id.successTick)
        updateScreenUI()
        val container = findViewById<FrameLayout>(R.id.drawContainer)

        drawView = DrawView()
        container.addView(drawView)
        digitClassifier = DigitClassifier(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    handler.postDelayed({
                        speak("Fingerprint required")
                    }, 500)
                }
            }
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
                    val screenWidth = drawView.width.toFloat()
                    val requiredDistance = screenWidth * 0.45f   // ← adjust here (0.40–0.50 sweet spot)

                    val rightSwipe = dx > requiredDistance
                    val leftSwipe = dx < -requiredDistance

                    if (!rightSwipe && !leftSwipe) return false


                    when (screen) {

                        Screen.HOME -> {
                            if (rightSwipe) {

                                speak("Opening scanner")

                                val intent = Intent(this@MainActivity, ScanActivity::class.java)
                                scanLauncher.launch(intent)
                            }
                        else if (leftSwipe) {
                                speak("Checking balance")
                            }
                        }

                        Screen.AMOUNT -> {
                            if (rightSwipe && amount.isNotEmpty()) {

                                speak("Authenticate to continue")
                                authenticateUser(forPin = true)

                                return true
                            }

                            if (leftSwipe) {
                                if (amount.isEmpty()) {
                                    speak("No amount entered yet")
                                    return true
                                }

                                cancelIdleTimer()
                                amount = ""
                                switchScreen(Screen.AMOUNT)
                                resetIdleTimer()

                                speak("Amount cleared. Please enter again")
                            }
                        }


                        Screen.PIN -> {
                            if (rightSwipe) {

                                if (pin.length < PIN_LENGTH) {
                                    speak("Pin incomplete")
                                    return true
                                }

                                gesturesEnabled = false
                                drawingEnabled = false

                                screen = Screen.HOME

                                successFlow()
                                return true

                            }
                            if (leftSwipe) {

                                if (pin.isEmpty()) {
                                    speak("No pin entered yet")
                                    return true
                                }

                                cancelIdleTimer()
                                pin = ""
                                switchScreen(Screen.PIN)
                                resetIdleTimer()

                                speak("Pin cleared. Please enter again")
                            }
                        }

                        Screen.SUCCESS -> {}
                    }

                    return true
                }
            })
    }

    inner class DrawView : View(this) {

        private val paint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 25f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            setShadowLayer(35f, 0f, 0f, Color.parseColor("#D500F9"))
        }

        private val path = Path()
        private val points = mutableListOf<PointF>()

        override fun onDraw(canvas: Canvas) {
            // REMOVED canvas.drawColor(Color.WHITE) to allow gradient background to show through

            // Show strokes ONLY when entering amount
            if (screen == Screen.AMOUNT) {
                canvas.drawPath(path, paint)
            }
        }

        fun getBitmap(): Bitmap {
            // Create tight bitmap same size as view
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 🚨 IMPORTANT: The classifier expects a WHITE background with BLACK strokes
            canvas.drawColor(Color.WHITE)
            
            // Temporarily set stroke to BLACK for the classifier
            val tempColor = paint.color
            paint.color = Color.BLACK
            canvas.drawPath(path, paint)
            
            // Restore original UI color (White)
            paint.color = tempColor

            return bmp
        }
        fun clearCanvas() {
            path.reset()
            invalidate()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {

            // Let gesture detector try FIRST
            val gestureHandled = gesture.onTouchEvent(e)
            if (gestureHandled) return true

            // 🚨 HARD BLOCK: if PIN already complete, ignore ALL drawing touches
            if (screen == Screen.PIN && pin.length == PIN_LENGTH) {
                return true
            }

            // Only allow drawing on AMOUNT or PIN screens
            if (screen != Screen.AMOUNT && screen != Screen.PIN)
                return true

            cancelIdleTimer()

            if (!drawingEnabled) return true

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

                    if (digit == null) {
                        speak("Please draw a clearer digit")
                        path.reset()
                        invalidate()
                        return true
                    }

                    if (screen == Screen.AMOUNT) {
                        amount += digit
                        speak("Amount $amount")
                    }

                    if (screen == Screen.PIN) {

                        if (pin.length >= PIN_LENGTH) {
                            speak("Pin already entered. Swipe right to confirm")
                            path.reset()
                            invalidate()
                            return true
                        }

                        pin += digit
                        speak("Digit ${pin.length} entered")

                        // 🔥 Auto lock when PIN complete
                        if (pin.length == PIN_LENGTH) {
                            pinReadyForConfirm = true

                            drawingEnabled = false
                            gesturesEnabled = true
                            speak("Pin complete. Swipe right to confirm or left to re enter")
                        }
                    }
                    resetIdleTimer()

                    path.reset()
                }
            }

            invalidate()
            return true
        }
    }



    private fun recognizeStroke(points: List<PointF>): String? {

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

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        // Safety check to ensure we are within bitmap bounds
        if (left + width > fullBitmap.width || top + height > fullBitmap.height) {
            return null
        }

        val cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height)

        // DEBUG THIS instead of full canvas
//        showDebugBitmap(cropped)

        val digit = digitClassifier.classify(cropped)
        return digit?.toString()
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
        switchScreen(Screen.SUCCESS)


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
            pinReadyForConfirm = false

            handler.postDelayed({

                screen = Screen.HOME
                updateScreenUI()
                amount = ""
                pin = ""
                gesturesEnabled = true
                drawingEnabled = false
                pinReadyForConfirm = false

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
