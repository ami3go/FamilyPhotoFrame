package com.example.familyphotoframe.ui.render

/**
 * Motion strength for three-portrait-panel collages (task §12).
 *
 * The public setting is a simple on/off switch; the profile exists so amplitude can be
 * reduced for accessibility (task §13) without a second code path. [SUBTLE] is the
 * initial implementation (task §12).
 */
enum class PanelMotionProfile(val amplitude: Float) {
    OFF(0f),

    /** Task §13: system animations reduced but not disabled. */
    REDUCED(0.5f),
    SUBTLE(1f),
    NORMAL(1.6f),
}

/**
 * The eight safe portrait-panel presets required by task §8.
 *
 * [dirX]/[dirY] are unit pan directions in panel-normalised coordinates; [zoom] is the
 * scale delta applied across the slide (positive zooms in, negative zooms out).
 */
enum class PanelMotionPreset(val dirX: Float, val dirY: Float, val zoom: Float) {
    ZOOM_IN(0f, 0f, 1f),
    ZOOM_OUT(0f, 0f, -1f),
    PAN_UP(0f, -1f, 0.35f),
    PAN_DOWN(0f, 1f, 0.35f),
    PAN_LEFT_TO_RIGHT(1f, 0f, 0.35f),
    PAN_RIGHT_TO_LEFT(-1f, 0f, 0.35f),
    DIAGONAL_UP(0.7f, -0.7f, 0.5f),
    DIAGONAL_DOWN(-0.7f, 0.7f, 0.5f),
}

/**
 * Immutable motion path for one panel (task §7).
 *
 * Translations are fractions of the rendered panel size, so the renderer multiplies by
 * pixel width/height and nothing here depends on Compose or Android types.
 */
data class PanelMotionPath(
    val preset: PanelMotionPreset,
    val startScale: Float,
    val endScale: Float,
    val startTranslateXFraction: Float,
    val endTranslateXFraction: Float,
    val startTranslateYFraction: Float,
    val endTranslateYFraction: Float,
    val durationMillis: Int,
    val profile: PanelMotionProfile,
    val seed: Long,
) {
    /** Smallest scale anywhere on the path; the crop-safety bound is derived from it. */
    val minScale: Float get() = minOf(startScale, endScale)
}

/** One sampled frame of a [PanelMotionPath]. */
data class PanelMotionFrame(
    val scale: Float,
    val translateXFraction: Float,
    val translateYFraction: Float,
)

/**
 * Geometry for the three-portrait-panel collage motion (task §4-§8).
 *
 * Pure arithmetic with no Compose or Android dependencies so it is unit-testable and can
 * be read inside a `graphicsLayer` lambda, keeping the animation GPU-composited with no
 * per-frame recomposition or allocation (task §14).
 *
 * ## Crop-safety invariant (task §6)
 *
 * Panels render with centre-crop, so at scale 1 the image already covers the panel
 * exactly. Zooming to scale `s` adds `(s - 1) / 2` of overscan on each side, which is the
 * furthest the image can pan before an edge appears.
 *
 * Paths are therefore built so that at **both** endpoints
 * `|translation| <= (minScale - 1) / 2`. Because scale and translation are both linearly
 * interpolated, `|t(p)|` never exceeds the larger endpoint magnitude while `scale(p)` is
 * never below `minScale`; the bound consequently holds at every point on the path, not
 * just at its ends. Using `minScale` rather than the current scale is what makes zoom-out
 * presets safe, since those are tightest at the end of the move.
 */
object PortraitPanelMotion {

    /** Task §5: scale range 1.01x to 1.05x at the SUBTLE profile. */
    const val BASE_MIN_SCALE = 1.01f
    const val BASE_MAX_SCALE = 1.05f

    /** Task §5: at most 2% of the rendered panel size on either axis. */
    const val MAX_TRANSLATION_FRACTION = 0.02f

    /**
     * Panels are tall and narrow, so the same fractional pan is far more visible
     * horizontally and eats crop margin faster. Task §6 requires limiting horizontal
     * movement more aggressively than vertical.
     */
    const val HORIZONTAL_TIGHTENING = 0.5f

    /** Task §8: keep the centre panel calmer than the two outside panels. */
    const val CENTRE_PANEL_DAMPING = 0.6f

    private val OUTER_PRESETS = listOf(
        PanelMotionPreset.PAN_LEFT_TO_RIGHT,
        PanelMotionPreset.PAN_RIGHT_TO_LEFT,
        PanelMotionPreset.DIAGONAL_UP,
        PanelMotionPreset.DIAGONAL_DOWN,
        PanelMotionPreset.ZOOM_IN,
    )

    private val CENTRE_PRESETS = listOf(
        PanelMotionPreset.ZOOM_OUT,
        PanelMotionPreset.PAN_UP,
        PanelMotionPreset.PAN_DOWN,
        PanelMotionPreset.ZOOM_IN,
    )

