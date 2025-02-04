package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.invoke.MethodHandles
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal val namespace = ExtensionContext.Namespace.create("ComposeReload")

internal inline fun <reified T> extensionContextProperty() = object : ReadWriteProperty<ExtensionContext, T?> {

    val pkg = MethodHandles.lookup().lookupClass().packageName

    override fun getValue(thisRef: ExtensionContext, property: KProperty<*>): T? {
        val key = "$pkg.${property.name}"
        return thisRef.getStore(namespace).get(key, T::class.java)?.let(T::class.java::cast)
    }

    override fun setValue(
        thisRef: ExtensionContext, property: KProperty<*>, value: T?
    ) {
        val key = "$pkg.${property.name}"
        return thisRef.getStore(namespace).put(key, value)
    }
}

internal inline fun <reified T : Any> SimpleValueProvider(value: T): SimpleValueProvider<T> {
    return SimpleValueProvider(T::class.java, value)
}

internal class SimpleValueProvider<T : Any>(
    private val type: Class<T>, private val value: T,
) : ParameterResolver {
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == type
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return value
    }
}
