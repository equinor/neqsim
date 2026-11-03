package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression tests for {@link SimpleTEGAbsorber} when the gas and lean TEG mixture splits off a separate hydrocarbon
 * liquid. The two-phase contactor flash used to converge to a non-material-balanced state and throw a component
 * inventory exception; the absorber now flashes with three phases and routes the hydrocarbon liquid to an outlet.
 *
 * <p>
 * All compositions are synthetic North Sea type export gas.
 * </p>
 */
class SimpleTEGAbsorberHydrocarbonLiquidTest extends NeqSimTest {
  /** Relative tolerance for component and mass balances. */
  private static final double BALANCE_TOLERANCE = 1.0e-6;

  /**
   * Builds and runs a contactor case at 59.82 bara with 647.9 kSm3/h water-saturated gas and 12.09 m3/h lean TEG.
   *
   * @param c6plusFactor multiplier on the n-hexane, n-heptane and n-octane content of the base gas
   * @param leanTemperatureC lean TEG temperature in Celsius
   * @param tegWeightFraction lean TEG purity (weight fraction TEG, rest water)
   * @param routing hydrocarbon liquid routing
   * @return the run absorber
   */
  static SimpleTEGAbsorber runCase(double c6plusFactor, double leanTemperatureC, double tegWeightFraction,
      SimpleTEGAbsorber.HydrocarbonLiquidRouting routing) {
    return runCase(c6plusFactor, leanTemperatureC, tegWeightFraction, routing, true);
  }

