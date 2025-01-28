package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyKmp
import org.jetbrains.compose.reload.utils.TestOnlyLatestVersions
import org.jetbrains.compose.reload.utils.checkScreenshot
import org.jetbrains.compose.reload.utils.initialSourceCode
import org.jetbrains.compose.reload.utils.replaceSourceCodeAndReload
import org.jetbrains.compose.reload.utils.sendTestEvent
import kotlin.test.assertEquals
import kotlin.test.fail

class AfterHotReloadEffectTest {
    @HotReloadTest
    @DefaultSettingsGradleKts
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
            import org.jetbrains.compose.reload.underTest.*
            
            fun main() {
                val decoy = 0
            
                underTestApplication {
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
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyDefaultCompilerOptions
    @TestOnlyLatestVersions
    @TestOnlyKmp
    fun `test - remove AfterHotReloadEffect code`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.underTest.*
            
            fun main() = underTestApplication {
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
