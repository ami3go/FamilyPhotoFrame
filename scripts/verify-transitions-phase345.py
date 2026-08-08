#!/usr/bin/env python3
"""Offline Phase 3-5 source, pure-logic, and diagnostics verification."""
from __future__ import annotations

import json
import os
import subprocess
import tempfile
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda rel: (root / rel).read_text(encoding="utf-8")
settings = read("app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt")
selector = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionSelector.kt")
performance = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionPerformance.kt")
renderer = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/SlideshowTransitionRenderer.kt")
screen = "".join(p.read_text() for p in sorted((root / "app/src/main/java/com/example/familyphotoframe/ui/slideshow").glob("Slideshow*.kt")))
settings_dir = root / "app/src/main/java/com/example/familyphotoframe/ui/settings"
ui = "".join(p.read_text() for p in sorted(settings_dir.glob("Settings*.kt")))
web = (read("app/src/main/java/com/example/familyphotoframe/web/SetupPage.kt") +
       read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt") +
       read("app/src/main/java/com/example/familyphotoframe/web/WebUiCss.kt") +
       read("app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt"))
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
model = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionModel.kt")

EFFECTS = {
    "CROSSFADE": "crossfade", "SOFT_DISSOLVE": "soft_dissolve",
    "GENTLE_ZOOM_IN": "gentle_zoom_in", "GENTLE_ZOOM_OUT": "gentle_zoom_out",
    "HORIZONTAL_GLIDE": "horizontal_glide", "VERTICAL_GLIDE": "vertical_glide",
    "DEPTH_FADE": "depth_fade", "KEN_BURNS_HANDOFF": "ken_burns_handoff",
    "SOFT_REVEAL": "soft_reveal", "SOFT_FOCUS_FADE": "soft_focus_fade",
}
for enum, stable in EFFECTS.items():
    assert enum in settings, f"missing {enum}"
    assert stable in settings, f"missing stable id {stable}"
    assert stable in web, f"missing web option {stable}"
    assert f"TransitionMode.{enum}" in renderer, f"missing renderer support {enum}"

assert "TransitionSelectionMode.AMBIENT_RANDOM" in ui
assert "mode == TransitionSelectionMode.FIXED" in selector
assert "ambientRandomValues" in selector
assert "SOFT_REVEAL" not in settings[settings.index("val ambientRandomValues"):settings.index("fun fromStorage")]
assert "SOFT_FOCUS_FADE" not in settings[settings.index("val ambientRandomValues"):settings.index("fun fromStorage")]
assert "reducedMotion" in selector and "isOpacityOnly" in selector
assert "fallbackChain" in selector and "low_performance_mode" in selector
assert "CompositingStrategy.Offscreen" in renderer and "BlendMode.DstIn" in renderer
assert "runtimeShader" not in renderer.lower() and "RenderEffect" not in renderer
assert "SOFT_FOCUS_DIMENSION_SCALE = 0.25f" in screen
assert "catch (c: CancellationException)" in screen and "throw c" in screen
assert "transitionBlurredBitmap" in screen
assert "releaseOwnedResources" not in screen, "display-bound bitmaps must not be manually recycled"
assert "preparedSlideCount" in model and "startLatencyMs" in model
assert "TRANSITION_LOW_PERFORMANCE_ENTERED" in screen
assert "TRANSITION_LOW_PERFORMANCE_EXITED" in screen
assert "TransitionFrameSampler" in screen and "TransitionPerformanceController" in screen
assert "syncTransitionMode" in web
assert "transitionSelectionMode" in vm and "transitionReduceMotion" in vm
assert "if (state.transitionSelectionMode == TransitionSelectionMode.FIXED)" in ui
assert "AnimatedContent(" not in screen
assert "allowDisplayMotion = false" in screen, "display-time Ken Burns must pause during transitions"
assert "deferredRelease" not in screen, "GC owns bitmaps after they have been handed to Compose"
assert "backdrop?.takeIf { !it.isRecycled }?.recycle()" not in screen

