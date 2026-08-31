package com.mactso.regrowth.village;

public enum VillageProfessionRole {
	CLEAR,
	FARM,
	HEAL_PEOPLE,
	HEAL_GOLEMS,
	LIGHTING,
	PLANT_FLOWERS,
	ROADS,
	TRIM,
	WALLS;

	public static VillageProfessionRole parse(String value) {
		return valueOf(value.trim().toUpperCase());
	}
}
