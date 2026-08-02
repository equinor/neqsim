package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport.ConvergenceReason;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests one-phase transient nonlinear and finite-volume mass diagnostics. */
class OnePhaseFlowConvergenceTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 288.15;
  private static final double PRESSURE_BARA = 70.0;
  private static final double MASS_FLOW_KG_PER_SECOND = 50.0;

  @Test
  void compositionStepConvergesWithEosConsistentTotalMass() {
    PipeFlowSystem pipe = createInitializedPipe();
    OnePhaseFlowConvergenceReport report = runCompositionStep(pipe, 30.0);

    assertEquals(ConvergenceReason.CONVERGED, report.getReason());
    assertTrue(report.isNonlinearMetricEquationResidual());
    assertTrue(report.getNonlinearIterations() <= 12);
    assertTrue(report.getRelativeFiniteVolumeMassResidual() < report.getMassBalanceRelativeTolerance(),
        "The authoritative finite-volume inventory must close to roundoff: " + report.getFiniteVolumeMassResidualKg()
            + " kg");
    assertTrue(report.getMaximumRelativeDensityResidual() <= report.getDensityRelativeTolerance());
    assertTrue(report.getRelativeThermodynamicMassResidual() < report.getMassBalanceRelativeTolerance());

    double impliedInletMassFlow = report.getInletBoundaryMassKg() / 30.0;
    assertTrue(impliedInletMassFlow > 45.0 && impliedInletMassFlow < 60.0,
        "The prescribed inlet density must supply the approximately 50 kg/s boundary flux: " + impliedInletMassFlow
            + " kg/s");

    double[] nonlinearHistory = report.getNonlinearUpdateHistory();
    double[] densityHistory = report.getDensityResidualHistory();
    assertEquals(report.getNonlinearIterations() + 1, nonlinearHistory.length);
    assertEquals(report.getNonlinearIterations() + 1, densityHistory.length);
    assertTrue(nonlinearHistory[0] > nonlinearHistory[nonlinearHistory.length - 1],
        "The update history must show the converged algebraic iterate.");
    assertAllFinite(nonlinearHistory);
    assertAllFinite(densityHistory);
  }

  @Test
  void outletDiagnosticUsesTheAuthoritativeFiniteVolumeFace() {
    PipeFlowSystem pipe = createInitializedPipe(12);
    int boundaryNode = pipe.getTotalNumberOfNodes() - 1;
    int outletCell = boundaryNode - 1;

    // The boundary node prescribes pressure and is not an accumulating control volume. Give it a
    // deliberately different area to prove the diagnostic uses the face owned by the last FV row.
    pipe.getNode(boundaryNode).getGeometry().setDiameter(0.35);
    pipe.getNode(boundaryNode).init();
    OnePhaseFlowConvergenceReport report = runCompositionStep(pipe, 30.0);

    double expectedOutletMass = 30.0 * pipe.getNode(outletCell).getVelocityOut().doubleValue()
        * pipe.getNode(outletCell).getGeometry().getArea()
        * pipe.getNode(outletCell).getBulkSystem().getPhase(0).getDensity();
    assertEquals(expectedOutletMass, report.getOutletBoundaryMassKg(), Math.abs(expectedOutletMass) * 1.0e-12);
    assertTrue(report.getRelativeFiniteVolumeMassResidual() < report.getMassBalanceRelativeTolerance());
  }

  @Test
  void diagnosticsAreDeterministicAndExposeTimestepSensitivity() {
    OnePhaseFlowConvergenceReport thirtySecondA = runCompositionStep(createInitializedPipe(), 30.0);
    OnePhaseFlowConvergenceReport thirtySecondB = runCompositionStep(createInitializedPipe(), 30.0);
    OnePhaseFlowConvergenceReport fifteenSecond = runCompositionStep(createInitializedPipe(), 15.0);

    assertEquals(thirtySecondA.getReason(), thirtySecondB.getReason());
    assertEquals(thirtySecondA.getMaximumRelativeDensityResidual(), thirtySecondB.getMaximumRelativeDensityResidual(),
        0.0);
    assertEquals(thirtySecondA.getFiniteVolumeMassResidualKg(), thirtySecondB.getFiniteVolumeMassResidualKg(), 0.0);
    assertArrayEquals(thirtySecondA.getNonlinearUpdateHistory(), thirtySecondB.getNonlinearUpdateHistory(), 0.0);
    assertArrayEquals(thirtySecondA.getDensityResidualHistory(), thirtySecondB.getDensityResidualHistory(), 0.0);

    assertTrue(fifteenSecond.isConverged());
    assertTrue(thirtySecondA.isConverged());
    assertEquals(2.0, thirtySecondA.getInletBoundaryMassKg() / fifteenSecond.getInletBoundaryMassKg(), 5.0e-3,
        "Integrated inlet mass must scale with timestep for the same boundary event.");
    assertTrue(fifteenSecond.getRelativeFiniteVolumeMassResidual() < fifteenSecond.getMassBalanceRelativeTolerance());
  }

  @Test
  void coupledSolveConvergesAcrossGridRefinement() {
    for (int nodes : new int[] { 3, 12, 40 }) {
      OnePhaseFlowConvergenceReport report = runCompositionStep(createInitializedPipe(nodes), 30.0);

      assertEquals(ConvergenceReason.CONVERGED, report.getReason(),
          "Coupled solve must converge for " + nodes + " nodes: " + report.getMessage());
      assertTrue(report.getMaximumRelativeDensityResidual() <= report.getDensityRelativeTolerance());
      assertTrue(report.getRelativeFiniteVolumeMassResidual() < report.getMassBalanceRelativeTolerance());
      assertTrue(report.getRelativeThermodynamicMassResidual() < report.getMassBalanceRelativeTolerance());
    }
  }

  @Test
  void compatibilityModeReturnsTheConvergedReportWithoutThrowing() {
    PipeFlowSystem pipe = createInitializedPipe();
    configureCompositionStep(pipe, 30.0);

    assertFalse(pipe.isFailOnNonConvergence());
    assertDoesNotThrow(() -> pipe.solveTransient(1));
    assertEquals(ConvergenceReason.CONVERGED, pipe.getConvergenceReport().getReason());
    assertTrue(pipe.getConvergenceReport().isNonlinearMetricEquationResidual());
    assertTrue(!ConvergenceReason.LINE_SEARCH_FAILED.isConverged());
    assertFalse(ConvergenceReason.NUMERICAL_FAILURE.isConverged());
    assertFalse(OnePhaseFlowConvergenceReport.notRun().isNonlinearMetricEquationResidual());
    assertDoesNotThrow(() -> OnePhaseFlowConvergenceReport.notRun().toJson());
  }

  @Test
  void legacyStagedSolverLabelsItsIterateChangeMetric() {
    PipeFlowSystem pipe = createInitializedPipe(3);
    configureCompositionStep(pipe, 30.0);

    assertDoesNotThrow(() -> pipe.solveTransient(20));
    assertFalse(pipe.getConvergenceReport().isNonlinearMetricEquationResidual());
    assertTrue(pipe.getConvergenceReport().getMessage().contains("relative nonlinear update="));
  }

  @Test
  void legacySteadySolverIsNotOverwrittenByHydraulicsOnlyRefinement() {
    PipeFlowSystem pipe = createInitializedPipe(3);

    assertDoesNotThrow(() -> pipe.solveSteadyState(20));
    assertFalse(pipe.getConvergenceReport().isNonlinearMetricEquationResidual());
  }

  @Test
  void reversedFlowFailsLoudlyOnTheCoupledPath() {
    PipeFlowSystem pipe = createInitializedPipe(3);
    configureCompositionStep(pipe, 30.0);
    pipe.getNode(2).setVelocityIn(-Math.abs(pipe.getNode(2).getVelocityIn().doubleValue()));
    pipe.setFailOnNonConvergence(true);

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.solveTransient(1));
    assertTrue(exception.getMessage().contains("supports positive flow only"),
        "Unvalidated reversed flow must fail with an actionable limit message.");
  }

  @Test
  void unchangedBoundaryIsAHydraulicFixedPoint() {
    PipeFlowSystem pipe = createInitializedPipe();
    double[] pressure = pressures(pipe);
    double[] velocity = velocities(pipe);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0 });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.95, 0.05) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.setFailOnNonConvergence(true);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertArrayEquals(pressure, pressures(pipe), 1.0e-8);
    assertArrayEquals(velocity, velocities(pipe), 1.0e-8);
  }

  private static OnePhaseFlowConvergenceReport runCompositionStep(PipeFlowSystem pipe, double timeStep) {
    configureCompositionStep(pipe, timeStep);
    pipe.setFailOnNonConvergence(true);
    assertTrue(pipe.isFailOnNonConvergence());

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    OnePhaseFlowConvergenceReport report = pipe.getConvergenceReport();
    assertTrue(report.isConverged(), report.getMessage());
    return report;
  }

  private static void configureCompositionStep(PipeFlowSystem pipe, double timeStep) {
    SystemInterface eventGas = createGas(0.80, 0.20);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStep });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { eventGas });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
  }

  private static PipeFlowSystem createInitializedPipe() {
    return createInitializedPipe(40);
  }

  private static PipeFlowSystem createInitializedPipe(int nodes) {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setInletThermoSystem(createGas(0.95, 0.05));
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(nodes);

    GeometryDefinitionInterface[] geometry = { new PipeData(), new PipeData() };
    for (GeometryDefinitionInterface section : geometry) {
      section.setDiameter(0.5);
      section.setInnerSurfaceRoughness(1.0e-5);
    }
    pipe.setEquipmentGeometry(geometry);
    pipe.setLegHeights(new double[] { 0.0, 0.0 });
    pipe.setLegPositions(new double[] { 0.0, 15000.0 });
    pipe.setLegOuterTemperatures(new double[] { TEMPERATURE_K, TEMPERATURE_K });
    pipe.setLegWallHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.setLegOuterHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(1);
    assertTrue(pipe.getConvergenceReport().isConverged());
    return pipe;
  }

  private static SystemInterface createGas(double methane, double nitrogen) {
    SystemInterface gas = new SystemSrkEos(TEMPERATURE_K, PRESSURE_BARA);
    gas.addComponent("methane", methane);
    gas.addComponent("nitrogen", nitrogen);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    gas.setTotalFlowRate(MASS_FLOW_KG_PER_SECOND, "kg/sec");
    return gas;
  }

  private static void assertAllFinite(double[] values) {
    for (double value : values) {
      assertTrue(Double.isFinite(value), "Residual history must contain only finite values.");
    }
  }

  private static double[] pressures(PipeFlowSystem pipe) {
    double[] values = new double[pipe.getTotalNumberOfNodes()];
    for (int i = 0; i < values.length; i++) {
      values[i] = pipe.getNode(i).getBulkSystem().getPressure();
    }
    return values;
  }

  private static double[] velocities(PipeFlowSystem pipe) {
    double[] values = new double[pipe.getTotalNumberOfNodes()];
    for (int i = 0; i < values.length; i++) {
      values[i] = pipe.getNode(i).getVelocityIn().doubleValue();
    }
    return values;
  }
}
