package com.tinklet.bharatdatingapp.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

object InAppChatGuard {
    private val badWords = listOf("porn", "sex", "nude", "gaali1", "gaali2", "abuse", "scam", "fraud")

    fun isMessageSafe(text: String): Boolean {
        val lowerText = text.lowercase()
        return badWords.none { lowerText.contains(it) }
    }

    suspend fun isImageSafe(bitmap: Bitmap): Boolean {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        
        return try {
            val labels = labeler.process(image).await()
            // Generic ML Kit might not have explicit 'nudity' labels without custom models,
            // but we can look for suspicious categories or use this as a base for custom TFLite.
            // For now, if it detects 'Person' and 'Clothing' is missing or low confidence, we can flag.
            val labelsText = labels.joinToString { it.text.lowercase() }
            
            // Placeholder: In a real app, you'd use a specific TFLite model for Nudity.
            // ML Kit labeling is free and runs on-device.
            !labelsText.contains("nudity") && !labelsText.contains("explicit")
        } catch (e: Exception) {
            false
        }
    }
}
