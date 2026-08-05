package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for phase-resolved transient mass conservation. */
class TwoFluidPipeMassBalanceTest {
  private static final double ABSOLUTE_BALANCE_TOLERANCE_KG = 1.0e-7;
  private static final double RELATIVE_BALANCE_TOLERANCE = 1.0e-10;

  @Test
  void testOpenBoundaryBalanceClosesAcrossTimeAndMeshRefinement() {
    TwoFluidMassBalanceReport coarse = runOpenBoundaryCase(6, 1.0e-3, TimeIntegrator.Method.RK4);
    TwoFluidMassBalanceReport fine = runOpenBoundaryCase(12, 5.0e-4, TimeIntegrator.Method.RK4);

    assertReportCloses(coarse);
    assertReportCloses(fine);
    assertTrue(coarse.getInletMassKg(Phase.TOTAL) > 0.0);
    assertTrue(coarse.getOutletMassKg(Phase.TOTAL) > 0.0);
    assertTrue(Math.abs(fine.getResidualKg(Phase.TOTAL)) <= 10.0 * Math.abs(coarse.getResidualKg(Phase.TOTAL))
        + ABSOLUTE_BALANCE_TOLERANCE_KG);
  }

  @Test
  void testClosedBoundariesConserveEveryPhase() {
    TwoFluidPipe pipe = createPipe("closed-balance", 8);
    pipe.run();
    pipe.closeInlet();
    pipe.closeOutlet();

    pipe.runTransient(1.0e-3, UUID.fromString("00000000-0000-0000-0000-000000002705"));

    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertNotNull(report);
    assertEquals(0.0, report.getInletMassKg(Phase.TOTAL), 1.0e-12);
    assertEquals(0.0, report.getOutletMassKg(Phase.TOTAL), 1.0e-12);
    assertEquals(0.0, pipe.getOutletStream().getFlowRate("kg/sec"), 1.0e-12);
    assertEquals(report.getInitialMassKg(Phase.TOTAL), report.getFinalMassKg(Phase.TOTAL),
        ABSOLUTE_BALANCE_TOLERANCE_KG);
    assertReportCloses(report);
  }

  @Test
  void testImexPressureCorrectionDoesNotChangePhaseMass() {
    TwoFluidPipe pipe = createPipe("imex-balance", 8);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.run();

    pipe.runTransient(1.0e-3, UUID.fromString("00000000-0000-0000-0000-000000012705"));

    assertReportCloses(pipe.getLastMassBalanceReport());
  }

  @Test
  void testStageWeightedReportClosesForEveryExplicitIntegrator() {
    TimeIntegrator.Method[] methods = { TimeIntegrator.Method.EULER, TimeIntegrator.Method.RK2,
        TimeIntegrator.Method.SSP_RK3 };

    for (TimeIntegrator.Method method : methods) {
      TwoFluidPipe pipe = createPipe("stage-balance-" + method, 6);
      pipe.setTimeIntegrationMethod(method);
      pipe.run();
      pipe.runTransient(1.0e-3, UUID.fromString("00000000-0000-0000-0000-000000042705"));

      assertReportCloses(pipe.getLastMassBalanceReport());
    }
  }

  @Test
  void testFlashDrivenPhaseTransferCancelsFromTotalMass() {
    TwoFluidPipe pipe = createPipe("phase-transfer-balance", 8);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(5.0);
    pipe.run();

    pipe.runTransient(1.0e-3, UUID.fromString("00000000-0000-0000-0000-000000022705"));

    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertNotNull(report);
    assertEquals(0.0, report.getSourceMassKg(Phase.TOTAL), 1.0e-12);
    assertEquals(-report.getSourceMassKg(Phase.GAS), report.getSourceMassKg(Phase.LIQUID), 1.0e-12);
    assertReportCloses(report);
  }

  @Test
  void testTransientOutletStreamUsesConservativeIntervalAverageFlux() {
    TwoFluidPipe pipe = createPipe("outlet-flux", 8);
    pipe.run();

    pipe.getInletStream().setFlowRate(1.5, "kg/sec");
    pipe.getInletStream().run();
    pipe.runTransient(1.0e-3, UUID.fromString("00000000-0000-0000-0000-000000052705"));

    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertNotNull(report, "A completed transient step should publish its mass-balance report");
    assertTrue(report.getElapsedTimeSeconds() > 0.0,
        "Accepted transient elapsed time must be positive before calculating an interval-average flux");
    double conservativeOutletFlow = report.getOutletMassKg(Phase.TOTAL) / report.getElapsedTimeSeconds();
    double exposedOutletFlow = pipe.getOutletStream().getFlowRate("kg/sec");

    assertTrue(Math.abs(conservativeOutletFlow - pipe.getInletStream().getFlowRate("kg/sec")) > 0.1,
        "Short-step outlet flux should retain transport memory after the inlet flow change");
    assertEquals(conservativeOutletFlow, exposedOutletFlow, 1.0e-9,
        "Transient outlet stream should expose the accepted interval-average flux");
  }

  private TwoFluidMassBalanceReport runOpenBoundaryCase(int sections, double timeStep,
      TimeIntegrator.Method integrationMethod) {
    TwoFluidPipe pipe = createPipe("open-balance-" + sections, sections);
    pipe.setTimeIntegrationMethod(integrationMethod);
    pipe.run();
    pipe.runTransient(timeStep, UUID.fromString("00000000-0000-0000-0000-000000032705"));
    return pipe.getLastMassBalanceReport();
  }

  private TwoFluidPipe createPipe(String name, int sections) {
    SystemInterface fluid = new SystemSrkEos(293.15, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("nC10", 0.02);
    fluid.addComponent("water", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name + "-pipe", inlet);
    pipe.setLength(300.0);
    pipe.setDiameter(0.15);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(sections);
    pipe.setOutletPressure(60.0, "bara");
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(1.0);
    return pipe;
  }

  private void assertReportCloses(TwoFluidMassBalanceReport report) {
    assertNotNull(report);
    assertTrue(report.getAcceptedSubsteps() > 0);
    for (Phase phase : Phase.values()) {
      assertTrue(report.isWithinTolerance(phase, ABSOLUTE_BALANCE_TOLERANCE_KG, RELATIVE_BALANCE_TOLERANCE),
          phase + " residual was " + report.getResidualKg(phase) + " kg (relative " + report.getRelativeResidual(phase)
              + ")");
    }
  }
}
