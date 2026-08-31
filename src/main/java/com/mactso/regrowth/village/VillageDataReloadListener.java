package com.mactso.regrowth.village;

import com.mactso.regrowth.Main;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class VillageDataReloadListener implements SimpleSynchronousResourceReloadListener {

	private static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "village_data");

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		VillageProfileManager.reload(resourceManager);
		ProfessionRoleManager.reload(resourceManager);
		VillagePlanManager.clear();
	}
}
