package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mactso.regrowth.village.VillagePlanCache.VillageKey;

class VillagePlanCacheTest {

	@Test
	void cachedPlanExpiresAndCanBeInvalidated() {
		VillagePlanCache cache = new VillagePlanCache();
		VillageKey key = new VillageKey("minecraft:overworld", 42L);
		VillagePlan plan = VillageGeometryPlanner.create(
				new GridPoint(0, 0), List.of(), VillageBoundarySettings.DEFAULT);

		cache.put(key, plan, 100, 20);
		assertTrue(cache.get(key, 119).isPresent());
		assertTrue(cache.get(key, 120).isEmpty());
		assertEquals(0, cache.size());

		cache.put(key, plan, 200, 20);
		cache.invalidate(key);
		assertTrue(cache.get(key, 201).isEmpty());
	}
}
