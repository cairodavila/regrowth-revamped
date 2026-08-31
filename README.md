<p align="center">
  <img src="src/main/resources/icon.png" alt="Regrowth: Revamped" width="320">
</p>

# Regrowth: Revamped

[![GitHub release](https://img.shields.io/github/v/release/cairodavila/regrowth-revamped?label=release)](https://github.com/cairodavila/regrowth-revamped/releases)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/regrowth-revamped?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/regrowth-revamped)
[![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/loader-Fabric-DBD0B4)](https://fabricmc.net/)
[![MIT License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

An unofficial Fabric continuation of [Regrowth](https://github.com/mactso/Regrowth) by MacTso and Lupicus.

Regrowth lets creatures affect nearby vegetation and gives villagers small jobs around their villages. Revamped ports the mod to current Minecraft versions and adds adaptive village planning for modded and overhauled villages.

## what changed

- Minecraft 26.2 and Java 25 support
- village boundaries generated from nearby meeting, home and job-site POIs
- road-crossing gates instead of walls placed across paths
- terrain-aware wall placement with the original square layout available as `legacy` mode
- conservative block replacement using data-pack tags
- data-driven biome and structure profiles
- data-driven profession roles for vanilla and modded villagers
- per-village analyze, preview, enable, disable, mode and rebuild commands
- cached village plans instead of recalculating geometry on every villager tick

The existing animal and monster actions remain unchanged.

## requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 or newer
- Fabric API 0.158.0+26.2 or a compatible release

## installation

1. Download the JAR from [GitHub Releases](https://github.com/cairodavila/regrowth-revamped/releases) or [Modrinth](https://modrinth.com/mod/regrowth-revamped).
2. Copy it into the instance's `mods` directory.
3. Start Minecraft once to create `config/regrowthconfig.properties`.

Villagers can change roads and build walls. Back up an existing world, or test the mod on a copy first.

## adaptive villages

The default `poi_hull` mode builds a boundary around nearby village POIs and scans that boundary for roads. Plans are cached by dimension and meeting point. Villagers still work one block at a time; calculating a plan does not rebuild a village immediately.

Wall construction keeps Regrowth's original control-block behavior. Unless `key.playerWallControlBlockString` is set to `minecraft:air`, the configured block must be directly above the meeting POI. New village chunks receive the control block automatically.

Use `legacy` mode if you prefer the original fixed-square wall layout.

![A villager building an adaptive village wall](docs/assets/villager-building-wall.gif)

The full profile and role formats are documented in [docs/adaptive-villages.md](docs/adaptive-villages.md).

## villager actions

The default villager action string is still `chrwvt`:

| Letter | Action |
| --- | --- |
| `c` | clear obstructing natural vegetation |
| `h` | let clerics heal people and toolsmiths repair iron golems |
| `r` | clear snow and repair likely gaps in roads |
| `w` | build village walls and road-crossing gates |
| `v` | trim natural leaves and cactus |
| `t` | place lighting in dark areas, near farms and along walls |

Farm maintenance and profession-specific flower planting are assigned through profession roles and do not use those letters.

## commands

Commands require game-master permission and use the nearest meeting POI within 96 blocks.

| Command | Description |
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

## data-pack integration

Village profiles:

```text
data/<namespace>/regrowth/village_profiles/*.json
```

Profession roles:

```text
data/<namespace>/regrowth/profession_roles/*.json
```

Available safety and material tags:

- `regrowth:wall_foundations`
- `regrowth:wall_replaceable`
- `regrowth:road_replaceable`
- `regrowth:village_roads`
- `regrowth:protected_from_villagers`

Block entities are always protected. Other mods can append blocks to these tags without a Java dependency.

## building

The 26.2 branch requires Java 25.

```bash
git clone https://github.com/cairodavila/regrowth-revamped.git
cd regrowth-revamped
./gradlew clean build
```

The compiled JAR is written to `build/libs/`.

## versioning

Revamped uses semantic versioning starting at `1.0.0`. Minecraft compatibility is recorded in the JAR name and release metadata instead of reusing the original project's branch-dependent version numbers.

## credits

- MacTso and Lupicus — original Regrowth mod
- Cairo D'Avila — current Fabric ports and adaptive village work

Original project:

- [GitHub](https://github.com/mactso/Regrowth)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/regrowth)

Please report fork-specific issues here rather than on the original project's issue tracker.

## license

Regrowth: Revamped is available under the [MIT License](LICENSE). The original copyright and license notice are preserved in the repository and packaged JAR.
