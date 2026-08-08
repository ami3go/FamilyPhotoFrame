# Phase 0 keeps R8/shrinking off (see app/build.gradle.kts and BUILD_NOTES.md).
# These rules are staged for the Phase 1 release-engineering pass that turns on
# minification. They are harmless while minify is disabled.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.example.familyphotoframe.**$$serializer { *; }
-keepclassmembers class com.example.familyphotoframe.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# R8 is now ENABLED for release (isMinifyEnabled = true). Everything below is
# load-bearing: each rule protects a layer that resolves types reflectively, so
# R8 cannot see the usage and would otherwise strip or rename it.
# ---------------------------------------------------------------------------

# Settings models are serialized by name through kotlinx.serialization; renaming a
# property silently changes the on-disk and exported-config key.
-keep class com.example.familyphotoframe.data.settings.** { *; }

# Enum values are resolved by name from the web API, config import, and DataStore
# (valueOf). Obfuscating them turns a valid saved config into an exception at startup.
-keepclassmembers enum com.example.familyphotoframe.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room entities/DAOs: column and method names are matched against generated SQL.
-keep class com.example.familyphotoframe.data.db.** { *; }

# jcifs-ng resolves providers and crypto implementations reflectively, and warns about
# optional dependencies (BouncyCastle, logging backends) that are not on this classpath.
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# NanoHTTPD subclasses its server and resolves handlers reflectively.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Coil decoders/fetchers are discovered via ServiceLoader-style registration.
-keep class coil.** { *; }
-dontwarn coil.**

# Compose keeps its own rules via consumer files; this guards the composer intrinsics
# that R8's optimizer has historically mis-inlined.
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Core-library desugaring stubs for java.time on API 21-25.
-dontwarn java.time.**
-dontwarn j$.time.**
