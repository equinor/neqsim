package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void compositionStepClosesFiniteVolumeMassAndFailsOnStaleEosDensity() {
    PipeFlowSystem pipe = createInitializedPipe();
    OnePhaseFlowConvergenceReport report = runCompositionStep(pipe, 30.0);

    assertEquals(ConvergenceReason.MAX_ITERATIONS_REACHED, report.getReason());
    assertEquals(100, report.getNonlinearIterations());
    assertTrue(report.getRelativeFiniteVolumeMassResidual() < 1.0e-12,
        "The authoritative finite-volume inventory must close to roundoff: " + report.getFiniteVolumeMassResidualKg()
            + " kg");
    assertTrue(report.getMaximumRelativeDensityResidual() > report.getDensityRelativeTolerance(),
        "The unconverged EOS density must remain visible.");
    assertTrue(report.getRelativeThermodynamicMassResidual() > report.getMassBalanceRelativeTolerance(),
        "Thermodynamic inventory must not be accepted while EOS density is stale.");

    double impliedInletMassFlow = report.getInletBoundaryMassKg() / 30.0;
    assertTrue(impliedInletMassFlow > 45.0 && impliedInletMassFlow < 60.0,
        "The prescribed inlet density must supply the approximately 50 kg/s boundary flux: " + impliedInletMassFlow
            + " kg/s");

    double[] nonlinearHistory = report.getNonlinearUpdateHistory();
    double[] densityHistory = report.getDensityResidualHistory();
    assertEquals(report.getNonlinearIterations(), nonlinearHistory.length);
    assertEquals(report.getNonlinearIterations(), densityHistory.length);
    assertTrue(nonlinearHistory[0] > nonlinearHistory[nonlinearHistory.length - 1],
        "The update history must show the converged algebraic iterate.");
    assertAllFinite(nonlinearHistory);
    assertAllFinite(densityHistory);
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

    assertTrue(fifteenSecond.getMaximumRelativeDensityResidual() < thirtySecondA.getMaximumRelativeDensityResidual(),
        "The shorter step must expose a smaller EOS/FV density inconsistency.");
    assertTrue(
        Math.abs(fifteenSecond.getThermodynamicMassResidualKg()) < Math
            .abs(thirtySecondA.getThermodynamicMassResidualKg()),
        "The shorter step must expose a smaller EOS inventory inconsistency.");
    assertTrue(fifteenSecond.getRelativeFiniteVolumeMassResidual() < 1.0e-12);
  }

  private static OnePhaseFlowConvergenceReport runCompositionStep(PipeFlowSystem pipe, double timeStep) {
    SystemInterface eventGas = createGas(0.80, 0.20);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStep });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { eventGas });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> pipe.solveTransient(20));
    OnePhaseFlowConvergenceReport report = pipe.getConvergenceReport();
    assertEquals(report.getMessage(), failure.getMessage());
    return report;
  }

  private static PipeFlowSystem createInitializedPipe() {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setInletThermoSystem(createGas(0.95, 0.05));
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(40);

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
    pipe.solveSteadyState(20);
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
}
