package com.mactso.regrowth.events;

import com.mactso.regrowth.utility.Utility;
import com.mactso.regrowth.village.ProfessionRoleManager;
import com.mactso.regrowth.village.VillageProfessionRole;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

public class TrampleEventHandler {

	// Villager levels run novice=1 .. master=5; experts and masters no longer trample their crops.
	private static final int EXPERT_LEVEL = 4;

	public static boolean handleTrampleEvent(Entity entity) {

		BlockPos pos = entity.blockPosition();
		if (entity instanceof Villager ve
				&& ProfessionRoleManager.hasRole(ve, VillageProfessionRole.FARM)
				&& ve.getVillagerData().level() >= EXPERT_LEVEL) {
			Utility.debugMsg(1, pos, "Farmland trample cancelled: expert farmer.");
			return true;
		}
		if (entity instanceof ServerPlayer player && player.isCreative()) {
			Utility.debugMsg(1, pos, "Farmland trample cancelled: creative player.");
			return true;
		}
		return false;
	}
	
}
