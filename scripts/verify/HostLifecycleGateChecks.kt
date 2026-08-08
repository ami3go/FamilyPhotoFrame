fun runHostLifecycleGateChecks() {
    println("-- host lifecycle generation gate --")
    val gate = HostLifecycleGate()
    check("inactive host has no token", null, gate.tokenIfActive())

    val first = gate.start()
    check("first generation is current", true, gate.isCurrent(first))
    check("active token matches generation", first, gate.tokenIfActive())

    gate.stop()
    check("stop invalidates old generation", false, gate.isCurrent(first))
    check("stopped host has no token", null, gate.tokenIfActive())

    val second = gate.start()
    check("restart advances generation", true, second > first)
    check("old generation stays stale after restart", false, gate.isCurrent(first))
    check("new generation is current", true, gate.isCurrent(second))
}
