package com.welo.aichat.network

import retrofit2.http.Body
import retrofit2.http.POST

interface AIChatApi {
    /**
     * 模拟 AI 聊天接口
     */
    @POST("api/v1/run/e3e07c37-49d7-44b7-be70-1ed17ea44851?stream=false") // 发送聊天信息
    suspend fun sendMessage(@Body request: AIChatRequest): AIChatResponse
}