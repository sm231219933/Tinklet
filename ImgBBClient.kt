package com.tinklet.bharatdatingapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ImgBBService {
    @Multipart
    @POST("1/upload")
    suspend fun uploadImage(
        @Query("key") apiKey: String,
        @Part image: MultipartBody.Part
    ): Response<ImgBBResponse>
}

data class ImgBBResponse(
    val data: ImgBBData,
    val success: Boolean,
    val status: Int
)

data class ImgBBData(
    val url: String,
    val display_url: String
)

object ImgBBClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.imgbb.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: ImgBBService = retrofit.create(ImgBBService::class.java)
}
