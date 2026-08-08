# FamilyPhotoFrame — Modern Slideshow Transition Effects

**Document ID:** FPF-FEAT-TRANSITIONS-001  
**Version:** 1.1  
**Status:** Implementation-ready  
**Target application:** FamilyPhotoFrame  
**Primary hardware target:** Huawei PLK-L01, Android 6 / API 23  
**Related baseline:** v50.3.1 or later, including proactive image preload and portrait-collage playback

---

## 1. Purpose

Implement a modern slideshow transition system for FamilyPhotoFrame.

The system shall provide:

- ten distinct visual transition effects;
- one curated random-selection mode;
- compatibility with single-photo and portrait-collage presentations;
- no visible loading or black frame between presentations;
- bounded memory usage;
- reliable operation on Android 6 / API 23;
- synchronized Android and web settings;
- diagnostics suitable for long-duration validation.

The transition system must animate only content that has already been resolved, downloaded, decoded, and prepared by the slideshow preload pipeline.

---

## 2. Scope

### 2.1 In scope

- Transition model and persistence.
- Transition state machine.
- Ten visual transition effects.
- Ambient Random selection mode.
- Android settings.
- Paired web settings.
- Reduced-motion mode.
- Performance fallback policy.
- Diagnostics.
- Unit, UI, integration, and endurance tests.
- Compatibility with:
  - landscape single-photo slides;
  - portrait single-photo fallback slides;
  - two-photo portrait collages;
  - three-photo portrait collages.

### 2.2 Out of scope

- Continuous pan and zoom during the normal photo-display interval.
- Face-aware crop selection.
- Per-tile collage animation.
- Audio synchronization.
- User-created transition sequences.
- Online downloading of transition assets.
- Runtime shader effects that require Android versions newer than API 23.

---

## 3. Definitions

### 3.1 Prepared slide

A complete presentation that is ready to render without further I/O or decoding.

```kotlin
sealed interface PreparedSlide {
    val presentationId: Long
    val photoIds: List<Long>

    data class Single(
        override val presentationId: Long,
        override val photoIds: List<Long>,
        val visual: PreparedVisual,
    ) : PreparedSlide

    data class Collage(
        override val presentationId: Long,
        override val photoIds: List<Long>,
        val tiles: List<PreparedTile>,
        val layout: CollageLayout,
    ) : PreparedSlide
}
```

A transition renderer must never:

- access SMB;
- query Room;
- decode an image;
- generate EXIF metadata;
- resolve a URI;
- select slideshow items;
- mutate shuffle history.

### 3.2 Committed slide

The slide currently considered active by the slideshow coordinator. Only the coordinator may change the committed slide.

### 3.3 Visual effect

A concrete animation applied between an outgoing and an incoming prepared slide.

### 3.4 Selection mode

A policy that resolves to a concrete visual effect. `Ambient Random` is a selection mode, not one of the ten visual effects.

---

## 4. Required visual effects

The application shall implement exactly ten visual effects.

### 4.1 Crossfade

The outgoing slide fades out while the incoming slide fades in.

**Parameters**

- Incoming alpha: `0.0 → 1.0`
- Outgoing alpha: `1.0 → 0.0`
- Incoming easing: `FastOutSlowIn`
- Outgoing easing: `LinearOutSlowIn`
- Duration multiplier: `1.00`

**Purpose**

- Default and safest transition.
- Required fallback for all transition failures.

---

### 4.2 Soft Dissolve

A slower opacity blend in which the outgoing slide remains partially visible until late in the transition.

**Parameters**

- Incoming alpha: `0.0 → 1.0`
- Outgoing alpha:
  - progress `0.0–0.75`: `1.0 → 0.35`
  - progress `0.75–1.0`: `0.35 → 0.0`
- Incoming easing: `LinearOutSlowIn`
- Outgoing easing: `Linear`
- Duration multiplier: `1.35`

**Restriction**

Do not implement pixel-noise, checkerboard, or block-based dissolve.

---

### 4.3 Gentle Zoom In

The incoming slide starts slightly smaller and settles at normal scale while fading in.

**Parameters**

- Incoming scale: `0.965 → 1.000`
- Incoming alpha: `0.0 → 1.0`
- Outgoing alpha: `1.0 → 0.0`
- Easing: `FastOutSlowIn`
- Duration multiplier: `1.15`

**Coverage rule**

The outgoing slide must remain fully covering the viewport until the incoming slide covers it. The application background must not become visible around the scaled incoming slide.

