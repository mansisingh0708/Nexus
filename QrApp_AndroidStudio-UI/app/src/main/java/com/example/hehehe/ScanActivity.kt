package com.example.hehehe

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.graphics.Rect
import android.content.Intent



@androidx.camera.core.ExperimentalGetImage
class ScanActivity : AppCompatActivity() {

    private var isLocked = false
    private var steadyCount = 0
    private var validFrameCount = 0
    private var lastLockTime = 0L
    private val MIN_QR_RATIO = 0.32f     // QR must occupy 32% width
    private val MAX_QR_RATIO = 0.92f     // not too close
    private var lastInstruction: Instruction? = null
    private var notFoundCount = 0
    private var stableCount = 0
    private var lastSpoken: Instruction? = null
    private lateinit var tts: TextToSpeech
    private val CAMERA_REQUEST = 100
    lateinit var guidanceEngine: GuidanceEngine
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_scan)

        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts.language = java.util.Locale.US
            }
        }


        checkCameraPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_REQUEST) {

            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                // USER JUST ALLOWED → START CAMERA NOW
                startCamera()
            } else {
                Toast.makeText(this,
                    "Camera permission required",
                    Toast.LENGTH_LONG).show()
            }
        }
    }


    fun checkCameraPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_REQUEST
            )

        } else {
            startCamera()
        }

    }

    fun startCamera() {

        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)

        val cameraProviderFuture =
            androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            // ----- PREVIEW -----
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // ----- QR ANALYZER -----
            val analysis = androidx.camera.core.ImageAnalysis.Builder().build()

            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()

            analysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->

                val mediaImage = imageProxy.image
                guidanceEngine = GuidanceEngine(
                    imageProxy.height,
                    imageProxy.width
                )
                if (mediaImage != null) {

                    val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (isLocked) return@addOnSuccessListener

                            val targetBarcode = barcodes.maxByOrNull { barcode ->
                                val box = barcode.boundingBox
                                if (box != null) box.width() * box.height() else 0
                            }

                            val box = targetBarcode?.boundingBox
                            val instruction = guidanceEngine.decide(box)
                            android.util.Log.d("CHECK_ALIGN", instruction.toString())
                            // --- NOT FOUND FILTER ---
                            if (instruction == Instruction.NOT_FOUND) {
                                notFoundCount++
                                if (notFoundCount < 20) return@addOnSuccessListener
                            } else {
                                notFoundCount = 0
                            }

                            // --- LOCK CHECK ---
                            android.util.Log.d("DEBUG_LOCK",
                                "instruction=$instruction  isLocked=$isLocked")
                            if (!isLocked && instruction == Instruction.ALIGNED) {
                                android.util.Log.d("LOCK_COUNT", steadyCount.toString())
                                steadyCount++
                                if (steadyCount >= 8) {

                                    isLocked = true
                                    speak("QR Locked")
                                    vibrate()

                                    // 1. Create result container
                                    val resultIntent = Intent()

                                    // 2. Put QR value inside it
                                    resultIntent.putExtra("qr_value", targetBarcode?.rawValue ?: "")

                                    // 3. Send result to MainActivity
                                    setResult(RESULT_OK, resultIntent)

                                    // 4. Close camera and go back
                                    finish()

                                    return@addOnSuccessListener
                                }
                            } else if (instruction != Instruction.ALIGNED) {
                                steadyCount = 0
                            }

                            // --- STABILITY FILTER ---
                            if (instruction == lastInstruction) {
                                stableCount++
                            } else {
                                stableCount = 1
                            }
                            lastInstruction = instruction

                            if (stableCount < 3) return@addOnSuccessListener

                            // --- NORMAL GUIDANCE ---
                            if (!isLocked) {
                                speak(instruction.speech)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }



                        .addOnCompleteListener {
                            imageProxy.close()
                        }


                }else {
                    imageProxy.close()
                }
            }

            val cameraSelector =
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    fun vibrate() {

        val vibrator =
            getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            val effect = android.os.VibrationEffect.createOneShot(
                150,
                android.os.VibrationEffect.DEFAULT_AMPLITUDE
            )

            vibrator.vibrate(effect)

        } else {
            vibrator.vibrate(150)
        }
    }
    private var lastSpeech = ""
    private var lastSpeechTime = 0L
    private var sameCount = 0

    fun speak(text: String) {

        val now = System.currentTimeMillis()

        val MIN_GAP = when (text) {
            "QR Locked" -> 4000
            "QR not found" -> 3500
            else -> 1200
        }

        if (text != "QR Locked" && now - lastSpeechTime < MIN_GAP) return

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )

        lastSpeechTime = now
    }

    }


