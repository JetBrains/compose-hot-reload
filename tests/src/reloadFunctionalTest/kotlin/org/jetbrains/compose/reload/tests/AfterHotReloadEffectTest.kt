package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.DefaultBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.test.gradle.TestOnlyKmp
import org.jetbrains.compose.reload.test.gradle.TestOnlyLatestVersions
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceSourceCodeAndReload
import org.jetbrains.compose.reload.test.gradle.sendTestEvent
import kotlin.test.assertEquals
import kotlin.test.fail

class AfterHotReloadEffectTest {
    @HotReloadTest
    @DefaultBuildGradleKts
    @TestOnlyDefaultCompilerOptions
    @TestOnlyLatestVersions
    @TestOnlyKmp
    fun `test - invokeAfterHotReload`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                val decoy = 0
            
                screenshotTestApplication {
                    var invocations by remember { mutableStateOf(0) }
                    var engaged by remember { mutableStateOf(true) }
                    
                    if(engaged) {
                        AfterHotReloadEffect {
                            invocations++
                            sendTestEvent(invocations)
                        }
                    }
                    
                    onTestEvent { value -> 
                        if(value == "start") engaged = true
                        if(value == "stop") engaged = false
                    }
                
                    Render(invocations)
                }
            }
            
            // https://github.com/JetBrains/compose-hot-reload/issues/65
            @Composable
            fun Render(invocations: Int) {
                TestText("invoc.: %invocations")
            }

            """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-initial")

        fixture.runTransaction {
            replaceSourceCode("decoy = 0", "decoy = 1")
            assertEquals(1, skipToMessage<OrchestrationMessage.TestEvent>().payload)
            skipToMessage<OrchestrationMessage.UIRendered>()
            fixture.checkScreenshot("1-after-reload")
        }

        fixture.runTransaction {
            replaceSourceCode("decoy = 1", "decoy = 2")
            assertEquals(2, skipToMessage<OrchestrationMessage.TestEvent>().payload)
            skipToMessage<OrchestrationMessage.UIRendered>()
            fixture.checkScreenshot("2-after-reload")
        }

        fixture.sendTestEvent("stop")
        fixture.replaceSourceCodeAndReload("invoc.", "invoc.a")
        fixture.checkScreenshot("3-after-stopped")

        fixture.sendTestEvent("start")
        fixture.runTransaction {
            replaceSourceCode("invoc.a", "invoc.b")
            assertEquals(3, skipToMessage<OrchestrationMessage.TestEvent>().payload)
            skipToMessage<OrchestrationMessage.UIRendered>()
            fixture.checkScreenshot("4-after-restarted")
        }
    }

    @HotReloadTest
    @DefaultBuildGradleKts
    @TestOnlyDefaultCompilerOptions
    @TestOnlyLatestVersions
    @TestOnlyKmp
    fun `test - remove AfterHotReloadEffect code`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.test.*
            
            fun main() = screenshotTestApplication {
                AfterHotReloadEffect { sendTestEvent("Servus") }
                TestText("A")
            }
            """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-initial-A")

        fixture.runTransaction {
            replaceSourceCode("\"A\"", "\"B\"")
            assertEquals("Servus", skipToMessage<OrchestrationMessage.TestEvent>().payload)
            fixture.checkScreenshot("1-B")
        }

        fixture.runTransaction {
            val testEventScanner = launchChildTransaction {
                val testEvent = skipToMessage<OrchestrationMessage.TestEvent>()
                fail("Unexpected TestEvent: ${testEvent.payload}")
            }

            replaceSourceCode("AfterHotReloadEffect { sendTestEvent(\"Servus\") }", "")
            replaceSourceCode("\"B\"", "\"C\"")
            skipToMessage<OrchestrationMessage.UIRendered>()
            fixture.checkScreenshot("2-C")
            testEventScanner.cancel()
        }
    }
}
