package com.example.blacknote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

class SolverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SolverScreen(onClose = { finish() })
            }
        }
    }
}

private enum class Phase { Idle, Listening, Thinking, Answer }

@Composable
fun SolverScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SolverStore(context) }
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf(store.provider) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }
    var endpoint by remember { mutableStateOf(store.endpoint) }
    var showConfig by remember { mutableStateOf(false) }

    var problem by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(Phase.Idle) }

    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    val recognizer = remember(speechAvailable) {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        onDispose { recognizer?.destroy() }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || phase == Phase.Thinking) return
        if (apiKey.isBlank()) {
            error = "Add your API key first — tap the gear icon at the top."
            showConfig = true
            return
        }
        if (provider == SolverStore.PROVIDER_CUSTOM && endpoint.isBlank()) {
            error = "Set a base URL for your custom endpoint — tap the gear icon at the top."
            showConfig = true
            return
        }
        problem = trimmed
        answer = null
        error = null
        phase = Phase.Thinking
        val config = LlmClient.Config(
            provider = provider,
            apiKey = apiKey.trim(),
            model = model.ifBlank { SolverStore.defaultModelFor(provider) },
            endpoint = endpoint.trim()
        )
        scope.launch {
            LlmClient.solve(config, trimmed).fold(
                onSuccess = { answer = it; phase = Phase.Answer },
                onFailure = { error = it.message ?: "Request failed"; phase = Phase.Idle }
            )
        }
    }

    fun startListening() {
        if (!speechAvailable) {
            error = "Speech recognition is not available on this device — type the problem below."
            return
        }
        if (!micGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val r = recognizer ?: return
        error = null
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    send(text)
                } else if (phase == Phase.Listening) {
                    phase = Phase.Idle
                }
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) problem = text
            }

            override fun onError(errorCode: Int) {
                when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (phase == Phase.Listening) phase = Phase.Idle
                    else -> {
                        error = "Speech error ($errorCode) — you can type the problem instead."
                        if (phase == Phase.Listening) phase = Phase.Idle
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "State your physics or engineering problem")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        answer = null
        phase = Phase.Listening
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        if (phase == Phase.Listening) {
            if (problem.isNotBlank()) send(problem) else phase = Phase.Idle
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "solver",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showConfig = !showConfig }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "API settings",
                    tint = if (showConfig) Color.White else Color.Gray
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to notes",
                    tint = Color.Gray
                )
            }
        }

        AnimatedVisibility(visible = showConfig) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        SolverStore.PROVIDER_MISTRAL to "Mistral",
                        SolverStore.PROVIDER_OPENAI to "OpenAI",
                        SolverStore.PROVIDER_CUSTOM to "Custom"
                    ).forEach { (value, label) ->
                        TextButton(onClick = { provider = value; store.provider = value }) {
                            Text(
                                text = label,
                                color = if (provider == value) Color.White else Color.Gray,
                                fontWeight = if (provider == value) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                ConfigField(
                    value = apiKey,
                    onChange = { apiKey = it; store.apiKey = it },
                    label = "API key",
                    password = true
                )
                ConfigField(
                    value = model,
                    onChange = { model = it; store.model = it },
                    label = "Model",
                    placeholder = SolverStore.defaultModelFor(provider)
                )
                if (provider == SolverStore.PROVIDER_CUSTOM) {
                    ConfigField(
                        value = endpoint,
                        onChange = { endpoint = it; store.endpoint = it },
                        label = "Base URL",
                        placeholder = "https://your-host/v1"
                    )
                }
            }
        }

        AnimatedContent(
            targetState = phase,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "phase",
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }) togetherWith
                    fadeOut(tween(200))
            }
        ) { target ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (target) {
                    Phase.Idle -> IdleView(
                        micVisible = speechAvailable,
                        onMic = { startListening() }
                    )
                    Phase.Listening -> ListeningView(
                        transcript = problem,
                        onStop = { stopListening() }
                    )
                    Phase.Thinking -> ThinkingView()
                    Phase.Answer -> AnswerView(problem = problem, answer = answer)
                }
            }
        }

        AnimatedVisibility(visible = error != null) {
            Text(
                text = error ?: "",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = problem,
                onValueChange = { problem = it },
                placeholder = { Text("type a problem…", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                colors = fieldColors()
            )
            if (speechAvailable) {
                IconButton(onClick = {
                    if (phase == Phase.Listening) stopListening() else startListening()
                }) {
                    Icon(
                        imageVector = if (phase == Phase.Listening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (phase == Phase.Listening) Color.White else Color.Gray
                    )
                }
            }
            IconButton(
                onClick = { send(problem) },
                enabled = problem.isNotBlank() && phase != Phase.Thinking
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Solve",
                    tint = if (problem.isNotBlank() && phase != Phase.Thinking) Color.White else Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun IdleView(micVisible: Boolean, onMic: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (micVisible) {
            MicButton(listening = false, big = true, onClick = onMic)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "tap the mic and state your problem",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "speech recognition is not available here — type the problem below",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ListeningView(transcript: String, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MicButton(listening = true, big = true, onClick = onStop)
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(visible = transcript.isNotBlank()) {
            Text(
                text = transcript,
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ThinkingView() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val transition = rememberInfiniteTransition(label = "dots")
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White.copy(alpha = alpha), CircleShape)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(text = "solving…", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
private fun AnswerView(problem: String, answer: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = problem, color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color.DarkGray)
        Spacer(Modifier.height(14.dp))
        if (answer != null) {
            MarkdownText(text = answer, baseSize = 15.sp)
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, big: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val duration = if (listening) 450 else 1100
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.1f else 1.05f,
        animationSpec = infiniteRepeatable(tween(duration), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = if (listening) 0.9f else 0.2f,
        targetValue = if (listening) 0.15f else 0.55f,
        animationSpec = infiniteRepeatable(tween(duration), repeatMode = RepeatMode.Reverse),
        label = "ring"
    )
    val size = if (big) 104.dp else 52.dp
    Box(
        modifier = Modifier.size(size + 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size + 28.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(2.dp, Color.White.copy(alpha = ringAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (listening) Color.White else Color.Black, CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (listening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (listening) "Stop listening" else "Start listening",
                    tint = if (listening) Color.Black else Color.White,
                    modifier = Modifier.size(if (big) 42.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfigField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Color.Gray) },
        placeholder = { Text(placeholder, color = Color.DarkGray) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.DarkGray,
    cursorColor = Color.White,
    focusedLabelColor = Color.Gray,
    unfocusedLabelColor = Color.Gray,
    focusedPlaceholderColor = Color.DarkGray,
    unfocusedPlaceholderColor = Color.DarkGray,
    focusedContainerColor = Color.Black,
    unfocusedContainerColor = Color.Black
)
