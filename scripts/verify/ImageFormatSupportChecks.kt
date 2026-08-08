fun runImageFormatSupportChecks() {
    println("-- image-format capability --")
    check("HEIC extension", true, ImageFormatSupport.isHeif("IMG_0001.HEIC", null))
    check("HEIF sequence MIME", true, ImageFormatSupport.isHeif("asset", "image/heif-sequence"))
    check("Android 6 blocks HEIC", false, ImageFormatSupport.isPlatformDecodable("IMG_0001.heic", null, 23))
    check("Android 7 blocks HEIF", false, ImageFormatSupport.isPlatformDecodable("IMG_0001.heif", null, 25))
    check("Android 8 accepts HEIC", true, ImageFormatSupport.isPlatformDecodable("IMG_0001.heic", null, 26))
    check("API 22 capability excludes HEIF", false, ImageFormatSupport.supportsPlatformHeif(22))
    check("API 26 capability includes HEIF", true, ImageFormatSupport.supportsPlatformHeif(26))
    check(
        "capability signature changes",
        false,
        ImageFormatSupport.playbackCapabilitySignature(22) ==
            ImageFormatSupport.playbackCapabilitySignature(26),
    )
    check("old Android still accepts JPEG", true, ImageFormatSupport.isPlatformDecodable("IMG_0001.jpg", null, 21))
}
