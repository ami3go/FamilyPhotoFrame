package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.PortraitCollageMode
import kotlin.math.abs

/** Pure orientation and layout scoring used by collage playback. */
enum class PhotoOrientation { PORTRAIT, LANDSCAPE, SQUARE_OR_UNKNOWN }

/**
 * Small deterministic layout set. Tile order is significant and is documented by
 * [PortraitCollagePolicy.tileRatios]. Mixed layouts intentionally stay finite rather
 * than becoming an arbitrary masonry engine.
 */
enum class CollageLayout {
    TWO_COLUMNS,
    TWO_ROWS,
    THREE_COLUMNS,
    FULL_LEFT_STACK_RIGHT,
    STACK_LEFT_FULL_RIGHT,
    FULL_TOP_SPLIT_BOTTOM,
    SPLIT_TOP_FULL_BOTTOM,
}

/** Metadata-only input to the optimizer. No bitmap or Android UI type is referenced. */
data class CollagePhotoCandidate(
    val id: Long,
    val aspectRatio: Float,
    val orientation: PhotoOrientation,
    val folderKey: String,
    val captureTimeEpochMs: Long,
    val lastShownAtEpochMs: Long? = null,
    /** Existing reservation/candidate order. Lower values win the final tie-break. */
    val order: Int,
)

/** Complete deterministic optimizer result, including diagnostics-ready metrics. */
data class CollageSelection(
    /** Photo ids in layout-tile order; this may place the anchor away from tile zero. */
    val photoIds: List<Long>,
    val layout: CollageLayout,
    val folderTier: Int,
    val orientationTier: Int,
    val screenCoverage: Float,
    val averageCropLoss: Float,
    val maximumCropLoss: Float,
    val emptyScreenFraction: Float,
    val timeDistanceScore: Double,
    val recentPenalty: Double,
    val visualLoss: Float,
    val evaluatedTwoPhotoCombinations: Int,
    val evaluatedThreePhotoCombinations: Int,
    val evaluatedLayoutCount: Int,
    val decisionReason: String,
)

object PortraitCollagePolicy {
    private const val PORTRAIT_MAX_RATIO = 0.90f
    private const val LANDSCAPE_MIN_RATIO = 1.10f
    private const val MAX_CANDIDATES = 12
    private const val MAX_SUPPORTED_CROP_LOSS = 0.78f
    private const val MAX_PREFER_THREE_AVERAGE_CROP = 0.55f
    private const val MAX_PREFER_THREE_INDIVIDUAL_CROP = 0.72f
    private const val THREE_PHOTO_COMPLEXITY_BIAS = 0.02f
    private const val FLOAT_TIE_EPSILON = 0.005f

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

    /**
     * Fraction of source content discarded by a centre-crop into [tileRatio].
     * 0 is an ideal fit; values approaching 1 indicate destructive cropping.
     */
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
     * Destination aspect ratios for every tile in [layout], expressed from the complete
     * screen aspect ratio. Gap pixels are deliberately omitted from policy scoring: gaps
     * are small presentation decoration, while the optimizer must stay resolution- and
     * Compose-independent.
     */
    fun tileRatios(layout: CollageLayout, screenRatio: Float): List<Float> {
        val r = screenRatio.coerceAtLeast(0.01f)
        return when (layout) {
            CollageLayout.TWO_COLUMNS -> listOf(r / 2f, r / 2f)
            CollageLayout.TWO_ROWS -> listOf(r * 2f, r * 2f)
            CollageLayout.THREE_COLUMNS -> listOf(r / 3f, r / 3f, r / 3f)
            // Full-height half-width tile, then two half-width/half-height tiles.
            CollageLayout.FULL_LEFT_STACK_RIGHT -> listOf(r / 2f, r, r)
            // Two stacked half-width/half-height tiles, then full-height half-width tile.
            CollageLayout.STACK_LEFT_FULL_RIGHT -> listOf(r, r, r / 2f)
            // Full-width half-height tile, then two half-width/half-height tiles.
            CollageLayout.FULL_TOP_SPLIT_BOTTOM -> listOf(r * 2f, r, r)
            // Two half-width/half-height tiles, then full-width half-height tile.
            CollageLayout.SPLIT_TOP_FULL_BOTTOM -> listOf(r, r, r * 2f)
        }
    }

