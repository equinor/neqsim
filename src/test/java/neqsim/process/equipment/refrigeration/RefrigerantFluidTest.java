package neqsim.process.equipment.refrigeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the refrigerant property layer against reference saturation data.
 *
 * <p>
 * Reference values are NIST Chemistry WebBook saturation properties (reference equations of state) rounded to the
 * digits shown.
 * </p>
 */
class RefrigerantFluidTest {
  /**
   * Checks saturation pressure and latent heat of one refrigerant.
   *
   * @param refrigerant refrigerant name
   * @param temperatureC temperature in C
   * @param pressureBara reference saturation pressure in bara
   * @param latentKJperKg reference latent heat in kJ/kg
   * @param pressureTolerance relative tolerance on pressure
   * @param latentTolerance relative tolerance on latent heat
   */
  private static void assertSaturation(String refrigerant, double temperatureC, double pressureBara,
      double latentKJperKg, double pressureTolerance, double latentTolerance) {
    RefrigerantFluid fluid = RefrigerantFluid.of(refrigerant);
    double temperature = temperatureC + 273.15;
    assertEquals(pressureBara, fluid.getSaturationPressure(temperature), pressureBara * pressureTolerance,
        refrigerant + " saturation pressure at " + temperatureC + " C");
    assertEquals(latentKJperKg, fluid.getLatentHeat(temperature) / 1000.0, latentKJperKg * latentTolerance,
        refrigerant + " latent heat at " + temperatureC + " C");
  }

  @Test
  void referenceModelsReproduceSaturationData() {
    assertSaturation("ammonia", -33.33, 1.013, 1369.5, 0.002, 0.002);
    assertSaturation("ammonia", 30.0, 11.67, 1144.4, 0.002, 0.002);
    assertSaturation("CO2", -20.0, 19.70, 282.4, 0.002, 0.002);
    assertSaturation("CO2", -40.0, 10.05, 322.4, 0.002, 0.002);
  }

  @Test
  void pengRobinsonReproducesHydrocarbonSaturationData() {
    assertSaturation("propane", -40.0, 1.110, 423.3, 0.01, 0.01);
    assertSaturation("propane", 30.0, 10.79, 326.6, 0.01, 0.01);
    assertSaturation("propene", -47.62, 1.013, 439.0, 0.02, 0.02);
    assertSaturation("ethylene", -103.7, 1.013, 482.4, 0.02, 0.02);
  }

  @Test
  void pengRobinsonReproducesR134aWithinThreePercent() {
    assertSaturation("R134a", -26.07, 1.013, 217.0, 0.01, 0.03);
    assertSaturation("R134a", 30.0, 7.702, 173.1, 0.01, 0.03);
  }

  @Test
  void ashraeDesignationsResolve() {
    assertEquals("ammonia", RefrigerantFluid.of("R717").getComponentName());
    assertEquals("propane", RefrigerantFluid.of("r290").getComponentName());
    assertEquals("CO2", RefrigerantFluid.of("R744").getComponentName());
    assertEquals("propene", RefrigerantFluid.of("R1270").getComponentName());
  }

  @Test
  void saturationTemperatureInvertsSaturationPressure() {
    for (String name : new String[] {"propane", "ammonia", "CO2", "R134a"}) {
      RefrigerantFluid fluid = RefrigerantFluid.of(name);
      double temperature = 0.75 * fluid.getCriticalTemperature();
      double pressure = fluid.getSaturationPressure(temperature);
      assertEquals(temperature, fluid.getSaturationTemperature(pressure), 1.0e-4, name);
    }
  }

  @Test
  void pressureEnthalpySolveCoversLiquidTwoPhaseAndVapour() {
    RefrigerantFluid fluid = RefrigerantFluid.of("propane");
    double temperature = 263.15;
    RefrigerantState liquid = fluid.saturatedLiquid(temperature);
    RefrigerantState vapor = fluid.saturatedVapor(temperature);
    double pressure = liquid.getPressure();

    RefrigerantState twoPhase = fluid.stateAtPH(pressure,
        liquid.getEnthalpy() + 0.3 * (vapor.getEnthalpy() - liquid.getEnthalpy()));
    assertEquals(0.3, twoPhase.getVaporFraction(), 1.0e-9);
    assertEquals(temperature, twoPhase.getTemperature(), 1.0e-4);

    RefrigerantState superheated = fluid.vaporAt(pressure, temperature + 25.0);
    RefrigerantState fromEnthalpy = fluid.stateAtPH(pressure, superheated.getEnthalpy());
    assertEquals(temperature + 25.0, fromEnthalpy.getTemperature(), 1.0e-3);
    RefrigerantState fromEntropy = fluid.stateAtPS(pressure, superheated.getEntropy());
    assertEquals(temperature + 25.0, fromEntropy.getTemperature(), 1.0e-3);

    RefrigerantState subcooled = fluid.liquidAt(pressure, temperature - 10.0);
    assertEquals(temperature - 10.0, fluid.stateAtPH(pressure, subcooled.getEnthalpy()).getTemperature(), 1.0e-3);
    assertTrue(subcooled.getEnthalpy() < liquid.getEnthalpy());
    assertTrue(superheated.getDensity() < vapor.getDensity());
  }

  @Test
  void isentropicCompressionOfAmmoniaMatchesReferenceDischargeTemperature() {
    // Saturated ammonia vapour at 1.013 bara compressed isentropically to 3 times the pressure. An ideal gas with the
    // ammonia isentropic exponent 1.31 gives 239.8 K * 3^(0.31/1.31) = 311 K; the real-gas reference value is 310 K.
    RefrigerantFluid fluid = RefrigerantFluid.of("ammonia");
    RefrigerantState suction = fluid.saturatedVapor(273.15 - 33.33);
    RefrigerantState discharge = fluid.stateAtPS(3.0 * suction.getPressure(), suction.getEntropy());
    assertEquals(310.0, discharge.getTemperature(), 1.5);
  }

  @Test
  void halocarbonsHaveIdealGasHeatCapacity() {
    // R134a ideal-gas cp is 85.4 J/(mol K) at 300 K (Tillner-Roth and Baehr 1994); R12 is 72.6 J/(mol K).
    assertEquals(85.4, idealGasCp("R134a"), 0.5);
    assertEquals(72.6, idealGasCp("R12"), 0.5);
  }

  /**
   * Gets the ideal-gas heat capacity of a component at 300 K.
   *
   * @param component component name
   * @return heat capacity in J/(mol K)
   */
  private static double idealGasCp(String component) {
    SystemInterface fluid = new SystemSrkEos(300.0, 1.0);
    fluid.addComponent(component, 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return fluid.getPhase(0).getComponent(0).getCp0(300.0);
  }

  @Test
  void mixturesAndUnresolvedPhasesAreRejected() {
    final SystemInterface mixture = new SystemSrkEos(273.15, 1.0);
    mixture.addComponent("propane", 0.9);
    mixture.addComponent("ethane", 0.1);
    mixture.createDatabase(true);
    mixture.setMixingRule(2);
    assertThrows(IllegalArgumentException.class, () -> RefrigerantFluid.of("commercial propane", mixture));
    assertThrows(IllegalArgumentException.class, () -> RefrigerantFluid.of("propane").getSaturationPressure(400.0));
  }
}
