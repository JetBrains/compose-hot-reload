# Run Tests

```
./gradlew check
```

# Architecture Overview

## Orchestration

The communication between a build tool (e.g., Gradle), the Application and tools such as the IDE is handled by
the 'orchestration.' If not instructed otherwise, the Application under hot reload will start a new orchestration
server. All connected clients can exchange messages as broadcasts. For example, the build tool might broadcast
a 'ReloadClassesRequest' after recompiling.

Tests or tools like the IDE might create their own orchestration server and instruct the Application to
connect to this server in 'client mode' by providing the necessary system properties.

## Agent

When an application is launched in 'hot reload mode,' then a special Compose Hot Reload agent will be
added to the jvm execution. This agent will participate in the orchestration and reload classes on demand.

## Runtime

When an application is launched in 'hot reload mode' then a special 'dev' variant of the runtime will be
added to the runtime classpath. This variant will be able to communicate with the Agent and also
participate in the orchestration by listening for messages and broadcasting UI related messages
(such as UIRendered).

## Recompiler

When the dev runtime is launched, a Gradle Daemon is launched in continuous mode, which will connect
to the orchestration and send 'ReloadClassesRequests' when the actual .class files for the runtime classpath
changes.

# Use locally built version in your project

1. Make code changes.
2. Change the version in `gradle.properties`, e.g. to `1.0.0-DEBUG`.
3. Run `gradlew publishAllPublicationsToLocalRepository`.
4. In your local project, add the following entries to your `settings.gradle.kts`:

```gradle
pluginManagement {
    repositories {
        maven("file://path/to/compose-hot-reload/build/repo")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("file://path/to/compose-hot-reload/build/repo")
    }
}
```

5. In your local project, update the version of `compose-hot-reload` to whatever you set in step 1:

```
id("org.jetbrains.compose.hot-reload") version "1.0.0-DEBUG"
```

6. Run project as usual.
