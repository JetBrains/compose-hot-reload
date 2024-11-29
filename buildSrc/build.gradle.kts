plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal {
        mavenContent {
            includeGroupByRegex("org.jetbrains.kotlin.*")
        }
    }


    google {
        mavenContent {
            includeGroupByRegex(".*google.*")
            includeGroupByRegex(".*android.*")
        }
    }
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin:${deps.versions.kotlin.get()}"))
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${deps.versions.kotlin.get()}")
    implementation("org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:${deps.versions.compose.get()}")
    implementation("com.android.tools.build:gradle:${deps.versions.androidGradlePlugin.get()}")
}
