package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

class VillageProfileTest {

	@Test
	void profileParsesSelectorsAndClampsUnsafeLimits() {
		String json = """
				{
				  "priority": 90,
				  "match": {"biomes": ["#minecraft:is_forest"]},
				  "boundary": {
				    "mode": "legacy",
				    "margin": 100,
				    "minimum_radius": 20,
				    "maximum_radius": 10
				  },
				  "roads": {"repair": "disabled"}
				}
				""";

		VillageProfile profile = VillageProfile.parse(
				Identifier.fromNamespaceAndPath("test", "forest"),
				JsonParser.parseString(json).getAsJsonObject());

		assertEquals(90, profile.priority());
		assertEquals("#minecraft:is_forest", profile.biomes().getFirst().toString());
		assertEquals(VillageBoundaryMode.LEGACY, profile.boundary().mode());
		assertEquals(24, profile.boundary().margin());
		assertEquals(20, profile.boundary().maximumRadius());
		assertEquals(VillageProfile.RoadRepairMode.DISABLED, profile.roads().repairMode());
		assertTrue(profile.structures().isEmpty());
	}
}
