package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests bounded forward, inverse, and interval-yield queries on refinery TBP cut tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpCurveQueryTest {
  /** Tests exact nodes, intrainterval interpolation, inverse round trips, and unit parity. */
  @Test
  void queriesForwardAndInversePiecewiseLinearCurve() {
    TbpCutTable table = sourceTable();

    assertEquals(0.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(300.0), 0.0);
    assertEquals(20.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(400.0), 0.0);
    assertEquals(100.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(700.0), 0.0);
    assertEquals(10.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(350.0), 1.0e-12);
    assertEquals(85.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(600.0), 1.0e-12);

    assertEquals(300.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(0.0), 0.0);
    assertEquals(400.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(20.0), 0.0);
    assertEquals(700.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(100.0), 0.0);
    assertEquals(350.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(10.0), 1.0e-12);
    assertEquals(600.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(85.0), 1.0e-12);

    for (double boilingPointKelvin : new double[] {325.0, 450.0, 625.0}) {
      double recovery = table.getCumulativeVolumePercentAtBoilingPointKelvin(boilingPointKelvin);
      assertEquals(boilingPointKelvin, table.getBoilingPointKelvinAtCumulativeVolumePercent(recovery), 1.0e-12);
    }

    assertEquals(table.getCumulativeVolumePercentAtBoilingPointKelvin(450.0),
        table.getCumulativeVolumePercentAtBoilingPointCelsius(176.85), 1.0e-12);
    assertEquals(table.getBoilingPointKelvinAtCumulativeVolumePercent(45.0) - 273.15,
        table.getBoilingPointCelsiusAtCumulativeVolumePercent(45.0), 1.0e-12);
  }

  /** Tests interval closure, boundary snapping, immutability, and fail-closed validation. */
  @Test
  void queriesBoundedIntervalYieldsWithoutMutatingSource() {
    TbpCutTable table = sourceTable();
    double[] sourceBoundaries = table.getBoilingPointKelvin();
    double[] sourceRecoveries = table.getCumulativeVolumePercent();

    assertEquals(75.0, table.getLiquidVolumePercentBetweenBoilingPointsKelvin(350.0, 600.0), 1.0e-12);
    assertEquals(75.0, table.getLiquidVolumePercentBetweenBoilingPointsCelsius(76.85, 326.85), 1.0e-12);

    double partitionedYield = table.getLiquidVolumePercentBetweenBoilingPointsKelvin(300.0, 400.0)
        + table.getLiquidVolumePercentBetweenBoilingPointsKelvin(400.0, 500.0)
        + table.getLiquidVolumePercentBetweenBoilingPointsKelvin(500.0, 700.0);
    assertEquals(100.0, partitionedYield, 1.0e-12);

    assertEquals(20.0, table.getCumulativeVolumePercentAtBoilingPointKelvin(400.0 + 5.0e-9), 0.0);
    assertEquals(400.0, table.getBoilingPointKelvinAtCumulativeVolumePercent(20.0 + 5.0e-9), 0.0);

    assertThrows(IllegalArgumentException.class,
        () -> table.getCumulativeVolumePercentAtBoilingPointKelvin(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> table.getCumulativeVolumePercentAtBoilingPointKelvin(299.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getCumulativeVolumePercentAtBoilingPointKelvin(701.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getBoilingPointKelvinAtCumulativeVolumePercent(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class,
        () -> table.getBoilingPointKelvinAtCumulativeVolumePercent(-1.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getBoilingPointKelvinAtCumulativeVolumePercent(101.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getLiquidVolumePercentBetweenBoilingPointsKelvin(500.0, 500.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getLiquidVolumePercentBetweenBoilingPointsKelvin(600.0, 400.0));

    assertArrayEquals(sourceBoundaries, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceRecoveries, table.getCumulativeVolumePercent(), 0.0);
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }
}
