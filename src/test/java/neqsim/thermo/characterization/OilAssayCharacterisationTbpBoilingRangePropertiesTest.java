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
 * Tests bounded density and yield receipts on refinery TBP cut tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpBoilingRangePropertiesTest {
  /** Tests exact whole-table and partial-overlap density bookkeeping. */
  @Test
  void reportsBoilingRangePropertiesAcrossPartialSourceCuts() {
    TbpCutTable table = sourceTable();

    TbpBoilingRangeProperties whole = table.getBoilingRangePropertiesKelvin(300.0, 700.0);
    assertEquals(100.0, whole.getLiquidVolumePercent(), 0.0);
    assertEquals(84.0, whole.getSpecificGravityWeightedLiquidVolumePercent(), 1.0e-12);
    assertEquals(0.84, whole.getAverageSpecificGravity(), 1.0e-12);
    assertEquals(141.5 / 0.84 - 131.5, whole.getApiGravity(), 1.0e-12);
    assertEquals(0.84 * 999.016, whole.getDensityKgPerCubicMetreAt60F(), 1.0e-12);

    TbpBoilingRangeProperties partial = table.getBoilingRangePropertiesKelvin(350.0, 550.0);
    assertEquals(67.5, partial.getLiquidVolumePercent(), 1.0e-12);
    assertEquals(54.5, partial.getSpecificGravityWeightedLiquidVolumePercent(), 1.0e-12);
    assertEquals(54.5 / 67.5, partial.getAverageSpecificGravity(), 1.0e-12);
    assertEquals(350.0, partial.getLowerBoilingPointKelvin(), 0.0);
    assertEquals(550.0, partial.getUpperBoilingPointKelvin(), 0.0);
  }

  /** Tests unit parity, additive closure, boundary snapping, and immutability. */
  @Test
  void preservesClosureAndSourceTableForEquivalentQueries() {
    TbpCutTable table = sourceTable();
    double[] sourceBoundaries = table.getBoilingPointKelvin();
    double[] sourceSpecificGravity = table.getSpecificGravity();

    TbpBoilingRangeProperties kelvin = table.getBoilingRangePropertiesKelvin(350.0, 600.0);
    TbpBoilingRangeProperties celsius = table.getBoilingRangePropertiesCelsius(76.85, 326.85);
    assertEquals(kelvin.getLiquidVolumePercent(), celsius.getLiquidVolumePercent(), 1.0e-12);
    assertEquals(kelvin.getAverageSpecificGravity(), celsius.getAverageSpecificGravity(), 1.0e-12);
    assertEquals(350.0, celsius.getLowerBoilingPointKelvin(), 1.0e-12);
    assertEquals(326.85, celsius.getUpperBoilingPointCelsius(), 1.0e-12);

    TbpBoilingRangeProperties light = table.getBoilingRangePropertiesKelvin(300.0, 400.0);
    TbpBoilingRangeProperties middle = table.getBoilingRangePropertiesKelvin(400.0, 500.0);
    TbpBoilingRangeProperties heavy = table.getBoilingRangePropertiesKelvin(500.0, 700.0);
    assertEquals(100.0,
        light.getLiquidVolumePercent() + middle.getLiquidVolumePercent() + heavy.getLiquidVolumePercent(), 1.0e-12);
    assertEquals(84.0,
        light.getSpecificGravityWeightedLiquidVolumePercent() + middle.getSpecificGravityWeightedLiquidVolumePercent()
            + heavy.getSpecificGravityWeightedLiquidVolumePercent(),
        1.0e-12);

    TbpBoilingRangeProperties snapped = table.getBoilingRangePropertiesKelvin(300.0 + 5.0e-9, 400.0 - 5.0e-9);
    assertEquals(300.0, snapped.getLowerBoilingPointKelvin(), 0.0);
    assertEquals(400.0, snapped.getUpperBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceBoundaries, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceSpecificGravity, table.getSpecificGravity(), 0.0);
  }

  /** Tests fail-closed validation for invalid or unbounded requests. */
  @Test
  void rejectsInvalidBoilingRangePropertyQueries() {
    TbpCutTable table = sourceTable();

    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(Double.NaN, 400.0));
    assertThrows(IllegalArgumentException.class,
        () -> table.getBoilingRangePropertiesKelvin(300.0, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(299.0, 400.0));
    assertThrows(IllegalArgumentException.class, () -> table.getBoilingRangePropertiesKelvin(300.0, 701.0));
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
