package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;
import neqsim.standards.gasquality.Draft_ISO18453;
import neqsim.standards.gasquality.Standard_ISO18453;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks equilibrium rather than the temperature at an iteration limit.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WaterDewPointConvergenceTest {
  /**
   * Checks trace-water convergence over the ISO pressure range.
   *
   * @throws Exception if the dew-point operation fails
   */
  @Test
  void traceWaterReachesFugacityEquilibrium() throws Exception {
    for (double pressure : new double[] {5.0, 70.0, 100.0}) {
      for (double ppm : new double[] {1.0, 5.0, 22.0, 100.0}) {
        SystemInterface gas = methane(pressure, ppm);
        new ThermodynamicOperations(gas).waterDewPointTemperatureFlash();
        double relativeResidual = gas.getPhase(0).getComponent("water").getz()
            * gas.getPhase(0).getComponent("water").getFugacityCoefficient()
            / gas.getPhase(1).getComponent("water").getFugacityCoefficient() - 1.0;
        assertEquals(0.0, relativeResidual, 1.0e-7,
            "Water fugacity equilibrium at " + pressure + " bara and " + ppm + " ppm");
        assertTrue(gas.getTemperature() > 100.0 && gas.getTemperature() < 400.0);
      }
    }
  }

  /** A dry gas must not acquire a fabricated zero-kelvin dew point. */
  @Test
  void dryGasHasNoWaterDewPoint() {
    SystemInterface gas = methane(70.0, 0.0);
    assertThrows(IllegalArgumentException.class,
        () -> new ThermodynamicOperations(gas).waterDewPointTemperatureFlash());
  }

  /** Missing or zero moisture must fail before converting a single gas phase to a trial two-phase state. */
  @Test
  void invalidMoisturePreservesInputPhaseState() {
    for (boolean addZeroWater : new boolean[] {false, true}) {
      for (boolean mixedLiquid : new boolean[] {false, true}) {
        SystemInterface gas = new SystemSrkCPAstatoil(300.0, 50.0);
        gas.addComponent("methane", 1.0);
        if (addZeroWater) {
          gas.addComponent("water", 0.0);
        }
        gas.setMixingRule(10);
        new ThermodynamicOperations(gas).TPflash();
        int phaseCount = gas.getNumberOfPhases();
        double beta = gas.getBeta(0);
        WaterDewPointTemperatureFlash flash = new WaterDewPointTemperatureFlash(gas, mixedLiquid);
        assertThrows(IllegalArgumentException.class, flash::run);
        assertEquals(300.0, gas.getTemperature(), 0.0);
        assertEquals(phaseCount, gas.getNumberOfPhases());
        assertEquals(beta, gas.getBeta(0), 0.0);
        assertEquals(1.0, gas.getTotalNumberOfMoles(), 1.0e-15);
      }
    }
  }

  /** Invalid absolute pressure must fail rather than produce a finite temperature. */
  @Test
  void invalidPressureIsRejected() {
    for (double pressure : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      SystemInterface gas = methane(70.0, 22.0);
      gas.setPressure(pressure);
      assertThrows(IllegalArgumentException.class,
          () -> new ThermodynamicOperations(gas).waterDewPointTemperatureFlash());
    }
  }

  /** A missing bracket is an explicit failure rather than the last temperature tried. */
  @Test
  void unbracketedDewPointIsRejected() {
    SystemInterface gas = methane(70.0, 1.0e-90);
    assertThrows(IllegalStateException.class, () -> new ThermodynamicOperations(gas).waterDewPointTemperatureFlash());
  }

  /**
   * Retains the legacy pure-MEG calculation and returns its equilibrium phase state.
   *
   * @throws Exception if the dew-point operation fails
   */
  @Test
  void legacyMegReachesEquilibrium() throws Exception {
    SystemInterface gas = new SystemGERGwaterEos(273.15, 70.0);
    gas.addComponent("methane", 0.9999);
    gas.addComponent("MEG", 0.0001);
    gas.setMixingRule(8);
    new ThermodynamicOperations(gas).waterDewPointTemperatureFlash();
    double ratio = gas.getPhase(0).getComponent("MEG").getz()
        * gas.getPhase(0).getComponent("MEG").getFugacityCoefficient()
        / gas.getPhase(1).getComponent("MEG").getFugacityCoefficient();
    assertEquals(1.0, ratio, 1.0e-8);
    assertTrue(gas.getTemperature() > 100.0);
  }

  /** A fluid without even a water component also has no water dew point. */
  @Test
  void missingWaterHasNoWaterDewPoint() {
    SystemInterface gas = new SystemGERGwaterEos(273.15, 70.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(8);
    assertThrows(IllegalArgumentException.class,
        () -> new ThermodynamicOperations(gas).waterDewPointTemperatureFlash());
  }

  /** Standard wrappers must invalidate a previous successful result after failure. */
  @Test
  void failedCalculationDoesNotReturnStaleDewPoint() {
    Standard_ISO18453 standard = new Standard_ISO18453(methane(70.0, 22.0));
    standard.calculate();
    assertTrue(Double.isFinite(standard.getValue("dewPointTemperature")));
    standard.setPressure(0.0);
    standard.calculate();
    assertTrue(Double.isNaN(standard.getValue("dewPointTemperature")));
    assertTrue(!standard.isOnSpec());
    standard.setPressure(70.0);
    standard.calculate();
    assertTrue(Double.isFinite(standard.getValue("dewPointTemperature")));

    Draft_ISO18453 legacy = new Draft_ISO18453(methane(70.0, 22.0));
    legacy.calculate();
    assertTrue(Double.isFinite(legacy.getValue("dewPointTemperature")));
    legacy.setReferencePressure(0.0);
    legacy.calculate();
    assertTrue(Double.isNaN(legacy.getValue("dewPointTemperature")));
    assertTrue(!legacy.isOnSpec());
  }

  /** The GERG analyser must not report its trial temperature after a failed flash. */
  @Test
  void dryAnalyserReturnsNaN() {
    WaterDewPointAnalyser analyser = new WaterDewPointAnalyser(new Stream("dry gas", methane(70.0, 0.0)));
    analyser.setMethod("GERG-water-EOS");
    assertTrue(Double.isNaN(analyser.getMeasuredValue("C")));
  }

  /**
   * Checks independent PR cubic roots, using the ISO 18453 alpha function and binary parameters. The 34.4 bara case
   * also reproduces Rajzinger (2012), Table 2's calculated value, not its experimental value.
   */
  @Test
  void agreesWithIndependentIsoModelCalculation() throws Exception {
    double[][] cases = {{34.4, 24.21925056, -22.20}, {70.0, 22.0, -16.10590838}, {100.0, 100.0, 3.58235523}};
    for (double[] testCase : cases) {
      SystemInterface gas = methane(testCase[0], testCase[1]);
      new ThermodynamicOperations(gas).waterDewPointTemperatureFlash();
      // The bundled component database differs slightly from ISO Table 2's critical constants.
      assertEquals(testCase[2], gas.getTemperature("C"), 0.15);
    }
  }

  /**
   * Repeated calls preserve the feed composition and recover the same equilibrium.
   *
   * @throws Exception if the dew-point operation fails
   */
  @Test
  void repeatedCalculationPreservesFeed() throws Exception {
    SystemInterface gas = methane(70.0, 5.0);
    ThermodynamicOperations operations = new ThermodynamicOperations(gas);
    operations.waterDewPointTemperatureFlash();
    double temperature = gas.getTemperature();
    operations.waterDewPointTemperatureFlash();
    assertEquals(temperature, gas.getTemperature(), 1.0e-8);
    assertEquals(5.0e-6, gas.getPhase(0).getComponent("water").getz(), 1.0e-15);
    assertEquals(5.0e-6, gas.getPhase(0).getComponent("water").getx(), 1.0e-15);
    assertEquals(1.0, gas.getPhase(1).getComponent("water").getx(), 1.0e-15);
    assertEquals(70.0, gas.getPressure(), 1.0e-12);
  }

  /**
   * Creates a normalized synthetic methane/water gas.
   *
   * @param pressure absolute pressure in bar
   * @param ppm water mole fraction in parts per million
   * @return the GERG-water fluid
   */
  private SystemInterface methane(double pressure, double ppm) {
    SystemInterface gas = new SystemGERGwaterEos(273.15, pressure);
    gas.addComponent("methane", 1.0 - ppm * 1.0e-6);
    gas.addComponent("water", ppm * 1.0e-6);
    gas.setMixingRule(8);
    return gas;
  }
}
