plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `api-validation-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation(deps.coroutines.core)
    implementation(deps.coroutines.test)
    implementation(deps.asm.tree)
    implementation(deps.asm)
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-analysis"))
    implementation(kotlin("compiler-embeddable"))

    compileOnly(project(":hot-reload-agent"))
    implementation(project(":hot-reload-orchestration"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
