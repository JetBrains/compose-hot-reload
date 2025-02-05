import org.jetbrains.compose.reload.core.Template
import org.jetbrains.compose.reload.core.asTemplate
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.render
import org.jetbrains.compose.reload.core.renderOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateTest {
    @Test
    fun `test - empty`() {
        assertEquals("", "".asTemplate().getOrThrow().render(emptyMap()).getOrThrow())
    }

    @Test
    fun `test - dollar interpolation`() {
        val d = "$"
        assertEquals<String>(
            """
                Hello ${d}name
            """.trimIndent(),
            """
                Hello %%name
            """.trimIndent().asTemplate().getOrThrow().render(emptyMap()).getOrThrow(),
        )
    }

    @Test
    fun `test - key with dot`() {
        val template = Template("""Hello {{name.first}}""").getOrThrow()
        assertEquals("Hello World", template.render(mapOf("name.first" to "World")).getOrThrow())
    }

    @Test
    fun `test - null value`() {
        val template = Template(
            """
            Hello {{name.first}}
            This line will be absent {{value.null}}
            """.trimIndent()
        ).getOrThrow()
        assertEquals(
            "Hello World\n",
            template.render(mapOf("name.first" to "World", "value.null" to null)).getOrThrow()
        )
    }

    @Test
    fun `test - simple single line, single variable`() {
        val template = """
            Hello, {{name}}!
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals("Hello, World!", template.render(mapOf("name" to "World")).getOrThrow())
        assertEquals("Hello, Seb!", template.render(mapOf("name" to "Seb")).getOrThrow())
    }

    @Test
    fun `test - single line, multiple variables`() {
        val template = """
            Hello, {{name}}! My age is {{age}}.
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals("Hello, World! My age is 18.", template.render(mapOf("name" to "World", "age" to 18)).getOrThrow())
        assertEquals("Hello, Seb! My age is 22.", template.render(mapOf("name" to "Seb", "age" to 22)).getOrThrow())
    }

    @Test
    fun `test - single line, multiple variables - multiple values`() {
        val template = """
            Hello, {{name}}! My age is {{age}}.
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            Hello, Sarah! My age is 29.
            Hello, Seb! My age is 23.
        """.trimIndent(),
            template.render(mapOf("name" to listOf("Sarah", "Seb"), "age" to listOf(29, 23))).getOrThrow()
        )
    }

    @Test
    fun `test multiline - single variable`() {
        val template = """
            <html>
               <body>
                   {{message}}
               </body>
            </html>
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            <html>
               <body>
                   Hello, World!
               </body>
            </html>
        """.trimIndent(), template.render(mapOf("message" to "Hello, World!")).getOrThrow()
        )
    }

    @Test
    fun `test multiline - single multiline variable`() {
        val template = """
            <html>
               <body>
                   {{message}}
               </body>
            </html>
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            <html>
               <body>
                   <div>
                       Hello
                   </div>
               </body>
            </html>
        """.trimIndent(), template.render(
                mapOf(
                    "message" to """
                    <div>
                        Hello
                    </div>
                """.trimIndent()
                )
            ).getOrThrow()
        )
    }

    @Test
    fun `test - if expression`() {
        val template = """
            Hello, {{name}}!
            {{if value}}
                Foo {{value}}
            {{/if}}
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            Hello, World!
                Foo 123
        """.trimIndent(),
            template.renderOrThrow {
                "name"("World")
                "value"(123)
            }
        )
    }

    @Test
    fun `test - if expression - missing value`() {
        val template = """
            Hello, {{name}}!
            {{if value}}
                Foo {{value}}
            {{/if}}
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            Hello, World!
            
        """.trimIndent(),
            template.renderOrThrow {
                "name"("World")
            }
        )
    }

    @Test
    fun `test - if expression - nested`() {
        val template = """
            Hello, {{name}}!
            {{if foo}}
                foo: {{foo}}
                {{if bar}}
                bar: {{bar}}
                {{/if}}
            {{/if}}
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            Hello, World!
            """.trimIndent(),
            template.renderOrThrow {
                "name"("World")
            }.trim()
        )

        assertEquals(
            """
            Hello, World!
                foo: 1602
        """.trimIndent(),
            template.renderOrThrow {
                "name"("World")
                "foo"("1602")
            }.trim()
        )

        assertEquals(
            """
            Hello, World!
                foo: 1602
                bar: 123
        """.trimIndent(),
            template.renderOrThrow {
                "name"("World")
                "foo"("1602")
                "bar"(123)
            }.trim()
        )
    }


    @Test
    fun `test - unused line`() {
        val template = """
            Hello, {{name}}!
            unused {
                This line should not be there {{unused}}
            }
        """.trimIndent().asTemplate().getOrThrow()

        assertEquals(
            """
            Hello, World!
            unused {
            }
            """.trimIndent(),
            template.render(mapOf("name" to "World")).getOrThrow()
        )
    }

    @Test
    fun `test - build example gradle script`() {
        val template = """
            plugins {
                {{plugin}}
                kotlin("{{kotlin_plugin}}") version "{{kotlin_version}}"
            }
            
            repositories {
                {{repository}}
            }
            
            dependencies {
                implementation("{{dependency}}")
            }
        """.trimIndent().asTemplate().getOrThrow()


        assertEquals(
            """
            plugins {
                `maven-publish`
                id("org.jetbrains.compose") version "1.7.3"
                kotlin("multiplatform") version "1.9.22"
                kotlin("plugin.serialization") version "1.9.22"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            }
        """.trimIndent(), template.render(
                "plugin" to "`maven-publish`",
                "plugin" to """id("org.jetbrains.compose") version "1.7.3"""",
                "kotlin_plugin" to "multiplatform", "kotlin_version" to "1.9.22",
                "kotlin_plugin" to "plugin.serialization", "kotlin_version" to "1.9.22",
                "repository" to "mavenCentral()",
                "dependency" to """org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2"""
            ).getOrThrow()
        )
    }

}
