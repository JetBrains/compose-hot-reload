package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.jupiter.api.extension.ExtensionContext

public enum class TestedGradleVersion(public val version: String) {
    G_8_7("8.7"),
    G_8_11("8.11.1");

    override fun toString(): String {
        return version
    }
}

public enum class TestedKotlinVersion(public val version: KotlinToolingVersion) {
    KT_2_1_20(KotlinToolingVersion("2.1.20-Beta2")),
    ;

    override fun toString(): String {
        return version.toString()
    }
}

public enum class TestedComposeVersion(public val version: String) {
    C_1_7(KotlinToolingVersion("1.7.3").toString()), ;

    override fun toString(): String {
        return version
    }
}

internal enum class TestedAndroidVersion(val version: String) {
    AGP_8_5("8.5.2"),
    AGP_8_7("8.7.1");

    override fun toString(): String {
        return version
    }
}

internal var ExtensionContext.kotlinVersion: TestedKotlinVersion? by extensionContextProperty()
internal var ExtensionContext.gradleVersion: TestedGradleVersion? by extensionContextProperty()
internal var ExtensionContext.composeVersion: TestedComposeVersion? by extensionContextProperty()
internal var ExtensionContext.androidVersion: TestedAndroidVersion? by extensionContextProperty()
