package com.example.latex.converter

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object MathMlPlainTextConverter {
    private val KATEX_SPAN_REGEX = Regex("""<span class="katex">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    
    /**
     * 将包含 Katex MathML 标签的文本批量转换为纯文本
     */
    fun convertFullText(content: String): String {
        if (!content.contains("<math") && !content.contains("class=\"katex\"")) {
            return content
        }
        return KATEX_SPAN_REGEX.replace(content) { match ->
            try {
                convert(match.value)
            } catch (_: Exception) {
                match.value
            }
        }
    }
    /**
     * 解析单个 MathML XML 块
     */
    fun convert(mathMl: String): String {
        val math = Jsoup.parse(mathMl).selectFirst("math") ?: return mathMl
        return render(math)
            .replace(Regex("""\s+"""), " ")
            .replace("( ", "(")
            .replace(" )", ")")
            .replace("[ ", "[")
            .replace(" ]", "]")
            .replace(" ,", ",")
            .trim()
    }
    
    private fun render(node: Node): String {
        if (node is TextNode) return node.text().takeUnless(String::isBlank).orEmpty()
        if (node !is Element) return ""
        
        val children = node.children()
        return when (node.tagName()) {
            "annotations" -> ""
            "semantics" -> children.firstOrNull()?.let(::render).orEmpty()
            "mfrac" -> renderFraction(children)
            "msqrt" -> "√(${renderChildren(node)})"
            "mroot" -> renderRoot(children)
            "msup" -> renderSuperscript(children)
            "msub" -> renderSubscript(children)
            "msubsup" -> renderSubSuperscript(children)
            "munder" -> renderUnder(children)
            "mover" -> renderOver(children)
            "munderover" -> renderUnderOver(children)
            "mtable" -> renderTable(children)
            "mo" -> renderOperator(node.text())
            "mi" -> renderIdentifier(node.text())
            "mn", "mtext" -> node.text()
            "mspace" -> " "
            else -> renderChildren(node)
        }
    }
    
    private fun renderChildren(element: Element): String =
        element.childNodes().joinToString(separator = "", transform = ::render)
        
    private fun renderFraction(children: List<Element>): String {
        val num = children.getOrNull(0)?.let(::render).orEmpty()
        val den = children.getOrNull(1)?.let(::render).orEmpty()
        return "($num)/($den)"
    }
    
     private fun renderRoot(children: List<Element>): String {
        val radicand = children.getOrNull(0)?.let(::render).orEmpty()
        val degree = children.getOrNull(1)?.let(::render).orEmpty()
        return "root($degree, $radicand)"
    }
    
    private fun renderSuperscript(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val exponent = children.getOrNull(1)?.let(::render).orEmpty()
        val formatted = base + (toSuperscript(exponent) ?: "^($exponent)")
        return appendLargeOperatorSpacing(base, formatted)
    }
    
    private fun renderSubscript(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val sub = children.getOrNull(1)?.let(::render).orEmpty()
        val formatted = base + (toSubscript(sub) ?: "_($sub)")
        return appendLargeOperatorSpacing(base, formatted)
    }
    
    private fun renderSubSuperscript(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val sub = children.getOrNull(1)?.let(::render).orEmpty()
        val sup = children.getOrNull(2)?.let(::render).orEmpty()
        val formatted = base + (toSubscript(sub) ?: "_($sub)") + (toSuperscript(sup) ?: "^($sup)")
        return appendLargeOperatorSpacing(base, formatted)
    }
    
     private fun renderUnder(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val under = children.getOrNull(1)?.let(::render).orEmpty()
        return appendLargeOperatorSpacing(base, "${base}_($under)")
    }
    
     private fun renderOver(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val over = children.getOrNull(1)?.let(::render).orEmpty()
        return appendLargeOperatorSpacing(base, "$base^($over)")
    }
    
    private fun renderUnderOver(children: List<Element>): String {
        val base = children.getOrNull(0)?.let(::render).orEmpty()
        val under = children.getOrNull(1)?.let(::render).orEmpty()
        val over = children.getOrNull(2)?.let(::render).orEmpty()
        return appendLargeOperatorSpacing(base, "${base}_($under)^($over)")
    }

    private fun renderTable(rows: List<Element>): String =
        rows.joinToString(prefix = "[", postfix = "]", separator = "; ") { row ->
            row.children().joinToString(", ", transform = ::render)
        }

    private fun renderIdentifier(text: String): String =
        if (text in FUNCTION_NAMES) "$text " else text
        
    private fun renderOperator(op: String): String =
        when (op) {
            "\u2061" -> ""
            "+", "−", "-", "=", "≠", "≤", "≥", "±", "∓", "×", "÷", "∈", "∉",
            "→", "←", "↔", "⇒", "⇐", "⇔", "∧", "∨" -> " $op "
            "," -> ", "
            else -> op
        }
    
    private fun appendLargeOperatorSpacing(base: String, expr: String): String =
        if (base.trim() in LARGE_OPERATORS) "$expr " else expr

    private fun toSuperscript(value: String): String? =
        value.map { SUPERSCRIPTS[it] ?: return null }.joinToString("")

    private fun toSubscript(value: String): String? =
        value.map { SUBSCRIPTS[it] ?: return null }.joinToString("")
        
     private val FUNCTION_NAMES = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "sinh", "cosh", "tanh", "log", "ln", "exp",
        "lim", "max", "min"
    )

    private val LARGE_OPERATORS = setOf("∑", "∏", "∫", "∬", "∭", "lim")
    
     private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'i' to 'ⁱ', 'n' to 'ⁿ'
    )
    
    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎'
    )
}