# Other World — Play Store listing kit

## Files
- `icon_512.png` — app icon (512×512)
- `feature_graphic_1024x500.png` — feature graphic
- `screenshots/play_01..07.png` — phone screenshots (1080×1920)
- Privacy policy: `docs/privacy.html` → with GitHub Pages on, the URL is
  `https://vjpisme1234-boop.github.io/OtherWorld/privacy.html`

## Text
**App name:** Other World
**Short description (≤80):** A sci-fi home screen: glass app wheel, living alien wallpapers, secret drawer.

**Full description:**
Turn your phone into another world.

• Living alien wallpapers — 8 neon colours that blink, glow, tilt with your phone and lean toward your touch. They glow when notifications arrive, dim at night and pulse while charging.
• The glass wheel — every app on a spinning ring of glass panels. Swipe through rings for Games, Social, Video & Music, Photos and Tools, or make your own. Earth goes up in smoke and becomes Mars in the middle.
• A secret drawer — hidden in plain sight. Keep private phone numbers, notes, files and apps apart from the rest of your phone. Only you know how to open it.
• He talks along — when Claude, Google Assistant, Alexa or Bixby speaks, the alien's lips and head move with the voice.
• "You have a phone call" — miss a call and the alien whispers it to you the next time you open your phone.
• Glass everything — a dock you can move anywhere, folders that open as mini spinning wheels, clock, weather and battery widgets.
• Your sounds — soft glass sounds for the wheel, a low boom when the Earth explodes, or record your own.
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
- **Notification listener:** used for unread dots, the alien's notification glow, and noticing missed calls (so the alien can say "you have a phone call"); content not stored or sent.
- **QUERY_ALL_PACKAGES:** not used — the app lists launchable apps via a `<queries>` LAUNCHER intent, which needs no declaration.
- **Microphone (RECORD_AUDIO):** (1) while the user holds "Hold to record" for a wheel sound (saved on the phone only); (2) "Talk along": Android's Visualizer needs it to read the loudness of sound the phone is playing, so the alien's lips can move with an assistant's voice. Only a live loudness level is read; no audio is recorded, stored or sent. Asked in the first-run setup, optional. Data safety: audio is **not collected** (processed on device, never leaves it).
- **WRITE_CONTACTS:** only during "Put back & clear" in Manage storage.

## Before uploading
- Release build: signed with the upload key in `../OtherWorld-keys/` (back it up); `./gradlew bundleRelease` → `.aab`. Keep Play App Signing on.
- Target SDK 36.
- New personal developer accounts: closed test with 12+ testers for 14 days before production.
- Content rating questionnaire: no violence, no user-generated sharing → expected "Everyone".
