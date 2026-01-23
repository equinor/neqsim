package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates production decline profiles using Arps decline curve analysis.
 *
 * <p>
 * This class implements the classic Arps decline curve equations (exponential, hyperbolic, and
 * harmonic) for forecasting oil and gas production over the field life. These empirical models are
 * widely used in reservoir engineering for production forecasting and reserves estimation.
 * </p>
 *
 * <h2>Decline Curve Types</h2>
 * <ul>
 * <li><b>Exponential (b=0)</b>: Constant percentage decline per period. Common for solution-gas
 * drive reservoirs and boundary-dominated flow.</li>
 * <li><b>Hyperbolic (0&lt;b&lt;1)</b>: Declining percentage decline over time. Most common for
 * multiphase flow and heterogeneous reservoirs.</li>
 * <li><b>Harmonic (b=1)</b>: Linear decline rate. Represents gravity drainage or some water drive
 * reservoirs.</li>
 * </ul>
 *
 * <h2>Arps Equations</h2>
 * <p>
 * The general Arps hyperbolic equation:
 * </p>
 * 
 * <pre>
 * q(t) = qi / (1 + b * Di * t) ^ (1 / b)
 * </pre>
 * 
 * <p>
 * Where:
 * </p>
 * <ul>
 * <li><b>q(t)</b> = production rate at time t</li>
 * <li><b>qi</b> = initial production rate</li>
 * <li><b>Di</b> = initial decline rate (fraction per time period)</li>
 * <li><b>b</b> = decline exponent (0 = exponential, 0&lt;b&lt;1 = hyperbolic, 1 = harmonic)</li>
 * <li><b>t</b> = time</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create generator
 * ProductionProfileGenerator generator = new ProductionProfileGenerator();
 * 
 * // Generate exponential decline (typical for gas wells)
 * Map<Integer, Double> gasProfile = generator.generateExponentialDecline(10.0e6, // Initial rate:
 *                                                                                // 10 MSm3/d
 *     0.15, // 15% annual decline
 *     2026, // Start year
 *     20, // 20 years
 *     0.5e6 // Economic limit: 0.5 MSm3/d
 * );
 * 
 * // Generate hyperbolic decline (typical for oil wells)
 * Map<Integer, Double> oilProfile = generator.generateHyperbolicDecline(15000, // Initial rate:
 *                                                                              // 15,000 bbl/d
 *     0.20, // 20% initial decline
 *     0.5, // b-factor = 0.5
 *     2026, // Start year
 *     25, // 25 years
 *     100 // Economic limit: 100 bbl/d
 * );
 * 
 * // Use with CashFlowEngine
 * CashFlowEngine engine = new CashFlowEngine("NO");
 * engine.setProductionProfile(oilProfile, gasProfile, null);
 * }</pre>
 *
 * <h2>Plateau Period</h2>
 * <p>
 * Many fields have a plateau period before decline begins. Use the methods with plateau parameters
 * to model this behavior:
 * </p>
 * 
 * <pre>{@code
 * // 3 years plateau, then exponential decline
 * Map<Integer, Double> profile = generator.generateWithPlateau(10.0e6, // Plateau rate
 *     3, // Plateau years
 *     0.12, // 12% decline after plateau
 *     DeclineType.EXPONENTIAL, 2026, // Start year
 *     20 // Total years
 * );
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see CashFlowEngine
 */
public class ProductionProfileGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // ENUMS
  // ============================================================================

  /**
   * Decline curve type enumeration.
   */
  public enum DeclineType {
    /**
     * Exponential decline (b=0): q(t) = qi * e^(-Di*t).
     */
    EXPONENTIAL,

    /**
     * Hyperbolic decline (0&lt;b&lt;1): q(t) = qi / (1 + b*Di*t)^(1/b).
     */
    HYPERBOLIC,

    /**
     * Harmonic decline (b=1): q(t) = qi / (1 + Di*t).
     */
    HARMONIC
  }

  // ============================================================================
  // CONSTANTS
  // ============================================================================

  /** Days per year for rate conversions. */
  private static final double DAYS_PER_YEAR = 365.25;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new production profile generator.
   */
  public ProductionProfileGenerator() {
    // Default constructor
  }

  // ============================================================================
  // EXPONENTIAL DECLINE
  // ============================================================================

  /**
   * Generates an exponential decline production profile.
   *
   * <p>
   * Exponential decline assumes a constant percentage decline per time period:
   * </p>
   * 
   * <pre>
   * q(t) = qi * e ^ (-Di * t)
   * </pre>
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param annualDeclineRate annual decline rate as fraction (e.g., 0.15 for 15%)
   * @param startYear first year of production
   * @param maxYears maximum years to generate
   * @param economicLimit minimum economic rate (stops when rate falls below)
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateExponentialDecline(double initialRatePerDay,
      double annualDeclineRate, int startYear, int maxYears, double economicLimit) {
    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();

    for (int i = 0; i < maxYears; i++) {
      int year = startYear + i;
      double t = i; // Time in years

      // Exponential decline: q = qi * e^(-Di*t)
      double ratePerDay = initialRatePerDay * Math.exp(-annualDeclineRate * t);

      if (ratePerDay < economicLimit) {
        break;
      }

      double annualVolume = ratePerDay * DAYS_PER_YEAR;
      profile.put(year, annualVolume);
    }

    return profile;
  }

  /**
   * Generates exponential decline with default economic limit of 0.
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param annualDeclineRate annual decline rate as fraction
   * @param startYear first year of production
   * @param years number of years to generate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateExponentialDecline(double initialRatePerDay,
      double annualDeclineRate, int startYear, int years) {
    return generateExponentialDecline(initialRatePerDay, annualDeclineRate, startYear, years, 0.0);
  }

  // ============================================================================
  // HYPERBOLIC DECLINE
  // ============================================================================

  /**
   * Generates a hyperbolic decline production profile.
   *
   * <p>
   * Hyperbolic decline uses the Arps equation with 0 &lt; b &lt; 1:
   * </p>
   * 
   * <pre>
   * q(t) = qi / (1 + b * Di * t) ^ (1 / b)
   * </pre>
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param initialDeclineRate initial decline rate as fraction (e.g., 0.20 for 20%)
   * @param bFactor Arps b-factor (0 &lt; b &lt; 1, typically 0.3-0.7)
   * @param startYear first year of production
   * @param maxYears maximum years to generate
   * @param economicLimit minimum economic rate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateHyperbolicDecline(double initialRatePerDay,
      double initialDeclineRate, double bFactor, int startYear, int maxYears,
      double economicLimit) {
    // Validate b-factor
    if (bFactor <= 0 || bFactor >= 1) {
      throw new IllegalArgumentException("b-factor must be between 0 and 1 (exclusive)");
    }

    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();

    for (int i = 0; i < maxYears; i++) {
      int year = startYear + i;
      double t = i; // Time in years

      // Hyperbolic decline: q = qi / (1 + b*Di*t)^(1/b)
      double denominator = Math.pow(1 + bFactor * initialDeclineRate * t, 1.0 / bFactor);
      double ratePerDay = initialRatePerDay / denominator;

      if (ratePerDay < economicLimit) {
        break;
      }

      double annualVolume = ratePerDay * DAYS_PER_YEAR;
      profile.put(year, annualVolume);
    }

    return profile;
  }

  /**
   * Generates hyperbolic decline with default economic limit.
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param initialDeclineRate initial decline rate as fraction
   * @param bFactor Arps b-factor (0 &lt; b &lt; 1)
   * @param startYear first year of production
   * @param years number of years to generate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateHyperbolicDecline(double initialRatePerDay,
      double initialDeclineRate, double bFactor, int startYear, int years) {
    return generateHyperbolicDecline(initialRatePerDay, initialDeclineRate, bFactor, startYear,
        years, 0.0);
  }

  // ============================================================================
  // HARMONIC DECLINE
  // ============================================================================

  /**
   * Generates a harmonic decline production profile.
   *
   * <p>
   * Harmonic decline is a special case of hyperbolic decline with b = 1:
   * </p>
   * 
   * <pre>
   * q(t) = qi / (1 + Di * t)
   * </pre>
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param initialDeclineRate initial decline rate as fraction
   * @param startYear first year of production
   * @param maxYears maximum years to generate
   * @param economicLimit minimum economic rate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateHarmonicDecline(double initialRatePerDay,
      double initialDeclineRate, int startYear, int maxYears, double economicLimit) {
    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();

    for (int i = 0; i < maxYears; i++) {
      int year = startYear + i;
      double t = i; // Time in years

      // Harmonic decline: q = qi / (1 + Di*t)
      double ratePerDay = initialRatePerDay / (1 + initialDeclineRate * t);

      if (ratePerDay < economicLimit) {
        break;
      }

      double annualVolume = ratePerDay * DAYS_PER_YEAR;
      profile.put(year, annualVolume);
    }

    return profile;
  }

  /**
   * Generates harmonic decline with default economic limit.
   *
   * @param initialRatePerDay initial production rate (volume per day)
   * @param initialDeclineRate initial decline rate as fraction
   * @param startYear first year of production
   * @param years number of years to generate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateHarmonicDecline(double initialRatePerDay,
      double initialDeclineRate, int startYear, int years) {
    return generateHarmonicDecline(initialRatePerDay, initialDeclineRate, startYear, years, 0.0);
  }

  // ============================================================================
  // PLATEAU + DECLINE
  // ============================================================================

  /**
   * Generates a production profile with an initial plateau period followed by decline.
   *
   * @param plateauRatePerDay plateau production rate (volume per day)
   * @param plateauYears number of years at plateau before decline begins
   * @param declineRate decline rate after plateau
   * @param declineType type of decline (exponential, hyperbolic, harmonic)
   * @param startYear first year of production
   * @param totalYears total years including plateau
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateWithPlateau(double plateauRatePerDay, int plateauYears,
      double declineRate, DeclineType declineType, int startYear, int totalYears) {
    return generateWithPlateau(plateauRatePerDay, plateauYears, declineRate, 0.5, declineType,
        startYear, totalYears, 0.0);
  }

  /**
   * Generates a production profile with plateau and configurable decline parameters.
   *
   * @param plateauRatePerDay plateau production rate (volume per day)
   * @param plateauYears number of years at plateau
   * @param declineRate decline rate after plateau
   * @param bFactor b-factor for hyperbolic decline (ignored for other types)
   * @param declineType type of decline
   * @param startYear first year of production
   * @param totalYears total years including plateau
   * @param economicLimit minimum economic rate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateWithPlateau(double plateauRatePerDay, int plateauYears,
      double declineRate, double bFactor, DeclineType declineType, int startYear, int totalYears,
      double economicLimit) {
    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();

    // Plateau period
    double annualPlateauVolume = plateauRatePerDay * DAYS_PER_YEAR;
    for (int i = 0; i < plateauYears && i < totalYears; i++) {
      profile.put(startYear + i, annualPlateauVolume);
    }

    // Decline period
    int declineStartYear = startYear + plateauYears;
    int declineYears = totalYears - plateauYears;

    if (declineYears > 0) {
      Map<Integer, Double> declineProfile;
      switch (declineType) {
        case EXPONENTIAL:
          declineProfile = generateExponentialDecline(plateauRatePerDay, declineRate,
              declineStartYear, declineYears, economicLimit);
          break;
        case HYPERBOLIC:
          declineProfile = generateHyperbolicDecline(plateauRatePerDay, declineRate, bFactor,
              declineStartYear, declineYears, economicLimit);
          break;
        case HARMONIC:
          declineProfile = generateHarmonicDecline(plateauRatePerDay, declineRate, declineStartYear,
              declineYears, economicLimit);
          break;
        default:
          declineProfile = generateExponentialDecline(plateauRatePerDay, declineRate,
              declineStartYear, declineYears, economicLimit);
      }
      profile.putAll(declineProfile);
    }

    return profile;
  }

  // ============================================================================
  // RAMP-UP + PLATEAU + DECLINE
  // ============================================================================

  /**
   * Generates a production profile with ramp-up, plateau, and decline phases.
   *
   * <p>
   * This is the most realistic model for new field developments, with:
   * </p>
   * <ul>
   * <li><b>Ramp-up</b>: Linear increase from first production to plateau</li>
   * <li><b>Plateau</b>: Constant rate at peak production</li>
   * <li><b>Decline</b>: Arps decline curve after plateau</li>
   * </ul>
   *
   * @param peakRatePerDay peak/plateau production rate (volume per day)
   * @param rampUpYears years to ramp from 0 to plateau
   * @param plateauYears years at plateau rate
   * @param declineRate decline rate after plateau
   * @param declineType type of decline curve
   * @param startYear first year of production
   * @param totalYears total project years
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateFullProfile(double peakRatePerDay, int rampUpYears,
      int plateauYears, double declineRate, DeclineType declineType, int startYear,
      int totalYears) {
    return generateFullProfile(peakRatePerDay, rampUpYears, plateauYears, declineRate, 0.5,
        declineType, startYear, totalYears, 0.0);
  }

  /**
   * Generates a full production profile with all configurable parameters.
   *
   * @param peakRatePerDay peak/plateau production rate (volume per day)
   * @param rampUpYears years to ramp from 0 to plateau
   * @param plateauYears years at plateau rate
   * @param declineRate decline rate after plateau
   * @param bFactor b-factor for hyperbolic decline
   * @param declineType type of decline curve
   * @param startYear first year of production
   * @param totalYears total project years
   * @param economicLimit minimum economic rate
   * @return map of year to annual production volume
   */
  public Map<Integer, Double> generateFullProfile(double peakRatePerDay, int rampUpYears,
      int plateauYears, double declineRate, double bFactor, DeclineType declineType, int startYear,
      int totalYears, double economicLimit) {
    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();
    int yearIndex = 0;

    // Ramp-up period (linear increase)
    for (int i = 0; i < rampUpYears && yearIndex < totalYears; i++) {
      double fraction = (i + 1.0) / rampUpYears;
      double ratePerDay = peakRatePerDay * fraction;
      double annualVolume = ratePerDay * DAYS_PER_YEAR;
      profile.put(startYear + yearIndex, annualVolume);
      yearIndex++;
    }

    // Plateau period
    double annualPlateauVolume = peakRatePerDay * DAYS_PER_YEAR;
    for (int i = 0; i < plateauYears && yearIndex < totalYears; i++) {
      profile.put(startYear + yearIndex, annualPlateauVolume);
      yearIndex++;
    }

    // Decline period
    int declineStartYear = startYear + yearIndex;
    int remainingYears = totalYears - yearIndex;

    if (remainingYears > 0) {
      Map<Integer, Double> declineProfile;
      switch (declineType) {
        case EXPONENTIAL:
          declineProfile = generateExponentialDecline(peakRatePerDay, declineRate, declineStartYear,
              remainingYears, economicLimit);
          break;
        case HYPERBOLIC:
          declineProfile = generateHyperbolicDecline(peakRatePerDay, declineRate, bFactor,
              declineStartYear, remainingYears, economicLimit);
          break;
        case HARMONIC:
          declineProfile = generateHarmonicDecline(peakRatePerDay, declineRate, declineStartYear,
              remainingYears, economicLimit);
          break;
        default:
          declineProfile = generateExponentialDecline(peakRatePerDay, declineRate, declineStartYear,
              remainingYears, economicLimit);
      }
      profile.putAll(declineProfile);
    }

    return profile;
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Calculates cumulative production from a profile.
   *
   * @param profile production profile (year to annual volume)
   * @return total cumulative production
   */
  public static double calculateCumulativeProduction(Map<Integer, Double> profile) {
    double cumulative = 0.0;
    for (Double volume : profile.values()) {
      cumulative += volume;
    }
    return cumulative;
  }

  /**
   * Calculates the estimated ultimate recovery (EUR) using Arps equations.
   *
   * <p>
   * For exponential decline, EUR = qi / Di. For hyperbolic/harmonic, the calculation is more
   * complex and depends on economic limit.
   * </p>
   *
   * @param initialRatePerDay initial production rate
   * @param declineRate initial decline rate
   * @param declineType type of decline
   * @param bFactor b-factor (for hyperbolic)
   * @return estimated ultimate recovery
   */
  public static double calculateEUR(double initialRatePerDay, double declineRate,
      DeclineType declineType, double bFactor) {
    double qi = initialRatePerDay * DAYS_PER_YEAR; // Annual rate

    switch (declineType) {
      case EXPONENTIAL:
        // EUR = qi / Di (infinite time)
        return qi / declineRate;

      case HARMONIC:
        // Harmonic decline has infinite EUR (asymptotic)
        // Return a practical estimate (10x initial annual)
        return qi * 10;

      case HYPERBOLIC:
        // Hyperbolic EUR depends on abandonment, use practical estimate
        // EUR ≈ qi / ((1-b) * Di) for 0 < b < 1
        if (bFactor < 1) {
          return qi / ((1 - bFactor) * declineRate);
        }
        return qi * 10;

      default:
        return qi / declineRate;
    }
  }

  /**
   * Scales a production profile by a factor.
   *
   * @param profile original profile
   * @param scaleFactor multiplier for all values
   * @return scaled profile
   */
  public static Map<Integer, Double> scaleProfile(Map<Integer, Double> profile,
      double scaleFactor) {
    Map<Integer, Double> scaled = new LinkedHashMap<Integer, Double>();
    for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
      scaled.put(entry.getKey(), entry.getValue() * scaleFactor);
    }
    return scaled;
  }

  /**
   * Shifts a production profile by a number of years.
   *
   * @param profile original profile
   * @param yearShift number of years to shift (positive = later)
   * @return shifted profile
   */
  public static Map<Integer, Double> shiftProfile(Map<Integer, Double> profile, int yearShift) {
    Map<Integer, Double> shifted = new LinkedHashMap<Integer, Double>();
    for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
      shifted.put(entry.getKey() + yearShift, entry.getValue());
    }
    return shifted;
  }

  /**
   * Combines multiple production profiles (e.g., for multiple wells or phases).
   *
   * @param profiles array of profiles to combine
   * @return combined profile with summed values
   */
  public static Map<Integer, Double> combineProfiles(Map<Integer, Double>... profiles) {
    Map<Integer, Double> combined = new LinkedHashMap<Integer, Double>();

    for (Map<Integer, Double> profile : profiles) {
      for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
        int year = entry.getKey();
        double value = entry.getValue();
        Double existing = combined.get(year);
        combined.put(year, existing != null ? existing + value : value);
      }
    }

    return combined;
  }

  /**
   * Gets a summary of a production profile.
   *
   * @param profile the profile to summarize
   * @return formatted summary string
   */
  public static String getProfileSummary(Map<Integer, Double> profile) {
    if (profile.isEmpty()) {
      return "Empty profile";
    }

    int firstYear = Integer.MAX_VALUE;
    int lastYear = Integer.MIN_VALUE;
    double total = 0;
    double peak = 0;

    for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
      firstYear = Math.min(firstYear, entry.getKey());
      lastYear = Math.max(lastYear, entry.getKey());
      total += entry.getValue();
      peak = Math.max(peak, entry.getValue());
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Production Profile Summary:%n"));
    sb.append(String.format("  Period: %d - %d (%d years)%n", firstYear, lastYear,
        lastYear - firstYear + 1));
    sb.append(String.format("  Peak Annual: %.2e%n", peak));
    sb.append(String.format("  Cumulative: %.2e%n", total));
    return sb.toString();
  }
}
