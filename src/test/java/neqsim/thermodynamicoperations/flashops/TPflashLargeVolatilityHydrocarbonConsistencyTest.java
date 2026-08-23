package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Qualification of high-pressure hydrocarbon flashes with a large volatility contrast. */
class TPflashLargeVolatilityHydrocarbonConsistencyTest extends neqsim.NeqSimTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double STATE_TOLERANCE = 1.0e-10;
  private static final Case[] REGRESSION_CASES = { new Case(Eos.SRK, 180.0, 50.0, 0.05),
      new Case(Eos.SRK, 180.0, 100.0, 0.10), new Case(Eos.SRK, 220.0, 200.0, 0.10),
      new Case(Eos.PR, 260.0, 200.0, 0.10) };

  @Test
  void largeVolatilityEndpointsCloseAndAgreeAcrossAlgorithms() {
    for (Case regression : REGRESSION_CASES) {
      SystemInterface ordinary = flash(createSystem(regression, false), false);
      SystemInterface multiphase = flash(createSystem(regression, true), false);

      assertEquals(2, ordinary.getNumberOfPhases(), regression.label());
      assertEquivalent(ordinary, multiphase, regression.label());
    }
  }

  @Test
  void poorInitializationRepeatsAndChangedPressureRemainDeterministic() {
    for (Case regression : REGRESSION_CASES) {
      for (boolean multiphase : new boolean[] { false, true }) {
        SystemInterface reference = flash(createSystem(regression, multiphase), false);
        SystemInterface poorGuess = flash(createSystem(regression, multiphase), true);
        assertEquivalent(reference, poorGuess, regression.label() + " poor initialization");

        SystemInterface repeatedReference = reference.clone();
        flash(reference, false);
        assertEquivalent(repeatedReference, reference, regression.label() + " cold-to-warm repeat", 1.0e-8);
        SystemInterface settledReference = reference.clone();
        flash(reference, false);
        assertEquivalent(settledReference, reference, regression.label() + " settled repeat");

        double changedPressure = regression.pressure * 1.01;
        reference.setPressure(changedPressure, "bara");
        flash(reference, false);
        SystemInterface changedReference = flash(createSystem(regression.withPressure(changedPressure), multiphase),
            false);
        assertEquivalent(changedReference, reference, regression.label() + " changed pressure", 1.0e-8);
      }
    }
  }

  @Test
  void nearbyCompositionsRemainContinuousAcrossAlgorithms() {
    for (Case regression : REGRESSION_CASES) {
      SystemInterface lower = null;
      for (double offset : new double[] { -1.0e-4, 0.0, 1.0e-4 }) {
        Case nearby = regression.withHeavyFraction(regression.heavyFraction + offset);
        SystemInterface ordinary = flash(createSystem(nearby, false), false);
        SystemInterface multiphase = flash(createSystem(nearby, true), false);
        assertEquivalent(ordinary, multiphase, nearby.label());
        if (lower != null && lower.getNumberOfPhases() == 2 && ordinary.getNumberOfPhases() == 2) {
          Integer[] lowerOrder = phaseOrder(lower);
          Integer[] ordinaryOrder = phaseOrder(ordinary);
          assertTrue(Math.abs(lower.getBeta(lowerOrder[0]) - ordinary.getBeta(ordinaryOrder[0])) < 0.02,
              nearby.label() + " nearby-state beta continuity");
        }
        lower = ordinary;
      }
    }
  }

  @Test
  void screenExcludedControlsRemainClosedAndCrossAlgorithmConsistent() {
    Case methaneEthane = new Case(Eos.SRK, 220.0, 200.0, 0.10, "ethane");
    Case traceHeavy = new Case(Eos.SRK, 300.0, 100.0, 1.0e-3);
    Case lowPressure = new Case(Eos.PR, 260.0, 20.0, 0.10);
    for (Case control : new Case[] { methaneEthane, traceHeavy, lowPressure }) {
      SystemInterface ordinary = flash(createSystem(control, false), false);
      SystemInterface multiphase = flash(createSystem(control, true), false);
      assertEquivalent(ordinary, multiphase, control.label());
    }
  }

  private static SystemInterface createSystem(Case testCase, boolean multiphase) {
    SystemInterface system = testCase.eos == Eos.PR ? new SystemPrEos(testCase.temperature, testCase.pressure)
        : new SystemSrkEos(testCase.temperature, testCase.pressure);
    system.addComponent("methane", 1.0 - testCase.heavyFraction);
    system.addComponent(testCase.heavyComponent, testCase.heavyFraction);
    system.setMixingRule("classic");
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

  private static void assertEquivalent(SystemInterface expected, SystemInterface actual, String label) {
    assertEquivalent(expected, actual, label, STATE_TOLERANCE);
  }

  private static void assertEquivalent(SystemInterface expected, SystemInterface actual, String label,
      double stateTolerance) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertClosure(expected, label + " reference");
    assertClosure(actual, label + " comparison");
    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getPhase(expectedPhase).getType(), actual.getPhase(actualPhase).getType(), label);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), stateTolerance, label);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-8, label);
      for (int component = 0; component < 2; component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), stateTolerance, label);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-7, 1.0e-8 * Math.abs(expected.getGibbsEnergy())), label);
  }

  private static Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order, Comparator.comparingDouble(index -> system.getPhase(index).getComponent(1).getx()));
    return order;
  }

  private static void assertClosure(SystemInterface system, String label) {
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < 2; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0, label);
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12, label);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0, label);
    }
    assertEquals(1.0, betaSum, 1.0e-12, label);

    double maximumMaterialBalanceResidual = 0.0;
    for (int component = 0; component < 2; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialBalanceResidual = Math.max(maximumMaterialBalanceResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    assertTrue(maximumMaterialBalanceResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material balance residual " + maximumMaterialBalanceResidual);

    if (system.getNumberOfPhases() == 2) {
      double maximumFugacityResidual = 0.0;
      for (int component = 0; component < 2; component++) {
        double first = system.getPhase(0).getComponent(component).getx()
            * system.getPhase(0).getComponent(component).getFugacityCoefficient();
        double second = system.getPhase(1).getComponent(component).getx()
            * system.getPhase(1).getComponent(component).getFugacityCoefficient();
        maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(Math.log(first / second)));
      }
      assertTrue(maximumFugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + maximumFugacityResidual);
    }
  }

  private enum Eos {
    SRK, PR
  }

  private static final class Case {
    private final Eos eos;
    private final double temperature;
    private final double pressure;
    private final double heavyFraction;
    private final String heavyComponent;

    private Case(Eos eos, double temperature, double pressure, double heavyFraction) {
      this(eos, temperature, pressure, heavyFraction, "n-heptane");
    }

    private Case(Eos eos, double temperature, double pressure, double heavyFraction, String heavyComponent) {
      this.eos = eos;
      this.temperature = temperature;
      this.pressure = pressure;
      this.heavyFraction = heavyFraction;
      this.heavyComponent = heavyComponent;
    }

    private Case withPressure(double changedPressure) {
      return new Case(eos, temperature, changedPressure, heavyFraction, heavyComponent);
    }

    private Case withHeavyFraction(double changedFraction) {
      return new Case(eos, temperature, pressure, changedFraction, heavyComponent);
    }

    private String label() {
      return eos + " methane+" + heavyComponent + " at " + temperature + " K, " + pressure + " bar, z(" + heavyComponent
          + ")=" + heavyFraction;
    }
  }
}
