package com.welo.aichat.data

import java.util.UUID

/**
 * 聊天消息数据类
 * @param content 消息内容
 * @param isUser 是否是用户发送的消息
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String?,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val imageUrl: String? = null,  // 图片 URL
    val audioUrl: String? = null   // 语音 URL
)

enum class MessageType {
    TEXT, IMAGE, AUDIO
}