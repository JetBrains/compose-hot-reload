package org.jetbrains.compose.reload.analysis

val ClassId.classInitializerMethodId: MethodId
    get() = MethodId(this, "<clinit>", "()V")
