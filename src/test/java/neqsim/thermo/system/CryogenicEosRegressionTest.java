package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.leachman.NeqSimLeachman;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks cryogenic EOS results against the references documented in docs/thermo/cryogenic_eos_validation.md.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
class CryogenicEosRegressionTest {
  private static final String FIXTURES = "/neqsim/thermo/cryogenic/";
  private static final String[] LNG_COMPONENTS = {"methane", "ethane", "propane", "nitrogen"};
  private static final double[] LNG_MOLE_FRACTIONS = {0.92, 0.05, 0.02, 0.01};

  /**
   * Checks the signed Leachman derivative against HEOS and independent centered enthalpy differences.
   *
   * @param temperature state temperature in K
   * @param pressure state pressure in bara
   * @param reference HEOS Joule–Thomson coefficient in K/bar
   */
  @ParameterizedTest
  @CsvSource({"25.0, 5.0, -0.04331861544", "100.0, 10.0, 0.12515725209", "250.0, 35.0, -0.02165171161"})
  void leachmanJouleThomsonUnitsAndDerivative(double temperature, double pressure, double reference) {
    PhaseInterface phase = hydrogenAt(temperature, pressure);
    double halfStep = 0.001;
    double enthalpyDifferenceP = hydrogenAt(temperature, pressure + halfStep).getEnthalpy("J/mol")
        - hydrogenAt(temperature, pressure - halfStep).getEnthalpy("J/mol");
    double enthalpyDifferenceT = hydrogenAt(temperature + halfStep, pressure).getEnthalpy("J/mol")
        - hydrogenAt(temperature - halfStep, pressure).getEnthalpy("J/mol");
    double coefficient = phase.getJouleThomsonCoefficient();
    double nativeCoefficient = new NeqSimLeachman(phase, "normal").propertiesLeachman()[13];
    assertEquals(100.0 * nativeCoefficient, coefficient, 1.0e-12);
    assertEquals(coefficient, phase.getJouleThomsonCoefficient("K/bar"), 0.0);
    assertEquals(coefficient / 1.0e5, phase.getJouleThomsonCoefficient("K/Pa"), 1.0e-15);
    assertEquals(reference, coefficient, 0.001 * Math.abs(reference));
    assertEquals(-enthalpyDifferenceP / enthalpyDifferenceT, coefficient, 0.001 * Math.abs(coefficient));
  }

  /**
   * Flashes normal hydrogen and initializes its molar enthalpy and Joule–Thomson coefficient.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return the single active phase
   */
  private static PhaseInterface hydrogenAt(double temperature, double pressure) {
    SystemInterface fluid = new SystemLeachmanEos(temperature, pressure, "hydrogen", false);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(1, fluid.getNumberOfPhases());
    return fluid.getPhase(0);
  }

  /**
   * Exercises the applicable models at the reference pure-fluid saturation points.
   *
   * @param component NeqSim component name
   * @param spinIsomer hydrogen spin isomer or none for methane
   * @param pressure saturation pressure in bara
   * @param temperature reference saturation temperature in K
   * @param vaporDensity reference vapor mass density in kg/m3
   * @param liquidDensity reference liquid mass density in kg/m3
   * @param latentHeat reference molar latent heat of vaporization in J/mol
   * @throws Exception if the bubble-point temperature flash fails
   */
  @ParameterizedTest(name = "{0} {1} at {2} bara")
  @CsvFileSource(resources = FIXTURES + "pure-saturation-heos.csv", numLinesToSkip = 1)
  void pureSaturation(String component, String spinIsomer, double pressure, double temperature, double vaporDensity,
      double liquidDensity, double latentHeat) throws Exception {
    if ("normal".equals(spinIsomer)) {
      SystemInterface hydrogen = new SystemLeachmanEos(20.369, pressure, component, false);
      assertSaturation(hydrogen, pressure, temperature, vaporDensity, liquidDensity, latentHeat, 0.01, 0.001, 0.001);
      return;
    }
    assertEquals("methane", component);
    assertEquals("none", spinIsomer);
    SystemInterface[] methaneModels = {new SystemPrEos(110.0, pressure), new SystemSrkEos(110.0, pressure),
        new SystemPrEos1978(110.0, pressure), new SystemSrkCPAstatoil(110.0, pressure),
        new SystemUMRPRUMCEos(110.0, pressure), new SystemGERG2008Eos(110.0, pressure)};
    for (SystemInterface model : methaneModels) {
      model.addComponent(component, 1.0);
      if (model instanceof SystemGERG2008Eos) {
        assertSaturation(model, pressure, temperature, vaporDensity, liquidDensity, latentHeat, 0.05, 0.003, 0.003);
      } else {
        boolean srkFamily = model instanceof SystemSrkEos;
        assertSaturation(model, pressure, temperature, vaporDensity, liquidDensity, latentHeat, srkFamily ? 1.0 : 0.5,
            srkFamily ? 0.05 : 0.15, srkFamily ? 0.05 : 0.03);
      }
    }
  }

