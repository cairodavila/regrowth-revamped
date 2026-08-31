package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public record ProfessionRoleRule(
		Identifier id,
		int priority,
		List<ProfessionSelector> professions,
		Set<VillageProfessionRole> roles,
		boolean replace) {

	public ProfessionRoleRule {
		professions = List.copyOf(professions);
		roles = Set.copyOf(roles);
	}

	public static ProfessionRoleRule parse(Identifier id, JsonObject json) {
		int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
		boolean replace = json.has("replace") && json.get("replace").getAsBoolean();
		List<ProfessionSelector> professions = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("professions")) {
			professions.add(new ProfessionSelector(element.getAsString()));
		}
		EnumSet<VillageProfessionRole> roles = EnumSet.noneOf(VillageProfessionRole.class);
		for (JsonElement element : json.getAsJsonArray("roles")) {
			roles.add(VillageProfessionRole.parse(element.getAsString()));
		}
		return new ProfessionRoleRule(id, priority, professions, roles, replace);
	}

	public boolean matches(Holder<VillagerProfession> profession) {
		return professions.stream().anyMatch(selector -> selector.matches(profession));
	}
}
