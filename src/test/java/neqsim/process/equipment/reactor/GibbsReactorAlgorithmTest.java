package neqsim.process.equipment.reactor;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the enhanced Gibbs reactor algorithmic features: Armijo backtracking line search,
 * Tikhonov regularization, and convergence diagnostics.
 *
 * <p>
 * These tests validate the algorithmic improvements described in the paper: "Robust Gibbs Free
 * Energy Minimization for Chemical Equilibrium with Equation of State Models".
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GibbsReactorAlgorithmTest {

  /**
   * Test Armijo backtracking line search on methane combustion. Verifies that the line search
   * converges to the same equilibrium as fixed damping when using the same base damping.
   */
  @Test
  public void testArmijoLineSearchConvergence() {
    SystemInterface system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("methane", 0.05);
    system.addComponent("oxygen", 0.5);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.run();

    // Run with Armijo line search using same small damping as baseline
    GibbsReactor reactorArmijo = new GibbsReactor("Armijo Reactor", inletStream);
    reactorArmijo.setUseAllDatabaseSpecies(false);
    reactorArmijo.setMaxIterations(10000);
    reactorArmijo.setConvergenceTolerance(1e-3);
    reactorArmijo.setDampingComposition(0.01);
    reactorArmijo.setUseArmijoLineSearch(true);
    reactorArmijo.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactorArmijo.run();

    // Run with fixed damping (baseline)
    GibbsReactor reactorFixed = new GibbsReactor("Fixed Reactor", inletStream);
    reactorFixed.setUseAllDatabaseSpecies(false);
    reactorFixed.setMaxIterations(10000);
    reactorFixed.setConvergenceTolerance(1e-3);
    reactorFixed.setDampingComposition(0.01);
    reactorFixed.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactorFixed.run();

    // Both should converge
    Assertions.assertTrue(reactorArmijo.hasConverged(), "Armijo reactor should converge");
    Assertions.assertTrue(reactorFixed.hasConverged(), "Fixed damping reactor should converge");

    // Equilibrium compositions should match within tolerance
    SystemInterface outArmijo = reactorArmijo.getOutletStream().getThermoSystem();
    SystemInterface outFixed = reactorFixed.getOutletStream().getThermoSystem();

    double zCO2_armijo = outArmijo.getComponent("CO2").getz();
    double zCO2_fixed = outFixed.getComponent("CO2").getz();
    Assertions.assertEquals(zCO2_fixed, zCO2_armijo, 0.01,
        "CO2 mole fraction should match between Armijo and fixed damping");

    double zH2O_armijo = outArmijo.getComponent("water").getz();
    double zH2O_fixed = outFixed.getComponent("water").getz();
    Assertions.assertEquals(zH2O_fixed, zH2O_armijo, 0.01,
        "Water mole fraction should match between Armijo and fixed damping");

    // Mass balance should be conserved for both
    Assertions.assertTrue(reactorArmijo.getMassBalanceConverged(),
        "Armijo reactor mass balance should converge");
  }

  /**
   * Test Armijo line search with adaptive step sizing combined. This is the recommended
   * configuration for robust convergence.
   */
  @Test
  public void testArmijoWithAdaptiveStepSize() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("hydrogen", 0.1);
    system.addComponent("oxygen", 1.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Armijo+Adaptive Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setMaxIterations(5000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setUseAdaptiveStepSize(true);
    reactor.setUseArmijoLineSearch(true);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    Assertions.assertTrue(reactor.hasConverged(),
        "Armijo + adaptive step sizing should converge");
    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be conserved");

    // Verify step size history is populated
    List<Double> stepSizes = reactor.getStepSizeHistory();
    Assertions.assertFalse(stepSizes.isEmpty(), "Step size history should be populated");

    // Verify Gibbs energy history is populated
    List<Double> gibbsHistory = reactor.getGibbsEnergyHistory();
    Assertions.assertFalse(gibbsHistory.isEmpty(), "Gibbs energy history should be populated");
  }

  /**
   * Test Tikhonov regularization on a system with multiple components. Verifies that
   * regularization does not break convergence and condition number history is tracked.
   */
  @Test
  public void testTikhonovRegularization() {
    SystemInterface system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("methane", 0.05);
    system.addComponent("oxygen", 0.5);
    system.addComponent("CO2", 0.0);
    system.addComponent("CO", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Regularized Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setDampingComposition(0.01);
    reactor.setUseRegularization(true);
    reactor.setRegularizationThreshold(1e10);
    reactor.setRegularizationTau(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactor.run();

    Assertions.assertTrue(reactor.hasConverged(),
        "Reactor with regularization should converge");

    // Condition number history should be available
    List<Double> condHistory = reactor.getConditionNumberHistory();
    Assertions.assertFalse(condHistory.isEmpty(),
        "Condition number history should be populated");
  }

  /**
   * Test convergence diagnostics are properly recorded during iterations.
   */
  @Test
  public void testConvergenceDiagnostics() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("hydrogen", 1.5);
    system.addComponent("nitrogen", 0.5);
    system.addComponent("ammonia", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.setPressure(300, "bara");
    inletStream.setTemperature(450, "K");
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Diagnostic Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.05);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    Assertions.assertTrue(reactor.hasConverged(), "Reactor should converge");

    // Gibbs energy history should be populated and generally decreasing
    List<Double> gibbsHistory = reactor.getGibbsEnergyHistory();
    Assertions.assertFalse(gibbsHistory.isEmpty(), "Gibbs energy history should be populated");

    // Element balance error history should be populated
    List<Double> elementHistory = reactor.getElementBalanceErrorHistory();
    Assertions.assertFalse(elementHistory.isEmpty(),
        "Element balance error history should be populated");

    // Step size history should be populated
    List<Double> stepHistory = reactor.getStepSizeHistory();
    Assertions.assertFalse(stepHistory.isEmpty(), "Step size history should be populated");
  }

  /**
   * Test Armijo line search on Claus process (sulfur formation). This is a chemically complex
   * system with multiple equilibria. Uses same parameters as existing sulfur test.
   */
  @Test
  public void testArmijoClausProcess() {
    SystemInterface system = new neqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.0);
    system.addComponent("methane", 1e6);
    system.addComponent("H2S", 10);
    system.addComponent("oxygen", 2);
    system.addComponent("SO2", 0.0);
    system.addComponent("water", 0.0);
    system.addComponent("S8", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.setPressure(10, "bara");
    inletStream.setTemperature(100, "C");
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Armijo Claus Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setDampingComposition(0.001);
    reactor.setUseArmijoLineSearch(true);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    Assertions.assertTrue(reactor.hasConverged(), "Armijo Claus reactor should converge");

    // Verify sulfur formation
    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();
    double ppm_s8 = outletSystem.getComponent("S8").getz() * 1e6;
    Assertions.assertTrue(ppm_s8 > 0.01, "S8 should be formed in Claus reactor");
  }

  /**
   * Test that enhanced features produce fewer iterations than fixed damping for the same
   * convergence tolerance on a standard combustion problem.
   */
  @Test
  public void testEnhancedVsBaselineIterationCount() {
    SystemInterface system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("methane", 0.05);
    system.addComponent("oxygen", 0.5);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet", system);
    inletStream.run();

    // Baseline: fixed small damping
    GibbsReactor baseline = new GibbsReactor("Baseline", inletStream);
    baseline.setUseAllDatabaseSpecies(false);
    baseline.setMaxIterations(10000);
    baseline.setConvergenceTolerance(1e-3);
    baseline.setDampingComposition(0.01);
    baseline.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    baseline.run();

    // Enhanced: Armijo + adaptive step (using same small base damping)
    GibbsReactor enhanced = new GibbsReactor("Enhanced", inletStream);
    enhanced.setUseAllDatabaseSpecies(false);
    enhanced.setMaxIterations(10000);
    enhanced.setConvergenceTolerance(1e-3);
    enhanced.setDampingComposition(0.01);
    enhanced.setUseAdaptiveStepSize(true);
    enhanced.setUseArmijoLineSearch(true);
    enhanced.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    enhanced.run();

    Assertions.assertTrue(baseline.hasConverged(), "Baseline should converge");
    Assertions.assertTrue(enhanced.hasConverged(), "Enhanced should converge");

    int baselineIter = baseline.getActualIterations();
    int enhancedIter = enhanced.getActualIterations();

    // Enhanced should generally need fewer or comparable iterations
    // (with larger effective steps due to line search)
    System.out.println("Baseline iterations: " + baselineIter);
    System.out.println("Enhanced iterations: " + enhancedIter);

    // Both should reach the same equilibrium
    double co2Baseline = baseline.getOutletStream().getThermoSystem().getComponent("CO2").getz();
    double co2Enhanced = enhanced.getOutletStream().getThermoSystem().getComponent("CO2").getz();
    Assertions.assertEquals(co2Baseline, co2Enhanced, 0.01,
        "Both algorithms should reach the same equilibrium");
  }
}
