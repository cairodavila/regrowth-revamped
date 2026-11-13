package com.mactso.regrowth.village;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mactso.regrowth.Main;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class VillageControlSavedData extends SavedData {

	private static final Codec<ControlEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("meeting_point").forGetter(ControlEntry::meetingPoint),
			Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ControlEntry::enabled),
			Codec.STRING.optionalFieldOf("mode", "default").forGetter(entry -> entry.mode().serializedName()))
			.apply(instance, (meetingPoint, enabled, mode) ->
					new ControlEntry(meetingPoint, enabled, VillageModeOverride.parse(mode))));
	private static final Codec<VillageControlSavedData> CODEC = ENTRY_CODEC.listOf().xmap(
			VillageControlSavedData::new,
			VillageControlSavedData::entries);
	private static final SavedDataType<VillageControlSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(Main.MOD_ID, "village_controls"),
			VillageControlSavedData::new,
			CODEC,
			null);

	private final Map<Long, VillageControl> controls = new HashMap<>();

	public VillageControlSavedData() {
	}

	private VillageControlSavedData(List<ControlEntry> entries) {
		for (ControlEntry entry : entries) {
			controls.put(entry.meetingPoint(), new VillageControl(entry.enabled(), entry.mode()));
		}
	}

	public static VillageControlSavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * The control of a village, given its bells with the representative first. An entry on another of its
	 * bells is still honoured: 1.1.0 kept one per bell, and commands could have written it to any of them.
	 */
	public VillageControl control(List<Long> bells) {
		return bells.stream()
				.map(controls::get)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(VillageControl.DEFAULT);
	}

	public void setEnabled(List<Long> bells, boolean enabled) {
		VillageControl current = control(bells);
		store(bells, new VillageControl(enabled, current.mode()));
	}

	public void setMode(List<Long> bells, VillageModeOverride mode) {
		VillageControl current = control(bells);
		store(bells, new VillageControl(current.enabled(), mode));
	}

	/** Keeps the village's control on its representative bell only. */
	private void store(List<Long> bells, VillageControl control) {
		bells.forEach(controls::remove);
		if (!control.equals(VillageControl.DEFAULT)) {
			controls.put(bells.getFirst(), control);
		}
		setDirty();
	}

	private List<ControlEntry> entries() {
		return controls.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new ControlEntry(entry.getKey(), entry.getValue().enabled(), entry.getValue().mode()))
				.toList();
	}

	public record VillageControl(boolean enabled, VillageModeOverride mode) {
		public static final VillageControl DEFAULT = new VillageControl(true, VillageModeOverride.DEFAULT);
	}

	private record ControlEntry(long meetingPoint, boolean enabled, VillageModeOverride mode) {
	}

	public enum VillageModeOverride {
		DEFAULT,
		ADAPTIVE,
		LEGACY;

		public static VillageModeOverride parse(String value) {
			if (value == null) {
				return DEFAULT;
			}
			return switch (value.trim().toLowerCase()) {
				case "adaptive", "poi_hull" -> ADAPTIVE;
				case "legacy", "fixed_square" -> LEGACY;
				default -> DEFAULT;
			};
		}

		public String serializedName() {
			return name().toLowerCase();
		}
	}
}
