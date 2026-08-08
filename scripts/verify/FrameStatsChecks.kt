
/** Build timestamps from a list of intervals in ms. */
fun fsTs(vararg ms: Double): LongArray {
  var t = 1_000_000_000L; val out = ArrayList<Long>(); out.add(t)
  for (m in ms) { t += (m * 1_000_000).toLong(); out.add(t) }
  return out.toLongArray()
}
fun runFrameStatsChecks() {
  println("-- percentileNs (nearest-rank) --")
  val a = longArrayOf(10,20,30,40,50,60,70,80,90,100)
  check("p50", 50L, FrameStats.percentileNs(a, 50.0))
  check("p95", 100L, FrameStats.percentileNs(a, 95.0))
  check("p100", 100L, FrameStats.percentileNs(a, 100.0))
  check("p0 -> first", 10L, FrameStats.percentileNs(a, 0.0))
  check("empty", 0L, FrameStats.percentileNs(longArrayOf(), 95.0))
  check("single", 42L, FrameStats.percentileNs(longArrayOf(42), 95.0))
  check("value really occurred", true, FrameStats.percentileNs(a, 55.0) in a.toList())

  println("-- fpsFromIntervalNs --")
  check("16.67ms -> 60fps", 60.0, Math.round(FrameStats.fpsFromIntervalNs(16_666_667L)).toDouble())
  check("33.3ms -> 30fps", 30.0, Math.round(FrameStats.fpsFromIntervalNs(33_333_333L)).toDouble())
  check("zero guarded", 0.0, FrameStats.fpsFromIntervalNs(0L))
  check("negative guarded", 0.0, FrameStats.fpsFromIntervalNs(-5L))

  println("-- summarize --")
  val steady = FrameStats.summarize(fsTs(*DoubleArray(120){16.7}))
  check("frame count", 121, steady.frameCount)
  check("steady ~60fps", 60.0, Math.round(steady.meanFps).toDouble())
  check("no jank", 0, steady.jankFrames)

  val empty = FrameStats.summarize(longArrayOf())
  check("empty -> zero frames", 0, empty.frameCount)
  check("empty formats", "no frames sampled", FrameStats.format(empty))
  check("single ts -> zero", 0, FrameStats.summarize(longArrayOf(5)).frameCount)

  println("-- non-monotonic clock must not inflate fps --")
  val backwards = longArrayOf(1000, 2000, 1500, 3000)
  val bs = FrameStats.summarize(backwards)
  check("negative interval dropped", 3, bs.frameCount)

  println("-- the gate itself --")
  // 100 good frames then a 200ms stall: mean looks fine, p95 must fail it.
  val stall = FrameStats.summarize(fsTs(*(DoubleArray(100){16.7} + DoubleArray(10){200.0})))
  check("stalls fail despite ok mean", false, stall.passesBudget)
  check("stall counted as severe jank", true, stall.severeJankFrames >= 10)
  val good = FrameStats.summarize(fsTs(*DoubleArray(200){16.7}))
  check("steady 60fps passes", true, good.passesBudget)
  val at30 = FrameStats.summarize(fsTs(*DoubleArray(200){33.0}))
  check("steady 33ms (>30fps) passes", true, at30.passesBudget)
  val at25 = FrameStats.summarize(fsTs(*DoubleArray(200){40.0}))
  check("steady 25fps fails", false, at25.passesBudget)
  val short = FrameStats.summarize(fsTs(*DoubleArray(10){16.7}))
  check("too few samples cannot pass", false, short.passesBudget)
  check("short run reports SAMPLING", true, FrameStats.format(short).startsWith("SAMPLING"))
  check("good run reports PASS", true, FrameStats.format(good).startsWith("PASS"))
  check("bad run reports FAIL", true, FrameStats.format(at25).startsWith("FAIL"))

  
}