    fun compatibleLayouts(photoCount: Int): List<CollageLayout> = when (photoCount) {
        2 -> listOf(CollageLayout.TWO_COLUMNS, CollageLayout.TWO_ROWS)
        3 -> listOf(
            CollageLayout.THREE_COLUMNS,
            CollageLayout.FULL_LEFT_STACK_RIGHT,
            CollageLayout.STACK_LEFT_FULL_RIGHT,
            CollageLayout.FULL_TOP_SPLIT_BOTTOM,
            CollageLayout.SPLIT_TOP_FULL_BOTTOM,
        )
        else -> emptyList()
    }

    /**
     * Evaluate every eligible two-/three-photo combination and every supported layout.
     * Folder locality and orientation are lexicographic tiers, so a marginal crop-score
     * improvement can never pull an unrelated folder into an otherwise good same-folder
     * presentation. Candidate order is used only after all visual/time/recent metrics tie.
     */
    fun chooseBestPresentation(
        mode: PortraitCollageMode,
        screenWidth: Int,
        screenHeight: Int,
        anchor: CollagePhotoCandidate,
        candidates: List<CollagePhotoCandidate>,
        maxPhotos: Int,
        nowEpochMs: Long,
    ): CollageSelection? {
        if (mode == PortraitCollageMode.OFF || screenWidth <= 0 || screenHeight <= 0) return null
        val allowedPhotos = maxPhotos.coerceIn(2, 3)
        val pool = candidates.asSequence()
            .filter { it.id != anchor.id }
            .distinctBy { it.id }
            .take(MAX_CANDIDATES)
            .toList()
        if (pool.isEmpty()) return null

        val screenRatio = aspectRatio(screenWidth, screenHeight)
        val scoredTwo = mutableListOf<ScoredPresentation>()
        val scoredThree = mutableListOf<ScoredPresentation>()
        var evaluatedLayouts = 0

        if (allowedPhotos >= 2) {
            pool.forEach { candidate ->
                val result = scoreCombination(
                    anchor = anchor,
                    photos = listOf(anchor, candidate),
                    screenRatio = screenRatio,
                    nowEpochMs = nowEpochMs,
                )
                evaluatedLayouts += result.evaluatedLayouts
                scoredTwo += result.presentations
            }
        }

        if (allowedPhotos >= 3 && pool.size >= 2) {
            for (i in 0 until pool.lastIndex) {
                for (j in i + 1 until pool.size) {
                    val result = scoreCombination(
                        anchor = anchor,
                        photos = listOf(anchor, pool[i], pool[j]),
                        screenRatio = screenRatio,
                        nowEpochMs = nowEpochMs,
                    )
                    evaluatedLayouts += result.evaluatedLayouts
                    scoredThree += result.presentations
                }
            }
        }

        val bestTwo = scoredTwo.minWithOrNull(::comparePresentation)
        val bestThree = scoredThree.minWithOrNull(::comparePresentation)
        val chosen = when (mode) {
            PortraitCollageMode.OFF -> null
            PortraitCollageMode.ALWAYS_TWO -> bestTwo
            PortraitCollageMode.PREFER_THREE -> choosePreferThree(bestTwo, bestThree)
            PortraitCollageMode.AUTOMATIC -> chooseAutomatic(bestTwo, bestThree)
        } ?: return null

        val twoCombinationCount = pool.size.takeIf { allowedPhotos >= 2 } ?: 0
        val threeCombinationCount = if (allowedPhotos >= 3) pool.size * (pool.size - 1) / 2 else 0
        val reason = decisionReason(mode, chosen, bestTwo, bestThree)
        return chosen.toSelection(
            evaluatedTwo = twoCombinationCount,
            evaluatedThree = threeCombinationCount,
            evaluatedLayouts = evaluatedLayouts,
            decisionReason = reason,
        )
    }

