# Pillar

**A portrait-first, visuals-first emulation frontend for Android phones.**

Pillar is a launcher for your game collection, designed for holding your phone upright. It
works with the emulators you already have, can download games from your own
[RomM](https://romm.app) server, and shows your [RetroAchievements](https://retroachievements.org)
progress. Almost everything about how it looks can be changed.

> Pillar is a frontend. It does not include games or emulators.

<p align="center">
  <img src="docs/home.webp" width="23%" alt="Home screen" />
  <img src="docs/library.webp" width="23%" alt="Library" />
  <img src="docs/game.webp" width="23%" alt="Game page" />
  <img src="docs/light.webp" width="23%" alt="Light theme" />
</p>

## Install

Grab the APK from [Releases](../../releases/latest), or track it with
[Obtainium](https://github.com/ImranR98/Obtainium) so you get updates automatically:

1. In Obtainium, tap **Add App**.
2. Paste `https://github.com/audxius/pillar` and tap **Add**.

Android 8.0 or newer.

## Features

- **Built for portrait.** A big swipeable "Jump back in" carousel, platform cards, cover grids
  and game pages laid out for tall screens. Landscape and auto-rotate are available too.
- **Your library.** Point it at your ROMs folder (one sub-folder per system, the same layout as
  ES-DE). Multi-disc games and `.cue`/`.bin` sets show up once.
- **200+ systems and hundreds of emulators.** Uses ES-DE's open Android system and emulator
  definitions: RetroArch cores, Dolphin, AetherSX2/NetherSX2, DuckStation, PPSSPP, melonDS,
  Azahar, Eden, Flycast, MAME4droid and many more. Choose a default per system or per game, or
  write your own launch command.
- **Artwork.** Box art from the libretro thumbnail server, your RomM covers, or ES-DE's
  `downloaded_media` folder (covers, fanart, logos). Set any image as a custom cover.
- **RomM.** Browse platforms, search, read game info and download games straight into the right
  system folder. Multi-file games are extracted for you, and downloads keep going in the
  background.
- **RetroAchievements.** Profile card, recent unlocks, progress per game with a full badge grid.
  Local games are matched by title; RomM games use RomM's match. You can also set a game ID by hand.
- **Customize everything.**
  - Theme: 8 palettes, any accent color, Material You, dark / AMOLED black / light.
  - Fonts: Nunito, Outfit, Space Grotesk, a pixel font, or the system font, plus text size.
  - Background: animated aurora, gradient, solid or your own image (with blur and dim), tinted by
    the platform or game on screen.
  - Corner roundness, frosted or solid cards, cover shape, grid columns, titles on/off.
  - Home screen: 12 widgets (clock, continue playing, platforms, favorites, RetroAchievements,
    RomM, random pick, quick apps, stats…) that you can toggle and reorder.
  - Platforms: show, hide, rename, recolor and reorder.
  - Haptics, click sounds, animations, orientation.
  - Export and import all of it as a backup file.
- **Launcher mode.** Optionally set Pillar as your home screen, with an apps drawer.
- **Controller friendly.** D-pad focus highlights; A selects and B goes back.

## Setting up

1. **ROMs:** Settings → Library → Add folder. Pick the folder that contains `snes`, `gba`,
   `psx` and so on.
2. **RomM (optional):** Settings → RomM. Enter your server address and username/password, or a
   client API token.
3. **RetroAchievements (optional):** Settings → RetroAchievements. Enter your username and your
   web API key from retroachievements.org → Settings.

RetroArch and a few other emulators need ROMs on real device storage (internal storage or SD card)
because they open files by path.

## Building

```sh
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
```

Releases are built by GitHub Actions when a `v*` tag is pushed. Signing uses four repository
secrets: `PILLAR_KEYSTORE_BASE64`, `PILLAR_KEYSTORE_PASSWORD`, `PILLAR_KEY_ALIAS` and
`PILLAR_KEY_PASSWORD`.

## Credits

- [ES-DE](https://es-de.org) for the system and emulator definitions (MIT).
- [RomM](https://romm.app), [RetroAchievements](https://retroachievements.org) and the
  [libretro thumbnails](https://thumbnails.libretro.com) project.
- Inspired by the look of [iiSU](https://github.com/iisu-network/iiSU). Pillar is an independent
  project and shares no code or assets with it.

## License

MIT. See [LICENSE](LICENSE) for bundled third-party licenses.