---

### 4.4 Gentle Zoom Out

The incoming slide starts slightly enlarged and settles to normal scale while fading in.

**Parameters**

- Incoming scale: `1.040 → 1.000`
- Incoming alpha: `0.0 → 1.0`
- Outgoing alpha: `1.0 → 0.0`
- Easing: `FastOutSlowIn`
- Duration multiplier: `1.15`

---

### 4.5 Horizontal Glide

The incoming slide moves gently from right to center while the outgoing slide shifts slightly left.

**Parameters**

- Incoming translation X: `+8% viewport width → 0`
- Outgoing translation X: `0 → -4% viewport width`
- Incoming alpha: `0.15 → 1.0`
- Outgoing alpha: `1.0 → 0.35`
- Incoming easing: `FastOutSlowIn`
- Outgoing easing: `FastOutLinearIn`
- Duration multiplier: `1.00`

**Coverage rule**

At every frame, outgoing or incoming prepared content must cover the complete viewport.

---

### 4.6 Vertical Glide

The incoming slide moves gently upward from below while the outgoing slide shifts slightly upward.

**Parameters**

- Incoming translation Y: `+6% viewport height → 0`
- Outgoing translation Y: `0 → -3% viewport height`
- Incoming alpha: `0.15 → 1.0`
- Outgoing alpha: `1.0 → 0.35`
- Incoming easing: `FastOutSlowIn`
- Outgoing easing: `FastOutLinearIn`
- Duration multiplier: `1.00`

---

### 4.7 Depth Fade

The outgoing slide appears to recede while the incoming slide settles above it.

**Parameters**

Outgoing:

- Scale: `1.000 → 0.970`
- Alpha: `1.000 → 0.200`

Incoming:

- Scale: `1.020 → 1.000`
- Alpha: `0.000 → 1.000`

**Easing**

- Incoming: `FastOutSlowIn`
- Outgoing: `LinearOutSlowIn`
- Duration multiplier: `1.10`

**Restrictions**

- No perspective rotation.
- No simulated 3D card flip.
- No Z-axis rotation.

---

### 4.8 Ken Burns Handoff

A restrained pan-and-zoom handoff used only during the transition.

**Important**

This is not continuous Ken Burns playback. Motion must stop when the transition completes. The slide remains static for the normal display interval.

**Parameters**

Outgoing:

- Scale: `1.020 → 1.040`
- Translation: `0 → -1.5%` in the selected direction
- Alpha: `1.0 → 0.25`

Incoming:

- Scale: `1.040 → 1.020`
- Translation: `+1.5% → 0`
- Alpha: `0.0 → 1.0`

**Directions**

- left-to-right;
- right-to-left;
- top-to-bottom;
- bottom-to-top.

Direction selection must use the same seeded random source as transition selection.

**Duration multiplier:** `1.50`

---

### 4.9 Soft Reveal

The incoming slide is revealed using a broad, soft-edged horizontal boundary.

**Primary API 23 implementation**

1. Draw outgoing and incoming slides as stacked full-screen layers.
2. Clip the incoming layer using an animated rectangular boundary.
3. Draw a fixed-width translucent gradient over the reveal edge.
4. Animate left-to-right.
5. Do not allocate a new bitmap for each frame.
6. Do not use runtime shaders.

**Parameters**

- Reveal boundary: `0% → 100% viewport width`
- Feather width: `10% viewport width`
- Duration multiplier: `1.20`
- Easing: `FastOutSlowIn`

**Fallback**

```text
Soft Reveal → Horizontal Glide → Crossfade
```

---

### 4.10 Soft Focus Fade

The outgoing slide becomes subtly blurred while fading out, and the incoming slide becomes sharp while fading in.

**API 23 implementation**

- Use pre-generated, low-resolution blurred layers.
- Blur layers must be created during prepared-slide construction, not during animation.
- Do not generate a new blur level for every frame.
- Animate opacity between:
  - sharp outgoing;
  - blurred outgoing;
  - blurred incoming;
  - sharp incoming.

**Parameters**

- Maximum visual blur radius: approximately `8–12 dp`
- Duration multiplier: `1.25`
- Easing: `LinearOutSlowIn`

**Fallback**

```text
Soft Focus Fade → Soft Dissolve → Crossfade
```

**Memory restriction**

Blurred layers may not increase retained transition memory by more than one additional quarter-resolution bitmap per active prepared slide.

---

## 5. Ambient Random selection mode

`Ambient Random` selects from supported visual effects. It is not counted among the ten visual effects.

