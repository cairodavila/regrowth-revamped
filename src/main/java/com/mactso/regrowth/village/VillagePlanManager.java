package com.mactso.regrowth.village;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
	private static final VillagePlanCache<VillagePlan> CACHE = new VillagePlanCache<>();
	private static final VillagePlanCache<Village> VILLAGES = new VillagePlanCache<>();
	private static final long VILLAGE_CACHE_TICKS = VillageBoundarySettings.DEFAULT.cacheTicks();

	private VillagePlanManager() {
	}

	/** The plan of the village this bell belongs to, keyed by the village's representative bell. */
	public static VillageContext context(ServerLevel level, BlockPos bell) {
		Village village = village(level, bell);
		BlockPos meetingPoint = village.representative();
		VillageProfile profile = VillageProfileManager.select(level, meetingPoint);
		VillageControl control = VillageControlSavedData.get(level).control(village.bellKeys());
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

	/**
	 * The bells of the village this bell belongs to, representative first. Cached for every one of them, so
	 * villagers keep resolving their bell without a POI scan and all of them agree until it expires.
	 */
	public static Village village(ServerLevel level, BlockPos bell) {
		long gameTime = level.getGameTime();
		return VILLAGES.get(key(level, bell), gameTime).orElseGet(() -> {
			Village village = findVillage(level, bell.immutable());
			VILLAGES.put(key(level, bell), village, gameTime, VILLAGE_CACHE_TICKS);
			for (BlockPos member : village.bells()) {
				VILLAGES.put(key(level, member), village, gameTime, VILLAGE_CACHE_TICKS);
			}
			return village;
		});
	}

	public static void invalidate(ServerLevel level, BlockPos meetingPoint) {
		CACHE.invalidate(key(level, village(level, meetingPoint).representative()));
		VILLAGES.clear();
	}

	public static void clear() {
		CACHE.clear();
		VILLAGES.clear();
	}

	public static int cachedPlanCount() {
		return CACHE.size();
	}

	private static Village findVillage(ServerLevel level, BlockPos bell) {
		Map<GridPoint, BlockPos> positions = new HashMap<>();
		positions.put(gridPoint(bell), bell);
		List<GridPoint> bells = VillageBells.village(gridPoint(bell),
				around -> bellsAround(level, positions.get(around), positions));
		if (bells.size() <= 1) {
			BlockPos only = bells.isEmpty() ? bell : positions.get(bells.getFirst());
			return new Village(only, List.of(only));
		}
		List<GridPoint> pointsOfInterest = bells.stream()
				.flatMap(member -> villagePois(level, positions.get(member), VillageBells.SAME_VILLAGE_DISTANCE))
				.distinct()
				.toList();
		BlockPos representative = positions.get(VillageBells.representative(bells, pointsOfInterest));
		List<BlockPos> members = Stream.concat(Stream.of(representative),
				bells.stream().map(positions::get).filter(member -> !member.equals(representative))).toList();
		LOGGER.info("Village bells {} in {} share the plan of the bell at {}",
				members, level.dimension().identifier(), representative);
		return new Village(representative, members);
	}

	private static Collection<GridPoint> bellsAround(
			ServerLevel level,
			BlockPos bell,
			Map<GridPoint, BlockPos> positions) {
		return level.getPoiManager()
				.getInSquare(holder -> holder.is(PoiTypes.MEETING), bell, VillageBells.SAME_VILLAGE_DISTANCE,
						Occupancy.ANY)
				.map(record -> {
					BlockPos position = record.getPos().immutable();
					// Stacked bells share a column; the lowest one stands for it.
					positions.merge(gridPoint(position), position,
							(current, other) -> current.getY() <= other.getY() ? current : other);
					return gridPoint(position);
				})
				.toList();
	}

	private static Stream<GridPoint> villagePois(ServerLevel level, BlockPos center, int radius) {
		return level.getPoiManager()
				.getInSquare(holder -> holder.is(PoiTypeTags.VILLAGE), center, radius, Occupancy.ANY)
				.map(record -> gridPoint(record.getPos()));
	}

	private static GridPoint gridPoint(BlockPos position) {
		return new GridPoint(position.getX(), position.getZ());
	}

	private static VillagePlan createPlan(
			ServerLevel level,
			BlockPos meetingPoint,
			VillageProfile profile,
			VillageBoundarySettings boundary) {
		List<GridPoint> pointsOfInterest = villagePois(level, meetingPoint, boundary.scanRadius())
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

	public record Village(BlockPos representative, List<BlockPos> bells) {
		List<Long> bellKeys() {
			return bells.stream().map(BlockPos::asLong).toList();
		}
	}

	public record VillageContext(
			BlockPos meetingPoint,
			VillageProfile profile,
			VillagePlan plan,
			VillageControl control) {
	}
}
