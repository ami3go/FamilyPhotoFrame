package com.example.familyphotoframe.web

/**
 * Stable facade used by the embedded server and setup page. The large stylesheet and
 * browser client live in separate source files so they can be reviewed independently.
 */
object WebUiAssets {
    const val REVISION: String = "v52150"

    val CSS: String get() = WebUiCss.VALUE
    val JS: String get() = WebUiScript.VALUE
}
