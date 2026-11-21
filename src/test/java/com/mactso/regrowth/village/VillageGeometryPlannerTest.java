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
	void straightRoadCrossingIsGatedAcrossItsWholeWidth() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 6, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls,
				node -> node.position().z() == -8 && Math.abs(node.position().x()) <= 1, node -> false);

		assertEquals(List.of(new GridPoint(-1, -8), new GridPoint(0, -8), new GridPoint(1, -8)), gates(gated));
		assertEquals(0, gated.openingCount());
		assertEquals(positions(walls), positions(gated));
	}

	@Test
	void diagonalRoadCrossingIsRedrawnAsAStraightLineOfGates() {
		List<GridPoint> pois = List.of(new GridPoint(30, 7), new GridPoint(-11, 24), new GridPoint(5, -33));
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), pois, VillageBoundarySettings.DEFAULT);
		// A road three wide running north out of the village, through a diagonal stretch of the boundary.
		java.util.function.Predicate<VillagePlan.BoundaryNode> road =
				node -> node.position().z() < 0 && node.position().x() >= 24 && node.position().x() <= 26;
		assertTrue(walls.boundary().stream().filter(road).map(node -> node.position().z()).distinct().count() > 1,
				"the road should cross a diagonal stretch");

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls, road, node -> false);

		List<GridPoint> gates = gates(gated);
		assertEquals(3, gates.size());
		assertEquals(1, gates.stream().map(GridPoint::z).distinct().count(), "gates in one row: " + gates);
		assertEquals(0, gated.openingCount());
		// Wall in the same row on both sides of the gates.
		int z = gates.getFirst().z();
		assertEquals(BoundaryRole.WALL, roleAt(gated, new GridPoint(23, z)));
		assertEquals(BoundaryRole.WALL, roleAt(gated, new GridPoint(27, z)));
		// The gates face along the road.
		assertTrue(gated.boundary().stream().filter(node -> node.role() == BoundaryRole.GATE)
				.allMatch(node -> node.outward() == VillagePlan.CardinalDirection.NORTH));
		assertEquals(walls.boundary().size(), gated.boundary().size());
		assertEquals(gated.boundary().size(), positions(gated).stream().distinct().count());
		assertConnectedLoop(gated);
		assertTrue(gated.boundary().stream().filter(node -> node.role() == BoundaryRole.WALL).noneMatch(road));
		// The redrawn stretch bulges outward, so nothing that was inside ends up outside.
		assertTrue(walls.boundary().stream().allMatch(node -> gated.contains(node.position())));
	}

	@Test
	void redrawnCrossingStaysOnTheLineThatIsAlreadyBuilt() {
		List<GridPoint> pois = List.of(new GridPoint(30, 7), new GridPoint(-11, 24), new GridPoint(5, -33));
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), pois, VillageBoundarySettings.DEFAULT);
		java.util.function.Predicate<VillagePlan.BoundaryNode> road =
				node -> node.position().z() < 0 && node.position().x() >= 24 && node.position().x() <= 26;
		int preferredRow = gates(VillageGeometryPlanner.markRoadGates(walls, road, node -> false)).getFirst().z();

		// Gates already stand one row away from the one the planner would pick on its own.
		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls, road,
				node -> node.position().z() == preferredRow + 1 && road.test(node));

		assertEquals(preferredRow + 1, gates(gated).getFirst().z());
		assertEquals(3, gated.gateCount());
		assertConnectedLoop(gated);
	}

	@Test
	void wallBuiltOnTheOldLineDoesNotMoveARedrawnCrossing() {
		List<GridPoint> pois = List.of(new GridPoint(30, 7), new GridPoint(-11, 24), new GridPoint(5, -33));
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), pois, VillageBoundarySettings.DEFAULT);
		java.util.function.Predicate<VillagePlan.BoundaryNode> road =
				node -> node.position().z() < 0 && node.position().x() >= 24 && node.position().x() <= 26;
		VillagePlan first = VillageGeometryPlanner.markRoadGates(walls, road, node -> false);
		java.util.Set<GridPoint> oldLine = new java.util.HashSet<>(positions(walls));

		// The wall is finished everywhere except on the redrawn stretch.
		VillagePlan second = VillageGeometryPlanner.markRoadGates(walls, road,
				node -> oldLine.contains(node.position()) && !road.test(node));

		assertEquals(first.boundary(), second.boundary());
	}

	@Test
	void holeInARoadIsGatedWithTheRestOfTheCrossing() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 6, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls,
				node -> node.position().z() == -8 && Math.abs(node.position().x()) == 1, node -> false);

		assertEquals(List.of(new GridPoint(-1, -8), new GridPoint(0, -8), new GridPoint(1, -8)), gates(gated));
		assertEquals(0, gated.openingCount());
	}

	@Test
	void roadRunningAlongTheBoundaryIsLeftOpenWithoutGates() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 8, 0, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls,
				node -> node.position().z() == -8 && Math.abs(node.position().x()) <= 3, node -> false);

		assertEquals(0, gated.gateCount());
		assertEquals(List.of(7), gated.openingWidths());
		assertEquals(positions(walls), positions(gated));
	}

	@Test
	void boundaryThatIsAllRoadIsLeftOpen() {
		VillageBoundarySettings settings = new VillageBoundarySettings(
				VillageBoundaryMode.LEGACY, 8, 64, 18, 64, 4, 6, 6000);
		VillagePlan walls = VillageGeometryPlanner.create(new GridPoint(0, 0), List.of(), settings);

		VillagePlan gated = VillageGeometryPlanner.markRoadGates(walls, node -> true, node -> false);

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
						|| node.position().x() == 8 && node.position().z() == 0, node -> false);

		assertEquals(List.of(3, 1), gated.openingWidths());
		assertEquals(List.of(), walls.openingWidths());
	}

	private static List<GridPoint> positions(VillagePlan plan) {
		return plan.boundary().stream().map(VillagePlan.BoundaryNode::position).toList();
	}

	private static List<GridPoint> gates(VillagePlan plan) {
		return plan.boundary().stream()
				.filter(node -> node.role() == BoundaryRole.GATE)
				.map(VillagePlan.BoundaryNode::position)
				.sorted()
				.toList();
	}

	private static BoundaryRole roleAt(VillagePlan plan, GridPoint position) {
		return plan.boundary().stream()
				.filter(node -> node.position().equals(position))
				.findFirst().orElseThrow().role();
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