  /**
   * Checks phase identity, fugacity, normalized inventories and two-phase properties before and after initialization.
   *
   * @param fluid initialized model selection and pure feed
   * @param pressure prescribed pressure in bara
   * @param temperature reference saturation temperature in K
   * @param vaporDensity reference vapor density in kg/m3
   * @param liquidDensity reference liquid density in kg/m3
   * @param latentHeat reference latent heat in J/mol
   * @param temperatureTolerance absolute saturation-temperature tolerance in K
   * @param densityTolerance relative tolerance for phase densities
   * @param heatTolerance relative tolerance for latent heat
   * @throws Exception if the bubble-point temperature flash fails
   */
  private static void assertSaturation(SystemInterface fluid, double pressure, double temperature, double vaporDensity,
      double liquidDensity, double latentHeat, double temperatureTolerance, double densityTolerance,
      double heatTolerance) throws Exception {
    if (fluid instanceof SystemSrkCPAstatoil) {
      fluid.setMixingRule(10);
    } else {
      fluid.setMixingRule("classic");
    }
    new ThermodynamicOperations(fluid).bubblePointTemperatureFlash();
    String model = fluid.getModelName();
    assertEquals(temperature, fluid.getTemperature(), temperatureTolerance, model);
    assertPureFugacityClosure(fluid, pressure);
    double vaporMolarVolume = fluid.getPhase(0).getMolarVolume();
    double liquidMolarVolume = fluid.getPhase(1).getMolarVolume();
    fluid.init(3);
    fluid.initPhysicalProperties();
    assertPureFugacityClosure(fluid, pressure);
    PhaseInterface vapor = fluid.getPhase(0);
    PhaseInterface liquid = fluid.getPhase(1);
    assertEquals(vaporMolarVolume, vapor.getMolarVolume(), 1.0e-7 * vaporMolarVolume, model);
    assertEquals(liquidMolarVolume, liquid.getMolarVolume(), 1.0e-7 * liquidMolarVolume, model);
    assertRelative(vaporDensity, vapor.getDensity(), densityTolerance, model + " vapor density");
    assertRelative(liquidDensity, liquid.getDensity(), densityTolerance, model + " liquid density");
    assertRelative(latentHeat, vapor.getEnthalpy("J/mol") - liquid.getEnthalpy("J/mol"), heatTolerance, model);
  }

  /**
   * Checks that the pure bubble-point flash exposes two distinct roots with closed fugacity and inventory.
   *
   * @param fluid flashed pure system
   * @param pressure specified pressure in bara
   */
  private static void assertPureFugacityClosure(SystemInterface fluid, double pressure) {
    assertEquals(2, fluid.getNumberOfPhases(), fluid.getModelName());
    PhaseInterface vapor = fluid.getPhase(0);
    PhaseInterface liquid = fluid.getPhase(1);
    assertEquals(PhaseType.GAS, vapor.getType());
    assertTrue(liquid.getType() == PhaseType.LIQUID || liquid.getType() == PhaseType.OIL);
    assertTrue(vapor.getMolarVolume() > liquid.getMolarVolume());
    assertEquals(pressure, vapor.getPressure(), 1.0e-7);
    assertEquals(pressure, liquid.getPressure(), 1.0e-7);
    assertEquals(1.0, vapor.getComponent(0).getx(), 1.0e-10);
    assertEquals(1.0, liquid.getComponent(0).getx(), 1.0e-10);
    assertEquals(fluid.getTotalNumberOfMoles(), vapor.getNumberOfMolesInPhase() + liquid.getNumberOfMolesInPhase(),
        1.0e-9);
    assertLogFugacityClosure(vapor, liquid);
  }

