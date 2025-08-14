/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package org.jetbrains.compose.reload.orchestration.utils


import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction


private fun <T> createProxy(instance: Any, interfaceClass: Class<T>, vararg additionalInterfaces: Class<T>): T {
    return interfaceClass.cast(
        Proxy.newProxyInstance(
            interfaceClass.classLoader, arrayOf(interfaceClass, *additionalInterfaces)
        ) { _, sourceMethod, sourceArgs ->
            if (sourceMethod.name == "getProxyInstance") return@newProxyInstance instance

            val targetClass = instance.javaClass

            val targetMethod = targetClass.methods.first { targetMethod ->
                targetMethod.name == sourceMethod.name && targetMethod.parameterCount == sourceMethod.parameterCount &&
                    targetMethod.parameterTypes.withIndex().all { (index, type) ->
                        type.simpleName == sourceMethod.parameterTypes[index].simpleName
                    } && targetMethod.returnType.simpleName == sourceMethod.returnType.simpleName
            }

            val targetArgs = sourceArgs?.mapIndexed { index, sourceArg ->
                val targetArgumentType = targetMethod.parameterTypes[index]

                if (sourceArg == null || targetArgumentType.isInstance(sourceArg)) sourceArg
                else createProxy(sourceArg, targetArgumentType)
            }

            val result = try {
                if (targetArgs == null) targetMethod.invoke(instance)
                else targetMethod.invoke(instance, *targetArgs.toTypedArray())
            } catch (t: Throwable) {
                println("Failed to invoke method: $sourceMethod (target: $targetClass)")
                throw t
            }


            if (result == null) return@newProxyInstance null
            result
        }
    )
}

fun Any.invoke(methodName: String): Any {
    return this.javaClass.getMethod(methodName).invoke(this)
}

inline fun <reified P1> Any.invoke(methodName: String, arg: P1): Any {
    return this.javaClass.getMethod(methodName, P1::class.java).invoke(this, arg)
}

inline fun <reified P1, reified P2> Any.invoke(methodName: String, arg1: P1, arg2: P2): Any {
    return this.javaClass.getMethod(methodName, P1::class.java, P2::class.java).invoke(this, arg1, arg2)
}

suspend fun Any.invokeSuspend(methodName: String): Any? {
    val classQueue = ArrayDeque<Class<*>>()
    classQueue.add(this.javaClass)

    val method = run {
        while (classQueue.isNotEmpty()) {
            val candidate = classQueue.removeFirst()
            classQueue.addAll(candidate.interfaces)
            candidate.superclass?.let { classQueue.add(it) }

            if (candidate.modifiers and Modifier.PUBLIC == 0) continue
            val methodResult = runCatching { candidate.getMethod(methodName, Continuation::class.java) }
            val method = methodResult.getOrNull() ?: continue
            return@run method
        }
        error("Method '$methodName' not found in class hierarchy of '${this.javaClass.name}'")
    }

    return method.kotlinFunction!!.callSuspend(this)
}
