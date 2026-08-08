/**
 * WebDAV mapping checks. The protocol cannot be exercised without a server, so the
 * parsing and URL logic is asserted against payloads shaped like what real servers
 * send — including the namespace-prefix differences that break naive parsers.
 */
fun runWebDavChecks() {
    println("-- normalizeBaseUrl --")
    check("adds https", "https://nas.local", WebDavApi.normalizeBaseUrl("nas.local"))
    check("keeps http", "http://nas.local", WebDavApi.normalizeBaseUrl("http://nas.local"))
    check("strips trailing slash", "https://nas.local", WebDavApi.normalizeBaseUrl("https://nas.local/"))
    check("keeps path", "https://h/remote.php/dav", WebDavApi.normalizeBaseUrl("https://h/remote.php/dav/"))
    check("empty stays empty", "", WebDavApi.normalizeBaseUrl("   "))

    println("-- normalizePath --")
    check("adds leading slash", "/photo", WebDavApi.normalizePath("photo"))
    check("collapses duplicates", "/a/b", WebDavApi.normalizePath("//a///b//"))
    check("backslashes", "/a/b", WebDavApi.normalizePath("\\a\\b"))
    check("root is empty", "", WebDavApi.normalizePath("/"))

    println("-- encode/decode path --")
    check("encodes space", "/My%20Photos", WebDavApi.encodePath("/My Photos"))
    check("keeps separators", "/a/b/c", WebDavApi.encodePath("/a/b/c"))
    check("encodes non-ascii", "/caf%C3%A9", WebDavApi.encodePath("/café"))
    check("decodes space", "/My Photos", WebDavApi.decodePath("/My%20Photos"))
    check("decodes non-ascii", "/café", WebDavApi.decodePath("/caf%C3%A9"))
    check("round trip", "/a b/ünïcode & co", WebDavApi.decodePath(WebDavApi.encodePath("/a b/ünïcode & co")))
    check("tolerates bad escape", "/a%zz", WebDavApi.decodePath("/a%zz"))

    println("-- hrefToPath --")
    check("full url", "/remote.php/dav/x.jpg", WebDavApi.hrefToPath("https://h:8443/remote.php/dav/x.jpg"))
    check("bare path", "/dav/x.jpg", WebDavApi.hrefToPath("/dav/x.jpg"))
    check("origin only", "/", WebDavApi.hrefToPath("https://host"))

    println("-- buildCollectionUrl --")
    check("joins", "https://h/remote.php/dav/files/me/Photos",
        WebDavApi.buildCollectionUrl("https://h/", "/remote.php/dav/files/me", "Photos"))
    check("encodes", "https://h/root/My%20Pics",
        WebDavApi.buildCollectionUrl("https://h", "root", "/My Pics"))
    check("root only", "https://h/root", WebDavApi.buildCollectionUrl("https://h", "/root"))

    println("-- nextcloudFilesRoot --")
    check("standard endpoint", "/remote.php/dav/files/alice", WebDavApi.nextcloudFilesRoot("alice"))
    check("encodes user", "/remote.php/dav/files/a%40b.com", WebDavApi.nextcloudFilesRoot("a@b.com"))

    println("-- parseHttpDate --")
    check("rfc1123", 1721039400000L, WebDavApi.parseHttpDate("Mon, 15 Jul 2024 10:30:00 GMT"))
    check("null", null, WebDavApi.parseHttpDate(null))
    check("blank", null, WebDavApi.parseHttpDate("  "))
    check("garbage", null, WebDavApi.parseHttpDate("not a date"))

    println("-- parsePropfind (Nextcloud 'd:' prefix) --")
    val nextcloud = """<?xml version="1.0"?>
      <d:multistatus xmlns:d="DAV:">
        <d:response>
          <d:href>/remote.php/dav/files/me/Photos/</d:href>
          <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
          <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        </d:response>
        <d:response>
          <d:href>/remote.php/dav/files/me/Photos/beach%20day.jpg</d:href>
          <d:propstat><d:prop>
            <d:resourcetype/>
            <d:getcontentlength>204800</d:getcontentlength>
            <d:getlastmodified>Mon, 15 Jul 2024 10:30:00 GMT</d:getlastmodified>
            <d:getcontenttype>image/jpeg</d:getcontenttype>
          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        </d:response>
        <d:response>
          <d:href>/remote.php/dav/files/me/Photos/2024/</d:href>
          <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
          <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        </d:response>
      </d:multistatus>"""
    val parsed = WebDavApi.parsePropfind(nextcloud, "/remote.php/dav/files/me/Photos")
    check("parsed", true, parsed != null)
    check("self excluded", 2, parsed?.size)
    val file = parsed?.firstOrNull { !it.isDir }
    check("file name decoded", "beach day.jpg", file?.name)
    check("file size", 204800L, file?.sizeBytes)
    check("file mtime", 1721039400000L, file?.modifiedEpochMs)
    check("content type", "image/jpeg", file?.contentType)
    check("file not dir", false, file?.isDir)
    val dir = parsed?.firstOrNull { it.isDir }
    check("subfolder found", "2024", dir?.name)
    check("subfolder is dir", true, dir?.isDir)

    println("-- parsePropfind (Apache mod_dav 'D:'/'lp1:' prefixes) --")
    val apache = """<?xml version="1.0" encoding="utf-8"?>
      <D:multistatus xmlns:D="DAV:" xmlns:lp1="DAV:">
        <D:response>
          <D:href>/dav/pics/</D:href>
          <D:propstat><D:prop><lp1:resourcetype><D:collection/></lp1:resourcetype></D:prop></D:propstat>
        </D:response>
        <D:response>
          <D:href>/dav/pics/a.png</D:href>
          <D:propstat><D:prop>
            <lp1:resourcetype/>
            <lp1:getcontentlength>512</lp1:getcontentlength>
            <lp1:getlastmodified>Tue, 16 Jul 2024 08:00:00 GMT</lp1:getlastmodified>
          </D:prop></D:propstat>
        </D:response>
      </D:multistatus>"""
    val ap = WebDavApi.parsePropfind(apache, "/dav/pics")
    check("apache parsed", 1, ap?.size)
    check("apache file", "a.png", ap?.firstOrNull()?.name)
    check("apache size", 512L, ap?.firstOrNull()?.sizeBytes)
    check("apache not dir", false, ap?.firstOrNull()?.isDir)

    println("-- parsePropfind (absolute-URL hrefs) --")
    val absolute = """<multistatus xmlns="DAV:">
        <response><href>https://nas.local:8443/dav/x/</href>
          <propstat><prop><resourcetype><collection/></resourcetype></prop></propstat></response>
        <response><href>https://nas.local:8443/dav/x/p.jpg</href>
          <propstat><prop><resourcetype/><getcontentlength>7</getcontentlength></prop></propstat></response>
      </multistatus>"""
    val abs = WebDavApi.parsePropfind(absolute, "/dav/x")
    check("no-prefix parsed", 1, abs?.size)
    check("absolute href reduced", "/dav/x/p.jpg", abs?.firstOrNull()?.path)

    println("-- parsePropfind rejects non-multistatus --")
    check("html error page", null, WebDavApi.parsePropfind("<html><body>Login</body></html>", "/x"))
    check("empty", null, WebDavApi.parsePropfind("", "/x"))
    check("empty multistatus is empty, not null", 0,
        WebDavApi.parsePropfind("""<d:multistatus xmlns:d="DAV:"></d:multistatus>""", "/x")?.size)

    println("-- xml entity unescaping --")
    val amp = """<multistatus xmlns="DAV:"><response>
        <href>/dav/Mum%20%26%20Dad.jpg</href>
        <propstat><prop><resourcetype/><getcontentlength>1</getcontentlength></prop></propstat>
      </response></multistatus>"""
    check("ampersand in name", "Mum & Dad.jpg", WebDavApi.parsePropfind(amp, "/dav")?.firstOrNull()?.name)

    println("-- mapStatus --")
    check("401 is auth, not permission", SourceError.AuthFailed, WebDavApi.mapStatus(401))
    check("403 is permission, not auth", SourceError.PermissionDenied, WebDavApi.mapStatus(403))
    check("404", SourceError.FileGone, WebDavApi.mapStatus(404))
    check("405 not a dav endpoint", SourceError.ProtocolError, WebDavApi.mapStatus(405))
    check("423 locked is retryable", SourceError.Timeout, WebDavApi.mapStatus(423))
    check("500", SourceError.ProviderError, WebDavApi.mapStatus(503))
    check("isSuccess 207", true, WebDavApi.isSuccess(207))
    check("isSuccess 404", false, WebDavApi.isSuccess(404))

    println("-- redactUserInfo (security-relevant) --")
    check("strips credentials", "https://***@nas.local/dav",
        WebDavApi.redactUserInfo("https://user:secret@nas.local/dav"))
    check("leaves clean url", "https://nas.local/dav",
        WebDavApi.redactUserInfo("https://nas.local/dav"))
    check("at-sign in path is not authority", "https://nas.local/dav/a@b.jpg",
        WebDavApi.redactUserInfo("https://nas.local/dav/a@b.jpg"))

    println("-- streamed PROPFIND bounds --")
    val manyResponses = (1..2_000).joinToString("") { index ->
        "<d:response><d:href>/dav/x/$index.jpg</d:href>" +
            "<d:getcontentlength>$index</d:getcontentlength></d:response>"
    }
    val streamed = WebDavApi.parsePropfind(
        java.io.ByteArrayInputStream(
            "<d:multistatus xmlns:d=\"DAV:\">$manyResponses</d:multistatus>".toByteArray(),
        ),
        "/dav/x",
    )
    check("streams 2,000 records", 2_000, streamed.entries.count())
    check("streamed root closes", true, streamed.isValid)

    val oversized = WebDavApi.parsePropfind(
        java.io.ByteArrayInputStream(
            ("<d:multistatus><d:response><d:href>/dav/x/a.jpg</d:href>" +
                "x".repeat(1_024) + "</d:response></d:multistatus>").toByteArray(),
        ),
        "/dav/x",
        maximumResponseBytes = 256,
    )
    val limited = try {
        oversized.entries.count()
        false
    } catch (_: WebDavLimitException) {
        true
    }
    check("rejects oversized record", true, limited)
}

