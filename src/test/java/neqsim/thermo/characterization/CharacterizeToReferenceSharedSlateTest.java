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
 * The imaginary composition supplies mass-quantile candidates that are constrained to the gaps between reference
 * sorting keys. Tests verify observable redistribution, boundary inclusion, conservation and legacy fallbacks.
 *
 * @author ESOL
 * @version 1.0
 */
class CharacterizeToReferenceSharedSlateTest {

  /**
   * Reference with equal moles but increasing molar mass, so the per-cut mass is unequal.
   *
   * @param cuts number of reference cuts, from one to six
   * @return reference fluid
   */
  private static SystemInterface referenceFluid(int cuts) {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.6);
    double[] molarMass = {0.080, 0.100, 0.130, 0.160, 0.200, 0.240};
    double[] density = {0.70, 0.73, 0.76, 0.79, 0.81, 0.83};
    for (int i = 0; i < cuts; i++) {
      fluid.addTBPfraction("C" + (7 + i), 0.10, molarMass[i], density[i]);
    }
    return fluid;
  }

  /**
   * A graded source spanning the same carbon-number range as the reference.
   *
   * @return source fluid
   */
  private static SystemInterface gradedSource() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.5);
    fluid.addTBPfraction("S1", 0.20, 0.090, 0.71);
    fluid.addTBPfraction("S2", 0.15, 0.140, 0.77);
    fluid.addTBPfraction("S3", 0.10, 0.210, 0.82);
    return fluid;
  }

  /**
   * Count pseudo-component moles.
   *
   * @param fluid fluid to inspect
   * @return total pseudo-component moles
   */
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

  /**
   * Calculate pseudo-component mass.
   *
   * @param fluid fluid to inspect
   * @return total pseudo-component mass in kg
   */
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

  /**
   * Count populated pseudo-components.
   *
   * @param fluid fluid to inspect
   * @return number of populated pseudo-components
   */
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

  /** Check that shared boundaries remain opt-in. */
  @Test
  @DisplayName("sharedImaginaryBoundaries defaults off")
  void testDefaultOff() {
    CharacterizationOptions options = CharacterizationOptions.builder().build();
    assertTrue(!options.isSharedImaginaryBoundaries(), "sharedImaginaryBoundaries should default to false");
  }

  /** Compare the complete flag-off result with the no-options overload. */
  @Test
  @DisplayName("flag off reproduces the no-options reference characterization exactly")
  void testBackwardCompatible() {
    SystemInterface source = gradedSource();
    SystemInterface reference = referenceFluid(6);

    SystemInterface base = PseudoComponentCombiner.characterizeToReference(source, reference);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(false).build();
    SystemInterface flagged = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertSameComposition(base, flagged);
    assertEquals(validPseudoComponents(base), validPseudoComponents(flagged),
        "flag off must not change the number of populated cuts");
    assertEquals(pseudoMoles(base), pseudoMoles(flagged), pseudoMoles(base) * 1e-9,
        "flag off must not change the total pseudo moles");
    assertEquals(pseudoMass(base), pseudoMass(flagged), pseudoMass(base) * 1e-9,
        "flag off must not change the total pseudo mass");
  }

  /** Verify inventory conservation with source-derived properties. */
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

  /** Preserve each reference lump and its inherited properties. */
  @Test
  @DisplayName("equal-mass boundaries preserve the one-to-one inherit mapping for an identical source")
  void testPositionalIdentityPreserved() {
    // Source built to match the reference pseudo-components exactly.
    SystemInterface reference = referenceFluid(6);
    SystemInterface source = referenceFluid(6);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(true)
        .sharedImaginaryBoundaries(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertSameComposition(reference, result);
    assertEquals(6, validPseudoComponents(result),
        "every reference cut must remain populated (no cut collapses or doubles up)");

    // Each output pseudo-component must inherit the reference molar mass in carbon-number order,
    // proving the clamped equal-mass cuts kept each reference PC inside its own cut.
    double[] expectedMw = {0.080, 0.100, 0.130, 0.160, 0.200, 0.240};
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

  /** Retain midpoint behavior when the imaginary grid is disabled. */
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
    assertSameComposition(base, fallback);
  }

  /** Sparse whole source lumps may stay in the same bins even after reference boundaries move. */
  @Test
  void testSparseSourceCanRetainItsBins() {
    SystemInterface source = gradedSource();
    SystemInterface reference = referenceFluid(6);
    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);
    SystemInterface midpoint = PseudoComponentCombiner.characterizeToReference(source, reference);

    assertSameComposition(midpoint, result);
    assertEquals(pseudoMoles(source), pseudoMoles(result), 1e-12);
    assertEquals(pseudoMass(source), pseudoMass(result), 1e-12);
  }

  /** Quantiles above both gaps must project to just below the upper reference keys. */
  @Test
  void testHeavyReferenceProjectsToUpperGapEdges() {
    SystemInterface reference = controlledReference(1.0, 1.0, 100.0);
    assertProbeCut(reference, 450.0, "R1_PC");
    assertProbeCut(reference, Math.nextDown(500.0), "R1_PC");
    assertProbeCut(reference, 500.0, "R2_PC");
    assertProbeCut(reference, 650.0, "R2_PC");
    assertProbeCut(reference, Math.nextDown(700.0), "R2_PC");
    assertProbeCut(reference, 700.0, "R3_PC");
  }

  /** Quantiles below both gaps must project to just above the lower reference keys. */
  @Test
  void testLightReferenceProjectsToLowerGapEdges() {
    SystemInterface reference = controlledReference(100.0, 1.0, 1.0);
    assertProbeCut(reference, 300.0, "R1_PC");
    assertProbeCut(reference, Math.nextUp(300.0), "R1_PC");
    assertProbeCut(reference, 350.0, "R2_PC");
    assertProbeCut(reference, 500.0, "R2_PC");
    assertProbeCut(reference, Math.nextUp(500.0), "R2_PC");
    assertProbeCut(reference, 550.0, "R3_PC");
  }

  /** Valid interior quantiles retain their positions rather than moving to gap edges. */
  @Test
  void testInteriorQuantilesAreRetained() {
    SystemInterface reference = controlledReference(1.0, 1.0, 1.0);
    // The discrete 12-point mass quantiles are 399.7979157 K and 625.3249451 K.
    assertProbeCut(reference, 399.7, "R1_PC");
    assertProbeCut(reference, 399.9, "R2_PC");
    assertProbeCut(reference, 625.2, "R2_PC");
    assertProbeCut(reference, 625.4, "R3_PC");
  }

  /** Shared reference cuts also conserve inventory when source delumping is enabled. */
  @Test
  void testConservationWithSourceDelumping() {
    SystemInterface source = gradedSource();
    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(true).delumpBeforeRecharacterization(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, referenceFluid(6), options);
    assertEquals(pseudoMoles(source), pseudoMoles(result), 1e-12);
    assertEquals(pseudoMass(source), pseudoMass(result), 1e-12);
    assertEquals(0.5, result.getComponent("methane").getNumberOfmoles(), 1e-12);
    CharacterizationOptions midpointOptions = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(true).build();
    SystemInterface midpoint = PseudoComponentCombiner.characterizeToReference(source, referenceFluid(6),
        midpointOptions);
    double largestMoleChange = 0.0;
    for (int i = 0; i < result.getNumberOfComponents(); i++) {
      ComponentInterface component = result.getComponent(i);
      ComponentInterface previous = midpoint.getComponent(component.getComponentName());
      double previousMoles = previous == null ? 0.0 : previous.getNumberOfmoles();
      largestMoleChange = Math.max(largestMoleChange, Math.abs(component.getNumberOfmoles() - previousMoles));
    }
    assertTrue(largestMoleChange > 1e-3, "the fine source grid must respond to the shifted reference boundaries");
  }

  /** Equal keys and gaps with no interior double retain the legacy fallback. */
  @Test
  void testDegenerateGapsRetainMidpointFallback() {
    for (double upper : new double[] {300.0, Math.nextUp(300.0)}) {
      SystemInterface reference = controlledReference(1.0, 1.0, 1.0);
      reference.getComponent("R2_PC").setNormalBoilingPoint(upper);
      reference.getComponent("R3_PC").setNormalBoilingPoint(upper);
      CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
          .sharedImaginaryBoundaries(true).build();
      SystemInterface midpoint = PseudoComponentCombiner.characterizeToReference(reference, reference);
      SystemInterface shared = PseudoComponentCombiner.characterizeToReference(reference, reference, options);
      assertSameComposition(midpoint, shared);
    }
  }

  /**
   * Build a synthetic reference with explicit boiling points and controlled mass distribution.
   *
   * @param light light reference moles
   * @param middle middle reference moles
   * @param heavy heavy reference moles
   * @return three-cut reference at 300, 500 and 700 K
   */
  private static SystemInterface controlledReference(double light, double middle, double heavy) {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    double[] moles = {light, middle, heavy};
    for (int i = 0; i < moles.length; i++) {
      String name = "R" + (i + 1);
      fluid.addTBPfraction(name, moles[i], 0.1, 0.75);
      fluid.getComponent(name + "_PC").setNormalBoilingPoint(300.0 + 200.0 * i);
    }
    return fluid;
  }

  /**
   * Locate a cut through the public API, including the inclusive boundary convention.
   *
   * @param reference reference defining the cut grid
   * @param boilingPoint source sorting key in K
   * @param expectedName expected populated reference cut
   */
  private static void assertProbeCut(SystemInterface reference, double boilingPoint, String expectedName) {
    SystemInterface source = new SystemPrEos(298.15, 60.0);
    source.addTBPfraction("probe", 1.0, 0.1, 0.75);
    source.getComponent("probe_PC").setNormalBoilingPoint(boilingPoint);
    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .sharedImaginaryBoundaries(true).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);
    assertEquals(1, validPseudoComponents(result));
    assertEquals(expectedName, result.getComponent(0).getComponentName(), "source key " + boilingPoint);
    assertEquals(1.0, pseudoMoles(result), 1e-12);
    assertEquals(0.1, pseudoMass(result), 1e-12);
  }

  /**
   * Compare every populated component so a changed cut cannot hide behind conserved totals.
   *
   * @param expected expected fluid
   * @param actual fluid to check
   */
  private static void assertSameComposition(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfComponents(), actual.getNumberOfComponents());
    for (int i = 0; i < expected.getNumberOfComponents(); i++) {
      ComponentInterface a = expected.getComponent(i);
      ComponentInterface b = actual.getComponent(i);
      assertEquals(a.getComponentName(), b.getComponentName());
      assertEquals(a.getNumberOfmoles(), b.getNumberOfmoles(), 1e-12);
      assertEquals(a.getMolarMass(), b.getMolarMass(), 1e-12);
      assertEquals(a.getNormalLiquidDensity(), b.getNormalLiquidDensity(), 1e-12);
    }
  }
}