### 5.1 Default curated pool

- Crossfade
- Soft Dissolve
- Gentle Zoom In
- Gentle Zoom Out
- Horizontal Glide
- Vertical Glide
- Depth Fade
- Ken Burns Handoff

Soft Reveal and Soft Focus Fade are excluded from the default random pool until hardware endurance testing confirms acceptable performance.

### 5.2 Weights

| Effect | Weight |
|---|---:|
| Crossfade | 22 |
| Soft Dissolve | 18 |
| Gentle Zoom In | 12 |
| Gentle Zoom Out | 12 |
| Horizontal Glide | 10 |
| Vertical Glide | 8 |
| Depth Fade | 10 |
| Ken Burns Handoff | 8 |

### 5.3 Selection rules

- Never repeat the immediately previous effect.
- Prefer effects absent from the previous three resolved effects.
- Do not select more than two motion-heavy effects consecutively.
- Motion-heavy effects:
  - Horizontal Glide
  - Vertical Glide
  - Ken Burns Handoff
- Remove unsupported effects before selection.
- With reduced motion enabled, select only:
  - Crossfade
  - Soft Dissolve
- Use an injectable seeded `Random` for deterministic tests.

```kotlin
class TransitionSelector(
    private val random: Random,
    private val historySize: Int = 3,
)
```

---

## 6. Transition identifiers and persistence

```kotlin
enum class SlideshowTransitionEffect {
    CROSSFADE,
    SOFT_DISSOLVE,
    GENTLE_ZOOM_IN,
    GENTLE_ZOOM_OUT,
    HORIZONTAL_GLIDE,
    VERTICAL_GLIDE,
    DEPTH_FADE,
    KEN_BURNS_HANDOFF,
    SOFT_REVEAL,
    SOFT_FOCUS_FADE,
}
```

```kotlin
enum class TransitionSelectionMode {
    FIXED,
    AMBIENT_RANDOM,
}
```

Persist stable strings, never enum ordinals.

### 6.1 Storage keys

```text
transition_selection_mode = "fixed" | "ambient_random"
transition_effect = "crossfade" | ...
transition_duration_ms = 900
transition_reduce_motion = false
```

### 6.2 Migration rules

For existing installations with no transition settings:

```text
selection mode = fixed
effect = crossfade
duration = 900 ms
reduce motion = false
```

Unknown values must fall back to these defaults.

Duration must be clamped to:

```text
300–2000 ms base duration
```

Backup and restore must include all transition settings.

---

## 7. Duration model

The configured duration is a base duration.

```kotlin
val resolvedDurationMs =
    (configuredBaseDurationMs * effect.durationMultiplier)
        .roundToInt()
        .coerceIn(300, 2500)
```

### 7.1 Presets

| Preset | Base duration |
|---|---:|
| Fast | 600 ms |
| Normal | 900 ms |
| Slow | 1300 ms |
| Custom | 300–2000 ms |

Default:

```text
Normal / 900 ms
```

---

## 8. Transition state machine

```kotlin
sealed interface TransitionState {
    data object Idle : TransitionState

    data class Preparing(
        val current: PreparedSlide?,
        val requestedDirection: NavigationDirection,
    ) : TransitionState

    data class Ready(
        val outgoing: PreparedSlide?,
        val incoming: PreparedSlide,
        val effect: ResolvedTransition,
    ) : TransitionState

    data class Animating(
        val outgoing: PreparedSlide?,
        val incoming: PreparedSlide,
        val effect: ResolvedTransition,
        val progress: Float,
    ) : TransitionState

    data class Committed(
        val current: PreparedSlide,
    ) : TransitionState

    data class Paused(
        val current: PreparedSlide?,
    ) : TransitionState
}
```

### 8.1 Ownership rules

- Only the slideshow coordinator may:
  - select the next slide;
  - commit an incoming slide;
  - mutate slideshow queues;
  - update shown history;
  - release prepared slides.
- The renderer:
  - receives prepared content;
  - reports completion or cancellation;
  - does not commit slides;
  - does not access repositories.
- Only one transition animation job may exist.
- Only one next-slide preparation job may exist.
- Automatic transition requests are ignored while an animation is active.
- Manual navigation has priority over automatic transition requests.

---

## 9. Playback and preload timing

```text
Transition completes
→ incoming slide is committed
→ display interval starts
→ following slide preparation starts immediately
→ display interval expires
→ if following slide is ready, transition starts
→ otherwise current slide remains visible
→ transition starts immediately when preparation succeeds
```

