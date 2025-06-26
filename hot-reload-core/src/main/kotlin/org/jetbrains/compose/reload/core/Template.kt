/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.core.ParsedTemplate.Block
import org.jetbrains.compose.reload.core.Template.ParseFailure

public interface Template {
    public fun render(values: Map<String, Any?>): Try<String>

    public class ParseFailure(message: String) : IllegalArgumentException(message)
    public class RenderFailure(message: String) : IllegalArgumentException(message)
}

public interface TemplateBuilder {
    public fun push(key: String, value: Any?)
    public fun set(key: String, value: Any?)
    public operator fun String.invoke(value: Any?): Unit = push(this, value)
}

public inline fun Template.render(values: TemplateBuilder.() -> Unit): Try<String> {
    val values = mutableMapOf<String, MutableList<Any?>>()
    object : TemplateBuilder {
        override fun push(key: String, value: Any?) {
            values.getOrPut(key) { mutableListOf() }.add(value)
        }

        override fun set(key: String, value: Any?) {
            values.set(key, mutableListOf(value))
        }
    }.values()
    return render(values)
}

public inline fun Template.renderOrThrow(values: TemplateBuilder.() -> Unit): String {
    return render(values).getOrThrow()
}

public fun Template.render(vararg values: Pair<String, Any?>): Try<String> {
    val map = mutableMapOf<String, MutableList<Any?>>()
    values.forEach { (key, value) ->
        map.getOrPut(key) { mutableListOf() }.add(value)
    }
    return render(map)
}

public fun Template.renderOrThrow(vararg values: Pair<String, Any?>): String {
    return render(*values).getOrThrow()
}

public fun Template(value: String): Try<Template> = value.asTemplate()

public fun String.asTemplate(): Try<Template> {
    val tokens = createTemplateTokens(this).leftOr { return it }
    return parseTemplateTokens(this, tokens)
}

public fun String.asTemplateOrThrow(): Template {
    return asTemplate().getOrThrow()
}

private fun createTemplateTokens(text: String): Try<List<TemplateToken>> {
    if (text.isEmpty()) return emptyList<TemplateToken>().toLeft()
    var index = 0
    val result = mutableListOf<TemplateToken>()

    while (index < text.length) {
        val nextToken = tokenizer.consume(index, text)
        if (nextToken == null) return ParseFailure("Cannot build tokens for template (index: $index").toRight()
        result.add(nextToken)
        index += nextToken.value.length
    }

    return result.toList().toLeft()
}

private fun parseTemplateTokens(template: String, tokens: List<TemplateToken>): Try<ParsedTemplate> {
    if (tokens.isEmpty()) return ParsedTemplate(template, emptyList()).toLeft()
    var context = ParsingContext(0, tokens)
    val parts = mutableListOf<ParsedTemplate.Part>()
    while (context.hasNext()) {
        val consumed = parsePart(context).leftOr { return it }
            ?: return ParseFailure("Failed parsing template part").toRight()

        parts.add(consumed.part)
        context = context.copy(index = context.index + consumed.tokens.size)
    }
    return ParsedTemplate(template, parts).toLeft()
}


private fun interface TemplateTokenizer {
    fun consume(startIndex: Int, text: String): TemplateToken?
}

private class RegexTemplateTokenizer(
    val regex: Regex, private val createToken: (MatchResult) -> TemplateToken?
) : TemplateTokenizer {
    override fun consume(startIndex: Int, text: String): TemplateToken? {
        val value = regex.matchAt(text, startIndex) ?: return null
        return createToken(value)
    }
}

private class ComposedTemplateTokenizer(
    private val tokenizers: List<TemplateTokenizer>
) : TemplateTokenizer {
    constructor(vararg tokenizers: TemplateTokenizer) : this(tokenizers.toList())

    override fun consume(startIndex: Int, text: String): TemplateToken? {
        return tokenizers.firstNotNullOfOrNull { tokenizer -> tokenizer.consume(startIndex, text) }
    }
}

private sealed class TemplateToken {
    abstract val value: String

