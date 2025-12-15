package neqsim.pvtsimulation.util;

/**
 * Saturation pressure (bubble point) correlations per Whitson wiki methodology.
 *
 * <p>
 * This class provides empirical correlations for estimating bubble point pressure from easily
 * measured field data such as GOR, API gravity, gas specific gravity, and temperature.
 * </p>
 *
 * <p>
 * Reference: Whitson wiki (https://wiki.whitson.com/bopvt/bo_correlations/)
 * </p>
 *
 * <h2>Available Correlations:</h2>
 * <ul>
 * <li><b>Standing (1947)</b> - Original correlation, widely used</li>
 * <li><b>Vasquez-Beggs (1980)</b> - Temperature-corrected</li>
 * <li><b>Glaso (1980)</b> - North Sea oils</li>
 * <li><b>Petrosky-Farshad (1993)</b> - Gulf of Mexico oils</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * double Rs = 150.0; // scf/STB
 * double gammaG = 0.75; // gas specific gravity
 * double API = 35.0; // API gravity
 * double T = 180.0; // °F
 *
 * double Pb = SaturationPressureCorrelation.standing(Rs, gammaG, API, T);
 * System.out.println("Bubble point: " + Pb + " psia");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SaturationPressureCorrelation {

  /** Private constructor to prevent instantiation. */
  private SaturationPressureCorrelation() {}

  /**
   * Standing (1947) bubble point correlation.
   *
   * <p>
   * One of the most widely used correlations for estimating bubble point pressure. Developed for
   * California crude oils.
   * </p>
   *
   * <p>
   * Equation: Pb = 18.2 * ((Rs/γg)^0.83 * 10^(0.00091*T - 0.0125*API) - 1.4)
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return bubble point pressure (psia)
   */
  public static double standing(double Rs, double gammaG, double API, double T) {
    if (Rs <= 0 || gammaG <= 0 || API <= 0) {
      return Double.NaN;
    }

    double x = 0.00091 * T - 0.0125 * API;
    double factor = Math.pow(Rs / gammaG, 0.83) * Math.pow(10, x);
    double Pb = 18.2 * (factor - 1.4);

    return Math.max(Pb, 14.7); // Cannot be below atmospheric
  }

  /**
   * Vasquez-Beggs (1980) bubble point correlation.
   *
   * <p>
   * Improved correlation that accounts for separator conditions. Uses temperature-corrected gas
   * gravity.
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return bubble point pressure (psia)
   */
  public static double vasquezBeggs(double Rs, double gammaG, double API, double T) {
    if (Rs <= 0 || gammaG <= 0 || API <= 0) {
      return Double.NaN;
    }

    // Coefficients depend on API gravity
    double C1, C2, C3;
    if (API <= 30) {
      C1 = 0.0362;
      C2 = 1.0937;
      C3 = 25.724;
    } else {
      C1 = 0.0178;
      C2 = 1.187;
      C3 = 23.931;
    }

    // Calculate bubble point
    double exponent = -C3 * API / (T + 459.67);
    double Pb = Math.pow(Rs / (C1 * gammaG * Math.exp(exponent)), 1.0 / C2);

    return Math.max(Pb, 14.7);
  }

  /**
   * Glaso (1980) bubble point correlation.
   *
   * <p>
   * Developed for North Sea crude oils. Includes a two-step correlation with a characteristic
   * function.
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return bubble point pressure (psia)
   */
  public static double glaso(double Rs, double gammaG, double API, double T) {
    if (Rs <= 0 || gammaG <= 0 || API <= 0 || T <= 0) {
      return Double.NaN;
    }

    // Calculate Pb* (characteristic function)
    double Tpower = Math.pow(T, 0.172);
    double APIpower = Math.pow(API, 0.989);
    double RsGgRatio = Rs / gammaG;
    double RsGgPower = Math.pow(RsGgRatio, 0.816);

    double pbStar = RsGgPower * Tpower / APIpower;

    // Apply correlation
    double logPbStar = Math.log10(pbStar);
    double logPb = 1.7669 + 1.7447 * logPbStar - 0.30218 * logPbStar * logPbStar;
    double Pb = Math.pow(10, logPb);

    return Math.max(Pb, 14.7);
  }

  /**
   * Petrosky-Farshad (1993) bubble point correlation.
   *
   * <p>
   * Developed for Gulf of Mexico oils. Provides improved accuracy for offshore Gulf Coast crude
   * oils.
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return bubble point pressure (psia)
   */
  public static double petroskyFarshad(double Rs, double gammaG, double API, double T) {
    if (Rs <= 0 || gammaG <= 0 || API <= 0 || T <= 0) {
      return Double.NaN;
    }

    double x = 0.0007916 * Math.pow(API, 1.5410) - 0.0004561 * Math.pow(T, 1.3911);
    double factor = Math.pow(Rs, 0.8439) * Math.pow(10, x);
    double Pb = 112.727 * (factor / (Math.pow(gammaG, 0.8439)) - 12.340);

    return Math.max(Pb, 14.7);
  }

  /**
   * Al-Marhoun (1988) bubble point correlation.
   *
   * <p>
   * Developed for Middle Eastern crude oils.
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param gammaO oil specific gravity (water = 1.0)
   * @param T temperature (°F)
   * @return bubble point pressure (psia)
   */
  public static double alMarhoun(double Rs, double gammaG, double gammaO, double T) {
    if (Rs <= 0 || gammaG <= 0 || gammaO <= 0 || T <= 0) {
      return Double.NaN;
    }

    double TR = T + 459.67; // Rankine

    double a = 5.38088E-3;
    double b = 0.715082;
    double c = -1.87784;
    double d = 3.1437;
    double e = 1.32657;

    double Pb = a * Math.pow(Rs, b) * Math.pow(gammaG, c) * Math.pow(gammaO, d) * Math.pow(TR, e);

    return Math.max(Pb, 14.7);
  }

  /**
   * Convert API gravity to specific gravity.
   *
   * @param API API gravity
   * @return specific gravity (water = 1.0)
   */
  public static double apiToSpecificGravity(double API) {
    return 141.5 / (API + 131.5);
  }

  /**
   * Convert specific gravity to API gravity.
   *
   * @param sg specific gravity (water = 1.0)
   * @return API gravity
   */
  public static double specificGravityToAPI(double sg) {
    return 141.5 / sg - 131.5;
  }

  /**
   * Convert pressure from psia to bar.
   *
   * @param psia pressure in psia
   * @return pressure in bar
   */
  public static double psiaToBar(double psia) {
    return psia * 0.0689476;
  }

  /**
   * Convert pressure from bar to psia.
   *
   * @param bar pressure in bar
   * @return pressure in psia
   */
  public static double barToPsia(double bar) {
    return bar / 0.0689476;
  }

  /**
   * Convert temperature from Fahrenheit to Celsius.
   *
   * @param fahrenheit temperature in °F
   * @return temperature in °C
   */
  public static double fahrenheitToCelsius(double fahrenheit) {
    return (fahrenheit - 32.0) * 5.0 / 9.0;
  }

  /**
   * Convert temperature from Celsius to Fahrenheit.
   *
   * @param celsius temperature in °C
   * @return temperature in °F
   */
  public static double celsiusToFahrenheit(double celsius) {
    return celsius * 9.0 / 5.0 + 32.0;
  }

  /**
   * Convert GOR from scf/STB to Sm3/Sm3.
   *
   * @param scfPerStb GOR in scf/STB
   * @return GOR in Sm3/Sm3
   */
  public static double scfStbToSm3Sm3(double scfPerStb) {
    return scfPerStb * 0.17810760667903;
  }

  /**
   * Convert GOR from Sm3/Sm3 to scf/STB.
   *
   * @param sm3PerSm3 GOR in Sm3/Sm3
   * @return GOR in scf/STB
   */
  public static double sm3Sm3ToScfStb(double sm3PerSm3) {
    return sm3PerSm3 / 0.17810760667903;
  }

  /**
   * Estimate bubble point using all correlations and return average.
   *
   * <p>
   * This method calculates bubble point using multiple correlations and returns statistics to help
   * assess uncertainty.
   * </p>
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return array of [average, min, max, stdDev]
   */
  public static double[] estimateWithStatistics(double Rs, double gammaG, double API, double T) {
    double[] results = new double[4]; // {Standing, VB, Glaso, PF}

    results[0] = standing(Rs, gammaG, API, T);
    results[1] = vasquezBeggs(Rs, gammaG, API, T);
    results[2] = glaso(Rs, gammaG, API, T);
    results[3] = petroskyFarshad(Rs, gammaG, API, T);

    // Calculate statistics
    double sum = 0;
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;
    int validCount = 0;

    for (double pb : results) {
      if (!Double.isNaN(pb)) {
        sum += pb;
        min = Math.min(min, pb);
        max = Math.max(max, pb);
        validCount++;
      }
    }

    if (validCount == 0) {
      return new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    }

    double avg = sum / validCount;

    // Calculate standard deviation
    double sumSq = 0;
    for (double pb : results) {
      if (!Double.isNaN(pb)) {
        sumSq += (pb - avg) * (pb - avg);
      }
    }
    double stdDev = Math.sqrt(sumSq / validCount);

    return new double[] {avg, min, max, stdDev};
  }

  /**
   * Generate a comparison report of all correlations.
   *
   * @param Rs solution gas-oil ratio at Pb (scf/STB)
   * @param gammaG gas specific gravity (air = 1.0)
   * @param API API gravity of stock-tank oil
   * @param T temperature (°F)
   * @return formatted comparison report
   */
  public static String generateComparisonReport(double Rs, double gammaG, double API, double T) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Bubble Point Pressure Comparison ===\n\n");
    sb.append(String.format("Input Parameters:\n"));
    sb.append(String.format("  Rs = %.1f scf/STB (%.1f Sm3/Sm3)\n", Rs, scfStbToSm3Sm3(Rs)));
    sb.append(String.format("  γg = %.4f\n", gammaG));
    sb.append(String.format("  API = %.1f (γo = %.4f)\n", API, apiToSpecificGravity(API)));
    sb.append(String.format("  T = %.1f °F (%.1f °C)\n\n", T, fahrenheitToCelsius(T)));

    sb.append("Correlation Results:\n");
    sb.append(String.format("  Standing (1947):        %.1f psia (%.2f bar)\n",
        standing(Rs, gammaG, API, T), psiaToBar(standing(Rs, gammaG, API, T))));
    sb.append(String.format("  Vasquez-Beggs (1980):   %.1f psia (%.2f bar)\n",
        vasquezBeggs(Rs, gammaG, API, T), psiaToBar(vasquezBeggs(Rs, gammaG, API, T))));
    sb.append(String.format("  Glaso (1980):           %.1f psia (%.2f bar)\n",
        glaso(Rs, gammaG, API, T), psiaToBar(glaso(Rs, gammaG, API, T))));
    sb.append(String.format("  Petrosky-Farshad (1993):%.1f psia (%.2f bar)\n",
        petroskyFarshad(Rs, gammaG, API, T), psiaToBar(petroskyFarshad(Rs, gammaG, API, T))));

    double[] stats = estimateWithStatistics(Rs, gammaG, API, T);
    sb.append(String.format("\nStatistics:\n"));
    sb.append(String.format("  Average: %.1f psia (%.2f bar)\n", stats[0], psiaToBar(stats[0])));
    sb.append(String.format("  Range:   %.1f - %.1f psia\n", stats[1], stats[2]));
    sb.append(String.format("  Std Dev: %.1f psia\n", stats[3]));

    return sb.toString();
  }
}
