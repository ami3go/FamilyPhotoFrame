from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one occurrence, found {count}: {old[:80]!r}')
    write(path, text.replace(old, new, 1))


def replace_between(path: str, start: str, end: str, replacement: str) -> None:
    text = read(path)
    start_i = text.find(start)
    if start_i < 0:
        raise RuntimeError(f'{path}: start marker not found: {start!r}')
    end_i = text.find(end, start_i)
    if end_i < 0:
        raise RuntimeError(f'{path}: end marker not found: {end!r}')
    write(path, text[:start_i] + replacement + text[end_i:])


def regex_once(path: str, pattern: str, replacement: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f'{path}: regex expected one match, found {count}: {pattern[:100]!r}')
    write(path, updated)


# ---------------------------------------------------------------------------
# 1. Persisted selection compatibility belongs at the serialization boundary.
#    Remove LEAST_RECENT_RANDOM as a runtime enum value and migration marker.
# ---------------------------------------------------------------------------
app_settings = 'app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt'
selection_start = '/**\n * How the next photo is chosen (spec §9.6).'
selection_end = '/**\n * What to play when a remote (SMB/Synology) primary is unreachable'
selection_block = '''/**
 * How the next photo is chosen (spec §9.6).
 *
 * Historical `LEAST_RECENT_RANDOM` values are translated at the persistence/web
 * boundary to [SHUFFLE_NO_REPEAT]. Keeping that old strategy out of the runtime enum
 * prevents new code from accidentally reactivating the pre-v52 implementation.
 */
@Serializable(with = SelectionModeSerializer::class)
enum class SelectionMode {
    /** Stable indexed path order; repeats only after reaching the end. */
    SEQUENTIAL,
    SHUFFLE_NO_REPEAT,
    FOLDER_BALANCED_SHUFFLE,
    DATE_TAKEN_NEWEST,
    DATE_TAKEN_OLDEST,
    /**
     * System-managed only — the pool is an explicit id list pushed by
     * `SlideshowViewModel`'s "on this day" trigger, not a SQL predicate. Never valid as
     * a directly user-chosen value for the global selection mode.
     */
    ON_THIS_DAY,
    ;

    companion object {
        private const val LEGACY_LEAST_RECENT_RANDOM = "LEAST_RECENT_RANDOM"

        fun fromStorage(value: String): SelectionMode? {
            val normalized = value.trim()
            if (normalized.equals(LEGACY_LEAST_RECENT_RANDOM, ignoreCase = true)) {
                return SHUFFLE_NO_REPEAT
            }
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        }
    }
}

object SelectionModeSerializer : KSerializer<SelectionMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SelectionMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SelectionMode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): SelectionMode =
        SelectionMode.fromStorage(decoder.decodeString()) ?: SelectionMode.FOLDER_BALANCED_SHUFFLE
}

'''
replace_between(app_settings, selection_start, selection_end, selection_block)
replace_once(
    app_settings,
    '    /** One-time compatibility migration: legacy random becomes global no-repeat. */\n'
    '    val playbackOrderMigrationVersion: Int = 0,\n',
    '',
)
replace_once(
    app_settings,
    '    val maxPhotosClamped: Int get() = maxPhotos.coerceIn(2, 3)\n'
    '    val cornerRadiusDpClamped: Int get() = cornerRadiusDp.coerceIn(0, 32)\n',
    '    val maxPhotosClamped: Int get() = maxPhotos.coerceIn(2, 3)\n'
    '    val cornerRadiusDpClamped: Int get() = cornerRadiusDp.coerceIn(0, 32)\n\n'
    '    fun normalized(): PortraitCollageSettings = copy(\n'
    '        maxPhotos = maxPhotosClamped,\n'
    '        cornerRadiusDp = cornerRadiusDpClamped,\n'
    '    )\n',
)
method_marker = '    /** Apply non-destructive defaults migrations after deserialization/import. */\n'
method_i = read(app_settings).find(method_marker)
if method_i < 0:
    raise RuntimeError('AppSettings.withCurrentDefaults marker not found')
