package com.example.familyphotoframe.data.diagnostics

/**
 * Defense-in-depth value policy shared by every diagnostics surface.
 *
 * Call sites are expected to pass categorical values and typed identity tokens. This
 * boundary still detects common private value shapes and replaces them with a stable,
 * installation-specific HMAC token before an entry reaches memory or disk.
 */
object DiagnosticPrivacyPolicy {
    data class ProtectedValue(val value: String, val transformed: Boolean)

    private val identityFields = setOf(
        "activityToken", "anchorToken", "browserToken", "configRevision", "folderToken",
        "periodToken", "photoToken", "playlistToken", "presentationToken", "refreshToken", "sessionToken",
        "sourceToken", "outgoingPresentationToken", "incomingPresentationToken",
        "breadcrumbPresentationToken", "breadcrumbSessionToken",
    )

    private val classFields = setOf(
        "errorClass", "exceptionClass", "rootCauseClass", "sinkStatus",
    )

    private val explicitPrivateShape = listOf(
        Regex("(?i)(?:content|file|smb|https?|webdav)://"),
        Regex("(?i)\\b(?:password|passwd|secret|authorization|bearer|cookie|otp|pin|api[_-]?key|token)\\s*[:=]"),
        Regex("(?i)\\b(?:user(?:name)?|share|root|album|folder|host(?:name)?)\\s*[:=]"),
        Regex("(?i)\\b(?:exif|gps|artist|copyright|image[ _-]?description)\\s*[:=]"),
        Regex("(?i)\\b[A-Z]:[\\\\/][^\\s]+"),
        Regex("\\\\\\\\[^\\s\\\\]+\\\\[^\\s]+"),
        Regex("(?:^|\\s)/(?:[^\\s/]+/)+[^\\s]*"),
        Regex("(?<![A-Za-z0-9])(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?![A-Za-z0-9])"),
        Regex("(?i)\\b(?:[a-f0-9]{0,4}:){2,}[a-f0-9:]{0,39}\\b"),
        Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
        Regex("(?i)\\b(?:[a-z0-9-]+\\.)+(?:local|lan|home|internal|com|net|org|io|cloud|dev)\\b"),
        Regex("[?&][A-Za-z0-9_.~-]+="),
        Regex("%[0-9A-Fa-f]{2}"),
        Regex("(?i)(?:^|[\\s/\\\\])[^\\s/\\\\]+\\.(?:jpe?g|png|gif|webp|heic|heif|avif|bmp|tiff?|dng)(?:$|[?&#\\s])"),
        Regex("[-+]?\\d{1,2}\\.\\d{4,}\\s*[,;]\\s*[-+]?\\d{1,3}\\.\\d{4,}"),
    )

    private val tokenShape = Regex("[a-z0-9]{1,20}_[0-9a-f]{24}")
    private val safeCode = Regex("[A-Za-z0-9_.$:+<>@=-]{0,200}")
    private val safeDevice = Regex("[A-Za-z0-9_ .+()-]{1,120}")
    private val safeClass = Regex("[A-Za-z_$][A-Za-z0-9_.$]*(?::[A-Za-z0-9_.$-]+)?")
    private val safeStack = Regex("[A-Za-z0-9_.$<>:-]+(?:>[A-Za-z0-9_.$<>:-]+)*")

    fun protect(field: String, rawValue: String): ProtectedValue {
        val value = rawValue.take(MAX_INPUT_LENGTH)
        val tokenType = field.removeSuffix("Token").removeSuffix("Revision")
            .ifBlank { "private" }
        if (field in identityFields) {
            if (tokenShape.matches(value)) return ProtectedValue(value, value != rawValue)
            return ProtectedValue(
                DiagnosticIdentityHasher.current().token(tokenType, rawValue),
                transformed = true,
            )
        }
        if (value.isEmpty()) return ProtectedValue("", rawValue.isNotEmpty())
        if (field in classFields && safeClass.matches(value)) {
            return ProtectedValue(value, value != rawValue)
        }
        if (field == "crashOrigin" && safeStack.matches(value)) {
            return ProtectedValue(value, value != rawValue)
        }
        if (field == "deviceModel" && safeDevice.matches(value) && value.none(Char::isISOControl)) {
            return ProtectedValue(value, value != rawValue)
        }
        val unsafe = value.any(Char::isISOControl) ||
            value.any { it.code > 0x7e } ||
            explicitPrivateShape.any { it.containsMatchIn(value) } ||
            !safeCode.matches(value)
        if (unsafe) {
            return ProtectedValue(
                DiagnosticIdentityHasher.current().token(tokenType, rawValue),
                transformed = true,
            )
        }
        return ProtectedValue(value, value != rawValue)
    }

    /** Variable diagnostic messages are forbidden; structured fields carry all values. */
    fun protectMessage(message: String): ProtectedValue =
        if (message.isEmpty()) ProtectedValue("", false) else ProtectedValue("", true)

    fun looksPrivate(value: String): Boolean = protect("reason", value).transformed

    private const val MAX_INPUT_LENGTH = 4_096
}
