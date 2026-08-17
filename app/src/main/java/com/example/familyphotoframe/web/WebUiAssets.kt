package com.example.familyphotoframe.web

/**
 * Stable facade used by the embedded server and setup page. The large stylesheet and
 * browser client live in separate source files so they can be reviewed independently.
 */
object WebUiAssets {
    /**
     * Derived from the asset contents rather than hand-maintained.
     *
     * The server sends these two URLs `immutable` with a one-year max-age, so the
     * revision is the only thing that ever invalidates them. A hand-edited constant has
     * to be remembered on every stylesheet or client change, and forgetting it is
     * invisible on a fresh browser and permanent on one that already cached the bundle.
     * Deriving it makes editing the asset sufficient.
     */
    val REVISION: String by lazy {
        val combined = 31 * WebUiCss.VALUE.hashCode() + WebUiScript.VALUE.hashCode()
        "v" + Integer.toHexString(combined)
    }

    val CSS: String get() = WebUiCss.VALUE
    val JS: String get() = WebUiScript.VALUE
}