text = read(app_settings)
write(
    app_settings,
    text[:method_i] + '''    /** Apply compatibility migrations and canonical bounds after deserialization/import. */
    fun withCurrentDefaults(): AppSettings = AppSettingsCanonicalizer.normalize(this)
}
''',
)

canonicalizer = '''package com.example.familyphotoframe.data.settings

/**
 * Single compatibility/canonicalization boundary for persisted settings.
 *
 * Runtime code should not contain historical migration branches. Old schema values are
 * accepted here, converted once, and the returned object always obeys the current safe
 * bounds before it reaches the slideshow engine or network schedulers.
 */
internal object AppSettingsCanonicalizer {
    private const val BRIGHTNESS_AUTOMATION_MIGRATION_V1 = 1

    fun normalize(settings: AppSettings): AppSettings {
        val migrationPending =
            settings.brightnessAutomationMigrationVersion < BRIGHTNESS_AUTOMATION_MIGRATION_V1
        val shouldImportLegacySleep =
            migrationPending && settings.schedule.sleepEnabled &&
                settings.brightnessAutomation == BrightnessAutomationSettings()

        val migratedBrightness = if (shouldImportLegacySleep) {
            val dayBrightness = safeBrightness(settings.schedule.brightnessDay, 1f)
            val nightBrightness = safeBrightness(settings.schedule.brightnessNight, 0.3f)
            settings.brightnessAutomation.copy(
                mode = BrightnessMode.SCHEDULED,
                manualBrightness = dayBrightness,
                periods = listOf(
                    BrightnessPeriod(
                        id = "day",
                        startTime = safeClock(settings.schedule.sleepEnd, "07:00"),
                        brightness = dayBrightness,
                        action = NightAction.DIM_ONLY,
                    ),
                    BrightnessPeriod(
                        id = "night",
                        startTime = safeClock(settings.schedule.sleepStart, "23:00"),
                        brightness = nightBrightness,
                        action = NightAction.PAUSE_SLIDESHOW,
                    ),
                ),
            ).normalized()
        } else {
            settings.brightnessAutomation.normalized()
        }

        val migratedSchedule = if (migrationPending) {
            // Compatibility fields remain serializable for old backups/clients, but the
            // obsolete runtime switch is always disabled after migration.
            settings.schedule.copy(sleepEnabled = false)
        } else {
            settings.schedule
        }

        return settings.copy(
            intervalSeconds = PlaybackInterval.clamp(settings.intervalSeconds),
            transitionDurationMs = settings.transitionDurationMs.coerceIn(300, 2_000),
            portraitCollage = settings.portraitCollage.normalized(),
            temporarilySuppressAfterDecodeFailures =
                settings.temporarilySuppressAfterDecodeFailures.coerceAtLeast(1),
            brightnessAutomationMigrationVersion = BRIGHTNESS_AUTOMATION_MIGRATION_V1,
            schedule = migratedSchedule,
            filters = settings.filters.withCurrentDefaultFormats(),
            playlists = settings.playlists.withCurrentDefaults(),
            brightnessAutomation = migratedBrightness,
            web = settings.web.copy(
                rememberedBrowsers = settings.web.rememberedBrowsers.normalized(),
            ),
            webUpload = settings.webUpload.normalized(),
            localThumbnailCache = settings.localThumbnailCache.normalized(),
            onThisDay = settings.onThisDay.normalized(),
        )
    }

    private fun safeBrightness(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0.01f, 1f) else fallback

    private fun safeClock(value: String, fallback: String): String {
        val match = CLOCK.matchEntire(value.trim()) ?: return fallback
        val hour = match.groupValues[1].toIntOrNull() ?: return fallback
        val minute = match.groupValues[2].toIntOrNull() ?: return fallback
        if (hour !in 0..23 || minute !in 0..59) return fallback
        return "%02d:%02d".format(hour, minute)
    }

    private val CLOCK = Regex("^(\\\\d{1,2}):(\\\\d{2})$")
}
'''
write('app/src/main/java/com/example/familyphotoframe/data/settings/AppSettingsCanonicalizer.kt', canonicalizer)

