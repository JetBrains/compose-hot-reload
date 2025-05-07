/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import kotlin.jvm.optionals.getOrNull

public inline fun <reified T : Annotation> ExtensionContext.findAnnotation(): T? {
    return AnnotationUtils.findAnnotation(testMethod, T::class.java).getOrNull()
        ?: AnnotationUtils.findAnnotation(testClass, T::class.java).getOrNull()
}

public inline fun <reified T : Annotation> ExtensionContext.findRepeatableAnnotations(): List<T> {
    return AnnotationUtils.findRepeatableAnnotations(testMethod, T::class.java)
        .plus(AnnotationUtils.findRepeatableAnnotations(testClass, T::class.java))
}

public inline fun <reified T : Annotation> ExtensionContext.hasAnnotation(): Boolean {
    return findAnnotation<T>() != null
}