Preload time must not reduce the configured display interval.

At all times, the current committed slide remains visible until another complete prepared slide is available.

---

## 10. Interruption policy

### 10.1 Manual Next during preparation

- Cancel obsolete preparation.
- Keep current committed slide visible.
- Start preparation for the new target.

### 10.2 Manual Previous during preparation

Same as Manual Next, using the previous target.

### 10.3 Manual navigation during animation

- Snap the active animation to its final incoming state.
- Commit the incoming slide.
- Process at most one pending navigation action.
- Do not reverse the animation.
- Do not abandon both slides mid-transition.

### 10.4 Repeated navigation

Maintain at most one pending direction.

Rules:

```text
Next + Next + Next = one pending Next
Previous + Previous = one pending Previous
Next + Previous = no pending action
Previous + Next = no pending action
```

### 10.5 Pause during animation

- Snap to and commit the incoming slide.
- Enter paused state.
- Do not freeze between slides.

### 10.6 Application backgrounding

- Commit the last valid visible slide.
- Cancel animation and obsolete preload jobs.
- On resume:
  - retain the current slide;
  - do not replay its transition;
  - restart its remaining or full display interval according to existing slideshow policy;
  - begin preparing the following slide.

### 10.7 Cold start

When no outgoing slide exists:

- show the first prepared slide using a 300 ms Crossfade from the configured background;
- do not use motion effects;
- do not mark the slide shown until it is rendered.

---

## 11. Renderer architecture

```kotlin
@Composable
fun SlideshowTransitionRenderer(
    outgoing: PreparedSlide?,
    incoming: PreparedSlide,
    effect: ResolvedTransition,
    onTransitionFinished: () -> Unit,
    onTransitionCancelled: () -> Unit,
)
```

The renderer must:

- animate the complete prepared slide as one visual unit;
- keep collage geometry fixed;
- apply identical transition progress to every collage tile;
- avoid per-frame allocations where practical;
- use GPU alpha, translation, scale, and clipping;
- leave the final incoming state exactly:
  - alpha `1.0`;
  - scale `1.0`;
  - translation X `0`;
  - translation Y `0`;
- remove outgoing layers after completion.

---

## 12. Black-frame prevention

A black-frame failure is defined as any frame where neither outgoing nor incoming prepared content covers the complete viewport and the application background becomes visible unintentionally.

Requirements:

- Outgoing and incoming layers must overlap for the complete transition.
- Scaling below `1.0` must not expose uncovered edges.
- Translation effects must use sufficient overlap.
- Collage gaps are part of prepared content and may not change during transition.
- If incoming preparation fails, do not begin the transition.
- If rendering fails after transition start, snap back to the most recently committed valid slide and resolve Crossfade for the next attempt.

---

## 13. Resource ownership and memory

### 13.1 Ownership

- Coil-owned bitmaps and drawables must not be manually recycled.
- Application-created temporary bitmaps may be released only when no current, outgoing, incoming, preload, or cache reference exists.
- `PreparedSlide.release()` must be idempotent.
- A slide may be released only when it is no longer:
  - current;
  - outgoing;
  - incoming;
  - preloaded;
  - referenced by a transition fallback.

### 13.2 Retention limit

The normal pipeline may retain at most:

```text
current prepared slide
incoming prepared slide during animation
next preloaded slide
```

An outgoing slide may coexist temporarily with the incoming slide during animation.

Soft Focus Fade may retain one additional quarter-resolution blurred bitmap per active slide.

### 13.3 Prohibited behaviour

- No new full-screen bitmap per frame.
- No bitmap copy for basic alpha, scale, or translation.
- No unbounded transition history containing prepared slides.
- No off-screen layer retained after transition completion.
- No repeated blur generation during animation.

---

## 14. Reduced-motion mode

When enabled:

- Only Crossfade and Soft Dissolve may be resolved.
- Stored selection mode and effect remain unchanged.
- Disabling reduced motion restores the previous configured choice.
- Movement and scale effects are not rendered.
- Soft Reveal and Soft Focus Fade are excluded.
- Web and Android settings must remain synchronized.

---

## 15. Performance fallback policy

### 15.1 Failure fallback chains

```text
Soft Reveal
→ Horizontal Glide
→ Crossfade
```

```text
Soft Focus Fade
→ Soft Dissolve
→ Crossfade
```

```text
Ken Burns Handoff
→ Gentle Zoom In
→ Crossfade
```

