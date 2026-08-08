package com.example.familyphotoframe.ui.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * Per-slide motion state for three-portrait-panel collages.
 *
 * ## Why this is hoisted out of the renderer
 *
 * A slide is rendered from two different call sites: directly while it is the committed
 * frame, and again through the transition renderer once it becomes the outgoing frame.
 * Those are separate points in the composition tree, so state held by `remember` inside
 * the panel composable is **not** shared between them — the second call site would start
 * a fresh [Animatable] at zero and the image would visibly snap back to the beginning of
 * its motion path exactly as the transition starts.
 *
 * Task §10 forbids that ("the outgoing frame must not jump back to its starting
 * position"), and task §11 requires the current transform to be preserved when the user
 * navigates manually mid-slide — which rules out the simpler trick of freezing the
 * outgoing frame at progress 1, since a manual `next` can interrupt at any progress.
 *
 * Keying the state by slide id and holding it above both call sites solves both: whichever
 * call site renders the slide reads the same progress value.
 *
 * ## Memory
 *
 * Task §14 forbids retaining references to previous frames, so [retain] prunes every entry
 * that is no longer on screen. At most three slides are ever live at once (outgoing,
 * incoming, committed), and each entry holds only floats — no bitmaps.
 */
@Stable
class PanelMotionStore {

    /** Paths are null when motion is off or path generation failed (task §15). */
    class Entry(
        val paths: List<PanelMotionPath>?,
        val progress: Animatable<Float, AnimationVector1D>,
        /**
         * Guards the §16 frame-description log so it emits exactly once per slide.
         *
         * [PreparedCollage] is rendered from up to three composition slots simultaneously:
         * as the committed frame, as the outgoing frame inside the transition renderer, and
         * during a recomposition of the same slot. Each slot runs its own
         * `LaunchedEffect(slideId, paths)`, so without this guard the description would log
         * once per rendering instance rather than once per slide.
         */
        val described: java.util.concurrent.atomic.AtomicBoolean =
            java.util.concurrent.atomic.AtomicBoolean(false),
    )

    private val entries = LinkedHashMap<Long, Entry>()
    private val signatures = HashMap<Long, String>()

    /**
     * The entry for [slideId], creating it on first use.
     *
     * [signature] captures the inputs the paths were built from. When it changes — a new
     * profile because the user toggled the setting, or a new slide duration — the entry is
     * rebuilt so the paths match the current settings rather than going stale.
     */
    fun entryFor(slideId: Long, signature: String, build: () -> List<PanelMotionPath>?): Entry {
        val existing = entries[slideId]
        if (existing != null && signatures[slideId] == signature) return existing
        val created = Entry(paths = build(), progress = Animatable(0f))
        entries[slideId] = created
        signatures[slideId] = signature
        return created
    }

    /** Drop state for slides that are no longer rendered (task §14). */
    fun retain(liveIds: Set<Long>) {
        entries.keys.retainAll(liveIds)
        signatures.keys.retainAll(liveIds)
    }

    /** Visible for tests: how many slides currently hold state. */
    val size: Int get() = entries.size
}

/** Remember a [PanelMotionStore] for the lifetime of the slideshow composition. */
@Composable
fun rememberPanelMotionStore(): PanelMotionStore = remember { PanelMotionStore() }
