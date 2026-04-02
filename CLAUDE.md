# Compose Hot Reload

JetBrains open-source project enabling instant UI code changes without restarting a Compose Multiplatform application.

## Build & Test

```bash
# Build
./gradlew build

# Run all tests
./gradlew check

# Run a sample
./gradlew :samples:counter:hotRunJvm

# Publish to local repository (for testing in external projects)
./gradlew publishAllPublicationsToLocalRepository
```

## Key Modules

| Module | Purpose                                                                                                               |
|--------|-----------------------------------------------------------------------------------------------------------------------|
| `hot-reload-core` | Core hot reload functionality                                                                                         |
| `hot-reload-gradle-plugin` | Gradle plugin (shaded for plugin portal)                                                                              |
| `hot-reload-gradle-core` | Core Gradle integration                                                                                               |
| `hot-reload-gradle-idea` | Project/build model used by IntelliJ IDEA / Android Studio                                                            |
| `hot-reload-agent` | JVM agent that reloads classes on demand                                                                              |
| `hot-reload-orchestration` | Communication layer between Gradle, devtools, app, and IDE                                                            |
| `hot-reload-runtime-jvm` | JVM runtime: communicates with the Agent, listens for orchestration messages, invalidates Compose groups after reload |
| `hot-reload-runtime-api` | Public runtime API                                                                                                    |
| `hot-reload-annotations` | Kotlin annotations for hot reload API                                                                                 |
| `hot-reload-analysis` | Bytecode analysis that determines dirty Compose scopes/groups that should be invalidated after reload                 |
| `hot-reload-devtools` | Developer tools UI window                                                                                             |
| `hot-reload-mcp` | MCP server for AI agent integration                                                                                   |
| `hot-reload-test` | Test infrastructure and test plugins                                                                                  |
| `tests/` | Functional integration tests                                                                                          |
| `samples/` | Example projects (counter app)                                                                                        |
| `buildSrc/` | Build conventions and custom Gradle tasks                                                                             |

## Architecture

**Orchestration** — Communication between Gradle, the devtools, the Application, and the IDE. The devtools starts an orchestration server; all connected clients exchange broadcast messages (e.g., `ReloadClassesRequest`).

**Agent** — JVM agent added at launch in hot reload mode. Runs in the `premain` phase and uses `java.lang.instrument.Instrumentation.redefineClasses` to apply class updates on demand.

**Runtime** — A `dev` variant of the runtime added to the classpath in hot reload mode. Communicates with the Agent and listens for/broadcasts UI messages (e.g., `UIRendered`). After reload it integrates with Compose Runtime APIs to invalidate states and trigger re-renders.

**Recompiler** — Gradle Daemon in continuous mode that connects to orchestration and sends `ReloadClassesRequest` when `.class` files change.

**MCP Server** — [Model Context Protocol](https://modelcontextprotocol.io/) server that enables AI agents to interact with a running Compose application. Connects to orchestration as a tooling client using PID file polling — starts without a running application, waits for it, detects shutdown, and reconnects automatically. Exposes `status` and `take_screenshot` tools over stdio transport. Launched via the `hotMcpServer` Gradle task.

### DCEVM — Dynamic Code Evolution VM

Hot reload relies on JetBrains Runtime with **DCEVM** (Dynamic Code Evolution VM), which lifts standard JVM restrictions on `redefineClasses`. Regular JVMs only allow function body updates; DCEVM supports arbitrary changes including class structure modifications (new fields, changed hierarchies, etc.).

**State transfer during structural changes:** When class layouts change, DCEVM performs a GC-like pass — allocates new memory for modified objects, migrates existing field values, initializes new fields with JVM defaults (`null`/`0`/`false`), updates all pointers, and de-optimizes affected JIT compilations.

**Key JVM flags added by the Gradle plugin:**
```
-XX:+AllowEnhancedClassRedefinition   # enables DCEVM unrestricted redefinition
-javaagent:<path-to-agent>            # attaches the hot-reload agent
```

### Classpath Strategy

The Gradle plugin splits dependencies into two buckets:

- **Hot** — project source outputs (mutable; checksummed for change detection)
- **Cold** — remote/library dependencies (immutable; never reloaded)

This avoids unnecessary reload attempts on stable third-party code.

### What Can Be Reloaded

Functions, classes, parameters, added/removed fields, and nearly arbitrary code changes — without manual configuration. The `hot-reload-analysis` module performs bytecode analysis to determine reload compatibility.

## Using a Locally Built Version

1. Make code changes.
2. Set a custom version in `gradle.properties`, e.g. `version=1.0.0-DEBUG`.
3. Run `./gradlew publishAllPublicationsToLocalRepository`.
4. In your project's `settings.gradle.kts`, add:
   ```kotlin
   pluginManagement {
       repositories { maven("file://path/to/compose-hot-reload/build/repo") }
   }
   dependencyResolutionManagement {
       repositories { maven("file://path/to/compose-hot-reload/build/repo") }
   }
   ```
5. Update the plugin version to match: `id("org.jetbrains.compose.hot-reload") version "1.0.0-DEBUG"`

## Versioning

- Current version: `gradle.properties` → `version`
- Bootstrap version: `gradle.properties` → `bootstrap.version`
- Main branch for PRs: `staging`
- Release branches: `release/x.y.z-*`

## Requirements

- Kotlin 2.1.20+
- Compose Multiplatform 1.8.2+
- JetBrains Runtime with Java 21 or earlier target
- IntelliJ IDEA 2025.2.2+ or Android Studio Otter 2025.2.1+
