package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.TestOnlyLatestVersions
import org.jetbrains.compose.reload.utils.checkScreenshot
import org.jetbrains.compose.reload.utils.initialSourceCode
import org.jetbrains.compose.reload.utils.replaceText
import org.jetbrains.compose.reload.utils.sendTestEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorRecoveryTests {

    @HotReloadTest
    @TestOnlyLatestVersions
    @DefaultBuildGradleKts
    @DefaultSettingsGradleKts
    fun `good - bad - good`(fixture: HotReloadTestFixture) = fixture.runTest {
        val code = fixture.initialSourceCode(
            """
            import org.jetbrains.compose.reload.underTest.*
            
            fun main() {
                underTestApplication {
                    TestText("Hello")
                }
            }
        """.trimIndent()
        )

        fixture.checkScreenshot("0-initial")
        fixture.runTransaction {
            code.replaceText("""TestText("Hello")""", """error("Foo")""")
            assertEquals("Foo", skipToMessage<OrchestrationMessage.UIException>().message)
        }

        fixture.runTransaction {
            code.replaceText("""error("Foo")""", """TestText("Recovered")""")
            awaitReload()
            fixture.checkScreenshot("1-recovered")
        }
    }

    @HotReloadTest
    @TestOnlyLatestVersions
    @DefaultBuildGradleKts
    @DefaultSettingsGradleKts
    fun `illegal code change - with recovery`(fixture: HotReloadTestFixture) = fixture.runTest {
        val d = "\$"
        val code = fixture.initialSourceCode(
            """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.underTest.*
            
            abstract class A
            abstract class B
            
            class Foo: A()
            
            fun main() {
                Foo() // <- Use Foo here to ensure the class is loaded!
                underTestApplication(width = 512, height = 512) {
                    var state by remember { mutableStateOf(0) }
                    onTestEvent {
                        state++
                    }
                    TestText("Before: ${d}state")
                }
            }
            """.trimIndent()
        )

        fixture.checkScreenshot("0-initial")

        /*
        Sending a test event to let the test carry some state!
         */
        fixture.sendTestEvent()
        fixture.checkScreenshot("1-after-sendTestEvent")

        /*
        Illegal Change: Replacing superclass:
        We expect this to be rejected!
         */
        code.replaceText("""Foo: A()""", """Foo: B()""")
        fixture.runTransaction {
            val request = skipToMessage<OrchestrationMessage.ReloadClassesRequest>()
            val result = skipToMessage<OrchestrationMessage.ReloadClassesResult>()
            assertEquals(request.messageId, result.reloadRequestId)
            assertFalse(result.isSuccess)
        }

        /*
        Recover from the illegal change: Revert to the original class
         */
        code.replaceText("""Foo: B()""", """Foo: A()""")
        fixture.runTransaction {
            val request = skipToMessage<OrchestrationMessage.ReloadClassesRequest>()
            val result = skipToMessage<OrchestrationMessage.ReloadClassesResult>()
            assertEquals(request.messageId, result.reloadRequestId)
            assertTrue(result.isSuccess)
            fixture.checkScreenshot("2-recovered-reload")
        }


        /*
        After recovery:
        Lets test if hot reload still works.
         */
        fixture.runTransaction {
            code.replaceText("Before:", "After:")
            awaitReload()
            fixture.checkScreenshot("3-after-change")
        }
    }
}
