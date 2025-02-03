plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
}

kotlin {
    explicitApi()
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
        artifactId = "hot-reload-test-core"
    }
}
