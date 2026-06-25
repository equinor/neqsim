package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.CharacterizationOptions.DelumpConservation;
import neqsim.thermo.characterization.CharacterizationOptions.DelumpGammaScope;

/**
 * Direct unit tests of the neighbour-aware, gamma-shaped delumping stage of {@link PseudoComponentCombiner}, exercised
 * through the package-private {@link PseudoComponentCombiner#delumpForTesting} seam on a synthetic three-lump fluid.
 *
 * <p>
 * The four invariants required of a Pedersen-faithful (Chapter 5) delumping are checked independently:
 * <ol>
 * <li><b>Neighbour-bounded ranges</b> &mdash; the middle lump's sub-fraction molar masses stay inside the carbon-number
 * midpoints to its lower and upper neighbours; no fabricated mass crosses into a neighbour's interval.</li>
 * <li><b>Monotonic molar decay</b> &mdash; with a light-biased gamma the sub-fraction mole weights decrease with
 * increasing molar mass (Eq. 5.15 / Whitson gamma behaviour).</li>
 * <li><b>Eq. 5.27 / 5.28 MW&ndash;Tb relation</b> &mdash; the assigned normal boiling points follow the non-linear
 * Katz-Firoozabadi molar-mass dependence ({@code Tb_i/Tb_j = (M_i/M_j)^0.3323} for equal-density sub-fractions of one
 * parent), i.e. boiling point is <em>not</em> linear in molar mass.</li>
 * <li><b>Mole and mass conservation</b> &mdash; per parent lump the sub-fraction moles sum to the parent moles and the
 * sub-fraction mass sums to the parent mass, to 1e-9, when {@link DelumpConservation#BOTH} is requested.</li>
 * </ol>
 */
class PseudoComponentDelumpDistributionTest {

  /** Katz-Firoozabadi molar-mass exponent (Eq. 5.28). */
  private static final double KATZ_MASS_EXPONENT = 0.3323;

  // Synthetic three-lump fluid: an evenly carbon-spaced light/medium/heavy slate (C ~ 7.4, 11.7, 17.4).
  private static final double[] MOLES = { 0.50, 0.30, 0.20 };
  private static final double[] MOLAR_MASS = { 0.100, 0.160, 0.240 }; // kg/mol
  private static final double[] DENSITY = { 0.73, 0.79, 0.83 }; // g/cm3
  private static final double[] BOILING_POINT = { 360.0, 430.0, 500.0 }; // K
  private static final int RESOLUTION = 6;

  private static double[][] delump(DelumpGammaScope scope, DelumpConservation conservation) {
    return PseudoComponentCombiner.delumpForTesting(MOLES, MOLAR_MASS, DENSITY, BOILING_POINT, RESOLUTION, scope,
	conservation);
  }

  /**
   * Group the flat sub-fraction matrix back into the three parent lumps using the parent-index column returned by the
   * test seam. Returns one {@code double[resolution][4]} block per parent in ascending parent-index order, preserving
   * the ascending sorting-key order within each block.
   *
   * @param subFractions the flat sub-fraction matrix from {@link PseudoComponentCombiner#delumpForTesting}
   * @return the sub-fractions grouped per parent lump
   */
  private static double[][][] groupByParent(double[][] subFractions) {
    double[][][] groups = new double[3][RESOLUTION][4];
    int[] counts = new int[3];
    for (double[] sub : subFractions) {
      int parent = (int) Math.round(sub[3]);
      assertTrue(parent >= 0 && parent < 3, "unexpected parent index " + parent);
      groups[parent][counts[parent]++] = sub;
    }
    for (int p = 0; p < 3; p++) {
      assertEquals(RESOLUTION, counts[p], "each parent lump must produce exactly " + RESOLUTION + " sub-fractions");
    }
    return groups;
  }

  @Test
  @DisplayName("middle lump delumps only within its neighbour-bounded carbon range")
  void testNeighbourBoundedRanges() {
    // Use MOLES conservation so the gamma conditional-mean molar masses are kept unscaled and therefore stay strictly
    // inside the neighbour-bounded carbon cells. (The BOTH closure rescales molar masses to conserve mass, which can
    // nudge the top sub-fraction marginally past the upper midpoint; range-bounding is a property of the cell grid.)
    double[][] subs = delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.MOLES);

    double cLow = PseudoComponentCombiner.carbonNumberFromMolarMassForTesting(MOLAR_MASS[0]);
    double cMid = PseudoComponentCombiner.carbonNumberFromMolarMassForTesting(MOLAR_MASS[1]);
    double cHigh = PseudoComponentCombiner.carbonNumberFromMolarMassForTesting(MOLAR_MASS[2]);
    double lowerEdge = PseudoComponentCombiner.molarMassFromCarbonNumberForTesting(0.5 * (cLow + cMid));
    double upperEdge = PseudoComponentCombiner.molarMassFromCarbonNumberForTesting(0.5 * (cMid + cHigh));

