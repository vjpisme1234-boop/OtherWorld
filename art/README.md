# Other World art

## originals/ — the user's files, byte-for-byte (never edit)
| file | source | sha256 |
|---|---|---|
| glass_wheel.png | ~/Downloads/magnific__a-symmetrical-arrangement-of-several-vertical-rect__8766.png | c8b93a4ef0472ac38b6a26f5a93b6190cddd81e620ece638acdc46ccb6e14f66 |

The original `glass_wheel.png` ships from `app/src/main/assets/` (assets are not re-encoded by the build), same checksum.

## derived/ — pieces cut from the originals
| file | from | change |
|---|---|---|
| floor_clean.png | glass_wheel.png | The whole ring area (all 12 painted slabs + their floor reflections) filled in from the surrounding floor, so the moving slabs aren't doubled. Approved by the user 2026-10-09; briefly removed 2026-10-10, then restored at the user's request. |
| slab_A.png … slab_F.png | glass_wheel.png | The six front glass slabs (left → right), cut out along their outline with a 1px soft edge; pixels untouched. Outlines are in `app/src/main/assets/wheel.json` (`slots`), plus the 12 ring keyframes measured from the art (`ring`); the back six reuse the front cut-outs mirrored. |

Alien face images (`~/Downloads/20261009_155733*.png`, `picsvg.svg`): on hold, not in the app.

## generated/ — made in magnific for this app (2026-10-10), styled on glass_wheel.png
| file | what |
|---|---|
| glass_icons_sheet.jpg | 8 glass app icons on one sheet (Nano Banana 2 Lite, wheel art as style reference). |
| icons/<package>.png | The sheet cut into 8 icons, black background turned into transparency. Named by the app they replace: Phone, Messenger, Chrome, Messages, Samsung Notes, Gallery, Gmail, Settings. Personal icons — not to ship in the Play Store build (they imitate brand marks). |
| glass_button_home.jpg | Glass pill button with a house symbol, for "Set as home" on the settings page; the label is drawn by the app. |
| glass_tiles_sheet.jpg → glass_tile.png, glass_apps.png | Blank glass tile (frames original app icons in "Originals in glass" style) and the dock's apps button (3×3 dots). Same model/style reference; black → transparency. Ship in the app (generic, no brand marks). |
| glass_button_home.png | glass_button_home.jpg with its black background turned into transparency (the app uses this one). |

