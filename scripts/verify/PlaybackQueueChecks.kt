import kotlin.random.Random

/**
 * The point of PlaybackQueue is a guarantee ("every photo once before any repeat")
 * that is easy to claim and easy to get subtly wrong, so it is asserted here over
 * whole cycles rather than spot-checked on single calls.
 */
fun runPlaybackQueueChecks() {
    println("-- shuffle no-repeat cycle --")
    run {
        val q = PlaybackQueue()
        val pool = (1L..20L).toList()
        q.sync(pool, shuffleMode = true, random = Random(7))
        val cycle = (1..20).map { q.next(Random(it)) }
        check("cycle length", 20, cycle.size)
        check("no nulls in cycle", true, cycle.all { it != null })
        check("every photo exactly once", pool.toSet(), cycle.filterNotNull().toSet())
        check("no duplicates in cycle", 20, cycle.filterNotNull().toSet().size)
        check("shuffled, not insertion order", false, cycle.filterNotNull() == pool)
    }

    run {
        val q = PlaybackQueue()
        q.sync((1L..6L).toList(), shuffleMode = true, random = Random(3))
        val first = (1..6).map { q.next(Random(9)) }
        val second = (1..6).map { q.next(Random(11)) }
        check("second cycle also complete", (1L..6L).toSet(), second.filterNotNull().toSet())
        check("cycles counted", 1, q.cyclesCompleted)
        check("no repeat across the seam", false, first.last() == second.first())
    }

    println("-- single-photo pool --")
    run {
        val q = PlaybackQueue()
        q.sync(listOf(42L), shuffleMode = true, random = Random(1))
        check("repeats the only photo", listOf(42L, 42L, 42L), (1..3).map { q.next(Random(it)) })
    }

    println("-- empty pool --")
    run {
        val q = PlaybackQueue()
        q.sync(emptyList(), shuffleMode = true, random = Random(1))
        check("empty yields null", null, q.next(Random(1)))
        check("isEmpty", true, q.isEmpty())
    }

    println("-- sequential (date-ordered) mode --")
    run {
        val q = PlaybackQueue()
        val ordered = listOf(50L, 40L, 30L, 20L, 10L)   // DAO already sorted these
        q.sync(ordered, shuffleMode = false, random = Random(5))
        check("plays in the given order", ordered, (1..5).map { q.next(Random(5)) })
        check("wraps to the start", 50L, q.next(Random(5)))
    }

    println("-- pool changes mid-cycle --")
    run {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L, 4L), shuffleMode = false, random = Random(2))
        val shown = listOf(q.next(), q.next())          // 1, 2
        check("first two shown", listOf(1L, 2L), shown)
        // A rescan drops 3 and adds 5 and 6.
        q.sync(listOf(1L, 2L, 4L, 5L, 6L), shuffleMode = false, random = Random(2))
        val rest = (1..3).map { q.next() }
        check("already-shown photos are not repeated", true, rest.none { it == 1L || it == 2L })
        check("removed photo does not appear", false, rest.contains(3L))
        check("new photos join the current cycle", setOf(4L, 5L, 6L), rest.filterNotNull().toSet())
    }

    run {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(2))
        q.next(); q.next()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(2))
        check("identical re-sync does not restart the cycle", 3L, q.next())
    }

    println("-- reset --")
    run {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(2))
        q.next(); q.next()
        q.reset()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(2))
        check("reset replays from the start", 1L, q.next())
        check("reset clears the cycle count", 0, q.cyclesCompleted)
    }

    println("-- determinism --")
    run {
        fun run1(): List<Long?> {
            val q = PlaybackQueue()
            q.sync((1L..10L).toList(), shuffleMode = true, random = Random(99))
            return (1..10).map { q.next(Random(99)) }
        }
        check("same seed, same order", run1(), run1())
    }
}