  /**
   * Compares GERG-2008 with the independently executed HEOS TP flash at cold starts and repeat calls.
   *
   * @param temperature flash temperature in K
   * @param pressure flash pressure in bara
   * @param topology reference liquid, VLE or gas result
   * @param vaporFraction reference mole fraction of the feed present as vapor
   * @param density reference bulk mass density in kg/m3
   */
  @ParameterizedTest
  @CsvFileSource(resources = FIXTURES + "lng-flash-heos.csv", numLinesToSkip = 1)
  void gergColdStartsAndRepeatedFlashes(double temperature, double pressure, String topology, double vaporFraction,
      double density) {
    SystemInterface fluid = syntheticLNG(temperature, pressure);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (int run = 0; run < 2; run++) {
      operations.TPflash();
      assertLNGState(fluid, temperature, pressure, topology);
      assertEquals(vaporFraction, fluid.hasPhaseType(PhaseType.GAS) ? fluid.getBeta(0) : 0.0, 0.001);
      assertRelative(density, bulkDensity(fluid), 0.01, "HEOS bulk density");
    }
  }

  /** Recycles one fluid across the liquid, two-phase and gas regions to test warm phase-index and cache changes. */
  @Test
  void gergWarmSweepCrossesPhaseBoundariesInBothDirections() {
    SystemInterface fluid = syntheticLNG(110.0, 10.0);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    double[] temperatures = {110.0, 170.0, 135.0, 155.0, 120.0, 205.0, 110.0};
    String[] topologies = {"LIQUID", "VLE", "LIQUID", "VLE", "LIQUID", "GAS", "LIQUID"};
    for (int state = 0; state < temperatures.length; state++) {
      fluid.setTemperature(temperatures[state]);
      operations.TPflash();
      assertLNGState(fluid, temperatures[state], 10.0, topologies[state]);
    }
  }

  /**
   * Checks a held-out experimental density for the GERG-2008 model at the reported state.
   *
   * @param temperature measured temperature in K
   * @param pressure measured absolute pressure in bara
   * @param density experimental mass density in kg/m3
   * @param uncertainty relative expanded experimental uncertainty with coverage factor 2
   */
  @ParameterizedTest
  @CsvFileSource(resources = FIXTURES + "lng-norway-measured.csv", numLinesToSkip = 1)
  void gergAgainstMeasuredLNGDensity(double temperature, double pressure, double density, double uncertainty) {
    SystemInterface fluid = new SystemGERG2008Eos(temperature, pressure);
    fluid.addComponent("methane", 0.91798);
    fluid.addComponent("ethane", 0.05698);
    fluid.addComponent("propane", 0.01303);
    fluid.addComponent("n-butane", 0.00396);
    fluid.addComponent("nitrogen", 0.00805);
    new ThermodynamicOperations(fluid).TPflash();
    assertLNGState(fluid, temperature, pressure, "LIQUID");
    // Published model discrepancy of up to 0.06% plus the expanded experimental uncertainty.
    assertRelative(density, fluid.getPhase(0).getDensity(), 0.0006 + uncertainty, "LNG-Norway liquid density");
  }

  /** Confirms a nonphysical pressure does not get treated as an alternate density branch. */
  @Test
  void gergRejectsNonpositivePressure() {
    SystemInterface fluid = syntheticLNG(110.0, 10.0);
    fluid.init(0);
    fluid.getPhase(0).setPressure(-1.0);
    assertThrows(IllegalStateException.class, () -> fluid.getPhase(0).getDensity_GERG2008());
  }

