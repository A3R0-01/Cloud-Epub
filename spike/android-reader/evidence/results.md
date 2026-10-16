# Android fixture evidence — 16 October 2026

The dependencies finished downloading. The harness builds offline, installs, and renders its EPUB on Android. **The accepted reader is not fully validated:** the main API 35 probe sets currently report 45 passes and three native selection/tap failures. A separate TalkBack activation-after-return check also fails. Keep [Validate continuous-chapter reading and original-content anchors on Android](https://github.com/A3R0-01/Cloud-Epub/issues/16) open; renderer adoption and the final specification remain unproven.

## Reproduction and provenance

The complete source, fixture generator and commands are in [the spike README](../README.md). `run-probes.ps1` and `run-extended-probes.ps1` distinguish programmatic operations from injected native touch/key input. A completed script records passes and failures; it is not an all-pass exit-status contract. The first script resets only this disposable app's data and emulator viewport.

| Component | Observed configuration |
| --- | --- |
| Engine | Readium Kotlin 3.4.0, Fragment EPUB navigator, continuous scrolling |
| Build | Gradle 9.7.0; AGP 9.3.1; Kotlin Gradle plugin 2.4.20; JBR 25.0.3; compile SDK 37.0; Build Tools 36.0.0; desugaring 2.1.5 |
| Build evidence | [Offline assemble](build-offline.log) succeeds without retrieving dependencies; [captured environment and local APK hash](run-environment.json) identify the run |
| Primary runtime | Android 15/API 35 AOSP x86_64, WebView 124.0.6367.219, 1080×1920, density 420, font scale 1.0 |
| Reflow probes | 1080×1000 viewport for the image-first-visible case; font size 100→150 with generic serif/sans changes; system font scale 1.3 and display density 480; defaults restored afterwards |
| Secondary runtime | Android 11/API 30 AOSP x86_64, WebView 83.0.4103.120, 1080×1920, density 420; used for limited TalkBack compatibility work, not the full primary suite |
| Emulator | 37.2.12, WHPX usable and operational, headless SwiftShader, 2 CPU cores; 2048 MB RAM requested, automatically raised to 2560 MB for the primary API 35 image |
| Fixture | Self-authored DRM-free EPUB; SHA-256 `57a3bdad153ef14c2389e003abddb06a2c58fea01328e1d5cda6e9d21e273203`; three spine resources, 16,591 original characters in the long chapter; English/Unicode probes do not choose supported book languages |

The default AGP Kotlin 2.2.10 compiler rejected Readium's Kotlin 2.4 metadata. Explicitly adding KGP 2.4.20 to the root buildscript resolved that mismatch. Earlier SDK/network failures and the pre-build checkpoint are historical, not current blockers. SDK, Gradle, both system images, and APK build outputs remain local; no production PR or deployment was created.

## Observed matrix

| Requirement | Observed result and limit |
| --- | --- |
| Continuous chapter | Native vertical scroll at the long chapter's bottom did not advance. Initial controls were hidden. |
| Horizontal chapter changes | Default Readium Previous swipes landed at the previous chapter's **end**. The extension now disables built-in page turns while scrolling and routes deliberate horizontal drags through explicit chapter links. Native next/previous then landed at the beginning. |
| Buttons and chapter list | Native Next/Previous buttons landed at beginnings; a native chapter-list tap opened the last chapter at its beginning. First/last button enabled states and final outward swipe were observed. The harness list currently displays resource hrefs; production chapter-title presentation needs refinement. |
| No boundary wrap | Final outward native swipe retained the final chapter; button enabled states matched the first/last boundaries. |
| Precise duplicate occurrence | The second `lantern` in the distant identical `duplicate-b` paragraph restored at the exact original-node offset after font reflow, with the target visible. A temporary arrival highlight was observed. This uses the custom original-node adapter, not Readium's quote-only selection locator. |
| Punctuation/Unicode ranges | Programmatic original-range round trips passed for `don’t`, `well-being`, composed/decomposed café, a target after emoji, soft hyphen, NBSP, a ligature, and `well-being` split across inline markup. These do **not** prove native word-selection boundaries. |
| Native word selection | **FAIL:** native long press inside `well-being` selected only `well`. The current confirmation popup does not offer the accepted correction flow. A native selection/word-boundary adapter remains required. |
| Selection and swipes | Native horizontal swipe did not change chapters while selection was active. |
| Outside-tap sequence | **FAIL:** the first tested native outside-text tap left selection active; the second did not reveal controls. The native selection/action-mode interaction still needs an adapter. |
| Regular bookmarks | First-visible original-content addressing was exercised. An image Regular bookmark restored image index 0 after moving away. The viewport was deliberately shortened so an image could actually be the first visible content; the first attempt with an entirely fitting chapter had invalid setup and is not a renderer failure. |
| Original-content mapping | Original text was unchanged after transformation; six original images yielded five meaningful-image controls, with the explicitly decorative image omitted. Generated button-label ranges were rejected as Precise targets. Caption ranges restored. A wrong quote threw `Original quote mismatch` without moving the viewport. User-facing unavailable-location UI remains unimplemented. |
| Resource identity | Destinations are bound to the fixture hash and displayed original-resource href. Raw navigator locator state was observed lagging resource transitions in an early harness run, so it is not used as the sole canonical resource identity. |
| Image classes | Ordinary map, equation, reading-flow cover, linked image and missing-alt probes opened native viewers. Classification is the fixture's explicit empty-alt/presentation-role heuristic, not a proven meaningful/decorative classifier for arbitrary EPUBs. SVG, picture/srcset and CSS backgrounds are not implemented. |
| Linked images | A programmatic click on the linked-image control opened the native viewer without changing the reading chapter. A physical/native linked-image tap was not separately asserted. Its separate link action is displayed/logged; actual following of the link is not implemented or claimed. |
| Captions/alt text | Original captions remain selectable book text; caption anchors passed. The popup presents original caption/alt text using selectable native TextViews. Words only inside the PNG are not made into original book-text nodes. |
| Whole-image fit | Native popup and full-screen image bounds retained the complete 1200×800 image without crop. Full-screen metadata, visible link prose and zoom controls were absent. [Full-screen fit](full-screen-fit.png) and [zoom/pan](full-screen-zoom.png) are captured. |
| Native zoom/pan | Native double tap zoomed in; native drag changed the image matrix; the next double tap restored whole-image fit. Named native accessibility actions include Zoom image in/Fit whole image and Pan left/right/up/down. Discovery and operation of every custom action through a current screen reader remain unverified. |
| Return and Back | Native Exit full screen returned to popup. Native Back staged full screen→popup→reading. Native Close from popup and full screen was exercised. Tested returns recovered the original viewport and bookmarks. During the shortened-viewport test, full screen changed the underlying viewport 309→333 CSS px, then dismissal returned it to 309; scrollY stayed 186.2857. The reader therefore does not remain at fixed dimensions throughout inspection; near-bottom/reflow cases need more coverage. Backdrop retention was configured but not separately asserted. |
| Focus return | DOM focus returned to the opening viewer control. **TalkBack activation after return did not reopen the control**, despite successful activation into the popup. DOM focus alone is insufficient evidence of screen-reader focus restoration. |
| Native icon names/targets | Seven named native controls were present; measured touch bounds were at least 126×126 px at density 2.625 (48 dp). [Native toolbar hierarchy](native-toolbar.xml) records names and bounds. |
| Typography/scaling | Original destination remained visible after the tested system font/display changes and explicit restoration. Generic Android serif/sans fonts were used; [device font configuration](android35-fonts.xml) is captured. No browser fonts are bundled by the spike. Font redistribution, selected font files and broader language coverage remain production checks. |
| Margins/safe areas | Inline important styles were needed because Readium overrode the initial stylesheet-only body padding. Final computed padding is `10px 31px 24px`; equal side margins and native system-bar insets were measured. The complete reviewed toolbar inset/icon size/animation/title collision layout is not implemented. |
| Scroll indicator | A native scroll made it visible and it hid 3002 ms after the last classified scroll event in the final primary run. Raw user-scroll/hidden timestamps are in the runtime journal. The current user-scroll classification uses a 500 ms heuristic; long inertial-scroll behavior still needs a targeted check. |
| Theme | Native toggle changed the persisted theme flag; force-stop/restart retained it. The test harness uses Readium's palette (dark root measured RGB 0,0,0 with RGB 254,254,254 text), not the exact accepted warm-paper/near-black palette or finished themed chrome. |
| Lifecycle | A native Back during initial loading exposed an uninitialized pager call in the first harness. A readiness guard now exits before invoking navigator JS, and that early-Back branch was exercised. This is not a production background/process-restoration policy. |

## Evidence files

- [Focused probe results](probes.json): 26 passes in the corrected primary run.
- [Extended probe results](extended-probes.json): 19 passes, three failures; includes actual image matrices, visible metadata flags, selection quotes, targets and scaling snapshots.
- [Primary runtime journal](runtime-api35-complete.jsonl), [extended journal](runtime-extended.jsonl), [native word selection screenshot](native-selection.png), [reader controls](reader-controls.png).
- [Default-swipe/invalid-image-setup results](probes-before-swipe-adapter.json) are retained as historical observations. [Interrupted resize results](probes-interrupted-resize.json) capture the earlier harness recreation problem; they are not the corrected suite.
- TalkBack capture details are below. Full APKs and downloaded third-party packages are not committed.

## TalkBack provenance and scope

The primary AOSP image had no screen reader. The available x86_64-compatible [F-Droid TalkBack build](https://f-droid.org/en/packages/com.android.talkback/) is version `2021-04-23`, version code `370044210`, target SDK 30. It is F-Droid-built/signed, not a current Google Play Accessibility Suite build. APK SHA-256 is `c164970fcfb78af04a972e8b60770b7b998adafaf38109c69f662b32e75c10d6d7`; signer certificate SHA-256 is `d648071fc2c2421a3823fc9a3b91745b121c98675285008057f71f452b942bb8`. [APK signature verification](talkback-apk-verification.txt) is captured.

The first API 35 run crashed in TalkBack's BootReceiver. UiAutomation hierarchy dumps were used in that run and can interfere with active accessibility services; **do not attribute the crash solely to Android-version incompatibility**. A later attempt without hierarchy dumps also had no bound service and a captured BootReceiver crash. Its [screen-reader assertions are explicitly unvalidated](talkback-api35-probes.json). Ordinary taps can open the viewer even after the service crashes, so the probe script now checks that TalkBack is bound before and after scoring screen-reader activation.

The API 30 run avoided hierarchy dumps while TalkBack was active. Its [bound-service state](talkback-api30-services.txt), [guarded native activation results](talkback-api30-probes.json), [popup focus](talkback-api30-popup.png) and [return state](talkback-api30-return.png) establish a limited real service test: image activation and popup entry worked; activation after return failed. A global-menu gesture synthesis attempt did not establish custom-action usability and is not scored as a reader failure. Spoken output was not audited; the emulator was headless with audio disabled.

Reproduce the limited test with `run-talkback-probes.ps1 -CaptureName api30` after enabling the named TalkBack service on the disposable emulator. It deliberately avoids `uiautomator dump`. Current TalkBack versions, complete action discovery, screen-reader focus restoration, physical-device touch and broader EPUB/font fixtures remain acceptance gaps.

## Disposition

This supplies real evidence that continuous text, original-content range mapping and a mandatory image-button/native-viewer extension can work on the synthetic fixture. It does not establish the full accepted native reader contract. Keep the validation task open until the native word/correction flow, two-step outside tap, screen-reader return/custom actions and remaining detailed UI/lifecycle checks are covered. Do not replace the accepted product behavior with the observed defaults, silently choose the stack, or mark the build-ready specification complete.

Cleanup review retained the lifecycle callbacks, original-range verification, shared chapter-control updates and native viewer fit/pan boundaries. An ineffective native focus-provider experiment was removed. JavaScript/PowerShell syntax, Android compilation, offline assembly and staged whitespace checks were run; the recorded native failures remain failures. Successful tool installations, AVDs and downloads are retained locally. Accessibility services were reset and the test emulator/build daemon were stopped after capture; no recurring monitor or downloader remains active.
