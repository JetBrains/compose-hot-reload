plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.2.1")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "org.jetbrains.compose.reload.test"
        name = "Compose Hot Reload Test Fixtures"
        version = project.version.toString()
        description = "Compose Hot Reload Test Fixtuers"
    }
}

tasks.named<JavaExec>("runIde") {
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Didea.kotlin.plugin.use.k2=true")
    }
}

tasks.named("buildPlugin")

tasks.named("publishPlugin")

tasks.buildSearchableOptions.configure {
    enabled = false
}
