package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests first-class liquid pump and booster edges.
 */
class NetworkPumpTest {
  @Test
  void testFixedOutletPumpMatchesStandalonePump() {
    SystemInterface oil = createOil(5.0);
    LoopedPipeNetwork network = new LoopedPipeNetwork("oil booster");
    network.setFluidTemplate(oil);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(500.0);
    network.setCompositionalHydraulicsEnabled(true);
    network.setThermalHydraulicsEnabled(true);
    network.setCouplingMaxIterations(5);
    network.setCouplingTolerances(1.0e-3, 100.0);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 10.0);
    LoopedPipeNetwork.NetworkPipe pumpEdge = network.addPump("suction", "delivery", "export pump", 10.0, 0.75);
    pumpEdge.setPumpMinimumFlowKgS(0.01);
    pumpEdge.setPumpRatedPowerKW(10000.0);
    network.setNodeFluid("suction", oil);

    network.run();

    assertNotNull(pumpEdge.getOutletFluid());
    assertTrue(pumpEdge.getPumpPowerKW() > 0.0);
    assertTrue(pumpEdge.getPumpHeadM() > 0.0);
    assertTrue(pumpEdge.getPumpHeadM() < 500.0);
    assertEquals(0.0, pumpEdge.getPumpPowerResidualKW(), 1.0e-12);
    assertEquals(0.0, pumpEdge.getPumpMinimumFlowResidualKgS(), 1.0e-12);
    assertFalse("REVERSE_FLOW_BLOCKED".equals(pumpEdge.getPumpOperatingStatus()));

    Stream inlet = new Stream("standalone inlet", oil.clone());
    inlet.setFlowRate(Math.abs(pumpEdge.getFlowRate()), "kg/sec");
    inlet.run();
    Pump standalone = new Pump("standalone pump", inlet);
    standalone.setOutletPressure(10.0, "bara");
    standalone.setIsentropicEfficiency(0.75);
    standalone.run();
    assertEquals(standalone.getPower("kW"), pumpEdge.getPumpPowerKW(), Math.max(1.0, 0.02 * standalone.getPower("kW")));

    LoopedPipeNetwork restored = LoopedPipeNetwork.fromJson(network.toJson());
    LoopedPipeNetwork.NetworkPipe restoredPump = restored.getPipe("export pump");
    assertEquals(LoopedPipeNetwork.NetworkElementType.PUMP, restoredPump.getElementType());
    assertEquals(10.0, restoredPump.getPumpOutletPressurePa() / 1.0e5, 1.0e-12);
    assertEquals(pumpEdge.getPumpMinimumFlowKgS(), restoredPump.getPumpMinimumFlowKgS(), 1.0e-12);
  }

  @Test
  void testCurvePumpUsesSpeedAndReportsOperatingPoint() {
    SystemInterface oil = createOil(5.0);
    Pump configuredPump = new Pump("curve pump");
    double[] chartConditions = new double[] { 0.200, 298.15, 1.0, 1.0, 850.0 };
    double[] speeds = new double[] { 2000.0, 3000.0 };
    double[][] flows = new double[][] { { 20.0, 40.0, 60.0 }, { 30.0, 60.0, 90.0 } };
    double[][] heads = new double[][] { { 80.0, 70.0, 50.0 }, { 180.0, 158.0, 113.0 } };
    double[][] efficiencies = new double[][] { { 65.0, 75.0, 68.0 }, { 68.0, 80.0, 72.0 } };
    configuredPump.getPumpChart().setCurves(chartConditions, speeds, flows, heads, efficiencies);
    configuredPump.getPumpChart().setHeadUnit("meter");
    configuredPump.setSpeed(2500.0);

    LoopedPipeNetwork network = new LoopedPipeNetwork("curve booster");
    network.setFluidTemplate(oil);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(500.0);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 12.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "curve pump", configuredPump);
    edge.setPumpSpeed(2500.0);

    network.run();

    assertTrue(Double.isFinite(edge.getPumpHeadM()));
    assertTrue(Double.isFinite(edge.getPumpPowerKW()));
    assertNotNull(edge.getPumpOperatingStatus());
    assertFalse("NOT_RUN".equals(edge.getPumpOperatingStatus()));
  }

  @Test
  void testReverseFlowCheckValveDoesNotCreatePressureRise() {
    SystemInterface oil = createOil(5.0);
    LoopedPipeNetwork network = new LoopedPipeNetwork("reverse pump");
    network.setFluidTemplate(oil);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(20);
    network.setTolerance(500.0);
    network.addSourceNode("low", 5.0, 0.0);
    network.addFixedPressureSinkNode("high", 20.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPump("low", "high", "check valve pump", 10.0, 0.75);
    edge.setPumpReverseFlowPolicy(LoopedPipeNetwork.PumpReverseFlowPolicy.CHECK_VALVE);

    network.run();

    if (edge.getFlowRate() < 0.0) {
      assertEquals("REVERSE_FLOW_BLOCKED", edge.getPumpOperatingStatus());
      assertEquals(0.0, edge.getPumpPowerKW(), 1.0e-12);
    }
  }

  private SystemInterface createOil(double pressureBara) {
    SystemInterface oil = new SystemSrkEos(298.15, pressureBara);
    oil.addComponent("nC10", 0.70);
    oil.addComponent("nC16", 0.30);
    oil.setMixingRule("classic");
    return oil;
  }
}
