#!/usr/bin/env python3
from pathlib import Path
import re
R=Path(__file__).resolve().parents[1]
def rd(p): return (R/p).read_text(encoding='utf-8')
def wr(p,s): (R/p).write_text(s,encoding='utf-8')
def rep(s,a,b,label):
    if s.count(a)!=1: raise RuntimeError(f'{label}: {s.count(a)} matches')
    return s.replace(a,b,1)
def rex(s,p,b,label):
    o,n=re.subn(p,b,s,count=1,flags=re.S)
    if n!=1: raise RuntimeError(f'{label}: {n} matches')
    return o

p='app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt'; s=rd(p)
a='''/** Visual gap between collage tiles. */
enum class CollageGap { NONE, SMALL, MEDIUM }

@Serializable
data class PortraitCollageSettings(
    val mode: PortraitCollageMode = PortraitCollageMode.AUTOMATIC,
    val maxPhotos: Int = 3,
    val fallback: PortraitFallback = PortraitFallback.BLURRED_BACKGROUND,
    val gap: CollageGap = CollageGap.SMALL,
    /**
     * Task §12: subtle continuous motion on three-portrait-photo frames.
     *
     * Only the on/off switch is public; the internal OFF/SUBTLE/NORMAL profiles exist so
     * accessibility settings can reduce amplitude (task §13) without a second code path.
     */
    val animateThreePhotoFrames: Boolean = true,
) {
    val maxPhotosClamped: Int get() = maxPhotos.coerceIn(2, 3)
}
'''
b='''/** Which photo orientations are allowed to participate in a collage. */
enum class CollageOrientationFilter { ANY, PORTRAIT_ONLY, LANDSCAPE_ONLY, SAME_AS_ANCHOR }
/** How the optimizer may arrange selected photos. */
enum class CollageLayoutPreference { AUTO, COLUMNS, ROWS, FEATURED }
/** Whether a tile crops to fill or keeps the full photo visible. */
enum class CollageScaleMode { CROP, FIT }
/** Focal placement used for crop anchoring and fitted-image positioning. */
enum class CollageAlignment { CENTER, TOP, BOTTOM, LEFT, RIGHT }
/** Background for tile letterboxing and visible gaps. */
enum class CollageBackground { APP_BACKGROUND, BLACK, WHITE }
/** Visual gap between collage tiles. */
enum class CollageGap { NONE, SMALL, MEDIUM, LARGE }

@Serializable
data class PortraitCollageSettings(
    val mode: PortraitCollageMode = PortraitCollageMode.AUTOMATIC,
    val maxPhotos: Int = 3,
    val fallback: PortraitFallback = PortraitFallback.BLURRED_BACKGROUND,
    val gap: CollageGap = CollageGap.SMALL,
    val orientationFilter: CollageOrientationFilter = CollageOrientationFilter.ANY,
    val layoutPreference: CollageLayoutPreference = CollageLayoutPreference.AUTO,
    val scaleMode: CollageScaleMode = CollageScaleMode.CROP,
    val alignment: CollageAlignment = CollageAlignment.CENTER,
    val background: CollageBackground = CollageBackground.APP_BACKGROUND,
    val cornerRadiusDp: Int = 0,
    /** Subtle continuous motion on equal three-photo portrait frames. */
    val animateThreePhotoFrames: Boolean = true,
) {
    val maxPhotosClamped: Int get() = maxPhotos.coerceIn(2, 3)
    val cornerRadiusDpClamped: Int get() = cornerRadiusDp.coerceIn(0, 32)
}
'''
s=rep(s,a,b,'settings model'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/domain/engine/PortraitCollagePolicy.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.PortraitCollageMode\n','import com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\nimport com.example.familyphotoframe.data.settings.PortraitCollageMode\n','imports')
s=rep(s,'    THREE_COLUMNS(3),\n','    THREE_COLUMNS(3),\n    THREE_ROWS(3),\n','layout enum')
s=rep(s,'''        CollageLayout.THREE_COLUMNS -> listOf(
            tile(0f, 0f, 1f / 3f, 1f),
            tile(1f / 3f, 0f, 2f / 3f, 1f),
            tile(2f / 3f, 0f, 1f, 1f),
        )
''','''        CollageLayout.THREE_COLUMNS -> listOf(
            tile(0f, 0f, 1f / 3f, 1f),
            tile(1f / 3f, 0f, 2f / 3f, 1f),
            tile(2f / 3f, 0f, 1f, 1f),
        )
        CollageLayout.THREE_ROWS -> listOf(
            tile(0f, 0f, 1f, 1f / 3f),
            tile(0f, 1f / 3f, 1f, 2f / 3f),
            tile(0f, 2f / 3f, 1f, 1f),
        )
''','geometry')
s=rep(s,'''        maxPhotos: Int,
        nowEpochMs: Long,
    ): CollageDecision? {''','''        maxPhotos: Int,
        nowEpochMs: Long,
        orientationFilter: CollageOrientationFilter = CollageOrientationFilter.ANY,
        layoutPreference: CollageLayoutPreference = CollageLayoutPreference.AUTO,
    ): CollageDecision? {''','signature')
s=rep(s,'''        val bounded = candidates.asSequence()
            .filter { it.id != anchor.id }
''','''        if (!orientationAllowed(anchor.orientation, anchor.orientation, orientationFilter)) return null
        val bounded = candidates.asSequence()
            .filter { it.id != anchor.id }
            .filter { orientationAllowed(it.orientation, anchor.orientation, orientationFilter) }
''','filter')
s=s.replace('''                    nowEpochMs = nowEpochMs,
                    evaluatedLayout = { statsBuilder.evaluatedLayoutCount++ },''','''                    nowEpochMs = nowEpochMs,
                    layoutPreference = layoutPreference,
                    evaluatedLayout = { statsBuilder.evaluatedLayoutCount++ },''')
s=rep(s,'''        nowEpochMs: Long,
        evaluatedLayout: () -> Unit,
    ): ScoredPresentation? {
        val layouts = compatibleLayouts(photos.map { it.orientation })''','''        nowEpochMs: Long,
        layoutPreference: CollageLayoutPreference,
        evaluatedLayout: () -> Unit,
    ): ScoredPresentation? {
        val layouts = compatibleLayouts(photos.map { it.orientation }, layoutPreference)''','evaluation')
pat=r'''    private fun compatibleLayouts\(orientations: List<PhotoOrientation>\): List<CollageLayout> \{.*?\n    \}\n\n    private fun orientationTier'''
new='''    private fun compatibleLayouts(
        orientations: List<PhotoOrientation>,
        preference: CollageLayoutPreference,
    ): List<CollageLayout> {
        if (orientations.size == 2) return when (preference) {
            CollageLayoutPreference.COLUMNS -> listOf(CollageLayout.TWO_COLUMNS)
            CollageLayoutPreference.ROWS -> listOf(CollageLayout.TWO_ROWS)
            CollageLayoutPreference.FEATURED, CollageLayoutPreference.AUTO ->
                if (orientations.all { it == PhotoOrientation.PORTRAIT }) listOf(CollageLayout.TWO_COLUMNS)
                else listOf(CollageLayout.TWO_COLUMNS, CollageLayout.TWO_ROWS)
        }
        if (orientations.size != 3) return emptyList()
        if (preference == CollageLayoutPreference.COLUMNS) return listOf(CollageLayout.THREE_COLUMNS)
        if (preference == CollageLayoutPreference.ROWS) return listOf(CollageLayout.THREE_ROWS)
        val featured = listOf(
            CollageLayout.FULL_TOP_TWO_BOTTOM, CollageLayout.TWO_TOP_FULL_BOTTOM,
            CollageLayout.FULL_LEFT_TWO_RIGHT, CollageLayout.TWO_LEFT_FULL_RIGHT,
        )
        if (preference == CollageLayoutPreference.FEATURED) return featured
        val portrait = orientations.count { it == PhotoOrientation.PORTRAIT }
        val landscape = orientations.count { it == PhotoOrientation.LANDSCAPE }
        val square = orientations.size - portrait - landscape
        return when {
            portrait == 3 -> listOf(CollageLayout.THREE_COLUMNS)
            landscape == 3 -> listOf(CollageLayout.FULL_TOP_TWO_BOTTOM, CollageLayout.TWO_TOP_FULL_BOTTOM)
            square > 0 -> listOf(CollageLayout.THREE_COLUMNS) + featured
            else -> featured
        }
    }

    private fun orientationAllowed(
        orientation: PhotoOrientation,
        anchorOrientation: PhotoOrientation,
        filter: CollageOrientationFilter,
    ): Boolean = when (filter) {
        CollageOrientationFilter.ANY -> true
        CollageOrientationFilter.PORTRAIT_ONLY -> orientation == PhotoOrientation.PORTRAIT
        CollageOrientationFilter.LANDSCAPE_ONLY -> orientation == PhotoOrientation.LANDSCAPE
        CollageOrientationFilter.SAME_AS_ANCHOR -> orientation == anchorOrientation
    }

    private fun orientationTier'''
s=rex(s,pat,new,'layouts'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowPreparation.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.CollageGap\n','import com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\n','prep imports')
s=s.replace('        CollageGap.MEDIUM -> 8f * scale\n','        CollageGap.MEDIUM -> 8f * scale\n        CollageGap.LARGE -> 16f * scale\n')
s=rep(s,'        CollageGap.MEDIUM -> 8f * density\n','        CollageGap.MEDIUM -> 8f * density\n        CollageGap.LARGE -> 16f * density\n','gap')
s=rep(s,'''    portraitFallback: PortraitFallback,
    collageGap: CollageGap,
    prepareSoftFocusBlur: Boolean,''','''    portraitFallback: PortraitFallback,
    collageGap: CollageGap,
    collageOrientationFilter: CollageOrientationFilter = CollageOrientationFilter.ANY,
    collageLayoutPreference: CollageLayoutPreference = CollageLayoutPreference.AUTO,
    prepareSoftFocusBlur: Boolean,''','params')
s=rep(s,'''                maxPhotos = maxCollagePhotos.coerceIn(1, 3),
                nowEpochMs = System.currentTimeMillis(),
            )''','''                maxPhotos = maxCollagePhotos.coerceIn(1, 3),
                nowEpochMs = System.currentTimeMillis(),
                orientationFilter = collageOrientationFilter,
                layoutPreference = collageLayoutPreference,
            )''','policy call')
s=rep(s,'''            "collageMode" to collageMode.name,
            "configuredMaxCollagePhotos" to configuredMaxCollagePhotos.toString(),''','''            "collageMode" to collageMode.name,
            "collageOrientationFilter" to collageOrientationFilter.name,
            "collageLayoutPreference" to collageLayoutPreference.name,
            "configuredMaxCollagePhotos" to configuredMaxCollagePhotos.toString(),''','diagnostics')
wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt'; s=rd(p)
s=rep(s,'        "collageMode", "configuredMaxCollagePhotos", "memoryMaxCollagePhotos",\n','        "collageMode", "collageOrientationFilter", "collageLayoutPreference",\n        "configuredMaxCollagePhotos", "memoryMaxCollagePhotos",\n','diagnostic allowlist'); wr(p,s)
print('collage model/optimizer patch applied')
