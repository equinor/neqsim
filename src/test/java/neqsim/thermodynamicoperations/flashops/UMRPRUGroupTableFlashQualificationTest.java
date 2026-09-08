package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification of TP-flash lifecycle behavior for component families added to the maintained
 * UMR-PRU UNIFAC group table.
 *
 * <p>
 * The fluids are synthetic numerical regressions. They verify that newly resolvable aromatic,
 * cyclic, hydrogen/inert, and substituted-naphthene mixtures remain finite and conservative; they
 * are not independent parameter or experimental PVT validation.
 * </p>
 */
class UMRPRUGroupTableFlashQualificationTest {
  private static final double NORMALIZATION_TOLERANCE = 5.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  private static final FluidCase[] CASES = {
      new FluidCase("aromatic-heavy", 298.15, 10.0, new String[] {"methane", "propylbenzene"},
          new double[] {0.90, 0.10}),
      new FluidCase("cyclic-light", 285.15, 20.0, new String[] {"methane", "c-propane", "c-C4"},
          new double[] {0.80, 0.10, 0.10}),
      new FluidCase("hydrogen-inert", 280.15, 50.0,
          new String[] {"hydrogen", "argon", "methane", "n-hexane"},
          new double[] {0.10, 0.02, 0.83, 0.05}),
      new FluidCase("substituted-naphthene", 310.15, 15.0,
          new String[] {"methane", "n-Bcychexane", "Pent-CC6"},
          new double[] {0.80, 0.10, 0.10})};

  /** Ordinary, multiphase, and deliberately poor beta estimates must agree at nominal states. */
  @Test
  void nominalAlgorithmsAndPoorInitializationAreEquivalent() {
    int multiphaseStates = 0;
    for (FluidCase testCase : CASES) {
      SystemInterface ordinary = flash(testCase.create(false));
      SystemInterface multiphase = flash(testCase.create(true));
      assertEquivalentState(ordinary, multiphase, 1.0e-8, testCase.name + " algorithm agreement");

      SystemInterface poor = testCase.create(true);
      setPoorBetaEstimate(poor);
      flash(poor);
      assertEquivalentState(multiphase, poor, 1.0e-8, testCase.name + " poor initialization");

      if (multiphase.getNumberOfPhases() > 1) {
        multiphaseStates++;
      }
    }
    assertTrue(multiphaseStates >= 2, "matrix must exercise at least two multiphase states");
  }

  /** Reused nearby, returned, and repeated states must match independently constructed flashes. */
  @Test
  void reusedNearbyAndReturnedStatesRemainContinuous() {
    for (FluidCase testCase : CASES) {
      SystemInterface reference = flash(testCase.create(true));
      SystemInterface reused = reference.clone();

      reused.setTemperature(testCase.temperatureK + 1.0);
      reused.setPressure(testCase.pressureBara * 1.02, "bara");
      flash(reused);
      SystemInterface freshChanged =
          flash(testCase.create(testCase.temperatureK + 1.0, testCase.pressureBara * 1.02, true));
      assertEquivalentState(freshChanged, reused, 1.0e-8, testCase.name + " changed state");

      reused.setTemperature(testCase.temperatureK);
      reused.setPressure(testCase.pressureBara, "bara");
      flash(reused);
      assertEquivalentState(reference, reused, 1.0e-8, testCase.name + " returned state");

      SystemInterface previous = reused.clone();
      flash(reused);
      assertEquivalentState(previous, reused, 1.0e-10, testCase.name + " deterministic repeat");
    }
  }

  private SystemInterface flash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void setPoorBetaEstimate(SystemInterface system) {
    system.init(0);
    assertTrue(system.getNumberOfPhases() >= 2, "poor initialization requires two phase slots");
    system.setBeta(0, 1.0e-12);
    system.setBeta(1, 1.0 - 1.0e-12);
  }

  private void assertClosedState(SystemInterface system, String label) {
    int componentCount = system.getPhase(0).getNumberOfComponents();
    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta <= 1.0, label + " beta " + phase);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition " + phase + "/" + component);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE,
          label + " composition normalization " + phase);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material-balance residual " + materialResidual);

    if (system.getNumberOfPhases() == 1) {
      assertEquals(1.0, system.getBeta(0), NORMALIZATION_TOLERANCE, label + " single-phase beta");
      for (int component = 0; component < componentCount; component++) {
        assertEquals(system.getPhase(0).getComponent(component).getz(),
            system.getPhase(0).getComponent(component).getx(), MATERIAL_BALANCE_TOLERANCE,
            label + " single-phase x=z " + component);
      }
    } else {
      double fugacityResidual = maximumComparableLogFugacityResidual(system);
      assertTrue(fugacityResidual < FUGACITY_TOLERANCE,
          label + " fugacity residual " + fugacityResidual);
    }

    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentState(SystemInterface expected, SystemInterface actual,
      double tolerance, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label + " phase count");
    assertClosedState(expected, label + " expected");
    assertClosedState(actual, label + " actual");

    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType type = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, type);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance,
          label + " beta " + type);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(),
          tolerance, label + " compressibility " + type);
      for (int component = 0;
          component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + type + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance,
        label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance,
        label + " enthalpy");
  }

  private void assertExtensiveEquals(double expected, double actual, double relativeTolerance,
      String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemInterface system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + type);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents(); component++) {
      for (int firstPhase = 0; firstPhase < system.getNumberOfPhases(); firstPhase++) {
        for (int secondPhase = firstPhase + 1; secondPhase < system.getNumberOfPhases();
            secondPhase++) {
          double firstComposition = system.getPhase(firstPhase).getComponent(component).getx();
          double secondComposition = system.getPhase(secondPhase).getComponent(component).getx();
          double firstCoefficient =
              system.getPhase(firstPhase).getComponent(component).getFugacityCoefficient();
          double secondCoefficient =
              system.getPhase(secondPhase).getComponent(component).getFugacityCoefficient();
          if (firstComposition > 1.0e-20 && secondComposition > 1.0e-20
              && Double.isFinite(firstCoefficient) && firstCoefficient > 0.0
              && Double.isFinite(secondCoefficient) && secondCoefficient > 0.0) {
            maximumResidual = Math.max(maximumResidual,
                Math.abs(Math.log(firstComposition * firstCoefficient)
                    - Math.log(secondComposition * secondCoefficient)));
            comparisons++;
          }
        }
      }
    }
    assertTrue(comparisons > 0, "expected at least one comparable cross-phase fugacity");
    return maximumResidual;
  }

  private static final class FluidCase {
    private final String name;
    private final double temperatureK;
    private final double pressureBara;
    private final String[] components;
    private final double[] moles;

    private FluidCase(String name, double temperatureK, double pressureBara, String[] components,
        double[] moles) {
      this.name = name;
      this.temperatureK = temperatureK;
      this.pressureBara = pressureBara;
      this.components = components;
      this.moles = moles;
    }

    private SystemInterface create(boolean multiphaseCheck) {
      return create(temperatureK, pressureBara, multiphaseCheck);
    }

    private SystemInterface create(double temperature, double pressure, boolean multiphaseCheck) {
      SystemInterface system = new SystemUMRPRUMCEos(temperature, pressure);
      for (int component = 0; component < components.length; component++) {
        system.addComponent(components[component], moles[component]);
      }
      system.setMixingRule("HV", "UNIFAC_UMRPRU");
      system.setMultiPhaseCheck(multiphaseCheck);
      return system;
    }
  }
}
