package com.tinklet.bharatdatingapp.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tinklet.bharatdatingapp.R

object SoundManager {
    private var soundPool: SoundPool? = null
    private var likeSoundId: Int = 0
    private var superlikeSoundId: Int = 0
    private var rejectSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var isLoaded = false
    var isSoundEnabled: Boolean = true

    fun init(context: Context) {
        if (isLoaded) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.let { pool ->
            likeSoundId = pool.load(context, R.raw.like_sound, 1)
            superlikeSoundId = pool.load(context, R.raw.superlike_sound, 1)
            rejectSoundId = pool.load(context, R.raw.reject_sound, 1)
            // Note: If you have a specific click sound, load it here. 
            // Otherwise we can use system sounds for navigation.
            isLoaded = true
        }
    }

    fun playLike() {
        if (!isSoundEnabled) return
        soundPool?.play(likeSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playSuperLike() {
        if (!isSoundEnabled) return
        soundPool?.play(superlikeSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playReject() {
        if (!isSoundEnabled) return
        soundPool?.play(rejectSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playClick() {
        if (!isSoundEnabled) return
        // Fallback to like sound or system click if dedicated click sound not provided
        if (clickSoundId != 0) {
            soundPool?.play(clickSoundId, 0.5f, 0.5f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
