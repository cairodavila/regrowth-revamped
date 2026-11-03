package com.mactso.regrowth.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mactso.regrowth.config.MyConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class Utility {

	// Biome categories double as wall config keys ("minecraft:<category>").
	public static final String NONE = "none";
	public static final String BEACH = "beach";
	public static final String DESERT = "desert";
	public static final String EXTREME_HILLS = "extreme_hills";
	public static final String ICY = "icy";
	public static final String JUNGLE = "jungle";
	public static final String FOREST = "forest";
	public static final String MESA = "mesa";
	public static final String NETHER = "nether";
	public static final String OCEAN = "ocean";
	public static final String PLAINS = "plains";
	public static final String RIVER = "river";
	public static final String SAVANNA = "savanna";
	public static final String SWAMP = "swamp";
	public static final String TAIGA = "taiga";

	private static final Logger LOGGER = LogManager.getLogger();

	public static String biomeCategory(Holder<Biome> biome) {
		if (biome.is(BiomeTags.HAS_VILLAGE_DESERT))
			return DESERT;
		if (biome.is(BiomeTags.IS_FOREST))
			return FOREST;
		if (biome.is(BiomeTags.IS_BEACH))
			return BEACH;
		if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY))
			return ICY;
		if (biome.is(BiomeTags.IS_JUNGLE))
			return JUNGLE;
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN))
			return OCEAN;
		if (biome.is(BiomeTags.HAS_VILLAGE_PLAINS))
			return PLAINS;
		if (biome.is(BiomeTags.IS_RIVER))
			return RIVER;
		if (biome.is(BiomeTags.IS_SAVANNA))
			return SAVANNA;
		if (biome.is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS))
			return SWAMP;
		if (biome.is(BiomeTags.IS_TAIGA))
			return TAIGA;
		if (biome.is(BiomeTags.IS_BADLANDS))
			return MESA;
		if (biome.is(BiomeTags.IS_MOUNTAIN))
			return EXTREME_HILLS;
		if (biome.is(BiomeTags.IS_NETHER))
			return NETHER;
		return NONE;
	}

	public static void debugMsg(int level, String dMsg) {
		if (MyConfig.getDebugLevel() >= level) {
			LOGGER.info("L" + level + ":" + dMsg);
		}
	}

	public static void debugMsg(int level, BlockPos pos, String dMsg) {
		if (MyConfig.getDebugLevel() >= level) {
			LOGGER.info("L" + level + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "): " + dMsg);
		}
	}

	public static void debugMsg(int level, LivingEntity le, String dMsg) {
		debugMsg(level, le.blockPosition(), dMsg);
	}

	public static void sendBoldChat(Player p, String chatMessage, ChatFormatting textColor) {
		sendChat(p, Component.literal(chatMessage).withStyle(textColor, ChatFormatting.BOLD));
	}

	public static void sendChat(Player p, String chatMessage, ChatFormatting textColor) {
		sendChat(p, Component.literal(chatMessage).withStyle(textColor));
	}

	private static void sendChat(Player p, MutableComponent component) {
		if (p instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(component);
		}
	}

	public static String getResourceLocationString(BlockState blockState) {
		return getResourceLocationString(blockState.getBlock());
	}

	@SuppressWarnings("deprecation")
	public static String getResourceLocationString(Block block) {
		return block.builtInRegistryHolder().key().identifier().toString();
	}

	public static String getResourceLocationString(Entity entity) {
		return EntityType.getKey(entity.getType()).toString();
	}
}
