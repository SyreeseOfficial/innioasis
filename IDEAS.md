# Y1 Music: Ideas to Improve

**Scores:** Impact = how much it helps (5 is best). Effort = how hard (1 is easy).

---

## Planned

| # | Idea | Impact | Effort | Status |
|---|------|:------:|:------:|:------:|
| 1 | **Stop freezes when songs load.** Songs now load in the background (`prepareAsync`), so the wheel never waits on the SD card. | 5 | 2 | ✅ Done (builds; needs a device test) |
| 4 | **Faster rescan.** Saves each file's date. Rescan only reads new or changed files. The first rescan after this update is still full, because the old cache has no dates. | 4 | 2 | ✅ Done (builds; needs a device test) |

### Speed and efficiency (new)

| # | Idea | Impact | Effort | Status |
|---|------|:------:|:------:|:------:|
| 17 | **Build lists off the UI thread.** `fill()` loops all songs in `onCreate`, and blocks if the library is still loading. Build rows in the background, show "Loading...". | 5 | 2 | ✅ Done (builds; needs a device test) |
| 18 | **Cache artist/album lists.** `artists()`, `allAlbums()`, `songs()` re-walk and re-sort on every screen. Compute once in `load()`/`rescan()`. | 4 | 1 | ✅ Done (builds; needs a device test) |
| 19 | **Cut Now Playing battery use.** 500 ms poll plus a new battery receiver each tick. Register once, update on change. | 4 | 2 | ✅ Done (builds; needs a device test) |
| 20 | **Crash-safe rescan.** Write `library.dat.tmp`, then `renameTo`, so a kill mid-write can't corrupt the cache. | 4 | 1 | ✅ Done (builds; needs a device test) |
| 21 | **Cache covers.** Keep the last cover per album in memory; optionally save 240 px JPEGs on disk. | 3 | 2 | ✅ Done (builds; needs a device test) |

Also done: queue saving moved off the UI thread (and made crash-safe), resume position saved with `apply()` instead of `commit()`, Shuffle All no longer blocks on the library, two rescans at once can't clash, and Rescan clears the cover cache.

**Test on the device (new):**
1. Open Artists right after boot. It should show "Loading..." and the wheel should still work.
2. Open Albums, Artists and a song list repeatedly. Should be instant.
3. Play songs from one album, then another. Covers should show; second visit to an album is instant.
4. Rescan, then check covers still load (cache is cleared and rebuilt).
5. Shuffle All with a big library. No freeze.

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