    /**
     * Compatibility wrapper retained for callers/tests written for the old portrait-only
     * policy. New slideshow preparation should use [chooseBestPresentation].
     */
    fun chooseLayout(
        mode: PortraitCollageMode,
        screenWidth: Int,
        screenHeight: Int,
        portraitRatios: List<Float>,
        maxPhotos: Int,
    ): CollageLayout? {
        if (portraitRatios.size < 2 || mode == PortraitCollageMode.OFF) return null
        val anchor = CollagePhotoCandidate(
            id = 0,
            aspectRatio = portraitRatios.first(),
            orientation = PhotoOrientation.PORTRAIT,
            folderKey = "compat",
            captureTimeEpochMs = 0,
            order = 0,
        )
        val candidates = portraitRatios.drop(1).mapIndexed { index, ratio ->
            CollagePhotoCandidate(
                id = (index + 1).toLong(),
                aspectRatio = ratio,
                orientation = PhotoOrientation.PORTRAIT,
                folderKey = "compat",
                captureTimeEpochMs = 0,
                order = index + 1,
            )
        }
        return chooseBestPresentation(
            mode = mode,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            anchor = anchor,
            candidates = candidates,
            maxPhotos = maxPhotos,
            nowEpochMs = 0,
        )?.layout
    }

    /** Existing lightweight ordering helper retained for DAO candidate windows. */
    fun candidateScore(anchorTime: Long, candidate: DisplayPhoto, now: Long): Double {
        val candidateTime = candidate.dateTakenEpochMs ?: candidate.fileModifiedEpochMs
        val timeDistanceHours = abs(candidateTime - anchorTime).toDouble() / 3_600_000.0
        val recentPenalty = recentPenalty(candidate.lastShownAtEpochMs, now)
        return recentPenalty + timeDistanceHours.coerceAtMost(24.0 * 30.0)
    }

    private data class CombinationScore(
        val presentations: List<ScoredPresentation>,
        val evaluatedLayouts: Int,
    )

    private data class ScoredPresentation(
        val photos: List<CollagePhotoCandidate>,
        val layout: CollageLayout,
        val folderTier: Int,
        val orientationTier: Int,
        val emptyScreenFraction: Float,
        val averageCropLoss: Float,
        val maximumCropLoss: Float,
        val timeDistanceScore: Double,
        val recentPenalty: Double,
        val reservationOrder: List<Int>,
        val permutationOrder: List<Int>,
    ) {
        val screenCoverage: Float get() = 1f - emptyScreenFraction
        val visualLoss: Float
            get() = averageCropLoss + maximumCropLoss * 0.25f + emptyScreenFraction * 2f

        fun toSelection(
            evaluatedTwo: Int,
            evaluatedThree: Int,
            evaluatedLayouts: Int,
            decisionReason: String,
        ) = CollageSelection(
            photoIds = photos.map { it.id },
            layout = layout,
            folderTier = folderTier,
            orientationTier = orientationTier,
            screenCoverage = screenCoverage,
            averageCropLoss = averageCropLoss,
            maximumCropLoss = maximumCropLoss,
            emptyScreenFraction = emptyScreenFraction,
            timeDistanceScore = timeDistanceScore,
            recentPenalty = recentPenalty,
            visualLoss = visualLoss,
            evaluatedTwoPhotoCombinations = evaluatedTwo,
            evaluatedThreePhotoCombinations = evaluatedThree,
            evaluatedLayoutCount = evaluatedLayouts,
            decisionReason = decisionReason,
        )
    }

