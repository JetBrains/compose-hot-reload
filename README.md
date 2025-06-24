# <img src="readme-assets/compose-logo.png" alt="drawing" width="30"/> Compose Hot Reload

[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.compose.hot-reload/hot-reload-core)](https://search.maven.org/artifact/org.jetbrains.compose.hot-reload/hot-reload-core)
[![GitHub license](https://img.shields.io/github/license/JetBrains/compose-hot-reload)](LICENSE.txt)
[![docs](https://img.shields.io/badge/documentation-blue)](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-hot-reload.html)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/compose-desktop/)

Iterate on your Compose UIs faster and let your creativity flow when building multiplatform UIs.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./readme-assets/banner_dark.png">
  <img alt="Text changing depending on mode. Light: 'So light!' Dark: 'So dark!'" src="./readme-assets/banner_light.png">
</picture>

Compose Hot Reload lets you make UI code changes in a Compose Multiplatform app and see the results instantly, no restarts needed.
Use JetBrains Runtime to intelligently reload your code whenever it changes.



There are two ways to add Compose Hot Reload to your project:
* Create a project from scratch in IntelliJ IDEA or Android Studio
* Add it as a Gradle plugin to an existing project

## Prerequisites
- Kotlin 2.1.20 or higher.
- Compose compiler 2.1.20 or higher.
- JetBrains runtime.

## Create a project from scratch

## Apply the Gradle plugin to your project

Add the `org.jetbrains.compose.hot-reload` Gradle plugin to your build script:

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.20" // <- Use Kotlin 2.1.20 or higher!
    kotlin("plugin.compose") version "2.1.20" // <- Use Compose Compiler Plugin 2.1.20 or higher!
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload") version "1.0.0-beta06" // <- add this additionally
}
```

## Run the application
### Multiplatform + IntelliJ
Using Kotlin Multiplatform and IntelliJ, launching your app is as simple as pressing 'run' on your main function:
<img alt="IntelliJ Run Gutter" src="./readme-assets/run-gutter.png">

### Gradle Tasks
#### Run Tasks

The plugin will create the following tasks to launch the application in 'hot reload mode':
- `:hotRunJvm`: Multiplatform, async alternative (`hotRunJvmAsync`)
- `:hotRun`: Kotlin/JVM, async alternative (`hotRunAsync`)
- 
____
**⚠️Note:**

Task names require adjustments if a custom target name is used. 
e.g. 
```kotlin
kotlin {
    jvm("desktop") 
         // ^
         // custom target name
}
```

Will lead to a task name of `hotRunDesktop` which can be invoked
```
./gradlew :app:hotRunDesktop --mainClass {{MainClass}}
```
____

**Arguments**

- `--mainClass <Main Class FQN>`:<br>
The main class to run.<br> 
_Example: `--mainClass com.example.MainKt`_


- `--autoReload` or `--auto`:<br>
Enable automatic reloading. Default: `false`.<br> 
_Example: `--autoReload`_<br>
_Example: `--auto`_


- `--no-autoReload` or `--no-auto`:
Disable automatic reloading.<br>
  _Example: `./gradlew :myApp:hotRunJvm --no-auto`_<br>

**Reload Task**

If the application was launched from CLI without the `--auto` option, 
then 'recompile + reloads' can be executed using the following task:
- `reload`: Generic task to reload all, currently running, applications.
- `hotReloadJvmMain`: Reload all applications that use the `jvmMain` source set.

The tasks 'mainClass' can be configured in the buildscript

**Using Compose Hot Reload:** 
```kotlin
tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("com.example.MainKt")
}
```

**Using Compose** 
```kotlin
compose.desktop {
    application {
        mainClass = "com.example.MainKt"
    }
}
```

or provided when invoking the task
```shell
./gradlew hotRunJvm --mainClass com.example.MainKt
```

## Set up automatic provisioning of the JetBrains Runtime (JBR) via Gradle

> [!IMPORTANT]  
> To use the full functionality of Compose Hot Reload, your project **must** run on the JetBrains Runtime (JBR, an OpenJDK fork that supports enhanced class redefinition).

Gradle can perform the download and setup for the JBR automatically for you via [Gradle Toolchains](https://github.com/gradle/foojay-toolchains).

Add the following to your `settings.gradle.kts`:
```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```
The Compose Hot Reload Gradle plugin will then use this resolver to automatically provision a compatible JDK.


## FAQ

### My multiplatform project doesn't have a Desktop target. Can I use Compose Hot Reload?

To use Compose Hot Reload, you'll have to add a module to your project that does configure a desktop target. It's worth noting that you will only be able to hot-reload code that can be run on the desktop JVM target. A possible setup might be the following:
- A `ui-components` module that specifies platform-agnostic UI components.
- A `gallery` module with a configured desktop target and Compose Hot Reload.

### I am developing an Android application and am not using Kotlin Multiplatform. Can I use Compose Hot Reload?

Compose Hot Reload is designed to work with Compose Multiplatform. If you'd like to use Compose Hot Reload with an Android-only project, you will have to:

- Switch from the Jetpack Compose plugin to the Compose Multiplatform plugin.
- Add a separate Gradle module and configure the `desktop` target according to the instructions above.

### My project is a desktop-only app with Compose Multiplatform. Can I use Compose Hot Reload?

Yes! However, please note that you can't start the application via the run button in the gutter ([CMP-3123](https://youtrack.jetbrains.com/issue/CMP-3123)). Instead, use the custom Gradle task as described above.


## Using 'dev' builds
The project publishes dev builds. To obtain the 'dev' Compose Hot Reload artifacts, first add the `firework` Maven repository:
In your projects' `settings.gradle.kts`, add the following:

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
