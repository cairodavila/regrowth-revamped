package com.mactso.regrowth;

import com.mactso.regrowth.commands.RegrowthCommands;
import com.mactso.regrowth.config.MyConfig;
import com.mactso.regrowth.village.VillageDataReloadListener;
import com.mactso.regrowth.village.VillagePlanManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public class Main implements ModInitializer {

	public static final String MOD_ID = "regrowth";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> RegrowthCommands.register(dispatcher));
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
				VillageDataReloadListener.ID, new VillageDataReloadListener());
		// Wall and fence blocks are resolved through block tags, which are bound once the server has loaded its data.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> MyConfig.registerConfigs());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VillagePlanManager.clear());
	}
}
