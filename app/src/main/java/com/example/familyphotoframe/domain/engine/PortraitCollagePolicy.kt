package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.PortraitCollageMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Pure orientation, layout geometry and deterministic collage optimization. */
enum class PhotoOrientation { PORTRAIT, LANDSCAPE, SQUARE_OR_UNKNOWN }

enum class CollageLayout(val tileCount: Int) {
    TWO_COLUMNS(2),
    TWO_ROWS(2),
    THREE_COLUMNS(3),
    FULL_TOP_TWO_BOTTOM(3),
    TWO_TOP_FULL_BOTTOM(3),
    FULL_LEFT_TWO_RIGHT(3),
    TWO_LEFT_FULL_RIGHT(3),
}

data class CollageTileGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/** Geometry is normalized to the display and deliberately small/deterministic. */
object CollageLayoutGeometry {
    fun tiles(layout: CollageLayout): List<CollageTileGeometry> = when (layout) {
        CollageLayout.TWO_COLUMNS -> listOf(
            tile(0f, 0f, .5f, 1f),
            tile(.5f, 0f, 1f, 1f),
        )
        CollageLayout.TWO_ROWS -> listOf(
            tile(0f, 0f, 1f, .5f),
            tile(0f, .5f, 1f, 1f),
        )
        CollageLayout.THREE_COLUMNS -> listOf(
            tile(0f, 0f, 1f / 3f, 1f),
            tile(1f / 3f, 0f, 2f / 3f, 1f),
            tile(2f / 3f, 0f, 1f, 1f),
        )
        CollageLayout.FULL_TOP_TWO_BOTTOM -> listOf(
            tile(0f, 0f, 1f, .5f),
            tile(0f, .5f, .5f, 1f),
            tile(.5f, .5f, 1f, 1f),
        )
        CollageLayout.TWO_TOP_FULL_BOTTOM -> listOf(
            tile(0f, 0f, .5f, .5f),
            tile(.5f, 0f, 1f, .5f),
            tile(0f, .5f, 1f, 1f),
        )
        CollageLayout.FULL_LEFT_TWO_RIGHT -> listOf(
            tile(0f, 0f, .5f, 1f),
            tile(.5f, 0f, 1f, .5f),
            tile(.5f, .5f, 1f, 1f),
        )
        CollageLayout.TWO_LEFT_FULL_RIGHT -> listOf(
            tile(0f, 0f, .5f, .5f),
            tile(0f, .5f, .5f, 1f),
            tile(.5f, 0f, 1f, 1f),
        )
    }

    private fun tile(left: Float, top: Float, right: Float, bottom: Float) =
        CollageTileGeometry(left, top, right, bottom)
}

data class CollageCandidateMeta(
    val id: Long,
    val aspectRatio: Float,
    val orientation: PhotoOrientation,
    val folderKey: String,
    val captureTimeEpochMs: Long,
    val lastShownAtEpochMs: Long?,
    /** Original reservation/query order; the final deterministic tie-breaker. */
    val candidateOrder: Int,
)

data class CollageSelectionStats(
    val candidateCount: Int,
    val sameFolderCandidateCount: Int,
    val portraitCandidateCount: Int,
    val squareCandidateCount: Int,
    val landscapeCandidateCount: Int,
    val evaluatedTwoPhotoCombinations: Int,
    val evaluatedThreePhotoCombinations: Int,
    val evaluatedLayoutCount: Int,
)

data class CollageDecision(
    /** Tile order, not consumption order. The anchor can occupy any tile. */
    val photoIds: List<Long>,
    val layout: CollageLayout,
    val folderTier: Int,
    val orientationTier: Int,
    val screenCoverage: Float,
    val averageCropLoss: Float,
    val maximumCropLoss: Float,
    val timeDistanceScore: Double,
    val recentPenalty: Double,
    val decisionReason: String,
    val stats: CollageSelectionStats,
)

object PortraitCollagePolicy {
    private const val PORTRAIT_MAX_RATIO = 0.90f
    private const val LANDSCAPE_MIN_RATIO = 1.10f
    private const val AUTOMATIC_THREE_COMPLEXITY_BIAS = 0.025f
    private const val MAX_TIME_DISTANCE_HOURS = 24.0 * 30.0
    private const val RECENT_WINDOW_HOURS = 24.0
    private const val REASONABLE_AVERAGE_CROP = 0.45f
    private const val REASONABLE_MAX_CROP = 0.70f
    private const val MAX_OPTIMIZER_CANDIDATES = 12

