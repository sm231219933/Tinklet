package com.tinklet.bharatdatingapp.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceNoteRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0

    fun startRecording(): File {
        val fileName = "voice_note_${System.currentTimeMillis()}.m4a"
        val file = File(context.cacheDir, fileName)
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        startTime = System.currentTimeMillis()
        return file
    }

    /**
     * Returns Pair(recorded file, duration in seconds)
     */
    fun stopRecording(): Pair<File?, Int> {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null

            val durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            Pair(outputFile, durationSeconds)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // Recording bahut chhoti thi, ignore karo
        }
        recorder = null
        outputFile?.delete()
    }
}