```text
Any other failed effect
→ Crossfade
```

### 15.2 Low-performance detection

Enter low-performance mode when three of the previous five transitions meet either condition:

- more than 20% of measured frames exceed 33.3 ms; or
- any measured frame exceeds 250 ms.

### 15.3 Low-performance mode

For the next 20 transitions:

- resolve Crossfade only;
- disable Soft Reveal;
- disable Soft Focus Fade;
- disable Ken Burns Handoff;
- retain the normal bounded preload policy.

After 20 transitions, retry normal operation.

Record entry and exit in diagnostics.

---

## 16. Settings UI

Location:

```text
Settings → Playback → Transitions
```

### 16.1 Transition mode

Options:

- Fixed
- Ambient Random

### 16.2 Fixed transition effect

Available only when mode is Fixed:

- Crossfade
- Soft Dissolve
- Gentle Zoom In
- Gentle Zoom Out
- Horizontal Glide
- Vertical Glide
- Depth Fade
- Ken Burns Handoff
- Soft Reveal
- Soft Focus Fade

### 16.3 Duration

- Fast
- Normal
- Slow
- Custom slider: 300–2000 ms

### 16.4 Reduce motion

Boolean setting.

### 16.5 Preview

Optional but recommended.

Preview requirements:

- uses bundled low-resolution sample images;
- performs no SMB access;
- does not modify slideshow history;
- does not change the committed slideshow slide;
- runs only when the user selects Preview.

---

## 17. Web settings

Expose:

- transition selection mode;
- fixed transition effect;
- duration preset or custom duration;
- reduce motion.

Requirements:

- same stable storage identifiers as Android settings;
- server-side duration clamping;
- reject unknown effect values with a clear validation response;
- update active playback without application restart;
- preserve backward compatibility with older settings payloads;
- include settings in backup and restore.

---

## 18. Diagnostics

Add events:

```text
TRANSITION_SELECTED
TRANSITION_STARTED
TRANSITION_COMPLETED
TRANSITION_CANCELLED
TRANSITION_FALLBACK
TRANSITION_PERFORMANCE_WARNING
TRANSITION_LOW_PERFORMANCE_ENTERED
TRANSITION_LOW_PERFORMANCE_EXITED
```

Example:

```json
{
  "event": "TRANSITION_COMPLETED",
  "configuredMode": "ambient_random",
  "configuredEffect": "crossfade",
  "resolvedEffect": "horizontal_glide",
  "durationMs": 914,
  "outgoingType": "single",
  "incomingType": "collage_3",
  "frameCount": 29,
  "slowFrameCount": 1,
  "maximumFrameMs": 41,
  "fallbackUsed": false
}
```

Do not log every frame individually.

---

## 19. Implementation phases and gates

### Phase 1 — Framework and Crossfade

Tasks:

- Add persistence model.
- Add transition coordinator and state machine.
- Add renderer interface.
- Add Crossfade.
- Add transition lifecycle diagnostics.
- Add first-slide startup behaviour.

Gate 1:

- Crossfade works between all prepared-slide types.
- No black or uncovered frame.
- Current preload pipeline remains functional.
- Manual Next and Previous do not create concurrent transitions.

---

### Phase 2 — Basic modern effects

Implement:

- Soft Dissolve
- Gentle Zoom In
- Gentle Zoom Out
- Horizontal Glide
- Vertical Glide

Gate 2:

- All effects work on API 23.
- Final transforms return exactly to neutral.
- Collage tiles remain synchronized.
- No new image decoding occurs during animation.

---

### Phase 3 — Advanced effects

Implement:

- Depth Fade
- Ken Burns Handoff
- Soft Reveal
- Soft Focus Fade

Gate 3:

- Each advanced effect has a working fallback chain.
- Soft Reveal uses clip and gradient only.
- Soft Focus Fade uses pre-generated low-resolution blur layers.
- Memory limits remain bounded.

---

### Phase 4 — Selection and settings

Tasks:

- Add Ambient Random selector.
- Add weighted selection and history.
- Add reduced-motion mode.
- Add Android settings.
- Add web settings.
- Add backup and restore fields.
- Add optional preview.

Gate 4:

- Settings apply without restart.
- Unknown persisted values fall back safely.
- Seeded random tests are deterministic.
- Reduced-motion mode resolves only opacity effects.

---

### Phase 5 — Performance and endurance

Tasks:

- Add frame timing.
- Add low-performance policy.
- Run long-duration hardware test.
- Analyze heap, PSS, cache, fallbacks, and transition timing.

