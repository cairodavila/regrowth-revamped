package com.mactso.regrowth.village;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record ResourceSelector(Identifier identifier, boolean tag) {

	public static ResourceSelector parse(String value) {
		String normalized = value.trim();
		boolean tag = normalized.startsWith("#");
		Identifier identifier = Identifier.tryParse(tag ? normalized.substring(1) : normalized);
		if (identifier == null) {
			throw new IllegalArgumentException("Invalid resource selector: " + value);
		}
		return new ResourceSelector(identifier, tag);
	}

	public boolean matches(BlockState state) {
		if (tag) {
			return state.is(TagKey.create(Registries.BLOCK, identifier));
		}
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(identifier);
	}

	public boolean matches(Holder<Biome> biome) {
		if (tag) {
			return biome.is(TagKey.create(Registries.BIOME, identifier));
		}
		return biome.is(ResourceKey.create(Registries.BIOME, identifier));
	}

	public Optional<Block> firstBlock(Predicate<BlockState> validator) {
		if (!tag) {
			return BuiltInRegistries.BLOCK.getOptional(identifier)
					.filter(block -> validator.test(block.defaultBlockState()));
		}
		TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, identifier);
		for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
			Block block = holder.value();
			if (validator.test(block.defaultBlockState())) {
				return Optional.of(block);
			}
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return (tag ? "#" : "") + identifier;
	}
}
