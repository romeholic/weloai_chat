// AIChatResponse.kt
package com.welo.aichat.network

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

// 主响应类
@Serializable
data class AIChatResponse(
    @Contextual val session_id: UUID,
    val outputs: List<Output>
)

// 输出层
@Serializable
data class Output(
    val inputs: Inputs,
    val outputs: List<OutputDetail>
)

// 输入信息
@Serializable
data class Inputs(
    val input_value: String
)

// 输出详情
@Serializable
data class OutputDetail(
    val results: Results,
    val artifacts: Artifacts,
    val outputs: OutputsData,
    val logs: Logs,
    val messages: List<Message>,
    val timedelta: Any? = null,
    val duration: Any? = null,
    val component_display_name: String,
    val component_id: String,
    val used_frozen_result: Boolean
)

// 结果数据
@Serializable
data class Results(
    val message: MessageData
)

// 消息数据（核心内容）
@Serializable
data class MessageData(
    val text_key: String,
    val data: MessageDataDetails,
    val default_value: String,
    val text: String,
    val sender: String,
    val sender_name: String,
    val files: List<Any>,
    val session_id: UUID,
    val timestamp: String,
    val flow_id: UUID,
    val error: Boolean,
    val edit: Boolean,
    val properties: Properties,
    val category: String,
    val content_blocks: List<ContentBlock>,
    val id: UUID,
    val duration: Any? = null
)

// 消息数据详情
@Serializable
data class MessageDataDetails(
    val timestamp: String,
    val sender: String,
    val sender_name: String,
    val session_id: UUID,
    val text: String,
    val files: List<Any>,
    val error: Boolean,
    val edit: Boolean,
    val properties: Properties,
    val category: String,
    val content_blocks: List<ContentBlock>,
    val id: UUID,
    val flow_id: UUID,
    val duration: Any? = null
)

// 消息属性
@Serializable
data class Properties(
    val text_color: String,
    val background_color: String,
    val edited: Boolean,
    val source: Source,
    val icon: String,
    val allow_markdown: Boolean,
    val positive_feedback: Any? = null,
    val state: String,
    val targets: List<Any>
)

// 消息来源
@Serializable
data class Source(
    val id: String,
    val display_name: String,
    val source: String
)

// 内容块
@Serializable
data class ContentBlock(
    val title: String,
    val contents: List<Content>,
    val allow_markdown: Boolean,
    val media_url: Any? = null
)

// 内容项
@Serializable
data class Content(
    val type: String,
    val duration: Int,
    val header: Header,
    val text: String
)

// 内容头部
@Serializable
data class Header(
    val title: String,
    val icon: String
)

// 工件数据
@Serializable
data class Artifacts(
    val message: String,
    val sender: String,
    val sender_name: String,
    val files: List<Any>,
    val type: String
)

// 输出数据
@Serializable
data class OutputsData(
    val message: MessageOutput
)

// 输出消息
@Serializable
data class MessageOutput(
    val message: String,
    val type: String
)

// 日志数据
@Serializable
data class Logs(
    val message: List<Any>
)

// 消息项
@Serializable
data class Message(
    val message: String,
    val sender: String,
    val sender_name: String,
    val session_id: UUID,
    val stream_url: Any? = null,
    val component_id: String,
    val files: List<Any>,
    val type: String
)