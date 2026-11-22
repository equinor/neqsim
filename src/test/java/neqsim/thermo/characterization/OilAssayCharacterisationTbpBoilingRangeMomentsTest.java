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
 * Tests bounded boiling-temperature moments and Watson descriptors on refinery TBP tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpBoilingRangeMomentsTest {
  /** Tests exact whole-table and partial-overlap temperature moments. */
  @Test
  void reportsLiquidVolumeWeightedBoilingMoments() {
    TbpCutTable table = sourceTable();

    TbpBoilingRangeProperties whole = table.getBoilingRangePropertiesKelvin(300.0, 700.0);
    assertEquals(47500.0, whole.getLiquidVolumeWeightedBoilingPointKelvinPercent(), 1.0e-12);
    assertEquals(475.0, whole.getAverageBoilingPointKelvin(), 1.0e-12);
    assertEquals(201.85, whole.getAverageBoilingPointCelsius(), 1.0e-12);
    assertEquals(Math.cbrt(1.8) * Math.cbrt(475.0) / 0.84, whole.getWatsonCharacterizationFactor(), 1.0e-12);

    TbpBoilingRangeProperties partial = table.getBoilingRangePropertiesKelvin(350.0, 550.0);
    assertEquals(30187.5, partial.getLiquidVolumeWeightedBoilingPointKelvinPercent(), 1.0e-12);
    assertEquals(30187.5 / 67.5, partial.getAverageBoilingPointKelvin(), 1.0e-12);
  }

  /** Tests unit parity, additive first-moment closure, boundary snapping, and immutability. */
  @Test
  void preservesMomentClosureAndSourceTable() {
    TbpCutTable table = sourceTable();
    double[] sourceBoundaries = table.getBoilingPointKelvin();
    double[] sourceSpecificGravity = table.getSpecificGravity();

    TbpBoilingRangeProperties kelvin = table.getBoilingRangePropertiesKelvin(350.0, 600.0);
    TbpBoilingRangeProperties celsius = table.getBoilingRangePropertiesCelsius(76.85, 326.85);
    assertEquals(kelvin.getAverageBoilingPointKelvin(), celsius.getAverageBoilingPointKelvin(), 1.0e-12);
    assertEquals(kelvin.getWatsonCharacterizationFactor(), celsius.getWatsonCharacterizationFactor(), 1.0e-12);

    TbpBoilingRangeProperties light = table.getBoilingRangePropertiesKelvin(300.0, 400.0);
    TbpBoilingRangeProperties middle = table.getBoilingRangePropertiesKelvin(400.0, 500.0);
    TbpBoilingRangeProperties heavy = table.getBoilingRangePropertiesKelvin(500.0, 700.0);
    double partitionedMoment = light.getLiquidVolumeWeightedBoilingPointKelvinPercent()
        + middle.getLiquidVolumeWeightedBoilingPointKelvinPercent()
        + heavy.getLiquidVolumeWeightedBoilingPointKelvinPercent();
    assertEquals(47500.0, partitionedMoment, 1.0e-12);

    TbpBoilingRangeProperties snapped = table.getBoilingRangePropertiesKelvin(300.0 + 5.0e-9, 400.0 - 5.0e-9);
    assertEquals(350.0, snapped.getAverageBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceBoundaries, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceSpecificGravity, table.getSpecificGravity(), 0.0);
  }

  /** Tests that moment requests retain the bounded range-query validation contract. */
  @Test
  void rejectsInvalidMomentRanges() {
    TbpCutTable table = sourceTable();

    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(Double.NaN, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(299.0, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(500.0, 500.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(600.0, 400.0));
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }
}
