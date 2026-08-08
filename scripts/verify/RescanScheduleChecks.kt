/**
 * The scheduler decides when the frame touches the NAS unattended, so the awkward cases
 * (missed runs, wrong weekday, double-firing within one window, clock changes) are
 * asserted rather than reasoned about.
 */
fun runRescanScheduleChecks() {
    val all = RescanSchedule.everyDay()
    val at = 3 * 60 + 30            // 03:30
    val WED = 3

    println("-- day selection --")
    check("runs on a selected day", true,
        RescanSchedule.isDue(at, WED, at, setOf(WED), null))
    check("skips an unselected day", false,
        RescanSchedule.isDue(at, WED, at, setOf(1, 2), null))
    check("empty day set never runs", false,
        RescanSchedule.isDue(at, WED, at, emptySet(), null))

    println("-- time window --")
    check("not due before the time", false,
        RescanSchedule.isDue(at - 1, WED, at, all, null))
    check("due exactly at the time", true,
        RescanSchedule.isDue(at, WED, at, all, null))
    check("due within the grace window", true,
        RescanSchedule.isDue(at + 59, WED, at, all, null))
    check("not due once grace has passed", false,
        RescanSchedule.isDue(at + 61, WED, at, all, null))

    println("-- does not run twice for one slot --")
    run {
        // Ran 10 minutes ago; now 30 minutes past the scheduled time.
        check("suppressed after running in this window", false,
            RescanSchedule.isDue(at + 30, WED, at, all, 10L))
        // Last run was a week ago.
        check("runs again next week", true,
            RescanSchedule.isDue(at + 30, WED, at, all, 7L * 24 * 60))
    }

    println("-- missed run (device was off) --")
    run {
        // Powered on 45 minutes late, having last run a day earlier.
        check("catches a late start inside grace", true,
            RescanSchedule.isDue(at + 45, WED, at, all, 24L * 60))
        // Powered on 5 hours late: the slot is gone, wait for the next one rather than
        // rescanning at a random moment.
        check("does not fire hours late", false,
            RescanSchedule.isDue(at + 300, WED, at, all, 24L * 60))
    }

    println("-- minutesUntilNext --")
    check("later today", 30,
        RescanSchedule.minutesUntilNext(at - 30, WED, at, all))
    check("never zero", true,
        (RescanSchedule.minutesUntilNext(at, WED, at, all) ?: 0) > 0)
    check("tomorrow when today has passed", 24 * 60 - 30,
        RescanSchedule.minutesUntilNext(at + 30, WED, at, all))
    check("null when no days selected", null,
        RescanSchedule.minutesUntilNext(at, WED, at, emptySet()))
    run {
        // Only Monday selected, and it is Tuesday: six days away.
        val mins = RescanSchedule.minutesUntilNext(at, 2, at, setOf(1))
        check("skips to the one selected day", 6 * 24 * 60, mins)
    }

    println("-- elapsed-time helper --")
    check("never run", null, RescanSchedule.minutesSince(0L, 1_000_000L))
    check("60 minutes", 60L, RescanSchedule.minutesSince(1_000_000L, 1_000_000L + 3_600_000L))
    check("clock moved backwards is treated as unknown", null,
        RescanSchedule.minutesSince(2_000_000L, 1_000_000L))

    println("-- day parsing --")
    check("parses csv", setOf(1, 3, 5), RescanSchedule.parseDays("1,3,5"))
    check("ignores junk and out-of-range", setOf(2), RescanSchedule.parseDays("2,9,x,0"))
    check("round-trips", "1,6,7", RescanSchedule.formatDays(setOf(7, 1, 6)))
    check("weekdays only", setOf(1, 2, 3, 4, 5), RescanSchedule.weekdaysOnly())
}
