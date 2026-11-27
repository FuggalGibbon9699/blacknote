package com.example.blacknote

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
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

@Composable
internal fun ConfigField(
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
