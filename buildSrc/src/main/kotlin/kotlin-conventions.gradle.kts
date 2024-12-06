import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins.withType<KotlinPluginWrapper> {
    extensions.configure<KotlinProjectExtension> {
        jvmToolchain {
            this.languageVersion = JavaLanguageVersion.of(17)
            this.vendor = JvmVendorSpec.JETBRAINS
        }
    }
}

plugins.withType<KotlinMultiplatformPluginWrapper> {
    extensions.configure<KotlinProjectExtension> {
        jvmToolchain {
            this.languageVersion = JavaLanguageVersion.of(17)
            this.vendor = JvmVendorSpec.JETBRAINS
        }
    }
}
