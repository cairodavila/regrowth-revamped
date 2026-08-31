package com.mactso.regrowth.village;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ProfessionRoleManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final String ROLE_DIRECTORY = "regrowth/profession_roles";
	private static volatile List<ProfessionRoleRule> rules = List.of();

	private ProfessionRoleManager() {
	}

	public static void reload(ResourceManager resourceManager) {
		Map<Identifier, Resource> resources = resourceManager.listResources(
				ROLE_DIRECTORY,
				identifier -> identifier.getPath().endsWith(".json"));
		List<ProfessionRoleRule> loadedRules = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				loadedRules.add(ProfessionRoleRule.parse(ruleId(entry.getKey()), json));
			} catch (Exception exception) {
				LOGGER.error("Could not load Regrowth profession roles {}", entry.getKey(), exception);
			}
		}
		loadedRules.sort(Comparator.comparingInt(ProfessionRoleRule::priority)
				.thenComparing(rule -> rule.id().toString()));
		rules = List.copyOf(loadedRules);
		LOGGER.info("Loaded {} Regrowth profession role rules", loadedRules.size());
	}

	public static boolean hasRole(Villager villager, VillageProfessionRole role) {
		return rolesFor(villager.getVillagerData().profession()).contains(role);
	}

	public static Set<VillageProfessionRole> rolesFor(Holder<VillagerProfession> profession) {
		if (rules.isEmpty()) {
			return fallbackRoles(profession);
		}
		EnumSet<VillageProfessionRole> result = EnumSet.noneOf(VillageProfessionRole.class);
		for (ProfessionRoleRule rule : rules) {
			if (!rule.matches(profession)) {
				continue;
			}
			if (rule.replace()) {
				result.clear();
			}
			result.addAll(rule.roles());
		}
		return Set.copyOf(result);
	}

	public static List<ProfessionRoleRule> rules() {
		return rules;
	}

	private static Set<VillageProfessionRole> fallbackRoles(Holder<VillagerProfession> profession) {
		EnumSet<VillageProfessionRole> roles = EnumSet.of(
				VillageProfessionRole.CLEAR,
				VillageProfessionRole.LIGHTING,
				VillageProfessionRole.ROADS,
				VillageProfessionRole.TRIM,
				VillageProfessionRole.WALLS);
		if (profession.is(VillagerProfession.FARMER)) {
			roles.add(VillageProfessionRole.FARM);
		}
		if (profession.is(VillagerProfession.CLERIC)) {
			roles.add(VillageProfessionRole.HEAL_PEOPLE);
		}
		if (profession.is(VillagerProfession.TOOLSMITH)) {
			roles.add(VillageProfessionRole.HEAL_GOLEMS);
		}
		profession.unwrapKey().ifPresent(key -> {
			String path = key.identifier().getPath();
			if (path.equals("beekeeper") || path.equals("florist")) {
				roles.add(VillageProfessionRole.PLANT_FLOWERS);
			}
		});
		return Set.copyOf(roles);
	}

	private static Identifier ruleId(Identifier resourceId) {
		String path = resourceId.getPath();
		String prefix = ROLE_DIRECTORY + "/";
		String relativePath = path.substring(prefix.length(), path.length() - ".json".length());
		return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), relativePath);
	}
}
