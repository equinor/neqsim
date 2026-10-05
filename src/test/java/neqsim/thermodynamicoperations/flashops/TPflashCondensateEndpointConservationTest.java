package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression for the nonconservative condensate endpoint reported in issue #4202.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashCondensateEndpointConservationTest extends neqsim.NeqSimTest {
  /** Logger for numerical qualification evidence. */
  private static final Logger logger = LogManager.getLogger(TPflashCondensateEndpointConservationTest.class);
  /** Feed component names in the reported reproduction. */
  private static final String[] NAMES = {"methane", "ethane", "propane", "n-butane", "n-hexane", "n-decane",
      "n-hexadecane"};
  /** Overall mole fractions in the reported reproduction. */
  private static final double[] FEED = {0.85, 0.05, 0.02, 0.01, 0.02, 0.03, 0.02};

  /** Replays all 750 explicit-multiphase map points from the issue #4198 notebook without relaxing its tolerance. */
  @Test
  void notebookPhaseMapRetainsComponentClosure() {
    SystemInterface oil = createNotebookOil();
    SystemInterface condensate = createFluid(373.15, 500.0, true);
    double maximumResidual = 0.0;
    for (SystemInterface template : new SystemInterface[] {oil, condensate}) {
      for (int pressureIndex = 0; pressureIndex < 25; pressureIndex++) {
        double pressure = 50.0 + 25.0 * pressureIndex;
        for (int temperatureIndex = 0; temperatureIndex < 15; temperatureIndex++) {
          double temperature = 333.15 + 10.0 * temperatureIndex;
          SystemInterface state = template.clone();
          state.setMultiPhaseCheck(true);
          state.setTemperature(temperature);
          state.setPressure(pressure);
          new ThermodynamicOperations(state).TPflash();
          double gasFraction = 0.0;
          for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
            if (state.getPhase(phase).getType() == PhaseType.GAS) {
              gasFraction += state.getBeta(phase);
            }
          }
          assertTrue(Double.isFinite(gasFraction) && gasFraction >= -1.0e-10 && gasFraction <= 1.0 + 1.0e-10);
          for (int component = 0; component < state.getNumberOfComponents(); component++) {
            double recovered = 0.0;
            for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
              recovered += state.getBeta(phase) * state.getPhase(phase).getComponent(component).getx();
            }
            maximumResidual = Math.max(maximumResidual, Math.abs(recovered - state.getComponent(component).getz()));
          }
          assertTrue(maximumResidual < 1.0e-7, "Original notebook component balance tolerance: " + maximumResidual);
        }
      }
    }
    logger.info("750 notebook map flashes: maximum component balance residual={}", maximumResidual);
  }

  /**
   * Recreates the characterized live-oil template from the #4198 notebook.
   *
   * @return synthetic characterized oil
   */
  private static SystemInterface createNotebookOil() {
    SystemInterface oil = new SystemSrkEos(370.65, 260.0);
    String[] lightNames = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane"};
    double[] lightAmounts = {0.39, 0.30, 40.20, 7.61, 7.95, 1.19, 4.08, 1.39, 2.15, 2.79};
    for (int component = 0; component < lightNames.length; component++) {
      oil.addComponent(lightNames[component], lightAmounts[component]);
    }
    double[] amounts = {4.28, 4.31, 3.08, 2.47, 1.91, 1.69, 1.59, 1.22, 1.25, 1.00, 0.99, 0.92, 0.60};
    double[] molarMasses = {0.095, 0.106, 0.121, 0.135, 0.148, 0.161, 0.175, 0.196, 0.206, 0.225, 0.236, 0.245, 0.265};
    double[] densities = {0.729, 0.749, 0.770, 0.786, 0.792, 0.804, 0.819, 0.833, 0.836, 0.843, 0.840, 0.846, 0.857};
    for (int cut = 0; cut < amounts.length; cut++) {
      oil.addTBPfraction("C" + (cut + 7), amounts[cut], molarMasses[cut], densities[cut]);
    }
    oil.addPlusFraction("C20", 6.64, 0.453, 0.918);
    oil.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    oil.getCharacterization().characterisePlusFraction();
    oil.setMixingRule("classic");
    oil.useVolumeCorrection(true);
    oil.init(0);
    oil.init(1);
    return oil;
  }

  /** Checks both topology paths at the exact reported temperature and pressure. */
  @Test
  void reportedCondensateConservesFeedAndRecoversEquilibriumSplit() {
    for (boolean multiphase : new boolean[] {false, true}) {
      SystemInterface fluid = createFluid(353.15, 350.0, multiphase);
      new ThermodynamicOperations(fluid).TPflash();
      logger.info("multiphase={}, phases={}, beta={}, balance={}", multiphase, fluid.getNumberOfPhases(),
          fluid.getBeta(0), maximumBalanceResidual(fluid));
      assertTrue(maximumBalanceResidual(fluid) <= 1.0e-10,
          "Component balance must close: " + maximumBalanceResidual(fluid));
      assertEquals(2, fluid.getNumberOfPhases(), "The feed must retain its distinct equilibrium phases");
      assertQualified(fluid);
      assertTrue(fluid.hasPhaseType(PhaseType.GAS), "The lighter condensate phase must retain its gas identity");
      assertTrue(fluid.hasPhaseType(PhaseType.OIL), "The denser condensate phase must retain its oil identity");
      assertTrue(fluid.getGibbsEnergy() < homogeneousGibbs(fluid) - 1.0e-6,
          "The recovered split must lower Gibbs energy at the conserved feed");
      SystemInterface stabilityTrial = fluid.clone();
      new TPmultiflash(stabilityTrial, false).run();
      assertEquivalent(fluid, stabilityTrial);
    }
  }

  /** Checks neighboring states, repeated flashes and state reuse against independent cold starts. */
  @Test
  void neighboringStatesAndChangedStateReuseRemainConservative() {
    for (boolean multiphase : new boolean[] {false, true}) {
      SystemInterface reused = createFluid(353.15, 350.0, multiphase);
      for (double temperature : new double[] {351.15, 353.15, 355.15}) {
        for (double pressure : new double[] {340.0, 350.0, 360.0}) {
          SystemInterface reference = createFluid(temperature, pressure, true);
          new ThermodynamicOperations(reference).TPflash();
          reused.setTemperature(temperature);
          reused.setPressure(pressure);
          for (int repeat = 0; repeat < 4; repeat++) {
            new ThermodynamicOperations(reused).TPflash();
            assertQualified(reused);
            assertEquivalent(reference, reused);
          }
        }
      }
      reused.setTemperature(800.0);
      reused.setPressure(10.0);
      new ThermodynamicOperations(reused).TPflash();
      assertEquals(1, reused.getNumberOfPhases(), "Stable hot gas must remain single phase");
      assertQualified(reused);
      reused.setTemperature(353.15);
      reused.setPressure(350.0);
      new ThermodynamicOperations(reused).TPflash();
      assertEquals(2, reused.getNumberOfPhases(), "Reuse must recover the condensate split");
      assertQualified(reused);
      assertEquals(multiphase, reused.doMultiPhaseCheck(), "Recovery must preserve the user's option");
    }
  }

  /** Checks that repeated recovery never subtracts trace-phase moles from the feed inventory. */
  @Test
  void repeatedRecoveryPreservesComponentInventory() {
    SystemInterface fluid = createFluid(353.15, 350.0, false);
    for (int repeat = 0; repeat < 128; repeat++) {
      new TPflash(fluid).run();
      assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1.0e-12);
      for (int component = 0; component < FEED.length; component++) {
        assertEquals(FEED[component], fluid.getPhase(0).getComponent(component).getNumberOfmoles(), 1.0e-12);
      }
      assertTrue(fluid.hasPhaseType(PhaseType.GAS));
      assertTrue(fluid.hasPhaseType(PhaseType.OIL));
      assertQualified(fluid);
    }
  }

  /** Checks the same state-reuse sequence with warm K-values enabled and restores the global setting. */
  @Test
  void warmStartedStateReuseRemainsConservative() {
    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(true);
      neighboringStatesAndChangedStateReuseRemainConservative();
      assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(), "Recovery must preserve the warm-start setting");
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /** Checks that reusing the operation clears phase-removal history when the fluid becomes stable hot gas. */
  @Test
  void reusedOperationClearsPhaseRemovalHistory() {
    SystemInterface fluid = createFluid(353.15, 350.0, false);
    TPflash flash = new TPflash(fluid);
    flash.run();
    assertEquals(2, fluid.getNumberOfPhases());
    assertQualified(fluid);
    assertTrue(flash.getLastStabilityOutcome().startsWith("recovered nonconservative hydrocarbon endpoint"));

    fluid.setTemperature(800.0);
    fluid.setPressure(10.0);
    flash.run();
    assertEquals(1, fluid.getNumberOfPhases());
    assertQualified(fluid);
    assertFalse(flash.getLastStabilityOutcome().startsWith("recovered nonconservative hydrocarbon endpoint"));

    fluid.setTemperature(353.15);
    fluid.setPressure(350.0);
    flash.run();
    assertEquals(2, fluid.getNumberOfPhases());
    assertQualified(fluid);
  }

  /** Checks that the PR sibling model also conserves this feed and agrees with explicit multiphase checking. */
  @Test
  void pengRobinsonCondensateRemainsQualified() {
    SystemInterface ordinary = new SystemPrEos(353.15, 350.0);
    for (int component = 0; component < NAMES.length; component++) {
      ordinary.addComponent(NAMES[component], FEED[component]);
    }
    ordinary.setMixingRule("classic");
    ordinary.useVolumeCorrection(true);
    SystemInterface multiphase = ordinary.clone();
    multiphase.setMultiPhaseCheck(true);
    new ThermodynamicOperations(ordinary).TPflash();
    new ThermodynamicOperations(multiphase).TPflash();
    assertEquivalent(multiphase, ordinary);
  }

  /**
   * Checks normalization, phase inventory and component fugacity equality.
   *
   * @param fluid flashed fluid
   */
  private static void assertQualified(SystemInterface fluid) {
    fluid.init(1);
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertTrue(fluid.getBeta(phase) > 0.0 && fluid.getBeta(phase) <= 1.0);
      betaSum += fluid.getBeta(phase);
      double compositionSum = 0.0;
      for (int component = 0; component < NAMES.length; component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        compositionSum += x;
      }
      assertEquals(1.0, compositionSum, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    assertTrue(maximumBalanceResidual(fluid) <= 1.0e-10,
        "Component balance residual: " + maximumBalanceResidual(fluid));
    if (fluid.getNumberOfPhases() == 2) {
      double maxDifference = 0.0;
      for (int component = 0; component < NAMES.length; component++) {
        double first = fluid.getPhase(0).getComponent(component).getx()
            * fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
        double second = fluid.getPhase(1).getComponent(component).getx()
            * fluid.getPhase(1).getComponent(component).getFugacityCoefficient();
        assertEquals(0.0, Math.log(first / second), 1.0e-8, "Component fugacities must agree");
        maxDifference = Math.max(maxDifference, Math
            .abs(fluid.getPhase(0).getComponent(component).getx() - fluid.getPhase(1).getComponent(component).getx()));
      }
      assertTrue(maxDifference > 1.0e-3, "The recovered split must be distinct");
    }
  }

  /**
   * Compares endpoints after ordering phases by their heavy-component content.
   *
   * @param expected reference endpoint
   * @param actual repeated or reused endpoint
   */
  private static void assertEquivalent(SystemInterface expected, SystemInterface actual) {
    assertQualified(expected);
    assertQualified(actual);
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    int expectedLight = lightPhase(expected);
    int actualLight = lightPhase(actual);
    for (int ordered = 0; ordered < expected.getNumberOfPhases(); ordered++) {
      int first = ordered == 0 ? expectedLight : 1 - expectedLight;
      int second = ordered == 0 ? actualLight : 1 - actualLight;
      // Near a root crossover, the same 1e-8 fugacity target permits slightly different phase fractions.
      assertEquals(expected.getBeta(first), actual.getBeta(second), 1.0e-7);
      assertEquals(expected.getPhase(first).getZ(), actual.getPhase(second).getZ(), 1.0e-7);
      for (int component = 0; component < NAMES.length; component++) {
        assertEquals(expected.getPhase(first).getComponent(component).getx(),
            actual.getPhase(second).getComponent(component).getx(), 1.0e-7);
      }
    }
  }

  /**
   * Finds the phase with less hexadecane.
   *
   * @param fluid endpoint to order
   * @return index of its lighter phase
   */
  private static int lightPhase(SystemInterface fluid) {
    return fluid.getNumberOfPhases() == 1
        || fluid.getPhase(0).getComponent(6).getx() <= fluid.getPhase(1).getComponent(6).getx() ? 0 : 1;
  }

  /**
   * Evaluates both homogeneous roots at the feed for an independent Gibbs comparison.
   *
   * @param fluid flashed condensate
   * @return lowest homogeneous Gibbs energy in J
   */
  private static double homogeneousGibbs(SystemInterface fluid) {
    SystemInterface homogeneous = createFluid(fluid.getTemperature(), fluid.getPressure(), false);
    homogeneous.init(0);
    homogeneous.setNumberOfPhases(1);
    homogeneous.setBeta(0, 1.0);
    double minimum = Double.POSITIVE_INFINITY;
    for (PhaseType root : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
      homogeneous.setPhaseType(0, root);
      homogeneous.init(1, 0);
      minimum = Math.min(minimum, homogeneous.getGibbsEnergy());
    }
    return minimum;
  }

  /**
   * Creates the exact synthetic condensate used by the issue #4198 demonstration.
   *
   * @param temperature temperature in K
   * @param pressure absolute pressure in bar
   * @param multiphase whether to request explicit multiphase checking
   * @return configured SRK fluid
   */
  private static SystemInterface createFluid(double temperature, double pressure, boolean multiphase) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    for (int component = 0; component < NAMES.length; component++) {
      fluid.addComponent(NAMES[component], FEED[component]);
    }
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(multiphase);
    return fluid;
  }

  /**
   * Measures component closure against the prescribed feed.
   *
   * @param fluid flashed fluid
   * @return largest absolute component mole-fraction residual
   */
  private static double maximumBalanceResidual(SystemInterface fluid) {
    double maximum = 0.0;
    for (int component = 0; component < NAMES.length; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      maximum = Math.max(maximum, Math.abs(FEED[component] - recovered));
    }
    return maximum;
  }
}
