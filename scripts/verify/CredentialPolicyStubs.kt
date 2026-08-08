data class SmbSettings(
    val host: String = "",
    val share: String = "",
    val path: String = "",
    val user: String = "",
    val domain: String = "",
    val credentialRef: String = "",
)

data class SynologySettings(
    val baseUrl: String = "",
    val folderPath: String = "/photo",
    val user: String = "",
    val credentialRef: String = "",
    val useThumbnails: Boolean = true,
    val thumbnailSize: String = "large",
    val pinnedCertSha256: String? = null,
)

data class WebDavSettings(
    val baseUrl: String = "",
    val rootPath: String = "",
    val folderPath: String = "",
    val user: String = "",
    val credentialRef: String = "",
    val pinnedCertSha256: String? = null,
)
