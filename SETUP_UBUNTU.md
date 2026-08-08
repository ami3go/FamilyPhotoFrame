# Setup, Build & Run on Ubuntu

For Ubuntu 22.04 / 24.04 with **Android Studio already installed**. The project
ships a committed Gradle wrapper (Gradle 8.9), so you do **not** need to install
Gradle separately.

What the project needs:

- **JDK 17** to run the build (Android Gradle Plugin 8.5.2 requires it). Android
  Studio already bundles one (the "JBR") — you can reuse it, so no separate install
  is required.
- **Android SDK Platform 35** + Build-Tools + platform-tools (installed through
  Android Studio's SDK Manager). `compileSdk`/`targetSdk` are 35; `minSdk` is 24.

---

## 0. Unpack the project

```bash
cd ~
unzip /path/to/FamilyPhotoFrame.zip -d ~/   # if you still have the zip
cd ~/FamilyPhotoFrame                        # the folder containing gradlew + settings.gradle.kts
chmod +x gradlew                             # ensure the wrapper is executable
```

Everything below is run from this project root (the directory that contains
`gradlew`).

---

## Option A — Android Studio (simplest)

1. **Launch Android Studio** → **Open** → select the `FamilyPhotoFrame` folder →
   **OK**. Open the folder itself, not a single file.
2. Let the **Gradle sync** finish (bottom status bar). On first sync Studio writes a
   `local.properties` pointing at your SDK automatically.
3. If Studio prompts to **install SDK Platform 35 / Build-Tools**, accept. Otherwise
   open **SDK Manager** (Settings → Languages & Frameworks → Android SDK, or the
   toolbar icon) and tick:
   - SDK Platforms → **Android 15.0 (API 35)**
   - SDK Tools → **Android SDK Build-Tools**, **Android SDK Platform-Tools**,
     **Android Emulator**
4. Make sure the Gradle JDK is 17: **Settings → Build, Execution, Deployment →
   Build Tools → Gradle → Gradle JDK** = the bundled **jbr-17** (or any JDK 17).
5. Pick a device in the toolbar (see **Running** below), then press **Run ▶**
   (Shift+F10). The app installs and launches; on first start choose a photo folder
   or tap **Use sample photos**.

---

## Option B — Command line

### One-time environment setup

Point Gradle at a JDK 17 and at your Android SDK.

**Reuse Android Studio's bundled JDK 17** (recommended — no extra install). Find it:

```bash
# Try the common install locations; use whichever exists:
ls -d /opt/android-studio/jbr ~/android-studio/jbr \
      /snap/android-studio/current/android-studio/jbr 2>/dev/null
```

Then, in `~/.bashrc` (adjust the path to the one that existed above):

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk           # default SDK location
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

Reload it: `source ~/.bashrc`. Verify: `java -version` should report **17**.

> Prefer a system JDK instead? `sudo apt install openjdk-17-jdk` and set
> `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

If you didn't open the project in Studio first, create `local.properties` in the
project root so the CLI build can find the SDK (Studio does this for you otherwise):

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

### Build

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. The build also runs the
**permission audit** and writes `app/build/reports/permissions/debug.txt` — it should
say the merged manifest contains none of the forbidden broad-storage permissions.

Other useful tasks:

```bash
./gradlew testDebugUnitTest         # JVM unit tests (StableId, Glob, engine, settings)
./gradlew assembleRelease           # release APK (debug-signed spike build)
./gradlew installDebug              # build + install onto a connected device/emulator
./gradlew --stop                    # stop the Gradle daemon if you need a clean slate
```

The **first** build downloads Gradle 8.9 and the dependencies, so it takes a few
minutes; later builds are cached and fast.

---

## Running the app

You need either an emulator or a physical device.

### Emulator (AVD)

1. Enable KVM so the emulator isn't unusably slow:
   ```bash
   sudo apt install -y cpu-checker qemu-kvm
   kvm-ok                       # should say "KVM acceleration can be used"
   sudo adduser "$USER" kvm     # then log out and back in (or reboot)
   ```
2. In Android Studio open **Device Manager → Create device**, pick e.g. *Pixel 7*,
   choose a system image (**API 35** recommended; download if needed), finish, then
   press ▶ next to the AVD.
3. From the CLI you can then `./gradlew installDebug` or press Run in Studio.

### Physical phone / tablet

1. On the device: **Settings → About phone → tap Build number 7×** to unlock
   Developer options, then **Settings → System → Developer options → USB debugging =
   on**.
2. Plug it in over USB and accept the "Allow USB debugging?" prompt.
3. Confirm Ubuntu sees it:
   ```bash
   adb devices        # your device serial should be listed as "device"
   ```
   If it shows `no permissions` or nothing, unplug/replug and accept the prompt; a
   reboot after adding yourself to the `plugdev` group resolves most cases.
4. `./gradlew installDebug`, or select the device in Studio and Run.

### Android TV / box (optional)

The app also declares a TV launcher entry. On a TV device, enable Developer options +
USB/network debugging, `adb connect <tv-ip>`, then `./gradlew installDebug`. Use the
remote's D-pad (Right/Left = next/prev, OK = pause, Menu = settings).

---

## Troubleshooting

- **"SDK location not found" / `sdk.dir`** — create `local.properties` (see above) or
  export `ANDROID_HOME`, then re-run. Opening once in Android Studio fixes it too.
- **"Android Gradle plugin requires Java 17"** — your `JAVA_HOME` points at an older
  JDK. Point it at Studio's `jbr` or an installed JDK 17 and `source ~/.bashrc`.
- **`./gradlew: Permission denied`** — run `chmod +x gradlew`.
- **`Failed to install the following SDK components ... Android SDK Platform 35`** —
  open SDK Manager and install API 35 + Build-Tools, then rebuild.
- **Emulator is extremely slow or won't boot** — KVM isn't set up; redo the
  `kvm-ok` / `adduser $USER kvm` steps and re-login. Prefer an *x86_64* system image.
- **First build seems stuck** — it's downloading Gradle 8.9 and dependencies. Give it
  a few minutes; watch progress with `./gradlew assembleDebug --info`.
- **Behind a proxy / restricted network** — Gradle needs to reach
  `services.gradle.org`, `dl.google.com`, and Maven Central for the first build.
