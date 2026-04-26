package com.example.hehehe
import android.content.Intent
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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.hehehe.ScanActivity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import java.util.Locale
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
    private var isAwaitingInput = false
    private var pinReadyForConfirm = false
    private lateinit var instructionText: TextView
    private lateinit var homeIcon: ImageView
    private lateinit var successCard: View
    private lateinit var homeCard: View
    private lateinit var homeContent: View
    private lateinit var titleText: TextView
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
    enum class InputMode { NONE, GESTURE, VOICE }
    private var inputMode = InputMode.NONE
    private val scanLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val qr = result.data?.getStringExtra("qr_value") ?: return@registerForActivityResult

                speak("QR detected")

                switchScreen(Screen.AMOUNT)
                inputMode = InputMode.NONE
                drawingEnabled = false
                gesturesEnabled = true

                speak("Swipe right for gesture input or left for voice input")
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

        if (pinReadyForConfirm) return

        cancelIdleTimer()

        idleTimer = Runnable {

            if (screen == Screen.AMOUNT && amount.isNotEmpty() && !isAwaitingInput) {

                gesturesEnabled = true
                drawingEnabled = false

                speak("Swipe right to confirm or left to re enter")
            }
            if (screen == Screen.PIN && pin.length == PIN_LENGTH) {


                gesturesEnabled = true
                drawingEnabled = false

                speak("Swipe right to confirm or left to re enter")
            }
        }

        handler.postDelayed(idleTimer!!, 4000)
    }

    private fun updateScreenUI() {

        when (screen) {

            Screen.HOME -> {
                homeContent.visibility = View.VISIBLE
                titleText.text = "ZARIYA"
                instructionText.visibility = View.VISIBLE

                // home icon
                homeIcon.setImageResource(R.drawable.ic_home)
                successCard.visibility = View.GONE
                homeIcon.visibility = View.VISIBLE
            }

            Screen.AMOUNT -> {
                homeContent.visibility = View.GONE
                titleText.text = if (inputMode == InputMode.VOICE) "Voice Entry Screen" else "Amount Entry Screen"
                instructionText.visibility = View.GONE

                // change icon → rupee
                homeIcon.setImageResource(R.drawable.ic_rupee)
                successCard.visibility = View.GONE
                homeIcon.visibility = View.VISIBLE
            }

            Screen.PIN -> {
                homeContent.visibility = View.GONE
                titleText.text = "PIN Entry Screen"
                instructionText.visibility = View.GONE
                homeIcon.setImageResource(R.drawable.ic_lock)
                successCard.visibility = View.GONE
                homeIcon.visibility = View.VISIBLE
            }

            Screen.SUCCESS -> {
                homeContent.visibility = View.GONE
                titleText.text = "Transaction Complete"
                instructionText.visibility = View.GONE

                successCard.visibility = View.VISIBLE

                homeIcon.visibility = View.GONE
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
        homeCard = findViewById(R.id.homeCard)
        homeContent = findViewById(R.id.homeContent)
        titleText = findViewById(R.id.title)
        instructionText = findViewById(R.id.instruction)
        homeIcon = findViewById(R.id.homeIcon)
        successCard = findViewById(R.id.successCard)
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

                            if (inputMode == InputMode.NONE) {

                                if (rightSwipe) {
                                    inputMode = InputMode.GESTURE
                                    cancelIdleTimer()

                                    amount = ""
                                    isAwaitingInput = true

                                    drawingEnabled = true
                                    gesturesEnabled = false
                                    updateScreenUI()
                                    resetIdleTimer()

                                    speak("Draw the amount")

                                } else if (leftSwipe) {
                                    inputMode = InputMode.VOICE
                                    cancelIdleTimer()

                                    amount = ""
                                    isAwaitingInput = true

                                    gesturesEnabled = false
                                    drawingEnabled = false
                                    updateScreenUI()

                                    speakAndThen("Speak the amount"){
                                        startVoiceInput()
                                    }
                                }

                                return true
                            }

                            if (inputMode != InputMode.NONE && amount.isNotEmpty()) {

                                if (rightSwipe && amount.isNotEmpty()) {

                                    speak("Authenticate to continue")
                                    authenticateUser(forPin = true)
                                    return true
                                }

                                if (leftSwipe) {

                                    amount = ""
                                    inputMode = InputMode.NONE

                                    speak("Re enter amount. Swipe right for gesture or left for voice")
                                    return true
                                }
                            }

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
                                inputMode = InputMode.NONE

                                drawingEnabled = false
                                gesturesEnabled = true

                                speak("Amount cleared. Swipe right for gesture or left for voice")
                            }
                        }


                        Screen.PIN -> {
                            cancelIdleTimer()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200 && resultCode == RESULT_OK) {

            val result = data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS
            )

            if (!result.isNullOrEmpty()) {

                val spokenText = result[0]
                tts.speak("Processing amount", TextToSpeech.QUEUE_FLUSH, null, null)

                val cleanedText = spokenText.trim()

                val digits = cleanedText.filter { it.isDigit() }

                val parsedNumber = when {
                    digits.isNotEmpty() -> digits.toIntOrNull()
                    else -> parseSpokenNumber(cleanedText.lowercase(Locale.getDefault()))
                }

                if (parsedNumber != null) {

                    amount = parsedNumber.toString()

                    isAwaitingInput = false

                    inputMode = InputMode.VOICE
                    gesturesEnabled = true
                    drawingEnabled = false

                    speak("Amount $amount")

                    resetIdleTimer()
                } else {
                    speak("Could not understand amount")
                }
            }
        }
    }

    private fun parseSpokenNumber(text: String): Int? {

        val words = text.lowercase(Locale.getDefault()).split(" ")

        val numberMap = mapOf(
            "zero" to 0,
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "six" to 6,
            "seven" to 7,
            "eight" to 8,
            "nine" to 9,
            "ten" to 10,
            "eleven" to 11,
            "twelve" to 12,
            "thirteen" to 13,
            "fourteen" to 14,
            "fifteen" to 15,
            "sixteen" to 16,
            "seventeen" to 17,
            "eighteen" to 18,
            "nineteen" to 19,
            "twenty" to 20,
            "thirty" to 30,
            "forty" to 40,
            "fifty" to 50,
            "sixty" to 60,
            "seventy" to 70,
            "eighty" to 80,
            "ninety" to 90
        )

        var total = 0
        var current = 0

        for (word in words) {

            when (word) {

                "hundred" -> current *= 100

                "thousand" -> {
                    total += current * 1000
                    current = 0
                }

                else -> {
                    val value = numberMap[word]
                    if (value != null) {
                        current += value
                    }
                }
            }
        }

        total += current

        return if (total > 0) total else null
    }

    inner class DrawView : View(this) {

        private val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 55f            // ← critical change
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND  // ← makes digits blob-like
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        private val path = Path()
        private val points = mutableListOf<PointF>()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT)

            // Show strokes only when entering amount
            if (screen == Screen.AMOUNT) {
                canvas.drawPath(path, paint)
            }
        }

        fun getBitmap(): Bitmap {
            // Create tight bitmap same size as view
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // IMPORTANT: draw ONLY path, not background UI
            canvas.drawColor(Color.WHITE)
            val oldColor = paint.color
            paint.color = Color.BLACK
            canvas.drawPath(path, paint)
            paint.color = oldColor

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
                        isAwaitingInput = false
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

        val width = right - left
        val height = bottom - top

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

    private fun speakAndThen(text: String, onDone: () -> Unit) {

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")

        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    onDone()
                }
            }

            override fun onError(utteranceId: String?) {}
        })
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

    private fun startVoiceInput() {

        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        intent.putExtra(
            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            android.speech.RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        intent.putExtra(
            android.speech.RecognizerIntent.EXTRA_PROMPT,
            "Speak amount"
        )

        try {
            startActivityForResult(intent, 200)
        } catch (e: Exception) {
            speak("Voice input not supported")
        }
    }
}
