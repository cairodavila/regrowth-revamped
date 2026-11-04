package com.mactso.regrowth.village;

import java.util.List;
import java.util.Optional;

import com.mactso.regrowth.village.VillagePlan.BoundaryNode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class VillageSafety {

	private VillageSafety() {
	}

	public static Optional<BoundarySite> boundarySite(
			ServerLevel level,
			VillageProfile profile,
			BoundaryNode node) {
		if (!isColumnLoaded(level, node.position().x(), node.position().z())) {
			return Optional.empty();
		}
		BlockPos surface = surface(level, node.position().x(), node.position().z());
		for (int depth = 0; depth < 6; depth++) {
			BlockState surfaceState = level.getBlockState(surface);
			if (isBoundaryBlock(surfaceState)) {
				return Optional.of(new BoundarySite(surface.below(), surface, true));
			}
			if (!matchesAny(surfaceState, profile.walls().replaceable())) {
				break;
			}
			surface = surface.below();
		}

		BlockPos ground = surface;
		BlockPos buildPosition = ground.above();
		BlockState groundState = level.getBlockState(ground);
		BlockState buildState = level.getBlockState(buildPosition);
		if (isBoundaryBlock(buildState)) {
			return Optional.of(new BoundarySite(ground, buildPosition, true));
		}
		if (!matchesAny(groundState, profile.walls().foundations())) {
			return Optional.empty();
		}
		if (!buildState.isAir() && !matchesAny(buildState, profile.walls().replaceable())) {
			return Optional.empty();
		}
		if (isProtected(level, ground, profile.walls().protectedBlocks())
				|| isProtected(level, buildPosition, profile.walls().protectedBlocks())) {
			return Optional.empty();
		}
		return Optional.of(new BoundarySite(ground, buildPosition, false));
	}

	/** Height of a column's lowest boundary block, or where it would go when nothing is built yet. */
	public static Optional<Integer> wallBaseY(ServerLevel level, VillageProfile profile, BoundaryNode node) {
		return boundarySite(level, profile, node).map(site -> {
			BlockPos base = site.buildPosition();
			if (site.alreadyBuilt()) {
				while (isBoundaryBlock(level.getBlockState(base.below()))) {
					base = base.below();
				}
			}
			return base.getY();
		});
	}

	public static boolean crossesRoad(
			ServerLevel level,
			VillageProfile profile,
			BoundaryNode node) {
		int scanDistance = profile.boundary().gateScanDistance();
		for (int distance = -scanDistance; distance <= scanDistance; distance++) {
			int x = node.position().x() + node.outward().stepX() * distance;
			int z = node.position().z() + node.outward().stepZ() * distance;
			if (!isColumnLoaded(level, x, z)) {
				continue;
			}
			if (isRoad(level.getBlockState(surface(level, x, z)), profile)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isColumnLoaded(ServerLevel level, int x, int z) {
		return level.isLoaded(new BlockPos(x, level.getMinY(), z));
	}

	public static BlockPos surface(ServerLevel level, int x, int z) {
		int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return new BlockPos(x, y, z);
	}

	public static boolean isRoad(BlockState state, VillageProfile profile) {
		return matchesAny(state, profile.roads().blocks());
	}

	public static boolean canReplaceRoad(
			ServerLevel level,
			BlockPos position,
			VillageProfile profile) {
		BlockState state = level.getBlockState(position);
		return matchesAny(state, profile.roads().replaceable())
				&& !isProtected(level, position, profile.roads().protectedBlocks());
	}

	public static boolean matchesAny(BlockState state, List<ResourceSelector> selectors) {
		return selectors.stream().anyMatch(selector -> selector.matches(state));
	}

	private static boolean isProtected(
			ServerLevel level,
			BlockPos position,
			List<ResourceSelector> protectedBlocks) {
		return level.getBlockEntity(position) != null
				|| matchesAny(level.getBlockState(position), protectedBlocks);
	}

	private static boolean isBoundaryBlock(BlockState state) {
		return state.is(BlockTags.WALLS)
				|| state.is(BlockTags.FENCES)
				|| state.is(BlockTags.FENCE_GATES);
	}

	public record BoundarySite(BlockPos ground, BlockPos buildPosition, boolean alreadyBuilt) {
	}
}
