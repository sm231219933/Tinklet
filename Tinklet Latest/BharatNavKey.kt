package com.tinklet.bharatdatingapp.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface BharatNavKey : NavKey {
    @Serializable
    data object MobileInput : BharatNavKey
    
    @Serializable
    data class OtpVerification(val phoneNumber: String) : BharatNavKey
    
    @Serializable
    data object EmailInput : BharatNavKey

    @Serializable
    data class EmailOtpVerification(val email: String) : BharatNavKey

    @Serializable
    data class Registration(
        val phoneNumber: String? = null,
        val email: String? = null,
        val name: String? = null
    ) : BharatNavKey

    @Serializable
    data class PhotoCapture(val isUpdate: Boolean = false) : BharatNavKey

    @Serializable
    data object Discovery : BharatNavKey

    @Serializable
    data object GeoRestriction : BharatNavKey

    @Serializable
    data object Requests : BharatNavKey

    @Serializable
    data object Matches : BharatNavKey

    @Serializable
    data object Conversations : BharatNavKey

    @Serializable
    data object Profile : BharatNavKey

    @Serializable
    data object Subscription : BharatNavKey

    @Serializable
    data object EditProfile : BharatNavKey

    @Serializable
    data object MyPhotos : BharatNavKey

    @Serializable
    data object CoinCenter : BharatNavKey

    @Serializable
    data class ProfileDetail(val email: String) : BharatNavKey

    @Serializable
    data class Chat(val email: String) : BharatNavKey
}