    data class Word(override val value: String) : TemplateToken()
    data class Linebreak(override val value: String) : TemplateToken()
    data class Whitespace(override val value: String) : TemplateToken()
    data class DollarInterpolation(override val value: String) : TemplateToken()
    data class Variable(val key: String, override val value: String) : TemplateToken()
    data class IfToken(val requiredKey: String, override val value: String) : TemplateToken()
    data class EndifToken(override val value: String) : TemplateToken()
}

private val variableTokenizer = RegexTemplateTokenizer(
    Regex("""\{\h*\{\h*(?<key>(\w|\.)+)\h*\}\h*\}""")
) { result ->
    val key = result.groups["key"]?.value ?: return@RegexTemplateTokenizer null
    TemplateToken.Variable(key = key, value = result.value)
}

private val ifTokenizer = RegexTemplateTokenizer(
    Regex("""\h*\{\h*\{\h*if\h+(?<key>(\w|\.)+)\h*\}\h*\}\h*\v""")

) { result ->
    val key = result.groups["key"]?.value ?: return@RegexTemplateTokenizer null
    TemplateToken.IfToken(key, result.value)
}

private val endifTokenizer = RegexTemplateTokenizer(
    Regex("""\v\h*\{\h*\{/if\}\h*\}\h*""")
) { result ->
    TemplateToken.EndifToken(result.value)
}

private val linebreakTokenizer = RegexTemplateTokenizer(Regex("""\v+""")) { result ->
    TemplateToken.Linebreak(result.value)
}

private val whitespaceTokenizer = RegexTemplateTokenizer(Regex("""\h+""")) { result ->
    TemplateToken.Whitespace(result.value)
}

private val dollarInterpolationTokenizer = RegexTemplateTokenizer(Regex.fromLiteral("%%")) { result ->
    TemplateToken.DollarInterpolation(result.value)
}

private val wordTokenizer = RegexTemplateTokenizer(Regex("""\S+""")) token@{ result ->
    /* Ensure to yield for higher priority tokenizers */
    val priorityMatch = listOf(variableTokenizer.regex, ifTokenizer.regex, endifTokenizer.regex)
        .firstNotNullOfOrNull { regex -> regex.find(result.value) }

    if (priorityMatch != null) {
        return@token TemplateToken.Word(result.value.substring(0, priorityMatch.range.first))
    }

    TemplateToken.Word(result.value)
}


private val tokenizer = ComposedTemplateTokenizer(
    variableTokenizer,
    ifTokenizer,
    endifTokenizer,
    dollarInterpolationTokenizer,
    linebreakTokenizer,
    whitespaceTokenizer,
    wordTokenizer
)

private data class ParsingContext(
    val index: Int,
    val tokens: List<TemplateToken>,
    val endIndexExclusive: Int = tokens.size,
) : Iterable<TemplateToken> {

    init {
        require(endIndexExclusive <= tokens.size)
    }

    operator fun inc(): ParsingContext = skip(1)
    operator fun plus(tokens: Int): ParsingContext = skip(tokens)

    fun skip(tokens: Int): ParsingContext {
        return copy(index = index + tokens)
    }

    fun withEndIndexExclusive(endIndexExclusive: Int): ParsingContext {
        return copy(endIndexExclusive = index + endIndexExclusive)
    }

    fun hasNext(): Boolean {
        return index < endIndexExclusive
    }

    inline fun forEach(block: (TemplateToken) -> Unit) {
        for (i in index until endIndexExclusive) {
            block(tokens[i])
        }
    }

    override fun iterator(): Iterator<TemplateToken> {
        return tokens.subList(index, endIndexExclusive).iterator()
    }

    operator fun get(index: Int): TemplateToken? {
        if (this.index + index >= endIndexExclusive) return null
        return tokens[this.index + index]
    }

    data class Consumed(
        val tokens: List<TemplateToken>,
        val part: ParsedTemplate.Part,
    )

    override fun toString(): String {
        return toList().toString()
    }
}

private fun parsePart(context: ParsingContext): Try<ParsingContext.Consumed?> {
    /* Prioritize conditional part parsing */
    val conditionalPart = parseConditionalPart(context).leftOr { return it }
    if (conditionalPart != null) return conditionalPart.toLeft()

    /* Prioritize variable parsing */
    val variableLine = parseVariableLine(context).leftOr { return it }
    if (variableLine != null) return variableLine.toLeft()

    return parseBlock(context)
}

