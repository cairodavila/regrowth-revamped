package com.mactso.regrowth.village;

public enum VillageBoundaryMode {
	ADAPTIVE,
	LEGACY;

	public static VillageBoundaryMode parse(String value, VillageBoundaryMode fallback) {
		if (value == null) {
			return fallback;
		}
		return switch (value.trim().toLowerCase()) {
			case "adaptive", "poi_hull" -> ADAPTIVE;
			case "legacy", "fixed_square" -> LEGACY;
			default -> fallback;
		};
	}
}
