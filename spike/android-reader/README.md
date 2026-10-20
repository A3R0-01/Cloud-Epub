# Android reader feasibility spike

Throwaway evidence for [Validate continuous-chapter reading and original-content anchors on Android](https://github.com/A3R0-01/Cloud-Epub/issues/16), under [Find the build-ready specification for Cloud EPUB Android and backend](https://github.com/A3R0-01/Cloud-Epub/issues/1). Read [the measured results and remaining limits](evidence/results.md).

This branch is an experiment, not a production reader or renderer adoption. Do not merge it or open a production PR. Historical setup-only and first-runtime checkpoints remain in commits `ba4c79832feeeea65710303bae54e699e780bc9a` and `988996793d551a46102cea8e08de438ae705c58c`.

## Build and run

PowerShell, Windows, Readium Kotlin 3.4.0 Fragment navigator. Tested toolchain: Gradle 9.7.0, AGP 9.3.1, explicitly upgraded KGP 2.4.20, Studio JBR 25.0.3, compile SDK 37.0, Build Tools 36.0.0, desugaring 2.1.5. AGP's default Kotlin 2.2.10 could not consume Readium's Kotlin 2.4 metadata. The final build also succeeds offline.

```powershell
$env:JAVA_HOME='D:/Apps/Android/Android Studio/jbr'
$env:ANDROID_HOME='D:/Apps/Android/cloud-epub-spike/sdk'
$env:GRADLE_USER_HOME='D:/Apps/Android/cloud-epub-spike/gradle-home'
$gradle='D:/Apps/Android/cloud-epub-spike/tools/gradle-9.7.0/bin/gradle.bat'
$adb="$env:ANDROID_HOME/platform-tools/adb.exe"
# Run from this directory. No wrapper is committed.
./generate-fixture.ps1
node --check app/src/main/assets/reader-spike.js
& $gradle :app:assembleDebug --offline --console=plain
# Check the build exit code before installing.
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb shell am start -n dev.cloudepub.spike/.MainActivity
```

Primary disposable AVD: AOSP API 35 x86_64, Android System WebView 124.0.6367.219, 1080×1920, density 420, font scale 1.0, emulator 37.2.12, WHPX, headless host GPU (NVIDIA RTX 3050), 4 cores/2560 MB. Local tools/AVDs are cached outside git under `D:/Apps/Android/cloud-epub-spike`. Install SDK packages `platform-tools`, `emulator`, `platforms;android-37.0`, `build-tools;36.0.0`, and `system-images;android-35;default;x86_64`. Use a separate terminal:

```powershell
$env:ANDROID_AVD_HOME='D:/Apps/Android/cloud-epub-spike/avd'
'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd --name cloud_epub_fixture --package 'system-images;android-35;default;x86_64' --device pixel_2
& "$env:ANDROID_HOME/emulator/emulator.exe" -accel-check
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd cloud_epub_fixture -no-window -no-audio -no-snapshot -gpu host -cores 4 -memory 2560
```

These are exercised Windows commands. For background launch use `Start-Process -WindowStyle Hidden`. Min API 24 and target API 35 are harness settings, not the product support policy. The read-only accessibility inspector uses API 34's app-process query API; the actual native focus bridge uses ordinary Android View APIs.

## Fixture

`generate-fixture.ps1` makes a self-authored, DRM-free EPUB, with no user data. Three spine resources contain 16,591 original long-chapter characters, repeated words/identical paragraphs, Unicode punctuation/accents/emoji/soft hyphen/NBSP/ligature, split inline markup, selectable captions, equations, reading-flow cover, linked image and missing metadata. Ten image sources yield nine viewer controls: ordinary PNGs, external SVG, responsive picture/source, labelled single CSS background, inline SVG; explicit empty-alt/presentation images are omitted.

The two PNG drawings use local Windows Arial via System.Drawing. No font is embedded. Book typography tests use Android generic serif/sans-serif. The committed EPUB/hash identifies the measured bytes; Windows font rendering can vary across machines. English/Unicode probes do not choose supported book languages. EPUBCheck 5.4.0 reports zero errors/warnings on the final fixture.

## Required extensions demonstrated here

- Resource injection collects original text-node references before adding image controls. Destinations store original-resource href, original-node ordinal and raw UTF-16 offsets, quote verification, and imported-file SHA-256. Generated labels and image-contained SVG text are excluded. Readium progression is not canonical content identity: transformed byte lengths differ.
- Native long press retains WebView's selection handles and menu. Choosing Precise bookmark resolves that native selection to a complete original-node word, including inline splits; the native confirmation shows this candidate and supports Previous word/Next word before Save/Cancel. WebView may place native handles around only `well` in `well-being`. The adapter paints the complete original-word candidate with a CSS Highlight without mutating that native range; Precise bookmark and Copy use the complete candidate. Correction moves the visible candidate before Save. Native handle geometry remains engine-controlled and needs production UI qualification. This is an English/Unicode fixture rule, not multilingual word segmentation.
- Activity-level deliberate horizontal gesture handling disables Readium page turns while scrolling, lands at chapter beginnings, and respects selection/viewer state. First outside tap clears selection/action mode; the following tap toggles controls.
- Popup/full screen keep shared system-bar dimensions stable. If the mounted reader changes size during inspection, Close restores the captured original anchor. Internal image links resolve their resource and fragment explicitly.
- Returning native Dialog focus to a WebView DOM button is insufficient for accessibility. A native control temporarily represents the same original image at the DOM control's measured bounds; its DOM counterpart is removed from accessibility traversal while represented. Native accessibility focus restores to that control, and activation reopens the same image. Scroll/drag/navigation remove or reposition it. It does not become original book text.
- Native zoom/pan actions use app resource IDs. TalkBack filters IDs in the Android system-action range from its custom Actions menu; declared labels alone were insufficient. Actual Actions-menu execution is tested.
- SVG/picture/single-background fixtures rasterize through a same-origin canvas seam for the native viewer. This does not establish arbitrary resource/author-semantic support. Extraction limits and error/fallback rules require specification.
- Shared theme colors, centered/ellipsized title, 18dp icons in 48dp targets, footer inset and 200ms fade/translation approximate the accepted chrome using platform icons. Body padding is enforced inline (`10px 31px 24px`) because Readium selectors outrank the injected stylesheet. This is measured Android conversion, not final visual acceptance.

## Reproduce the checks

Run native suites with accessibility disabled, one AVD connected as `emulator-5554`, and the app resumed. `run-probes.ps1` clears only this disposable app's data and resets its viewport. Scripts record each assertion with observed JSON; script completion alone does not imply every row passed.

```powershell
./run-probes.ps1 -Adb $adb
./run-extended-probes.ps1 -Adb $adb
./run-gap-probes.ps1 -Adb $adb
./run-word-probes.ps1 -Adb $adb
```

The typography check asserts computed size and family: the harness percentage is converted to Readium's scale factor. `prepare-touch-probe.ps1` compiles the small `probes/TouchProbe.java` helper using the installed JDK/Android jar/d8 and pushes its dex into the disposable AVD. The extended suite invokes it through `app_process` as shell to inject real, tightly timed MotionEvents; separate Android input process startups were unreliable for double taps. This is test tooling, not an app zoom command or a production API. Set its SDK/JDK parameters for another host.

Programmatic `select`, `click`, scroll, font and chapter commands test integration; separately scored native touch/key probes cover actual selection, correction, swipes, icon taps, zoom/pan and inertia. Broadcast commands are correlated/acknowledged, retried only until acknowledgement, and wait for reader readiness. The app consumes the adapter ready signal and serializes restore work so early/cached page callbacks cannot expose an unrestored reader. They avoid launching debug activities during gestures, which previously interfered with focus/lifecycle. Results/errors are returned within a bounded 120-second timeout (90 seconds for native reader readiness).

```powershell
./invoke-spike.ps1 -Command state -Adb $adb
./invoke-spike.ps1 -Command eval -Data "spike.select('duplicate-b','lantern',1)" -Adb $adb
./invoke-spike.ps1 -Command save-precise -Adb $adb
./invoke-spike.ps1 -Command font -Data 150 -Adb $adb
./invoke-spike.ps1 -Command restore -Data precise -Adb $adb
& $adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl
```

For screen-reader tests, install/enable the captured TalkBack 16.2 source build or Backtalk 16.2 and eSpeak TTS on this disposable rootable AOSP image; finish service onboarding. See results/environment for APK provenance, build changes and hashes. Then:

```powershell
./run-screenreader-probes.ps1 -Adb $adb -Serial emulator-5554 -CaptureName google-talkback-api35
# run-talkback-probes.ps1 delegates to this calibrated suite.
```

`raw-touch.sh` and `screenreader-key.sh` deliver raw touch exploration/keyboard events through this AVD's identified input devices (`event2` touchscreen, `event1` keyboard, 0..32767 axes). These mappings are device-specific. Activation uses the service's Meta+Space path; Meta+Ctrl+A opens its Actions menu. Screenshots and read-only app accessibility queries verify focus/action discovery. Do not call UiAutomation during these tests: it can suppress accessibility services. A bound service plus `adb input tap` does not prove screen-reader activation. The plain `AccessibilityControlActivity` is an optional diagnostic negative control, not part of the reader UI.

## Limits

The debug bridge/broadcast/eval surfaces are broad and intended only for this trusted, script-free fixture. Do not load untrusted/user books, ship this APK, or reuse these surfaces in production. No auth, encryption, synchronization, storage architecture, dictionary, production bookmark management or complete lifecycle/recovery system is implemented.

Fixture image classification follows explicit author metadata. Mis-authored/missing/ambiguous metadata, nested/multilayer CSS, external links/resources, giant assets, malformed EPUB/SVG, extraction limits and unsupported-image fallback are not guaranteed. Physical devices, shipping Google Play TalkBack, supported Android/WebView/language/font matrices and production visual acceptance remain release qualification. The evidence supports architecture/specification decisions, not adoption or certification.