    /**
     * Build one safe path for a panel.
     *
     * [panelIndex] 1 is the centre panel and is damped per task §8.
     */
    fun pathFor(
        preset: PanelMotionPreset,
        panelIndex: Int,
        durationMillis: Int,
        profile: PanelMotionProfile,
        seed: Long,
    ): PanelMotionPath {
        if (profile == PanelMotionProfile.OFF) return staticPath(preset, durationMillis, profile, seed)

        val damping = if (panelIndex == 1) CENTRE_PANEL_DAMPING else 1f
        val strength = (profile.amplitude * damping).coerceAtLeast(0f)

        // Pan first: a pan is only possible if the zoom funds enough overscan, so the
        // amplitude the preset wants determines the scale floor rather than the reverse.
        // Without this the crop bound silently shrinks every pan to near-invisibility.
        val wantX = MAX_TRANSLATION_FRACTION * HORIZONTAL_TIGHTENING * strength * kotlin.math.abs(preset.dirX)
        val wantY = MAX_TRANSLATION_FRACTION * strength * kotlin.math.abs(preset.dirY)
        val wantPan = maxOf(wantX, wantY)

        // |translation| <= (minScale - 1) / 2  =>  minScale >= 1 + 2 * pan.
        val scaleFloor = (1f + 2f * wantPan).coerceAtLeast(BASE_MIN_SCALE)
        val span = ((BASE_MAX_SCALE - BASE_MIN_SCALE) * strength * kotlin.math.abs(preset.zoom))
            .coerceAtMost(BASE_MAX_SCALE - scaleFloor)
            .coerceAtLeast(0f)

        val startScale: Float
        val endScale: Float
        if (preset.zoom >= 0f) {
            startScale = scaleFloor
            endScale = scaleFloor + span
        } else {
            startScale = scaleFloor + span
            endScale = scaleFloor
        }

        val minScale = minOf(startScale, endScale)
        // Crop safety is the hard ceiling; the task §5 amplitude cap applies under it.
        val overscan = maxSafeTranslation(minScale)
        val capX = minOf(overscan, wantX)
        val capY = minOf(overscan, wantY)

        // Pan symmetrically about centre so the subject is never pushed fully aside (task §6).
        val halfX = preset.dirX.sign() * capX / 2f
        val halfY = preset.dirY.sign() * capY / 2f

        return PanelMotionPath(
            preset = preset,
            startScale = startScale,
            endScale = endScale,
            startTranslateXFraction = clampToOverscan(-halfX, minScale),
            endTranslateXFraction = clampToOverscan(halfX, minScale),
            startTranslateYFraction = clampToOverscan(-halfY, minScale),
            endTranslateYFraction = clampToOverscan(halfY, minScale),
            durationMillis = durationMillis,
            profile = profile,
            seed = seed,
        )
    }

    /**
     * Choose three compatible presets and build their paths (task §8).
     *
     * Deterministic in [seed] so the same frame always animates identically, which is what
     * makes the behaviour testable (task §7).
     */
    fun pathsForFrame(
        seed: Long,
        durationMillis: Int,
        profile: PanelMotionProfile,
    ): List<PanelMotionPath> {
        val presets = selectPresets(seed)
        return presets.mapIndexed { index, preset ->
            pathFor(preset, index, durationMillis, profile, seed + index)
        }
    }

    /**
     * Pick three presets that read as coordinated but not synchronised (task §8):
     * all distinct, the centre panel calmer, and never all panning the same way.
     */
    fun selectPresets(seed: Long): List<PanelMotionPreset> {
        val left = OUTER_PRESETS[indexFor(seed, 11L, OUTER_PRESETS.size)]
        val centre = CENTRE_PRESETS
            .filter { it != left }
            .let { it[indexFor(seed, 29L, it.size)] }
        val right = OUTER_PRESETS
            .filter { it != left && it != centre && !sameHorizontalDirection(it, left) }
            .ifEmpty { OUTER_PRESETS.filter { it != left && it != centre } }
            .let { it[indexFor(seed, 47L, it.size)] }
        return listOf(left, centre, right)
    }

    /** True when two presets drift the same way horizontally (task §8 rejects this). */
    fun sameHorizontalDirection(a: PanelMotionPreset, b: PanelMotionPreset): Boolean =
        a.dirX != 0f && b.dirX != 0f && (a.dirX > 0f) == (b.dirX > 0f)

