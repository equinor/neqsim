package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;

/**
 * Statutory Brazilian Special Participation (participação especial) for concession contracts.
 *
 * <p>
 * Implements the progressive quarterly tables of Decreto nº 2.705/1998, Art. 22, which applies to fields in a
 * concession area with large production volume. The charge is computed per field and per quarter from the net
 * production revenue of the quarter (RLP, receita líquida da produção trimestral, after the deductions of Lei nº
 * 9.478/1997 Art. 50 §1) and the fiscalised quarterly production volume (VPF, thousand cubic metres of oil equivalent).
 * The rate band depends on the location of the field, the year of production counted from the start of production, and
 * VPF.
 * </p>
 *
 * <p>
 * Within a band the decree gives a rate and a "parcela a deduzir" written as {@code D x RLP / VPF}. The charge is
 * therefore {@code SP = rate x (RLP - D x RLP / VPF) = rate x RLP x (1 - D / VPF)}. The tabulated deductions make the
 * charge continuous across band limits, which the unit tests verify. Negative net revenue in a quarter can be offset
 * against later quarters of the same field (Art. 22 §6).
 * </p>
 *
 * <p>
 * The values are those printed in the decree. One printed value breaks continuity: the shallow-water third-year 20 %
 * band deduction is 775, whereas 725 would make the charge continuous at 950. The published value is kept; confirm the
 * applicable value with ANP for a shallow-water field in its third production year.
 * </p>
 *
 * <p>
 * Out of scope: computing RLP itself (the caller supplies it), ANP reference prices, unitised fields (Art. 23) and any
 * contract-specific terms. Production-sharing (pre-salt PSC) contracts do not pay Special Participation; use
 * {@link PscBidEconomics} and {@link ProfitOilShareTable} for those.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BrazilSpecialParticipation implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Marginal rates of the five taxable bands. */
  private static final double[] RATES = {0.10, 0.20, 0.30, 0.35, 0.40};

  /**
   * Lower limits of the five taxable bands in thousand m3 oil equivalent per quarter, indexed by location then
   * production-year group (first, second, third, after third year).
   */
  private static final double[][][] THRESHOLDS = {
      {{450, 900, 1350, 1800, 2250}, {350, 800, 1250, 1700, 2150}, {250, 700, 1150, 1600, 2050},
          {150, 600, 1050, 1500, 1950}},
      {{900, 1350, 1800, 2250, 2700}, {750, 1200, 1650, 2100, 2550}, {500, 950, 1400, 1850, 2300},
          {300, 750, 1200, 1650, 2100}},
      {{1350, 1800, 2250, 2700, 3150}, {1050, 1500, 1950, 2400, 2850}, {750, 1200, 1650, 2100, 2550},
          {450, 900, 1350, 1800, 2250}}};

  /**
   * Deduction factors D of each band in thousand m3 oil equivalent, as printed in the decree, indexed like
   * {@link #THRESHOLDS}.
   */
  private static final double[][][] DEDUCTIONS = {
      {{450, 675, 900, 360 / 0.35, 1181.25}, {350, 575, 800, 325 / 0.35, 1081.25}, {250, 475, 700, 290 / 0.35, 981.25},
          {150, 375, 600, 255 / 0.35, 881.25}},
      {{900, 1125, 1350, 517.5 / 0.35, 1631.25}, {750, 975, 1200, 465 / 0.35, 1481.25},
          {500, 775, 950, 377.5 / 0.35, 1231.25}, {300, 525, 750, 307.5 / 0.35, 1031.25}},
      {{1350, 1575, 1800, 675 / 0.35, 2081.25}, {1050, 1275, 1500, 570 / 0.35, 1781.25},
          {750, 975, 1200, 465 / 0.35, 1481.25}, {450, 675, 900, 360 / 0.35, 1181.25}}};

  /** Water depth separating the shallow and deep offshore tables in metres. */
  public static final double DEEP_WATER_LIMIT_M = 400.0;

  /**
   * Location class of the field, which selects the table (Art. 22 items I, II and III).
   *
   * @author ESOL
   * @version 1.0
   */
  public enum Location {
    /** Onshore, lakes, rivers and river or lake islands (item I). */
    ONSHORE,
    /** Continental shelf with water depth up to 400 m (item II). */
    SHALLOW_WATER,
    /** Continental shelf with water depth above 400 m (item III). */
    DEEP_WATER;

    /**
     * Selects the offshore location class from the water depth.
     *
     * @param waterDepthM water depth in metres, positive downwards
     * @return {@link #SHALLOW_WATER} up to 400 m, otherwise {@link #DEEP_WATER}
     * @throws IllegalArgumentException if the depth is negative or not finite
     */
    public static Location fromWaterDepth(double waterDepthM) {
      if (!(waterDepthM >= 0.0) || Double.isInfinite(waterDepthM)) {
        throw new IllegalArgumentException("water depth must be a finite value >= 0 m");
      }
      return waterDepthM > DEEP_WATER_LIMIT_M ? DEEP_WATER : SHALLOW_WATER;
    }
  }

  /** Private constructor; the class only has static methods. */
  private BrazilSpecialParticipation() {
  }

  /**
   * Maps a production year to the table group of the decree.
   *
   * @param productionYear year of production counted from the start of production, 1 for the first year
   * @return 0, 1, 2 for the first three years and 3 for later years
   * @throws IllegalArgumentException if the production year is below 1
   */
  private static int yearGroup(int productionYear) {
    if (productionYear < 1) {
      throw new IllegalArgumentException("productionYear must be >= 1");
    }
    return Math.min(productionYear, 4) - 1;
  }

  /**
   * Finds the band for a fiscalised volume.
   *
   * @param location location class
   * @param productionYear year of production, 1 for the first year
   * @param vpfThousandM3oe fiscalised quarterly volume in thousand m3 oil equivalent
   * @return band index 0 to 4, or -1 when the volume is exempt
   */
  private static int band(Location location, int productionYear, double vpfThousandM3oe) {
    double[] limits = THRESHOLDS[location.ordinal()][yearGroup(productionYear)];
    int found = -1;
    for (int i = 0; i < limits.length; i++) {
      if (vpfThousandM3oe > limits[i]) {
        found = i;
      }
    }
    return found;
  }

  /**
   * Returns the exemption limit of a table.
   *
   * @param location location class
   * @param productionYear year of production, 1 for the first year
   * @return quarterly volume in thousand m3 oil equivalent up to which no Special Participation is due
   */
  public static double getExemptionVolume(Location location, int productionYear) {
    return THRESHOLDS[location.ordinal()][yearGroup(productionYear)][0];
  }

  /**
   * Returns the marginal rate of the band holding a volume.
   *
   * @param location location class
   * @param productionYear year of production, 1 for the first year
   * @param vpfThousandM3oe fiscalised quarterly volume in thousand m3 oil equivalent
   * @return marginal rate as a fraction, 0 when exempt
   */
  public static double getMarginalRate(Location location, int productionYear, double vpfThousandM3oe) {
    int b = band(location, productionYear, vpfThousandM3oe);
    return b < 0 ? 0.0 : RATES[b];
  }

  /**
   * Returns the effective rate SP / RLP for a quarter with positive net revenue.
   *
   * @param location location class
   * @param productionYear year of production, 1 for the first year
   * @param vpfThousandM3oe fiscalised quarterly volume in thousand m3 oil equivalent
   * @return effective rate as a fraction of net revenue, between 0 and 0.40
   * @throws IllegalArgumentException if the volume is negative or not finite
   */
  public static double getEffectiveRate(Location location, int productionYear, double vpfThousandM3oe) {
    if (!(vpfThousandM3oe >= 0.0) || Double.isInfinite(vpfThousandM3oe)) {
      throw new IllegalArgumentException("vpf must be a finite value >= 0");
    }
    int b = band(location, productionYear, vpfThousandM3oe);
    if (b < 0) {
      return 0.0;
    }
    double d = DEDUCTIONS[location.ordinal()][yearGroup(productionYear)][b];
    return RATES[b] * (1.0 - d / vpfThousandM3oe);
  }

  /**
   * Computes the Special Participation of one quarter without loss carry-forward.
   *
   * @param location location class
   * @param productionYear year of production, 1 for the first year
   * @param netRevenue net production revenue of the quarter (RLP) in any currency
   * @param vpfThousandM3oe fiscalised quarterly volume in thousand m3 oil equivalent
   * @return Special Participation in the currency of the revenue, 0 when the revenue is not positive
   */
  public static double calculateQuarter(Location location, int productionYear, double netRevenue,
      double vpfThousandM3oe) {
    if (netRevenue <= 0.0) {
      return 0.0;
    }
    return netRevenue * getEffectiveRate(location, productionYear, vpfThousandM3oe);
  }

  /**
   * Computes Special Participation for a sequence of quarters of one field, offsetting negative net revenue against
   * later quarters (Art. 22 §6).
   *
   * @param location location class
   * @param productionYears year of production of each quarter, 1 for the first four quarters after start-up
   * @param netRevenue net production revenue (RLP) of each quarter
   * @param vpfThousandM3oe fiscalised volume of each quarter in thousand m3 oil equivalent
   * @return Special Participation per quarter
   * @throws IllegalArgumentException if the arrays differ in length
   */
  public static double[] calculateQuarters(Location location, int[] productionYears, double[] netRevenue,
      double[] vpfThousandM3oe) {
    if (productionYears.length != netRevenue.length || netRevenue.length != vpfThousandM3oe.length) {
      throw new IllegalArgumentException("productionYears, netRevenue and vpf must have the same length");
    }
    double[] sp = new double[netRevenue.length];
    double lossPool = 0.0;
    for (int i = 0; i < netRevenue.length; i++) {
      double base = netRevenue[i] - lossPool;
      if (base <= 0.0) {
        lossPool = -base;
        continue;
      }
      lossPool = 0.0;
      sp[i] = calculateQuarter(location, productionYears[i], base, vpfThousandM3oe[i]);
    }
    return sp;
  }

  /**
   * Computes annual Special Participation from annual totals by splitting each year into four equal quarters.
   *
   * <p>
   * This is a screening convenience for annual cash-flow models. Year index 0 is the first year of production. A
   * seasonal or ramping profile within a year gives a different (usually higher) charge than equal quarters because the
   * tables are progressive in the quarterly volume.
   * </p>
   *
   * @param location location class
   * @param annualNetRevenue net production revenue (RLP) per production year
   * @param annualVolumeThousandM3oe fiscalised volume per production year in thousand m3 oil equivalent
   * @return Special Participation per production year
   * @throws IllegalArgumentException if the arrays differ in length
   */
  public static double[] calculateAnnualFromEqualQuarters(Location location, double[] annualNetRevenue,
      double[] annualVolumeThousandM3oe) {
    if (annualNetRevenue.length != annualVolumeThousandM3oe.length) {
      throw new IllegalArgumentException("annualNetRevenue and annualVolume must have the same length");
    }
    int n = annualNetRevenue.length;
    int[] years = new int[4 * n];
    double[] rlp = new double[4 * n];
    double[] vpf = new double[4 * n];
    for (int y = 0; y < n; y++) {
      for (int q = 0; q < 4; q++) {
        years[4 * y + q] = y + 1;
        rlp[4 * y + q] = annualNetRevenue[y] / 4.0;
        vpf[4 * y + q] = annualVolumeThousandM3oe[y] / 4.0;
      }
    }
    double[] quarterly = calculateQuarters(location, years, rlp, vpf);
    double[] annual = new double[n];
    for (int i = 0; i < quarterly.length; i++) {
      annual[i / 4] += quarterly[i];
    }
    return annual;
  }

  /**
   * Converts oil and gas volumes to oil equivalent.
   *
   * @param oilSm3 oil volume in standard cubic metres
   * @param gasSm3 fiscalised gas volume in standard cubic metres
   * @param gasSm3PerOilEquivalentM3 gas volume counted as one cubic metre of oil equivalent; 1000 is the convention
   * commonly used by ANP and must be confirmed for the contract in question
   * @return oil equivalent in thousand cubic metres
   * @throws IllegalArgumentException if the conversion factor is not positive
   */
  public static double toThousandM3OilEquivalent(double oilSm3, double gasSm3, double gasSm3PerOilEquivalentM3) {
    if (!(gasSm3PerOilEquivalentM3 > 0.0)) {
      throw new IllegalArgumentException("gas conversion factor must be > 0");
    }
    return (oilSm3 + gasSm3 / gasSm3PerOilEquivalentM3) / 1000.0;
  }
}
