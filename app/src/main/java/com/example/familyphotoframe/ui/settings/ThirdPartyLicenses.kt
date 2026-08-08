package com.example.familyphotoframe.ui.settings

/**
 * Backs the in-app "open-source notices" screen required by two documented release
 * gates:
 *  - `LGPL_COMPLIANCE.md` (jcifs-ng: notices screen + upstream source link), and
 *  - `WEATHER_LICENSING.md` (Open-Meteo: CC BY 4.0 attribution "in the notices screen").
 *
 * **Design choice — link to canonical full text, don't embed a copy.** The Apache
 * Software Foundation's own guidance is that providing the canonical URL is sufficient
 * in place of the license text itself, and reconstructing a long legal text (LGPL-2.1
 * runs to several thousand words) from memory or from fragments risks a subtly wrong
 * copy — worse for a compliance screen than a correct link. Each entry therefore carries
 * [canonicalTextUrl], opened via the system browser (`LocalUriHandler`, no new
 * permission — INTERNET already exists for jcifs-ng/NanoHTTPD/weather). [summary] is an
 * original, factual one-line description of what the license permits, not a reproduction
 * of any license's own wording.
 *
 * This is the curated source of truth mirrored from `THIRD_PARTY_LICENSES.md`; keep the
 * two in sync when a dependency changes (spec §3.1 / §19).
 */
data class LicenseEntry(
    val component: String,
    val licenseName: String,
    val summary: String,
    val canonicalTextUrl: String,
    /** Set only for entries with an upstream-source-link obligation (LGPL) or where
     *  linking to the project is otherwise the more useful destination than the license
     *  text itself. */
    val projectUrl: String? = null,
)

object ThirdPartyLicenses {

    private const val APACHE_2_0_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    private const val APACHE_2_0_SUMMARY =
        "Permissive: free to use, modify, and distribute, including commercially; " +
            "requires keeping the copyright and license notice."
    private const val LGPL_2_1_URL = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
    private const val MIT_URL = "https://opensource.org/license/mit"
    private const val MIT_SUMMARY =
        "Permissive: free to use, modify, and distribute, including commercially; " +
            "requires keeping the copyright and license notice."
    private const val BSD_3_URL = "https://opensource.org/license/bsd-3-clause"
    private const val BSD_3_SUMMARY =
        "Permissive, similar to MIT; additionally forbids using the project's or " +
            "contributors' names to promote derived products without permission."

    private fun apache(component: String, projectUrl: String? = null) =
        LicenseEntry(component, "Apache-2.0", APACHE_2_0_SUMMARY, APACHE_2_0_URL, projectUrl)

    /**
     * Runtime dependencies only (test-only libraries listed in `THIRD_PARTY_LICENSES.md`
     * are omitted — they never ship in the built app, so they carry no distribution
     * obligation and would only clutter a screen meant to be read by an end user).
     */
    val entries: List<LicenseEntry> = listOf(
        apache("AndroidX Core KTX"),
        apache("AndroidX Lifecycle"),
        apache("AndroidX Activity Compose"),
        apache("Jetpack Compose"),
        apache("Coil (Compose)", "https://github.com/coil-kt/coil"),
        apache("Room"),
        apache("DataStore"),
        apache("kotlinx.serialization"),
        apache("kotlinx.coroutines"),
        apache("AndroidX DocumentFile"),
        apache("AndroidX ExifInterface"),
        LicenseEntry(
            component = "jcifs-ng (SMB2/3 client)",
            licenseName = "LGPL-2.1",
            summary = "Copyleft for the library itself; may be used by proprietary " +
                "apps as an unmodified, dynamically-linked dependency, which is how " +
                "this app uses it (see LGPL_COMPLIANCE.md).",
            canonicalTextUrl = LGPL_2_1_URL,
            projectUrl = "https://github.com/AgNO3/jcifs-ng",
        ),
        LicenseEntry("SLF4J no-op binding", "MIT", MIT_SUMMARY, MIT_URL),
        LicenseEntry(
            component = "NanoHTTPD (embedded web setup server)",
            licenseName = "BSD-3-Clause",
            summary = BSD_3_SUMMARY,
            canonicalTextUrl = BSD_3_URL,
            projectUrl = "https://github.com/NanoHttpd/nanohttpd",
        ),
        apache("ZXing core (QR encoding)", "https://github.com/zxing/zxing"),
        apache("Desugar JDK libs"),
    )

    /**
     * Weather **data** attribution (CC BY 4.0) — legally distinct from the entries
     * above, since this is data the app calls out to, not a bundled library (see
     * WEATHER_LICENSING.md's "the distinction that matters" table). Required regardless
     * of whether the configured endpoint is the free Open-Meteo tier or a paid,
     * commercial-use one; only the request-volume/commercial terms differ between those.
     */
    const val WEATHER_ATTRIBUTION_URL = "https://open-meteo.com/"
}
