package com.mactso.regrowth.village;

/** Decides whether natural ground in line with a road is a break in that road. */
public final class RoadHole {

	/** A longer break is left alone: it may be two roads that were never joined. */
	public static final int MAXIMUM_LENGTH = 3;

	private RoadHole() {
	}

	/**
	 * Whether a block lies in a break of at most {@link #MAXIMUM_LENGTH} blocks with road at both ends.
	 *
	 * @param fillableBefore blocks of fillable ground between this block and the road one way along the line, or
	 *        negative when there's no road that way
	 * @param fillableAfter the same, the other way
	 */
	public static boolean spans(int fillableBefore, int fillableAfter) {
		return fillableBefore >= 0 && fillableAfter >= 0 && fillableBefore + 1 + fillableAfter <= MAXIMUM_LENGTH;
	}
}
