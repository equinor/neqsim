package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification coverage for water-bearing SRK-CPA well-fluid TP flashes.
 *
 * <p>
 * The component inventories are established public repository regressions. They qualify numerical closure and lifecycle
 * behavior at a small set of states; they are not independent experimental validation of the fluid characterization or
 * CPA parameters.
 * </p>
 */
class TPflashWellFluidConsistencyTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double EQUIVALENCE_TOLERANCE = 1.0e-8;

  @Test
  void baseWellFluidRecoversFromPoorGuessNearbyStateAndRepeat() {
    SystemInterface reference = flashBaseWellFluid(303.15, 65.0, false);
    SystemInterface poorGuess = flashBaseWellFluid(303.15, 65.0, true);

    assertClosedEquilibrium(reference, "base reference");
    assertEquivalentEquilibrium(reference, poorGuess, "base poor beta initialization");

    for (double pressureBara : new double[] { 64.5, 65.0, 65.5 }) {
      assertClosedEquilibrium(flashBaseWellFluid(303.15, pressureBara, false),
          "base nearby pressure " + pressureBara + " bara");
    }

    SystemInterface changedState = reference.clone();
    changedState.setPressure(65.5, "bara");
    flash(changedState);
    assertEquivalentEquilibrium(flashBaseWellFluid(303.15, 65.5, false), changedState,
        "base changed pressure");

    changedState.setPressure(65.0, "bara");
    flash(changedState);
    assertEquivalentEquilibrium(reference, changedState, "base return pressure");

    SystemInterface repeatedReference = changedState.clone();
    flash(changedState);
    assertEquivalentEquilibrium(repeatedReference, changedState, "base deterministic repeat");
  }

  @Test
  void waterRichEndpointRetainsDensityClosureAndLifecycleContinuity() {
    SystemInterface reference = flashWaterRichWellFluid(339.04, 1.5, false);
    SystemInterface poorGuess = flashWaterRichWellFluid(339.04, 1.5, true);

    assertClosedEquilibrium(reference, "water-rich reference");
    assertEquals(1.432253736300898, reference.getPhase(0).getDensity(), 1.0e-5,
        "established phase-zero density reference in kg/m3");
    assertEquivalentEquilibrium(reference, poorGuess, "water-rich poor beta initialization");

    for (double temperatureK : new double[] { 338.54, 339.04, 339.54 }) {
      assertClosedEquilibrium(flashWaterRichWellFluid(temperatureK, 1.5, false),
          "water-rich nearby temperature " + temperatureK + " K");
    }

    SystemInterface changedState = reference.clone();
    changedState.setTemperature(339.54, "K");
    flash(changedState);
    assertEquivalentEquilibrium(flashWaterRichWellFluid(339.54, 1.5, false), changedState,
        "water-rich changed temperature");

    changedState.setTemperature(339.04, "K");
    flash(changedState);
    assertEquivalentEquilibrium(reference, changedState, "water-rich return temperature");

    SystemInterface repeatedReference = changedState.clone();
    flash(changedState);
    assertEquivalentEquilibrium(repeatedReference, changedState, "water-rich deterministic repeat");
  }

  private SystemInterface flashBaseWellFluid(double temperatureK, double pressureBara,
      boolean poorGuess) {
    SystemInterface system = createWellFluid();
    system.setTemperature(temperatureK, "K");
    system.setPressure(pressureBara, "bara");
    prepareGuess(system, poorGuess);
    flash(system);
    return system;
  }

  private SystemInterface flashWaterRichWellFluid(double temperatureK, double pressureBara,
      boolean poorGuess) {
    SystemInterface system = createWellFluid();
    system.setMolarComposition(new double[] { 0.0, 4.76579e-6, 1.21459e-5, 1.3409e-3,
        3.30439e-2, 5.06e-3, 7.34e-3, 1.53e-3, 4.11e-3, 1.58e-3, 2.255e-3,
        2.8779e-4, 8.58e-4, 8.73e-4, 8.5e-4, 3.88e-3, 7.36e-2, 1.47e-1, 6.176e-2,
        3.69e-2, 7.735e-3, 1.023e-2, 6.19e-3, 4.3e-3, 1.2e-2, 8.96e-3, 1.539e-3,
        5.9921e-1 });
    system.setTemperature(temperatureK, "K");
    system.setPressure(pressureBara, "bara");
    prepareGuess(system, poorGuess);
    flash(system);
    return system;
  }

  private void prepareGuess(SystemInterface system, boolean poorGuess) {
    system.setMultiPhaseCheck(true);
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
  }

  private void flash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
  }

  private SystemInterface createWellFluid() {
    SystemInterface system = new SystemSrkCPAstatoil(303.15, 65.0);
    system.addComponent("oxygen", 0.0);
    system.addComponent("H2S", 0.00008);
    system.addComponent("nitrogen", 0.08);
    system.addComponent("CO2", 3.56);
    system.addComponent("methane", 87.36);
    system.addComponent("ethane", 4.02);
    system.addComponent("propane", 1.54);
    system.addComponent("i-butane", 0.2);
    system.addComponent("n-butane", 0.42);
    system.addComponent("i-pentane", 0.15);
    system.addComponent("n-pentane", 0.20);

    system.addTBPfraction("C6_Frigg", 0.24, 84.99 / 1000.0, 695.0 / 1000.0);
    system.addTBPfraction("C7_Frigg", 0.34, 97.87 / 1000.0, 718.0 / 1000.0);
    system.addTBPfraction("C8_Frigg", 0.33, 111.54 / 1000.0, 729.0 / 1000.0);
    system.addTBPfraction("C9_Frigg", 0.19, 126.1 / 1000.0, 749.0 / 1000.0);
    system.addTBPfraction("C10_Frigg", 0.15, 140.14 / 1000.0, 760.0 / 1000.0);
    system.addTBPfraction("C11_Frigg", 0.69, 175.0 / 1000.0, 830.0 / 1000.0);
    system.addTBPfraction("C12_Frigg", 0.5, 280.0 / 1000.0, 914.0 / 1000.0);
    system.addTBPfraction("C13_Frigg", 0.103, 560.0 / 1000.0, 980.0 / 1000.0);

    system.addTBPfraction("C6_ML_WestCtrl", 0.0, 84.0 / 1000.0, 684.0 / 1000.0);
    system.addTBPfraction("C7_ML_WestCtrl", 0.0, 97.9 / 1000.0, 742.0 / 1000.0);
    system.addTBPfraction("C8_ML_WestCtrl", 0.0, 111.5 / 1000.0, 770.0 / 1000.0);
    system.addTBPfraction("C9_ML_WestCtrl", 0.0, 126.1 / 1000.0, 790.0 / 1000.0);
    system.addTBPfraction("C10_ML_WestCtrl", 0.0, 140.14 / 1000.0, 805.0 / 1000.0);
    system.addTBPfraction("C11_ML_WestCtrl", 0.0, 175.0 / 1000.0, 815.0 / 1000.0);
    system.addTBPfraction("C12_ML_WestCtrl", 0.0, 280.0 / 1000.0, 835.0 / 1000.0);
    system.addTBPfraction("C13_ML_WestCtrl", 0.0, 450.0 / 1000.0, 850.0 / 1000.0);
    system.addComponent("water", 12.01);
    system.setMixingRule(10);
    return system;
  }

  private void assertClosedEquilibrium(SystemInterface system, String label) {
    assertTrue(system.getNumberOfPhases() >= 1, label + " phase count");
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double beta = system.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0,
          label + " phase fraction");
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int componentIndex = 0;
          componentIndex < system.getPhase(phaseIndex).getNumberOfComponents();
          componentIndex++) {
        double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " phase composition");
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE,
          label + " phase normalization");
      assertTrue(Double.isFinite(system.getPhase(phaseIndex).getZ())
          && system.getPhase(phaseIndex).getZ() > 0.0, label + " compressibility");
      assertTrue(Double.isFinite(system.getPhase(phaseIndex).getDensity())
          && system.getPhase(phaseIndex).getDensity() > 0.0, label + " density");
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " maximum material-balance residual " + materialResidual);

    if (system.getNumberOfPhases() >= 2) {
      double fugacityResidual = maximumComparableLogFugacityResidual(system);
      assertTrue(fugacityResidual < FUGACITY_TOLERANCE,
          label + " maximum comparable log-fugacity residual " + fugacityResidual);
    }
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual,
      String label) {
    assertClosedEquilibrium(expected, label + " expected");
    assertClosedEquilibrium(actual, label + " actual");
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label + " phase count");

    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase),
          EQUIVALENCE_TOLERANCE, label + " phase fraction");
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase),
          EQUIVALENCE_TOLERANCE, label + " compressibility");
      for (int componentIndex = 0;
          componentIndex < expected.getPhase(expectedPhase).getNumberOfComponents();
          componentIndex++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhase).getComponent(componentIndex).getx(),
            EQUIVALENCE_TOLERANCE, label + " phase composition");
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), label + " enthalpy");
  }

  private Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, phaseIndex -> phaseIndex);
    Arrays.sort(order,
        Comparator.comparingDouble(
            (Integer phaseIndex) -> system.getPhase(phaseIndex).getComponent("water").getx())
            .thenComparingDouble(
                phaseIndex -> system.getPhase(phaseIndex).getDensity()));
    return order;
  }

  private void assertExtensiveEquals(double expected, double actual, String label) {
    assertEquals(expected, actual,
        Math.max(1.0e-6, EQUIVALENCE_TOLERANCE * Math.abs(expected)), label);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0;
        componentIndex < system.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex)
            * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(componentIndex).getz() - recoveredFeed));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int componentIndex = 0;
        componentIndex < system.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      if (system.getPhase(0).getComponent(componentIndex).getz() <= 1.0e-14) {
        continue;
      }
      for (int phaseIndex = 1; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        double referenceComposition =
            system.getPhase(0).getComponent(componentIndex).getx();
        double otherComposition =
            system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        if (referenceComposition <= 1.0e-20 || otherComposition <= 1.0e-20) {
          continue;
        }
        double referenceCoefficient =
            system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient();
        double otherCoefficient =
            system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
        assertTrue(Double.isFinite(referenceCoefficient) && referenceCoefficient > 0.0,
            "reference fugacity coefficient");
        assertTrue(Double.isFinite(otherCoefficient) && otherCoefficient > 0.0,
            "other-phase fugacity coefficient");
        double referenceLogFugacity =
            Math.log(referenceComposition) + Math.log(referenceCoefficient);
        double otherLogFugacity = Math.log(otherComposition) + Math.log(otherCoefficient);
        maximumResidual = Math.max(maximumResidual,
            Math.abs(referenceLogFugacity - otherLogFugacity));
        comparisons++;
      }
    }
    assertTrue(comparisons > 0, "multiphase endpoint must expose fugacity comparisons");
    return maximumResidual;
  }
}
