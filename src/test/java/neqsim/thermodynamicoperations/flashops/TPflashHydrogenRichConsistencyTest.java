package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Cross-algorithm qualification for neutral hydrogen-rich cubic-EOS TP flashes.
 */
@Tag("slow")
class TPflashHydrogenRichConsistencyTest {
  private static final String[] COMPONENTS = { "hydrogen", "methane", "ethane", "n-heptane", "nC10" };
  private static final double[][] FEEDS = { { 0.920, 0.060, 0.015, 0.004, 0.001 },
      { 0.750, 0.150, 0.050, 0.040, 0.010 }, { 0.350, 0.450, 0.100, 0.080, 0.020 } };
  private static final double[][] STATES = { { 320.0, 20.0 }, { 300.0, 100.0 }, { 260.0, 100.0 }, { 220.0, 200.0 } };

  @Test
  void ordinaryAndMultiphaseAgreeAcrossFreshMatrix() {
    boolean sawOnePhase = false;
    boolean sawTwoPhases = false;

    for (boolean usePr : new boolean[] { false, true }) {
      for (int feedIndex = 0; feedIndex < FEEDS.length; feedIndex++) {
        for (double[] state : STATES) {
          String label = modelName(usePr) + " feed " + feedIndex + " at " + state[0] + " K, " + state[1] + " bara";
          SystemInterface ordinary = flash(usePr, FEEDS[feedIndex], state[0], state[1], false, false);
          SystemInterface multiphase = flash(usePr, FEEDS[feedIndex], state[0], state[1], true, false);

          assertEquivalent(ordinary, multiphase, FEEDS[feedIndex], label);
          sawOnePhase |= ordinary.getNumberOfPhases() == 1;
          sawTwoPhases |= ordinary.getNumberOfPhases() == 2;
        }
      }
    }

    assertTrue(sawOnePhase, "matrix must contain a stable one-phase endpoint");
    assertTrue(sawTwoPhases, "matrix must contain a stable two-phase endpoint");
  }

  @Test
  void extremeBetaAndRepeatedExecutionRetainAcceptedState() {
    for (boolean usePr : new boolean[] { false, true }) {
      double[] feed = FEEDS[1];
      SystemInterface reference = flash(usePr, feed, 250.0, 120.0, false, false);
      SystemInterface poorOrdinary = flash(usePr, feed, 250.0, 120.0, false, true);
      SystemInterface poorMultiphase = flash(usePr, feed, 250.0, 120.0, true, true);

      assertEquivalent(reference, poorOrdinary, feed, modelName(usePr) + " poor ordinary beta");
      assertEquivalent(reference, poorMultiphase, feed, modelName(usePr) + " poor multiphase beta");

      SystemInterface repeatedReference = reference.clone();
      new ThermodynamicOperations(reference).TPflash();
      reference.init(1);
      assertEquivalent(repeatedReference, reference, feed, modelName(usePr) + " repeated flash");
    }
  }

  @Test
  void nearbyChangedStateSweepMatchesFreshReferencesInBothDirections() {
    double[][] path = { { 230.0, 160.0 }, { 240.0, 140.0 }, { 250.0, 120.0 }, { 240.0, 140.0 }, { 230.0, 160.0 } };

    for (boolean usePr : new boolean[] { false, true }) {
      double[] feed = FEEDS[1];
      SystemInterface changedOrdinary = createSystem(usePr, feed, path[0][0], path[0][1], false, false);
      SystemInterface changedMultiphase = createSystem(usePr, feed, path[0][0], path[0][1], true, false);

      for (double[] state : path) {
        setStateAndFlash(changedOrdinary, state[0], state[1]);
        setStateAndFlash(changedMultiphase, state[0], state[1]);
        SystemInterface freshReference = flash(usePr, feed, state[0], state[1], false, false);
        String label = modelName(usePr) + " changed state at " + state[0] + " K, " + state[1] + " bara";

        assertEquivalent(freshReference, changedOrdinary, feed, label + " ordinary");
        assertEquivalent(freshReference, changedMultiphase, feed, label + " multiphase");
      }
    }
  }

  private SystemInterface flash(boolean usePr, double[] feed, double temperature, double pressure, boolean multiphase,
      boolean poorGuess) {
    SystemInterface system = createSystem(usePr, feed, temperature, pressure, multiphase, poorGuess);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private SystemInterface createSystem(boolean usePr, double[] feed, double temperature, double pressure,
      boolean multiphase, boolean poorGuess) {
    SystemInterface system = usePr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphase);
    if (poorGuess) {
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);
    }
    return system;
  }

  private void setStateAndFlash(SystemInterface system, double temperature, double pressure) {
    system.setTemperature(temperature, "K");
    system.setPressure(pressure, "bara");
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
  }

  private void assertEquivalent(SystemInterface expected, SystemInterface actual, double[] feed, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertTrue(expected.getNumberOfPhases() >= 1 && expected.getNumberOfPhases() <= 2, label);
    assertClosure(expected, feed, label + " expected");
    assertClosure(actual, feed, label + " actual");

    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getPhase(expectedPhase).getType(), actual.getPhase(actualPhase).getType(), label);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10, label);
      assertRelativeEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-8,
          1.0e-10, label + " phase Z");
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhase).getComponent(componentIndex).getx(), 1.0e-10, label);
      }
    }
    assertRelativeEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-8, 1.0e-7, label + " Gibbs energy");
  }

  private Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order,
        Comparator.comparingDouble(phaseIndex -> -system.getPhase(phaseIndex).getComponent("hydrogen").getx()));
    return order;
  }

  private void assertClosure(SystemInterface system, double[] feed, String label) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double beta = system.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta), label + " beta must be finite");
      assertTrue(beta >= 0.0 && beta <= 1.0, label + " beta must be bounded");
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(composition), label + " composition must be finite");
        assertTrue(composition >= -1.0e-14 && composition <= 1.0 + 1.0e-14, label + " composition must be bounded");
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, 1.0e-12, label + " composition normalization");
      assertTrue(Double.isFinite(system.getPhase(phaseIndex).getZ()), label + " phase Z must be finite");
      assertTrue(system.getPhase(phaseIndex).getZ() > 0.0, label + " phase Z must be positive");
    }
    assertEquals(1.0, betaTotal, 1.0e-12, label + " beta normalization");

    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(feed[componentIndex], recoveredFeed, 1.0e-10, label + " component balance");
    }

    if (system.getNumberOfPhases() == 2) {
      double maximumFugacityResidual = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double firstComposition = Math.max(system.getPhase(0).getComponent(componentIndex).getx(), Double.MIN_NORMAL);
        double secondComposition = Math.max(system.getPhase(1).getComponent(componentIndex).getx(), Double.MIN_NORMAL);
        double firstLogFugacity = Math.log(firstComposition)
            + Math.log(system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient());
        double secondLogFugacity = Math.log(secondComposition)
            + Math.log(system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient());
        maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(firstLogFugacity - secondLogFugacity));
      }
      assertTrue(maximumFugacityResidual < 1.0e-8,
          label + " maximum log-fugacity residual was " + maximumFugacityResidual);
    }
  }

  private void assertRelativeEquals(double expected, double actual, double relativeTolerance, double absoluteTolerance,
      String label) {
    assertTrue(Double.isFinite(expected) && Double.isFinite(actual), label + " must be finite");
    double tolerance = Math.max(absoluteTolerance, relativeTolerance * Math.max(Math.abs(expected), Math.abs(actual)));
    assertEquals(expected, actual, tolerance, label);
  }

  private String modelName(boolean usePr) {
    return usePr ? "PR" : "SRK";
  }
}
