package com.aro.music.spotify

import java.net.URI
import org.json.JSONObject

/** Only prefills an email the user explicitly chose; never submits or reads credentials. */
internal object SpotifyLoginForm {
    fun isLoginPage(url: String?): Boolean = runCatching {
        val uri = URI(url ?: return false)
        uri.scheme == "https" && uri.host in setOf("accounts.spotify.com", "accounts.google.com") && uri.userInfo == null
    }.getOrDefault(false)

    fun fillEmailScript(email: String): String = """
        (() => {
            if (location.protocol !== 'https:' || !['accounts.spotify.com', 'accounts.google.com'].includes(location.hostname)) return;
            const email = ${JSONObject.quote(email)};
            const fill = () => {
                const input = document.querySelector('#login-username, #identifierId, input[name="username"], input[name="identifier"], input[type="email"]');
                if (!input) return false;
                const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(input, email);
                input.dispatchEvent(new Event('input', {bubbles: true}));
                input.dispatchEvent(new Event('change', {bubbles: true}));
                return true;
            };
            if (window.__aroEmailObserver) window.__aroEmailObserver.disconnect();
            if (fill()) return;
            const observer = new MutationObserver(() => { if (fill()) observer.disconnect(); });
            window.__aroEmailObserver = observer;
            observer.observe(document.documentElement, {childList: true, subtree: true});
            setTimeout(() => observer.disconnect(), 10000);
        })();
    """.trimIndent()
}
