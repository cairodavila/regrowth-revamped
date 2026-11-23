package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.mactso.regrowth.village.VillagePlan.BoundaryNode;
import com.mactso.regrowth.village.VillagePlan.BoundaryRole;
import com.mactso.regrowth.village.VillagePlan.CardinalDirection;

public final class VillageGeometryPlanner {

	/** A crossing wider than this is left open; a longer row of gates is a road running along the boundary. */
	private static final int MAXIMUM_GATE_WIDTH = 5;
	/** Road nodes this few non-road nodes apart count as one crossing. */
	private static final int MAXIMUM_ROAD_GAP = 2;
	/** Wall nodes kept in line on each side of a crossing, preferred first. */
	private static final int[] CROSSING_FLANKS = { 2, 1, 3 };

	private VillageGeometryPlanner() {
	}

	public static VillagePlan create(
			GridPoint meetingPoint,
			Collection<GridPoint> pointsOfInterest,
			VillageBoundarySettings settings) {
		List<GridPoint> relevantPoints = pointsOfInterest.stream()
				.filter(point -> point.distanceSquared(meetingPoint)
						<= (long) settings.scanRadius() * settings.scanRadius())
				.toList();

		List<GridPoint> polygon = settings.mode() == VillageBoundaryMode.LEGACY
				? legacySquare(meetingPoint, settings.legacyRadius())
				: adaptiveHull(meetingPoint, relevantPoints, settings);
		List<BoundaryNode> boundary = rasterizePolygon(polygon);
		return new VillagePlan(meetingPoint, settings.mode(), boundary, relevantPoints.size());
	}

	/**
	 * Turns every road crossing into gates. Around a crossing the boundary is redrawn as two straight legs, so the
	 * gates span the road in one line with wall on both sides, even where the boundary runs diagonally. The corner
	 * goes on the outside, so the redrawn stretch never cuts into the village. A crossing that can't be drawn that
	 * way (a road running along the boundary, a plaza) is left open without gates.
	 *
	 * @param isBuilt whether a node already holds a wall or gate; a crossing keeps the line that is already built
	 *                when the road's edge shifts by a block between plans
	 */
	public static VillagePlan markRoadGates(
			VillagePlan plan,
			Predicate<BoundaryNode> crossesRoad,
			Predicate<BoundaryNode> isBuilt) {
		List<BoundaryNode> nodes = new ArrayList<>(plan.boundary());
		int boundarySize = nodes.size();
		boolean[] onRoad = new boolean[boundarySize];
		for (int index = 0; index < boundarySize; index++) {
			onRoad[index] = crossesRoad.test(nodes.get(index));
		}
		// Village paths have holes; a short gap between road nodes is still the same road.
		boolean[] road = onRoad.clone();
		for (int index = 0; index < boundarySize; index++) {
			for (int reach = 2; onRoad[index] && reach <= MAXIMUM_ROAD_GAP + 1; reach++) {
				if (onRoad[(index + reach) % boundarySize]) {
					for (int gap = 1; gap < reach; gap++) {
						road[(index + gap) % boundarySize] = true;
					}
				}
			}
		}
		int firstNonRoad = 0;
		while (firstNonRoad < boundarySize && road[firstNonRoad]) {
			firstNonRoad++;
		}
		if (firstNonRoad == boundarySize) {
			nodes.replaceAll(node -> withRole(node, BoundaryRole.OPENING));
			return new VillagePlan(plan.meetingPoint(), plan.mode(), nodes, plan.pointOfInterestCount());
		}

		// Road nodes, plus every node an earlier crossing already redrew.
		boolean[] taken = road.clone();
		int runLength = 0;
		for (int offset = 1; offset <= boundarySize; offset++) {
			int index = Math.floorMod(firstNonRoad + offset, boundarySize);
			if (road[index]) {
				runLength++;
			} else if (runLength > 0) {
				crossRoad(nodes, taken, index - runLength, runLength, crossesRoad, isBuilt);
				runLength = 0;
			}
		}
		return new VillagePlan(plan.meetingPoint(), plan.mode(), nodes, plan.pointOfInterestCount());
	}

