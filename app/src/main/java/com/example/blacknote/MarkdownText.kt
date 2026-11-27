package com.example.blacknote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    baseSize: TextUnit = 15.sp
) {
    Column(modifier = modifier) {
        text.lines().forEach { raw ->
            when {
                raw.isBlank() -> Spacer(Modifier.height(8.dp))
                raw.startsWith("### ") ->
                    Line(raw.removePrefix("### "), color, baseSize, FontWeight.Bold, Modifier.padding(top = 4.dp))
                raw.startsWith("## ") ->
                    Line(raw.removePrefix("## "), color, baseSize * 1.15f, FontWeight.Bold, Modifier.padding(top = 10.dp))
                raw.startsWith("# ") ->
                    Line(raw.removePrefix("# "), color, baseSize * 1.3f, FontWeight.Bold, Modifier.padding(top = 12.dp))
                raw.startsWith("- ") || raw.startsWith("* ") ->
                    Line("•  ${raw.substring(2)}", color, baseSize, FontWeight.Normal, Modifier.padding(start = 8.dp))
                else ->
                    Line(raw, color, baseSize, FontWeight.Normal, Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun Line(
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight,
    modifier: Modifier
) {
    Text(
        text = inline(text),
        color = color,
        fontSize = size,
        fontWeight = weight,
        modifier = modifier
    )
}

private val INLINE_PATTERN =
    Regex("(\\*\\*.+?\\*\\*|(?<!\\*)\\*[^*\\n]+?\\*(?!\\*)|`[^`\\n]+?`)")

private fun inline(raw: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in INLINE_PATTERN.findAll(raw)) {
        if (match.range.first > last) append(raw.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("`") -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color.LightGray))
                append(token.substring(1, token.length - 1))
                pop()
            }
            else -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(token.substring(1, token.length - 1))
                pop()
            }
        }
        last = match.range.last + 1
    }
    if (last < raw.length) append(raw.substring(last))
}
