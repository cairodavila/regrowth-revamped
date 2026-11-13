package com.mactso.regrowth.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mactso.regrowth.village.LampPost.Piece;
import com.mactso.regrowth.village.VillagePlan.CardinalDirection;
import com.mactso.regrowth.village.VillagePlanManager.VillageContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Villagers put up lamp posts beside village paths where it's dark, one whole post per action. */
public final class VillageLamps {

	// Plants and snow on the ground are looked through to find the ground itself, like the wall builder does.
	private static final int MAXIMUM_PLANT_DEPTH = 3;

	private VillageLamps() {
	}

	/** Puts up a post on one of the villager's four neighboring columns, if one of them qualifies. */
	public static boolean buildLampPost(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos standing = villager.blockPosition();
		VillageContext village = null;
		for (CardinalDirection side : LampPost.candidateSides(level.getRandom().nextInt(4))) {
			int x = standing.getX() + side.stepX();
			int z = standing.getZ() + side.stepZ();
			if (!VillageSafety.isColumnLoaded(level, x, z)) {
				continue;
			}
			BlockPos above = VillageSafety.surface(level, x, z).above();
			// Cheap checks first: most spots near a villager are lit or covered.
			if (level.getBrightness(LightLayer.BLOCK, above) > LampPost.MAXIMUM_BLOCK_LIGHT || !level.canSeeSky(above)) {
				continue;
			}
			if (village == null) {
				Optional<VillageContext> context = VillageService.contextFor(villager);
				if (context.isEmpty()
						|| !context.get().control().enabled()
						|| context.get().profile().lampPost() == LampPost.NONE) {
					return false;
				}
				village = context.get();
			}
			Optional<Post> post = postAt(level, village, x, z, LampPost.opposite(side));
			if (post.isPresent()) {
				place(level, village.profile(), post.get());
				return true;
			}
		}
		return false;
	}

	private static Optional<Post> postAt(
			ServerLevel level,
			VillageContext village,
			int x,
			int z,
			CardinalDirection villagerSide) {
		VillageProfile profile = village.profile();
		GridPoint column = new GridPoint(x, z);
		if (!village.plan().contains(column)) {
			return Optional.empty();
		}
		BlockPos ground = naturalGround(level, profile, x, z);
		BlockState groundState = level.getBlockState(ground);
		if (VillageSafety.isRoad(groundState, profile)
				|| !VillageSafety.matchesAny(groundState, profile.walls().foundations())) {
			return Optional.empty();
		}
		List<CardinalDirection> roadSides = roadSides(level, profile, ground);
		if (roadSides.isEmpty()) {
			return Optional.empty();
		}
		BlockPos base = ground.above();
		if (level.getPoiManager().getInSquare(holder -> holder.is(PoiTypeTags.VILLAGE), base, 2, Occupancy.ANY)
				.findAny().isPresent()) {
			return Optional.empty();
		}
		LampPost design = profile.lampPost();
		List<CardinalDirection> arms = design.hasArm()
				? LampPost.armDirections(roadSides, villagerSide)
				: List.of(CardinalDirection.NORTH);
		for (CardinalDirection arm : arms) {
			List<Piece> pieces = design.pieces(arm);
			if (fits(level, village, base, pieces)) {
				return Optional.of(new Post(base, design, pieces));
			}
		}
		return Optional.empty();
	}

	/** Every block of the post goes into open-sky air or plants, clear of the wall line, doors and beds. */
	private static boolean fits(ServerLevel level, VillageContext village, BlockPos base, List<Piece> pieces) {
		List<GridPoint> columns = new ArrayList<>();
		for (Piece piece : pieces) {
			BlockPos position = base.offset(piece.x(), piece.y(), piece.z());
			BlockState state = level.getBlockState(position);
			if (!VillageSafety.isAirOrReplaceable(state, village.profile())) {
				return false;
			}
			if (!level.canSeeSky(position)) {
				return false;
			}
			GridPoint column = new GridPoint(position.getX(), position.getZ());
			if (!columns.contains(column)) {
				columns.add(column);
			}
		}
		for (GridPoint column : columns) {
			if (!village.plan().boundaryWithin(column, 2).isEmpty()
					|| nearDoorOrBed(level, column, base.getY() - 1, base.getY() + 3)) {
				return false;
			}
		}
		return true;
	}