/**
 * Scan-filter policy. These decide what gets indexed at all, so a mistake here silently
 * changes the contents of the frame rather than throwing anything.
 */
fun runScanFilterChecks() {
    println("-- ScanOptions.allowsFile --")
    val def = ScanOptions()
    check("jpg allowed", true, def.allowsFile("holiday.jpg"))
    check("JPEG case-insensitive", true, def.allowsFile("HOLIDAY.JPEG"))
    check("png allowed", true, def.allowsFile("a.png"))
    check("txt rejected", false, def.allowsFile("notes.txt"))
    check("hidden file rejected", false, def.allowsFile(".hidden.jpg"))
    check("Thumbs.db rejected", false, def.allowsFile("Thumbs.db"))

    println("-- ScanOptions.allowsFolder --")
    check("normal folder", true, def.allowsFolder("2024"))
    check("hidden folder pruned", false, def.allowsFolder(".git"))
    // @eaDir/** cannot match the bare folder name, so it must be an excludeFolders entry.
    check("@eaDir pruned", false, def.allowsFolder("@eaDir"))
    check("@eaDir case-insensitive", false, def.allowsFolder("@eadir"))
    check("subfolders off prunes everything", false,
        ScanOptions(includeSubfolders = false).allowsFolder("2024"))

    println("-- user-configured filters --")
    val custom = ScanOptions(
        includeGlobs = listOf("*.jpg"),
        excludeGlobs = listOf("*_edit.jpg"),
        excludeFolders = listOf("Screenshots", "Camera Uploads"),
    )
    check("custom include", true, custom.allowsFile("a.jpg"))
    check("png now excluded", false, custom.allowsFile("a.png"))
    check("custom exclude wins over include", false, custom.allowsFile("a_edit.jpg"))
    check("named folder pruned", false, custom.allowsFolder("Screenshots"))
    check("named folder pruned, spaced", false, custom.allowsFolder("Camera Uploads"))
    check("other folder kept", true, custom.allowsFolder("Family"))

    println("-- FilterSettings cleaning --")
    val messy = FilterSettings(
        includeGlobs = listOf(" *.jpg ", "", "   "),
        excludeGlobs = listOf("", "Thumbs.db"),
        excludeFolders = listOf(" Screenshots ", ""),
    )
    check("blank includes dropped", listOf("*.jpg"), messy.cleanIncludes)
    check("blank excludes dropped", listOf("Thumbs.db"), messy.cleanExcludes)
    check("folders trimmed", listOf("Screenshots"), messy.cleanExcludeFolders)
    check("all-blank include list is empty", emptyList<String>(),
        FilterSettings(includeGlobs = listOf("", " ")).cleanIncludes)
}

