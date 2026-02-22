# Known limitations of Compose Hot Reload

This list contains known issues/limitations of Compose Hot Reload. The limitations listed here are mainly
caused by external factors rather than Compose Hot Reload itself. Therefore, we cannot guarantee that these issues
will be resolved in the future. The list does not cover all possible issues and may be updated.

### Adding Compose Hot Reload causes my application's window to flicker/jump/behave weirdly

Compose Hot Reload currently supports all main OSs. However, the dev tooling window snapping itself to the main application's
window can cause unusual behavior (flickering, jumping, etc.) on some window managers. If you encounter such issues,
please [report](https://youtrack.jetbrains.com/newIssue?project=CMP&c=Library%20group%20Hot%20Reload) them to us; we will try to reproduce and fix them.

As a workaround, you can try:

1. Running the dev tools window in detached mode by setting the `-Dcompose.reload.devToolsDetached=true` property. This will
launch the dev tools as a regular window, without snapping to the main application's window.
2. Running the dev tools window in a headless mode by setting the `-Dcompose.reload.devToolsHeadless=true` property. This
will launch the dev tools process, but will not show any UI.
3. Disabling the dev tools process entirely by setting the `-Dcompose.reload.devToolsEnabled=false` property. This will
prevent the dev tools process from starting, which may limit some functionality of Compose Hot Reload.

Original issue: [CMP-9674](https://youtrack.jetbrains.com/issue/CMP-9674)

### Issues when using Virtual Desktops/Workspaces

All the main OSs support some form of virtual desktops/workspaces. However, they do not provide a public API for 
accessing information about the current desktop of the window nor an API for changing the virtual desktop of the window.
This is expected, as user applications should not rely on or care about the current desktop of the window.

Unfortunately, this means that Compose Hot Reload cannot reliably detect on which virtual desktop the user windows spawns
and cannot change the virtual desktop of the dev tools window when necessary. The same happens when the user moves the main
applications window to another virtual desktop: Compose Hot Reload cannot detect that and move the dev tools window accordingly.

Therefore, if you use virtual desktops, we recommend running the dev tools window in detached mode. You can enable detached mode by setting the `-Dcompose.reload.devToolsDetached=true` property.

Original issue: [#426](https://github.com/JetBrains/compose-jb/issues/426)

### Compose Hot Reload does not work with Windows Dev Drive (ReFS)

Compose Hot Reload relies on the Gradle File System Watching to detect changes in the source files. Unfortunately,
it [does not currently support ReFS](https://docs.gradle.org/current/userguide/file_system_watching.html#supported_file_systems).
There is an open issue in Gradle's tracker: [#31634](https://github.com/gradle/gradle/issues/31634).

For now, we recommend moving your project to an NTFS-formatted drive.

Original issue: [#190](https://github.com/JetBrains/compose-hot-reload/issues/190)

### Running multiple Compose applications in the same IDE instance

The Kotlin Multiplatform IDE plugin currently supports only a single Compose Hot Reload connection at a time. If you try to 
simultaneously run multiple Compose applications within the same IDE instance, you may experience unexpected shutdowns.

Supporting only a single Compose Hot Reload connection in Kotlin Multiplatform is intentional, as having multiple Compose
Hot Reload-related UI elements in the IDE can be overwhelming and confusing. If you're running multiple Compose applications 
simultaneously, we recommend running only one of them in hot reload mode. Alternatively, you can run other applications
[from the command line;](https://github.com/JetBrains/compose-hot-reload?tab=readme-ov-file#from-the-cli) then Compose
Hot Reload will not automatically connect to the IDE.

Original issue: [#408](https://github.com/JetBrains/compose-hot-reload/issues/408)

### Property `compose.application.resources.dir` is null when running hot reload tasks

Compose desktop native distribution that allows adding arbitrary files to the final distribution, accessing them later via
`compose.application.resources.dir` system property from the application. However, the Compose Gradle plugin currently sets
this property only for the `run` task. For other tasks, including `desktopRun`, `hotRunDesktop`, etc., this property is not set.

As a workaround, you can set this property manually in your `build.gradle.kts` file:
```kotlin
tasks.withType<ComposeHotRun>().configureEach {
    systemProperty(
        "compose.application.resources.dir",
        project.layout.buildDirectory.dir("compose/tmp/prepareAppResources").get()
    )
}
```

Original issue: [#343](https://github.com/JetBrains/compose-hot-reload/issues/343)

Original issue in CMP tracker: [CMP-8800](https://youtrack.jetbrains.com/issue/CMP-8800)

---

If you encounter any issues not mentioned here, please [report](https://youtrack.jetbrains.com/newIssue?project=CMP&c=Library%20group%20Hot%20Reload) 
them to us. If you have any further questions regarding one of the listed issues, please feel free to ask them in the 
linked original issue or create a new issue/[discussion](https://github.com/JetBrains/compose-hot-reload/discussions).