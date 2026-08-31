package com.mactso.regrowth.village;

public record GridPoint(int x, int z) implements Comparable<GridPoint> {

	public long distanceSquared(GridPoint other) {
		long deltaX = (long) x - other.x;
		long deltaZ = (long) z - other.z;
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	public GridPoint clampAround(GridPoint center, int radius) {
		return new GridPoint(
				Math.clamp(x, center.x - radius, center.x + radius),
				Math.clamp(z, center.z - radius, center.z + radius));
	}

	@Override
	public int compareTo(GridPoint other) {
		int xComparison = Integer.compare(x, other.x);
		return xComparison != 0 ? xComparison : Integer.compare(z, other.z);
	}
}
