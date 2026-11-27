/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidComposeCompilerArgsTest {
    @Test
    fun `test - function key meta annotations - kmp`() {
        assertFunctionKeyMetaAnnotations(AndroidProjectKind.Kmp, "compileComposeHotReloadKotlinAndroid")
    }

    @Test
    fun `test - function key meta annotations - kotlin android`() {
        assertFunctionKeyMetaAnnotations(AndroidProjectKind.KotlinAndroid, "compileComposeHotReloadKotlin")
    }

    @Test
    fun `test - class lambdas in the hot variant only - kmp`() {
        assertClassLambdas(
            AndroidProjectKind.Kmp,
            hotCompileTaskName = "compileComposeHotReloadKotlinAndroid", debugCompileTaskName = "compileDebugKotlinAndroid"
        )
    }

    @Test
    fun `test - class lambdas in the hot variant only - kotlin android`() {
        assertClassLambdas(
            AndroidProjectKind.KotlinAndroid,
            hotCompileTaskName = "compileComposeHotReloadKotlin", debugCompileTaskName = "compileDebugKotlin"
        )
    }

    private fun assertClassLambdas(kind: AndroidProjectKind, hotCompileTaskName: String, debugCompileTaskName: String) {
        val project = androidProject(kind)
        project.evaluate()
        val classLambdaArgs = listOf("-Xlambdas=class", "-Xsam-conversions=class")

        val hotArgs = project.tasks.withType<KotlinJvmCompile>().getByName(hotCompileTaskName)
            .compilerOptions.freeCompilerArgs.get()
        val debugArgs = project.tasks.withType<KotlinJvmCompile>().getByName(debugCompileTaskName)
            .compilerOptions.freeCompilerArgs.get()

        assertTrue(hotArgs.containsAll(classLambdaArgs), "Missing $classLambdaArgs in '$hotCompileTaskName': $hotArgs")
        assertTrue(
            classLambdaArgs.none { it in debugArgs },
            "'$debugCompileTaskName' must keep the default lambdas: $debugArgs"
        )
    }

    private fun assertFunctionKeyMetaAnnotations(kind: AndroidProjectKind, compileTaskName: String) {
        val project = androidProject(kind)
        project.evaluate()

        val compileTask = project.tasks.withType<KotlinJvmCompile>().getByName(compileTaskName)
        val args = compileTask.compilerOptions.freeCompilerArgs.get()
        assertTrue(
            "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true" in args,
            "Missing 'generateFunctionKeyMetaAnnotations' in '$compileTaskName': $args"
        )
    }
}
