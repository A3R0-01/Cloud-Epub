# Reader layout prototype

Throwaway comparison for **Decide reader layout, chapter navigation, and typography through prototypes**. Run from the repository root:

```powershell
node Docs/prototypes/serve-reader.prototype.cjs
```

Open http://127.0.0.1:8766/?variant=A&review=1 to review the accepted bottom toolbar with smaller centered icons and no visible text labels. Omit `review=1` to compare A (bottom bar), B (top bar), and C (floating cluster) using the bottom switcher. The HTML file also works directly in a browser.

For the remaining margin decision, open http://127.0.0.1:8766/?variant=A&review=1&margins=balanced. Use Compact / Balanced / Original in the review-only bottom picker. Compact uses 12 px side margins and 8 px vertical padding; Balanced uses 18 px side margins and 12 px vertical padding. Both reclaim the space previously reserved for hidden controls. Font size and line spacing remain the same. The picker does not propose an application margin setting.

Scroll chapters vertically. On touch screens, horizontal swipes change chapters; desktop demo buttons simulate those swipes. Try the four reader controls, separate bookmark actions, word selection, image popup, and Android Back simulation. Reach typography through Library → App Settings. State is in memory and resets on reload.

HTML fixture only: no EPUB pagination, engine validation, real dictionary, clipboard copying, cloud sync, or persistent bookmarks. Word selection uses browser selection; it does not validate Android long-press or multilingual segmentation. The linked-image action is simulated and opens no external page. The user accepted layout A, chapter jumps to beginnings without wrapping, the light/dark palette, icon-only controls, image eligibility/captions/alt treatment, and selection/Back behavior. Controls start hidden; the footer shows only the chapter name while controls are visible. User scrolling reveals the indicator; three seconds after scrolling stops, it hides. Image popups close only via Close or Android Back. Production must remember theme on the device across restarts; the prototype intentionally resets in-memory preferences on reload. Only reading margins remain under discussion. No production implementation is included.
