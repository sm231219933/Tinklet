package com.tinklet.bharatdatingapp.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    private val context: Context,
    private val myUserId: String,
    private val observer: PeerConnection.Observer
) {
    private val db = FirebaseFirestore.getInstance()
    private val peerConnectionFactory: PeerConnectionFactory
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-IntelVP8/Enabled/")
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    // Signaling: Start Call (Caller side)
    fun callUser(targetUserId: String, sdp: SessionDescription) {
        val callData = mapOf(
            "offer" to sdp.description,
            "callerId" to myUserId,
            "type" to sdp.type.canonicalForm(),
            "status" to "RINGING",
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("calls").document(targetUserId).set(callData)
            .addOnFailureListener { Log.e("WebRTC", "Signaling failed", it) }
    }

    // Signaling: Answer Call (Receiver side)
    fun answerCall(callerId: String, sdp: SessionDescription) {
        val answerData = mapOf(
            "answer" to sdp.description,
            "status" to "CONNECTED"
        )
        db.collection("calls").document(myUserId).update(answerData)
    }

    // Signaling: Send ICE Candidate
    fun sendIceCandidate(targetUserId: String, candidate: IceCandidate, isCaller: Boolean) {
        val candidateData = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "senderId" to myUserId
        )
        val docId = if (isCaller) targetUserId else targetUserId // Both write to target's sub-collection
        db.collection("calls").document(docId)
            .collection("candidates").add(candidateData)
    }

    fun createAudioSource(constraints: MediaConstraints): AudioSource {
        return peerConnectionFactory.createAudioSource(constraints)
    }

    fun createVideoSource(isScreenshot: Boolean): VideoSource {
        return peerConnectionFactory.createVideoSource(isScreenshot)
    }
}