# ---------------------------------------------------------------------------
# 2. Canonicalize every settings write. This prevents malformed imported or legacy
#    values from living in memory until the next process restart.
# ---------------------------------------------------------------------------
repo = 'app/src/main/java/com/example/familyphotoframe/data/settings/SettingsRepository.kt'
replace_once(
    repo,
    '    override suspend fun writeTo(t: AppSettings, output: OutputStream) {\n'
    '        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())\n'
    '    }\n',
    '    override suspend fun writeTo(t: AppSettings, output: OutputStream) {\n'
    '        val canonical = t.withCurrentDefaults()\n'
    '        output.write(json.encodeToString(AppSettings.serializer(), canonical).encodeToByteArray())\n'
    '    }\n',
)
replace_once(
    repo,
    '    suspend fun update(transform: (AppSettings) -> AppSettings) {\n'
    '        dataStore.updateData(transform)\n'
    '    }\n',
    '    suspend fun update(transform: (AppSettings) -> AppSettings) {\n'
    '        dataStore.updateData { current ->\n'
    '            val canonical = transform(current).withCurrentDefaults()\n'
    '            if (canonical == current) current else canonical\n'
    '        }\n'
    '    }\n',
)
replace_once(
    repo,
    '        dataStore.updateData { current ->\n'
    '            if (current.selectedFolders == normalized) current\n'
    '            else current.copy(selectedFolders = normalized)\n'
    '        }\n',
    '        update { current ->\n'
    '            if (current.selectedFolders == normalized) current\n'
    '            else current.copy(selectedFolders = normalized)\n'
    '        }\n',
)

# ---------------------------------------------------------------------------
# 3. Remove obsolete pre-v52 random-selection runtime code and DAO queries.
# ---------------------------------------------------------------------------
engine = 'app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt'
for imp in [
    'import com.example.familyphotoframe.domain.randomize.FolderCycleQueue\n',
    'import com.example.familyphotoframe.domain.randomize.LeastRecentRandom\n',
]:
    replace_once(engine, imp, '')
replace_once(engine, '    @Volatile private var windowSize: Int = 50\n', '')
replace_once(
    engine,
    '    /** Independent outer folder cycles for least-recent random playback. */\n'
    '    private val primaryFolderCycle = FolderCycleQueue()\n'
    '    private val fallbackFolderCycle = FolderCycleQueue()\n\n',
    '',
)
replace_once(
    engine,
    '        primaryFolderCycle.reset()\n'
    '        fallbackFolderCycle.reset()\n',
    '',
)
regex_once(
    engine,
    r'\n    private fun publishFolderCycleProgress\(queue: FolderCycleQueue\) \{.*?\n    \}\n',
    '\n',
)
replace_once(engine, '        val current = excludeId ?: _ui.value.current?.id\n', '')
replace_once(engine, '    private suspend fun pick(excludeId: Long? = null): Pick? {\n', '    private suspend fun pick(): Pick? {\n')
replace_once(engine, '                folderCycle = primaryFolderCycle,\n                currentId = current,\n', '')
replace_once(engine, '                folderCycle = fallbackFolderCycle,\n                currentId = current,\n', '')
replace_once(
    engine,
    '        queue: PlaybackQueue,\n'
    '        folderCycle: FolderCycleQueue,\n'
    '        currentId: Long?,\n',
    '        queue: PlaybackQueue,\n',
)
regex_once(
    engine,
    r'\n        if \(selectionMode == SelectionMode\.LEAST_RECENT_RANDOM\) \{.*?\n            return null\n        \}\n',
    '\n',
)
replace_once(
    engine,
    '            SelectionMode.ON_THIS_DAY -> onThisDayIds\n'
    '            else -> emptyList()\n',
    '            SelectionMode.ON_THIS_DAY -> onThisDayIds\n'
    '            // Returned earlier; explicit branch keeps this when-expression exhaustive\n'
    '            // so adding another mode becomes a compiler-visible change.\n'
    '            SelectionMode.FOLDER_BALANCED_SHUFFLE -> emptyList()\n',
)
replace_once(
    engine,
    '            pick(excludeId = photo.id).also { reservedNext = it }?.item?.toDisplayPhoto()\n',
    '            pick().also { reservedNext = it }?.item?.toDisplayPhoto()\n',
)
replace_once(engine, '        const val MAX_FOLDER_MISSES = 256\n', '')
replace_once(
    engine,
    '    /** Only used for legacy in-memory history; all database fields are preserved. */\n',
    '    /** Placeholder used by the bounded in-memory Previous/Next history. */\n',
)

