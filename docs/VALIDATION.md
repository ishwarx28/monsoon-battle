# Validation — 2026-09-07

## Executed locally

Environment: connected Apple Silicon Mac, Android Studio JBR 21, Android SDK 36, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.2.20 and libGDX/Bullet 1.14.2.

`./gradlew :core:test :desktop:classes :android:assembleDebug :android:lintDebug` succeeded. The 23 tests cover exact ten-hit health, the three-revive limit, duplicate/failed/stale reward handling, saved incapacitation, both five-minute time phases, fog and line-of-sight gates, all 32 room routes, sampled intelligence/hostage routes, closest-hit bullet collision, swept wall collision, vehicle cover, actual stance hitboxes, both rescue state machines, shooting/ammunition, melee, reaction delays, wall/fog acquisition, vehicle boarding/exit stability, blocked hostage interaction and reload conservation.

The desktop application was launched with a real OpenGL window, rendered 100-frame smoke sessions, and exited cleanly. Menu, factory and vehicle captures were inspected. These brief smoke sessions are not mobile performance benchmarks. The test harness accepts:

```sh
./gradlew :desktop:run --args="--smoke --silent"
./gradlew :desktop:run --args="--smoke --silent --scene=factory"
./gradlew :desktop:run --args="--smoke --silent --scene=car"
```

Captures are written beneath the ignored `.build-tools/` directory. Smoke mode does not overwrite real saves. The source-only macOS/Linux launcher was exercised against the installed Gradle cache; the Windows PowerShell launcher has not been executed here. The APK is `android/build/outputs/apk/debug/android-debug.apk`. ARM64 native library ELF LOAD segments were inspected and use 16,384-byte alignment; this does not substitute for an actual 16 KB-page Android device test.

## Not verified on hardware

No Android device or configured emulator was connected during this build. Android installation/rendering, touch ergonomics, sustained frame rate/temperature/memory, sound mixing, background/resume and system interruptions still need a device pass. Android SDK integrations compile, but a real served rewarded-ad completion and Hindi TTS playback were not witnessed. Desktop does not fake those services.

## Manual release checks

1. Install on a physical ARM64 Android device; walk both missions end to end, then repeat with low reflection quality and with sound disabled.
2. Fire against cars, trunks, room walls and door edges; test standing/crouching/prone cover. Walk/drive into boundaries and test blocked vehicle exits with the hostage boarded.
3. Test all ad outcomes: load failure, offline, dismissal without reward, earned reward, duplicate callbacks, activity recreation and three earned revives followed by permanent death. Verify paused simulation during full-screen ads.
4. Restart during each mission phase, while in a vehicle and while incapacitated. Confirm health/revives/ammunition/stashes/target progress survive and no stale ad callback revives another run.
5. Test Hindi, English-only and absent TTS engines, privacy-consent changes and audio focus interruptions. Provide localized UI and accessibility/device-scale testing before public release.
6. Replace procedural production art/audio as needed; finish license notices, content rating, privacy disclosures, AdMob/UMP setup, release signing and store compliance review.

The repository contains a working gameplay milestone, not a claim that these manual checks or production release tasks are complete.
