package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Verifies the closure correlations behind the named <code>addTBPfraction_*</code> overloads.
 *
 * <p>
 * The reference values are pure components taken from the shipped COMP.csv, so a failure here means either a
 * correlation coefficient has been altered or the component data underneath it has moved. Both are worth failing for.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TbpClosureTest {
  /** Molar mass tolerance, kg/mol, matching the published accuracy of the correlations. */
  private static final double MOLAR_MASS_TOLERANCE = 0.005;

  @Test
  void testRiaziDaubert1987ReproducesParaffinMolarMass() {
    // n-heptane: Tb 371.6 K, SG 0.690, M 100.2 g/mol.
    double molarMass = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(371.6, 0.690, null);
    assertEquals(0.1002, molarMass, MOLAR_MASS_TOLERANCE);

    // nC10: Tb 447.3 K, SG 0.734, M 142.3 g/mol.
    assertEquals(0.1423, TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(447.3, 0.734, null), 0.006);
  }

  @Test
  void testRiaziDaubert1980RoundTripsBetweenMolarMassAndDensity() {
    double boilingPoint = 447.3;
    double density = 0.734;
    double molarMass = TbpClosure.RIAZI_DAUBERT_1980.calcMolarMass(boilingPoint, density, null);
    double recovered = TbpClosure.RIAZI_DAUBERT_1980.calcDensity(boilingPoint, molarMass, null);
    assertEquals(density, recovered, 1.0e-9);
  }

  @Test
  void testSoreideInversionRecoversItsOwnForwardCorrelation() {
    double molarMass = 0.150;
    double density = 0.780;
    double boilingPoint = TbpClosure.calcBoilingPointSoreide(molarMass, density);
    double recovered = TbpClosure.SOREIDE.calcMolarMass(boilingPoint, density, null);
    assertEquals(molarMass, recovered, 1.0e-6);
  }

  @Test
  void testOnlyRiaziDaubert1980CanBeInvertedForDensity() {
    assertTrue(TbpClosure.RIAZI_DAUBERT_1980.supportsDensityFromMolarMass());
    assertFalse(TbpClosure.RIAZI_DAUBERT_1987.supportsDensityFromMolarMass());
    assertFalse(TbpClosure.SOREIDE.supportsDensityFromMolarMass());
    assertFalse(TbpClosure.TBP_MODEL.supportsDensityFromMolarMass());
  }

  @Test
  void testNonInvertibleClosuresThrowRatherThanReturnAnArbitraryRoot() {
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> TbpClosure.RIAZI_DAUBERT_1987.calcDensity(450.0, 0.150, null));
    assertTrue(thrown.getCause().getMessage().contains("RIAZI_DAUBERT_1980"),
        "the message must name the closure that does work, got: " + thrown.getCause().getMessage());

    assertThrows(RuntimeException.class, () -> TbpClosure.SOREIDE.calcDensity(450.0, 0.150, null));
    assertThrows(RuntimeException.class, () -> TbpClosure.TBP_MODEL.calcDensity(450.0, 0.150, null));
  }

  @Test
  void testRiaziDaubert1987IsNotMonotonicInDensity() {
    // This is the measured reason RIAZI_DAUBERT_1987.calcDensity refuses to run. If the correlation
    // ever becomes monotonic over this range the refusal is no longer justified.
    double boilingPoint = 450.0;
    boolean sawIncrease = false;
    boolean sawDecrease = false;
    double previous = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, 0.60, null);
    for (int i = 1; i <= 20; i++) {
      double density = 0.60 + 0.02 * i;
      double current = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, density, null);
      if (current > previous) {
        sawIncrease = true;
      } else if (current < previous) {
        sawDecrease = true;
      }
      previous = current;
    }
    assertTrue(sawIncrease && sawDecrease,
        "molar mass must turn over within 0.60-1.00 specific gravity for the refusal to be justified");
  }

  @Test
  void testTbpModelClosureRequiresAModel() {
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> TbpClosure.TBP_MODEL.calcMolarMass(450.0, 0.75, null));
    assertTrue(thrown.getCause().getMessage().contains("model"));
  }

  @Test
  void testUnattainableBoilingPointReportsTheAttainableRange() {
    // 1500 K is far above anything the Soreide correlation produces for a petroleum fraction.
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> TbpClosure.SOREIDE.calcMolarMass(1500.0, 0.75, null));
    assertTrue(thrown.getCause().getMessage().contains("Attainable range"),
        "the caller needs to be told what is reachable, got: " + thrown.getCause().getMessage());
  }

  @Test
  void testRiaziDaubert1987WorksForwardEvenThoughItCannotBeInverted() {
    // The distinction the API depends on: addTBPfraction_Tb_Kw uses this closure forward and is
    // fine; addTBPfraction_Mw_Tb would have to invert it and is not.
    double boilingPoint = 447.3;
    double density = 0.734;
    double molarMass = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, density, null);
    assertEquals(0.1423, molarMass, 0.006);

    assertThrows(RuntimeException.class,
        () -> TbpClosure.RIAZI_DAUBERT_1987.calcDensity(boilingPoint, molarMass, null));
  }

  @Test
  void testTwoDensitiesReproduceTheSameMolarMassUnderRiaziDaubert1987() {
    // The concrete reason the inverse is refused: the turning point sits inside the petroleum
    // range, so a molar mass just below the maximum is reached from both sides.
    double boilingPoint = 447.3;
    double turningPointDensity = 4.98308 / (7.78712 - 2.08476e-3 * boilingPoint);
    assertTrue(turningPointDensity > 0.60 && turningPointDensity < 1.00,
        "turning point must fall inside the petroleum range for the ambiguity to be real, got " + turningPointDensity);

    double maximumMolarMass = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, turningPointDensity, null);
    double target = 0.98 * maximumMolarMass;

    Double lightRoot = findRoot(boilingPoint, target, 0.60, turningPointDensity);
    Double heavyRoot = findRoot(boilingPoint, target, turningPointDensity, 1.00);
    assertTrue(lightRoot != null && heavyRoot != null,
        "expected a root on each side of the turning point, got " + lightRoot + " and " + heavyRoot);
    assertTrue(Math.abs(heavyRoot - lightRoot) > 0.02,
        "the two roots must be far enough apart to matter, got " + lightRoot + " and " + heavyRoot);
  }

  /**
   * Bisect for a specific gravity reproducing a target molar mass, or null when none exists.
   *
   * @param boilingPoint normal boiling point in K
   * @param targetMolarMass molar mass to reach, kg/mol
   * @param lower lower specific gravity bound
   * @param upper upper specific gravity bound
   * @return the specific gravity, or null when the target is not bracketed
   */
  private Double findRoot(double boilingPoint, double targetMolarMass, double lower, double upper) {
    double fLower = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, lower, null) - targetMolarMass;
    double fUpper = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, upper, null) - targetMolarMass;
    if (fLower * fUpper > 0.0) {
      return null;
    }
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lower + upper);
      double fMid = TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, mid, null) - targetMolarMass;
      if (fLower * fMid <= 0.0) {
        upper = mid;
      } else {
        lower = mid;
        fLower = fMid;
      }
    }
    return 0.5 * (lower + upper);
  }

  @Test
  void testAccuracyRankingHoldsForParaffins() {
    // RD-1987 is documented as the most accurate closure for molar mass and is therefore the
    // default. If a coefficient is mistyped this ordering is the first thing to break.
    double referenceMolarMass = 0.1423;
    double boilingPoint = 447.3;
    double density = 0.734;

    double errorRd1987 = Math
        .abs(TbpClosure.RIAZI_DAUBERT_1987.calcMolarMass(boilingPoint, density, null) - referenceMolarMass);
    double errorRd1980 = Math
        .abs(TbpClosure.RIAZI_DAUBERT_1980.calcMolarMass(boilingPoint, density, null) - referenceMolarMass);

    assertTrue(errorRd1987 < errorRd1980,
        "RD-1987 error " + errorRd1987 + " should be below RD-1980 error " + errorRd1980);
  }
}
