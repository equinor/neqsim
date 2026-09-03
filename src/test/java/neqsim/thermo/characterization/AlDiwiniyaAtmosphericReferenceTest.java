package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.AlDiwiniyaAtmosphericReference.ProductReference;

/** Tests the public Al-Diwiniya atmospheric operating reference. */
public class AlDiwiniyaAtmosphericReferenceTest {
  @Test
  public void partialTbpCurvePreservesAllPublishedCoordinates() {
    double[] expectedVolumePercent = { 2.0, 3.5, 5.0, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0,
        50.0, 55.0, 60.0 };
    double[] expectedCelsius = { 40.0, 52.0, 62.0, 77.0, 95.0, 112.0, 128.0, 143.0, 159.0, 189.0, 218.0, 249.0, 279.0,
        310.0, 342.0, 373.0, 405.0 };

    assertArrayEquals(expectedVolumePercent, AlDiwiniyaAtmosphericReference.getTbpCumulativeVolumePercent(), 0.0);
    assertArrayEquals(expectedCelsius, AlDiwiniyaAtmosphericReference.getTbpTemperatureCelsius(), 0.0);
    assertEquals(313.15, AlDiwiniyaAtmosphericReference.getTbpTemperatureKelvin()[0], 1.0e-12);
    assertEquals(678.15, AlDiwiniyaAtmosphericReference.getTbpTemperatureKelvin()[16], 1.0e-12);
    assertFalse(AlDiwiniyaAtmosphericReference.hasCompleteTbpCurve());
    assertFalse(AlDiwiniyaAtmosphericReference.isIndependentYieldValidationCase());
    assertTrue(AlDiwiniyaAtmosphericReference.areHysysProductRatesImposedSpecifications());

    assertStrictlyIncreasing(expectedVolumePercent);
    assertStrictlyIncreasing(expectedCelsius);
  }

  @Test
  public void returnedArraysCannotMutateTheFrozenReference() {
    double[] volumes = AlDiwiniyaAtmosphericReference.getTbpCumulativeVolumePercent();
    volumes[0] = 0.0;
    assertEquals(2.0, AlDiwiniyaAtmosphericReference.getTbpCumulativeVolumePercent()[0], 0.0);

    ProductReference[] products = AlDiwiniyaAtmosphericReference.getProducts();
    products[0] = null;
    assertEquals("Light Naphtha", AlDiwiniyaAtmosphericReference.getProducts()[0].getName());

    int[] trays = AlDiwiniyaAtmosphericReference.getHeavyNaphthaDrawTrays();
    trays[0] = 1;
    assertArrayEquals(new int[] { 24, 22 }, AlDiwiniyaAtmosphericReference.getHeavyNaphthaDrawTrays());
  }

  @Test
  public void productRowsReproducePublishedRatesTemperaturesAndErrors() {
    ProductReference[] products = AlDiwiniyaAtmosphericReference.getProducts();
    assertEquals(6, products.length);

    assertProduct(products[0], "Light Naphtha", 8.0, 8.0, 110.0, 109.0, 0.0, 0.9090909090909091);
    assertProduct(products[1], "Total Heavy Naphtha", 2.0, 2.0, 135.0, 140.0, 0.0, -3.7037037037037037);
    assertProduct(products[2], "Kerosene", 4.0, 4.0, 180.0, 190.0, 0.0, -5.555555555555555);
    assertProduct(products[3], "Gasoil", 10.0, 10.0, 240.0, 235.0, 0.0, 2.0833333333333335);
    assertProduct(products[4], "Atmospheric residue", 41.0, 41.25, 295.0, 295.0, -0.6097560975609756, 0.0);
    assertProduct(products[5], "Off gas", 1.0, 0.75, 60.0, 65.0, 25.0, -8.333333333333334);
  }

