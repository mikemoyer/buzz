# Buzz

A silent vibrating metronome for Android. Set the tempo and the vibration strength, press Start, and lock your phone. It keeps pulsing in your pocket so you can pace your speech without anyone hearing a click.

## Features

- **Tempo:** 30–220 BPM, with a slider, +/− buttons, and one-tap presets (60 / 80 / 100 / 120).
- **Vibration intensity:** 1–100%. On phones that can't vary strength, intensity changes the pulse length instead, and the app tells you so.
- **Pulse length:** 20–300 ms, for a short tick or a longer thump.
- **Auto-stop timer:** Off (run until you stop it) or 1–60 minutes, with quick picks for 5, 10 and 20. When time is up it gives three long buzzes and stops. The countdown shows in the app and on the lock-screen notification, and you can change it mid-session.
- **Works locked:** runs as a foreground service with a wake lock, so the beat continues with the screen off.
- **Stop from the lock screen:** a quiet notification shows the BPM and has a Stop button.
- **Live adjustment:** changes apply immediately, even mid-beat.
- **Completely silent:** vibration only, no sound. Settings are remembered between sessions.
- **No ads, no internet, no third-party libraries.** Requires Android 8.0 or newer.

## Getting it onto your phone

### Option A: GitHub builds it for you (no software to install)

1. Create a new repository on GitHub and upload this folder's contents, including the hidden `.github` folder.
2. Open the repo's **Actions** tab and wait for "Build APK" to finish (about 3–5 minutes).
3. Open the finished run and download **Buzz-apk** under Artifacts. It's a zip containing `app-debug.apk`.
4. Copy the `.apk` to your phone (email it to yourself or use Google Drive), tap it, and allow "Install unknown apps" when prompted.

### Option B: Android Studio

1. Install Android Studio (free) from developer.android.com/studio.
2. Choose **File → Open**, select this folder, and let it sync. The first sync downloads the build tools.
3. On your phone, turn on Developer options and USB debugging. To enable Developer options, go to Settings → About phone and tap Build number 7 times.
4. Plug in the phone and press the green ▶ Run button.

## First-run tips

- **Allow notifications** when asked. That's how the Stop button appears on the lock screen. The metronome works even if you decline.
- **Battery optimization:** some phones, especially Samsung, Xiaomi, and OnePlus, aggressively kill background apps. If the pulsing stops after a while with the screen off, go to Settings → Apps → Buzz → Battery and set it to **Unrestricted**.
- If you feel nothing at all, check that vibration (including alarm vibration) is turned on in Settings → Sound & vibration.

## Using it for speech pacing

Use "Feel one pulse" to find an intensity you can notice through a pocket without it buzzing on a table. Then match the tempo to whatever unit your therapist has you pacing: one beat per word, per syllable, or per phrase. Presets make it quick to step down gradually, for example from 120 to 100 to 80, as you practice.

## How it works (for the curious)

| File | Role |
|---|---|
| `MetronomeService.java` | Foreground service. Schedules beats against an absolute clock so tempo doesn't drift, holds a partial wake lock while running (capped at 4 hours), and shows the notification. |
| `Pulse.java` | Fires one vibration, using "alarm" vibration usage so Android delivers it while the screen is off. |
| `MainActivity.java` | The settings screen. Saves changes instantly, and the running service picks them up live. |
| `Prefs.java` | Saved settings and their limits. |
