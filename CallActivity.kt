package com.tinklet.bharatdatingapp.calling

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tinklet.bharatdatingapp.R
import com.tinklet.bharatdatingapp.data.local.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

class CallActivity : AppCompatActivity(), SignalingClient.SignalingListener, WebRtcClient.WebRtcListener {

    companion object {
        const val EXTRA_TARGET_ID = "otherUserId"
        const val EXTRA_TARGET_NAME = "otherUserName"
        const val EXTRA_IS_CALLER = "isCaller"
        const val EXTRA_OFFER_SDP = "offerSdp"
        const val EXTRA_CALL_TYPE = "callType"

        private const val SIGNALING_SERVER_URL = "ws://15.206.14.213:4001"
    }

    private lateinit var otherUserId: String
    private lateinit var otherUserName: String
    private var isCaller: Boolean = true
    private var callType: String = "audio"

    private lateinit var webRtcClient: WebRtcClient
    private var signalingClient: SignalingClient? = null

    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var statusText: TextView
    private lateinit var callerNameText: TextView

    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_call)

        val root = findViewById<View>(R.id.call_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        otherUserId = intent.getStringExtra(EXTRA_TARGET_ID) ?: ""
        otherUserName = intent.getStringExtra(EXTRA_TARGET_NAME) ?: "User"
        isCaller = intent.getBooleanExtra(EXTRA_IS_CALLER, true)
        callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"

        if (otherUserId.isBlank()) {
            finish()
            return
        }

        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        statusText = findViewById(R.id.statusText)
        callerNameText = findViewById(R.id.callerNameText)

        callerNameText.text = otherUserName

        if (callType == "audio") {
            localVideoView.visibility = View.GONE
            remoteVideoView.visibility = View.GONE
        }

        setupSignalingAndWebRtc()
        setupControlButtons()
    }

    private fun setupSignalingAndWebRtc() {
        lifecycleScope.launch {
            val db = com.tinklet.bharatdatingapp.data.local.AppDatabase.getDatabase(this@CallActivity)
            val me = db.profileDao().getMyProfile()
            val myId = me?.email ?: "unknown"
            val myName = me?.name ?: "User"
            
            // Re-init signaling for this activity to ensure listener is correct
            signalingClient = SignalingClient(SIGNALING_SERVER_URL, myId, this@CallActivity)
            signalingClient?.connect()
            CallSignalingHolder.signalingClient = signalingClient

            webRtcClient = WebRtcClient(
                context = this@CallActivity,
                localVideoView = if (callType == "video") localVideoView else null,
                remoteVideoView = if (callType == "video") remoteVideoView else null,
                listener = this@CallActivity
            )
            webRtcClient.initialize(isVideoCall = (callType == "video"))

            if (isCaller) {
                statusText.text = "Calling..."
                webRtcClient.createOffer { sdp ->
                    signalingClient?.sendOffer(otherUserId, myName, sdp.description, callType)
                }
            } else {
                statusText.text = "Connecting..."
                answerIncomingCall()
            }
        }
    }

    private fun setupControlButtons() {
        findViewById<ImageButton>(R.id.muteButton).setOnClickListener {
            isMuted = !isMuted
            webRtcClient.toggleMute(isMuted)
            (it as ImageButton).setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode
            )
        }

        findViewById<ImageButton>(R.id.endCallButton).setOnClickListener {
            endCall()
        }

        if (callType == "video") {
            findViewById<ImageButton>(R.id.switchCameraButton).apply {
                visibility = View.VISIBLE
                setOnClickListener { webRtcClient.switchCamera() }
            }
        }
    }

    private fun startOutgoingCall() {
        webRtcClient.createOffer { sdp ->
            signalingClient?.sendOffer(otherUserId, "User", sdp.description, callType)
        }
    }

    private fun answerIncomingCall() {
        val offerSdpString = intent.getStringExtra(EXTRA_OFFER_SDP) ?: return
        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdpString)

        webRtcClient.setRemoteDescription(offerSdp)
        webRtcClient.createAnswer { sdp ->
            signalingClient?.sendAnswer(otherUserId, sdp.description)
        }
    }

    private fun endCall() {
        signalingClient?.sendEndCall(otherUserId)
        webRtcClient.close()
        finish()
    }

    // Signaling Listener
    override fun onIncomingCall(fromUserId: String, fromUserName: String, offer: String, callType: String) {}

    override fun onCallAnswered(answer: String) {
        runOnUiThread {
            statusText.text = "Connected"
            val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, answer)
            webRtcClient.setRemoteDescription(answerSdp)
        }
    }

    override fun onCallRejected() {
        runOnUiThread {
            statusText.text = "Rejected"
            finish()
        }
    }

    override fun onCallEnded() {
        runOnUiThread {
            webRtcClient.close()
            finish()
        }
    }

    override fun onIceCandidateReceived(candidate: String) {
        val iceCandidate = IceCandidate("0", 0, candidate)
        webRtcClient.addIceCandidate(iceCandidate)
    }

    override fun onCallFailed(reason: String) {
        runOnUiThread {
            statusText.text = "Failed: $reason"
            finish()
        }
    }

    override fun onConnected() {}

    // WebRTC Listener
    override fun onLocalIceCandidate(candidate: IceCandidate) {
        signalingClient?.sendIceCandidate(otherUserId, candidate.sdp)
    }

    override fun onRemoteStreamAdded() {
        runOnUiThread { statusText.text = "Connected" }
    }

    override fun onDestroy() {
        super.onDestroy()
        webRtcClient.close()
    }
}
