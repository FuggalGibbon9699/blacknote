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
import androidx.compose.ui.text.BaselineShift
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

private val SYMBOLS = mapOf(
    "pi" to "π", "cdot" to "·", "times" to "×", "div" to "÷",
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "Delta" to "Δ",
    "theta" to "θ", "Theta" to "Θ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
    "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω",
    "Omega" to "Ω", "varepsilon" to "ε", "infty" to "∞", "pm" to "±", "mp" to "∓",
    "approx" to "≈", "leq" to "≤", "geq" to "≥", "neq" to "≠",
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
    "partial" to "∂", "nabla" to "∇", "circ" to "°"
)

private fun renderLatex(latex: String, vars: Set<String>, baseSize: TextUnit): AnnotatedString =
    LatexParser(latex, vars, baseSize).parse()

private class LatexParser(val s: String, val vars: Set<String>, val baseSize: TextUnit) {
    var i = 0

    fun parse(): AnnotatedString = buildAnnotatedString { parseSeq(false) }

    private fun AnnotatedString.Builder.parseSeq(inGroup: Boolean) {
        while (i < s.length) {
            when (val c = s[i]) {
                '}' -> {
                    i++
                    if (inGroup) return
                }
                '{' -> {
                    i++
                    parseSeq(true)
                }
                '\\' -> parseCommand()
                '^' -> {
                    i++
                    pushStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = baseSize * 0.65f
                        )
                    )
                    append(parseGroup())
                    pop()
                }
                '_' -> {
                    i++
                    pushStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = baseSize * 0.65f
                        )
                    )
                    append(parseGroup())
                    pop()
                }
                else -> if (c.isLetter()) {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    val word = s.substring(start, i)
                    if (vars.contains(word)) {
                        pushStringAnnotation("var", word)
                        pushStyle(
                            SpanStyle(
                                color = Color.White,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(word)
                        pop()
                        pop()
                    } else {
                        append(word)
                    }
                } else {
                    append(c.toString())
                    i++
                }
            }
        }
    }

    private fun AnnotatedString.Builder.parseCommand() {
        i++
        val start = i
        while (i < s.length && s[i].isLetter()) i++
        val cmd = s.substring(start, i)
        when {
            cmd == "frac" -> {
                val num = parseGroup()
                val den = parseGroup()
                pushStyle(SpanStyle(fontSize = baseSize * 0.75f))
                append(num)
                append("/")
                append(den)
                pop()
            }
            cmd == "sqrt" -> {
                append("√(")
                append(parseGroup())
                append(")")
            }
            cmd == "text" || cmd == "mathrm" -> append(parseGroupRaw())
            cmd.isEmpty() -> {
                if (i < s.length) {
                    val c = s[i]
                    i++
                    append(if (c == ',' || c == ';' || c == '!' || c == ' ') " " else c.toString())
                }
            }
            cmd == "quad" || cmd == "qquad" -> append("   ")
            else -> append(SYMBOLS[cmd] ?: cmd)
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
            val ann = buildAnnotatedString { sub.parseCommand() }
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
