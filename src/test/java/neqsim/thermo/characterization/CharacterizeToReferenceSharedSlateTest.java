package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests the optional shared-imaginary (equal-mass) reference cut-point placement of
 * {@link PseudoComponentCombiner#characterizeToReference(SystemInterface, SystemInterface, CharacterizationOptions)}
 * enabled through {@link CharacterizationOptions#isSharedImaginaryBoundaries()}.
 *
 * <p>
 * When the flag is on, the reference cut boundaries are placed as carbon-number-based equal-mass cut points on the
 * reference fluid's imaginary (delumped) composition (Pedersen et al., Chapter 5.6, Eqs. 5.58-5.59, with the reference
 * as the sole weighted fluid), instead of arithmetic boiling-point midpoints. Each equal-mass cut is clamped into the
 * gap between the two adjacent reference pseudo-components so the strict one-to-one property-inheritance ordering is
 * preserved even for a reference whose lumps carry unequal mass.
 */
class CharacterizeToReferenceSharedSlateTest {

  /** Reference with equal moles but increasing molar mass, so the per-cut mass is unequal. */
  private static SystemInterface referenceFluid(int cuts) {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.6);
    double[] molarMass = { 0.080, 0.100, 0.130, 0.160, 0.200, 0.240 };
    double[] density = { 0.70, 0.73, 0.76, 0.79, 0.81, 0.83 };
    for (int i = 0; i < cuts; i++) {
      fluid.addTBPfraction("C" + (7 + i), 0.10, molarMass[i], density[i]);
    }
    return fluid;
  }

  /** A graded source spanning the same carbon-number range as the reference. */
  private static SystemInterface gradedSource() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.5);
    fluid.addTBPfraction("S1", 0.20, 0.090, 0.71);
    fluid.addTBPfraction("S2", 0.15, 0.140, 0.77);
    fluid.addTBPfraction("S3", 0.10, 0.210, 0.82);
    return fluid;
  }

  private static double pseudoMoles(SystemInterface fluid) {
    double moles = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if (c.isIsTBPfraction() || c.isIsPlusFraction()) {
        moles += c.getNumberOfmoles();
      }
    }
    return moles;
  }

  private static double pseudoMass(SystemInterface fluid) {
    double mass = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if (c.isIsTBPfraction() || c.isIsPlusFraction()) {
        mass += c.getNumberOfmoles() * c.getMolarMass();
      }
    }
    return mass;
  }

  private static int validPseudoComponents(SystemInterface fluid) {
    int count = 0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if ((c.isIsTBPfraction() || c.isIsPlusFraction()) && c.getNumberOfmoles() > 0.0) {
        count++;
      }
    }
    return count;
  }

  @Test
  @DisplayName("sharedImaginaryBoundaries defaults off")
  void testDefaultOff() {
    CharacterizationOptions options = CharacterizationOptions.builder().build();
    assertTrue(!options.isSharedImaginaryBoundaries(), "sharedImaginaryBoundaries should default to false");
  }

  @Test
  @DisplayName("flag off reproduces the no-options reference characterization exactly")
  void testBackwardCompatible() {
    SystemInterface source = gradedSource();
    SystemInterface reference = referenceFluid(6);

    SystemInterface base = PseudoComponentCombiner.characterizeToReference(source, reference);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(false).build();
    SystemInterface flagged = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(validPseudoComponents(base), validPseudoComponents(flagged),
        "flag off must not change the number of populated cuts");
    assertEquals(pseudoMoles(base), pseudoMoles(flagged), pseudoMoles(base) * 1e-9,
        "flag off must not change the total pseudo moles");
    assertEquals(pseudoMass(base), pseudoMass(flagged), pseudoMass(base) * 1e-9,
        "flag off must not change the total pseudo mass");
  }

  @Test
  @DisplayName("equal-mass boundaries conserve total pseudo-fraction moles and mass")
  void testMassConservation() {
    SystemInterface source = gradedSource();
    SystemInterface reference = referenceFluid(6);

    double srcMoles = pseudoMoles(source);
    double srcMass = pseudoMass(source);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(srcMoles, pseudoMoles(result), srcMoles * 1e-6, "total pseudo moles conserved");
    assertEquals(srcMass, pseudoMass(result), srcMass * 1e-6, "total pseudo mass conserved");
  }

  @Test
  @DisplayName("equal-mass boundaries preserve the one-to-one inherit mapping for an identical source")
  void testPositionalIdentityPreserved() {
    // Source built to match the reference pseudo-components exactly.
    SystemInterface reference = referenceFluid(6);
    SystemInterface source = referenceFluid(6);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(true)
        .sharedImaginaryBoundaries(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(6, validPseudoComponents(result),
        "every reference cut must remain populated (no cut collapses or doubles up)");

    // Each output pseudo-component must inherit the reference molar mass in carbon-number order,
    // proving the clamped equal-mass cuts kept each reference PC inside its own cut.
    double[] expectedMw = { 0.080, 0.100, 0.130, 0.160, 0.200, 0.240 };
    int idx = 0;
    for (int i = 0; i < result.getNumberOfComponents(); i++) {
      ComponentInterface c = result.getComponent(i);
      if ((c.isIsTBPfraction() || c.isIsPlusFraction()) && c.getNumberOfmoles() > 0.0) {
        assertEquals(expectedMw[idx], c.getMolarMass(), 1e-9,
            "inherited molar mass must stay in carbon-number order at position " + idx);
        idx++;
      }
    }
  }

  @Test
  @DisplayName("resolution <= 1 falls back to boiling-point midpoints (no equal-mass shift)")
  void testResolutionFallback() {
    SystemInterface source = gradedSource();
    SystemInterface reference = referenceFluid(6);

    SystemInterface base = PseudoComponentCombiner.characterizeToReference(source, reference);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(true).delumpResolution(1).build();
    SystemInterface fallback = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(pseudoMoles(base), pseudoMoles(fallback), pseudoMoles(base) * 1e-9,
        "resolution<=1 must reproduce the midpoint boundaries");
  }
}
