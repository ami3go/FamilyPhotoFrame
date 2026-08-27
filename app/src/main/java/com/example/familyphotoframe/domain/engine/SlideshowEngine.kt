package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.db.FolderSelectionSql
import com.example.familyphotoframe.data.db.PhotoItemEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.source.BuiltInSourceIds
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.domain.randomize.PlaybackQueue
import com.example.familyphotoframe.slideshow.shuffle.FolderBalancedShuffleCoordinator
import com.example.familyphotoframe.slideshow.shuffle.FolderKey
import com.example.familyphotoframe.slideshow.shuffle.HistoryPresentation
import com.example.familyphotoframe.slideshow.shuffle.ReservedPresentation
import com.example.familyphotoframe.slideshow.shuffle.ShuffleAdvanceResult
import com.example.familyphotoframe.slideshow.shuffle.ShuffleScopeKeyFactory
import com.example.familyphotoframe.util.ImageFormatSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Slideshow engine (spec §9). A single command-driven coroutine owns the timing
 * loop and the current/next photo, so the UI never schedules its own timers and
 * there is exactly one source of truth. Auto-advance happens on interval timeout;
 * D-pad/web controls arrive as commands that pre-empt the wait.
 *
 * Selection is delegated to the configured ordering mode. Folder-balanced playback
 * loads only folder metadata globally and the selected folder's member rows; it never
 * enumerates or decodes the entire library on the engine/UI thread. Byte decoding is
 * done by the image loader in the UI, which reports failures
 * back via [reportDecodeFailure] so corrupt files are skipped and counted (§9.2).
 */