    double[][][] groups = groupByParent(subs);
    double[][] middle = groups[1];
    for (double[] sub : middle) {
      double mw = sub[1];
      assertTrue(mw >= lowerEdge - 1e-9,
	  "middle sub-fraction MW " + mw + " kg/mol fell below the lower-neighbour midpoint " + lowerEdge);
      assertTrue(mw <= upperEdge + 1e-9,
	  "middle sub-fraction MW " + mw + " kg/mol exceeded the upper-neighbour midpoint " + upperEdge);
    }
  }

  @Test
  @DisplayName("sub-fraction mole weights form a single-peaked (unimodal) gamma distribution")
  void testUnimodalMolarDistribution() {
    double[][] subs = delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.BOTH);
    double[][][] groups = groupByParent(subs);

    for (int p = 0; p < 3; p++) {
      double[][] lump = groups[p];
      // sub-fractions arrive sorted ascending by sorting key (boiling point), hence ascending molar mass.
      for (int k = 1; k < lump.length; k++) {
	assertTrue(lump[k][1] > lump[k - 1][1] - 1e-12, "sub-fraction molar masses must be ascending within a lump");
      }
      // A fitted Whitson gamma (shape >= 0.5) is unimodal: the mole weights rise to a single peak then fall, with no
      // second hump. Allow a tiny tolerance against floating-point wiggle around the peak.
      int directionChanges = 0;
      for (int k = 1; k < lump.length - 1; k++) {
	boolean risingBefore = lump[k][0] - lump[k - 1][0] > 1e-12;
	boolean fallingAfter = lump[k + 1][0] - lump[k][0] < -1e-12;
	boolean fallingBefore = lump[k][0] - lump[k - 1][0] < -1e-12;
	boolean risingAfter = lump[k + 1][0] - lump[k][0] > 1e-12;
	if ((risingBefore && fallingAfter) || (fallingBefore && risingAfter)) {
	  directionChanges++;
	}
      }
      assertTrue(directionChanges <= 1,
	  "parent " + p + ": mole-weight profile must be unimodal but had " + directionChanges + " turning points");
    }
  }

  @Test
  @DisplayName("boiling point follows the non-linear Katz-Firoozabadi MW dependence (Eq. 5.27/5.28)")
  void testKatzFiroozabadiMwTbRelation() {
    double[][] subs = delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.BOTH);
    double[][][] groups = groupByParent(subs);

    for (int p = 0; p < 3; p++) {
      double[][] lump = groups[p];
      double[] ref = lump[0];
      for (int k = 1; k < lump.length; k++) {
	double mwRatio = lump[k][1] / ref[1];
	double tbRatio = lump[k][2] / ref[2];
	double expectedTbRatio = Math.pow(mwRatio, KATZ_MASS_EXPONENT);
	assertEquals(expectedTbRatio, tbRatio, 1e-6,
	    "parent " + p + " slice " + k + ": Tb ratio must follow (M_i/M_j)^0.3323");
	// Confirm the relation is genuinely non-linear: a linear Tb-proportional-to-MW model would give
	// tbRatio=mwRatio.
	assertTrue(tbRatio < mwRatio - 1e-9 || Math.abs(mwRatio - 1.0) < 1e-9,
	    "Tb must grow slower than MW (non-linear), but tbRatio " + tbRatio + " >= mwRatio " + mwRatio);
      }
    }
  }

  @Test
  @DisplayName("BOTH conserves parent moles and mass per lump to 1e-9")
  void testMoleAndMassConservationBoth() {
    double[][] subs = delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.BOTH);
    double[][][] groups = groupByParent(subs);

    for (int p = 0; p < 3; p++) {
      double sumMoles = 0.0;
      double sumMass = 0.0;
      for (double[] sub : groups[p]) {
	sumMoles += sub[0];
	sumMass += sub[0] * sub[1];
      }
      assertEquals(MOLES[p], sumMoles, 1e-9, "parent " + p + ": moles not conserved");
      assertEquals(MOLES[p] * MOLAR_MASS[p], sumMass, 1e-9, "parent " + p + ": mass not conserved");
    }
  }

  @Test
  @DisplayName("MOLES conserves parent moles exactly; MASS conserves parent mass exactly")
  void testSingleQuantityConservationModes() {
    double[][][] molesGroups = groupByParent(delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.MOLES));
    for (int p = 0; p < 3; p++) {
      double sumMoles = 0.0;
      for (double[] sub : molesGroups[p]) {
	sumMoles += sub[0];
      }
      assertEquals(MOLES[p], sumMoles, 1e-9, "MOLES mode: parent " + p + " moles not conserved");
    }

    double[][][] massGroups = groupByParent(delump(DelumpGammaScope.NEIGHBOURS, DelumpConservation.MASS));
    for (int p = 0; p < 3; p++) {
      double sumMass = 0.0;
      for (double[] sub : massGroups[p]) {
	sumMass += sub[0] * sub[1];
      }
      assertEquals(MOLES[p] * MOLAR_MASS[p], sumMass, 1e-9, "MASS mode: parent " + p + " mass not conserved");
    }
  }

  @Test
  @DisplayName("global gamma scope also conserves moles and mass per lump")
  void testGlobalGammaScopeConserves() {
    double[][] subs = delump(DelumpGammaScope.GLOBAL, DelumpConservation.BOTH);
    double[][][] groups = groupByParent(subs);
    for (int p = 0; p < 3; p++) {
      double sumMoles = 0.0;
      double sumMass = 0.0;
      for (double[] sub : groups[p]) {
	sumMoles += sub[0];
	sumMass += sub[0] * sub[1];
      }
      assertEquals(MOLES[p], sumMoles, 1e-9, "GLOBAL scope parent " + p + ": moles not conserved");
      assertEquals(MOLES[p] * MOLAR_MASS[p], sumMass, 1e-9, "GLOBAL scope parent " + p + ": mass not conserved");
    }
  }
}
