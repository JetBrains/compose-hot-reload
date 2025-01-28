package org.jetbrains.compose.reload.utils

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.jupiter.api.extension.ExtensionContext

enum class TestedGradleVersion(val version: String) {
    G_8_7("8.7"),
    G_8_11("8.11.1");

    override fun toString(): String {
        return version
    }
}

enum class TestedKotlinVersion(val version: KotlinToolingVersion) {
    KT_2_1_20(KotlinToolingVersion("2.1.20-Beta2")),
    ;

    override fun toString(): String {
        return version.toString()
    }
}

enum class TestedComposeVersion(val version: String) {
    C_1_7(KotlinToolingVersion("1.7.3").toString()), ;

    override fun toString(): String {
        return version
    }
}

enum class TestedAndroidVersion(val version: String) {
    AGP_8_5("8.5.2"),
    AGP_8_7("8.7.1");

    override fun toString(): String {
        return version
    }
}

var ExtensionContext.kotlinVersion: TestedKotlinVersion? by extensionContextProperty()
var ExtensionContext.gradleVersion: TestedGradleVersion? by extensionContextProperty()
var ExtensionContext.composeVersion: TestedComposeVersion? by extensionContextProperty()
var ExtensionContext.androidVersion: TestedAndroidVersion? by extensionContextProperty()
