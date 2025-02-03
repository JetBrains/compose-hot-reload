plugins {
    kotlin("jvm")
    `maven-publish`
    `kotlin-conventions`
    `publishing-conventions`
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
    }
}

dependencies {
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-test:core"))
    implementation(project(":hot-reload-orchestration"))
    api(kotlin("test"))
    api(kotlin("tooling-core"))
    api(deps.junit.jupiter)
    api(deps.coroutines.test)
    implementation(deps.junit.jupiter.engine)
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
        artifactId = "hot-reload-gradleTestFixtures"
    }
}
