package dev.cloudepub.spike

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import java.io.File

/** Isolates Android/WebView/TalkBack from Readium and its adapter. No external resources. */
class AccessibilityControlActivity : Activity() {
    private lateinit var web: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = object : WebView(this) {
            override fun onHoverEvent(event: android.view.MotionEvent): Boolean {
                ControlBridge().record("hover:${event.actionMasked}:${event.source}")
                return super.onHoverEvent(event)
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.actionMasked in listOf(0,1)) ControlBridge().record("touch:${event.actionMasked}:${event.source}:${event.getToolType(0)}")
                return super.onTouchEvent(event)
            }
        }.apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(ControlBridge(), "ControlBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    ControlBridge().record("web-ready")
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        view.postDelayed({
                        val root = view.createAccessibilityNodeInfo()
                        root.setQueryFromAppProcessEnabled(view, true)
                        ControlBridge().record("web-accessibility-children:${root.childCount}")
                        fun inspect(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
                            if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) ControlBridge().record("node:${node.className}:${node.text}:${node.contentDescription}")
                            if (depth < 6) for (index in 0 until node.childCount) node.getChild(index)?.let { inspect(it, depth + 1) }
                        }
                        inspect(root, 0)
                        }, 1000)
                    }
                }
            }
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Button(this).apply {
            text = "Native button control"
            setOnClickListener { ControlBridge().record("native-button-activate"); AlertDialog.Builder(this@AccessibilityControlActivity).setMessage("Native button activated").setPositiveButton("Close", null).show() }
        }, LinearLayout.LayoutParams(-1, (96 * resources.displayMetrics.density).toInt()))
        column.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(column)
        web.loadDataWithBaseURL("https://fixture.invalid/", """
            <!doctype html><html lang="en"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>button{display:block;width:90%;height:60px;margin:20px 5%;font-size:18px}dialog{padding:20px}</style>
            <button id="native" onclick="ControlBridge.openNative()">Native window control</button>
            <button id="dom" onclick="document.querySelector('dialog').showModal();ControlBridge.record('dom-open')">Web dialog control</button>
            <dialog aria-label="Control popup"><button onclick="this.closest('dialog').close()">Close control popup</button></dialog>
            <script>document.querySelector('dialog').addEventListener('close',()=>{document.getElementById('dom').focus();ControlBridge.record('dom-close')});</script>
            </html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
    inner class ControlBridge {
        @JavascriptInterface fun record(value: String) { File(filesDir, "accessibility-control.log").appendText("${System.currentTimeMillis()} $value\n") }
        @JavascriptInterface fun openNative() { runOnUiThread {
            record("native-open")
            AlertDialog.Builder(this@AccessibilityControlActivity).setTitle("Native control popup")
                .setPositiveButton("Close", null).setOnDismissListener {
                    record("native-close")
                    web.evaluateJavascript("document.getElementById('native').focus()", null)
                }.show()
        } }
    }
}