  /**
   * Creates the synthetic LNG feed specified in issue #5285.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return GERG-2008 system with its default flash settings
   */
  private static SystemInterface syntheticLNG(double temperature, double pressure) {
    SystemInterface fluid = new SystemGERG2008Eos(temperature, pressure);
    for (int component = 0; component < LNG_COMPONENTS.length; component++) {
      fluid.addComponent(LNG_COMPONENTS[component], LNG_MOLE_FRACTIONS[component]);
    }
    return fluid;
  }

  /**
   * Checks topology, pressure roots and phase/component material balances at a flashed LNG state.
   *
   * @param fluid flashed GERG-2008 system
   * @param temperature requested temperature in K
   * @param pressure requested pressure in bara
   * @param topology expected liquid, VLE or gas result
   */
  private static void assertLNGState(SystemInterface fluid, double temperature, double pressure, String topology) {
    boolean twoPhase = "VLE".equals(topology);
    assertEquals(twoPhase ? 2 : 1, fluid.getNumberOfPhases(), "phase count at " + temperature + " K");
    assertEquals(temperature, fluid.getTemperature(), 0.0);
    assertEquals(pressure, fluid.getPressure(), 1.0e-10);
    PhaseType expectedFirst = twoPhase || "GAS".equals(topology) ? PhaseType.GAS : PhaseType.LIQUID;
    assertEquals(expectedFirst, fluid.getPhase(0).getType(), "first phase at " + temperature + " K");
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double[] properties = phase.getProperties_GERG2008();
      assertEquals(pressure * 100.0, properties[0], 1.0e-7 * pressure * 100.0);
      assertTrue(properties[1] > 0.0 && properties[2] > 0.0 && phase.getDensity() > 0.0);
      assertTrue(fluid.getBeta(phaseIndex) > 0.0);
      betaSum += fluid.getBeta(phaseIndex);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double reconstructed = 0.0;
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        reconstructed += fluid.getBeta(phaseIndex) * fluid.getPhase(phaseIndex).getComponent(component).getx();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), reconstructed, 1.0e-8);
    }
    if (twoPhase) {
      PhaseInterface vapor = fluid.getPhase(0);
      PhaseInterface liquid = fluid.getPhase(1);
      assertEquals(PhaseType.LIQUID, liquid.getType());
      assertTrue(liquid.getDensity() > vapor.getDensity());
      assertLogFugacityClosure(vapor, liquid);
    }
  }

  /**
   * Verifies agreement of component log fugacities for an equilibrium phase pair.
   *
   * @param vapor active vapor phase
   * @param liquid active liquid phase
   */
  private static void assertLogFugacityClosure(PhaseInterface vapor, PhaseInterface liquid) {
    for (int component = 0; component < vapor.getNumberOfComponents(); component++) {
      double vaporPartialPressure = vapor.getComponent(component).getx() * vapor.getPressure();
      double liquidPartialPressure = liquid.getComponent(component).getx() * liquid.getPressure();
      double vaporLogFugacity = vapor.getComponent(component).getLogFugacityCoefficient()
          + Math.log(vaporPartialPressure);
      double liquidLogFugacity = liquid.getComponent(component).getLogFugacityCoefficient()
          + Math.log(liquidPartialPressure);
      assertTrue(Double.isFinite(vaporLogFugacity) && Double.isFinite(liquidLogFugacity));
      assertEquals(vaporLogFugacity, liquidLogFugacity, 1.0e-6, "component " + component);
    }
  }

  /**
   * Computes mixture bulk density from additive phase volumes and phase masses.
   *
   * @param fluid flashed system
   * @return mass per unit total phase volume in kg/m3
   */
  private static double bulkDensity(SystemInterface fluid) {
    double mass = 0.0;
    double volume = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double phaseMass = fluid.getBeta(phaseIndex) * phase.getMolarMass();
      mass += phaseMass;
      volume += phaseMass / phase.getDensity();
    }
    return mass / volume;
  }

  /**
   * Compares an actual positive quantity against a reference using a relative error bound.
   *
   * @param expected reference value
   * @param actual calculated value
   * @param tolerance fractional relative tolerance
   * @param label assertion context
   */
  private static void assertRelative(double expected, double actual, double tolerance, String label) {
    assertEquals(expected, actual, tolerance * expected, label);
  }
}
