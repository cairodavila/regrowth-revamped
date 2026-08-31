package com.mactso.regrowth.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class VillagePlanCache {

	private final Map<VillageKey, CachedPlan> plans = new HashMap<>();

	public Optional<VillagePlan> get(VillageKey key, long gameTime) {
		CachedPlan cachedPlan = plans.get(key);
		if (cachedPlan == null) {
			return Optional.empty();
		}
		if (gameTime >= cachedPlan.expiresAt()) {
			plans.remove(key);
			return Optional.empty();
		}
		return Optional.of(cachedPlan.plan());
	}

	public void put(VillageKey key, VillagePlan plan, long gameTime, long cacheTicks) {
		plans.put(key, new CachedPlan(plan, gameTime + cacheTicks));
	}

	public void invalidate(VillageKey key) {
		plans.remove(key);
	}

	public void clear() {
		plans.clear();
	}

	public int size() {
		return plans.size();
	}

	public record VillageKey(String dimension, long meetingPoint) {
	}

	private record CachedPlan(VillagePlan plan, long expiresAt) {
	}
}
