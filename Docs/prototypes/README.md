# Reader layout prototype

Throwaway comparison for **Decide reader layout, chapter navigation, and typography through prototypes**. Run from the repository root:

```powershell
node Docs/prototypes/serve-reader.prototype.cjs
```

Open http://127.0.0.1:8766/?variant=A&review=1&margins=original&final=1 to review the accepted reader: original margins and a centered icon-only bottom toolbar. Controls start hidden; tap the reading area to reveal them. The HTML file also works directly in a browser. Omit `review=1` and `final=1` to revisit A (bottom bar), B (top bar), and C (floating cluster) using the bottom switcher.

The user chose Original after comparing margins. Earlier proposals remain captured at http://127.0.0.1:8766/?variant=A&review=1&margins=balanced. Compact used 12 px side margins and 8 px vertical padding; Balanced used 18 px side margins and 12 px vertical padding. Both reclaimed the space previously reserved for hidden controls; neither was accepted. Font size and line spacing remain the same. The picker is a prototype review tool, not an application margin setting.

Scroll chapters vertically. On touch screens, horizontal swipes change chapters; desktop demo buttons simulate those swipes. Try the four reader controls, separate bookmark actions, word selection, image popup, and Android Back simulation. Reach typography through Library → App Settings. State is in memory and resets on reload.

HTML fixture only: no EPUB pagination, engine validation, real dictionary, clipboard copying, cloud sync, or persistent bookmarks. Word selection uses browser selection; it does not validate Android long-press or multilingual segmentation. The linked-image action is simulated and opens no external page. The user accepted layout A, original margins, chapter jumps to beginnings without wrapping, the light/dark palette, icon-only controls, image eligibility/captions/alt treatment, and selection/Back behavior. Controls start hidden; the footer shows only the chapter name while controls are visible. User scrolling reveals the indicator; three seconds after scrolling stops, it hides. Image popups close only via Close or Android Back. Production must remember theme on the device across restarts; the prototype intentionally resets in-memory preferences on reload. The authoritative decision lives in the reader-layout issue's final resolution. No production implementation is included.
