import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.reload.test.HotReloadUnitTestTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    id("org.jetbrains.compose.hot-reload.test")
}

kotlin {
    jvm()

    sourceSets.jvmMain.dependencies {
        implementation("org.jetbrains.compose.hot-reload:runtime-api:1.0.0-alpha02-69")
        implementation(compose.runtime)
    }

    sourceSets.getByName("jvmReloadUnitTest").dependencies {
        implementation(kotlin("test"))
        implementation(deps.coroutines.test)
        implementation("ch.qos.logback:logback-classic:1.5.16")
    }
}

tasks.withType<HotReloadUnitTestTask>().configureEach {
    testLogging {
        events = setOf(
            TestLogEvent.STARTED,
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR,
        )
    }
    outputs.upToDateWhen { false }
}
