package com.welo.aichat

import android.Manifest
import android.app.Activity
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.welo.aichat.data.ChatMessage
import com.welo.aichat.data.MessageType
import com.welo.aichat.ui.theme.Welo_aichatTheme
import com.welo.aichat.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

class MainActivity : ComponentActivity() {
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Welo_aichatTheme {
                ChatScreen()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
        val chatMessages by viewModel.chatMessages.collectAsState()
        var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // 图片选择结果处理
        val selectImageLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                viewModel.sendImage(it, context) { errorMessage ->
                    scope.launch {
                        snackbarHostState.showSnackbar(errorMessage)
                        // 移除正在发送的图片消息
                        val newMessages = chatMessages.toMutableList()
                        val index = newMessages.indexOfLast { msg -> msg.isUser && msg.type == MessageType.IMAGE }
                        if (index >= 0) {
                            newMessages.removeAt(index)
                            viewModel.updateMessages(newMessages)
                        }
                    }
                }
            }
        }

        // 相机拍照结果处理
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success) {
                // 处理拍摄的图片
            }
        }

        // 录音文件 URI
        var audioUri by remember { mutableStateOf<Uri?>(null) }

        // 开始录音
        fun startAudioRecording() {
            val file = File(context.cacheDir, "recording.3gp")
            audioUri = Uri.fromFile(file)

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)

                try {
                    prepare()
                    start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 语音录制结果处理
        val recordAudioLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            if (granted) {
                // 开始录音
                startAudioRecording()
            } else {
                // 显示权限被拒绝的提示
            }
        }

        // 停止录音并发送
        fun stopAudioRecording() {
            audioUri?.let { viewModel.sendAudio(it, context) }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { /* 顶部导航栏保持不变 */ },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 聊天消息列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = false,
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(chatMessages,  key = { message -> message.id }) { message ->
                        ChatMessageItem(message = message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // 输入区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图片选择按钮
                    IconButton(onClick = {
                        selectImageLauncher.launch("image/*")
                    }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "发送图片",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 语音输入按钮
                    IconButton(onClick = {
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "语音输入",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 文本输入框
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息...") },
                        singleLine = true
                    )

                    // 发送按钮
                    Button(
                        onClick = {
                            if (textFieldValue.text.isNotEmpty()) {
                                viewModel.sendMessageToAI(textFieldValue.text)
                                textFieldValue = TextFieldValue()
                            }
                        },
                        enabled = textFieldValue.text.isNotEmpty()
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }

    @Composable
    fun ChatMessageItem(message: ChatMessage) {
        android.util.Log.d("ChatDebug", "2################渲染消息: ${message.content}，类型: ${message.type}")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                when (message.type) {
                    MessageType.TEXT -> {
                        Text(
                            text = message.content?:"",
                            modifier = Modifier.padding(8.dp),
                            color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MessageType.IMAGE -> {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            android.util.Log.e("liutuo", "chatMessage:${message}")
                            // 显示图片
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = "图片",
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                                //placeholder = painterResource(id = R.drawable.ic_launcher_background),
                                error = painterResource(id = R.drawable.ic_launcher_foreground)
                            )
                            // 显示图片描述
                            if (message.content?.isNotEmpty() == true) {
                                Text(
                                    text = message.content,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    MessageType.AUDIO -> {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 显示语音消息
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "语音消息",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "语音消息",
                                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 显示语音转文本结果
                            if (message.content?.isNotEmpty() == true) {
                                Text(
                                    text = "文本: ${message.content}",
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (message.isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun ChatScreenPreview() {
        Welo_aichatTheme {
            ChatScreen()
        }
    }
}

