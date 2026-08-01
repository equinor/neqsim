package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for phase-stable sum-rates solves of reboiler-only columns. */
public class ReboilerOnlySumRatesPhaseStabilityTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-butane", "nC10" };
  private static final double[] BASE_GAS_COMPOSITION = { 0.70, 0.15, 0.10, 0.05, 1.0e-10 };

  /** Column and its external feeds for container and invalidation tests. */
  private static final class ColumnCase {
    private final Stream gasFeed;
    private final Stream solventFeed;
    private final DistillationColumn column;

    private ColumnCase(Stream gasFeed, Stream solventFeed, DistillationColumn column) {
      this.gasFeed = gasFeed;
      this.solventFeed = solventFeed;
      this.column = column;
    }

    private ProcessSystem createProcessSystem() {
      ProcessSystem process = new ProcessSystem();
      process.add(gasFeed);
      process.add(solventFeed);
      process.add(column);
      return process;
    }
  }

  private static SystemInterface createFluid(double temperature, double pressure, double[] moles) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      fluid.addComponent(COMPONENTS[componentIndex], moles[componentIndex]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Stream createFeed(String name, SystemInterface fluid, double flowRate, double pressure) {
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(flowRate, "kg/hr");
    feed.setPressure(pressure, "bara");
    feed.run();
    return feed;
  }

  private static ColumnCase createColumnCase(String name, double gasTemperature, double solventFlowRate,
      double pressure, double gasFlowRate, double[] gasComposition, DistillationColumn.SolverType solverType) {
    Stream gasFeed = createFeed(name + " rich gas", createFluid(gasTemperature, pressure, gasComposition), gasFlowRate,
        pressure);
    Stream solventFeed = createFeed(name + " lean solvent",
        createFluid(298.15, pressure, new double[] { 1.0e-10, 1.0e-10, 1.0e-10, 1.0e-10, 1.0 }), solventFlowRate,
        pressure);
    DistillationColumn column = new DistillationColumn(name, 10, true, false);
    column.addFeedStream(gasFeed, 1);
    column.addFeedStream(solventFeed, column.getNumberOfTrays() - 1);
    column.getReboiler().setOutTemperature(330.15);
    column.setTopPressure(pressure);
    column.setBottomPressure(pressure);
    column.setMaxNumberOfIterations(400);
    column.setTemperatureTolerance(1.0e-4);
    column.setMassBalanceTolerance(1.0e-1);
    column.setEnthalpyBalanceTolerance(10.0);
    column.setSolverType(solverType);
    return new ColumnCase(gasFeed, solventFeed, column);
  }

  static ColumnCase createRepresentativeCase(String name, DistillationColumn.SolverType solverType) {
    return createColumnCase(name, 313.15, 1200.0, 30.0, 1000.0, BASE_GAS_COMPOSITION, solverType);
  }

  private static List<String> phaseTypes(StreamInterface stream) {
    List<String> result = new ArrayList<String>();
    SystemInterface system = stream.getThermoSystem();
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      result.add(system.getPhase(phaseIndex).getPhaseTypeName());
    }
    return result;
  }

  private static double[] componentMoles(StreamInterface stream) {
    SystemInterface system = stream.getThermoSystem();
    double[] result = new double[system.getNumberOfComponents()];
    for (int componentIndex = 0; componentIndex < result.length; componentIndex++) {
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        result[componentIndex] += system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
      }
      if (result[componentIndex] <= 0.0 && system.getNumberOfPhases() > 0) {
        result[componentIndex] = system.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      }
    }
    return result;
  }

  private static void assertComponentClosure(ColumnCase testCase) {
    double[] gasFeedMoles = componentMoles(testCase.gasFeed);
    double[] solventFeedMoles = componentMoles(testCase.solventFeed);
    double[] gasProductMoles = componentMoles(testCase.column.getGasOutStream());
    double[] liquidProductMoles = componentMoles(testCase.column.getLiquidOutStream());
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double feedMoles = gasFeedMoles[componentIndex] + solventFeedMoles[componentIndex];
      double productMoles = gasProductMoles[componentIndex] + liquidProductMoles[componentIndex];
      assertEquals(feedMoles, productMoles, Math.max(1.0e-12, feedMoles * 1.0e-9),
          COMPONENTS[componentIndex] + " must close across the column");
    }
  }

  private static void assertEquivalentProducts(ColumnCase dampedCase, ColumnCase sumRatesCase) {
    DistillationColumn damped = dampedCase.column;
    DistillationColumn sumRates = sumRatesCase.column;
    assertTrue(damped.solved(), damped.getConvergenceDiagnostics());
    assertTrue(sumRates.solved(), sumRates.getConvergenceDiagnostics());
    assertFalse(damped.wasFeedFlashFallbackApplied());
    assertFalse(sumRates.wasFeedFlashFallbackApplied());
    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, damped.getLastSolverTypeUsed());
    assertEquals(DistillationColumn.SolverType.SUM_RATES, sumRates.getLastSolverTypeUsed());

    assertEquals(phaseTypes(damped.getGasOutStream()), phaseTypes(sumRates.getGasOutStream()));
    assertEquals(phaseTypes(damped.getLiquidOutStream()), phaseTypes(sumRates.getLiquidOutStream()));
    assertEquals(1, sumRates.getGasOutStream().getThermoSystem().getNumberOfPhases());
    assertEquals("gas", sumRates.getGasOutStream().getThermoSystem().getPhase(0).getPhaseTypeName());
    assertEquals(1.0, sumRates.getGasOutStream().getThermoSystem().getBeta(0), 1.0e-12);

    assertEquals(damped.getGasOutStream().getFlowRate("kg/hr"), sumRates.getGasOutStream().getFlowRate("kg/hr"),
        1.0e-3);
    assertEquals(damped.getLiquidOutStream().getFlowRate("kg/hr"), sumRates.getLiquidOutStream().getFlowRate("kg/hr"),
        1.0e-3);
    assertEquals(damped.getGasOutStream().getTemperature("K"), sumRates.getGasOutStream().getTemperature("K"), 1.0e-4);
    assertEquals(damped.getLiquidOutStream().getTemperature("K"), sumRates.getLiquidOutStream().getTemperature("K"),
        1.0e-4);
    assertEquals(damped.getReboiler().getDuty(), sumRates.getReboiler().getDuty(), 0.25);

    double[] dampedComposition = damped.getGasOutStream().getThermoSystem().getMolarComposition();
    double[] sumRatesComposition = sumRates.getGasOutStream().getThermoSystem().getMolarComposition();
    assertEquals(dampedComposition.length, sumRatesComposition.length);
    for (int componentIndex = 0; componentIndex < dampedComposition.length; componentIndex++) {
      assertEquals(dampedComposition[componentIndex], sumRatesComposition[componentIndex], 2.0e-6,
          COMPONENTS[componentIndex] + " gas-product composition differs");
    }
    assertComponentClosure(dampedCase);
    assertComponentClosure(sumRatesCase);
  }

  /** Native sum-rates must match the damped phase state and products across the issue envelope. */
  @Test
  public void nativeSumRatesMatchesDampedAcrossNearbyOperatingAndInitializationPoints() {
    double[][] operatingPoints = { { 313.15, 1200.0, 30.0, 1000.0, 0.70, 0.15, 0.10, 0.05 },
        { 318.15, 1300.0, 30.0, 1000.0, 0.70, 0.15, 0.10, 0.05 },
        { 315.15, 1250.0, 29.5, 980.0, 0.69, 0.16, 0.10, 0.05 },
        { 316.15, 1275.0, 30.5, 1020.0, 0.71, 0.14, 0.09, 0.06 } };
    for (int pointIndex = 0; pointIndex < operatingPoints.length; pointIndex++) {
      double[] point = operatingPoints[pointIndex];
      double[] gasComposition = { point[4], point[5], point[6], point[7], 1.0e-10 };
      ColumnCase damped = createColumnCase("damped " + pointIndex, point[0], point[1], point[2], point[3],
          gasComposition, DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
      ColumnCase sumRates = createColumnCase("sum-rates " + pointIndex, point[0], point[1], point[2], point[3],
          gasComposition, DistillationColumn.SolverType.SUM_RATES);
      if (pointIndex == 2) {
        for (int trayIndex = 0; trayIndex < damped.column.getNumberOfTrays(); trayIndex++) {
          double seedTemperature = 330.15 - 1.5 * trayIndex;
          damped.column.setSeedTemperature(trayIndex, seedTemperature);
          sumRates.column.setSeedTemperature(trayIndex, seedTemperature);
        }
      }
      damped.column.run();
      sumRates.column.run();
      assertEquivalentProducts(damped, sumRates);
    }
  }

  /** Exact reuse must remain lossless, while a changed feed must invalidate the accepted state. */
  @Test
  public void unchangedInputReuseAndChangedInputInvalidationRemainDeterministic() {
    ColumnCase testCase = createRepresentativeCase("reuse", DistillationColumn.SolverType.SUM_RATES);
    testCase.column.run();
    assertTrue(testCase.column.solved(), testCase.column.getConvergenceDiagnostics());
    double gasFlow = testCase.column.getGasOutStream().getFlowRate("kg/hr");
    double gasTemperature = testCase.column.getGasOutStream().getTemperature("K");

    testCase.column.run();
    assertTrue(testCase.column.wasSequentialWarmStateReused());
    assertEquals(0, testCase.column.getLastIterationCount());
    assertEquals(gasFlow, testCase.column.getGasOutStream().getFlowRate("kg/hr"), 0.0);
    assertEquals(gasTemperature, testCase.column.getGasOutStream().getTemperature("K"), 0.0);

    testCase.gasFeed.setTemperature(314.15, "K");
    testCase.gasFeed.run();
    testCase.column.run();
    assertFalse(testCase.column.wasSequentialWarmStateReused());
    assertTrue(testCase.column.getLastIterationCount() > 0);
    assertNotEquals(gasFlow, testCase.column.getGasOutStream().getFlowRate("kg/hr"), 1.0e-6);
    assertComponentClosure(testCase);

    DistillationColumn copied = (DistillationColumn) testCase.column.copy();
    copied.run();
    assertTrue(copied.solved(), copied.getConvergenceDiagnostics());
    assertEquals(phaseTypes(testCase.column.getGasOutStream()), phaseTypes(copied.getGasOutStream()));
  }

  /** AUTO, ProcessSystem, and ProcessModel must retain native phase-stable routing. */
  @Test
  public void autoAndProcessContainersUseNativeSumRates() {
    ColumnCase autoCase = createRepresentativeCase("auto process", DistillationColumn.SolverType.AUTO);
    autoCase.createProcessSystem().run();
    assertEquals(DistillationColumn.SolverType.SUM_RATES, autoCase.column.getLastSolverTypeUsed());
    assertTrue(autoCase.column.getLastAutoSolverSummary().contains("SUM_RATES"));
    assertEquals(1, autoCase.column.getGasOutStream().getThermoSystem().getNumberOfPhases());
    assertComponentClosure(autoCase);

    ColumnCase areaA = createRepresentativeCase("model area A", DistillationColumn.SolverType.AUTO);
    ColumnCase areaB = createColumnCase("model area B", 318.15, 1300.0, 30.0, 1000.0, BASE_GAS_COMPOSITION,
        DistillationColumn.SolverType.AUTO);
    ProcessModel model = new ProcessModel();
    model.add("area A", areaA.createProcessSystem());
    model.add("area B", areaB.createProcessSystem());
    model.run();
    assertEquals(DistillationColumn.SolverType.SUM_RATES, areaA.column.getLastSolverTypeUsed());
    assertEquals(DistillationColumn.SolverType.SUM_RATES, areaB.column.getLastSolverTypeUsed());
    assertComponentClosure(areaA);
    assertComponentClosure(areaB);
  }

  /** Any condenser configuration must remain routed to damped substitution. */
  @Test
  public void condenserConfigurationsRemainGuarded() {
    SystemInterface feedFluid = new SystemSrkEos(323.15, 10.0);
    feedFluid.addComponent("propane", 1.0);
    feedFluid.addComponent("n-butane", 1.0);
    feedFluid.setMixingRule("classic");
    Stream feed = new Stream("guarded feed", feedFluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn condenserOnly = new DistillationColumn("condenser only", 5, false, true);
    condenserOnly.addFeedStream(feed, 0);
    condenserOnly.getCondenser().setOutTemperature(298.15);
    condenserOnly.setTopPressure(10.0);
    condenserOnly.setBottomPressure(10.0);
    condenserOnly.setSolverType(DistillationColumn.SolverType.SUM_RATES);
    condenserOnly.run();
    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, condenserOnly.getLastSolverTypeUsed());

    DistillationColumn fullColumn = new DistillationColumn("full column", 5, true, true);
    fullColumn.addFeedStream(feed, 3);
    fullColumn.getCondenser().setOutTemperature(298.15);
    fullColumn.getReboiler().setOutTemperature(348.15);
    fullColumn.getCondenser().setRefluxRatio(2.0);
    fullColumn.getReboiler().setRefluxRatio(2.0);
    fullColumn.setTopPressure(10.0);
    fullColumn.setBottomPressure(10.0);
    fullColumn.setSolverType(DistillationColumn.SolverType.SUM_RATES);
    fullColumn.run();
    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, fullColumn.getLastSolverTypeUsed());
  }
}
