package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class VillageBellsTest {

	@Test
	void bellsWithinTheVillageDistanceChainIntoOneVillage() {
		List<GridPoint> world = List.of(
				new GridPoint(2, -4), new GridPoint(-28, 8), new GridPoint(-80, 30),
				new GridPoint(5, -167), new GridPoint(5, -175));

		List<GridPoint> spawn = VillageBells.village(new GridPoint(2, -4), bell -> world);
		List<GridPoint> north = VillageBells.village(new GridPoint(5, -167), bell -> world);

		assertEquals(List.of(new GridPoint(-80, 30), new GridPoint(-28, 8), new GridPoint(2, -4)), spawn);
		assertEquals(List.of(new GridPoint(5, -175), new GridPoint(5, -167)), north);
	}

	@Test
	void aRemovedBellLeadsToTheVillageAroundIt() {
		List<GridPoint> world = List.of(new GridPoint(5, -175));

		assertEquals(world, VillageBells.village(new GridPoint(5, -167), bell -> world));
		assertEquals(List.of(), VillageBells.village(new GridPoint(500, 500), bell -> List.of()));
	}

	@Test
	void oneBellIsItsOwnRepresentativeWhereverThePoisAre() {
		GridPoint bell = new GridPoint(-28, 8);

		assertEquals(bell, VillageBells.representative(List.of(bell), List.of(new GridPoint(500, 500))));
	}

	@Test
	void theBellNearestTheMiddleOfTheVillageRepresentsIt() {
		GridPoint central = new GridPoint(2, -4);
		GridPoint west = new GridPoint(-28, 8);
		List<GridPoint> pointsOfInterest = List.of(
				new GridPoint(-30, 10), new GridPoint(0, 0), new GridPoint(20, -10), new GridPoint(47, 5));

		assertEquals(central, VillageBells.representative(List.of(west, central), pointsOfInterest));
		assertEquals(central, VillageBells.representative(List.of(central, west), pointsOfInterest));
	}

	@Test
	void aTieGoesToTheSmallestCoordinatesInAnyOrder() {
		GridPoint first = new GridPoint(-10, 0);
		GridPoint second = new GridPoint(10, 0);
		List<GridPoint> pointsOfInterest = List.of(new GridPoint(0, 5), new GridPoint(0, -5));

		assertEquals(first, VillageBells.representative(List.of(second, first), pointsOfInterest));
		assertEquals(first, VillageBells.representative(List.of(first, second), pointsOfInterest));
	}
}
