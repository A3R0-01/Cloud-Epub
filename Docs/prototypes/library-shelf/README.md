# Cloud EPUB library shelf prototype

Planning question: how should All, Collections, and Authors present books and groups uniformly?

Context: [Define library tabs, Collections, Authors, and editable book details](https://github.com/A3R0-01/Cloud-Epub/issues/12).

The user reviewed List, Shelf, and Large covers and chose Shelf for every tab and opened group. Collection and Author images are fans of member-book covers; an empty Collection uses a question mark. Final visual review of the revised cover fans is pending.

## Preview

Open `preview.html` in a browser. It is a standalone visual simulation with no installation or service calls. The interactive source is `source.html`, an inline HTML fragment for Codex; `initial-variants.html` preserves the comparison the user reviewed. To regenerate the standalone wrapper, use the visualize skill's `scripts/render.py source.html preview.html`.

All uses cover/title-only book tiles with single-line titles and a bottom-right options icon. Collections and Authors use the same shelf layout with cover fans, names, and counts. The revised draft places counts below names, awaiting shared-understanding review. Cover fan selection takes up to three member covers in title order solely to demonstrate the visual treatment.

The preview simulates tabs, search/sort, group navigation, full-title Details, options, author suggestions, Collection creation, and membership selection. State is local to the preview. Book covers are synthetic stand-ins, not publication assets.

This is throwaway planning code. No real EPUB reading, downloads, encryption, synchronization, date validation, or Author-entry identity persistence is validated. The author editor illustrates suggestion selection and deliberate creation; it is not the production multiple-author identity model. Collection rename/delete actions are indicated, not implemented.

## Inspection

Checked in Chromium at normal and narrow widths: single-line titles, Details, options, author suggestion `elson` -> `Elson Madara`, Collection navigation, cover fans on both grouping tabs, empty Collection placeholder, and no runtime errors in the inspected interactions.

The prototype stays on a throwaway branch and is not merged into the application. Decisions are authoritative in the linked issue's eventual resolution, not in this code.
