package org.jetbrains.compose.reload.test

/**
 * Marker annotation for unit tests dedicated to testing the behavior of a component under hot-reload.
 * The annotation works similar to unit's `@Test` annotation
 *
 * ### Usage Note: Use top-level functions!
 * Contrary to JUnit, this test annotation requires top-level functions, no classes!
 * ```kotlin
 * // Correct
 * @HotReloadUnitTest
 * fun `my test`() {
 *     ...
 * }
 * ```
 *
 * ```kotlin
 * // WRONG!
 * class MyTestClass {
 *     @HotReloadUnitTest
 *     fun `this is wrong`() {
 *     }
 * }
 * ```
 *
 * ### Usage Note: Use [compileAndReload]
 * Use [compileAndReload] to dynamically swap existing code (or introduce new code)
 * ```kotlin
 * @HotReloadUnitTest
 * fun `test - change function body`() {
 *     assertEquals("Before", ExampleApi.value())
 *
 *     compileAndReload(
 *         """
 *             package sample.library
 *
 *             object ExampleApi {
 *                 fun value() = "After"
 *             }
 *         """.trimIndent()
 *     )
 *
 *     assertEquals("After", ExampleApi.value())
 * }
 *
 * ```
 *
 * ### Usage Note: Every test will be executed in absolute isolation.
 * Hot Reload Unit Tests are also intended to test the behavior of static caches and objects.
 * You can change any static state in a test as every test will be executed in absolute isolation.
 * Any change (e.g., by reloading code) in a given test will therefore also not leak into other tests.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class HotReloadUnitTest
