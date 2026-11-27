package com.example.blacknote

sealed class Expr {
    data class Num(val value: Double) : Expr()
    data class Var(val name: String) : Expr()
    data class Bin(val op: Char, val l: Expr, val r: Expr) : Expr()
}

object ExprEngine {

    val LATEX_SYMBOLS = mapOf(
        "pi" to "π", "cdot" to "·", "times" to "×", "div" to "÷",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "Delta" to "Δ",
        "theta" to "θ", "Theta" to "Θ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω",
        "Omega" to "Ω", "varepsilon" to "ε", "infty" to "∞", "pm" to "±", "mp" to "∓",
        "approx" to "≈", "leq" to "≤", "geq" to "≥", "neq" to "≠",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "partial" to "∂", "nabla" to "∇", "circ" to "°"
    )

    private val GREEK = setOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta",
        "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma",
        "tau", "upsilon", "phi", "chi", "psi", "omega",
        "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega"
    )

    fun displaySymbol(name: String): String = LATEX_SYMBOLS[name] ?: name

    fun latexVar(name: String): String = if (name in GREEK) "\\$name" else name

    fun varsOf(e: Expr): List<String> = when (e) {
        is Expr.Num -> emptyList()
        is Expr.Var -> listOf(e.name)
        is Expr.Bin -> varsOf(e.l) + varsOf(e.r)
    }

    fun parseEquation(input: String): Pair<Expr, Expr>? {
        var depth = 0
        for ((idx, c) in input.withIndex()) {
            when (c) {
                '(' -> depth++
                ')' -> depth--
                '=' -> if (depth == 0) {
                    val l = parse(input.substring(0, idx)) ?: return null
                    val r = parse(input.substring(idx + 1)) ?: return null
                    return l to r
                }
            }
        }
        return null
    }

    fun parse(input: String): Expr? {
        val p = Parser(input)
        val e = p.parseExpr() ?: return null
        p.skipSpaces()
        return if (p.pos >= input.length) e else null
    }

    fun eval(e: Expr, values: Map<String, Double>): Double = when (e) {
        is Expr.Num -> e.value
        is Expr.Var -> values[e.name]
            ?: throw IllegalArgumentException("missing value for $e.name")
        is Expr.Bin -> {
            val l = eval(e.l, values)
            val r = eval(e.r, values)
            when (e.op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> l / r
                '^' -> Math.pow(l, r)
                else -> throw IllegalArgumentException("bad operator ${e.op}")
            }
        }
    }

    fun countVar(e: Expr, name: String): Int = when (e) {
        is Expr.Num -> 0
        is Expr.Var -> if (e.name == name) 1 else 0
        is Expr.Bin -> countVar(e.l, name) + countVar(e.r, name)
    }

    private fun contains(e: Expr, name: String): Boolean = countVar(e, name) > 0

    fun solveFor(eq: Pair<Expr, Expr>, variable: String): Expr? {
        val (l, r) = eq
        if (countVar(l, variable) + countVar(r, variable) != 1) return null
        return if (contains(l, variable)) solveSide(l, r, variable)
        else solveSide(r, l, variable)
    }

    private fun solveSide(side: Expr, other: Expr, variable: String): Expr? {
        if (side is Expr.Var && side.name == variable) return other
        if (side !is Expr.Bin) return null
        val inLeft = contains(side.l, variable)
        return when (side.op) {
            '+' -> if (inLeft) solveSide(side.l, Expr.Bin('-', other, side.r), variable)
                   else solveSide(side.r, Expr.Bin('-', other, side.l), variable)
            '-' -> if (inLeft) solveSide(side.l, Expr.Bin('+', other, side.r), variable)
                   else solveSide(side.r, Expr.Bin('-', side.l, other), variable)
            '*' -> if (inLeft) solveSide(side.l, Expr.Bin('/', other, side.r), variable)
                   else solveSide(side.r, Expr.Bin('/', other, side.l), variable)
            '/' -> if (inLeft) solveSide(side.l, Expr.Bin('*', other, side.r), variable)
                   else solveSide(side.r, Expr.Bin('/', side.l, other), variable)
            '^' -> if (inLeft) solveSide(
                side.l,
                Expr.Bin('^', other, Expr.Bin('/', Expr.Num(1.0), side.r)),
                variable
            ) else null
            else -> null
        }
    }

    fun substitute(e: Expr, values: Map<String, Double>): Expr = when (e) {
        is Expr.Num -> e
        is Expr.Var -> values[e.name]?.let { Expr.Num(it) } ?: e
        is Expr.Bin -> Expr.Bin(e.op, substitute(e.l, values), substitute(e.r, values))
    }

    fun toLatex(e: Expr): String = when (e) {
        is Expr.Num -> formatNum(e.value)
        is Expr.Var -> latexVar(e.name)
        is Expr.Bin -> when (e.op) {
            '+' -> "${toLatex(e.l)} + ${toLatex(e.r)}"
            '-' -> "${toLatex(e.l)} - ${toLatex(e.r)}"
            '*' -> if (e.l is Expr.Num) "${toLatex(e.l)}${toLatex(e.r)}"
                   else "${toLatex(e.l)} \\cdot ${toLatex(e.r)}"
            '/' -> "\\frac{${toLatex(e.l)}}{${toLatex(e.r)}}"
            '^' -> "${latexBase(e.l)}^{${toLatex(e.r)}}"
            else -> "?"
        }
    }

    fun toLatexEquation(l: Expr, r: Expr): String = "${toLatex(l)} = ${toLatex(r)}"

    private fun latexBase(e: Expr): String = when (e) {
        is Expr.Var, is Expr.Num -> toLatex(e)
        else -> "(${toLatex(e)})"
    }

    fun formatNum(v: Double): String =
        if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    private class Parser(val s: String) {
        var pos = 0

        fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parseExpr(): Expr? {
            var left = parseTerm() ?: return null
            while (true) {
                skipSpaces()
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                    val op = s[pos++]
                    val right = parseTerm() ?: return null
                    left = Expr.Bin(op, left, right)
                } else return left
            }
        }

        fun parseTerm(): Expr? {
            var left = parseFactor() ?: return null
            while (true) {
                skipSpaces()
                if (pos >= s.length) return left
                when (val c = s[pos]) {
                    '*', '/' -> {
                        pos++
                        val r = parseFactor() ?: return null
                        left = Expr.Bin(c, left, r)
                    }
                    '+', '-', '=', ')' -> return left
                    else -> {
                        val r = parseFactor() ?: return left
                        left = Expr.Bin('*', left, r)
                    }
                }
            }
        }

        fun parseFactor(): Expr? {
            var base = parseUnary() ?: return null
            skipSpaces()
            if (pos < s.length && s[pos] == '^') {
                pos++
                val exp = parseFactor() ?: return null
                base = Expr.Bin('^', base, exp)
            }
            return base
        }

        fun parseUnary(): Expr? {
            skipSpaces()
            if (pos < s.length && s[pos] == '-') {
                pos++
                val e = parseUnary() ?: return null
                return Expr.Bin('*', Expr.Num(-1.0), e)
            }
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseUnary()
            }
            return parsePrimary()
        }

        fun parsePrimary(): Expr? {
            skipSpaces()
            if (pos >= s.length) return null
            val c = s[pos]
            return when {
                c.isDigit() || c == '.' -> {
                    val start = pos
                    while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                    s.substring(start, pos).toDoubleOrNull()?.let { Expr.Num(it) }
                }
                c.isLetter() -> {
                    val start = pos
                    while (pos < s.length && s[pos].isLetter()) pos++
                    Expr.Var(s.substring(start, pos))
                }
                c == '(' -> {
                    pos++
                    val e = parseExpr() ?: return null
                    skipSpaces()
                    if (pos < s.length && s[pos] == ')') pos++
                    e
                }
                else -> null
            }
        }
    }
}