    /**
     * True when two paths are close enough to look synchronised (task §8).
     * Compares pan direction and zoom sign rather than exact values.
     */
    fun nearlyIdentical(a: PanelMotionPath, b: PanelMotionPath): Boolean {
        val sameZoomSign = (a.endScale - a.startScale > 0f) == (b.endScale - b.startScale > 0f)
        val sameX = kotlin.math.abs(a.preset.dirX - b.preset.dirX) < 0.2f
        val sameY = kotlin.math.abs(a.preset.dirY - b.preset.dirY) < 0.2f
        return sameZoomSign && sameX && sameY
    }

    /**
     * Sample a path. [progress] is 0..1 across the slide; easing is smooth ease-in-out so
     * the move starts and stops without a visible jerk (task §5).
     */
    fun frameAt(path: PanelMotionPath, progress: Float): PanelMotionFrame {
        val p = easeInOut(progress.coerceIn(0f, 1f))
        return PanelMotionFrame(
            scale = path.startScale + (path.endScale - path.startScale) * p,
            translateXFraction = path.startTranslateXFraction +
                (path.endTranslateXFraction - path.startTranslateXFraction) * p,
            translateYFraction = path.startTranslateYFraction +
                (path.endTranslateYFraction - path.startTranslateYFraction) * p,
        )
    }

    /** Smoothstep: zero first derivative at both ends, so no visible start/stop jerk. */
    fun easeInOut(p: Float): Float {
        val x = p.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Largest pan fraction that still keeps a centre-cropped image covering its panel at
     * [minScale]. Exposed for tests asserting the task §6 guarantee.
     */
    fun maxSafeTranslation(minScale: Float): Float = ((minScale - 1f) / 2f).coerceAtLeast(0f)

    /** Direction only; magnitude is supplied separately by the per-axis caps. */
    private fun Float.sign(): Float = when {
        this > 0f -> 1f
        this < 0f -> -1f
        else -> 0f
    }

    private fun clampToOverscan(value: Float, minScale: Float): Float {
        val bound = maxSafeTranslation(minScale)
        return value.coerceIn(-bound, bound)
    }

    /** A valid path that does not move; used for OFF and for static fallback. */
    fun staticPath(
        preset: PanelMotionPreset = PanelMotionPreset.ZOOM_IN,
        durationMillis: Int = 0,
        profile: PanelMotionProfile = PanelMotionProfile.OFF,
        seed: Long = 0L,
    ): PanelMotionPath = PanelMotionPath(
        preset = preset,
        startScale = 1f,
        endScale = 1f,
        startTranslateXFraction = 0f,
        endTranslateXFraction = 0f,
        startTranslateYFraction = 0f,
        endTranslateYFraction = 0f,
        durationMillis = durationMillis,
        profile = profile,
        seed = seed,
    )

    /**
     * One-line description of a frame's motion for diagnostics (task §16).
     *
     * Covers panel assignment, the preset chosen per panel, the resulting scale range and
     * the translation limits. Built once per slide — task §16 forbids emitting per-frame
     * animation data — and carries no file paths or personal detail, only geometry.
     */
    fun describeFrame(paths: List<PanelMotionPath>): String =
        paths.mapIndexed { index, path ->
            val name = when (index) {
                0 -> "left"
                1 -> "centre"
                else -> "right"
            }
            val maxX = maxOf(
                kotlin.math.abs(path.startTranslateXFraction),
                kotlin.math.abs(path.endTranslateXFraction),
            )
            val maxY = maxOf(
                kotlin.math.abs(path.startTranslateYFraction),
                kotlin.math.abs(path.endTranslateYFraction),
            )
            "%s=%s scale %.3f->%.3f dx<=%.4f dy<=%.4f".format(
                java.util.Locale.ROOT,
                name, path.preset.name, path.startScale, path.endScale, maxX, maxY,
            )
        }.joinToString("; ")

    /**
     * Resolve the effective profile for a frame (task §12, §13).
     *
     * [animationScale] is the system animator duration scale: 0 means the user disabled
     * animations, below 1 means they reduced them. Task §13 requires movement to be
     * disabled outright in the first case and reduced in amplitude in the second.
     *
     * A negative scale means "unknown" (the setting was unreadable) and is treated as
     * normal, since failing to read a system setting should not silently disable a
     * feature the user explicitly enabled.
     */
    fun profileFor(enabled: Boolean, animationScale: Float): PanelMotionProfile = when {
        !enabled -> PanelMotionProfile.OFF
        animationScale in 0f..0.01f -> PanelMotionProfile.OFF
        animationScale in 0f..0.99f -> PanelMotionProfile.REDUCED
        else -> PanelMotionProfile.SUBTLE
    }

    /** Stable index from a seed; mixes bits so adjacent ids don't select alike. */
    private fun indexFor(seed: Long, salt: Long, size: Int): Int {
        if (size <= 0) return 0
        var x = (seed + salt) * -7046029254386353131L
        x = x xor (x ushr 32)
        return ((x ushr 3).toInt() and 0x7FFFFFFF) % size
    }
}
