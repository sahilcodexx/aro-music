package com.aro.music.data.update

import java.net.URI
import kotlinx.serialization.json.*

/** Semantic ordering prevents a published older APK from downgrading a local candidate. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val prerelease: List<String>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) return when {
            prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
            prerelease.isEmpty() -> 1
            else -> -1
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[index]; val b = other.prerelease[index]
            val an = a.all(Char::isDigit); val bn = b.all(Char::isDigit)
            val result = when {
                an && bn -> compareValues(a.length, b.length).takeIf { it != 0 } ?: a.compareTo(b)
                an -> if (bn) 0 else -1
                bn -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }
    companion object {
        private val pattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
        fun parse(text: String): AppVersion? {
            val match = pattern.matchEntire(text.trim()) ?: return null
            val parts = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.') ?: emptyList()
            if (parts.any { it.isEmpty() || it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return AppVersion(match.groupValues[1].toIntOrNull() ?: return null, match.groupValues[2].toIntOrNull() ?: return null, match.groupValues[3].toIntOrNull() ?: return null, parts)
        }
    }
}

data class AppRelease(val tag: String, val version: AppVersion, val apkUrl: String, val notes: String)

object AppReleaseParser {
    fun latest(json: String, current: AppVersion, supportedAbis: List<String>): AppRelease? {
        val releases = Json.parseToJsonElement(json) as? JsonArray ?: error("Invalid release response")
        return releases.mapNotNull releaseLoop@ { element ->
            val release = element as? JsonObject ?: return@releaseLoop null
            if (release["draft"]?.jsonPrimitive?.booleanOrNull != false) return@releaseLoop null
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@releaseLoop null
            val version = AppVersion.parse(tag) ?: return@releaseLoop null
            if (current.prerelease.isEmpty() && (version.prerelease.isNotEmpty() || release["prerelease"]?.jsonPrimitive?.booleanOrNull == true)) return@releaseLoop null
            val assets = release["assets"] as? JsonArray ?: return@releaseLoop null
            val apk = assets.mapNotNull assetLoop@ { assetElement ->
                val asset = assetElement as? JsonObject ?: return@assetLoop null
                val name = asset["name"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return@assetLoop null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@assetLoop null
                if (!name.endsWith(".apk") || !trustedApkUrl(url)) return@assetLoop null
                val rank = when {
                    "arm64" in name || "aarch64" in name -> if ("arm64-v8a" in supportedAbis) 100 - supportedAbis.indexOf("arm64-v8a") else return@assetLoop null
                    "armeabi" in name || "armv7" in name -> if ("armeabi-v7a" in supportedAbis) 100 - supportedAbis.indexOf("armeabi-v7a") else return@assetLoop null
                    "x86_64" in name -> if ("x86_64" in supportedAbis) 100 - supportedAbis.indexOf("x86_64") else return@assetLoop null
                    "x86" in name -> if ("x86" in supportedAbis) 100 - supportedAbis.indexOf("x86") else return@assetLoop null
                    "universal" in name -> 2
                    else -> 1
                }
                rank to url
            }.maxByOrNull { it.first }?.second ?: return@releaseLoop null
            AppRelease(tag, version, apk, release["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4000))
        }.maxByOrNull { it.version }
    }
    fun trustedApkUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.path.split('/').none { it == "." || it == ".." } && uri.path.startsWith("/${AroProject.OWNER}/${AroProject.REPOSITORY}/releases/download/") && uri.path.endsWith(".apk", ignoreCase = true)
    }.getOrDefault(false)
}
