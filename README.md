# <img src="readme-assets/compose-logo.png" alt="drawing" width="30"/> Compose Hot Reload

[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin Beta](https://kotl.in/badges/stable.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.compose.hot-reload/hot-reload-core)](https://search.maven.org/artifact/org.jetbrains.compose.hot-reload/hot-reload-core)
[![GitHub license](https://img.shields.io/github/license/JetBrains/compose-hot-reload)](LICENSE.txt)
[![docs](https://img.shields.io/badge/documentation-blue)](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-hot-reload.html)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/compose-desktop/)

Build Compose UIs faster and let your creativity flow when designing multiplatform user interfaces.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./readme-assets/banner_dark.png">
  <img alt="Text changing depending on mode. Light: 'So light!' Dark: 'So dark!'" src="./readme-assets/banner_light.png">
</picture>

With Compose Hot Reload, you can make UI code changes in a Compose Multiplatform app and see the results instantly,
without needing to restart.
The JetBrains Runtime intelligently reloads your code whenever it changes.

> [!IMPORTANT]  
> Compose Hot Reload needs a JVM target in your multiplatform project. We're exploring adding support for
> other targets in the future.

## Prerequisites

Ensure that your project meets the minimum version requirements:

- Kotlin 2.1.20 or higher.
- Compose compiler 2.1.20 or higher.
- Compose Multiplatform 1.8.2 or higher.
- [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime):
  To be compatible with JetBrains Runtime, your project must
  [target](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_cross_compilation)
  Java 21 or earlier.

For the best development experience, we recommend using an IDE with the Kotlin Multiplatform plugin:

- IntelliJ IDEA 2025.2.2 or higher, or Android Studio Otter 2025.2.1 or higher.
- [Kotlin Multiplatform IDE plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform).

## Set up your project

There are two ways to add Compose Hot Reload to your project:

* [Create a project from scratch in IntelliJ IDEA or Android Studio](#create-a-project-from-scratch)
* [Add it as a Gradle plugin to an existing project](#apply-the-gradle-plugin-to-your-project)

### Create a project from scratch

Follow the [Kotlin Multiplatform quickstart](https://www.jetbrains.com/help/kotlin-multiplatform-dev/quickstart.html)
guide to set up your environment and create a project. Be sure to select the desktop target when you create the project.

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

3. In the `build.gradle.kts` of the subproject containing your multiplatform application, add the following code to your
   `plugins {}` block:

   ```
   plugins { 
       alias(libs.plugins.composeHotReload)
   }
   ```

4. An installation of the JetBrains Runtime is required:
   Launching Compose Hot Reload with the Kotlin Multiplatform IDE plugin will re-use IntelliJ's installation of the
   JetBrains Runtime.
   If you want Gradle to automatically download the JetBrains Runtime, add the following code to your
   `settings.gradle.kts` file
   ```
   plugins {
       id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
   }
   ```
    Alternatively, you can use automatic JetBrains Runtime provisioning for the hot reload tasks by enabling
    the `compose.reload.jbr.autoProvisioningEnabled` property.

> [!IMPORTANT]  
> Automatic JetBrains Runtime provisioning is an experimental feature. Please report any issues you encounter.

5. Click the **Sync Gradle Changes** button to synchronize Gradle files:

    <img alt="Synchronize Gradle files" src="./readme-assets/gradle-sync.png" width="50">

## Use Compose Hot Reload

You can run your application with Compose Hot Reload using your IDE or the CLI via Gradle tasks.
Compose Hot Reload supports two modes: **Explicit** mode and **Auto** mode.

* In **Explicit** mode, you manually trigger the reload to apply changes.
* In **Auto** mode, Compose Hot Reload uses Gradle’s file-watching and continuous build system
to automatically reload when file changes are detected.
To enable this mode, specify the `--autoReload` or `--auto` arguments in CLI 
or in the run configuration settings.

### In the IDE

In IntelliJ IDEA or Android Studio, you can run Compose Hot Reload directly from the IDE gutter.

1. Click the **Run** icon <img alt="Run main function" src="./readme-assets/run.png" width="12"> 
in the gutter of your main function and select **Run 'shared [jvm]' with Compose Hot Reload**.

2. When you save code changes, the reload is triggered automatically. 

   Alternatively, you can trigger the reload explicitly by pressing the assigned shortcut key or
   clicking the **Reload UI** button:

    <img src="/readme-assets/compose-hot-reload-floating-toolbar.png" alt="Reload UI in the IDE" width="528">

You can modify the trigger behavior on the **Settings | Tools | Compose Hot Reload** page in your IDE.

> [!IMPORTANT]  
> If you don't have the [Kotlin Multiplatform IDE plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)
> installed, you can manually create Gradle run configurations with hot reload tasks (see [Run tasks](#run-tasks)).
> In this case, you can trigger the reload by clicking the **Reload UI** button or running the `reload` Gradle task.

### From the CLI

#### Run tasks

The Compose Hot Reload plugin automatically creates the following tasks to launch the application:

- `:hotRunJvm`: For multiplatform projects. The async alternative is `:hotRunJvmAsync`.
- `:hotRun`: For Kotlin/JVM projects. The async alternative is `:hotRunAsync`.

You can run these Gradle tasks from the command line:

```shell
./gradlew :app:hotRunJvm
# or
./gradlew :composeApp:hotRunJvm
```

After making changes, save all files to automatically update your app's UI.

##### Custom target name

If you define a custom JVM target name, Gradle uses a different task name. For example, if your target name is
`desktop`:

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

#### Reload tasks (Explicit mode)

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

If you want to try the latest changes in Compose Hot Reload, you can use `dev` builds. To use the latest 'dev' builds of
Compose Hot Reload, add the `firework` Maven repository in your `settings.gradle.kts` file:

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

Compose Hot Reload is designed to work with Compose Multiplatform. To use Compose Hot Reload with an Android-only
project, you need to:

- Switch from the Jetpack Compose plugin to the Compose Multiplatform plugin.
- Add a separate Gradle module and configure the JVM target according
  to [the instructions](#apply-the-gradle-plugin-to-your-project).

### My project is a desktop-only app with Compose Multiplatform. Can I use Compose Hot Reload?

Yes! However, be aware that you can't start the application via the run button in the
gutter ([CMP-3123](https://youtrack.jetbrains.com/issue/CMP-3123)). Instead, use [Gradle tasks](#from-the-cli).

## Feedback and issues

Feel free to submit any feedback to our [GitHub issues](https://github.com/JetBrains/compose-hot-reload/issues) or 
[CMP issue tracker](https://youtrack.jetbrains.com/issues/CMP). If you encounter an issue, please check the 
[known issues and limitations](docs/Known_limitations.md) for potential workarounds. 
