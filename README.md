# <img src="readme-assets/compose-logo.png" alt="drawing" width="30"/> Compose Hot Reload

[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.compose.hot-reload/hot-reload-core)](https://search.maven.org/artifact/org.jetbrains.compose.hot-reload/hot-reload-core)
[![GitHub license](https://img.shields.io/github/license/JetBrains/compose-hot-reload)](LICENSE.txt)
[![docs](https://img.shields.io/badge/documentation-blue)](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-hot-reload.html)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/compose-desktop/)

Build Compose UIs faster and let your creativity flow when designing multiplatform user interfaces.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./readme-assets/banner_dark.png">
  <img alt="Text changing depending on mode. Light: 'So light!' Dark: 'So dark!'" src="./readme-assets/banner_light.png">
</picture>

With Compose Hot Reload, you can make UI code changes in a Compose Multiplatform app and see the results instantly, without needing to restart.
The JetBrains Runtime intelligently reloads your code whenever it changes.

> [!IMPORTANT]  
> Compose Hot Reload only works if you have a desktop target in your multiplatform project. We're exploring adding support for 
> other targets in the future.

## Prerequisites

- Kotlin 2.1.20 or higher.
- Compose compiler 2.1.20 or higher.
- JetBrains Runtime.

## Set up your project

There are two ways to add Compose Hot Reload to your project:

* [Create a project from scratch in IntelliJ IDEA or Android Studio](#create-a-project-from-scratch)
* [Add it as a Gradle plugin to an existing project](#apply-the-gradle-plugin-to-your-project)

### Create a project from scratch

First, set up your environment with IntelliJ IDEA or Android Studio:

1. Install **IntelliJ IDEA 2025.1.1.1** or **Android Studio Narwhal 2025.1.1**.
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
  Make sure to launch Xcode before starting to work with your project so that it completes the initial setup.

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

## Use Compose Hot Reload

You can run your application with Compose Hot Reload from inside your IDE or from the CLI by using Gradle tasks.

### From the IDE

In IntelliJ IDEA or Android Studio, in the gutter, click the **Run** icon of your main function. Select **Run 'composeApp [hotRunJvm]' with Compose Hot Reload (Beta)**.

<img alt="Run Compose Hot Reload from gutter" src="./readme-assets/compose-hot-reload-gutter-run.png" width="400">

### From the CLI

#### Run tasks

The Compose Hot Reload plugin automatically creates the following tasks to launch the application in 'hot reload mode':

- `:hotRunJvm`: For multiplatform projects. The async alternative is `:hotRunJvmAsync`.
- `:hotRun`: For Kotlin/JVM projects. The async alternative `:hotRunAsync`.

You can run these Gradle tasks from the command line:

```shell
./gradlew :app:hotRunDesktop
```

After making changes, save all files to automatically update your app's UI.

##### Custom target name

If you define a custom JVM target name, Gradle uses a different task name. For example, if your target name is `desktop`:

```kotlin
kotlin {
    jvm("desktop")
}
```

The task name is `:hotRunDesktop`.

##### Command-line arguments

Here's a list of all the possible arguments that you can use with the Gradle run tasks:

| Argument                           | Description                                   | Example                                                                         |
|------------------------------------|-----------------------------------------------|---------------------------------------------------------------------------------|
| `--mainClass <Main class FQN>`     | The main class to run.                        | `./gradlew :app:hotRunJvm --mainClass com.example.MainKt`                       |
| `--autoReload` <br> `--auto`       | Enable automatic reloading. Default: `false`. | `./gradlew :app:hotRunJvm --autoReload` <br> `./gradlew :app:hotRunJvm --auto`  |
| `--no-autoReload` <br> `--no-auto` | Disable automatic reloading.                  | `./gradlew :myApp:hotRunJvm --no-auto` <br> `./gradlew :myApp:hotRunJvm --auto` |

##### Configure the main class

You can configure the main class directly in your build script instead of passing it as a command-line argument.

You can configure it in the Compose Hot Reload task:

```kotlin
tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("com.example.MainKt")
}
```

Or if you use Compose Multiplatform, in the `application {}` block:

```kotlin
compose.desktop {
    application {
        mainClass = "com.example.MainKt"
    }
}
```

#### Reload tasks

> [!WARNING]  
> You can't run reload tasks with the `--autoReload` or `--auto` command-line argument.

The Compose Hot Reload plugin also provides Gradle tasks to recompile **and** reload your application:

- `reload`: Reload all, currently running, applications.
- `hotReloadJvmMain`: Reload all applications that use the `jvmMain` source set.

For example:

```shell
./gradlew :app:reload
```

## Compose Multiplatform optimization

If you're using Compose Multiplatform, you can configure the [`OptimizeNonSkippingGroups`](https://kotlinlang.org/api/kotlin-gradle-plugin/compose-compiler-gradle-plugin/org.jetbrains.kotlin.compose.compiler.gradle/-compose-feature-flag/-companion/-optimize-non-skipping-groups.html) feature flag to remove groups around non-skipping composable functions.
Enabling this feature flag can improve your app's runtime performance.

Add the following to your `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

// ...

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}
```

## Use developer builds

If you want to try the latest changes in Compose Hot Reload, you can use `dev` builds. To use the latest 'dev' builds of Compose Hot Reload, add the `firework` Maven repository in your `settings.gradle.kts` file:

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

## FAQ

### I'm developing an Android-only app without Kotlin Mutliplatform. Can I use Compose Hot Reload?

Compose Hot Reload is designed to work with Compose Multiplatform. To use Compose Hot Reload with an Android-only project, you need to:

- Switch from the Jetpack Compose plugin to the Compose Multiplatform plugin.
- Add a separate Gradle module and configure the `desktop` target according to [the instructions](#apply-the-gradle-plugin-to-your-project).

### My project is a desktop-only app with Compose Multiplatform. Can I use Compose Hot Reload?

Yes! However, be aware that you can't start the application via the run button in the gutter ([CMP-3123](https://youtrack.jetbrains.com/issue/CMP-3123)). Instead, use [Gradle tasks](#from-the-cli).
