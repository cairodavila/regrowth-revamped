# adaptive villages

Regrowth's adaptive village system keeps road and wall improvements while avoiding assumptions about vanilla village layouts.

## design goals

- derive village footprints from meeting, home and job-site POIs.
- keep the original fixed-square behavior available as `legacy` mode.
- never overwrite buildings, block entities, or blocks outside explicit replacement tags.
- let data packs describe biome and structure profiles, materials and profession roles.
- cache plans per dimension and meeting point instead of planning on every villager tick.
- give operators analyze, preview, enable/disable, mode and rebuild controls.

## runtime model

`VillagePlanManager` owns cached plans keyed by dimension and meeting point. a plan holds an ordered boundary and marks each node as wall, gate or opening. adaptive boundaries are the expanded convex hull of nearby POIs, clamped by the profile radii. legacy plans keep the original square boundary. when every node of a boundary crosses a road, the whole boundary is left open.

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
    "gate_scan_distance": 1,
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
  }
}
```

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
- `regrowth:wall_replaceable`: blocks a wall may safely replace.
- `regrowth:road_replaceable`: natural ground that road repair may replace.
- `regrowth:village_roads`: blocks recognized as village roads.
- `regrowth:protected_from_villagers`: blocks that construction and repair must never replace.

block entities are always protected, whatever the tags say. other mods and modpacks can append values to these tags without Java integration code.

## commands

- `/regrowth village analyze`: report the nearest village's profile, mode, POI count, boundary size, gate count and control state.
- `/regrowth village preview` (or `/regrowth village preview walls`): show the planned wall, openings and gates with particles, without changing blocks.
- `/regrowth village enable|disable`: persistently allow or pause road and wall work for the nearest village.
- `/regrowth village mode default|adaptive|legacy`: choose a persistent per-village mode override.
- `/regrowth village rebuild`: invalidate and recalculate the nearest village plan.

these commands need game-master permission and a meeting POI near the player running them.

wall construction also keeps Regrowth's physical opt-in. unless `key.playerWallControlBlockString` is set to `minecraft:air`, the configured control block must be directly above the village meeting POI. new village chunks receive the control block automatically, as before. removing it pauses walls without stopping road work; the per-village `disable` command pauses both. an unknown block id falls back to `minecraft:cobblestone_wall` and logs a warning, so a typo never turns the opt-in off.

## compatibility strategy

compatibility is generic rather than a list of hard-coded mods:

- structure overhauls benefit from POI-derived boundaries and road-aligned gates.
- biome mods can extend biome tags or add a profile with their preferred palette.
- fence and decoration mods can extend the material and safety tags.
- profession mods can add role resources.
- protection mods can integrate later through a small block-change veto in `VillageService`, without leaking third-party APIs into the planning code.
