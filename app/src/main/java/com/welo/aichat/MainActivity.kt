package com.welo.aichat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Welo_aichatTheme {
                ChatScreen()
            }
        }
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

       /* // 录音状态
        var isRecording by remember { mutableStateOf(false) }

        // Vosk相关变量
        var speechService: SpeechService? = null
        var model: Model? = null

        // 语音识别结果
        var recognitionResult by remember { mutableStateOf("") }


        // 使用反射设置模型语言（解决原始模型lang None错误）
        fun setModelLanguage(model: Model?, languageCode: String) {
            if (model == null) return

            try {
                val langField = Model::class.java.getDeclaredField("_lang")
                langField.isAccessible = true
                langField.set(model, languageCode)
                Log.d("VoskModel", "模型语言设置为: $languageCode")
            } catch (e: Exception) {
                Log.e("VoskModel", "设置模型语言失败: ${e.message}", e)
                // 即使设置语言失败，也继续执行，可能仍能工作
            }
        }

        // 直接从SD卡加载原始模型，无需标准格式
        fun initVoskModelDirectlyFromSDCard(context: Context) {
            Log.d("VoskModel", "开始从SD卡直接加载Vosk原始模型")

            scope.launch {
                try {
                    snackbarHostState.showSnackbar("正在加载语音识别模型...")
                    LibVosk.setLogLevel(LogLevel.INFO)

                    // SD卡模型路径（根据实际情况调整）
                    val modelPath = "/sdcard/model-cn"

                    // 验证模型目录存在
                    val modelDir = File(modelPath)
                    if (!modelDir.exists() || !modelDir.isDirectory) {
                        throw IOException("SD卡模型目录不存在: $modelPath")
                    }

                    // 验证原始模型必要文件
                    val requiredFiles = listOf(
                        "am/final.mdl",
                        "graph/HCLG.fst",
                        "graph/words.txt",
                        "ivector/final.ie"
                    )

                    val missingFiles = requiredFiles.filter { !File(modelDir, it).exists() }
                    if (missingFiles.isNotEmpty()) {
                        throw IOException("缺失原始模型必要文件: ${missingFiles.joinToString()}")
                    }

                    // 直接加载原始模型
                    Log.d("VoskModel", "加载原始模型: $modelPath")
                    model = Model(modelPath)

                    // 使用反射设置语言（解决lang None错误）
                    setModelLanguage(model, "zh")

                    Log.d("VoskModel", "原始模型加载成功")

                    scope.launch {
                        snackbarHostState.showSnackbar("语音识别模型加载成功")
                    }
                } catch (e: Exception) {
                    Log.e("VoskModel", "模型初始化失败", e)
                    scope.launch {
                        snackbarHostState.showSnackbar("模型加载失败: ${e.message}")
                    }
                }
            }
        }

        // 初始化Vosk模型
        LaunchedEffect(Unit) {
            //initVoskModelFromSDCard(context, "/sdcard/model-cn/vosk-model-cn-0.22")
            initVoskModelDirectlyFromSDCard(context)
        }

        // 开始录音和识别
        fun startRecognition() {
            if (model == null) {
                scope.launch {
                    snackbarHostState.showSnackbar("模型未加载，请稍候...")
                }
                return
            }

            try {
                // 创建识别器，设置采样率为16000Hz
                val recognizer = org.vosk.Recognizer(model, 16000.0f)

                // 创建语音服务
                speechService = SpeechService(recognizer, 16000.0f).apply {
                    startListening(object : RecognitionListener {
                        // Vosk识别结果回调（中间结果）
                        override fun onResult(hypothesis: String?) {
                            hypothesis?.let {
                                scope.launch {
                                    recognitionResult = it
                                    // 实时显示识别结果
                                }
                            }
                        }

                        // Vosk最终识别结果回调
                        override fun onFinalResult(hypothesis: String?) {
                            hypothesis?.let { finalResult ->
                                scope.launch {
                                    recognitionResult = finalResult
                                    // 发送最终识别结果到后端
                                    viewModel.sendMessageToAI("用户语音输入: $finalResult")

                                    // 添加语音消息到聊天列表
                                    val newMessages = chatMessages.toMutableList()
                                    newMessages.add(
                                        ChatMessage(
                                            content = "语音已发送",
                                            isUser = true,
                                            type = MessageType.AUDIO,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    viewModel.updateMessages(newMessages)
                                }
                            }
                        }

                        // 识别错误回调
                        override fun onError(e: Exception?) {
                            scope.launch {
                                val errorMessage = e?.message ?: "识别错误"
                                snackbarHostState.showSnackbar(errorMessage)
                            }
                        }

                        // 识别超时回调
                        override fun onTimeout() {
                            scope.launch {
                                snackbarHostState.showSnackbar("识别超时")
                            }
                        }

                        // 以下是可选方法的空实现
                        override fun onPartialResult(hypothesis: String?) {
                            // 处理部分识别结果（可选）
                        }
                    })
                }
                isRecording = true
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("启动识别失败: ${e.message}")
                }
                isRecording = false
            }
        }

        // 停止录音和识别
        fun stopRecognition() {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            isRecording = false
        }

        // 请求录音权限
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecognition()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("需要录音权限才能使用语音消息功能")
                }
            }
        }

        // 检查权限并开始录音
        fun checkPermissionAndStartRecording() {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startRecognition()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }*/

        // 录音状态
        var isRecording by remember { mutableStateOf(false) }

        // Vosk相关变量
        var speechService: SpeechService? by remember { mutableStateOf(null) }
        var model: Model? by remember { mutableStateOf(null) }

        // 语音识别结果
        var recognitionResult by remember { mutableStateOf("") }
        val isModelLoaded = remember { mutableStateOf(false) }


        // 开始录音和识别
        fun startRecognition() {
            if (!isModelLoaded.value) {
                scope.launch {
                    snackbarHostState.showSnackbar("模型未加载，请稍候...")
                }
                return
            }

            try {
                // 创建识别器，设置采样率为16000Hz
                val recognizer = org.vosk.Recognizer(model, 16000.0f)

                // 创建语音服务
                speechService = SpeechService(recognizer, 16000.0f).apply {
                    startListening(object : RecognitionListener {
                        // Vosk识别结果回调（中间结果）
                        override fun onResult(hypothesis: String?) {
                            hypothesis?.let {
                                scope.launch {
                                    recognitionResult = it
                                    // 实时显示识别结果
                                }
                            }
                        }

                        // Vosk最终识别结果回调
                        override fun onFinalResult(hypothesis: String?) {
                            hypothesis?.let { finalResult ->
                                scope.launch {
                                    recognitionResult = finalResult
                                    // 发送最终识别结果到后端
                                    viewModel.sendMessageToAI("用户语音输入: $finalResult")

                                    // 添加语音消息到聊天列表
                                    val newMessages = chatMessages.toMutableList()
                                    newMessages.add(
                                        ChatMessage(
                                            content = "语音已发送",
                                            isUser = true,
                                            type = MessageType.AUDIO,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    viewModel.updateMessages(newMessages)
                                }
                            }
                        }

                        // 识别错误回调
                        override fun onError(e: Exception?) {
                            scope.launch {
                                val errorMessage = e?.message ?: "识别错误"
                                snackbarHostState.showSnackbar(errorMessage)
                            }
                        }

                        // 识别超时回调
                        override fun onTimeout() {
                            scope.launch {
                                snackbarHostState.showSnackbar("识别超时")
                            }
                        }

                        // 以下是可选方法的空实现
                        override fun onPartialResult(hypothesis: String?) {
                            // 处理部分识别结果（可选）
                        }
                    })
                }
                isRecording = true
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("启动识别失败: ${e.message}")
                }
                isRecording = false
            }
        }

        // 停止录音和识别
        fun stopRecognition() {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            isRecording = false
        }

        // 请求录音权限
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecognition()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("需要录音权限才能使用语音消息功能")
                }
            }
        }

        // 检查权限并开始录音
        fun checkPermissionAndStartRecording() {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startRecognition()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // 使用StorageService.unpack从assets目录加载模型
        suspend fun initVoskModelFromAssets(
            context: Context,
            snackbarHostState: SnackbarHostState,
            scope: CoroutineScope
        ) {
            Log.d("VoskModel", "开始从assets目录加载模型")
            LibVosk.setLogLevel(LogLevel.INFO)

            try {
                snackbarHostState.showSnackbar("正在准备语音识别模型...")

                //val modelAssetName = "vosk-model-small-cn-0.22" // 根据你的实际文件名修改
                val modelAssetName = "vosk-model-cn"

                // 解压到应用内部存储的路径
                //val unpackPath = context.getDir("vosk_model", Context.MODE_PRIVATE).absolutePath
                val unpackPath = "model"

                // 使用StorageService.unpack方法解压模型
                StorageService.unpack(
                    context,
                    modelAssetName,
                    unpackPath,
                    { loadedModel ->
                        // 模型加载成功回调
                        model = loadedModel
                        isModelLoaded.value = true
                        Log.d("VoskModel", "模型加载成功: $unpackPath")

                        scope.launch {
                            snackbarHostState.showSnackbar("语音识别模型加载成功")
                        }
                    },
                    { exception ->
                        // 模型加载失败回调
                        Log.e("VoskModel", "模型加载失败", exception)
                        scope.launch {
                            snackbarHostState.showSnackbar("模型加载失败: ${exception.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("VoskModel", "模型初始化失败", e)
                scope.launch {
                    snackbarHostState.showSnackbar("模型初始化失败: ${e.message}")
                }
            }
        }


        // 初始化Vosk模型 - 使用StorageService.unpack方法
        LaunchedEffect(Unit) {
            scope.launch {
                initVoskModelFromAssets(context, snackbarHostState, scope)
            }
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

                // 录音状态提示
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(Color.Red.copy(alpha = 0.2f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "正在录音",
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在识别...", color = Color.Red)
                            }

                            if (recognitionResult.isNotEmpty()) {
                                Text(
                                    text = "识别中: $recognitionResult",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = Color.Red
                                )
                            }
                        }
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

                    // 长按录音按钮
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        checkPermissionAndStartRecording()
                                        awaitRelease()
                                        stopRecognition()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "按住录音",
                            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.primary
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

        // 组件销毁时释放资源
        DisposableEffect(Unit) {
            onDispose {
                speechService?.shutdown()
                speechService = null
                model?.apply {
                    close()
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
                            if (message.content?.isNotEmpty() == true && !message.content.startsWith("语音已发送")) {
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