/**
 * Recovery backoff. Previously untestable in place (KNOWN_LIMITATIONS.md), and the kind
 * of logic whose bugs surface as "the frame took 15 minutes to notice the NAS was back".
 */
fun runRecoveryPolicyChecks() {
    println("-- backoff escalation while down --")
    var st = RecoveryPolicy.State(primaryActive = true)
    val waits = ArrayList<Long>()
    // First failure demotes; subsequent failures escalate.
    var step = RecoveryPolicy.next(st, healthy = false)
    check("first failure demotes", RecoveryPolicy.Action.DEMOTE, step.action)
    check("demote resets attempt", 0, step.state.attempt)
    waits.add(step.waitMs)
    st = step.state
    repeat(7) {
        step = RecoveryPolicy.next(st, healthy = false)
        waits.add(step.waitMs)
        st = step.state
    }
    check("stays demoted", false, st.primaryActive)
    check("escalating schedule", listOf(30_000L, 120_000L, 300_000L, 900_000L, 900_000L, 900_000L),
        waits.take(6))
    check("caps at the longest wait", 900_000L, waits.last())
    check("no action while still down", RecoveryPolicy.Action.NONE, step.action)

    println("-- recovery --")
    val back = RecoveryPolicy.next(st, healthy = true)
    check("promotes on recovery", RecoveryPolicy.Action.PROMOTE, back.action)
    check("primary active again", true, back.state.primaryActive)
    check("attempt reset", 0, back.state.attempt)
    check("healthy poll interval", 600_000L, back.waitMs)

    println("-- steady state --")
    val steady = RecoveryPolicy.next(back.state, healthy = true)
    check("no action while healthy", RecoveryPolicy.Action.NONE, steady.action)
    check("keeps polling at the healthy interval", 600_000L, steady.waitMs)

    println("-- a second outage retries promptly, not at the old backoff --")
    val drop = RecoveryPolicy.next(steady.state, healthy = false)
    check("demotes again", RecoveryPolicy.Action.DEMOTE, drop.action)
    check("backoff restarts at 30s", 30_000L, drop.waitMs)

    println("-- jitter --")
    check("jitter added", 30_000L + 1_500L,
        RecoveryPolicy.next(RecoveryPolicy.State(true), healthy = false, jitterMs = 1_500).waitMs)
    check("jitter clamped", 30_000L + RecoveryPolicy.MAX_JITTER_MS,
        RecoveryPolicy.next(RecoveryPolicy.State(true), healthy = false, jitterMs = 99_999).waitMs)
    check("negative jitter ignored", 30_000L,
        RecoveryPolicy.next(RecoveryPolicy.State(true), healthy = false, jitterMs = -5).waitMs)

    println("-- waitMs bounds --")
    check("negative attempt clamped", 30_000L, RecoveryPolicy.waitMs(false, -3))
    check("huge attempt clamped", 900_000L, RecoveryPolicy.waitMs(false, 9_999))

    println("-- playback failure integration --")
    val coordinator = SourceRecoveryCoordinator(initiallyPrimary = true)
    coordinator.markPlaybackUnavailable()
    val recoveryCheck = coordinator.beginCheck(actualPrimaryActive = false)
    val recovered = coordinator.decide(recoveryCheck, healthy = true)
    check("playback failure promotes on next successful check", RecoveryPolicy.Action.PROMOTE, recovered.step.action)
    check("promotion remains valid", true, coordinator.promotionStillValid(recoveryCheck))
    val postRecovery = coordinator.beginCheck(actualPrimaryActive = true)
    check("successful recovery promotes exactly once", RecoveryPolicy.Action.NONE,
        coordinator.decide(postRecovery, healthy = true).step.action)

    println("-- in-flight recovery invalidation --")
    val raced = SourceRecoveryCoordinator(initiallyPrimary = true)
    val staleCheck = raced.beginCheck(actualPrimaryActive = true)
    raced.markPlaybackUnavailable()
    val staleDecision = raced.decide(staleCheck, healthy = true)
    check("new read failure supersedes old health result", true, staleDecision.superseded)
    check("superseded result stays demoted", false, staleDecision.step.state.primaryActive)
    check("superseded result retries promptly", 30_000L, staleDecision.step.waitMs)

    println("-- failed recovery indexing retries --")
    val failedPromotion = SourceRecoveryCoordinator(initiallyPrimary = false)
    val firstPromotion = failedPromotion.beginCheck(actualPrimaryActive = false)
    check("healthy probe initially promotes", RecoveryPolicy.Action.PROMOTE,
        failedPromotion.decide(firstPromotion, healthy = true).step.action)
    failedPromotion.markPlaybackUnavailable()
    val retryPromotion = failedPromotion.beginCheck(actualPrimaryActive = false)
    check("failed index can promote again", RecoveryPolicy.Action.PROMOTE,
        failedPromotion.decide(retryPromotion, healthy = true).step.action)
}
