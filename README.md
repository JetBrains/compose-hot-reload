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

> [!IMPORTANT]  
> Compose Hot Reload only works if you have a desktop target in your multiplatform project. We're exploring adding support for 
> other targets in the future.

## Prerequisites
- Kotlin 2.1.20 or higher.
- Compose compiler 2.1.20 or higher.
- JetBrains runtime.

## Set up your project

There are two ways to add Compose Hot Reload to your project:

* Create a project from scratch in IntelliJ IDEA or Android Studio
* Add it as a Gradle plugin to an existing project

### Create a project from scratch

First, set up your environment with IntelliJ IDEA or Android Studio:

1. Install **IntelliJ IDEA 2025.1.1.1** or **Android Studio Narwhal 2025.1.1 RC 1**.
2. If you have:
   * MacOS, install the [Kotlin Multiplatform IDE plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform.).
   * Windows or Linux, install the following plugins:
     * [Android](https://plugins.jetbrains.com/plugin/22989-android)
     * [Android Design Tools](https://plugins.jetbrains.com/plugin/22990-android-design-tools)
     * [Jetpack Compose](https://plugins.jetbrains.com/plugin/18409-jetpack-compose)
     * [Native Debugging Support](https://plugins.jetbrains.com/plugin/12775-native-debugging-support)
     * [Compose Multiplatform for Desktop IDE Support](https://plugins.jetbrains.com/plugin/16541-compose-multiplatform-for-desktop-ide-support)
3. If you want to create Android applications, make sure that you have the `ANDROID_HOME` variable set. For example, in Bash or Zsh:
    ```
    export ANDROID_HOME=~/Library/Android/sdk
    ```
4. If you want to create iOS applications, you need a macOS host with [Xcode](https://apps.apple.com/us/app/xcode/id497799835) installed. Your IDE runs Xcode under the hood to build the necessary iOS frameworks.
  Make sure to launch Xcode before starting to work with your project so that it completes the initial set up.

#### On macOS

1. In IntelliJ IDEA, select **File** | **New** | **Project**.
2. In the panel on the left, select **Kotlin Multiplatform**.
3. Specify the **Name**, **Group**, and **Artifact** fields in the **New Project** window.
4. Select the **Desktop** target and click **Create**.
    <img alt="Create multiplatform project with desktop target" src="./readme-assets/create-desktop-project.png" width="500">

#### On Windows or Linux

1. Generate a project using the [web KMP wizard](https://kmp.jetbrains.com/). Make sure to select the desktop target.
2. Extract the archive and open the resulting folder in your IDE.

### Apply the Gradle plugin to your project

1. In your project, update the version catalog. In `gradle/libs.versions.toml`, add the following code:

   ```
   composeHotReload = { id = "org.jetbrains.compose.hot-reload", version.ref = "composeHotReload"}
   ```

   > To learn more about how to use a version catalog to centrally manage dependencies across your project, see our [Gradle best practices](https://kotlinlang.org/gradle-best-practices.html).

2. In the `build.gradle.kts` of your parent project, add the following code to your `plugins {}` block:

   ```
   plugins {
       alias(libs.plugins.composeHotReload) apply false
   }
   ```
   
   This prevents the Compose Hot Reload plugin from being loaded multiple times in each of your subprojects.

3. In the `build.gradle.kts` of the subproject containing your multiplatform application, add the following code to your `plugins {}` block:

   ```
   plugins { 
       alias(libs.plugins.composeHotReload)
   }
   ```

4. In your `settings.gradle.kts` file, add a plugin that's required for the Compose Hot Reload plugin:

   ```
   plugins {
       id("org.gradle.toolchains.foojay-resolver-convention") version "%foojayResolverConventionVersion%"
   }
   ```

5. Click the **Sync Gradle Changes** button to synchronize Gradle files:

    <img alt="Synchronize Gradle files" src="./readme-assets/gradle-sync.png" width="50">


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

### Optimization: Enable 'OptimizeNonSkippingGroups' (Optional):
Note: This optimization is not required, but will lead to a better user experience.
It is expected that the feature will be enabled by default in future versions of the compiler.

Add the following to your `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

// ...

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}
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

### I am developing an Android application and am not using Kotlin Multiplatform. Can I use Compose Hot Reload?

Compose Hot Reload is designed to work with Compose Multiplatform. If you'd like to use Compose Hot Reload with an Android-only project, you need to:

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
