// 15.2 - 1.0.0.0 regrowth
package com.mactso.regrowth.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegrowthEntitiesManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<String, RegrowthMobItem> regrowthMobs = new HashMap<>();

	public static RegrowthMobItem getRegrowthMobInfo(String entityId) {
		return regrowthMobs.get(entityId);
	}

	public static void regrowthMobInit() {
		regrowthMobs.clear();
		regrowthMobs.putAll(parseActionMobList(MyConfig.getActionMobList()));
	}

	static Map<String, RegrowthMobItem> parseActionMobList(String actionMobList) {
		Map<String, RegrowthMobItem> mobs = new HashMap<>();
		for (String mobEntry : actionMobList.split(";")) {
			if (mobEntry.isBlank()) {
				continue;
			}
			String[] fields = mobEntry.split(",");
			try {
				String entityId = fields[0].trim();
				String actions = fields[1].trim();
				// An entry without actions would still run the shared overcrowding damage.
				if (entityId.isEmpty() || actions.isEmpty()) {
					throw new IllegalArgumentException("blank entity or actions");
				}
				double seconds = Math.max(1.0, Double.parseDouble(fields[2].trim()));
				mobs.put(entityId, new RegrowthMobItem(actions, seconds));
			} catch (RuntimeException e) {
				LOGGER.warn("Regrowth: bad actionMobList entry '{}', expected entity,actions,seconds", mobEntry.trim());
			}
		}
		return mobs;
	}

	public static class RegrowthMobItem {
		private final double regrowthEventSeconds;
		private final String regrowthAction;

		public RegrowthMobItem(String regrowthType, double regrowthEventSeconds) {
			this.regrowthAction = regrowthType;
			this.regrowthEventSeconds = regrowthEventSeconds;
		}

		public String getRegrowthActions() {
			return regrowthAction.toLowerCase();
		}

		public double getRegrowthEventSeconds() {
			return regrowthEventSeconds;
		}
	}
}
