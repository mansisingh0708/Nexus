package com.example.hehehe

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        val appName = findViewById<TextView>(R.id.appName)
        val tagline = findViewById<TextView>(R.id.tagline)

        val fade = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        appName.startAnimation(fade)
        tagline.startAnimation(fade)

        Handler(Looper.getMainLooper()).postDelayed({

            startActivity(Intent(this, MainActivity::class.java))
            finish()

        }, 2000)
    }
}
