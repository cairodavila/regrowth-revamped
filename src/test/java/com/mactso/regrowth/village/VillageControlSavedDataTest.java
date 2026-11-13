package com.mactso.regrowth.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mactso.regrowth.village.VillageControlSavedData.VillageControl;
import com.mactso.regrowth.village.VillageControlSavedData.VillageModeOverride;

class VillageControlSavedDataTest {

	private static final long REPRESENTATIVE = 1L;
	private static final long OTHER_BELL = 2L;

	@Test
	void controlLeftOnAnotherBellStillCountsUntilTheVillageIsChanged() {
		VillageControlSavedData data = new VillageControlSavedData();
		data.setEnabled(List.of(OTHER_BELL), false);
		List<Long> village = List.of(REPRESENTATIVE, OTHER_BELL);

		assertEquals(new VillageControl(false, VillageModeOverride.DEFAULT), data.control(village));

		data.setEnabled(village, true);

		assertEquals(VillageControl.DEFAULT, data.control(village));
		assertEquals(VillageControl.DEFAULT, data.control(List.of(OTHER_BELL)));
	}

	@Test
	void theRepresentativeBellsControlWins() {
		VillageControlSavedData data = new VillageControlSavedData();
		data.setMode(List.of(OTHER_BELL), VillageModeOverride.LEGACY);
		data.setMode(List.of(REPRESENTATIVE), VillageModeOverride.ADAPTIVE);

		assertEquals(VillageModeOverride.ADAPTIVE, data.control(List.of(REPRESENTATIVE, OTHER_BELL)).mode());
	}
}
