package com.welo.aichat.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.welo.aichat.data.ChatMessage
import com.welo.aichat.data.MessageType
import com.welo.aichat.network.AIChatRequest
import com.welo.aichat.network.AIChatResponse
import com.welo.aichat.network.ChatInput
import com.welo.aichat.network.RetrofitClient
import com.welo.aichat.network.Tweaks
import com.welo.aichat.network.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.io.File
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class ChatViewModel : ViewModel() {
    // 聊天消息列表的状态流
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val sessionId = UUID.randomUUID().toString()
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    fun sendMessageToAI(userInput: String) {
        if (userInput.isBlank()) return

        // 先添加用户消息到列表
        val newMessages = _chatMessages.value.toMutableList()
        newMessages.add(ChatMessage(content = userInput, isUser = true))
        _chatMessages.value = newMessages

        // 发起网络请求调用 AI 接口
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("ChatDebug", "0################开始请求: $userInput，时间: ${System.currentTimeMillis()}")
            try {
                val request = AIChatRequest(input_value = userInput, session_id = sessionId, tweaks = null)

                val response = RetrofitClient.aiChatApi.sendMessage(request)
                android.util.Log.d("ChatDebug", "1################收到响应: ${response.outputs}，时间: ${System.currentTimeMillis()}")

                // 回到主线程更新状态
                withContext(Dispatchers.Main) {
                    // 从复杂响应中提取AI回复文本
                    val aiMessage = extractAIMessage(response)

                    // 添加 AI 回复消息到列表
                    _chatMessages.value = _chatMessages.value + ChatMessage(content = aiMessage, isUser = false)
                    android.util.Log.d("ChatDebug", "更新状态: 新增 AI 回复，列表长度: ${newMessages.size}")
                }

            } catch (e: Exception) {
                // 网络请求失败时，添加错误提示消息
                android.util.Log.e("ChatDebug", "请求失败: ${e.message}")

                withContext(Dispatchers.Main) {
                    newMessages.add(ChatMessage(content = "请求失败，请重试：${e.message}", isUser = false))
                    _chatMessages.value = newMessages
                }

            }
        }
    }

    private fun extractAIMessage(response: AIChatResponse): String {
        // 从复杂的响应结构中提取实际的AI回复文本
        return try {
            // 尝试从最可能的位置提取文本,回复文本主要存在于messages.message字段中
            response.outputs.firstOrNull()?.outputs?.firstOrNull()?.messages?.firstOrNull()?.message ?:
            "AI暂无回复"
        } catch (e: Exception) {
            android.util.Log.e("AI_ERROR", "提取AI消息失败", e)
            "AI回复解析失败"
        }
    }

    // 处理图片消息
    fun sendImage(imageUri: Uri, context: Context, onError: (String) -> Unit) {
        // 添加正在发送的消息
        val newMessages = _chatMessages.value.toMutableList()
        newMessages.add(
            ChatMessage(
                content = "正在发送图片...",
                isUser = true,
                type = MessageType.IMAGE,
                imageUrl = imageUri.toString()
            )
        )
        _chatMessages.value = newMessages

        // 上传图片到服务器并获取 URL
        viewModelScope.launch {
            try {
                // 将 Uri 转换为 File 或字节数组
                val imageFile = uriToFile(imageUri, context)
                val uploadResponse = uploadImage(imageFile)
                //val imageUrl = uploadResponse.file_path
                val imageUrl = "http://192.168.111.10:7860/files/${uploadResponse.file_path}"
                android.util.Log.e("liutuo", "uploadImage return imageUrl:$imageUrl")
                // 更新消息状态
                val updatedMessages = _chatMessages.value.toMutableList()
                val index = updatedMessages.indexOfLast { it.isUser && it.type == MessageType.IMAGE }
                if (index >= 0) {
                    updatedMessages[index] = updatedMessages[index].copy(
                        content = "图片已发送",
                        imageUrl = imageUrl
                    )
                    _chatMessages.value = updatedMessages
                }

                // 构建 chat 接口的请求体
                val chatRequest = AIChatRequest(
                    input_value = "请详细描述下这个图片",
                    output_type = "chat",
                    input_type = "chat",
                    session_id = sessionId,
                    tweaks = Tweaks(
                        ChatInput(files = uploadResponse.file_path)
                    )
                )

                // 调用 chat 接口
                val chatResponse = RetrofitClient.aiChatApi.sendMessage(chatRequest)

                // 回到主线程更新状态
                withContext(Dispatchers.Main) {
                    // 从复杂响应中提取AI回复文本
                    val aiMessage = extractAIMessage(chatResponse)

                    // 添加 AI 回复消息到列表
                    _chatMessages.value = _chatMessages.value + ChatMessage(content = aiMessage, isUser = false)
                }
            } catch (e: Exception) {
                // 处理错误
                updateLastMessage("图片发送失败: ${e.message}")
                onError(e.message ?: "未知错误")
            }
        }
    }

    // 更新消息
    fun updateMessages(messages: List<ChatMessage>) {
        _chatMessages.value = messages
    }

    // 处理语音消息
    fun sendAudio(audioUri: Uri, context: Context) {
        // 添加正在发送的消息
        val newMessages = _chatMessages.value.toMutableList()
        newMessages.add(
            ChatMessage(
                content = "正在发送语音...",
                isUser = true,
                type = MessageType.AUDIO,
                audioUrl = audioUri.toString()
            )
        )
        _chatMessages.value = newMessages

        // 上传语音并转换为文本
        viewModelScope.launch {
            try {
                val audioFile = uriToFile(audioUri, context)
                val text = transcribeAudio(audioFile)

                // 更新消息状态
                val updatedMessages = _chatMessages.value.toMutableList()
                val index = updatedMessages.indexOfLast { it.isUser && it.type == MessageType.AUDIO }
                if (index >= 0) {
                    updatedMessages[index] = updatedMessages[index].copy(
                        content = "语音已发送",
                        audioUrl = audioFile.absolutePath
                    )
                    _chatMessages.value = updatedMessages
                }

                // 发送文本给 AI
                sendMessageToAI("用户语音输入: $text")
            } catch (e: Exception) {
                // 处理错误
                updateLastMessage("语音发送失败: ${e.message}")
            }
        }
    }

    // 辅助方法
    private fun updateLastMessage(newContent: String) {
        val messages = _chatMessages.value.toMutableList()
        messages.lastOrNull()?.let {
            messages[messages.size - 1] = it.copy(content = newContent)
            _chatMessages.value = messages
        }
    }

    // 将 Uri 转换为 File
    private suspend fun uriToFile(uri: Uri, context: Context): File {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            // 获取原文件的扩展名
            val extension = context.contentResolver.getType(uri)?.substringAfterLast("/")
            val fileName = if (extension != null) {
                "temp_file.$extension"
            } else {
                "temp_file"
            }
            val file = File(context.cacheDir, fileName)
            file.outputStream().use { output ->
                inputStream?.copyTo(output)
            }
            file
        }
    }


    // 上传图片到服务器
    private suspend fun uploadImage(file: File): UploadResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val response: Call<UploadResponse> = RetrofitClient.uploadApi.uploadImage(body)
                val result = response.execute()
                if (result.isSuccessful) {
                    val uploadResponse = result.body()
                    if (uploadResponse != null) {
                        return@withContext uploadResponse
                    } else {
                        throw Exception("服务器响应为空")
                    }
                } else {
                    throw Exception("图片上传失败: ${result.message()}")
                }
            } catch (e: Exception) {
                throw Exception("图片上传失败: ${e.message}")
            }
        }
    }


    // 语音转文本
    private suspend fun transcribeAudio(file: File): String {
        // 实现语音转文本逻辑
        // 可以调用语音识别 API，如 Google Speech-to-Text
        return "这是一段语音转文本的示例"
    }
}