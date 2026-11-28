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
  void mathiasCopemanPengRobinsonReproducesHydrocarbonSaturationData() {
    assertSaturation("propane", -40.0, 1.110, 423.3, 0.006, 0.008);
    assertSaturation("propane", 0.0, 4.745, 374.9, 0.006, 0.008);
    assertSaturation("propane", 30.0, 10.79, 326.6, 0.006, 0.008);
    assertSaturation("i-butane", -11.75, 1.013, 365.1, 0.006, 0.008);
    assertSaturation("n-butane", -0.49, 1.013, 385.7, 0.006, 0.008);
    assertSaturation("ethane", -88.58, 1.013, 489.4, 0.006, 0.008);
    // No Mathias-Copeman parameters in the database: standard Peng-Robinson temperature function.
    assertSaturation("propene", -47.62, 1.013, 439.0, 0.01, 0.01);
    assertSaturation("ethylene", -103.77, 1.013, 482.4, 0.02, 0.015);
  }

  @Test
  void mathiasCopemanPengRobinsonReproducesHalocarbonSaturationData() {
    // R134a: Tillner-Roth and Baehr (1994) saturation pressures; latent heat is limited by the cubic vapour volume.
    assertSaturation("R134a", -40.0, 0.5121, 225.9, 0.005, 0.01);
    assertSaturation("R134a", -26.07, 1.013, 217.0, 0.005, 0.01);
    assertSaturation("R134a", 0.0, 2.928, 198.6, 0.005, 0.02);
    assertSaturation("R134a", 30.0, 7.702, 173.1, 0.005, 0.025);
    assertSaturation("R134a", 60.0, 16.82, 139.1, 0.005, 0.025);
    assertSaturation("R12", -29.75, 1.013, 165.2, 0.006, 0.01);
    RefrigerantFluid r12 = RefrigerantFluid.of("R12");
    assertEquals(3.086, r12.getSaturationPressure(273.15), 0.006 * 3.086);
    assertEquals(7.449, r12.getSaturationPressure(303.15), 0.006 * 7.449);
  }

  @Test
  void ammoniaCubicParametersFollowTheReferenceEquation() {
    RefrigerantFluid reference = RefrigerantFluid.of("ammonia");
    for (String model : new String[] {"PR-MC", "SRK-MC", "PSRK"}) {
      SystemInterface fluid = "PR-MC".equals(model) ? new neqsim.thermo.system.SystemPrMathiasCopeman(273.15, 1.0)
          : "SRK-MC".equals(model) ? new neqsim.thermo.system.SystemSrkMathiasCopeman(273.15, 1.0)
              : new neqsim.thermo.system.SystemPsrkEos(273.15, 1.0);
      fluid.addComponent("ammonia", 1.0);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      RefrigerantFluid cubic = RefrigerantFluid.of("ammonia " + model, fluid);
      for (double temperature = 220.0; temperature <= 380.0; temperature += 40.0) {
        double expected = reference.getSaturationPressure(temperature);
        assertEquals(expected, cubic.getSaturationPressure(temperature), 0.005 * expected,
            model + " ammonia saturation pressure at " + temperature + " K");
      }
    }
    // Ideal-gas heat capacity of ammonia: 35.6 J/(mol K) at 300 K, 41.9 at 500 K and 56.5 at 1000 K (JANAF).
    assertEquals(35.6, idealGasCp("ammonia", 300.0), 0.3);
    assertEquals(41.9, idealGasCp("ammonia", 500.0), 0.3);
    assertEquals(56.5, idealGasCp("ammonia", 1000.0), 0.5);
  }

  @Test
  void aModelWhoseBubblePointIsNotAnEquilibriumIsRejected() {
    // The GERG-2008 bubble-point flash returns 21.5 bara for propane at 314 K where the saturation pressure is 14.3.
    SystemInterface gerg = new neqsim.thermo.system.SystemGERG2008Eos(273.15, 1.0);
    gerg.addComponent("propane", 1.0);
    gerg.createDatabase(true);
    gerg.setMixingRule(2);
    final RefrigerantFluid fluid = RefrigerantFluid.of("propane GERG-2008", gerg);
    assertEquals(4.745, fluid.getSaturationPressure(273.15), 0.01);
    assertThrows(IllegalStateException.class, () -> fluid.getSaturationPressure(318.15));
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
    assertEquals(85.4, idealGasCp("R134a", 300.0), 0.5);
    assertEquals(72.6, idealGasCp("R12", 300.0), 0.5);
  }

  /**
   * Gets the ideal-gas heat capacity of a component.
   *
   * @param component component name
   * @param temperature temperature in K
   * @return heat capacity in J/(mol K)
   */
  private static double idealGasCp(String component, double temperature) {
    SystemInterface fluid = new SystemSrkEos(300.0, 1.0);
    fluid.addComponent(component, 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return fluid.getPhase(0).getComponent(0).getCp0(temperature);
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
