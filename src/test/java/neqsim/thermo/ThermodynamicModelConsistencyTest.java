package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests that model diagnostics reject individual inconsistencies without cancellation or source mutation.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ThermodynamicModelConsistencyTest {
  /** Verifies that errors in an early phase or derivative column cannot be hidden by the final column. */
  @Test
  void detectsEveryCompositionIdentityColumn() {
    for (int phase = 0; phase < 2; phase++) {
      SystemInterface fluid = twoPhaseFluid();
      clearCompositionDerivatives(fluid);
      ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
      assertTrue(checker.checkFugacityCoefficientsDn());
      fluid.getPhase(phase).getComponent(0).setdfugdn(0, 1.0);
      assertFalse(checker.checkFugacityCoefficientsDn(), "phase " + phase);
    }
  }

  /** Verifies that signed symmetry errors do not cancel within the final derivative column. */
  @Test
  void rejectsCancellingSymmetryErrors() {
    SystemInterface fluid = twoPhaseFluid();
    clearCompositionDerivatives(fluid);
    fluid.getPhase(1).getComponent(0).setdfugdn(2, 1.0);
    fluid.getPhase(1).getComponent(1).setdfugdn(2, -1.0);
    assertFalse(new ThermodynamicModelTest(fluid).checkFugacityCoefficientsDn2());
  }

  /** Checks both composition diagnostics for non-finite entries outside the final column and phase. */
  @Test
  void rejectsNonFiniteCompositionEntries() {
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      SystemInterface fluid = twoPhaseFluid();
      clearCompositionDerivatives(fluid);
      fluid.getPhase(0).getComponent(0).setdfugdn(0, invalid);
      ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
      assertFalse(checker.checkFugacityCoefficientsDn());
      assertFalse(checker.checkFugacityCoefficientsDn2());
    }
  }

  /** Checks that equal and opposite phase errors cannot hide in a combined temperature residual. */
  @Test
  void rejectsCancellingTemperatureErrors() {
    SystemInterface fluid = twoPhaseFluid();
    ThermodynamicModelTest checker = checker(fluid, 1e-8);
    assertTrue(checker.checkFugacityCoefficientsDT());
    for (int phase = 0; phase < 2; phase++) {
      ComponentInterface component = fluid.getPhase(phase).getComponent(0);
      component.setdfugdt(component.getdfugdt() + (phase == 0 ? 1.0 : -1.0) / component.getNumberOfMolesInPhase());
    }
    assertFalse(checker.checkFugacityCoefficientsDT());
  }

  /** Checks that equal and opposite phase errors cannot hide in a combined pressure residual. */
  @Test
  void rejectsCancellingPressureErrors() {
    SystemInterface fluid = twoPhaseFluid();
    ThermodynamicModelTest checker = checker(fluid, 1e-8);
    assertTrue(checker.checkFugacityCoefficientsDP());
    for (int phase = 0; phase < 2; phase++) {
      ComponentInterface component = fluid.getPhase(phase).getComponent(0);
      component.setdfugdp(component.getdfugdp() + (phase == 0 ? 1.0 : -1.0) / component.getNumberOfMolesInPhase());
    }
    assertFalse(checker.checkFugacityCoefficientsDP());
  }

  /** Checks each residual Gibbs identity independently even when the total error is zero. */
  @Test
  void rejectsCancellingFugacityErrors() {
    SystemInterface fluid = twoPhaseFluid();
    ThermodynamicModelTest checker = checker(fluid, 1e-8);
    assertTrue(checker.checkFugacityCoefficients());
    for (int phase = 0; phase < 2; phase++) {
      ComponentInterface component = fluid.getPhase(phase).getComponent(0);
      component.setFugacityCoefficient(component.getFugacityCoefficient()
          * Math.exp((phase == 0 ? 0.1 : -0.1) / component.getNumberOfMolesInPhase()));
    }
    assertFalse(checker.checkFugacityCoefficients());
  }

  /** Requires a numeric check to report an incorrect analytic derivative rather than unconditional success. */
  @Test
  void numericalCheckRejectsDerivativeMismatch() {
    FaultyDerivativeSystem fluid = new FaultyDerivativeSystem();
    configure(fluid);
    fluid.injectError = true;
    byte[] before = SerializationUtils.serialize(fluid);
    assertFalse(checker(fluid, 1e-6).checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }

  /** Checks finite differences on a real two-phase fluid and verifies complete source-state preservation. */
  @Test
  void numericalCheckAcceptsConsistentFluidWithoutMutation() {
    SystemInterface fluid = twoPhaseFluid();
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker(fluid, 1e-6).checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }

  /** A one-phase state must be checked without assuming a second active phase exists. */
  @Test
  void numericalCheckSupportsOneActivePhase() {
    SystemInterface fluid = new SystemSrkEos(400.0, 1.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    assertEquals(1, fluid.getNumberOfPhases());
    assertTrue(checker(fluid, 1e-6).checkNumerically());
  }

  /**
   * Creates a real two-phase fixture with three components and initialized derivative caches.
   *
   * @return initialized SRK fluid
   */
  private static SystemInterface twoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    configure(fluid);
    return fluid;
  }

  /**
   * Populates and flashes a fixture shared by ordinary and deliberately faulty derivative providers.
   *
   * @param fluid empty SRK fluid
   */
  private static void configure(SystemInterface fluid) {
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("n-decane", 0.1);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
  }

  /**
   * Clears composition derivatives to isolate aggregation behavior from the thermodynamic model.
   *
   * @param fluid initialized fluid whose derivative entries will be replaced
   */
  private static void clearCompositionDerivatives(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      for (int row = 0; row < fluid.getNumberOfComponents(); row++) {
        for (int column = 0; column < fluid.getNumberOfComponents(); column++) {
          fluid.getPhase(phase).getComponent(row).setdfugdn(column, 0.0);
        }
      }
    }
  }

  /**
   * Configures the accuracy demanded of a diagnostic without changing its production default.
   *
   * @param fluid initialized fluid
   * @param tolerance requested diagnostic tolerance
   * @return configured checker
   */
  private static ThermodynamicModelTest checker(SystemInterface fluid, double tolerance) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(tolerance);
    return checker;
  }

  /**
   * An SRK model with a deliberately incorrect declared derivative but unchanged fugacity evaluation.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static class FaultyDerivativeSystem extends SystemSrkEos {
    /** Serialization identifier for detached numerical trials. */
    private static final long serialVersionUID = 1L;
    /** Whether to corrupt the initialized analytic temperature derivative. */
    private boolean injectError;

    /** Creates the same thermodynamic state as the ordinary two-phase fixture. */
    FaultyDerivativeSystem() {
      super(300.0, 50.0);
    }

    /** {@inheritDoc} */
    @Override
    public void init(int type) {
      super.init(type);
      if (injectError && type == 3) {
        ComponentInterface component = getPhase(0).getComponent(0);
        component.setdfugdt(component.getdfugdt() + 1.0);
      }
    }
  }
}
