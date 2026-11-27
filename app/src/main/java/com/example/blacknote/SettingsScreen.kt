package com.example.blacknote

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MISTRAL_MODELS = listOf(
    "Smallest" to "mistral-smallest-latest",
    "Small" to "mistral-small-latest",
    "Medium" to "mistral-medium-latest",
    "Large" to "mistral-large-latest",
    "Latest" to "mistral-latest"
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SolverStore(context) }

    var provider by remember { mutableStateOf(store.provider) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }
    var endpoint by remember { mutableStateOf(store.endpoint) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "API settings",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

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

        if (provider == SolverStore.PROVIDER_MISTRAL) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MISTRAL_MODELS.forEach { (label, value) ->
                    TextButton(onClick = { model = value; store.model = value }) {
                        Text(
                            text = label,
                            color = if (model == value) Color.White else Color.Gray,
                            fontWeight = if (model == value) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

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

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Saved automatically on this device. The key is only sent to the endpoint you choose.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
