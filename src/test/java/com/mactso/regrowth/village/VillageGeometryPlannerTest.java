package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mactso.regrowth.village.VillagePlan.BoundaryRole;

class VillageGeometryPlannerTest {

	@Test
	void legacyBoundaryRemainsAConnectedSquare() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 2, 6, 6000);

		VillagePlan plan = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		assertEquals(64, plan.boundary().size());
		assertTrue(plan.contains(new GridPoint(0, 0)));
		assertTrue(!plan.contains(new GridPoint(20, 20)));
		assertConnectedLoop(plan);
	}

	@Test
	void adaptiveBoundaryExpandsAroundOutlyingVillagePois() {
		VillagePlan plan = VillageGeometryPlanner.create(
				new GridPoint(0, 0),
				List.of(new GridPoint(30, 0), new GridPoint(0, 24)),
				VillageBoundarySettings.DEFAULT);

		int maximumX = plan.boundary().stream().mapToInt(node -> node.position().x()).max().orElseThrow();
		int maximumZ = plan.boundary().stream().mapToInt(node -> node.position().z()).max().orElseThrow();
		assertEquals(38, maximumX);
		assertEquals(32, maximumZ);
		assertEquals(2, plan.pointOfInterestCount());
		assertConnectedLoop(plan);
	}

	@Test
	void adaptiveBoundaryIgnoresPoisOutsideTheScanRadius() {
		VillagePlan plan = VillageGeometryPlanner.create(
				new GridPoint(0, 0),
				List.of(new GridPoint(100, 0)),
				VillageBoundarySettings.DEFAULT);

		int maximumX = plan.boundary().stream().mapToInt(node -> node.position().x()).max().orElseThrow();
		assertEquals(18, maximumX);
		assertEquals(0, plan.pointOfInterestCount());
	}

	@Test
	void roadCrossingBecomesOneGateWithSafeOpenings() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 6, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls,
				node -> node.position().z() == -8 && Math.abs(node.position().x()) <= 1);

		assertEquals(1, gated.gateCount());
		assertEquals(4, gated.openingCount());
		assertEquals(BoundaryRole.GATE, gated.boundary().stream()
				.filter(node -> node.role() == BoundaryRole.GATE)
				.findFirst().orElseThrow().role());
	}

	@Test
	void boundaryThatIsAllRoadIsLeftOpen() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 6, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls, node -> true);

		assertEquals(walls.boundary().size(), gated.openingCount());
	}

	@Test
	void boundaryWithinIsNearestFirstAndNeighborsWrapAround() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 8, 1, 6000);
		VillagePlan plan = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		var nearby = plan.boundaryWithin(new GridPoint(0, -10), 3);

		assertEquals(new GridPoint(0, -8), nearby.getFirst().node().position());
		assertTrue(nearby.stream().allMatch(node -> node.node().position().distanceSquared(new GridPoint(0, -10)) <= 9));
		assertEquals(plan.boundary().getLast(), plan.neighbor(0, -1).node());
		assertEquals(0, plan.neighbor(plan.boundary().size() - 1, 1).index());
	}

	@Test
	void gatesOnlyOpenWhereTheNodesOwnGroundIsRoadByDefault() {
		assertEquals(0, VillageBoundarySettings.DEFAULT.gateScanDistance());
		assertEquals(0, new VillageBoundarySettings(VillageBoundaryMode.ADAPTIVE, 8, 64, 18, 64, 23, -2, 6000)
				.gateScanDistance());
	}

	@Test
	void everyDiagonalPathOutOfTheVillageCrossesABoundaryNode() {
		VillagePlan plan = VillageGeometryPlanner.create(
				new GridPoint(0, 0),
				List.of(new GridPoint(30, 7), new GridPoint(-11, 24), new GridPoint(5, -33)),
				VillageBoundarySettings.DEFAULT);
		java.util.Set<GridPoint> nodes = new java.util.HashSet<>();
		plan.boundary().forEach(node -> nodes.add(node.position()));

		for (int[] step : new int[][] { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } }) {
			for (int start = -3; start <= 3; start++) {
				boolean crossed = false;
				for (int distance = 0; distance < 120 && !crossed; distance++) {
					crossed = nodes.contains(new GridPoint(start + step[0] * distance, step[1] * distance));
				}
				assertTrue(crossed, "a diagonal path from x=" + start + " slipped through the boundary");
			}
		}
	}

	@Test
	void openingWidthsCountRunsOfGatesAndOpenings() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 0, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls,
				node -> node.position().z() == -8 && Math.abs(node.position().x()) <= 1
						|| node.position().x() == 8 && node.position().z() == 0);

		assertEquals(List.of(5, 3), gated.openingWidths());
		assertEquals(List.of(), walls.openingWidths());
	}

	private static void assertConnectedLoop(VillagePlan plan) {
		for (int index = 0; index < plan.boundary().size(); index++) {
			GridPoint current = plan.boundary().get(index).position();
			GridPoint next = plan.boundary().get((index + 1) % plan.boundary().size()).position();
			int manhattanDistance = Math.abs(current.x() - next.x()) + Math.abs(current.z() - next.z());
			assertTrue(manhattanDistance == 1,
					() -> "Boundary gap between " + current + " and " + next);
		}
	}
}
