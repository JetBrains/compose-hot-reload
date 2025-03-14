/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

open class CheckPublicationTask : DefaultTask() {

    private val logger = project.logger

    @get:Input
    val projectVersion by lazy { project.version.toString() }

    @get:Input
    val projectGroupId by lazy { project.group.toString() }

    @get:InputDirectory
    val publicationDirectory = project.objects.directoryProperty()

    @TaskAction
    fun checkPublication() {
        val root = publicationDirectory.get().asFile
        if (!root.exists()) error("No publication directory found")
        if (!root.isDirectory) error("Publication directory is not a directory")
        checkPomFiles(root.toPath())
    }

    @OptIn(ExperimentalPathApi::class)
    private fun checkPomFiles(root: Path) {
        root.walk().forEach { path ->
            if (path.extension == "pom") checkPomFile(path)
            if (path.extension == "jar") checkJarFile(path)
            if (path.extension == "module") checkModuleFile(path)
        }
    }

    private fun checkPomFile(pom: Path) {
        val pomName = pom.relativeTo(publicationDirectory.get().asFile.toPath()).pathString

        logger.quiet("Checking '$pomName'")
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pom.toFile())

        document.getElementsByTagName("dependencies").toList()
            .flatMap { node -> node as Element; node.getElementsByTagName("dependency").toList() }
            .forEach { dependencyNode ->
                dependencyNode as Element
                if (dependencyNode.getSingleElement("groupId").textContent == projectGroupId) {
                    if (dependencyNode.getSingleElement("version").textContent != projectVersion) {
                        error("$pomName: Illegal version for dependency: ${dependencyNode.textContent}")
                    }
                }
            }
    }

    private fun checkModuleFile(module: Path) {
        logger.quiet("Checking '$module'")

        fun JsonElement.checkDependency() {
            val obj = this.jsonObject
            if (obj["group"]?.jsonPrimitive?.contentOrNull == projectGroupId) {
                val version = obj["version"] ?: return
                version.jsonObject.forEach { (key, value) ->
                    if (value.jsonPrimitive.content != projectVersion) {
                        error("$module: Illegal version for dependency: ${key}:${value.jsonPrimitive.content}")
                    }
                }
            }
        }

        fun JsonElement.check() {
            if (this is JsonArray) {
                forEach { child -> child.check() }
            }

            if (this !is JsonObject) return

            forEach { (key, value) ->
                if (key == "dependencies") {
                    val dependenciesArray = value.jsonArray
                    dependenciesArray.forEach { dependency -> dependency.checkDependency() }
                } else {
                    value.check()
                }
            }
        }

        val rootObject = Json {}.parseToJsonElement(module.toFile().readText()).jsonObject
        rootObject.check()
    }

    private fun checkJarFile(jar: Path) {
        val jarName = jar.relativeTo(publicationDirectory.get().asFile.toPath()).pathString
        logger.quiet("Checking '$jarName'")
        ZipFile(jar.toFile()).use { zip ->
            zip.entries().toList().forEach { entry ->
                if (!entry.name.endsWith(".class")) return@forEach
                val classNode = ClassNode()
                zip.getInputStream(entry).use { inputStream ->
                    ClassReader(inputStream).accept(classNode, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
                }

                /* 61: Java Version 17 */
                if (classNode.version > 61) {
                    error("$jarName: Illegal class version for class: ${classNode.name} (${classNode.version})")
                }
            }
        }
    }
}

private fun Element.getSingleElement(tag: String): Element {
    val elements = childNodes.toList().filterIsInstance<Element>().filter { it.tagName == tag }
    if (elements.isEmpty()) error("No '$tag' tag found")
    if (elements.size > 1) error("Multiple '$tag' tags found")
    return elements.first()
}

private fun NodeList.toList(): List<Node> {
    val list = mutableListOf<Node>()
    for (i in 0 until length) {
        val node = item(i)
        list.add(node)
    }

    return list
}
