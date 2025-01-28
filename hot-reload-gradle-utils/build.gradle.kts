plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    compileOnly(deps.compose.compiler.gradlePlugin)
    implementation(project(":hot-reload-core"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
