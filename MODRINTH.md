<p align="center">
  <strong>Regrowth for modern Fabric, with village behavior that adapts to modded layouts.</strong>
</p>

> **Regrowth: Revamped is an unofficial continuation.** it's based on [Regrowth](https://github.com/mactso/Regrowth) by MacTso and Lupicus and is not an official upstream release.

Regrowth gives creatures small environmental effects and lets villagers maintain their settlements over time. animals can eat or spread vegetation, monsters keep their original environmental actions, and villagers repair farms, roads, lighting, vegetation and village walls.

Revamped ports the mod to Minecraft 26.2 and 26.3 and replaces the original assumption of a small square village with a safer, data-driven planning system meant for custom village structures and large modpacks.

## features

- Minecraft 26.2 and 26.3 support on Fabric
- server-side only; clients don't need the mod
- works on dedicated servers and in singleplayer
- adaptive boundaries built from meeting, home and job-site POIs
- gates and openings where roads cross the planned wall
- terrain-aware, incremental wall construction
- the original fixed-square wall algorithm, available as `legacy` mode
- conservative block replacement with protected-block and foundation tags
- data-pack profiles for biome, structure, material and road behavior
- data-pack profession roles for vanilla and modded villagers
- cached plans instead of recalculating village geometry every villager tick
- per-village analyze, preview, enable/disable, mode and rebuild controls

## adaptive village walls

the default `poi_hull` mode expands a boundary around nearby village POIs and scans that boundary for roads. villagers then build it gradually, one block at a time.

wall construction keeps Regrowth's original control-block behavior. unless `key.playerWallControlBlockString` is set to `minecraft:air`, the configured control block must be directly above the village meeting POI. new village chunks receive this block automatically.

use `legacy` mode if you prefer the original fixed-square perimeter.

![a villager building an adaptive village wall](https://raw.githubusercontent.com/cairodavila/regrowth-revamped/main/docs/assets/villager-building-wall.gif)

## villager actions

the default villager action string is still `chrwvt`.

| letter | action |
| --- | --- |
| `c` | clear obstructing grass (flowers are left alone) |
| `h` | let clerics heal people and toolsmiths repair iron golems |
| `r` | clear snow and repair likely road gaps |
| `w` | build planned village walls and road-crossing gates |
| `v` | trim natural leaves and cactus |
| `t` | place lighting in dark areas, near farms and along walls |

farm maintenance and profession-specific flower planting are assigned through profession roles and don't use these letters.

## commands

commands need game-master permission and select the nearest meeting POI within 96 blocks.

| command | what it does |
| --- | --- |
| `/regrowth village analyze` | show the selected profile, mode, control state and plan size |
| `/regrowth village preview` | display the planned boundary with particles |
| `/regrowth village preview walls` | alias for the boundary preview |
| `/regrowth village enable` | resume road and wall work |
| `/regrowth village disable` | pause road and wall work |
| `/regrowth village mode default` | use the profile's configured mode |
| `/regrowth village mode adaptive` | use the POI-derived boundary |
| `/regrowth village mode legacy` | use the fixed-square boundary |
| `/regrowth village rebuild` | discard and recalculate the cached plan |

## requirements

| Minecraft | Fabric API | Fabric Loader | Java |
| --- | --- | --- | --- |
| 26.3 | 0.165.2+26.3 or newer | 0.19.3 or newer | 25 |
| 26.2 | 0.161.0+26.2 or newer | 0.19.3 or newer | 25 |

pick the file that matches your Minecraft version; its version number ends in it, like `1.1.0+26.3`. [Fabric API](https://modrinth.com/mod/fabric-api) is a required dependency.

## installation

1. install Fabric Loader and Fabric API.
2. place the Regrowth: Revamped JAR in the server's `mods` directory.
3. start the server once to generate `config/regrowthconfig.properties`.

for singleplayer, place the JAR in the instance's `mods` directory. the mod runs through the integrated server.

> villagers can change roads and build walls. back up an existing world, or try the mod on a copy, before adding it to a long-running save.

## data-pack integration

village profiles:

```text
data/<namespace>/regrowth/village_profiles/*.json
```

profession roles:

```text
data/<namespace>/regrowth/profession_roles/*.json
```

safety and material tags:

- `regrowth:wall_foundations`
- `regrowth:wall_replaceable`
- `regrowth:road_replaceable`
- `regrowth:village_roads`
- `regrowth:protected_from_villagers`

block entities are always protected. other mods can append blocks to these tags without adding a Java dependency.

the full profile format is in the [adaptive villages guide](https://github.com/cairodavila/regrowth-revamped/blob/main/docs/adaptive-villages.md).

## source and issues

- [source code](https://github.com/cairodavila/regrowth-revamped)
- [issue tracker](https://github.com/cairodavila/regrowth-revamped/issues)
- [changelog](https://github.com/cairodavila/regrowth-revamped/blob/main/CHANGELOG.md)

please report Revamped-specific issues there rather than on the original project's tracker.

## credits

- MacTso and Lupicus — original Regrowth design and implementation
- Cairo D'Avila — current Fabric ports and adaptive village work

original project:

- [GitHub](https://github.com/mactso/Regrowth)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/regrowth)

## license

Regrowth: Revamped is distributed under the [MIT license](https://github.com/cairodavila/regrowth-revamped/blob/main/LICENSE). the original copyright and permission notice are preserved in the source repository and packaged JAR.

## AI disclosure

AI-assisted coding and editing tools were used during the port, the adaptive-village implementation, testing, and the preparation of this page. the gameplay GIF above is an in-game screen recording.
