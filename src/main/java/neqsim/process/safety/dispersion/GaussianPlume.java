package neqsim.process.safety.dispersion;

import java.io.Serializable;

/**
 * Pasquill–Gifford Gaussian plume dispersion model for neutral / passive gas releases.
 *
 * <p>
 * Implements the steady-state Gaussian plume centerline and ground-level concentration formulae
 * (Briggs urban / rural sigma coefficients) for the six Pasquill atmospheric stability classes
 * (A–F).
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>EPA-454/R-99-005 — Industrial Source Complex (ISC) Dispersion Models, Vol II</li>
 * <li>Briggs G.A. (1973) — Diffusion estimation for small emissions</li>
 * <li>CCPS — Guidelines for Consequence Analysis of Chemical Releases</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class GaussianPlume implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Pasquill atmospheric stability classes. */
  public enum Stability {
    /** Extremely unstable. */
    A,
    /** Moderately unstable. */
    B,
    /** Slightly unstable. */
    C,
    /** Neutral. */
    D,
    /** Slightly stable. */
    E,
    /** Moderately stable. */
    F
  }

  /** Terrain category for sigma correlation selection. */
  public enum Terrain {
    /** Open / rural terrain. */
    RURAL,
    /** Built-up / urban terrain. */
    URBAN
  }

  private final double emissionRateKgPerS;
  private final double effectiveStackHeightM;
  private final double windSpeedMperS;
  private final Stability stability;
  private final Terrain terrain;

  /**
   * Construct a Gaussian plume model.
   *
   * @param emissionRateKgPerS continuous source strength in kg/s
   * @param effectiveStackHeightM effective release height (stack + buoyancy rise) in m
   * @param windSpeedAtRefM10m wind speed at 10 m reference in m/s
   * @param stability Pasquill atmospheric stability class
   * @param terrain rural or urban terrain
   */
  public GaussianPlume(double emissionRateKgPerS, double effectiveStackHeightM,
      double windSpeedAtRefM10m, Stability stability, Terrain terrain) {
    if (emissionRateKgPerS < 0.0 || windSpeedAtRefM10m <= 0.0) {
      throw new IllegalArgumentException("emissionRate must be >=0 and windSpeed must be >0");
    }
    this.emissionRateKgPerS = emissionRateKgPerS;
    this.effectiveStackHeightM = effectiveStackHeightM;
    this.windSpeedMperS = windSpeedAtRefM10m;
    this.stability = stability;
    this.terrain = terrain;
  }

  /**
   * Horizontal dispersion coefficient (Briggs).
   *
   * @param x downwind distance in m (must be positive)
   * @return σy in m
   */
  public double sigmaY(double x) {
    double[] ab = briggsY();
    return ab[0] * x / Math.pow(1.0 + ab[1] * x, 0.5);
  }

  /**
   * Vertical dispersion coefficient (Briggs).
   *
   * @param x downwind distance in m
   * @return σz in m
   */
  public double sigmaZ(double x) {
    double[] cd = briggsZ();
    return cd[0] * x / Math.pow(1.0 + cd[1] * x, cd[2]);
  }

  private double[] briggsY() {
    boolean rural = terrain == Terrain.RURAL;
    switch (stability) {
      case A:
        return rural ? new double[] {0.22, 0.0001} : new double[] {0.32, 0.0004};
      case B:
        return rural ? new double[] {0.16, 0.0001} : new double[] {0.32, 0.0004};
      case C:
        return rural ? new double[] {0.11, 0.0001} : new double[] {0.22, 0.0004};
      case D:
        return rural ? new double[] {0.08, 0.0001} : new double[] {0.16, 0.0004};
      case E:
        return rural ? new double[] {0.06, 0.0001} : new double[] {0.11, 0.0004};
      case F:
      default:
        return rural ? new double[] {0.04, 0.0001} : new double[] {0.11, 0.0004};
    }
  }

  private double[] briggsZ() {
    boolean rural = terrain == Terrain.RURAL;
    // {a, b, exponent}
    switch (stability) {
      case A:
        return rural ? new double[] {0.20, 0.0, 1.0} : new double[] {0.24, 0.001, 0.5};
      case B:
        return rural ? new double[] {0.12, 0.0, 1.0} : new double[] {0.24, 0.001, 0.5};
      case C:
        return rural ? new double[] {0.08, 0.0002, 0.5} : new double[] {0.20, 0.0, 1.0};
      case D:
        return rural ? new double[] {0.06, 0.0015, 0.5} : new double[] {0.14, 0.0003, 0.5};
      case E:
        return rural ? new double[] {0.03, 0.0003, 1.0} : new double[] {0.08, 0.00015, 0.5};
      case F:
      default:
        return rural ? new double[] {0.016, 0.0003, 1.0} : new double[] {0.08, 0.00015, 0.5};
    }
  }

  /**
   * Compute concentration at point (x downwind, y crosswind, z height).
   *
   * @param x downwind distance in m (&gt; 0)
   * @param y crosswind distance in m
   * @param z receptor height in m
   * @return concentration in kg/m³
   */
  public double concentration(double x, double y, double z) {
    if (x <= 0.0 || emissionRateKgPerS == 0.0) {
      return 0.0;
    }
    double sy = sigmaY(x);
    double sz = sigmaZ(x);
    double H = effectiveStackHeightM;
    double term1 = emissionRateKgPerS / (2.0 * Math.PI * windSpeedMperS * sy * sz);
    double yTerm = Math.exp(-0.5 * (y * y) / (sy * sy));
    double zTerm1 = Math.exp(-0.5 * Math.pow(z - H, 2) / (sz * sz));
    double zTerm2 = Math.exp(-0.5 * Math.pow(z + H, 2) / (sz * sz));
    return term1 * yTerm * (zTerm1 + zTerm2);
  }

  /**
   * Compute ground-level centerline concentration directly downwind.
   *
   * @param x downwind distance in m
   * @return concentration in kg/m³
   */
  public double centerlineGroundConcentration(double x) {
    return concentration(x, 0.0, 0.0);
  }

  /**
   * Estimate the maximum downwind distance at which the centerline ground concentration drops to
   * the supplied threshold (e.g. LFL, IDLH, ERPG-2).
   *
   * @param thresholdKgPerM3 concentration threshold in kg/m³
   * @return downwind distance in m, or {@link Double#NaN} if never reached
   */
  public double distanceToConcentration(double thresholdKgPerM3) {
    if (thresholdKgPerM3 <= 0.0) {
      return Double.NaN;
    }

    double minDistance = 1.0;
    double maxDistance = 100000.0;
    double previousDistance = minDistance;
    double previousConcentration = centerlineGroundConcentration(previousDistance);
    double aboveDistance =
        previousConcentration >= thresholdKgPerM3 ? previousDistance : Double.NaN;
    double belowDistance = Double.NaN;

    for (int i = 1; i <= 200; i++) {
      double fraction = i / 200.0;
      double distance = minDistance * Math.pow(maxDistance / minDistance, fraction);
      double concentration = centerlineGroundConcentration(distance);
      if (concentration >= thresholdKgPerM3) {
        aboveDistance = distance;
      } else if (previousConcentration >= thresholdKgPerM3) {
        aboveDistance = previousDistance;
        belowDistance = distance;
      }
      previousDistance = distance;
      previousConcentration = concentration;
    }

    if (!Double.isFinite(aboveDistance)) {
      return Double.NaN;
    }
    if (!Double.isFinite(belowDistance)) {
      return maxDistance;
    }
    double lo = aboveDistance;
    double hi = belowDistance;
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (lo + hi);
      double c = centerlineGroundConcentration(mid);
      if (c > thresholdKgPerM3) {
        lo = mid;
      } else {
        hi = mid;
      }
      if ((hi - lo) < 0.5) {
        break;
      }
    }
    return 0.5 * (lo + hi);
  }
}
