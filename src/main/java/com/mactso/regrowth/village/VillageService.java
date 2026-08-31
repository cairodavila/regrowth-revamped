package com.mactso.regrowth.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mactso.regrowth.config.MyConfig;
import com.mactso.regrowth.village.VillageControlSavedData.VillageModeOverride;
import com.mactso.regrowth.village.VillagePlan.BoundaryNode;
import com.mactso.regrowth.village.VillagePlan.BoundaryRole;
import com.mactso.regrowth.village.VillagePlan.CardinalDirection;
import com.mactso.regrowth.village.VillagePlan.IndexedBoundaryNode;
import com.mactso.regrowth.village.VillagePlanManager.VillageContext;
import com.mactso.regrowth.village.VillageSafety.BoundarySite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class VillageService {

	private static final int VILLAGER_BUILD_REACH = 3;
	private static final int COMMAND_VILLAGE_RADIUS = 96;
	private static final int PREVIEW_PARTICLE_LIMIT = 400;
	private static final int[][] ORTHOGONAL_STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	private VillageService() {
	}

	public static boolean improveWall(Villager villager, String regrowthActions) {
		if (!ProfessionRoleManager.hasRole(villager, VillageProfessionRole.WALLS)) {
			return false;
		}
		Optional<VillageContext> context = contextFor(villager);
		if (context.isEmpty() || !context.get().control().enabled()) {
			return false;
		}
		VillageContext village = context.get();
		ServerLevel level = (ServerLevel) villager.level();
		if (!wallWorkEnabled(level, village.meetingPoint())) {
			return false;
		}
		GridPoint villagerPosition = new GridPoint(villager.getBlockX(), villager.getBlockZ());
		Optional<IndexedBoundaryNode> nearest = village.plan()
				.nearestBoundary(villagerPosition, VILLAGER_BUILD_REACH);
		if (nearest.isEmpty() || nearest.get().node().role() == BoundaryRole.OPENING) {
			return false;
		}

		IndexedBoundaryNode plannedNode = nearest.get();
		Optional<BoundarySite> site = VillageSafety.boundarySite(level, village.profile(), plannedNode.node());
		if (site.isEmpty() || site.get().alreadyBuilt()) {
			return false;
		}

		BlockState state = boundaryState(village.profile(), plannedNode.node());
		if (!level.setBlock(site.get().buildPosition(), state, Block.UPDATE_ALL)) {
			return false;
		}
		placeBoundaryTorch(level, village, plannedNode, site.get(), regrowthActions);
		moveVillagerAwayFromBoundary(villager, plannedNode.node());
		return true;
	}

	public static boolean improveRoad(Villager villager) {
		if (!ProfessionRoleManager.hasRole(villager, VillageProfessionRole.ROADS)) {
			return false;
		}
		Optional<VillageContext> context = contextFor(villager);
		if (context.isEmpty() || !context.get().control().enabled()) {
			return false;
		}
		VillageContext village = context.get();
		if (village.profile().roads().repairMode() == VillageProfile.RoadRepairMode.DISABLED) {
			return false;
		}

		GridPoint position = new GridPoint(villager.getBlockX(), villager.getBlockZ());
		if (!village.plan().contains(position)) {
			return false;
		}
		ServerLevel level = (ServerLevel) villager.level();
		BlockPos ground = VillageSafety.surface(level, position.x(), position.z());
		BlockState groundState = level.getBlockState(ground);
		BlockPos above = ground.above();
		if (VillageSafety.isRoad(groundState, village.profile())
				&& level.getBlockState(above).getBlock() instanceof SnowLayerBlock) {
			level.destroyBlock(above, false);
			return true;
		}
		if (VillageSafety.isRoad(groundState, village.profile())
				|| !level.canSeeSky(above)
				|| !VillageSafety.canReplaceRoad(level, ground, village.profile())) {
			return false;
		}

		RoadNeighbors neighbors = roadNeighbors(level, ground, village.profile());
		if (neighbors.roadCount() >= 3
				|| village.profile().roads().smoothSlopes() && neighbors.connectsOppositeSides()) {
			return level.setBlock(ground, neighbors.preferredRoad().defaultBlockState(), Block.UPDATE_ALL);
		}
		return false;
	}

	public static Optional<VillageContext> nearestContext(ServerLevel level, BlockPos position) {
		return VillagePlanManager.nearestMeeting(level, position, COMMAND_VILLAGE_RADIUS)
				.map(meetingPoint -> VillagePlanManager.context(level, meetingPoint));
	}

	public static int preview(ServerPlayer player, VillageContext village) {
		ServerLevel level = (ServerLevel) player.level();
		int boundarySize = village.plan().boundary().size();
		int stride = Math.max(1, (int) Math.ceil((double) boundarySize / PREVIEW_PARTICLE_LIMIT));
		int particles = 0;
		for (int index = 0; index < boundarySize; index += stride) {
			BoundaryNode node = village.plan().boundary().get(index);
			if (!VillageSafety.isColumnLoaded(level, node.position().x(), node.position().z())) {
				continue;
			}
			BlockPos position = VillageSafety.boundarySite(level, village.profile(), node)
					.map(BoundarySite::buildPosition)
					.orElseGet(() -> VillageSafety.surface(level, node.position().x(), node.position().z()).above());
			var particle = switch (node.role()) {
				case GATE -> ParticleTypes.HAPPY_VILLAGER;
				case OPENING -> ParticleTypes.COMPOSTER;
				case WALL -> ParticleTypes.END_ROD;
			};
			level.sendParticles(player, particle, true, true,
					position.getX() + 0.5, position.getY() + 0.6, position.getZ() + 0.5,
					1, 0.0, 0.0, 0.0, 0.0);
			particles++;
		}
		return particles;
	}

	public static void setEnabled(ServerLevel level, BlockPos meetingPoint, boolean enabled) {
		VillageControlSavedData.get(level).setEnabled(meetingPoint.asLong(), enabled);
	}

	public static void setMode(ServerLevel level, BlockPos meetingPoint, VillageModeOverride mode) {
		VillageControlSavedData.get(level).setMode(meetingPoint.asLong(), mode);
		VillagePlanManager.invalidate(level, meetingPoint);
	}

	public static void rebuild(ServerLevel level, BlockPos meetingPoint) {
		VillagePlanManager.invalidate(level, meetingPoint);
		VillagePlanManager.context(level, meetingPoint);
	}

	private static Optional<VillageContext> contextFor(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		return villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
				.map(GlobalPos::pos)
				.map(meetingPoint -> VillagePlanManager.context(level, meetingPoint));
	}

	private static boolean wallWorkEnabled(ServerLevel level, BlockPos meetingPoint) {
		Block controlBlock = MyConfig.getPlayerWallControlBlock();
		if (controlBlock == Blocks.AIR) {
			return true;
		}
		if (level.getChunkAt(meetingPoint).getInhabitedTime() < 200) {
			level.setBlock(meetingPoint.above(), controlBlock.defaultBlockState(), Block.UPDATE_ALL);
		}
		return level.getBlockState(meetingPoint.above()).is(controlBlock);
	}

	private static BlockState boundaryState(VillageProfile profile, BoundaryNode node) {
		if (node.role() != BoundaryRole.GATE) {
			return profile.walls().wallBlock().orElse(Blocks.COBBLESTONE_WALL).defaultBlockState();
		}
		Block gate = profile.walls().gateBlock().orElse(Blocks.OAK_FENCE_GATE);
		BlockState state = gate.defaultBlockState();
		if (gate instanceof FenceGateBlock) {
			state = state.setValue(FenceGateBlock.FACING, direction(node.outward()));
		}
		return state;
	}

	private static void placeBoundaryTorch(
			ServerLevel level,
			VillageContext village,
			IndexedBoundaryNode plannedNode,
			BoundarySite site,
			String regrowthActions) {
		int spacing = village.profile().walls().torchSpacing();
		if (!regrowthActions.contains("t")
				|| spacing == 0
				|| plannedNode.node().role() != BoundaryRole.WALL
				|| plannedNode.index() % spacing != 0) {
			return;
		}
		BlockPos torchPosition = site.buildPosition().above();
		if (level.getBlockState(torchPosition).isAir()) {
			level.setBlock(torchPosition, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static RoadNeighbors roadNeighbors(
			ServerLevel level,
			BlockPos center,
			VillageProfile profile) {
		Map<Block, Integer> roadCounts = new HashMap<>();
		boolean east = false;
		boolean west = false;
		boolean south = false;
		boolean north = false;
		for (int[] step : ORTHOGONAL_STEPS) {
			BlockPos neighborSurface = VillageSafety.surface(
					level, center.getX() + step[0], center.getZ() + step[1]);
			BlockState neighborState = level.getBlockState(neighborSurface);
			if (!VillageSafety.isRoad(neighborState, profile)
					|| Math.abs(neighborSurface.getY() - center.getY()) > 1) {
				continue;
			}
			roadCounts.merge(neighborState.getBlock(), 1, Integer::sum);
			east |= step[0] > 0;
			west |= step[0] < 0;
			south |= step[1] > 0;
			north |= step[1] < 0;
		}
		Block preferredRoad = roadCounts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(Blocks.DIRT_PATH);
		int roadCount = roadCounts.values().stream().mapToInt(Integer::intValue).sum();
		return new RoadNeighbors(preferredRoad, roadCount, east && west || north && south);
	}

	private static void moveVillagerAwayFromBoundary(Villager villager, BoundaryNode node) {
		double pushX = -node.outward().stepX() * 0.35;
		double pushZ = -node.outward().stepZ() * 0.35;
		villager.setDeltaMovement(pushX, 0.35, pushZ);
	}

	private static Direction direction(CardinalDirection direction) {
		return switch (direction) {
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}

	private record RoadNeighbors(Block preferredRoad, int roadCount, boolean connectsOppositeSides) {
	}
}
