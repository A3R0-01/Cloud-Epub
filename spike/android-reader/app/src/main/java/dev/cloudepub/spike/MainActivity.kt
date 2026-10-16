@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package dev.cloudepub.spike

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Debug-only single-fixture harness. Never use its command/JS bridge in a shipping reader. */
class MainActivity : AppCompatActivity() {
    private lateinit var publication: Publication
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var column: LinearLayout
    private lateinit var header: FrameLayout
    private lateinit var footer: LinearLayout
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var chapterName: TextView
    private val preferences by lazy { getSharedPreferences("spike", MODE_PRIVATE) }
    private val fixture by lazy { File(filesDir, "fixture.epub") }
    private var controls = false
    private var selectionActive = false
    private var viewer: ImageViewer? = null
    private var pendingRestore: JSONObject? = null
    private var fontSize = 100.0
    private var serif = true
    private var dark = false
    private var currentHref = ""
    private var fixtureHash = ""
    private var readerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // This disposable harness rebuilds the reader from its saved original-content destination.
        super.onCreate(null)
        WebView.setWebContentsDebuggingEnabled(true)
        dark = preferences.getBoolean("dark", false)
        fontSize = preferences.getFloat("fontSize", 100f).toDouble()
        serif = preferences.getBoolean("serif", true)
        assets.open("fixture.epub").use { input -> fixture.outputStream().use { output -> input.copyTo(output) } }
        fixtureHash = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes()).joinToString("") { "%02x".format(it) }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(column)
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!readerReady) { journal("back", "before-reader-ready"); finish(); return }
                lifecycleScope.launch {
                    if (selectionActive) { navigator.clearSelection(); journal("back", "selection") }
                    else { saveProgress(); journal("back", "library-placeholder"); finish() }
                }
            }
        })
        lifecycleScope.launch {
            try {
                val http = DefaultHttpClient()
                val retriever = AssetRetriever(contentResolver, http)
                val asset = retriever.retrieve(fixture).getOrNull() ?: error("Cannot retrieve fixture")
                val adapter = assets.open("reader-spike.js").bufferedReader().use { it.readText() }
                val opener = PublicationOpener(DefaultPublicationParser(this@MainActivity, assetRetriever = retriever, httpClient = http, pdfFactory = null))
                publication = opener.open(asset, allowUserInteraction = false, onCreatePublication = {
                    container = TransformingContainer(container) { url, resource ->
                        if (!url.toString().endsWith(".xhtml")) resource else resource.map { bytes ->
                            val original = bytes.toString(Charsets.UTF_8)
                            Try.success(original.replace("</head>", "<script type=\"text/javascript\">//<![CDATA[\n$adapter\n//]]></script></head>").toByteArray())
                        }
                    }
                }).getOrNull() ?: error("Cannot open fixture")
                val saved = preferences.getString("progress", null)?.let(::JSONObject)?.takeIf { it.optString("fixtureSha256") == fixtureHash }
                pendingRestore = saved
                currentHref = saved?.getJSONObject("locator")?.getString("href") ?: publication.readingOrder.first().href.toString()
                val factory = EpubNavigatorFactory(publication).createFragmentFactory(
                    initialLocator = saved?.optJSONObject("locator")?.let { Locator.fromJSON(it) },
                    initialPreferences = readerPreferences(),
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() { lifecycleScope.launch { pageLoaded() } }
                    },
                    configuration = EpubNavigatorFragment.Configuration {
                        disablePageTurnsWhileScrolling = true
                        selectionActionModeCallback = object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                menu.clear()
                                menu.add(0, 1, 0, "Precise bookmark")
                                menu.add(0, 2, 1, "Copy")
                                return true
                            }
                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                            override fun onDestroyActionMode(mode: ActionMode) {}
                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                lifecycleScope.launch {
                                    if (item.itemId == 1) {
                                        val anchor = js("spike.selection()") as? JSONObject
                                        if (anchor != null && !anchor.has("error")) {
                                            AlertDialog.Builder(this@MainActivity).setTitle("Precise bookmark")
                                                .setMessage(anchor.optString("quote"))
                                                .setPositiveButton("Save") { _, _ -> saveBookmark("precise", anchor) }
                                                .setNegativeButton("Cancel", null).show()
                                        }
                                    } else {
                                        val text = (js("spike.selection()") as? JSONObject)?.optString("quote") ?: ""
                                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .setPrimaryClip(android.content.ClipData.newPlainText("Original text", text))
                                    }
                                }
                                return true
                            }
                        }
                        registerJavascriptInterface("SpikeBridge") { Bridge() }
                    }
                )
                supportFragmentManager.fragmentFactory = factory
                buildControls()
                val container = FragmentContainerView(this@MainActivity).apply { id = View.generateViewId() }
                column.addView(container, 1, LinearLayout.LayoutParams(-1, 0, 1f))
                navigator = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                supportFragmentManager.beginTransaction().add(container.id, navigator, "reader").commitNow()
                navigator.addInputListener(object : InputListener {
                    override fun onDrag(event: DragEvent): Boolean {
                        if (selectionActive || viewer != null) return true
                        val horizontal = kotlin.math.abs(event.offset.x)
                        if (horizontal > dp(48) && horizontal > kotlin.math.abs(event.offset.y) * 1.5f) {
                            if (event.type == DragEvent.Type.End) {
                                navigateChapter(chapterIndex() + if (event.offset.x < 0) 1 else -1)
                            }
                            return true
                        }
                        return false
                    }
                    override fun onTap(event: TapEvent): Boolean {
                        lifecycleScope.launch {
                            if (selectionActive) js("(getSelection().removeAllRanges(), null)") else showControls(!controls)
                        }
                        return true
                    }
                })
                lifecycleScope.launch { navigator.currentLocator.collect { locator ->
                    updateChapterControls()
                    journal("locator", locator.toJSON())
                } }
                journal("environment", JSONObject().put("fixtureSha256", fixtureHash).put("engine", "Readium 3.4.0")
                    .put("api", android.os.Build.VERSION.SDK_INT).put("webview", if (android.os.Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.versionName else "not queried")
                    .put("density", resources.displayMetrics.density).put("fontScale", resources.configuration.fontScale))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { journal("fatal", Log.getStackTraceString(error)) }
        }
    }

    private fun readerPreferences() = EpubPreferences(scroll = true, publisherStyles = false,
        fontSize = fontSize, fontFamily = FontFamily(if (serif) "serif" else "sans-serif"),
        theme = if (dark) Theme.DARK else Theme.LIGHT)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun icon(name: String, resource: Int, action: () -> Unit) = ImageButton(this).apply {
        contentDescription = name; setImageResource(resource); setBackgroundColor(android.graphics.Color.TRANSPARENT)
        minimumWidth = dp(48); minimumHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun buildControls() {
        header = FrameLayout(this)
        val title = TextView(this).apply { text = "Original-content anchor probes"; gravity = Gravity.CENTER; maxLines = 1 }
        header.addView(title, FrameLayout.LayoutParams(-1, dp(48)))
        header.addView(icon("Back to Library", android.R.drawable.ic_menu_revert) { onBackPressedDispatcher.onBackPressed() },
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START))
        val actions = LinearLayout(this)
        actions.addView(icon("Regular bookmark", android.R.drawable.ic_menu_save) { lifecycleScope.launch {
            (js("spike.firstVisible()") as? JSONObject)?.let { saveBookmark("regular", it) }
        } })
        actions.addView(icon("Bookmarks", android.R.drawable.ic_menu_agenda) {
            AlertDialog.Builder(this).setItems(arrayOf("Regular bookmark", "Precise bookmark")) { _, which ->
                lifecycleScope.launch { restoreBookmark(if (which == 0) "regular" else "precise") }
            }.show()
        })
        header.addView(actions, FrameLayout.LayoutParams(-2, dp(48), Gravity.END))
        footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        previous = icon("Previous chapter", android.R.drawable.ic_media_previous) { navigateChapter(chapterIndex() - 1) }
        next = icon("Next chapter", android.R.drawable.ic_media_next) { navigateChapter(chapterIndex() + 1) }
        row.addView(previous); row.addView(icon("Chapters", android.R.drawable.ic_menu_sort_by_size) {
            AlertDialog.Builder(this).setTitle("Chapters").setItems(publication.readingOrder.map { it.title ?: it.href.toString() }.toTypedArray()) { _, which -> navigateChapter(which) }.show()
        }); row.addView(next)
        row.addView(icon("Toggle light/dark theme", android.R.drawable.ic_menu_day) {
            dark = !dark; preferences.edit().putBoolean("dark", dark).apply(); navigator.submitPreferences(readerPreferences())
            journal("theme", dark)
        })
        footer.addView(row)
        chapterName = TextView(this).apply { gravity = Gravity.CENTER; textSize = 12f }
        footer.addView(chapterName)
        column.addView(header); column.addView(footer)
        showControls(false)
    }

    private fun showControls(visible: Boolean) {
        controls = visible
        header.visibility = if (visible) View.VISIBLE else View.GONE
        footer.visibility = if (visible) View.VISIBLE else View.GONE
        journal("controls", visible)
    }

    private fun chapterIndex() = publication.readingOrder.indexOfFirst { currentHref.substringBefore('#').endsWith(it.href.toString().substringBefore('#')) }.coerceAtLeast(0)

    private fun updateChapterControls() {
        val index = chapterIndex()
        previous.isEnabled = index > 0
        next.isEnabled = index < publication.readingOrder.lastIndex
        chapterName.text = publication.tableOfContents.firstOrNull { it.href.toString() == currentHref }?.title
            ?: publication.readingOrder.getOrNull(index)?.title ?: currentHref
    }

    private fun navigateChapter(index: Int) {
        if (viewer != null || selectionActive || index !in publication.readingOrder.indices) return
        pendingRestore = null
        currentHref = publication.readingOrder[index].href.toString()
        navigator.go(publication.readingOrder[index], animated = false)
        updateChapterControls()
        journal("chapter-action", index)
    }

    private suspend fun js(expression: String): Any? {
        val raw = navigator.evaluateJavascript("JSON.stringify(($expression))") ?: return null
        val decoded = JSONTokener(raw).nextValue()
        return if (decoded is String) JSONTokener(decoded).nextValue() else decoded
    }

    private suspend fun destination(anchor: JSONObject): JSONObject {
        val href = (js("location.pathname") as String).removePrefix("/")
        val link = publication.readingOrder.first { it.href.toString() == href }
        return JSONObject().put("schema", 1).put("fixtureSha256", fixtureHash)
            .put("locator", publication.locatorFromLink(link)!!.toJSON()).put("anchor", anchor)
    }

    private fun saveBookmark(type: String, anchor: JSONObject) { lifecycleScope.launch {
        val saved = destination(anchor)
        preferences.edit().putString(type, saved.toString()).commit()
        journal("bookmark-saved-$type", saved)
    } }

    private suspend fun saveProgress() {
        val anchor = js("spike.firstVisible()") as? JSONObject ?: return
        preferences.edit().putString("progress", destination(anchor).toString()).commit()
        journal("progress-saved", anchor)
    }

    private suspend fun restoreBookmark(type: String) {
        val saved = preferences.getString(type, null)?.let(::JSONObject) ?: return
        if (saved.getString("fixtureSha256") != fixtureHash) { journal("unavailable", "Imported copy mismatch"); return }
        pendingRestore = saved.put("highlight", type == "precise")
        navigator.go(Locator.fromJSON(saved.getJSONObject("locator"))!!, animated = false)
        delay(500)
        pageLoaded()
    }

    private suspend fun pageLoaded() {
        delay(200)
        val rendered = js("spike.state()") as? JSONObject ?: return
        readerReady = true
        currentHref = rendered.getString("href").removePrefix("/")
        updateChapterControls()
        val saved = pendingRestore
        if (saved != null && currentHref == saved.getJSONObject("locator").getString("href")) {
            pendingRestore = null
            journal("restored", js("spike.restore(${saved.getJSONObject("anchor")}, ${saved.optBoolean("highlight")})"))
        }
        journal("page-loaded", js("spike.state()"))
    }

    inner class Bridge {
        @JavascriptInterface fun event(encoded: String) { runOnUiThread {
            val event = JSONObject(encoded)
            when (event.getString("type")) {
                "selection" -> selectionActive = !event.isNull("data")
                "image" -> if (viewer == null) {
                    val data = event.getJSONObject("data")
                    val entry = "EPUB/" + data.getString("src")
                    if (entry !in setOf("EPUB/map.png", "EPUB/equation.png")) return@runOnUiThread
                    val bitmap = ZipFile(fixture).use { zip -> zip.getInputStream(zip.getEntry(entry)).use(BitmapFactory::decodeStream) } ?: return@runOnUiThread
                    viewer = ImageViewer(this@MainActivity, bitmap, data) { stage ->
                        journal("image-stage", stage)
                        if (stage == "reading") {
                            viewer = null
                            lifecycleScope.launch {
                                js("(()=>{const b=spike.controls.get(${data.getInt("index")});b.focus();return document.activeElement===b;})()")
                                    .also { journal("dom-focus-return", it) }
                            }
                        }
                    }.also { it.show() }
                }
            }
            journal("bridge", event)
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            try {
                withTimeout(20000) { while (!readerReady) delay(50) }
                val data = intent.getStringExtra("data")?.let { String(Base64.decode(it, Base64.NO_WRAP)) } ?: ""
                val result: Any? = when (intent.getStringExtra("command")) {
                    "eval" -> js(data)
                    "chapter" -> { navigateChapter(data.toInt()); null }
                    "controls" -> { showControls(data == "true"); controls }
                    "save-regular" -> { saveBookmark("regular", js("spike.firstVisible()") as JSONObject); null }
                    "save-precise" -> { saveBookmark("precise", js("spike.selection()") as JSONObject); null }
                    "restore" -> { restoreBookmark(data); null }
                    "font" -> {
                        val saved = js("spike.firstVisible()") as JSONObject
                        fontSize = data.toDouble(); serif = !serif
                        preferences.edit().putFloat("fontSize", fontSize.toFloat()).putBoolean("serif", serif).commit()
                        navigator.submitPreferences(readerPreferences()); delay(900)
                        js("spike.restore($saved)")
                    }
                    "save-progress" -> { saveProgress(); null }
                    "full-screen" -> { viewer?.fullScreen(); null }
                    "exit-full-screen" -> { viewer?.exitFullScreen(); null }
                    "close-image" -> { viewer?.dismiss(); null }
                    "state" -> {
                        val bounds = android.graphics.Rect()
                        navigator.publicationView.getGlobalVisibleRect(bounds)
                        JSONObject().put("href", currentHref).put("controls", controls).put("dark", dark)
                            .put("fontSize", fontSize).put("viewer", viewer?.stage ?: "reading").put("viewerDetails", viewer?.debugState())
                            .put("readerBounds", org.json.JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                            .put("previousEnabled", previous.isEnabled).put("nextEnabled", next.isEnabled).put("reader", js("spike.state()"))
                    }
                    else -> error("Unknown spike command")
                }
                journal("command-result", JSONObject().put("id", intent.getStringExtra("id")).put("result", result ?: JSONObject.NULL))
            } catch (error: Throwable) { journal("command-error", JSONObject().put("id", intent.getStringExtra("id")).put("error", Log.getStackTraceString(error))) }
        }
    }

    private fun journal(type: String, data: Any?) {
        val line = JSONObject().put("time", System.currentTimeMillis()).put("type", type).put("data", data ?: JSONObject.NULL).toString()
        File(filesDir, "journal.jsonl").appendText(line + "\n")
        Log.i("CloudEpubSpike", line)
    }
}
