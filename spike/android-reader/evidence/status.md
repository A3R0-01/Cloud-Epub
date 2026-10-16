# Environment and evidence status

16 October 2026. **Incomplete: no Android runtime evidence and no successful APK build.** This is a checkpoint for resuming the task, not a resolution comment or feasibility conclusion.

## Observed local checks

| Check | Observed result |
| --- | --- |
| Workspace isolation | Separate `codex/android-reader-feasibility` branch/worktree; production checkout unchanged |
| Hardware preflight | Ryzen 5 5600H, about 16 GB RAM; firmware virtualization enabled; Windows reports a present hypervisor |
| Windows hypervisor capability | `WHvGetCapability(0, ...)` returned HRESULT `0x00000000`, HypervisorPresent `1`; this does not prove an emulator boots |
| Existing Java | Android Studio JBR 25.0.3 runs |
| Gradle | Downloaded Gradle 9.7.0; `gradle --version` runs |
| SDK command-line tools | Downloaded package 15859902; SHA-256 matched official download-page value `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` |
| SDK platform tools | Downloaded/extracted 37.0.1; official repository SHA-1 matched `e03e78b1d80b396f1c3358e31251cb31740e1110` |
| SDK platform | Downloaded/extracted API 37.0 r2; official SHA-1 matched `ed8ebf7f8822a4de5686d427f237d2fa30ff7410` |
| SDK build tools | Downloaded/extracted 37.0.0; official SHA-1 matched `e9c97e26b5b5678002e9d3fed632c841ae62d99f` |
| Connected Android target | `adb devices -l` returned no connected devices |
| Emulator/system image | Downloads incomplete; no executable emulator or bootable AVD established |
| Build | Dependency retrieval started; compilation and APK creation have not completed |
| Fixture generation | Generator ran locally; captured EPUB SHA-256 `57a3bdad153ef14c2389e003abddb06a2c58fea01328e1d5cda6e9d21e273203` |
| JavaScript | `node --check app/src/main/assets/reader-spike.js` passed |
| Fixture repeatability/XML | Same-host regeneration produced the identical SHA-256; six packaged XML/OPF/XHTML documents parsed; mimetype was the first ZIP entry. EPUBCheck was not run |
| PowerShell | Both committed scripts passed the PowerShell parser |

## Download investigation

Official SDK repository metadata was readable. Large binary downloads then stalled or transferred too slowly to complete. The SDK Manager stopped making observable progress while its Java thread was reading a TLS socket. It was stopped, and the corresponding official archive downloads were attempted directly.

- Stable emulator: `https://dl.google.com/android/repository/emulator-windows_x64-16428233.zip` (455,342,868 expected bytes; expected SHA-1 `488ed747e82de7e9bb5247becd1ac043c7e5e85d`). The full download remained incomplete.
- API 35 AOSP x86_64 image: `https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip` (782,404,023 expected bytes; expected SHA-1 `2d857d170c0d1b827149565da34b3383e5306f7f`). The full download remained incomplete.
- A 1 MiB emulator range request timed out after 60 seconds with 259,158 bytes; a query-string variant timed out after 45 seconds with 45,278 bytes.
- `dl-ssl.google.com` and the official `redirector.gvt1.com/edgedl/` route timed out. An alternative `/dl/android/repository/` request also timed out.
- A newer emulator package probe remained incomplete (138,022 bytes after the bounded attempt). An older guessed archive URL returned 404; it was not selected as a candidate.
- An API 24 AOSP image download timed out after 180 seconds with 99,694 of 419,261,998 bytes. This was only an environment fallback attempt, not an Android support decision.
- A documentation-frame request failed with Windows Schannel `CRYPT_E_REVOCATION_OFFLINE`. This is one observed failure, not proof that all download stalls share that cause. The Git curl client also timed out. A Node HTTPS range probe obtained HTTP 206, transferred 61,942 bytes, then hit its idle timeout and aborted. An independent TLS client therefore did not restore a usable transfer.
- The second Gradle attempt resolved plugin metadata and downloaded some jars, then continued waiting on binary retrieval. Both build attempts were stopped during dependency retrieval. No compiler verdict or application-toolchain compatibility result is available. The captured [first attempt](build.log), [retry](build-retry.log) and [preflight](preflight.json) distinguish setup observations from runtime evidence.
- The two remaining full archive downloads were stopped after the fallback attempts also failed to establish usable transfers. Partial downloads and successful SDK/Gradle installs were preserved outside the repository for a later retry. No background downloader or emulator was left running.

Do not describe these as an emulator incompatibility, renderer failure, a compiled harness, or a successful validation. Fixing the download/runtime prerequisite is the next action. No production PR is needed.

## Review of this checkpoint

The cleanup review kept framework lifecycle/bridge/selection callbacks, native fit/pan methods, original-range verification and shared density conversion in place: these isolate real behavior or platform boundaries. No speculative abstraction or additional cleanup rewrite was introduced. JavaScript and PowerShell parser checks passed; Kotlin/Android compilation, lint and runtime verification remain unavailable. Inspect the build output before treating any source here as working code.

## Required runtime matrix

Every row below is **NOT RUN**. No passes or failures are inferred from documentation, static code, browser prototypes or programmatic equivalents of native gestures.

| Group | Evidence still required |
| --- | --- |
| Baseline | Candidate/version, fixture hash, exact successful build/install commands, Android/WebView, density, viewport, font and layout mode |
| Continuous chapters | Native vertical scrolling; no automatic advance at bottom; deliberate horizontal swipe versus buttons/list; all chapter-beginning landings, including Previous; no wrap |
| Reading progress | Same original-content passage after font, viewport, return/reopen, and restart changes; deliberate jumps update progress |
| Regular bookmarks | First visible original text; image represented by its viewer button; restoration after font/screen changes; no percentage substitution |
| Precise bookmarks | Duplicate word occurrences and identical passages; split markup; punctuation/apostrophe/hyphen boundaries; composed/decomposed Unicode, emoji offsets, soft hyphen, NBSP and ligature; native selection and correction; temporary arrival highlight |
| Canonical mapping | Before/after original-node destinations; generated labels excluded; caption targets; quote/hash mismatch produces unavailable location, not first-match fallback; transformed lengths distinguished from original positions |
| Image classification | Meaningful/decorative images, equations, reading-flow covers, linked images, missing metadata; distinguish fixture heuristics from general support; specify unsupported image formats |
| Image inspection | Popup and full-screen whole-image fit; hidden visible metadata/links/zoom controls in full screen; native double-tap toggle and pan; accessible alternatives |
| Image return | Exit full screen; staged Android Back; Close from both presentations; backdrop retention; exact underlying destination/viewport and bookmark preservation |
| Input conflicts | Selection versus chapter swipe; outside-tap clearing; popup versus reader navigation; image zoom/pan versus chapter gestures |
| Accessibility | Actual screen-reader action names and focus, opening/closing return focus, Back, icon touch targets, nonvisual image descriptions and zoom/pan actions |
| Layout/scaling | Reduced top spacing, equal side margins, accepted icon placement and title behavior; density-safe equivalents; system font/display scaling; available/licensed font inventory |
| Scroll indicator | Only native user scrolling triggers it; repeated user activity resets the timer; hide three seconds after stopping; programmatic movement distinguished |
| Preferences | Initial light theme; theme retained across books/restart; global typography applied with same-passage restoration |

The current harness intentionally lacks several complete UI flows. Extend it only as needed to observe the accepted behavior, label missing behavior honestly, and turn any newly exposed product decisions into separate map children. Do not close the task until the requested Android evidence is captured.
