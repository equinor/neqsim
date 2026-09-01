package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests EOS linepack and discrete-time inventory conservation.
 */
class NetworkPlanningHorizonTest {
  @Test
  void testTwoPeriodAnalyticalLinepackClosesExactly() {
    LoopedPipeNetwork network = createGasPipeline(90.0, 70.0, 288.15, 0.95);
    network.run();
    GasLinepackState initial = GasLinepackState.fromSolvedState(network, "pipeline");

    NetworkPlanningHorizon horizon = new NetworkPlanningHorizon(network);
    horizon.addHourlyPeriods("2026-01-01T00:00:00Z", 2);
    horizon.setInitialLinepack("pipeline", initial);
    horizon.addPipeFlowSchedule("pipeline", new double[] { 10.0, 12.0 }, new double[] { 12.0, 10.0 }, "kg/s");
    horizon.setTerminalLinepackTarget("pipeline", initial.getMassKg());

    NetworkScheduleResult result = horizon.optimize();

    assertTrue(result.isFeasible(), result.getMessage());
    assertEquals(initial.getMassKg(), result.getTerminalLinepack().get("pipeline").getMassKg(), 1.0e-6);
    assertEquals(0.0, result.getPeriods().get(0).getClosingLinepack().get("pipeline").getMassBalanceResidualKg(),
        1.0e-6);
    assertTrue(result.getPeriods().get(0).getClosingLinepack().get("pipeline").getMassKg() < initial.getMassKg());
    assertTrue(result.getPeriods().get(1).getClosingLinepack().get("pipeline").getMassKg() > result.getPeriods().get(0)
        .getClosingLinepack().get("pipeline").getMassKg());
  }

  @Test
  void testEosLinepackRespondsToPressureTemperatureAndComposition() {
    LoopedPipeNetwork lowPressure = createGasPipeline(60.0, 50.0, 288.15, 0.95);
    LoopedPipeNetwork highPressure = createGasPipeline(100.0, 90.0, 288.15, 0.95);
    LoopedPipeNetwork hot = createGasPipeline(100.0, 90.0, 320.0, 0.95);
    LoopedPipeNetwork rich = createGasPipeline(100.0, 90.0, 288.15, 0.70);
    lowPressure.run();
    highPressure.run();
    hot.run();
    rich.run();

    GasLinepackState low = GasLinepackState.fromSolvedState(lowPressure, "pipeline");
    GasLinepackState high = GasLinepackState.fromSolvedState(highPressure, "pipeline");
    GasLinepackState hotState = GasLinepackState.fromSolvedState(hot, "pipeline");
    GasLinepackState richState = GasLinepackState.fromSolvedState(rich, "pipeline");

    assertTrue(high.getMolarInventoryMol() > low.getMolarInventoryMol());
    assertTrue(hotState.getMolarInventoryMol() < high.getMolarInventoryMol());
    assertTrue(richState.getMassKg() > high.getMassKg());
    assertTrue(Double.isFinite(high.getCompressibilityFactor()));
    assertTrue(high.getStandardVolumeSm3() > 0.0);
  }

  @Test
  void testScheduleJsonRoundTripAndOutageDiagnostic() {
    LoopedPipeNetwork network = createGasPipeline(90.0, 70.0, 288.15, 0.95);
    network.run();
    NetworkPlanningHorizon horizon = new NetworkPlanningHorizon(network);
    horizon.addHourlyPeriods("2026-01-01T00:00:00Z", 2);
    horizon.setInitialLinepackFromSolvedState();
    horizon.derateElement("pipeline", 0, 1, 0.5);
    NetworkScheduleResult result = horizon.optimize();
    NetworkScheduleResult restored = NetworkScheduleResult.fromJson(result.toJson());

    assertNotNull(restored);
    assertEquals(2, restored.getPeriods().size());
    assertFalse(restored.toJson().isEmpty());
    assertEquals(1.0, network.getPipe("pipeline").getAvailability(), 1.0e-12);
  }

  private LoopedPipeNetwork createGasPipeline(double sourceBar, double sinkBar, double temperatureK,
      double methaneFraction) {
    SystemInterface gas = new SystemSrkEos(temperatureK, sourceBar);
    gas.addComponent("methane", methaneFraction);
    gas.addComponent("ethane", 1.0 - methaneFraction);
    gas.setMixingRule("classic");
    LoopedPipeNetwork network = new LoopedPipeNetwork("linepack network");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(500.0);
    network.addSourceNode("source", sourceBar, 0.0);
    network.addFixedPressureSinkNode("delivery", sinkBar);
    network.addPipe("source", "delivery", "pipeline", 100000.0, 1.0);
    network.getNode("source").setTemperature(temperatureK);
    network.getNode("delivery").setTemperature(temperatureK);
    network.setNodeFluid("source", gas);
    return network;
  }
}
