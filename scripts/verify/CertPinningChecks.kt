
fun runCertPinningChecks() {
  println("-- formatFingerprint --")
  check("hex pairs", "00:0F:AB:FF", CertPinning.formatFingerprint(byteArrayOf(0, 15, 0xAB.toByte(), 0xFF.toByte())))
  check("empty", "", CertPinning.formatFingerprint(byteArrayOf()))

  println("-- normalize --")
  check("strips colons", "ABCD", CertPinning.normalize("AB:CD"))
  check("uppercases", "ABCD", CertPinning.normalize("ab:cd"))
  check("strips spaces", "ABCD", CertPinning.normalize("ab cd"))
  check("null", "", CertPinning.normalize(null))

  println("-- matches (security-critical) --")
  val fp = "AB:CD:" + "11:".repeat(29) + "22"
  check("same value", true, CertPinning.matches(fp, fp))
  check("format-insensitive", true, CertPinning.matches(fp.lowercase(), fp.replace(":", "")))
  check("different value", false, CertPinning.matches(fp, fp.replaceFirst("AB", "AC")))
  check("blank pin never matches", false, CertPinning.matches("", fp))
  check("null pin never matches", false, CertPinning.matches(null, fp))
  check("blank actual never matches", false, CertPinning.matches(fp, ""))
  check("both blank never matches", false, CertPinning.matches("", ""))
  check("prefix is not a match", false, CertPinning.matches("ABCD", "ABCDEF"))

  println("-- isValidSha256 --")
  check("64 hex chars ok", true, CertPinning.isValidSha256("A".repeat(64)))
  check("colon form ok", true, CertPinning.isValidSha256((1..32).joinToString(":") { "AB" }))
  check("too short", false, CertPinning.isValidSha256("ABCD"))
  check("null", false, CertPinning.isValidSha256(null))

  
}
