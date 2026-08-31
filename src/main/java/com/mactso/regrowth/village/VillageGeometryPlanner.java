package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.mactso.regrowth.village.VillagePlan.BoundaryNode;
import com.mactso.regrowth.village.VillagePlan.BoundaryRole;
import com.mactso.regrowth.village.VillagePlan.CardinalDirection;

public final class VillageGeometryPlanner {

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

	public static VillagePlan markRoadGates(VillagePlan plan, Predicate<BoundaryNode> crossesRoad) {
		int boundarySize = plan.boundary().size();
		if (boundarySize == 0) {
			return plan;
		}

		boolean[] roadCandidates = new boolean[boundarySize];
		int firstNonRoad = -1;
		for (int index = 0; index < boundarySize; index++) {
			roadCandidates[index] = crossesRoad.test(plan.boundary().get(index));
			if (!roadCandidates[index] && firstNonRoad < 0) {
				firstNonRoad = index;
			}
		}
		if (firstNonRoad < 0) {
			return plan;
		}

		List<BoundaryRole> roles = new ArrayList<>(Collections.nCopies(boundarySize, BoundaryRole.WALL));
		List<Integer> roadRun = new ArrayList<>();
		for (int offset = 1; offset <= boundarySize; offset++) {
			int index = Math.floorMod(firstNonRoad + offset, boundarySize);
			if (roadCandidates[index]) {
				roadRun.add(index);
			} else if (!roadRun.isEmpty()) {
				markGateRun(roles, roadRun);
				roadRun.clear();
			}
		}
		return plan.withBoundaryRoles(roles);
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

	private static void markGateRun(List<BoundaryRole> roles, List<Integer> roadRun) {
		int boundarySize = roles.size();
		for (int index : roadRun) {
			roles.set(index, BoundaryRole.OPENING);
		}
		int centerIndex = roadRun.get(roadRun.size() / 2);
		roles.set(centerIndex, BoundaryRole.GATE);
		roles.set(Math.floorMod(roadRun.getFirst() - 1, boundarySize), BoundaryRole.OPENING);
		roles.set(Math.floorMod(roadRun.getLast() + 1, boundarySize), BoundaryRole.OPENING);
	}

	private static long cross(GridPoint origin, GridPoint a, GridPoint b) {
		return ((long) a.x() - origin.x()) * ((long) b.z() - origin.z())
				- ((long) a.z() - origin.z()) * ((long) b.x() - origin.x());
	}
}
