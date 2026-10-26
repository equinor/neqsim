package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent derivative and returned-state checks for VU caloric initialization.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class VUCaloricStateQualificationTest {
  /**
   * Resolves each Jacobian entry with two central-difference steps on independently configured fluids.
   *
   * @param pr whether to use PR
   * @param rule classical or HV mixing rule
   * @param state gas, hydrocarbon liquid or methanol/water liquid
   */
  @ParameterizedTest
  @CsvSource({"false,2,gas", "false,4,gas", "true,2,gas", "true,4,gas", "false,2,oil", "false,4,oil", "true,2,oil",
      "true,4,oil", "false,2,aqueous", "false,4,aqueous", "true,2,aqueous", "true,4,aqueous"})
  void jacobianMatchesDetachedTemperaturePressureDifferences(boolean pr, int rule, String state) {
    double temperature = temperature(state);
    double pressure = pressure(state);
    for (double amount : new double[] {1.0, 1000.0}) {
      SystemInterface fluid = fluid(pr, rule, state, temperature, pressure, amount);
      ThermodynamicModelTest identities = new ThermodynamicModelTest(fluid);
      // Native identities are extensive sums. Keep the default 1e-10 bound per mole at both inventory scales.
      // The 1000-mol oil Gibbs residual is 2.51e-9 mol versus 2.51e-12 mol for the one-mol fixture.
      identities.setMaxError(amount * 1.0e-10);
      assertTrue(identities.checkFugacityCoefficients());
      assertTrue(identities.checkFugacityCoefficientsDP());
      assertTrue(identities.checkFugacityCoefficientsDT());
      double vt = fluid.getdVdTpn();
      double vp = fluid.getdVdPtn();
      double[] analytical = {vt, vp, fluid.getCp() - pressure * vt, -temperature * vt - pressure * vp};
      double[] coarse = differences(pr, rule, state, temperature, pressure, amount, 0.005, 0.002);
      double[] fine = differences(pr, rule, state, temperature, pressure, amount, 0.0025, 0.001);
      for (int entry = 0; entry < analytical.length; entry++) {
        assertTrue(Double.isFinite(analytical[entry]) && Double.isFinite(coarse[entry]) && Double.isFinite(fine[entry]),
            "all analytic and numerical derivative entries must be finite");
        double scale = Math.max(Math.abs(analytical[entry]), amount * 1.0e-7);
        assertEquals(fine[entry], coarse[entry], 1.0e-5 * scale, "finite differences must resolve entry " + entry);
        assertEquals(fine[entry], analytical[entry], 1.0e-5 * scale, "Jacobian entry " + entry);
      }
    }
  }

  /**
   * Checks fresh, repeated, changed and returned V/U targets against independent caloric states.
   *
   * @param pr whether to use PR
   * @param rule classical or HV mixing rule
   * @param state gas, hydrocarbon liquid or methanol/water liquid
   */
  @ParameterizedTest
  @CsvSource({"false,2,gas", "false,4,gas", "true,2,gas", "true,4,gas", "false,2,oil", "false,4,oil", "true,2,oil",
      "true,4,oil", "false,2,aqueous", "false,4,aqueous", "true,2,aqueous", "true,4,aqueous"})
  void publicVuReturnsCurrentCaloricState(boolean pr, int rule, String state) {
    double targetPressure = pressure(state);
    SystemInterface working = fluid(pr, rule, state, temperature(state) + 2.0, targetPressure * 1.02, 1.0);
    for (double shift : new double[] {0.0, 0.0, 0.2, 0.0}) {
      SystemInterface reference = fluid(pr, rule, state, temperature(state) + shift, targetPressure, 1.0);
      double volume = reference.getVolume();
      double energy = reference.getInternalEnergy();
      double energyScale = Math.max(1.0, Math.abs(energy));
      ThermodynamicOperations operations = new ThermodynamicOperations(working);
      operations.VUflash(volume, energy);
      assertTrue(((OptimizedVUflash) operations.getOperation()).isLastRunConverged());
      double returnedEnthalpy = working.getEnthalpy();
      double returnedEnergy = working.getInternalEnergy();
      working.init(2);
      assertEquals(returnedEnthalpy, working.getEnthalpy(), 1.0e-12 * energyScale,
          "VU must return caloric properties initialized on its final TP state");
      assertEquals(returnedEnergy, working.getInternalEnergy(), 1.0e-12 * energyScale);
      SystemInterface independent = fluid(pr, rule, state, working.getTemperature(), working.getPressure(), 1.0);
      double propertyScale = Math.max(1.0,
          Math.max(Math.abs(independent.getEnthalpy()), Math.abs(independent.getInternalEnergy())));
      assertEquals(independent.getEnthalpy(), returnedEnthalpy, 1.0e-12 * propertyScale);
      assertEquals(independent.getInternalEnergy(), returnedEnergy, 1.0e-12 * propertyScale);
      // Assert the existing general VU acceptance contract. Strict inventory refinement is a separate regression.
      assertTrue(Math.abs(working.getVolume() / volume - 1.0) < 1.0e-3);
      double enthalpyTarget = energy + working.getPressure() * volume;
      assertTrue(Math.abs(returnedEnthalpy - enthalpyTarget) / Math.max(1.0, Math.abs(enthalpyTarget)) < 1.0e-3);
      assertEquals(1, working.getNumberOfPhases());
      assertEquals(1.0, working.getBeta(0), 1.0e-12);
      for (int component = 0; component < working.getNumberOfComponents(); component++) {
        assertEquals(reference.getComponent(component).getz(), working.getPhase(0).getComponent(component).getx(),
            1.0e-10);
      }
    }
  }

  /** Executes the maintained VU expansion example with caloric initialization before specifying internal energy. */
  @Test
  void documentedExpansionUsesInitializedInternalEnergy() {
    SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 100.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("CO2", 0.1);
    fluid.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.init(2);
    double initialVolume = fluid.getVolume("m3");
    double initialEnergy = fluid.getInternalEnergy("J");
    ops.VUflash(initialVolume * 2.0, initialEnergy, "m3", "J");
    org.apache.logging.log4j.LogManager.getLogger("VU example").info("New T: {} C, new P: {} bara",
        fluid.getTemperature("C"), fluid.getPressure("bara"));
    double returnedEnthalpy = fluid.getEnthalpy();
    fluid.init(2);
    assertEquals(returnedEnthalpy, fluid.getEnthalpy(), 1.0e-10);
    assertTrue(Math.abs(fluid.getVolume("m3") / (2.0 * initialVolume) - 1.0) < 1.0e-3);
    double enthalpyTarget = initialEnergy + fluid.getPressure() * 2.0 * initialVolume * 1.0e5;
    assertTrue(Math.abs(fluid.getEnthalpy() - enthalpyTarget) / Math.max(1.0, Math.abs(enthalpyTarget)) < 1.0e-3);
    assertTrue(Double.isFinite(fluid.getTemperature()) && fluid.getTemperature() > 0.0);
    assertTrue(fluid.getPressure() > 0.0 && fluid.getPressure() < 100.0);
  }

  /**
   * Calculates volume and internal-energy derivatives using detached central trials at fixed inventories.
   *
   * @param pr whether to use PR
   * @param rule mixing rule
   * @param state fixture state
   * @param temperature base temperature in K
   * @param pressure base pressure in bara
   * @param amount total molar scale
   * @param dt temperature step in K
   * @param dp pressure step in bara
   * @return dV/dT, dV/dP, dU/dT and dU/dP in internal volume and joule units
   */
  private static double[] differences(boolean pr, int rule, String state, double temperature, double pressure,
      double amount, double dt, double dp) {
    SystemInterface tp = fluid(pr, rule, state, temperature + dt, pressure, amount);
    SystemInterface tm = fluid(pr, rule, state, temperature - dt, pressure, amount);
    SystemInterface pp = fluid(pr, rule, state, temperature, pressure + dp, amount);
    SystemInterface pm = fluid(pr, rule, state, temperature, pressure - dp, amount);
    return new double[] {(tp.getVolume() - tm.getVolume()) / (2.0 * dt), (pp.getVolume() - pm.getVolume()) / (2.0 * dp),
        (tp.getInternalEnergy() - tm.getInternalEnergy()) / (2.0 * dt),
        (pp.getInternalEnergy() - pm.getInternalEnergy()) / (2.0 * dp)};
  }

  /**
   * Configures an independent worker fluid, avoiding shared clone calculation state.
   *
   * @param pr whether to use PR
   * @param rule mixing rule
   * @param state fixture state
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param amount total molar scale
   * @return initialized single-phase fluid
   */
  private static SystemInterface fluid(boolean pr, int rule, String state, double temperature, double pressure,
      double amount) {
    SystemInterface fluid = pr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    if ("gas".equals(state)) {
      fluid.addComponent("methane", 0.8 * amount);
      fluid.addComponent("ethane", 0.1 * amount);
      fluid.addComponent("propane", 0.1 * amount);
    } else if ("oil".equals(state)) {
      fluid.addComponent("propane", 0.8 * amount);
      fluid.addComponent("n-butane", 0.2 * amount);
    } else {
      fluid.addComponent("methanol", 0.3 * amount);
      fluid.addComponent("water", 0.7 * amount);
    }
    fluid.setMixingRule(rule);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    assertEquals(1, fluid.getNumberOfPhases(), "Jacobian requires a single phase at fixed composition");
    return fluid;
  }

  /**
   * Gets the base fixture temperature.
   *
   * @param state fixture state
   * @return temperature in K
   */
  private static double temperature(String state) {
    return "gas".equals(state) ? 350.0 : "oil".equals(state) ? 300.0 : 330.0;
  }

  /**
   * Gets the base fixture pressure.
   *
   * @param state fixture state
   * @return pressure in bara
   */
  private static double pressure(String state) {
    return "gas".equals(state) ? 40.0 : "oil".equals(state) ? 20.0 : 50.0;
  }
}
