# adaptive villages

Regrowth's adaptive village system keeps road and wall improvements while avoiding assumptions about vanilla village layouts.

## design goals

- derive village footprints from meeting, home and job-site POIs.
- keep the original fixed-square behavior available as `legacy` mode.
- never overwrite buildings, block entities, or blocks outside explicit replacement tags.
- let data packs describe biome and structure profiles, materials and profession roles.
- give each village one boundary, however many bells it has.
- cache plans per dimension and village instead of planning on every villager tick.
- give operators analyze, preview, enable/disable, mode and rebuild controls.

## runtime model

`VillagePlanManager` owns cached plans keyed by dimension and village. bells within 64 blocks of each other, horizontally and through chains of bells, belong to one village. its plan, controls and commands are keyed by one representative bell: the bell nearest the centroid of the village POIs around all of its bells, with ties going to the smallest coordinates. a village with one bell is keyed by that bell. villagers remembering any of its bells, or a bell that was removed, use the representative's plan. the grouping is cached alongside the plans, so it's only recalculated when they expire or a command invalidates them. the boundary is still planned around the representative bell alone, from the POIs within the profile's `scan_radius` of it and no farther out than `maximum_radius` (64 blocks each by default). a large modded or overhauled village whose bells chain out beyond that gets no boundary around its far parts, so villagers there neither build walls nor repair roads; raise both radii in the village's profile to cover it. a plan holds an ordered boundary and marks each node as wall, gate or opening. adaptive boundaries are the expanded convex hull of nearby POIs, clamped by the profile radii. legacy plans keep the original square boundary. when every node of a boundary crosses a road, the whole boundary is left open.

`VillageService` is the adapter between the plans and Minecraft. it gathers POIs, selects profiles, resolves terrain and tagged materials, repairs roads, places planned wall pieces (extending started segments first, and raising a column to meet a neighbor one or two blocks higher), renders previews, and reads persistent village controls. geometry stays separate so it can be unit-tested without a running server.

villagers still work incrementally. one action repairs or places at most one local block; plans describe where work is allowed rather than applying a whole village in one operation.

## village profiles

profiles are server-data JSON resources under `data/<namespace>/regrowth/village_profiles/*.json`. higher priorities win. empty biome or structure selectors match anything. a selector is an exact identifier, or a tag prefixed with `#`.

```json
{
  "priority": 100,
  "match": {
    "biomes": ["#minecraft:is_plains"],
    "structures": ["#minecraft:village"]
  },
  "boundary": {
    "mode": "poi_hull",
    "margin": 8,
    "scan_radius": 64,
    "minimum_radius": 18,
    "maximum_radius": 64,
    "gate_scan_distance": 0,
    "cache_ticks": 6000
  },
  "walls": {
    "blocks": ["minecraft:cobblestone_wall"],
    "fences": ["minecraft:oak_fence"],
    "gates": ["minecraft:oak_fence_gate"],
    "foundations": ["#regrowth:wall_foundations"],
    "replaceable": ["#regrowth:wall_replaceable"],
    "protected": ["#regrowth:protected_from_villagers"],
    "torch_spacing": 6
  },
  "roads": {
    "blocks": ["#regrowth:village_roads"],
    "replaceable": ["#regrowth:road_replaceable"],
    "protected": ["#regrowth:protected_from_villagers"],
    "repair": "gaps_only",
    "smooth_slopes": true
  },
  "lighting": {
    "lamp_post": "plains"
  }
}
```

`gate_scan_distance` is how far inward and outward from a boundary node a road still opens it. at 0, the default, a node opens only when its own ground is road. the boundary is a closed line of orthogonal steps, so every path that crosses it, even a diagonal one, has a road block on one of its nodes, while a path running alongside the boundary no longer opens it. one open node is added on each side of every crossing.

`lamp_post` picks the lamp post villagers put up, copied from vanilla's village lamps:

| value | post |
| --- | --- |
| `plains` | two oak fences under stripped oak wood, with a wall torch on each side |
| `desert` | cut sandstone, terracotta and a torch |
| `snowy` | three spruce fences with an arm off the top one and a lantern hanging under it |
| `fence_torch` | two fences from the profile's `walls.fences` with a torch on top |
| `none` | no lamp posts |

