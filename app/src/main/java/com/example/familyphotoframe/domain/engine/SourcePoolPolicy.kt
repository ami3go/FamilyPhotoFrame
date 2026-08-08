package com.example.familyphotoframe.domain.engine

/**
 * Decides which sources feed the primary pool (spec §9.3).
 *
 * This logic used to live inline in `SlideshowViewModel`, tangled with coroutines, Room
 * and Android URIs — which meant it could only ever be syntax-checked, never type-checked
 * or executed, in an environment without the Android toolchain. Pulling the decisions
 * out here makes them ordinary Kotlin: compiled and run by the offline harness and by
 * `SourcePoolPolicyTest`. The ViewModel keeps the I/O; this keeps the rules.
 *
 * Pure and total: no clock, no randomness, no I/O, and every input combination yields a
 * plan.
 */
object SourcePoolPolicy {

    /** One configured source after activation has tried to bring it online. */
    data class Slot(
        val sourceId: String,
        /** Answered its health check and has been indexed, so its photos may play. */
        val healthy: Boolean,
        /** The source the user actually selected, as opposed to one merged in behind it. */
        val isChosen: Boolean = false,
    )

    sealed interface Plan {
        /** Play the union of these sources, with the fallback pool behind them. */
        data class Play(val primaryIds: List<String>) : Plan

        /**
         * Nothing is reachable: apply the `on_unreachable` policy for [sourceId], which
         * is the source the user chose — the one whose photos they expect to see.
         */
        data class Unreachable(val sourceId: String) : Plan

        /** Nothing is even configured; there is no pool to build. */
        data object NothingConfigured : Plan
    }

    /**
     * Plan for a freshly activated set of slots.
     *
     * Order is preserved from [slots] rather than sorted, so the engine's pool stays
     * stable across reconfigurations and a rescan cannot reshuffle it.
     */
    fun initialPlan(slots: List<Slot>): Plan {
        if (slots.isEmpty()) return Plan.NothingConfigured
        val healthy = slots.filter { it.healthy }.map { it.sourceId }
        val chosen = (slots.firstOrNull { it.isChosen } ?: slots.first()).sourceId
        return planFor(healthy, chosen)
    }

    /**
     * Plan for an already-running frame whose set of healthy sources just changed
     * (a recovery loop promoted or demoted one).
     *
     * An unreachable source is **dropped from the pool** while any other source is still
     * healthy, rather than triggering stale-cache playback: serving one source's stale
     * cached bytes while another serves live photos at full quality would be strictly
     * worse. Stale playback is a last resort, not a per-source behaviour.
     */
    fun planFor(healthyIds: Collection<String>, chosenId: String?): Plan {
        val ids = healthyIds.distinct()
        if (ids.isNotEmpty()) return Plan.Play(ids)
        return if (chosenId != null) Plan.Unreachable(chosenId) else Plan.NothingConfigured
    }

    /** Pool after a source recovered. Additive: co-primaries keep playing. */
    fun afterPromote(pool: Collection<String>, sourceId: String): List<String> =
        if (sourceId in pool) pool.toList() else pool.toList() + sourceId

    /** Pool after a source dropped out. */
    fun afterDemote(pool: Collection<String>, sourceId: String): List<String> =
        pool.filter { it != sourceId }
}
