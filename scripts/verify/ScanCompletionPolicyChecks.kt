fun runScanCompletionPolicyChecks() {
    println("-- streamed scan completion policy --")
    check("clean finished scan reconciles", true,
        ScanCompletionPolicy.shouldReconcile(receivedFinished = true, errors = 0))
    check("partial scan preserves stale rows", false,
        ScanCompletionPolicy.shouldReconcile(receivedFinished = true, errors = 1))
    check("missing Finished preserves stale rows", false,
        ScanCompletionPolicy.shouldReconcile(receivedFinished = false, errors = 0))
    check("missing Finished is reported as an error", 1,
        ScanCompletionPolicy.finalErrorCount(receivedFinished = false, errors = 0))
}
