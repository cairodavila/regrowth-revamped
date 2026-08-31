# Adaptive villages

Regrowth's adaptive village system keeps road and wall improvements while avoiding assumptions about vanilla village layouts.

## Design goals

- Derive village footprints from meeting, home, and job-site POIs.
- Keep the original fixed-square behavior available as `legacy` mode.
- Never overwrite buildings, block entities, or blocks outside explicit replacement tags.
- Let data packs describe biome/structure profiles, materials, and profession roles.
- Cache plans per dimension and meeting point instead of planning from every villager tick.
- Give operators analyze, preview, enable/disable, mode, and rebuild controls.

## Runtime model

`VillagePlanManager` owns cached plans keyed by dimension and meeting point. A plan contains an ordered boundary and identifies wall, gate, and opening nodes. Adaptive boundaries are the expanded convex hull of nearby POIs, clamped by profile radii. Legacy plans retain the original square boundary.

`VillageService` is the Minecraft boundary adapter. It gathers POIs, selects profiles, resolves terrain and tagged materials, repairs roads, places planned wall pieces, renders previews, and reads persistent village controls. Geometry remains separate so it can be unit-tested without a running server.

Villagers still perform work incrementally. One action repairs or places at most one local block; plans describe where work is allowed rather than applying a village in one operation.

## Village profiles

Profiles are server-data JSON resources under `data/<namespace>/regrowth/village_profiles/*.json`. Higher priorities win. Empty biome or structure selectors match any value. A selector is an exact identifier or a tag prefixed by `#`.

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
    "gate_scan_distance": 6,
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

Structure selectors are optional because many structure data packs preserve vanilla village POIs but do not add their structures to the vanilla village structure tag. A pack can target an exact custom structure identifier when necessary.

## Profession roles

Profession rules are resources under `data/<namespace>/regrowth/profession_roles/*.json`.

```json
{
  "priority": 100,
  "professions": ["minecraft:farmer"],
  "roles": ["farm"]
}
```

Supported roles are `clear`, `farm`, `heal_people`, `heal_golems`, `lighting`, `plant_flowers`, `roads`, `trim`, and `walls`. Selectors support exact profession identifiers, profession tags, `*`, and namespace wildcards such as `morevillagers:*`.

## Safety tags

- `regrowth:wall_foundations`: ground that may support a planned wall.
- `regrowth:wall_replaceable`: blocks that a wall may safely replace.
- `regrowth:road_replaceable`: natural ground that road repair may replace.
- `regrowth:village_roads`: blocks recognized as village roads.
- `regrowth:protected_from_villagers`: blocks that construction and repair must never replace.

Block entities are always protected, regardless of tags. Other mods and modpacks can append values to these tags without Java integration code.

## Commands

- `/regrowth village analyze`: report the nearest village's profile, mode, POI count, boundary size, gate count, and control state.
- `/regrowth village preview` (or `/regrowth village preview walls`): show the planned wall, openings, and gates with particles without changing blocks.
- `/regrowth village enable|disable`: persistently allow or pause road and wall work for the nearest village.
- `/regrowth village mode default|adaptive|legacy`: choose a persistent per-village mode override.
- `/regrowth village rebuild`: invalidate and recalculate the nearest village plan.

These commands require game-master permission and a meeting POI near the invoking player.

Wall construction also preserves Regrowth's existing physical opt-in. Unless `key.playerWallControlBlockString` is set to `minecraft:air`, the configured control block must be directly above the village meeting POI. New village chunks receive the configured control block automatically, matching legacy behavior. Removing it pauses walls without disabling road work; the per-village `disable` command pauses both.

## Compatibility strategy

Compatibility is generic rather than a list of hard-coded mods:

- Structure overhauls benefit from POI-derived boundaries and road-aligned gates.
- Biome mods can extend biome tags or add a profile with their preferred palette.
- Fence and decoration mods can extend material and safety tags.
- Profession mods can add role resources.
- Protection mods can integrate later through a small block-change veto boundary in `VillageService` without leaking third-party APIs into planning code.