	private static void crossRoad(
			List<BoundaryNode> nodes,
			boolean[] taken,
			int firstRoad,
			int roadLength,
			Predicate<BoundaryNode> crossesRoad,
			Predicate<BoundaryNode> isBuilt) {
		int boundarySize = nodes.size();
		List<BoundaryNode> best = List.of();
		int bestStart = 0;
		int bestBuilt = -1;
		for (int before : CROSSING_FLANKS) {
			for (int after : CROSSING_FLANKS) {
				int start = firstRoad - before;
				int length = before + roadLength + after;
				if (length >= boundarySize || !isFree(taken, start, before)
						|| !isFree(taken, firstRoad + roadLength, after)) {
					continue;
				}
				List<BoundaryNode> crossing = straightCrossing(
						nodes.get(Math.floorMod(start, boundarySize)),
						nodes.get(Math.floorMod(start + length - 1, boundarySize)),
						length,
						crossesRoad);
				// Only blocks off the old line count: those can only be there because this line was picked before.
				int built = 0;
				for (int offset = 0; offset < crossing.size(); offset++) {
					BoundaryNode node = crossing.get(offset);
					if (!node.position().equals(nodes.get(Math.floorMod(start + offset, boundarySize)).position())
							&& isBuilt.test(node)) {
						built++;
					}
				}
				if (!crossing.isEmpty() && built > bestBuilt) {
					best = crossing;
					bestStart = start;
					bestBuilt = built;
				}
			}
		}
		for (int offset = 0; offset < best.size(); offset++) {
			int index = Math.floorMod(bestStart + offset, boundarySize);
			nodes.set(index, best.get(offset));
			taken[index] = true;
		}
		if (best.isEmpty()) {
			for (int offset = 0; offset < roadLength; offset++) {
				int index = Math.floorMod(firstRoad + offset, boundarySize);
				nodes.set(index, withRole(nodes.get(index), BoundaryRole.OPENING));
			}
		}
	}

