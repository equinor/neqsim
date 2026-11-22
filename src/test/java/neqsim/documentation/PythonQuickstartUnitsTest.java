package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exercises the underlying Java API of the Python quickstart unit-handling recipe.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PythonQuickstartUnitsTest {
  /** Verify explicit units, initialized properties and a changed stream state after rerunning. */
  @Test
  void unitsRecipeReadsCurrentSolvedFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("units example", fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.setTemperature(30.0, "C");
    stream.setPressure(50.0, "bara");
    stream.run();
    SystemInterface solvedFluid = stream.getFluid();
    solvedFluid.initProperties();
    assertState(solvedFluid, 30.0, 50.0);
    double firstDensity = solvedFluid.getDensity("kg/m3");

    stream.setTemperature(40.0, "C");
    stream.setPressure(40.0, "bara");
    stream.run();
    SystemInterface rerunFluid = stream.getFluid();
    rerunFluid.initProperties();
    assertState(rerunFluid, 40.0, 40.0);
    assertTrue(rerunFluid.getDensity("kg/m3") < firstDensity);
  }

  /**
   * Verify state, flow conservation, unit conversion and finite initialized properties.
   *
   * @param fluid current solved stream fluid
   * @param temperatureC expected temperature in degrees Celsius
   * @param pressureBara expected absolute pressure in bar
   */
  private void assertState(SystemInterface fluid, double temperatureC, double pressureBara) {
    assertEquals(temperatureC + 273.15, fluid.getTemperature(), 1e-8);
    assertEquals(temperatureC, fluid.getTemperature("C"), 1e-8);
    assertEquals(pressureBara, fluid.getPressure("bara"), 1e-8);
    assertEquals(1000.0, fluid.getFlowRate("kg/hr"), 1e-6);
    assertEquals(1, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType("gas"));
    double density = fluid.getDensity("kg/m3");
    double viscosity = fluid.getViscosity("cP");
    double enthalpy = fluid.getEnthalpy("kJ/kg");
    assertTrue(Double.isFinite(density) && density > 0.0);
    assertTrue(Double.isFinite(viscosity) && viscosity > 0.0);
    assertTrue(Double.isFinite(enthalpy));
    assertEquals(viscosity * 1e-3, fluid.getViscosity("kg/msec"), 1e-10);
    assertEquals(enthalpy * 1000.0, fluid.getEnthalpy("J/kg"), 1e-6);
  }
}
