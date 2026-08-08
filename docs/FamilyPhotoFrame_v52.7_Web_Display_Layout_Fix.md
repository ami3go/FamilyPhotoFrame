# FamilyPhotoFrame v52.7 — web Display layout fix

Version: `0.12.5-prerelease` (`versionCode 25`)  
Web asset revision: `v5270`

## Root cause

The generic web card grid allowed cards as narrow as 280 CSS pixels. A settings row inside
each card still reserved at least 150 pixels for its label and 180 pixels for its control,
plus the row gap and card padding. At desktop and browser-zoom combinations that produced
four Display cards, controls therefore exceeded their grid track and painted over the next
card. Each brightness schedule row compounded the problem by placing three full-width form
controls in one non-wrapping flex row.

## Corrections

- The Display tab now uses two deliberate, zero-minimum-width columns and stacks to one
  column at 1199 CSS pixels.
- Image fit and Overlays share the first column; Screen and Automatic brightness share the
  second, so card order remains predictable when stacked.
- Generic cards no longer shrink below 360 CSS pixels before the existing mobile stack
  applies.
- Grid and flex children explicitly use a zero minimum width and a 100% maximum width, so
  long select labels cannot enlarge their parent track.
- Brightness schedule periods use a bounded three-field grid. On narrow phones, the action
  selector moves to a full second row.
- Brightness period controls now have explicit accessible labels.
- The revisioned CSS/JavaScript URL changed to `v5270`, preventing browsers from reusing the
  previous immutable asset.

## Verification

The automated web contract checks cover the two-column/one-column breakpoints, shrinkable
control contract, brightness sub-grid, JavaScript layout hooks, asset revision, and syntax.

Hardware/browser acceptance:

1. Open Display at 100%, 125%, 150%, and 200% zoom.
2. Verify no horizontal page scrolling and no control crosses a card boundary.
3. Check desktop widths of 1920, 1536, 1366, and 1024 pixels.
4. Check phone widths of 412, 390, and 360 CSS pixels.
5. Change every Display control and save both brightness periods to confirm the responsive
   markup did not change setting behavior.
