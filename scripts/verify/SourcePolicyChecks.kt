fun runSourcePolicyChecks() {
  println("-- remote media-cache routing --")
  check("SMB requires cache", true, BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.SMB))
  check("Synology requires cache", true, BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.SYNOLOGY))
  check("SAF stays direct", false, BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.LOCAL_SAF))
  check("fallback stays direct", false, BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.FALLBACK))

  println("-- credential identity policy --")
  val smb = SmbSettings("NAS.local", "/Photos/", "Family", "frame", "WORKGROUP")
  val equivalent = smb.copy(host = "nas.LOCAL", share = "Photos", path = "Other")
  check("SMB path does not change credential scope", true, CredentialPolicy.sameSmbScope(smb, equivalent))
  check("SMB ref stable across harmless formatting", CredentialPolicy.smbRef(smb), CredentialPolicy.smbRef(equivalent))
  check("SMB user isolates secret", false, CredentialPolicy.smbRef(smb) == CredentialPolicy.smbRef(smb.copy(user = "other")))
  check("SMB domain isolates secret", false, CredentialPolicy.smbRef(smb) == CredentialPolicy.smbRef(smb.copy(domain = "OTHER")))

  val syn = SynologySettings("https://NAS.local/", user = "frame")
  val synEquivalent = syn.copy(baseUrl = "https://nas.LOCAL", folderPath = "/photo/Other")
  check("Synology folder does not change credential scope", true, CredentialPolicy.sameSynologyScope(syn, synEquivalent))
  check("Synology ref stable across URL formatting", CredentialPolicy.synologyRef(syn), CredentialPolicy.synologyRef(synEquivalent))
  check("Synology user isolates secret", false, CredentialPolicy.synologyRef(syn) == CredentialPolicy.synologyRef(syn.copy(user = "other")))

  val dav = WebDavSettings(baseUrl = "https://CLOUD.example/", user = "alice")
  val davEquivalent = dav.copy(baseUrl = "https://cloud.EXAMPLE", folderPath = "/Pics", rootPath = "/other")
  check("WebDAV root/folder do not change credential scope", true, CredentialPolicy.sameWebDavScope(dav, davEquivalent))
  check("WebDAV ref stable across URL formatting", CredentialPolicy.webDavRef(dav), CredentialPolicy.webDavRef(davEquivalent))
  check("WebDAV user isolates secret", false, CredentialPolicy.webDavRef(dav) == CredentialPolicy.webDavRef(dav.copy(user = "bob")))
  check("WebDAV host isolates secret", false, CredentialPolicy.webDavRef(dav) == CredentialPolicy.webDavRef(dav.copy(baseUrl = "https://other.example")))
  check("different host is a different scope", false, CredentialPolicy.sameWebDavScope(dav, dav.copy(baseUrl = "https://other.example")))
}
