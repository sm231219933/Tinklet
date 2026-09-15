package com.tinklet.bharatdatingapp.calling

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * WebRTC peer connection ko manage karta hai — audio, video,
 * offer/answer generation, ICE candidates.
 */
class WebRtcClient(
    private val context: Context,
    private val localVideoView: SurfaceViewRenderer?, // local preview
    private val remoteVideoView: SurfaceViewRenderer?, // dusre user ki video
    private val listener: WebRtcListener
) {
    interface WebRtcListener {
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onRemoteStreamAdded()
    }

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private val eglBase = EglBase.create()

    // Free STUN (Google) + Free TURN (Open Relay Project)
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    fun initialize(isVideoCall: Boolean) {
        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        localVideoView?.init(eglBase.eglBaseContext, null)
        remoteVideoView?.init(eglBase.eglBaseContext, null)

        createPeerConnection()
        setupLocalMedia(isVideoCall)
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    listener.onLocalIceCandidate(candidate)
                }

                override fun onAddStream(stream: MediaStream) {
                    stream.videoTracks.firstOrNull()?.addSink(remoteVideoView)
                    listener.onRemoteStreamAdded()
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d("WebRtcClient", "ICE state: $state")
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            }
        )
    }

    private fun setupLocalMedia(isVideoCall: Boolean) {
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)

        val localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream.addTrack(localAudioTrack)

        if (isVideoCall) {
            videoCapturer = createCameraCapturer()
            val videoSource = peerConnectionFactory.createVideoSource(false)
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", eglBase.eglBaseContext
            )
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource)
            localVideoTrack?.addSink(localVideoView)
            localStream.addTrack(localVideoTrack)
        }

        peerConnection?.addStream(localStream)
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Pehle front camera dhoondo
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // Agar front na mile to koi bhi camera
        for (deviceName in deviceNames) {
            return enumerator.createCapturer(deviceName, null)
        }
        return null
    }

    fun createOffer(onSuccess: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onSuccess(sdp)
            }
        }, constraints)
    }

    fun createAnswer(onSuccess: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onSuccess(sdp)
            }
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            peerConnection?.close()
            localVideoView?.release()
            remoteVideoView?.release()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error closing client", e)
        }
    }
}

/**
 * SdpObserver ke saare methods implement karne se bachne ke liye
 * ek simple adapter class — sirf jo chahiye wo override karo.
 */
open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) { Log.e("SdpObserver", "Create failed: $error") }
    override fun onSetFailure(error: String) { Log.e("SdpObserver", "Set failed: $error") }
}
