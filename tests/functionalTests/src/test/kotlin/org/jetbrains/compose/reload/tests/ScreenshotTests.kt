@file:Suppress("DuplicatedCode", "FunctionName")

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.utils.AndroidHotReloadTest
import org.jetbrains.compose.reload.utils.DefaultAndroidAndJvmBuildSetup
import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyKmp
import org.jetbrains.compose.reload.utils.TestOnlyLatestVersions
import org.jetbrains.compose.reload.utils.checkScreenshot
import org.jetbrains.compose.reload.utils.initialSourceCode
import org.jetbrains.compose.reload.utils.replaceSourceCode
import org.jetbrains.compose.reload.utils.replaceSourceCodeAndReload
import org.jetbrains.compose.reload.utils.sendTestEvent

class ScreenshotTests {
    private val logger = createLogger()

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    fun `test - simple change`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("Hello")
                }
            }
            """.trimIndent()
        logger.info("Check 'before' screenshot")
        fixture.checkScreenshot("before")

        logger.info("Replace 'Hello' with 'Goodbye'")
        fixture.replaceSourceCodeAndReload("Hello", "Goodbye")

        logger.info("Check 'after' screenshot")
        fixture.checkScreenshot("after")
    }

    @HostIntegrationTest
    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    fun `test - retained state`(fixture: HotReloadTestFixture) = fixture.runTest {
        val d = "\$"
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    var state by remember { mutableStateOf(0) }
                    onTestEvent {
                        state++
                    }
                    
                    Group {
                        TestText("Before: ${d}state")
                    }
                }
            }
            """.trimIndent()
        fixture.checkScreenshot("before-0")

        fixture.sendTestEvent()
        fixture.checkScreenshot("before-1")

        fixture.sendTestEvent()
        fixture.checkScreenshot("before-2")

        fixture.replaceSourceCodeAndReload("Before", "After")
        fixture.checkScreenshot("after-2")
    }

    @AndroidHotReloadTest
    @DefaultSettingsGradleKts
    @DefaultAndroidAndJvmBuildSetup
    fun `test - kmp with android and jvm`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("Hello")
                }
            }
            """.trimIndent()
        fixture.checkScreenshot("before")

        fixture.replaceSourceCodeAndReload("Hello", "Goodbye")
        fixture.checkScreenshot("after")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - add button`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    Column {
                        TestText("Initial")
                        // Add button
                    }
                }
            }
            """.trimIndent()
        fixture.checkScreenshot("0-before")

        fixture.replaceSourceCodeAndReload(
            """// Add button""", """
                Button(onClick = { }) {
                    TestText("Button")
                }
            """.trimIndent()
        )
        fixture.checkScreenshot("1-withButton")
    }


    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - add remembered state`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    // add state
                    
                    // add effect
                    
                    Column {
                        TestText("Initial")
                        // Add button
                    }
                }
            }
            """.trimIndent()
        fixture.checkScreenshot("0-before")

        fixture.replaceSourceCodeAndReload("""// add state""", """var state by remember { mutableStateOf(0) }""")
        fixture.checkScreenshot("1-addedState")

        fixture.replaceSourceCodeAndReload(""""Initial"""", """"State: %state"""".replace("%", "$"))
        fixture.checkScreenshot("2-renderState")

        fixture.replaceSourceCodeAndReload("""// add effect""", """onTestEvent { state++ }""")
        fixture.checkScreenshot("3-addedEffect")

        fixture.sendTestEvent { event ->
            fixture.checkScreenshot("4-afterEvent")
        }
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyKmp
    fun `test - update remembered value`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    var state by remember { mutableStateOf(0) }
                    onTestEvent { state++ }
                    Group {
                        TestText("Before: %state")
                    }
                }
            }
            """.trimIndent().replace("%", "$")
        fixture.checkScreenshot("0-before")

        fixture.sendTestEvent()
        fixture.checkScreenshot("1-afterEvent")

        fixture.replaceSourceCodeAndReload("Before:", "After:")
        fixture.checkScreenshot("2-afterSimpleCodeChange")

        fixture.replaceSourceCodeAndReload("mutableStateOf(0)", "mutableStateOf(42)")
        fixture.checkScreenshot("3-afterChangeInsideRememberBlock")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - change lambda from non-capturing to capturing`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    var state by remember { mutableStateOf(0) }
                   
                    val myLambda = {
                        // lambda body
                        sendLog("run: myLambda")
                        sendTestEvent("run: myLambda")
                    }
                    
                    onTestEvent { value ->
                        if(value == "inc") myLambda() 
                     }
                    
                    TestText("%state")
                }
            }
            """.trimIndent().replace("%", "$")
        fixture.checkScreenshot("0-before")

        fixture.sendTestEvent("inc")
        fixture.checkScreenshot("0-before")

        fixture.replaceSourceCodeAndReload("// lambda body", "state++")


        fixture.sendTestEvent("inc") {
            skipToMessage<OrchestrationMessage.TestEvent> { event ->
                event.payload == "run: myLambda"
            }
        }

        fixture.checkScreenshot("1-afterLambdaEngaged")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - change lambda from non-capturing to capturing - wrapper`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            @Composable fun Wrapper(child: @Composable () -> Unit) {
                child()
            }
            
            @Composable fun Actor(
                onEvent: () -> Unit, 
                child: @Composable () -> Unit,
            ) {
                onTestEvent { onEvent() }
                child()
            }
            
            fun main() = screenshotTestApplication {
                var state by remember { mutableStateOf(0) }
                Wrapper {
                    Actor(onEvent = { /* lambda body */ }) {
                        TestText("%state")
                     }
                }
            }
            """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before-0")

        fixture.sendTestEvent()
        fixture.checkScreenshot("1-afterEventWithoutAction-0")

        fixture.replaceSourceCodeAndReload("/* lambda body */", "state++")
        fixture.checkScreenshot("2-afterLambdaEngaged-0")

        fixture.sendTestEvent()
        fixture.checkScreenshot("3-afterEventWithLambdaEngaged-1")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - add top level value`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.material3.*
            import androidx.compose.ui.unit.*
            import org.jetbrains.compose.reload.test.*
            
            val foo = 42
            // add field
            
            fun main() {
                screenshotTestApplication {
                   TestText("%foo")
                }
            }
            """.trimIndent().replace("%", "$")
        fixture.checkScreenshot("0-before")

        fixture.runTransaction {
            fixture.replaceSourceCode("// add field", "val bar = 1902")
            fixture.replaceSourceCode("\$foo", "\$bar")
            awaitReload()
        }

        fixture.checkScreenshot("1-after")
    }

    @HotReloadTest
    @TestOnlyKmp
    @TestOnlyLatestVersions
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    fun `test - changing spacedBy`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Row
            import androidx.compose.foundation.layout.fillMaxWidth
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            import org.jetbrains.compose.reload.test.*


            fun main() {
                screenshotTestApplication {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp) ,
                    ) {
                        TestText("A")
                        TestText("B")
                    }
                }
            }

            """.trimIndent().replace("%", "$")
        fixture.checkScreenshot("0-before")

        /* Increase value passed to 'spacedBy' */
        fixture.replaceSourceCodeAndReload("spacedBy(6.dp)", "spacedBy(32.dp)")
        fixture.checkScreenshot("1-larger-spacedBy")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyKmp
    fun `test - change in canvas draw coordinates`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.Canvas
            import androidx.compose.foundation.layout.Box
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.size
            import androidx.compose.foundation.progressSemantics
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.geometry.Offset
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp
            import org.jetbrains.compose.reload.test.*
              
            fun main() = screenshotTestApplication {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Canvas(
                       Modifier
                       .progressSemantics()
                       .size(200.dp, 200.dp)
                    ) {
                        drawLine(
                            color = Color.Black,
                            Offset(0f, 0f),
                            Offset(50f, 0f),
                            20f,
                        )
                   }
               }
            }
         """.trimIndent()

        fixture.checkScreenshot("0-before")

        fixture.replaceSourceCodeAndReload("Offset(50f, 0f)", "Offset(200f, 0f)")
        fixture.checkScreenshot("1-after")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - remember in two composables`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
              
            fun main() = screenshotTestApplication {
                Column {
                    Foo()
                }
            }
            
            @Composable
            fun Foo() {
                var foo by remember { mutableStateOf(0) }
                onTestEvent { value ->
                    if(value == "foo") foo++
                }
                TestText("Foo: %foo")
                Bar()
            }
            
            @Composable
            fun Bar() {
                var bar by remember { mutableStateOf(0) }
                onTestEvent { value ->
                    if(value == "bar") bar++
                }
                TestText("Bar: %bar")
            }
         """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before")

        fixture.sendTestEvent("foo")
        fixture.checkScreenshot("1-foo_1-bar_0")

        fixture.sendTestEvent("foo")
        fixture.checkScreenshot("2-foo_2-bar_0")

        fixture.sendTestEvent("bar")
        fixture.checkScreenshot("3-foo_2-bar_1")

        fixture.replaceSourceCodeAndReload(
            "var bar by remember { mutableStateOf(0) }",
            "var bar by remember { mutableStateOf(24) }"
        )
        fixture.checkScreenshot("4-foo_2-bar_24-afterReload")
    }


    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    fun `test - change line numbers - by adding whitespace`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
              
            fun main() = screenshotTestApplication {
                Column {
                    Foo()
                }
            }
            
            fun decoy() {
                //add whitespace
            }
            
            @Composable
            fun Foo() {
                var foo by remember { mutableStateOf(0) }
                onTestEvent { value ->
                    if(value == "foo") foo++
                }
                TestText("Foo: %foo")
            }
         """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before")

        fixture.sendTestEvent("foo")
        fixture.checkScreenshot("1-foo_1")

        fixture.replaceSourceCodeAndReload("//add whitespace", "\n\n\n\n")
        fixture.checkScreenshot("2-foo_1")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    fun `test - change line numbers - by adding whitespace and code`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
              
            fun main() = screenshotTestApplication {
                Column {
                    Foo()
                }
            }
            
            fun decoy() {
                //add whitespace
            }
            
            @Composable
            fun Foo() {
                var foo by remember { mutableStateOf(0) }
                onTestEvent { value ->
                    if(value == "foo") foo++
                }
                TestText("Foo: %foo")
            }
         """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before")

        fixture.sendTestEvent("foo")
        fixture.checkScreenshot("1-foo_1")

        fixture.replaceSourceCodeAndReload(
            "//add whitespace", """
            |
            |
            | println("new code")
            | 
            """.trimMargin()
        )
        fixture.checkScreenshot("2-foo_1")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    fun `test - if branch`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
              
            fun main() = screenshotTestApplication {
                Column {
                    Foo(true)
                }
            }
            
            @Composable
            fun Foo(value: Boolean) {
                if(value) {
                    var state by remember { mutableStateOf(0) }
                    onTestEvent { value -> if(value == "inc") state++ }
                    TestText("Foo: %state")
                } else {
                    TestText("Value is 'false'")
                }
            }
        """.replace("%", "$").trimIndent()

        fixture.checkScreenshot("0-true-0")
        fixture.sendTestEvent("inc")
        fixture.checkScreenshot("1-true-1")

        fixture.replaceSourceCodeAndReload(
            "Foo(true)", """
            | 
            | Foo(
            |    true
            | )
        """.trimMargin()
        )
        fixture.checkScreenshot("2-afterWhitespaceChangeInMain-true-1")

        fixture.replaceSourceCodeAndReload("""Value is 'false'""", """Value is <false>""")
        fixture.checkScreenshot("3-afterChangeInElse-true-1")

        fixture.replaceSourceCodeAndReload(
            """
            | Foo(
            |    true
            | )
            """.trimMargin(), """Foo(false)"""
        )
        fixture.checkScreenshot("4-false-0")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    fun `test - add enum case`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            enum class Tab {
                A, 
                B,
                // add C entry
            }
              
            fun main() = screenshotTestApplication {
                var tab by remember { mutableStateOf(Tab.A) }
                onTestEvent { value -> 
                    tab = Tab.valueOf(value.toString())
                }
                
                when(tab) {
                    Tab.A -> TestText("A")
                    Tab.B -> TestText("B")
                    // add C case
                }                
            }
           
         """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before")

        fixture.runTransaction {
            replaceSourceCode("// add C entry", "C,")
            replaceSourceCode("// add C case", "Tab.C -> TestText(\"C\")")
            awaitReload()
        }
        fixture.checkScreenshot("1-after-c-added")

        fixture.sendTestEvent("C")
        fixture.checkScreenshot("2-after-c-selected")
    }

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    fun `test - change in static field`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.*
            
            val foo = 42
          
            fun main() = screenshotTestApplication {
                val state by remember { mutableStateOf(foo) }
                TestText("state: %state")
            }
         """.trimIndent().replace("%", "$")

        fixture.checkScreenshot("0-before")

        fixture.replaceSourceCodeAndReload("val foo = 42", "val foo = 1902")
        fixture.checkScreenshot("1-after-foo-changed")
    }
}
