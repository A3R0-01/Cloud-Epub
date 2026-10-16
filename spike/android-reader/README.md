# Android reader feasibility spike — work in progress

For [Validate continuous-chapter reading and original-content anchors on Android](https://github.com/A3R0-01/Cloud-Epub/issues/16), under [Find the build-ready specification for Cloud EPUB Android and backend](https://github.com/A3R0-01/Cloud-Epub/issues/1).

**This capture has not compiled or run on Android. It is an unfinished experiment, not a runnable/validated deliverable, renderer adoption, or a resolution of the task.** See [evidence/status.md](evidence/status.md). Keep this throwaway branch separate from production and open no production PR.

The candidate is Readium Kotlin 3.4.0's Fragment navigator. The initial build attempt uses AGP 9.3.1, Gradle 9.7.0, compile SDK 37, build tools 37.0.0, and Studio's JBR 25.0.3. This combination is **not yet a validated application toolchain**. The harness declares min API 24 and target API 35 only for the experiment; neither is an accepted support-policy decision.

## Fixture provenance

`generate-fixture.ps1` creates the self-authored, DRM-free, English/Unicode probe EPUB, including two Windows-rendered synthetic PNGs. The corpus contains three spine resources, long continuous text, duplicate words and identical paragraphs, curly punctuation, internal hyphens/apostrophes, composed/decomposed accents, an emoji before a target, a soft hyphen, NBSP, a ligature, text split across emphasis, selectable captions, an equation, a linked image, a reading-flow cover, an explicitly decorative image, and an image lacking alt metadata. English/Unicode probes do not decide the supported book-language set.

The image drawings use local Arial through Windows System.Drawing. The EPUB contains raster images, not a bundled font. Native book-font probes use Android generic `serif` and `sans-serif`; browser-prototype fonts are not included. Cross-machine PNG generation can vary; the committed EPUB and its recorded SHA-256 identify this captured fixture. No user books or credentials are included.

## Build and start, once downloads work

Use PowerShell. Set `JAVA_HOME` to an appropriate installed JDK and `ANDROID_HOME` to the experiment's SDK. Use a separately installed Gradle 9.7.0 on PATH; no Gradle wrapper is committed yet.

```powershell
./generate-fixture.ps1
node --check app/src/main/assets/reader-spike.js
gradle :app:assembleDebug --console=plain
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell am start -n dev.cloudepub.spike/.MainActivity
```

The intended AVD is a disposable AOSP API 35 x86_64 phone. Install `platform-tools`, `emulator`, `platforms;android-37.0`, `build-tools;37.0.0`, and `system-images;android-35;default;x86_64` with SDK Manager. Once available:

```powershell
'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd --name cloud_epub_fixture --package 'system-images;android-35;default;x86_64' --device pixel_2
& "$env:ANDROID_HOME/emulator/emulator.exe" -accel-check
# Run headlessly; this is a blocking emulator process in its own terminal.
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd cloud_epub_fixture -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
```

The emulator/AVD commands have not been exercised. Record actual resolution, density, API, WebView, font/display scale and acceleration before collecting results.

## Experiment seams and limits

- The resource transform injects an adapter script into the synthetic XHTML. The adapter enumerates original body text nodes before adding controls, keeps those node references, and addresses ranges by original-node ordinal and raw UTF-16 offset. It verifies the saved quote and the imported-file hash. This is an **unproven experiment**, not a production anchor schema or a general malformed-EPUB contract.
- The transform changes resource byte lengths. Readium's publication positions may therefore differ from original-archive positions. The custom anchors must be tested independently; **do not equate Readium progression/position with canonical original-content identity**.
- Only ordinary local `img` resources are handled. Decorative classification uses explicit `role="presentation"` or empty alt in the fixture. Missing alt becomes a meaningful-image probe. Those heuristics cannot establish correct classification for arbitrary books. SVG, picture/srcset and CSS backgrounds remain unimplemented.
- `ImageViewer` is a proposed native overlay with whole-image fit, double tap, pan, accessible action declarations, and staged Back. Link actions are logged without navigation. Focus return, gesture arbitration, dimensions, accessibility and position preservation are **untested**.
- UI chrome is an approximation using platform icons. The precise accepted toolbar inset, animation, margins under scaling, title collision handling and palette still require work and measurements. CSS samples use 31 px side padding and 10 px top padding; they are not accepted Android density/scaling conversions.
- The selection menu currently offers Copy and Precise bookmark, without Define. It does not implement word-boundary correction, bookmark-list management, synchronization, production lifecycle management, encrypted storage or account access. A `font` command changes typography for the experiment; no production Settings screen is built.
- Selection-gesture conflicts, previous-chapter swipe landing and vertical-boundary behavior are deliberately awaiting native observation. No test result can be inferred from a method or configuration flag.
- The `eval` intent and JavaScript bridge accept broad debug commands. **Use only this self-authored local fixture. Do not load user/untrusted books, include secrets, distribute this APK, or reuse these debug surfaces in production.**

## Probe commands

Start the app normally and wait for the first resource before using `invoke-spike.ps1`. These commands have not run on a device. The helper returns a correlated command-result JSON event or times out; it does not invent a pass.

```powershell
./invoke-spike.ps1 state
./invoke-spike.ps1 controls true
./invoke-spike.ps1 chapter 0
./invoke-spike.ps1 eval 'spike.state()'
./invoke-spike.ps1 eval "spike.select('duplicate-b','lantern',1)"
./invoke-spike.ps1 save-precise
./invoke-spike.ps1 font 150
./invoke-spike.ps1 restore precise
./invoke-spike.ps1 eval 'spike.state()'
./invoke-spike.ps1 chapter 1
./invoke-spike.ps1 eval '(spike.controls.get(0).scrollIntoView(), spike.state())'
./invoke-spike.ps1 save-regular
./invoke-spike.ps1 eval '(spike.controls.get(0).click(), null)'
./invoke-spike.ps1 full-screen
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell input keyevent 4
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell input keyevent 4
./invoke-spike.ps1 restore regular
./invoke-spike.ps1 save-progress
```

Programmatic `select`, `click`, scroll, font and chapter commands test adapter/control integration only. Separately use native touch for long-press word selection, correction boundaries, swipes, taps, double-tap, pan and scroll-indicator timing. Record coordinates and screen size for reproducibility. Preserve destination/viewport JSON before and after each action. Include malformed/unavailable anchors and generated-label selection attempts.

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" exec-out run-as dev.cloudepub.spike cat files/journal.jsonl > evidence/runtime.jsonl
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell uiautomator dump /sdcard/cloud-epub-window.xml
& "$env:ANDROID_HOME/platform-tools/adb.exe" pull /sdcard/cloud-epub-window.xml evidence/window.xml
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell screencap -p /sdcard/cloud-epub-screen.png
& "$env:ANDROID_HOME/platform-tools/adb.exe" pull /sdcard/cloud-epub-screen.png evidence/screen.png
```

Accessibility-node names or declared actions alone do not prove TalkBack focus, speech or usability. Run an actual available screen reader and separately report anything it cannot exercise. Theme persistence requires changing theme through the UI, force-stopping and restarting; do not clear app data during that check. Reflow needs recorded font and viewport/orientation changes. The current `configChanges` harness does not establish production process-recreation behavior.
