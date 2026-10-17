package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.ServiceLocator
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.SourceHealth

/** Stable diagnostic codes for source health and source kinds, shared across the ViewModel. */

internal fun healthStateCode(health: SourceHealth): String = when (health) {
    is SourceHealth.Ok -> "OK"
    is SourceHealth.NeedsPermission -> "NEEDS_PERMISSION"
    is SourceHealth.Missing -> "MISSING"
    is SourceHealth.ProviderError -> "PROVIDER_ERROR"
    is SourceHealth.Unavailable -> "UNAVAILABLE"
}

internal fun healthErrorCode(health: SourceHealth): String = when (health) {
    is SourceHealth.Ok -> "NONE"
    is SourceHealth.NeedsPermission -> "AUTH_OR_PERMISSION_REQUIRED"
    is SourceHealth.Missing -> "SOURCE_MISSING"
    is SourceHealth.Unavailable -> "HOST_UNAVAILABLE"
    is SourceHealth.ProviderError -> when (health.detail) {
        "two_factor_required" -> "TWO_FACTOR_REQUIRED"
        "auth_failed" -> "AUTH_FAILED"
        "CertUntrusted" -> "CERT_UNTRUSTED"
        "HostUnreachable" -> "HOST_UNAVAILABLE"
        "not_webdav" -> "NOT_WEBDAV"
        else -> "PROVIDER_ERROR"
    }
}

internal fun diagnosticSourceKind(source: PhotoSource): String = when (source.type) {
    com.example.familyphotoframe.data.source.SourceType.LOCAL_SAF_FOLDER -> "LOCAL_SAF"
    com.example.familyphotoframe.data.source.SourceType.APP_PRIVATE_BUILTIN -> "SAMPLES"
    com.example.familyphotoframe.data.source.SourceType.APP_PRIVATE_UPLOADS -> "LOCAL_UPLOADS"
    com.example.familyphotoframe.data.source.SourceType.SMB_SOURCE -> "SMB"
    com.example.familyphotoframe.data.source.SourceType.SYNOLOGY_FILE_STATION -> "SYNOLOGY"
    com.example.familyphotoframe.data.source.SourceType.WEBDAV -> "WEBDAV"
}

internal fun diagnosticSourceKind(sourceId: String): String = when (sourceId) {
    ServiceLocator.SOURCE_LOCAL_SAF -> "LOCAL_SAF"
    ServiceLocator.SOURCE_LOCAL_UPLOADS -> "LOCAL_UPLOADS"
    ServiceLocator.SOURCE_SMB -> "SMB"
    ServiceLocator.SOURCE_SYNOLOGY -> "SYNOLOGY"
    ServiceLocator.SOURCE_WEBDAV -> "WEBDAV"
    ServiceLocator.SOURCE_FALLBACK -> "SAMPLES"
    else -> "UNKNOWN"
}
