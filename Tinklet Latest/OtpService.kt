package com.tinklet.bharatdatingapp.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OtpApi {
    @POST("send-otp")
    suspend fun sendOtp(@Body request: OtpRequest): Response<Unit>

    @POST("send-email-otp")
    suspend fun sendEmailOtp(@Body request: EmailOtpRequest): Response<Unit>
}

@JsonClass(generateAdapter = true)
data class OtpRequest(
    val phoneNumber: String,
    val otp: String
)

@JsonClass(generateAdapter = true)
data class EmailOtpRequest(
    val email: String,
    val otp: String
)

object OtpClient {
    private const val BASE_URL = "http://15.252.204.160:4000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: OtpApi = retrofit.create(OtpApi::class.java)
}
