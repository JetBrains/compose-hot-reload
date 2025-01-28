package org.jetbrains.compose.reload.utils

import org.junit.jupiter.api.extension.ExtensionContext
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