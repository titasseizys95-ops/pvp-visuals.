# PvP Visuals (Fabric 1.21.11)

Client-side, visual-only PvP mod. No gameplay advantage, safe for servers.

## Modules (12)
Master HUD, Combo counter, CPS counter, Target HUD, Hit particles, Keystrokes,
Armor HUD, Potion effects, Item counts (totems/gapples/crystals/pearls/anchors),
FPS/Ping, Hit distance, Target dot (crosshair turns red on a target).

## Menu
Press **Right Shift** in-game to open the menu (rebind in Options > Controls > PvP Visuals).
Settings are saved to `config/pvpvisuals.properties`.

## Commands
`/pvpvisuals <module> <true|false>` (module ids: hud combo cps target particles keystrokes armor effects items stats reach crosshair) and `/pvpvisuals resetbest`

## Build the jar
Option A (no install): push this folder to a GitHub repo. The included workflow
(.github/workflows/build.yml) builds it; download `pvp-visuals-jar` from the Actions tab.

Option B (local, JDK 21): `gradle wrapper --gradle-version 9.2.1` then `./gradlew build`.
Jar: build/libs/pvp-visuals-1.0.0.jar

Put the jar in `.minecraft/mods` with Fabric Loader 0.18+ and Fabric API for 1.21.11.
