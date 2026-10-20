package dev.cloudepub.spike

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONArray

/** Native overlay retaining the mounted Readium view and its dimensions underneath. */
class ImageViewer(context: Context, bitmap: Bitmap, private val data: JSONObject, private val changed: (String) -> Unit) : Dialog(context) {
    var stage = "popup"
        private set
    private lateinit var root: FrameLayout
    private lateinit var metadata: LinearLayout
    private lateinit var image: InspectableImage
    private lateinit var full: ImageButton
    private lateinit var exit: ImageButton
    private val source = bitmap
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(false)
        root = FrameLayout(context).apply { setBackgroundColor(Color.WHITE) }
        image = InspectableImage(context, source).apply {
            contentDescription = data.optString("alt").ifBlank { "Image without an original description" }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        metadata = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)); setBackgroundColor(Color.WHITE)
        }
        for (text in listOf(data.optString("caption"), data.optString("alt"))) {
            if (text.isNotBlank()) metadata.addView(TextView(context).apply { this.text = text; setTextIsSelectable(true) })
        }
        if (data.optString("link").isNotBlank()) metadata.addView(TextView(context).apply {
            text = "Open image link: ${data.getString("link")}"
            contentDescription = text; isFocusable = true
            // The host resolves the explicit link separately from image activation.
            setOnClickListener { changed("link-action:${data.getString("link")}") }
        })
        root.addView(metadata, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val actions = LinearLayout(context).apply { gravity = Gravity.END }
        fun action(name: String, icon: Int, run: () -> Unit) = ImageButton(context).apply {
            contentDescription = name; setImageResource(icon); setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { run() }
        }
        full = action("Full screen", android.R.drawable.ic_menu_view) { fullScreen() }
        exit = action("Exit full screen", android.R.drawable.ic_menu_revert) { exitFullScreen() }
        actions.addView(full); actions.addView(exit)
        actions.addView(action("Close image", android.R.drawable.ic_menu_close_clear_cancel) { dismiss() })
        root.addView(actions, FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP))
        setContentView(root)
        setOnShowListener { applyStage(); full.requestFocus(); changed("popup") }
        setOnDismissListener { changed("reading") }
    }

    fun fullScreen() { stage = "full-screen"; applyStage(); changed(stage); exit.requestFocus() }
    fun exitFullScreen() { stage = "popup"; applyStage(); changed(stage); full.requestFocus() }

    fun debugState() = image.inspect().put("stage", stage).put("metadataVisible", metadata.visibility == View.VISIBLE)
        .put("resolvedSrc", data.optString("resolvedSrc"))
        .put("fullScreenActionVisible", full.visibility == View.VISIBLE).put("exitActionVisible", exit.visibility == View.VISIBLE)

    private fun applyStage() {
        val expanded = stage == "full-screen"
        metadata.visibility = if (expanded) View.GONE else View.VISIBLE
        full.visibility = if (expanded) View.GONE else View.VISIBLE
        exit.visibility = if (expanded) View.VISIBLE else View.GONE
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(if (expanded) -1 else (context.resources.displayMetrics.widthPixels * .92).toInt(), if (expanded) -1 else dp(420))
        window?.setDimAmount(.25f)
        // Fill the reader window without hiding shared system bars and resizing its WebView.
        root.setBackgroundColor(if (expanded) Color.BLACK else Color.WHITE)
        image.setPadding(0, if (expanded) 0 else dp(48), 0, if (expanded) 0 else dp(120))
        image.post { image.fit() }
    }

    @Deprecated("This debug harness explicitly disables predictive Back")
    override fun onBackPressed() { if (stage == "full-screen") exitFullScreen() else dismiss() }
}

private class InspectableImage(context: Context, private val bitmap: Bitmap) : ImageView(context) {
    private val transform = Matrix()
    private var zoomed = false
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onDoubleTap(event: MotionEvent): Boolean { toggleZoom(); return true }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!zoomed) return false
            pan(-dx, -dy); return true
        }
    })
    init { scaleType = ScaleType.MATRIX; setImageBitmap(bitmap) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); fit() }
    fun fit() {
        zoomed = false
        transform.setRectToRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            RectF(0f, 0f, (width - paddingLeft - paddingRight).toFloat(), (height - paddingTop - paddingBottom).toFloat()), Matrix.ScaleToFit.CENTER)
        imageMatrix = transform
        if (android.os.Build.VERSION.SDK_INT >= 30) stateDescription = "Whole image fitted"
    }
    private fun toggleZoom() {
        if (zoomed) fit() else {
            transform.postScale(2.5f, 2.5f, width / 2f, height / 2f)
            imageMatrix = transform; zoomed = true
            if (android.os.Build.VERSION.SDK_INT >= 30) stateDescription = "Image zoomed"
        }
    }
    private fun pan(dx: Float, dy: Float) {
        transform.postTranslate(dx, dy)
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()); transform.mapRect(bounds)
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        val correctionX = if (bounds.width() <= availableWidth) (availableWidth - bounds.width()) / 2 - bounds.left else when { bounds.left > 0 -> -bounds.left; bounds.right < availableWidth -> availableWidth - bounds.right; else -> 0f }
        val correctionY = if (bounds.height() <= availableHeight) (availableHeight - bounds.height()) / 2 - bounds.top else when { bounds.top > 0 -> -bounds.top; bounds.bottom < availableHeight -> availableHeight - bounds.bottom; else -> 0f }
        transform.postTranslate(correctionX, correctionY); imageMatrix = transform
    }
    override fun onTouchEvent(event: MotionEvent): Boolean { gestures.onTouchEvent(event); return true }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.spike_zoom_image, if (zoomed) "Fit whole image" else "Zoom image in"))
        if (zoomed) for ((id, name) in listOf(R.id.spike_pan_left to "Pan left", R.id.spike_pan_right to "Pan right", R.id.spike_pan_up to "Pan up", R.id.spike_pan_down to "Pan down")) info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, name))
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        R.id.spike_zoom_image -> { toggleZoom(); true }
        R.id.spike_pan_left -> { pan(100f, 0f); true }
        R.id.spike_pan_right -> { pan(-100f, 0f); true }
        R.id.spike_pan_up -> { pan(0f, 100f); true }
        R.id.spike_pan_down -> { pan(0f, -100f); true }
        else -> super.performAccessibilityAction(action, arguments)
    }
    fun inspect(): JSONObject {
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        transform.mapRect(bounds)
        val info = createAccessibilityNodeInfo()
        val actions = JSONArray(info.actionList.map { JSONObject().put("id", it.id).put("label", it.label) })
        return JSONObject().put("sourceWidth", bitmap.width).put("sourceHeight", bitmap.height).put("centerPixel", bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            .put("zoomed", zoomed).put("imageBounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .put("width", width - paddingLeft - paddingRight).put("height", height - paddingTop - paddingBottom)
            .put("description", contentDescription).put("accessibilityActions", actions)
    }
}
