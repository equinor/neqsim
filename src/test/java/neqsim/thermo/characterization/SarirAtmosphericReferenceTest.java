package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductQualityReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductYieldReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.PumparoundReference;

/** Tests the public Sarir atmospheric assay and validation reference. */
public class SarirAtmosphericReferenceTest {
  @Test
  public void tbpEvidencePreservesNumericCurveAndOpenEndedResidue() {
    double[] expectedTemperatureCelsius = { 70.0, 90.0, 110.0, 150.0, 195.0, 215.0, 255.0, 275.0, 295.0, 335.0, 370.0,
        400.0, 460.0, 480.0, 500.0, 520.0, 550.0 };
    double[] expectedVolumePercent = { 7.44, 10.47, 13.83, 21.16, 28.52, 31.54, 38.03, 41.76, 44.68, 51.97, 59.19,
        63.50, 72.52, 75.61, 78.66, 81.05, 83.70 };

    assertArrayEquals(expectedTemperatureCelsius, SarirAtmosphericReference.getTbpTemperatureCelsius(), 0.0);
    assertArrayEquals(expectedVolumePercent, SarirAtmosphericReference.getTbpCumulativeVolumePercent(), 0.0);
    assertEquals(343.15, SarirAtmosphericReference.getTbpTemperatureKelvin()[0], 1.0e-12);
    assertEquals(823.15, SarirAtmosphericReference.getTbpTemperatureKelvin()[16], 1.0e-12);
    assertEquals(550.0, SarirAtmosphericReference.getTerminalResidueLowerBoundaryCelsius(), 0.0);
    assertEquals(16.30, SarirAtmosphericReference.getTerminalResidueVolumePercent(), 1.0e-12);
    assertFalse(SarirAtmosphericReference.hasCompleteNumericTbpCurve());
    assertFalse(SarirAtmosphericReference.hasResolvedLightEndComposition());
    assertStrictlyIncreasing(expectedTemperatureCelsius);
    assertStrictlyIncreasing(expectedVolumePercent);
  }

  @Test
  public void returnedArraysCannotMutateFrozenReference() {
    double[] volumes = SarirAtmosphericReference.getTbpCumulativeVolumePercent();
    volumes[0] = 0.0;
    assertEquals(7.44, SarirAtmosphericReference.getTbpCumulativeVolumePercent()[0], 0.0);

    ProductQualityReference[] quality = SarirAtmosphericReference.getProductQualities();
    quality[0] = null;
    assertEquals("Light Naphtha", SarirAtmosphericReference.getProductQualities()[0].getName());

    ProductYieldReference[] yields = SarirAtmosphericReference.getProductYields();
    yields[0] = null;
    assertEquals("Total Naphtha", SarirAtmosphericReference.getProductYields()[0].getName());

    PumparoundReference[] pumparounds = SarirAtmosphericReference.getPumparounds();
    pumparounds[0] = null;
    assertEquals("Top pump around (TPA)", SarirAtmosphericReference.getPumparounds()[0].getName());
  }

  @Test
  public void numericProductQualityRowsMatchPublishedTable() {
    ProductQualityReference[] quality = SarirAtmosphericReference.getProductQualities();
    assertEquals(4, quality.length);
    assertQuality(quality[0], "Light Naphtha", 42.0, 90.0, -9.0, 97.0);
    assertQuality(quality[1], "Heavy Naphtha", 96.0, 160.0, 83.0, 153.0);
    assertQuality(quality[2], "Kerosene", 185.0, 221.0, 159.0, 214.0);
    assertQuality(quality[3], "Diesel", 262.0, 346.0, 235.0, 339.0);
  }

  @Test
  public void plantYieldRowsRemainIndependentValidationEvidence() {
    ProductYieldReference[] yields = SarirAtmosphericReference.getProductYields();
    assertEquals(4, yields.length);
    assertYield(yields[0], "Total Naphtha", 208.95, 208.2, 0.35893754486719315);
    assertYield(yields[1], "Kerosene", 22.85, 20.0, 12.472647702407006);
    assertYield(yields[2], "Diesel", 425.018, 393.0, 7.533328000225867);
    assertYield(yields[3], "Residual", 646.5, 706.1, 9.218870843000776);
    assertTrue(SarirAtmosphericReference.isIndependentYieldValidationCase());
    assertFalse(SarirAtmosphericReference.areSimulationProductRatesImposedSpecifications());
  }