photo_dao = 'app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt'
replace_between(
    photo_dao,
    '    /**\n     * Least-recent-random candidate window',
    '    /** Eligible folders for balanced random rotation',
    '',
)
replace_between(
    photo_dao,
    '    /** Eligible folders for balanced random rotation',
    '    /** Least-recent photo window inside one folder',
    '',
)
replace_between(
    photo_dao,
    '    /** Least-recent photo window inside one folder',
    '    /**\n     * Folder-only eligibility for the persistent coordinator.',
    '',
)

playback_queue = 'app/src/main/java/com/example/familyphotoframe/domain/randomize/PlaybackQueue.kt'
replace_once(
    playback_queue,
    ' * [LeastRecentRandom] samples a *window* supplied by the DAO, which biases away from\n'
    ' * recently shown photos but can never promise "every photo once before any repeat":\n'
    ' * the window is smaller than the library, so a photo just outside it can reappear\n'
    ' * early. This class takes the whole displayable id list instead and consumes it, which\n'
    ' * is what makes the no-repeat guarantee actually hold.\n',
    ' * This queue consumes the whole displayable id list rather than sampling a partial\n'
    ' * window, which makes the no-repeat guarantee explicit and testable.\n',
)

for obsolete in [
    'app/src/main/java/com/example/familyphotoframe/domain/randomize/FolderBalancedPlaybackQueue.kt',
    'app/src/main/java/com/example/familyphotoframe/domain/randomize/FolderCycleQueue.kt',
    'app/src/main/java/com/example/familyphotoframe/domain/randomize/LeastRecentRandom.kt',
    'app/src/test/java/com/example/familyphotoframe/domain/randomize/FolderBalancedPlaybackQueueTest.kt',
    'app/src/test/java/com/example/familyphotoframe/domain/randomize/FolderCycleQueueTest.kt',
    'app/src/test/java/com/example/familyphotoframe/domain/randomize/LeastRecentRandomTest.kt',
]:
    target = ROOT / obsolete
    if not target.exists():
        raise RuntimeError(f'obsolete file missing: {obsolete}')
    target.unlink()

