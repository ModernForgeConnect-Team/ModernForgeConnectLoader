# LWJGL compatibility boundary

MFCL embeds Forge 1.7.10 inside a Forge 1.20.1 host. The host owns the native window, input event loop and OpenGL context. The legacy loader retains private LWJGL2 class identities; `GuiPlatformAdapter` replaces selected GL, keyboard, mouse and timer implementations before definition. `LegacyOpenGl` connects the private GL11 identity to a host backend, while immediate-mode geometry and display lists use MFCL's capture/replay bridges.

[LWJGL3ify](https://github.com/GTNewHorizons/lwjgl3ify#how-does-it-work) provides useful reference architecture: LWJGL2-compatible wrappers and transformations applied after other transformers. Its [current Display implementation](https://github.com/GTNewHorizons/lwjgl3ify/blob/master/src/main/java/org/lwjglx/opengl/Display.java) creates SDL windows and GL contexts. That lifecycle does not fit an embedded runtime with an existing host window. MFCL's implementation below is independent and does not import LWJGL3ify code or its bootstrap.

## Display queries

`DisplayPlatformAdapter` now replaces `org.lwjgl.opengl.Display` before definition, including its native-loading static initializer. Calls through reflection and ordinary bytecode resolve to the same adapted class. Its package and code source continue through `LegacyLaunchLoader`'s existing manifest handling; the LWJGL2 JAR is not modified or unsealed.

Supported queries are `getWidth`, `getHeight`, `getX`, `getY`, `isCreated`, `isActive`, `isVisible`, `isFullscreen`, `isCloseRequested` and `isResizable`. They read a JDK-only `LegacyOpenGl.DisplayState` from the attached backend. The client backend queries the existing GLFW window on the host render executor. Width and height use host framebuffer pixels, consistent with the host render surface, rather than scaled GUI dimensions. With no backend, dimensions are zero and all flags are false.

All other Display methods fail with an explicit `UnsupportedOperationException`. This includes creating/destroying windows, polling events, swapping buffers, changing titles or display modes, and taking/releasing the context. `getDisplayMode`, `wasResized` and `isCurrent` also remain unsupported; this is a bounded read-only adapter, not full LWJGL2 compatibility.

The private LWJGL2 library is still required as API bytecode input. This change does not eliminate all possible old native paths: other classes such as `GLContext`, `Pbuffer`, OpenAL and unadapted GL extensions require separate audits. Moving all legacy references into a new namespace is a larger change involving descriptors, reflection, transformer ordering and class resources; it is not necessary for this Display boundary.

## Regression verification

Run with a prepared runtime directory containing `lwjgl-2.9.1.jar` and its original companion libraries:

```powershell
.\gradlew.bat test assemble '-PlegacySupportDir=run/mfcl/runtime/1.7.10'
```

`LegacyDisplayAdapterTest` initializes original LWJGL2 Display through the real isolated root and LaunchWrapper loaders in both Display-first and GL11-first order. It verifies shared package/code source, subsequent unadapted DisplayMode loading, ten forwarded queries, forbidden lifecycle calls, reflection access and backend detachment. Without the property the two fixture tests skip; an explicitly configured missing fixture fails. These tests use a fake host backend and do not validate live GLFW queries, window resizing, focus changes or graphical rendering; those still need client acceptance.
