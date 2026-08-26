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

/** Qualification of rich-gas SRK/PR flashes near the cricondenbar. */
class TPflashNearCricondenbarConsistencyTest extends neqsim.NeqSimTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double STATE_TOLERANCE = 1.0e-7;
  private static final Case[] REGRESSION_CASES = { new Case(Eos.SRK, 273.15, 100.0), new Case(Eos.SRK, 283.15, 100.0),
      new Case(Eos.PR, 268.15, 95.0), new Case(Eos.PR, 273.15, 100.0), new Case(Eos.PR, 278.15, 100.0),
      new Case(Eos.PR, 283.15, 100.0) };

  @Test
  void nearCricondenbarEndpointsCloseAndAgreeAcrossAlgorithms() {
    for (Case regression : REGRESSION_CASES) {
      SystemInterface ordinary = flash(createSystem(regression, false), false);
      SystemInterface multiphase = flash(createSystem(regression, true), false);

      assertEquals(2, ordinary.getNumberOfPhases(), regression.label());
      assertEquivalent(ordinary, multiphase, regression.label(), STATE_TOLERANCE);
      assertTrue(maximumFugacityResidual(ordinary) < 1.0e-10,
          regression.label() + " polished ordinary fugacity residual " + maximumFugacityResidual(ordinary));
    }
  }

  @Test
  void poorInitializationRepeatsAndChangedStateRemainDeterministic() {
    for (Case regression : new Case[] { new Case(Eos.SRK, 283.15, 100.0), new Case(Eos.PR, 273.15, 100.0) }) {
      for (boolean multiphase : new boolean[] { false, true }) {
        SystemInterface reference = flash(createSystem(regression, multiphase), false);
        SystemInterface poorGuess = flash(createSystem(regression, multiphase), true);
        assertEquivalent(reference, poorGuess, regression.label() + " poor initialization", STATE_TOLERANCE);

        SystemInterface repeatedReference = reference.clone();
        flash(reference, false);
        assertEquivalent(repeatedReference, reference, regression.label() + " repeat", STATE_TOLERANCE);

        Case changedCase = regression.withTemperature(regression.temperature + 0.25);
        reference.setTemperature(changedCase.temperature, "K");
        flash(reference, false);
        SystemInterface changedReference = flash(createSystem(changedCase, multiphase), false);
        assertEquivalent(changedReference, reference, regression.label() + " changed temperature", STATE_TOLERANCE);

        reference.setTemperature(regression.temperature, "K");
        flash(reference, false);
        SystemInterface returnedReference = flash(createSystem(regression, multiphase), false);
        assertEquivalent(returnedReference, reference, regression.label() + " return to state", STATE_TOLERANCE);
      }
    }
  }

  @Test
  void phaseAppearanceAndDisappearanceRemainContinuous() {
    Case[][] transitions = { { new Case(Eos.SRK, 293.15, 100.0), new Case(Eos.SRK, 303.15, 100.0) },
        { new Case(Eos.PR, 288.15, 100.0), new Case(Eos.PR, 293.15, 100.0) } };
    for (Case[] transition : transitions) {
      SystemInterface twoPhase = flash(createSystem(transition[0], false), false);
      SystemInterface twoPhaseMultiphase = flash(createSystem(transition[0], true), false);
      assertEquals(2, twoPhase.getNumberOfPhases(), transition[0].label());
      assertEquivalent(twoPhase, twoPhaseMultiphase, transition[0].label(), STATE_TOLERANCE);

      SystemInterface onePhase = flash(createSystem(transition[1], false), false);
      SystemInterface onePhaseMultiphase = flash(createSystem(transition[1], true), false);
      assertEquals(1, onePhase.getNumberOfPhases(), transition[1].label());
      assertEquivalent(onePhase, onePhaseMultiphase, transition[1].label(), 1.0e-12);
      assertEquals(1.0, onePhase.getBeta(0), 0.0, transition[1].label());
      for (int component = 0; component < onePhase.getPhase(0).getNumberOfComponents(); component++) {
        assertEquals(onePhase.getPhase(0).getComponent(component).getz(),
            onePhase.getPhase(0).getComponent(component).getx(), 1.0e-12, transition[1].label());
      }
    }

    for (Eos eos : Eos.values()) {
      Case pressureControl = new Case(eos, 273.15, 105.0);
      SystemInterface ordinary = flash(createSystem(pressureControl, false), false);
      SystemInterface multiphase = flash(createSystem(pressureControl, true), false);
      assertEquals(1, ordinary.getNumberOfPhases(), pressureControl.label());
      assertEquivalent(ordinary, multiphase, pressureControl.label(), 1.0e-12);
    }
  }

  @Test
  void lowPressureScreenExcludedControlRemainsCrossAlgorithmConsistent() {
    for (Eos eos : Eos.values()) {
      Case control = new Case(eos, 273.15, 20.0);
      SystemInterface ordinary = flash(createSystem(control, false), false);
      SystemInterface multiphase = flash(createSystem(control, true), false);
      assertEquivalent(ordinary, multiphase, control.label(), 1.0e-10);
    }
  }

  private static SystemInterface createSystem(Case testCase, boolean multiphase) {
    SystemInterface system = testCase.eos == Eos.PR ? new SystemPrEos(testCase.temperature, testCase.pressure)
        : new SystemSrkEos(testCase.temperature, testCase.pressure);
    system.addComponent("nitrogen", 3.43);
    system.addComponent("CO2", 0.34);
    system.addComponent("methane", 62.51);
    system.addComponent("ethane", 15.65);
    system.addComponent("propane", 13.22);
    system.addComponent("i-butane", 1.61);
    system.addComponent("n-butane", 2.48);
    system.addComponent("i-pentane", 0.35);
    system.addComponent("n-pentane", 0.29);
    system.addComponent("n-hexane", 0.12);
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
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), stateTolerance, label);
      assertEquals(expected.getPhase(expectedPhase).getDensity(), actual.getPhase(actualPhase).getDensity(),
          Math.max(1.0e-8, stateTolerance * expected.getPhase(expectedPhase).getDensity()), label);
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), stateTolerance, label);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-7, stateTolerance * Math.abs(expected.getGibbsEnergy())), label);
  }

  private static Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order, Comparator.comparingDouble(index -> system.getPhase(index).getDensity()));
    return order;
  }

  private static void assertClosure(SystemInterface system, String label) {
    double betaSum = 0.0;
    int componentCount = system.getPhase(0).getNumberOfComponents();
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0, label);
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12, label);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0, label);
    }
    assertEquals(1.0, betaSum, 1.0e-12, label);

    double maximumMaterialBalanceResidual = 0.0;
    for (int component = 0; component < componentCount; component++) {
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
      double maximumFugacityResidual = maximumFugacityResidual(system);
      assertTrue(maximumFugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + maximumFugacityResidual);
    }
  }

  private static double maximumFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double first = system.getPhase(0).getComponent(component).getx()
          * system.getPhase(0).getComponent(component).getFugacityCoefficient();
      double second = system.getPhase(1).getComponent(component).getx()
          * system.getPhase(1).getComponent(component).getFugacityCoefficient();
      maximumResidual = Math.max(maximumResidual, Math.abs(Math.log(first / second)));
    }
    return maximumResidual;
  }

  private enum Eos {
    SRK, PR
  }

  private static final class Case {
    private final Eos eos;
    private final double temperature;
    private final double pressure;

    private Case(Eos eos, double temperature, double pressure) {
      this.eos = eos;
      this.temperature = temperature;
      this.pressure = pressure;
    }

    private Case withTemperature(double changedTemperature) {
      return new Case(eos, changedTemperature, pressure);
    }

    private String label() {
      return eos + " rich gas at " + temperature + " K, " + pressure + " bara";
    }
  }
}
