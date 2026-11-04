# changelog

every release of Regrowth: Revamped, newest first. versions follow [semver](https://semver.org); each release is built once per supported Minecraft version, marked as build metadata (`1.1.0+26.3`).

## unreleased

### changed

- village walls grow as continuous runs. a villager near the boundary extends a wall segment that's already started before beginning a new one, so the wall no longer appears as scattered single posts.
- where the ground steps up one or two blocks, the lower wall column is raised to meet its neighbor, so walls stay connected on terraced terrain. bigger drops are left as they are; the cliff is already a barrier.
- the default `gate_scan_distance` is now 1, so only roads that actually cross the boundary leave a gap. it was 6, which also opened the wall wherever a road ran alongside it.
- a planned wall node whose ground turns out to be a road is left open, even if its chunk wasn't loaded when the plan was made.

## 1.1.0 · 2026-11-03

supports Minecraft 26.2 and 26.3.

### added

- Minecraft 26.3 support. 26.2 keeps getting updates: every release now ships one JAR per Minecraft version, built from the same source.
- version numbers carry the Minecraft version, like `1.1.0+26.2` and `1.1.0+26.3`.

### changed

- villagers no longer treat buttons as pebbles, so player-placed floor buttons are no longer replaced with torches.
- an unknown `key.playerWallControlBlockString` falls back to `minecraft:cobblestone_wall` and logs a warning. it used to resolve to air, which turns the wall opt-in off.
- `key.actionMobList` entries with an empty entity or empty actions are skipped with a warning instead of running the overcrowding check.
- personal fences (`p`) read the wall settings for their biome. badlands now uses the `minecraft:mesa` entry.
- a village boundary where every node crosses a road is left open instead of walled.
- debug names (`debugLevel` above 0) only go on unnamed villagers and no longer replace names players gave them.
- config parsing problems are logged as warnings instead of printed to the console.

### fixed

- villagers with even entity ids never got the faster road and wall work in young villages.
- grass cutting could break the dirt path or farmland a villager stood on.
- villagers cut tall flowers along with tall grass.
- farm torches went on any smooth sandstone, whether or not water was next to it.
- villager lighting skipped the ground check and reported success when nothing was placed.
- beekeepers almost never planted flowers, and when they did they could overwrite the block the villager stood in, like a door.
- `key.mushroomDensity` counted small mushrooms instead of huge mushrooms, so it had no effect.
- overcrowded animals were teleported to y=-66, which no longer reaches the void, and their death sound only played client-side. they're now removed, and everyone nearby hears the sound.
- animals that grow plants bonemealed the air above the grass instead of the grass.
- villagers facing a negative yaw trimmed leaves and hopped off walls in the mirrored direction.

### removed

- the old road and wall code that the adaptive village planner replaced, plus unused helpers and commented-out code.

### development

- the build uses [Stonecutter](https://stonecutter.kikugie.dev/) and the Gradle Kotlin DSL. per-version values live in `stonecutter.properties.toml`, and `./gradlew buildAndCollect` builds every version into `build/libs/<mod version>/`.
- Loom 1.19 without the identity-mapping workaround, Gradle 9.8.2 and Fabric Loader 0.20.1.
- data reloading uses Fabric API's resource loader v1 instead of the deprecated v0.

## 1.0.0 · 2026-08-31

first release of Regrowth: Revamped, for Minecraft 26.2.

### added

- Minecraft 26.2 and Java 25 support.
- village boundaries generated from nearby meeting, home and job-site POIs, with the original square layout available as `legacy` mode.
- road-crossing gates instead of walls placed across paths.
- terrain-aware wall placement and conservative block replacement using data-pack tags.
- data-driven biome and structure profiles, and profession roles for vanilla and modded villagers.
- `/regrowth village` analyze, preview, enable, disable, mode and rebuild commands.
- cached village plans instead of recalculating geometry on every villager tick.
