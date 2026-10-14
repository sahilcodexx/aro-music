package com.aro.music.spotify

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One Spotify sign-in page; verification survives leaving the screen. */
@AndroidEntryPoint
class SpotifyConnectActivity : ComponentActivity() {
    @Inject lateinit var session: SpotifySession
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var retry: Button
    private var connecting = false
    private var lastCookieAttempt: String? = null
    private var poll: Job? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        status = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            visibility = View.GONE
        }
        retry = Button(this).apply {
            text = "Retry connection"
            visibility = View.GONE
            setOnClickListener { lastCookieAttempt = null; captureSession() }
        }
        content.addView(status)
        content.addView(retry)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        webView = WebView(this).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            cookies.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme != "https"
                override fun onPageFinished(view: WebView, url: String) {
                    val host = Uri.parse(url).host
                    if (host == "open.spotify.com" || host == "accounts.spotify.com") captureSession()
                }
            }
            loadUrl(SpotifyAuth.LOGIN_URL)
        }
        content.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(content)
        content.requestApplyInsets()
        poll = lifecycleScope.launch {
            while (true) { captureSession(); delay(1000) }
        }
    }

    private fun captureSession() {
        if (connecting) return
        val cookies = CookieManager.getInstance()
        val values = listOf("https://open.spotify.com/", "https://accounts.spotify.com/")
            .flatMap { cookies.getCookie(it).orEmpty().split(';') }
            .mapNotNull { entry ->
                val parts = entry.trim().split('=', limit = 2)
                if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
            }.toMap()
        val spDc = values["sp_dc"] ?: return
        if (lastCookieAttempt == spDc) return
        lastCookieAttempt = spDc
        connecting = true
        status.text = "Connecting Spotify…"
        status.visibility = View.VISIBLE
        retry.visibility = View.GONE
        val task = session.connectWebSession(spDc, values["sp_key"].orEmpty())
        lifecycleScope.launch {
            val result = task.await()
            connecting = false
            if (result.isSuccess) {
                cookies.flush()
                Toast.makeText(this@SpotifyConnectActivity, "Spotify connected", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                val code = (result.exceptionOrNull() as? Spotify.SpotifyException)?.statusCode
                status.text = when (code) {
                    401 -> "Spotify session could not be verified. Sign in again or retry."
                    429 -> "Spotify is limiting requests. Wait a little, then retry."
                    503 -> "Spotify session verification is unavailable. Please retry later."
                    else -> "Could not finish connecting Spotify. Check your connection and retry."
                }
                retry.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        poll?.cancel()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
