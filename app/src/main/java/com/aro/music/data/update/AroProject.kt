package com.aro.music.data.update

object AroProject {
    const val OWNER = "sahilcodexx"
    const val REPOSITORY = "aro-music"
    const val URL = "https://github.com/$OWNER/$REPOSITORY"
    const val ISSUES = "$URL/issues"
    const val REPORT = "$ISSUES/new/choose"
    const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPOSITORY/releases?per_page=20"
}
