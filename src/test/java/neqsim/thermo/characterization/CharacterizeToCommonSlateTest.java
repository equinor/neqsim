package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for {@link PseudoComponentCombiner#characterizeToCommonSlate} — the faithful Pedersen Chapter 5.6 (Eqs.
 * 5.55-5.60) common-slate characterization that keeps fluids separate while forcing them to share a single,
 * mole-fraction weighted pseudo-component property set.
 *
 * @author NeqSim
 */
class CharacterizeToCommonSlateTest {

  /** Methane moles (identical in both test fields). */
  private static final double METHANE = 0.5;
  /** Fixed normal boiling points used to force a deterministic, well separated cut grid. */
  private static final double[] NBP = { 400.0, 500.0, 600.0 };

  // Field A pseudo-component definitions (three cuts).
  private static final double[] MOLES_A = { 0.30, 0.20, 0.10 };
  private static final double[] MW_A = { 0.090, 0.140, 0.250 };
  private static final double[] RHO_A = { 0.74, 0.78, 0.82 };
  private static final double[] TC_A = { 500.0, 560.0, 620.0 };
  private static final double[] PC_A = { 30.0, 26.0, 22.0 };
  private static final double[] OMEGA_A = { 0.30, 0.40, 0.55 };

  // Field B pseudo-component definitions (three cuts, deliberately different from A).
  private static final double[] MOLES_B = { 0.20, 0.15, 0.05 };
  private static final double[] MW_B = { 0.100, 0.150, 0.260 };
  private static final double[] RHO_B = { 0.75, 0.79, 0.83 };
  private static final double[] TC_B = { 520.0, 580.0, 640.0 };
  private static final double[] PC_B = { 31.0, 27.0, 23.0 };
  private static final double[] OMEGA_B = { 0.32, 0.42, 0.57 };

  /**
   * Build a three-cut field fluid with forced normal boiling points so the shared cut grid is deterministic.
   *
   * @param moles per-cut mole amounts
   * @param mw per-cut molar masses (kg/mol)
   * @param rho per-cut normal liquid densities (g/cm3)
   * @param tc per-cut critical temperatures (K)
   * @param pc per-cut critical pressures (bara)
   * @param omega per-cut acentric factors
   * @return the constructed fluid
   */
  private static SystemInterface field(double[] moles, double[] mw, double[] rho, double[] tc, double[] pc,
      double[] omega) {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", METHANE);
    String[] names = { "PCa", "PCb", "PCc" };
    for (int i = 0; i < names.length; i++) {
      fluid.addTBPfraction(names[i], moles[i], mw[i], rho[i]);
      ComponentInterface c = fluid.getComponent(names[i] + "_PC");
      c.setNormalBoilingPoint(NBP[i]);
      c.setTC(tc[i]);
      c.setPC(pc[i]);
      c.setAcentricFactor(omega[i]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Weighted mean using the same definition as the implementation: effective weight per fluid is {@code Wgt(j) * z_i^j}
   * with {@code z_i^j = moles_ij / totalMoles_j}.
   *
   * @param cut cut index
   * @param valuesA field A per-cut values
   * @param valuesB field B per-cut values
   * @param weightA field A weight
   * @param weightB field B weight
   * @return the expected weighted mean for the cut
   */
  private static double weightedMean(int cut, double[] valuesA, double[] valuesB, double weightA, double weightB) {
    double totalA = METHANE + MOLES_A[0] + MOLES_A[1] + MOLES_A[2];
    double totalB = METHANE + MOLES_B[0] + MOLES_B[1] + MOLES_B[2];
    double wA = weightA * MOLES_A[cut] / totalA;
    double wB = weightB * MOLES_B[cut] / totalB;
    return (wA * valuesA[cut] + wB * valuesB[cut]) / (wA + wB);
  }

  /**
   * Expected weighted-mean density per cut: weighted molar mass divided by weighted molar volume (Peneloux basis, Eq.
   * 5.6).
   *
   * @param cut cut index
   * @param weightA field A weight
   * @param weightB field B weight
   * @return the expected reconstructed density for the cut
   */
  private static double weightedDensity(int cut, double weightA, double weightB) {
    double totalA = METHANE + MOLES_A[0] + MOLES_A[1] + MOLES_A[2];
    double totalB = METHANE + MOLES_B[0] + MOLES_B[1] + MOLES_B[2];
    double wA = weightA * MOLES_A[cut] / totalA;
    double wB = weightB * MOLES_B[cut] / totalB;
    double mw = (wA * MW_A[cut] + wB * MW_B[cut]) / (wA + wB);
    double volume = (wA * (MW_A[cut] / RHO_A[cut]) + wB * (MW_B[cut] / RHO_B[cut])) / (wA + wB);
    return mw / volume;
  }

  @Test
  @DisplayName("Common slate lump properties are weighted means across the fields (Eqs. 5.55-5.60)")
  void commonSlateUsesWeightedMeans() {
    SystemInterface a = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    double weightA = 2.0;
    double weightB = 1.0;
    List<SystemInterface> slate = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b),
	new double[] { weightA, weightB }, 3);

    assertEquals(2, slate.size());
    SystemInterface resultA = slate.get(0);
    SystemInterface resultB = slate.get(1);

    for (int i = 0; i < 3; i++) {
      String name = "PC" + (i + 1) + "_PC";
      ComponentInterface ca = resultA.getComponent(name);
      ComponentInterface cb = resultB.getComponent(name);
      assertNotNull(ca, "fluid A should contain shared cut " + name);
      assertNotNull(cb, "fluid B should contain shared cut " + name);

      // The slate must be identical across the two fluids (same EoS basis).
      assertEquals(ca.getMolarMass(), cb.getMolarMass(), 1e-9, "MW shared for " + name);
      assertEquals(ca.getTC(), cb.getTC(), 1e-9, "Tc shared for " + name);
      assertEquals(ca.getPC(), cb.getPC(), 1e-9, "Pc shared for " + name);
      assertEquals(ca.getAcentricFactor(), cb.getAcentricFactor(), 1e-9, "omega shared for " + name);

      // The shared lump equals the cross-field weighted mean (NOT either single field's value).
      assertEquals(weightedMean(i, MW_A, MW_B, weightA, weightB), ca.getMolarMass(), 1e-6, "MW mean for " + name);
      assertEquals(weightedMean(i, TC_A, TC_B, weightA, weightB), ca.getTC(), 1e-4, "Tc mean for " + name);
      assertEquals(weightedMean(i, PC_A, PC_B, weightA, weightB), ca.getPC(), 1e-4, "Pc mean for " + name);
      // Acentric factor is re-derived internally by the EoS, so allow a small absolute tolerance.
      assertEquals(weightedMean(i, OMEGA_A, OMEGA_B, weightA, weightB), ca.getAcentricFactor(), 1e-3,
	  "omega mean for " + name);

      // Density is reconstructed from weighted MW and weighted molar volume (Eq. 5.6).
      assertEquals(weightedDensity(i, weightA, weightB), ca.getNormalLiquidDensity(), 1e-4, "rho mean for " + name);
    }
  }

  @Test
  @DisplayName("Common slate is a genuine average, not a snap to one reference field")
  void commonSlateIsNotSnapToReference() {
    SystemInterface a = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    List<SystemInterface> slate = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b),
	new double[] { 2.0, 1.0 }, 3);
    ComponentInterface lumpA = slate.get(0).getComponent("PC1_PC");

