package neqsim.process.safety.dispersion;

import java.io.Serializable;

/**
 * Probit (probability unit) consequence model for fatality / injury fraction from a hazard dose.
 *
 * <p>
 * Computes Y = a + b · ln(D), then converts the probit Y to a fatality fraction via the standard
 * normal CDF: P = 0.5 · (1 + erf((Y − 5) / √2)). The dose D depends on the hazard:
 * <ul>
 * <li><b>Toxic:</b> D = ∫ C^n dt with C in ppm, t in min (varies by chemical)</li>
 * <li><b>Thermal:</b> D = ∫ I^(4/3) dt with I in W/m², t in s</li>
 * <li><b>Overpressure:</b> D = ΔP in Pa (or with impulse for structural damage)</li>
 * </ul>
 *
 * <p>
 * <b>References:</b> CCPS Yellow Book / Eisenberg, Lynch &amp; Breeding (1975) / TNO Green Book.
 *
 * @author ESOL
 * @version 1.0
 */
public class ProbitModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double a;
  private final double b;
  private final double n;

  /**
   * Construct a probit model with constants Y = a + b · ln(D^n) ≡ a + b·n·ln(D).
   * Many sources publish (a, b) with n already absorbed; in that case set n = 1.
   *
   * @param a probit intercept
   * @param b probit slope
   * @param n dose exponent (use 1 if (a,b) already include it)
   */
  public ProbitModel(double a, double b, double n) {
    this.a = a;
    this.b = b;
    this.n = n;
  }

  /**
   * Compute the probit Y for a given dose.
   *
   * @param dose dose value (units depend on hazard)
   * @return probit value Y
   */
  public double probit(double dose) {
    if (dose <= 0.0) {
      return Double.NEGATIVE_INFINITY;
    }
    return a + b * n * Math.log(dose);
  }

  /**
   * Convert probit Y to fatality / injury fraction P (0..1).
   *
   * @param Y probit value
   * @return probability in [0, 1]
   */
  public double probability(double Y) {
    if (Double.isInfinite(Y) && Y < 0.0) {
      return 0.0;
    }
    return 0.5 * (1.0 + erf((Y - 5.0) / Math.sqrt(2.0)));
  }

  /**
   * Direct dose → probability shortcut.
   *
   * @param dose hazard-specific dose
   * @return probability in [0, 1]
   */
  public double probabilityFromDose(double dose) {
    return probability(probit(dose));
  }

  // Abramowitz & Stegun 7.1.26 erf approximation
  private static double erf(double x) {
    double sign = x < 0.0 ? -1.0 : 1.0;
    x = Math.abs(x);
    double t = 1.0 / (1.0 + 0.3275911 * x);
    double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
        - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
    return sign * y;
  }

  // ------------------------------------------------------------------
  // Library of common probit constants (CCPS Yellow Book, Table 5.16)
  // ------------------------------------------------------------------

  /**
   * Toxic-fatality probit for H₂S (TNO Green Book): Y = -31.42 + 3.008·ln(C^1.43·t),
   * C in ppm, t in min.
   *
   * @return ProbitModel for H₂S fatality
   */
  public static ProbitModel h2sFatality() {
    return new ProbitModel(-31.42, 3.008, 1.43);
  }

  /**
   * Toxic-fatality probit for chlorine (Eisenberg): Y = -17.10 + 1.69·ln(C^2.75·t),
   * C in ppm, t in min.
   *
   * @return ProbitModel for chlorine fatality
   */
  public static ProbitModel chlorineFatality() {
    return new ProbitModel(-17.10, 1.69, 2.75);
  }

  /**
   * Toxic-fatality probit for ammonia (TNO Green Book).
   *
   * @return ProbitModel for ammonia fatality
   */
  public static ProbitModel ammoniaFatality() {
    return new ProbitModel(-35.9, 1.85, 2.0);
  }

  /**
   * Thermal radiation fatality probit for unprotected skin (Eisenberg):
   * Y = -14.9 + 2.56·ln(t·I^(4/3)/10⁴), t in s, I in W/m².
   *
   * @return ProbitModel for thermal radiation fatality
   */
  public static ProbitModel thermalFatality() {
    // Use n = 1 with dose = t · I^(4/3) / 1e4
    return new ProbitModel(-14.9, 2.56, 1.0);
  }

  /**
   * Lung-haemorrhage fatality probit for blast overpressure (Eisenberg):
   * Y = -77.1 + 6.91·ln(ΔP), ΔP in Pa.
   *
   * @return ProbitModel for blast lung damage fatality
   */
  public static ProbitModel blastLungFatality() {
    return new ProbitModel(-77.1, 6.91, 1.0);
  }
}
