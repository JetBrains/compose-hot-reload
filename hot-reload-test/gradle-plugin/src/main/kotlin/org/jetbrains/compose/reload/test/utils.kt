package org.jetbrains.compose.reload.test

import org.gradle.api.provider.Provider
import java.io.Serializable


internal fun Provider<String>.string() = StringProvider(this)

internal class StringProvider(val property: Provider<String>) : Serializable {
    override fun toString(): String {
        return property.get()
    }

    fun writeReplace(): Any {
        return property.get()
    }

    fun readResolve(): Any {
        return property.get()
    }
}

internal fun lowerCamelCase(vararg parts: String?): String {
    return buildString {
        parts.filterNotNull().filter { it.isNotBlank() }.forEachIndexed { i, part ->
            if (i > 0) append(part.replaceFirstChar { it.uppercaseChar() })
            else append(part)
        }
    }
}
