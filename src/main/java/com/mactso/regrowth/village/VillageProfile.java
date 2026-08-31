package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record VillageProfile(
		Identifier id,
		int priority,
		List<ResourceSelector> biomes,
		List<ResourceSelector> structures,
		VillageBoundarySettings boundary,
		WallSettings walls,
		RoadSettings roads) {

	public VillageProfile {
		biomes = List.copyOf(biomes);
		structures = List.copyOf(structures);
	}

	public static VillageProfile parse(Identifier id, JsonObject root) {
		int priority = integer(root, "priority", 0);
		JsonObject match = object(root, "match");
		List<ResourceSelector> biomes = selectors(match, "biomes", List.of());
		List<ResourceSelector> structures = selectors(match, "structures", List.of());

		JsonObject boundaryJson = object(root, "boundary");
		VillageBoundarySettings defaults = VillageBoundarySettings.DEFAULT;
		VillageBoundarySettings boundary = new VillageBoundarySettings(
				VillageBoundaryMode.parse(string(boundaryJson, "mode", "poi_hull"), defaults.mode()),
				integer(boundaryJson, "margin", defaults.margin()),
				integer(boundaryJson, "scan_radius", defaults.scanRadius()),
				integer(boundaryJson, "minimum_radius", defaults.minimumRadius()),
				integer(boundaryJson, "maximum_radius", defaults.maximumRadius()),
				integer(boundaryJson, "legacy_radius", defaults.legacyRadius()),
				integer(boundaryJson, "gate_scan_distance", defaults.gateScanDistance()),
				longValue(boundaryJson, "cache_ticks", defaults.cacheTicks()));

		JsonObject wallsJson = object(root, "walls");
		WallSettings wallDefaults = WallSettings.defaults();
		WallSettings walls = new WallSettings(
				selectors(wallsJson, "blocks", wallDefaults.blocks()),
				selectors(wallsJson, "fences", wallDefaults.fences()),
				selectors(wallsJson, "gates", wallDefaults.gates()),
				selectors(wallsJson, "foundations", wallDefaults.foundations()),
				selectors(wallsJson, "replaceable", wallDefaults.replaceable()),
				selectors(wallsJson, "protected", wallDefaults.protectedBlocks()),
				integer(wallsJson, "torch_spacing", wallDefaults.torchSpacing()));

		JsonObject roadsJson = object(root, "roads");
		RoadSettings roadDefaults = RoadSettings.defaults();
		RoadSettings roads = new RoadSettings(
				selectors(roadsJson, "blocks", roadDefaults.blocks()),
				selectors(roadsJson, "replaceable", roadDefaults.replaceable()),
				selectors(roadsJson, "protected", roadDefaults.protectedBlocks()),
				RoadRepairMode.parse(string(roadsJson, "repair", "gaps_only")),
				bool(roadsJson, "smooth_slopes", roadDefaults.smoothSlopes()));

		return new VillageProfile(id, priority, biomes, structures, boundary, walls, roads);
	}

	public static VillageProfile fallback() {
		return new VillageProfile(
				Identifier.fromNamespaceAndPath("regrowth", "fallback"),
				Integer.MIN_VALUE,
				List.of(),
				List.of(),
				VillageBoundarySettings.DEFAULT,
				WallSettings.defaults(),
				RoadSettings.defaults());
	}

	public boolean matchesBiome(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		return biomes.isEmpty() || biomes.stream().anyMatch(selector -> selector.matches(biome));
	}

	public record WallSettings(
			List<ResourceSelector> blocks,
			List<ResourceSelector> fences,
			List<ResourceSelector> gates,
			List<ResourceSelector> foundations,
			List<ResourceSelector> replaceable,
			List<ResourceSelector> protectedBlocks,
			int torchSpacing) {

		public WallSettings {
			blocks = List.copyOf(blocks);
			fences = List.copyOf(fences);
			gates = List.copyOf(gates);
			foundations = List.copyOf(foundations);
			replaceable = List.copyOf(replaceable);
			protectedBlocks = List.copyOf(protectedBlocks);
			torchSpacing = Math.clamp(torchSpacing, 0, 32);
		}

		static WallSettings defaults() {
			return new WallSettings(
					selectorList("minecraft:cobblestone_wall"),
					selectorList("minecraft:oak_fence"),
					selectorList("minecraft:oak_fence_gate"),
					selectorList("#regrowth:wall_foundations"),
					selectorList("#regrowth:wall_replaceable"),
					selectorList("#regrowth:protected_from_villagers"),
					6);
		}

		public Optional<Block> wallBlock() {
			return firstBlock(blocks, state -> state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES));
		}

		public Optional<Block> fenceBlock() {
			return firstBlock(fences, state -> state.is(BlockTags.FENCES));
		}

		public Optional<Block> gateBlock() {
			return firstBlock(gates, state -> state.is(BlockTags.FENCE_GATES));
		}
	}

	public record RoadSettings(
			List<ResourceSelector> blocks,
			List<ResourceSelector> replaceable,
			List<ResourceSelector> protectedBlocks,
			RoadRepairMode repairMode,
			boolean smoothSlopes) {

		public RoadSettings {
			blocks = List.copyOf(blocks);
			replaceable = List.copyOf(replaceable);
			protectedBlocks = List.copyOf(protectedBlocks);
			repairMode = repairMode == null ? RoadRepairMode.GAPS_ONLY : repairMode;
		}

		static RoadSettings defaults() {
			return new RoadSettings(
					selectorList("#regrowth:village_roads"),
					selectorList("#regrowth:road_replaceable"),
					selectorList("#regrowth:protected_from_villagers"),
					RoadRepairMode.GAPS_ONLY,
					true);
		}
	}

	public enum RoadRepairMode {
		DISABLED,
		GAPS_ONLY;

		static RoadRepairMode parse(String value) {
			return "disabled".equalsIgnoreCase(value) ? DISABLED : GAPS_ONLY;
		}
	}

	private static Optional<Block> firstBlock(
			List<ResourceSelector> selectors,
			Predicate<BlockState> validator) {
		for (ResourceSelector selector : selectors) {
			Optional<Block> block = selector.firstBlock(validator);
			if (block.isPresent()) {
				return block;
			}
		}
		return Optional.empty();
	}

	private static List<ResourceSelector> selectorList(String... values) {
		List<ResourceSelector> selectors = new ArrayList<>(values.length);
		for (String value : values) {
			selectors.add(ResourceSelector.parse(value));
		}
		return List.copyOf(selectors);
	}

	private static List<ResourceSelector> selectors(
			JsonObject object,
			String key,
			List<ResourceSelector> fallback) {
		if (!object.has(key)) {
			return fallback;
		}
		JsonArray array = object.getAsJsonArray(key);
		List<ResourceSelector> selectors = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			selectors.add(ResourceSelector.parse(element.getAsString()));
		}
		return List.copyOf(selectors);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) ? object.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		return object.has(key) ? object.get(key).getAsInt() : fallback;
	}

	private static long longValue(JsonObject object, String key, long fallback) {
		return object.has(key) ? object.get(key).getAsLong() : fallback;
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		return object.has(key) ? object.get(key).getAsBoolean() : fallback;
	}
}
