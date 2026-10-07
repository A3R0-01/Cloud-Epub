# Android EPUB rendering, word anchors, and image replacement

Research date: 7 October 2026. Resolves the investigation in [issue #2](https://github.com/A3R0-01/Cloud-Epub/issues/2), under [map #1](https://github.com/A3R0-01/Cloud-Epub/issues/1). This is evidence and a proposed feasibility spike, not an engine selection or an accepted architecture decision. No Android app was built or EPUB fixture executed during this investigation.

## Findings that affect the decision

Readium Kotlin provides documented EPUB rendering, reader typography, chapter navigation, selection and locator APIs. Its current released version is **3.4.0**, published 11 September 2026. The original Fragment navigator exposes useful extension points for the requested image interaction. The newer Compose navigators are explicitly experimental and lack some of these points. Neither a locator schema nor the existence of selection APIs proves that a precise bookmark returns to the same occurrence in all books. That guarantee needs a fixture-based spike. [Release][release] [Navigator guide][navigator] [Parity comparison][parity]

A reflowable book's displayed page changes with font, viewport and image presentation. Store a content location for a Regular bookmark; show the newly laid-out page containing that location when restoring it. This is a **proposal for the still-open Regular bookmark semantics**, not confirmation that the user has accepted it. Precise bookmarks need a particular text range/occurrence, not only the word string or a page number. Readium deliberately uses publication positions rather than screen pages. [Navigator guide][navigator] [Locator model][locators]

## Verified Readium capabilities and constraints

| Need | Evidence in released 3.4.0 | Application responsibility or limit |
| --- | --- | --- |
| Reflowable EPUB | README lists EPUB 2 and EPUB 3; pagination, scrolling, RTL and search | Format support is not a promise to repair every malformed book. Media overlays are listed as planned. [README][readme] |
| Chapter navigation | `publication.tableOfContents`, `readingOrder`, and `navigator.go(Link)` | Build chapter list/UI; table-of-contents entries and spine resources can differ. [Navigator guide][navigator] |
| Reading progress and Regular bookmarks | `currentLocator`, JSON serialization, `initialLocator`, `go(Locator)` | The app persists and synchronizes these; the navigator does not store them. [Navigator guide][navigator] |
| Font family/size | `EpubPreferences` and preferences submission; font-face declarations and served app assets | Settings controls and their persistence belong to the app. Advanced spacing/line-height controls in the Fragment API require `publisherStyles = false`. [Preferences][preferences] [Fonts][fonts] |
| Selection and highlights | `SelectableNavigator`, `currentSelection()`, decoration groups/templates | A selection/quote is a useful starting point, not proof of unique occurrence restoration. [Navigator guide][navigator] [Selection source][selection] |
| Open an image viewer | Experimental `TapEvent.targetElement` gives `Content.ImageElement`; resource is readable using `publication.get(embeddedLink)` | Build viewer UI. Inline SVG uses a different content type. Images inside interactive links do not produce this image tap event. [Image guide][images] |
| Runtime customization | Fragment configuration has `registerJavascriptInterface`; `evaluateJavascript()` runs on the loaded visible reflowable resource | Navigation/load lifecycle, trusted bridge design and transformation code need validation. [Fragment source][fragment] |
| Resource transformation | `PublicationOpener.onCreatePublication` can change the root container; `TransformingContainer` and `Resource.map` can transform resources | These are seams, not a built-in image-to-button feature. Full-resource byte transforms can use substantial memory. [Opener][opener] [Container][container] [Resource][resource] |

The released parity table marks the Compose variants as missing tapped-element information, custom JavaScript interfaces, `evaluateJavascript()`, keyboard turns and resource-load error reporting. It also records selection differences. A Compose application shell does not require adopting the experimental Compose reader: the host can evaluate integration with the Fragment reader separately. Treat that combination as an implementation option to spike, not an already-tested result. [Parity comparison][parity]

### Android, build and licensing

Readium 3.4.0 requires minimum Android **API 24 (Android 7.0)** and lists compile SDK **37**. Its README lists Kotlin **2.4.20** and Gradle **9.7.0** for source/submodule integration; those two values are explicitly qualified as not mandatory simply because an app consumes Maven artifacts. Source integration must match the toolkit's Android Gradle Plugin; its version catalog pins **AGP 9.3.1**. Core-library desugaring is required. A real Maven integration build is still needed to establish the complete compatible app toolchain. Do not silently downgrade the reader to meet an undecided minimum Android version. [README][readme] [Version catalog][versions]

The toolkit is BSD 3-Clause: source and binary redistribution carry notice/disclaimer requirements and endorsement restrictions. Bundled and additional fonts and transitive dependencies have their own licenses; an app must retain the relevant notices and verify rights for every font it ships. LCP support involves an additional EDRLab binary, but DRM is excluded from the current project scope. [License][license] [README][readme] [Font assets][fontassets]

## Exact word occurrences and reflow

Readium locators identify a resource using `href`/media type and may carry progression, position and text (`highlight`, `before`, `after`). The HTML extension defines `cssSelector`, `domRange` and `partialCfi`; DOM ranges combine an element selector with a text-node index and character offset. The format describes possible addresses; it does not certify each navigator's production/resolution of every address. EPUB CFI addresses structural locations and UTF-16 offsets and supports assertions for recovering intended targets after some changes. [Locator model][locators] [HTML extension][html] [EPUB CFI][cfi]

The **actual 3.4.0 Fragment selection source** returns selected text and its before/after context; Kotlin `currentSelection()` copies `currentLocator` and replaces its `text`. It does not export the selected DOM range there. A CSS selector that happens to be in the current locator is not necessarily the selected word's exact address. Therefore persisting `currentSelection().locator` alone must not be advertised as the precise-occurrence solution. The JavaScript computes context from `document.body.textContent`, so inserted button labels can affect that context. The parity guide's broad selection claim should be interpreted alongside this source, rather than as a guarantee. [Fragment source][fragment] [Selection source][selection] [Parity comparison][parity]

Readium's default EPUB positions service uses archive-entry length in 1024-byte units, producing resource progressions. These positions are independent of font/viewport changes, but are coarse approximations rather than word addresses or print page numbers. Repacking/replacing book content or changing the position calculation can change them. [Positions source][positionssource] [Positions model][positions]

**Proposed anchor contract to test:** bind bookmarks to an immutable imported book revision (content hash plus app book ID). Store the original spine-resource href, a canonical selected range with raw UTF-16 start/end addresses, and selected quote/context as cross-checks. Persist range/anchor schema version. Preserve the raw text; use a separate presentation string for the bookmark list. Resolve the structural address first and verify its quote; attempt context-scoped recovery only if uniquely identifiable. If ambiguous/missing, explicitly report that and offer the chapter or approximate reading position. Never silently jump to the first matching word. These are Cloud EPUB proposals, not Readium guarantees. [HTML extension][html] [EPUB CFI][cfi]

A `word + occurrence number` counted in rendered text is fragile: hidden text, new button labels, text normalization and altered editions can change the count. Repeated words and repeated entire paragraphs require structural disambiguation. The spike must validate both navigation and a visible temporary highlight of the precise target, including text split across inline elements. DOM/CFI offsets use UTF-16 units; user-visible characters and words use different boundaries. Unicode UAX #29 defines grapheme/word segmentation and requires language tailoring for reliable Thai, Lao, Chinese and Japanese words. Define supported book languages before claiming universal word selection; do not split by ASCII spaces or cut a surrogate pair/combining sequence. [EPUB CFI][cfi] [Unicode segmentation][unicode]

## Image replacement without changing the authoritative EPUB

Readium documents image taps and custom resource/JavaScript seams, but **does not document a ready-made image replacement button**. Opening the original image on tap is supported more directly than replacing the image in text. User-requested separators, blank space and a button require app styling/interaction code. [Image guide][images] [Container][container] [Fragment source][fragment]

**Proposed implementation approach:** keep the original EPUB and its text addresses authoritative. Build a rendering-only view that preserves original text nodes and IDs, records original image-resource references, and hides/reduces eligible images while adding an accessible viewer control. Exclude app-inserted labels from bookmark text/range calculations. Keep a deterministic mapping from original nodes to transformed nodes; do not assume selectors, text-node indices, CFI steps or byte-based positions stay correct after adding wrappers/siblings. Merely using `display:none` or `aria-hidden` does not remove a button's text from `textContent`. A resource transform that changes byte length must not become the source of canonical publication position calculations. The exact mapping and interception mechanism are spike work. [HTML extension][html] [Selection source][selection] [Positions source][positionssource]

Capture the current content anchor before opening the overlay. Keep the reader mounted with the same dimensions/preferences while the overlay is visible; restore/verify the anchor on close, Back and process restoration. If the image mode itself changes, restore by content anchor after pagination settles. Inject consistently before first layout on every chapter load and on reload; repeatedly modifying already-laid-out content could otherwise cause jumps. The safe load timing, repagination and restoration behavior have **not** been established by this research. [Fragment source][fragment] [Navigator guide][navigator]

Start with ordinary local `<img>` elements. Treat linked images, `<picture>`/`srcset`, external-resource SVG, inline SVG, CSS backgrounds, small ornamental images, cover pages, figures/captions and meaningful alt text as explicit eligibility decisions. Do not erase image meaning or make an originally navigable link inaccessible. The documented tap API excludes linked images and distinguishes image resources from inline SVG; it is not sufficient to cover all these cases. [Image guide][images]

Keep any native JavaScript bridge narrowly limited to reader actions and validated local resource identifiers. Never expose account credentials, keys or arbitrary file reads to publication content. Android documents that `addJavascriptInterface` is exposed to frames and has origin limitations; EPUB reading-system guidance treats publication scripts as untrusted. Blocking book scripts/remote resources while allowing trusted reader code needs its own integration verification, especially for the project's offline/private reader. [Android bridges][bridges] [EPUB security][rs]

## Fonts and diagnosing mixed text

Reader typography and app-menu typography are separate integrations. Readium can serve packaged TTF/OTF files and register regular, italic, bold and variable font faces for reflowable book content; its guide says Android downloadable fonts are not supported by that reader interface. Native Compose menu text can use `res/font` and a `FontFamily` independently. Package licensed fonts for predictable offline behavior; test fallback glyph coverage for accepted languages. [Readium fonts][fonts] [Compose fonts][composefonts]

The existing reader's mixed-text problem has no reproducible sample yet. Candidate causes to investigate include incorrect source text/encoding, CSS layout or publisher-style overrides, missing/obfuscated font handling, font fallback, bidi/shaping, ligatures and pagination errors. These are hypotheses, not a diagnosis. EPUB is packaged structured web content; replacing all book content with extracted plain text would discard meaningful layout, semantics, hyperlinks and image context. EPUB font obfuscation is a defined format feature and should not automatically be treated as DRM. [EPUB specification][epub] [EPUB reading systems][rs]

Use EPUBCheck to report conformance problems, then inspect raw XHTML order, manifest/spine and font declarations alongside screenshots and selected/copy text. Passing EPUBCheck does not prove that the chosen reader renders a book correctly. The acceptance comparison must include publisher styles on/off and each supported font on recorded Android/WebView versions. User samples, if supplied later, can remain local and private; synthetic fixtures are enough to begin the spike. [EPUBCheck][epubcheck] [Preferences][preferences]

## Proposed feasibility spike and exit evidence

Use a disposable Android reader harness, with synthetic DRM-free reflowable EPUBs and an agreed language set. Readium's Fragment navigator is a reasonable **candidate to test first** because its verified extension points fit the experiment; the user has not selected it. Record engine version, app toolchain, Android API, WebView version, font, viewport and layout mode for each run.

| Fixture | Required evidence |
| --- | --- |
| Same word repeated in one paragraph; identical paragraphs far apart; selection across emphasis spans | Bookmark restores and highlights the selected occurrence after restart and on a differently sized device. No first-match substitution. |
| Accents in composed/decomposed forms, emoji before target, soft hyphens, nonbreaking spaces, ligatures; agreed RTL/CJK languages | Range respects original UTF-16 addresses and user selection boundaries; text order/glyphs match the fixture's expected content. |
| Embedded ordinary and obfuscated fonts; font-family fallback; bold/italic; long chapters and footnotes | No missing/duplicated text, overlapping lines or misplaced chapter links at minimum/default/maximum text sizes. |
| Images before/after a target; multiple figures; captions; linked image; SVG; missing image resource | Viewer control is accessible; target occurrence is unchanged with image mode toggled. Unsupported cases have defined behavior. |
| Font changes, rotation, theme, scrolling/pagination, chapter reload, process death, overlay Back/close | Same content anchor is recovered; Regular bookmark opens the new page containing its anchor. |
| Missing resource/invalid XHTML, pathological long chapter, remote image/script, fixed-layout and DRM samples represented by synthetic rejection cases | Load failures are reported; unsupported scope is explicit; no unapproved network/plaintext leakage or app bridge access. |

Exit evidence should include the small fixture corpus, observed screenshots/text comparisons, locator JSON before/after, exact-occurrence assertions, and notes on every unsupported case. A successful spike supports an engine decision; failure identifies whether a narrowly scoped adapter is sufficient or a different engine/requirement is needed. Do not substitute documentation review for these runtime results.

## Other viable candidates and remaining choices

| Candidate | First-party evidence | Unresolved for this project |
| --- | --- | --- |
| MuPDF | Library with Android integration; official docs list DRM-free EPUB 2 and limited EPUB 3 support; AGPL/commercial licensing. [Formats][mupdfformats] [Android integration][mupdfandroid] [License][mupdflicense] | Exact-occurrence anchors, requested buttons and EPUB typography need an independent spike. The Android guide contains old artifact/repository examples, so do not reuse its historical minimum/toolchain as a validated current build. Licensing fit requires review before adoption. |
| FBReader SDK | Binary commercial Android engine with text styles, selection, bookmarks, TOC and sample UI source. [SDK][fbreader] | Vendor page says API 16, but its changelog raises the minimum to 21 for SDK 1.2.0; verify against actual selected artifact. Current public changelog ends at 1.2.2 (2024). Precise anchors, replacement seam, maintenance/support and price are not established. [Changelog][fbreaderchanges] |

Readium has the clearest publicly inspectable match for this research, but that is a candidate assessment, not a selection. Remaining decisions: minimum supported Android, book languages, exact Regular bookmark reflow semantics, precise-selection UX, image eligibility/button design, handling unsupported/invalid EPUB, and acceptance thresholds for the fixture spike. A backend/privacy design must also establish how decrypted local resources are supplied to the reader; this note does not resolve local key/cache handling.

[release]: https://github.com/readium/kotlin-toolkit/releases/tag/3.4.0
[readme]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/README.md
[navigator]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/navigator/navigator.md
[preferences]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/navigator/preferences.md
[fonts]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/navigator/epub-fonts.md
[images]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/navigator/epub-image-viewer.md
[parity]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/navigator/epub-feature-parity.md
[fragment]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt
[selection]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/navigator/src/main/assets/_scripts/src/selection.js
[opener]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/streamer/src/main/java/org/readium/r2/streamer/PublicationOpener.kt
[container]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/shared/src/main/java/org/readium/r2/shared/util/resource/TransformingContainer.kt
[resource]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/shared/src/main/java/org/readium/r2/shared/util/resource/TransformingResource.kt
[positionssource]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/readium/streamer/src/main/java/org/readium/r2/streamer/parser/epub/EpubPositionsService.kt
[positions]: https://readium.org/architecture/models/locators/positions/
[locators]: https://readium.org/architecture/models/locators/
[html]: https://readium.org/architecture/models/locators/extensions/html.html
[cfi]: https://w3c.github.io/epub-specs/epub33/epubcfi/
[unicode]: https://www.unicode.org/reports/tr29/
[epub]: https://www.w3.org/TR/epub-33/
[rs]: https://www.w3.org/TR/epub-rs-33/
[epubcheck]: https://github.com/w3c/epubcheck
[versions]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/gradle/libs.versions.toml
[license]: https://github.com/readium/kotlin-toolkit/blob/3.4.0/LICENSE
[fontassets]: https://github.com/readium/kotlin-toolkit/tree/3.4.0/readium/navigator/src/main/assets/readium/readium-css/fonts
[composefonts]: https://developer.android.com/develop/ui/compose/text/fonts
[bridges]: https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
[mupdfformats]: https://mupdf.readthedocs.io/en/latest/guide/what-is-mupdf.html
[mupdfandroid]: https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html
[mupdflicense]: https://mupdf.readthedocs.io/en/latest/license.html
[fbreader]: https://sdk.fbreader.org/android.html
[fbreaderchanges]: https://sdk.fbreader.org/android-changelog.html