class SlideshowEngine(
    private val dao: PhotoDao,
    private val diagnostics: DiagnosticsLog,
    private val folderBalancedShuffle: FolderBalancedShuffleCoordinator,
    private val random: Random = Random.Default,
    private val allowHeif: Boolean = true,
    private val onRenderAckTimeout: () -> Unit = {},
) {
    private sealed interface Command {
        data object Next : Command
        data object Previous : Command
        data object TogglePause : Command
        data object Reselect : Command   // content/source changed; re-pick from index
        data object SleepChanged : Command
        /** Restart the dwell timer after the selected bitmap is actually on screen. */
        data object Rendered : Command
        data object InteractionHoldChanged : Command
        data object VisibilityHoldChanged : Command
        data object DiagnosticHoldChanged : Command
        data class PreviewFolderOnce(val folderKey: String) : Command
        data object ReconcileShuffle : Command
    }

    private val commands = Channel<Command>(Channel.CONFLATED)

    private val _ui = MutableStateFlow(EngineUiModel())
    val ui: StateFlow<EngineUiModel> = _ui.asStateFlow()

    // Configuration (guarded by single-threaded loop consumption).
    @Volatile private var primaryIds: List<String> = emptyList()
    @Volatile private var fallbackIds: List<String> = emptyList()
    @Volatile private var playingFallback: Boolean = false
    @Volatile private var intervalMs: Long = 15_000
    @Volatile private var maxFailures: Int = 3
    @Volatile private var asleep: Boolean = false
    /** Temporary UI hold: pauses only the automatic dwell timer, not playback state. */
    @Volatile private var interactionHold: Boolean = false
    @Volatile private var hostActive: Boolean = false
    @Volatile private var surfaceObscured: Boolean = false
    @Volatile private var diagnosticHold: Boolean = false
    /** Survives conflation with lifecycle wakeups until a visible loop applies it. */
    @Volatile private var reselectPending: Boolean = false
    @Volatile private var selectionMode: SelectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE
    @Volatile private var favoritesOnly: Boolean = false
    @Volatile private var activePlaylistId: String = "builtin_all"
    @Volatile private var collageLookahead: Int = 0
    @Volatile private var unavailableSourceIds: Set<String> = emptySet()
    @Volatile private var exhaustedUnavailableSourceIds: Set<String> = emptySet()

    /**
     * Folder names to restrict playback to (spec §9.4). Empty means "all folders".
     *
     * Held as a list for stable scope hashing. DAO calls encode it into one exact-match
     * SQLite parameter, avoiding the platform bind-variable limit for large libraries.
     */
    @Volatile private var selectedFolders: List<String> = emptyList()

    /**
     * Restrict the *primary* pool to photos with cached bytes (spec §9.3 stale-cache
     * playback). Only ever true for a remote source that is currently unreachable; the
     * fallback pool is local and never restricted.
     */
    @Volatile private var primaryCachedOnly: Boolean = false

    /**
     * Live pool membership, for surfaces outside the ViewModel (the web config's source
     * indicator) that need to say which source is actually feeding playback.
     */
    val poolSnapshot: PoolSnapshot
        get() = PoolSnapshot(
            primaryIds = primaryIds,
            unavailableSourceIds = unavailableSourceIds,
            primaryCachedOnly = primaryCachedOnly,
        )

    data class PoolSnapshot(
        val primaryIds: List<String>,
        val unavailableSourceIds: Set<String>,
        /** True while an unreachable remote primary is being served from cached bytes. */
        val primaryCachedOnly: Boolean,
    )

    /** Sequential queues for the date-ordered modes. */
    private val primaryQueue = PlaybackQueue()
    private val fallbackQueue = PlaybackQueue()

    /** The already-selected and preloaded next photo; consuming it prevents queue skips. */
    private var reservedNext: Pick? = null

    /** Persisted folder-balanced reservation for the selected, not-yet-committed presentation. */
    private var activeReservation: ReservedPresentation? = null
    private var activeShuffleScopeKey: String? = null
    /** True when an oversized explicit queue is using the persistent bounded selector. */
    @Volatile private var boundedSelectionFallbackActive: Boolean = false
    /** Current committed slide restored after a non-mutating one-folder preview. */
    private var previewReturnPick: Pick? = null
    /**
     * Explicit id pool for [SelectionMode.ON_THIS_DAY], pushed by
     * `SlideshowViewModel`'s "on this day" trigger (docs/FPF-FEAT-ON-THIS-DAY-001.md
     * §4.2) rather than derived from a SQL predicate like every other selection mode.
     */
    private var onThisDayIds: List<Long> = emptyList()
    /** True until the first presentation of this process is selected. */
    private var startupSelectionPending: Boolean = true

    /**
     * Cached playback pool, so an ordinary advance does not re-read the entire photo
     * index. Without it every slide runs an unbounded `displayableIds` query and
     * materialises the whole id list as boxed Longs purely to discover it is unchanged
     * — on a large NAS library that is megabytes of garbage per slide, which the small
     * heaps this frame runs on cannot spare. Keyed by everything the query depends on,
     * and dropped outright whenever the pool is reconfigured or the index is rewritten.
     */
    private class CachedPool(val key: String, val ids: LongArray, val loadedAtElapsedMs: Long)
    private data class PoolScaleDecision(val key: String, val useFolderBalanced: Boolean)

    @Volatile private var cachePlaybackPool: Boolean = true
    private var cachedPrimaryPool: CachedPool? = null
    private var cachedFallbackPool: CachedPool? = null
    private var primaryScaleDecision: PoolScaleDecision? = null
    private var fallbackScaleDecision: PoolScaleDecision? = null

    private val backStack = ArrayDeque<DisplayPhoto>()
    private val forwardStack = ArrayDeque<DisplayPhoto>()
    private val historyLimit = 50

    /** One terminal render outcome is accepted per selected current photo. */
    private var renderedCurrentId: Long? = null
    private var failedCurrentId: Long? = null

    /** Playback-pool reuse; see [CachedPool]. Turning it off re-queries on every advance. */
    fun setCachePlaybackPool(enabled: Boolean) {
        if (cachePlaybackPool == enabled) return
        cachePlaybackPool = enabled
        invalidatePlaybackPool()
    }

    /**
     * Drop the cached pools. Called whenever rows may have been added, removed or made
     * (un)displayable behind the engine's back: a completed scan, a curation edit, a
     * favourite toggled from the web UI.
     */
    fun invalidatePlaybackPool() {
        cachedPrimaryPool = null
        cachedFallbackPool = null
        primaryScaleDecision = null
        fallbackScaleDecision = null
        if (selectionMode != SelectionMode.FOLDER_BALANCED_SHUFFLE) {
            // Re-evaluate the safety fallback after any membership change; a library can
            // legitimately shrink back below the explicit-queue threshold.
            boundedSelectionFallbackActive = false
        }
    }

    fun configure(
        primaryIds: List<String>,
        fallbackIds: List<String>,
        intervalSeconds: Int,
        maxFailures: Int,
        selectionMode: SelectionMode = this.selectionMode,
        favoritesOnly: Boolean = this.favoritesOnly,
        primaryCachedOnly: Boolean = false,
        selectedFolders: List<String> = this.selectedFolders,
        activePlaylistId: String = this.activePlaylistId,
        collageLookahead: Int = this.collageLookahead,
        unavailableSourceIds: Set<String> = this.unavailableSourceIds,
        exhaustedUnavailableSourceIds: Set<String> = this.exhaustedUnavailableSourceIds,
    ) {
        // A scan may change membership without changing settings. Invalidate before
        // comparing pool fields so an identical reconfiguration still sees the new index.
        folderBalancedShuffle.invalidateEligibility()
        // Same reasoning for the sequential/date/shuffle pools: configure() is the call
        // a finished scan arrives through, and the row set may have changed under a key
        // that did not.
        invalidatePlaybackPool()
        // A changed pool definition invalidates an in-flight no-repeat cycle: the cycle
        // is a promise about a specific set of photos, and silently carrying it across a
        // source swap would skip photos of the new source for a whole cycle.
        val poolChanged = primaryIds != this.primaryIds ||
            fallbackIds != this.fallbackIds ||
            favoritesOnly != this.favoritesOnly ||
            primaryCachedOnly != this.primaryCachedOnly ||
            selectedFolders != this.selectedFolders ||
            selectionMode != this.selectionMode ||
            activePlaylistId != this.activePlaylistId ||
            exhaustedUnavailableSourceIds != this.exhaustedUnavailableSourceIds
        this.primaryIds = primaryIds
        this.fallbackIds = fallbackIds
        this.intervalMs = intervalSeconds.coerceIn(3, 600) * 1000L
        this.maxFailures = maxFailures
        this.selectionMode = selectionMode
        this.favoritesOnly = favoritesOnly
        this.primaryCachedOnly = primaryCachedOnly
        this.selectedFolders = selectedFolders
        this.activePlaylistId = activePlaylistId
        this.collageLookahead = collageLookahead.coerceIn(0, 12)
        this.unavailableSourceIds = unavailableSourceIds
        this.exhaustedUnavailableSourceIds = exhaustedUnavailableSourceIds
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "POOL_CONFIGURED",
            "primaryCount" to primaryIds.size.toString(),
            "fallbackCount" to fallbackIds.size.toString(),
            "cachedOnly" to primaryCachedOnly.toString(),
            "favorite" to favoritesOnly.toString(),
            "count" to selectedFolders.size.toString(),
            "playlistToken" to diagnosticToken(activePlaylistId, "playlist"),
            "candidateCount" to this.collageLookahead.toString(),
            "failures" to unavailableSourceIds.size.toString(),
            "exhausted" to exhaustedUnavailableSourceIds.size.toString(),
            "intervalMs" to (intervalSeconds * 1_000L).toString(),
        )
        if (poolChanged) resetSelectionState()
        requestReselect()
    }

    /**
     * Apply playback preferences without reselecting, when the pools are unchanged.
     * Changing the *mode* still resets cycle state — a half-finished shuffle cycle is
     * meaningless once the ordering rule changes — but the current photo stays up.
     */
    fun setPlayback(
        selectionMode: SelectionMode,
        favoritesOnly: Boolean,
        selectedFolders: List<String> = this.selectedFolders,
    ) {
        if (selectionMode == this.selectionMode &&
            favoritesOnly == this.favoritesOnly &&
            selectedFolders == this.selectedFolders
        ) return
        this.selectionMode = selectionMode
        this.favoritesOnly = favoritesOnly
        this.selectedFolders = selectedFolders
        resetSelectionState()
        requestReselect()
    }

    /**
     * Update timing/suppression without reselecting the current photo (so changing
     * "seconds per photo" in settings doesn't jump the slideshow). The new interval
     * takes effect at the next auto-advance wait.
     */
    fun setShuffleContext(
        activePlaylistId: String,
        collageLookahead: Int,
        unavailableSourceIds: Set<String> = this.unavailableSourceIds,
        exhaustedUnavailableSourceIds: Set<String> = this.exhaustedUnavailableSourceIds,
    ) {
        val playlistChanged = this.activePlaylistId != activePlaylistId
        val availabilityChanged = this.unavailableSourceIds != unavailableSourceIds ||
            this.exhaustedUnavailableSourceIds != exhaustedUnavailableSourceIds
        this.activePlaylistId = activePlaylistId
        this.collageLookahead = collageLookahead.coerceIn(0, 12)
        this.unavailableSourceIds = unavailableSourceIds
        this.exhaustedUnavailableSourceIds = exhaustedUnavailableSourceIds
        if (playlistChanged) resetSelectionState()
        if (playlistChanged || availabilityChanged) requestReselect()
    }

    fun setTiming(intervalSeconds: Int, maxFailures: Int) {
        this.intervalMs = intervalSeconds.coerceIn(3, 600) * 1000L
        this.maxFailures = maxFailures
    }

    /**
     * Enter or leave quiet hours (spec §20). While asleep the loop stops advancing and
     * blocks on the command channel, so no timers, decodes, or network work happen; the
     * current photo stays on screen for the UI to dim.
     */
    fun setAsleep(value: Boolean) {
        if (asleep == value) return
        asleep = value
        commands.trySend(Command.SleepChanged)
    }

    fun setInteractionHold(value: Boolean) {
        if (interactionHold == value) return
        interactionHold = value
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE,
            if (value) "SLIDESHOW_CONTROLS_HOLD" else "SLIDESHOW_CONTROLS_RELEASE",
        )
        commands.trySend(Command.InteractionHoldChanged)
    }

    /** Hold automatic advancement without changing the user's paused state or current frame. */
    fun setDiagnosticHold(value: Boolean) {
        if (diagnosticHold == value) return
        diagnosticHold = value
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE,
            if (value) "NATIVE_HIL_HOLD_STARTED" else "NATIVE_HIL_HOLD_RELEASED",
        )
        commands.trySend(Command.DiagnosticHoldChanged)
    }

    fun setHostActive(owner: Long, value: Boolean) {
        val changed = synchronized(loopOwnerLock) {
            if (loopOwner != owner) return
            if (hostActive == value) false else {
                hostActive = value
                true
            }
        }
        if (changed) commands.trySend(Command.VisibilityHoldChanged)
    }

    fun setSurfaceObscured(owner: Long, value: Boolean) {
        val changed = synchronized(loopOwnerLock) {
            if (loopOwner != owner) return
            if (surfaceObscured == value) false else {
                surfaceObscured = value
                true
            }
        }
        if (changed) commands.trySend(Command.VisibilityHoldChanged)
    }

    fun next() { commands.trySend(Command.Next) }
    fun previous() { commands.trySend(Command.Previous) }
    fun previewFolderOnce(folderKey: String) {
        if (folderKey.isNotBlank()) commands.trySend(Command.PreviewFolderOnce(folderKey))
    }

    /**
     * Sets the explicit id pool for [SelectionMode.ON_THIS_DAY]. Must be called before
     * activating `PlaylistSettings.PLAYLIST_ON_THIS_DAY` — switching to that playlist
     * changes the effective selection mode, which triggers an immediate
     * [Command.Reselect] (via [setPlayback]) that reads this pool right away.
     */
    fun setOnThisDayPool(ids: List<Long>) {
        onThisDayIds = ids
    }

    /** Request idempotent queue reconciliation without changing the visible slide. */
    fun reconcileShuffle() {
        folderBalancedShuffle.invalidateEligibility()
        commands.trySend(Command.ReconcileShuffle)
    }

    private fun requestReselect() {
        reselectPending = true
        commands.trySend(Command.Reselect)
    }

    fun resetActiveShuffle(clearHistory: Boolean = false) {
        loopScope?.launch {
            cancelActiveReservation("reset")
            folderBalancedShuffle.resetPlaylist(activePlaylistId, clearHistory)
            resetSelectionState()
            _ui.value = _ui.value.copy(
                next = null,
                reservedCandidateIds = emptyList(),
                shuffleProgress = activeShuffleScopeKey?.let { folderBalancedShuffle.progress(it) }
                    ?.copy(unavailableSourceCount = unavailableSourceIds.size)
                    ?: com.example.familyphotoframe.slideshow.shuffle.ShuffleProgress(
                        unavailableSourceCount = unavailableSourceIds.size,
                    ),
            )
        }
    }

    fun resetAllShuffle(clearHistory: Boolean = false) {
        loopScope?.launch {
            cancelActiveReservation("reset_all")
            folderBalancedShuffle.resetAll(clearHistory)
            resetSelectionState()
            _ui.value = _ui.value.copy(
                next = null,
                reservedCandidateIds = emptyList(),
                historyPhotoIds = emptyList(),
                shuffleProgress = activeShuffleScopeKey?.let { folderBalancedShuffle.progress(it) }
                    ?.copy(unavailableSourceCount = unavailableSourceIds.size)
                    ?: com.example.familyphotoframe.slideshow.shuffle.ShuffleProgress(
                        unavailableSourceCount = unavailableSourceIds.size,
                    ),
            )
        }
    }
    fun deletePlaylistShuffleState(playlistId: String) {
        loopScope?.launch {
            if (playlistId == activePlaylistId) cancelActiveReservation("playlist_deleted")
            folderBalancedShuffle.deletePlaylist(playlistId)
            if (playlistId == activePlaylistId) {
                activeShuffleScopeKey = null
                resetSelectionState()
            }
        }
    }

    /** Restart the dwell interval without changing the current photo or playback history. */
    fun restartInterval() { commands.trySend(Command.Rendered) }
    /**
     * [source] is recorded in diagnostics only. An accidental pause is indistinguishable
     * from a broken frame to whoever is looking at it, so when one is reported the log
     * should say whether it came from a touch, a remote, or the web panel rather than
     * leaving it to be inferred.
     */
    fun togglePause(source: String = "unknown") {
        pauseRequestSource = source
        commands.trySend(Command.TogglePause)
    }

    @Volatile private var pauseRequestSource: String = "unknown"

    /**
     * Curation actions (spec §9.4).
     *
     * Written straight to the index rather than sent through [commands]: that channel is
     * CONFLATED, so a curation action queued behind a Next would be silently discarded —
     * acceptable for "advance the slideshow", not for "the user marked this photo".
     *
     * Favouriting updates the on-screen model immediately so the star responds on the
     * frame without waiting for a reselect.
     */
    fun toggleFavorite() {
        val photo = _ui.value.current ?: return
        val newValue = !photo.isFavorite
        // Favourite state is a displayability predicate while favourites-only is on.
        invalidatePlaybackPool()
        loopScope?.launch {
            dao.setFavorite(photo.id, newValue)
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                if (newValue) "FAVORITE_ADD" else "FAVORITE_REMOVE",
                "id=${photo.id}",
            )
            _ui.value = _ui.value.copy(
                current = _ui.value.current?.takeIf { it.id == photo.id }?.copy(isFavorite = newValue)
                    ?: _ui.value.current,
            )
            // In favourites-only playback, un-favouriting the current photo removes it
            // from the pool, so move on rather than leaving a now-ineligible photo up.
            if (!newValue && favoritesOnly) commands.trySend(Command.Next)
        }
    }

    /** Reflect a batch favourite edit immediately when the anchor is among the edited ids. */
    fun reflectFavoriteState(ids: Set<Long>, value: Boolean) {
        invalidatePlaybackPool()
        val current = _ui.value.current ?: return
        if (current.id in ids) {
            _ui.value = _ui.value.copy(current = current.copy(isFavorite = value))
        }
    }

    /** Hide the current photo from all future selection; reversible via [PhotoDao.unhideAll]. */
    fun hideCurrent() {
        val photo = _ui.value.current ?: return
        invalidatePlaybackPool()
        loopScope?.launch {
            cancelActiveReservation("hide")
            dao.setHidden(photo.id, true)
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTO_HIDDEN",
                "photoToken" to diagnosticToken(photo.stableId.ifBlank { photo.id.toString() }, "photo"),
            )
            commands.trySend(Command.Next)
        }
    }

    /**
     * Record a presentation after it becomes visible. Folder-balanced ordering/history
     * was already committed by [commitPrepared] before the transition started.
     */
    suspend fun commitPrepared(
        anchorId: Long,
        memberIds: List<Long>,
        layout: String?,
    ): Boolean {
        val current = _ui.value.current ?: return false
        if (current.id != anchorId) return false
        val reservation = activeReservation ?: return true
        if (reservation.anchorPhotoId != anchorId) return false
        val committed = folderBalancedShuffle.commitPrepared(
            scopeKey = reservation.scopeKey,
            reservationId = reservation.reservationId,
            memberIds = (listOf(anchorId) + memberIds).distinct(),
            presentationType = if (memberIds.distinct().size > 1) "COLLAGE" else "SINGLE",
        ) ?: return false
        activeReservation = null
        activeShuffleScopeKey = committed.scopeKey
        _ui.value = _ui.value.copy(
            historyPhotoIds = committed.photoIds,
            reservedCandidateIds = emptyList(),
            shuffleProgress = folderBalancedShuffle.progress(committed.scopeKey).copy(
                unavailableSourceCount = unavailableSourceIds.size,
            ),
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PRESENTATION_PREPARED_COMMIT",
            "scope" to committed.scopeKey,
            "photoCount" to committed.photoIds.size.toString(),
            "layout" to layout.orEmpty(),
        )
        return true
    }

    fun reportRendered(
        anchorId: Long,
        memberIds: List<Long> = listOf(anchorId),
        dataSources: List<String?> = emptyList(),
        layout: String? = null,
    ) {
        loopScope?.launch {
            val current = _ui.value.current ?: return@launch
            if (current.id != anchorId || renderedCurrentId == anchorId || failedCurrentId == anchorId) return@launch
            renderedCurrentId = anchorId

            val ids = (listOf(anchorId) + memberIds).distinct()
            val now = System.currentTimeMillis()
            for (id in ids) {
                dao.markShown(id, now)
                dao.clearDecodeFailure(id)
            }
            if (ids.size > 1 && selectionMode != SelectionMode.FOLDER_BALANCED_SHUFFLE) {
                val companions = ids.drop(1)
                primaryQueue.consume(companions)
                fallbackQueue.consume(companions)
            }

            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                if (ids.size > 1) "COLLAGE_RENDERED" else "SLIDE_RENDERED",
                "photoToken" to diagnosticToken(current.stableId.ifBlank { current.id.toString() }, "photo"),
                "presentationToken" to diagnosticToken(ids.joinToString(","), "presentation"),
                "members" to ids.size.toString(),
                "sourceKind" to diagnosticSourceKind(current.sourceId),
                "sourceToken" to diagnosticToken(current.sourceId, "source"),
                "folderToken" to diagnosticToken("${current.sourceId}\u001f${current.folderName}", "folder"),
                "format" to current.fileName.substringAfterLast('.', "UNKNOWN").uppercase(),
                "stage" to dataSources.filterNotNull().map(::safeDiagnosticCode).distinct().joinToString("+").ifEmpty { "UNKNOWN" },
                "layout" to layout.orEmpty(),
                "selectionMode" to selectionMode.name,
            )
            commands.trySend(Command.Rendered)
        }
    }

    fun reportDecodeFailure(failure: DecodeFailure) {
        // A painter can recompose and emit the same terminal state more than once. Accept
        // only the first failure for the currently selected presentation.
        loopScope?.launch {
            val current = _ui.value.current ?: return@launch
            if (current.id != failure.photoId ||
                failedCurrentId == failure.photoId ||
                renderedCurrentId == failure.photoId
            ) return@launch

            failedCurrentId = failure.photoId
            activeReservation?.takeIf { it.anchorPhotoId == failure.photoId }?.let { reservation ->
                val reason = failure.reason ?: failure.exceptionClass ?: "decode_failure"
                val folderLevelFailure = failure.stage == DecodeFailureStage.SOURCE_READ &&
                    !failure.sourceLevelFailure && failure.sourceId !in unavailableSourceIds
                when {
                    failure.sourceLevelFailure -> {
                        unavailableSourceIds = unavailableSourceIds + failure.sourceId
                        folderBalancedShuffle.cancelReservation(
                            reservation.scopeKey, reservation.reservationId, "source_unavailable",
                        )
                    }
                    folderLevelFailure -> folderBalancedShuffle.releaseFolderFailure(
                        reservation.scopeKey, reservation.reservationId, reason,
                    )
                    else -> folderBalancedShuffle.releaseAnchorFailure(
                        reservation.scopeKey, reservation.reservationId, failure.photoId, reason,
                    )
                }
                activeReservation = null
                _ui.value = _ui.value.copy(reservedCandidateIds = emptyList())
            }
            persistSuppression(failure)
            diagnostics.log(
                DiagnosticsLog.Category.DECODE,
                if (failure.permanent) "DECODE_UNSUPPORTED" else "DECODE_FAILED",
                "photoToken" to diagnosticToken(failure.photoId.toString(), "photo"),
                "sourceKind" to diagnosticSourceKind(failure.sourceId),
                "format" to safeDiagnosticCode(failure.fileExtension.ifBlank { "UNKNOWN" }),
                "stage" to failure.stage.name,
                "errorClass" to failure.exceptionClass?.takeIf { it.isNotBlank() }.orEmpty().ifEmpty { "UNKNOWN" },
                "permanent" to failure.permanent.toString(),
                "reason" to failure.reason.orEmpty(),
            )
            commands.trySend(Command.Next)
        }
    }

    /** Applies [DecodeSuppressionPolicy]; this function owns only the write. */
    private suspend fun persistSuppression(failure: DecodeFailure) {
        val now = System.currentTimeMillis()
        when (DecodeSuppressionPolicy.outcomeFor(failure)) {
            DecodeSuppressionOutcome.NONE -> Unit
            DecodeSuppressionOutcome.COUNT -> dao.recordDecodeFailure(failure.photoId, now)
            DecodeSuppressionOutcome.PERMANENT ->
                dao.suppressDecode(failure.photoId, now, PERMANENT_DECODE_FAILURE_COUNT)
        }
    }

    /** Persist/log a companion failure without advancing the current anchor slide. */
    fun reportCollageCandidateFailure(failure: DecodeFailure) {
        loopScope?.launch {
            activeReservation?.takeIf { failure.photoId in it.candidatePhotoIds }?.let { reservation ->
                val recorded = folderBalancedShuffle.recordCandidateFailure(
                    reservation.scopeKey, reservation.reservationId, failure.photoId,
                )
                if (recorded) {
                    val remaining = reservation.candidatePhotoIds.filterNot { it == failure.photoId }
                    activeReservation = reservation.copy(candidatePhotoIds = remaining)
                    _ui.value = _ui.value.copy(reservedCandidateIds = remaining)
                }
            }
            persistSuppression(failure)
            diagnostics.log(
                DiagnosticsLog.Category.DECODE, "COLLAGE_CANDIDATE_FAILED",
                "photoToken" to diagnosticToken(failure.photoId.toString(), "photo"),
                "sourceKind" to diagnosticSourceKind(failure.sourceId),
                "format" to safeDiagnosticCode(failure.fileExtension.ifBlank { "UNKNOWN" }),
                "stage" to failure.stage.name,
                "errorClass" to failure.exceptionClass.orEmpty(),
                "permanent" to failure.permanent.toString(),
                "reason" to failure.reason.orEmpty(),
            )
        }
    }

    private val loopOwnerLock = Any()
    private var loopScope: CoroutineScope? = null
    private var loopJob: Job? = null
    private var loopOwner = 0L

    /** Start exactly one loop and return an owner token safe across ViewModel overlap. */
    fun start(scope: CoroutineScope): Long {
        var predecessor: Job? = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // Cancellation is asynchronous. Waiting for the retired owner eliminates a
            // brief two-loop window during Activity/ViewModel recreation.
            predecessor?.join()
            _ui.value = _ui.value.copy(state = EngineState.STARTING)
            val recovered = folderBalancedShuffle.startupRecovery()
            if (recovered > 0) {
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE,
                    "SHUFFLE_STARTUP_RECOVERY",
                    "reservations" to recovered.toString(),
                )
            }
            // Prime first photo.
            // A configuration published before this point is already visible through
            // volatile fields and is therefore satisfied by the initial selection.
            reselectPending = false
            advance(forward = true)
            while (isActive) {
                if (reselectPending && hostActive && !surfaceObscured) {
                    applyPendingReselect()
                    continue
                }
                val paused = _ui.value.paused
                val currentId = _ui.value.current?.id
                val hasContent = currentId != null
                val waitingForRender = currentId != null && renderedCurrentId != currentId
                if (paused || asleep || interactionHold || diagnosticHold ||
                    !hostActive || surfaceObscured || !hasContent
                ) {
                    // These waits are genuinely open-ended: nothing but an explicit
                    // command (resume, wake, content becoming available) should end them.
                    handle(commands.receive())
                } else if (waitingForRender) {
                    // Do not start the dwell countdown while the selected photo is still
                    // resolving/decoding. The UI keeps the previous bitmap visible and
                    // wakes this loop with Rendered (or Next on failure) — normally well
                    // under RENDER_ACK_TIMEOUT_MS. Bounded, unlike the waits above,
                    // because a dropped acknowledgment must not freeze playback forever.
                    val cmd = withTimeoutOrNull(RENDER_ACK_TIMEOUT_MS) { commands.receive() }
                    if (cmd == null) {
                        _ui.value.current?.let {
                            diagnostics.log(
                                DiagnosticsLog.Category.ENGINE,
                                "RENDER_ACK_TIMEOUT",
                                "photoToken" to diagnosticToken(it.stableId.ifBlank { it.id.toString() }, "photo"),
                            )
                        }
                        onRenderAckTimeout()
                        advance(forward = true)
                    } else {
                        handle(cmd)
                    }
                } else {
                    // A full interval starts only after successful visible presentation;
                    // any manual command still pre-empts it immediately.
                    val cmd = withTimeoutOrNull(intervalMs) { commands.receive() }
                    if (cmd == null) advance(forward = true) else handle(cmd)
                }
            }
        }
        val owner = synchronized(loopOwnerLock) {
            loopOwner += 1L
            predecessor = loopJob
            predecessor?.cancel()
            // The channel is process-owned too. A conflated command left by the retired
            // UI (for example Rendered/Next during recreation) must not skip the new
            // owner's freshly primed first slide.
            while (commands.tryReceive().isSuccess) Unit
            loopJob = job
            loopScope = scope
            hostActive = false
            surfaceObscured = false
            loopOwner
        }
        job.invokeOnCompletion {
            synchronized(loopOwnerLock) {
                if (loopOwner == owner && loopJob === job) {
                    loopJob = null
                    loopScope = null
                }
            }
        }
        job.start()
        return owner
    }

    fun stop(owner: Long) {
        synchronized(loopOwnerLock) {
            if (loopOwner != owner) return
            loopJob?.cancel()
            loopScope = null
            hostActive = false
            surfaceObscured = false
        }
    }

    private suspend fun handle(cmd: Command) {
        when (cmd) {
            Command.Next -> advance(forward = true)
            Command.Previous -> advance(forward = false)
            Command.Reselect -> if (hostActive && !surfaceObscured) applyPendingReselect() else Unit
            Command.InteractionHoldChanged -> Unit
            Command.VisibilityHoldChanged -> Unit
            Command.DiagnosticHoldChanged -> Unit
            Command.SleepChanged -> {
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE,
                    if (asleep) "SLEEP_ENTER" else "SLEEP_EXIT",
                )
                _ui.value = _ui.value.copy(state = currentState())
            }
            // No state mutation is needed: receiving this command exits the timeout
            // started at selection and the loop begins a fresh full dwell interval.
            Command.Rendered -> Unit
            is Command.PreviewFolderOnce -> previewFolderOnceInternal(cmd.folderKey)
            Command.ReconcileShuffle -> {
                activeShuffleScopeKey?.let { scopeKey ->
                    _ui.value = _ui.value.copy(
                        shuffleProgress = folderBalancedShuffle.progress(scopeKey).copy(
                            unavailableSourceCount = unavailableSourceIds.size,
                        )
                    )
                }
            }
            Command.TogglePause -> {
                val nowPaused = !_ui.value.paused
                _ui.value = _ui.value.copy(
                    paused = nowPaused,
                    state = currentState(paused = nowPaused),
                )
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE, if (nowPaused) "PAUSE" else "RESUME",
                    "trigger" to pauseRequestSource,
                )
            }
        }
    }

    private suspend fun applyPendingReselect() {
        // Clear before suspending. A newer configuration arriving during cancellation
        // or selection sets the flag again and is handled on the next loop iteration.
        reselectPending = false
        cancelActiveReservation("reselect")
        resetHistory()
        advance(forward = true)
    }

    private fun resetHistory() {
        backStack.clear()
        forwardStack.clear()
        reservedNext = null
        _ui.value = _ui.value.copy(
            current = null,
            next = null,
            reservedCandidateIds = emptyList(),
            historyPhotoIds = emptyList(),
        )
    }

    private fun resetSelectionState() {
        primaryQueue.reset()
        fallbackQueue.reset()
        reservedNext = null
        previewReturnPick = null
        boundedSelectionFallbackActive = false
    }


    private suspend fun cancelActiveReservation(reason: String) {
        val reservation = activeReservation ?: return
        folderBalancedShuffle.cancelReservation(
            reservation.scopeKey,
            reservation.reservationId,
            reason,
        )
        activeReservation = null
        _ui.value = _ui.value.copy(reservedCandidateIds = emptyList())
    }

    private suspend fun advance(forward: Boolean) {
        if (selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE ||
            boundedSelectionFallbackActive || activeReservation != null
        ) {
            advanceFolderBalanced(forward)
            return
        }

        if (!forward) {
            val prev = backStack.removeLastOrNull() ?: return
            _ui.value.current?.let { pushBounded(forwardStack, it) }
            setCurrent(Pick(prev.toEntityPlaceholder(), playingFallback), computeNextPreview = false)
            return
        }

        val fromForward = forwardStack.removeLastOrNull()
        if (fromForward != null) {
            _ui.value.current?.let { pushBounded(backStack, it) }
            setCurrent(Pick(fromForward.toEntityPlaceholder(), playingFallback), computeNextPreview = false)
            return
        }

        val picked = reservedNext?.also { reservedNext = null } ?: pick()
        if (picked == null) {
            publishNoContent()
            return
        }
        _ui.value.current?.let { pushBounded(backStack, it) }
        setCurrent(
            picked,
            computeNextPreview = picked.reservation == null && picked.scopeKey == null,
        )
    }

    private suspend fun advanceFolderBalanced(forward: Boolean) {
        // A manual advance while preparation is in progress releases the uncommitted turn.
        cancelActiveReservation(if (forward) "advance" else "previous")
        previewReturnPick?.let { returnPick ->
            previewReturnPick = null
            setCurrent(returnPick, computeNextPreview = false)
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_RETURNED",
                "scope" to (activeShuffleScopeKey ?: "none"),
            )
            return
        }
        val scopeKey = activeShuffleScopeKey
        if (!forward) {
            if (scopeKey == null) return
            val history = findValidHistory(scopeKey, previous = true) ?: return
            val item = dao.byId(history.photoIds.first()) ?: return
            setCurrent(
                Pick(
                    item = item,
                    fromFallback = playingFallback,
                    scopeKey = scopeKey,
                    historyPhotoIds = history.photoIds,
                ),
                computeNextPreview = false,
            )
            return
        }

        if (scopeKey != null) {
            val history = findValidHistory(scopeKey, previous = false)
            if (history != null) {
                val item = dao.byId(history.photoIds.first())
                if (item != null) {
                    setCurrent(
                        Pick(
                            item = item,
                            fromFallback = playingFallback,
                            scopeKey = scopeKey,
                            historyPhotoIds = history.photoIds,
                        ),
                        computeNextPreview = false,
                    )
                    return
                }
            }
        }

        val picked = pick()
        if (picked == null) {
            publishNoContent()
            return
        }
        setCurrent(picked, computeNextPreview = false)
    }

    private suspend fun previewFolderOnceInternal(folderStorageKey: String) {
        val folderKey = runCatching { FolderKey.parse(folderStorageKey) }.getOrNull() ?: run {
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_REJECTED",
                "reason" to "invalid_folder_key",
            )
            return
        }
        cancelActiveReservation("folder_preview")
        val cachedOnly = if (folderKey.sourceId in unavailableSourceIds) 1 else 0
        val candidates = dao.previewFolderCandidates(
            sourceId = folderKey.sourceId,
            canonicalDirectory = folderKey.canonicalRelativeDirectory,
            maxFailures = maxFailures,
            favoritesOnly = if (favoritesOnly) 1 else 0,
            cachedOnly = cachedOnly,
            allowHeif = if (allowHeif) 1 else 0,
            limit = 64,
        )
        val candidate = candidates.filterNot { it.id == _ui.value.current?.id }
            .ifEmpty { candidates }
            .let { rows -> if (rows.isEmpty()) null else rows[random.nextInt(rows.size)] }
        if (candidate == null) {
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_REJECTED",
                "folderToken" to diagnosticToken(folderStorageKey, "folder"),
                "reason" to "no_displayable_photo",
            )
            return
        }
        previewReturnPick = _ui.value.current?.let { current ->
            Pick(
                item = current.toEntityPlaceholder(),
                fromFallback = playingFallback,
                scopeKey = activeShuffleScopeKey,
                historyPhotoIds = _ui.value.historyPhotoIds,
            )
        }
        setCurrent(
            Pick(
                item = candidate,
                fromFallback = candidate.sourceId in fallbackIds,
                scopeKey = null,
            ),
            computeNextPreview = false,
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_ONCE",
            "folderToken" to diagnosticToken(folderStorageKey, "folder"),
            "active" to (previewReturnPick != null).toString(),
        )
    }

    private suspend fun findValidHistory(scopeKey: String, previous: Boolean): HistoryPresentation? {
        repeat(HISTORY_SKIP_LIMIT) {
            val history = if (previous) {
                folderBalancedShuffle.previous(scopeKey)
            } else {
                folderBalancedShuffle.nextFromHistory(scopeKey)
            } ?: return null
            if (historyIsDisplayable(history)) return history
        }
        return null
    }

    private suspend fun historyIsDisplayable(history: HistoryPresentation): Boolean {
        if (history.photoIds.isEmpty()) return false
        val rows = dao.byIds(history.photoIds).associateBy { it.id }
        return history.photoIds.all { id ->
            rows[id]?.let(::isRuntimeDisplayable) == true
        }
    }

    private fun publishNoContent() {
        _ui.value = _ui.value.copy(
            state = if (primaryIds.isEmpty() && fallbackIds.isEmpty()) EngineState.INDEX_EMPTY
            else EngineState.DEGRADED_NO_CONTENT,
        )
    }

    private suspend fun setCurrent(pick: Pick, computeNextPreview: Boolean) {
        val photo = pick.item.toDisplayPhoto()
        renderedCurrentId = null
        failedCurrentId = null
        playingFallback = pick.fromFallback
        activeReservation = pick.reservation
        activeShuffleScopeKey = pick.scopeKey ?: activeShuffleScopeKey
        if (selectionMode != SelectionMode.FOLDER_BALANCED_SHUFFLE) {
            when {
                pick.scopeKey != null || pick.reservation != null -> boundedSelectionFallbackActive = true
                // A one-folder preview temporarily has no scope. Preserve the fallback
                // marker so its next action returns through persistent history.
                previewReturnPick == null -> boundedSelectionFallbackActive = false
            }
        }
        startupSelectionPending = false
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "SLIDE_SELECTED",
            "photoToken" to diagnosticToken(photo.stableId.ifBlank { photo.id.toString() }, "photo"),
            "sourceKind" to diagnosticSourceKind(photo.sourceId),
            "sourceToken" to diagnosticToken(photo.sourceId, "source"),
            "folderToken" to diagnosticToken("${photo.sourceId}\u001f${photo.folderName}", "folder"),
            "format" to photo.fileName.substringAfterLast('.', "UNKNOWN").uppercase(),
            "cachedOnly" to photo.needsCache.toString(),
            "selectionMode" to selectionMode.name,
            "active" to (pick.reservation != null).toString(),
        )
        val canPreselect = computeNextPreview &&
            selectionMode != SelectionMode.FOLDER_BALANCED_SHUFFLE &&
            !boundedSelectionFallbackActive
        val preview = if (canPreselect) {
            pick().also { reservedNext = it }?.item?.toDisplayPhoto()
        } else null
        val progress = pick.scopeKey?.let { folderBalancedShuffle.progress(it) }
            ?.copy(unavailableSourceCount = unavailableSourceIds.size)
            ?: _ui.value.shuffleProgress.copy(unavailableSourceCount = unavailableSourceIds.size)
        _ui.value = _ui.value.copy(
            current = photo,
            next = preview,
            state = currentState(),
            reservedCandidateIds = pick.reservation?.candidatePhotoIds.orEmpty(),
            historyPhotoIds = pick.historyPhotoIds,
            shuffleProgress = progress,
            cycleShown = if (selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE || boundedSelectionFallbackActive) {
                progress.folderResolved
            } else {
                _ui.value.cycleShown
            },
            cycleTotal = if (selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE || boundedSelectionFallbackActive) {
                progress.folderTotal
            } else {
                _ui.value.cycleTotal
            },
        )
    }

    /** Sleep outranks pause, which outranks normal playback (spec §9.1). */
    private fun currentState(paused: Boolean = _ui.value.paused): EngineState = when {
        asleep -> EngineState.SLEEPING
        paused -> EngineState.PAUSED
        else -> playingState()
    }

    private fun playingState(): EngineState =
        if (playingFallback) EngineState.PLAYING_FALLBACK else EngineState.PLAYING_PRIMARY

    private data class Pick(
        val item: PhotoItemEntity,
        val fromFallback: Boolean,
        val reservation: ReservedPresentation? = null,
        val scopeKey: String? = null,
        val historyPhotoIds: List<Long> = emptyList(),
    )

    /** Prefer a displayable primary photo; fall back to the fallback pool (spec §9.3 on_empty). */
    private suspend fun pick(): Pick? {
        if (primaryIds.isNotEmpty() ||
            (selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE && unavailableSourceIds.isNotEmpty())
        ) {
            pickFrom(
                sourceIds = primaryIds,
                queue = primaryQueue,
                cachedOnly = primaryCachedOnly,
                poolRole = "primary",
                fromFallback = false,
            )?.let { return it }
        }
        if (fallbackIds.isNotEmpty()) {
            pickFrom(
                sourceIds = fallbackIds,
                queue = fallbackQueue,
                cachedOnly = false,
                poolRole = "fallback",
                fromFallback = true,
            )?.let { return it }
        }
        return null
    }

    private suspend fun pickFrom(
        sourceIds: List<String>,
        queue: PlaybackQueue,
        cachedOnly: Boolean,
        poolRole: String,
        fromFallback: Boolean,
    ): Pick? {
        val favFlag = if (favoritesOnly) 1 else 0
        val cacheFlag = if (cachedOnly) 1 else 0
        val folders = selectedFolders
        val allFolders = if (folders.isEmpty()) 1 else 0
        val folderSelection = FolderSelectionSql.encode(folders)

        val useFolderBalanced = selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE ||
            (selectionMode != SelectionMode.ON_THIS_DAY && explicitPoolIsOversized(
                sourceIds = sourceIds,
                cachedOnly = cachedOnly,
                folders = folders,
                allFolders = allFolders,
                folderSelection = folderSelection,
                poolRole = poolRole,
            ))
        if (useFolderBalanced) {
            return pickFolderBalanced(
                sourceIds = sourceIds,
                cachedOnly = cachedOnly,
                folders = folders,
                poolRole = poolRole,
                fromFallback = fromFallback,
                // Keep fallback scopes distinct by the mode the user requested. The
                // storage algorithm is folder-balanced, but switching two oversized
                // explicit modes must not silently reuse the other's persisted cursor.
                playbackOrder = selectionMode,
            )
        }


        suspend fun queryPool(): List<Long> = when (selectionMode) {
            SelectionMode.SEQUENTIAL ->
                dao.displayableIds(sourceIds, maxFailures, favFlag, cacheFlag, if (allowHeif) 1 else 0, allFolders, folderSelection)
            SelectionMode.SHUFFLE_NO_REPEAT ->
                dao.displayableIds(sourceIds, maxFailures, favFlag, cacheFlag, if (allowHeif) 1 else 0, allFolders, folderSelection)
            SelectionMode.DATE_TAKEN_NEWEST ->
                dao.displayableIdsByDateTakenDesc(sourceIds, maxFailures, favFlag, cacheFlag, if (allowHeif) 1 else 0, allFolders, folderSelection)
            SelectionMode.DATE_TAKEN_OLDEST ->
                dao.displayableIdsByDateTakenAsc(sourceIds, maxFailures, favFlag, cacheFlag, if (allowHeif) 1 else 0, allFolders, folderSelection)
            // Explicit id list, not a SQL predicate — see setOnThisDayPool.
            SelectionMode.ON_THIS_DAY -> onThisDayIds
            // Returned earlier; explicit branch keeps this when-expression exhaustive
            // so adding another mode becomes a compiler-visible change.
            SelectionMode.FOLDER_BALANCED_SHUFFLE -> emptyList()
        }

        val pool: LongArray = if (!cachePlaybackPool || selectionMode == SelectionMode.ON_THIS_DAY) {
            // The on-this-day pool is already an in-memory list pushed by the ViewModel,
            // so there is nothing to save by caching it a second time.
            queryPool().toLongArray()
        } else {
            val key = PlaybackPoolCachePolicy.key(
                selectionMode = selectionMode,
                sourceIds = sourceIds,
                maxFailures = maxFailures,
                favoritesOnly = favFlag == 1,
                cachedOnly = cacheFlag == 1,
                allowHeif = allowHeif,
                folders = folders,
            )
            val slot = if (fromFallback) cachedFallbackPool else cachedPrimaryPool
            val now = System.nanoTime() / 1_000_000L
            val reusable = PlaybackPoolCachePolicy.canReuse(
                enabled = cachePlaybackPool,
                cachedKey = slot?.key,
                currentKey = key,
                ageMs = now - (slot?.loadedAtElapsedMs ?: 0L),
                maxAgeMs = POOL_CACHE_MAX_AGE_MS,
            )
            if (reusable) {
                slot!!.ids
            } else {
                // Converted immediately: the DAO's boxed list becomes garbage now
                // instead of being retained for the life of the cache entry.
                queryPool().toLongArray().also { loaded ->
                    val entry = CachedPool(key, loaded, now)
                    if (fromFallback) cachedFallbackPool = entry else cachedPrimaryPool = entry
                }
            }
        }
        if (pool.isEmpty()) return null
        queue.sync(pool, shuffleMode = selectionMode == SelectionMode.SHUFFLE_NO_REPEAT, random = random)
        repeat(MAX_QUEUE_MISSES) {
            val id = queue.next(random) ?: return null
            val row = dao.byId(id)
            if (row != null && isRuntimeDisplayable(row)) {
                _ui.value = _ui.value.copy(
                    cycleShown = queue.poolSize - queue.remainingInCycle,
                    cycleTotal = queue.poolSize,
                )
                return Pick(row, fromFallback)
            }
        }
        return null
    }

    /**
     * Prevent whole-library modes from materializing a multi-array queue beyond the
     * tested heap budget. Folder-balanced selection preserves no-repeat progress while
     * keeping memory proportional to folder count plus one folder's members.
     */
    private suspend fun explicitPoolIsOversized(
        sourceIds: List<String>,
        cachedOnly: Boolean,
        folders: List<String>,
        allFolders: Int,
        folderSelection: String,
        poolRole: String,
    ): Boolean {
        val key = PlaybackPoolCachePolicy.key(
            selectionMode = selectionMode,
            sourceIds = sourceIds,
            maxFailures = maxFailures,
            favoritesOnly = favoritesOnly,
            cachedOnly = cachedOnly,
            allowHeif = allowHeif,
            folders = folders,
        )
        val cached = if (poolRole == "primary") primaryScaleDecision else fallbackScaleDecision
        if (cached?.key == key) return cached.useFolderBalanced

        val count = dao.displayableCount(
            sourceIds = sourceIds,
            maxFailures = maxFailures,
            favoritesOnly = if (favoritesOnly) 1 else 0,
            cachedOnly = if (cachedOnly) 1 else 0,
            allowHeif = if (allowHeif) 1 else 0,
            allFolders = allFolders,
            folderSelection = folderSelection,
        )
        val decision = PoolScaleDecision(key, count > MAX_EXPLICIT_POOL_IDS)
        if (poolRole == "primary") primaryScaleDecision = decision else fallbackScaleDecision = decision
        if (decision.useFolderBalanced) {
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "EXPLICIT_POOL_BOUNDED_FALLBACK",
                "requestedMode" to selectionMode.name,
                "eligiblePhotos" to count.toString(),
                "limit" to MAX_EXPLICIT_POOL_IDS.toString(),
                "poolRole" to poolRole,
            )
        }
        return decision.useFolderBalanced
    }

    private suspend fun pickFolderBalanced(
        sourceIds: List<String>,
        cachedOnly: Boolean,
        folders: List<String>,
        poolRole: String,
        fromFallback: Boolean,
        playbackOrder: SelectionMode,
    ): Pick? {
        val relevantUnavailable = if (poolRole == "primary") unavailableSourceIds else emptySet()
        val relevantExhausted = if (poolRole == "primary") exhaustedUnavailableSourceIds else emptySet()
        val shuffleSourceIds = (sourceIds + relevantUnavailable).distinct()
        val revision = ShuffleScopeKeyFactory.eligibilityRevision(
            ShuffleScopeKeyFactory.EligibilityInputs(
                playlistId = activePlaylistId,
                sourceIds = shuffleSourceIds,
                selectedFolders = folders,
                favoritesOnly = favoritesOnly,
                cachedOnly = cachedOnly,
                maxDecodeFailures = maxFailures,
                formatCapabilitySignature = if (allowHeif) "heif-platform" else "heif-excluded",
            )
        )
        val descriptor = ShuffleScopeKeyFactory.scope(
            playlistId = activePlaylistId,
            revision = revision,
            playbackOrder = playbackOrder,
            poolRole = poolRole,
        )
        val scopeChanged = activeShuffleScopeKey != descriptor.scopeKey
        activeShuffleScopeKey = descriptor.scopeKey
        if (scopeChanged) {
            val newest = folderBalancedShuffle.newestHistory(descriptor.scopeKey)
            if (!startupSelectionPending && newest != null) {
                val history = if (historyIsDisplayable(newest)) {
                    newest
                } else {
                    findValidHistory(descriptor.scopeKey, previous = true)
                }
                val anchor = history?.photoIds?.firstOrNull()?.let { dao.byId(it) }
                if (anchor != null) {
                    return Pick(
                        item = anchor,
                        fromFallback = fromFallback,
                        scopeKey = descriptor.scopeKey,
                        historyPhotoIds = history.photoIds,
                    )
                }
            }
        }
        val selectionStartedNs = System.nanoTime()
        val reservationResult = folderBalancedShuffle.reserveNext(
            FolderBalancedShuffleCoordinator.PoolConfiguration(
                descriptor = descriptor,
                sourceIds = shuffleSourceIds,
                maxFailures = maxFailures,
                favoritesOnly = favoritesOnly,
                cachedOnly = cachedOnly,
                selectedFolders = folders,
                unavailableSourceIds = relevantUnavailable,
                exhaustedUnavailableSourceIds = relevantExhausted,
            ),
            collageLookahead = collageLookahead,
        )
        val selectionElapsedMs = ((System.nanoTime() - selectionStartedNs) / 1_000_000L)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE,
            "SHUFFLE_SELECTION_TIMING",
            "elapsedMs" to selectionElapsedMs.toString(),
            "targetMs" to if (cachedOnly) "50" else "250",
            "cachedOnly" to cachedOnly.toString(),
            "result" to if (reservationResult is ShuffleAdvanceResult.Reserved) "reserved" else "empty",
        )
        return when (val result = reservationResult) {
            is ShuffleAdvanceResult.Reserved -> {
                val reserved = result.presentation
                val row = dao.byId(reserved.anchorPhotoId)
                if (row == null || !isRuntimeDisplayable(row)) {
                    folderBalancedShuffle.releaseAnchorFailure(
                        reserved.scopeKey,
                        reserved.reservationId,
                        reserved.anchorPhotoId,
                        "reserved_anchor_not_displayable",
                    )
                    null
                } else {
                    Pick(
                        item = row,
                        fromFallback = fromFallback,
                        reservation = reserved,
                        scopeKey = descriptor.scopeKey,
                    )
                }
            }
            ShuffleAdvanceResult.Empty -> null
        }
    }

    /** Query filtering is the fast path; this guard protects restored history/reservations. */
    private fun isRuntimeDisplayable(row: PhotoItemEntity): Boolean =
        !row.isHidden && row.decodeFailureCount < maxFailures &&
            (allowHeif || !ImageFormatSupport.isHeif(row.fileName, row.mimeType))

    /** Placeholder used by the bounded in-memory Previous/Next history. */
    private fun DisplayPhoto.toEntityPlaceholder() = PhotoItemEntity(
        id = id,
        stableId = stableId,
        sourceId = sourceId,
        normalizedPath = normalizedPath,
        openToken = openToken,
        fileName = fileName,
        folderName = folderName,
        sizeBytes = sizeBytes,
        fileModifiedEpochMs = fileModifiedEpochMs,
        indexedAtEpochMs = 0L,
        mimeType = mimeType,
        width = width,
        height = height,
        exifOrientation = exifOrientation,
        dateTakenEpochMs = dateTakenEpochMs,
        caption = caption,
        gpsLat = gpsLat,
        gpsLon = gpsLon,
        isFavorite = isFavorite,
        isHidden = false,
        lastShownAtEpochMs = lastShownAtEpochMs,
        decodeFailureCount = 0,
        lastDecodeFailureAtEpochMs = null,
        missingSinceEpochMs = null,
        cacheKey = null,
        exifScannedAtEpochMs = null,
    )

    private fun pushBounded(stack: ArrayDeque<DisplayPhoto>, item: DisplayPhoto) {
        stack.addLast(item)
        while (stack.size > historyLimit) stack.removeFirst()
    }

    private fun diagnosticSourceKind(sourceId: String): String = when (sourceId) {
        BuiltInSourceIds.LOCAL_SAF -> "LOCAL_SAF"
        BuiltInSourceIds.FALLBACK -> "FALLBACK"
        BuiltInSourceIds.SMB -> "SMB"
        BuiltInSourceIds.SYNOLOGY -> "SYNOLOGY"
        BuiltInSourceIds.WEBDAV -> "WEBDAV"
        BuiltInSourceIds.LOCAL_UPLOADS -> "LOCAL_UPLOADS"
        else -> "OTHER"
    }

    private fun safeDiagnosticCode(value: String): String = value.uppercase()
        .filter { it.isLetterOrDigit() || it == '_' || it == '+' }
        .take(40)
        .ifEmpty { "UNKNOWN" }


    private companion object {
        /**
         * How many stale queue entries to skip before giving up on a pick. Small on
         * purpose: each miss is a database round-trip, and a pool that misses this many
         * times in a row is better re-synced on the next selection than spun on here.
         */
        const val MAX_QUEUE_MISSES = 8

        /**
         * Backstop for pool reuse. Explicit invalidation covers the changes the engine
         * is told about; this bounds how long an unnoticed one can persist, and a stale
         * id is skipped at pick time by the existing runtime-displayable recheck anyway.
         */
        const val POOL_CACHE_MAX_AGE_MS = 5L * 60_000L
        /** Keeps explicit queues comfortably below the heap budget of API-21 tablets. */
        const val MAX_EXPLICIT_POOL_IDS = 200_000L
        const val HISTORY_SKIP_LIMIT = 100

        /**
         * Backstop for a dropped render acknowledgment. The UI reports rendering via
         * [reportRendered] once a selection is visible; if that report is ever lost (a
         * UI-side bug skips the call, e.g. a reselected candidate matching the already
         * -committed slide bypassed its only call site), the loop would otherwise block
         * on [commands] with no timeout and freeze playback silently and indefinitely.
         * This bounds that wait so a lost acknowledgment self-heals into a retry instead.
         * Comfortably above observed decode/collage-evaluation durations (single-digit
         * seconds even for multi-candidate remote probing) so it never fires for a
         * merely slow — as opposed to lost — render.
         */
        const val RENDER_ACK_TIMEOUT_MS = 30_000L

    }
}
