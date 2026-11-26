# Orbital Arsenal (Fabric, Minecraft 1.21.11)

OrbitalCannon 1.4.1 + DogOrbital 1.3.0 merged into one **server-side Fabric mod**.
Players do not need the mod installed (everything is vanilla items/entities/particles).

| Command | Who | What |
|---|---|---|
| `/orbitalstrike [player]` | op | instant strike (beam, 3 lightning bolts, power-12 blast) |
| `/orbitalnuke [player]` | op | announced nuke falling from the sky, power-20 blast + 40 TNT + mushroom cloud |
| `/orbitalrod [strike\|nuke]` | everyone | 1-use fishing rod; right-click fires where you look |
| `/dogorbital [player\|clear]`, `/dogrod` | op | 1-use Dog Orbital rod / clear all dogs |
| `/cleardogs` | op | remove every Dog Orbital dog |

TNT-minecart explosions deal at least `tntMinecartDamage` raw damage (default 200).
Config: `config/orbitalarsenal.json` (`dogsPerRod`, `tntMinecartDamage`).

## Build
Requires JDK 21.
```
gradle wrapper --gradle-version 9.2.1     # once, to create ./gradlew
./gradlew build
```
Jar: `build/libs/orbitalarsenal-1.0.0.jar` -> drop in `mods/` with **Fabric Loader >= 0.18** and **Fabric API 0.141.1+1.21.11** (or newer 1.21.11 build).
