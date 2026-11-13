package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mactso.regrowth.village.LampPost.Part;
import com.mactso.regrowth.village.LampPost.Piece;
import com.mactso.regrowth.village.VillagePlan.CardinalDirection;

class LampPostTest {

	@Test
	void unknownOrMissingDesignFallsBack() {
		assertEquals(LampPost.SNOWY, LampPost.parse(" Snowy ", LampPost.DEFAULT));
		assertEquals(LampPost.NONE, LampPost.parse("none", LampPost.DEFAULT));
		assertEquals(LampPost.FENCE_TORCH, LampPost.parse("lighthouse", LampPost.DEFAULT));
		assertEquals(LampPost.FENCE_TORCH, LampPost.parse(null, LampPost.DEFAULT));
	}

	@Test
	void plainsPostHasAWallTorchOnEverySideOfTheWood() {
		List<Piece> post = LampPost.PLAINS.pieces(CardinalDirection.NORTH);

		List<Piece> torches = post.stream().filter(piece -> piece.part() == Part.WALL_TORCH).toList();
		assertEquals(4, torches.size());
		for (Piece torch : torches) {
			assertEquals(2, torch.y());
			assertEquals(1, Math.abs(torch.x()) + Math.abs(torch.z()));
		}
		assertTrue(post.contains(new Piece(0, 2, 0, Part.STRIPPED_OAK_WOOD)));
	}

	@Test
	void snowyArmAndPostConnectAndTheLanternHangsUnderTheArm() {
		List<Piece> post = LampPost.SNOWY.pieces(CardinalDirection.WEST);

		Piece arm = new Piece(-1, 2, 0, Part.FENCE);
		assertTrue(post.contains(new Piece(0, 2, 0, Part.FENCE)));
		assertTrue(post.contains(arm));
		assertTrue(post.contains(new Piece(-1, 1, 0, Part.HANGING_LANTERN)));
		assertFalse(post.contains(new Piece(-1, 0, 0, Part.FENCE)));
		assertTrue(post.indexOf(arm) < post.indexOf(new Piece(-1, 1, 0, Part.HANGING_LANTERN)));
	}

	@Test
	void candidatesAreTheFourNeighborsNeverTheVillagersColumn() {
		List<CardinalDirection> sides = LampPost.candidateSides(6);

		assertEquals(4, sides.size());
		assertEquals(CardinalDirection.WEST, sides.getFirst());
		assertTrue(sides.containsAll(List.of(CardinalDirection.values())));
	}

	@Test
	void armPointsAwayFromTheRoadAndNeverOverItOrTheVillager() {
		List<CardinalDirection> arms = LampPost.armDirections(List.of(CardinalDirection.NORTH), CardinalDirection.EAST);
		assertEquals(List.of(CardinalDirection.SOUTH, CardinalDirection.WEST), arms);

		// The villager stands on the far side from the road: the arm goes along the road instead.
		assertEquals(List.of(CardinalDirection.WEST, CardinalDirection.EAST),
				LampPost.armDirections(List.of(CardinalDirection.NORTH), CardinalDirection.SOUTH));
		assertFalse(LampPost.armDirections(List.of(CardinalDirection.NORTH, CardinalDirection.SOUTH),
				CardinalDirection.EAST).contains(CardinalDirection.NORTH));
	}
}
