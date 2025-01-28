plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":hot-reload-core"))
    testImplementation(project(":hot-reload-orchestration"))
    testImplementation(kotlin("tooling-core"))
    testImplementation(deps.coroutines.core)
    testImplementation(deps.coroutines.test)
    testImplementation(deps.slf4j.api)
    testImplementation(deps.logback)
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
}

val testWarmup by tasks.registering(Test::class) {
    val test = kotlin.target.compilations.getByName("test")
    testClassesDirs = test.output.classesDirs
    classpath = test.output.allOutputs + test.runtimeDependencyFiles
    useJUnitPlatform { includeTags("Warmup") }

    val outputMarker = layout.buildDirectory.file("gradleHome/warmup.marker")
    outputs.file(outputMarker)
    outputs.upToDateWhen { outputMarker.get().asFile.exists() }
    onlyIf { !outputMarker.get().asFile.exists() }

    doLast {
        outputMarker.get().asFile.writeText("Warmup done")
    }
}

tasks.test.configure {
    dependsOn(testWarmup)
    useJUnitPlatform {
        excludeTags("Warmup")
        if (providers.gradleProperty("host-integration-tests").orNull == "true") {
            includeTags("HostIntegrationTest")
            environment("TEST_ONLY_LATEST_VERSIONS", "true")
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(":publishLocally")
}
