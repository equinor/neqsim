package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Onset-search contracts using a synthetic SRK solid-equilibrium fixture, not an empirical crude-oil calibration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AsphalteneOnsetFlashTest {
  /**
   * Create the fixed public synthetic composition without fitting any property or interaction.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return initialized model definition
   */
  private SystemInterface fluid(double temperature, double pressure) {
    SystemInterface result = new SystemSrkEos(temperature, pressure);
    result.addComponent("methane", 0.30);
    result.addComponent("n-heptane", 0.65);
    result.addComponent("asphaltene", 0.05);
    result.setMixingRule("classic");
    return result;
  }

  /** Missing characterization is an input error rather than a successful no-onset search. */
  @Test
  void rejectsMissingAsphalteneThroughBothFacadeOverloads() {
    SystemInterface uncharacterized = new SystemSrkEos(450.0, 500.0);
    uncharacterized.addComponent("n-heptane", 1.0);
    uncharacterized.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(uncharacterized);
    assertThrows(IllegalArgumentException.class, ops::asphalteneOnsetPressure);
    assertThrows(IllegalArgumentException.class, () -> ops.asphalteneOnsetPressure(500.0, 1.0));
    assertThrows(IllegalArgumentException.class, ops::asphalteneOnsetTemperature);
    assertThrows(IllegalArgumentException.class, () -> ops.asphalteneOnsetTemperature(500.0, 350.0, 550.0));
    assertEquals(450.0, uncharacterized.getTemperature(), 0.0);
    assertEquals(500.0, uncharacterized.getPressure(), 0.0);
    assertFalse(uncharacterized.doSolidPhaseCheck());
  }

  /** A name with exactly zero overall inventory does not characterize an asphaltene-bearing fluid. */
  @Test
  void rejectsZeroAsphalteneInventory() {
    SystemInterface empty = new SystemSrkEos(450.0, 500.0);
    empty.addComponent("n-heptane", 1.0);
    empty.addComponent("asphaltene", 0.0);
    empty.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new AsphalteneOnsetPressureFlash(empty).run());
    assertThrows(IllegalArgumentException.class, () -> new AsphalteneOnsetTemperatureFlash(empty).run());
  }

  /** Reject impossible steps before a bounded search can stall or silently truncate. */
  @Test
  void rejectsInvalidSteps() {
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0));
    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(fluid(500.0, 500.0));
    for (double step : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> pressure.setPressureStep(step));
      assertThrows(IllegalArgumentException.class, () -> temperature.setTemperatureStep(step));
    }
  }

  /** Search bounds must be finite, positive and contain the start. */
  @Test
  void rejectsInvalidRanges() {
    assertThrows(IllegalArgumentException.class,
        () -> new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0), 10.0, 20.0).run());
    assertThrows(IllegalArgumentException.class,
        () -> new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0), Double.NaN, 1.0).run());
    assertThrows(IllegalArgumentException.class,
        () -> new AsphalteneOnsetTemperatureFlash(fluid(500.0, 500.0), 500.0, 350.0, 450.0).run());
  }

  /** Initial precipitation is not a zero-width bracket proving onset at the start. */
  @Test
  void rejectsAlreadyPrecipitatedPressureStart() {
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(fluid(350.0, 500.0), 500.0, 490.0);
    assertThrows(IllegalStateException.class, flash::run);
    assertFalse(flash.isOnsetFound());
    assertTrue(Double.isNaN(flash.getOnsetPressure()));
  }

  /** A finite pressure result must be a genuine solid-stability transition and conserve the feed. */
  @Test
  void pressureOnsetHasIndependentFugacityBracket() {
    SystemInterface input = fluid(450.0, 500.0);
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(input, 500.0, 200.0);
    flash.setPressureStep(25.0);
    flash.run();
    double onset = flash.getOnsetPressure();
    assertTrue(flash.isOnsetFound());
    assertTrue(Double.isFinite(onset) && onset > 200.0 && onset < 500.0);
    assertTrue(solidDrivingForce(450.0, onset + 0.2) < 0.0, "Upper pressure must be undersaturated");
    assertTrue(solidDrivingForce(450.0, onset - 0.2) > 0.0, "Lower pressure must be supersaturated");
    assertEquals(onset, input.getPressure(), 0.0);
    assertEquals(onset * 0.1, flash.getOnsetPressure("MPa"), 1e-12);
    assertEquals(onset * 14.5038, flash.getOnsetPressure("psia"), 1e-10);
    assertSame(input, flash.getThermoSystem());
    assertInventory(input);
  }

  /** Cooling and heating must find the same solid-equilibrium transition at fixed pressure. */
  @Test
  void temperatureOnsetHasIndependentFugacityBracketInBothDirections() {
    AsphalteneOnsetTemperatureFlash down = new AsphalteneOnsetTemperatureFlash(fluid(500.0, 500.0), 500.0, 350.0,
        550.0);
    down.setTemperatureStep(25.0);
    down.run();
    double onset = down.getOnsetTemperature();
    assertTrue(down.isOnsetFound());
    assertTrue(Double.isFinite(onset) && onset > 350.0 && onset < 500.0);
    assertTrue(solidDrivingForce(onset + 1.0, 500.0) < 0.0);
    assertTrue(solidDrivingForce(onset - 1.0, 500.0) > 0.0);
    assertEquals(onset, down.getThermoSystem().getTemperature(), 0.0);
    assertEquals(onset - 273.15, down.getOnsetTemperature("C"), 1e-12);
    assertInventory(down.getThermoSystem());

    AsphalteneOnsetTemperatureFlash up = new AsphalteneOnsetTemperatureFlash(fluid(350.0, 500.0), 350.0, 300.0, 500.0);
    up.setSearchDecreasing(false);
    up.setTemperatureStep(25.0);
    up.run();
    assertTrue(up.isOnsetFound());
    assertEquals(onset, up.getOnsetTemperature(), 0.5);
    assertInventory(up.getThermoSystem());
  }

  /** Fully sampled stable intervals have an explicit NaN/false result and include the endpoint. */
  @Test
  void noOnsetIsDistinctFromInvalidInputAndIncludesTheRangeLimit() {
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0), 500.0, 495.0);
    pressure.setPressureStep(10.0);
    pressure.run();
    assertFalse(pressure.isOnsetFound());
    assertTrue(Double.isNaN(pressure.getOnsetPressure()));
    assertEquals(495.0, pressure.getThermoSystem().getPressure(), 0.0);

    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(fluid(500.0, 500.0), 500.0, 495.0,
        550.0);
    temperature.setTemperatureStep(10.0);
    temperature.run();
    assertFalse(temperature.isOnsetFound());
    assertTrue(Double.isNaN(temperature.getOnsetTemperature()));
    assertEquals(495.0, temperature.getThermoSystem().getTemperature(), 0.0);
  }

  /** A previous success must not survive a later no-onset pressure range. */
  @Test
  void pressureRerunClearsPreviousSuccessAndPrecipitatedAmount() {
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0), 500.0, 200.0);
    pressure.setPressureStep(25.0);
    pressure.run();
    assertTrue(pressure.isOnsetFound());
    pressure.setMinPressure(495.0);
    pressure.run();
    assertFalse(pressure.isOnsetFound());
    assertTrue(Double.isNaN(pressure.getOnsetPressure()));
    assertEquals(0.0, pressure.getPrecipitatedFraction(), 0.0);
  }

  /** A previous success must not survive a failed pressure or temperature rerun. */
  @Test
  void failedRerunsClearResultsAndPropagateTheCause() {
    FailingFluid pFluid = new FailingFluid(450.0);
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(pFluid, 500.0, 200.0);
    pressure.setPressureStep(25.0);
    pressure.run();
    assertTrue(pressure.isOnsetFound());
    pFluid.fail = true;
    assertThrows(IllegalStateException.class, pressure::run);
    assertFalse(pressure.isOnsetFound());
    assertTrue(Double.isNaN(pressure.getOnsetPressure()));

    FailingFluid tFluid = new FailingFluid(500.0);
    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(tFluid, 500.0, 350.0, 550.0);
    temperature.run();
    assertTrue(temperature.isOnsetFound());
    tFluid.fail = true;
    assertThrows(IllegalStateException.class, temperature::run);
    assertFalse(temperature.isOnsetFound());
    assertTrue(Double.isNaN(temperature.getOnsetTemperature()));
  }

  /** An incomplete budget-limited scan must not masquerade as a completed no-onset search. */
  @Test
  void rejectsExhaustedCoarseSearches() {
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(fluid(450.0, 500.0), 500.0, 495.0);
    pressure.setPressureStep(0.001);
    assertThrows(IllegalStateException.class, pressure::run);
    assertFalse(pressure.isOnsetFound());
    assertTrue(Double.isNaN(pressure.getOnsetPressure()));
    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(fluid(500.0, 500.0), 500.0, 495.0,
        550.0);
    temperature.setTemperatureStep(0.001);
    assertThrows(IllegalStateException.class, temperature::run);
    assertFalse(temperature.isOnsetFound());
    assertTrue(Double.isNaN(temperature.getOnsetTemperature()));
  }

  /** Unknown midpoint evidence must never be assigned a stable or unstable bracket sign. */
  @Test
  void refinementFailuresDoNotPublishAnOnset() {
    FailingFluid pFluid = new FailingFluid(450.0);
    pFluid.failPressureRefinement = true;
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(pFluid, 500.0, 200.0);
    pressure.setPressureStep(25.0);
    IllegalStateException pError = assertThrows(IllegalStateException.class, pressure::run);
    assertTrue(pError.getMessage().contains("TP flash failed"));
    assertFalse(pressure.isOnsetFound());
    assertTrue(Double.isNaN(pressure.getOnsetPressure()));

    FailingFluid tFluid = new FailingFluid(500.0);
    tFluid.failTemperatureRefinement = true;
    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(tFluid, 500.0, 350.0, 550.0);
    temperature.setTemperatureStep(25.0);
    IllegalStateException tError = assertThrows(IllegalStateException.class, temperature::run);
    assertTrue(tError.getMessage().contains("TP flash failed"));
    assertFalse(temperature.isOnsetFound());
    assertTrue(Double.isNaN(temperature.getOnsetTemperature()));
  }

  /** Execute the three onset-search examples in the CPA calculations guide with the declared synthetic SRK fluid. */
  @Test
  void guideExamplesUseTheReturnedResultAndSupportedConfiguration() {
    SystemInterface input = fluid(450.0, 500.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(input);
    double onsetP = ops.asphalteneOnsetPressure(500.0, 200.0);
    assertTrue(Double.isFinite(onsetP));
    input.setPressure(500.0);
    input.setTemperature(500.0);
    double onsetT = ops.asphalteneOnsetTemperature(500.0, 350.0, 550.0);
    assertTrue(Double.isFinite(onsetT));

    input.setTemperature(450.0);
    AsphalteneOnsetPressureFlash pressure = new AsphalteneOnsetPressureFlash(input, 500.0, 200.0);
    pressure.setPressureStep(25.0);
    pressure.run();
    assertTrue(pressure.isOnsetFound());
    input.setPressure(500.0);
    AsphalteneOnsetTemperatureFlash temperature = new AsphalteneOnsetTemperatureFlash(input, 500.0, 350.0, 550.0);
    temperature.setTemperatureStep(25.0);
    temperature.run();
    assertTrue(temperature.isOnsetFound());
  }

  /**
   * Independently test the incipient-solid fugacity inequality without running the onset or solid-split search.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return log fluid-to-pure-solid fugacity ratio
   */
  private double solidDrivingForce(double temperature, double pressure) {
    SystemInterface reference = fluid(temperature, pressure);
    reference.setSolidPhaseCheck("asphaltene");
    new ThermodynamicOperations(reference).TPflash(false);
    PhaseInterface solid = reference.getPhases()[3];
    int component = reference.getPhase(0).getComponent("asphaltene").getComponentNumber();
    double solidPhi = solid.getComponent(component).fugcoef(solid);
    double fluidFugacity = Double.POSITIVE_INFINITY;
    for (int p = 0; p < reference.getNumberOfPhases(); p++) {
      PhaseInterface phase = reference.getPhase(p);
      fluidFugacity = Math.min(fluidFugacity,
          phase.getComponent(component).getx() * phase.getComponent(component).getFugacityCoefficient());
    }
    return Math.log(fluidFugacity / solidPhi);
  }

  /**
   * Check independent component inventories after the final accepted flash.
   *
   * @param result flashed fluid
   */
  private void assertInventory(SystemInterface result) {
    double[] expected = {0.30, 0.65, 0.05};
    for (int c = 0; c < expected.length; c++) {
      double actual = 0.0;
      for (int p = 0; p < result.getNumberOfPhases(); p++) {
        actual += result.getPhase(p).getComponent(c).getNumberOfMolesInPhase();
      }
      assertEquals(expected[c], actual, 1e-7);
    }
  }

  /** Deterministic failure injection into a real characterized SRK fluid. */
  private static class FailingFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private boolean fail;
    private boolean failPressureRefinement;
    private boolean failTemperatureRefinement;

    /**
     * Create a normally working synthetic model.
     *
     * @param temperature initial temperature in K
     */
    FailingFluid(double temperature) {
      super(temperature, 500.0);
      addComponent("methane", 0.30);
      addComponent("n-heptane", 0.65);
      addComponent("asphaltene", 0.05);
      setMixingRule("classic");
    }

    /** {@inheritDoc} */
    @Override
    public void init(int level) {
      if (fail || failPressureRefinement && getPressure() > 430.0 && getPressure() < 440.0
          || failTemperatureRefinement && getTemperature() > 430.0 && getTemperature() < 440.0) {
        throw new IllegalStateException("Injected EOS initialization failure");
      }
      super.init(level);
    }
  }
}
