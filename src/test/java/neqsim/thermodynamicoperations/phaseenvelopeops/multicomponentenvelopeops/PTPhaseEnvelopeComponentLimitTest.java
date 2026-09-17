package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the number of components the Michelsen phase envelope accepts.
 *
 * <p>
 * The cricondentherm and cricondenbar composition arrays were declared with a fixed length of 100 while the loops that
 * fill them are bounded by the component count, so any fluid with more than 100 components failed with
 * {@code ArrayIndexOutOfBoundsException: Index 100 out of bounds for length 100}. Reported against a 101-component
 * characterised fluid.
 * </p>
 *
 * @author Pablo Dupuy
 * @version 1.0
 */
public class PTPhaseEnvelopeComponentLimitTest {
  /** Backbone mole fractions, summing to 0.94; the remainder is spread over the filler components. */
  private static final double[] BACKBONE_FRACTIONS = new double[] { 0.780, 0.080, 0.040, 0.010, 0.015, 0.006, 0.005,
      0.004 };

  /** Backbone component names, chosen so the mixture actually traces an envelope. */
  private static final String[] BACKBONE_NAMES = new String[] { "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "n-hexane" };

  /**
   * Build a light gas padded with TBP fractions until it has the requested number of components.
   *
   * <p>
   * TBP fractions are used as filler because they let the component count be raised to any value without depending on
   * how many named components the database happens to contain.
   * </p>
   *
   * @param numberOfComponents total component count to reach; must be at least the backbone length
   * @return a system with the requested number of components and a classic mixing rule
   */
  private SystemInterface buildFluid(int numberOfComponents) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 15.0, 50.0);
    for (int i = 0; i < BACKBONE_NAMES.length; i++) {
      fluid.addComponent(BACKBONE_NAMES[i], BACKBONE_FRACTIONS[i]);
    }
    double spare = 1.0;
    for (int i = 0; i < BACKBONE_FRACTIONS.length; i++) {
      spare -= BACKBONE_FRACTIONS[i];
    }
    int fillers = numberOfComponents - BACKBONE_NAMES.length;
    if (fillers > 0) {
      double each = spare / fillers;
      for (int i = 0; i < fillers; i++) {
        // Molar mass and density are stepped so the fractions stay distinguishable.
        fluid.addTBPfraction("C" + (7 + i), each, 100.0 + 2.0 * i, 0.75 + 0.0005 * i);
      }
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * A fluid with more than 100 components must not overflow the criconden composition arrays.
   */
  @Test
  void testEnvelopeAcceptsMoreThanOneHundredComponents() {
    SystemInterface fluid = buildFluid(120);
    assertTrue(fluid.getPhase(0).getNumberOfComponents() > 100,
        "fluid must exceed the old fixed array length to exercise the regression");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope(),
        "phase envelope must not fail on a fluid with more than 100 components");
  }

  /**
   * The criconden composition arrays must be as long as the component count, not a fixed 100.
   */
  @Test
  void testCricondenCompositionArraysMatchComponentCount() {
    SystemInterface fluid = buildFluid(120);
    int numberOfComponents = fluid.getPhase(0).getNumberOfComponents();

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    for (String key : new String[] { "cricondenthermX", "cricondenthermY", "cricondenbarX", "cricondenbarY" }) {
      double[] values = ops.get(key);
      assertNotNull(values, key + " must be available");
      assertEquals(numberOfComponents, values.length, key + " must have one entry per component");
    }
  }

  /**
   * A fluid at the old boundary must keep working, so the fix does not disturb existing behaviour.
   */
  @Test
  void testEnvelopeStillWorksAtOneHundredComponents() {
    SystemInterface fluid = buildFluid(100);
    assertEquals(100, fluid.getPhase(0).getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope(), "phase envelope must still work at exactly 100 components");
    assertNotNull(ops.get("dewT"));
  }
}
