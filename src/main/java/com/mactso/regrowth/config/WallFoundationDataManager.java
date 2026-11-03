package com.mactso.regrowth.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class WallFoundationDataManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<String, wallFoundationItem> wallFoundations = new HashMap<>();

	public static wallFoundationItem getWallFoundationInfo(String blockId) {
		return wallFoundations.get(blockId);
	}

	public static void wallFoundationsInit() {
		wallFoundations.clear();
		for (String entry : MyConfig.getWallFoundationsList().split(";")) {
			String blockId = entry.trim();
			if (blockId.isEmpty()) {
				continue;
			}
			Identifier identifier = Identifier.tryParse(blockId);
			if (identifier == null) {
				LOGGER.warn("Regrowth: wall foundation '{}' is not a valid id ([a-z0-9_.-] only)", blockId);
			} else if (!BuiltInRegistries.BLOCK.containsKey(identifier)) {
				LOGGER.warn("Regrowth: wall foundation '{}' is not in the block registry. Misspelled? Missing semicolon?",
						blockId);
			} else {
				wallFoundations.put(blockId, new wallFoundationItem(blockId));
			}
		}
	}

	public static class wallFoundationItem {
		String wallFoundationType; // a block like "minecraft:block" or "oby:dirt"

		public wallFoundationItem(String wallFoundationType) {
			this.wallFoundationType = wallFoundationType;
		}
	}
}
