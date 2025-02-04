# 🔥 Compose Hot Reload

[![JetBrains team project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

## Intro

This repository contains recent experiments for Hot Reloading Compose Applications.  
The intent is to upstream this repository into an official JetBrains product.

No guarantees apply.

## State

The project publishes experimental builds

### Add the 'firework' maven repository

(settings.gradle.kts)

```kotlin
pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
    }
}

```

### Apply the Gradle plugin to your project

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.20-Beta2" // <- Use Kotlin 2.1.20-Beta2 or higher!
    kotlin("plugin.compose") version "2.1.20-Beta2" // <- Use Compose Compiler Plugin 2.1.20-Beta2 or higher!
    id("org.jetbrains.compose")
    id("org.jetbrains.compose-hot-reload") version "1.0.0-dev.34.6" // <- add this additionally
}
```

### Enable 'OptimizeNonSkippingGroups' in your build.gradle.kts

```kotlin
composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}
```

### Optional: Create a custom entry point to launch your hot application

```kotlin
// build.gradle.kts
tasks.register<ComposeHotRun>("runHot") {
    mainClass.set("my.app.MainKt")
}
```

#### 💡The JBR can also be downloaded automatically by Gradle (foojay)

https://github.com/gradle/foojay-toolchains

```kotlin
// settings.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```

### Provide an Entry Point for your UI to hot-reload

```kotlin
@Composable
fun App() {
    DevelopmentEntryPoint {
        MainPage()
    }
}
```
