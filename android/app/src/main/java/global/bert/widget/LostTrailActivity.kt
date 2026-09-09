package global.bert.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** A single packaged document. No remote pages, file access or JavaScript object bridge. */
internal object LostTrailContent {
    const val URL = "https://appassets.androidplatform.net/lost-trail/index.html?app"
    const val ASSET = "games/lost-trail/index.html"
    fun allows(uri: Uri): Boolean = uri.toString() == URL
    fun response(context: Context, uri: Uri): WebResourceResponse {
        if (!allows(uri)) return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf(
            "Content-Security-Policy" to "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-src 'none'",
            "Cache-Control" to "no-store",
        ), context.assets.open(ASSET))
    }
}

class LostTrailActivity : ComponentActivity() {
    internal lateinit var gameView: WebView
    private var backgrounded = false

    @SuppressLint("SetJavaScriptEnabled") // Trusted, packaged game requires JS; no external content is admitted.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        gameView = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 18, 26))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                blockNetworkLoads = true
                mediaPlaybackRequiresUserGesture = true
                setGeolocationEnabled(false)
                setSupportMultipleWindows(false)
                builtInZoomControls = false
                textZoom = 100 // Game HUD has its own responsive type scale.
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = LostTrailContent.response(this@LostTrailActivity, request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun onPageFinished(view: WebView, url: String) {
                    if (backgrounded) pauseGame()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                gameView.evaluateJavascript("window.BertAppGame ? window.BertAppGame.back() : false") { handled ->
                    if (handled != "true" && !isFinishing) finish()
                }
            }
        })
        setContent {
            BERTTheme {
                Scaffold(containerColor = Navy, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { pauseGame(); finish() }) { Text("‹ Back to Bert", color = Cream) }
                            TextButton(onClick = { gameView.evaluateJavascript("window.BertAppGame?.pause()", null) }) { Text("Pause", color = AccentText) }
                        }
                        AndroidView(factory = { gameView }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        // Re-creation opens the stored lantern, never an unsafe transient platform position.
        gameView.loadUrl(LostTrailContent.URL)
    }

    internal fun pauseGame() { gameView.evaluateJavascript("window.BertAppGame?.pause()", null) }
    override fun onPause() {
        backgrounded = true
        pauseGame()
        gameView.onPause()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        gameView.onResume()
        if (backgrounded) pauseGame() // WebView.onPause alone does not stop JavaScript.
        backgrounded = false
    }
    override fun onDestroy() {
        (gameView.parent as? ViewGroup)?.removeView(gameView)
        gameView.destroy()
        super.onDestroy()
    }
}

internal fun openLostTrail(context: Context) { context.startActivity(Intent(context, LostTrailActivity::class.java)) }
