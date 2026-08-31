fun runDecodeColorPolicyChecks() {
    println("-- low-memory decode colour policy --")
    val roomyHeap = 512L * 1024L * 1024L

    check(
        "standard roomy AUTO keeps full colour",
        DecodeColorChoice.ARGB_8888,
        DecodeColorPolicy.choose(
            DecodeColorDepth.AUTO,
            roomyHeap,
            PlaybackMemoryLevel.NORMAL,
            lowMemoryTier = false,
        ),
    )
    check(
        "low-tier roomy AUTO still halves pixel bytes",
        DecodeColorChoice.RGB_565,
        DecodeColorPolicy.choose(
            DecodeColorDepth.AUTO,
            roomyHeap,
            PlaybackMemoryLevel.NORMAL,
            lowMemoryTier = true,
        ),
    )
    check(
        "explicit full colour overrides low-tier AUTO policy",
        DecodeColorChoice.ARGB_8888,
        DecodeColorPolicy.choose(
            DecodeColorDepth.FULL,
            roomyHeap,
            PlaybackMemoryLevel.CRITICAL,
            lowMemoryTier = true,
        ),
    )
    check(
        "RGB565 uses two bytes per pixel",
        2,
        DecodeColorPolicy.bytesPerPixel(DecodeColorChoice.RGB_565),
    )
}
