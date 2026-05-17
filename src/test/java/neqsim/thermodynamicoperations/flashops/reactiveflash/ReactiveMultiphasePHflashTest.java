package neqsim.thermodynamicoperations.flashops.reactiveflash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for ReactiveMultiphasePHflash.
 *
 * <p>
 * Validates the reactive PH flash (pressure-enthalpy flash with simultaneous chemical equilibrium)
 * against known results. Test strategy:
 * <ol>
 * <li><b>Round-trip tests:</b> Run reactive TP flash at known T, record H, then run PH flash at
 * that H and verify we recover the original T and composition.</li>
 * <li><b>Consistency tests:</b> Verify that the PH flash result satisfies H = H_spec and
 * composition matches reactive TP flash at the found temperature.</li>
 * <li><b>Multiple systems:</b> WGS reaction, steam methane reforming, ammonia synthesis, non-
 * reactive baseline.</li>
 * </ol>
 * </p>
 *
 * @author copilot
 * @version 1.0
 */
public class ReactiveMultiphasePHflashTest {

  /**
   * Test PH flash round-trip for the water-gas shift reaction.
   *
   * <p>
   * CO + H2O = CO2 + H2 at 1 bar. First run TP flash at 600 K, record enthalpy, then run PH flash
   * at recorded enthalpy and verify T recovers to 600 K.
   * </p>
   */
  @Test
  public void testWGSRoundTrip600K() {
    // Step 1: Create WGS system at 600 K, 1 bar
    SystemInterface system = new SystemSrkEos(600.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    // Step 2: Run reactive TP flash to get equilibrium composition
    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    assertTrue(tpFlash.isConverged(), "TP flash should converge");

    // Step 3: Record equilibrium enthalpy
    system.init(2);
    double Hspec = system.getEnthalpy();
    double xCO2_tp = system.getPhase(0).getComponent("CO2").getx();

    // Step 4: Perturb temperature (simulate unknown T)
    system.setTemperature(500.0); // Wrong temperature

    // Step 5: Run PH flash to recover temperature
    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "PH flash should converge");
    assertEquals(600.0, system.getTemperature(), 0.5, "PH flash should recover T=600K within 0.5K");

    // Verify enthalpy matches
    double Hactual = system.getEnthalpy();
    assertEquals(Hspec, Hactual, Math.abs(Hspec) * 1e-6, "Enthalpy should match specification");

    // Verify composition is consistent with TP flash at found T
    double xCO2_ph = system.getPhase(0).getComponent("CO2").getx();
    assertEquals(xCO2_tp, xCO2_ph, 0.01, "CO2 mole fraction should match TP flash result");
  }

  /**
   * Test PH flash round-trip for WGS at 1000 K.
   *
   * <p>
   * At high temperature, WGS equilibrium shifts back toward CO+H2O (reverse reaction). Tests the PH
   * flash at a different operating point.
   * </p>
   */
  @Test
  public void testWGSRoundTrip1000K() {
    SystemInterface system = new SystemSrkEos(1000.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    assertTrue(tpFlash.isConverged(), "TP flash at 1000K should converge");

    system.init(2);
    double Hspec = system.getEnthalpy();

    // Perturb T
    system.setTemperature(800.0);

    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "PH flash should converge");
    assertEquals(1000.0, system.getTemperature(), 1.0, "Should recover T=1000K");
  }

  /**
   * Test PH flash for steam methane reforming.
   *
   * <p>
   * CH4 + H2O = CO + 3H2 (plus WGS: CO + H2O = CO2 + H2). Two independent reactions at 1100 K, 1
   * bar. The PH flash must handle multiple simultaneous reactions with a large enthalpy change
   * (strongly endothermic reforming reaction).
   * </p>
   */
  @Test
  public void testSteamMethaneReformingRoundTrip() {
    SystemInterface system = new SystemSrkEos(1100.0, 1.0);
    system.addComponent("methane", 0.20);
    system.addComponent("water", 0.30);
    system.addComponent("CO", 0.15);
    system.addComponent("CO2", 0.10);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    assertTrue(tpFlash.isConverged(), "SMR TP flash should converge");

    system.init(2);
    double Hspec = system.getEnthalpy();

    // Perturb T
    system.setTemperature(900.0);

    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "SMR PH flash should converge");
    assertEquals(1100.0, system.getTemperature(), 2.0, "Should recover T=1100K within 2K");

