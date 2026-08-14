package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  private static final double PHASE_STABLE_REFERENCE_TEMPERATURE_TOLERANCE = 5.0e-6;

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
      final int diagnosticComponentIndex = componentIndex;
      double feedMoles = gasFeedMoles[componentIndex] + solventFeedMoles[componentIndex];
      double productMoles = gasProductMoles[componentIndex] + liquidProductMoles[componentIndex];
      assertEquals(feedMoles, productMoles, Math.max(1.0e-12, feedMoles * 1.0e-9),
          () -> COMPONENTS[diagnosticComponentIndex] + " must close across the column; "
              + componentClosureDiagnostics(testCase, diagnosticComponentIndex));
    }
  }

  private static String componentClosureDiagnostics(ColumnCase testCase, int componentIndex) {
    DistillationColumn column = testCase.column;
    return "solver=" + column.getLastSolverTypeUsed() + ", status=" + column.getLastSolveStatus() + ", reason="
        + column.getLastSolveStatusReason() + ", gasFeed=" + componentInventory(testCase.gasFeed, componentIndex)
        + ", solventFeed=" + componentInventory(testCase.solventFeed, componentIndex) + ", gasProduct="
        + componentInventory(column.getGasOutStream(), componentIndex) + ", liquidProduct="
        + componentInventory(column.getLiquidOutStream(), componentIndex);
  }

  private static String componentInventory(StreamInterface stream, int componentIndex) {
    SystemInterface system = stream.getThermoSystem();
    StringBuilder inventory = new StringBuilder();
    inventory.append(stream.getName()).append("{total=").append(system.getTotalNumberOfMoles()).append(", phases=")
        .append(system.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      inventory.append(", ").append(system.getPhase(phaseIndex).getPhaseTypeName()).append("[beta=")
          .append(system.getBeta(phaseIndex)).append(", componentInPhase=")
          .append(system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase()).append("]");
    }
    if (system.getNumberOfPhases() > 0) {
      inventory.append(", componentTotal=").append(system.getPhase(0).getComponent(componentIndex).getNumberOfmoles());
    }
    return inventory.append("}").toString();
  }

  private static void assertPhysicalProduct(StreamInterface product, String label) {
    double flowRate = product.getFlowRate("kg/hr");
    double temperature = product.getTemperature("K");
    double pressure = product.getPressure("bara");
    assertTrue(Double.isFinite(flowRate) && flowRate >= 0.0, label + " flow must be finite and non-negative");
    assertTrue(Double.isFinite(temperature) && temperature > 100.0 && temperature < 1000.0,
        label + " temperature must remain physical");
    assertTrue(Double.isFinite(pressure) && pressure > 0.0, label + " pressure must remain positive");

    double compositionSum = 0.0;
    double[] composition = product.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < composition.length; componentIndex++) {
      assertTrue(Double.isFinite(composition[componentIndex]) && composition[componentIndex] >= 0.0,
          label + " composition must remain finite and non-negative");
      compositionSum += composition[componentIndex];
    }
    assertEquals(1.0, compositionSum, 1.0e-10, label + " composition must remain normalized");
  }

  private static void assertNaphtaliBasinPoint(ColumnCase testCase, String point) {
    DistillationColumn column = testCase.column;
    assertTrue(column.solved(), point + ": " + column.getConvergenceDiagnostics());
    assertTrue(
        column.getLastSolverTypeUsed() == DistillationColumn.SolverType.NAPHTALI_SANDHOLM
            || column.getLastSolverTypeUsed() == DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
        point + " must finish with the simultaneous solver or its coordinated damped fallback");
    assertTrue(column.getLastNaphtaliThermoEvaluationCount() > 0,
        point + " must retain thermodynamic work from the Naphtali-Sandholm attempt");
    assertTrue(column.getLastNaphtaliThermoKValueIterationCount() >= column.getLastNaphtaliThermoEvaluationCount(),
        point + " must perform at least one K-value sweep per uncached thermodynamic evaluation");
    assertTrue(column.getLastNaphtaliThermoEvaluationCount() < 500000,
        point + " must keep tray thermodynamic evaluations bounded");
    assertTrue(column.getLastNaphtaliThermoKValueIterationCount() < 1500000,
        point + " must keep K-value sweeps bounded");
    assertTrue(
        Double.isFinite(column.getLastMassResidual())
            && column.getLastMassResidual() <= column.getMassBalanceTolerance(),
        point + " must satisfy total mass residual");
    assertTrue(
        Double.isFinite(column.getLastEnergyResidual())
            && column.getLastEnergyResidual() <= column.getEnthalpyBalanceTolerance(),
        point + " must satisfy the active energy residual");
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()), point + " must publish a finite MESH residual");
    assertPhysicalProduct(column.getGasOutStream(), point + " gas product");
    assertPhysicalProduct(column.getLiquidOutStream(), point + " liquid product");
    assertEquals(330.15, column.getLiquidOutStream().getTemperature("K"), 0.1,
        point + " must satisfy the fixed reboiler temperature");
    double feedMassFlow = testCase.gasFeed.getFlowRate("kg/hr") + testCase.solventFeed.getFlowRate("kg/hr");
    double productMassFlow = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(feedMassFlow, productMassFlow, Math.max(1.0e-6, feedMassFlow * 1.0e-8),
        point + " must close total product mass");
    assertComponentClosure(testCase);
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

    double dampedGasFlow = damped.getGasOutStream().getFlowRate("kg/hr");
    double dampedLiquidFlow = damped.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(dampedGasFlow, sumRates.getGasOutStream().getFlowRate("kg/hr"),
        Math.max(1.0e-3, Math.abs(dampedGasFlow) * 1.0e-5));
    assertEquals(dampedLiquidFlow, sumRates.getLiquidOutStream().getFlowRate("kg/hr"),
        Math.max(1.0e-3, Math.abs(dampedLiquidFlow) * 1.0e-5));
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
      // Compare at the same terminal target used internally by native reboiler-only SUM_RATES.
      // Production damped solves retain their established configured tolerance.
      damped.column.setTemperatureTolerance(PHASE_STABLE_REFERENCE_TEMPERATURE_TOLERANCE);
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

  /**
   * Map cold, exact-reuse, nearby-point, and severe retained-state Naphtali-Sandholm behavior on a two-feed
   * absorber/stripper.
   */
  @Test
  public void naphtaliSandholmBasinRemainsAccountableAcrossRetainedStates() {
    ColumnCase testCase = createRepresentativeCase("naphtali basin", DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    testCase.column.setMassBalanceTolerance(1.0e-8);
    testCase.column.run();
    assertNaphtaliBasinPoint(testCase, "cold");
    DistillationColumn.SolverType coldSolver = testCase.column.getLastSolverTypeUsed();
    double coldGasFlow = testCase.column.getGasOutStream().getFlowRate("kg/hr");
    double coldLiquidFlow = testCase.column.getLiquidOutStream().getFlowRate("kg/hr");

    testCase.column.run();
    assertNaphtaliBasinPoint(testCase, "exact repeat");
    if (coldSolver == DistillationColumn.SolverType.NAPHTALI_SANDHOLM) {
      assertTrue(testCase.column.wasNaphtaliSandholmWarmStateReused(),
          "an accepted simultaneous state should be reused for identical inputs");
      assertEquals(0, testCase.column.getLastIterationCount(),
          "exact simultaneous-state reuse should require no initializer or Newton iteration");
      assertEquals(coldGasFlow, testCase.column.getGasOutStream().getFlowRate("kg/hr"), 0.0);
      assertEquals(coldLiquidFlow, testCase.column.getLiquidOutStream().getFlowRate("kg/hr"), 0.0);
    } else {
      assertFalse(testCase.column.wasNaphtaliSandholmWarmStateReused(),
          "a damped fallback state must not be cached as a Naphtali-Sandholm state");
      assertTrue(testCase.column.wasSequentialWarmStateReused(),
          "an unchanged coordinated damped fallback should reuse its accepted sequential state");
      assertEquals(0, testCase.column.getLastIterationCount(),
          "exact coordinated-fallback reuse should require no initializer or solver iteration");
      assertTrue(testCase.column.getLastNaphtaliThermoEvaluationCount() > 0,
          "exact fallback reuse must retain work from the rejected Naphtali-Sandholm attempt");
      assertEquals(coldGasFlow, testCase.column.getGasOutStream().getFlowRate("kg/hr"), 0.0);
      assertEquals(coldLiquidFlow, testCase.column.getLiquidOutStream().getFlowRate("kg/hr"), 0.0);
    }

    testCase.gasFeed.setTemperature(314.15, "K");
    testCase.gasFeed.run();
    testCase.column.run();
    assertFalse(testCase.column.wasNaphtaliSandholmWarmStateReused(),
        "a nearby feed state must invalidate exact simultaneous-state reuse");
    assertNaphtaliBasinPoint(testCase, "nearby");

    for (int trayIndex = 0; trayIndex < testCase.column.getNumberOfTrays(); trayIndex++) {
      double perturbedTemperature = testCase.column.getTray(trayIndex).getTemperature()
          + 90.0 * Math.sin((trayIndex + 1.0) * 1.9);
      testCase.column.getTray(trayIndex).setTemperature(perturbedTemperature);
      testCase.column.getTray(trayIndex).getThermoSystem().setTemperature(perturbedTemperature);
    }
    testCase.solventFeed.setFlowRate(1260.0, "kg/hr");
    testCase.solventFeed.run();
    testCase.column.run();
    assertFalse(testCase.column.wasNaphtaliSandholmWarmStateReused(),
        "a changed feed must force evaluation of the severe retained-state perturbation");
    assertNaphtaliBasinPoint(testCase, "severe retained perturbation");
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

  /** Trace-phase canonicalization must reject multi-phase, non-finite, and non-normalized states. */
  @Test
  public void terminalTracePhaseCandidateRequiresFiniteNormalizedTwoPhaseState() {
    double[] componentMoles = { 1.0, 2.0 };
    assertTrue(
        DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(2, 1.0 - 3.0e-9, 3.0e-9, componentMoles));
    assertFalse(
        DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(3, 1.0 - 3.0e-9, 3.0e-9, componentMoles));
    assertFalse(DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(2, 0.9, 3.0e-9, componentMoles));
    assertFalse(
        DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(2, 1.0 - 3.0e-9, Double.NaN, componentMoles));
    assertFalse(DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(2, 1.0 - 3.0e-9, 3.0e-9,
        new double[] { 1.0, Double.POSITIVE_INFINITY }));
    assertFalse(DistillationColumn.isTerminalTracePhaseCanonicalizationCandidate(2, 1.0 - 3.0e-9, 3.0e-9,
        new double[] { 1.0, -1.0e-12 }));
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
