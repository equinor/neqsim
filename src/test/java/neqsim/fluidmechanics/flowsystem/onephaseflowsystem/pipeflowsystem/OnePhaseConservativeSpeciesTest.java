package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests conservative one-phase species transport coupled to hydraulic/EOS state. */
class OnePhaseConservativeSpeciesTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 288.15;
  private static final double PRESSURE_BARA = 70.0;
  private static final double MASS_FLOW_KG_PER_SECOND = 50.0;

  @Test
  void compositionStepClosesEveryComponentAndSynchronizesThermodynamics() {
    PipeFlowSystem pipe = runCompositionStep(40, 30.0);
    OnePhaseSpeciesConservationReport report = pipe.getSpeciesConservationReport();

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.CONVERGED, report.getReason(),
        report.getMessage());
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertTrue(pipe.getConvergenceReport().getMaximumRelativeDensityResidual() <= pipe.getConvergenceReport()
        .getDensityRelativeTolerance());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-8, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    assertTrue(report.getMaximumMassFractionSumError() <= 1.0e-12, report.getMessage());
    assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
    assertTrue(report.getCouplingIterations() > 0, report.getMessage());
    assertEquals(report.getCouplingIterations(), report.getMaximumMassFractionChangeHistory().length);
    assertEquals(report.getCouplingIterations(), report.getDensityResidualHistory().length);
    assertTrue(report.getMaximumMassFractionChangeHistory()[report.getCouplingIterations() - 1] <= 1.0e-10,
        report.getMessage());
    assertTrue(report.getDensityResidualHistory()[report.getCouplingIterations() - 1] <= pipe.getConvergenceReport()
        .getDensityRelativeTolerance(), report.getMessage());

    int nitrogen = 1;
    assertEquals(report.getFinalInventoryKg()[nitrogen] - report.getInitialInventoryKg()[nitrogen],
        report.getInletBoundaryMassKg()[nitrogen] - report.getOutletBoundaryMassKg()[nitrogen],
        Math.max(1.0, report.getInitialInventoryKg()[nitrogen]) * 1.0e-8);
    double firstCellNitrogen = pipe.getNode(1).getBulkSystem().getPhase(0).getComponent(nitrogen).getx();
    assertTrue(firstCellNitrogen > 0.05 && firstCellNitrogen < 0.20,
        "The bounded inlet front must enter the first physical cell: " + firstCellNitrogen);
  }

  @Test
  void conservativeSpeciesStepIsDeterministicAndTimestepSensitive() {
    PipeFlowSystem thirtySecondA = runCompositionStep(12, 30.0);
    PipeFlowSystem thirtySecondB = runCompositionStep(12, 30.0);
    PipeFlowSystem fifteenSecond = runCompositionStep(12, 15.0);

    OnePhaseSpeciesConservationReport reportA = thirtySecondA.getSpeciesConservationReport();
    OnePhaseSpeciesConservationReport reportB = thirtySecondB.getSpeciesConservationReport();
    assertArrayEquals(reportA.getFinalInventoryKg(), reportB.getFinalInventoryKg(), 0.0);
    assertArrayEquals(reportA.getInventoryResidualKg(), reportB.getInventoryResidualKg(), 0.0);
    assertArrayEquals(reportA.getMassFractionProfile()[1], reportB.getMassFractionProfile()[1], 0.0);

    double thirtySecondFront = thirtySecondA.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    double fifteenSecondFront = fifteenSecond.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    assertTrue(thirtySecondFront > fifteenSecondFront,
        "A longer first-order implicit step must advance more inlet tracer mass.");
    assertTrue(fifteenSecond.getSpeciesConservationReport().isConverged());
  }

  @Test
  void optInSpeciesTransportFailsLoudlyForReversedFlowWithoutLegacyStrictFlag() {
    PipeFlowSystem pipe = createInitializedPipe(3);
    pipe.setConservativeSpeciesTransport(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0 });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.getNode(2).setVelocityIn(-Math.abs(pipe.getNode(2).getVelocityIn().doubleValue()));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.solveTransient(1));
    assertTrue(exception.getMessage().contains("supports positive flow only"));
  }

  static PipeFlowSystem runCompositionStep(int nodes, double timeStep) {
    PipeFlowSystem pipe = createInitializedPipe(nodes);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStep });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe;
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
}
