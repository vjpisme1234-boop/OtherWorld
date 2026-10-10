# Other World — Play Store listing kit

## Files
- `icon_512.png` — app icon (512×512)
- `feature_graphic_1024x500.png` — feature graphic
- `screenshots/play_01..07.png` — phone screenshots (1080×1920)
- Privacy policy: `docs/privacy.html` → with GitHub Pages on, the URL is
  `https://<your-github-name>.github.io/<repo-name>/privacy.html`

## Text
**App name:** Other World
**Short description (≤80):** A sci-fi home screen: glass app wheel, living alien wallpapers, secret drawer.

**Full description:**
Turn your phone into another world.

• Living alien wallpapers — 8 neon colours that blink, glow, tilt with your phone and lean toward your touch. They glow when notifications arrive, dim at night and pulse while charging.
• The glass wheel — every app on a spinning ring of glass panels. Swipe through rings for Games, Social, Video & Music, Photos and Tools, or make your own. Earth goes up in smoke and becomes Mars in the middle.
• A secret drawer — hidden in plain sight. Keep private phone numbers, notes, files and apps apart from the rest of your phone. Only you know how to open it.
• Glass everything — dock, folders that open as mini spinning wheels, clock, weather and battery widgets.
• Your sounds — soft glass sounds for the wheel, or record your own.
• Gestures — double-tap to lock, swipe down for notifications, two fingers for quick settings.

No accounts. No ads. No tracking. Everything stays on your phone.

**Category:** Personalization  **Price:** paid (set in Play Console)

## Data safety form
- Does your app collect or share user data? **Yes — Location (approximate) only**
  - Collected: Approximate location → purpose **App functionality** (weather widget). Not shared for advertising.
  - Is it processed ephemerally? **Yes** (sent to Open-Meteo for the forecast, not stored by you).
  - Required or optional? **Optional** (only if the weather widget is used).
- Is data encrypted in transit? **Yes** (HTTPS).
- Can users request deletion? **Yes** — uninstall / clear data deletes everything; nothing is held off-device.
- Everything else (contacts, files, audio, notes, app list) is stored **on device only** and never leaves it → under Play's rules this is not "collected".

## Sensitive permissions — what to tell Google
- **Accessibility service (declaration required):** "Other World is a home-screen launcher. Its accessibility service is used only to lock the screen when the user double-taps empty space on the home screen (performGlobalAction LOCK_SCREEN), so biometric unlock keeps working. It does not read window content (canRetrieveWindowContent=false) or collect any data." Also add the in-app prominent disclosure note: Home settings → Home gestures already explains it before opening Accessibility settings. You'll need a short video of turning it on and double-tapping to lock.
- **Notification listener:** used for unread dots and the alien's notification glow; content not stored or sent.
- **QUERY_ALL_PACKAGES:** not used — the app lists launchable apps via a `<queries>` LAUNCHER intent, which needs no declaration.
- **Microphone:** only while the user holds "Hold to record".
- **WRITE_CONTACTS:** only during "Put back & clear" in Manage storage.

## Before uploading
- Release build: needs a signing key (upload key) and `./gradlew bundleRelease` → `.aab`.
- Target SDK: Play currently requires a recent targetSdk; the project is on 34 — check Play Console's current requirement and raise it if asked.
- Content rating questionnaire: no violence, no user-generated sharing → expected "Everyone".
