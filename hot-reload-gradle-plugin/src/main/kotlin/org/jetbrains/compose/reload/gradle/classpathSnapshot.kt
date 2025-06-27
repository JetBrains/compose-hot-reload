/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal fun ClasspathSnapshot(files: Iterable<File>): ClasspathSnapshot {
    val snapshot = ClasspathSnapshot()
    files.forEach { file ->
        if (!file.isFile || (file.extension != "jar" && file.extension != "zip")) return@forEach
        snapshot[file] = createJarSnapshot(file)
    }

    return snapshot
}

@ConsistentCopyVisibility
internal data class ClasspathSnapshot private constructor(
    private val jars: MutableMap<File, JarSnapshot>
) : Serializable {

    constructor() : this(mutableMapOf())

    operator fun get(file: File): JarSnapshot? {
        return jars[file.absoluteFile]
    }

    operator fun set(file: File, snapshot: JarSnapshot) {
        jars[file.absoluteFile] = snapshot
    }

    fun remove(file: File): JarSnapshot? = jars.remove(file.absoluteFile)
}

internal fun File.writeClasspathSnapshot(snapshot: ClasspathSnapshot) {
    return toPath().writeClasspathSnapshot(snapshot)
}

internal fun Path.writeClasspathSnapshot(snapshot: ClasspathSnapshot) {
    ObjectOutputStream(outputStream().buffered()).use { oos ->
        oos.writeObject(snapshot)
    }
}

internal fun File.readClasspathSnapshot(): ClasspathSnapshot {
    return toPath().readClasspathSnapshot()
}

internal fun Path.readClasspathSnapshot(): ClasspathSnapshot {
    return ObjectInputStream(inputStream().buffered()).use { ois ->
        @Suppress("UNCHECKED_CAST")
        ois.readObject() as ClasspathSnapshot
    }
}

internal data class JarSnapshot(
    /**
     * Index from the 'zip entry name' to the 'crc'
     */
    private val index: Map<String, Long> = mutableMapOf(),
) : Serializable {
    operator fun contains(entryName: String): Boolean =
        index.containsKey(entryName)

    fun crc(entryName: String): Long? = index[entryName]

    fun entries(): Collection<String> = index.keys
}


internal fun createJarSnapshot(file: File): JarSnapshot {
    val index = mutableMapOf<String, Long>()
    ZipFile(file).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            if (entry.isDirectory) return@forEach
            index.put(entry.name, entry.crc)
        }
    }
    return JarSnapshot(index)
}

internal data class ZipFileChanges(
    val snapshot: JarSnapshot,
    val changes: List<ZipFileChange>
)

internal sealed class ZipFileChange {
    sealed class Change
    data class Added(val entry: ZipEntry) : ZipFileChange()
    data class Modified(val entry: ZipEntry) : ZipFileChange()
    data class Removed(override val entryName: String) : ZipFileChange()

    open val entryName: String
        get() = when (this) {
            is Added -> entry.name
            is Modified -> entry.name
            is Removed -> entryName
        }
}

internal fun ZipFile.resolveChanges(snapshot: JarSnapshot): ZipFileChanges {
    val newIndex = mutableMapOf<String, Long>()
    val changes = mutableListOf<ZipFileChange>()
    val removedEntries = snapshot.entries().toHashSet()

    entries().asSequence().forEach { entry ->
        if (entry.isDirectory) return@forEach
        newIndex[entry.name] = entry.crc
        removedEntries.remove(entry.name)
        when (snapshot.crc(entry.name)) {
            entry.crc -> Unit
            null -> changes.add(ZipFileChange.Added(entry))
            else -> changes.add(ZipFileChange.Modified(entry))
        }
    }

    return ZipFileChanges(
        snapshot = JarSnapshot(newIndex),
        changes = changes + removedEntries.map { name -> ZipFileChange.Removed(name) }
    )
}