    private fun scoreCombination(
        anchor: CollagePhotoCandidate,
        photos: List<CollagePhotoCandidate>,
        screenRatio: Float,
        nowEpochMs: Long,
    ): CombinationScore {
        val folderTier = if (photos.all { it.folderKey == anchor.folderKey }) 0 else 1
        val orientationTier = orientationTier(photos)
        val reservationOrder = photos.asSequence()
            .filter { it.id != anchor.id }
            .map { it.order }
            .sorted()
            .toList()
        val timeDistance = photos.asSequence()
            .filter { it.id != anchor.id }
            .map {
                abs(it.captureTimeEpochMs - anchor.captureTimeEpochMs).toDouble() /
                    3_600_000.0
            }
            .map { it.coerceAtMost(24.0 * 30.0) }
            .toList()
            .averageOrZero()
        val recent = photos.asSequence()
            .filter { it.id != anchor.id }
            .map { recentPenalty(it.lastShownAtEpochMs, nowEpochMs) }
            .toList()
            .averageOrZero()

        var evaluated = 0
        val scored = mutableListOf<ScoredPresentation>()
        val layouts = compatibleLayouts(photos.size)
        for (permutation in permutations(photos)) {
            val permutationOrder = permutation.map { photos.indexOfFirst { original -> original.id == it.id } }
            for (layout in layouts) {
                evaluated++
                val ratios = tileRatios(layout, screenRatio)
                val losses = permutation.indices.map { index ->
                    tileFitLoss(permutation[index].aspectRatio, ratios[index])
                }
                val maxLoss = losses.maxOrNull() ?: 1f
                if (maxLoss > MAX_SUPPORTED_CROP_LOSS) continue
                val averageLoss = losses.average().toFloat()
                // Every supported deterministic layout covers the complete screen. The
                // field remains explicit so future layouts with intentional breathing
                // room cannot accidentally bypass the empty-area penalty.
                val emptyFraction = 0f
                scored += ScoredPresentation(
                    photos = permutation,
                    layout = layout,
                    folderTier = folderTier,
                    orientationTier = orientationTier,
                    emptyScreenFraction = emptyFraction,
                    averageCropLoss = averageLoss,
                    maximumCropLoss = maxLoss,
                    timeDistanceScore = timeDistance,
                    recentPenalty = recent,
                    reservationOrder = reservationOrder,
                    permutationOrder = permutationOrder,
                )
            }
        }
        return CombinationScore(scored, evaluated)
    }

    private fun choosePreferThree(
        bestTwo: ScoredPresentation?,
        bestThree: ScoredPresentation?,
    ): ScoredPresentation? {
        if (bestThree == null) return bestTwo
        if (bestTwo == null) return bestThree.takeIf(::reasonableThree)
        // Folder locality remains stronger than the mode preference.
        if (bestThree.folderTier > bestTwo.folderTier) return bestTwo
        return if (reasonableThree(bestThree)) bestThree else bestTwo
    }

    private fun reasonableThree(presentation: ScoredPresentation): Boolean =
        presentation.averageCropLoss <= MAX_PREFER_THREE_AVERAGE_CROP &&
            presentation.maximumCropLoss <= MAX_PREFER_THREE_INDIVIDUAL_CROP

    private fun chooseAutomatic(
        bestTwo: ScoredPresentation?,
        bestThree: ScoredPresentation?,
    ): ScoredPresentation? = when {
        bestTwo == null -> bestThree
        bestThree == null -> bestTwo
        compareAutomatic(bestTwo, bestThree) <= 0 -> bestTwo
        else -> bestThree
    }

    /** Same lexicographic priorities as the normal comparator plus a tiny 3-photo bias. */
    private fun compareAutomatic(a: ScoredPresentation, b: ScoredPresentation): Int {
        compareValues(a.folderTier, b.folderTier).takeIf { it != 0 }?.let { return it }
        compareValues(a.orientationTier, b.orientationTier).takeIf { it != 0 }?.let { return it }
        compareFloat(a.emptyScreenFraction, b.emptyScreenFraction).takeIf { it != 0 }?.let { return it }
        val aCrop = a.averageCropLoss + if (a.photos.size == 3) THREE_PHOTO_COMPLEXITY_BIAS else 0f
        val bCrop = b.averageCropLoss + if (b.photos.size == 3) THREE_PHOTO_COMPLEXITY_BIAS else 0f
        compareFloat(aCrop, bCrop).takeIf { it != 0 }?.let { return it }
        return comparePresentation(a, b)
    }

