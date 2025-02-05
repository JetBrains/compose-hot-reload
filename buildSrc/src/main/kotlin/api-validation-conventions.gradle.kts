/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalBCVApi::class)

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi

plugins {
    org.jetbrains.kotlinx.`binary-compatibility-validator`
}

extensions.configure<ApiValidationExtension> {
    klib { enabled = true }
}
