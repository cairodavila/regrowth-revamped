package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

class ProfessionRoleRuleTest {

	@Test
	void roleRuleParsesWildcardProfessionsAndRoles() {
		ProfessionRoleRule rule = ProfessionRoleRule.parse(
				Identifier.fromNamespaceAndPath("test", "engineers"),
				JsonParser.parseString("""
						{
						  "priority": 20,
						  "professions": ["morevillagers:*"],
						  "roles": ["roads", "walls"]
						}
						""").getAsJsonObject());

		assertEquals(20, rule.priority());
		assertEquals("morevillagers:*", rule.professions().getFirst().value());
		assertTrue(rule.roles().contains(VillageProfessionRole.ROADS));
		assertTrue(rule.roles().contains(VillageProfessionRole.WALLS));
	}

	@Test
	void invalidProfessionSelectorFailsAtTheDataBoundary() {
		assertThrows(IllegalArgumentException.class, () -> new ProfessionSelector("bad id!"));
	}
}