	private static boolean nearDoorOrBed(ServerLevel level, GridPoint column, int minimumY, int maximumY) {
		for (int x = column.x() - 1; x <= column.x() + 1; x++) {
			for (int z = column.z() - 1; z <= column.z() + 1; z++) {
				for (int y = minimumY; y <= maximumY; y++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));
					if (state.is(BlockTags.DOORS) || state.is(BlockTags.BEDS)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static List<CardinalDirection> roadSides(ServerLevel level, VillageProfile profile, BlockPos ground) {
		List<CardinalDirection> sides = new ArrayList<>();
		for (CardinalDirection side : CardinalDirection.values()) {
			BlockPos neighbor = VillageSafety.surface(level, ground.getX() + side.stepX(), ground.getZ() + side.stepZ());
			if (Math.abs(neighbor.getY() - ground.getY()) <= 1
					&& VillageSafety.isRoad(level.getBlockState(neighbor), profile)) {
				sides.add(side);
			}
		}
		return sides;
	}

	/** The surface under any plants or snow on it. */
	private static BlockPos naturalGround(ServerLevel level, VillageProfile profile, int x, int z) {
		BlockPos ground = VillageSafety.surface(level, x, z);
		for (int depth = 0; depth < MAXIMUM_PLANT_DEPTH
				&& VillageSafety.matchesAny(level.getBlockState(ground), profile.walls().replaceable()); depth++) {
			ground = ground.below();
		}
		return ground;
	}

	private static void place(ServerLevel level, VillageProfile profile, Post post) {
		List<BlockPos> fences = new ArrayList<>();
		for (Piece piece : post.pieces()) {
			BlockPos position = post.base().offset(piece.x(), piece.y(), piece.z());
			BlockState state = state(post.design(), profile, piece);
			level.setBlock(position, state, Block.UPDATE_ALL);
			if (piece.part() == LampPost.Part.FENCE) {
				fences.add(position);
			}
		}
		// A fence placed in its default state isn't connected; once the whole post stands, connect each one the way
		// it would be if a player had placed it, which joins the snowy arm and the post.
		for (BlockPos fence : fences) {
			BlockState current = level.getBlockState(fence);
			BlockState connected = Block.updateFromNeighbourShapes(current, level, fence);
			if (connected != current) {
				level.setBlock(fence, connected, Block.UPDATE_ALL);
			}
		}
	}

	private static BlockState state(LampPost design, VillageProfile profile, Piece piece) {
		return switch (piece.part()) {
			case FENCE -> switch (design) {
				case PLAINS -> Blocks.OAK_FENCE.defaultBlockState();
				case SNOWY -> Blocks.SPRUCE_FENCE.defaultBlockState();
				default -> profile.walls().fenceBlock().orElse(Blocks.OAK_FENCE).defaultBlockState();
			};
			case STRIPPED_OAK_WOOD -> Blocks.STRIPPED_OAK_WOOD.defaultBlockState()
					.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
			case WALL_TORCH -> Blocks.WALL_TORCH.defaultBlockState()
					.setValue(WallTorchBlock.FACING, Direction.getApproximateNearest(piece.x(), 0.0, piece.z()));
			case TORCH -> Blocks.TORCH.defaultBlockState();
			case CUT_SANDSTONE -> Blocks.CUT_SANDSTONE.defaultBlockState();
			case TERRACOTTA -> Blocks.TERRACOTTA.defaultBlockState();
			case HANGING_LANTERN -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		};
	}

	private record Post(BlockPos base, LampPost design, List<Piece> pieces) {
	}
}
