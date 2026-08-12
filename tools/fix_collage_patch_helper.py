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
helper.write_text(text.replace(old, new, 1))
