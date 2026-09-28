package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Qualifies TP-flash lifecycle invariance between the standard and extended component
 * databases.
 *
 * @author OpenAI
 * @version 1.0
 */
class TPflashExtendedComponentDatabaseLifecycleTest {
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double STATE_TOLERANCE = 1.0e-10;
  private static final String[] STATE_LABELS = {
    "ordinary reference", "multiphase reference", "poor initialization", "nearby low pressure",
    "nearby high pressure", "changed state", "returned state", "deterministic repeat"
  };

  /**
   * Verifies the synchronized neutral-component rows preserve the qualified TP-flash lifecycle.
   */
  @Test
  void standardAndExtendedDatabasesPreserveTpflashLifecycle() {
    LifecycleSnapshot standard;
    LifecycleSnapshot extended;
    synchronized (NeqSimDataBase.class) {
      try {
        NeqSimDataBase.useExtendedComponentDatabase(false);
        standard = runLifecycle("standard database");

        NeqSimDataBase.useExtendedComponentDatabase(true);
        extended = runLifecycle("extended database");
      } finally {
        NeqSimDataBase.useExtendedComponentDatabase(false);
      }
    }

    for (int state = 0; state < STATE_LABELS.length; state++) {
      assertEquivalentState(standard.states[state], extended.states[state], STATE_TOLERANCE,
          "standard versus extended database " + STATE_LABELS[state]);
    }
  }

  /**
   * Runs and qualifies the frozen water-rich PR lifecycle in the currently selected component
   * database.
   *
   * @param databaseLabel selected component-database label for assertion diagnostics
   * @return immutable snapshot of each qualified lifecycle state
   */
  private LifecycleSnapshot runLifecycle(String databaseLabel) {
    SystemInterface ordinaryReference = flashWaterBearingPr(500.0, false, false);
    SystemInterface multiphaseReference = flashWaterBearingPr(500.0, true, false);
    SystemInterface poorInitialization = flashWaterBearingPr(500.0, true, true);
    SystemInterface nearbyLowPressure = flashWaterBearingPr(499.0, true, false);
    SystemInterface nearbyHighPressure = flashWaterBearingPr(501.0, true, false);

    assertEquivalentState(ordinaryReference, multiphaseReference, 1.0e-8,
        databaseLabel + " ordinary versus multiphase");
    assertEquivalentState(multiphaseReference, poorInitialization, 1.0e-8,
        databaseLabel + " poor initialization");

    SystemInterface reused = multiphaseReference.clone();
    reused.setPressure(501.0, "bara");
    flashInPlace(reused);
    SystemInterface changedState = reused.clone();
    assertEquivalentState(nearbyHighPressure, changedState, 1.0e-8,
        databaseLabel + " changed pressure");

    reused.setPressure(500.0, "bara");
    flashInPlace(reused);
    SystemInterface returnedState = reused.clone();
    assertEquivalentState(multiphaseReference, returnedState, 1.0e-8,
        databaseLabel + " returned pressure");

    SystemInterface beforeRepeat = reused.clone();
    flashInPlace(reused);
    SystemInterface repeatedState = reused.clone();
    assertEquivalentState(beforeRepeat, repeatedState, STATE_TOLERANCE,
        databaseLabel + " deterministic repeat");

    SystemInterface[] states = {
      ordinaryReference, multiphaseReference, poorInitialization, nearbyLowPressure,
      nearbyHighPressure, changedState, returnedState, repeatedState
    };
    for (int state = 0; state < states.length; state++) {
      assertClosedEquilibrium(states[state], databaseLabel + " " + STATE_LABELS[state]);
    }
    return new LifecycleSnapshot(states);
  }

  /**
   * Builds and flashes the frozen 288.15 K water-bearing Peng-Robinson case.
   *
   * @param pressure pressure in bara
   * @param multiphaseCheck whether to enable multiphase checking
   * @param poorGuess whether to seed an intentionally poor two-phase beta split
   * @return flashed and initialized thermodynamic system
   */
  private SystemInterface flashWaterBearingPr(
      double pressure, boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = createWaterBearingPrSystem();
    system.setTemperature(288.15, "K");
    system.setPressure(pressure, "bara");
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
    flashInPlace(system);
    return system;
  }

  /**
   * Executes a TP flash and initializes properties on an existing system.
   *
   * @param system thermodynamic system to flash in place
   */
  private void flashInPlace(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
  }

