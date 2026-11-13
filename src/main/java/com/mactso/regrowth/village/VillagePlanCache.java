package com.mactso.regrowth.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Values per dimension and bell that expire after a number of ticks. */
public final class VillagePlanCache<V> {

	private final Map<VillageKey, CachedPlan<V>> plans = new HashMap<>();

	public Optional<V> get(VillageKey key, long gameTime) {
		CachedPlan<V> cachedPlan = plans.get(key);
		if (cachedPlan == null) {
			return Optional.empty();
		}
		if (gameTime >= cachedPlan.expiresAt()) {
			plans.remove(key);
			return Optional.empty();
		}
		return Optional.of(cachedPlan.plan());
	}

	public void put(VillageKey key, V plan, long gameTime, long cacheTicks) {
		plans.put(key, new CachedPlan<>(plan, gameTime + cacheTicks));
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

	private record CachedPlan<V>(V plan, long expiresAt) {
	}
}