    double Hactual = system.getEnthalpy();
    assertEquals(Hspec, Hactual, Math.abs(Hspec) * 1e-5, "Enthalpy should match");
  }

  /**
   * Test PH flash for ammonia synthesis at high pressure.
   *
   * <p>
   * N2 + 3H2 = 2NH3. The reaction is exothermic, so higher temperatures shift equilibrium toward
   * reactants. Tests at 100 bar (industrial conditions).
   * </p>
   */
  @Test
  public void testAmmoniaSynthesisRoundTrip() {
    SystemInterface system = new SystemSrkEos(500.0, 100.0);
    system.addComponent("nitrogen", 0.25);
    system.addComponent("hydrogen", 0.75);
    system.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    assertTrue(tpFlash.isConverged(), "NH3 TP flash should converge");

    system.init(2);
    double Hspec = system.getEnthalpy();

    // Perturb T
    system.setTemperature(600.0);

    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "NH3 PH flash should converge");
    assertEquals(500.0, system.getTemperature(), 2.0, "Should recover T=500K");
  }

  /**
   * Test PH flash for a non-reactive system (baseline comparison).
   *
   * <p>
   * Methane + ethane at 1 bar (no reactions). The PH flash should still work correctly, falling
   * back to standard TP flash behavior in the inner loop (NR=0 detected by RAND solver).
   * </p>
   */
  @Test
  public void testNonReactiveBaseline() {
    SystemInterface system = new SystemSrkEos(250.0, 10.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");

    // Run reactive TP flash (will detect NR=0)
    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();

    system.init(2);
    double Hspec = system.getEnthalpy();

    // Perturb T
    system.setTemperature(200.0);

    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "Non-reactive PH flash should converge");
    assertEquals(250.0, system.getTemperature(), 1.0, "Should recover T=250K");
  }

  /**
   * Test PH flash via ThermodynamicOperations convenience method.
   *
   * <p>
   * Verifies the public API path: ThermodynamicOperations.reactivePHflash(Hspec, type).
   * </p>
   */
  @Test
  public void testConvenienceMethod() {
    SystemInterface system = new SystemSrkEos(600.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.reactiveTPflash();
    system.init(2);
    double Hspec = system.getEnthalpy();

    // Perturb and recover
    system.setTemperature(500.0);
    ops.reactivePHflash(Hspec, 0);

    assertEquals(600.0, system.getTemperature(), 1.0, "Convenience method should recover T=600K");
  }

  /**
   * Test PH flash with large temperature perturbation.
   *
   * <p>
   * Starting from a very wrong initial temperature to test robustness of the Newton-Raphson
   * iteration with adaptive step control and bracket tracking.
   * </p>
   */
  @Test
  public void testLargeTemperaturePerturbation() {
    SystemInterface system = new SystemSrkEos(800.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    system.init(2);
    double Hspec = system.getEnthalpy();

    // Start from very wrong temperature
    system.setTemperature(300.0);

    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    assertTrue(phFlash.isConverged(), "PH flash should converge from far initial guess");
    assertEquals(800.0, system.getTemperature(), 2.0, "Should recover T=800K");
  }

  /**
   * Test PS flash (entropy specification) round-trip.
   *
   * <p>
   * Verifies the entropy specification mode: run reactive TP flash, record entropy, then use PS
   * flash to recover the temperature.
   * </p>
   */
  @Test
  public void testPSflashRoundTrip() {
    SystemInterface system = new SystemSrkEos(700.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.run();
    system.init(2);
    double Sspec = system.getEntropy();

    // Perturb T
    system.setTemperature(500.0);

    // Use PS flash
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.reactivePSflash(Sspec);

    assertEquals(700.0, system.getTemperature(), 2.0, "PS flash should recover T=700K");
  }

  /**
   * Test PH flash at different pressures.
   *
   * <p>
   * For ammonia synthesis (N2 + 3H2 = 2NH3), the equilibrium constant changes with pressure.
   * Verifies PH flash works correctly across a range of pressures.
   * </p>
   */
  @Test
  public void testPressureRange() {
    double[] pressures = {1.0, 10.0, 50.0, 200.0};

    for (double P : pressures) {
      SystemInterface system = new SystemSrkEos(500.0, P);
      system.addComponent("nitrogen", 0.25);
      system.addComponent("hydrogen", 0.75);
      system.setMixingRule("classic");

      ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
      tpFlash.run();
      system.init(2);
      double Hspec = system.getEnthalpy();

      system.setTemperature(600.0);

      ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
      phFlash.run();

      assertTrue(phFlash.isConverged(), "PH flash should converge at P=" + P + " bar");
      assertEquals(500.0, system.getTemperature(), 3.0, "Should recover T=500K at P=" + P + " bar");
    }
  }

  /**
   * Test consistency between reactive TP and PH flash.
   *
   * <p>
   * At the converged PH flash temperature, re-running a reactive TP flash should give the same
   * composition and enthalpy.
   * </p>
   */
  @Test
  public void testTPPHConsistency() {
    SystemInterface system = new SystemSrkEos(750.0, 5.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");

    // TP flash at 750K
    ReactiveMultiphaseTPflash tpFlash1 = new ReactiveMultiphaseTPflash(system);
    tpFlash1.run();
    system.init(2);
    double Hspec = system.getEnthalpy();
    double xCO2_tp = system.getPhase(0).getComponent("CO2").getx();

    // PH flash from different T
    system.setTemperature(500.0);
    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
    phFlash.run();

    // Now re-run TP flash at PH flash's temperature
    double Tph = system.getTemperature();
    SystemInterface system2 = new SystemSrkEos(Tph, 5.0);
    system2.addComponent("CO", 0.25);
    system2.addComponent("water", 0.25);
    system2.addComponent("CO2", 0.25);
    system2.addComponent("hydrogen", 0.25);
    system2.setMixingRule("classic");

    ReactiveMultiphaseTPflash tpFlash2 = new ReactiveMultiphaseTPflash(system2);
    tpFlash2.run();
    system2.init(2);

    double xCO2_verify = system2.getPhase(0).getComponent("CO2").getx();
    double H_verify = system2.getEnthalpy();

    assertEquals(xCO2_tp, xCO2_verify, 0.02,
        "CO2 from PH flash temperature should match original TP flash");
    assertEquals(Hspec, H_verify, Math.abs(Hspec) * 0.01,
        "Enthalpy at PH flash T should match specification");
  }
}