# Compile and execute the actual selector/performance production files against a tiny
# settings stub. This catches type errors and validates 1,000 deterministic selections.
kotlinc_env = os.environ.get("KOTLINC")
kotlinc_path = kotlinc_env or shutil.which("kotlinc")
kotlinc = Path(kotlinc_path) if kotlinc_path else None
if kotlinc is not None and kotlinc.exists():
    with tempfile.TemporaryDirectory(prefix="fpf-transitions-") as td:
        td = Path(td)
        stub = td / "Settings.kt"
        stub.write_text('''package com.example.familyphotoframe.data.settings

enum class TransitionSelectionMode(val storageValue: String) { FIXED("fixed"), AMBIENT_RANDOM("ambient_random") }
enum class TransitionMode(val storageValue: String, val durationMultiplier: Float) {
 CROSSFADE("crossfade",1f), SOFT_DISSOLVE("soft_dissolve",1.35f),
 GENTLE_ZOOM_IN("gentle_zoom_in",1.15f), GENTLE_ZOOM_OUT("gentle_zoom_out",1.15f),
 HORIZONTAL_GLIDE("horizontal_glide",1f), VERTICAL_GLIDE("vertical_glide",1f),
 DEPTH_FADE("depth_fade",1.1f), KEN_BURNS_HANDOFF("ken_burns_handoff",1.5f),
 SOFT_REVEAL("soft_reveal",1.2f), SOFT_FOCUS_FADE("soft_focus_fade",1.25f);
 val isOpacityOnly get()=this==CROSSFADE||this==SOFT_DISSOLVE
 val isMotionHeavy get()=this in setOf(HORIZONTAL_GLIDE,VERTICAL_GLIDE,KEN_BURNS_HANDOFF)
 companion object {
  val selectableValues=entries
  val ambientRandomValues=listOf(CROSSFADE,SOFT_DISSOLVE,GENTLE_ZOOM_IN,GENTLE_ZOOM_OUT,HORIZONTAL_GLIDE,VERTICAL_GLIDE,DEPTH_FADE,KEN_BURNS_HANDOFF)
 }
}
''', encoding="utf-8")
        driver = td / "Driver.kt"
        driver.write_text('''import com.example.familyphotoframe.data.settings.*
import com.example.familyphotoframe.ui.slideshow.transition.*
import kotlin.random.Random
fun main(){
 val s=TransitionSelector(Random(1234)); val out=mutableListOf<TransitionMode>()
 repeat(1000){ out += s.select(TransitionSelectionMode.AMBIENT_RANDOM,TransitionMode.CROSSFADE,TransitionMode.selectableValues.toSet(),false,false).effect }
 check(out.zipWithNext().none{it.first==it.second})
 check(out.windowed(3).none{w->w.all{it.isMotionHeavy}})
 check(TransitionMode.SOFT_REVEAL !in out && TransitionMode.SOFT_FOCUS_FADE !in out)
 val c=TransitionPerformanceController(); val bad=TransitionFrameMetrics(10,3,50_000_000); val good=TransitionFrameMetrics(10,0,16_000_000)
 listOf(bad,good,bad,good).forEach{check(c.record(it).stateChange==TransitionPerformanceStateChange.NONE)}
 check(c.record(bad).stateChange==TransitionPerformanceStateChange.ENTERED_LOW_PERFORMANCE)
 repeat(19){c.record(good)}; check(c.isLowPerformanceMode); check(c.record(good).stateChange==TransitionPerformanceStateChange.EXITED_LOW_PERFORMANCE)
 println("transition selector/performance 1000-cycle checks passed")
}
''', encoding="utf-8")
        jar = td / "checks.jar"
        subprocess.run([
            str(kotlinc), str(stub),
            str(root / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionSelector.kt"),
            str(root / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionPerformance.kt"),
            str(driver), "-include-runtime", "-d", str(jar), "-nowarn",
        ], check=True)
        subprocess.run(["java", "-jar", str(jar)], check=True)
else:
    print("note: kotlinc unavailable; source checks only")

# Generate complete synthetic Phase-5 evidence and verify the real diagnostics analyser.
with tempfile.TemporaryDirectory(prefix="fpf-transition-log-") as td:
    path = Path(td) / "transitions.jsonl"
    effects = list(EFFECTS.values())
    t = 1_000_000
    lines = []
    for i in range(1000):
        effect = effects[i % len(effects)]
        lines.append({"t": t, "sid": "test", "cat": "ENGINE", "code": "TRANSITION_COMPLETED",
                      "configuredMode": "fixed", "configuredEffect": effect, "resolvedEffect": effect,
                      "durationMs": "900", "frameCount": "55", "slowFrameCount": "1",
                      "maximumFrameMs": "24", "startLatencyMs": "8", "preparedSlideCount": "2",
                      "activeDecodedBytes": "16777216", "fallbackUsed": "false"})
        if i % 10 == 0:
            lines.append({"t": t, "sid": "test", "cat": "MEMORY", "code": "HEAP_SAMPLE",
                          "heapUsedKb": str(50000 + i * 2), "heapMaxKb": "262144"})
        t += 4_000
    path.write_text("\n".join(json.dumps(x) for x in lines) + "\n", encoding="utf-8")
    result = subprocess.run(["python3", str(root / "scripts/analyze-transition-diagnostics.py"), str(path)], text=True, capture_output=True)
    print(result.stdout, end="")
    if result.returncode != 0:
        print(result.stderr, end="")
        raise SystemExit(f"transition diagnostics analyser failed with {result.returncode}")

print("transition phases 3-5 verification passed")
