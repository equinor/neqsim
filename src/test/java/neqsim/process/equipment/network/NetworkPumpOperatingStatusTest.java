package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Same-network speed migration with synthetic curves, not a qualified operating envelope.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NetworkPumpOperatingStatusTest extends NeqSimTest {
  /** Absolute-flow spline diagnostics migrate without rebuilding the pump, topology or host. */
  @Test
  void alternativeMapMigrationRetainsItsOwnInterpolationAndHostIdentity() {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    Stream reference = new Stream("alternative suction reference", fluid);
    reference.run();
    reference.getFluid().initProperties();
    double kgSPerM3Hr = reference.getFluid().getDensity("kg/m3") / 3600.0;
    LoopedPipeNetwork network = new LoopedPipeNetwork("alternative injection pump migration");
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(20);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addSinkNode("delivery", 40.0 * kgSPerM3Hr * 3600.0);
    Pump pump = new Pump("existing alternative mapped pump");
    pump.setPumpChartType("interpolate and extrapolate");
    pump.getPumpChart().setCurves(new double[0], new double[] {1000.0, 2000.0},
        new double[][] {{0, 20, 40, 60, 80, 100}, {0, 20, 40, 60, 80, 100}},
        new double[][] {{200, 180, 160, 140, 120, 100}, {400, 380, 360, 340, 320, 300}},
        new double[][] {{64, 76, 80, 76, 64, 44}, {44, 64, 76, 80, 76, 64}});
    pump.getPumpChart().setUsePumpChart(true);
    pump.getPumpChart().setHeadUnit("meter");
    pump.setSpeed(1000.0);
    Object chart = pump.getPumpChart();
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "pump", pump);
    ProcessSystem area = new ProcessSystem();
    area.add(network);
    ProcessModel host = new ProcessModel();
    host.add("injection", area);
    network.run();
    assertState(network, edge, pump, host, 40.0 * kgSPerM3Hr, "OPTIMAL");
    double originalPressure = network.getNodePressure("delivery");
    double originalPower = host.getPower("kW");

    double[][] points = {{2000.0, 40.0, 360.0}, {2000.0, 60.0, 340.0}, {1500.0, 50.0, 250.0}, {1000.0, 40.0, 160.0}};
    for (int index = 0; index < points.length; index++) {
      edge.setPumpSpeed(points[index][0]);
      network.getNode("delivery").setDemand(points[index][1] * kgSPerM3Hr);
      network.run();
      assertState(network, edge, pump, host, points[index][1] * kgSPerM3Hr, index == 0 ? "NORMAL" : "OPTIMAL");
      assertEquals(points[index][2], edge.getPumpHeadM(), 1e-6,
          "Linear head curves interpolate at absolute flow, not affinity-scaled flow");
      assertSame(chart, pump.getPumpChart());
      assertSame(edge, network.getPipe("pump"));
    }
    assertEquals(originalPressure, network.getNodePressure("delivery"), 1e-7);
    assertEquals(originalPower, host.getPower("kW"), 1e-8);
  }

  /** Status follows speed/demand changes while canonical identity and hydraulic similarity survive. */
  @Test
  void speedMigrationAndRestorationUseTheExistingPumpAndHost() {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    Stream reference = new Stream("suction reference", fluid);
    reference.run();
    reference.getFluid().initProperties();
    double baseFlowKgS = 50.0 / 3600.0 * reference.getFluid().getDensity("kg/m3");

    LoopedPipeNetwork network = new LoopedPipeNetwork("injection pump speed migration");
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(20);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addSinkNode("delivery", baseFlowKgS * 3600.0);
    Pump pump = new Pump("existing mapped pump");
    pump.getPumpChart().setCurves(new double[0], new double[] {1000.0}, new double[][] {{10.0, 30.0, 50.0, 70.0, 90.0}},
        new double[][] {{190.0, 170.0, 150.0, 130.0, 110.0}}, new double[][] {{64.0, 76.0, 80.0, 76.0, 64.0}});
    pump.getPumpChart().setHeadUnit("meter");
    pump.setSpeed(1000.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "pump", pump);
    ProcessSystem area = new ProcessSystem();
    area.add(network);
    ProcessModel host = new ProcessModel();
    host.add("injection", area);

    network.run();
    assertState(network, edge, pump, host, baseFlowKgS, "OPTIMAL");
    double baseHead = edge.getPumpHeadM();
    double basePower = host.getPower("kW");
    double basePressure = network.getNodePressure("delivery");
    assertEquals(150.0, baseHead, 1e-6);

    // Change speed alone: fixed delivery is now above the reduced-map flow boundary.
    edge.setPumpSpeed(500.0);
    network.run();
    assertState(network, edge, pump, host, baseFlowKgS, "STONEWALL");
    // A curve diagnostic is not an automatic operating constraint: the hydraulic solve can converge.

    // Change speed and demand together to preserve Q/N. H scales with N^2 and shaft power with N^3.
    edge.setPumpSpeed(2000.0);
    network.getNode("delivery").setDemand(2.0 * baseFlowKgS);
    network.run();
    assertState(network, edge, pump, host, 2.0 * baseFlowKgS, "OPTIMAL");
    assertEquals(4.0 * baseHead, edge.getPumpHeadM(), 1e-5);
    // One ppm of the expected power allows the EOS flash/enthalpy recovery roundoff.
    assertEquals(8.0 * basePower, host.getPower("kW"), 1e-6 * 8.0 * basePower);

    edge.setPumpSpeed(1000.0);
    network.getNode("delivery").setDemand(baseFlowKgS);
    network.run();
    assertState(network, edge, pump, host, baseFlowKgS, "OPTIMAL");
    assertEquals(basePressure, network.getNodePressure("delivery"), 1e-7);
    assertEquals(basePower, host.getPower("kW"), 1e-8);
  }

  /**
   * Check conservation, physical work, diagnostic propagation and object ownership after each solve.
   *
   * @param network canonical network
   * @param edge original pump edge
   * @param pump original chart-owning pump
   * @param host canonical host process model
   * @param expectedFlowKgS demanded mass flow in kg/s
   * @param status expected reduced-curve diagnostic
   */
  private void assertState(LoopedPipeNetwork network, LoopedPipeNetwork.NetworkPipe edge, Pump pump, ProcessModel host,
      double expectedFlowKgS, String status) {
    assertTrue(network.isConverged());
    assertTrue(pump.solved());
    assertSame(pump, edge.getPumpModel());
    assertEquals(status, edge.getPumpOperatingStatus());
    assertEquals(expectedFlowKgS, edge.getFlowRate(), 1e-8);
    assertTrue(network.getMassBalanceError() < 1e-8);
    assertTrue(network.getNodePressure("delivery") > 5.0);
    assertTrue(edge.getPumpPowerKW() > 0.0);
    assertEquals(edge.getPumpPowerKW(), host.getPower("kW"), 1e-10);
    assertTrue(Double.isFinite(edge.getOutletFluid().getTemperature()));
    double hydraulicPowerKw = edge.getFlowRate() * edge.getPumpHeadM() * 9.80665 / 1000.0;
    assertEquals(hydraulicPowerKw / edge.getPumpEfficiency(), edge.getPumpPowerKW(), 1e-5 * edge.getPumpPowerKW());
  }
}
