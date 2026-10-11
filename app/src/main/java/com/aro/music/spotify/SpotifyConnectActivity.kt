package com.aro.music.spotify

import android.annotation.SuppressLint
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aro.music.R
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Meld's web-session login. The account password stays in Spotify's own page. */
@AndroidEntryPoint
class SpotifyConnectActivity : ComponentActivity() {
    @Inject lateinit var session: SpotifySession
    private var webView: WebView? = null
    private var connecting = false
    private var selectedEmail: String? = null
    private val deviceAccount = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf { it.isNotBlank() }?.let {
                selectedEmail = it
                fillSelectedEmail()
            }
        }
    }

    private fun chooseDeviceAccount() {
        try {
            deviceAccount.launch(AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), getString(R.string.spotify_device_email_description), null, null, null
            ))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.spotify_device_email_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun fillSelectedEmail() {
        val email = selectedEmail ?: return
        val view = webView ?: return
        if (!SpotifyLoginForm.isLoginPage(view.url)) return
        view.evaluateJavascript(SpotifyLoginForm.fillEmailScript(email), null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedEmail = savedInstanceState?.getString("selected_email")
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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https"

                override fun onPageFinished(view: WebView, url: String) {
                    fillSelectedEmail()
                    if (android.net.Uri.parse(url).host != "open.spotify.com" || connecting) return
                    val values = cookies.getCookie("https://open.spotify.com").orEmpty()
                        .split(';').mapNotNull { entry ->
                            val parts = entry.trim().split('=', limit = 2)
                            if (parts.size == 2) parts[0] to parts[1] else null
                        }.toMap()
                    val spDc = values["sp_dc"]?.takeIf { it.isNotBlank() } ?: return
                    connecting = true
                    lifecycleScope.launch {
                        val result = session.connect(spDc, values["sp_key"].orEmpty())
                        if (result.isSuccess) {
                            cookies.flush()
                            Toast.makeText(this@SpotifyConnectActivity, "Spotify connected", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            connecting = false
                            Toast.makeText(this@SpotifyConnectActivity, "Could not connect Spotify. Please try signing in again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            loadUrl(SpotifyAuth.LOGIN_URL)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
            // The login activity follows the device's system-bar/keyboard insets.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(Button(this@SpotifyConnectActivity).apply {
                text = getString(R.string.spotify_use_device_email)
                setOnClickListener { chooseDeviceAccount() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(TextView(this@SpotifyConnectActivity).apply {
                text = getString(R.string.spotify_device_email_hint)
                setTextColor(android.graphics.Color.LTGRAY)
                val padding = (12 * resources.displayMetrics.density).toInt()
                setPadding(padding, 0, padding, padding)
            })
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(content)
        content.requestApplyInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectedEmail?.let { outState.putString("selected_email", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
