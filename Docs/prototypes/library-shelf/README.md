# Cloud EPUB library shelf prototype

Planning question: how should All, Collections, and Authors present books and groups uniformly?

Context: [Define library tabs, Collections, Authors, and editable book details](https://github.com/A3R0-01/Cloud-Epub/issues/12).

The user reviewed List, Shelf, and Large covers and chose Shelf for every tab and opened group. Collection and Author images are fans of member-book covers; an empty Collection uses a question mark. Final visual review of the revised cover fans is pending.

## Preview

Open `preview.html` in a browser. It is a standalone visual simulation with no installation or service calls. The interactive source is `source.html`, an inline HTML fragment for Codex; `initial-variants.html` preserves the comparison the user reviewed. To regenerate the standalone wrapper, use the visualize skill's `scripts/render.py source.html preview.html`.

All uses cover/title-only book tiles with single-line titles and a bottom-right options icon. Collections and Authors use the same shelf layout with cover fans, names, and counts. The revised draft places counts below names, awaiting shared-understanding review. Cover fan selection takes up to three member covers in title order solely to demonstrate the visual treatment.

The user's latest revision moves the three icon tabs back to the top, directly below the Cloud EPUB header. They span the available width in three equal portions: an open book for All, grouped books for Collections, and a person for Authors. This supersedes the previously explored compact centered bottom bar. Shelf content scrolls below the tabs. Each icon retains its accessible tab name, a tooltip, and an active state. A gear opposite Cloud EPUB opens a provisional Settings destination. Detailed Settings/account/profile choices are tracked in [Define Settings navigation and account controls](https://github.com/A3R0-01/Cloud-Epub/issues/15); the example account menu is not an accepted editable-profile specification.

The newest visual refinement reduces the visible tab height to 40px and its following gap to 6px, with an expanded invisible hit area for touch. Collections uses a bottom-right floating plus icon to open Collection creation instead of a wide text button. All uses the same floating icon to open Add a book with the already agreed device and Google Drive import sources. Those import endpoints are visual stubs. Authors and opened groups do not show the floating create button. The shelf scroll viewport ends above the floating-button space so dragging the shelf does not cross its target.

Opened Collection/Author shelves omit the in-app Back button. Android Back returns to the corresponding index. A control below the phone simulates that system action in this browser preview and closes an open sheet before leaving a group. The shelf hides the native draggable scrollbar and uses a noninteractive 2px-wide, 24px-long position indicator, hidden when all content fits. The indicator ends with the shelf viewport, above the floating-button area.

The preview simulates tabs, search/sort, group navigation, full-title Details, options, author suggestions, Collection creation, and membership selection. State is local to the preview. Book covers are synthetic stand-ins, not publication assets.

This is throwaway planning code. No real EPUB reading, downloads, encryption, synchronization, date validation, or Author-entry identity persistence is validated. The author editor illustrates suggestion selection and deliberate creation; it is not the production multiple-author identity model. Collection rename/delete actions are indicated, not implemented.

## Inspection

Checked in Chromium at normal and narrow widths: single-line titles, Details, options, author suggestion `elson` -> `Elson Madara`, Collection navigation, cover fans on both grouping tabs, empty Collection placeholder, and no runtime errors in the inspected interactions.

Also checked full-width top navigation, equal icon targets, all three rendered navigation icons, the top-right Settings icon, and opening Settings.

Checked the smaller top bar, floating Add book source menu, floating Collection creation, and the absence of the create button on Authors and inside opened groups.

Checked group return through the simulated Android Back control, absence of the group Back button, the indicator's noninteractive dimensions and motion, and separation between the scrolling shelf and Add button targets.

The prototype stays on a throwaway branch and is not merged into the application. Decisions are authoritative in the linked issue's eventual resolution, not in this code.
