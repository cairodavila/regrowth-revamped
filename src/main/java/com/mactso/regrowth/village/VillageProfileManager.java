package com.mactso.regrowth.village;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class VillageProfileManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final String PROFILE_DIRECTORY = "regrowth/village_profiles";
	private static volatile List<VillageProfile> profiles = List.of(VillageProfile.fallback());

	private VillageProfileManager() {
	}

	public static void reload(ResourceManager resourceManager) {
		Map<Identifier, Resource> resources = resourceManager.listResources(
				PROFILE_DIRECTORY,
				identifier -> identifier.getPath().endsWith(".json"));
		List<VillageProfile> loadedProfiles = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			Identifier resourceId = entry.getKey();
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				loadedProfiles.add(VillageProfile.parse(profileId(resourceId), json));
			} catch (Exception exception) {
				LOGGER.error("Could not load Regrowth village profile {}", resourceId, exception);
			}
		}
		loadedProfiles.sort(Comparator.comparingInt(VillageProfile::priority).reversed()
				.thenComparing(profile -> profile.id().toString()));
		loadedProfiles.add(VillageProfile.fallback());
		profiles = List.copyOf(loadedProfiles);
		LOGGER.info("Loaded {} Regrowth village profiles", loadedProfiles.size() - 1);
	}

	public static VillageProfile select(ServerLevel level, BlockPos meetingPoint) {
		Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(meetingPoint);
		for (VillageProfile profile : profiles) {
			if (profile.matchesBiome(biome) && matchesStructure(level, meetingPoint, profile.structures())) {
				return profile;
			}
		}
		return VillageProfile.fallback();
	}

	public static List<VillageProfile> profiles() {
		return profiles;
	}

	private static boolean matchesStructure(
			ServerLevel level,
			BlockPos meetingPoint,
			List<ResourceSelector> selectors) {
		if (selectors.isEmpty()) {
			return true;
		}
		return selectors.stream().anyMatch(selector -> level.structureManager()
				.getStructureWithPieceAt(meetingPoint, structure -> selector.matches(structure, Registries.STRUCTURE))
				.isValid());
	}

	private static Identifier profileId(Identifier resourceId) {
		String path = resourceId.getPath();
		String prefix = PROFILE_DIRECTORY + "/";
		String relativePath = path.substring(prefix.length(), path.length() - ".json".length());
		return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), relativePath);
	}
}
