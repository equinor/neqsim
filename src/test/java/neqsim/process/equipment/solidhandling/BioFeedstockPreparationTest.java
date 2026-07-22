package neqsim.process.equipment.solidhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.BioFeedstock;

/** Tests for {@link BioFeedstockPreparation}. */
class BioFeedstockPreparationTest {

  @Test
  void preparationClosesMassAndReportsEnergyAndVolumeEffects() {
    BioFeedstockPreparation preparation = new BioFeedstockPreparation("feed preparation");
    preparation.setFeedstock(BioFeedstock.library("crop_residue"), 1000.0);
    preparation.setTargetTotalSolidsFraction(0.90);
    preparation.setTargetDensityKgPerM3(650.0);
    preparation.setEnergyIntensities(15.0, 70.0, 0.75);
    preparation.run();

    assertEquals(1.0, preparation.getMassClosureFraction(), 1.0e-12);
    assertEquals(0.90, preparation.getPreparedFeedstock().getTotalSolidsFraction(), 1.0e-12);
    assertTrue(preparation.getWaterRemovedKgPerHr() > 0.0);
    assertTrue(preparation.getPowerKW() > 0.0);
    assertTrue((Double) preparation.getResults().get("preparedBulkVolume_m3PerHr") < (Double) preparation.getResults()
        .get("inletBulkVolume_m3PerHr"));
  }
}
