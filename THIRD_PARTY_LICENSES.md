# Third-Party Licenses

See also `Settings → "Open-source licenses"` in the app, which links each entry below to its canonical license text and, where relevant, its upstream source (Phase 2 increment 13).

All third-party runtime dependencies in Phase 0 are licensed under the
**Apache License 2.0**. Versions are pinned in `gradle/libs.versions.toml`; regenerate
this file whenever a version is bumped (spec §3.1).

| Component | Group / artifact | Version | License |
|---|---|---|---|
| AndroidX Core KTX | androidx.core:core-ktx | 1.13.1 | Apache-2.0 |
| AndroidX Lifecycle (runtime/viewmodel/compose) | androidx.lifecycle:* | 2.8.6 | Apache-2.0 |
| AndroidX Activity Compose | androidx.activity:activity-compose | 1.9.3 | Apache-2.0 |
| Jetpack Compose (BOM) | androidx.compose:compose-bom | 2024.09.03 | Apache-2.0 |
| Compose UI / Graphics / Tooling / Material3 | androidx.compose.* | via BOM | Apache-2.0 |
| Coil (Compose) | io.coil-kt:coil-compose | 2.7.0 | Apache-2.0 |
| Room (runtime/ktx/compiler/testing) | androidx.room:* | 2.6.1 | Apache-2.0 |
| DataStore | androidx.datastore:datastore | 1.1.1 | Apache-2.0 |
| kotlinx.serialization JSON | org.jetbrains.kotlinx:kotlinx-serialization-json | 1.7.3 | Apache-2.0 |
| kotlinx.coroutines (android/test) | org.jetbrains.kotlinx:kotlinx-coroutines-* | 1.9.0 | Apache-2.0 |
| AndroidX DocumentFile | androidx.documentfile:documentfile | 1.0.1 | Apache-2.0 |
| AndroidX ExifInterface | androidx.exifinterface:exifinterface | 1.3.7 | Apache-2.0 |
| jcifs-ng (SMB2/3 client) | eu.agno3.jcifs:jcifs-ng | 2.1.10 | **LGPL-2.1** — see LGPL_COMPLIANCE.md |
| SLF4J no-op binding | org.slf4j:slf4j-nop | 1.7.36 | MIT |
| NanoHTTPD (embedded web setup server) | org.nanohttpd:nanohttpd | 2.3.1 | BSD-3-Clause |
| ZXing core (QR encoding for pairing) | com.google.zxing:core | 3.5.3 | Apache-2.0 |

Weather data, when the overlay is enabled, comes from a user-configurable third-party
endpoint (default Open-Meteo). The data is licensed CC BY 4.0 and **requires attribution**;
the free service tier is non-commercial only. See LGPL_COMPLIANCE.md's sibling document
WEATHER_LICENSING.md for the release gate.
| Desugar JDK libs | com.android.tools:desugar_jdk_libs | 2.1.2 | Apache-2.0 / GPL-2.0-with-classpath-exception (build-time) |
| JUnit 4 | junit:junit | 4.13.2 | EPL-1.0 (test only) |
| Robolectric | org.robolectric:robolectric | 4.13 | MIT/Apache-2.0 (test only) |
| AndroidX Test (ext-junit/runner/espresso) | androidx.test.* | 1.2.1 / 1.6.2 / 3.6.1 | Apache-2.0 (test only) |

Kotlin standard library and the Kotlin/Android Gradle plugins are Apache-2.0.

Bundled sample photos in `app/src/main/assets/fallback/` are original images
generated for this project and carry no third-party rights.

A machine-generated report can also be produced at build time with a license plugin
(e.g. `com.jaredsburrows.license`) in a later phase; this table is the curated
source of truth for Phase 0.
