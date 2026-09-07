package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the combustion side of {@link GasTurbine}.
 *
 * <p>
 * Covers two defects found while building a CO2 profile for a tie-back study: the power-demand (driven-load) run left
 * the outlet stream holding unburned fuel, and the combustion stoichiometry threw on the pseudo-components of a
 * characterised reservoir fluid.
 * </p>
 *
 * @author NeqSim engineering tasks
 * @version 1.0
 */
public class GasTurbineCombustionTest extends neqsim.NeqSimTest {
  /** Relative tolerance on the CO2 carbon balance. */
  private static final double CO2_TOLERANCE = 0.02;

  /**
   * Builds a dry fuel gas stream.
   *
   * @return a fuel gas stream at 25 bara and 15 C
   */
  private Stream fuelGas() {
    SystemInterface fuel = new SystemSrkEos(288.15, 25.0);
    fuel.addComponent("methane", 0.90);
    fuel.addComponent("ethane", 0.06);
    fuel.addComponent("propane", 0.02);
    fuel.addComponent("CO2", 0.02);
    fuel.setMixingRule("classic");
    Stream stream = new Stream("fuel gas", fuel);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Builds a compressor that presents a known shaft load to the turbine.
   *
   * @return a compressor that has been run
   */
  private Compressor drivenCompressor() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    Stream feed = new Stream("compressor feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();
    Compressor compressor = new Compressor("driven compressor", feed);
    compressor.setOutletPressure(140.0);
    compressor.setPolytropicEfficiency(0.75);
    compressor.setUsePolytropicCalc(true);
    compressor.run();
    return compressor;
  }

  /**
   * The outlet of a load-driven turbine must be combustion exhaust, not the fuel it burned.
   *
   * <p>
   * The carbon leaving in the exhaust CO2 must equal the carbon entering with the fuel. Before the fix the outlet
   * carried the fuel composition, so the exhaust CO2 was the fuel's own 2 mol% and an emission calculation read roughly
   * an order of magnitude low.
   * </p>
   */
  @Test
  void testPowerDemandOutletIsCombustionExhaust() {
    Stream fuel = fuelGas();
    Compressor load = drivenCompressor();

    GasTurbine turbine = new GasTurbine("GT");
    turbine.setInletStream(fuel);
    turbine.setThermalEfficiency(0.32);
    turbine.addDrivenLoad(load);
    turbine.run();

    assertEquals(load.getPower(), turbine.getPower(), Math.abs(load.getPower()) * 1.0e-6);

    SystemInterface exhaust = turbine.getOutletStream().getFluid();
    double co2MolesOut = 0.0;
    double oxygenMolesOut = 0.0;
    for (int i = 0; i < exhaust.getNumberOfComponents(); i++) {
      String name = exhaust.getComponent(i).getName();
      if ("CO2".equals(name)) {
        co2MolesOut = exhaust.getComponent(i).getNumberOfmoles();
      }
      if ("oxygen".equals(name)) {
        oxygenMolesOut = exhaust.getComponent(i).getNumberOfmoles();
      }
    }
    assertTrue(co2MolesOut > 0.0, "exhaust must contain CO2");
    assertTrue(oxygenMolesOut >= 0.0, "oxygen inventory must not go negative");

    SystemInterface burnedFuel = turbine.getInletStream().getFluid();
    double carbonIn = 0.0;
    for (int i = 0; i < burnedFuel.getNumberOfComponents(); i++) {
      String name = burnedFuel.getComponent(i).getName();
      double moles = burnedFuel.getComponent(i).getNumberOfmoles();
      if ("methane".equals(name)) {
        carbonIn += moles;
      } else if ("ethane".equals(name)) {
        carbonIn += 2.0 * moles;
      } else if ("propane".equals(name)) {
        carbonIn += 3.0 * moles;
      } else if ("CO2".equals(name)) {
        carbonIn += moles;
      }
    }
    // The combustion air carries a little CO2 of its own, so it belongs on the inlet side.
    SystemInterface air = turbine.airStream.getFluid();
    for (int i = 0; i < air.getNumberOfComponents(); i++) {
      if ("CO2".equals(air.getComponent(i).getName())) {
        carbonIn += air.getComponent(i).getNumberOfmoles();
      }
    }
    assertEquals(carbonIn, co2MolesOut, carbonIn * CO2_TOLERANCE);
  }

  /**
   * A fuel containing characterised pseudo-components must burn instead of aborting the run.
   *
   * <p>
   * Pseudo-components are absent from the element database, so asking them for a carbon count used to throw and take
   * the whole process solve with it.
   * </p>
   */
  @Test
  void testCombustionHandlesPseudoComponents() {
    SystemInterface fuel = new SystemSrkEos(288.15, 25.0);
    fuel.addComponent("methane", 0.85);
    fuel.addComponent("ethane", 0.05);
    fuel.addTBPfraction("C7P", 0.08, 96.0 / 1000.0, 0.75);
    fuel.addComponent("CO2", 0.02);
    fuel.setMixingRule("classic");
    Stream fuelStream = new Stream("pseudo fuel", fuel);
    fuelStream.setFlowRate(1000.0, "kg/hr");
    fuelStream.run();

    GasTurbine turbine = new GasTurbine("GT pseudo");
    turbine.setInletStream(fuelStream);
    turbine.setThermalEfficiency(0.32);
    turbine.addDrivenLoad(drivenCompressor());
    turbine.run();

    SystemInterface exhaust = turbine.getOutletStream().getFluid();
    double co2Moles = 0.0;
    for (int i = 0; i < exhaust.getNumberOfComponents(); i++) {
      if ("CO2".equals(exhaust.getComponent(i).getName())) {
        co2Moles = exhaust.getComponent(i).getNumberOfmoles();
      }
    }
    assertTrue(co2Moles > 0.0, "pseudo-component fuel must produce CO2");
    assertTrue(turbine.getFuelFlowRate("kg/hr") > 0.0, "fuel must be sized from the driven load");
  }
}