private fun parseBlock(context: ParsingContext): Try<ParsingContext.Consumed?> {
    var currentContext = context
    val block = mutableListOf<TemplateToken>()

    while (currentContext.hasNext()) {
        if (currentContext[0] is TemplateToken.IfToken) {
            val header = Block(block)
            val body = parseConditionalPart(currentContext).leftOr { return it }
            if (body == null) return ParseFailure("Failed parsing conditional part").toRight()
            val footerContext = currentContext.skip(body.tokens.size)
            val footer = parsePart(footerContext).leftOr { return it }
            if (footer == null) return ParseFailure("Failed parsing conditional part").toRight()
            return ParsingContext.Consumed(
                block + body.tokens + footer.tokens, ParsedTemplate.NestedPart(header, body.part, footer.part)
            ).toLeft()
        }


        val variableLine = parseVariableLine(currentContext).leftOr { return it }
        if (variableLine != null) {
            val header = Block(block)
            val footerContext = currentContext.skip(variableLine.tokens.size)
            val footer = parseBlock(footerContext).leftOr { return it }
            return ParsingContext.Consumed(
                block + variableLine.tokens + footer?.tokens.orEmpty(),
                ParsedTemplate.NestedPart(
                    header, variableLine.part, footer?.part
                ),
            ).toLeft()
        }

        if (currentContext[0] is TemplateToken.EndifToken)
            break

        block.add(currentContext[0] ?: return null.toLeft())
        currentContext = currentContext.skip(1)
    }

    return ParsingContext.Consumed(
        block, Block(block)
    ).toLeft()
}

private fun parseConditionalPart(context: ParsingContext): Try<ParsingContext.Consumed?> {
    val tokens = mutableListOf<TemplateToken>()
    var currentContext = context

    while (currentContext.hasNext()) {
        val token = currentContext[0] ?: return null.toLeft()
        when (token) {
            is TemplateToken.Whitespace -> {
                tokens.add(token)
                currentContext++
            }
            is TemplateToken.IfToken -> {
                tokens.add(token)
                currentContext++

                // search index of corresponding 'endIf'
                val endIfTokenIndex = run index@{
                    var parity = 1
                    currentContext.forEachIndexed { currentIndex, token ->
                        if (token is TemplateToken.IfToken) parity++
                        if (token is TemplateToken.EndifToken) parity--
                        if (parity == 0) return@index currentIndex
                    }

                    return ParseFailure("Missing '{{/if}}' for '${token.value.trim()}'").toRight()
                }

                /* Parse underlying part */
                val partParsingContext = currentContext.withEndIndexExclusive(endIfTokenIndex)
                val parsedBlock = parseBlock(partParsingContext).leftOr { return it }
                if (parsedBlock == null) return ParseFailure("Failed parsing conditional part").toRight()
                tokens.addAll(parsedBlock.tokens)
                currentContext += parsedBlock.tokens.size

                /* Check 'endif */
                val endif = currentContext[0] ?: return ParseFailure("Missing 'endif'").toRight()
                if (endif !is TemplateToken.EndifToken) return ParseFailure("'endif' expected, found $endif").toRight()
                tokens.add(endif)

                /* Build the conditional part */
                return ParsingContext.Consumed(
                    tokens, ParsedTemplate.ConditionalPart(token.requiredKey, parsedBlock.part)
                ).toLeft()
            }
            else -> return null.toLeft()
        }
    }

    return null.toLeft()
}


private fun parseVariableLine(context: ParsingContext): Try<ParsingContext.Consumed?> {
    val line = mutableListOf<ParsedTemplate.VariableLine.Content>()
    val tokens = mutableListOf<TemplateToken>()

    for (token in context) {
        if (token is TemplateToken.IfToken) return null.toLeft()
        if (token is TemplateToken.EndifToken) return null.toLeft()
        tokens.add(token)
        line.add(
            if (token is TemplateToken.Variable) ParsedTemplate.VariableLine.Variable(token.key)
            else ParsedTemplate.VariableLine.Constant(token)
        )
        if (token is TemplateToken.Linebreak) break
    }

    return if (line.any { content -> content is ParsedTemplate.VariableLine.Variable }) {
        ParsingContext.Consumed(tokens, ParsedTemplate.VariableLine(line)).toLeft()
    } else null.toLeft()
}

