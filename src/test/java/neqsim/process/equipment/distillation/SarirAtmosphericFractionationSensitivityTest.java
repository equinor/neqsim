package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.SarirAtmosphericAssay;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductYieldReference;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Property-profile sensitivity qualification for the public Sarir atmospheric reference.
 *
 * <p>
 * The source fixes the TBP cut volumes and boundaries, whole-crude density and average molar mass, 34 valve trays, and
 * feed conditions. It does not publish per-cut density or molar-mass profiles, column endpoint conditions, or side-draw
 * locations and fractions. Both profiles and every unreported process setting used here are explicit engineering test
 * inputs. Published plant product rates remain read-only evidence and are not specifications or acceptance targets.
 * </p>
 */
public class SarirAtmosphericFractionationSensitivityTest {
  private static final double[] BASE_SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000,
      0.741826000, 0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000,
      0.901826000, 0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] BASE_MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992,
      0.272675861324, 0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318,
      0.495774293317, 0.545351722649, 0.743661439976 };

  private static final int SIMPLE_TRAY_COUNT = 34;
  private static final int FEED_INTERNAL_INDEX = 4;
  private static final int DIESEL_SCREEN_TRAY = 15;
  private static final int KEROSENE_SCREEN_TRAY = 24;
  private static final double BALANCE_TOLERANCE = 5.0e-2;

  /** Require two admissible property profiles to converge, conserve, separate, and differ. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  public void wholeCrudeConsistentProfilesProduceBoundedThirtyFourTraySensitivity() {
    ProductYieldReference[] publishedTargets = SarirAtmosphericReference.getProductYields();

    double[] alternateSpecificGravity = createAlternateSpecificGravity();
    double[] alternateMolarMass = createAlternateMolarMass(alternateSpecificGravity);
    assertProfilesAdmissible(BASE_SPECIFIC_GRAVITY, BASE_MOLAR_MASS_KG_PER_MOL);
    assertProfilesAdmissible(alternateSpecificGravity, alternateMolarMass);

    ColumnResult base = runCase("base engineering profile", BASE_SPECIFIC_GRAVITY, BASE_MOLAR_MASS_KG_PER_MOL);
    ColumnResult alternate = runCase("alternate engineering profile", alternateSpecificGravity, alternateMolarMass);

    assertTrue(maximumRelativeDifference(base, alternate) > 1.0e-4,
        "A bounded change in unreported cut properties should produce a measurable column response");
    assertPublishedTargetsUnchanged(publishedTargets);
  }

  private static ColumnResult runCase(String label, double[] specificGravity, double[] molarMassKgPerMol) {
    Stream feed = createFeed(label, specificGravity, molarMassKgPerMol);
    DistillationColumn column = createColumn(label, feed);
    column.run(UUID.randomUUID());

    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, column.getLastSolverTypeUsed(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus(), diagnostics);
    assertTrue(column.getLastIterationCount() <= 600, diagnostics);

    StreamInterface overhead = column.getGasOutStream();
    StreamInterface kerosene =
        column.getSideDrawStream(KEROSENE_SCREEN_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface diesel = column.getSideDrawStream(DIESEL_SCREEN_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface bottoms = column.getLiquidOutStream();

    assertBalances(column, feed, overhead, kerosene, diesel, bottoms);
    double overheadBoilingPoint = meanNormalBoilingPoint(overhead);
    double keroseneBoilingPoint = meanNormalBoilingPoint(kerosene);
    double dieselBoilingPoint = meanNormalBoilingPoint(diesel);
    double bottomsBoilingPoint = meanNormalBoilingPoint(bottoms);
    assertTrue(overheadBoilingPoint < keroseneBoilingPoint);
    assertTrue(keroseneBoilingPoint < dieselBoilingPoint);
    assertTrue(dieselBoilingPoint < bottomsBoilingPoint);

    double feedMassFlow = feed.getFlowRate("kg/hr");
    return new ColumnResult(overhead.getFlowRate("kg/hr") / feedMassFlow,
        kerosene.getFlowRate("kg/hr") / feedMassFlow, diesel.getFlowRate("kg/hr") / feedMassFlow,
        bottoms.getFlowRate("kg/hr") / feedMassFlow, overheadBoilingPoint, keroseneBoilingPoint, dieselBoilingPoint,
        bottomsBoilingPoint);
  }

  private static Stream createFeed(String label, double[] specificGravity, double[] molarMassKgPerMol) {
    double feedTemperatureKelvin = SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15;
    double feedPressureBara = SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0;
    SystemInterface crude = new SystemSrkEos(feedTemperatureKelvin, feedPressureBara);
    OilAssayCharacterisation assay =
        SarirAtmosphericAssay.create(crude, specificGravity, molarMassKgPerMol);
    assay.apply();
    crude.setMixingRule("classic");

    Stream feed = new Stream("Sarir " + label + " feed", crude);
    feed.setFlowRate(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), "kg/hr");
    feed.setTemperature(feedTemperatureKelvin, "K");
    feed.setPressure(feedPressureBara, "bara");
    feed.run();
    return feed;
  }

  private static DistillationColumn createColumn(String label, Stream feed) {
    assertEquals(SIMPLE_TRAY_COUNT, SarirAtmosphericReference.getColumnTrayCount());
    assertEquals(FEED_INTERNAL_INDEX,
        SIMPLE_TRAY_COUNT - SarirAtmosphericReference.getFeedTrayFromTop() + 1);

    DistillationColumn column = new DistillationColumn("Sarir 34-tray " + label, SIMPLE_TRAY_COUNT, true, true);
    column.addFeedStream(feed, FEED_INTERNAL_INDEX);
    assertEquals(FEED_INTERNAL_INDEX, column.getFeedTrayNumber(feed));

    // The source does not report these endpoint and side-draw settings. They are fixed engineering controls,
    // deliberately identical between profiles and deliberately unrelated to the published plant product rates.
    column.setTopPressure(1.20);
    column.setBottomPressure(SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0);
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    column.getReboiler().setOutTemperature(700.0);
    column.setCondenserRefluxRatio(1.0);
    column.setLiquidSideDrawFraction(KEROSENE_SCREEN_TRAY, 0.08);
    column.setLiquidSideDrawFraction(DIESEL_SCREEN_TRAY, 0.15);

    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.30);
    column.setMaxNumberOfIterations(600, true);
    column.setTemperatureTolerance(0.50);
    column.setMassBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnthalpyBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnforceEnergyBalanceTolerance(true);
    return column;
  }

  private static void assertProfilesAdmissible(double[] specificGravity, double[] molarMassKgPerMol) {
    assertEquals(SarirAtmosphericReference.getCrudeDensityAt15CKgPerCubicMetre() / 1000.0,
        SarirAtmosphericAssay.calculateBulkSpecificGravity(specificGravity), 1.0e-12);
    assertEquals(SarirAtmosphericReference.getCrudeAverageMolarMassKgPerMol(),
        SarirAtmosphericAssay.calculateBulkMolarMassKgPerMol(specificGravity, molarMassKgPerMol), 1.0e-12);
  }

  private static double[] createAlternateSpecificGravity() {
    double[] volumePercent = SarirAtmosphericAssay.getCutVolumePercent();
    double weightedIndex = 0.0;
    for (int i = 0; i < volumePercent.length; i++) {
      weightedIndex += volumePercent[i] / 100.0 * i;
    }

    double[] alternate = BASE_SPECIFIC_GRAVITY.clone();
    for (int i = 0; i < alternate.length; i++) {
      alternate[i] += 0.006 * (i - weightedIndex);
      assertTrue(alternate[i] > 0.0);
    }
    return alternate;
  }

  private static double[] createAlternateMolarMass(double[] specificGravity) {
    double[] alternate = BASE_MOLAR_MASS_KG_PER_MOL.clone();
    for (int i = 0; i < alternate.length; i++) {
      double normalizedPosition = (2.0 * i) / (alternate.length - 1.0) - 1.0;
      alternate[i] *= 1.0 + 0.10 * normalizedPosition;
    }
    double unscaledBulk = SarirAtmosphericAssay.calculateBulkMolarMassKgPerMol(specificGravity, alternate);
    double scale = SarirAtmosphericReference.getCrudeAverageMolarMassKgPerMol() / unscaledBulk;
    for (int i = 0; i < alternate.length; i++) {
      alternate[i] *= scale;
    }
    return alternate;
  }

  private static void assertBalances(DistillationColumn column, Stream feed, StreamInterface... products) {
    double feedMassFlow = feed.getFlowRate("kg/hr");
    double productMassFlow = 0.0;
    for (StreamInterface product : products) {
      double flow = product.getFlowRate("kg/hr");
      assertTrue(Double.isFinite(flow) && flow > 0.0);
      productMassFlow += flow;
    }
    assertEquals(feedMassFlow, productMassFlow, BALANCE_TOLERANCE * feedMassFlow);
    assertTrue(Double.isFinite(column.getMassBalanceError()));
    assertTrue(column.getMassBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(Double.isFinite(column.getEnergyBalanceError()));
    assertTrue(column.getEnergyBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(column.getLastTrayMaterialBalanceError() <= column.getTrayMaterialBalanceTolerance(),
        column.getConvergenceDiagnostics());
    assertComponentMolarBalance(feed, products);
  }

  private static void assertComponentMolarBalance(Stream feed, StreamInterface... products) {
    double feedFlow = feed.getFlowRate("mol/hr");
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      double productComponentFlow = 0.0;
      for (StreamInterface product : products) {
        productComponentFlow += product.getFlowRate("mol/hr")
            * product.getThermoSystem().getMolarComposition()[componentIndex];
      }
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow,
          Math.max(1.0e-7, BALANCE_TOLERANCE * Math.abs(feedComponentFlow)));
    }
  }

  private static double meanNormalBoilingPoint(StreamInterface stream) {
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double[] normalBoilingPoints = stream.getThermoSystem().getNormalBoilingPointTemperatures();
    double weightedBoilingPoint = 0.0;
    for (int i = 0; i < composition.length; i++) {
      assertTrue(Double.isFinite(normalBoilingPoints[i]) && normalBoilingPoints[i] > 0.0);
      weightedBoilingPoint += composition[i] * normalBoilingPoints[i];
    }
    return weightedBoilingPoint;
  }

  private static double maximumRelativeDifference(ColumnResult first, ColumnResult second) {
    double maximum = 0.0;
    for (int i = 0; i < first.values.length; i++) {
      double scale = Math.max(1.0e-12, Math.abs(first.values[i]));
      maximum = Math.max(maximum, Math.abs(second.values[i] - first.values[i]) / scale);
    }
    return maximum;
  }

  private static void assertPublishedTargetsUnchanged(ProductYieldReference[] before) {
    ProductYieldReference[] after = SarirAtmosphericReference.getProductYields();
    assertEquals(before.length, after.length);
    for (int i = 0; i < before.length; i++) {
      assertEquals(before[i].getName(), after[i].getName());
      assertEquals(before[i].getPlantMetricTonPerDay(), after[i].getPlantMetricTonPerDay(), 0.0);
      assertEquals(before[i].getSimulationMetricTonPerDay(), after[i].getSimulationMetricTonPerDay(), 0.0);
    }
  }

  private static final class ColumnResult {
    private final double[] values;

    private ColumnResult(double... values) {
      this.values = values;
    }
  }
}
