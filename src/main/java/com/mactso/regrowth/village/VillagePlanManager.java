package com.mactso.regrowth.village;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mactso.regrowth.village.VillageControlSavedData.VillageControl;
import com.mactso.regrowth.village.VillageControlSavedData.VillageModeOverride;
import com.mactso.regrowth.village.VillagePlanCache.VillageKey;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public final class VillagePlanManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final VillagePlanCache CACHE = new VillagePlanCache();

	private VillagePlanManager() {
	}

	public static VillageContext context(ServerLevel level, BlockPos meetingPoint) {
		VillageProfile profile = VillageProfileManager.select(level, meetingPoint);
		VillageControl control = VillageControlSavedData.get(level).control(meetingPoint.asLong());
		VillageBoundarySettings boundary = withModeOverride(profile.boundary(), control.mode());
		VillageKey key = key(level, meetingPoint);
		VillagePlan plan = CACHE.get(key, level.getGameTime()).orElseGet(() -> {
			VillagePlan createdPlan = createPlan(level, meetingPoint, profile, boundary);
			CACHE.put(key, createdPlan, level.getGameTime(), boundary.cacheTicks());
			return createdPlan;
		});
		return new VillageContext(meetingPoint.immutable(), profile, plan, control);
	}

	public static Optional<BlockPos> nearestMeeting(ServerLevel level, BlockPos position, int radius) {
		return level.getPoiManager().findClosest(
				holder -> holder.is(PoiTypes.MEETING),
				position,
				radius,
				Occupancy.ANY);
	}

	public static void invalidate(ServerLevel level, BlockPos meetingPoint) {
		CACHE.invalidate(key(level, meetingPoint));
	}

	public static void clear() {
		CACHE.clear();
	}

	public static int cachedPlanCount() {
		return CACHE.size();
	}

	private static VillagePlan createPlan(
			ServerLevel level,
			BlockPos meetingPoint,
			VillageProfile profile,
			VillageBoundarySettings boundary) {
		List<GridPoint> pointsOfInterest = level.getPoiManager()
				.getInSquare(holder -> holder.is(PoiTypeTags.VILLAGE), meetingPoint,
						boundary.scanRadius(), Occupancy.ANY)
				.map(record -> new GridPoint(record.getPos().getX(), record.getPos().getZ()))
				.distinct()
				.toList();
		VillagePlan walls = VillageGeometryPlanner.create(
				new GridPoint(meetingPoint.getX(), meetingPoint.getZ()),
				pointsOfInterest,
				boundary);
		VillagePlan plan = VillageGeometryPlanner.markRoadGates(
				walls,
				node -> VillageSafety.crossesRoad(level, profile, node));
		LOGGER.info("Built {} village plan at {} in {}: profile={}, POIs={}, boundary={}, gates={}",
				plan.mode().name().toLowerCase(), meetingPoint, level.dimension().identifier(), profile.id(),
				plan.pointOfInterestCount(), plan.boundary().size(), plan.gateCount());
		return plan;
	}

	private static VillageBoundarySettings withModeOverride(
			VillageBoundarySettings settings,
			VillageModeOverride override) {
		VillageBoundaryMode mode = switch (override) {
			case DEFAULT -> settings.mode();
			case ADAPTIVE -> VillageBoundaryMode.ADAPTIVE;
			case LEGACY -> VillageBoundaryMode.LEGACY;
		};
		return new VillageBoundarySettings(
				mode,
				settings.margin(),
				settings.scanRadius(),
				settings.minimumRadius(),
				settings.maximumRadius(),
				settings.legacyRadius(),
				settings.gateScanDistance(),
				settings.cacheTicks());
	}

	private static VillageKey key(ServerLevel level, BlockPos meetingPoint) {
		return new VillageKey(level.dimension().identifier().toString(), meetingPoint.asLong());
	}

	public record VillageContext(
			BlockPos meetingPoint,
			VillageProfile profile,
			VillagePlan plan,
			VillageControl control) {
	}
}