private class ParsedTemplate(
    private val template: String,
    private val parts: List<Part>
) : Template {
    sealed class Part
    data class Block(val tokens: List<TemplateToken>) : Part() {
        fun TemplateToken.render(): String = when (this) {
            is TemplateToken.DollarInterpolation -> "$"
            else -> value
        }

        override fun toString() = tokens.joinToString("") { token -> token.render() }
        fun render() = toString()
    }

    data class VariableLine(val content: List<Content>) : Part() {
        sealed class Content

        data class Constant(
            val token: TemplateToken
        ) : Content() {
            override fun toString(): String {
                return token.value
            }
        }

        data class Variable(val key: String) : Content()
    }

    data class NestedPart(val header: Part, val body: Part, val footer: Part?) : Part()

    data class ConditionalPart(val requiresKey: String, val part: Part) : Part()

    override fun render(values: Map<String, Any?>): Try<String> = buildString {
        parts.forEach forEach@{ part ->
            append(render(part, values))
        }
    }.toLeft()

    private fun render(part: Part, values: Map<String, Any?>): Try<String> = buildString {
        when (part) {
            is Block -> append(part.render())
            is VariableLine -> run {
                val resolved = resolve(part, values).leftOr { return it } ?: return@run
                append(resolved)
            }
            is ConditionalPart -> run {
                val resolvedValue = values[part.requiresKey] ?: return@run
                if (resolvedValue == false) return@run
                if (resolvedValue is Iterable<*>) {
                    val resolvedValues = resolvedValue.toList()
                    if (resolvedValues.isEmpty()) return@run
                    if (resolvedValues.any { it == false }) return@run
                }

                append(render(part.part, values))
            }
            is NestedPart -> {
                append(render(part.header, values))
                append(render(part.body, values))
                if (part.footer != null) append(
                    render(part.footer, values)
                )
            }
        }
    }.toLeft()

    private fun resolve(line: VariableLine, values: Map<String, Any?>): Try<String?> {
        val variables = line.content.filterIsInstance<VariableLine.Variable>()
        val expectedKey = variables.map { it.key }.toSet()

        val resolvedValues = expectedKey.associateWith { key ->
            val value = values[key] ?: emptyList<String>()
            if (value is Iterable<*>) value.map { it?.toString() }
            else listOf(value.toString())
        }

        /* check that we have equal number of pairs */
        val sizes = resolvedValues.mapValues { (_, values) -> values.size }
        val distinctSizes = sizes.values.toSet()

        if (distinctSizes.size != 1) return Template.RenderFailure(
            "Mismatching sizes for variables: $resolvedValues"
        ).toRight()

        val size = distinctSizes.single()
        if (size == 0) return null.toLeft()

        val prefix = line.content.takeWhile { content -> content is VariableLine.Constant }.joinToString("")
        val indentRegex = Regex("""\h+""")
        val indent = indentRegex.matchAt(prefix, 0)?.value.orEmpty()

        return buildString {
            repeat(size) { index ->
                // Defend against lines with null values (reject)
                resolvedValues.values.forEach { values ->
                    if (values.getOrNull(index) == null) return@repeat
                }

                line.content.forEach { content ->
                    when (content) {
                        is VariableLine.Constant -> append(content)
                        is VariableLine.Variable -> {
                            val lines = resolvedValues[content.key]?.getOrNull(index).orEmpty().lines()
                            if (lines.isEmpty()) {
                                return@forEach
                            }

                            if (lines.size == 1) {
                                append(lines.first())
                                return@forEach
                            }

                            append(lines.first())
                            lines.drop(1).forEach { line ->
                                appendLine()
                                append(indent)
                                append(line)
                            }
                        }
                    }
                }

                /* Manually append a new line if the line was not terminated with new line */
                if (size > 1 && index < size - 1 &&
                    line.content.last().let { it !is VariableLine.Constant || it.token !is TemplateToken.Linebreak }
                ) {
                    appendLine()
                }
            }
        }.toLeft()
    }

    override fun toString(): String {
        return template
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ParsedTemplate) return false
        return template == other.template
    }

    override fun hashCode(): Int {
        return template.hashCode()
    }
}
