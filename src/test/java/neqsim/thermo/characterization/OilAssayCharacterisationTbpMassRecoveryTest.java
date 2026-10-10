package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpBoilingRangeProperties;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests ideal-additive-volume mass recovery on bounded refinery TBP tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpMassRecoveryTest {
  /** Tests complete-table denominator and exact bounded mass yields. */
  @Test
  void reportsWholeAndPartialMassRecovery() {
    TbpCutTable table = sourceTable();

    assertEquals(84.0, table.getTotalSpecificGravityWeightedLiquidVolumePercent(), 1.0e-12);
    assertEquals(100.0, table.getMassPercentBetweenBoilingPointsKelvin(300.0, 700.0), 1.0e-12);

    TbpBoilingRangeProperties partial = table.getBoilingRangePropertiesKelvin(350.0, 550.0);
    assertEquals(54.5, partial.getSpecificGravityWeightedLiquidVolumePercent(), 1.0e-12);
    assertEquals(100.0 * 54.5 / 84.0, partial.getMassPercent(), 1.0e-12);
    assertEquals(partial.getMassPercent(), table.getMassPercentBetweenBoilingPointsCelsius(76.85, 276.85), 1.0e-12);
  }

  /** Tests source-node values, partial-cut round trips, and unit parity. */
  @Test
  void roundTripsMassRecoveryAndCutPoints() {
    TbpCutTable table = sourceTable();

    assertEquals(100.0 * 14.0 / 84.0, table.getCumulativeMassPercentAtBoilingPointKelvin(400.0), 1.0e-12);
    assertEquals(100.0 * 54.0 / 84.0, table.getCumulativeMassPercentAtBoilingPointCelsius(226.85), 1.0e-12);
    assertEquals(470.0, table.getBoilingPointKelvinAtCumulativeMassPercent(50.0), 1.0e-12);
    assertEquals(196.85, table.getBoilingPointCelsiusAtCumulativeMassPercent(50.0), 1.0e-12);

    double[] boilingPointKelvin = {300.0, 350.0, 400.0, 470.0, 550.0, 700.0};
    for (double boilingPoint : boilingPointKelvin) {
      double massPercent = table.getCumulativeMassPercentAtBoilingPointKelvin(boilingPoint);
      assertEquals(boilingPoint, table.getBoilingPointKelvinAtCumulativeMassPercent(massPercent), 1.0e-10);
    }
  }

  /** Tests partition closure, boundary snapping, source immutability, and endpoint tolerance. */
  @Test
  void preservesMassClosureAndSourceTable() {
    TbpCutTable table = sourceTable();
    double[] sourceCumulativeVolume = table.getCumulativeVolumePercent();
    double[] sourceBoundaries = table.getBoilingPointKelvin();
    double[] sourceSpecificGravity = table.getSpecificGravity();

    double partitionedMassPercent = table.getMassPercentBetweenBoilingPointsKelvin(300.0, 400.0)
        + table.getMassPercentBetweenBoilingPointsKelvin(400.0, 500.0)
        + table.getMassPercentBetweenBoilingPointsKelvin(500.0, 700.0);
    assertEquals(100.0, partitionedMassPercent, 1.0e-12);

    double snappedMassPercent = table.getMassPercentBetweenBoilingPointsKelvin(300.0 + 5.0e-9, 400.0 - 5.0e-9);
    assertEquals(100.0 * 14.0 / 84.0, snappedMassPercent, 0.0);
    assertEquals(300.0, table.getBoilingPointKelvinAtCumulativeMassPercent(-5.0e-9), 0.0);
    assertEquals(700.0, table.getBoilingPointKelvinAtCumulativeMassPercent(100.0 + 5.0e-9), 0.0);
    assertArrayEquals(sourceCumulativeVolume, table.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(sourceBoundaries, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceSpecificGravity, table.getSpecificGravity(), 0.0);
  }

  /** Tests fail-closed validation for invalid mass-basis queries. */
  @Test
  void rejectsInvalidMassRecoveryQueries() {
    TbpCutTable table = sourceTable();

    assertThrows(IllegalArgumentException.class, () -> table.getCumulativeMassPercentAtBoilingPointKelvin(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> table.getCumulativeMassPercentAtBoilingPointKelvin(299.0));
    assertThrows(IllegalArgumentException.class, () -> table.getMassPercentBetweenBoilingPointsKelvin(500.0, 500.0));
    assertThrows(IllegalArgumentException.class, () -> table.getMassPercentBetweenBoilingPointsKelvin(600.0, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingPointKelvinAtCumulativeMassPercent(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingPointKelvinAtCumulativeMassPercent(-0.1));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingPointKelvinAtCumulativeMassPercent(100.1));
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }
}
