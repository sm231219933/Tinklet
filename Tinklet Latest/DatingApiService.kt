package com.tinklet.bharatdatingapp.data.remote

import com.tinklet.bharatdatingapp.data.local.UserProfile
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class CoinResponse(
    val success: Boolean = false,
    val coins: Int? = null,
    val error: String? = null

)
data class ReportUserRequest(
    val reportedEmail: String,
    val reason: String,
    val description: String = ""
)

data class ReportUserResponse(
    val success: Boolean = false,
    val reportId: String? = null,
    val createdAt: String? = null,
    val error: String? = null
)

data class MessageRequest(
    val matchId: String,
    val senderId: String,
    val text: String // 'content' ko badal kar 'text' kijiye
)
data class ChatSendRequest(
    val matchId: String,
    val text: String
)
interface DatingApiService {

    @POST("api/report")
    suspend fun reportUser(
        @Body request: ReportUserRequest
    ): Response<ReportUserResponse>

    @POST("api/coins/reward")
    suspend fun rewardCoins(): Response<CoinResponse>

    @POST("profile")
    suspend fun updateProfile(@Body profile: UserProfile): Response<Map<String, Boolean>>

    @GET("profiles")
    suspend fun getProfiles(@Query("userId") userId: String): Response<List<UserProfile>>

    @POST("like")
    suspend fun sendLike(@Body request: LikeRequest): Response<LikeResponse>

    @GET("likes/incoming")
    suspend fun getIncomingLikes(@Query("userId") userId: String): Response<List<LikeItem>>

    @POST("likes/respond")
    suspend fun respondToLike(@Body request: RespondRequest): Response<LikeResponse>

    @GET("matches")
    suspend fun getMatchesLegacy(@Query("userId") userId: String): Response<List<MatchEnriched>>

    @POST("messages/send")
    suspend fun sendMessage(@Body request: MessageRequest): Response<Map<String, Any>>


    @POST("profile/save")
    suspend fun saveProfileSecure(@Body profile: UserProfile): Response<Map<String, Any>>

    @POST("api/chat/send")
    suspend fun saveMessageSecure(@Body request: ChatSendRequest): Response<Map<String, Any>>

    @GET("api/chat/{matchId}")
    suspend fun getMessages(@Path("matchId") matchId: String): Response<Map<String, Any>>
    @POST("image/upload")
    suspend fun uploadImageSecure(@Body request: ImageUploadRequest): Response<Map<String, String>>

    @POST("safety/check")
    suspend fun checkSafetySecure(@Body request: SafetyCheckRequest): Response<Map<String, Boolean>>

    @POST("referral/credit")
    suspend fun creditReferralSecure(@Body request: ReferralRequest): Response<Map<String, Any>>

    @GET("phone/check")
    suspend fun checkPhoneSecure(@Query("phone") phone: String): Response<Map<String, Boolean>>

    @POST("report/save")
    suspend fun saveReportSecure(@Body request: ReportRequest): Response<Map<String, Any>>

    @POST("media/upload")
    suspend fun uploadMediaSecure(@Body request: MediaUploadRequest): Response<Map<String, String>>

    @POST("fcm/token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<Map<String, Any>>

    @GET("profile/get")
    suspend fun getProfileSecure(@Query("email") email: String): Response<UserProfile>

    @GET("profile/get/public")
    suspend fun getProfilePublic(@Query("email") email: String): Response<UserProfile>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @GET("api/swipe/feed")
    suspend fun getFeed(): Response<FeedResponse>

    @POST("api/swipe/action")
    suspend fun swipeAction(@Body request: SwipeActionRequest): Response<SwipeActionResponse>

    @GET("api/swipe/matches")
    suspend fun getMatches(): Response<MatchesResponse>

    @GET("api/sync/all")
    suspend fun syncAll(): Response<SyncAllResponse>

    @POST("api/account/deactivate")
    suspend fun deactivateAccount(): Response<Map<String, Boolean>>

    @POST("api/account/delete-request")
    suspend fun requestDeletion(): Response<Map<String, Boolean>>

    @POST("api/report")
    suspend fun reportUser(@Body request: ReportRequest): Response<Map<String, Boolean>>
}

data class SyncAllResponse(
    val user: UserProfile? = null,
    val sent: List<SwipeActionRecord> = emptyList(),
    val incomingLikes: List<SwipeActionRecord> = emptyList(),
    val incomingSuperlikes: List<SwipeActionRecord> = emptyList(),
    val incomingRejected: List<SwipeActionRecord> = emptyList(),
    val matches: List<Map<String, Any>> = emptyList()
)

data class SwipeActionRecord(
    val fromUserId: String = "",
    val toUserId: String = "",
    val action: String = "",
    val timestamp: Long = 0,
    val profile: UserProfile? = null
)

data class SignupRequest(
    val email: String,
    val password: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phoneNumber: String = "",
    val photoUri: String = "",
    val secondaryPhotos: List<String> = emptyList(),
    val country: String = "",
    val state: String = "",
    val bio: String = "",
    val religion: String = "",
    val habits: String = ""
)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val user: UserProfile)
data class SwipeActionRequest(val toUserId: String, val action: String)
data class SwipeActionResponse(
    val success: Boolean,
    val matched: Boolean? = false,
    val matchId: String? = null,
    val coins: Int? = null
)
data class FeedResponse(val feed: List<UserProfile>)
data class MatchesResponse(val matches: List<Map<String, Any>>)

data class MediaUploadRequest(val base64Data: String, val mediaType: String, val matchId: String)
data class ImageUploadRequest(val base64Image: String)
data class SafetyCheckRequest(val text: String?, val imageUrl: String?)
data class ReferralRequest(val code: String, val deviceId: String)
data class ReportRequest(val reporterEmail: String, val targetEmail: String, val reason: String = "")
data class FcmTokenRequest(val userId: String, val token: String)
data class LikeRequest(val fromUserId: String, val toUserId: String, val type: String)
data class LikeResponse(val success: Boolean, val isMatch: Boolean, val matchId: String?)
data class RespondRequest(val currentUserId: String, val otherUserId: String, val action: String)
data class LikeItem(val fromUserId: String, val toUserId: String, val type: String, val senderProfile: UserProfile?)
data class MatchEnriched(val matchId: String, val user1Id: String, val user2Id: String, val otherUser: UserProfile?)

data class RemoteMessage(val matchId: String, val timestamp: Long, val senderId: String, val content: String)

object RetrofitClient {
    private const val BASE_URL = "http://15.252.204.160:4000/"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("tinklet_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? = prefs.getString("jwt_token", null)

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val apiService: DatingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DatingApiService::class.java)
    }

    @Deprecated("Use init and saveToken", ReplaceWith("RetrofitClient.saveToken(token)"))
    fun setToken(token: String?) {
        token?.let { saveToken(it) }
    }
}
