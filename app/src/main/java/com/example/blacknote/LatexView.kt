package com.example.blacknote

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun LatexFormula(
    latex: String,
    variables: Set<String>,
    fontSize: TextUnit = 30.sp,
    onVarTap: (String) -> Unit,
    onVarLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(latex, variables, fontSize) {
        renderLatex(latex, variables, fontSize)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        color = Color.White,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { pos -> dispatchVar(layout, annotated, pos, onVarLongPress) },
                onTap = { pos -> dispatchVar(layout, annotated, pos, onVarTap) }
            )
        },
        onTextLayout = { layout = it }
    )
}

private fun dispatchVar(
    layout: TextLayoutResult?,
    text: AnnotatedString,
    pos: Offset,
    handler: (String) -> Unit
) {
    val lr = layout ?: return
    val offset = lr.getOffsetForPosition(pos)
    if (offset < 0 || offset >= text.length) return
    text.getStringAnnotations("var", offset, offset)
        .firstOrNull()
        ?.let { handler(it.item) }
}

private fun renderLatex(latex: String, vars: Set<String>, baseSize: TextUnit): AnnotatedString =
    LatexParser(latex, vars, baseSize).parse()

private class LatexParser(val s: String, val vars: Set<String>, val baseSize: TextUnit) {
    var i = 0

    fun parse(): AnnotatedString = buildAnnotatedString { parseSeq(this, false) }

    private fun parseSeq(b: AnnotatedString.Builder, inGroup: Boolean) {
        while (i < s.length) {
            when (val c = s[i]) {
                '}' -> {
                    i++
                    if (inGroup) return
                }
                '{' -> {
                    i++
                    parseSeq(b, true)
                }
                '\\' -> parseCommand(b)
                '^' -> {
                    i++
                    b.pushStyle(SpanStyle(fontSize = baseSize * 0.65f))
                    b.append(parseGroup())
                    b.pop()
                }
                '_' -> {
                    i++
                    b.pushStyle(SpanStyle(fontSize = baseSize * 0.65f))
                    b.append(parseGroup())
                    b.pop()
                }
                else -> if (c.isLetter()) {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    val word = s.substring(start, i)
                    if (vars.contains(word)) {
                        b.pushStringAnnotation("var", word)
                        b.pushStyle(
                            SpanStyle(
                                color = Color.White,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        b.append(word)
                        b.pop()
                        b.pop()
                    } else {
                        b.append(word)
                    }
                } else {
                    b.append(c.toString())
                    i++
                }
            }
        }
    }

    private fun parseCommand(b: AnnotatedString.Builder) {
        i++
        val start = i
        while (i < s.length && s[i].isLetter()) i++
        val cmd = s.substring(start, i)
        when {
            cmd == "frac" -> {
                val num = parseGroup()
                val den = parseGroup()
                b.pushStyle(SpanStyle(fontSize = baseSize * 0.75f))
                b.append(num)
                b.append("/")
                b.append(den)
                b.pop()
            }
            cmd == "sqrt" -> {
                b.append("√(")
                b.append(parseGroup())
                b.append(")")
            }
            cmd == "text" || cmd == "mathrm" -> b.append(parseGroupRaw())
            cmd.isEmpty() -> {
                if (i < s.length) {
                    val c = s[i]
                    i++
                    b.append(if (c == ',' || c == ';' || c == '!' || c == ' ') " " else c.toString())
                }
            }
            cmd == "quad" || cmd == "qquad" -> b.append("   ")
            else -> {
                val sym = ExprEngine.LATEX_SYMBOLS[cmd]
                if (sym != null && vars.contains(cmd)) {
                    b.pushStringAnnotation("var", cmd)
                    b.pushStyle(
                        SpanStyle(
                            color = Color.White,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    b.append(sym)
                    b.pop()
                    b.pop()
                } else {
                    b.append(sym ?: cmd)
                }
            }
        }
    }

    private fun parseGroup(): AnnotatedString {
        if (i < s.length && s[i] == '{') {
            i++
            val start = i
            var depth = 1
            while (i < s.length && depth > 0) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth > 0) i++
            }
            val inner = s.substring(start, i)
            if (i < s.length) i++
            return LatexParser(inner, vars, baseSize).parse()
        }
        if (i < s.length && s[i] == '\\') {
            val sub = LatexParser(s.substring(i), vars, baseSize)
            val ann = buildAnnotatedString { sub.parseCommand(this) }
            i += sub.i
            return ann
        }
        if (i >= s.length) return buildAnnotatedString { }
        val c = s[i]
        i++
        return buildAnnotatedString { append(c.toString()) }
    }

    private fun parseGroupRaw(): String {
        if (i < s.length && s[i] == '{') {
            i++
            val start = i
            var depth = 1
            while (i < s.length && depth > 0) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth > 0) i++
            }
            val inner = s.substring(start, i)
            if (i < s.length) i++
            return inner
        }
        if (i >= s.length) return ""
        val c = s[i]
        i++
        return c.toString()
    }
}
