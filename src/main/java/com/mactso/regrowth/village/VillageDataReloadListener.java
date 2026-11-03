package com.mactso.regrowth.village;

import com.mactso.regrowth.Main;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class VillageDataReloadListener implements ResourceManagerReloadListener {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "village_data");

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		VillageProfileManager.reload(resourceManager);
		ProfessionRoleManager.reload(resourceManager);
		VillagePlanManager.clear();
	}
}
