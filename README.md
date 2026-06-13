# HoldClicker

HoldClicker is an open, user-controlled auto tapper for Android. Every target can be a normal **Tap**, a **Hold** press for a configurable number of milliseconds, or (in multi target mode) a **Swipe**. It uses Android's Accessibility gesture API and a floating overlay control bar, with no hidden behavior: automation only runs while you press Start, a persistent notification is shown the whole time, and Stop halts it immediately.

- **Single Target Mode** — one draggable target, repeated tap or hold at a chosen interval.
- **Multi Target Mode** — independent **parallel branches** of taps, holds and swipes — e.g. hold one spot while another branch taps a sequence.
- **Record Actions** — count down, then capture the taps, holds, swipes and multi-touch you perform into an editable sequence.
- **Configurations** — save, load, duplicate, rename and delete setups locally.
- **Common Settings** — target size, control bar size, vibration, countdown, **light/dark mode and accent themes**.

Light or dark with four accent palettes (Aqua, Ocean, Grape, Ember). Original branding and assets.

---

## 1. Getting the project onto GitHub from your phone

1. Download or copy the `HoldClicker` project folder (or the zip) to your phone.
2. In a browser, go to [github.com](https://github.com) and sign in (create a free account if needed).
3. Tap **＋ → New repository**, name it (e.g. `HoldClicker`), keep it private or public, and create it.
4. Open the repository, tap **Add file → Upload files**.
   - If you have the zip, extract it first with any file manager (most Android file managers can extract zips).
   - Upload the **contents** of the `HoldClicker` folder, keeping the folder structure (`app/...`, `.github/...`, `build.gradle`, etc.). The GitHub mobile website lets you upload folders; if yours doesn't, upload the files folder by folder.
5. Commit the upload.

> Important: the `.github/workflows/build-apk.yml` file must end up at exactly that path in the repository, or the build workflow will not appear.

## 2. Building the APK with GitHub Actions

No laptop or Android Studio needed — GitHub's servers compile the app.

1. In your repository, open the **Actions** tab.
2. The **Build debug APK** workflow runs automatically on every push. You can also tap the workflow and choose **Run workflow** to start it manually.
3. Wait for the run to finish (usually a few minutes; the first run is slowest).
4. Open the finished run and scroll to **Artifacts**. Download **HoldClicker-debug-apk**.
5. The artifact downloads as a **zip file** — extract it to get `app-debug.apk`.

## 3. Installing the APK

1. Open the extracted `app-debug.apk` with your file manager.
2. Android will ask you to allow installs from that app ("Install unknown apps") — allow it for your file manager or browser.
3. Confirm the install. Because it's a debug build, Play Protect may show a warning; choose "Install anyway".

## 4. Enabling the Accessibility permission

HoldClicker dispatches taps, holds and swipes through Android's accessibility gesture system, so the service must be enabled once:

1. Open **Settings → Accessibility**.
2. Find **HoldClicker** (sometimes under "Installed apps" or "Downloaded services").
3. Toggle it **on** and confirm.

The app's home screen shows whether the service is enabled and has a shortcut button to these settings. The service is only used to perform the gestures you configure — it does not read screen content (`canRetrieveWindowContent` is off).

## 5. Using Single Target Mode

1. Open **Single Target Mode** from the home screen.
2. Set the **click interval** in milliseconds (e.g. 300).
3. Choose the action type: **Tap** or **Hold**. For Hold, set the **hold duration** in ms.
4. Pick a stop condition: run indefinitely, stop after a number of seconds, or stop after a number of cycles.
5. Tap **Show overlay**. The app goes to the home screen and a floating control bar plus one numbered target circle appear.
6. Drag the circle onto the spot you want pressed, open the app you want to automate, then tap **▶** on the bar.
7. After a 1-second safety delay (plus a 3-second countdown if enabled in settings), the automation runs. Tap **⏹** to stop instantly, or **✕** to close the overlay.

## 6. Using Multi Target Mode

1. Open **Multi Target Mode**.
2. Set the **delay between cycles** and a stop condition.
3. Edit the action list. Each action has:
   - **Type**: Tap, Hold or Swipe (dropdown)
   - **Hold (ms)** for Hold actions
   - **Swipe (ms)** for Swipe actions
   - **Delay before / after** the action in ms
   - Use **↑ / ↓** to reorder and **✕** to delete; **＋ Add action** appends one.
4. Tap **Show overlay**. Numbered circles appear for every action; swipe actions also show a pink end point connected by a dashed line.
5. Drag every circle (and swipe end point) into place, then press **▶**. Actions run strictly in order — each gesture finishes before the next starts — then the cycle repeats per your stop condition.
6. The overlay **＋ / －** buttons add or remove targets on the fly (added targets default to Tap with a 200 ms after-delay). Note: targets added from the overlay live only in that overlay session; save changes from the Multi Target screen if you want to keep them.

## 7. Adding Hold actions

- **Single mode:** choose **Hold** as the action type and enter the duration, e.g. interval 300 ms with hold 800 ms. Since the hold is longer than the interval, the runner simply waits for each hold to finish before starting the next one (the screen tells you this).
- **Multi mode:** set an action's type dropdown to **Hold** and fill in **Hold (ms)**.
- Hold targets show a small **H** marker on the overlay circle.

## 7a. Parallel branches (independent simultaneous action paths)

Multi Target Mode is built around **branches** that run **in parallel**. Each branch is its own independent timeline of taps, holds and swipes, and every branch starts together at the top of each cycle. This is what lets you, for example, **hold a spot on one side of the screen while a separate branch taps a sequence on the other side** — the two run on their own schedules at the same time.

- Use **＋ Add parallel branch** to create another independent path, and **＋ Add action to this branch** to extend one.
- A live **branch diagram** at the top shows Start fanning out into each branch, the order of actions within a branch (down-arrows), and that the whole thing repeats each cycle.
- On the overlay, each branch's targets are drawn in its own colour and labelled by branch letter and step (A1, A2, B1 …).
- The pre-seeded **"Parallel hold + tap"** config is a ready example: branch A holds one point while branch B taps another three times.

**How it runs and its limits.** On stock Android (no root), the only reliable way to press two places at the same time is to send them as one gesture with multiple strokes at different start times — Android cancels a gesture if a second one is dispatched mid-way, so independent OS-level "threads" aren't possible. HoldClicker therefore compiles every branch into a single timed gesture per cycle. Consequences to know:

- A cycle can contain **at most ~10 actions total across all branches** (Android's stroke limit) and must fit within **60 seconds**.
- A branch that should stay **continuously** held while another branch taps many times will briefly re-press at each cycle boundary, because each cycle is one gesture. Keep the hold to roughly one cycle's length and set the cycle delay to 0 for the smoothest result.

## 7b. Recording a sequence

The **⏺ Record Actions** card captures exactly what you do:

1. Tap **⏺ Record Actions** on the home screen (the accessibility service must be enabled).
2. A **3-second countdown** appears, then a translucent capture layer covers the screen.
3. Perform your taps, holds and swipes — **including two or more fingers at once**. Each press is captured with its real position and timing; quick presses become taps, longer ones become holds, dragged ones become swipes.
4. Tap **■ Stop** on the small bar at the top.
5. HoldClicker turns it into **parallel branches** — touches that overlapped in time are placed on separate branches (so a finger you held while tapping with another finger becomes its own branch) — saves it as **"Recorded sequence"**, and opens it in Multi Target Mode to fine-tune and rename.

**Important about how recording works:** Android does not let an app silently watch your touches inside other apps (that capability is what malware abuses, and it's blocked without root). So recording happens *on the capture layer* — while you record, your touches land on that layer rather than the app underneath. You're laying out the sequence and its timing on screen, then HoldClicker plays it back into the real app afterward. Positions and timing are captured faithfully, which is what playback needs.

## 8. Themes and light mode

Open **Common Settings → Appearance** to choose **System**, **Light**, or **Dark**. Under **Accent theme** pick **Aqua** (teal/pink), **Ocean** (blue/cyan), **Grape** (purple/magenta) or **Ember** (orange/red). Appearance changes apply immediately; the floating overlay keeps a fixed dark, high-contrast look so its controls stay readable over any app.

## 9. Managing configurations

Open **Manage Configurations** to Load, Rename, Duplicate or Delete saved setups. Examples are pre-seeded, including `Hold test`, `Multi target test` and `Parallel hold + tap` (a ready parallel-branch demo). Save new ones from the Single/Multi screens with **Save as configuration**. Configs are stored locally in the app's private storage (SharedPreferences as JSON).

## 10. Safety notes

- Intervals below **40 ms** trigger a warning — extremely fast tapping can overload apps and your device.
- Negative durations are rejected by input validation.
- A **1-second safety delay** always precedes a start, with an optional 3-second countdown.
- A **persistent notification** is shown the entire time automation is running.
- **⏹ stops immediately**, including mid-sequence.
- HoldClicker is a plain, visible automation tool. It has no stealth or anti-detection features, and using automation may violate the terms of service of some apps and games — use it responsibly and at your own risk.

## Project structure

```
HoldClicker/
├── .github/workflows/build-apk.yml      # GitHub Actions: builds debug APK artifact
├── build.gradle / settings.gradle       # Gradle project setup (AGP 8.5.2, Kotlin 1.9.24)
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                         # layouts, drawables, theme, accessibility config
│       └── java/com/holdclicker/app/
│           ├── model/Models.kt          # ClickerConfig / TargetAction
│           ├── data/ConfigStore.kt      # save/load configs (SharedPreferences JSON)
│           ├── data/Prefs.kt            # common settings
│           ├── service/AutoClickService.kt   # AccessibilityService + gesture dispatch
│           ├── service/AutomationRunner.kt   # sequential scheduling engine
│           ├── overlay/                 # control bar, targets, lines, recorder, branch tree
│           ├── App.kt                   # applies saved light/dark mode
│           └── ui/                      # home, single, multi, configs, settings, themed base
└── README.md
```
