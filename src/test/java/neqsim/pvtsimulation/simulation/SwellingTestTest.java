package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression checks for isothermal gas-injection swelling experiments.
 *
 * @author ESOL
 * @version 1.0
 */
class SwellingTestTest {
  /**
   * Build a synthetic binary oil at a different temperature from the experiment.
   *
   * @return a one-mole SRK oil with classic mixing
   */
  private SystemInterface oil() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.2);
    fluid.addComponent("nC10", 0.8);
    fluid.setMixingRule(1);
    fluid.useVolumeCorrection(false);
    return fluid;
  }

  /**
   * Build the methane injection gas.
   *
   * @return the injection fluid
   */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(1);
    return fluid;
  }

  /** Verify that reference and swollen volumes use the same temperature. */
  @Test
  void zeroInjectionHasUnitSwellingAtRequestedTemperature() {
    SwellingTest simulation = new SwellingTest(oil());
    simulation.setInjectionGas(gas());
    simulation.setTemperature(353.15);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0});
    simulation.runCalc();
    assertEquals(1.0, simulation.getRelativeOilVolume()[0], 1.0e-8);
  }

  /** Verify that the injection-gas template is not rescaled by the experiment. */
  @Test
  void injectionGasRemainsUnchanged() {
    SystemInterface injectionGas = gas();
    double originalMoles = injectionGas.getTotalNumberOfMoles();
    SwellingTest simulation = new SwellingTest(oil());
    simulation.setInjectionGas(injectionGas);
    simulation.setTemperature(353.15);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    simulation.runCalc();
    assertEquals(originalMoles, injectionGas.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(298.15, injectionGas.getTemperature(), 1.0e-12);
    assertEquals(50.0, injectionGas.getPressure(), 1.0e-12);
  }

  /**
   * Configure and execute the common synthetic addition schedule.
   *
   * @param temperature requested experiment temperature
   * @param unit temperature unit
   * @return the executed experiment
   */
  private SwellingTest run(double temperature, String unit) {
    SwellingTest simulation = new SwellingTest(oil());
    simulation.setInjectionGas(gas());
    simulation.setTemperature(temperature, unit);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    simulation.runCalc();
    return simulation;
  }

  /** Compare to an independent cubic-root SRK implementation with identical frozen inputs. */
  @Test
  void matchesIndependentSrkBubbleBranchReference() {
    // Reproduce with devtools/generate_srk_swelling_reference.py, no NeqSim imports.
    // Synthetic numerical verification, not measured reservoir-fluid accuracy.
    SwellingTest simulation = run(353.15, "K");
    assertArrayEquals(new double[] {51.28370973792926, 73.20109927826115, 93.14150754180672}, simulation.getPressures(),
        1.0e-4);
    assertArrayEquals(new double[] {1.0, 1.030102097197686, 1.0602317416359233}, simulation.getRelativeOilVolume(),
        1.0e-6);
  }

  /** Verify that configured Peneloux shifts are included in the reported volume ratio. */
  @Test
  void usesDensityCorrectedVolumes() {
    SystemInterface fluid = oil();
    fluid.useVolumeCorrection(true);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      fluid.getPhase(phase).getComponent("methane").setVolumeCorrection(1.0);
      fluid.getPhase(phase).getComponent("nC10").setVolumeCorrection(3.0);
      fluid.getPhase(phase).getComponent("methane").setVolumeCorrectionT(0.0);
      fluid.getPhase(phase).getComponent("nC10").setVolumeCorrectionT(0.0);
    }
    SwellingTest simulation = new SwellingTest(fluid);
    simulation.setInjectionGas(gas());
    simulation.setTemperature(353.15);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    simulation.runCalc();
    // Independent unshifted cubic volumes minus sum(n_i c_i), with c in m3/mol.
    double referenceVolume = 0.00021125416417646127 - 0.2e-5 - 0.8 * 3.0e-5;
    double finalVolume = 0.0002239783704126508 - 0.4e-5 - 0.8 * 3.0e-5;
    assertEquals(finalVolume / referenceVolume, simulation.getRelativeOilVolume()[2], 1.0e-6);
    assertArrayEquals(run(353.15, "K").getPressures(), simulation.getPressures(), 1.0e-6);
  }

  /** Verify Celsius/Kelvin equivalence and the default initial-temperature convention. */
  @Test
  void honorsTemperatureUnitsAndDefault() {
    SwellingTest kelvin = run(353.15, "K");
    SwellingTest celsius = run(80.0, "C");
    assertArrayEquals(kelvin.getPressures(), celsius.getPressures(), 1.0e-8);
    assertArrayEquals(kelvin.getRelativeOilVolume(), celsius.getRelativeOilVolume(), 1.0e-8);
    SwellingTest defaultTemperature = new SwellingTest(oil());
    defaultTemperature.setInjectionGas(gas());
    defaultTemperature.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    defaultTemperature.runCalc();
    assertArrayEquals(run(298.15, "K").getPressures(), defaultTemperature.getPressures(), 1.0e-8);
    assertEquals(298.15, defaultTemperature.getThermoSystem().getTemperature(), 1.0e-12);
  }

  /** Verify cumulative mole basis, component conservation and repeatability. */
  @Test
  void conservesComponentsAndRestartsFromInitialOil() {
    SystemInterface originalOil = oil();
    SwellingTest simulation = new SwellingTest(originalOil);
    simulation.setInjectionGas(gas());
    simulation.setTemperature(353.15);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    simulation.runCalc();
    SystemInterface swollen = simulation.getThermoSystem();
    assertEquals(1.2, swollen.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(0.4, swollen.getComponent("methane").getNumberOfmoles(), 1.0e-12);
    assertEquals(0.8, swollen.getComponent("nC10").getNumberOfmoles(), 1.0e-12);
    assertEquals(1.0, originalOil.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(298.15, originalOil.getTemperature(), 1.0e-12);
    double[] firstPressures = simulation.getPressures().clone();
    double[] firstVolumes = simulation.getRelativeOilVolume().clone();
    simulation.runCalc();
    assertArrayEquals(firstPressures, simulation.getPressures(), 1.0e-8);
    assertArrayEquals(firstVolumes, simulation.getRelativeOilVolume(), 1.0e-8);
    assertEquals(1.2, simulation.getThermoSystem().getTotalNumberOfMoles(), 1.0e-12);
  }

  /** Check nonzero initial additions, duplicate stages, and defensive schedule copying. */
  @Test
  void handlesCumulativeScheduleWithoutDoubleInjection() {
    SwellingTest simulation = new SwellingTest(oil());
    simulation.setInjectionGas(gas());
    simulation.setTemperature(353.15);
    double[] schedule = {10.0, 10.0, 20.0};
    simulation.setCummulativeMolePercentGasInjected(schedule);
    schedule[2] = 50.0;
    simulation.runCalc();
    SwellingTest reference = run(353.15, "K");
    assertEquals(reference.getPressures()[1], simulation.getPressures()[0], 1.0e-8);
    assertEquals(simulation.getPressures()[0], simulation.getPressures()[1], 1.0e-8);
    assertEquals(reference.getRelativeOilVolume()[2], simulation.getRelativeOilVolume()[2], 1.0e-8);
    assertEquals(1.2, simulation.getThermoSystem().getTotalNumberOfMoles(), 1.0e-12);
  }

  /** Reject schedules that do not describe cumulative additions. */
  @Test
  void rejectsInvalidSchedulesAndMissingGas() {
    SwellingTest simulation = new SwellingTest(oil());
    double[][] invalid = {null, {}, {-1.0}, {Double.NaN}, {Double.POSITIVE_INFINITY}, {10.0, 5.0}};
    for (double[] schedule : invalid) {
      assertThrows(IllegalArgumentException.class, () -> simulation.setCummulativeMolePercentGasInjected(schedule));
    }
    assertThrows(IllegalArgumentException.class, simulation::runCalc);
    simulation.setCummulativeMolePercentGasInjected(new double[] {10.0});
    assertThrows(IllegalArgumentException.class, simulation::runCalc);
    SystemInterface emptyGas = gas();
    emptyGas.setTotalNumberOfMoles(0.0);
    simulation.setInjectionGas(emptyGas);
    assertThrows(IllegalArgumentException.class, simulation::runCalc);
  }

  /** A reference-only experiment does not require an injection-gas template. */
  @Test
  void zeroInjectionDoesNotRequireGas() {
    SwellingTest simulation = new SwellingTest(oil());
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 0.0});
    simulation.runCalc();
    assertArrayEquals(new double[] {1.0, 1.0}, simulation.getRelativeOilVolume(), 1.0e-12);
  }

  /**
   * Verify conservation when the injection adds a component absent from the oil.
   *
   * @param usePr whether to use PR rather than SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedInjectionPreservesNewComponents(boolean usePr) {
    SystemInterface fluid = usePr ? new SystemPrEos(353.15, 50.0) : oil();
    if (usePr) {
      fluid.addComponent("methane", 0.2);
      fluid.addComponent("nC10", 0.8);
      fluid.setMixingRule(1);
    }
    SystemInterface injection = usePr ? new SystemPrEos(298.15, 50.0) : gas();
    if (usePr) {
      injection.addComponent("methane", 1.0);
    }
    injection.addComponent("ethane", 0.25);
    injection.setMixingRule(1);
    fluid.init(0);
    injection.init(0);
    double expectedMass = fluid.getMass("kg") + 0.2 * injection.getMolarMass();
    SwellingTest simulation = new SwellingTest(fluid);
    simulation.setInjectionGas(injection);
    simulation.setTemperature(353.15);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    simulation.runCalc();
    SystemInterface swollen = simulation.getThermoSystem();
    assertEquals(1.2, swollen.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(0.36, swollen.getComponent("methane").getNumberOfmoles(), 1.0e-12);
    assertEquals(0.04, swollen.getComponent("ethane").getNumberOfmoles(), 1.0e-12);
    assertEquals(0.8, swollen.getComponent("nC10").getNumberOfmoles(), 1.0e-12);
    assertEquals(expectedMass, swollen.getMass("kg"), 1.0e-12);
    assertEquals(1.25, injection.getTotalNumberOfMoles(), 1.0e-12);
    assertTrue(simulation.getRelativeOilVolume()[2] > 1.0);
  }

  /** Supercritical input must not silently become a swelling curve. */
  @Test
  void propagatesSaturationFailureWithoutPublishingResults() {
    SystemInterface methane = gas();
    SwellingTest simulation = new SwellingTest(methane);
    simulation.setTemperature(400.0);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0});
    assertThrows(IllegalStateException.class, simulation::runCalc);
    assertTrue(Double.isNaN(simulation.getPressures()[0]));
    assertTrue(Double.isNaN(simulation.getRelativeOilVolume()[0]));
  }

  /** Validate positive absolute temperature and a nonempty original sample. */
  @Test
  void rejectsInvalidTemperatureAndEmptyOil() {
    SwellingTest invalidTemperature = new SwellingTest(oil());
    invalidTemperature.setTemperature(-10.0);
    invalidTemperature.setCummulativeMolePercentGasInjected(new double[] {0.0});
    assertThrows(IllegalArgumentException.class, invalidTemperature::runCalc);
    SystemInterface emptyOil = oil();
    emptyOil.setTotalNumberOfMoles(0.0);
    SwellingTest invalidOil = new SwellingTest(emptyOil);
    invalidOil.setCummulativeMolePercentGasInjected(new double[] {0.0});
    assertThrows(IllegalArgumentException.class, invalidOil::runCalc);
  }
}