# ---------------------------------------------------------------------------
# 4. Keep old web Sleep/Day/Night clients compatible, but isolate that translation
#    from the current web settings patcher.
# ---------------------------------------------------------------------------
legacy_web = '''package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.BrightnessPeriod
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Compatibility adapter for pre-unified-brightness web clients. */
internal object LegacyBrightnessWebPatch {
    private val keys = setOf(
        "sleepEnabled", "sleepStart", "sleepEnd", "brightnessDay", "brightnessNight",
    )

    fun validationError(patch: JsonObject): String? {
        string(patch, "sleepStart")?.let {
            if (SleepSchedule.parseMinutes(it) == null) return "sleepStart must be HH:mm"
        }
        string(patch, "sleepEnd")?.let {
            if (SleepSchedule.parseMinutes(it) == null) return "sleepEnd must be HH:mm"
        }
        float(patch, "brightnessDay")?.let {
            if (it !in 0.05f..1f) return "brightnessDay must be 0.05-1.0"
        }
        float(patch, "brightnessNight")?.let {
            if (it !in 0.05f..1f) return "brightnessNight must be 0.05-1.0"
        }
        return null
    }

    fun apply(current: AppSettings, patch: JsonObject): AppSettings {
        if (keys.none(patch::containsKey)) return current

        val base = current.brightnessAutomation
        val periods = base.periods.toMutableList()
        fun replacePeriod(period: BrightnessPeriod) {
            val index = periods.indexOfFirst { it.id == period.id }
            if (index >= 0) periods[index] = period else periods += period
        }

        val day = periods.firstOrNull { it.id == "day" } ?: BrightnessPeriod(
            id = "day",
            startTime = "07:00",
            brightness = base.manualBrightness,
            action = NightAction.DIM_ONLY,
        )
        val night = periods.firstOrNull { it.id == "night" } ?: BrightnessPeriod(
            id = "night",
            startTime = "23:00",
            brightness = 0.30f,
            action = NightAction.PAUSE_SLIDESHOW,
        )
        replacePeriod(day.copy(
            startTime = string(patch, "sleepEnd")?.trim() ?: day.startTime,
            brightness = float(patch, "brightnessDay") ?: day.brightness,
        ))
        replacePeriod(night.copy(
            startTime = string(patch, "sleepStart")?.trim() ?: night.startTime,
            brightness = float(patch, "brightnessNight") ?: night.brightness,
            action = if (bool(patch, "sleepEnabled") == true) {
                NightAction.PAUSE_SLIDESHOW
            } else {
                night.action
            },
        ))
        val mode = when (bool(patch, "sleepEnabled")) {
            true -> BrightnessMode.SCHEDULED
            false -> BrightnessMode.MANUAL
            null -> base.mode
        }
        return current.copy(
            brightnessAutomation = base.copy(
                mode = mode,
                manualBrightness = float(patch, "brightnessDay") ?: base.manualBrightness,
                periods = periods,
            ).normalized(),
        )
    }

    private fun string(patch: JsonObject, key: String): String? =
        patch[key]?.jsonPrimitive?.let { if (it.isString) it.content else null }

    private fun float(patch: JsonObject, key: String): Float? =
        patch[key]?.jsonPrimitive?.doubleOrNull?.toFloat()

    private fun bool(patch: JsonObject, key: String): Boolean? =
        patch[key]?.jsonPrimitive?.booleanOrNull
}
'''
write('app/src/main/java/com/example/familyphotoframe/web/LegacyBrightnessWebPatch.kt', legacy_web)

web_patch = 'app/src/main/java/com/example/familyphotoframe/web/WebSettingsPatchApplier.kt'
replace_once(web_patch, 'import com.example.familyphotoframe.data.settings.BrightnessPeriod\n', '')
replace_once(
    web_patch,
    '        str("selectionMode")?.let { v ->\n'
    '            // ON_THIS_DAY is system-managed only (empty pool otherwise) — never a valid\n'
    '            // direct choice for the global selection mode. See SelectionMode.ON_THIS_DAY.\n'
    '            if (v == SelectionMode.ON_THIS_DAY.name || SelectionMode.entries.none { it.name == v }) {\n'
    '                return "Unknown selectionMode"\n'
    '            }\n'
    '        }\n',
    '        str("selectionMode")?.let { v ->\n'
    '            // Historical LEAST_RECENT_RANDOM is accepted as an alias for the current\n'
    '            // no-repeat mode; ON_THIS_DAY remains system-managed only.\n'
    '            val parsed = SelectionMode.fromStorage(v)\n'
    '            if (parsed == null || parsed == SelectionMode.ON_THIS_DAY) {\n'
    '                return "Unknown selectionMode"\n'
    '            }\n'
    '        }\n',
)
replace_once(web_patch, '        float("brightnessDay")?.let { if (it !in 0.05f..1f) return "brightnessDay must be 0.05-1.0" }\n', '')
replace_once(web_patch, '        float("brightnessNight")?.let { if (it !in 0.05f..1f) return "brightnessNight must be 0.05-1.0" }\n', '')
replace_once(web_patch, '        str("sleepStart")?.let { if (SleepSchedule.parseMinutes(it) == null) return "sleepStart must be HH:mm" }\n', '')
replace_once(web_patch, '        str("sleepEnd")?.let { if (SleepSchedule.parseMinutes(it) == null) return "sleepEnd must be HH:mm" }\n', '')
replace_once(
    web_patch,
    '        // Same guard as the on-device toggle: enabling favourites-only with nothing\n',
    '        LegacyBrightnessWebPatch.validationError(patch)?.let { return it }\n\n'
    '        // Same guard as the on-device toggle: enabling favourites-only with nothing\n',
)
legacy_start = '        val hasLegacyBrightnessPatch = listOf(\n'
legacy_end = '            int("intervalSeconds")?.let { next = next.copy(intervalSeconds = it) }\n'
replace_between(
    web_patch,
    legacy_start,
    legacy_end,
    '        settings.update { cur ->\n'
    '            var next = LegacyBrightnessWebPatch.apply(cur, patch)\n',
)
replace_once(
    web_patch,
    '            str("selectionMode")?.let { v -> next = next.copy(selectionMode = SelectionMode.valueOf(v)) }\n',
    '            str("selectionMode")?.let { v ->\n'
    '                SelectionMode.fromStorage(v)?.takeIf { it != SelectionMode.ON_THIS_DAY }?.let { mode ->\n'
    '                    next = next.copy(selectionMode = mode)\n'
    '                }\n'
    '            }\n',
)

