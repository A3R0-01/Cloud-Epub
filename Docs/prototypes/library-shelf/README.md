# Cloud EPUB library shelf prototype

Planning question: how should All, Collections, and Authors present books and groups uniformly?

Context: [Define library tabs, Collections, Authors, and editable book details](https://github.com/A3R0-01/Cloud-Epub/issues/12).

The user reviewed List, Shelf, and Large covers and chose Shelf for every tab and opened group. Collection and Author images are fans of member-book covers; an empty Collection uses a question mark. The user accepted the final revised preview on 2026-10-08 with “That looks great.”

## Preview

Open `preview.html` in a browser. It is a standalone visual simulation with no installation or service calls. The interactive source is `source.html`, an inline HTML fragment for Codex; `initial-variants.html` preserves the comparison the user reviewed. To regenerate the standalone wrapper, use the visualize skill's `scripts/render.py source.html preview.html`.

All uses cover/title-only book tiles with single-line titles and a bottom-right options icon. Collections and Authors use the same shelf layout with cover fans, names, and counts below names. Cover fan selection takes up to three member covers in title order.

The user's latest revision moves the three icon tabs back to the top, directly below the Cloud EPUB header. They span the available width in three equal portions: an open book for All, grouped books for Collections, and a person for Authors. This supersedes the previously explored compact centered bottom bar. Shelf content scrolls below the tabs. Each icon retains its accessible tab name, a tooltip, and an active state. A gear opposite Cloud EPUB opens a provisional Settings destination. Detailed Settings/account/profile choices are tracked in [Define Settings navigation and account controls](https://github.com/A3R0-01/Cloud-Epub/issues/15); the example account menu is not an accepted editable-profile specification.

The accepted visual baseline uses a 40px visible tab height and a 6px following gap, with an expanded invisible hit area for touch. Collections uses a bottom-right floating plus icon to open Collection creation instead of a wide text button. All uses the same floating icon to open Add a book with the already agreed device and Google Drive import sources. Those import endpoints are visual stubs. Authors and opened groups do not show the floating create button. The Add button overlays the full-height shelf, without a reserved footer. Padding at the end of the scrollable content allows later tiles to move above it. Cover frames are 128px high so the complete third sample tile is visible before scrolling in the normal and narrow previews. These browser dimensions establish the reviewed appearance; Android accessibility and runtime layout still need validation.

Opened Collection/Author shelves omit the in-app Back button. Android Back returns to the corresponding index. A control below the phone simulates that system action in this browser preview and closes an open sheet before leaving a group. The shelf hides the native draggable scrollbar and uses a noninteractive 2px-wide, 24px-long position indicator, hidden when all content fits. It stays in the outer right gutter, horizontally clear of the floating button.

The preview simulates tabs, search/sort, group navigation, full-title Details, options, author suggestions, Collection creation, and membership selection. State is local to the preview. Book covers are synthetic stand-ins, not publication assets.

This is throwaway planning code. No real EPUB reading, downloads, encryption, synchronization, date validation, or Author-entry identity persistence is validated. The author editor illustrates suggestion selection and deliberate creation; it is not the production multiple-author identity model. Collection rename/delete actions are indicated, not implemented.

## Inspection

Checked in Chromium at normal and narrow widths: single-line titles, Details, options, author suggestion `elson` -> `Elson Madara`, Collection navigation, cover fans on both grouping tabs, empty Collection placeholder, and no runtime errors in the inspected interactions.

Also checked full-width top navigation, equal icon targets, all three rendered navigation icons, the top-right Settings icon, and opening Settings.

Checked the smaller top bar, floating Add book source menu, floating Collection creation, and the absence of the create button on Authors and inside opened groups.

Checked group return through the simulated Android Back control, absence of the group Back button, the indicator's noninteractive dimensions and motion, and its separation from the Add button.

A focused headless check reproduced the reserved-footer regression: at scroll position zero the third tile ended below the shelf viewport, and the Add button sat outside it. Removing the footer reservation and shortening cover frames made the same check pass at 440px and 320px browser widths: the complete third tile fits and the Add button overlays the shelf. This is a browser-prototype check, not Android runtime validation.

The prototype stays on a throwaway branch and is not merged into the application. Decisions are authoritative in the [accepted resolution](https://github.com/A3R0-01/Cloud-Epub/issues/12#issuecomment-6056991594), not in this code.
