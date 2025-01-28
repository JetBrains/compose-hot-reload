import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.reload.test.HotReloadTestTask

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose-hot-reload")
    id("org.jetbrains.compose-hot-reload-test")
}

kotlin {
    jvm()

    sourceSets.jvmMain.dependencies {
        implementation("org.jetbrains.compose:hot-reload-runtime-api:1.0.0-dev.34.3")
    }

    sourceSets.getByName("jvmReloadTest").dependencies {
        implementation(kotlin("test"))
        implementation("ch.qos.logback:logback-classic:1.5.16")
    }
}

tasks.withType<HotReloadTestTask>().configureEach {
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
}
