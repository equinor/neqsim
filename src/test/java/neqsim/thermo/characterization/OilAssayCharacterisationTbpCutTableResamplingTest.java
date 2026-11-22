package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests conservative target-grid resampling of immutable refinery TBP cut tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpCutTableResamplingTest {
  /** Tests identity, refinement, coarsening, and mixed-grid volume and mass closure. */
  @Test
  void resamplesCompleteTargetGridsConservatively() {
    TbpCutTable source = sourceTable();

    TbpCutTable identity = source.resampleAtBoilingPointsKelvin(300.0, 400.0, 500.0, 700.0);
    assertArrayEquals(source.getCumulativeVolumePercent(), identity.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(source.getBoilingPointKelvin(), identity.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(source.getSpecificGravity(), identity.getSpecificGravity(), 0.0);

    TbpCutTable finer = source.resampleAtBoilingPointsKelvin(300.0, 350.0, 400.0, 450.0, 500.0, 600.0, 700.0);
    assertArrayEquals(new double[] {0.0, 10.0, 20.0, 45.0, 70.0, 85.0, 100.0}, finer.getCumulativeVolumePercent(),
        1.0e-12);
    assertArrayEquals(new double[] {0.70, 0.70, 0.80, 0.80, 1.00, 1.00}, finer.getSpecificGravity(), 1.0e-12);

    TbpCutTable coarser = source.resampleAtBoilingPointsKelvin(300.0, 500.0, 700.0);
    assertArrayEquals(new double[] {0.0, 70.0, 100.0}, coarser.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {54.0 / 70.0, 1.00}, coarser.getSpecificGravity(), 1.0e-12);

    TbpCutTable mixed = source.resampleAtBoilingPointsKelvin(300.0, 350.0, 500.0, 600.0, 700.0);
    assertArrayEquals(new double[] {0.0, 10.0, 70.0, 85.0, 100.0}, mixed.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {0.70, 47.0 / 60.0, 1.00, 1.00}, mixed.getSpecificGravity(), 1.0e-12);
    assertEquals(impliedMass(source), impliedMass(finer), 1.0e-12);
    assertEquals(impliedMass(source), impliedMass(coarser), 1.0e-12);
    assertEquals(impliedMass(source), impliedMass(mixed), 1.0e-12);
  }

  /** Tests Celsius parity, endpoint snapping, immutability, and fail-closed validation. */
  @Test
  void validatesCompleteTargetGridWithoutMutatingSource() {
    TbpCutTable source = sourceTable();
    double[] sourceBoundaries = source.getBoilingPointKelvin();
    double[] sourceYields = source.getCumulativeVolumePercent();
    double[] sourceSpecificGravity = source.getSpecificGravity();

    TbpCutTable celsius = source.resampleAtBoilingPointsCelsius(26.85, 126.85, 326.85, 426.85);
    assertArrayEquals(new double[] {300.0, 400.0, 600.0, 700.0}, celsius.getBoilingPointKelvin(), 1.0e-12);

    TbpCutTable snapped = source.resampleAtBoilingPointsKelvin(300.0 + 5.0e-9, 400.0, 700.0 - 5.0e-9);
    assertArrayEquals(new double[] {300.0, 400.0, 700.0}, snapped.getBoilingPointKelvin(), 0.0);

    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin((double[]) null));
    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin());
    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin(300.0));
    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin(300.0, Double.NaN, 700.0));
    assertThrows(IllegalArgumentException.class,
        () -> source.resampleAtBoilingPointsKelvin(300.0, 500.0, 400.0, 700.0));
    assertThrows(IllegalArgumentException.class,
        () -> source.resampleAtBoilingPointsKelvin(300.0, 400.0, 400.0, 700.0));
    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin(310.0, 700.0));
    assertThrows(IllegalArgumentException.class, () -> source.resampleAtBoilingPointsKelvin(300.0, 710.0));

    double[] mutableCopy = snapped.getSpecificGravity();
    mutableCopy[0] = 9.0;
    assertEquals(0.70, snapped.getSpecificGravity()[0], 0.0);
    assertArrayEquals(sourceBoundaries, source.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceYields, source.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(sourceSpecificGravity, source.getSpecificGravity(), 0.0);
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }

  private static double impliedMass(TbpCutTable table) {
    double[] cumulativeVolumePercent = table.getCumulativeVolumePercent();
    double[] specificGravity = table.getSpecificGravity();
    double impliedMass = 0.0;
    for (int i = 0; i < specificGravity.length; i++) {
      impliedMass += (cumulativeVolumePercent[i + 1] - cumulativeVolumePercent[i]) * specificGravity[i];
    }
    return impliedMass;
  }
}
