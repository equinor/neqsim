package neqsim.pvtsimulation.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standalone Z-factor (gas compressibility factor) correlations.
 *
 * <p>
 * Provides multiple correlations for calculating the gas compressibility factor (Z) from
 * pseudoreduced temperature and pressure. Also includes convenience methods that accept Kelvin,
 * bara, and gas specific gravity directly.
 *
 * <p>
 * <b>Default units:</b> All convenience methods use Kelvin and bara. Core correlations
 * ({@link #hallYarborough}, {@link #dranchukAbouKassem}, {@link #papay}) are dimensionless and take
 * pseudoreduced properties directly.
 *
 * <p>
 * <b>Correlations:</b>
 * <ul>
 * <li><b>Hall-Yarborough (1973)</b> - Newton-Raphson iterative solution</li>
 * <li><b>Dranchuk-Abou-Kassem (1975)</b> - 11-constant BWR equation</li>
 * <li><b>Papay (1968)</b> - simple explicit correlation</li>
 * </ul>
 *
 * @author ESOL
 * @version 2.0
 */
public final class ZFactorCorrelations {

  /** Conversion factor: Kelvin to Rankine. */
  private static final double K_TO_R = 9.0 / 5.0;
  /** Conversion factor: bara to psia. */
  private static final double BARA_TO_PSIA = 14.5038;
  /** Gas constant R in field units (psia*ft3)/(lbmol*R). */
  private static final double R_FIELD = 10.7316;
  /** Gas constant R in SI-like units (bara*m3)/(kmol*K). */
  private static final double R_SI = 0.0831447;

  private ZFactorCorrelations() {
    // Utility class
  }

  // ============================================================
  // Core correlations (dimensionless Tpr, Ppr)
  // ============================================================

  /**
   * Hall-Yarborough (1973) Z-factor correlation.
   *
   * <p>
   * Iterative Newton-Raphson solution of the Starling-Carnahan equation of state. Uses reduced
   * density y as the primary unknown.
   *
   * @param tpr Pseudoreduced temperature (dimensionless). Valid: 1.0 - 3.0
   * @param ppr Pseudoreduced pressure (dimensionless). Valid: 0 - 25
   * @return Z-factor (dimensionless)
   */
  public static double hallYarborough(double tpr, double ppr) {
    if (ppr < 1.0e-10) {
      return 1.0;
    }
    double t = 1.0 / tpr;
    double a = -0.06125 * ppr * t * Math.exp(-1.2 * (1.0 - t) * (1.0 - t));
    double b = 14.76 * t - 9.76 * t * t + 4.58 * t * t * t;
    double c = 90.7 * t - 242.2 * t * t + 42.4 * t * t * t;
    double d = 2.18 + 2.82 * t;

    double y = 0.001;
    for (int i = 0; i < 200; i++) {
      double fy = a + (y + y * y + y * y * y - y * y * y * y) / Math.pow(1.0 - y, 3) - b * y * y
          + c * Math.pow(y, d);
      double dfy =
          (1.0 + 4.0 * y + 4.0 * y * y - 4.0 * y * y * y + y * y * y * y) / Math.pow(1.0 - y, 4)
              - 2.0 * b * y + c * d * Math.pow(y, d - 1.0);
      double yNew = y - fy / dfy;
      if (yNew < 1.0e-12) {
        yNew = 1.0e-12;
      }
      if (yNew > 0.9999) {
        yNew = 0.9999;
      }
      if (Math.abs(yNew - y) < 1.0e-13) {
        y = yNew;
        break;
      }
      y = yNew;
    }

    // a already includes ppr, so Z = -a / y gives positive Z
    double z = -a / y;
    if (z < 0.05 || z > 5.0) {
      z = 1.0;
    }
    return z;
  }

  /**
   * Dranchuk-Abou-Kassem (1975) Z-factor correlation.
   *
   * <p>
   * Eleven-constant modified Benedict-Webb-Rubin equation of state. Uses iterative Newton-Raphson
   * solution for reduced density.
   *
   * @param tpr Pseudoreduced temperature (dimensionless). Valid: 1.0 - 3.0
   * @param ppr Pseudoreduced pressure (dimensionless). Valid: 0.2 - 30
   * @return Z-factor (dimensionless)
   */
  public static double dranchukAbouKassem(double tpr, double ppr) {
    if (ppr < 1.0e-10) {
      return 1.0;
    }
    double a1 = 0.3265, a2 = -1.0700, a3 = -0.5339, a4 = 0.01569;
    double a5 = -0.05165, a6 = 0.5475, a7 = -0.7361, a8 = 0.1844;
    double a9 = 0.1056, a10 = 0.6134, a11 = 0.7210;

    double rhoR = 0.27 * ppr / tpr;
    for (int i = 0; i < 200; i++) {
      double rr = rhoR;
      double rr2 = rr * rr;
      double c1 =
          a1 + a2 / tpr + a3 / (tpr * tpr * tpr) + a4 / Math.pow(tpr, 4) + a5 / Math.pow(tpr, 5);
      double c2 = a6 + a7 / tpr + a8 / (tpr * tpr);
      double c3 = a9 * (a7 / tpr + a8 / (tpr * tpr));
      double fz = 1.0 + c1 * rr + c2 * rr2 - c3 * rr2 * rr2 * rr
          + a10 * (1.0 + a11 * rr2) * (rr2 / (tpr * tpr * tpr)) * Math.exp(-a11 * rr2)
          - 0.27 * ppr / (rhoR * tpr);
      double dfz = c1 + 2.0 * c2 * rr - 5.0 * c3 * rr2 * rr2
          + a10 * (rr / (tpr * tpr * tpr))
              * ((2.0 + 4.0 * a11 * rr2 - 2.0 * a11 * a11 * rr2 * rr2) * Math.exp(-a11 * rr2))
          + 0.27 * ppr / (rhoR * rhoR * tpr);
      double rhoNew = rhoR - fz / dfz;
      if (rhoNew < 1.0e-15) {
        rhoNew = 1.0e-15;
      }
      if (Math.abs(rhoNew - rhoR) < 1.0e-12) {
        rhoR = rhoNew;
        break;
      }
      rhoR = rhoNew;
    }

    double z = 0.27 * ppr / (rhoR * tpr);
    if (z < 0.05 || z > 5.0) {
      z = 1.0;
    }
    return z;
  }

  /**
   * Papay (1968) explicit Z-factor correlation.
   *
   * <p>
   * Simple explicit formula. Less accurate than iterative methods but useful for quick estimates or
   * as an initial guess.
   *
   * $$ Z = 1 - \frac{3.52 P_{pr}}{10^{0.9813 T_{pr}}} + \frac{0.274 P_{pr}^2}{10^{0.8157 T_{pr}}}
   * $$
   *
   * @param tpr Pseudoreduced temperature (dimensionless). Valid: 1.0 - 3.0
   * @param ppr Pseudoreduced pressure (dimensionless). Valid: 0.2 - 15
   * @return Z-factor (dimensionless)
   */
  public static double papay(double tpr, double ppr) {
    double z = 1.0 - 3.52 * ppr / Math.pow(10.0, 0.9813 * tpr)
        + 0.274 * ppr * ppr / Math.pow(10.0, 0.8157 * tpr);
    if (z < 0.05) {
      z = 0.05;
    }
    return z;
  }

  // ============================================================
  // Convenience methods (Kelvin / bara)
  // ============================================================

  /**
   * Z-factor using Sutton pseudocritical properties and Hall-Yarborough correlation.
   *
   * @param pressureBara Pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @return Z-factor (dimensionless)
   */
  public static double zFactorSutton(double pressureBara, double temperatureK, double gammaG) {
    double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(gammaG);
    double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(gammaG);
    double tpr = temperatureK / tpc;
    double ppr = pressureBara / ppc;
    return hallYarborough(tpr, ppr);
  }

  /**
   * Z-factor for sour gas with Wichert-Aziz correction.
   *
   * @param pressureBara Pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @param yH2S Mole fraction of H2S [0, 1]
   * @param yCO2 Mole fraction of CO2 [0, 1]
   * @return Z-factor (dimensionless)
   */
  public static double zFactorSourGas(double pressureBara, double temperatureK, double gammaG,
      double yH2S, double yCO2) {
    double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(gammaG);
    double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(gammaG);
    double[] corrected = GasPseudoCriticalProperties.wichertAzizCorrection(tpc, ppc, yH2S, yCO2);
    double tpr = temperatureK / corrected[0];
    double ppr = pressureBara / corrected[1];
    return hallYarborough(tpr, ppr);
  }

  /**
   * Gas density using Z-factor from Sutton + Hall-Yarborough.
   *
   * @param pressureBara Pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @param molWeight Gas molecular weight (g/mol)
   * @return Gas density (kg/m3)
   */
  public static double gasDensity(double pressureBara, double temperatureK, double gammaG,
      double molWeight) {
    double z = zFactorSutton(pressureBara, temperatureK, gammaG);
    // rho = P * MW / (Z * R * T) in consistent units
    // P in bara, T in K, R = 0.0831447 (bara*m3)/(kmol*K), MW in g/mol = kg/kmol
    return pressureBara * molWeight / (z * R_SI * temperatureK);
  }

  /**
   * Gas formation volume factor from Z-factor.
   *
   * <p>
   * $$ B_g = \frac{Z \, T}{P} \times \frac{P_{sc}}{T_{sc}} $$
   *
   * Standard conditions: 1.01325 bara, 288.71 K (15.56 C / 60 F). Returns Bg in reservoir m3 /
   * standard m3.
   *
   * @param pressureBara Pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @return Gas FVF (res m3 / std m3)
   */
  public static double gasFVFFromZ(double pressureBara, double temperatureK, double gammaG) {
    double z = zFactorSutton(pressureBara, temperatureK, gammaG);
    double pSc = 1.01325; // bara
    double tSc = 288.71; // K (60 F)
    return z * temperatureK / pressureBara * pSc / tSc;
  }

  /**
   * Compare all three Z-factor correlations at the given reduced conditions.
   *
   * @param tpr Pseudoreduced temperature (dimensionless)
   * @param ppr Pseudoreduced pressure (dimensionless)
   * @return Map of correlation name to Z value
   */
  public static Map<String, Double> compareAll(double tpr, double ppr) {
    Map<String, Double> results = new LinkedHashMap<String, Double>();
    results.put("Hall-Yarborough", hallYarborough(tpr, ppr));
    results.put("Dranchuk-Abou-Kassem", dranchukAbouKassem(tpr, ppr));
    results.put("Papay", papay(tpr, ppr));
    return results;
  }
}
