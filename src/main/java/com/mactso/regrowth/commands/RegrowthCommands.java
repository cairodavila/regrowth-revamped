package com.mactso.regrowth.commands;

import java.util.Optional;

import com.mactso.regrowth.config.MyConfig;
import com.mactso.regrowth.utility.Utility;
import com.mactso.regrowth.village.VillageControlSavedData.VillageModeOverride;
import com.mactso.regrowth.village.VillagePlanManager;
import com.mactso.regrowth.village.VillagePlanManager.VillageContext;
import com.mactso.regrowth.village.VillageService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class RegrowthCommands {

	private RegrowthCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("regrowth")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("debugLevel")
						.then(Commands.argument("debugLevel", IntegerArgumentType.integer(0, 2))
								.executes(context -> setDebugLevel(
										IntegerArgumentType.getInteger(context, "debugLevel")))))
				.then(Commands.literal("info").executes(context -> showInfo(context.getSource())))
				.then(Commands.literal("village")
						.then(Commands.literal("analyze")
								.executes(context -> analyzeVillage(context.getSource())))
						.then(Commands.literal("preview")
								.executes(context -> previewVillage(context.getSource()))
								.then(Commands.literal("walls")
										.executes(context -> previewVillage(context.getSource()))))
						.then(Commands.literal("enable")
								.executes(context -> setVillageEnabled(context.getSource(), true)))
						.then(Commands.literal("disable")
								.executes(context -> setVillageEnabled(context.getSource(), false)))
						.then(Commands.literal("rebuild")
								.executes(context -> rebuildVillage(context.getSource())))
						.then(Commands.literal("mode")
								.then(Commands.literal("default").executes(context ->
										setVillageMode(context.getSource(), VillageModeOverride.DEFAULT)))
								.then(Commands.literal("adaptive").executes(context ->
										setVillageMode(context.getSource(), VillageModeOverride.ADAPTIVE)))
								.then(Commands.literal("legacy").executes(context ->
										setVillageMode(context.getSource(), VillageModeOverride.LEGACY))))));
	}

	public static int setDebugLevel(int newDebugLevel) {
		MyConfig.setDebugLevel(newDebugLevel);
		return 1;
	}

	private static int showInfo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Level level = player.level();
		Vec3 eyePosition = player.getEyePosition(1.0F);
		Vec3 viewVector = player.getViewVector(1.0F);
		double reach = 32.0D;
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(
				player,
				eyePosition,
				eyePosition.add(viewVector.scale(reach)),
				player.getBoundingBox().expandTowards(viewVector.scale(reach)).inflate(1.0D),
				entity -> entity != player && entity.isPickable(),
				reach * reach);
		String objectInfo = "You are not looking at an entity.";
		if (hit != null) {
			Entity entity = hit.getEntity();
			objectInfo = EntityType.getKey(entity.getType()).toString();
		}

		Utility.sendBoldChat(player, level.dimension() + "\n Current Values", ChatFormatting.DARK_GREEN);
		Utility.sendChat(player,
				"\n  Regrowth (Fabric)"
						+ "\n  Debug Level...........: " + MyConfig.getDebugLevel()
						+ "\n  Looking At............: " + objectInfo,
				ChatFormatting.DARK_GREEN);
		return 1;
	}

	private static int analyzeVillage(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Optional<VillageSelection> selection = nearestVillage(source);
		if (selection.isEmpty()) {
			return 0;
		}
		VillageContext village = selection.get().context();
		BlockPos meetingPoint = village.meetingPoint();
		String message = "Regrowth village " + coordinates(meetingPoint)
				+ ": profile=" + village.profile().id()
				+ ", mode=" + village.plan().mode().name().toLowerCase()
				+ ", enabled=" + village.control().enabled()
				+ ", POIs=" + village.plan().pointOfInterestCount()
				+ ", boundary=" + village.plan().boundary().size()
				+ ", gates=" + village.plan().gateCount()
				+ ", openings=" + village.plan().openingCount()
				+ ", cachedPlans=" + VillagePlanManager.cachedPlanCount();
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int previewVillage(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Optional<VillageSelection> selection = nearestVillage(source);
		if (selection.isEmpty()) {
			return 0;
		}
		int particleCount = VillageService.preview(selection.get().player(), selection.get().context());
		source.sendSuccess(() -> Component.literal(
				"Previewed " + particleCount + " planned village boundary points."), false);
		return particleCount;
	}

	private static int setVillageEnabled(CommandSourceStack source, boolean enabled)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Optional<VillageSelection> selection = nearestVillage(source);
		if (selection.isEmpty()) {
			return 0;
		}
		VillageSelection village = selection.get();
		VillageService.setEnabled((ServerLevel) village.player().level(),
				village.context().meetingPoint(), enabled);
		source.sendSuccess(() -> Component.literal(
				"Regrowth village work " + (enabled ? "enabled" : "disabled")
						+ " at " + coordinates(village.context().meetingPoint()) + "."), true);
		return 1;
	}

	private static int setVillageMode(CommandSourceStack source, VillageModeOverride mode)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Optional<VillageSelection> selection = nearestVillage(source);
		if (selection.isEmpty()) {
			return 0;
		}
		VillageSelection village = selection.get();
		VillageService.setMode((ServerLevel) village.player().level(), village.context().meetingPoint(), mode);
		source.sendSuccess(() -> Component.literal(
				"Regrowth village mode set to " + mode.serializedName()
						+ " at " + coordinates(village.context().meetingPoint()) + "."), true);
		return 1;
	}

	private static int rebuildVillage(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Optional<VillageSelection> selection = nearestVillage(source);
		if (selection.isEmpty()) {
			return 0;
		}
		VillageSelection village = selection.get();
		VillageService.rebuild((ServerLevel) village.player().level(), village.context().meetingPoint());
		source.sendSuccess(() -> Component.literal(
				"Rebuilt the Regrowth village plan at " + coordinates(village.context().meetingPoint()) + "."), true);
		return 1;
	}

	private static Optional<VillageSelection> nearestVillage(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		Optional<VillageContext> village = VillageService.nearestContext(level, player.blockPosition());
		if (village.isEmpty()) {
			source.sendFailure(Component.literal("No village meeting point was found within 96 blocks."));
			return Optional.empty();
		}
		return Optional.of(new VillageSelection(player, village.get()));
	}

	private static String coordinates(BlockPos position) {
		return position.getX() + "," + position.getY() + "," + position.getZ();
	}

	private record VillageSelection(ServerPlayer player, VillageContext context) {
	}
}
