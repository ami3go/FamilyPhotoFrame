fun runBrightnessTimelineChecks() {
    println("-- scheduled brightness timeline --")
    val periods = listOf("07:00", "23:00")
    check("day boundary selects day period", 0, BrightnessTimeline.activeIndex(periods, 7 * 60))
    check("late evening stays on day period", 0, BrightnessTimeline.activeIndex(periods, 22 * 60 + 59))
    check("night boundary selects night period", 1, BrightnessTimeline.activeIndex(periods, 23 * 60))
    check("after midnight wraps to prior night period", 1, BrightnessTimeline.activeIndex(periods, 1 * 60))
    check("invalid period is ignored", 0, BrightnessTimeline.activeIndex(listOf("07:00", "bad"), 12 * 60))
    check("all-invalid schedule safely has no active period", null, BrightnessTimeline.activeIndex(listOf("bad", "25:99"), 12 * 60))
    check("out-of-range clock wraps safely", 1, BrightnessTimeline.activeIndex(periods, -60))
}