# ---------------------------------------------------------------------------
# 5. Remove dead ViewModel mutation routes for the retired Schedule -> Sleep UI.
# ---------------------------------------------------------------------------
vm = 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt'
regex_once(
    vm,
    r'\n    // ---- sleep schedule \(spec §20 `schedule`\) ----\n\n'
    r'    fun setSleepEnabled\(value: Boolean\) \{.*?\n'
    r'    fun setSleepEnd\(text: String\) \{.*?\n    \}\n',
    '\n',
)

# ---------------------------------------------------------------------------
# 6. Remove stale UI wording that described the deleted windowed-random mode.
# ---------------------------------------------------------------------------
engine_state = 'app/src/main/java/com/example/familyphotoframe/domain/engine/EngineState.kt'
replace_once(
    engine_state,
    '    /**\n'
    '     * Progress through the current no-repeat cycle: photos shown so far, and the pool\n'
    '     * size. Both zero in the windowed random mode, which has no cycle to report.\n'
    '     */\n',
    '    /** Progress through the current explicit-id playback cycle. */\n',
)

# ---------------------------------------------------------------------------
# Tests: preserve old-config compatibility while asserting the runtime legacy mode is gone.
# ---------------------------------------------------------------------------
serializer_test = 'app/src/test/java/com/example/familyphotoframe/data/settings/AppSettingsSerializerTest.kt'
replace_once(
    serializer_test,
    '        assertEquals(SelectionMode.SHUFFLE_NO_REPEAT, restored.selectionMode)\n'
    '        assertEquals(1, restored.playbackOrderMigrationVersion)\n'
    '        assertEquals(\n'
    '            SelectionMode.SHUFFLE_NO_REPEAT,\n'
    '            restored.withCurrentDefaults().selectionMode,\n'
    '        )\n',
    '        assertEquals(SelectionMode.SHUFFLE_NO_REPEAT, restored.selectionMode)\n'
    '        assertEquals(\n'
    '            SelectionMode.SHUFFLE_NO_REPEAT,\n'
    '            restored.withCurrentDefaults().selectionMode,\n'
    '        )\n',
)

selection_test = '''package com.example.familyphotoframe.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionModeSerializerTest {
    private val json = Json

    @Test
    fun legacyLeastRecentRandomDecodesToNoRepeatWithoutRuntimeEnumValue() {
        val decoded = json.decodeFromString(SelectionModeSerializer, "\\\"LEAST_RECENT_RANDOM\\\"")
        assertEquals(SelectionMode.SHUFFLE_NO_REPEAT, decoded)
        assertFalse(SelectionMode.entries.any { it.name == "LEAST_RECENT_RANDOM" })
    }

    @Test
    fun canonicalSerializationNeverWritesLegacyName() {
        val encoded = json.encodeToString(SelectionModeSerializer, SelectionMode.SHUFFLE_NO_REPEAT)
        assertTrue(encoded.contains("SHUFFLE_NO_REPEAT"))
        assertFalse(encoded.contains("LEAST_RECENT_RANDOM"))
    }
}
'''
write('app/src/test/java/com/example/familyphotoframe/data/settings/SelectionModeSerializerTest.kt', selection_test)

