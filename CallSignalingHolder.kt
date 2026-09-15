package com.tinklet.bharatdatingapp.calling

/**
 * App-wide signaling connection ko hold karta hai, taaki
 * background mein bhi incoming calls receive ho sakein.
 */
object CallSignalingHolder {
    var signalingClient: SignalingClient? = null
}
