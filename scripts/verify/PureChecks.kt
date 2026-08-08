import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

var failures = 0
fun check(name: String, expected: Any?, actual: Any?) {
    if (expected == actual) println("  PASS  $name")
    else { failures++; println("  FAIL  $name\n        expected=<$expected>\n        actual  =<$actual>") }
}

fun runExifChecks() {
    println("-- parseExifDateTime --")
    check("explicit offset",
        OffsetDateTime.of(2024,7,4,8,30,0,0, ZoneOffset.ofHours(2)).toInstant().toEpochMilli(),
        ExifParsing.parseExifDateTime("2024:07:04 08:30:00", "+02:00"))
    check("no offset uses given zone",
        LocalDateTime.of(2024,7,4,8,30,0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli(),
        ExifParsing.parseExifDateTime("2024:07:04 08:30:00", null, ZoneOffset.UTC))
    check("null", null, ExifParsing.parseExifDateTime(null))
    check("empty", null, ExifParsing.parseExifDateTime(""))
    check("blank", null, ExifParsing.parseExifDateTime("   "))
    check("zeroed placeholder", null, ExifParsing.parseExifDateTime("0000:00:00 00:00:00"))
    check("garbage text", null, ExifParsing.parseExifDateTime("not a date"))
    check("wrong separator", null, ExifParsing.parseExifDateTime("2024-07-04 08:30:00"))
    check("garbage offset falls back",
        LocalDateTime.of(2024,7,4,8,30,0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli(),
        ExifParsing.parseExifDateTime("2024:07:04 08:30:00", "nonsense", ZoneOffset.UTC))

    println("-- cleanCaption --")
    check("trims", "Family reunion", ExifParsing.cleanCaption("  Family reunion  "))
    check("null", null, ExifParsing.cleanCaption(null))
    check("blank", null, ExifParsing.cleanCaption("   "))
    check("dashes", null, ExifParsing.cleanCaption("----"))
    check("underscores", null, ExifParsing.cleanCaption("___"))
    check("truncates to 280", 280, ExifParsing.cleanCaption("x".repeat(500))?.length)

    println("-- formatGpsCoordinate --")
    check("NW", "40.7128°N, 74.0060°W", ExifParsing.formatGpsCoordinate(40.7128, -74.0060))
    check("SE", "33.8688°S, 151.2093°E", ExifParsing.formatGpsCoordinate(-33.8688, 151.2093))
    check("origin", "0.0000°N, 0.0000°E", ExifParsing.formatGpsCoordinate(0.0, 0.0))

    println("-- locale independence (the v24 fix) --")
    val orig = Locale.getDefault()
    try {
        Locale.setDefault(Locale.GERMANY)
        check("German locale keeps dot", "40.7128°N, 74.0060°W",
            ExifParsing.formatGpsCoordinate(40.7128, -74.0060))
    } finally { Locale.setDefault(orig) }

    
}
