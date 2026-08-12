from pathlib import Path

helper = Path(__file__).with_name("collage_feature_patch.py")
text = helper.read_text()
old = "rep(prep,OLD_BLUR,NEW_BLUR)"
new = '''t=p(prep).read_text()
s=t.index("                is PreparedSlide.Collage -> {\\n                    val gapPx = when (collageGap) {")
e=t.index("\\n            }\\n\\n            val pixels = IntArray", s)
p(prep).write_text(t[:s]+NEW_BLUR+t[e:])'''
if text.count(old) != 1:
    raise RuntimeError(f"expected one blur helper line, got {text.count(old)}")
helper.write_text(text.replace(old, new, 1))
