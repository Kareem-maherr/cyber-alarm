# Cyber-Alarm — Build Prompt

Oct 22, 2026 · @Kareem Maher

This is a build prompt to hand to a coding assistant to create **Cyber-Alarm**, a native Android alarm app inspired by Alarmy with a Cyberpunk 2077–style neon HUD UI.

One honest limitation up front: on non-rooted stock Android you **cannot** fully block a user from powering off the phone — the hardware power-button long-press and the firmware-level power menu aren't interceptable by a normal app. The prompt below asks for the strongest *feasible* version of that (suppressing the power dialog via overlay, re-triggering the alarm on reboot, and so on) and flags it so the coder doesn't chase the impossible. Everything else is doable with the right permissions.

## Tech stack

- Kotlin, **Jetpack Compose** for all UI, Material3 as a base but fully re-themed (see Design).
- MVVM architecture, Kotlin Coroutines + Flow, Hilt for DI.
- **Room** for persisting alarms, challenge configs, and locked-app lists.
- `AlarmManager` with `setAlarmClock()` / exact alarms for scheduling; **foreground Service** + full-screen-intent notification to fire the alarm even when the device is locked or dozing.
- Min SDK 26, target the latest stable SDK. Handle runtime permission flows gracefully.

## Core alarm features

1. **Create, edit, and delete alarms**, each with: time, label, repeat days (Mon–Sun), enable/disable toggle, snooze config, and vibration toggle.
2. **Alarm sound from the user's music library** — let the user pick any track via `MediaStore` or the Storage Access Framework, plus a few built-in synthwave tones. Loop the chosen track at alarm time with ramping volume. Fall back to a default if the track is unavailable.
3. Alarms survive reboot — register a `BOOT_COMPLETED` receiver and reschedule from Room.

## Challenge / dismissal system

This is the core differentiator. The alarm screen **cannot be dismissed until the user completes the configured challenge(s)**. Per alarm, the user selects which challenges are required and at what difficulty. Implement each challenge behind a common `Challenge` interface so more can be added later.

- **Math problems** — generate arithmetic or algebra at a selectable difficulty; require N correct in a row.
- **Mix & match** — pair items (symbols, words, colors) correctly.
- **Connect 4 vs. computer** — a full playable board with an AI opponent (minimax with adjustable depth); the user must win or reach a defined outcome to pass.
- **At least one more**, e.g. memory/sequence repeat, or a shake/steps challenge (walk X steps using the step sensor).
- Support **chaining multiple challenges** in one dismissal, with a configurable count (e.g. “solve 3 math problems”).

## Enforcement (anti-dismiss / anti-escape)

The alarm screen must be very hard to bypass. Combine these:

- **Full-screen intent** notification + an `Activity` shown over the lock screen (`setShowWhenLocked`, `setTurnScreenOn`, `FLAG_KEEP_SCREEN_ON`).
- **Block back/home/recents escape**: intercept the back press (no dismiss), and use a `SYSTEM_ALERT_WINDOW` overlay + a foreground service so that if the user manages to leave, the overlay/alarm immediately returns to the foreground. Detect app-leaving and relaunch.
- **Suppress the power menu**: use an overlay that intercepts or covers the long-press power dialog where the OS permits (`onCloseSystemDialogs` / `ACTION_CLOSE_SYSTEM_DIALOGS` handling, accessibility-based re-foregrounding).
- **Document the limit in code comments**: fully preventing hardware power-off is **not possible** on non-rooted stock Android. Implement the best-effort mitigation — if the device reboots or the app is killed, re-fire the pending alarm on restart so the user still has to complete the challenge. Optionally detect “alarm was escaped” and resume it.
- Keep the alarm sound and enforcement running via the foreground service until the challenge is passed.

## App-locking / stay-awake feature

After an alarm is dismissed, **lock a user-selected set of other apps for 2 hours** (configurable). The user picks which apps to lock per alarm.

- Implement via an **Accessibility Service** + **Usage Stats permission**: detect when a locked app is foregrounded and overlay a block screen.
- To unlock early (proving they're really awake), the block screen presents a **challenge** that must be solved. Define the behavior: solving may grant brief access or reduce the lock window.
- Persist lock windows so they survive app/process death and reboot.
- Make all of this opt-in, with a clear permissions setup screen explaining each permission.

## Permissions needed

Request each with a clear rationale screen, and add a permissions dashboard showing what's granted.

- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `POST_NOTIFICATIONS`
- `SYSTEM_ALERT_WINDOW` (overlay)
- `FOREGROUND_SERVICE` (+ mediaPlayback / specialUse type)
- Accessibility Service
- `PACKAGE_USAGE_STATS`
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`
- Read access to audio/media
- `VIBRATE`

## UI / Design — Cyberpunk 2077 HUD aesthetic

Match the attached reference image closely.

- **Dark near-black background** (deep teal-black, subtle grid texture and faint scanlines).
- **Neon yellow (#F0FF00-ish) as the primary accent**, with red and cyan as secondary HUD accents. Disabled items render in **desaturated grey**.
- **Angular, beveled/clipped-corner cards** (not rounded rectangles) with glowing yellow borders and a small accent tab on the left edge.
- **Monospaced / techno display font**; a large **glitchy digital clock** at the top with a chromatic-aberration/scanline glitch effect, with the date under it in `DAY, DD MON YYYY` format, uppercase.
- Top status strip: left shows `■ SYS.ONLINE` with a divider line; right shows small mono text `ALARM CLOCK v2.4.1 / NEON UI / ANDROID / 00:HH:MM`.
- Each alarm card: big time, an uppercase underscore-style label (e.g. `WAKE_UP`, `GYM_PROTOCOL`), a row of pill day-chips (`MON TUE WED THU FRI`…), and a **custom angular hexagon-style toggle switch** that glows yellow when on, grey when off. Small decorative hazard stripes in the card corner.
- HUD **corner brackets/reticle framing** around the clock and screen edges (cyan bottom-left, red bottom-right accents).
- Bottom-left terminal log readout: `> TIME.SET`, `> ALARMS.LOAD`, `> READY` in red mono text.
- **Hexagonal glowing “+” button** (bottom-right) to add an alarm.
- Add subtle glow/bloom and press animations; keep it performant. The whole thing should feel like a retro-futuristic terminal HUD.

## Deliverables

- Full project structure, Gradle config, and `AndroidManifest.xml` with all permissions, services, and receivers.
- Compose screens: alarm list, add/edit alarm, challenge picker, challenge runtime screens, app-lock picker, permissions dashboard, and alarm-ringing screen.
- Room entities/DAOs, the scheduling + service + receiver layer, the challenge engine, and the accessibility/overlay enforcement layer.
- Clean, commented code. Note any OS limitations inline where best-effort is used instead of a hard guarantee.
