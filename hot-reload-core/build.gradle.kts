plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `java-test-fixtures`
    org.jetbrains.kotlinx.benchmark
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    api(deps.slf4j.api)

    testFixturesImplementation(kotlin("tooling-core"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesCompileOnly(kotlin("compiler-embeddable"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}

kotlin {
    target.compilations.create("benchmark") {
        associateWith(target.compilations.getByName("main"))
        defaultSourceSet {
            dependencies {
                implementation(deps.benchmark.runtime)
            }
        }
    }
}

benchmark {
    targets.register("benchmark")
}


/* Make the current 'Hot Reload Version (aka version of this project) available */
run {
    val generatedSourceDir = file("build/generated/main/kotlin")

    val writeBuildConfig = tasks.register("writeBuildConfig") {
        val file = generatedSourceDir.resolve("BuildConfig.kt")

        val versionProperty = project.providers.gradleProperty("version").get()
        inputs.property("version", versionProperty)

        println(versionProperty)


        outputs.file(file)

        val text = """
            package org.jetbrains.compose.reload.core
            
            public const val HOT_RELOAD_VERSION: String = "$versionProperty"
            """
            .trimIndent()

        inputs.property("text", text)

        doLast {
            file.parentFile.mkdirs()
            logger.quiet(text)
            file.writeText(text)
        }
    }

    kotlin {
        sourceSets.main.get().kotlin.srcDir(generatedSourceDir)
    }

    tasks.register("prepareKotlinIdeaImport") {
        dependsOn(writeBuildConfig)
    }

    kotlin.target.compilations.getByName("main").compileTaskProvider.configure {
        dependsOn(writeBuildConfig)
    }
}
