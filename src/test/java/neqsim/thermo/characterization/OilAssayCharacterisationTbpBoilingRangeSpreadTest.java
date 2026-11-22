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
 * Tests analytical second moments and spread descriptors on bounded refinery TBP tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpBoilingRangeSpreadTest {
  /** Tests whole-table and partial-overlap second moments and spread. */
  @Test
  void reportsAnalyticalBoilingPointSpread() {
    TbpCutTable table = sourceTable();

    TbpBoilingRangeProperties whole = table.getBoilingRangePropertiesKelvin(300.0, 700.0);
    double expectedSecondMoment = intervalSecondMoment(20.0, 300.0, 400.0) + intervalSecondMoment(50.0, 400.0, 500.0)
        + intervalSecondMoment(30.0, 500.0, 700.0);
    double expectedVariance = expectedSecondMoment / 100.0 - 475.0 * 475.0;
    assertEquals(expectedSecondMoment, whole.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent(), 1.0e-8);
    assertEquals(expectedVariance, whole.getBoilingPointVarianceKelvinSquared(), 1.0e-10);
    assertEquals(Math.sqrt(expectedVariance), whole.getBoilingPointStandardDeviationKelvin(), 1.0e-12);

    TbpBoilingRangeProperties partial = table.getBoilingRangePropertiesKelvin(350.0, 550.0);
    double expectedPartialSecondMoment = intervalSecondMoment(10.0, 350.0, 400.0)
        + intervalSecondMoment(50.0, 400.0, 500.0) + intervalSecondMoment(7.5, 500.0, 550.0);
    double expectedPartialVariance = expectedPartialSecondMoment / partial.getLiquidVolumePercent()
        - partial.getAverageBoilingPointKelvin() * partial.getAverageBoilingPointKelvin();
    assertEquals(expectedPartialSecondMoment, partial.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent(),
        1.0e-8);
    assertEquals(expectedPartialVariance, partial.getBoilingPointVarianceKelvinSquared(), 1.0e-10);
  }

  /** Tests additive closure, unit parity, boundary snapping, and source immutability. */
  @Test
  void preservesSecondMomentClosureAndSourceTable() {
    TbpCutTable table = sourceTable();
    double[] sourceBoundaries = table.getBoilingPointKelvin();
    double[] sourceSpecificGravity = table.getSpecificGravity();

    TbpBoilingRangeProperties kelvin = table.getBoilingRangePropertiesKelvin(350.0, 600.0);
    TbpBoilingRangeProperties celsius = table.getBoilingRangePropertiesCelsius(76.85, 326.85);
    assertEquals(kelvin.getBoilingPointVarianceKelvinSquared(), celsius.getBoilingPointVarianceKelvinSquared(),
        1.0e-12);
    assertEquals(kelvin.getBoilingPointStandardDeviationKelvin(), celsius.getBoilingPointStandardDeviationKelvin(),
        1.0e-12);

    TbpBoilingRangeProperties light = table.getBoilingRangePropertiesKelvin(300.0, 400.0);
    TbpBoilingRangeProperties middle = table.getBoilingRangePropertiesKelvin(400.0, 500.0);
    TbpBoilingRangeProperties heavy = table.getBoilingRangePropertiesKelvin(500.0, 700.0);
    double partitionedSecondMoment = light.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent()
        + middle.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent()
        + heavy.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent();
    double wholeSecondMoment = table.getBoilingRangePropertiesKelvin(300.0, 700.0)
        .getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent();
    assertEquals(wholeSecondMoment, partitionedSecondMoment, 1.0e-8);

    TbpBoilingRangeProperties snapped = table.getBoilingRangePropertiesKelvin(300.0 + 5.0e-9, 400.0 - 5.0e-9);
    assertEquals(intervalSecondMoment(20.0, 300.0, 400.0),
        snapped.getLiquidVolumeWeightedSquaredBoilingPointKelvinSquaredPercent(), 0.0);
    assertArrayEquals(sourceBoundaries, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceSpecificGravity, table.getSpecificGravity(), 0.0);
  }

  /** Tests that spread requests retain the bounded range-query validation contract. */
  @Test
  void rejectsInvalidSpreadRanges() {
    TbpCutTable table = sourceTable();

    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(Double.NaN, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(299.0, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(500.0, 500.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(600.0, 400.0));
  }

  private static double intervalSecondMoment(double liquidVolumePercent, double lowerKelvin, double upperKelvin) {
    return liquidVolumePercent * (lowerKelvin * lowerKelvin + lowerKelvin * upperKelvin + upperKelvin * upperKelvin)
        / 3.0;
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }
}
