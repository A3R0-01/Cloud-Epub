@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package dev.cloudepub.spike

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
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
    private lateinit var readingRoot: FrameLayout
    private lateinit var header: FrameLayout
    private lateinit var footer: LinearLayout
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var chapterName: TextView
    private lateinit var bookTitle: TextView
    private val preferences by lazy { getSharedPreferences("spike", MODE_PRIVATE) }
    private val fixture by lazy { File(filesDir, "fixture.epub") }
    private var controls = false
    private var selectionActive = false
    private var viewer: ImageViewer? = null
    private var imageOpening = false
    private var nativeImageControl: Button? = null
    private var preciseDialog: AlertDialog? = null
    private val pageLock = Mutex()
    private var imageControlHref = ""
    private var originalTouchDown: MotionEvent? = null
    private var pendingRestore: JSONObject? = null
    private var fontSize = 100.0
    private var serif = true
    private var dark = false
    private var currentHref = ""
    private var fixtureHash = ""
    private var readerReady = false
    private var selectionMode: ActionMode? = null
    private var readerTouch: Pair<Float, Float>? = null
    private var touchStartedSelected = false
    private var selectionTouchTime = 0L
    private val receivedCommands = mutableSetOf<String>()
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) handleCommand(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // This disposable harness rebuilds the reader from its saved original-content destination.
        super.onCreate(null)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(commandReceiver, IntentFilter("dev.cloudepub.spike.COMMAND"), RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(commandReceiver, IntentFilter("dev.cloudepub.spike.COMMAND"))
        WebView.setWebContentsDebuggingEnabled(true)
        dark = preferences.getBoolean("dark", false)
        fontSize = preferences.getFloat("fontSize", 100f).toDouble()
        serif = preferences.getBoolean("serif", true)
        assets.open("fixture.epub").use { input -> fixture.outputStream().use { output -> input.copyTo(output) } }
        fixtureHash = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes()).joinToString("") { "%02x".format(it) }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        readingRoot = FrameLayout(this).apply { addView(column, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(readingRoot)
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
                                selectionMode = mode
                                lifecycleScope.launch { journal("word-candidate", js("spike.showWordCandidate(spike.wordSelection())")) }
                                menu.clear()
                                menu.add(0, 1, 0, "Precise bookmark")
                                menu.add(0, 2, 1, "Copy")
                                return true
                            }
                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                                lifecycleScope.launch { js("spike.showWordCandidate(spike.wordSelection())") }
                                return false
                            }
                            override fun onDestroyActionMode(mode: ActionMode) {
                                if (selectionMode === mode) selectionMode = null
                                if (preciseDialog == null) lifecycleScope.launch { js("spike.clearWordCandidate()") }
                            }
                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                lifecycleScope.launch {
                                    if (item.itemId == 1) {
                                        val anchor = js("spike.wordSelection()") as? JSONObject
                                        if (anchor != null && !anchor.has("error")) {
                                            confirmPreciseBookmark(anchor)
                                        }
                                    } else {
                                        val text = (js("spike.wordSelection()") as? JSONObject)?.optString("quote") ?: ""
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
        fontSize = fontSize / 100.0, fontFamily = FontFamily(if (serif) "serif" else "sans-serif"),
        theme = if (dark) Theme.DARK else Theme.LIGHT)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (readerReady && viewer == null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val bounds = android.graphics.Rect()
                navigator.publicationView.getGlobalVisibleRect(bounds)
                readerTouch = if (bounds.contains(event.rawX.toInt(), event.rawY.toInt())) event.rawX to event.rawY else null
                originalTouchDown?.recycle()
                originalTouchDown = if (readerTouch != null) MotionEvent.obtain(event) else null
                nativeImageControl?.let { button ->
                    val buttonBounds = android.graphics.Rect()
                    button.getGlobalVisibleRect(buttonBounds)
                    if (!buttonBounds.contains(event.rawX.toInt(), event.rawY.toInt())) removeImageControl()
                }
                touchStartedSelected = selectionActive
                selectionTouchTime = event.eventTime
            }
            val down = readerTouch
            if (event.actionMasked == MotionEvent.ACTION_MOVE && down != null && nativeImageControl != null &&
                (kotlin.math.abs(event.rawX-down.first) > dp(8) || kotlin.math.abs(event.rawY-down.second) > dp(8))) {
                // Return a drag begun on the native image control to the mounted reader.
                removeImageControl()
                originalTouchDown?.let { super.dispatchTouchEvent(it) }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP && down != null) {
                readerTouch = null
                originalTouchDown?.recycle(); originalTouchDown = null
                val dx = event.rawX - down.first
                val dy = event.rawY - down.second
                val chapterSwipe = !touchStartedSelected && !selectionActive && kotlin.math.abs(dx) > dp(48) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f
                if (chapterSwipe) {
                    navigateChapter(chapterIndex() + if (dx < 0) 1 else -1)
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel); cancel.recycle()
                    return true
                }
                if (touchStartedSelected && event.eventTime - selectionTouchTime < 350 && kotlin.math.abs(event.rawX - down.first) < dp(10) && kotlin.math.abs(event.rawY - down.second) < dp(10)) {
                    val bounds = android.graphics.Rect()
                    navigator.publicationView.getGlobalVisibleRect(bounds)
                    val x = (event.rawX - bounds.left) / resources.displayMetrics.density
                    val y = (event.rawY - bounds.top) / resources.displayMetrics.density
                    lifecycleScope.launch {
                        if (js("spike.selectionContains($x,$y)") != true) {
                            navigator.clearSelection()
                            selectionMode?.finish()
                            js("(getSelection().removeAllRanges(),null)")
                            selectionActive = false
                            journal("selection-dismiss", "outside-tap")
                        }
                    }
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) { readerTouch = null; originalTouchDown?.recycle(); originalTouchDown = null }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun confirmPreciseBookmark(initial: JSONObject) {
        journal("word-confirmation", initial)
        var selected = initial
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val word = TextView(this).apply { text = selected.getString("quote"); textSize = 20f }
        content.addView(word)
        for ((label, step) in listOf("Previous word" to -1, "Next word" to 1)) {
            content.addView(Button(this).apply {
                text = label
                setOnClickListener { lifecycleScope.launch {
                    val corrected = js("spike.showWordCandidate(spike.wordSelection($step, $selected))") as? JSONObject
                    if (corrected != null && !corrected.has("error")) {
                        selected = corrected; word.text = corrected.getString("quote")
                        journal("word-correction", selected)
                    }
                } }
            })
        }
        preciseDialog = AlertDialog.Builder(this).setTitle("Precise bookmark").setView(content)
            .setPositiveButton("Save") { _, _ -> saveBookmark("precise", selected) }
            .setNegativeButton("Cancel", null).create().also { dialog ->
                dialog.setOnDismissListener {
                    preciseDialog = null
                    lifecycleScope.launch { js("spike.clearWordCandidate()") }
                }
            }
        navigator.clearSelection(); selectionMode?.finish(); selectionActive = false
        preciseDialog?.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun icon(name: String, resource: Int, action: () -> Unit) = ImageButton(this).apply {
        contentDescription = name; setImageResource(resource); setBackgroundColor(android.graphics.Color.TRANSPARENT)
        minimumWidth = dp(48); minimumHeight = dp(48)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(15), dp(15), dp(15), dp(15))
        setOnClickListener { action() }
    }

    private fun buildControls() {
        header = FrameLayout(this)
        bookTitle = TextView(this).apply {
            text = "Original-content anchor probes"; contentDescription = text
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(bookTitle, FrameLayout.LayoutParams(-1, dp(48)).apply { leftMargin = dp(96); rightMargin = dp(96) })
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
            applyTheme()
            lifecycleScope.launch { delay(300); js("spike.setTheme($dark)") }
            journal("theme", dark)
        })
        footer.addView(row)
        for (index in 0 until row.childCount) row.getChildAt(index).setPadding(dp(15), dp(27), dp(15), dp(3))
        chapterName = TextView(this).apply { gravity = Gravity.CENTER; textSize = 12f }
        footer.addView(chapterName)
        column.addView(header); column.addView(footer)
        applyTheme()
        showControls(false)
    }

    private fun showControls(visible: Boolean) {
        if (visible != controls) removeImageControl()
        controls = visible
        for ((view, offset) in listOf(header to -12f, footer to 12f)) {
            view.animate().cancel()
            if (visible) {
                view.visibility = View.VISIBLE; view.alpha = 0f; view.translationY = offset * resources.displayMetrics.density
                view.animate().alpha(1f).translationY(0f).setDuration(200).start()
            } else if (readerReady && view.visibility == View.VISIBLE) {
                view.animate().alpha(0f).translationY(offset * resources.displayMetrics.density).setDuration(200)
                    .withEndAction { if (!controls) view.visibility = View.GONE }.start()
            } else {
                view.visibility = View.GONE
            }
        }
        journal("controls", visible)
    }

    private fun applyTheme() {
        val ink = if (dark) Color.WHITE else Color.rgb(17, 17, 17)
        column.setBackgroundColor(if (dark) Color.rgb(24, 28, 33) else Color.rgb(250, 247, 240))
        bookTitle.setTextColor(ink); chapterName.setTextColor(ink)
        fun tint(view: View) {
            if (view is ImageButton) view.setColorFilter(ink)
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) tint(view.getChildAt(index))
        }
        tint(header); tint(footer)
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
        removeImageControl()
        pendingRestore = null
        val target = publication.readingOrder[index].href.toString()
        if (currentHref == target) {
            lifecycleScope.launch { js("(scrollTo(0,0),null)") }
            return
        }
        currentHref = target
        readerReady = false
        navigator.go(publication.readingOrder[index], animated = false)
        lifecycleScope.launch { settleReader() }
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
        removeImageControl()
        val saved = preferences.getString(type, null)?.let(::JSONObject) ?: return
        if (saved.getString("fixtureSha256") != fixtureHash) { unavailableLocation("Imported copy mismatch"); return }
        if (currentHref == saved.getJSONObject("locator").getString("href")) {
            val valid = js("(()=>{try{spike.snapshot(${saved.getJSONObject("anchor")});return true}catch(error){return false}})()")
            if (valid != true) { unavailableLocation("Original content could not be verified"); return }
            journal("restored", js("spike.restore(${saved.getJSONObject("anchor")}, ${type == "precise"})"))
            return
        }
        pendingRestore = saved.put("highlight", type == "precise")
        currentHref = saved.getJSONObject("locator").getString("href")
        readerReady = false
        navigator.go(Locator.fromJSON(saved.getJSONObject("locator"))!!, animated = false)
        settleReader()
    }

    // Cached resources need not emit onPageLoaded again. Wait for the displayed
    // document and stable geometry as well as handling the normal page callback.
    private suspend fun settleReader() {
        var previous = ""
        var stable = 0
        withTimeout(15000) {
            while (stable < 3) {
                delay(150)
                val state = js("spike.state()") as? JSONObject ?: continue
                if (state.getString("href").removePrefix("/") != currentHref) { stable = 0; continue }
                val geometry = "${state.getInt("height")}:${state.getInt("viewport")}:${navigator.publicationView.width}"
                stable = if (geometry == previous) stable + 1 else 0
                previous = geometry
            }
        }
        pageLoaded()
    }

    private suspend fun pageLoaded(): Unit = pageLock.withLock {
        val expectedHref = currentHref
        delay(200)
        val rendered = js("spike.state()") as? JSONObject ?: return@withLock
        if (rendered.getString("href").removePrefix("/") != expectedHref || currentHref != expectedHref) return@withLock
        js("spike.setTheme($dark)")
        if (currentHref != expectedHref) return@withLock
        updateChapterControls()
        val saved = pendingRestore
        if (saved != null && currentHref == saved.getJSONObject("locator").getString("href")) {
            pendingRestore = null
            val restored = js("(()=>{try{return {ok:true,result:spike.restore(${saved.getJSONObject("anchor")}, ${saved.optBoolean("highlight")})}}catch(error){return {ok:false,error:error.message}}})()") as? JSONObject
            if (restored?.optBoolean("ok") == true) journal("restored", restored.opt("result"))
            else {
                unavailableLocation(restored?.optString("error") ?: "Original content could not be verified")
            }
        }
        journal("page-loaded", js("spike.state()"))
        if (currentHref == expectedHref) readerReady = true
    }

    inner class Bridge {
        @JavascriptInterface fun event(encoded: String) { runOnUiThread {
            val event = JSONObject(encoded)
            when (event.getString("type")) {
                "ready" -> if (event.getJSONObject("data").getString("href").removePrefix("/") == currentHref) {
                    lifecycleScope.launch { pageLoaded() }
                }
                "selection" -> selectionActive = !event.isNull("data")
                "reading-progress" -> {
                    val data = event.getJSONObject("data")
                    val href = data.getString("href").removePrefix("/")
                    val link = publication.readingOrder.firstOrNull { it.href.toString() == href }
                    if (readerReady && viewer == null && href == currentHref && link != null && !data.isNull("anchor")) {
                        val saved = JSONObject().put("schema", 1).put("fixtureSha256", fixtureHash)
                            .put("locator", publication.locatorFromLink(link)!!.toJSON()).put("anchor", data.getJSONObject("anchor"))
                        preferences.edit().putString("progress", saved.toString()).commit()
                    }
                }
                "image-control-position" -> if (event.getJSONObject("data").getString("href").removePrefix("/") == imageControlHref) positionImageControl(event.getJSONObject("data").getJSONObject("rect"))
                "image" -> openImage(event.getJSONObject("data"))
            }
            journal("bridge", event)
        } }
    }

    private fun openImage(data: JSONObject) {
        if (viewer != null || imageOpening) return
        imageOpening = true
        lifecycleScope.launch {
            try {
                val bitmap = if (data.has("png")) {
                    val bytes = Base64.decode(data.getString("png"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    val entry = "EPUB/" + data.getString("src")
                    if (entry !in setOf("EPUB/map.png", "EPUB/equation.png")) return@launch
                    ZipFile(fixture).use { zip -> zip.getInputStream(zip.getEntry(entry)).use(BitmapFactory::decodeStream) }
                } ?: return@launch
                data.remove("png")
                val imageAnchor = data.optJSONObject("readingAnchor")
                val imageDimensions = navigator.publicationView.width to navigator.publicationView.height
                val imageHref = data.getString("href").removePrefix("/")
                installImageControl(data)
                viewer = ImageViewer(this@MainActivity, bitmap, data) { stage ->
                    journal("image-stage", stage)
                    if (stage.startsWith("link-action:")) {
                        followImageLink(data.getString("link"))
                    }
                    if (stage == "reading") {
                        viewer = null
                        lifecycleScope.launch {
                            if (imageHref == currentHref) {
                                if (imageAnchor != null && (navigator.publicationView.width != imageDimensions.first || navigator.publicationView.height != imageDimensions.second)) {
                                    journal("image-reflow-restore", js("spike.restore($imageAnchor)"))
                                }
                                journal("dom-focus-return", js("(()=>{const b=spike.controls.get(${data.getInt("index")});b.focus({preventScroll:true});return document.activeElement===b;})()"))
                                val rect = js("spike.controls.get(${data.getInt("index")}).getBoundingClientRect().toJSON()") as? JSONObject
                                if (rect != null) positionImageControl(rect)
                                focusImageControl()
                            }
                        }
                    }
                }.also { it.show() }
            } finally { imageOpening = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCommand(intent)
    }

    private fun handleCommand(intent: Intent) {
        if (!intent.hasExtra("command")) return
        val commandId = intent.getStringExtra("id")
        if (commandId != null && !receivedCommands.add(commandId)) return
        journal("command-received", JSONObject().put("id",commandId))
        lifecycleScope.launch {
            try {
                withTimeout(90000) { while (!readerReady) delay(50) }
                val data = intent.getStringExtra("data")?.let { String(Base64.decode(it, Base64.NO_WRAP)) } ?: ""
                val result: Any? = when (intent.getStringExtra("command")) {
                    "eval" -> js(data)
                    "clear-selection" -> {
                        navigator.clearSelection(); selectionMode?.finish(); selectionActive = false
                        js("(spike.clearWordCandidate(),getSelection().removeAllRanges(),null)")
                    }
                    "chapter" -> { navigateChapter(data.toInt()); null }
                    "controls" -> { showControls(data == "true"); controls }
                    "title" -> { bookTitle.text = data; bookTitle.contentDescription = data; null }
                    "a11y-tree" -> inspectAccessibility()
                    "save-regular" -> { saveBookmark("regular", js("spike.firstVisible()") as JSONObject); null }
                    "save-precise" -> { saveBookmark("precise", js("spike.selection()") as JSONObject); null }
                    "corrupt-precise" -> {
                        val saved = JSONObject(preferences.getString("precise", null) ?: error("Save a fixture bookmark first"))
                        saved.getJSONObject("anchor").put("quote", "INTENTIONALLY UNAVAILABLE FIXTURE WORD")
                        preferences.edit().putString("precise", saved.toString()).commit(); null
                    }
                    "restore" -> { restoreBookmark(data); null }
                    "font" -> {
                        removeImageControl()
                        val saved = js("spike.firstVisible()") as JSONObject
                        fontSize = data.toDouble(); serif = !serif
                        preferences.edit().putFloat("fontSize", fontSize.toFloat()).putBoolean("serif", serif).commit()
                        navigator.submitPreferences(readerPreferences()); delay(900)
                        js("spike.setTheme($dark)")
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
                            .put("titleWidth", bookTitle.width).put("titleLeft", bookTitle.left).put("titleText", bookTitle.text)
                            .put("titleEllipsized", (bookTitle.layout?.getEllipsisCount(0) ?: 0) > 0)
                            .put("nativeImageControl", nativeImageControl != null).put("nativeImageControlFocused", nativeImageControl?.isAccessibilityFocused ?: false)
                            .put("nativeImageIndex", nativeImageControl?.tag ?: JSONObject.NULL)
                            .put("readerBounds", org.json.JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                            .put("previousEnabled", previous.isEnabled).put("nextEnabled", next.isEnabled).put("reader", js("spike.state()"))
                    }
                    else -> error("Unknown spike command")
                }
                journal("command-result", JSONObject().put("id", intent.getStringExtra("id")).put("result", result ?: JSONObject.NULL))
            } catch (error: Throwable) { journal("command-error", JSONObject().put("id", intent.getStringExtra("id")).put("error", Log.getStackTraceString(error))) }
        }
    }

    override fun onPause() {
        removeImageControl()
        if (readerReady) lifecycleScope.launch { saveProgress() }
        super.onPause()
    }

    override fun onDestroy() {
        originalTouchDown?.recycle()
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }

    private fun followImageLink(href: String) {
        val resolved = java.net.URI(currentHref).resolve(href)
        val target = publication.readingOrder.firstOrNull { it.href.toString() == resolved.path }
        if (target == null) { journal("unavailable", "Fixture link is not an internal reading-order destination"); return }
        currentHref = target.href.toString()
        removeImageControl()
        viewer?.dismiss()
        pendingRestore = null
        val locator = publication.locatorFromLink(target)!!.toJSON()
        locator.getJSONObject("locations").put("fragments", org.json.JSONArray(listOfNotNull(resolved.fragment)))
        updateChapterControls()
        navigator.go(Locator.fromJSON(locator)!!, animated = false)
        journal("image-link-followed", resolved.toString())
    }

    private fun unavailableLocation(reason: String) {
        journal("unavailable", reason)
        AlertDialog.Builder(this).setTitle("Saved location unavailable")
            .setMessage("The saved original text could not be verified. Choose a new destination to move this bookmark.")
            .setPositiveButton("OK", null).show()
    }

    // The native control represents the same original image as the DOM button.
    // Android can restore accessibility focus to a real View after a native Dialog;
    // Chromium ignores DOM focus when accessibility focus has left its WebView.
    private fun installImageControl(data: JSONObject) {
        removeImageControl()
        val rect = data.getJSONObject("buttonRect")
        if (rect.getDouble("bottom") <= 0 || rect.getDouble("top") >= data.getJSONObject("viewport").getDouble("height")) return
        val index = data.getInt("index")
        imageControlHref = data.getString("href").removePrefix("/")
        nativeImageControl = Button(this).apply {
            tag = index; text = "View image"; isAllCaps = false; contentDescription = "View image: ${data.optString("alt").ifBlank { "Image" }}"
            minimumWidth = 0; minimumHeight = 0; setPadding(0, 0, 0, 0)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (data.getDouble("fontPixels") * resources.displayMetrics.density).toFloat())
            typeface = android.graphics.Typeface.create(if (serif) "serif" else "sans-serif", android.graphics.Typeface.NORMAL)
            val ink = if (dark) Color.WHITE else Color.rgb(17, 17, 17)
            setTextColor(ink)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(if (dark) Color.rgb(24,28,33) else Color.rgb(250,247,240)); setStroke(dp(1),ink) }
            setOnClickListener { lifecycleScope.launch { js("(spike.controls.get($index).click(),null)") } }
        }.also { readingRoot.addView(it) }
        positionImageControl(rect)
        lifecycleScope.launch { js("(()=>{spike.nativeControlIndex=$index;const b=spike.controls.get($index);b.setAttribute('aria-hidden','true');b.tabIndex=-1;return null;})()") }
    }

    private fun positionImageControl(rect: JSONObject) {
        val reader = android.graphics.Rect(); navigator.publicationView.getGlobalVisibleRect(reader)
        if (rect.getDouble("bottom") <= 0 || rect.getDouble("top") * resources.displayMetrics.density >= reader.height()) { removeImageControl(); return }
        val origin = IntArray(2); readingRoot.getLocationOnScreen(origin)
        val density = resources.displayMetrics.density
        nativeImageControl?.layoutParams = FrameLayout.LayoutParams((rect.getDouble("width")*density).toInt(), (rect.getDouble("height")*density).toInt()).apply {
            leftMargin = reader.left-origin[0]+(rect.getDouble("left")*density).toInt()
            topMargin = reader.top-origin[1]+(rect.getDouble("top")*density).toInt()
        }
    }

    private fun removeImageControl() {
        val button = nativeImageControl ?: return
        nativeImageControl = null; readingRoot.removeView(button)
        val href = imageControlHref
        if (readerReady) lifecycleScope.launch {
            js("(()=>{if(location.pathname==='/'+${JSONObject.quote(href)}){if(spike.nativeControlIndex===${button.tag})spike.nativeControlIndex=null;const b=spike.controls.get(${button.tag});b?.removeAttribute('aria-hidden');if(b)b.tabIndex=0;}return null;})()")
        }
    }

    private fun focusImageControl() {
        val button = nativeImageControl ?: return
        button.postDelayed({
            if (nativeImageControl === button && viewer == null && hasWindowFocus()) {
                button.requestFocus()
                journal("native-image-focus-return", button.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
            }
        }, 350)
    }

    @android.annotation.TargetApi(34)
    private fun inspectAccessibility(): org.json.JSONArray {
        check(android.os.Build.VERSION.SDK_INT >= 34) { "App-process accessibility inspection requires API 34" }
        preciseDialog?.window?.decorView?.let { decor ->
            // App-process node queries can report window-local child bounds when
            // no service is bound. Native Views expose the actual screen geometry.
            val result = org.json.JSONArray()
            fun visitView(view: View) {
                val info = view.createAccessibilityNodeInfo()
                val location = IntArray(2); view.getLocationOnScreen(location)
                result.put(JSONObject().put("role", info.className).put("text", info.text)
                    .put("name", info.contentDescription).put("accessibilityFocused", view.isAccessibilityFocused)
                    .put("bounds", org.json.JSONArray(listOf(location[0], location[1], location[0]+view.width, location[1]+view.height))))
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) visitView(view.getChildAt(index))
            }
            visitView(decor)
            return result
        }
        val rootView = preciseDialog?.window?.decorView ?: viewer?.window?.decorView ?: readingRoot
        val root = rootView.createAccessibilityNodeInfo()
        root.setQueryFromAppProcessEnabled(rootView, true)
        val result = org.json.JSONArray()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
            if (result.length() >= 200) return
            val bounds = android.graphics.Rect(); node.getBoundsInScreen(bounds)
            result.put(JSONObject().put("role", node.className).put("text", node.text?.take(180)).put("name", node.contentDescription)
                .put("accessibilityFocused", node.isAccessibilityFocused).put("clickable", node.isClickable)
                .put("bounds", org.json.JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
                .put("actions", org.json.JSONArray(node.actionList.map { JSONObject().put("id",it.id).put("name",it.label) })))
            if (depth < 20) for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it,depth+1) }
        }
        visit(root,0)
        return result
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && viewer == null) focusImageControl()
    }

    private fun journal(type: String, data: Any?) {
        val line = JSONObject().put("time", System.currentTimeMillis()).put("type", type).put("data", data ?: JSONObject.NULL).toString()
        File(filesDir, "journal.jsonl").appendText(line + "\n")
        Log.i("CloudEpubSpike", line)
    }
}
