# Build 82: unattended boot recovery

Build 82 keeps build 81's slideshow, bitmap-arena, image-quality, transport, ownership,
diagnostic, and memory-policy behavior unchanged. It adds only unattended window recovery.

When the persisted **Start on boot** setting is enabled, the existing `BOOT_COMPLETED`
receiver launches `MainActivity`. The activity now uses API-22-compatible public window flags to
turn the display on, keep it on, show the frame over keyguard, and dismiss a non-secure swipe
keyguard. Android continues to protect PIN, pattern, and password credentials; the app does not
attempt to bypass a secure keyguard.

The V80 must have **Start on boot** enabled and should use no secure screen lock for completely
unattended recovery after a device watchdog reboot.
