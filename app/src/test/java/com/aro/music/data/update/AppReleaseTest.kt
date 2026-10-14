package com.aro.music.data.update

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppReleaseTest {
    @Test fun `versions sort numerically and stable follows candidates`() {
        val versions = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta", "1.0.0-rc.2", "1.0.0-rc.10", "1.0.0", "1.9.0", "1.10.0")
        versions.zipWithNext().forEach { (a,b) -> assertTrue(AppVersion.parse(a)!! < AppVersion.parse(b)!!, "$a before $b") }
        assertEquals(AppVersion.parse("1.0.0"), AppVersion.parse("v1.0.0+build.42"))
    }
    @Test fun `invalid versions cannot trigger updates`() {
        listOf("latest", "1.2", "01.2.3", "1.2.3-rc..1", "1.2.3-rc.01", "2147483648.0.0").forEach { assertNull(AppVersion.parse(it), it) }
    }
    @Test fun `candidate app finds candidate releases but never claims older apk is newer`() {
        val current = AppVersion.parse("1.0.0-rc.3")!!
        val old = AppReleaseParser.latest(releases(release("v1.0.0-rc.1", prerelease = true)),current,listOf("arm64-v8a"))!!
        assertFalse(old.version > current)
        val latest = AppReleaseParser.latest(releases(release("v1.0.0-rc.4", prerelease = true), release("v1.0.0-rc.1", prerelease = true)),current,listOf("arm64-v8a"))!!
        assertTrue(latest.version > current)
    }
    @Test fun `stable app excludes prereleases and drafts`() {
        val latest = AppReleaseParser.latest(releases(release("v3.0.0", draft = true),release("v2.0.0-rc.1",prerelease = true),release("v1.1.0")),AppVersion.parse("1.0.0")!!,listOf("arm64-v8a"))!!
        assertEquals("v1.1.0",latest.tag)
    }
    @Test fun `apk selection prefers phone architecture and excludes source archives`() {
        val data = releases(release("v2.0.0", assets = listOf("Aro-armv7.apk", "Aro-universal.apk", "Aro-arm64.apk", "Source.zip")))
        assertTrue(AppReleaseParser.latest(data,AppVersion.parse("1.0.0")!!,listOf("arm64-v8a","armeabi-v7a"))!!.apkUrl.endsWith("Aro-arm64.apk"))
        assertTrue(AppReleaseParser.latest(data,AppVersion.parse("1.0.0")!!,listOf("x86_64"))!!.apkUrl.endsWith("Aro-universal.apk"))
        assertNull(AppReleaseParser.latest(releases(release("v2.0.0", assets = listOf("Aro-arm64.apk"))),AppVersion.parse("1.0.0")!!,listOf("x86_64")))
    }
    @Test fun `downloads must belong to Aro github release assets`() {
        assertTrue(AppReleaseParser.trustedApkUrl("${AroProject.URL}/releases/download/v2.0.0/Aro.apk"))
        listOf("http://github.com/sahilcodexx/aro-music/releases/download/v2.0.0/Aro.apk", "https://github.com.evil.test/sahilcodexx/aro-music/releases/download/v2.0.0/Aro.apk", "https://github.com/other/app/releases/download/v2.0.0/Aro.apk", "https://github.com/sahilcodexx/aro-music/releases/download/../../other/Aro.apk", "https://github.com/sahilcodexx/aro-music/releases/download/v2.0.0/Source.zip").forEach { assertFalse(AppReleaseParser.trustedApkUrl(it),it) }
    }
    private fun releases(vararg entries: JsonObject) = JsonArray(entries.toList()).toString()
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false, assets: List<String> = listOf("Aro.apk")) = buildJsonObject {
        put("tag_name",tag);put("draft",draft);put("prerelease",prerelease);put("body","Changes")
        putJsonArray("assets") { assets.forEach { name -> add(buildJsonObject { put("name",name);put("browser_download_url","${AroProject.URL}/releases/download/$tag/$name") }) } }
    }
}
