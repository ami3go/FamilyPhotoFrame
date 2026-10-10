# Rooted V80 battery care

FamilyPhotoFrame has an optional battery-care switch for the validated rooted V80 tablet.
It is disabled by default and is intentionally unavailable as a generic Android charging
API: Android does not provide one, and OEM sysfs controls are not portable.

When enabled, the app verifies the exact V80 identity inside every privileged command and
uses only the verified `charge_control_limit` kernel interface. On this Intel API-22 kernel,
the ordinary `enable_charging` bit is immediately rewritten by the charging framework; the
framework's own final throttle state is the durable disable control. The app pauses charging
at 75% and resumes at 60%. It also pauses at 42°C and will not resume a temperature-triggered
pause until the battery cools to 38°C. The wide bands avoid relay-like rapid switching.

The app records whether it—not Android or another tool—paused charging. It never overrides
an externally throttled or disabled charge state. Both enable and disable commands abort if a
thermal controller changes the state between inspection and write. Turning the option off or disconnecting external power
restores charging only when the app owns the paused state. While active, an exact alarm
checks every 15 minutes, or every 5 minutes while charging is paused. Boot handling performs
the same bounded evaluation.

All privileged commands are fixed at build time, run off the main thread, have a 30-second
first-use authorization window, verify the device identity before touching sysfs, and read
the node back after a write. The V80 exposes this writable attribute as mode 0444, so the
command temporarily enables owner-write and restores 0444 immediately after read-back. A
missing root binary, denied root access, wrong device, missing node, concurrent thermal
control, timeout, or unexpected read-back produces diagnostics and leaves playback running.

## Important limitation

This protects an always-powered test frame; it does not replace the tablet's hardware charge
controller. Android force-stop disables the app and its alarms. The kernel normally restores
charging on reboot, and the app also evaluates on boot, but the operator should confirm the
device's charge indicator after changing root configuration or firmware.
