package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Explicit ambient-temperature units and low-rate wet-gas riser energy regressions for issue #4450.
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeSurfaceTemperatureProfileTest {
  /** Number of sections in the reported riser geometry. */
  private static final int SECTIONS = 20;

  /**
   * Build a public wet gas-condensate feed and the reported riser geometry.
   *
   * @param massFlowKgHr feed mass flow in kg/hr
   * @param jouleThomson whether expansion cooling is enabled
   * @return the configured riser, before execution
   */
  private TwoFluidPipe riser(double massFlowKgHr, boolean jouleThomson) {
    SystemInterface fluid = new SystemSrkEos(283.65, 35.8);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("n-heptane", 8.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(massFlowKgHr, "kg/hr");
    Stream feed = new Stream("wet gas feed", fluid);
    feed.run();
    Assertions.assertTrue(feed.getFluid().getNumberOfPhases() >= 2, "the public feed must be multiphase");

    TwoFluidPipe pipe = new TwoFluidPipe("wet gas riser", feed);
    pipe.setLength(470.0);
    pipe.setDiameter(0.2286);
    pipe.setNumberOfSections(SECTIONS);
    double[] elevations = new double[SECTIONS];
    for (int i = 0; i < SECTIONS; i++) {
      elevations[i] = 375.0 * i / (SECTIONS - 1);
    }
    pipe.setElevationProfile(elevations);
    pipe.setHeatTransferCoefficient(4.8);
    pipe.setEnableJouleThomson(jouleThomson);
    return pipe;
  }

  /**
   * Return the reported seawater profile with explicit units.
   *
   * @param kelvin true to return Kelvin, false to return Celsius
   * @return temperatures for all sections
   */
  private double[] surfaceProfile(boolean kelvin) {
    double[] profile = new double[SECTIONS];
    for (int i = 0; i < SECTIONS; i++) {
      profile[i] = 5.0 + 3.0 * i / (SECTIONS - 1) + (kelvin ? 273.15 : 0.0);
    }
    return profile;
  }

  /**
   * Celsius input must represent the same physical boundary as a legacy Kelvin profile.
   *
   * @param massFlowKgHr feed mass flow in kg/hr
   * @param jouleThomson whether expansion cooling is enabled
   */
  @ParameterizedTest
  @CsvSource({"12800, false", "12800, true", "14000, true", "32400, true"})
  void testWetGasRiserWithExplicitCelsius(double massFlowKgHr, boolean jouleThomson) {
    TwoFluidPipe celsius = riser(massFlowKgHr, jouleThomson);
    celsius.setSurfaceTemperatureProfile(surfaceProfile(false), "C");
    celsius.run();
    TwoFluidPipe kelvin = riser(massFlowKgHr, jouleThomson);
    kelvin.setSurfaceTemperatureProfile(surfaceProfile(true));
    kelvin.run();

    Assertions.assertTrue(celsius.isSteadyStateConverged(), "Celsius case must converge");
    Assertions.assertTrue(kelvin.isSteadyStateConverged(), "Kelvin case must converge");
    Assertions.assertArrayEquals(kelvin.getTemperatureProfile(), celsius.getTemperatureProfile(), 1.0e-8);
    Assertions.assertArrayEquals(kelvin.getPressureProfile(), celsius.getPressureProfile(), 1.0e-5);
    double outletC = celsius.getOutletStream().getTemperature("C");
    Assertions.assertTrue(outletC > 0.0 && outletC < 10.5,
        "375 m of lift and modest expansion must not produce tens of kelvin of cooling: " + outletC);
    if (!jouleThomson) {
      double cp = celsius.getInletStream().getFluid().getCp("J/kgK");
      double lowerBoundK = 278.15 - 9.81 * 375.0 / cp;
      for (double temperatureK : celsius.getTemperatureProfile()) {
        Assertions.assertTrue(temperatureK >= lowerBoundK,
            "without expansion, wall exchange plus gravity must respect the energy bound");
      }
    } else {
      assertEnthalpyBalance(celsius, massFlowKgHr / 3600.0);
    }
  }

  /**
   * Compare against an independent PH flash including integrated wall duty and potential energy.
   *
   * @param pipe solved riser
   * @param massFlow mass flow in kg/s
   */
  private void assertEnthalpyBalance(TwoFluidPipe pipe, double massFlow) {
    double[] temperature = pipe.getTemperatureProfile();
    double[] position = pipe.getPositionProfile();
    double[] surface = surfaceProfile(true);
    double duty = 0.0;
    for (int i = 1; i < SECTIONS; i++) {
      duty += 4.8 * Math.PI * 0.2286 * (position[i] - position[i - 1])
          * (0.5 * (temperature[i] + temperature[i - 1]) - surface[i]);
    }
    SystemInterface reference = pipe.getInletStream().getFluid().clone();
    ThermodynamicOperations operations = new ThermodynamicOperations(reference);
    operations.TPflash();
    reference.init(2);
    double targetSpecificEnthalpy = reference.getEnthalpy("J/kg") - duty / massFlow - 9.81 * 375.0;
    reference.setPressure(pipe.getOutletStream().getPressure("bara"), "bara");
    operations.PHflash(targetSpecificEnthalpy, "J/kg");
    Assertions.assertEquals(reference.getTemperature("K"), temperature[SECTIONS - 1], 0.5,
        "riser temperature must agree with the wall-duty/potential-energy PH balance");
  }

  /** Unit conversion must copy the supplied array without changing caller-owned Celsius data. */
  @Test
  void testConversionCopiesInputAndPreservesKelvinConvention() {
    TwoFluidPipe pipe = riser(12800.0, false);
    double[] input = {5.0, 8.0};
    pipe.setSurfaceTemperatureProfile(input, "C");
    Assertions.assertArrayEquals(new double[] {278.15, 281.15}, pipe.getSurfaceTemperatureProfile(), 1.0e-12);
    Assertions.assertArrayEquals(new double[] {5.0, 8.0}, input, 0.0);
    input[0] = -100.0;
    Assertions.assertEquals(278.15, pipe.getSurfaceTemperatureProfile()[0], 1.0e-12);
    pipe.setSurfaceTemperatureProfile(new double[] {5.0, 8.0});
    Assertions.assertArrayEquals(new double[] {5.0, 8.0}, pipe.getSurfaceTemperatureProfile(), 0.0,
        "the legacy setter must retain absolute Kelvin semantics, without guessing Celsius");
    pipe.setSurfaceTemperatureProfile(null, "C");
    Assertions.assertNull(pipe.getSurfaceTemperatureProfile());
    pipe.setSurfaceTemperatureProfile(new double[0], "K");
    Assertions.assertEquals(0, pipe.getSurfaceTemperatureProfile().length);
  }

  /** Invalid temperatures and units must fail before replacing an existing valid profile. */
  @Test
  void testInvalidProfileDoesNotReplaceValidBoundary() {
    TwoFluidPipe pipe = riser(12800.0, false);
    pipe.setSurfaceTemperatureProfile(new double[] {5.0, 8.0}, "C");
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0}) {
      Assertions.assertThrows(IllegalArgumentException.class,
          () -> pipe.setSurfaceTemperatureProfile(new double[] {278.15, invalid}));
    }
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> pipe.setSurfaceTemperatureProfile(new double[] {-273.15}, "C"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> pipe.setSurfaceTemperatureProfile(new double[] {5.0}, "celsius"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> pipe.setSurfaceTemperatureProfile(new double[] {5.0}, null));
    Assertions.assertArrayEquals(new double[] {278.15, 281.15}, pipe.getSurfaceTemperatureProfile(), 1.0e-12);
  }
}
