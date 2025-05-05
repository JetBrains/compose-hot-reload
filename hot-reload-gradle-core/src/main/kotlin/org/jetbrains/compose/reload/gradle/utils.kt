/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.Locale
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@InternalHotReloadGradleApi
val String.capitalized
    get() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

@InternalHotReloadGradleApi
val String.decapitalized
    get() = this.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }

@InternalHotReloadGradleApi
fun camelCase(vararg nameParts: String?): String =
    nameParts.filterNotNull().filter { it.isNotBlank() }.joinToString("") { it.capitalized }.decapitalized

@InternalHotReloadGradleApi
val Project.kotlinMultiplatformOrNull: KotlinMultiplatformExtension?
    get() = extensions.getByName("kotlin") as? KotlinMultiplatformExtension

@InternalHotReloadGradleApi
val Project.kotlinJvmOrNull: KotlinJvmProjectExtension?
    get() = extensions.getByName("kotlin") as? KotlinJvmProjectExtension

@InternalHotReloadGradleApi
fun Project.files(lazy: () -> Any) = files({ lazy() })

@InternalHotReloadGradleApi
fun <T> NamedDomainObjectCollection<T>.forEachNamed(action: (name: String, provider: Provider<T>) -> Unit) {
    names.forEach { name ->
        action(name, named(name))
    }
}

@InternalHotReloadGradleApi
fun <T : Task> TaskCollection<T>.forEachTaskProvider(action: (provider: TaskProvider<T>) -> Unit) {
    names.forEach { name ->
        action(named(name))
    }
}

@InternalHotReloadGradleApi
fun Project.withComposePlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.compose") {
        block()
    }
}

@InternalHotReloadGradleApi
fun Project.withComposeCompilerPlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        block()
    }
}

@InternalHotReloadGradleApi
suspend fun awaitComposeCompilerPlugin() {
    val project = project()
    suspendCoroutine { continuation ->
        project.withComposeCompilerPlugin {
            continuation.resumeWith(Result.success(Unit))
        }
    }
}

@InternalHotReloadGradleApi
fun Project.withKotlinPlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        block()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        block()
    }
}


@InternalHotReloadGradleApi
suspend fun awaitKotlinPlugin() {
    val project = project()
    suspendCoroutine { continuation ->
        project.withKotlinPlugin {
            continuation.resumeWith(Result.success(Unit))
        }
    }
}

@InternalHotReloadGradleApi
suspend fun <T> Project.forAllJvmCompilations(block: suspend (compilation: KotlinCompilation<*>) -> T): List<T> {
    val futures = mutableListOf<Future<T>>()

    withKotlinPlugin {
        kotlinJvmOrNull?.target?.compilations?.all { compilation ->
            futures += future { block(compilation) }
        }
        kotlinMultiplatformOrNull?.targets?.withType(KotlinJvmTarget::class.java)?.all { target ->
            target.compilations.all { compilation ->
                futures += future { block(compilation) }
            }
        }
    }

    PluginStage.DeferredConfiguration.await()
    return futures.map { it.await() }
}

suspend fun <T> Project.forAllJvmTargets(block: suspend (target: KotlinTarget) -> T): List<T> {
    val futures = mutableListOf<Future<T>>()
    withKotlinPlugin {
        kotlinJvmOrNull?.target?.let { target ->
            futures += future { block(target) }
        }

        kotlinMultiplatformOrNull?.targets?.withType(KotlinJvmTarget::class.java)?.all { target ->
            futures += future { block(target) }
        }
    }

    PluginStage.DeferredConfiguration.await()
    return futures.map { it.await() }
}

@InternalHotReloadGradleApi
inline fun <reified T : Any> Path.readObject(): T {
    return ObjectInputStream(inputStream()).use { ois ->
        @Suppress("UNCHECKED_CAST")
        ois.readObject() as T
    }
}

@InternalHotReloadGradleApi
inline fun <reified T : Serializable> Path.writeObject(value: T) {
    return ObjectOutputStream(outputStream()).use { oos ->
        oos.writeObject(value)
        oos.flush()
    }
}

@InternalHotReloadGradleApi
fun Provider<String>.string() = StringProvider(this)

@InternalHotReloadGradleApi
class StringProvider(val property: Provider<String>) : Serializable {
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
