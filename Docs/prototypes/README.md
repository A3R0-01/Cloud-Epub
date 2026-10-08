# Reader layout prototype

Throwaway comparison for **Decide reader layout, chapter navigation, and typography through prototypes**. Run from the repository root:

```powershell
node Docs/prototypes/serve-reader.prototype.cjs
```

Open http://127.0.0.1:8766/?variant=A and use the bottom switcher to compare A (bottom bar), B (top bar), and C (floating cluster). The HTML file also works directly in a browser.

Scroll chapters vertically. On touch screens, horizontal swipes change chapters; desktop demo buttons simulate those swipes. Try the four reader controls, separate bookmark actions, word selection, image popup, and Android Back simulation. Reach typography through Library → App Settings. State is in memory and resets on reload.

HTML fixture only: no EPUB pagination, engine validation, real dictionary, clipboard copying, cloud sync, or persistent bookmarks. Word selection uses browser selection; it does not validate Android long-press or multilingual segmentation. The simulated Android Back, chapter landing behavior, scrollbar, colors, icons, image caption/alt presentation, and layout variants await user review. No production implementation is included.
