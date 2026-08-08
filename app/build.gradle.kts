import com.android.build.api.artifact.SingleArtifact
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.familyphotoframe"
    // compileSdk / targetSdk recorded in BUILD_NOTES.md (spec §2). Chosen at generation time.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.familyphotoframe"
        // Spec §2 sets the *commercial* floor at API 24. This build lowers it to 21
        // by product decision; API 21-23 is an UNVALIDATED tier (see
        // KNOWN_LIMITATIONS.md) and must not be marketed until its test matrix
        // passes (Contract Rule 12). Secret storage branches by API level because
        // AES-in-AndroidKeyStore is API 23+ — see KeystoreSecretStore.
        minSdk = 21            // Android 5.0
        targetSdk = 35
        versionCode = 33
        versionName = "0.12.13-prerelease"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * Release signing is configured from `keystore.properties` (git-ignored) or from
     * environment variables, so no key material is ever committed. When neither is
     * present the release build falls back to the debug key and says so loudly — a
     * developer building locally still gets an installable APK, but nobody can ship a
     * debug-signed release without having seen the warning.
     */
    val keystorePropsFile = rootProject.file("keystore.properties")
    // `Properties` is imported at the top of this file on purpose: inside a
    // build.gradle.kts, the name `java` resolves to the Java plugin *extension*, not the
    // `java.*` package, so a fully-qualified `java.util.Properties` fails to compile.
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile.inputStream().use { stream -> load(stream) }
        }
    }
    fun prop(name: String, env: String): String? =
        (keystoreProps.getProperty(name) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

    val releaseStorePath = prop("storeFile", "FPF_KEYSTORE")
    val hasReleaseSigning = releaseStorePath != null &&
        prop("storePassword", "FPF_KEYSTORE_PASSWORD") != null &&
        prop("keyAlias", "FPF_KEY_ALIAS") != null &&
        prop("keyPassword", "FPF_KEY_PASSWORD") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = prop("storePassword", "FPF_KEYSTORE_PASSWORD")
                keyAlias = prop("keyAlias", "FPF_KEY_ALIAS")
                keyPassword = prop("keyPassword", "FPF_KEY_PASSWORD")
                enableV1Signing = true    // APK Signature Scheme v1 is required below API 24
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // StrictMode is enabled at runtime in debug (App.kt) — Contract Rule 2.
        }
        release {
            // R8 shrinking + resource shrinking. Keep rules live in proguard-rules.pro;
            // reflection-driven layers (Room, kotlinx.serialization, Compose, jcifs-ng)
            // are the ones that need them, and each rule there says which and why.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WARNING: no release signing config found (keystore.properties or " +
                        "FPF_KEYSTORE* env vars). Falling back to the DEBUG key — this APK " +
                        "must not be distributed. See BUILD_NOTES.md.",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true   // java.time on minSdk 21 — spec §3.
    }
    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Room schema export dir (spec §6.3 / §27.5: schemas reviewed and migration-tested).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    // SMB / NAS source (Phase 1, spec §8).
    implementation(libs.jcifs.ng)
    runtimeOnly(libs.slf4j.nop)

    // Embedded web setup server (Phase 1.5, spec §15).
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}

// =============================================================================
// Merged-manifest permission audit (spec §7.8, §21.4, §27.8, §27.9).
//
// Runs against the FINAL merged manifest for every variant (libraries can inject
// permissions), fails the build if any forbidden broad media / storage permission
// is present, and writes a report to build/reports/permissions/<variant>.txt that
// is suitable to attach to release artifacts.
// =============================================================================
abstract class PermissionAuditTask : DefaultTask() {

    @get:org.gradle.api.tasks.InputFile
    abstract val mergedManifest: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Input
    abstract val variantName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.OutputFile
    abstract val reportFile: org.gradle.api.file.RegularFileProperty

    private val forbidden = listOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_MEDIA_LOCATION",
        "android.permission.QUERY_ALL_PACKAGES",
    )

    @org.gradle.api.tasks.TaskAction
    fun audit() {
        val xml = mergedManifest.get().asFile.readText()
        val nameRegex = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
        val declared = nameRegex.findAll(xml).map { it.groupValues[1] }.toSortedSet()

        val violations = declared.filter { it in forbidden }

        val report = buildString {
            appendLine("Permission audit — variant: ${variantName.get()}")
            appendLine("Generated: ${Instant.now()}")
            appendLine("Merged manifest: ${mergedManifest.get().asFile.path}")
            appendLine("=".repeat(72))
            appendLine("Declared <uses-permission> entries (${declared.size}):")
            declared.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("Forbidden broad media/storage permissions checked:")
            forbidden.forEach { appendLine("  - $it") }
            appendLine()
            if (violations.isEmpty()) {
                appendLine("RESULT: PASS — no forbidden broad media/storage permission present.")
            } else {
                appendLine("RESULT: FAIL — forbidden permissions present:")
                violations.forEach { appendLine("  !! $it") }
            }
        }
        val out = reportFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(report)
        logger.lifecycle("Permission audit (${variantName.get()}) -> ${out.path}")

        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Release-blocking permission(s) in merged manifest for ${variantName.get()}: " +
                    violations.joinToString(", ") + ". See ${out.path}."
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        val audit = tasks.register("audit${capName}Permissions", PermissionAuditTask::class.java) {
            variantName.set(variant.name)
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            reportFile.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        // Make the normal build path run the audit. Use matching{}.configureEach{}
        // (lazy) rather than tasks.named(...) because the assemble task may not be
        // registered yet at the moment this variant callback runs.
        tasks.matching { it.name == "assemble${capName}" }.configureEach { dependsOn(audit) }
    }
}
