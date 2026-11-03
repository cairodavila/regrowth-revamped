package com.mactso.regrowth.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mactso.regrowth.utility.Utility;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class WallBiomeDataManager {

	public static class WallBiomeDataItem {
		private final int wallDiameter;
		private final BlockState wallBlockState;
		private final BlockState fenceBlockState;

		public WallBiomeDataItem(int wallDiameter, BlockState wallBlockState, BlockState fenceBlockState) {
			this.wallDiameter = wallDiameter;
			this.wallBlockState = wallBlockState;
			this.fenceBlockState = fenceBlockState;
		}

		public BlockState getFenceBlockState() {
			return fenceBlockState;
		}

		public BlockState getWallBlockState() {
			return wallBlockState;
		}

		public int getWallDiameter() {
			return wallDiameter;
		}
	}

	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MINIMUM_WALL_DIAMETER = 24;
	private static final int MAXIMUM_WALL_DIAMETER = 80;
	private static final BlockState DEFAULT_WALL_BLOCKSTATE = Blocks.COBBLESTONE_WALL.defaultBlockState();
	private static final BlockState DEFAULT_FENCE_BLOCKSTATE = Blocks.OAK_FENCE.defaultBlockState();
	private static final WallBiomeDataItem DEFAULT_WALL_ITEM = new WallBiomeDataItem(36, DEFAULT_WALL_BLOCKSTATE,
			DEFAULT_FENCE_BLOCKSTATE);

	private static final Map<String, WallBiomeDataItem> wallBiomeData = new HashMap<>();

	public static WallBiomeDataItem getWallBiomeDataItem(String biomeKey) {
		WallBiomeDataItem item = wallBiomeData.get(biomeKey);
		if (item == null) {
			Utility.debugMsg(1, "Requested wall has unknown biome: " + biomeKey + ", using the default wall.");
			return DEFAULT_WALL_ITEM;
		}
		return item;
	}

	public static void wallBiomeDataInit() {
		wallBiomeData.clear();
		for (String entry : MyConfig.getWallblockList().split(";")) {
			if (entry.isBlank()) {
				continue;
			}
			String[] fields = entry.split(",");
			try {
				int wallDiameter = Math.clamp(Integer.parseInt(fields[1].trim()), MINIMUM_WALL_DIAMETER,
						MAXIMUM_WALL_DIAMETER);
				BlockState wallBlockState = taggedBlockState(fields[2], BlockTags.WALLS, DEFAULT_WALL_BLOCKSTATE);
				BlockState fenceBlockState = taggedBlockState(fields[3], BlockTags.FENCES, DEFAULT_FENCE_BLOCKSTATE);
				wallBiomeData.put(fields[0].trim(), new WallBiomeDataItem(wallDiameter, wallBlockState, fenceBlockState));
			} catch (RuntimeException e) {
				LOGGER.warn("Regrowth: bad wallblockList entry '{}', expected biome,diameter,wall,fence", entry.trim());
			}
		}
	}

	private static BlockState taggedBlockState(String blockId, TagKey<Block> requiredTag, BlockState fallback) {
		Identifier identifier = Identifier.tryParse(blockId.trim());
		if (identifier == null) {
			return fallback;
		}
		return BuiltInRegistries.BLOCK.getOptional(identifier)
				.map(Block::defaultBlockState)
				.filter(state -> state.is(requiredTag))
				.orElse(fallback);
	}
}