  @Test
  public void operatingConfigurationAndProvenanceAreExplicit() {
    assertEquals("10.66411/jer.v33i.46", SarirAtmosphericReference.DOI);
    assertEquals("CC BY 4.0", SarirAtmosphericReference.LICENSE);
    assertEquals("2022-03-31", SarirAtmosphericReference.PUBLICATION_DATE);
    assertEquals(841.5, SarirAtmosphericReference.getCrudeDensityAt15CKgPerCubicMetre(), 0.0);
    assertEquals(36.5, SarirAtmosphericReference.getCrudeApiGravityAt60F(), 0.0);
    assertEquals(0.120, SarirAtmosphericReference.getCrudeSulfurMassPercent(), 0.0);
    assertEquals(0.2447, SarirAtmosphericReference.getCrudeAverageMolarMassKgPerMol(), 0.0);
    assertEquals(34, SarirAtmosphericReference.getColumnTrayCount());
    assertEquals(31, SarirAtmosphericReference.getFeedTrayFromTop());
    assertEquals(54420.0, SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), 0.0);
    assertEquals(350.0, SarirAtmosphericReference.getColumnFeedTemperatureCelsius(), 0.0);
    assertEquals(233.0, SarirAtmosphericReference.getColumnFeedPressureKPa(), 0.0);
    assertEquals(340.2, SarirAtmosphericReference.getMainColumnSteamRateKgPerHour(), 0.0);
    assertEquals(29777.64, SarirAtmosphericReference.getTopPumpAroundRateKgPerHour(), 0.0);
    assertEquals(60423.66, SarirAtmosphericReference.getBottomPumpAroundRateKgPerHour(), 0.0);
  }

  @Test
  public void pumparoundRowsPreservePublishedTableWithoutInferringTrayBasis() {
    PumparoundReference[] pumparounds = SarirAtmosphericReference.getPumparounds();
    assertEquals(2, pumparounds.length);
    assertPumparound(pumparounds[0], "Top pump around (TPA)", 3, 1, 29777.64, 143.9, 80.99, 62.91);
    assertPumparound(pumparounds[1], "Bottom pump around (BPA)", 22, 19, 60423.66, 232.4, 173.99, 58.41);
    assertFalse(SarirAtmosphericReference.hasExplicitPumparoundTrayNumberingBasis());
    assertEquals(pumparounds[0], SarirAtmosphericReference.getPumparound("Top pump around (TPA)"));
    assertEquals(pumparounds[1], SarirAtmosphericReference.getPumparound("Bottom pump around (BPA)"));
  }

  @Test
  public void invalidQueriesAndErrorInputsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericReference.getProductYield(null));
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericReference.getProductYield("Naphtha"));
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericReference.getPumparound(null));
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericReference.getPumparound("TPA"));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericReference.calculateAbsoluteRelativeErrorPercent(0.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericReference.calculateAbsoluteRelativeErrorPercent(Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericReference.calculateAbsoluteRelativeErrorPercent(1.0, Double.POSITIVE_INFINITY));
  }

  private static void assertPumparound(PumparoundReference pumparound, String name, int drawTray, int returnTray,
      double massFlowRate, double drawTemperature, double returnTemperature, double temperatureDrop) {
    assertEquals(name, pumparound.getName());
    assertEquals(drawTray, pumparound.getSourceDrawTrayNumber());
    assertEquals(returnTray, pumparound.getSourceReturnTrayNumber());
    assertEquals(massFlowRate, pumparound.getMassFlowRateKgPerHour(), 0.0);
    assertEquals(drawTemperature, pumparound.getDrawTemperatureCelsius(), 0.0);
    assertEquals(returnTemperature, pumparound.getReturnTemperatureCelsius(), 0.0);
    assertEquals(temperatureDrop, pumparound.getTemperatureDropKelvin(), 1.0e-12);
  }

  private static void assertQuality(ProductQualityReference quality, String name, double labFive, double labNinetyFive,
      double simulationFive, double simulationNinetyFive) {
    assertEquals(name, quality.getName());
    assertEquals(labFive, quality.getLaboratoryFivePercentCelsius(), 0.0);
    assertEquals(labNinetyFive, quality.getLaboratoryNinetyFivePercentCelsius(), 0.0);
    assertEquals(simulationFive, quality.getSimulationFivePercentCelsius(), 0.0);
    assertEquals(simulationNinetyFive, quality.getSimulationNinetyFivePercentCelsius(), 0.0);
  }

  private static void assertYield(ProductYieldReference yield, String name, double plant, double simulation,
      double errorPercent) {
    assertEquals(name, yield.getName());
    assertEquals(plant, yield.getPlantMetricTonPerDay(), 0.0);
    assertEquals(simulation, yield.getSimulationMetricTonPerDay(), 0.0);
    assertEquals(errorPercent, yield.getAbsoluteRelativeErrorPercent(), 1.0e-9);
  }

  private static void assertStrictlyIncreasing(double[] values) {
    for (int i = 1; i < values.length; i++) {
      assertTrue(values[i] > values[i - 1]);
    }
  }
}
