package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoadHoleTest {

	@Test
	void breakOfUpToThreeBlocksBetweenRoadsIsAHole() {
		assertTrue(RoadHole.spans(0, 0));
		assertTrue(RoadHole.spans(1, 0));
		assertTrue(RoadHole.spans(1, 1));
		assertTrue(RoadHole.spans(0, 2));
	}

	@Test
	void longerBreakIsLeftAlone() {
		assertFalse(RoadHole.spans(2, 1));
		assertFalse(RoadHole.spans(0, 3));
	}

	@Test
	void groundWithRoadOnOneSideOnlyIsNotAHole() {
		assertFalse(RoadHole.spans(-1, 0));
		assertFalse(RoadHole.spans(0, -1));
	}
}
