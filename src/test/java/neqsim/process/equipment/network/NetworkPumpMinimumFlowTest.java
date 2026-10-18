package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Separates stable operating limits from numerical pump bypass thresholds.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NetworkPumpMinimumFlowTest extends NeqSimTest {
  /** Both fixed-pressure modes retain physical work below the stable operating limit. */
  @Test
  void stableLimitDoesNotBypassFixedPressurePhysics() {
    for (boolean differential : new boolean[] {false, true}) {
      LoopedPipeNetwork network = network(0.0005);
      LoopedPipeNetwork.NetworkPipe edge = differential
          ? network.addPumpDifferentialPressure("suction", "delivery", "pump", 5.0, 0.75)
          : network.addPump("suction", "delivery", "pump", 10.0, 0.75);
      edge.setPumpMinimumFlowKgS(2.0);
      network.run();

      assertTrue(network.isConverged());
      assertTrue(edge.getPumpModel().isActive());
      assertEquals(1e-20, edge.getPumpModel().getMinimumFlow(), 1e-30);
      assertEquals(0.0005, edge.getFlowRate(), 1e-10);
      assertEquals(10.0, network.getNodePressure("delivery"), 1e-5);
      assertEquals(1.9995, edge.getPumpMinimumFlowResidualKgS(), 1e-10);
      assertEquals("BELOW_MINIMUM_FLOW", edge.getPumpOperatingStatus());
      assertTrue(edge.getPumpPowerKW() > 0.0);
      assertTrue(network.getMassBalanceError() < 1e-8);

      Pump standalone = new Pump("reference", new Stream("feed", fluid()));
      standalone.getInletStream().setFlowRate(0.0005, "kg/sec");
      standalone.getInletStream().run();
      standalone.setOutletPressure(10.0, "bara");
      standalone.setIsentropicEfficiency(0.75);
      standalone.run();
      assertEquals(standalone.getPower("kW"), edge.getPumpPowerKW(), 1e-8);
    }
  }

  /** A stable-flow constraint changes feasibility, not chart identity, pressure or power. */
  @Test
  void mappedStableLimitRemainsSeparateFromConfiguredBypass() {
    LoopedPipeNetwork network = network(1.0);
    Pump pump = mappedPump();
    pump.setMinimumFlow(17.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "pump", pump);
    edge.setPumpMinimumFlowKgS(2.0);
    network.run();

    assertTrue(network.isConverged());
    assertEquals(17.0, pump.getMinimumFlow(), 0.0);
    assertEquals(1.0, edge.getPumpMinimumFlowResidualKgS(), 1e-8);
    assertFalse(NetworkConstraints.edgeFlow("pump", 2.0 * 3600.0, 3.0 * 3600.0, true).evaluate(network).isSatisfied());
    double pressure = network.getNodePressure("delivery");
    double power = edge.getPumpPowerKW();
    assertTrue(power > 0.0);
    // The chart mode uses hydraulic work with physical density; the default fixed-pressure
    // mode instead uses an EOS isentropic enthalpy change, not this incompressible formula.
    double hydraulicPowerKw = edge.getFlowRate() * (pressure - 5.0) * 1e5
        / pump.getInletStream().getFluid().getDensity("kg/m3") / 1000.0;
    double shaftPowerKw = hydraulicPowerKw / edge.getPumpEfficiency();
    assertEquals(shaftPowerKw, power, 1e-5 * shaftPowerKw);

    edge.setPumpMinimumFlowKgS(0.5);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(0.0, edge.getPumpMinimumFlowResidualKgS(), 1e-8);
    assertTrue(NetworkConstraints.edgeFlow("pump", 0.5 * 3600.0, 3.0 * 3600.0, true).evaluate(network).isSatisfied());
    assertEquals(pressure, network.getNodePressure("delivery"), 1e-7);
    assertEquals(power, edge.getPumpPowerKW(), 1e-8);
    assertEquals(17.0, pump.getMinimumFlow(), 0.0);
    assertSame(pump, edge.getPumpModel());
    assertTrue(network.getMassBalanceError() < 1e-8);
  }

  /** A caller-selected numerical bypass cannot masquerade as zero-power pumping. */
  @Test
  void explicitBypassIsRejectedAndCanRecover() {
    LoopedPipeNetwork network = network(1.0);
    Pump pump = mappedPump();
    pump.setMinimumFlow(1800.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "pump", pump);
    ProcessSystem area = new ProcessSystem();
    area.add(network);
    ProcessModel host = new ProcessModel();
    host.add("injection", area);
    network.run();
    assertTrue(network.isConverged());
    double power = host.getPower("kW");
    assertTrue(power > 0.0);

    pump.setMinimumFlow(7200.0);
    network.run();
    assertEquals(7200.0, pump.getMinimumFlow(), 0.0);
    assertFalse(pump.isActive());
    assertEquals("INFEASIBLE", edge.getPumpOperatingStatus());
    assertEquals("Pump-INFEASIBLE", edge.getFlowRegime());
    assertTrue(Double.isNaN(host.getPower("kW")));
    assertTrue(Double.isNaN(edge.getPumpHeadM()));
    assertTrue(Double.isNaN(edge.getPumpMinimumFlowResidualKgS()));
    assertNull(edge.getOutletFluid());

    pump.setMinimumFlow(1800.0);
    network.run();
    assertTrue(network.isConverged());
    assertTrue(pump.isActive());
    assertEquals(1800.0, pump.getMinimumFlow(), 0.0);
    assertEquals(power, host.getPower("kW"), 1e-8);
    assertEquals(1.0, edge.getFlowRate(), 1e-8);
    assertSame(pump, edge.getPumpModel());
  }

  /**
   * Build a single-pump demand network without over-specifying delivery pressure.
   *
   * @param demandKgS delivered mass flow in kg/s
   * @return canonical network awaiting its pump edge
   */
  private LoopedPipeNetwork network(double demandKgS) {
    LoopedPipeNetwork network = new LoopedPipeNetwork("pump flow semantics");
    network.setFluidTemplate(fluid());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(20);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addSinkNode("delivery", demandKgS * 3600.0);
    return network;
  }

  /**
   * Create a synthetic curve containing the 1 kg/s water operating point.
   *
   * @return configured pump with an enabled map
   */
  private Pump mappedPump() {
    Pump pump = new Pump("mapped pump");
    pump.getPumpChart().setCurves(new double[] {0.018, 298.15, 1.0, 1.0, 1000.0}, new double[] {3000.0},
        new double[][] {{1.0, 4.0, 8.0}}, new double[][] {{60.0, 50.0, 30.0}}, new double[][] {{65.0, 80.0, 70.0}});
    pump.getPumpChart().setHeadUnit("meter");
    pump.setSpeed(3000.0);
    return pump;
  }

  /**
   * Create the liquid-water suction fluid using SRK with classic mixing.
   *
   * @return fluid at 298.15 K and 5 bara
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