    /** EXIF orientations 5–8 rotate the stored pixel axes by 90 degrees. */
    fun effectiveDimensions(width: Int?, height: Int?, exifOrientation: Int): Pair<Int, Int>? {
        val w = width?.takeIf { it > 0 } ?: return null
        val h = height?.takeIf { it > 0 } ?: return null
        return if (exifOrientation in 5..8) h to w else w to h
    }

    fun classify(width: Int?, height: Int?, exifOrientation: Int = 0): PhotoOrientation {
        val (w, h) = effectiveDimensions(width, height, exifOrientation)
            ?: return PhotoOrientation.SQUARE_OR_UNKNOWN
        return classify(w, h)
    }

    fun classify(width: Int, height: Int): PhotoOrientation {
        if (width <= 0 || height <= 0) return PhotoOrientation.SQUARE_OR_UNKNOWN
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio <= PORTRAIT_MAX_RATIO -> PhotoOrientation.PORTRAIT
            ratio >= LANDSCAPE_MIN_RATIO -> PhotoOrientation.LANDSCAPE
            else -> PhotoOrientation.SQUARE_OR_UNKNOWN
        }
    }

    fun aspectRatio(width: Int, height: Int): Float =
        if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    /** Fraction of source pixels discarded by a centred crop into [tileRatio]. */
    fun tileFitLoss(photoRatio: Float, tileRatio: Float): Float {
        if (!photoRatio.isFinite() || !tileRatio.isFinite() || photoRatio <= 0f || tileRatio <= 0f) {
            return 1f
        }
        val retained = if (photoRatio >= tileRatio) {
            tileRatio / photoRatio
        } else {
            photoRatio / tileRatio
        }
        return 1f - retained.coerceIn(0f, 1f)
    }

    /**
     * Deterministically evaluate every two/three-photo combination from the bounded pool.
     * Folder locality and orientation class are hard lexicographic priorities; visual fit
     * then decides among presentations in the same tier.
     */
    fun chooseBestPresentation(
        mode: PortraitCollageMode,
        screenWidth: Int,
        screenHeight: Int,
        anchor: CollageCandidateMeta,
        candidates: List<CollageCandidateMeta>,
        maxPhotos: Int,
        nowEpochMs: Long,
    ): CollageDecision? {
        if (mode == PortraitCollageMode.OFF || maxPhotos < 2 || screenWidth <= 0 || screenHeight <= 0) {
            return null
        }
        val bounded = candidates.asSequence()
            .filter { it.id != anchor.id }
            .distinctBy { it.id }
            .take(MAX_OPTIMIZER_CANDIDATES)
            .toList()
        if (bounded.isEmpty()) return null

        val statsBuilder = MutableStats(
            candidateCount = bounded.size,
            sameFolderCandidateCount = bounded.count { it.folderKey == anchor.folderKey },
            portraitCandidateCount = bounded.count { it.orientation == PhotoOrientation.PORTRAIT },
            squareCandidateCount = bounded.count { it.orientation == PhotoOrientation.SQUARE_OR_UNKNOWN },
            landscapeCandidateCount = bounded.count { it.orientation == PhotoOrientation.LANDSCAPE },
        )
        val two = mutableListOf<ScoredPresentation>()
        val three = mutableListOf<ScoredPresentation>()

        if (maxPhotos >= 2) {
            bounded.forEach { candidate ->
                statsBuilder.evaluatedTwoPhotoCombinations++
                evaluateCombination(
                    photos = listOf(anchor, candidate),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    nowEpochMs = nowEpochMs,
                    evaluatedLayout = { statsBuilder.evaluatedLayoutCount++ },
                )?.let(two::add)
            }
        }
        if (maxPhotos >= 3 && mode != PortraitCollageMode.ALWAYS_TWO) {
            for (first in 0 until bounded.lastIndex) {
                for (second in first + 1 until bounded.size) {
                    statsBuilder.evaluatedThreePhotoCombinations++
                    evaluateCombination(
                        photos = listOf(anchor, bounded[first], bounded[second]),
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        nowEpochMs = nowEpochMs,
                        evaluatedLayout = { statsBuilder.evaluatedLayoutCount++ },
                    )?.let(three::add)
                }
            }
        }

        val ranking = Comparator<ScoredPresentation> { a, b ->
            comparePresentation(a, b, automatic = false)
        }
        val bestTwo = two.minWithOrNull(ranking)
        val bestThree = three.minWithOrNull(ranking)
        val selected = when (mode) {
            PortraitCollageMode.OFF -> null
            PortraitCollageMode.ALWAYS_TWO -> bestTwo
            PortraitCollageMode.PREFER_THREE -> when {
                bestThree != null && isReasonable(bestThree) -> bestThree
                else -> bestTwo
            }
            PortraitCollageMode.AUTOMATIC -> when {
                bestTwo == null -> bestThree
                bestThree == null -> bestTwo
                comparePresentation(bestThree, bestTwo, automatic = true) < 0 -> bestThree
                else -> bestTwo
            }
        } ?: return null

        val alternative = when (selected.photos.size) {
            2 -> bestThree
            else -> bestTwo
        }
        val reason = decisionReason(mode, selected, alternative, bestThree)
        return CollageDecision(
            photoIds = selected.photos.map { it.id },
            layout = selected.layout,
            folderTier = selected.folderTier,
            orientationTier = selected.orientationTier,
            screenCoverage = selected.screenCoverage,
            averageCropLoss = selected.averageCropLoss,
            maximumCropLoss = selected.maximumCropLoss,
            timeDistanceScore = selected.timeDistanceScore,
            recentPenalty = selected.recentPenalty,
            decisionReason = reason,
            stats = statsBuilder.freeze(),
        )
    }

    /**
     * Compatibility adapter for the previous API. New production code uses
     * [chooseBestPresentation], but keeping this avoids churn for callers/tests that only
     * need equal-column portrait behaviour.
     */
    fun chooseLayout(
        mode: PortraitCollageMode,
        screenWidth: Int,
        screenHeight: Int,
        portraitRatios: List<Float>,
        maxPhotos: Int,
    ): CollageLayout? {
        if (portraitRatios.size < 2) return null
        val metas = portraitRatios.mapIndexed { index, ratio ->
            CollageCandidateMeta(
                id = index.toLong() + 1L,
                aspectRatio = ratio,
                orientation = PhotoOrientation.PORTRAIT,
                folderKey = "compat",
                captureTimeEpochMs = 0L,
                lastShownAtEpochMs = null,
                candidateOrder = index - 1,
            )
        }
        return chooseBestPresentation(
            mode = mode,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            anchor = metas.first(),
            candidates = metas.drop(1),
            maxPhotos = maxPhotos,
            nowEpochMs = 0L,
        )?.layout
    }

    /** Candidate ordering helper retained for bounded Room query results. */
    fun candidateScore(anchorTime: Long, candidate: DisplayPhoto, now: Long): Double {
        val candidateTime = candidate.dateTakenEpochMs ?: candidate.fileModifiedEpochMs
        return timeDistanceHours(anchorTime, candidateTime) + recentPenalty(candidate.lastShownAtEpochMs, now) * 5.0
    }

    private data class MutableStats(
        val candidateCount: Int,
        val sameFolderCandidateCount: Int,
        val portraitCandidateCount: Int,
        val squareCandidateCount: Int,
        val landscapeCandidateCount: Int,
        var evaluatedTwoPhotoCombinations: Int = 0,
        var evaluatedThreePhotoCombinations: Int = 0,
        var evaluatedLayoutCount: Int = 0,
    ) {
        fun freeze() = CollageSelectionStats(
            candidateCount,
            sameFolderCandidateCount,
            portraitCandidateCount,
            squareCandidateCount,
            landscapeCandidateCount,
            evaluatedTwoPhotoCombinations,
            evaluatedThreePhotoCombinations,
            evaluatedLayoutCount,
        )
    }

    private data class ScoredPresentation(
        val photos: List<CollageCandidateMeta>,
        val layout: CollageLayout,
        val folderTier: Int,
        val orientationTier: Int,
        val screenCoverage: Float,
        val emptyScreenFraction: Float,
        val averageCropLoss: Float,
        val maximumCropLoss: Float,
        val timeDistanceScore: Double,
        val recentPenalty: Double,
        val tieOrder: List<Int>,
    )

    private fun evaluateCombination(
        photos: List<CollageCandidateMeta>,
        screenWidth: Int,
        screenHeight: Int,
        nowEpochMs: Long,
        evaluatedLayout: () -> Unit,
    ): ScoredPresentation? {
        val layouts = compatibleLayouts(photos.map { it.orientation })
        val permutations = permutations(photos)
        var best: ScoredPresentation? = null
        for (layout in layouts) {
            val geometry = CollageLayoutGeometry.tiles(layout)
            if (geometry.size != photos.size) continue
            for (ordered in permutations) {
                evaluatedLayout()
                val losses = ordered.zip(geometry).map { (photo, tile) ->
                    val tileRatio = (screenWidth.toFloat() * tile.width) /
                        (screenHeight.toFloat() * tile.height).coerceAtLeast(0.0001f)
                    tileFitLoss(photo.aspectRatio, tileRatio)
                }
                val coverage = geometry.sumOf { it.area.toDouble() }.toFloat().coerceIn(0f, 1f)
                val anchor = photos.first()
                val companions = photos.drop(1)
                val scored = ScoredPresentation(
                    photos = ordered,
                    layout = layout,
                    folderTier = if (photos.all { it.folderKey == anchor.folderKey }) 0 else 1,
                    orientationTier = orientationTier(photos.map { it.orientation }),
                    screenCoverage = coverage,
                    emptyScreenFraction = (1f - coverage).coerceIn(0f, 1f),
                    averageCropLoss = losses.average().toFloat(),
                    maximumCropLoss = losses.maxOrNull() ?: 1f,
                    timeDistanceScore = companions.map {
                        timeDistanceHours(anchor.captureTimeEpochMs, it.captureTimeEpochMs)
                    }.averageOrZero(),
                    recentPenalty = companions.map {
                        recentPenalty(it.lastShownAtEpochMs, nowEpochMs)
                    }.averageOrZero(),
                    tieOrder = companions.map { it.candidateOrder }.sorted(),
                )
                if (best == null || comparePresentation(scored, best, automatic = false) < 0) {
                    best = scored
                }
            }
        }
        return best
    }

    private fun compatibleLayouts(orientations: List<PhotoOrientation>): List<CollageLayout> {
        if (orientations.size == 2) {
            return if (orientations.all { it == PhotoOrientation.PORTRAIT }) {
                listOf(CollageLayout.TWO_COLUMNS)
            } else {
                listOf(CollageLayout.TWO_COLUMNS, CollageLayout.TWO_ROWS)
            }
        }
        if (orientations.size != 3) return emptyList()
        val portrait = orientations.count { it == PhotoOrientation.PORTRAIT }
        val landscape = orientations.count { it == PhotoOrientation.LANDSCAPE }
        val square = orientations.size - portrait - landscape
        return when {
            portrait == 3 -> listOf(CollageLayout.THREE_COLUMNS)
            landscape == 3 -> listOf(
                CollageLayout.FULL_TOP_TWO_BOTTOM,
                CollageLayout.TWO_TOP_FULL_BOTTOM,
            )
            square > 0 -> listOf(
                CollageLayout.THREE_COLUMNS,
                CollageLayout.FULL_TOP_TWO_BOTTOM,
                CollageLayout.TWO_TOP_FULL_BOTTOM,
                CollageLayout.FULL_LEFT_TWO_RIGHT,
                CollageLayout.TWO_LEFT_FULL_RIGHT,
            )
            else -> listOf(
                CollageLayout.FULL_TOP_TWO_BOTTOM,
                CollageLayout.TWO_TOP_FULL_BOTTOM,
                CollageLayout.FULL_LEFT_TWO_RIGHT,
                CollageLayout.TWO_LEFT_FULL_RIGHT,
            )
        }
    }

    private fun orientationTier(orientations: List<PhotoOrientation>): Int {
        val portrait = orientations.count { it == PhotoOrientation.PORTRAIT }
        val landscape = orientations.count { it == PhotoOrientation.LANDSCAPE }
        val square = orientations.size - portrait - landscape
        return when {
            portrait == orientations.size -> 0
            landscape == 0 && portrait > 0 && square > 0 -> 1
            landscape > 0 && (portrait > 0 || square > 0) -> 2
            square == orientations.size -> 2
            landscape == orientations.size -> 3
            else -> 2
        }
    }

    private fun comparePresentation(a: ScoredPresentation, b: ScoredPresentation, automatic: Boolean): Int {
        compareValues(a.folderTier, b.folderTier).takeIf { it != 0 }?.let { return it }
        compareValues(a.orientationTier, b.orientationTier).takeIf { it != 0 }?.let { return it }
        compareValues(quantize(a.emptyScreenFraction), quantize(b.emptyScreenFraction))
            .takeIf { it != 0 }?.let { return it }

        if (automatic && a.photos.size != b.photos.size) {
            val aVisual = a.averageCropLoss + a.maximumCropLoss * .25f +
                if (a.photos.size == 3) AUTOMATIC_THREE_COMPLEXITY_BIAS else 0f
            val bVisual = b.averageCropLoss + b.maximumCropLoss * .25f +
                if (b.photos.size == 3) AUTOMATIC_THREE_COMPLEXITY_BIAS else 0f
            compareValues(quantize(aVisual), quantize(bVisual)).takeIf { it != 0 }?.let { return it }
        } else {
            compareValues(quantize(a.averageCropLoss), quantize(b.averageCropLoss))
                .takeIf { it != 0 }?.let { return it }
            compareValues(quantize(a.maximumCropLoss), quantize(b.maximumCropLoss))
                .takeIf { it != 0 }?.let { return it }
        }

        compareValues(quantizeDouble(a.timeDistanceScore), quantizeDouble(b.timeDistanceScore))
            .takeIf { it != 0 }?.let { return it }
        compareValues(quantizeDouble(a.recentPenalty), quantizeDouble(b.recentPenalty))
            .takeIf { it != 0 }?.let { return it }
        compareOrder(a.tieOrder, b.tieOrder).takeIf { it != 0 }?.let { return it }
        compareValues(a.layout.ordinal, b.layout.ordinal).takeIf { it != 0 }?.let { return it }
        return compareLongOrder(a.photos.map { it.id }, b.photos.map { it.id })
    }

    private fun isReasonable(presentation: ScoredPresentation): Boolean =
        presentation.screenCoverage >= .98f &&
            presentation.averageCropLoss <= REASONABLE_AVERAGE_CROP &&
            presentation.maximumCropLoss <= REASONABLE_MAX_CROP

    private fun decisionReason(
        mode: PortraitCollageMode,
        selected: ScoredPresentation,
        alternative: ScoredPresentation?,
        bestThree: ScoredPresentation?,
    ): String {
        if (alternative != null && selected.folderTier < alternative.folderTier) return "SAME_FOLDER_PRIORITY"
        if (alternative != null && selected.orientationTier < alternative.orientationTier && selected.orientationTier == 0) {
            return "PORTRAIT_ONLY_PRIORITY"
        }
        if (selected.orientationTier == 2 && selected.folderTier == 0) return "MIXED_ORIENTATION_FALLBACK"
        return when (mode) {
            PortraitCollageMode.OFF -> "INSUFFICIENT_COMPATIBLE_PHOTOS"
            PortraitCollageMode.ALWAYS_TWO -> "ALWAYS_TWO"
            PortraitCollageMode.PREFER_THREE -> if (selected.photos.size == 3) {
                "PREFER_THREE"
            } else if (bestThree == null) {
                "ONLY_TWO_AVAILABLE"
            } else {
                "TWO_LOWER_VISUAL_LOSS"
            }
            PortraitCollageMode.AUTOMATIC -> if (selected.photos.size == 3) {
                "THREE_LOWER_VISUAL_LOSS"
            } else {
                "TWO_LOWER_VISUAL_LOSS"
            }
        }
    }

    private fun timeDistanceHours(anchorTime: Long, candidateTime: Long): Double {
        if (anchorTime <= 0L || candidateTime <= 0L) return MAX_TIME_DISTANCE_HOURS
        return (abs(candidateTime - anchorTime).toDouble() / 3_600_000.0)
            .coerceAtMost(MAX_TIME_DISTANCE_HOURS)
    }

    private fun recentPenalty(lastShownAtEpochMs: Long?, nowEpochMs: Long): Double {
        val shown = lastShownAtEpochMs ?: return 0.0
        if (nowEpochMs <= 0L) return 0.0
        val ageHours = (nowEpochMs - shown).coerceAtLeast(0L).toDouble() / 3_600_000.0
        return if (ageHours < RECENT_WINDOW_HOURS) RECENT_WINDOW_HOURS - ageHours else 0.0
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun quantize(value: Float): Int = (value.coerceAtLeast(0f) * 10_000f).roundToInt()
    private fun quantizeDouble(value: Double): Long = (value.coerceAtLeast(0.0) * 100.0).roundToLong()

    private fun compareOrder(a: List<Int>, b: List<Int>): Int {
        val size = minOf(a.size, b.size)
        for (index in 0 until size) {
            compareValues(a[index], b[index]).takeIf { it != 0 }?.let { return it }
        }
        return compareValues(a.size, b.size)
    }

    private fun compareLongOrder(a: List<Long>, b: List<Long>): Int {
        val size = minOf(a.size, b.size)
        for (index in 0 until size) {
            compareValues(a[index], b[index]).takeIf { it != 0 }?.let { return it }
        }
        return compareValues(a.size, b.size)
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val result = ArrayList<List<T>>()
        items.forEachIndexed { index, item ->
            val remainder = items.toMutableList().also { it.removeAt(index) }
            permutations(remainder).forEach { tail -> result += listOf(item) + tail }
        }
        return result
    }
}
