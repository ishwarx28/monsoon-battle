# monsoon-battle

A native Kotlin/libGDX first-person rescue shooter set in a rain-soaked fictional Indian district. Android package: `com.ishwarx28.monsoonbattle`.

**Status: playable first milestone, not a finished photorealistic release.** The full gameplay loop and native Android integration are implemented. Current characters, city geometry, materials and audio are procedural; production-quality human assets, richer level art and Android device testing remain important work.

## Play

Open the repository in Android Studio, select the **android** run configuration and use a landscape Android device. The desktop module is a development harness, not a replacement for the Android app.

```sh
# JDK 17 or 21; Android SDK platform 36 installed
./gradlew :core:test :android:assembleDebug :android:lintDebug
# APK: android/build/outputs/apk/debug/android-debug.apk

# Desktop development (Gradle sets -XstartOnFirstThread on macOS)
./gradlew :desktop:run
```

Set `sdk.dir` in your untracked `local.properties`, or use the normal `ANDROID_HOME` environment variable. Android Studio can create `local.properties` automatically. The source-only `gradlew` launcher reuses a cached Gradle 8.13 distribution or downloads it with SHA-256 verification; a binary wrapper JAR is not required. macOS/Linux need `curl` and `unzip`. Windows uses `gradlew.bat` and the checked-in PowerShell launcher, subject to your existing execution policy. An installed Gradle 8.13 also works directly. A standard wrapper can be regenerated with `./gradlew wrapper --gradle-version 8.13 --distribution-type bin`.

The GitHub Actions workflow builds a debug APK and publishes it as the **monsoon-battle-debug** workflow artifact. It does not publish the game to Google Play.

## Operations

**The Last Shift**: locate Shanti Textiles, search its 16 rooms for the security log, find Meera, then escort her back to the relief checkpoint.

**After the Shutters**: search Meghdoot Galleria's 16 rooms, locate the security log and rescue Arjun. The two mission interiors each occupy approximately 54 by 68 metres, with connected corridors, doorways, cover, room labels, furnishings and ammunition. Intelligence and hostage locations vary with the run seed. Missions have no countdown timer.

Ordinary shops and warehouses are sealed exterior scenery. The district includes a boulevard, side streets, shopfront signs, posters, billboards, auto-rickshaws, vehicles, a garden and a relief checkpoint.

## Controls

| Action | Android | Desktop |
|---|---|---|
| Move / steer | Left thumbstick | W A S D |
| Look | Drag the right side | Mouse |
| Fire / aim | FIRE / AIM | Left / right mouse |
| Interact / enter / exit | USE | E |
| Reload | RELOAD | R |
| Crouch / prone | CROUCH / PRONE | C / Z |
| Melee | MELEE; FIRE also melees when all ammo is gone | F |
| Sprint / accelerate | SPRINT | Left Shift |
| Field map / pause | MAP / pause button | M / Escape |

Set your officer name and sound preference in the menu. Sound defaults to on. The map and pause menu freeze the simulation. Game state is saved locally every five seconds, at significant transitions and when the app pauses. Returning to the menu does not abandon the run; **End this run** does.

## Combat contract

Every person starts at 100 health. Every bullet hit removes exactly 10 health; no headshot multiplier changes this rule. Melee also deals 10. A 30-round magazine reloads from the reserve; ammunition stashes and bodies can be searched with USE. Bullets stop at the nearest Bullet collision object rather than dealing damage through walls.

The first three lethal player encounters offer a rewarded revive. Only an earned SDK reward restores 100 health and consumes one revive. Failure, dismissal or unavailability grants nothing and consumes nothing. Duplicate, stale and cross-run callbacks are rejected. After three successful revives, the next lethal encounter permanently ends that run. Desktop mode deliberately does not simulate ad rewards.

NPC perception uses the same Bullet scene as shooting, plus a conservative fog-distance limit and field of view. Goons have reaction delays, imperfect physical aim, last-seen pursuit and cover movement. Crouch/prone change the actual capsule, not just camera height. Vehicles use dynamic Bullet bodies; boarded occupants are protected and do not collide with their own vehicle. Exit positions are checked before occupants are placed outside.

## Rendering and sound

Five-minute days alternate with five-minute nights. Weather changes rain density, sight range, lighting and occasional lightning/thunder. Rendering includes shadow maps, a visible first-person body and full player shadow, projective road reflections, puddles, rain ripples, fog, indoor rain exclusion and restrained color grading. Reflection quality can be reduced from the pause menu.

Procedural WAV generation supplies gunfire, reload/melee/footstep sounds, rain, wind, thunder, engine audio and an original restrained background score. Android TTS prefers an available Hindi voice and falls back to English; spoken dialogue also has captions. No external character, texture, music or font files are required. Device fonts are rasterized at runtime, with a bundled libGDX fallback.

## Android services and release configuration

Debug builds use Google's test AdMob app/rewarded identifiers. UMP consent gates ad requests, privacy options are exposed in the menu, and ads pause gameplay. A release build is intentionally blocked while the sample IDs remain configured:

```sh
./gradlew :android:assembleRelease   -PadmobAppId=YOUR_ADMOB_APP_ID   -PrewardedAdUnit=YOUR_REWARDED_AD_UNIT
```

Before distribution, configure release signing, real AdMob IDs and UMP messages, and test consent, completed/dismissed/unavailable rewards, privacy options and TTS on actual Android devices. Do not use live ads for development testing. No production credentials, signing keys or ad-account configuration are included.

## Project layout

- `core`: deterministic gameplay rules, world layout/navigation, Bullet scene, simulation, rendering, input/HUD, audio and persistence.
- `android`: Android launcher, AdMob rewarded ads, UMP consent and TTS.
- `desktop`: local rendering/playability harness with no ad rewards.
- `core/src/test`: rule, navigation, native collision and integrated mission/combat tests.
- `docs`: architecture, validation results and release checklist.

See [validation](docs/VALIDATION.md) and [architecture](docs/ARCHITECTURE.md). This milestone does not claim photorealistic people, production-grade vehicle handling or verified performance on Android hardware.