## Wallpapers (2026-10-10)
| file | from | what |
|---|---|---|
| originals/globe_earth.mp4 | magnific "0_Globe_Earth_1280x720" (user's upload) | Spinning globe; ships as the Globe live wallpaper (fit, with a blurred copy behind instead of bars). |
| originals/alien.png | ~/Downloads/20261009_155733-2.png | Alien face; Alien (cyan) live wallpaper, drawn at real size, glow pulse + float added at runtime. |
| derived/alien_green.png | originals/alien.png | Hue shifted cyan → neon electric green (user asked for both colours). |
| derived/globe_thumb.jpg | globe_earth.mp4 first frame (magnific's start frame) | Thumbnail in the Wallpaper menu. |
| generated/blink_options.gif | Preview of 3 blink ideas shown to the user (2026-10-10); they chose #2. |
| generated/alien_eyes_closed_ai.png | Ideogram in-place edit "eyes closed" (80 credits). Came back with a white background; not used (option 1, not chosen). |

Alien blink (chosen #2) is drawn at runtime, no new files: the mesh just above each eye hole slides down over it, clipped to the hole's shape, with a bright seam. Tilt moves the face up to ~30 px.
| derived/alien_pink.png | originals/alien.png | Hue shifted cyan → glowing neon pink (2026-10-10); Alien (neon pink) live wallpaper. |
| derived/alien_purple.png | originals/alien.png | Hue shifted cyan → deep neon purple, extra saturation so highlights go purple (2026-10-10); Alien (neon purple) live wallpaper. |

## Hidden drawer (2026-10-10)
| file | source | use |
|---|---|---|
| originals/vault_bg.png | ~/Downloads/magnific__photo-a-rectangular-glass-display-case-with-a-brig__62027.png | Was the hidden drawer's background; replaced by the glass cube (derived/vault_cube.png) on 2026-10-10 at the user's request. Kept here, no longer shipped. |

## Sounds (generated 2026-10-10, magnific sound effects, 75 credits)
`art/generated/sounds/` → shipped as `assets/sounds/`: tick (panel passes), open (panel flips open),
ring (switching rings), drawer (wheel opens) — 3 takes each; the user picks one per sound in Home settings.

## Mars (2026-10-10, magnific)
| generated/mars/mars_still_1.png | Nano Banana Pro edit of the Earth video's first frame → holographic Mars, same stand (option 1 of 4, 300 credits for 4). |
| generated/mars/earth_to_mars_draft.mp4, mars_spin_draft.mp4 | Seedance 2.0 Mini 480p drafts (350 each). Rejected: smoke ended as a landscape; spin showed Earth continents. |
Wheel centre frames are cut like the Earth's: scale 1280×720 → ×0.6, crop x 144–624, the Earth frames' own oval fade (derived/globe_fade_mask.png).
| generated/vault_open_draft.mp4 | Seedance 2.0 Mini 480p, start = alien (cover-cropped 9:16), end = user's display case (fit on black). Alien dissolves into cyan smoke, glass case appears. User chose this for opening the hidden drawer (2026-10-10). Shipped as assets/vault_open.mp4. |
| derived/alien_red.png, alien_gold.png, alien_ice.png, alien_blue.png | originals/alien.png | Hue/saturation shifts (2026-10-10, Phase E "more alien colors"): red +175°, gold −162° (sat ×1.35+25), ice white (sat ×0.18, brighter), deep blue +40° (sat ×1.5+40). Each is a live wallpaper. |
Sounds v2 (same day): user said the first set was "a bit too harsh" → regenerated soft/muffled/warm takes (75 credits); first set kept in generated/sounds/harsh_v1/. Playback volume also lowered.
| generated/vault_open_1080.mp4 | Seedance 2.5 1080p (3,950 credits), same start/end/prompt as the draft. Alien → fine glowing dust → the user's own wide display case. Now shipped as assets/vault_open.mp4 (draft kept in generated/vault_open_draft.mp4). |
| generated/vault_open_smoke_draft.mp4 | Seedance 2.5 Draft (1,000 credits): alien → thick cyan smoke → star flash → light beam → glass cube (end frame = last frame of the first draft). User wanted this one too, with the HD version's light flash. Shipped as assets/vault_open_smoke.mp4 ("Smoke & glass cube"). HD possible until 2026-10-17 19:29 UTC. |
Sounds v2 levelled (2026-10-10): the soft takes were too quiet (user heard only one) → decoded, normalised to −20 dB RMS (peaks ≤ −1 dB), 4 ms fades, saved as mono 44.1 kHz WAV in generated/sounds/normalized/ and shipped as assets/sounds/*.wav. Default tick = take 2.
| originals/wheel_open_user.m4a | The user's own recording (made with Hold to record on their Fold, 2026-10-10), pulled unchanged. Was shipped as drawer_4.m4a (default "Wheel opens"); taken out of the app 2026-10-12 at the user's request. "Wheel opens" default is take 1 again. |

## App icon (2026-10-10)
generated/icon/icon_option_1..4.png: Nano Banana Pro, refs = alien_green + glass_tile (300 credits). User picked 1.
generated/icon/store/: play_store_512.png (Play listing), app_store_1024.png (no alpha). Launcher: res/mipmap-*/ic_launcher(.png/_round/_foreground) + adaptive icon (black background, art at 88/108 dp).

| derived/vault_cube.png | Last frame of assets/vault_open_smoke.mp4 (480×854), 2× Lanczos to 960×1708. Background of the hidden drawer since 2026-10-10 ("make the secret vault the other box"); numbers and hidden apps sit inside its front glass. Shipped as assets/vault_cube.png. |
| assets/vault_close.mp4, assets/vault_close_smoke.mp4 | The two opening films played backwards (ffmpeg reverse, x264 crf 18, no audio). Played when the hidden drawer is closed with Close or Back (2026-10-10, "when closing the vault make everything go in reverse"). Face-down closes at once. |
| generated/sounds/vault/boom_*.mp3, star_*.mp3, beam_*.mp3 | magnific SFX (ElevenLabs), 2 takes each, 80 credits, 2026-10-12. User asked for "a low explosion sound of the alien and the star light shooting" on the vault opening film. Used: boom_a (at 0.48 s, alien bursts), star_b (3.22 s, star flare), beam_b (3.62 s, beam + cube). |
| generated/sounds/vault/vault_open_mix.wav, vault_close_mix.wav | Those three mixed on the film's timeline (5.04 s, 0.25 s fade out); the close mix is the same reversed (boom becomes an implosion). Muxed as AAC into assets/vault_open_smoke.mp4 and vault_close_smoke.mp4. Silent when the phone is on silent/vibrate or launcher sounds are off. |