	private static boolean isFree(boolean[] taken, int start, int length) {
		for (int offset = 0; offset < length; offset++) {
			if (taken[Math.floorMod(start + offset, taken.length)]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The nodes from {@code start} to {@code end} as two straight legs with gates on the road, or nothing when the
	 * road doesn't cross one leg cleanly. It has as many nodes as the stretch it replaces, so the nodes after it
	 * keep their index and the wall torches their spacing.
	 */
	private static List<BoundaryNode> straightCrossing(
			BoundaryNode first,
			BoundaryNode last,
			int length,
			Predicate<BoundaryNode> crossesRoad) {
		GridPoint start = first.position();
		GridPoint end = last.position();
		int deltaX = end.x() - start.x();
		int deltaZ = end.z() - start.z();
		if (Math.abs(deltaX) + Math.abs(deltaZ) != length - 1) {
			// The stretch doubles back around a sharp corner of the boundary.
			return List.of();
		}
		// The boundary's outward side is its travel direction turned by (z, -x); see outwardDirection.
		GridPoint corner = (long) deltaX * deltaZ > 0
				? new GridPoint(end.x(), start.z())
				: new GridPoint(start.x(), end.z());
		List<GridPoint> line = new ArrayList<>(orthogonalRasterLine(start, corner));
		List<GridPoint> secondLeg = orthogonalRasterLine(corner, end);
		line.addAll(secondLeg.subList(1, secondLeg.size()));

		List<BoundaryNode> crossing = new ArrayList<>(length);
		int firstRoad = -1;
		int lastRoad = -1;
		crossing.add(first);
		for (int index = 1; index < length - 1; index++) {
			BoundaryNode node = new BoundaryNode(
					line.get(index), outwardDirection(line.get(index), line.get(index + 1)), BoundaryRole.WALL);
			if (crossesRoad.test(node)) {
				firstRoad = firstRoad < 0 ? index : firstRoad;
				lastRoad = index;
			}
			crossing.add(node);
		}
		crossing.add(last);
		// The gates need wall in line at both ends, so they can't reach the corner. The two end nodes are off the
		// road already.
		int cornerIndex = Math.abs(corner.x() - start.x()) + Math.abs(corner.z() - start.z());
		if (firstRoad < 0 || lastRoad - firstRoad >= MAXIMUM_GATE_WIDTH
				|| firstRoad <= cornerIndex && cornerIndex <= lastRoad) {
			return List.of();
		}
		// Holes in the road between its first and last block get a gate too.
		for (int index = firstRoad; index <= lastRoad; index++) {
			crossing.set(index, withRole(crossing.get(index), BoundaryRole.GATE));
		}
		return crossing;
	}

	private static BoundaryNode withRole(BoundaryNode node, BoundaryRole role) {
		return new BoundaryNode(node.position(), node.outward(), role);
	}

	static List<GridPoint> adaptiveHull(
			GridPoint meetingPoint,
			Collection<GridPoint> pointsOfInterest,
			VillageBoundarySettings settings) {
		Set<GridPoint> expandedPoints = new LinkedHashSet<>();
		addSquare(expandedPoints, meetingPoint, settings.minimumRadius(), meetingPoint, settings.maximumRadius());
		for (GridPoint point : pointsOfInterest) {
			addSquare(expandedPoints, point, settings.margin(), meetingPoint, settings.maximumRadius());
		}
		return convexHull(expandedPoints);
	}

	static List<GridPoint> convexHull(Collection<GridPoint> points) {
		List<GridPoint> sortedPoints = points.stream().distinct().sorted().toList();
		if (sortedPoints.size() <= 2) {
			return sortedPoints;
		}

		List<GridPoint> lower = new ArrayList<>();
		for (GridPoint point : sortedPoints) {
			while (lower.size() >= 2 && cross(
					lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
				lower.remove(lower.size() - 1);
			}
			lower.add(point);
		}

		List<GridPoint> upper = new ArrayList<>();
		for (int index = sortedPoints.size() - 1; index >= 0; index--) {
			GridPoint point = sortedPoints.get(index);
			while (upper.size() >= 2 && cross(
					upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
				upper.remove(upper.size() - 1);
			}
			upper.add(point);
		}

		lower.remove(lower.size() - 1);
		upper.remove(upper.size() - 1);
		lower.addAll(upper);
		return lower;
	}

	static List<BoundaryNode> rasterizePolygon(List<GridPoint> polygon) {
		if (polygon.size() < 2) {
			return List.of();
		}
		List<BoundaryNode> boundary = new ArrayList<>();
		for (int index = 0; index < polygon.size(); index++) {
			GridPoint start = polygon.get(index);
			GridPoint end = polygon.get((index + 1) % polygon.size());
			CardinalDirection outward = outwardDirection(start, end);
			List<GridPoint> edge = orthogonalRasterLine(start, end);
			for (int pointIndex = 0; pointIndex < edge.size() - 1; pointIndex++) {
				GridPoint point = edge.get(pointIndex);
				if (boundary.isEmpty() || !boundary.getLast().position().equals(point)) {
					boundary.add(new BoundaryNode(point, outward, BoundaryRole.WALL));
				}
			}
		}
		return List.copyOf(boundary);
	}

	private static void addSquare(
			Set<GridPoint> points,
			GridPoint squareCenter,
			int radius,
			GridPoint villageCenter,
			int maximumRadius) {
		points.add(new GridPoint(squareCenter.x() - radius, squareCenter.z() - radius)
				.clampAround(villageCenter, maximumRadius));
		points.add(new GridPoint(squareCenter.x() + radius, squareCenter.z() - radius)
				.clampAround(villageCenter, maximumRadius));
		points.add(new GridPoint(squareCenter.x() + radius, squareCenter.z() + radius)
				.clampAround(villageCenter, maximumRadius));
		points.add(new GridPoint(squareCenter.x() - radius, squareCenter.z() + radius)
				.clampAround(villageCenter, maximumRadius));
	}

	private static List<GridPoint> legacySquare(GridPoint meetingPoint, int radius) {
		return List.of(
				new GridPoint(meetingPoint.x() - radius, meetingPoint.z() - radius),
				new GridPoint(meetingPoint.x() + radius, meetingPoint.z() - radius),
				new GridPoint(meetingPoint.x() + radius, meetingPoint.z() + radius),
				new GridPoint(meetingPoint.x() - radius, meetingPoint.z() + radius));
	}

	private static List<GridPoint> orthogonalRasterLine(GridPoint start, GridPoint end) {
		List<GridPoint> diagonalLine = bresenhamLine(start, end);
		List<GridPoint> orthogonalLine = new ArrayList<>();
		orthogonalLine.add(start);
		for (int index = 1; index < diagonalLine.size(); index++) {
			GridPoint previous = orthogonalLine.getLast();
			GridPoint next = diagonalLine.get(index);
			if (previous.x() != next.x() && previous.z() != next.z()) {
				orthogonalLine.add(new GridPoint(next.x(), previous.z()));
			}
			if (!orthogonalLine.getLast().equals(next)) {
				orthogonalLine.add(next);
			}
		}
		return orthogonalLine;
	}

	private static List<GridPoint> bresenhamLine(GridPoint start, GridPoint end) {
		List<GridPoint> line = new ArrayList<>();
		int x = start.x();
		int z = start.z();
		int deltaX = Math.abs(end.x() - x);
		int stepX = Integer.compare(end.x(), x);
		int deltaZ = -Math.abs(end.z() - z);
		int stepZ = Integer.compare(end.z(), z);
		int error = deltaX + deltaZ;

		while (true) {
			line.add(new GridPoint(x, z));
			if (x == end.x() && z == end.z()) {
				return line;
			}
			int doubleError = error * 2;
			if (doubleError >= deltaZ) {
				error += deltaZ;
				x += stepX;
			}
			if (doubleError <= deltaX) {
				error += deltaX;
				z += stepZ;
			}
		}
	}

	private static CardinalDirection outwardDirection(GridPoint start, GridPoint end) {
		int normalX = end.z() - start.z();
		int normalZ = start.x() - end.x();
		if (Math.abs(normalX) >= Math.abs(normalZ)) {
			return normalX >= 0 ? CardinalDirection.EAST : CardinalDirection.WEST;
		}
		return normalZ >= 0 ? CardinalDirection.SOUTH : CardinalDirection.NORTH;
	}

	private static long cross(GridPoint origin, GridPoint a, GridPoint b) {
		return ((long) a.x() - origin.x()) * ((long) b.z() - origin.z())
				- ((long) a.z() - origin.z()) * ((long) b.x() - origin.x());
	}
}