  @Test
  public void liquidClosureExcludesIncompatibleOffGasAndWaterBases() {
    assertEquals(66.0, AlDiwiniyaAtmosphericReference.getCrudeFeedRateM3PerHour(), 0.0);
    assertEquals(65.0, AlDiwiniyaAtmosphericReference.getMeasuredHydrocarbonLiquidProductRateM3PerHour(), 0.0);
    assertEquals(98.48484848484848, AlDiwiniyaAtmosphericReference.getMeasuredHydrocarbonLiquidClosurePercent(),
        1.0e-12);
    assertEquals(0.4, AlDiwiniyaAtmosphericReference.getOverheadWaterRateM3PerHour(), 0.0);
    assertFalse(AlDiwiniyaAtmosphericReference.getProduct("Off gas").isHydrocarbonLiquid());
  }

  @Test
  public void operatingConfigurationAndProvenanceAreExplicit() {
    assertEquals("10.52716/jprs.v15i3.965", AlDiwiniyaAtmosphericReference.DOI);
    assertEquals("CC BY 4.0", AlDiwiniyaAtmosphericReference.LICENSE);
    assertEquals("2025-09-21", AlDiwiniyaAtmosphericReference.PUBLICATION_DATE);
    assertEquals(10000.0, AlDiwiniyaAtmosphericReference.getCrudeCapacityBarrelsPerDay(), 0.0);
    assertEquals(29, AlDiwiniyaAtmosphericReference.getColumnStageCount());
    assertEquals(300.0, AlDiwiniyaAtmosphericReference.getColumnFeedTemperatureCelsius(), 0.0);
    assertEquals(1.5, AlDiwiniyaAtmosphericReference.getColumnFeedPressureBarGauge(), 0.0);
    assertEquals(0.75, AlDiwiniyaAtmosphericReference.getColumnTopPressureBarGauge(), 0.0);
    assertEquals(1.2, AlDiwiniyaAtmosphericReference.getColumnBottomPressureBarGauge(), 0.0);
    assertEquals(3.0, AlDiwiniyaAtmosphericReference.getGasoilPumpAroundRateM3PerHour(), 0.0);
    assertEquals(75.0, AlDiwiniyaAtmosphericReference.getKeroseneStripperSteamRateKgPerHour(), 0.0);
    assertEquals(125.0, AlDiwiniyaAtmosphericReference.getGasoilStripperSteamRateKgPerHour(), 0.0);
    assertEquals(300.0, AlDiwiniyaAtmosphericReference.getBottomSteamRateKgPerHour(), 0.0);
  }

  @Test
  public void invalidQueriesAndRelativeErrorInputsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> AlDiwiniyaAtmosphericReference.getProduct(null));
    assertThrows(IllegalArgumentException.class, () -> AlDiwiniyaAtmosphericReference.getProduct("Naphtha"));
    assertThrows(IllegalArgumentException.class,
        () -> AlDiwiniyaAtmosphericReference.calculateSignedRelativeErrorPercent(0.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AlDiwiniyaAtmosphericReference.calculateSignedRelativeErrorPercent(Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AlDiwiniyaAtmosphericReference.calculateSignedRelativeErrorPercent(1.0, Double.POSITIVE_INFINITY));
  }

  private static void assertProduct(ProductReference product, String name, double plantRate, double hysysRate,
      double plantTemperature, double hysysTemperature, double expectedRateError, double expectedTemperatureError) {
    assertEquals(name, product.getName());
    assertEquals(plantRate, product.getPlantRateM3PerHour(), 0.0);
    assertEquals(hysysRate, product.getHysysRateM3PerHour(), 0.0);
    assertEquals(plantTemperature, product.getPlantDrawTemperatureCelsius(), 0.0);
    assertEquals(hysysTemperature, product.getHysysDrawTemperatureCelsius(), 0.0);
    assertEquals(expectedRateError, product.getRateRelativeErrorPercent(), 1.0e-12);
    assertEquals(expectedTemperatureError, product.getDrawTemperatureRelativeErrorPercent(), 1.0e-12);
  }

  private static void assertStrictlyIncreasing(double[] values) {
    for (int i = 1; i < values.length; i++) {
      assertFalse(values[i] <= values[i - 1]);
    }
  }
}
