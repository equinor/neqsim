package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Rejection and recovery of incomplete pump evidence at the canonical network boundary.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MappedPumpAcceptanceTest extends NeqSimTest {
  /** Disabling the registered curve must not select fixed-pressure fallback physics. */
  @Test
  void disabledChartFailsClosed() {
    assertRejectedAndRecovered("disabled");
  }

  /** A detached curve model cannot be silently replaced with an unconfigured pump. */
  @Test
  void missingModelFailsClosed() {
    assertRejectedAndRecovered("missing");
  }

  /** Finite output is not sufficient when the equipment reports incomplete convergence. */
  @Test
  void unsolvedFiniteResultFailsClosed() {
    assertRejectedAndRecovered("unsolved");
  }

  /** Non-finite or negative shaft demand must invalidate the whole operating-point sample. */
  @Test
  void invalidPowerFailsClosed() {
    assertRejectedAndRecovered("nan power");
    assertRejectedAndRecovered("negative power");
  }

  /** Non-positive or non-finite outlet state must not enter network thermal mixing. */
  @Test
  void invalidOutletFailsClosed() {
    assertRejectedAndRecovered("nan temperature");
    assertRejectedAndRecovered("zero temperature");
    assertRejectedAndRecovered("nan pressure");
    assertRejectedAndRecovered("zero pressure");
  }

  /** Exceptions clear previously accepted diagnostics and permit a fresh recovery solve. */
  @Test
  void thrownCalculationClearsPreviousState() {
    assertRejectedAndRecovered("exception");
  }

  /**
   * Exercise good, invalid and recovered results using the same graph and configured pump.
   *
   * @param failure deliberately invalid equipment result or configuration
   */
  private void assertRejectedAndRecovered(String failure) {
    String[] state = {"valid"};
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    LoopedPipeNetwork network = new LoopedPipeNetwork("mapped pump evidence");
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(10);
    network.addSourceNode("suction", 5.0, 0.0);
    network.addSinkNode("delivery", 3600.0);
    Pump pump = new Pump("controlled pump", new Stream("feed", fluid.clone())) {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public void run(UUID id) {
        if ("exception".equals(state[0])) {
          throw new IllegalStateException("Deliberate pump failure");
        }
        SystemInterface result = getInletStream().getFluid().clone();
        result
            .setPressure("nan pressure".equals(state[0]) ? Double.NaN : "zero pressure".equals(state[0]) ? 0.0 : 10.0);
        result.setTemperature(
            "nan temperature".equals(state[0]) ? Double.NaN : "zero temperature".equals(state[0]) ? 0.0 : 300.0);
        getOutletStream().setThermoSystem(result);
        setCalculationIdentifier(id);
      }

      /** {@inheritDoc} */
      @Override
      public boolean solved() {
        return !"unsolved".equals(state[0]);
      }

      /** {@inheritDoc} */
      @Override
      public double getPower(String unit) {
        double watts = "nan power".equals(state[0]) ? Double.NaN : "negative power".equals(state[0]) ? -1000.0 : 1000.0;
        return new neqsim.util.unit.PowerUnit(watts, "W").getValue(unit);
      }
    };
    pump.getPumpChart().setCurves(new double[] {0.018, 298.15, 1.0, 1.0, 1000.0}, new double[] {3000.0},
        new double[][] {{1.0, 4.0, 8.0}}, new double[][] {{60.0, 50.0, 30.0}}, new double[][] {{65.0, 80.0, 70.0}});
    pump.setSpeed(3000.0);
    LoopedPipeNetwork.NetworkPipe edge = network.addPumpWithCurve("suction", "delivery", "pump", pump);
    ProcessSystem area = new ProcessSystem();
    area.add(network);
    ProcessModel host = new ProcessModel();
    host.add("injection", area);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(1.0, edge.getPumpPowerKW(), 1.0e-12);
    assertNotNull(edge.getOutletFluid());
    double acceptedHead = edge.getPumpHeadM();

    state[0] = failure;
    pump.getPumpChart().setUsePumpChart(!"disabled".equals(failure));
    if ("missing".equals(failure)) {
      edge.setPumpModel(null);
    }
    network.run();

    assertEquals("INFEASIBLE", edge.getPumpOperatingStatus());
    assertEquals("Pump-INFEASIBLE", edge.getFlowRegime());
    assertTrue(Double.isNaN(edge.getPumpPowerKW()));
    assertTrue(Double.isNaN(network.getPower("kW")));
    assertTrue(Double.isNaN(host.getPower("kW")));
    assertTrue(Double.isNaN(edge.getPumpHeadM()));
    assertTrue(Double.isNaN(edge.getPumpNpshaM()));
    assertTrue(Double.isNaN(edge.getPumpNpshrM()));
    assertTrue(Double.isNaN(edge.getPumpPowerResidualKW()));
    assertTrue(Double.isNaN(edge.getPumpMinimumFlowResidualKgS()));
    assertTrue(Double.isNaN(edge.getPumpNpshResidualM()));
    assertNull(edge.getInletFluid());
    assertNull(edge.getOutletFluid());
    assertTrue(Double.isNaN(edge.getOutletTemperature()));

    state[0] = "valid";
    pump.getPumpChart().setUsePumpChart(true);
    edge.setPumpModel(pump);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(1.0, edge.getFlowRate(), 1.0e-8);
    assertEquals(1.0, network.getPower("kW"), 1.0e-12);
    assertEquals(1.0, host.getPower("kW"), 1.0e-12);
    assertEquals(acceptedHead, edge.getPumpHeadM(), 1.0e-8);
    assertNotNull(edge.getOutletFluid());
    assertTrue(edge.getPumpModel() == pump);
  }
}