  /**
   * Builds and runs a contactor case at 59.82 bara with 647.9 kSm3/h water-saturated gas and 12.09 m3/h lean TEG.
   *
   * @param c6plusFactor multiplier on the n-hexane, n-heptane and n-octane content of the base gas
   * @param leanTemperatureC lean TEG temperature in Celsius
   * @param tegWeightFraction lean TEG purity (weight fraction TEG, rest water)
   * @param routing hydrocarbon liquid routing
   * @param stabilityCheck whether the absorber runs the hydrocarbon liquid stability check
   * @return the run absorber
   */
  static SimpleTEGAbsorber runCase(double c6plusFactor, double leanTemperatureC, double tegWeightFraction,
      SimpleTEGAbsorber.HydrocarbonLiquidRouting routing, boolean stabilityCheck) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 23.5, 59.82);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane"};
    double[] moles = {0.8, 1.5, 82.0, 8.0, 4.0, 0.6, 1.0, 0.3, 0.3, 0.2 * c6plusFactor, 0.1 * c6plusFactor,
        0.05 * c6plusFactor};
    for (int i = 0; i < names.length; i++) {
      gas.addComponent(names[i], moles[i]);
    }
    gas.addComponent("water", 0.0);
    gas.addComponent("TEG", 0.0);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(false);

    Stream dry = new Stream("dry gas", gas);
    dry.setFlowRate(647.9 * 24.0 / 1000.0, "MSm3/day");
    dry.setTemperature(23.5, "C");
    dry.setPressure(59.82, "bara");
    dry.run();
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("saturator", dry);
    saturator.run();
    Stream wet = new Stream("wet gas", saturator.getOutletStream());
    wet.run();

    SystemInterface lean = gas.clone();
    double[] z = new double[lean.getNumberOfComponents()];
    double nTeg = tegWeightFraction / 150.17;
    double nWater = (1.0 - tegWeightFraction) / 18.015;
    z[z.length - 1] = nTeg / (nTeg + nWater);
    z[z.length - 2] = nWater / (nTeg + nWater);
    lean.setMolarComposition(z);
    Stream leanTeg = new Stream("lean TEG", lean);
    leanTeg.setFlowRate(12.09 * 1120.0, "kg/hr");
    leanTeg.setTemperature(leanTemperatureC, "C");
    leanTeg.setPressure(59.82, "bara");
    leanTeg.run();

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("contactor");
    absorber.addGasInStream(wet);
    absorber.addSolventInStream(leanTeg);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.70);
    absorber.setInternalDiameter(3.1);
    absorber.setHydrocarbonLiquidRouting(routing);
    absorber.setHydrocarbonLiquidCheck(stabilityCheck);
    absorber.run();
    return absorber;
  }

  /**
   * Total moles of a component in a stream (all phases).
   *
   * @param stream the stream
   * @param component component name
   * @return component moles in the stream's thermo system
   */
  private static double moles(StreamInterface stream, String component) {
    return stream.getThermoSystem().getPhase(0).getComponent(component).getNumberOfmoles();
  }

  /**
   * Asserts the overall mass balance and the per-component mole balance of the absorber.
   *
   * @param absorber the run absorber
   */
  private static void assertBalances(SimpleTEGAbsorber absorber) {
    StreamInterface gasIn = absorber.getGasInStream();
    StreamInterface solventIn = absorber.getSolventInStream();
    StreamInterface gasOut = absorber.getGasOutStream();
    StreamInterface richOut = absorber.getLiquidOutStream();
    double massIn = gasIn.getFlowRate("kg/hr") + solventIn.getFlowRate("kg/hr");
    double massOut = gasOut.getFlowRate("kg/hr") + richOut.getFlowRate("kg/hr");
    assertEquals(massIn, massOut, BALANCE_TOLERANCE * massIn, "overall mass balance");
    SystemInterface reference = gasIn.getThermoSystem();
    for (int i = 0; i < reference.getPhase(0).getNumberOfComponents(); i++) {
      String name = reference.getPhase(0).getComponent(i).getName();
      double in = moles(gasIn, name) + moles(solventIn, name);
      double out = moles(gasOut, name) + moles(richOut, name);
      assertEquals(in, out, BALANCE_TOLERANCE * Math.max(Math.abs(in), 1.0e-12), "mole balance of " + name);
    }
  }

  /**
   * Water content of the treated gas phase in ppmv.
   *
   * @param absorber the run absorber
   * @return water mole fraction of the gas outlet's gas phase times 1e6
   */
  private static double waterPpmv(SimpleTEGAbsorber absorber) {
    return absorber.getGasOutStream().getThermoSystem().getPhase(0).getComponent("water").getx() * 1.0e6;
  }

  @Test
  void hydrocarbonLiquidSplitNoLongerThrowsAndConservesMass() {
    // Before the fix this case threw "changed n-hexane inventory during PH flash phase recombination".
    SimpleTEGAbsorber absorber = runCase(3.0, 29.75, 0.9992, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET);
    assertTrue(absorber.hasHydrocarbonLiquid(), "a separate hydrocarbon liquid should form");
    assertNotNull(absorber.getHydrocarbonLiquidOutStream());
    assertTrue(absorber.getHydrocarbonLiquidMoleFraction() > 0.0);
    assertTrue(absorber.isEquilibriumLiquidGlycolRich(), "the K-value phase must be the glycol phase, TEG wt frac = "
        + absorber.getEquilibriumLiquidTEGWeightFraction());
    double ppmv = waterPpmv(absorber);
    assertTrue(Double.isFinite(ppmv) && ppmv > 0.1 && ppmv < 100.0, "treated gas water content " + ppmv + " ppmv");
    assertTrue(absorber.getAbsorptionFactor() > 1.0);
    assertBalances(absorber);
  }

  @Test
  void tegBalanceWithHydrocarbonLiquid() {
    SimpleTEGAbsorber absorber = runCase(3.0, 29.75, 0.9992, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET);
    double tegIn = moles(absorber.getSolventInStream(), "TEG") + moles(absorber.getGasInStream(), "TEG");
    double tegGasOut = moles(absorber.getGasOutStream(), "TEG");
    double tegRichOut = moles(absorber.getLiquidOutStream(), "TEG");
    assertEquals(tegIn, tegGasOut + tegRichOut, BALANCE_TOLERANCE * tegIn, "TEG mole balance");
    assertTrue(tegRichOut / tegIn > 0.99, "nearly all TEG must leave in the rich TEG outlet: " + tegRichOut / tegIn);
    assertTrue(absorber.getLiquidOutStream().getThermoSystem().getPhase(0).getWtFrac("TEG") > 0.9,
        "rich TEG outlet must remain glycol");
  }

  @Test
  void reportedOperatingPointRunsWithThreePhaseFlash() {
    SimpleTEGAbsorber absorber = runCase(1.0, 29.25, 0.99968, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET);
    assertTrue(absorber.hasHydrocarbonLiquid());
    double ppmv = waterPpmv(absorber);
    assertTrue(ppmv > 0.1 && ppmv < 100.0, "treated gas water content " + ppmv + " ppmv");
    assertBalances(absorber);
  }

  @Test
  void hydrocarbonLiquidCanBeRoutedToRichSolvent() {
    SimpleTEGAbsorber absorber = runCase(3.0, 29.75, 0.9992,
        SimpleTEGAbsorber.HydrocarbonLiquidRouting.RICH_SOLVENT_OUTLET);
    assertTrue(absorber.hasHydrocarbonLiquid());
    assertEquals(1, absorber.getGasOutStream().getThermoSystem().getNumberOfPhases(),
        "the gas outlet carries no hydrocarbon liquid when it is routed to the rich solvent");
    assertTrue(absorber.getLiquidOutStream().getThermoSystem().getNumberOfPhases() >= 2);
    assertBalances(absorber);
  }

  @Test
  void disabledStabilityCheckPreservesInventoriesAndGlycolPhase() {
    // Without stability checking, a balanced glycol-rich two-phase solution is allowed. Whether this mixture
    // triggers the three-phase fallback depends on the flash path and JVM, so do not require a condensate split.
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("probe");
    assertTrue(absorber.isHydrocarbonLiquidCheck());
    SimpleTEGAbsorber fast = runCase(3.0, 29.75, 0.9992, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET, false);
    assertFalse(fast.isHydrocarbonLiquidCheck());
    assertTrue(fast.isEquilibriumLiquidGlycolRich(), "the fast path must retain a glycol-rich K-value phase");
    double ppmv = waterPpmv(fast);
    assertTrue(Double.isFinite(ppmv) && ppmv > 0.1 && ppmv < 100.0, "treated gas water content " + ppmv + " ppmv");
    assertBalances(fast);
    SimpleTEGAbsorber lean = runCase(0.0, 29.25, 0.99968, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET, false);
    assertFalse(lean.hasHydrocarbonLiquid());
    assertEquals(2.0306706847, waterPpmv(lean), 2.0306706847 * 1.0e-6);
  }

  @Test
  void leanGasWithoutHydrocarbonLiquidIsUnchanged() {
    // Reference values from the two-phase implementation before the three-phase fix.
    SimpleTEGAbsorber absorber = runCase(0.0, 29.25, 0.99968, SimpleTEGAbsorber.HydrocarbonLiquidRouting.GAS_OUTLET);
    assertFalse(absorber.hasHydrocarbonLiquid());
    assertNull(absorber.getHydrocarbonLiquidOutStream());
    assertEquals(0.0, absorber.getHydrocarbonLiquidMoleFraction(), 0.0);
    assertEquals(2.0306706847, waterPpmv(absorber), 2.0306706847 * 1.0e-6);
    assertEquals(297.24909952, absorber.getGasOutStream().getTemperature(), 297.24909952 * 1.0e-6);
    assertEquals(1.4102749112e4, absorber.getLiquidOutStream().getFlowRate("kg/hr"), 1.4102749112e4 * 1.0e-6);
    assertEquals(5.4420023689e5, absorber.getGasOutStream().getFlowRate("kg/hr"), 5.4420023689e5 * 1.0e-6);
    assertEquals(9.7762439002, absorber.getAbsorptionFactor(), 9.7762439002 * 1.0e-6);
    assertEquals(1, absorber.getGasOutStream().getThermoSystem().getNumberOfPhases());
    assertFalse(absorber.getGasOutStream().getThermoSystem().doMultiPhaseCheck(),
        "outlet keeps the inlet multiphase-check setting");
    assertBalances(absorber);
  }
}
