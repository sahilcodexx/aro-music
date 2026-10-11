package com.aro.music.spotify

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyLoginFormDeviceTest {
    @Test fun chosenEmailFillsSpotifyWithoutSubmittingOrTouchingPassword() = checkEmailForm("accounts.spotify.com", "login-username")

    @Test fun chosenEmailFillsGoogleWithoutSubmittingOrTouchingPassword() = checkEmailForm("accounts.google.com", "identifierId")

    @SuppressLint("SetJavaScriptEnabled")
    private fun checkEmailForm(host: String, inputId: String) {
        assertTrue(SpotifyLoginForm.isLoginPage("https://$host/login"))
        assertFalse(SpotifyLoginForm.isLoginPage("https://accounts.google.com.evil.test/login"))
        assertFalse(SpotifyLoginForm.isLoginPage("https://accounts.spotify.com.evil.test/login"))
        assertFalse(SpotifyLoginForm.isLoginPage("http://accounts.spotify.com/login"))
        assertFalse(SpotifyLoginForm.isLoginPage("https://accounts.spotify.com@evil.test/login"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        val evaluated = CountDownLatch(1)
        var view: WebView? = null
        var result: String? = null
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(webView: WebView, url: String) { loaded.countDown() }
                }
                loadDataWithBaseURL("https://$host/login", "<form onsubmit='window.submitted=true;return false'><input id='$inputId'><input type='password' value='unchanged'><button>Continue</button></form>", "text/html", "utf-8", null)
            }
        }
        try {
            assertTrue(loaded.await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                // Quotes verify the selected account cannot become executable script.
                val email = "chosen'\"@example.test"
                view!!.evaluateJavascript(SpotifyLoginForm.fillEmailScript(email)) {
                    view!!.evaluateJavascript("document.querySelector('#$inputId').value === ${org.json.JSONObject.quote(email)} && document.querySelector('input[type=password]').value === 'unchanged' && !window.submitted") { answer ->
                        result = answer
                        evaluated.countDown()
                    }
                }
            }
            assertTrue(evaluated.await(10, TimeUnit.SECONDS))
            assertEquals("true", result)
        } finally {
            instrumentation.runOnMainSync { view?.destroy() }
        }
    }
}
