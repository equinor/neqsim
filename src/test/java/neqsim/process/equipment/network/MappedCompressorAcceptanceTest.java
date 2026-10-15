package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Acceptance of mapped compressor calculation evidence at the canonical network boundary.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MappedCompressorAcceptanceTest extends NeqSimTest {
  /** A returned finite state does not certify a calculation that reports unsolved. */
  @Test
  void unsolvedFiniteChartResultFailsClosed() {
    assertRejected(false, 1000.0, 310.0, true);
  }

  /** Disabling a registered chart cannot switch an existing mapped edge to simpler physics. */
  @Test
  void chartDisabledAfterRegistrationFailsClosed() {
    assertRejected(true, 1000.0, 310.0, false);
  }

  /** A non-finite demand cannot leave a solved chart label or reusable thermal state. */
  @Test
  void nonFiniteChartPowerFailsClosed() {
    assertRejected(true, Double.NaN, 310.0, true);
  }

  /** A non-finite outlet cannot leave a solved chart label or reusable thermal state. */
  @Test
  void nonFiniteChartTemperatureFailsClosed() {
    assertRejected(true, 1000.0, Double.NaN, true);
  }

  /**
   * Exercise the public network solve with one deliberately incomplete chart result.
   *
   * @param solved result reported by the existing equipment convergence contract
   * @param power returned shaft power in W
   * @param temperature returned outlet temperature in K
   * @param chartEnabled whether the chart remains enabled after registration
   */
  private void assertRejected(boolean solved, double power, double temperature, boolean chartEnabled) {
    boolean[] returnedSolved = {true};
    double[] returnedPower = {1000.0};
    double[] returnedTemperature = {310.0};
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    LoopedPipeNetwork network = new LoopedPipeNetwork("mapped result acceptance");
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(2);
    network.addSourceNode("supply", 30.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 60.0);
    Compressor compressor = new Compressor("controlled mapped compressor", new Stream("feed", fluid.clone())) {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public void run(UUID id) {
        SystemInterface result = getInletStream().getThermoSystem().clone();
        result.setPressure(60.0);
        result.setTemperature(returnedTemperature[0]);
        getOutletStream().setThermoSystem(result);
        setPolytropicEfficiency(0.75);
        dH = returnedPower[0];
        setCalculationIdentifier(id);
      }

      /** {@inheritDoc} */
      @Override
      public boolean solved() {
        return returnedSolved[0];
      }

      /** {@inheritDoc} */
      @Override
      public double getPower(String unit) {
        return new neqsim.util.unit.PowerUnit(returnedPower[0], "W").getValue(unit);
      }
    };
    compressor.getCompressorChart().setCurves(new double[] {298.15, 1.0, 1.0, 1.0}, new double[] {9000.0},
        new double[][] {{1000.0, 1500.0, 2000.0}}, new double[][] {{80.0, 70.0, 55.0}},
        new double[][] {{72.0, 78.0, 74.0}});
    compressor.getCompressorChart().setUseCompressorChart(true);
    LoopedPipeNetwork.NetworkPipe edge = network.addCompressorWithChart("supply", "delivery", "compressor", compressor);
    network.run();
    assertEquals("Compressor-Chart", edge.getFlowRegime());
    assertTrue(Double.isFinite(edge.getCompressorPower()));
    assertNotNull(edge.getInletFluid());
    assertNotNull(edge.getOutletFluid());

    returnedSolved[0] = solved;
    returnedPower[0] = power;
    returnedTemperature[0] = temperature;
    compressor.getCompressorChart().setUseCompressorChart(chartEnabled);

    network.run();

    assertEquals("Compressor-Chart-Infeasible", edge.getFlowRegime());
    assertTrue(Double.isNaN(edge.getCompressorPower()));
    assertTrue(Double.isNaN(network.getPower("kW")));
    assertNull(edge.getInletFluid());
    assertNull(edge.getOutletFluid());
    assertTrue(Double.isNaN(edge.getOutletTemperature()));
  }
}
