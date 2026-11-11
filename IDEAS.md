# Y1 Music: Ideas to Improve

**Scores:** Impact = how much it helps (5 is best). Effort = how hard (1 is easy).

---

## Planned

| # | Idea | Impact | Effort | Status |
|---|------|:------:|:------:|:------:|
| 1 | **Stop freezes when songs load.** Songs now load in the background (`prepareAsync`), so the wheel never waits on the SD card. | 5 | 2 | ✅ Done (builds; needs a device test) |
| 4 | **Faster rescan.** Saves each file's date. Rescan only reads new or changed files. The first rescan after this update is still full, because the old cache has no dates. | 4 | 2 | ✅ Done (builds; needs a device test) |

**Test on the device:**
1. Press play, then spam Next. The wheel should never freeze.
2. Pause right after pressing play. It should stay paused.
3. Settings, Rescan once (slow). Rescan again (should take seconds).

---

## Skipped

Not feeling these. Can come back later.

| # | Idea | Impact | Effort |
|---|------|:------:|:------:|
| 2 | Repeat All toggle | 4 | 1 |
| 3 | Keep Shuffle All going | 4 | 1 |
| 5 | Know when the cache is old (Idea 4 covers most of this) | 3 | 1 |
| 6 | Letter jump in long lists | 5 | 2 |
| 7 | Progress bar and time | 4 | 2 |
| 8 | Seek with the wheel | 4 | 3 |
| 9 | Sleep timer | 3 | 2 |
| 10 | Headphone button | 3 | 2 |
| 11 | All Songs list | 2 | 1 |
| 12 | Gapless playback | 3 | 3 |
| 13 | Recently played | 3 | 2 |
| 14 | Sort albums by year | 2 | 2 |
| 15 | Low battery warning | 2 | 1 |
| 16 | Track number on song rows | 2 | 1 |
| – | Hi-res FLAC over 48 kHz (player can't play it) | – | huge |
| – | Equalizer, lyrics, search | – | – |
