from pathlib import Path

helper = Path(__file__).with_name("collage_feature_patch.py")
text = helper.read_text()
old = "rep(prep,OLD_BLUR,NEW_BLUR)"
new = '''t=p(prep).read_text()
s=t.index("                is PreparedSlide.Collage -> {\\n                    val gapPx = when (collageGap) {")
e=t.index("\\n            }\\n\\n            val pixels = IntArray", s)
adaptive = """                is PreparedSlide.Collage -> {
                    val gapPx = when (collageGap) {
                        CollageGap.NONE -> 0f
                        CollageGap.SMALL -> 4f * scale
                        CollageGap.MEDIUM -> 8f * scale
                    }
                    val destinations = collageDestinationRects(
                        slide.layout, width.toFloat(), height.toFloat(), gapPx,
                    )
                    slide.tiles.zip(destinations).forEach { (tile, destination) ->
                        drawCrop(tile.bitmap, destination)
                    }
                }"""
p(prep).write_text(t[:s]+adaptive+t[e:])'''
if text.count(old) != 1:
    raise RuntimeError(f"expected one blur helper line, got {text.count(old)}")
text = text.replace(old, new, 1)

# The generated prepareSlide loop returns directly from every terminal path. Keeping the
# outer construct as `return try { while (true) ... }` makes Kotlin type the while-body as
# Unit and widen the whole try expression to Any. Use normal try/catch control flow instead.
old_tail_write = 'p(prep).write_text(t[:i]+PREP_TAIL)'
new_tail_write = '''p(prep).write_text(t[:i]+PREP_TAIL)
t=p(prep).read_text()
t=t.replace(
    "    return try {\\n        val startedAt = SystemClock.elapsedRealtime()",
    "    try {\\n        val startedAt = SystemClock.elapsedRealtime()",
    1,
)
t=t.replace(
    "    } catch (_: OutOfMemoryError) {\\n        PrepareSlideResult.Failed(",
    "    } catch (_: OutOfMemoryError) {\\n        return PrepareSlideResult.Failed(",
    1,
)
t=t.replace(
    "    } catch (e: Exception) {\\n        PrepareSlideResult.Failed(",
    "    } catch (e: Exception) {\\n        return PrepareSlideResult.Failed(",
    1,
)
p(prep).write_text(t)'''
if text.count(old_tail_write) != 1:
    raise RuntimeError(f"expected one prepare tail write, got {text.count(old_tail_write)}")
text = text.replace(old_tail_write, new_tail_write, 1)
helper.write_text(text)

# Pre-existing instrumentation fixture drift: EligibleFolder has `key`, not `folderKey`.
# Keep EligiblePhotoMember.folderKey assertions untouched.
root = helper.parents[1]
test = root / "app/src/androidTest/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleEligibilityProviderTest.kt"
test_text = test.read_text()
test_text = test_text.replace(
    'val localTrip = snapshot.folders.single {\n            it.folderKey == FolderKey("local", "Trip")',
    'val localTrip = snapshot.folders.single {\n            it.key == FolderKey("local", "Trip")',
    1,
)
test_text = test_text.replace(
    'snapshot.folders.single { it.folderKey == FolderKey("local", "Other") }',
    'snapshot.folders.single { it.key == FolderKey("local", "Other") }',
    1,
)
test_text = test_text.replace(
    'snapshot.folders.single { it.folderKey == FolderKey("nas", "Trip") }',
    'snapshot.folders.single { it.key == FolderKey("nas", "Trip") }',
    1,
)
test.write_text(test_text)
