package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies SRK/PR Huron-Vidal fugacity coefficients through the existing thermodynamic model diagnostics.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalThermodynamicModelConsistencyTest extends neqsim.NeqSimTest {
  /** Existing model-checker test accuracy for step-converged numerical derivatives, without changing its default. */
  private static final double NUMERICAL_TOLERANCE = 1e-6;
  /** Initial state, changed state, and restoration of the initial state. */
  private static final double[][] STATES = {{298.15, 60.0}, {303.15, 70.0}, {298.15, 60.0}};

  /**
   * Requires all five native identities at their unchanged default tolerance in every gas, oil and aqueous phase.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   */
  @ParameterizedTest
  @CsvSource({"false,none", "false,methanol", "false,MEG", "true,none", "true,methanol", "true,MEG"})
  void nativeIdentitiesCoverThreePhaseAndChangedStates(boolean pengRobinson, String inhibitor) {
    SystemInterface fluid = fluid(pengRobinson, inhibitor);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (double[] state : STATES) {
      flash(fluid, operations, state);
      assertNativeIdentities(fluid);
    }
  }

  /**
   * Checks numerical T, P and mole derivatives on detached phase roots, including trace decane in uninhibited water.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   */
  @ParameterizedTest
  @CsvSource({"false,none", "false,methanol", "false,MEG", "true,none", "true,methanol", "true,MEG"})
  void nativeNumericalDerivativesCoverThreePhaseAndChangedStates(boolean pengRobinson, String inhibitor) {
    SystemInterface fluid = fluid(pengRobinson, inhibitor);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (double[] state : STATES) {
      flash(fluid, operations, state);
      byte[] snapshot = SerializationUtils.serialize(fluid);
      assertTrue(numericalChecker(fluid).checkNumerically(),
          "Native numerical derivatives for " + fluid.getModelName() + "/" + inhibitor + " at " + state[0] + " K");
      assertArrayEquals(snapshot, SerializationUtils.serialize(fluid),
          "The diagnostic must preserve the supplied fluid");
    }
  }

  /**
   * Exercises the same public checker on a valid forced gas root without relying on a multiphase flash.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nativeCheckerSupportsOnePhaseHuronVidal(boolean pengRobinson) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(310.0, 20.0) : new SystemSrkEos(310.0, 20.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("n-decane", 0.20);
    fluid.addComponent("water", 0.12);
    fluid.addComponent("MEG", 0.03);
    fluid.setMixingRule(4);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    assertEquals(1, fluid.getNumberOfPhases());
    assertNativeIdentities(fluid);
    assertTrue(numericalChecker(fluid).checkNumerically());
  }

  /**
   * Checks that refining a trace-boundary stencil cannot accept a wrong HV mole derivative or mutate the source.
   *
   * @param error persistent analytic bias or non-finite corruption
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0, Double.NaN, Double.POSITIVE_INFINITY})
  void nativeNumericalCheckerRejectsWrongHvDerivativeWithoutMutation(double error) {
    FaultyHvSystem fluid = new FaultyHvSystem();
    configure(fluid, "none");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertNativeIdentities(fluid);
    fluid.derivativeError = error;
    byte[] snapshot = SerializationUtils.serialize(fluid);
    assertFalse(numericalChecker(fluid).checkNumerically());
    assertArrayEquals(snapshot, SerializationUtils.serialize(fluid));
  }

  /**
   * Constructs a synthetic three-phase test fluid with temperature-dependent Huron-Vidal mixing.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   * @return configured, unflashed fluid
   */
  private static SystemInterface fluid(boolean pengRobinson, String inhibitor) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(298.15, 60.0) : new SystemSrkEos(298.15, 60.0);
    configure(fluid, inhibitor);
    return fluid;
  }

  /**
   * Populates the common fixture without changing model parameters or solver tolerances.
   *
   * @param fluid empty cubic-EOS fluid
   * @param inhibitor inhibitor component or {@code none}
   */
  private static void configure(SystemInterface fluid, String inhibitor) {
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("n-decane", 0.20);
    fluid.addComponent("water", 0.15);
    if (!"none".equals(inhibitor)) {
      fluid.addComponent(inhibitor, 0.03);
    }
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
  }

  /**
   * Flashes and initializes the full derivative caches at a prescribed state.
   *
   * @param fluid configured fluid
   * @param operations operations bound to that fluid
   * @param state temperature in K and absolute pressure in bara
   */
  private static void flash(SystemInterface fluid, ThermodynamicOperations operations, double[] state) {
    fluid.setTemperature(state[0]);
    fluid.setPressure(state[1]);
    operations.TPflash();
    fluid.init(3);
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
  }

  /**
   * Asserts returned checker results rather than relying on its log-only convenience runner.
   *
   * @param fluid initialized fluid whose active phases will all be checked
   */
  private static void assertNativeIdentities(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    assertAll("Native HV thermodynamic identities",
        () -> assertTrue(checker.checkFugacityCoefficients(), "Residual Gibbs energy and log fugacity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDn(), "Composition Gibbs-Duhem identity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDn2(), "Composition derivative symmetry"),
        () -> assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure derivative identity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature derivative identity"));
  }

  /**
   * Uses the numerical accuracy already established by the checker tests, keeping analytic identities stricter.
   *
   * @param fluid initialized fluid
   * @return checker requiring step-converged numerical derivatives at the declared tolerance
   */
  private static ThermodynamicModelTest numericalChecker(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(NUMERICAL_TOLERANCE);
    return checker;
  }

  /**
   * A real HV fluid that can deliberately publish a wrong trace-boundary mole derivative on reference initialization.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class FaultyHvSystem extends SystemSrkEos {
    /** Serialization identifier for independent numerical snapshots. */
    private static final long serialVersionUID = 1L;
    /** Deliberate error added to a trace-composition derivative during analytical initialization. */
    private double derivativeError;

    /** Creates the same initial state as the ordinary three-phase fixture. */
    FaultyHvSystem() {
      super(298.15, 60.0);
    }

    /** {@inheritDoc} */
    @Override
    public void init(int type) {
      super.init(type);
      if (derivativeError != 0.0 && type == 3) {
        ComponentInterface component = getPhase(PhaseType.AQUEOUS).getComponent("water");
        int variable = getPhase(PhaseType.AQUEOUS).getComponent("n-decane").getComponentNumber();
        component.setdfugdn(variable, component.getdfugdn(variable) + derivativeError);
      }
    }
  }
}
