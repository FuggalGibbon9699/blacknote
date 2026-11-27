package com.example.blacknote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Phase { Idle, Listening, Thinking, Answer }

@Composable
fun SolverScreen() {
    val context = LocalContext.current
    val store = remember { SolverStore(context) }
    val scope = rememberCoroutineScope()

    var problem by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var solution by remember { mutableStateOf<Solution?>(null) }
    var equation by remember { mutableStateOf<Pair<Expr, Expr>?>(null) }
    var solvedExpr by remember { mutableStateOf<Expr?>(null) }
    var solvedFor by remember { mutableStateOf<String?>(null) }
    var formulaLatex by remember { mutableStateOf("") }
    var values by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputVar by remember { mutableStateOf<String?>(null) }

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

    fun resultText(symbol: String, value: Double): String {
        val sol = solution ?: return "${ExprEngine.displaySymbol(symbol)} = ${ExprEngine.formatNum(value)}"
        val unit = sol.variables.find { it.symbol == symbol }?.unit ?: ""
        return "${ExprEngine.displaySymbol(symbol)} = ${ExprEngine.formatNum(value)} $unit".trim()
    }

    fun refreshFormula() {
        val sol = solution ?: return
        val eq = equation
        formulaLatex = when {
            solvedFor != null && solvedExpr != null ->
                "${ExprEngine.toLatex(Expr.Var(solvedFor!!))} = ${ExprEngine.toLatex(ExprEngine.substitute(solvedExpr!!, values))}"
            eq != null -> ExprEngine.toLatexEquation(
                ExprEngine.substitute(eq.first, values),
                ExprEngine.substitute(eq.second, values)
            )
            else -> sol.formula
        }
    }

    fun computeIfComplete() {
        val eq = equation ?: return
        try {
            if (solvedFor != null && solvedExpr != null) {
                val se = solvedExpr!!
                if (ExprEngine.varsOf(se).all { values.containsKey(it) }) {
                    result = resultText(
                        solvedFor!!,
                        ExprEngine.eval(ExprEngine.substitute(se, values), emptyMap())
                    )
                }
                return
            }
            val lVars = ExprEngine.varsOf(eq.first)
            val rVars = ExprEngine.varsOf(eq.second)
            when {
                rVars.isNotEmpty() && rVars.all { values.containsKey(it) } && eq.first is Expr.Var ->
                    result = resultText(
                        eq.first.name,
                        ExprEngine.eval(ExprEngine.substitute(eq.second, values), emptyMap())
                    )
                lVars.isNotEmpty() && lVars.all { values.containsKey(it) } && eq.second is Expr.Var ->
                    result = resultText(
                        eq.second.name,
                        ExprEngine.eval(ExprEngine.substitute(eq.first, values), emptyMap())
                    )
            }
        } catch (e: Exception) {
            result = null
            error = "Calculation error: ${e.message}"
        }
    }

    fun rearrange(symbol: String) {
        val eq = equation ?: return
        val solved = ExprEngine.solveFor(eq, symbol)
        if (solved == null) {
            error = "Can't rearrange the equation for $symbol"
            return
        }
        solvedFor = symbol
        solvedExpr = solved
        result = null
        refreshFormula()
        computeIfComplete()
    }

    fun setValue(symbol: String, value: Double) {
        values = values + (symbol to value)
        refreshFormula()
        computeIfComplete()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || phase == Phase.Thinking) return
        if (store.apiKey.isBlank()) {
            error = "Add your API key first — menu (☰) → API settings."
            return
        }
        if (store.provider == SolverStore.PROVIDER_CUSTOM && store.endpoint.isBlank()) {
            error = "Set a base URL first — menu (☰) → API settings."
            return
        }
        problem = trimmed
        solution = null
        equation = null
        solvedExpr = null
        solvedFor = null
        formulaLatex = ""
        values = emptyMap()
        result = null
        error = null
        phase = Phase.Thinking
        val config = LlmClient.Config(
            provider = store.provider,
            apiKey = store.apiKey.trim(),
            model = store.model.ifBlank { SolverStore.defaultModelFor(store.provider) },
            endpoint = store.endpoint.trim()
        )
        scope.launch {
            LlmClient.solve(config, trimmed).fold(
                onSuccess = { sol ->
                    solution = sol
                    if (sol.raw == null) {
                        equation = ExprEngine.parseEquation(sol.expr)
                        solvedFor = (equation?.first as? Expr.Var)?.name
                        formulaLatex = sol.formula.ifBlank { sol.expr }
                    }
                    phase = Phase.Answer
                },
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
            .padding(horizontal = 20.dp)
    ) {
        AnimatedContent(
            targetState = phase,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "phase",
            transitionSpec = {
                ContentTransform(
                    fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 },
                    fadeOut(tween(200))
                )
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
                    Phase.Answer -> AnswerView(
                        solution = solution,
                        formulaLatex = formulaLatex,
                        result = result,
                        values = values,
                        onVarTap = { inputVar = it },
                        onVarLongPress = { rearrange(it) }
                    )
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
                shape = RoundedCornerShape(28.dp),
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

    if (inputVar != null) {
        val v = inputVar!!
        val unit = solution?.variables?.find { it.symbol == v }?.unit ?: ""
        ValueDialog(
            variable = v,
            unit = unit,
            onDismiss = { inputVar = null },
            onValue = { setValue(v, it); inputVar = null }
        )
    }
}

@Composable
private fun IdleView(micVisible: Boolean, onMic: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (micVisible) {
            MicButton(listening = false, onClick = onMic)
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
        MicButton(listening = true, onClick = onStop)
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
private fun AnswerView(
    solution: Solution?,
    formulaLatex: String,
    result: String?,
    values: Map<String, Double>,
    onVarTap: (String) -> Unit,
    onVarLongPress: (String) -> Unit
) {
    if (solution == null) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (solution.raw != null) {
            MarkdownText(text = solution.raw, baseSize = 15.sp)
            return
        }
        Text(
            text = solution.explanation,
            color = Color.White,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            targetState = formulaLatex,
            label = "formula",
            transitionSpec = {
                ContentTransform(
                    fadeIn(tween(300)) + scaleIn(tween(300)),
                    fadeOut(tween(200)) + scaleOut(tween(200))
                )
            }
        ) { latex ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LatexFormula(
                    latex = latex,
                    variables = solution.variables.map { it.symbol }.toSet(),
                    fontSize = 30.sp,
                    onVarTap = onVarTap,
                    onVarLongPress = onVarLongPress
                )
            }
        }
        AnimatedVisibility(visible = result != null) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = result ?: "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        solution.variables.forEach { v ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onVarTap(v.symbol) },
                            onLongPress = { onVarLongPress(v.symbol) }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ExprEngine.displaySymbol(v.symbol)} — ${v.name}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = values[v.symbol]?.let { "${ExprEngine.formatNum(it)} ${v.unit}".trim() }
                        ?: v.unit,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        if (solution.estimate.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = solution.estimate, color = Color.Gray, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "tap a variable for its value — hold it to rearrange the equation",
            color = Color.DarkGray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            tween(if (listening) 400 else 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(110.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = if (listening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (listening) "Stop listening" else "Start listening",
                tint = Color.Black,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun ValueDialog(
    variable: String,
    unit: String,
    onDismiss: () -> Unit,
    onValue: (Double) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        title = {
            Text(
                text = "Value for $variable" + if (unit.isNotBlank()) " ($unit)" else "",
                color = Color.White
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("number…", color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.trim().toDoubleOrNull()?.let(onValue)
            }) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
