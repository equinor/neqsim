package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks hydrate-temperature start independence, input validation and multiphase inhibitor calculations.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HydrateTemperatureRobustnessTest {
  /**
   * A hot initial trial must reach the same finite-water boundary as cold trials.
   *
   * @param pressure absolute pressure in bar
   * @param waterPpm wet-gas water mole fraction in parts per million
   */
  @ParameterizedTest
  @CsvSource({"5,1", "5,10", "5,100", "5,1000", "20,1", "20,10", "20,100", "20,1000", "50,1", "50,10", "50,100",
      "50,1000", "100,1", "100,10", "100,100", "100,1000"})
  void traceWaterBoundaryIsIndependentOfStartingTemperature(double pressure, double waterPpm) {
    double reference = Double.NaN;
    for (double initial : new double[] {180.0, 240.0, 300.0, 350.0}) {
      SystemInterface fluid = traceGas(initial, pressure, waterPpm);
      SystemInterface input = fluid.clone();
      HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
      flash.run();
      assertConvergedAndBalanced(fluid, flash, input);
      if (Double.isNaN(reference)) {
        reference = fluid.getTemperature();
      } else {
        assertEquals(reference, fluid.getTemperature(), 1.0e-4,
            "Same boundary from " + initial + " K at " + pressure + " bara and " + waterPpm + " ppmv");
      }
      assertTrue(Math.abs(freshResidual(input, fluid.getTemperature())) < 1.0e-3,
          "A detached fluid flash must reproduce hydrate-water equilibrium");
    }
  }

  /** Invalid pressure is rejected before changing phase options or the input temperature. */
  @Test
  void invalidPressureIsRejectedBeforeChangingTheFluid() {
    for (double pressure : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      SystemInterface fluid = traceGas(300.0, 50.0, 100.0);
      fluid.setPressure(pressure);
      boolean multiPhase = fluid.doMultiPhaseCheck();
      int phases = fluid.getNumberOfPhases();
      HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
      assertThrows(IllegalArgumentException.class, flash::run);
      assertFalse(flash.isConverged());
      assertTrue(Double.isNaN(flash.getLastResidual()));
      assertNull(flash.getDiagnostics());
      assertEquals(300.0, fluid.getTemperature(), 0.0);
      assertEquals(phases, fluid.getNumberOfPhases());
      assertEquals(multiPhase, fluid.doMultiPhaseCheck());
    }
  }

  /** A missing or zero water inventory cannot define a hydrate-water boundary. */
  @Test
  void dryAndMissingWaterAreRejected() {
    SystemInterface dry = traceGas(300.0, 50.0, 0.0);
    assertThrows(IllegalArgumentException.class, () -> new HydrateFormationTemperatureFlash(dry).run());
    SystemInterface missing = new SystemSrkCPAstatoil(300.0, 50.0);
    missing.addComponent("methane", 1.0);
    missing.setMixingRule(10);
    missing.setHydrateCheck(true);
    assertThrows(IllegalArgumentException.class, () -> new HydrateFormationTemperatureFlash(missing).run());
  }

  /** Failure after success must clear the previous diagnostic state and permit a later valid calculation. */
  @Test
  void reusedOperationClearsSuccessAfterInvalidInputAndRecovers() {
    SystemInterface fluid = traceGas(300.0, 50.0, 100.0);
    HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
    flash.run();
    assertTrue(flash.isConverged());
    double reference = fluid.getTemperature();
    fluid.setPressure(0.0);
    assertThrows(IllegalArgumentException.class, flash::run);
    assertFalse(flash.isConverged());
    assertTrue(Double.isNaN(flash.getLastResidual()));
    assertNull(flash.getDiagnostics());
    fluid.setPressure(50.0);
    fluid.setTemperature(350.0);
    flash.run();
    assertTrue(flash.isConverged());
    assertEquals(reference, fluid.getTemperature(), 1.0e-4);
  }

  /**
   * MEG and methanol retain their aqueous activity effect when an oil phase is also present.
   *
   * @param inhibitor the thermodynamic inhibitor to include
   */
  @ParameterizedTest
  @ValueSource(strings = {"MEG", "methanol"})
  void oilAndInhibitorCalculationsRemainMultiphaseAndStartIndependent(String inhibitor) {
    SystemInterface uninhibited = oilGas(300.0, null);
    HydrateFormationTemperatureFlash baseline = new HydrateFormationTemperatureFlash(uninhibited);
    baseline.run();
    assertTrue(baseline.isConverged());
    double reference = Double.NaN;
    for (double initial : new double[] {240.0, 300.0, 350.0}) {
      SystemInterface fluid = oilGas(initial, inhibitor);
      SystemInterface input = fluid.clone();
      HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
      flash.run();
      assertConvergedAndBalanced(fluid, flash, input);
      assertTrue(fluid.getNumberOfPhases() >= 3, "Retain gas, oil and inhibited aqueous phases");
      assertTrue(fluid.getTemperature() < uninhibited.getTemperature() - 1.0,
          "Aqueous " + inhibitor + " must depress the hydrate temperature");
      if (Double.isNaN(reference)) {
        reference = fluid.getTemperature();
      } else {
        assertEquals(reference, fluid.getTemperature(), 1.0e-4);
      }
      assertTrue(Math.abs(freshResidual(input, fluid.getTemperature())) < 1.0e-3);
    }
  }

  /**
   * Builds a normalized synthetic natural gas with finite moisture.
   *
   * @param temperature temperature in kelvin
   * @param pressure absolute pressure in bar
   * @param waterPpm water mole fraction on a wet-gas basis, in ppm
   * @return configured CPA fluid with hydrate phases enabled
   */
  private SystemInterface traceGas(double temperature, double pressure, double waterPpm) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, pressure);
    double water = waterPpm * 1.0e-6;
    fluid.addComponent("methane", 0.90 * (1.0 - water));
    fluid.addComponent("ethane", 0.08 * (1.0 - water));
    fluid.addComponent("propane", 0.02 * (1.0 - water));
    fluid.addComponent("water", water);
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  /**
   * Builds a gas/oil/water feed, optionally adding inhibitor without removing any other component.
   *
   * @param temperature initial temperature in kelvin
   * @param inhibitor inhibitor name, or null for the uninhibited reference
   * @return configured multiphase CPA fluid
   */
  private SystemInterface oilGas(double temperature, String inhibitor) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("water", 0.15);
    if (inhibitor != null) {
      fluid.addComponent(inhibitor, 0.03);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  /**
   * Checks numerical convergence together with unchanged component amounts.
   *
   * @param fluid calculated fluid
   * @param flash completed operation
   * @param input detached input inventory
   */
  private void assertConvergedAndBalanced(SystemInterface fluid, HydrateFormationTemperatureFlash flash,
      SystemInterface input) {
    assertTrue(flash.isConverged(), "Hydrate fugacity equation must converge");
    assertTrue(Double.isFinite(fluid.getTemperature()));
    assertTrue(Math.abs(flash.getLastResidual()) <= 1.0e-6);
    assertNotNull(flash.getDiagnostics());
    assertTrue(flash.getDiagnostics().isConverged());
    assertTrue(flash.getDiagnostics().getComponentBalanceResidual() <= 1.0e-9);
    assertEquals(input.getTotalNumberOfMoles(), fluid.getTotalNumberOfMoles(), 1.0e-10);
    for (int component = 0; component < input.getNumberOfComponents(); component++) {
      assertEquals(input.getPhase(0).getComponent(component).getNumberOfmoles(),
          fluid.getPhase(0).getComponent(component).getNumberOfmoles(), 1.0e-10);
    }
  }

  /**
   * Reinitializes the fluid split independently and compares hydrate water to the water-rich fluid phase.
   *
   * @param input detached feed
   * @param temperature candidate temperature in kelvin
   * @return dimensionless one-minus hydrate/fluid water fugacity ratio
   */
  private double freshResidual(SystemInterface input, double temperature) {
    SystemInterface fresh = input.clone();
    fresh.setTemperature(temperature);
    fresh.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fresh).TPflash();
    HydrateFormationTemperatureFlash reference = new HydrateFormationTemperatureFlash(fresh);
    reference.setFug();
    fresh.getPhase(4).getComponent("water").fugcoef(fresh.getPhase(4));
    fresh.getPhase(4).getComponent("water").setx(1.0);
    int waterPhase = 0;
    double highestWaterFraction = 0.3;
    for (int phase = 0; phase < fresh.getNumberOfPhases(); phase++) {
      double fraction = fresh.getPhase(phase).getComponent("water").getx();
      if (fraction > highestWaterFraction) {
        highestWaterFraction = fraction;
        waterPhase = phase;
      }
    }
    return 1.0 - fresh.getPhase(4).getFugacity("water") / fresh.getPhase(waterPhase).getFugacity("water");
  }
}
