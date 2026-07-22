package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests the optional delumping stage of
 * {@link PseudoComponentCombiner#characterizeToReference(SystemInterface, SystemInterface, CharacterizationOptions)}
 * enabled through {@link CharacterizationOptions#isDelumpBeforeRecharacterization()}.
 *
 * <p>
 * Delumping splits every coarse source lump into a finer grid of single-carbon-number sub-fractions that conserve the
 * parent moles and mass exactly (Pedersen et al., Chapter 5, lumping/delumping), then re-lumps them onto the reference
 * cuts. This removes the per-field molar-mass and density drift that occurs when the native source lumps already
 * coincide with the reference grid.
 */
class CharacterizeToReferenceDelumpTest {

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

  private static SystemInterface coarseSource() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.5);
    fluid.addTBPfraction("PCA", 0.20, 0.100, 0.73);
    fluid.addTBPfraction("PCB", 0.12, 0.160, 0.79);
    fluid.addTBPfraction("PCC", 0.08, 0.240, 0.83);
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

  private static double pseudoVolume(SystemInterface fluid) {
    double volume = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if (c.isIsTBPfraction() || c.isIsPlusFraction()) {
        double mass = c.getNumberOfmoles() * c.getMolarMass();
        volume += mass / c.getNormalLiquidDensity();
      }
    }
    return volume;
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
  @DisplayName("delump flags default off with resolution 12")
  void testDefaults() {
    CharacterizationOptions options = CharacterizationOptions.builder().build();
    assertTrue(!options.isDelumpBeforeRecharacterization(), "delump should default to false");
    assertEquals(12, options.getDelumpResolution());
  }

  @Test
  @DisplayName("delump conserves total pseudo-fraction moles, mass and bulk molar mass")
  void testMassConservation() {
    SystemInterface source = coarseSource();
    SystemInterface reference = referenceFluid(6);

    double srcMoles = pseudoMoles(source);
    double srcMass = pseudoMass(source);
    double srcMw = srcMass / srcMoles;

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(true).delumpResolution(12).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(srcMoles, pseudoMoles(result), srcMoles * 1e-6, "total pseudo moles conserved");
    assertEquals(srcMass, pseudoMass(result), srcMass * 1e-6, "total pseudo mass conserved");

    double resMw = pseudoMass(result) / pseudoMoles(result);
    assertEquals(srcMw, resMw, srcMw * 0.01, "bulk molar mass preserved within 1%");
  }

  @Test
  @DisplayName("delump preserves bulk normal-liquid density of the pseudo fraction")
  void testDensityPreservation() {
    SystemInterface source = coarseSource();
    SystemInterface reference = referenceFluid(6);

    double srcDensity = pseudoMass(source) / pseudoVolume(source);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(true).delumpResolution(12).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    double resDensity = pseudoMass(result) / pseudoVolume(result);
    assertEquals(srcDensity, resDensity, srcDensity * 0.01, "bulk density preserved within 1%");
  }

  @Test
  @DisplayName("delump removes the identity source-to-reference mapping by redistributing mass")
  void testIdentityMapElimination() {
    // Source lumps coincide with three of the six reference cuts (0.100, 0.160,
    // 0.240).
    SystemInterface reference = referenceFluid(6);

    CharacterizationOptions noDelump = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(false).build();
    SystemInterface resultNoDelump = PseudoComponentCombiner.characterizeToReference(coarseSource(), reference,
        noDelump);

    CharacterizationOptions withDelump = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(true).delumpResolution(12).build();
    SystemInterface resultDelump = PseudoComponentCombiner.characterizeToReference(coarseSource(), reference,
        withDelump);

    int noDelumpCuts = validPseudoComponents(resultNoDelump);
    int delumpCuts = validPseudoComponents(resultDelump);

    assertTrue(delumpCuts > noDelumpCuts, "delumping should populate more reference cuts than the identity mapping ("
        + delumpCuts + " vs " + noDelumpCuts + ")");
  }

  @Test
  @DisplayName("delump=false reproduces the existing grid-only behaviour exactly")
  void testBackwardCompatibility() {
    SystemInterface reference = referenceFluid(6);

    SystemInterface bare = PseudoComponentCombiner.characterizeToReference(coarseSource(), reference);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(false).build();
    SystemInterface withOptions = PseudoComponentCombiner.characterizeToReference(coarseSource(), reference, options);

    assertEquals(bare.getNumberOfComponents(), withOptions.getNumberOfComponents(), "same number of components");
    for (int i = 0; i < bare.getNumberOfComponents(); i++) {
      ComponentInterface a = bare.getComponent(i);
      ComponentInterface b = withOptions.getComponent(i);
      assertEquals(a.getNumberOfmoles(), b.getNumberOfmoles(), 1e-12, a.getComponentName() + " moles");
      assertEquals(a.getMolarMass(), b.getMolarMass(), 1e-12, a.getComponentName() + " molar mass");
    }
  }

  @Test
  @DisplayName("single-lump delump closes mass and moles through the public path")
  void testSingleLumpClosure() {
    // One coarse source lump exercises the per-parent mass-closure invariant in
    // isolation.
    SystemInterface source = new SystemPrEos(298.15, 60.0);
    source.addComponent("methane", 0.5);
    source.addTBPfraction("PCA", 0.30, 0.150, 0.78);

    SystemInterface reference = referenceFluid(6);

    double srcMoles = pseudoMoles(source);
    double srcMass = pseudoMass(source);

    CharacterizationOptions options = CharacterizationOptions.builder().inheritReferenceProperties(false)
        .delumpBeforeRecharacterization(true).delumpResolution(20).build();
    SystemInterface result = PseudoComponentCombiner.characterizeToReference(source, reference, options);

    assertEquals(srcMoles, pseudoMoles(result), srcMoles * 1e-9, "single-lump moles conserved");
    assertEquals(srcMass, pseudoMass(result), srcMass * 1e-9, "single-lump mass conserved");
  }
}
