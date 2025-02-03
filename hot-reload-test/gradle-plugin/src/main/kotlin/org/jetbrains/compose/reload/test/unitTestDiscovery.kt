package org.jetbrains.compose.reload.test

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

internal data class TestMethodCoordinates(
    val className: String, val methodName: String
) {
    override fun toString(): String {
        return "$className.$methodName"
    }
}

internal fun Path.findTestMethods(): List<TestMethodCoordinates> {
    if (this.isDirectory()) {
        return this.listDirectoryEntries().flatMap { it.findTestMethods() }
    }

    if (this.extension == "class") {
        val bytecode = readBytes()
        val reader = ClassReader(bytecode)
        val classNode = ClassNode(ASM9)
        reader.accept(classNode, 0)
        return classNode.methods.filter { method ->
            method.visibleAnnotations.orEmpty()
                .any { annotation -> annotation.desc == "Lorg/jetbrains/compose/reload/test/HotReloadUnitTest;" }
        }.map { method -> TestMethodCoordinates(classNode.name.replace("/", "."), method.name) }
    }

    if (this.extension == "jar") {
        return ZipFile(this.toFile()).use { zipFile ->
            zipFile.entries().asSequence().flatMap { entry ->
                if (!entry.name.endsWith(".class")) return@flatMap emptySequence()
                zipFile.getInputStream(entry).use { entryStream ->
                    val reader = ClassReader(entryStream)
                    val classNode = ClassNode(ASM9)
                    reader.accept(classNode, 0)
                    classNode.findTestMethods().asSequence()
                }

            }.toList()
        }
    }

    return emptyList()
}

private fun ClassNode.findTestMethods(): List<TestMethodCoordinates> {
    return methods.filter { method ->
        method.visibleAnnotations.orEmpty()
            .any { annotation -> annotation.desc == "Lorg/jetbrains/compose/reload/test/HotReloadTest;" }
    }.map { method -> TestMethodCoordinates(name.replace("/", "."), method.name) }
}
