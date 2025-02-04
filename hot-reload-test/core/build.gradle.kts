plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":hot-reload-core"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
        artifactId = "hot-reload-test-core"
    }
}
