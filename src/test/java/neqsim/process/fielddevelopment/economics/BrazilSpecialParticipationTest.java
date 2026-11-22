package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.economics.BrazilSpecialParticipation.Location;

/**
 * Tests for {@link BrazilSpecialParticipation} against Decreto nº 2.705/1998 Art. 22.
 *
 * @author ESOL
 * @version 1.0
 */
public class BrazilSpecialParticipationTest {

  /** Band widths between the five taxable band limits in thousand m3 oe. */
  private static final double[] OFFSETS = {0.0, 450.0, 900.0, 1350.0, 1800.0};

  /**
   * Exemption limits from the decree tables.
   */
  @Test
  public void exemptionLimitsMatchDecree() {
    assertEquals(1350.0, BrazilSpecialParticipation.getExemptionVolume(Location.DEEP_WATER, 1), 1e-12);
    assertEquals(1050.0, BrazilSpecialParticipation.getExemptionVolume(Location.DEEP_WATER, 2), 1e-12);
    assertEquals(750.0, BrazilSpecialParticipation.getExemptionVolume(Location.DEEP_WATER, 3), 1e-12);
    assertEquals(450.0, BrazilSpecialParticipation.getExemptionVolume(Location.DEEP_WATER, 4), 1e-12);
    assertEquals(450.0, BrazilSpecialParticipation.getExemptionVolume(Location.DEEP_WATER, 25), 1e-12);
    assertEquals(900.0, BrazilSpecialParticipation.getExemptionVolume(Location.SHALLOW_WATER, 1), 1e-12);
    assertEquals(300.0, BrazilSpecialParticipation.getExemptionVolume(Location.SHALLOW_WATER, 4), 1e-12);
    assertEquals(450.0, BrazilSpecialParticipation.getExemptionVolume(Location.ONSHORE, 1), 1e-12);
    assertEquals(150.0, BrazilSpecialParticipation.getExemptionVolume(Location.ONSHORE, 4), 1e-12);
  }

  /**
   * The printed deductions make the charge continuous at every band limit, except the one documented misprint (shallow
   * water, third year, 20 % band printed as 775 instead of 725).
   */
  @Test
  public void chargeIsContinuousAcrossAllBandLimits() {
    for (Location loc : Location.values()) {
      for (int year = 1; year <= 4; year++) {
        double t0 = BrazilSpecialParticipation.getExemptionVolume(loc, year);
        for (double offset : OFFSETS) {
          double limit = t0 + offset;
          if (loc == Location.SHALLOW_WATER && year == 3 && (limit == 950.0 || limit == 1400.0)) {
            continue;
          }
          double below = BrazilSpecialParticipation.getEffectiveRate(loc, year, limit);
          double above = BrazilSpecialParticipation.getEffectiveRate(loc, year, limit + 1e-6);
          assertEquals(below, above, 1e-8, loc + " year " + year + " limit " + limit);
        }
      }
    }
  }

  /**
   * The published shallow-water third-year 20 % band deduction (775) is kept literally.
   */
  @Test
  public void shallowWaterThirdYearUsesPublishedDeduction() {
    double r = BrazilSpecialParticipation.getEffectiveRate(Location.SHALLOW_WATER, 3, 1000.0);
    assertEquals(0.20 * (1.0 - 775.0 / 1000.0), r, 1e-12);
  }

  /**
   * Deep-water first-year value checked by hand: VPF 2,000 is in the 20 % band with D = 1,575.
   */
  @Test
  public void deepWaterFirstYearHandCalculation() {
    double sp = BrazilSpecialParticipation.calculateQuarter(Location.DEEP_WATER, 1, 1.0e9, 2000.0);
    assertEquals(0.20 * 1.0e9 * (1.0 - 1575.0 / 2000.0), sp, 1e-3);
    assertEquals(0.20, BrazilSpecialParticipation.getMarginalRate(Location.DEEP_WATER, 1, 2000.0), 1e-12);
    assertEquals(0.0, BrazilSpecialParticipation.calculateQuarter(Location.DEEP_WATER, 1, 1.0e9, 1350.0), 1e-12);
  }

  /**
   * Effective rate rises with volume and approaches the 40 % top rate.
   */
  @Test
  public void effectiveRateIsMonotonicAndBounded() {
    double prev = 0.0;
    for (double v = 0.0; v <= 50000.0; v += 25.0) {
      double r = BrazilSpecialParticipation.getEffectiveRate(Location.DEEP_WATER, 4, v);
      assertTrue(r >= prev - 1e-12);
      assertTrue(r < 0.40);
      prev = r;
    }
    assertTrue(prev > 0.38);
  }

  /**
   * Negative net revenue is offset against later quarters (Art. 22 §6).
   */
  @Test
  public void negativeRevenueCarriesForward() {
    int[] years = {1, 1, 1};
    double[] rlp = {-300.0, 200.0, 500.0};
    double[] vpf = {2000.0, 2000.0, 2000.0};
    double[] sp = BrazilSpecialParticipation.calculateQuarters(Location.DEEP_WATER, years, rlp, vpf);
    double rate = BrazilSpecialParticipation.getEffectiveRate(Location.DEEP_WATER, 1, 2000.0);
    assertEquals(0.0, sp[0], 1e-12);
    assertEquals(0.0, sp[1], 1e-12);
    assertEquals(400.0 * rate, sp[2], 1e-9);
  }

  /**
   * Annual helper equals four equal quarters, and a deep-water 150 kbopd plateau pays a material charge.
   */
  @Test
  public void annualHelperAndPlateauExample() {
    double oilSm3PerYear = 150000.0 * 0.158987 * 365.25;
    double vpf = BrazilSpecialParticipation.toThousandM3OilEquivalent(oilSm3PerYear, 0.0, 1000.0);
    double[] sp = BrazilSpecialParticipation.calculateAnnualFromEqualQuarters(Location.DEEP_WATER,
        new double[] {4.0e9, 4.0e9}, new double[] {vpf, vpf});
    double q = BrazilSpecialParticipation.calculateQuarter(Location.DEEP_WATER, 1, 1.0e9, vpf / 4.0);
    assertEquals(4.0 * q, sp[0], 1e-3);
    // ~2,177 thousand m3 oe per quarter lies in the 35 % band after year 3 (1,800 to 2,250): 0.35 x (1 - 1028.6/2177)
    double rateYear4 = BrazilSpecialParticipation.getEffectiveRate(Location.DEEP_WATER, 4, vpf / 4.0);
    assertEquals(0.35, BrazilSpecialParticipation.getMarginalRate(Location.DEEP_WATER, 4, vpf / 4.0), 1e-12);
    assertTrue(rateYear4 > 0.17 && rateYear4 < 0.20, "effective rate " + rateYear4);
  }

  /**
   * Location selection from water depth and argument checks.
   */
  @Test
  public void locationAndValidation() {
    assertEquals(Location.SHALLOW_WATER, Location.fromWaterDepth(400.0));
    assertEquals(Location.DEEP_WATER, Location.fromWaterDepth(2420.0));
    assertThrows(IllegalArgumentException.class, () -> Location.fromWaterDepth(-1.0));
    assertThrows(IllegalArgumentException.class,
        () -> BrazilSpecialParticipation.getEffectiveRate(Location.DEEP_WATER, 0, 100.0));
    assertThrows(IllegalArgumentException.class,
        () -> BrazilSpecialParticipation.toThousandM3OilEquivalent(1.0, 1.0, 0.0));
  }
}
