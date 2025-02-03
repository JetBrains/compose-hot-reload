plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `tests-with-compiler`
    `java-test-fixtures`
}

kotlin.compilerOptions {
    optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}

dependencies {
    implementation(project(":hot-reload-core"))

    implementation(deps.slf4j.api)
    implementation(deps.asm)
    implementation(deps.asm.tree)

    testImplementation(deps.logback)

    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesImplementation(project(":hot-reload-core"))
    testFixturesImplementation(project(":hot-reload-analysis"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
