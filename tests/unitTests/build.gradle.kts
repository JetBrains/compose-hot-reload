import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose-hot-reload-test")
}

tasks.reloadTest {
    dependsOn(":publishLocally")
    outputs.upToDateWhen { false }
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

dependencies {
    implementation(project(":hot-reload-runtime-api"))
    reloadTestImplementation(kotlin("test"))
    reloadTestImplementation(deps.logback)
}
