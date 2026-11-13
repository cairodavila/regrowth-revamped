package com.mactso.regrowth.village;

public record VillageBoundarySettings(
		VillageBoundaryMode mode,
		int margin,
		int scanRadius,
		int minimumRadius,
		int maximumRadius,
		int legacyRadius,
		int gateScanDistance,
		long cacheTicks) {

	public static final VillageBoundarySettings DEFAULT = new VillageBoundarySettings(
			VillageBoundaryMode.ADAPTIVE, 8, 64, 18, 64, 23, 0, 6000);

	public VillageBoundarySettings {
		mode = mode == null ? VillageBoundaryMode.ADAPTIVE : mode;
		margin = Math.clamp(margin, 1, 24);
		scanRadius = Math.clamp(scanRadius, 16, 128);
		minimumRadius = Math.clamp(minimumRadius, 8, 80);
		maximumRadius = Math.clamp(maximumRadius, minimumRadius, 128);
		legacyRadius = Math.clamp(legacyRadius, 8, maximumRadius);
		gateScanDistance = Math.clamp(gateScanDistance, 0, 16);
		cacheTicks = Math.clamp(cacheTicks, 200, 72000);
	}
}
