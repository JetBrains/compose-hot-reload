/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.idea.tests

import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotReloadModel
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotReloadModelImpl
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotRunTask
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun `test - sample 1`() {
        val model = IdeaComposeHotReloadModel(
            "foo", runTasks = listOf(
                IdeaComposeHotRunTask(
                    taskName = "task1",
                    targetName = "target1",
                    compilationName = "compilation1",
                    argFile = Path("myArgsFile"),
                    argFileTaskName = "myArgsFileTask"
                )
            )
        )


        assertEquals(model, model.serialize().deserialize<IdeaComposeHotReloadModel>())
    }

    @Test
    fun `test - malformed json - is lenient`() {
        val model = IdeaComposeHotReloadModelImpl.Surrogate(
            """
            {
                "unknownField": "myValue", 
                "runTasks": [
                    {
                        "taskName": "task1", 
                        "unknownField": "myOtherValue"
                    }
                ]
            }
        """.trimIndent().encodeToByteArray()
        ).serialize().deserialize<IdeaComposeHotReloadModel>()

        assertEquals(
            IdeaComposeHotReloadModel(runTasks = listOf(IdeaComposeHotRunTask(taskName = "task1"))),
            model
        )
    }
}

private fun Any.serialize(): ByteArray {
    val baos = ByteArrayOutputStream()
    ObjectOutputStream(baos).writeObject(this)
    return baos.toByteArray()
}

private inline fun <reified T> ByteArray.deserialize(): T {
    return ObjectInputStream(inputStream()).use { stream -> stream.readObject() as T }
}
