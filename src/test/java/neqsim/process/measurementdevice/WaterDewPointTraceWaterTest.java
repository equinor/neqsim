package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Trace-moisture analyser regressions for issue 4628.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WaterDewPointTraceWaterTest {
  /** The CPA result must reach the liquid-water boundary rather than a phase-detection threshold. */
  @Test
  void fivePpmReachesColdLiquidDewPoint() {
    double temperature = analyser(gas(43.0, 5.0), 43.0, "CPA").getMeasuredValue("C");
    assertTrue(temperature < -38.8, "Below the ideal-gas liquid-water dew point");
    assertEquals(-43.89654, temperature, 0.01, "Synthetic mixed-liquid CPA equilibrium fixture");
  }

  /** No water or invalid pressure must not produce the last trial temperature. */
  @Test
  void invalidCpaMeasurementReturnsNaN() {
    assertTrue(Double.isNaN(analyser(gas(43.0, 0.0), 43.0, "CPA").getMeasuredValue("C")));
    assertTrue(Double.isNaN(analyser(gas(43.0, 5.0), 0.0, "CPA").getMeasuredValue("C")));
  }

  /** ISO ice-reference results remain responsive to moisture at trace concentrations. */
  @Test
  void gergTraceWaterDoesNotFlatten() {
    for (double pressure : new double[] {43.0, 70.0}) {
      double previous = Double.NEGATIVE_INFINITY;
      for (double ppm : new double[] {1.0, 5.0, 8.6, 20.0, 100.0}) {
        double temperature = analyser(gas(pressure, ppm), pressure, "GERG-water").getMeasuredValue("C");
        assertTrue(Double.isFinite(temperature));
        assertTrue(temperature > previous + 1.0);
        previous = temperature;
      }
    }
  }

  /** Reference-pressure and unit conversions leave the source stream and phase options unchanged. */
  @Test
  void repeatedMeasurementsPreserveInput() {
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
   * Checks every component against freshly initialized, independent gas and mixed-liquid EOS states.
   *
   * @throws Exception if a dew-point operation fails
   */
  @Test
  void mixedLiquidEquilibratesAllComponentsDownToOnePpm() throws Exception {
    for (double pressure : new double[] {43.0, 70.0}) {
      double previous = Double.NEGATIVE_INFINITY;
      for (double ppm : new double[] {1.0, 5.0, 8.6, 20.0, 100.0}) {
        SystemInterface result = gas(pressure, ppm);
        ThermodynamicOperations operations = new ThermodynamicOperations(result);
        operations.waterDewPointTemperatureFlash(true);
        double temperature = result.getTemperature();
        assertTrue(temperature > previous);
        previous = temperature;
        assertTrue(result.getPhase(1).getComponent("water").getx() > 0.9);
        assertTrue(result.getPhase(1).getComponent("methane").getx() > 1.0e-4,
            "Dissolved gas must be included, rather than a pure-water approximation");

        SystemInterface independentGas = gas(pressure, ppm);
        independentGas.setTemperature(temperature);
        independentGas.init(0);
        independentGas.init(1, 0);
        SystemInterface independentLiquid = new SystemSrkCPAstatoil(temperature, pressure);
        for (int component = 0; component < result.getNumberOfComponents(); component++) {
          independentLiquid.addComponent(result.getComponent(component).getName(),
              result.getPhase(1).getComponent(component).getx());
        }
        independentLiquid.setMixingRule(10);
        independentLiquid.init(0);
        independentLiquid.setBeta(1, 1.0);
        independentLiquid.setPhaseType(1, PhaseType.LIQUID);
        independentLiquid.init(1, 1);
        // Refresh the cold-start CPA association state before checking the independent liquid root.
        independentLiquid.init(1, 1);
        assertEquals(pressure, ((PhaseEosInterface) independentLiquid.getPhase(1)).calcPressure(), 1.0e-6);
        for (int component = 0; component < result.getNumberOfComponents(); component++) {
          double gasFugacity = independentGas.getPhase(0).getComponent(component).getx()
              * independentGas.getPhase(0).getComponent(component).getFugacityCoefficient();
          double liquidFugacity = independentLiquid.getPhase(1).getComponent(component).getx()
              * independentLiquid.getPhase(1).getComponent(component).getFugacityCoefficient();
          assertEquals(1.0, gasFugacity / liquidFugacity, 1.0e-7,
              result.getComponent(component).getName() + " at " + pressure + " bara, " + ppm + " ppmv");
        }
        operations.waterDewPointTemperatureFlash(true);
        assertEquals(temperature, result.getTemperature(), 1.0e-8);
      }
    }
  }

  /**
   * Exact-zero dissolved components do not create a NaN seed for pure water.
   *
   * @throws Exception if the dew-point operation fails
   */
  @Test
  void pureWaterWithZeroGasComponentReachesSaturation() throws Exception {
    SystemInterface water = new SystemSrkCPAstatoil(300.0, 1.0);
    water.addComponent("methane", 0.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    new ThermodynamicOperations(water).waterDewPointTemperatureFlash(true);
    assertEquals(373.15, water.getTemperature(), 4.0);
    assertEquals(0.0, water.getPhase(1).getComponent("methane").getx(), 0.0);
    assertEquals(1.0, water.getPhase(0).getComponent("water").getFugacityCoefficient()
        / water.getPhase(1).getComponent("water").getFugacityCoefficient(), 1.0e-7);
  }

  /**
   * Creates a normalized synthetic lean gas.
   *
   * @param pressure absolute pressure in bara
   * @param ppm water content in ppmv
   * @return configured CPA fluid
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
   * Creates an analyser without changing the process fluid.
   *
   * @param gas process fluid
   * @param pressure absolute measurement pressure in bara
   * @param method dew-point method
   * @return configured analyser
   */
  private WaterDewPointAnalyser analyser(SystemInterface gas, double pressure, String method) {
    WaterDewPointAnalyser analyser = new WaterDewPointAnalyser(new Stream("gas", gas));
    analyser.setReferencePressure(pressure);
    analyser.setMethod(method);
    return analyser;
  }
}
