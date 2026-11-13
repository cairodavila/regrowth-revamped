package com.mactso.regrowth.village;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** Groups the bells of one village and picks the one its plan, controls and commands are keyed by. */
public final class VillageBells {

	/** Bells this close to another bell of the village, horizontally, belong to the same village. */
	public static final int SAME_VILLAGE_DISTANCE = 64;

	private VillageBells() {
	}

	/**
	 * Every bell reachable from {@code start} in steps of at most {@link #SAME_VILLAGE_DISTANCE}, sorted.
	 * {@code bellsAround} returns the bells near a position; anything farther than the distance is ignored.
	 * {@code start} is only included when it is still a bell, so a villager remembering a removed bell joins
	 * the village around it; the result is empty when there is none.
	 */
	public static List<GridPoint> village(GridPoint start, Function<GridPoint, Collection<GridPoint>> bellsAround) {
		long maximumDistanceSquared = (long) SAME_VILLAGE_DISTANCE * SAME_VILLAGE_DISTANCE;
		Set<GridPoint> found = new TreeSet<>();
		Deque<GridPoint> pending = new ArrayDeque<>(List.of(start));
		while (!pending.isEmpty()) {
			GridPoint bell = pending.pop();
			for (GridPoint other : bellsAround.apply(bell)) {
				if (other.distanceSquared(bell) <= maximumDistanceSquared && found.add(other)) {
					pending.add(other);
				}
			}
		}
		return List.copyOf(found);
	}

	/**
	 * The bell nearest the centroid of the village's POIs, so the ring stays centred on the village. A village
	 * with one bell keeps it, and ties go to the smallest coordinates so every villager picks the same bell.
	 */
	public static GridPoint representative(Collection<GridPoint> bells, Collection<GridPoint> pointsOfInterest) {
		if (bells.size() == 1) {
			return bells.iterator().next();
		}
		Collection<GridPoint> village = pointsOfInterest.isEmpty() ? bells : pointsOfInterest;
		double centerX = village.stream().mapToInt(GridPoint::x).average().orElseThrow();
		double centerZ = village.stream().mapToInt(GridPoint::z).average().orElseThrow();
		return bells.stream()
				.min(Comparator.comparingDouble((GridPoint bell) -> {
					double deltaX = bell.x() - centerX;
					double deltaZ = bell.z() - centerZ;
					return deltaX * deltaX + deltaZ * deltaZ;
				}).thenComparing(Comparator.naturalOrder()))
				.orElseThrow();
	}
}
