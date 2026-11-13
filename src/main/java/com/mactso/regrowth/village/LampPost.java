package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mactso.regrowth.village.VillagePlan.CardinalDirection;

/**
 * Lamp post designs, copied from vanilla's village lamps, as blocks relative to the post's first block above the
 * ground. Choosing where a post goes is kept here too, so it can be tested without a server.
 */
public enum LampPost {
	NONE,
	/** Two oak fences under stripped oak wood with a wall torch on each side (vanilla plains_lamp_1). */
	PLAINS,
	/** Cut sandstone, terracotta and a torch (vanilla desert_lamp_1). */
	DESERT,
	/** Three spruce fences, an arm off the top one and a lantern hanging under it (vanilla snowy_lamp_post_02). */
	SNOWY,
	/** Two fences of the village's wood with a torch on top. */
	FENCE_TORCH;

	public static final LampPost DEFAULT = FENCE_TORCH;

	/** Block light at the post's spot must be at most this; a lit post keeps the next one away by itself. */
	public static final int MAXIMUM_BLOCK_LIGHT = 1;

	public static LampPost parse(String value, LampPost fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return fallback;
		}
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public boolean hasArm() {
		return this == SNOWY;
	}

	/** The post's blocks, bottom up, so each one is placed after what it rests on. {@code arm} only matters for SNOWY. */
	public List<Piece> pieces(CardinalDirection arm) {
		return switch (this) {
			case NONE -> List.of();
			case PLAINS -> List.of(
					new Piece(0, 0, 0, Part.FENCE),
					new Piece(0, 1, 0, Part.FENCE),
					new Piece(0, 2, 0, Part.STRIPPED_OAK_WOOD),
					new Piece(0, 2, -1, Part.WALL_TORCH),
					new Piece(1, 2, 0, Part.WALL_TORCH),
					new Piece(0, 2, 1, Part.WALL_TORCH),
					new Piece(-1, 2, 0, Part.WALL_TORCH));
			case DESERT -> List.of(
					new Piece(0, 0, 0, Part.CUT_SANDSTONE),
					new Piece(0, 1, 0, Part.TERRACOTTA),
					new Piece(0, 2, 0, Part.TORCH));
			case SNOWY -> List.of(
					new Piece(0, 0, 0, Part.FENCE),
					new Piece(0, 1, 0, Part.FENCE),
					new Piece(0, 2, 0, Part.FENCE),
					new Piece(arm.stepX(), 2, arm.stepZ(), Part.FENCE),
					new Piece(arm.stepX(), 1, arm.stepZ(), Part.HANGING_LANTERN));
			case FENCE_TORCH -> List.of(
					new Piece(0, 0, 0, Part.FENCE),
					new Piece(0, 1, 0, Part.FENCE),
					new Piece(0, 2, 0, Part.TORCH));
		};
	}

	/**
	 * Where a post may go around a villager standing at the origin: its four neighbors, in an order that starts at
	 * {@code rotation}. Never the villager's own column, so the post can't go up around it.
	 */
	public static List<CardinalDirection> candidateSides(int rotation) {
		CardinalDirection[] sides = CardinalDirection.values();
		List<CardinalDirection> order = new ArrayList<>(sides.length);
		for (int index = 0; index < sides.length; index++) {
			order.add(sides[Math.floorMod(rotation + index, sides.length)]);
		}
		return order;
	}

	/**
	 * Directions the snowy arm may point, best first: away from the road, then along it. Never over a road, where
	 * the lantern would hang at head height, and never over the villager standing next to the post.
	 */
	public static List<CardinalDirection> armDirections(List<CardinalDirection> roadSides, CardinalDirection villagerSide) {
		List<CardinalDirection> preferred = new ArrayList<>();
		List<CardinalDirection> along = new ArrayList<>();
		for (CardinalDirection direction : CardinalDirection.values()) {
			if (roadSides.contains(direction) || direction == villagerSide) {
				continue;
			}
			if (roadSides.contains(opposite(direction))) {
				preferred.add(direction);
			} else {
				along.add(direction);
			}
		}
		preferred.addAll(along);
		return preferred;
	}

	public static CardinalDirection opposite(CardinalDirection direction) {
		return switch (direction) {
			case NORTH -> CardinalDirection.SOUTH;
			case SOUTH -> CardinalDirection.NORTH;
			case EAST -> CardinalDirection.WEST;
			case WEST -> CardinalDirection.EAST;
		};
	}

	/** One block of a post. A wall torch faces away from the post's center column, toward its own offset. */
	public record Piece(int x, int y, int z, Part part) {
	}

	public enum Part {
		FENCE,
		STRIPPED_OAK_WOOD,
		WALL_TORCH,
		TORCH,
		CUT_SANDSTONE,
		TERRACOTTA,
		HANGING_LANTERN
	}
}