canonicalizer_test = '''package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsCanonicalizerTest {
    @Test
    fun unsafePersistedBoundsAreCanonicalizedBeforeRuntimeUse() {
        val normalized = AppSettings(
            intervalSeconds = -100,
            transitionDurationMs = 99_999,
            temporarilySuppressAfterDecodeFailures = 0,
            portraitCollage = PortraitCollageSettings(maxPhotos = 99, cornerRadiusDp = -4),
        ).withCurrentDefaults()

        assertEquals(3, normalized.intervalSeconds)
        assertEquals(2_000, normalized.transitionDurationMs)
        assertEquals(1, normalized.temporarilySuppressAfterDecodeFailures)
        assertEquals(3, normalized.portraitCollage.maxPhotos)
        assertEquals(0, normalized.portraitCollage.cornerRadiusDp)
    }

    @Test
    fun malformedLegacySleepValuesFallBackToSafeClockAndBrightness() {
        val normalized = AppSettings(
            schedule = ScheduleSettings(
                sleepEnabled = true,
                sleepStart = "88:99",
                sleepEnd = "bad",
                brightnessDay = Float.NaN,
                brightnessNight = 8f,
            ),
        ).withCurrentDefaults()

        assertFalse(normalized.schedule.sleepEnabled)
        val day = normalized.brightnessAutomation.periods.single { it.id == "day" }
        val night = normalized.brightnessAutomation.periods.single { it.id == "night" }
        assertEquals("07:00", day.startTime)
        assertEquals(1f, day.brightness, 0.0001f)
        assertEquals("23:00", night.startTime)
        assertEquals(1f, night.brightness, 0.0001f)
    }
}
'''
write('app/src/test/java/com/example/familyphotoframe/data/settings/AppSettingsCanonicalizerTest.kt', canonicalizer_test)

legacy_web_test = '''package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyBrightnessWebPatchTest {
    @Test
    fun oldSleepAliasesTranslateIntoUnifiedBrightnessOnly() {
        val patch = buildJsonObject {
            put("sleepEnabled", true)
            put("sleepStart", "22:10")
            put("sleepEnd", "06:40")
            put("brightnessDay", 0.70)
            put("brightnessNight", 0.12)
        }
        assertNull(LegacyBrightnessWebPatch.validationError(patch))

        val updated = LegacyBrightnessWebPatch.apply(AppSettings(), patch)
        assertEquals(BrightnessMode.SCHEDULED, updated.brightnessAutomation.mode)
        assertEquals(0.70f, updated.brightnessAutomation.manualBrightness, 0.0001f)
        val day = updated.brightnessAutomation.periods.single { it.id == "day" }
        val night = updated.brightnessAutomation.periods.single { it.id == "night" }
        assertEquals("06:40", day.startTime)
        assertEquals("22:10", night.startTime)
        assertEquals(NightAction.PAUSE_SLIDESHOW, night.action)
        // The compatibility adapter never re-enables the retired Schedule runtime flag.
        assertEquals(false, updated.schedule.sleepEnabled)
    }

    @Test
    fun malformedLegacyClockIsRejectedBeforeWrite() {
        val patch = buildJsonObject { put("sleepStart", "25:61") }
        assertEquals("sleepStart must be HH:mm", LegacyBrightnessWebPatch.validationError(patch))
    }
}
'''
write('app/src/test/java/com/example/familyphotoframe/web/LegacyBrightnessWebPatchTest.kt', legacy_web_test)

print('Stability refactor patch applied.')
