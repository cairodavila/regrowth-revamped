package com.mactso.regrowth.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.mactso.regrowth.config.MyConfig;
import com.mactso.regrowth.config.RegrowthEntitiesManager;
import com.mactso.regrowth.config.RegrowthEntitiesManager.RegrowthMobItem;
import com.mactso.regrowth.config.WallBiomeDataManager;
import com.mactso.regrowth.config.WallFoundationDataManager;
import com.mactso.regrowth.utility.Utility;
import com.mactso.regrowth.village.ProfessionRoleManager;
import com.mactso.regrowth.village.VillageProfessionRole;
import com.mactso.regrowth.village.VillageService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.CowSoundVariants;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
//? if >=26.3
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.CoralWallFanBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.MyceliumBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MoveEntityEvent {

	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPEN = FenceGateBlock.OPEN;
	static final int WALL_CENTER = 0;
	private static final String DEBUG_NAME_PREFIX = "Reg-";

	static final String ACTION_GROW = "grow";
	static final String ACTION_EAT = "eat";
	static final String ACTION_BOTH = "both";
	static final String ACTION_TALL = "tall";
	static final String ACTION_MUSHROOM = "mushroom";
	static final String ACTION_STUMBLE = "stumble";
	static final String ACTION_REFOREST = "reforest";
	static final String ACTION_CORAL = "coral";
	private static Random moveRand = new Random();
	private static final Block[] coralPlants = { Blocks.TALL_SEAGRASS, Blocks.SEAGRASS, Blocks.SEA_PICKLE,
			Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN,
			Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN,
			Blocks.HORN_CORAL_FAN, Blocks.TUBE_CORAL_FAN };
	private static final Block[] coralfans = { Blocks.BRAIN_CORAL_WALL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN,
			Blocks.FIRE_CORAL_WALL_FAN, Blocks.HORN_CORAL_WALL_FAN, Blocks.TUBE_CORAL_WALL_FAN };

	// Entities are evaluated on every other tick.
	private static final int CHECKS_PER_SECOND = 10;
	private static final int CHECKS_BETWEEN_FAST_VILLAGE_WORK = 6;
	private static final long FAST_VILLAGE_WORK_CHUNK_AGE = 480000;
	// Indexed by yaw / 45 degrees, starting at south and turning toward west.
	private static final int[][] facingArray = { { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }, { 1, 0 },
			{ 1, 1 } };
	private static int lastTorchX = 0;
	private static int lastTorchY = 0;
	private static int lastTorchZ = 0;
	private static BlockState footBlockState;

	private static BlockState groundBlockState;
	private static Block footBlock;
	private static Block groundBlock;
	private static String biomeCategory;
	static BlockPos adjustedPos;

	@SuppressWarnings("resource")
	private static void doMobRegrowthEvents(LivingEntity entity, String key, String regrowthType) {
		Utility.debugMsg(1, "enter Mob Events");
		if (regrowthType.equals(ACTION_STUMBLE)) {
			if ((footBlock instanceof TorchBlock) || (footBlock instanceof WallTorchBlock)) {
				mobStumbleAction(entity, key);
			}
			return;
		}

		if (regrowthType.equals(ACTION_REFOREST)) {
			mobReforestAction(entity, key);
			return;
		}

		if (regrowthType.equals(ACTION_MUSHROOM)) {
			mobGrowMushroomAction(entity, key);
			return;
		}

		if (regrowthType.equals(ACTION_CORAL)) {
			mobGrowCoralAction(entity, key);
			return;
		}

		mobHandleOverCrowding(entity, key);

		// all remaining actions currently require a grass block underfoot so if not a
		// grass block- can exit now.
		// this is for performance savings only.
		// looks like meadow_grass_block is not a grassBlock

		if (!isKindOfGrassBlock(groundBlockState)) {
			return;
		}

		if (regrowthType.equals(ACTION_TALL)) {
			mobGrowTallAction(entity, key);
			return;
		}

		if (regrowthType.equals(ACTION_BOTH)) {
			if (entity.level().getRandom().nextDouble() * 100 > 85.0) {
				regrowthType = ACTION_GROW;
			} else {
				regrowthType = ACTION_EAT;
			}
		}

		if (regrowthType.contentEquals(ACTION_EAT)) {
			mobEatPlantsAction(entity, key, regrowthType);
			return;
		}

		if ((regrowthType.equals(ACTION_GROW))) {
			mobGrowPlantsAction(entity, key);
			return;
		}

	}

	private static void doVillagerRegrowthEvents(Villager ve, String debugKey, String regrowthActions) {

		// Villagers hopping, falling, etc. are doing improvements.
		if (!(ve.onGround()))
			return;
		if (groundBlockState.getBlock() instanceof TorchBlock)
			return;
		if (groundBlockState.getBlock() instanceof WallTorchBlock)
			return;

		boolean hasDebugName = ve.getCustomName() != null
				&& ve.getCustomName().getString().startsWith(DEBUG_NAME_PREFIX);
		if (MyConfig.getDebugLevel() > 0 && (ve.getCustomName() == null || hasDebugName)) {
			float veYaw = ve.getViewYRot(1.0f);
			ve.setCustomName(Component.literal(DEBUG_NAME_PREFIX + ve.getX() + "," + ve.getZ() + ": " + veYaw));
		} else if (MyConfig.getDebugLevel() == 0 && hasDebugName) {
			ve.setCustomName(null);
		}

		// note all villagers may not have a home. poor homeless villagers.
		// default = repair farms
		if (ProfessionRoleManager.hasRole(ve, VillageProfessionRole.FARM)
				&& vImproveFarm(ve, regrowthActions)) {
			Utility.debugMsg(1, ve, debugKey + " farm improved.");
		}

		// 'h'eal villagers and players
		if (regrowthActions.contains("h")) {
			vClericalHealing(ve);
			vToolMasterHealing(ve);
		}

		vBeeKeeperFlowers(ve);

		// cut lea'v'es.
		// remove leaves if facing head height leaves

		if (regrowthActions.contains("v")
				&& ProfessionRoleManager.hasRole(ve, VillageProfessionRole.TRIM)) {
			vImproveLeaves(ve, debugKey);
		}

		// c = cut down grass, but leave flowers alone.
		if (regrowthActions.contains("c")
				&& ProfessionRoleManager.hasRole(ve, VillageProfessionRole.CLEAR)) {
			boolean isTallFlower = footBlock instanceof TallFlowerBlock;
			if ((footBlock instanceof TallGrassBlock) || (footBlock instanceof DoublePlantBlock && !isTallFlower)
					|| (footBlock.getDescriptionId().equals("block.byg.short_grass"))) {

				ve.level().destroyBlock(adjustedPos, false);
				Utility.debugMsg(1, ve, debugKey + " grass cut.");
			}
		}
		// improve roads
		// to do - replace "r" with a meaningful constant.f
		if (regrowthActions.contains("r")) {
			Utility.debugMsg(1, ve, debugKey + " try road improve.");
			VillageService.improveRoad(ve);
		}

		// note villages may not have a meeting place. Sometimes they change. Sometimes
		// they take a few minutes to form.
		if (regrowthActions.contains("w")) {
			Utility.debugMsg(1, ve, " try town wall build.");
			VillageService.improveWall(ve, regrowthActions);
		}

		if ((regrowthActions.contains("p"))) {
			Utility.debugMsg(1, ve, " try personal fence build.");
			vImproveFences(ve, debugKey, regrowthActions);

		}

		if (regrowthActions.contains("t")
				&& ProfessionRoleManager.hasRole(ve, VillageProfessionRole.LIGHTING)
				&& footBlock != Blocks.TORCH) {
			if (vImproveLighting(ve)) {
				Utility.debugMsg(1, ve, debugKey + "-" + footBlock + ", " + groundBlock + " pitch: " + ve.getXRot()
						+ " lighting improved.");
			}
		}
		Utility.debugMsg(1, "exit Village Events");
	}

	private static int getAbsVX(Entity e, BlockPos gVMPPos) {
		int absvx = (int) Math.abs(getVX(e, gVMPPos));
		return absvx;
	}

	private static int getAbsVZ(Entity e, BlockPos gVMPPos) {
		return (int) Math.abs(getVZ(e, gVMPPos));
	}

	private static BlockPos getAdjustedBlockPos(Entity e) {
		if (e.getY() == e.blockPosition().getY()) {
			return e.blockPosition();
		}
		return e.blockPosition().above();
	}

	private static BlockState getAdjustedFootBlockState(Entity e) {
		if (e.getY() == e.blockPosition().getY()) {
			return e.level().getBlockState(e.blockPosition());
		}
		return e.level().getBlockState(e.blockPosition().above());
	}

	private static BlockState getAdjustedGroundBlockState(Entity e) {
		return e.level().getBlockState(e.blockPosition().below(getAdjustedY(e)));
	}

	private static int getAdjustedY(Entity e) {
		if (e.getY() == e.blockPosition().getY())
			return 1;
		return 0;
	}

	private static int getVX(Entity e, BlockPos gVMPPos) {
		return (int) (e.getX() - gVMPPos.getX());
	}

	private static int getVZ(Entity e, BlockPos gVMPPos) {
		return (int) (e.getZ() - gVMPPos.getZ());
	}

	public static void handleEntityMoveEvents(LivingEntity le) {
		if (le.level().isClientSide() || le instanceof Player) {
			return;
		}
		if (le.getId() % 2 == le.level().getGameTime() % 2) {
			return;
		}

		String rlString = Utility.getResourceLocationString(le);
		RegrowthMobItem currentRegrowthMobItem = RegrowthEntitiesManager.getRegrowthMobInfo(rlString);
		if (currentRegrowthMobItem == null)
			return;



		adjustedPos = getAdjustedBlockPos(le);

		footBlockState = getAdjustedFootBlockState(le);
		footBlock = footBlockState.getBlock();

		if (footBlock instanceof WoolCarpetBlock)
			return;

		groundBlockState = getAdjustedGroundBlockState(le);
		groundBlock = groundBlockState.getBlock();

		if ((groundBlockState.isAir()) && !(le instanceof Bat))
			return;



		biomeCategory = Utility.biomeCategory(le.level().getBiome(le.blockPosition()));

		String regrowthActions = currentRegrowthMobItem.getRegrowthActions();

		if (isImpossibleRegrowthEvent(regrowthActions))
			return;

		double regrowthEventOdds = 1 / (currentRegrowthMobItem.getRegrowthEventSeconds() * CHECKS_PER_SECOND);
		if (isHorseTypeEatingNow(le)) {
			regrowthEventOdds *= 20;
		}
		double randomD100Roll = le.level().getRandom().nextDouble();

		long chunkAge = le.level().getChunkAt(le.blockPosition()).getInhabitedTime();

		// improve village roads and walls faster for the first 200 minutes;
		if (chunkAge < FAST_VILLAGE_WORK_CHUNK_AGE) {
			if (le instanceof Villager ve) {
				// Counted in checks rather than ticks: a tick-based modulo would always
				// land on the same parity and skip every villager with an even id.
				long checkNumber = ve.level().getGameTime() / 2;
				if (checkNumber % CHECKS_BETWEEN_FAST_VILLAGE_WORK == 0) {
					if (regrowthActions.contains("r")) {
						VillageService.improveRoad(ve);
					}
					if (regrowthActions.contains("w")) {
						VillageService.improveWall(ve, regrowthActions);
					}

				}
			}
		}

		if (randomD100Roll <= regrowthEventOdds) {
			if (le instanceof Villager ve) {
				improvePowderedSnow(le);
				// if onGround and not on bed.
				if ((ve.onGround()) && (!(footBlock instanceof BedBlock))) {
					doVillagerRegrowthEvents(ve, rlString, regrowthActions);
				}
			} else {
				doMobRegrowthEvents(le, rlString, regrowthActions);
			}
		}
		Utility.debugMsg(1, "fall out of Handle Entity tick Events");
	}


	private static void helperChildAgeEntity(Entity ent) {
		if (ent instanceof AgeableMob) {
			AgeableMob aEnt = (AgeableMob) ent;
			if (aEnt.isBaby()) {
				aEnt.setAge(aEnt.getAge() + 30);
			}
		}
	}

	
	public static int helperCountBlocksBB(Block searchBlock, int maxCount, ServerLevel serverLevel, BlockPos bPos, int boxSize) {
		return helperCountBlocksBB(searchBlock, maxCount, serverLevel, bPos, boxSize, boxSize); // "square" box subcase
	}

	public static int helperCountBlocksBB(Block searchBlock, int maxCount, ServerLevel serverLevel, BlockPos bPos, int boxSize,
			int ySize) {
		int count = 0;
		int minX = bPos.getX() - boxSize;
		int maxX = bPos.getX() + boxSize;
		int minZ = bPos.getZ() - boxSize;
		int maxZ = bPos.getZ() + boxSize;
		int minY = bPos.getY() - ySize;
		int maxY = bPos.getY() + ySize;

		MutableBlockPos mPos = new MutableBlockPos();
		for (int dx = minX; dx <= maxX; dx++) {
			for (int dz = minZ; dz <= maxZ; dz++) {
				for (int dy = minY; dy <= maxY; dy++) {
					mPos.set(dx, dy, dz);
					if (serverLevel.getBlockState(mPos).getBlock() == searchBlock) {
						if (++count >= maxCount)
							return count;
					}
				}
			}
		}

		Utility.debugMsg(2, bPos,
				Utility.getResourceLocationString(searchBlock) + " Sparse count:" + count + " countBlockBB ");

		return count;
	}
	
	public static int helperCountBlocksBB(Class<? extends Block> searchBlock, int maxCount, ServerLevel serverLevel, BlockPos bPos, int boxSize, int ySize) {

		int count = 0;
		int minX = bPos.getX() - boxSize;
		int maxX = bPos.getX() + boxSize;
		int minZ = bPos.getZ() - boxSize;
		int maxZ = bPos.getZ() + boxSize;
		int minY = bPos.getY() - ySize;
		int maxY = bPos.getY() + ySize;

		MutableBlockPos mPos = new MutableBlockPos();

		for (int dx = minX; dx <= maxX; dx++) {
			for (int dz = minZ; dz <= maxZ; dz++) {
				for (int dy = minY; dy <= maxY; dy++) {
					mPos.set(dx, dy, dz);
					if (searchBlock.isInstance(serverLevel.getBlockState(mPos).getBlock())) {
						if (++count >= maxCount) {
							return count;
						}
					}
				}
			}
		}

		Utility.debugMsg(2, bPos, searchBlock.getSimpleName() + " Sparse count:" + count + " countBlockBB ");

		return count;
	}
	

	// this routine returns a count of the searchBlock immediately orthogonal to
	// BlockPos, exiting if a max count is exceeded.
	public static int helperCountBlocksOrthogonalBB(Block searchBlock, int maxCount, Level w, BlockPos bPos,
			int boundY) {
		return helperCountBlocksOrthogonalBB(searchBlock, maxCount, w, bPos, 0 - boundY, 0 + boundY);
	}

	public static int helperCountBlocksOrthogonalBB(Block searchBlock, int maxCount, Level w, BlockPos bPos,
			int lowerBoundY, int upperBoundY) {
		int count = 0;
		for (int j = lowerBoundY; j <= upperBoundY; j++) {
			if (w.getBlockState(bPos.above(j).east()).getBlock() == searchBlock)
				count++;
			if (w.getBlockState(bPos.above(j).west()).getBlock() == searchBlock)
				count++;
			if (w.getBlockState(bPos.above(j).north()).getBlock() == searchBlock)
				count++;
			if (w.getBlockState(bPos.above(j).south()).getBlock() == searchBlock)
				count++;
			if (count >= maxCount)
				return count;
		}

		return count;

	}

	private static int helperCountCoral(Entity e) {
		int c = 0;
		for (int ud = -1; ud <= 0; ud++) {
			for (int ew = -1; ew <= 1; ew++) {
				for (int ns = -1; ns <= 1; ns++) {
					if (e.level().getBlockState(e.blockPosition().below(1).east(ew).north(ns))
							.getBlock() instanceof CoralBlock) {
						c++;
					}
				}
			}
		}
		return c;
	}


	private static void helperJumpAway(Entity e) {
		// "jump" villagers away if they are inside a wall, fence, or dirtPath block.
		Block postActionFootBlock = getAdjustedFootBlockState(e).getBlock();
		if (postActionFootBlock == Blocks.DIRT_PATH) {
			e.setDeltaMovement(0, 0.33, 0);
			return;
		}
		if ((postActionFootBlock instanceof WallBlock) || (postActionFootBlock instanceof FenceBlock)) {
			int[] facing = facingStep(e);
			e.setDeltaMovement(facing[0] / 2.0, 0.55, facing[1] / 2.0);
		}
	}

	private static int[] facingStep(Entity e) {
		int facingIndex = Math.floorMod(Math.round(e.getViewYRot(1.0f) / 45), facingArray.length);
		return facingArray[facingIndex];
	}

	private static long helperLongRandomSeed(BlockPos ePos) {
		return (long) Math.abs(ePos.getX() * 31) + Math.abs(ePos.getZ() * 11) + Math.abs(ePos.getY() * 7);
	}

	private static boolean helperPlaceOneWallPiece(LivingEntity le, int wallRadius, BlockState wallType,
			BlockPos gVMPPos) {

		// Build East and West Walls (and corners)
		BlockState gateBlockState = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FACING, Direction.EAST)
				.setValue(OPEN, true);
		Direction d = null;
		if (getAbsVX(le, gVMPPos) == wallRadius) {
			if (getAbsVZ(le, gVMPPos) <= wallRadius) {
				d = (getVX(le, gVMPPos) > 0) ? Direction.EAST : Direction.WEST;
				return helperPlaceWallPiece(le, wallType, gateBlockState.setValue(FACING, d), getVZ(le, gVMPPos));
			}
		}
		// Build North and South Walls (and corners)
		if (getAbsVZ(le, gVMPPos) == wallRadius) {
			if (getAbsVX(le, gVMPPos) <= wallRadius) {
				d = (getVZ(le, gVMPPos) > 0) ? Direction.NORTH : Direction.SOUTH;
				return helperPlaceWallPiece(le, wallType, gateBlockState.setValue(FACING, d), getVX(le, gVMPPos));
			}
		}

		return false;
	}

	private static boolean helperPlaceWallPiece(LivingEntity le, BlockState wallType, BlockState gateBlockType, int va) {

		BlockPos vePos = getAdjustedBlockPos(le);

		if (footBlock instanceof SnowLayerBlock) {
			le.level().destroyBlock(vePos, false);
		}

		if (isNatProgPebbleOrStick((ServerLevel)le.level())) {
			le.level().destroyBlock(vePos, true);
		}

		if ((footBlock instanceof SaplingBlock) || (footBlock instanceof TallGrassBlock)
				|| (footBlock instanceof FlowerBlock) || (footBlock instanceof DoublePlantBlock)) {
			le.level().destroyBlock(vePos, true);
		}

		int absva = Math.abs(va);
		if (absva == WALL_CENTER) {
			le.level().setBlock(vePos, gateBlockType, 3);
			return true;
		}
		if (le.level().setBlock(vePos, wallType, 3)) {
			return true;
		} else {
			Utility.debugMsg(1, le,
					"Building Wall Fail: SetBlockAndUpdate Time End = " + le.level().getGameTime());
			return false;
		}

	}

	private static BlockState helperSaplingState(ServerLevel serverLevel, BlockPos pos) {
		BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
		String biomeName = serverLevel.getBiome(pos).unwrapKey()
				.map(key -> key.identifier().getPath())
				.orElse("");

		if (biomeName.contains("birch")) {
			sapling = Blocks.BIRCH_SAPLING.defaultBlockState();
		}
		if (biomeName.contains("taiga")) {
			sapling = Blocks.SPRUCE_SAPLING.defaultBlockState();
		}
		if (biomeName.contains("jungle")) {
			sapling = Blocks.JUNGLE_SAPLING.defaultBlockState();
		}
		if (biomeName.contains("savanna")) {
			sapling = Blocks.ACACIA_SAPLING.defaultBlockState();
		}
		if (biomeName.contains("desert")) {
			sapling = Blocks.ACACIA_SAPLING.defaultBlockState();
		}
		return sapling;
	}

	private static void improvePowderedSnow(Entity entity) {
		Level sLevel = entity.level();
		if (entity.isInPowderSnow) {
			int hp = 0;
			if (sLevel.getBlockState(entity.blockPosition().above(2)).getBlock() == Blocks.POWDER_SNOW) {
				entity.level().destroyBlock(entity.blockPosition().above(2), false);
				hp = 2;
			}
			if (sLevel.getBlockState(entity.blockPosition().above()).getBlock() == Blocks.POWDER_SNOW) {
				entity.level().destroyBlock(entity.blockPosition().above(), false);
				hp += 2;
			}
			if (sLevel.getBlockState(entity.blockPosition()).getBlock() == Blocks.POWDER_SNOW) {
				BlockState SnowLayer = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2 + hp);
				entity.level().setBlockAndUpdate(entity.blockPosition(), SnowLayer);
			}
		}
	}

	private static boolean isBlockGrassOrDirt(BlockState tempBlockState) {

		if (isKindOfGrassBlock(tempBlockState) || (tempBlockState.getBlock() == Blocks.DIRT)) {
			return true;
		}
		return false;
	}


	private static boolean isFootBlockOkayToBuildIn(ServerLevel serverLevel) {
		if ((footBlockState.isAir()) || (isGrassOrFlower(footBlockState))) {
			return true;
		}
		if (footBlockState.getBlock() instanceof SnowLayerBlock) {
			return true;
		}
		if (isNatProgPebbleOrStick(serverLevel))
			return true;

		return false;
	}

	private static boolean isGoodMushroomTemperature(Entity entity) {
		BlockPos ePos = getAdjustedBlockPos(entity);
		float biomeTemp = entity.level().getBiome(ePos).value().getBaseTemperature();
		Utility.debugMsg(1, ePos, "Mushroom Biome temp: " + biomeTemp + ".");
		if (biomeTemp < MyConfig.getMushroomMinTemp())
			return false;
		if (biomeTemp > MyConfig.getMushroomMaxTemp())
			return false;
		return true;
	}

	private static boolean isGrassOrFlower(BlockState footBlockState) {
		Block footBlock = footBlockState.getBlock();

		if (footBlock instanceof TallFlowerBlock) {
			return true;
		}
		if (footBlock instanceof FlowerBlock) {
			return true;
		}
		if (footBlock instanceof DoublePlantBlock) { // includes tall grass and ferns now
			return true;
		}
		// compatibility with other biome mods.
		if (footBlockState.is(BlockTags.FLOWERS)) {
			return true;
		}
		// biomes you'll go grass compatibility
		return footBlock.getDescriptionId().equals("block.byg.short_grass");
	}

	private static boolean isHorseTypeEatingNow(Entity entity) {
		if (entity instanceof AbstractHorse) {
			AbstractHorse h = (AbstractHorse) entity;
			if (h.isEating()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isImpossibleRegrowthEvent(String regrowthType) {
		if ((regrowthType.equals("eat")) && (footBlockState.isAir())) {
			return true;
		}
		if ((regrowthType.equals("grow")) && (footBlockState.getBlock() instanceof TallGrassBlock)) {
			return true;
		}
		if ((regrowthType.equals("grow")) && (footBlockState.getBlock() instanceof FlowerBlock)) {
			return true;
		}
		if ((regrowthType.equals("tall")) && (!(footBlockState.getBlock() instanceof TallGrassBlock))) {
			return true;
		}
		return false;
	}

	private static boolean isKindOfGrassBlock(BlockState groundBlockState) {
		if (groundBlockState.getBlock() instanceof GrassBlock)
			return true;
		if (groundBlockState.getBlock().getDescriptionId().equals("block.byg.meadow_grass_block"))
			return true;
		return false;
	}

	// handle natural progression pebbles and sticks
	private static boolean isNatProgPebbleOrStick(ServerLevel serverLevel) {
		String rl = Utility.getResourceLocationString(footBlock);
		return rl.contains("natprog") && (rl.contains("pebble") || rl.contains("twigs"));
	}


	// TODO water mod compatibility?
	// also ... Move this code back to forge.
	private static boolean isNearWater(Level level, BlockPos pos) {
		AABB box = AABB.encapsulatingFullBlocks(pos.east(4).north(4), pos.west(4).south(4));
		return level.containsAnyLiquid(box);
	}

	private static boolean isOkayToBuildWallHere(Villager ve) {

		boolean okayToBuildWalls = true;

		if (!(isOnGround(ve))) {
			okayToBuildWalls = false;
		}
		if (!(isFootBlockOkayToBuildIn((ServerLevel)ve.level()))) {
			okayToBuildWalls = false;
		}
		if (!(isValidGroundBlockToBuildWallOn(ve))) {
			okayToBuildWalls = false;
		}
		return okayToBuildWalls;
	}

	private static boolean isOnGround(Entity e) {
		return e.onGround();
	}


	private static WallBiomeDataManager.WallBiomeDataItem localWallBiomeData() {
		return WallBiomeDataManager.getWallBiomeDataItem("minecraft:" + biomeCategory.toLowerCase());
	}

	private static boolean isOutsideMeetingPlaceWall(Villager ve, BlockPos meetingPlacePos) {

		BlockPos vePos = getAdjustedBlockPos(ve);
		int wallDiameter = (localWallBiomeData().getWallDiameter() / 2) - 1;
		int absVMpX = (int) Math.abs(vePos.getX() - meetingPlacePos.getX());
		int absVMpZ = (int) Math.abs(vePos.getZ() - meetingPlacePos.getZ());
		if ((absVMpX > wallDiameter + 1))
			return true;
		if ((absVMpZ > wallDiameter + 1))
			return true;
		return false;

	}

	private static boolean isValidGroundBlockToBuildWallOn(LivingEntity le) {

		if (le.level().getBrightness(LightLayer.SKY, le.blockPosition()) < 13)
			return false;

		if (groundBlock instanceof SnowLayerBlock)
			return false;
		if (groundBlock instanceof TorchBlock)
			return false; // includes WallTorchBlock

		if (le.level().getBlockState(le.blockPosition().above()).getBlock() instanceof WallBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below()).getBlock() instanceof WallBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below(1)).getBlock() instanceof WallBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below(2)).getBlock() instanceof TorchBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().above()).getBlock() instanceof TorchBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below()).getBlock() instanceof TorchBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below(1)).getBlock() instanceof TorchBlock) {
			return false;
		}
		if (le.level().getBlockState(le.blockPosition().below(2)).getBlock() instanceof TorchBlock) {
			return false;
		}

		groundBlock = groundBlockState.getBlock();
		Utility.debugMsg(1, le,
				"Build Wall : gb" + groundBlock.toString() + ", fb:" + footBlock.toString());
		int debug = 3;
		WallFoundationDataManager.wallFoundationItem currentWallFoundationItem = WallFoundationDataManager
				.getWallFoundationInfo(Utility.getResourceLocationString(groundBlock));

		if (currentWallFoundationItem == null)
			return false;

		return true;

	}

	private static boolean isValidGroundBlockToPlaceTorchOn(Villager ve) {

 		String key = Utility.getResourceLocationString(groundBlockState);
		WallFoundationDataManager.wallFoundationItem currentWallFoundationItem = WallFoundationDataManager
				.getWallFoundationInfo(key);
		if (currentWallFoundationItem == null)
			return false;

		return true;

	}

	private static boolean isValidTorchLocation(int wallRadius, int wallTorchSpacing, int absvx, int absvz,
			Block wallFenceBlock) {

		boolean hasAWallUnderIt = false;
		if (wallFenceBlock instanceof WallBlock) {
			hasAWallUnderIt = true;
		}
		if (wallFenceBlock instanceof FenceBlock) {
			hasAWallUnderIt = true;
		}
		if (!(hasAWallUnderIt)) {
			return false;
		}
		if ((absvx == wallRadius) && ((absvz % wallTorchSpacing) == 1)) {
			return true;
		}
		if (((absvx % wallTorchSpacing) == 1) && (absvz == wallRadius)) {
			return true;
		}
		if ((absvx == wallRadius) && (absvz == wallRadius)) {
			return true;
		}

		return false;
	}

	// fix unfinished spots in road with 3-4 grass blocks orthogonal to them.
	// on slopes too.

	private static boolean mobEatGrassOrFlower(Entity entity, String regrowthType) {
		BlockPos ePos = getAdjustedBlockPos(entity);
		if (!(isGrassOrFlower(footBlockState))) {
			return false;
		}
		if (isKindOfGrassBlock(groundBlockState)) {
			mobTrodGrassBlock(entity);
		}
		entity.level().destroyBlock(ePos, false);
		LivingEntity le = (LivingEntity) entity;
		helperChildAgeEntity(entity);
		if (le.getMaxHealth() > le.getHealth() && (MyConfig.getEatingHeals())) {
			MobEffectInstance ei = new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0, false, true);
			le.addEffect(ei);
		}
		return true;
	}


	private static boolean mobEatPlantsAction(LivingEntity entity, String key, String regrowthType) {
		if (mobEatGrassOrFlower(entity, regrowthType)) {
			Utility.debugMsg(2, getAdjustedBlockPos(entity), key + " ate plants.");
			return true;
		}
		return false;
	}

	private static boolean mobGrowCoralAction(LivingEntity le, String key) {

		Level level = le.level();
		int sealevel = level.getSeaLevel();
		RandomSource rand = level.getRandom();

		// Block bwf = Blocks.BRAIN_CORAL_WALL_FAN;

		moveRand.setSeed(le.getBlockY() * 1151 + le.getBlockX() * 51 + le.getBlockZ() * 31); // "predictable" random.
		double docoralplant = moveRand.nextDouble();
		docoralplant = moveRand.nextDouble();
		double docoralfan = moveRand.nextDouble();
		int coralfanDirection = moveRand.nextInt(4);
		int minCoraldepth = sealevel - 4 + moveRand.nextInt(2);
		int maxCoraldepth = sealevel - 16;

		if (le.getBlockY() > minCoraldepth)
			return false;
		if (le.getBlockY() < maxCoraldepth)
			return false;

		BlockPos pos = le.blockPosition();

		// TODO might be coral or grass below?
		if (level.getBlockState(pos.below(0)).getBlock() != Blocks.WATER)
			return false; // should be impossible.
		if (level.getBlockState(pos.below(1)).getBlock() != Blocks.WATER)
			return false;
		if (level.getBlockState(pos.below(2)).getBlock() instanceof CoralBlock) {
			if (docoralfan < 0.3) {
				Direction d = Direction.from2DDataValue(coralfanDirection);
				BlockPos fanPos = le.blockPosition().below(2).relative(d);
				if (level.getBlockState(fanPos).getBlock() == Blocks.WATER) {
					level.setBlockAndUpdate(fanPos, coralfans[rand.nextInt(coralfans.length)].defaultBlockState()
							.setValue(CoralWallFanBlock.FACING, d));
				}
			}

			int count = helperCountCoral(le);
			Utility.debugMsg(2, le, "CORAL count = :" + count + ", " + Utility.getResourceLocationString(le) + " .");
			if (count > 5)
				return false;
			BlockState theCoralBlock = level.getBlockState(pos.below(2)); // grow same kind of coral block
			if ((count < 6) && (le.getBlockY() == minCoraldepth)) {
				if (docoralplant < 0.30)
					return false; 
				Utility.debugMsg(2, pos,
						"CORAL Plant grows over Coral Block:" + Utility.getResourceLocationString(le) + " .");
				level.setBlockAndUpdate(pos.below(1),
						coralPlants[rand.nextInt(coralPlants.length)].defaultBlockState());
				level.playSound(null, pos, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.AMBIENT, 0.9f, 1.4f);
				return true;
			} else if ((le.getBlockY() < minCoraldepth)) {
				int ew = rand.nextInt(3) - 1;
				int ns = rand.nextInt(3) - 1;
				if (level.getBlockState(pos.below(1).east(ew).north(ns)).getBlock() != Blocks.WATER)
					return false;
				Utility.debugMsg(2, pos,
						"CORAL Block grows over Coral Block:" + Utility.getResourceLocationString(le) + " .");
				level.setBlockAndUpdate(pos.below(1).east(ew).north(ns), theCoralBlock);
				level.playSound(null, pos, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.AMBIENT, 0.9f, 1.4f);
				Utility.debugMsg(2, pos, "CORAL:" + Utility.getResourceLocationString(le) + " new block set at near "
						+ pos.below(1) + " .");

			}

		}

		return true;
	}

	private static void mobGrowMushroomAction(LivingEntity le, String key) {
		ServerLevel sLevel = (ServerLevel) le.level();
		BlockPos ePos = le.blockPosition();
		if (sLevel.getBlockState(ePos).getBlock() instanceof MushroomBlock) {
			return;
		}
		if (sLevel.canSeeSky(ePos)) {
			return;
		}
		if (!(isGoodMushroomTemperature(le))) {
			return;
		}

		Random mushRand = new Random(helperLongRandomSeed(le.blockPosition()));

		double fertilityDouble = mushRand.nextDouble();
		fertilityDouble = mushRand.nextDouble();

		if (fertilityDouble < .75) {
			Utility.debugMsg(1, ePos, key + " Mushroom fertility (" + fertilityDouble + ") non-growing spot.");
			return;
		}

		int smallMushroomCount = helperCountBlocksBB(MushroomBlock.class, 4, sLevel, ePos, 4, 1);
		if (smallMushroomCount > 3) {
			Utility.debugMsg(1, ePos, key + " smallMushroomCount (" + smallMushroomCount + ") mushroom too crowded.");
			return;
		}

		int myceliumCount = helperCountBlocksBB(MyceliumBlock.class, 4, sLevel, ePos, 4, 1);
		if (myceliumCount > 2) {
			Utility.debugMsg(1, ePos, key + " myceliumCount (" + myceliumCount + ") mycelium too crowded.");
			return;
		}

		// dust the top of giant mushrooms with little mushrooms of the same type.

		if (groundBlock == Blocks.RED_MUSHROOM_BLOCK) {
			sLevel.setBlockAndUpdate(ePos, Blocks.RED_MUSHROOM.defaultBlockState());
			return;
		}

		if (groundBlock == Blocks.BROWN_MUSHROOM_BLOCK) {
			sLevel.setBlockAndUpdate(ePos, Blocks.BROWN_MUSHROOM.defaultBlockState());
			return;
		}

		// Right next to a huge mushroom, growth may continue past the density check.
		boolean touchesHugeMushroom = helperCountBlocksBB(HugeMushroomBlock.class, 1, sLevel, ePos, 1, 1) > 0;
		if (!touchesHugeMushroom
				&& helperCountBlocksBB(HugeMushroomBlock.class, 1, sLevel, ePos, MyConfig.getMushroomDensity(), 1) > 0) {
			Utility.debugMsg(1, ePos, key + " huge mushroom too crowded.");
			return;
		}

		if (!groundBlockState.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return;
		}
		boolean playerNearby = sLevel.hasNearbyAlivePlayer(ePos.getX(), ePos.getY(), ePos.getZ(), 12.0);

		if (!playerNearby) {

			double vx = le.position().x() - (ePos.getX() + 0.5d);
			double vz = le.position().z() - (ePos.getZ() + 0.5d);

			Vec3 vM = new Vec3(vx, 0.0d, vz).normalize().scale(1.0d).add(0, 0.5, 0);
			le.setDeltaMovement(le.getDeltaMovement().add(vM));
			if (fertilityDouble > 0.9) {
				sLevel.setBlockAndUpdate(ePos.below(), Blocks.MYCELIUM.defaultBlockState());
			}

			Block theBlock = null;
			Block theCapBlock = null;

			if (sLevel.getRandom().nextDouble() * 100.0 > 75.0) {
				theBlock = Blocks.RED_MUSHROOM;
				theCapBlock = Blocks.RED_MUSHROOM_BLOCK;
			} else {
				theBlock = Blocks.BROWN_MUSHROOM;
				theCapBlock = Blocks.BROWN_MUSHROOM_BLOCK;
			}
			if (fertilityDouble > 0.9) {
				sLevel.setBlockAndUpdate(ePos.below(), Blocks.MYCELIUM.defaultBlockState());
			}
			sLevel.setBlockAndUpdate(ePos, theBlock.defaultBlockState());
			MushroomBlock mb = (MushroomBlock) theBlock;
			if (mushRand.nextDouble() < 0.03) {
				mobGrowSmallMushroom(sLevel, ePos, theCapBlock);
			} else {
				try {
					mb.growMushroom(sLevel, ePos, theBlock.defaultBlockState(), sLevel.getRandom());
				} catch (Exception e) {
					// technically an "impossible" error but it's happened so this should
					// bulletproof it.
				}

				// light the top stem inside the cap with glowshroom.
				if (theBlock == Blocks.RED_MUSHROOM) {
					for (int y = 9; y > 3; y--) {
						Block b = sLevel.getBlockState(ePos.above(y)).getBlock();
						if (b == Blocks.MUSHROOM_STEM) {
							sLevel.setBlockAndUpdate(ePos.above(y), Blocks.SHROOMLIGHT.defaultBlockState());
							break;
						}
					}
				}
			}

			Utility.debugMsg(1, le, key + " grow mushroom.");
		}

	}



	private static boolean mobGrowPlantsAction(LivingEntity le, String key) {

		if (footBlockState.isAir()) {
			if (!(groundBlock instanceof BonemealableBlock growable)) {
				return false;
			}
			BlockPos groundPos = getAdjustedBlockPos(le).below();
			Utility.debugMsg(1, le, key + " growable plant found.");
			try {
				ServerLevel serverLevel = (ServerLevel) le.level();
				performMobBonemeal(growable, serverLevel, groundPos, groundBlockState);
			} catch (Exception e) {
				Utility.debugMsg(1, le, key + " caught grow attempt exception.");
			}
		}
		return true;
	}

	// fix unfinished spots in road with 3-4 grass blocks orthogonal to them.
	// on slopes too.
	// TODO: Move this to forge!
	private static void mobGrowSmallMushroom(ServerLevel serverLevel, BlockPos ePos, Block theCapBlock) {
		// TODO: Set this back to 3 afterwards.
		if (serverLevel.getRandom().nextInt(6) < 3) {
			serverLevel.setBlockAndUpdate(ePos, Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(1), Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2), Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).east(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).west(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).north(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).south(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(3), theCapBlock.defaultBlockState());
		} else {
			serverLevel.setBlockAndUpdate(ePos, Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(1), Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2), Blocks.MUSHROOM_STEM.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).east(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).west(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).north(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).south(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).north().east(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).north().west(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).south().east(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(2).south().west(), theCapBlock.defaultBlockState());
			serverLevel.setBlockAndUpdate(ePos.above(3), theCapBlock.defaultBlockState());

		}

	}

	// fix unfinished spots in road with 3-4 grass blocks orthogonal to them.
	// on slopes too.  

	@SuppressWarnings("resource")
	private static boolean mobGrowTallAction(Entity ent, String key) {
		if ((footBlock instanceof TallGrassBlock) && (footBlock instanceof BonemealableBlock)) {
			BlockPos ePos = getAdjustedBlockPos(ent);
			// TODO test this.  BiomesYoullGo has some bad bonemealable blocks- prevent crash.
			if (!Utility.getResourceLocationString(footBlock).contains("byg")) {
				try {
					BonemealableBlock ib = (BonemealableBlock) footBlock;
					performMobBonemeal(ib, (ServerLevel) ent.level(), ePos, ent.level().getBlockState(ePos));
					Utility.debugMsg(2, ePos, key + " grew and hid in tall plant.");
					return true;  // could make this method void.

				} catch (Exception e) {
					Utility.debugMsg(1, ePos, key + " caught grow attempt exception.");
					return false; // could make this method void.
				}
			}
		}
		return false; // could make this method void.
	}

	private static void performMobBonemeal(BonemealableBlock block, ServerLevel level, BlockPos pos, BlockState state) {
		//? if >=26.3 {
		block.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.MOB);
		//?} else {
		/*block.performBonemeal(level, level.getRandom(), pos, state);
		*///?}
	}

	private static void mobHandleOverCrowding(LivingEntity le, String key) {
		BlockPos pos = BlockPos.containing(le.getX(), le.getY(), le.getZ());
		if (le instanceof Animal && le.level() instanceof ServerLevel serverLevel) {
			AABB box = AABB.encapsulatingFullBlocks(pos.east(3).above(2).north(3), pos.west(3).below(2).south(3));
			int excess = serverLevel.getEntities(le.getType(), box, (entity) -> true).size() - 16;

			if (excess > 16) {
				// playLocalSound is client-only; the server has to broadcast the sound.
				serverLevel.playSound(null, le.getX(), le.getY(), le.getZ(),
						SoundEvents.COW_SOUNDS.get(CowSoundVariants.SoundSet.CLASSIC).deathSound().value(),
						SoundSource.NEUTRAL, 1.1f, 0.54f);
				le.discard();
			} else if (excess > 0) {
				float hurt = excess + (serverLevel.getRandom().nextFloat() / 6);
				le.hurtServer(serverLevel, serverLevel.damageSources().inWall(), hurt);
			}
		}
	}


	private static void mobReforestAction(Entity entity, String key) {

		if (footBlock != Blocks.AIR)
			return;

		if (!(isBlockGrassOrDirt(groundBlockState)))
			return;

		BlockPos ePos = getAdjustedBlockPos(entity);
		// only try to plant saplings in about 1/4th of blocks.
		double sinY = Math.sin((double) ((ePos.getY() + 64) % 256) / 256);

		ServerLevel serverLevel = (ServerLevel) entity.level();
		
		if (serverLevel.getRandom().nextDouble() > Math.abs(sinY))
			return;

		BlockState sapling = helperSaplingState(serverLevel, ePos);

		// check if there is room for a new tree. Original trees.
		// don't plant a sapling near another sapling
		// TEST: The SaplingBlock
		int hval = 5;
		int yval = 0;
		int yrange = 0;

		if (helperCountBlocksBB(SaplingBlock.class, 1, serverLevel, ePos, hval, yrange) > 0)
			return;

		// check if there is room for a new tree.

		int leafCount = 0;
		yval = 4;
		yrange = 0;
		hval = 4;

		if (sapling == Blocks.ACACIA_SAPLING.defaultBlockState()) {
			yval = 5;
			hval = 7;
		}
		// TEST: The LeavesBlock
		leafCount = helperCountBlocksBB(LeavesBlock.class, 1, serverLevel, ePos.above(yval), hval, yrange);
		if (leafCount > 0)
			return;

		entity.level().setBlockAndUpdate(ePos, sapling);
		Utility.debugMsg(1, ePos, key + " planted sapling.");
	}




	private static void mobStumbleAction(Entity entity, String key) {
		entity.level().destroyBlock(getAdjustedBlockPos(entity), true);
		Utility.debugMsg(1, getAdjustedBlockPos(entity), key + " stumbled over torch.");
	}

	private static void mobTrodGrassBlock(Entity e) {

		BlockPos pos = BlockPos.containing(e.getX(), e.getY(), e.getZ());
		if (e.level() instanceof ServerLevel serverlevel) {
			AABB box = AABB.encapsulatingFullBlocks(pos.east(2).above(2).north(2), pos.west(2).below(2).south(2));
			List<?> entityList = serverlevel.getEntities(e.getType(), box, (entity) -> true);
			if (entityList.size() >= 9) {
				serverlevel.setBlockAndUpdate(pos.below(), Blocks.DIRT_PATH.defaultBlockState());
				e.hurtServer(serverlevel, serverlevel.damageSources().inWall(), 0.25f);
			}
		}

	}

	private static void vBeeKeeperFlowers(Villager ve) {
		if (!ProfessionRoleManager.hasRole(ve, VillageProfessionRole.PLANT_FLOWERS)) {
			return;
		}
		Utility.debugMsg(1, ve.blockPosition(), "Beekeeper checking on flowers here.");
		if ((ve.getBlockX() % 6 == 0) && (ve.getBlockZ() % 7 == 0) && footBlockState.isAir()) {
			if (isBlockGrassOrDirt(groundBlockState)) {
				if (helperCountBlocksOrthogonalBB(Blocks.DIRT_PATH, 1, ve.level(), ve.blockPosition().below(), 0) == 1) {
					BlockState flowerBlockState = Blocks.AZURE_BLUET.defaultBlockState();
					ve.level().setBlockAndUpdate(adjustedPos, flowerBlockState);
				}
			}
		}
	}

	private static void vClericalHealing(Villager ve) {

		if (!ProfessionRoleManager.hasRole(ve, VillageProfessionRole.HEAL_PEOPLE)) {
			return;
		}
		long daytime = ve.level().getOverworldClockTime() % 24000;

		if (daytime < 9000 || daytime > 11000) {
			return;
		}
		if (ve.level() instanceof ServerLevel varW) {
			int clericalLevel = ve.getVillagerData().level();

			BlockPos pos = BlockPos.containing(ve.getX(), (ve.getY() + 0.99d), (ve.getZ()));
			AABB box = AABB.encapsulatingFullBlocks(pos.east(4).above(2).north(4), pos.west(4).below(2).south(4));

			List<Entity> l = varW.getEntities((Entity) null, box, (entity) -> {
				if (entity instanceof Villager || entity instanceof Player) {
					return true;
				}
				return false;
			});

			// TODO check contents of list in test.

			for (Entity e : l) {
				boolean heal = true;
				LivingEntity le = (LivingEntity) e;
				if (le.getHealth() >= le.getMaxHealth()) {
					heal = false;
				}
				if (le.getEffect(MobEffects.REGENERATION) != null) {
					heal = false;
				}
				if (e instanceof Player pe) {
					int rep = ve.getPlayerReputation(pe);
					if (rep < 0) { // I was a bad bad boy.
						heal = false;
					}
				}
				if (heal) {
					le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, clericalLevel * 51, 0), ve);
					ve.level().playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 1.2f,
							1.52f);
					return;
				}

			}
		}
	}

	// if a grassblock in village has farmland next to it on the same level- retill
	// it.
	// todo add hydration check before tilling land.
	private static boolean vImproveFarm(Villager ve, String regrowthType) {
		if (!ProfessionRoleManager.hasRole(ve, VillageProfessionRole.FARM)) {
			return false;
		}

		BlockPos vePos = getAdjustedBlockPos(ve);
		Block groundBlock = groundBlockState.getBlock();
		Block footBlock = footBlockState.getBlock();

		if (helperCountBlocksOrthogonalBB(Blocks.FARMLAND, 1, ve.level(), vePos.below(1), 0) > 0) {
			if (isNearWater(ve.level(), vePos.below(1))) {
				if (groundBlock instanceof GrassBlock) {
					ve.level().setBlockAndUpdate(vePos.below(), Blocks.FARMLAND.defaultBlockState());
					return true;
				}
			}

			if (!regrowthType.contains("t") || (footBlock != Blocks.AIR)) {
				return false;
			}

			// Special farm lighting torches.
			if (ve.level().getBrightness(LightLayer.BLOCK, vePos) > 12) {
				return false; // block already bright enough
			}

			int veX = vePos.getX();
			int veY = vePos.getY();
			int veZ = vePos.getZ();

			if ((lastTorchX == veX) && (lastTorchY == veY) && (lastTorchZ == veZ)) {
				return false; // weak Anti torch-exploit
			}

			int waterValue = helperCountBlocksOrthogonalBB(Blocks.WATER, 1, ve.level(), vePos.below(), 0);
			boolean isFarmBorder = groundBlockState.is(BlockTags.LOGS) || groundBlock == Blocks.SMOOTH_SANDSTONE;
			if (waterValue > 0 && isFarmBorder) {
				ve.level().setBlock(vePos, Blocks.TORCH.defaultBlockState(), 3);
				lastTorchX = veX;
				lastTorchY = veY;
				lastTorchZ = veZ;
				return true;
			}
		}
		return false;
	}

	private static void vImproveFences(Villager ve, String key, String regrowthType) {
		Brain<Villager> vb = ve.getBrain();
		Optional<GlobalPos> vMeetingPlace = vb.getMemory(MemoryModuleType.MEETING_POINT);
		if (!(vMeetingPlace.isPresent())) {
			return;
		}

		if (isOkayToBuildWallHere(ve)) {
			GlobalPos gVMP = vMeetingPlace.get();
			BlockPos villageMeetingPlaceBlockPos = gVMP.pos();

			if (!(ve.level().getBlockState(villageMeetingPlaceBlockPos.above(1)).getBlock() instanceof WallBlock)) {
				return;
			}

			// build a wall on perimeter of villager's home
			if (regrowthType.contains("p")) {
				Optional<GlobalPos> villagerHome = vb.getMemory(MemoryModuleType.HOME);
				if (!(villagerHome.isPresent())) {
					return;
				}
				GlobalPos gVHP = villagerHome.get();
				BlockPos villagerHomePos = gVHP.pos();
				// don't build personal walls inside the village wall perimeter.
				// don't build personal walls until the village has a meeting place.
				if (isOutsideMeetingPlaceWall(ve, villageMeetingPlaceBlockPos)) {
					vImproveHomeFence(ve, villagerHomePos, regrowthType);
				}
			}
		}
	}

	// villagers build protective walls around their homes. currently 32 out.
	// to do- reduce distance of wall from home.
	private static boolean vImproveHomeFence(Villager ve, BlockPos vHomePos, String regrowthActions) {

		BlockPos vePos = getAdjustedBlockPos(ve);
		WallBiomeDataManager.WallBiomeDataItem currentWallBiomeDataItem = localWallBiomeData();

		int homeFenceDiameter = currentWallBiomeDataItem.getWallDiameter();
		homeFenceDiameter = homeFenceDiameter / 4; // resize for personal home fence.

		int wallTorchSpacing = homeFenceDiameter / 4;
		homeFenceDiameter = (homeFenceDiameter / 2) - 1;

		Collection<PoiRecord> result = ((ServerLevel) ve.level()).getPoiManager()
				.getInSquare(t -> t.is(PoiTypes.HOME), vePos, 17, Occupancy.ANY)
				.collect(Collectors.toCollection(ArrayList::new));

		// 08/30/20 Collection had bug with range that I couldn't resolve.
		boolean buildFence = true;
		if (!(result.isEmpty())) {
			Iterator<PoiRecord> i = result.iterator();
			while (i.hasNext()) { // in 16.1, finds the point of interest.
				PoiRecord P = i.next();
				if ((vHomePos.getX() == P.getPos().getX()) && (vHomePos.getY() == P.getPos().getY())
						&& (vHomePos.getZ() == P.getPos().getZ())) {
					continue; // ignore meeting place that owns this wall segment.
				} else {
					int disX = Math.abs(vePos.getX() - P.getPos().getX());
					int disZ = Math.abs(vePos.getZ() - P.getPos().getZ());
					Utility.debugMsg(1, P.getPos(), "extra Point of Interest Found.");
					if ((disX < homeFenceDiameter) && (disZ < homeFenceDiameter)) {
						buildFence = false; // another meeting place too close. cancel wall.
						break;
					}
				}
			}
		} else if ((result.isEmpty())) {
			buildFence = true;
		}

		if (buildFence) {

			BlockState fenceBlockState = currentWallBiomeDataItem.getFenceBlockState();

			if (helperPlaceOneWallPiece(ve, homeFenceDiameter, fenceBlockState, vHomePos)) {

				if (regrowthActions.contains("t")) {
					if (isValidTorchLocation(homeFenceDiameter, wallTorchSpacing, getAbsVX(ve, vHomePos),
							getAbsVZ(ve, vHomePos), ve.level().getBlockState(vePos).getBlock())) {
						ve.level().setBlockAndUpdate(vePos.above(), Blocks.TORCH.defaultBlockState());
					}
				}
				helperJumpAway(ve);
				return true;
			}
		}

		return false;
	}

	private static void vImproveLeaves(Villager ve, String key) {

		BlockPos vePos = getAdjustedBlockPos(ve);
		int[] facing = facingStep(ve);
		int dx = facing[0];
		int dz = facing[1];

		BlockPos tmpBP = null;
		BlockState tempBS = null;
		Block tempBlock = null;
		boolean destroyBlock = false;

		for (int iY = 0; iY < 2; iY++) {
			tmpBP = new BlockPos(vePos.getX() + dx, vePos.getY() + iY, vePos.getZ() + dz);
			tempBS = ve.level().getBlockState(tmpBP);
			tempBlock = tempBS.getBlock();
			if (tempBlock instanceof LeavesBlock) {
				boolean persistentLeaves = tempBS.getValue(LeavesBlock.PERSISTENT);
				if (!(persistentLeaves)) {
					destroyBlock = true;
				}
			}
			if ((tempBlock instanceof CactusBlock)) {
				destroyBlock = true;
			}
			if (destroyBlock) {
				ve.level().destroyBlock(tmpBP, false);
				destroyBlock = false;
				Utility.debugMsg(1, ve, key + " cleared " + tempBlock.getDescriptionId().toString());
			}
		}
	}

	private static boolean vImproveLighting(Villager ve) {
		BlockPos vePos = getAdjustedBlockPos(ve);

		int blockLightValue = ve.level().getBrightness(LightLayer.BLOCK, vePos);
		int skyLightValue = ve.level().getBrightness(LightLayer.SKY, vePos);

		if (blockLightValue > MyConfig.getTorchLightLevel())
			return false;
		if (skyLightValue > 11)
			return false;

		if (ve.isSleeping()) {
			return false;
		}
		if (footBlockState.getBlock() instanceof BedBlock) {
			return false;
		}

		boolean hasRoomForTorch = footBlockState.isAir() || isNatProgPebbleOrStick((ServerLevel) ve.level());
		if (!isValidGroundBlockToPlaceTorchOn(ve) || !hasRoomForTorch) {
			return false;
		}
		return ve.level().setBlock(vePos, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void vToolMasterHealing(Villager ve) {

		if (!ProfessionRoleManager.hasRole(ve, VillageProfessionRole.HEAL_GOLEMS)) {
			return;
		}
		long daytime = ve.level().getOverworldClockTime() % 24000;

		if (daytime < 9000 || daytime > 11000) {
			return;
		}

		if (ve.level() instanceof ServerLevel varW) {
			int villagerLevel = ve.getVillagerData().level();
			if (villagerLevel < 1)
				return;
			BlockPos pos = BlockPos.containing(ve.getX(), (ve.getY() + 0.99d), (ve.getZ()));
			AABB box = AABB.encapsulatingFullBlocks(pos.east(6).above(3).north(6), pos.west(6).below(2).south(6));

			List<IronGolem> l = varW.getEntities(EntityTypes.IRON_GOLEM, box, e -> true);

			for (IronGolem e : l) {
				boolean heal = true;

				if (e.getHealth() >= e.getMaxHealth()) {
					heal = false;
				}
				if (e.getEffect(MobEffects.REGENERATION) != null) {
					heal = false;
				}
				if (heal) {
					e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, villagerLevel * 51, 0), ve);
					ve.addEffect(new MobEffectInstance(MobEffects.REGENERATION, villagerLevel * 11, 0), ve);
					ve.level().playSound(null, pos, SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.5f,
							0.5f);
					return;
				}
			}
		}
	}

}
