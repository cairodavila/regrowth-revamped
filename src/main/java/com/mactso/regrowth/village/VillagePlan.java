package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record VillagePlan(
		GridPoint meetingPoint,
		VillageBoundaryMode mode,
		List<BoundaryNode> boundary,
		int pointOfInterestCount) {

	public VillagePlan {
		boundary = List.copyOf(boundary);
	}

	public int gateCount() {
		return (int) boundary.stream().filter(node -> node.role() == BoundaryRole.GATE).count();
	}

	public int openingCount() {
		return (int) boundary.stream().filter(node -> node.role() == BoundaryRole.OPENING).count();
	}

	/** Widths of the gaps in the wall, each a run of gate and opening nodes, widest first. */
	public List<Integer> openingWidths() {
		int firstWall = -1;
		for (int index = 0; index < boundary.size() && firstWall < 0; index++) {
			if (boundary.get(index).role() == BoundaryRole.WALL) {
				firstWall = index;
			}
		}
		if (firstWall < 0) {
			return boundary.isEmpty() ? List.of() : List.of(boundary.size());
		}
		List<Integer> widths = new ArrayList<>();
		int width = 0;
		for (int offset = 1; offset <= boundary.size(); offset++) {
			if (boundary.get((firstWall + offset) % boundary.size()).role() != BoundaryRole.WALL) {
				width++;
			} else if (width > 0) {
				widths.add(width);
				width = 0;
			}
		}
		widths.sort(Comparator.reverseOrder());
		return widths;
	}

	public boolean contains(GridPoint position) {
		boolean inside = false;
		for (int index = 0, previous = boundary.size() - 1; index < boundary.size(); previous = index++) {
			GridPoint currentPoint = boundary.get(index).position();
			GridPoint previousPoint = boundary.get(previous).position();
			if (currentPoint.equals(position)) {
				return true;
			}
			boolean crossesZ = (currentPoint.z() > position.z()) != (previousPoint.z() > position.z());
			if (crossesZ) {
				double crossingX = (double) (previousPoint.x() - currentPoint.x())
						* (position.z() - currentPoint.z())
						/ (previousPoint.z() - currentPoint.z()) + currentPoint.x();
				if (position.x() < crossingX) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	/** Boundary nodes within {@code maximumDistance} of {@code position}, nearest first. */
	public List<IndexedBoundaryNode> boundaryWithin(GridPoint position, int maximumDistance) {
		long maximumDistanceSquared = (long) maximumDistance * maximumDistance;
		List<IndexedBoundaryNode> nearby = new ArrayList<>();
		for (int index = 0; index < boundary.size(); index++) {
			if (boundary.get(index).position().distanceSquared(position) <= maximumDistanceSquared) {
				nearby.add(new IndexedBoundaryNode(index, boundary.get(index)));
			}
		}
		nearby.sort(Comparator.comparingLong(node -> node.node().position().distanceSquared(position)));
		return nearby;
	}

	public IndexedBoundaryNode neighbor(int index, int offset) {
		int neighborIndex = Math.floorMod(index + offset, boundary.size());
		return new IndexedBoundaryNode(neighborIndex, boundary.get(neighborIndex));
	}

	public VillagePlan withBoundaryRoles(List<BoundaryRole> roles) {
		if (roles.size() != boundary.size()) {
			throw new IllegalArgumentException("A boundary role is required for every boundary node");
		}
		List<BoundaryNode> updatedBoundary = new ArrayList<>(boundary.size());
		for (int index = 0; index < boundary.size(); index++) {
			BoundaryNode node = boundary.get(index);
			updatedBoundary.add(new BoundaryNode(node.position(), node.outward(), roles.get(index)));
		}
		return new VillagePlan(meetingPoint, mode, updatedBoundary, pointOfInterestCount);
	}

	public record BoundaryNode(GridPoint position, CardinalDirection outward, BoundaryRole role) {
	}

	public record IndexedBoundaryNode(int index, BoundaryNode node) {
	}

	public enum BoundaryRole {
		WALL,
		GATE,
		OPENING
	}

	public enum CardinalDirection {
		NORTH(0, -1),
		SOUTH(0, 1),
		WEST(-1, 0),
		EAST(1, 0);

		private final int stepX;
		private final int stepZ;

		CardinalDirection(int stepX, int stepZ) {
			this.stepX = stepX;
			this.stepZ = stepZ;
		}

		public int stepX() {
			return stepX;
		}

		public int stepZ() {
			return stepZ;
		}
	}
}
