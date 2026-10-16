# Android reader feasibility spike — experimental evidence

For [Validate continuous-chapter reading and original-content anchors on Android](https://github.com/A3R0-01/Cloud-Epub/issues/16), under [Find the build-ready specification for Cloud EPUB Android and backend](https://github.com/A3R0-01/Cloud-Epub/issues/1).

**The harness now builds and runs on Android. It has known native behavior failures and is not a production reader or renderer adoption.** See [the runtime results](evidence/results.md) for observed results and their limits. Keep this throwaway branch separate from production and open no production PR. The earlier setup-only capture is preserved in commit `ba4c79832feeeea65710303bae54e699e780bc9a`.

The candidate is Readium Kotlin 3.4.0's Fragment navigator. The successful build uses AGP 9.3.1, Gradle 9.7.0, Kotlin Gradle plugin 2.4.20, compile SDK 37.0, Build Tools 36.0.0, desugaring 2.1.5, and Studio's JBR 25.0.3. AGP's default Kotlin 2.2.10 failed on Readium's 2.4 metadata, so the root buildscript explicitly upgrades the compiler. An offline assemble also passes. The harness declares min API 24 and target API 35 only for the experiment; neither is an accepted support-policy decision.

## Fixture provenance

`generate-fixture.ps1` creates the self-authored, DRM-free, English/Unicode probe EPUB, including two Windows-rendered synthetic PNGs. The corpus contains three spine resources, long continuous text, duplicate words and identical paragraphs, curly punctuation, internal hyphens/apostrophes, composed/decomposed accents, an emoji before a target, a soft hyphen, NBSP, a ligature, text split across emphasis, selectable captions, an equation, a linked image, a reading-flow cover, an explicitly decorative image, and an image lacking alt metadata. English/Unicode probes do not decide the supported book-language set.

The image drawings use local Arial through Windows System.Drawing. The EPUB contains raster images, not a bundled font. Native book-font probes use Android generic `serif` and `sans-serif`; browser-prototype fonts are not included. Cross-machine PNG generation can vary; the committed EPUB and its recorded SHA-256 identify this captured fixture. No user books or credentials are included.

## Build and start

Use PowerShell. Set `JAVA_HOME` to an appropriate installed JDK and `ANDROID_HOME` to the experiment's SDK. Use a separately installed Gradle 9.7.0 on PATH; no Gradle wrapper is committed yet.

```powershell
./generate-fixture.ps1
node --check app/src/main/assets/reader-spike.js
gradle :app:assembleDebug --console=plain
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell am start -n dev.cloudepub.spike/.MainActivity
```

The primary tested AVD is a disposable AOSP API 35 x86_64 phone: 1080×1920, density 420, Android System WebView 124.0.6367.219, emulator 37.2.12 with WHPX acceleration. Install `platform-tools`, `emulator`, `platforms;android-37.0`, `build-tools;36.0.0`, and `system-images;android-35;default;x86_64` with SDK Manager. The newer Android CLI is also available through current command-line tools. Once installed:

```powershell
'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd --name cloud_epub_fixture --package 'system-images;android-35;default;x86_64' --device pixel_2
& "$env:ANDROID_HOME/emulator/emulator.exe" -accel-check
# Run headlessly; this is a blocking emulator process in its own terminal.
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd cloud_epub_fixture -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
```

These AVD creation/boot and APK build/install operations were exercised on Windows. The local experiment keeps tools under `D:/Apps/Android/cloud-epub-spike` and sets `ANDROID_AVD_HOME` to its `avd` directory. Record actual resolution, density, API, WebView, font/display scale and acceleration for new runs. On Windows, a background emulator can be launched with `Start-Process -WindowStyle Hidden` and the same arguments.

## Experiment seams and limits

- The resource transform injects an adapter script into the synthetic XHTML. The adapter enumerates original body text nodes before adding controls, keeps those node references, and addresses ranges by original-node ordinal and raw UTF-16 offset. It verifies the saved quote and the imported-file hash. Fixture range round trips now have runtime evidence; this remains an experimental schema, not a general malformed-EPUB contract. Persisted destinations use the displayed resource's href and original-content anchor, rather than assuming the current coarse navigator locator always identifies the displayed resource during transitions.
- The transform changes resource byte lengths. Readium's publication positions may therefore differ from original-archive positions. The custom anchors must be tested independently; **do not equate Readium progression/position with canonical original-content identity**.
- Only ordinary local `img` resources are handled. Decorative classification uses explicit `role="presentation"` or empty alt in the fixture. Missing alt becomes a meaningful-image probe. Those heuristics cannot establish correct classification for arbitrary books. SVG, picture/srcset and CSS backgrounds remain unimplemented.
- `ImageViewer` is a native overlay with measured whole-image fit, native double tap and pan, accessibility action declarations, and staged Back. Popup/Close/Back preserve the mounted viewport in the tested cases. Link actions are logged without navigation. DOM focus return and native action names were observed; consult the evidence matrix before inferring screen-reader usability.
- UI chrome is an approximation using platform icons. The precise accepted icon inset/size, animation, title collision handling and palette still require app work. The adapter now enforces 31 CSS px side padding and 10 px top padding through inline important styles, because Readium's selectors overrode the first stylesheet-only attempt. At density 420, the fixture's CSS pixel ratio is 2.625; icon target measurements are in the results. System-bar insets are reserved through native WindowInsets. This establishes a measured fixture conversion, not a universal typography/margin guarantee.
- The selection menu currently offers Copy and Precise bookmark, without Define. It does not implement word-boundary correction, bookmark-list management, synchronization, production lifecycle management, encrypted storage or account access. A `font` command changes typography for the experiment; no production Settings screen is built.
- Default native Previous swipes landed at the prior chapter's end. The tested adapter disables Readium's built-in page turns while scrolling and routes deliberate horizontal drags through explicit chapter links; native Next/Previous, no-advance and final-boundary probes then passed. Selection blocks chapter swipes. Native automatic selection of `well-being` and the outside-tap sequence still fail. Do not infer those behaviors from the successful programmatic range probes.
- The `eval` intent and JavaScript bridge accept broad debug commands. **Use only this self-authored local fixture. Do not load user/untrusted books, include secrets, distribute this APK, or reuse these debug surfaces in production.**

## Probe commands

Start the app normally before using `invoke-spike.ps1`. The activity queues commands until its first page is ready. The helper returns a correlated command-result JSON event, reports a correlated command error, or times out. The commands below were exercised; programmatic commands and native actions are distinguished in the results.

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

Run `./run-probes.ps1` for the focused probe set and `./run-extended-probes.ps1` for native viewer, selection, theme and scaling probes. **The first script clears only the disposable app's data and resets the emulator viewport.** Both scripts write JSON results, including observed failures; a completed script is not a claim that every row passed. They currently target `emulator-5554`, with one emulator connected. Test build/runtime versions are recorded in the evidence.

Accessibility-node names or declared actions alone do not prove TalkBack focus, speech or usability. The installed F-Droid TalkBack 2021-04-23 build crashed during the first API 35 test run; UiAutomation interference is a possible confounder. On API 30 it could activate the image button and enter the popup, while activation after return failed. See the runtime results and the limited `run-talkback-probes.ps1` capture. Theme persistence was exercised through a native toggle, force-stop and restart without clearing data during that check. Font/display scaling was tested at font scale 1.3 and density 480, then reset. The harness handles size/layout configuration changes in place; this does not establish a production process-recreation or background-save policy.
