package com.example.blacknote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoteNotifier.ensureChannel(this)
        NoteNotifier.showAll(this)
        setContent {
            MaterialTheme {
                NoteApp()
            }
        }
    }
}

@Composable
fun NoteApp() {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    var notes by remember { mutableStateOf(store.all()) }
    var text by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
    ) {
        Text(
            text = "Notes",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Write a note", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = Color.White,
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray,
                focusedContainerColor = Color.Black,
                unfocusedContainerColor = Color.Black
            )
        )
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    val note = store.add(trimmed)
                    NoteNotifier.show(context, note)
                    notes = store.all()
                    text = ""
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Text("Add note")
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text(
                "Keep notifications pinned (enable in settings)",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes, key = { it.id }) { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.text,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        store.markRemoving(note.id)
                        store.remove(note.id)
                        NoteNotifier.hide(context, note.id)
                        notes = store.all()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove note",
                            tint = Color.Gray
                        )
                    }
                }
                HorizontalDivider(color = Color.DarkGray)
            }
        }
    }
}
