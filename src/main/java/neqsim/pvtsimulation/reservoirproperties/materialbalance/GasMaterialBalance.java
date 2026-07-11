package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;
import neqsim.pvtsimulation.util.ZFactorCorrelations;

/**
 * Analytical gas material balance ("inverse" reservoir surveillance).
 *
 * <p>
 * Estimates original gas in place (OGIP) and diagnoses aquifer support from a measured pressure-versus-cumulative-gas
 * history. This complements the forward tank models (e.g. {@code SimpleReservoir}) by regressing reserves directly from
 * field data.
 * </p>
 *
 * <h2>P/Z straight line (volumetric depletion drive)</h2>
 *
 * <p>
 * For a volumetric dry-gas reservoir with no water influx the material balance reduces to a straight line (Craft &amp;
 * Hawkins; Havlena &amp; Odeh, 1963):
 * </p>
 *
 * <p>
 * $$ \frac{p}{Z} = \frac{p_i}{Z_i}\left(1 - \frac{G_p}{G}\right) $$
 * </p>
 *
 * <p>
 * A linear regression of $p/Z$ against $G_p$ gives the intercept $p_i/Z_i$ and the x-intercept $G$ (the OGIP) where
 * $p/Z = 0$.
 * </p>
 *
 * <h2>Cole plot (aquifer diagnostic)</h2>
 *
 * <p>
 * The Cole plot of the withdrawal-to-expansion ratio $F/E_g$ against cumulative production diagnoses drive mechanism: a
 * horizontal trend indicates volumetric depletion, while an upward trend indicates water influx. Here $F = G_p B_g$ is
 * the reservoir withdrawal and $E_g = B_g - B_{gi}$ is the gas expansion.
 * </p>
 *
 * <h2>Havlena-Odeh gas balance with aquifer</h2>
 *
 * <p>
 * With a supplied cumulative water influx $W_e$, the balance $F = G\,E_g + W_e$ is regressed for the OGIP $G$.
 * </p>
 *
 * <p>
 * <b>Units:</b> pressures in bara, temperatures in Kelvin, cumulative gas $G_p$ in any consistent surface volume unit;
 * the returned OGIP is in the same surface volume unit as $G_p$. Formation volume factors are reservoir volume per
 * surface volume at NeqSim standard conditions (1.01325 bara, 288.71 K).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class GasMaterialBalance implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** NeqSim standard-condition pressure (bara). */
  private static final double P_SC = 1.01325;
  /** NeqSim standard-condition temperature (Kelvin, 60 F). */
  private static final double T_SC = 288.71;

  private GasMaterialBalance() {
    // Utility class
  }

  /**
   * Result of a gas material balance regression.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Original gas in place (same surface volume unit as Gp). */
    private final double ogip;
    /** Regression intercept p_i/Z_i (bara). */
    private final double piOverZi;
    /** Regression slope (bara per unit Gp). */
    private final double slope;
    /** Coefficient of determination of the fit. */
    private final double rSquared;

    /**
     * Constructor.
     *
     * @param ogip original gas in place
     * @param piOverZi intercept p_i/Z_i in bara
     * @param slope regression slope
     * @param rSquared coefficient of determination
     */
    public Result(double ogip, double piOverZi, double slope, double rSquared) {
      this.ogip = ogip;
      this.piOverZi = piOverZi;
      this.slope = slope;
      this.rSquared = rSquared;
    }

    /**
     * Original gas in place.
     *
     * @return OGIP in the same surface volume unit as the supplied cumulative gas
     */
    public double getOgip() {
      return ogip;
    }

    /**
     * Initial p/Z intercept.
     *
     * @return p_i/Z_i in bara
     */
    public double getPiOverZi() {
      return piOverZi;
    }

    /**
     * Regression slope of the p/Z line.
     *
     * @return slope in bara per unit cumulative gas
     */
    public double getSlope() {
      return slope;
    }

    /**
     * Goodness of fit.
     *
     * @return coefficient of determination (R^2)
     */
    public double getRSquared() {
      return rSquared;
    }
  }

  /**
   * Gas formation volume factor from a supplied Z-factor.
   *
   * <p>
   * $$ B_g = \frac{Z\,T}{p}\,\frac{p_{sc}}{T_{sc}} $$
   * </p>
   *
   * @param pressureBara pressure (bara), must be positive
   * @param z gas compressibility factor (dimensionless), must be positive
   * @param temperatureK reservoir temperature (Kelvin), must be positive
   * @return gas FVF in reservoir volume per surface volume
   */
  public static double bg(double pressureBara, double z, double temperatureK) {
    if (pressureBara <= 0.0 || z <= 0.0 || temperatureK <= 0.0) {
      throw new IllegalArgumentException("pressure, z and temperature must be positive");
    }
    return z * temperatureK / pressureBara * P_SC / T_SC;
  }

  /**
   * Fit a volumetric P/Z material balance from user-supplied Z-factors.
   *
   * @param pressure array of measured pressures (bara)
   * @param z array of Z-factors at each pressure (dimensionless)
   * @param gp array of cumulative gas produced at each pressure (consistent surface volume unit)
   * @return regression {@link Result} with OGIP, intercept, slope and R^2
   */
  public static Result fitVolumetric(double[] pressure, double[] z, double[] gp) {
    validateEqualLength(pressure, z, gp);
    double[] poz = new double[pressure.length];
    for (int i = 0; i < pressure.length; i++) {
      if (z[i] <= 0.0) {
        throw new IllegalArgumentException("Z-factor must be positive at index " + i);
      }
      poz[i] = pressure[i] / z[i];
    }
    double[] fit = linearRegression(gp, poz);
    double slope = fit[0];
    double intercept = fit[1];
    double rSquared = fit[2];
    double ogip = slope < 0.0 ? -intercept / slope : Double.POSITIVE_INFINITY;
    return new Result(ogip, intercept, slope, rSquared);
  }

  /**
   * Fit a volumetric P/Z material balance, computing Z-factors internally.
   *
   * <p>
   * Z-factors are evaluated with the Sutton pseudo-critical correlation and the Hall-Yarborough equation of state via
   * {@link ZFactorCorrelations#zFactorSutton(double, double, double)}.
   * </p>
   *
   * @param pressure array of measured pressures (bara)
   * @param gp array of cumulative gas produced at each pressure
   * @param temperatureK reservoir temperature (Kelvin)
   * @param gasGravity gas specific gravity (air = 1.0)
   * @return regression {@link Result} with OGIP, intercept, slope and R^2
   */
  public static Result fitVolumetric(double[] pressure, double[] gp, double temperatureK, double gasGravity) {
    if (pressure == null || gp == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    double[] z = new double[pressure.length];
    for (int i = 0; i < pressure.length; i++) {
      z[i] = ZFactorCorrelations.zFactorSutton(pressure[i], temperatureK, gasGravity);
    }
    return fitVolumetric(pressure, z, gp);
  }

  /**
   * Compute a Cole plot for aquifer diagnosis.
   *
   * <p>
   * Returns two arrays: cumulative gas $G_p$ and the withdrawal-to-expansion ratio $F/E_g$ for each history point after
   * the initial one. A near-constant ratio indicates volumetric depletion; an increasing ratio indicates water influx.
   * </p>
   *
   * @param pressure array of measured pressures (bara); index 0 is the initial pressure
   * @param z array of Z-factors at each pressure (dimensionless)
   * @param gp array of cumulative gas produced at each pressure
   * @param temperatureK reservoir temperature (Kelvin)
   * @return two-row array: row 0 = cumulative gas (from index 1), row 1 = F/Eg
   */
  public static double[][] colePlot(double[] pressure, double[] z, double[] gp, double temperatureK) {
    validateEqualLength(pressure, z, gp);
    if (pressure.length < 2) {
      throw new IllegalArgumentException("Cole plot requires at least two history points");
    }
    double bgi = bg(pressure[0], z[0], temperatureK);
    int n = pressure.length - 1;
    double[] gpOut = new double[n];
    double[] foverEg = new double[n];
    for (int i = 1; i < pressure.length; i++) {
      double bgCur = bg(pressure[i], z[i], temperatureK);
      double f = gp[i] * bgCur;
      double eg = bgCur - bgi;
      gpOut[i - 1] = gp[i];
      foverEg[i - 1] = eg > 1.0e-30 ? f / eg : Double.NaN;
    }
    return new double[][] { gpOut, foverEg };
  }

  /**
   * Havlena-Odeh gas material balance with a supplied cumulative water influx.
   *
   * <p>
   * Regresses $F = G\,E_g + W_e$ for the OGIP $G$ using ordinary least squares of $(F - W_e)$ against $E_g$ through the
   * origin, where $F = G_p B_g$ and $E_g = B_g - B_{gi}$.
   * </p>
   *
   * @param pressure array of measured pressures (bara); index 0 is the initial pressure
   * @param z array of Z-factors at each pressure (dimensionless)
   * @param gp array of cumulative gas produced at each pressure
   * @param we array of cumulative water influx at each pressure (reservoir volume, consistent with Bg surface unit)
   * @param temperatureK reservoir temperature (Kelvin)
   * @return regression {@link Result}; slope and intercept fields carry the through-origin slope and 0.0
   */
  public static Result fitHavlenaOdeh(double[] pressure, double[] z, double[] gp, double[] we, double temperatureK) {
    validateEqualLength(pressure, z, gp);
    if (we == null || we.length != pressure.length) {
      throw new IllegalArgumentException("we array must match pressure length");
    }
    double bgi = bg(pressure[0], z[0], temperatureK);
    int n = pressure.length - 1;
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 1; i < pressure.length; i++) {
      double bgCur = bg(pressure[i], z[i], temperatureK);
      double f = gp[i] * bgCur;
      double eg = bgCur - bgi;
      x[i - 1] = eg;
      y[i - 1] = f - we[i];
    }
    double[] fit = throughOriginRegression(x, y);
    return new Result(fit[0], 0.0, fit[0], fit[1]);
  }

  // ============================================================
  // Regression helpers
  // ============================================================

  /**
   * Ordinary least-squares linear regression y = slope*x + intercept.
   *
   * @param x independent variable samples
   * @param y dependent variable samples
   * @return three-element array {slope, intercept, rSquared}
   */
  static double[] linearRegression(double[] x, double[] y) {
    int n = x.length;
    if (n < 2) {
      throw new IllegalArgumentException("At least two points required for regression");
    }
    double sx = 0.0;
    double sy = 0.0;
    double sxx = 0.0;
    double sxy = 0.0;
    for (int i = 0; i < n; i++) {
      sx += x[i];
      sy += y[i];
      sxx += x[i] * x[i];
      sxy += x[i] * y[i];
    }
    double denom = n * sxx - sx * sx;
    if (Math.abs(denom) < 1.0e-30) {
      throw new IllegalArgumentException("Regression is singular (no variation in x)");
    }
    double slope = (n * sxy - sx * sy) / denom;
    double intercept = (sy - slope * sx) / n;
    double meanY = sy / n;
    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      double pred = slope * x[i] + intercept;
      ssRes += (y[i] - pred) * (y[i] - pred);
      ssTot += (y[i] - meanY) * (y[i] - meanY);
    }
    double rSquared = ssTot > 1.0e-30 ? 1.0 - ssRes / ssTot : 1.0;
    return new double[] { slope, intercept, rSquared };
  }

  /**
   * Least-squares regression y = slope*x forced through the origin.
   *
   * @param x independent variable samples
   * @param y dependent variable samples
   * @return two-element array {slope, rSquared}
   */
  static double[] throughOriginRegression(double[] x, double[] y) {
    int n = x.length;
    if (n < 1) {
      throw new IllegalArgumentException("At least one point required for regression");
    }
    double sxx = 0.0;
    double sxy = 0.0;
    double sy = 0.0;
    for (int i = 0; i < n; i++) {
      sxx += x[i] * x[i];
      sxy += x[i] * y[i];
      sy += y[i];
    }
    if (sxx < 1.0e-30) {
      throw new IllegalArgumentException("Regression is singular (no variation in x)");
    }
    double slope = sxy / sxx;
    double meanY = sy / n;
    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      double pred = slope * x[i];
      ssRes += (y[i] - pred) * (y[i] - pred);
      ssTot += (y[i] - meanY) * (y[i] - meanY);
    }
    double rSquared = ssTot > 1.0e-30 ? 1.0 - ssRes / ssTot : 1.0;
    return new double[] { slope, rSquared };
  }

  /**
   * Validate that three arrays are non-null and of equal length.
   *
   * @param a first array
   * @param b second array
   * @param c third array
   */
  private static void validateEqualLength(double[] a, double[] b, double[] c) {
    if (a == null || b == null || c == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    if (a.length != b.length || a.length != c.length) {
      throw new IllegalArgumentException("input arrays must be of equal length");
    }
    if (a.length < 2) {
      throw new IllegalArgumentException("at least two history points are required");
    }
  }
}
