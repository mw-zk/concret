package edu.uoqasim.rcbeam

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.parseColor("#10263D")),
            navigationBarStyle = SystemBarStyle.light(
                Color.parseColor("#F3F6FA"),
                Color.parseColor("#10263D")
            )
        )
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#10263D"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F3F6FA"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        root.addView(webView)
        setContentView(root)

        // Android 15 Edge-to-Edge safe insets handling
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidNative")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        // Load locally via HTTPS virtual origin for full PWA / localStorage speed
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){var m=document.getElementById('printModal');if(m&&!m.classList.contains('hidden')){m.classList.add('hidden');return 'closed';}return 'exit';})()"
                ) { res ->
                    if (res != ""closed"") {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun printPage(jobTitle: String) {
            runOnUiThread {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter(jobTitle.ifBlank { "RC_Beam_Section_ACI318" })
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobTitle.ifBlank { "RC Beam Section" }, adapter, attrs)
            }
        }

        @JavascriptInterface
        fun saveTextFile(filename: String, content: String, mimeType: String) {
            runOnUiThread {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RCBeamDesigner")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(this@MainActivity, "تم الحفظ في Downloads/RCBeamDesigner/$filename", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "تعذّر حفظ الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
