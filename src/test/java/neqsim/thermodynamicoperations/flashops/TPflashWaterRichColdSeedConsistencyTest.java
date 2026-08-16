package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for cold-state reciprocal stability of water-rich cubic-EOS feeds. */
class TPflashWaterRichColdSeedConsistencyTest extends neqsim.NeqSimTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "nC10", "water" };
  private static final double[] FEED = { 0.74, 0.15, 0.05, 0.01, 0.05 };

  @Test
  void coldSeedRecoversBalancedOilAqueousEndpoint() {
    SystemInterface ordinary = flash(createSystem(230.0, 90.0, FEED, false), false);
    SystemInterface multiphase = flash(createSystem(230.0, 90.0, FEED, true), false);

    assertEquivalentOilAqueousState(ordinary, multiphase);
    assertEquals(0.95019632, ordinary.getPhase("oil").getBeta(), 1.0e-8);
    assertEquals(0.04980368, ordinary.getPhase("aqueous").getBeta(), 1.0e-8);

    SystemInterface poorGuess = flash(createSystem(230.0, 90.0, FEED, false), true);
    assertEquivalentOilAqueousState(ordinary, poorGuess);
    SystemInterface poorMultiphaseGuess = flash(createSystem(230.0, 90.0, FEED, true), true);
    assertEquivalentOilAqueousState(ordinary, poorMultiphaseGuess);

    double firstGibbsEnergy = ordinary.getGibbsEnergy();
    double firstOilBeta = ordinary.getPhase("oil").getBeta();
    double[] firstOilComposition = phaseComposition(ordinary, PhaseType.OIL);
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(1);

    assertEquals(firstGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-6);
    assertEquals(firstOilBeta, ordinary.getPhase("oil").getBeta(), 1.0e-12);
    assertCompositionEquals(firstOilComposition, phaseComposition(ordinary, PhaseType.OIL), 1.0e-12);
    assertPhysicalEquilibrium(ordinary);

    double firstMultiphaseGibbsEnergy = multiphase.getGibbsEnergy();
    double firstMultiphaseOilBeta = multiphase.getPhase("oil").getBeta();
    double[] firstMultiphaseOilComposition = phaseComposition(multiphase, PhaseType.OIL);
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);

    assertEquals(firstMultiphaseGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-6);
    assertEquals(firstMultiphaseOilBeta, multiphase.getPhase("oil").getBeta(), 1.0e-12);
    assertCompositionEquals(firstMultiphaseOilComposition, phaseComposition(multiphase, PhaseType.OIL), 1.0e-12);
    assertPhysicalEquilibrium(multiphase);
  }

  @Test
  void nearbyStatesAndChangedFeedRemainContinuousAndDeterministic() {
    double[][] stablePoints = { { 229.0, 92.0 }, { 229.5, 92.0 }, { 230.0, 90.0 }, { 231.0, 88.0 }, { 231.0, 90.0 },
        { 231.0, 92.0 }, { 232.0, 88.0 }, { 232.0, 92.0 }, { 234.0, 90.0 } };
    for (double[] point : stablePoints) {
      SystemInterface ordinary = flash(createSystem(point[0], point[1], FEED, false), false);
      SystemInterface multiphase = flash(createSystem(point[0], point[1], FEED, true), false);
      assertEquivalentOilAqueousState(ordinary, multiphase);
    }

    SystemInterface continued = flash(createSystem(230.0, 90.0, FEED, false), false);
    continued.setTemperature(232.0, "K");
    continued.setPressure(90.0, "bara");
    continued = flash(continued, false);
    assertEquivalentOilAqueousState(continued, flash(createSystem(232.0, 90.0, FEED, true), false));

    double[] nearbyFeed = { 0.75, 0.15, 0.05, 0.01, 0.04 };
    continued.setTemperature(230.0, "K");
    continued.setMolarComposition(nearbyFeed);
    continued = flash(continued, false);
    assertEquivalentOilAqueousState(continued, flash(createSystem(230.0, 90.0, nearbyFeed, true), false));

    continued.setMolarComposition(FEED);
    continued = flash(continued, false);
    assertEquivalentOilAqueousState(continued, flash(createSystem(230.0, 90.0, FEED, true), false));
  }

  @Test
  void duplicateOilTrialIsMergedWithoutLosingMaterialBalance() {
    SystemInterface multiphase = flash(createSystem(230.0, 91.0, FEED, true), false);

    assertEquals(2, multiphase.getNumberOfPhases());
    assertTrue(multiphase.hasPhaseType(PhaseType.OIL));
    assertTrue(multiphase.hasPhaseType(PhaseType.AQUEOUS));
    assertEquals(0.95019654, multiphase.getPhase("oil").getBeta(), 1.0e-8);
    assertEquals(0.04980346, multiphase.getPhase("aqueous").getBeta(), 1.0e-8);
    assertPhysicalEquilibrium(multiphase);
  }

  private static SystemInterface createSystem(double temperature, double pressure, double[] composition,
      boolean multiphase) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    for (int component = 0; component < COMPONENTS.length; component++) {
      system.addComponent(COMPONENTS[component], composition[component]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphase);
    return system;
  }

  private static SystemInterface flash(SystemInterface system, boolean poorGuess) {
    if (poorGuess) {
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private static void assertEquivalentOilAqueousState(SystemInterface first, SystemInterface second) {
    assertEquals(2, first.getNumberOfPhases());
    assertEquals(2, second.getNumberOfPhases());
    assertTrue(first.hasPhaseType(PhaseType.OIL));
    assertTrue(first.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(second.hasPhaseType(PhaseType.OIL));
    assertTrue(second.hasPhaseType(PhaseType.AQUEOUS));
    assertPhysicalEquilibrium(first);
    assertPhysicalEquilibrium(second);

    for (PhaseType type : new PhaseType[] { PhaseType.OIL, PhaseType.AQUEOUS }) {
      assertEquals(first.getPhase(type.getDesc()).getBeta(), second.getPhase(type.getDesc()).getBeta(), 1.0e-10);
      assertEquals(first.getPhase(type.getDesc()).getZ(), second.getPhase(type.getDesc()).getZ(), 1.0e-10);
      assertCompositionEquals(phaseComposition(first, type), phaseComposition(second, type), 1.0e-10);
    }
    assertEquals(first.getGibbsEnergy(), second.getGibbsEnergy(), 1.0e-6);
  }

  private static void assertPhysicalEquilibrium(SystemInterface system) {
    assertEquals(1.0, betaSum(system), 1.0e-12);
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      assertTrue(system.getBeta(phase) >= 0.0 && system.getBeta(phase) <= 1.0);
      double compositionSum = 0.0;
      for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition));
        assertTrue(composition >= 0.0 && composition <= 1.0);
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
    }
    assertTrue(maximumComponentBalanceResidual(system) < 1.0e-10);
    if (system.getNumberOfPhases() == 2) {
      assertTrue(maximumLogFugacityResidual(system) < 1.0e-8);
    }
  }

  private static double betaSum(SystemInterface system) {
    double sum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      sum += system.getBeta(phase);
    }
    return sum;
  }

  private static double maximumComponentBalanceResidual(SystemInterface system) {
    double maximum = 0.0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximum = Math.max(maximum, Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximum;
  }

  private static double maximumLogFugacityResidual(SystemInterface system) {
    double maximum = 0.0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double first = system.getPhase(0).getComponent(component).getx()
          * system.getPhase(0).getComponent(component).getFugacityCoefficient();
      double second = system.getPhase(1).getComponent(component).getx()
          * system.getPhase(1).getComponent(component).getFugacityCoefficient();
      maximum = Math.max(maximum, Math.abs(Math.log(first / second)));
    }
    return maximum;
  }

  private static double[] phaseComposition(SystemInterface system, PhaseType type) {
    double[] composition = new double[system.getPhase(type.getDesc()).getNumberOfComponents()];
    for (int component = 0; component < composition.length; component++) {
      composition[component] = system.getPhase(type.getDesc()).getComponent(component).getx();
    }
    return composition;
  }

  private static void assertCompositionEquals(double[] expected, double[] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int component = 0; component < expected.length; component++) {
      assertEquals(expected[component], actual[component], tolerance);
    }
  }
}
