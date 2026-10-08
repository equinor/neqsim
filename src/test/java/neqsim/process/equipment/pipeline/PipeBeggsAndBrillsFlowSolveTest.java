package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for target-pressure flow acceptance.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipeBeggsAndBrillsFlowSolveTest {
  /**
   * Creates the public issue reproducer.
   *
   * @return configured pipeline
   */
  private PipeBeggsAndBrills pipe() {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 100.0);
    fluid.addComponent("methane", 0.94);
    fluid.addComponent("nC10", 0.06);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(10.0, "kg/sec");
    inlet.run();
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", inlet);
    pipe.setLength(40000.0);
    pipe.setDiameter(0.35);
    pipe.setPipeWallRoughness(4.5e-5);
    pipe.setNumberOfIncrements(30);
    pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ISOTHERMAL);
    pipe.setOutletPressure(80.0, "bara");
    return pipe;
  }

  /** A hydraulic-domain bound retains the public invalid-output cause used by candidate rejection. */
  @Test
  void forwardHydraulicFailurePreservesInvalidOutputCauseAndRecovers() {
    PipeBeggsAndBrills pipe = pipe();
    pipe.setCalculationMode(PipeBeggsAndBrills.CalculationMode.CALCULATE_OUTLET_PRESSURE);
    pipe.run();
    assertTrue(pipe.getOutletStream().getPressure("bara") > 0.0);
    pipe.getInletStream().setFlowRate(1000.0, "kg/sec");
    pipe.getInletStream().run();
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> pipe.run());
    assertTrue(failure.getCause() instanceof neqsim.util.exception.InvalidOutputException);
    assertTrue(failure.getCause().getMessage().contains("Outlet pressure is negative"));
    pipe.getInletStream().setFlowRate(10.0, "kg/sec");
    pipe.getInletStream().run();
    pipe.run();
    assertTrue(pipe.getOutletStream().getPressure("bara") > 0.0);
    assertNull(pipe.getFlowSolveReport());
  }

  /** An exhausted iteration budget must never return a capacity. */
  @Test
  void iterationLimitThrowsAndRestoresInlet() {
    PipeBeggsAndBrills pipe = pipe();
    pipe.setMaxFlowIterations(1);
    assertThrows(IllegalStateException.class, () -> pipe.run());
    assertEquals(FlowSolveReport.TerminationReason.ITERATION_LIMIT, pipe.getFlowSolveReport().getTerminationReason());
    assertEquals(1, pipe.getFlowSolveReport().getIterations());
    assertFalse(pipe.getFlowSolveReport().isConverged());
    assertNull(pipe.getCalculationIdentifier());
    assertFalse(pipe.solved());
    assertTrue(Math.abs(pipe.getFlowSolveReport().getPressureResidualBar()) > pipe.getFlowSolveReport()
        .getPressureToleranceBar());
    assertEquals(10.0, pipe.getInletStream().getFlowRate("kg/sec"), 1e-10);
  }

  /** Normal-budget solve qualifies the final replay and supports repeat runs after failure. */
  @Test
  void convergedReplayAndJson() {
    PipeBeggsAndBrills pipe = pipe();
    pipe.setMaxFlowIterations(1);
    assertThrows(IllegalStateException.class, () -> pipe.run());
    pipe.setMaxFlowIterations(50);
    pipe.run();
    FlowSolveReport report = pipe.getFlowSolveReport();
    assertTrue(report.isConverged());
    assertEquals(80.0, pipe.getOutletStream().getPressure("bara"), 0.008);
    assertEquals(pipe.getOutletStream().getPressure("bara") - 80.0, report.getPressureResidualBar(), 1e-10);
    assertEquals(pipe.getInletStream().getFlowRate("kg/hr"), report.getCandidateFlowKgPerHour(), 1e-7);
    assertEquals("CONVERGED", com.google.gson.JsonParser.parseString(pipe.toJson()).getAsJsonObject()
        .getAsJsonObject("flowSolveReport").get("terminationReason").getAsString());
    pipe.run();
    assertTrue(pipe.getFlowSolveReport().isConverged());
    pipe.setCalculationMode(PipeBeggsAndBrills.CalculationMode.CALCULATE_OUTLET_PRESSURE);
    pipe.run();
    assertNull(pipe.getFlowSolveReport());
  }

  /**
   * Fault-injected forward solver, independent of physical property library failure details.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class FaultPipe extends PipeBeggsAndBrills {
    private static final long serialVersionUID = 1L;
    private final int fault;
    private int calls;

    /**
     * Creates a deterministic failure.
     *
     * @param inlet inlet stream
     * @param fault failure type
     */
    FaultPipe(neqsim.process.equipment.stream.StreamInterface inlet, int fault) {
      super("fault", inlet);
      this.fault = fault;
      setLength(40000.0);
      setDiameter(0.35);
      setOutletPressure(80.0, "bara");
    }

    /** {@inheritDoc} */
    @Override
    protected double evaluateFlowPressure(double flowRate, java.util.UUID id) {
      calls++;
      getInletStream().setFlowRate(flowRate, "kg/hr");
      if (fault == 0) {
        throw new IllegalArgumentException("injected property failure");
      }
      if (fault == 1) {
        return Double.NaN;
      }
      if (fault == 2) {
        return 90.0;
      }
      if (fault == 3) {
        return calls == 1 ? 80.0 : 81.0;
      }
      return Double.NEGATIVE_INFINITY;
    }
  }

  /** Classifies failures and restores the exact original inlet fluid, even after mutation. */
  @Test
  void diagnosticFailuresRestoreBaseline() {
    FlowSolveReport.TerminationReason[] expected = {FlowSolveReport.TerminationReason.INNER_FAILURE,
        FlowSolveReport.TerminationReason.NON_FINITE_OUTPUT, FlowSolveReport.TerminationReason.BRACKET_LIMIT,
        FlowSolveReport.TerminationReason.REPLAY_FAILURE, FlowSolveReport.TerminationReason.NON_FINITE_OUTPUT};
    for (int fault = 0; fault < expected.length; fault++) {
      PipeBeggsAndBrills original = pipe();
      FaultPipe pipe = new FaultPipe(original.getInletStream(), fault);
      neqsim.thermo.system.SystemInterface baseline = pipe.getInletStream().getFluid();
      IllegalStateException failure = assertThrows(IllegalStateException.class, () -> pipe.run());
      assertNotNull(failure.getCause());
      assertEquals(expected[fault], pipe.getFlowSolveReport().getTerminationReason());
      assertSame(baseline, pipe.getInletStream().getFluid());
      assertEquals(10.0, baseline.getFlowRate("kg/sec"), 1e-10);
      assertFalse(pipe.getFlowSolveReport().toJson().contains("NaN"));
      assertFalse(pipe.getFlowSolveReport().toJson().contains("Infinity"));
      if (fault == 2) {
        assertEquals(20, pipe.getFlowSolveReport().getBracketIterations());
      }
    }
  }

  /** Invalid settings must receive fresh diagnostics without touching the inlet. */
  @Test
  void invalidInputs() {
    for (int variant = 0; variant < 6; variant++) {
      PipeBeggsAndBrills pipe = pipe();
      if (variant == 0) {
        pipe.setFlowConvergenceTolerance(Double.NaN);
      }
      if (variant == 1) {
        pipe.setMaxFlowIterations(0);
      }
      if (variant == 2) {
        pipe.setDiameter(Double.NaN);
      }
      if (variant == 3) {
        pipe.setOutletPressure(Double.POSITIVE_INFINITY, "bara");
      }
      if (variant == 4) {
        pipe.getInletStream().setFlowRate(0.0, "kg/hr");
      }
      if (variant == 5) {
        pipe.setOutletPressure(120.0, "bara");
      }
      double original = pipe.getInletStream().getFlowRate("kg/hr");
      assertThrows(IllegalStateException.class, () -> pipe.run());
      assertEquals(FlowSolveReport.TerminationReason.INVALID_INPUT, pipe.getFlowSolveReport().getTerminationReason());
      assertEquals(original, pipe.getInletStream().getFlowRate("kg/hr"), 1e-8);
    }
  }

  /**
   * Accepted evidence remains immutable through a later failed run and Java serialization.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void acceptedThenFailedAndSerializedReport() throws Exception {
    PipeBeggsAndBrills pipe = pipe();
    pipe.run();
    FlowSolveReport accepted = pipe.getFlowSolveReport();
    neqsim.thermo.system.SystemInterface inlet = pipe.getInletStream().getFluid();
    neqsim.thermo.system.SystemInterface outlet = pipe.getOutletStream().getFluid();
    java.util.UUID inletId = pipe.getInletStream().getCalculationIdentifier();
    java.util.UUID outletId = pipe.getOutletStream().getCalculationIdentifier();
    pipe.setOutletPressure(70.0, "bara");
    pipe.setMaxFlowIterations(1);
    assertThrows(IllegalStateException.class, () -> pipe.run());
    assertFalse(pipe.solved());
    assertTrue(accepted.isConverged());
    assertSame(inlet, pipe.getInletStream().getFluid());
    assertSame(outlet, pipe.getOutletStream().getFluid());
    assertEquals(inletId, pipe.getInletStream().getCalculationIdentifier());
    assertEquals(outletId, pipe.getOutletStream().getCalculationIdentifier());
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.ObjectOutputStream output = new java.io.ObjectOutputStream(bytes)) {
      output.writeObject(accepted);
    }
    try (java.io.ObjectInputStream input = new java.io.ObjectInputStream(
        new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      assertEquals(accepted.toJson(), ((FlowSolveReport) input.readObject()).toJson());
    }
  }

  /** Target pressure units are converted before residual acceptance. */
  @Test
  void pressureUnits() {
    PipeBeggsAndBrills pipe = pipe();
    pipe.setOutletPressure(8.0, "MPa");
    pipe.run();
    assertEquals(80.0, pipe.getOutletStream().getPressure("bara"), 0.008);
    assertEquals(0.008, pipe.getFlowSolveReport().getPressureToleranceBar(), 1e-12);
  }
}