Gate 5:

- 1,000-transition test completes without crash, ANR, OOM, or black frame.
- Performance and memory acceptance criteria pass.

---

## 20. Required tests

### 20.1 Unit tests

- Stable storage mapping for every mode and effect.
- Unknown mode fallback.
- Unknown effect fallback.
- Duration clamp.
- Duration multiplier calculation.
- Ambient Random immediate-repeat prevention.
- Ambient Random three-item history.
- Motion-heavy limit.
- Supported-effect filtering.
- Reduced-motion filtering.
- Seeded deterministic selection.
- Performance fallback entry and exit.
- Pending navigation coalescing.

### 20.2 Renderer tests

- Outgoing visible at progress `0`.
- Incoming fully visible at progress `1`.
- Final alpha, translation, and scale are neutral.
- No uncovered viewport region for glide and zoom effects.
- All collage tiles use identical transition progress.
- Outgoing content is removed after completion.
- Transition rendering performs no repository or decode request.
- Cancellation leaves a valid committed slide.

### 20.3 Integration tests

- Single landscape → single landscape.
- Single landscape → portrait fallback.
- Single landscape → two-photo collage.
- Three-photo collage → single landscape.
- Two-photo collage → three-photo collage.
- Collage → collage.
- Slow SMB preparation.
- Decode failure during preparation.
- Manual Next during preparation.
- Manual Previous during preparation.
- Manual Next during animation.
- Pause during animation.
- Settings change during animation.
- Application background and resume.
- Low-memory callback during transition.
- Transition fallback after rendering failure.

### 20.4 Endurance test

Configuration:

```text
1,000 transitions
3-second display interval
mixed single and collage slides
Ambient Random enabled
slow-network simulation enabled
Huawei PLK-L01 / Android 6
```

Capture:

- Java heap;
- native heap;
- process PSS;
- Coil image-cache size;
- prepared-slide count;
- transition duration;
- transition start latency;
- frame timing;
- fallback count;
- decode failures;
- cancelled transitions;
- black-frame detector result.

---

## 21. Acceptance criteria

### 21.1 Functional

- Ten distinct visual effects exist.
- Ambient Random is implemented as a separate selection mode.
- Every effect supports all prepared-slide types.
- Settings persist across restart.
- Android and web settings remain synchronized.
- Backup and restore include transition settings.
- Reduced motion works without overwriting the stored effect.
- Manual navigation does not create an unbounded queue.

### 21.2 Visual

- No dated transition style is introduced.
- No card flip, cube, page curl, checkerboard, blinds, spin, bounce, flash, or dramatic 3D perspective.
- No unintended application background is visible.
- No black loading frame occurs.
- Collage layout remains fixed during transition.
- Motion remains subtle.

### 21.3 Reliability

- Incoming content is fully prepared before animation begins.
- Failed preload leaves the current slide visible.
- Rendering failure falls back safely.
- Only one preparation and one animation job can be active.
- Outgoing content is released after completion.
- Resource release is idempotent.
- Memory remains bounded.

### 21.4 Performance

On Huawei PLK-L01:

- at least 95% of measured transition frames complete within 33.3 ms;
- no prepared transition causes a UI stall longer than 250 ms;
- transition begins within 100 ms after incoming content becomes ready;
- heap after warm-up grows by no more than 20 MB across 1,000 transitions;
- automatic fallbacks remain below 1% under normal conditions;
- no OOM, ANR, renderer crash, or black frame occurs.

---

## 22. Definition of done

The feature is complete when:

- all five phase gates pass;
- all ten visual effects are implemented;
- Ambient Random is available;
- Android and web settings are complete;
- reduced motion and fallback policies work;
- diagnostics are present;
- automated tests pass;
- the Huawei API 23 endurance test passes;
- the application builds and installs successfully with:

```bash
./gradlew clean installDebug
```

---

## 23. Recommended rollout

### First development milestone

Implement and validate:

- Crossfade
- Soft Dissolve
- Gentle Zoom In
- Gentle Zoom Out
- Horizontal Glide
- Vertical Glide

### Second development milestone

Implement:

- Depth Fade
- Ken Burns Handoff
- Soft Reveal
- Soft Focus Fade
- Ambient Random

### Production default

Use:

```text
mode = Fixed
effect = Crossfade
duration = Normal
reduce motion = false
```

Change the default for new installations to Ambient Random only after the Huawei API 23 endurance test passes.
