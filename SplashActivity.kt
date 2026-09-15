package com.tinklet.bharatdatingapp.calling

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.tinklet.bharatdatingapp.MainActivity
import com.tinklet.bharatdatingapp.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.splashVideoView)
        val resId = resources.getIdentifier("splash_video", "raw", packageName)
        
        if (resId != 0) {
            val videoPath = "android.resource://" + packageName + "/" + resId
            try {
                videoView.setVideoURI(Uri.parse(videoPath))
                videoView.setOnCompletionListener {
                    startMainActivity()
                }
                videoView.start()
            } catch (e: Exception) {
                startMainActivity()
            }
        } else {
            // Video missing, just wait 3 seconds and start
            videoView.postDelayed({
                startMainActivity()
            }, 3000)
        }

        // Fallback timer
        videoView.postDelayed({
            startMainActivity()
        }, 5000)
    }

    private var started = false
    private fun startMainActivity() {
        if (started) return
        started = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