  /**
   * Creates the frozen water-bearing Peng-Robinson regression fluid.
   *
   * @return configured thermodynamic system
   */
  private SystemInterface createWaterBearingPrSystem() {
    SystemInterface system = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    system.addComponent("nitrogen", 1.0);
    system.addComponent("methane", 90.0);
    system.addComponent("ethane", 2.0);
    system.addComponent("propane", 1.0);
    system.addComponent("i-butane", 1.0);
    system.addComponent("n-butane", 1.0);
    system.addComponent("i-pentane", 1.0);
    system.addComponent("n-pentane", 1.0);
    system.addComponent("n-hexane", 1.0);
    system.addComponent("nC10", 1.0);
    system.addComponent("water", 10.0);
    system.setMixingRule("classic");
    return system;
  }

  /**
   * Checks normalization, material balance, interphase fugacity equality, and finite properties.
   *
   * @param system flashed system to validate
   * @param label assertion label
   */
  private void assertClosedEquilibrium(SystemInterface system, String label) {
    double betaSum = 0.0;
    int componentCount = system.getPhase(0).getNumberOfComponents();
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label + " beta");
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition");
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, NORMALIZATION_TOLERANCE,
          label + " phase normalization");
      assertTrue(Double.isFinite(system.getPhase(phase).getZ())
          && system.getPhase(phase).getZ() > 0.0, label + " compressibility");
    }
    assertEquals(1.0, betaSum, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double maximumMaterialResidual = 0.0;
    double maximumFugacityResidual = 0.0;
    int fugacityComparisons = 0;
    for (int component = 0; component < componentCount; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase)
            * system.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));

      if (system.getNumberOfPhases() >= 2) {
        for (int phase = 1; phase < system.getNumberOfPhases(); phase++) {
          double referenceComposition =
              system.getPhase(0).getComponent(component).getx();
          double otherComposition = system.getPhase(phase).getComponent(component).getx();
          double referenceCoefficient =
              system.getPhase(0).getComponent(component).getFugacityCoefficient();
          double otherCoefficient =
              system.getPhase(phase).getComponent(component).getFugacityCoefficient();
          if (referenceComposition > 1.0e-20 && otherComposition > 1.0e-20
              && Double.isFinite(referenceCoefficient) && referenceCoefficient > 0.0
              && Double.isFinite(otherCoefficient) && otherCoefficient > 0.0) {
            maximumFugacityResidual = Math.max(maximumFugacityResidual,
                Math.abs(Math.log(referenceComposition * referenceCoefficient)
                    - Math.log(otherComposition * otherCoefficient)));
            fugacityComparisons++;
          }
        }
      }
    }

    assertTrue(maximumMaterialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material-balance residual " + maximumMaterialResidual);
    if (system.getNumberOfPhases() >= 2) {
      assertTrue(fugacityComparisons > 0, label + " must expose fugacity comparisons");
      assertTrue(maximumFugacityResidual < FUGACITY_TOLERANCE,
          label + " fugacity residual " + maximumFugacityResidual);
    }
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
  }

  /**
   * Compares two water-bearing states after ordering phases by water fraction.
   *
   * @param expected reference state
   * @param actual state under comparison
   * @param tolerance absolute fraction/composition tolerance and relative property tolerance
   * @param label assertion label
   */
  private void assertEquivalentState(
      SystemInterface expected, SystemInterface actual, double tolerance, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertClosedEquilibrium(expected, label + " expected");
    assertClosedEquilibrium(actual, label + " actual");

    Integer[] expectedOrder = phaseOrderByWaterFraction(expected);
    Integer[] actualOrder = phaseOrderByWaterFraction(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label);
      assertEquals(expected.getPhase(expectedPhase).getZ(),
          actual.getPhase(actualPhase).getZ(), tolerance, label);
      for (int component = 0;
          component < expected.getPhase(expectedPhase).getNumberOfComponents();
          component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance, label);
      }
    }

    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(),
        Math.max(1.0e-8, tolerance * Math.abs(expected.getEnthalpy())), label);
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-8, tolerance * Math.abs(expected.getGibbsEnergy())), label);
  }

  /**
   * Orders active phases by their water mole fraction for deterministic comparison.
   *
   * @param system flashed thermodynamic system
   * @return active phase indexes in ascending water-fraction order
   */
  private Integer[] phaseOrderByWaterFraction(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order, Comparator.comparingDouble(
        (Integer index) -> system.getPhase(index).getComponent("water").getx()));
    return order;
  }

  /** Immutable collection of the qualified lifecycle states. */
  private static final class LifecycleSnapshot {
    private final SystemInterface[] states;

    /**
     * Creates a lifecycle snapshot.
     *
     * @param states qualified lifecycle states in {@link #STATE_LABELS} order
     */
    private LifecycleSnapshot(SystemInterface[] states) {
      this.states = states.clone();
    }
  }
}
