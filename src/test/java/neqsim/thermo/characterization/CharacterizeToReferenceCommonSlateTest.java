package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests the Pedersen et al. (Chapter 5.6) "Common EoS" slate produced by
 * {@link PseudoComponentCombiner#characterizeToReference(SystemInterface, SystemInterface,
 * CharacterizationOptions)} when {@link CharacterizationOptions#isInheritReferenceProperties()} is
 * enabled (the default).
 *
 * <p>
 * Two different source fluids characterized to the same reference must end up with an identical set
 * of pseudo-component properties (molar mass, critical constants, acentric factor) and may differ
 * only in their mole fractions. The bare two-argument overload must remain grid-only for backward
 * compatibility.
 */
class CharacterizeToReferenceCommonSlateTest {
  private static final double TOLERANCE = 1e-6;

  private static SystemInterface referenceFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.6);

    fluid.addTBPfraction("C7", 0.20, 0.090, 0.74);
    ComponentInterface c7 = fluid.getComponent("C7_PC");
    c7.setTC(530.0);
    c7.setPC(29.0);
    c7.setAcentricFactor(0.31);

    fluid.addTBPfraction("C8", 0.12, 0.110, 0.76);
    ComponentInterface c8 = fluid.getComponent("C8_PC");
    c8.setTC(550.0);
    c8.setPC(27.0);
    c8.setAcentricFactor(0.33);

    fluid.addTBPfraction("C9", 0.08, 0.150, 0.79);
    ComponentInterface c9 = fluid.getComponent("C9_PC");
    c9.setTC(570.0);
    c9.setPC(25.0);
    c9.setAcentricFactor(0.35);
    return fluid;
  }

  private static SystemInterface source(double pcA, double pcB, double pcC, double tailMolarMass) {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.5);
    fluid.addTBPfraction("PCA", pcA, 0.085, 0.73);
    fluid.addTBPfraction("PCB", pcB, 0.120, 0.77);
    fluid.addTBPfraction("PCC", pcC, tailMolarMass, 0.80);
    return fluid;
  }

  @Test
  @DisplayName("inheritReferenceProperties defaults to true")
  void testDefaultIsInheritReferenceProperties() {
    assertTrue(CharacterizationOptions.builder().build().isInheritReferenceProperties());
  }

  @Test
  @DisplayName("common slate: two sources share identical lump properties, only mole fractions differ")
  void testCommonSlate() {
    SystemInterface reference = referenceFluid();
    CharacterizationOptions options = CharacterizationOptions.builder().build();

    SystemInterface a =
        PseudoComponentCombiner.characterizeToReference(source(0.05, 0.06, 0.07, 0.150), reference, options);
    SystemInterface b =
        PseudoComponentCombiner.characterizeToReference(source(0.10, 0.12, 0.15, 0.200), reference, options);

    String[] lumps = {"C7_PC", "C8_PC", "C9_PC"};
    double[] refTc = {530.0, 550.0, 570.0};
    double[] refPc = {29.0, 27.0, 25.0};
    double[] refOmega = {0.31, 0.33, 0.35};
    double[] refMw = {0.090, 0.110, 0.150};

    boolean someMoleFractionDiffers = false;
    for (int i = 0; i < lumps.length; i++) {
      ComponentInterface ca = a.getComponent(lumps[i]);
      ComponentInterface cb = b.getComponent(lumps[i]);

      // Lump properties are inherited from the reference and identical between the two sources.
      assertEquals(refMw[i], ca.getMolarMass(), TOLERANCE, lumps[i] + " molar mass A");
      assertEquals(ca.getMolarMass(), cb.getMolarMass(), TOLERANCE, lumps[i] + " molar mass A vs B");
      assertEquals(refTc[i], ca.getTC(), TOLERANCE, lumps[i] + " Tc A");
      assertEquals(ca.getTC(), cb.getTC(), TOLERANCE, lumps[i] + " Tc A vs B");
      assertEquals(refPc[i], ca.getPC(), TOLERANCE, lumps[i] + " Pc A");
      assertEquals(ca.getPC(), cb.getPC(), TOLERANCE, lumps[i] + " Pc A vs B");
      assertEquals(refOmega[i], ca.getAcentricFactor(), TOLERANCE, lumps[i] + " omega A");
      assertEquals(ca.getAcentricFactor(), cb.getAcentricFactor(), TOLERANCE, lumps[i] + " omega A vs B");

      if (Math.abs(ca.getz() - cb.getz()) > 1e-4) {
        someMoleFractionDiffers = true;
      }
    }
    assertTrue(someMoleFractionDiffers, "mole fractions must differ between the two sources");
  }

  @Test
  @DisplayName("bare two-arg overload stays grid-only (lump properties recomputed from source)")
  void testTwoArgRemainsGridOnly() {
    SystemInterface reference = referenceFluid();

    SystemInterface a =
        PseudoComponentCombiner.characterizeToReference(source(0.05, 0.06, 0.07, 0.150), reference);
    SystemInterface b =
        PseudoComponentCombiner.characterizeToReference(source(0.10, 0.12, 0.15, 0.200), reference);

    // Grid-only: lump molar mass is recomputed from each source's mass, so the heaviest lump
    // (which carries the differing tail) differs between the two sources and does not match the
    // inherited reference molar mass.
    double mwA = a.getComponent("C9_PC").getMolarMass();
    double mwB = b.getComponent("C9_PC").getMolarMass();
    assertFalse(Math.abs(mwA - mwB) < TOLERANCE, "grid-only C9_PC molar mass should differ between sources");
  }
}
