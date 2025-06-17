package com.welo.aichat.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UploadResponse(
    val flowId: String,
    val file_path: String
)

interface UploadApi {
    @Multipart
    @POST("/api/v1/files/upload/e3e07c37-49d7-44b7-be70-1ed17ea44851") // 上传图片
    fun uploadImage(@Part image: MultipartBody.Part): Call<UploadResponse>
}