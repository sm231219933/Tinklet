package com.tinklet.bharatdatingapp.calling

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tinklet.bharatdatingapp.R
import com.tinklet.bharatdatingapp.utils.NotificationHelper

class IncomingCallActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val EXTRA_FROM_USER_ID = "fromUserId"
        const val EXTRA_FROM_USER_NAME = "fromUserName"
        const val EXTRA_OFFER_SDP = "offerSdp"
        const val EXTRA_CALL_TYPE = "callType"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_incoming_call)

        val root = findViewById<View>(R.id.incoming_call_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fromUserId = intent.getStringExtra(EXTRA_FROM_USER_ID) ?: return
        val fromUserName = intent.getStringExtra(EXTRA_FROM_USER_NAME) ?: "Unknown"
        val offerSdp = intent.getStringExtra(EXTRA_OFFER_SDP) ?: return
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"

        findViewById<TextView>(R.id.callerNameText).text = fromUserName
        findViewById<TextView>(R.id.callTypeText).text =
            if (callType == "video") "Incoming Video Call..." else "Incoming Voice Call..."

        NotificationHelper.cancelCallNotification(this)
        startRingtoneAndVibration()

        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            stopRingtoneAndVibration()
            NotificationHelper.cancelCallNotification(this)

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra(CallActivity.EXTRA_TARGET_ID, fromUserId)
                putExtra(CallActivity.EXTRA_TARGET_NAME, fromUserName)
                putExtra(CallActivity.EXTRA_IS_CALLER, false)
                putExtra(CallActivity.EXTRA_OFFER_SDP, offerSdp)
                putExtra(CallActivity.EXTRA_CALL_TYPE, callType)
            }
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.rejectButton).setOnClickListener {
            stopRingtoneAndVibration()
            NotificationHelper.cancelCallNotification(this)
            CallSignalingHolder.signalingClient?.sendReject(fromUserId)
            finish()
        }
    }

    private fun startRingtoneAndVibration() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()

            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 1000, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtoneAndVibration() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
    }
}
