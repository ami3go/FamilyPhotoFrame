# First run — what to do, and what to send me

The app is installed. Everything from here is runtime behaviour, which nothing so far has
verified: the offline harness proved the logic is well-formed, and the build proved it
compiles. Neither says the slideshow plays.

## 1. Launch it and watch the first minute

Expect the first-run screen (no source configured). The most likely failure points are
startup crashes in code no compiler exercised until today.

**If it crashes or shows a black screen**, capture the log — this is far more useful than
a description:

```bash
adb logcat -d AndroidRuntime:E FamilyPhotoFrame:V *:S > crash.txt
```

Or simply grab everything from the moment it launched:

```bash
adb logcat -c && adb shell am start -n com.example.familyphotoframe/.MainActivity
# reproduce, then:
adb logcat -d > crash.txt
```

Send me `crash.txt`. A stack trace names the exact line; a description makes me guess.

## 2. Then try it in this order

Cheapest first, so a failure costs the least time:

1. **"Try samples"** — bundled photos, no permissions, no network. If this does not play,
   the problem is the engine, not any source.
2. **Local folder** via the SAF picker.
3. **The web panel** — enable it in settings, open the URL on your phone, pair with the
   PIN or QR.
4. **SMB**, then **Synology/WebDAV** if you use them. *This is the first time credentials
   will be written to the Keystore on an API 23 device, which is exactly the boundary
   where `KeystoreSecretStore` changes behaviour — worth watching.*
5. **"Also play from"** to merge a local folder and the NAS into one pool.

## 3. Then the Phase 1 evidence run

Once the basics work, follow `PHASE1_EVIDENCE_GUIDE.md`: clear the diagnostics log, run
for 24 hours with a couple of NAS outages and at least one reboot, then download the
bundle and send it to me. I will analyse it with `scripts/analyze-diagnostics.py`.

**Treat the first bundle partly as a test of the logging itself.** The pipeline has been
exercised end to end offline with simulated clocks, but never on a device — so download
the log after an hour or two first, and send me that. If something is wrong with the
logging, I would rather find it then than after you have spent a day on it.

## A note on this device

`PLK-L01` is API 23, inside the API 21–23 tier that `KNOWN_LIMITATIONS.md` marks as
unvalidated. That makes it a genuinely useful test target rather than a safe one: if you
also have a newer device, running there too would separate "bug" from "old-API bug", and
would additionally satisfy the gate's "auto-start on ≥ 2 API levels" requirement.
