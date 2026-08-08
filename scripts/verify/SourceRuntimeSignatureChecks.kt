fun runSourceRuntimeSignatureChecks() {
    println("-- source refresh identity --")
    val smb = SmbSettings(
        host = "nas.local",
        share = "photos",
        user = "frame",
        credentialRef = "smb-ref",
    )
    val synology = SynologySettings(
        baseUrl = "https://nas.local:5001",
        folderPath = "/photo",
        user = "frame",
        credentialRef = "syn-ref",
    )
    val failedSynology = AppSettings(
        source = ActiveSource(
            kind = ActiveSourceKind.SYNOLOGY,
            smb = smb,
            synology = synology,
        )
    )
    val configuredSmb = failedSynology.copy(
        source = failedSynology.source.copy(kind = ActiveSourceKind.SMB)
    )
    check(
        "failed Synology -> SMB starts a new activation",
        false,
        SourceRuntimeSignature.of(failedSynology) == SourceRuntimeSignature.of(configuredSmb),
    )

    val merged = configuredSmb.copy(
        source = configuredSmb.source.copy(alsoPlay = setOf(ActiveSourceKind.SYNOLOGY))
    )
    val editedSecondary = merged.copy(
        source = merged.source.copy(synology = synology.copy(folderPath = "/photo/Family"))
    )
    check(
        "merged secondary edit starts a new activation",
        false,
        SourceRuntimeSignature.of(merged) == SourceRuntimeSignature.of(editedSecondary),
    )

    val smbOnly = configuredSmb
    val editedUnused = smbOnly.copy(
        source = smbOnly.source.copy(synology = synology.copy(folderPath = "/photo/Unused"))
    )
    check(
        "unused source edit does not interrupt SMB",
        SourceRuntimeSignature.of(smbOnly),
        SourceRuntimeSignature.of(editedUnused),
    )

    val filtered = smbOnly.copy(
        filters = smbOnly.filters.copy(excludeFolders = smbOnly.filters.excludeFolders + "Screenshots")
    )
    check(
        "filter edit starts a new index",
        false,
        SourceRuntimeSignature.of(smbOnly) == SourceRuntimeSignature.of(filtered),
    )
}
