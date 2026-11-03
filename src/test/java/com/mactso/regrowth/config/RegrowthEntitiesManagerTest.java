package com.mactso.regrowth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mactso.regrowth.config.RegrowthEntitiesManager.RegrowthMobItem;

class RegrowthEntitiesManagerTest {

	@Test
	void entryWithoutActionsIsRejected() {
		Map<String, RegrowthMobItem> mobs = RegrowthEntitiesManager.parseActionMobList(
				"minecraft:cow,,1;,eat,5;minecraft:sheep, eat , 120.0");

		assertFalse(mobs.containsKey("minecraft:cow"));
		assertFalse(mobs.containsKey(""));
		assertEquals("eat", mobs.get("minecraft:sheep").getRegrowthActions());
		assertEquals(120.0, mobs.get("minecraft:sheep").getRegrowthEventSeconds());
	}
}
