package org.jetbrains.compose.reload.core

public interface Template {
    public fun render(values: Map<String, Any?>): Try<String>

    public class ParseFailure(message: String) : IllegalArgumentException(message)
    public class RenderFailure(message: String) : IllegalArgumentException(message)
}

public interface TemplateBuilder {
    public fun push(key: String, value: Any?)
    public fun set(key: String, value: Any?)
    public operator fun String.invoke(value: Any?) = push(this, value)
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
        if (nextToken == null) return Template.ParseFailure("Cannot build tokens for template (index: $index").toRight()
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
        val consumed = parsePart(context) ?: return Template.ParseFailure("Failed parsing template part").toRight()
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
}

private val variableTokenizer = RegexTemplateTokenizer(
    Regex("""\{\h*\{\h*(?<key>(\w|\.)+)\h*\}\h*\}""")
) { result ->
    val key = result.groups["key"]?.value ?: return@RegexTemplateTokenizer null
    TemplateToken.Variable(key = key, value = result.value)
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
    /* Yield for variableTokenizer tokenizers */
    variableTokenizer.regex.find(result.value)?.let { variableResult ->
        return@token TemplateToken.Word(result.value.substring(0, variableResult.range.first))
    }

    /* Yield for dollarInterpolationTokenizer tokenizers */
    dollarInterpolationTokenizer.regex.find(result.value)?.let { variableResult ->
        return@token TemplateToken.Word(result.value.substring(0, variableResult.range.first))
    }

    TemplateToken.Word(result.value)
}


private val tokenizer = ComposedTemplateTokenizer(
    variableTokenizer,
    dollarInterpolationTokenizer,
    linebreakTokenizer,
    whitespaceTokenizer,
    wordTokenizer
)

private data class ParsingContext(
    val index: Int,
    val tokens: List<TemplateToken>,
) : Iterable<TemplateToken> {
    fun hasNext(): Boolean = index < tokens.size

    inline fun forEach(block: (TemplateToken) -> Unit) {
        for (i in index until tokens.size) {
            block(tokens[i])
        }
    }

    override fun iterator(): Iterator<TemplateToken> {
        return tokens.listIterator(index)
    }

    operator fun get(index: Int): TemplateToken? {
        return tokens.getOrNull(this.index + index)
    }

    data class Consumed(
        val tokens: List<TemplateToken>,
        val part: ParsedTemplate.Part,
    ) : RuntimeException()
}

private fun parsePart(context: ParsingContext): ParsingContext.Consumed? {
    /* Prioritize variable parsing */
    parseVariableLine(context)?.let { return it }

    var currentContext = context
    val block = mutableListOf<TemplateToken>()

    fun TemplateToken.render(): String = when (this) {
        is TemplateToken.DollarInterpolation -> "$"
        else -> value
    }

    fun build(): ParsingContext.Consumed {
        return ParsingContext.Consumed(block, ParsedTemplate.Block(block.joinToString("") { it.render() }))
    }

    while (currentContext.hasNext()) {
        if (parseVariableLine(currentContext) != null) {
            return build()
        }
        block.add(currentContext[0] ?: return null)
        currentContext = currentContext.copy(currentContext.index + 1)
    }

    return build()
}

private fun parseVariableLine(context: ParsingContext): ParsingContext.Consumed? {
    val line = mutableListOf<ParsedTemplate.VariableLine.Content>()
    val tokens = mutableListOf<TemplateToken>()

    for (token in context) {
        tokens.add(token)
        line.add(
            if (token is TemplateToken.Variable) ParsedTemplate.VariableLine.Variable(token.key)
            else ParsedTemplate.VariableLine.Constant(token)
        )
        if (token is TemplateToken.Linebreak) break
    }

    return if (line.any { content -> content is ParsedTemplate.VariableLine.Variable }) {
        ParsingContext.Consumed(tokens, ParsedTemplate.VariableLine(line))
    } else null
}

private class ParsedTemplate(
    private val template: String,
    private val parts: List<Part>
) : Template {
    sealed class Part
    data class Block(val value: String) : Part()
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

    override fun render(values: Map<String, Any?>): Try<String> = buildString {
        parts.forEachIndexed forEach@{ index, part ->
            when (part) {
                is Block -> append(part.value)
                is VariableLine -> {
                    val resolved = resolve(part, values).leftOr { return it } ?: return@forEach
                    append(resolved)
                }
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
