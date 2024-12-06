plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        instrumentationTools()
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation(project(":hot-reload-orchestration"))
    implementation(deps.jewel.ide)
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "org.jetbrains.compose.hotreload"
        name = "Compose Hot Reload"
        version = "1.0.0"
        description = "Compose Hot Reload Experiments"
    }
}