    // Differs from BOTH source fields' first-cut molar mass -> proves cross-field averaging.
    assertTrue(Math.abs(lumpA.getMolarMass() - MW_A[0]) > 1e-4, "MW must differ from field A");
    assertTrue(Math.abs(lumpA.getMolarMass() - MW_B[0]) > 1e-4, "MW must differ from field B");

    // And it lies strictly between the two field values.
    double lo = Math.min(MW_A[0], MW_B[0]);
    double hi = Math.max(MW_A[0], MW_B[0]);
    assertTrue(lumpA.getMolarMass() > lo && lumpA.getMolarMass() < hi, "MW must be between the two fields");
  }

  @Test
  @DisplayName("Fluids stay separate and keep their own lump mole fractions")
  void fluidsRemainSeparate() {
    SystemInterface a = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    List<SystemInterface> slate = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b), null, 3);
    SystemInterface resultA = slate.get(0);
    SystemInterface resultB = slate.get(1);

    // Two distinct fluids returned.
    assertTrue(resultA != resultB);

    // Each keeps its own per-cut mole amount (different between the fields).
    double molesA = resultA.getComponent("PC1_PC").getNumberOfmoles();
    double molesB = resultB.getComponent("PC1_PC").getNumberOfmoles();
    assertEquals(MOLES_A[0], molesA, 1e-9, "fluid A keeps its own PC1 moles");
    assertEquals(MOLES_B[0], molesB, 1e-9, "fluid B keeps its own PC1 moles");
    assertTrue(Math.abs(molesA - molesB) > 1e-6, "fields keep different lump amounts");
  }

  @Test
  @DisplayName("Default (null) weighting is equivalent to equal weights")
  void nullWeightsEqualEqualWeights() {
    SystemInterface a1 = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b1 = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);
    SystemInterface a2 = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b2 = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    List<SystemInterface> nullWeighted = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a1, b1), null,
	3);
    List<SystemInterface> equalWeighted = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a2, b2),
	new double[] { 1.0, 1.0 }, 3);

    for (int i = 0; i < 3; i++) {
      String name = "PC" + (i + 1) + "_PC";
      assertEquals(equalWeighted.get(0).getComponent(name).getMolarMass(),
	  nullWeighted.get(0).getComponent(name).getMolarMass(), 1e-12, "MW equal for " + name);
      assertEquals(equalWeighted.get(0).getComponent(name).getTC(), nullWeighted.get(0).getComponent(name).getTC(),
	  1e-9, "Tc equal for " + name);
    }
  }

  @Test
  @DisplayName("Default overload infers the slate size from the input fluids")
  void inferredSlateSize() {
    SystemInterface a = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    List<SystemInterface> slate = PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b), null);

    assertEquals(2, slate.size());
    // Both fields have three pseudo components -> three shared cuts inferred.
    for (int i = 1; i <= 3; i++) {
      assertNotNull(slate.get(0).getComponent("PC" + i + "_PC"), "expected shared cut PC" + i);
    }
  }

  @Test
  @DisplayName("Invalid arguments are rejected")
  void invalidArguments() {
    SystemInterface a = field(MOLES_A, MW_A, RHO_A, TC_A, PC_A, OMEGA_A);
    SystemInterface b = field(MOLES_B, MW_B, RHO_B, TC_B, PC_B, OMEGA_B);

    assertThrows(IllegalArgumentException.class,
	() -> PseudoComponentCombiner.characterizeToCommonSlate(Arrays.<SystemInterface>asList(), null));
    assertThrows(IllegalArgumentException.class,
	() -> PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b), new double[] { 1.0 }, 3));
    assertThrows(IllegalArgumentException.class,
	() -> PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b), new double[] { 0.0, 0.0 }, 3));
    assertThrows(IllegalArgumentException.class,
	() -> PseudoComponentCombiner.characterizeToCommonSlate(Arrays.asList(a, b), null, 0));
  }
}
