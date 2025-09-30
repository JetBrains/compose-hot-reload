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
> Compose Hot Reload needs a JVM target in your multiplatform project. We're exploring adding support for 
> other targets in the future.

## Prerequisites

- Kotlin 2.1.20 or higher.
- Compose compiler 2.1.20 or higher.
- Compose 1.8.2 or higher
- JetBrains Runtime.

## Set up your project

There are two ways to add Compose Hot Reload to your project:

* [Create a project from scratch in IntelliJ IDEA or Android Studio](#create-a-project-from-scratch)
* [Add it as a Gradle plugin to an existing project](#apply-the-gradle-plugin-to-your-project)

### Create a project from scratch

Follow the [Kotlin Multiplatform quickstart](https://www.jetbrains.com/help/kotlin-multiplatform-dev/quickstart.html) guide to set up your environment and create a project. Be sure to select the desktop target when you create the project.

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
       id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
   }
   ```

5. Click the **Sync Gradle Changes** button to synchronize Gradle files:

    <img alt="Synchronize Gradle files" src="./readme-assets/gradle-sync.png" width="50">

## Use Compose Hot Reload

You can run your application with Compose Hot Reload from inside your IDE or from the CLI by using Gradle tasks.

### From the IDE

In IntelliJ IDEA or Android Studio, in the gutter, click the **Run** icon <img alt="Run main function" src="./readme-assets/run.png" width="12"> of your main function:

* If you have the [Kotlin Multiplatform IDE plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform) installed, select **Run 'MainKt [hotRunJvm]' with Compose Hot Reload (Beta)**.
* Otherwise, select **Run 'MainKt [jvm]'**.

### From the CLI

#### Run tasks

The Compose Hot Reload plugin automatically creates the following tasks to launch the application in 'hot reload mode':

- `:hotRunJvm`: For multiplatform projects. The async alternative is `:hotRunJvmAsync`.
- `:hotRun`: For Kotlin/JVM projects. The async alternative `:hotRunAsync`.

You can run these Gradle tasks from the command line:

```shell
./gradlew :app:hotRunJvm
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
- Add a separate Gradle module and configure the JVM target according to [the instructions](#apply-the-gradle-plugin-to-your-project).

### My project is a desktop-only app with Compose Multiplatform. Can I use Compose Hot Reload?

Yes! However, be aware that you can't start the application via the run button in the gutter ([CMP-3123](https://youtrack.jetbrains.com/issue/CMP-3123)). Instead, use [Gradle tasks](#from-the-cli).
