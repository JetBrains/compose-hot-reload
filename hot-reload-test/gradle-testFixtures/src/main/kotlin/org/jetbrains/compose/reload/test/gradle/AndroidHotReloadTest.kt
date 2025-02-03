package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.CompilerOptions
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils
import java.util.stream.Stream
import kotlin.streams.asStream

@TestTemplate
@ExtendWith(AndroidScreenshotTestInvocationContextProvider::class)
@DefaultSettingsGradleKts
public annotation class AndroidHotReloadTest

internal class AndroidScreenshotTestInvocationContextProvider : TestTemplateInvocationContextProvider {
    private val hotReloadInvocationContextProvider = HotReloadTestInvocationContextProvider()

    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return hotReloadInvocationContextProvider.supportsTestTemplate(context) &&
            AnnotationUtils.findAnnotation(context.testMethod, AndroidHotReloadTest::class.java).isPresent
    }

    override fun provideTestTemplateInvocationContexts(
        context: ExtensionContext
    ): Stream<TestTemplateInvocationContext> {

        return TestedAndroidVersion.entries.map { androidVersion ->
            HotReloadTestInvocationContext(
                gradleVersion = TestedGradleVersion.entries.last(),
                kotlinVersion = TestedKotlinVersion.entries.last(),
                composeVersion = TestedComposeVersion.entries.last(),
                androidVersion = androidVersion,
                projectMode = ProjectMode.Kmp,
                compilerOptions = CompilerOptions.default,
            )
        }.asSequence().asStream()
    }
}
