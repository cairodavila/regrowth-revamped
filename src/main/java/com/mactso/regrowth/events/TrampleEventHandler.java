package com.mactso.regrowth.events;

import com.mactso.regrowth.utility.Utility;
import com.mactso.regrowth.village.ProfessionRoleManager;
import com.mactso.regrowth.village.VillageProfessionRole;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

public class TrampleEventHandler {


	public static boolean handleTrampleEvent(Entity entity) {

		BlockPos pos = entity.blockPosition();
		Utility.debugMsg(1, pos, "Enter handleTrampleEvent");
		
		if (entity instanceof Villager ve) {
			if (ProfessionRoleManager.hasRole(ve, VillageProfessionRole.FARM)
					&& ve.getVillagerData().level() > 3) {
				Utility.debugMsg(1, pos, "Villager is L3 farmer");
				return true;
			}
		}
		if ((entity instanceof ServerPlayer spe)) {
			if (spe.isCreative()) {
				return true;
			}
			Utility.debugMsg(1, pos, "FarmlandTrampleCancelled");
		}

		return false;
	}
	
}
