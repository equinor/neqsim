package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.FreezingPointResult;

/**
 * Independent water melting-curve and gas-ice controls for the fusion-volume pressure correction.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ComponentSolidWaterPressureTest {
  /** Triple-point temperature in kelvin from IAPWS R14-08(2011), equation 1. */
  private static final double TRIPLE_TEMPERATURE = 273.16;
  /** Triple-point pressure in bar from IAPWS R14-08(2011), equation 1. */
  private static final double TRIPLE_PRESSURE_BARA = 0.00611657;

  /**
   * Solid and liquid references coincide at the specified triple point.
   */
  @Test
  void triplePointRetainsLiquidFugacityReference() {
    PhaseInterface phase = pureWaterSolid();
    assertEquals(0.0, logSolidOverLiquid(phase, TRIPLE_TEMPERATURE, TRIPLE_PRESSURE_BARA), 1.0e-13);
  }

  /**
   * The pressure derivative of the log fugacity ratio includes the bar-to-pascal conversion and positive volume change.
   */
  @Test
  void pressureDerivativeMatchesIndependentClapeyronVolume() {
    PhaseInterface phase = pureWaterSolid();
    ComponentSolid water = (ComponentSolid) phase.getComponent("water");
    assertEquals(6010.0, water.getHeatOfFusion(), 1.0e-10);
    double temperature = 260.0;
    double below = logSolidOverLiquid(phase, temperature, 20.0);
    double above = logSolidOverLiquid(phase, temperature, 80.0);
    // Independently evaluated from IAPWS equation 1's triple-point melting slope and the bundled fusion enthalpy.
    double expectedVolumeM3PerMol = 1.633925283183e-6;
    double expectedDerivativePerBar = expectedVolumeM3PerMol * 1.0e5 / (water.R * temperature);
    assertEquals(expectedDerivativePerBar, (above - below) / 60.0, expectedDerivativePerBar * 1.0e-10);
    assertTrue(above > below, "Ice has the greater molar volume at the water triple point");
  }

  /**
   * Compares the retained constant-volume fusion approximation with the independent IAPWS ice-Ih melting curve.
   *
   * @param pressure absolute pressure in bar
   * @param iapwsTemperature independently solved IAPWS equation 1 temperature in kelvin
   */
  @ParameterizedTest
  @CsvSource({"1.01325, 273.152519080", "20.0, 273.010886555", "50.0, 272.784781409", "80.0, 272.555845582",
      "100.0, 272.401654479"})
  void meltingCurveAgreesWithinConstantVolumeApproximation(double pressure, double iapwsTemperature) {
    double temperature = meltingTemperature(pureWaterSolid(), pressure);
    assertEquals(iapwsTemperature, temperature, 0.015,
        "Fusion/Cp approximation against IAPWS R14-08(2011), equation 1; not an IAPWS EOS replacement");
    assertTrue(temperature < TRIPLE_TEMPERATURE);
  }

  /**
   * Increased pressure lowers the water melting point with the independently known triple-point slope.
   */
  @Test
  void meltingSlopeHasClapeyronSignAndMagnitude() {
    PhaseInterface phase = pureWaterSolid();
    double low = meltingTemperature(phase, 1.0);
    double high = meltingTemperature(phase, 2.0);
    assertEquals(-0.007426, high - low, 1.0e-5);
  }

  /**
   * Checks finite-water CPA gas-ice roots independently calculated with the source-qualified pressure correction.
   *
   * @param pressure absolute pressure in bar
   * @param expectedC independently calculated gas-ice temperature in degrees Celsius
   * @throws Exception if the freezing calculation fails
   */
  @ParameterizedTest
  @CsvSource({"20.0, -14.732669369", "50.0, -5.990353826", "80.0, -2.265046202"})
  void gasIceRootsMatchIndependentPressureCorrectedControls(double pressure, double expectedC) throws Exception {
    SystemInterface gas = new SystemSrkCPAstatoil(280.0, pressure);
    double water = 100.0e-6;
    gas.addComponent("methane", 0.90 * (1.0 - water));
    gas.addComponent("ethane", 0.08 * (1.0 - water));
    gas.addComponent("propane", 0.02 * (1.0 - water));
    gas.addComponent("water", water);
    gas.setMixingRule(10);
    gas.setSolidPhaseCheck("water");
    FreezingPointResult result = new ThermodynamicOperations(gas).freezingPointTemperatureFlashResult();
    assertTrue(result.isConverged(), result.getFailureReason());
    assertEquals(expectedC, result.getTemperature("C"), 1.0e-5);
    assertTrue(Math.abs(result.getResidual()) < 1.0e-8);
  }

  /**
   * Configures a pure-water solid with the same CPA liquid reference used in the gas-ice operation.
   *
   * @return the water solid phase
   */
  private PhaseInterface pureWaterSolid() {
    SystemInterface fluid = new SystemSrkCPAstatoil(TRIPLE_TEMPERATURE, TRIPLE_PRESSURE_BARA);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    fluid.setSolidPhaseCheck("water");
    PhaseInterface phase = fluid.getPhases()[3];
    phase.getComponent("water").setx(1.0);
    ((ComponentSolid) phase.getComponent("water")).setSolidRefFluidPhase(fluid.getPhase(0));
    return phase;
  }

  /**
   * Evaluates the ice/liquid fugacity ratio with the production reference phases independently initialized at each
   * state.
   *
   * @param phase the configured pure-water solid phase
   * @param temperature temperature in kelvin
   * @param pressure absolute pressure in bar
   * @return log of the solid-to-liquid fugacity ratio
   */
  private double logSolidOverLiquid(PhaseInterface phase, double temperature, double pressure) {
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    ComponentSolid water = (ComponentSolid) phase.getComponent("water");
    double solidCoefficient = water.fugcoef2(phase);
    double liquidCoefficient = water.refPhase.getComponent("water").getFugacityCoefficient();
    return Math.log(solidCoefficient / liquidCoefficient);
  }

  /**
   * Brackets the equality of the solid and liquid references in a known near-triple-point interval.
   *
   * @param phase the configured pure-water solid phase
   * @param pressure absolute pressure in bar
   * @return the fusion-model melting temperature in kelvin
   */
  private double meltingTemperature(PhaseInterface phase, double pressure) {
    double lower = 270.0;
    double upper = TRIPLE_TEMPERATURE;
    assertTrue(logSolidOverLiquid(phase, lower, pressure) < 0.0);
    assertTrue(logSolidOverLiquid(phase, upper, pressure) > 0.0);
    for (int iteration = 0; iteration < 45; iteration++) {
      double middle = 0.5 * (lower + upper);
      if (logSolidOverLiquid(phase, middle, pressure) < 0.0) {
        lower = middle;
      } else {
        upper = middle;
      }
    }
    return 0.5 * (lower + upper);
  }
}
