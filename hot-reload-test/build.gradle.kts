plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
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
    implementation(deps.coroutines.swing)
    implementation(deps.asm.tree)
    implementation(deps.asm)
    implementation(deps.logback)
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-analysis"))
    implementation(kotlin("compiler-embeddable"))

    compileOnly(project(":hot-reload-agent"))
    implementation(project(":hot-reload-orchestration"))
    api(compose.material3)
    implementation(compose.components.resources)
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}


/* Add special 'dev' runtime dependency */
internal class ComposeDevJavaRuntimeCompatibilityRule : AttributeCompatibilityRule<Usage> {
    override fun execute(details: CompatibilityCheckDetails<Usage>) {
        if (details.consumerValue?.name == "compose-dev-java-runtime" &&
            details.producerValue?.name == Usage.JAVA_RUNTIME
        ) {
            details.compatible()
        }
    }
}

dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(
    ComposeDevJavaRuntimeCompatibilityRule::class.java
)

configurations.compileClasspath {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("compose-dev-java-runtime"))
}

dependencies {
    compileOnly(project(":hot-reload-runtime-api"))
    compileOnly(project(":hot-reload-runtime-jvm", configuration = "jvmDevRuntimeElements"))
}
