/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// JVMTI agent for the Compose Hot Reload Android (ART) runtime.
//
// Purpose: redefine loaded classes' method bodies at runtime via a single
// jvmtiEnv::RedefineClasses call. On ART the replacement `class_bytes` must be
// a DEX file (every class must have its own dex).

#include <jni.h>
#include "jvmti.h"
#include <android/log.h>
#include <cstring>
#include <vector>

#define LOG_TAG "ComposeHotReloadAgent"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static jvmtiEnv *gJvmti = nullptr;

// Called by ART when the agent is attached via Debug.attachJvmtiAgent(...).
extern "C" JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
    LOGI("Agent_OnAttach");
    jvmtiEnv *jvmti = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION_1_2) != JNI_OK || jvmti == nullptr) {
        LOGE("GetEnv(JVMTI) failed");
        return JNI_ERR;
    }
    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_redefine_classes = 1;
    jvmtiError err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        LOGE("AddCapabilities(can_redefine_classes) failed: %d", err);
        return JNI_ERR;
    }
    gJvmti = jvmti;
    LOGI("JVMTI ready; can_redefine_classes acquired");
    return JNI_OK;
}

// JNI-linked native method for
// org.jetbrains.compose.reload.android.NativeRedefiner.redefine.
// Each `dexes[i]` must be a full DEX file that (re)defines `targets[i]`.
// One JVMTI call redefines the whole batch: all classes or none — the JVMTI
// contract, which ART keeps by validating every definition before installing
// anything.
extern "C" JNIEXPORT jint JNICALL
Java_org_jetbrains_compose_reload_android_NativeRedefiner_redefine(
        JNIEnv *env, jobject, jobjectArray targets, jobjectArray dexes) {
    if (gJvmti == nullptr) {
        LOGE("redefine called but agent not attached");
        return -1000;
    }
    jsize count = env->GetArrayLength(targets);
    if (count != env->GetArrayLength(dexes)) {
        LOGE("redefine called with %d targets but %d dexes", count,
             env->GetArrayLength(dexes));
        return -1001;
    }
    if (count == 0) {
        return 0;  // JVMTI treats an empty batch as success
    }

    std::vector<jbyteArray> dex_handles(static_cast<size_t>(count));
    std::vector<jbyte *> buffers(static_cast<size_t>(count));
    std::vector<jvmtiClassDefinition> defs(static_cast<size_t>(count));
    for (jsize i = 0; i < count; i++) {
        dex_handles[static_cast<size_t>(i)] =
                static_cast<jbyteArray>(env->GetObjectArrayElement(dexes, i));
        buffers[static_cast<size_t>(i)] =
                env->GetByteArrayElements(dex_handles[static_cast<size_t>(i)], nullptr);
        defs[static_cast<size_t>(i)].klass =
                static_cast<jclass>(env->GetObjectArrayElement(targets, i));
        defs[static_cast<size_t>(i)].class_byte_count =
                env->GetArrayLength(dex_handles[static_cast<size_t>(i)]);
        defs[static_cast<size_t>(i)].class_bytes =
                reinterpret_cast<const unsigned char *>(buffers[static_cast<size_t>(i)]);
    }

    jvmtiError err = gJvmti->RedefineClasses(static_cast<jint>(count), defs.data());
    for (jsize i = 0; i < count; i++) {
        env->ReleaseByteArrayElements(dex_handles[static_cast<size_t>(i)],
                                      buffers[static_cast<size_t>(i)], JNI_ABORT);
    }
    LOGI("RedefineClasses(%d) returned %d", count, err);
    return static_cast<jint>(err);
}
