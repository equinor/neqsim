package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for trace-water measurements in issue 4628.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WaterDewPointTraceWaterTest {
  /** Checks the returned CPA temperature against independently initialized gas and pure liquid water. */
  @Test
  void cpaTraceWaterReachesLiquidWaterEquilibrium() {
    for (double pressure : new double[] {43.0, 70.0}) {
      double previous = Double.NEGATIVE_INFINITY;
      for (double ppm : new double[] {1.0, 5.0, 8.6, 20.0, 100.0}) {
        SystemInterface gas = gas(pressure, ppm);
        WaterDewPointAnalyser analyser = analyser(gas, pressure, "CPA");
        double temperature = analyser.getMeasuredValue("K");
        assertTrue(Double.isFinite(temperature));
        assertTrue(temperature > previous, "Dew point must increase with water content");
        previous = temperature;

        gas.setTemperature(temperature);
        gas.init(0);
        gas.init(1, 0);
        SystemInterface water = new SystemSrkCPAstatoil(temperature, pressure);
        water.addComponent("water", 1.0);
        water.setMixingRule(10);
        water.init(0);
        water.init(1);
        double ratio = gas.getPhase(0).getComponent("water").getz()
            * gas.getPhase(0).getComponent("water").getFugacityCoefficient()
            / water.getPhase(1).getComponent("water").getFugacityCoefficient();
        assertEquals(1.0, ratio, 1.0e-7, "Liquid-water equilibrium at " + pressure + " bara, " + ppm + " ppmv");
      }
    }
  }

  /** Checks the reported dry-gas operating point without imposing an ice/liquid model agreement. */
  @Test
  void fivePpmAt43BaraHasColdLiquidDewPoint() {
    SystemInterface gas = gas(43.0, 5.0);
    assertEquals(-43.97, analyser(gas, 43.0, "CPA").getMeasuredValue("C"), 0.1);
  }

  /** Retains the corrected ISO-model trace-water trend without substituting a liquid-water reference. */
  @Test
  void gergTraceWaterDoesNotFlatten() {
    for (double pressure : new double[] {43.0, 70.0}) {
      double previous = Double.NEGATIVE_INFINITY;
      for (double ppm : new double[] {1.0, 5.0, 8.6, 20.0, 100.0}) {
        double temperature = analyser(gas(pressure, ppm), pressure, "GERG-water").getMeasuredValue("C");
        assertTrue(Double.isFinite(temperature));
        assertTrue(temperature > previous + 1.0, "Trace-water ISO result must continue to change with moisture");
        previous = temperature;
        if (pressure == 43.0 && ppm == 5.0) {
          assertEquals(-34.4991, temperature, 0.01, "ISO-model regression, not a liquid-water benchmark");
        }
      }
    }
  }

  /**
   * The separate pure phase must retain component tuning in a direct CPA calculation.
   *
   * @throws Exception if the dew-point operation fails
   */
  @Test
  void directCpaRetainsWaterParameters() throws Exception {
    SystemInterface gas = gas(43.0, 5.0);
    SystemInterface water = new SystemSrkCPAstatoil(250.0, 43.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    double criticalTemperature = gas.getComponent("water").getTC() + 3.0;
    for (SystemInterface fluid : new SystemInterface[] {gas, water}) {
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        fluid.getPhase(phase).getComponent("water").setTC(criticalTemperature);
      }
    }
    new ThermodynamicOperations(gas).waterDewPointTemperatureFlash();
    water.setTemperature(gas.getTemperature());
    water.init(0);
    water.init(1);
    double ratio = gas.getPhase(0).getComponent("water").getz()
        * gas.getPhase(0).getComponent("water").getFugacityCoefficient()
        / water.getPhase(1).getComponent("water").getFugacityCoefficient();
    assertEquals(1.0, ratio, 1.0e-7);
  }

  /** A failed CPA calculation must not return the analyser's trial temperature. */
  @Test
  void cpaRejectsDryGasAndInvalidPressure() {
    assertTrue(Double.isNaN(analyser(gas(43.0, 0.0), 43.0, "CPA").getMeasuredValue("C")));
    assertTrue(Double.isNaN(analyser(gas(43.0, 5.0), 0.0, "CPA").getMeasuredValue("C")));
  }

  /** Repeated measurements preserve the process fluid, composition, phase options and reference pressure. */
  @Test
  void repeatedMeasurementPreservesInput() {
    SystemInterface gas = gas(43.0, 5.0);
    gas.setHydrateCheck(true);
    gas.setMultiPhaseCheck(true);
    WaterDewPointAnalyser analyser = analyser(gas, 70.0, "CPA");
    double[] composition = gas.getMolarComposition();
    double first = analyser.getMeasuredValue("C");
    assertEquals(first, analyser.getMeasuredValue("C"), 1.0e-8);
    assertEquals(first + 273.15, analyser.getMeasuredValue("K"), 1.0e-8);
    assertEquals(300.0, gas.getTemperature(), 1.0e-12);
    assertEquals(43.0, gas.getPressure(), 1.0e-12);
    assertTrue(gas.getHydrateCheck());
    assertTrue(gas.doMultiPhaseCheck());
    for (int component = 0; component < composition.length; component++) {
      assertEquals(composition[component], gas.getMolarComposition()[component], 1.0e-15);
    }
  }

  /**
   * Creates a synthetic lean natural gas with normalized trace moisture.
   *
   * @param pressure absolute pressure in bara
   * @param ppm water mole fraction in ppmv
   * @return CPA gas with its mixing rule configured
   */
  private SystemInterface gas(double pressure, double ppm) {
    SystemInterface gas = new SystemSrkCPAstatoil(300.0, pressure);
    gas.addComponent("methane", 0.95 - ppm * 1.0e-6);
    gas.addComponent("ethane", 0.03);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.01);
    gas.addComponent("water", ppm * 1.0e-6);
    gas.setMixingRule(10);
    return gas;
  }

  /**
   * Binds an analyser without flashing or changing the input fluid.
   *
   * @param gas input fluid
   * @param pressure measurement reference pressure in bara
   * @param method analyser method
   * @return configured analyser
   */
  private WaterDewPointAnalyser analyser(SystemInterface gas, double pressure, String method) {
    WaterDewPointAnalyser analyser = new WaterDewPointAnalyser(new Stream("trace-water gas", gas));
    analyser.setReferencePressure(pressure);
    analyser.setMethod(method);
    return analyser;
  }
}
