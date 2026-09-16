package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification coverage for TP flashes after combining fluids in opposite input orders.
 *
 * <p>
 * The synthetic gas and hydrocarbon-liquid inputs reproduce the public shape reported in issue 1362: a petroleum
 * pseudo-component exists only in one operand. The tests qualify numerical order invariance; they do not reproduce or
 * validate the reporter's unavailable private fluid.
 * </p>
 */
class TPflashAddFluidOrderTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "n-heptane", "C10_PC" };
  private static final double REFERENCE_TEMPERATURE_K = 280.0;
  private static final double REFERENCE_PRESSURE_BARA = 30.0;
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double EQUIVALENCE_TOLERANCE = 1.0e-10;

  /**
   * Feed identity and mole inventory must not depend on which operand receives the other fluid.
   */
  @Test
  void feedInventoryIsIndependentOfAddOrder() {
    SystemInterface gasThenOil = combine(true, false);
    SystemInterface oilThenGas = combine(false, false);

    assertEquals(COMPONENTS.length, gasThenOil.getNumberOfComponents(), "gas-then-oil component count");
    assertEquals(COMPONENTS.length, oilThenGas.getNumberOfComponents(), "oil-then-gas component count");
    for (String name : COMPONENTS) {
      assertNotNull(gasThenOil.getComponent(name), "gas-then-oil component " + name);
      assertNotNull(oilThenGas.getComponent(name), "oil-then-gas component " + name);
      assertEquals(gasThenOil.getComponent(name).getNumberOfmoles(), oilThenGas.getComponent(name).getNumberOfmoles(),
          NORMALIZATION_TOLERANCE, "feed moles for " + name);
    }
  }

  /**
   * Ordinary and multiphase-enabled flashes must remain closed and order independent nearby.
   */
  @Test
  void ordinaryAndMultiphaseFlashesAreOrderIndependent() {
    for (boolean multiphaseCheck : new boolean[] { false, true }) {
      for (double pressureBara : new double[] { 28.0, REFERENCE_PRESSURE_BARA, 32.0 }) {
        SystemInterface gasThenOil = flash(combine(true, multiphaseCheck), pressureBara);
        SystemInterface oilThenGas = flash(combine(false, multiphaseCheck), pressureBara);

        String label = (multiphaseCheck ? "multiphase" : "ordinary") + " at " + pressureBara + " bara";
        assertEquivalentState(gasThenOil, oilThenGas, label);
      }
    }

    SystemInterface ordinary = flash(combine(true, false), REFERENCE_PRESSURE_BARA);
    SystemInterface multiphase = flash(combine(true, true), REFERENCE_PRESSURE_BARA);
    assertEquivalentState(ordinary, multiphase, "ordinary/multiphase agreement");
  }

  /**
   * Poor initialization, state reuse, return continuity, and repeat execution must preserve order-independent
   * equilibrium.
   */
  @Test
  void poorInitializationAndReuseRemainOrderIndependent() {
    SystemInterface gasThenOil = combine(true, true);
    SystemInterface oilThenGas = combine(false, true);
    preparePoorGuess(gasThenOil);
    preparePoorGuess(oilThenGas);
    flash(gasThenOil, REFERENCE_PRESSURE_BARA);
    flash(oilThenGas, REFERENCE_PRESSURE_BARA);
    assertEquivalentState(gasThenOil, oilThenGas, "poor initialization");

    gasThenOil.setPressure(31.0, "bara");
    oilThenGas.setPressure(31.0, "bara");
    flash(gasThenOil, 31.0);
    flash(oilThenGas, 31.0);
    assertEquivalentState(gasThenOil, oilThenGas, "changed pressure");

    gasThenOil.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    oilThenGas.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    flash(gasThenOil, REFERENCE_PRESSURE_BARA);
    flash(oilThenGas, REFERENCE_PRESSURE_BARA);
    SystemInterface freshGasThenOil = flash(combine(true, true), REFERENCE_PRESSURE_BARA);
    assertEquivalentState(freshGasThenOil, gasThenOil, "returned gas-then-oil state");
    assertEquivalentState(freshGasThenOil, oilThenGas, "returned oil-then-gas state");

    SystemInterface settled = gasThenOil.clone();
    flash(gasThenOil, REFERENCE_PRESSURE_BARA);
    assertEquivalentState(settled, gasThenOil, "deterministic repeat");
  }

  private SystemInterface combine(boolean gasFirst, boolean multiphaseCheck) {
    SystemInterface gas = createGas();
    SystemInterface oil = createOil();
    SystemInterface combined = gasFirst ? gas.clone() : oil.clone();
    combined.addFluid(gasFirst ? oil : gas);
    combined.setTemperature(REFERENCE_TEMPERATURE_K, "K");
    combined.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    combined.setMixingRule("classic");
    combined.setMultiPhaseCheck(multiphaseCheck);
    return combined;
  }

  private SystemInterface createGas() {
    SystemInterface gas = new SystemSrkEos(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA);
    gas.addComponent("methane", 0.50);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    return gas;
  }

  private SystemInterface createOil() {
    SystemInterface oil = new SystemSrkEos(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA);
    oil.addComponent("n-heptane", 0.40);
    oil.addTBPfraction("C10", 0.05, 0.142, 0.82);
    oil.setMixingRule("classic");
    return oil;
  }

  private void preparePoorGuess(SystemInterface system) {
    system.init(0);
    assertTrue(system.getMaxNumberOfPhases() >= 2, "poor initialization requires two phase slots");
    system.setBeta(0, 1.0e-12);
    system.setBeta(1, 1.0 - 1.0e-12);
  }

  private SystemInterface flash(SystemInterface system, double pressureBara) {
    system.setPressure(pressureBara, "bara");
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void assertEquivalentState(SystemInterface expected, SystemInterface actual, String label) {
    assertQualifiedState(expected, label + " expected");
    assertQualifiedState(actual, label + " actual");

    for (PhaseType type : new PhaseType[] { PhaseType.GAS, PhaseType.OIL }) {
      int expectedPhase = findPhase(expected, type);
      int actualPhase = findPhase(actual, type);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), EQUIVALENCE_TOLERANCE,
          label + " beta " + type);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-8,
          label + " compressibility " + type);
      for (String name : COMPONENTS) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(name).getx(),
            actual.getPhase(actualPhase).getComponent(name).getx(), EQUIVALENCE_TOLERANCE,
            label + " composition " + type + "/" + name);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), label + " enthalpy");
  }

  private void assertQualifiedState(SystemInterface system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label + " phase count");
    assertTrue(system.hasPhaseType(PhaseType.GAS), label + " gas phase");
    assertTrue(system.hasPhaseType(PhaseType.OIL), label + " oil phase");

    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0, label + " beta " + phase);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (String name : COMPONENTS) {
        double composition = system.getPhase(phase).getComponent(name).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition " + phase + "/" + name);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE, label + " composition normalization " + phase);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE, label + " material-balance residual " + materialResidual);

    double fugacityResidual = maximumComparableLogFugacityResidual(system);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + fugacityResidual);
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private int findPhase(SystemInterface system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + type);
  }

  private void assertExtensiveEquals(double expected, double actual, String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, 1.0e-10 * Math.abs(expected)), label);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (String name : COMPONENTS) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(name).getx();
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(system.getPhase(0).getComponent(name).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface system) {
    int gasPhase = findPhase(system, PhaseType.GAS);
    int oilPhase = findPhase(system, PhaseType.OIL);
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (String name : COMPONENTS) {
      double gasComposition = system.getPhase(gasPhase).getComponent(name).getx();
      double oilComposition = system.getPhase(oilPhase).getComponent(name).getx();
      double gasCoefficient = system.getPhase(gasPhase).getComponent(name).getFugacityCoefficient();
      double oilCoefficient = system.getPhase(oilPhase).getComponent(name).getFugacityCoefficient();
      if (gasComposition > 1.0e-20 && oilComposition > 1.0e-20 && Double.isFinite(gasCoefficient)
          && gasCoefficient > 0.0 && Double.isFinite(oilCoefficient) && oilCoefficient > 0.0) {
        maximumResidual = Math.max(maximumResidual,
            Math.abs(Math.log(gasComposition * gasCoefficient) - Math.log(oilComposition * oilCoefficient)));
        comparisons++;
      }
    }
    assertTrue(comparisons > 0, "expected at least one comparable cross-phase fugacity");
    return maximumResidual;
  }
}
