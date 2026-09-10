package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;

/** Qualification tests for {@link SarirAtmosphericFractionationCase}. */
public class SarirAtmosphericFractionationCaseTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };
  private static final double BALANCE_TOLERANCE = 5.0e-2;
  private static final double MATERIAL_FLOW_FRACTION = 1.0e-8;

  /** Require explicit inputs to build a conservative, ordered, non-fallback column solve. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  public void explicitEngineeringInputsCreateQualifiedColumnCase() {
    OperatingInputs inputs = qualifiedInputs();
    SarirAtmosphericFractionationCase model = SarirAtmosphericFractionationCase
        .create("Sarir public-reference integration", SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL, inputs);
    Stream feed = model.getFeedStream();
    DistillationColumn column = model.getColumn();

    assertEquals(SarirAtmosphericFractionationCase.FEED_INTERNAL_INDEX, column.getFeedTrayNumber(feed));
    assertEquals(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), feed.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(SarirAtmosphericReference.getColumnFeedTemperatureCelsius(), feed.getTemperature("C"), 1.0e-9);
    assertEquals(SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, feed.getPressure("bara"), 1.0e-12);

    assertEquals(1.20, inputs.getTopPressureBara(), 0.0);
    assertEquals(SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, inputs.getBottomPressureBara(), 0.0);
    assertEquals(700.0, inputs.getReboilerTemperatureKelvin(), 0.0);
    assertEquals(1.0, inputs.getCondenserRefluxRatio(), 0.0);
    assertEquals(24, inputs.getKeroseneSideDrawTray());
    assertEquals(0.08, inputs.getKeroseneSideDrawFraction(), 0.0);
    assertEquals(15, inputs.getDieselSideDrawTray());
    assertEquals(0.15, inputs.getDieselSideDrawFraction(), 0.0);
    assertTrue(inputs == model.getOperatingInputs());
    assertThrows(IllegalStateException.class, () -> SarirAtmosphericFractionationResult.evaluate(model));

    column.run(UUID.randomUUID());

    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus(), diagnostics);
    assertTrue(column.getLastIterationCount() <= 600, diagnostics);

    StreamInterface[] products = { column.getGasOutStream(),
        column.getSideDrawStream(inputs.getKeroseneSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID),
        column.getSideDrawStream(inputs.getDieselSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID),
        column.getLiquidOutStream() };
    assertBalancesAndBoilingOrder(column, feed, products);

    SarirAtmosphericFractionationResult result = SarirAtmosphericFractionationResult.evaluate(model);
    assertEquals(feed.getFlowRate("kg/hr"), result.getFeedMassFlowKgPerHour(), 1.0e-8);
    assertEquals(column.getLastIterationCount(), result.getIterationCount());
    assertEquals(column.getMassBalanceError(), result.getColumnMassBalanceError(), 0.0);
    assertEquals(column.getEnergyBalanceError(), result.getColumnEnergyBalanceError(), 0.0);
    assertTrue(result.getMassClosureRelativeError() <= BALANCE_TOLERANCE);
    assertTrue(!result.getConvergenceDiagnostics().isEmpty());

    String[] expectedLabels = { "Total Naphtha", "Kerosene", "Diesel", "Residual" };
    SarirAtmosphericFractionationResult.ProductResult[] rows = result.getProducts();
    assertEquals(expectedLabels.length, rows.length);
    double calculatedMassFlow = 0.0;
    double calculatedFraction = 0.0;
    double previousBoilingPoint = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < rows.length; i++) {
      assertEquals(expectedLabels[i], rows[i].getProductLabel());
      assertEquals(rows[i], result.getProduct(expectedLabels[i]));
      assertTrue(Double.isFinite(rows[i].getCalculatedMassFlowKgPerHour()));
      assertTrue(rows[i].getCalculatedMassFlowKgPerHour() >= 0.0);
      assertTrue(Double.isFinite(rows[i].getCalculatedMassFractionOfFeed()));
      assertTrue(Double.isFinite(rows[i].getPlantMassFlowKgPerHour()));
      assertTrue(Double.isFinite(rows[i].getAbsoluteRelativeErrorPercentAgainstPlant()));
      calculatedMassFlow += rows[i].getCalculatedMassFlowKgPerHour();
      calculatedFraction += rows[i].getCalculatedMassFractionOfFeed();
      if (Double.isFinite(rows[i].getMeanNormalBoilingPointKelvin())) {
        assertTrue(rows[i].getMeanNormalBoilingPointKelvin() > previousBoilingPoint);
        assertEquals(rows[i].getMeanNormalBoilingPointKelvin() - 273.15, rows[i].getMeanNormalBoilingPointCelsius(),
            1.0e-12);
        assertTrue(rows[i].hasBoilingPointDistribution());
        assertBoilingRange(rows[i]);
        previousBoilingPoint = rows[i].getMeanNormalBoilingPointKelvin();
      } else {
        assertTrue(!rows[i].hasBoilingPointDistribution());
      }
    }
    assertEquals(result.getProductMassFlowKgPerHour(), calculatedMassFlow, 1.0e-8);
    assertEquals(calculatedMassFlow / feed.getFlowRate("kg/hr"), calculatedFraction, 1.0e-12);
    rows[0] = null;
    assertTrue(result.getProducts()[0] != null);
    assertThrows(IllegalArgumentException.class, () -> result.getProduct(null));
    assertThrows(IllegalArgumentException.class, () -> result.getProduct("Naphtha"));
  }

  /** Require unreported operating controls and profiles to fail closed. */
  @Test
  public void invalidEngineeringInputsAreRejectedBeforeCaseCreation() {
    assertThrows(NullPointerException.class, () -> SarirAtmosphericFractionationResult.evaluate(null));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(2.5, 2.0, 700.0, 1.0, 24, 0.08, 15, 0.15));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(1.2, 2.33, 700.0, 1.0, 15, 0.08, 24, 0.15));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(1.2, 2.33, 700.0, 1.0, 24, 1.0, 15, 0.15));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatingInputs(1.2, 2.33, 700.0, Double.NaN, 24, 0.08, 15, 0.15));
    assertThrows(IllegalArgumentException.class, () -> new OperatingInputs(1.2, 2.33, 700.0, 1.0,
        SarirAtmosphericFractionationCase.FEED_INTERNAL_INDEX, 0.08, 15, 0.15));

    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericFractionationCase.create(" ", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, qualifiedInputs()));
    assertThrows(NullPointerException.class,
        () -> SarirAtmosphericFractionationCase.create("Sarir", SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL, null));

    double[] inconsistentSpecificGravity = SPECIFIC_GRAVITY.clone();
    inconsistentSpecificGravity[0] += 0.5;
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericFractionationCase.create("Sarir",
        inconsistentSpecificGravity, MOLAR_MASS_KG_PER_MOL, qualifiedInputs()));
  }

  private static void assertBoilingRange(SarirAtmosphericFractionationResult.ProductResult product) {
    double[] temperatures = product.getBoilingPointTemperaturesKelvin();
    double[] cumulativeFractions = product.getCumulativeMoleFractions();
    assertEquals(temperatures.length, cumulativeFractions.length);
    assertTrue(temperatures.length > 0);
    assertNotSame(temperatures, product.getBoilingPointTemperaturesKelvin());
    assertNotSame(cumulativeFractions, product.getCumulativeMoleFractions());

    for (int i = 0; i < temperatures.length; i++) {
      assertTrue(Double.isFinite(temperatures[i]) && temperatures[i] > 0.0);
      assertTrue(Double.isFinite(cumulativeFractions[i]) && cumulativeFractions[i] > 0.0);
      if (i > 0) {
        assertTrue(temperatures[i] >= temperatures[i - 1]);
        assertTrue(cumulativeFractions[i] > cumulativeFractions[i - 1]);
      }
    }
    assertEquals(1.0, cumulativeFractions[cumulativeFractions.length - 1], 1.0e-12);

    double t10 = product.getNormalBoilingPointQuantileKelvin(0.10);
    double t50 = product.getNormalBoilingPointQuantileKelvin(0.50);
    double t90 = product.getNormalBoilingPointQuantileKelvin(0.90);
    assertTrue(t10 <= t50);
    assertTrue(t50 <= t90);
    assertEquals(t50 - 273.15, product.getNormalBoilingPointQuantileCelsius(0.50), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> product.getNormalBoilingPointQuantileKelvin(0.0));
    assertThrows(IllegalArgumentException.class, () -> product.getNormalBoilingPointQuantileKelvin(1.0001));
    assertThrows(IllegalArgumentException.class, () -> product.getNormalBoilingPointQuantileKelvin(Double.NaN));

    double originalTemperature = product.getBoilingPointTemperaturesKelvin()[0];
    temperatures[0] = -1.0;
    cumulativeFractions[0] = -1.0;
    assertEquals(originalTemperature, product.getBoilingPointTemperaturesKelvin()[0], 0.0);
    assertTrue(product.getCumulativeMoleFractions()[0] > 0.0);
  }

  private static OperatingInputs qualifiedInputs() {
    return new OperatingInputs(1.20, SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, 700.0, 1.0, 24, 0.08,
        15, 0.15);
  }

  private static void assertBalancesAndBoilingOrder(DistillationColumn column, Stream feed,
      StreamInterface[] products) {
    double feedMassFlow = feed.getFlowRate("kg/hr");
    double productMassFlow = 0.0;
    double previousBoilingPoint = Double.NEGATIVE_INFINITY;
    int materialProductCount = 0;

    for (StreamInterface product : products) {
      double massFlow = product.getFlowRate("kg/hr");
      assertTrue(Double.isFinite(massFlow) && massFlow >= 0.0);
      productMassFlow += massFlow;
      if (massFlow > MATERIAL_FLOW_FRACTION * feedMassFlow) {
        double meanBoilingPoint = meanNormalBoilingPoint(product);
        assertTrue(meanBoilingPoint > previousBoilingPoint,
            "Material products must become heavier from the column top to the bottoms");
        previousBoilingPoint = meanBoilingPoint;
        materialProductCount++;
      }
    }

    assertTrue(materialProductCount >= 2);
    assertEquals(feedMassFlow, productMassFlow, BALANCE_TOLERANCE * feedMassFlow);
    assertTrue(Double.isFinite(column.getMassBalanceError()));
    assertTrue(column.getMassBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(Double.isFinite(column.getEnergyBalanceError()));
    assertTrue(column.getEnergyBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(column.getLastTrayMaterialBalanceError() <= column.getTrayMaterialBalanceTolerance(),
        column.getConvergenceDiagnostics());
  }

  private static double meanNormalBoilingPoint(StreamInterface stream) {
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double[] boilingPoints = stream.getThermoSystem().getNormalBoilingPointTemperatures();
    double mean = 0.0;
    for (int i = 0; i < composition.length; i++) {
      assertTrue(Double.isFinite(boilingPoints[i]) && boilingPoints[i] > 0.0);
      mean += composition[i] * boilingPoints[i];
    }
    return mean;
  }
}
