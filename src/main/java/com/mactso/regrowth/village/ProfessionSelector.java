package com.mactso.regrowth.village;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public record ProfessionSelector(String value) {

	public ProfessionSelector {
		value = value.trim().toLowerCase();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("Profession selector cannot be empty");
		}
		if (!value.equals("*") && !value.contains("*") && Identifier.tryParse(stripTag(value)) == null) {
			throw new IllegalArgumentException("Invalid profession selector: " + value);
		}
	}

	public boolean matches(Holder<VillagerProfession> profession) {
		if (value.equals("*")) {
			return true;
		}
		if (value.startsWith("#")) {
			Identifier tagId = Identifier.parse(value.substring(1));
			return profession.is(TagKey.create(Registries.VILLAGER_PROFESSION, tagId));
		}
		Identifier professionId = profession.unwrapKey().map(ResourceKey::identifier).orElse(null);
		if (professionId == null) {
			return false;
		}
		int separator = value.indexOf(':');
		String namespacePattern = separator >= 0 ? value.substring(0, separator) : "minecraft";
		String pathPattern = separator >= 0 ? value.substring(separator + 1) : value;
		return wildcardMatches(namespacePattern, professionId.getNamespace())
				&& wildcardMatches(pathPattern, professionId.getPath());
	}

	private static boolean wildcardMatches(String pattern, String actual) {
		return pattern.equals("*") || pattern.equals(actual);
	}

	private static String stripTag(String selector) {
		return selector.startsWith("#") ? selector.substring(1) : selector;
	}
}