a profile without a `lighting` section, like one written for 1.1.0, gets `fence_torch`, and so does an unknown value. the shipped profiles use `plains` for plains villages, `desert` and `snowy` for theirs, and `fence_torch` everywhere else.

a villager with the `lighting` role and `t` in its actions puts up a whole post in one action, on one of the four columns next to it, never its own. the column has to be inside the village boundary under open sky, on natural ground from the wall foundations that isn't road, with road on at least one side. it has to be dark: block light 1 or less, so a lit post or a house's torches keep the next post away by themselves: about 11 to 13 blocks counted along the x and z axes, which can be as little as 8 blocks straight across on a diagonal, and less where a slope or a cliff blocks the light. every block of the post goes into air or a small plant from `regrowth:wall_replaceable`, and nothing of it may be within two blocks of the planned wall line, next to a door or a bed, or within two blocks of a bed, job site or bell. the snowy arm points away from the road, or along it, never over the road or the villager. `/regrowth village disable` stops lamp posts like it stops walls and roads; the wall control block doesn't affect them.

structure selectors are optional because many structure data packs keep vanilla village POIs but don't add their structures to the vanilla village structure tag. a pack can target an exact custom structure identifier when it needs to.

## profession roles

profession rules are resources under `data/<namespace>/regrowth/profession_roles/*.json`.

```json
{
  "priority": 100,
  "professions": ["minecraft:farmer"],
  "roles": ["farm"]
}
```

supported roles are `clear`, `farm`, `heal_people`, `heal_golems`, `lighting`, `plant_flowers`, `roads`, `trim` and `walls`. selectors take exact profession identifiers, profession tags, `*`, and namespace wildcards such as `morevillagers:*`.

## safety tags

- `regrowth:wall_foundations`: ground that may support a planned wall.
- `regrowth:wall_replaceable`: blocks a wall or lamp post may safely replace: air, snow and small plants such as grass, flowers, saplings and bushes. a plant holding water is never replaced, whatever the tag says.
- `regrowth:road_replaceable`: natural ground that road repair may replace.
- `regrowth:village_roads`: blocks recognized as village roads.
- `regrowth:protected_from_villagers`: blocks that construction and repair must never replace.

block entities are always protected, whatever the tags say. other mods and modpacks can append values to these tags without Java integration code.

## commands

- `/regrowth village analyze`: report the nearest village's representative bell, profile, mode, POI count, boundary size, gate count and control state.
- `/regrowth village preview` (or `/regrowth village preview walls`): show the planned wall, openings and gates with particles, without changing blocks.
- `/regrowth village enable|disable`: persistently allow or pause road, wall and lamp post work for the nearest village.
- `/regrowth village mode default|adaptive|legacy`: choose a persistent per-village mode override.
- `/regrowth village rebuild`: invalidate and recalculate the nearest village plan.

these commands need game-master permission and a meeting POI near the player running them. they act on the village of the nearest bell, keyed by its representative bell. a setting stored on another bell of the village, as 1.1.0 did, still applies until the village's setting is changed.

wall construction also keeps Regrowth's physical opt-in. unless `key.playerWallControlBlockString` is set to `minecraft:air`, the configured control block must be directly above the village's representative bell, the one `analyze` reports. a block above any other bell of the same village doesn't count. new village chunks receive the control block automatically, as before. a village that had already been explored before the mod was added gets no automatic block, so its walls don't start until someone places the configured control block on the bell that `analyze` reports. removing it pauses walls without stopping road work; the per-village `disable` command pauses both. an unknown block id falls back to `minecraft:cobblestone_wall` and logs a warning, so a typo never turns the opt-in off.

## compatibility strategy

compatibility is generic rather than a list of hard-coded mods:

- structure overhauls benefit from POI-derived boundaries and road-aligned gates.
- biome mods can extend biome tags or add a profile with their preferred palette.
- fence and decoration mods can extend the material and safety tags.
- profession mods can add role resources.
- protection mods can integrate later through a small block-change veto in `VillageService`, without leaking third-party APIs into the planning code.