    private fun comparePresentation(a: ScoredPresentation, b: ScoredPresentation): Int {
        compareValues(a.folderTier, b.folderTier).takeIf { it != 0 }?.let { return it }
        compareValues(a.orientationTier, b.orientationTier).takeIf { it != 0 }?.let { return it }
        compareFloat(a.emptyScreenFraction, b.emptyScreenFraction).takeIf { it != 0 }?.let { return it }
        compareFloat(a.averageCropLoss, b.averageCropLoss).takeIf { it != 0 }?.let { return it }
        compareFloat(a.maximumCropLoss, b.maximumCropLoss).takeIf { it != 0 }?.let { return it }
        compareDouble(a.timeDistanceScore, b.timeDistanceScore).takeIf { it != 0 }?.let { return it }
        compareDouble(a.recentPenalty, b.recentPenalty).takeIf { it != 0 }?.let { return it }
        compareIntLists(a.reservationOrder, b.reservationOrder).takeIf { it != 0 }?.let { return it }
        compareValues(a.layout.ordinal, b.layout.ordinal).takeIf { it != 0 }?.let { return it }
        return compareIntLists(a.permutationOrder, b.permutationOrder)
    }

    private fun orientationTier(photos: List<CollagePhotoCandidate>): Int {
        val portraitCount = photos.count { it.orientation == PhotoOrientation.PORTRAIT }
        val landscapeCount = photos.count { it.orientation == PhotoOrientation.LANDSCAPE }
        return when {
            portraitCount == photos.size -> 0
            landscapeCount == 0 -> 1
            portraitCount > 0 -> 2
            else -> 3
        }
    }

    private fun decisionReason(
        mode: PortraitCollageMode,
        chosen: ScoredPresentation,
        bestTwo: ScoredPresentation?,
        bestThree: ScoredPresentation?,
    ): String = when {
        mode == PortraitCollageMode.ALWAYS_TWO -> "ALWAYS_TWO"
        mode == PortraitCollageMode.PREFER_THREE && chosen.photos.size == 3 -> "PREFER_THREE"
        chosen.folderTier == 0 && chosen.orientationTier == 0 -> "PORTRAIT_ONLY_PRIORITY"
        chosen.folderTier == 0 && chosen.orientationTier >= 2 -> "MIXED_ORIENTATION_FALLBACK"
        chosen.folderTier == 0 -> "SAME_FOLDER_PRIORITY"
        mode == PortraitCollageMode.AUTOMATIC && chosen.photos.size == 2 && bestThree != null ->
            "TWO_LOWER_VISUAL_LOSS"
        mode == PortraitCollageMode.AUTOMATIC && chosen.photos.size == 3 && bestTwo != null ->
            "THREE_LOWER_VISUAL_LOSS"
        chosen.photos.size == 2 -> "ONLY_TWO_AVAILABLE"
        else -> "MIXED_ORIENTATION_FALLBACK"
    }

    private fun recentPenalty(lastShownAtEpochMs: Long?, nowEpochMs: Long): Double =
        lastShownAtEpochMs?.let { shown ->
            val ageHours = (nowEpochMs - shown).coerceAtLeast(0L).toDouble() / 3_600_000.0
            if (ageHours < 24.0) (24.0 - ageHours) * 5.0 else 0.0
        } ?: 0.0

    private fun compareFloat(a: Float, b: Float): Int =
        if (abs(a - b) <= FLOAT_TIE_EPSILON) 0 else a.compareTo(b)

    private fun compareDouble(a: Double, b: Double): Int =
        if (abs(a - b) <= 0.01) 0 else a.compareTo(b)

    private fun compareIntLists(a: List<Int>, b: List<Int>): Int {
        val common = minOf(a.size, b.size)
        for (index in 0 until common) {
            val compared = a[index].compareTo(b[index])
            if (compared != 0) return compared
        }
        return a.size.compareTo(b.size)
    }

    private fun permutations(photos: List<CollagePhotoCandidate>): List<List<CollagePhotoCandidate>> = when (photos.size) {
        2 -> listOf(
            listOf(photos[0], photos[1]),
            listOf(photos[1], photos[0]),
        )
        3 -> listOf(
            listOf(photos[0], photos[1], photos[2]),
            listOf(photos[0], photos[2], photos[1]),
            listOf(photos[1], photos[0], photos[2]),
            listOf(photos[1], photos[2], photos[0]),
            listOf(photos[2], photos[0], photos[1]),
            listOf(photos[2], photos[1], photos[0]),
        )
        else -> emptyList()
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
