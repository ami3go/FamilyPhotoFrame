
fun runSynologyChecks() {
  println("-- normalizeBaseUrl --")
  check("adds https", "https://nas.local:5001", SynologyApi.normalizeBaseUrl("nas.local:5001"))
  check("keeps http", "http://nas.local", SynologyApi.normalizeBaseUrl("http://nas.local"))
  check("strips slash", "https://nas.local", SynologyApi.normalizeBaseUrl("https://nas.local/"))

  println("-- auth URL + encoding --")
  val au = SynologyApi.buildAuthUrl("nas.local", "me", "p@ss word", "123456")
  check("encodes password", true, au.contains("passwd=p%40ss%20word"))
  check("has otp", true, au.contains("otp_code=123456"))
  check("format=sid", true, au.contains("format=sid"))
  check("no otp when blank", false, SynologyApi.buildAuthUrl("n","u","p").contains("otp_code"))

  println("-- sid redaction --")
  check("redacts", "https://x/a?path=b&_sid=REDACTED&z=1",
     SynologyApi.redactSid("https://x/a?path=b&_sid=SECRET123&z=1"))

  println("-- success / error parsing --")
  check("success true", true, SynologyApi.isSuccess("""{"success":true,"data":{}}"""))
  check("success false", false, SynologyApi.isSuccess("""{"success":false,"error":{"code":403}}"""))
  check("error code", 403, SynologyApi.errorCode("""{"success":false,"error":{"code":403}}"""))
  check("no error obj", null, SynologyApi.errorCode("""{"success":true,"data":{"code":9}}"""))
  check("sid", "abc123", SynologyApi.parseSid("""{"success":true,"data":{"sid":"abc123"}}"""))
  check("sid null on failure", null, SynologyApi.parseSid("""{"success":false,"error":{"code":400}}"""))

  println("-- error mapping --")
  check("400 auth", SourceError.AuthFailed, SynologyApi.mapError(400))
  check("403 2fa", SourceError.TwoFactorRequired, SynologyApi.mapError(403))
  check("119 expired", SourceError.SessionExpired, SynologyApi.mapError(119))
  check("407 blocked", SourceError.PermissionDenied, SynologyApi.mapError(407))
  check("414 gone", SourceError.FileGone, SynologyApi.mapError(414))
  check("unknown", SourceError.ProtocolError, SynologyApi.mapError(9999))
  check("null", SourceError.ProtocolError, SynologyApi.mapError(null))

  println("-- list page parsing --")
  val listJson = """
  {"data":{"total":3,"offset":0,"files":[
    {"path":"/photo/a b.jpg","name":"a b.jpg","isdir":false,
     "additional":{"size":1234,"time":{"mtime":1700000000}}},
    {"path":"/photo/sub","name":"sub","isdir":true,
     "additional":{"size":0,"time":{"mtime":1600000000}}},
    {"path":"/photo/c\/d.png","name":"d.png","isdir":false,
     "additional":{"size":99,"time":{"mtime":1500000000}}}
  ]},"success":true}"""
  val page = SynologyApi.parseListPage(listJson)
  check("parsed", true, page != null)
  check("total", 3, page?.total)
  check("count", 3, page?.entries?.size)
  check("name w/ space", "a b.jpg", page?.entries?.get(0)?.name)
  check("size", 1234L, page?.entries?.get(0)?.sizeBytes)
  check("mtime->ms", 1700000000000L, page?.entries?.get(0)?.modifiedEpochMs)
  check("dir flagged", true, page?.entries?.get(1)?.isDir)
  check("file not dir", false, page?.entries?.get(0)?.isDir)
  check("unescapes slash", "/photo/c/d.png", page?.entries?.get(2)?.path)
  check("failure -> null", null, SynologyApi.parseListPage("""{"success":false,"error":{"code":119}}"""))
  check("empty files", 0, SynologyApi.parseListPage("""{"success":true,"data":{"total":0,"files":[]}}""")?.entries?.size)

  println("-- thumbnail/download URLs --")
  check("thumb has size", true, SynologyApi.buildThumbnailUrl("n","s","/p/x.jpg").contains("size=large"))
  check("download mode", true, SynologyApi.buildDownloadUrl("n","s","/p/x.jpg").contains("mode=open"))
  check("path encoded", true, SynologyApi.buildDownloadUrl("n","s","/p/a b.jpg").contains("%2Fp%2Fa%20b.jpg"))

  
}
