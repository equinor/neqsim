package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;

/**
 * Analytical oil material balance (Havlena-Odeh) for reservoir surveillance.
 *
 * <p>
 * Estimates original oil in place (OOIP), gas-cap size and aquifer support from a measured pressure-versus-production
 * history plus tabulated black-oil PVT. This is the "inverse" companion to the forward tank models and follows the
 * linearised material balance of Havlena and Odeh (1963, 1964).
 * </p>
 *
 * <h2>General material balance</h2>
 *
 * <p>
 * $$ F = N\,(E_o + m\,E_g + E_{fw}) + W_e B_w $$
 * </p>
 *
 * <p>
 * with the reservoir withdrawal, oil/dissolved-gas expansion, gas-cap expansion and connate-water/rock expansion terms
 * </p>
 *
 * <p>
 * $$ F = N_p\,[B_o + (R_p - R_s)B_g] + W_p B_w $$
 * </p>
 * <p>
 * $$ E_o = (B_o - B_{oi}) + (R_{si} - R_s)B_g $$
 * </p>
 * <p>
 * $$ E_g = B_{oi}\left(\frac{B_g}{B_{gi}} - 1\right) $$
 * </p>
 * <p>
 * $$ E_{fw} = B_{oi}(1+m)\frac{c_w S_{wi} + c_f}{1 - S_{wi}}\,\Delta p $$
 * </p>
 *
 * <p>
 * where $N$ is OOIP, $m$ is the ratio of initial gas-cap reservoir volume to initial oil reservoir volume, $W_e$ is
 * cumulative aquifer influx and $\Delta p = p_i - p$.
 * </p>
 *
 * <h2>Drive indices</h2>
 *
 * <p>
 * The fractional drive indices (Pirson) sum to unity and quantify the contribution of each mechanism: depletion drive
 * (DDI), segregation / gas-cap drive (SDI), water drive (WDI) and connate-water/rock expansion drive (EDI).
 * </p>
 *
 * <p>
 * <b>Units:</b> pressures in bara; formation volume factors in reservoir volume per surface volume; solution and
 * producing gas-oil ratios in surface gas volume per surface oil volume; cumulative oil $N_p$ in surface oil volume.
 * The returned OOIP is in surface oil volume.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class OilMaterialBalance implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  private OilMaterialBalance() {
    // Utility class
  }

  /**
   * Result of an oil material balance regression.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Original oil in place (surface oil volume). */
    private final double ooip;
    /** Fitted gas-cap ratio m (dimensionless). */
    private final double m;
    /** Coefficient of determination of the fit. */
    private final double rSquared;

    /**
     * Constructor.
     *
     * @param ooip original oil in place
     * @param m gas-cap ratio (dimensionless)
     * @param rSquared coefficient of determination
     */
    public Result(double ooip, double m, double rSquared) {
      this.ooip = ooip;
      this.m = m;
      this.rSquared = rSquared;
    }

    /**
     * Original oil in place.
     *
     * @return OOIP in surface oil volume
     */
    public double getOoip() {
      return ooip;
    }

    /**
     * Fitted gas-cap ratio.
     *
     * @return m (initial gas-cap volume / initial oil volume)
     */
    public double getM() {
      return m;
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

  // ============================================================
  // Material balance terms
  // ============================================================

  /**
   * Reservoir voidage (withdrawal) term F.
   *
   * @param np cumulative oil produced (surface oil volume)
   * @param rp cumulative producing gas-oil ratio (surface gas / surface oil)
   * @param bo oil formation volume factor at current pressure (rvol/svol)
   * @param rs solution gas-oil ratio at current pressure (surface gas / surface oil)
   * @param bg gas formation volume factor at current pressure (rvol/svol gas)
   * @param wp cumulative water produced (surface water volume)
   * @param bw water formation volume factor (rvol/svol)
   * @return reservoir withdrawal F (reservoir volume)
   */
  public static double withdrawalF(double np, double rp, double bo, double rs, double bg, double wp, double bw) {
    return np * (bo + (rp - rs) * bg) + wp * bw;
  }

  /**
   * Oil and dissolved-gas expansion term E_o.
   *
   * @param bo oil FVF at current pressure (rvol/svol)
   * @param boi initial oil FVF (rvol/svol)
   * @param rsi initial solution gas-oil ratio (surface gas / surface oil)
   * @param rs solution gas-oil ratio at current pressure (surface gas / surface oil)
   * @param bg gas FVF at current pressure (rvol/svol gas)
   * @return oil expansion term E_o (reservoir volume per surface oil volume)
   */
  public static double eo(double bo, double boi, double rsi, double rs, double bg) {
    return (bo - boi) + (rsi - rs) * bg;
  }

  /**
   * Gas-cap expansion term E_g.
   *
   * @param boi initial oil FVF (rvol/svol)
   * @param bg gas FVF at current pressure (rvol/svol gas)
   * @param bgi initial gas FVF (rvol/svol gas)
   * @return gas-cap expansion term E_g (reservoir volume per surface oil volume)
   */
  public static double eg(double boi, double bg, double bgi) {
    return boi * (bg / bgi - 1.0);
  }

  /**
   * Connate-water and rock (formation) expansion term E_fw.
   *
   * @param boi initial oil FVF (rvol/svol)
   * @param m gas-cap ratio (dimensionless)
   * @param cw water compressibility (1/bar)
   * @param swi initial water saturation (fraction)
   * @param cf formation (pore) compressibility (1/bar)
   * @param deltaP pressure drop p_i - p (bar)
   * @return connate-water/rock expansion term E_fw (reservoir volume per surface oil volume)
   */
  public static double efw(double boi, double m, double cw, double swi, double cf, double deltaP) {
    return boi * (1.0 + m) * (cw * swi + cf) / (1.0 - swi) * deltaP;
  }

  // ============================================================
  // Regression forms
  // ============================================================

  /**
   * Depletion-drive OOIP: regress F = N*E_o through the origin.
   *
   * <p>
   * Applicable to an undersaturated reservoir with no gas cap and no water drive (rock/water expansion optionally
   * folded into E_o via {@link #efw}).
   * </p>
   *
   * @param f array of reservoir withdrawal F at each history point
   * @param eo array of oil expansion E_o at each history point
   * @return {@link Result} with OOIP; m = 0
   */
  public static Result fitDepletionDrive(double[] f, double[] eo) {
    validateEqualLength(f, eo);
    double[] fit = throughOriginRegression(eo, f);
    return new Result(fit[0], 0.0, fit[1]);
  }

  /**
   * Gas-cap OOIP: regress F = N*E_o + N*m*E_g for both N and m.
   *
   * <p>
   * Uses a two-predictor least-squares fit through the origin with predictors E_o and E_g. The gas-cap ratio is
   * recovered as $m = (N m)/N$.
   * </p>
   *
   * @param f array of reservoir withdrawal F at each history point
   * @param eo array of oil expansion E_o at each history point
   * @param eg array of gas-cap expansion E_g at each history point
   * @return {@link Result} with OOIP and gas-cap ratio m
   */
  public static Result fitGasCapDrive(double[] f, double[] eo, double[] eg) {
    validateEqualLength(f, eo);
    if (eg == null || eg.length != f.length) {
      throw new IllegalArgumentException("eg array must match length");
    }
    double[] coef = twoPredictorThroughOrigin(eo, eg, f);
    double n = coef[0];
    double nm = coef[1];
    double m = Math.abs(n) > 1.0e-30 ? nm / n : 0.0;
    double rSquared = twoPredictorRSquared(eo, eg, f, n, nm);
    return new Result(n, m, rSquared);
  }

  /**
   * Water-drive OOIP: regress F/E_o = N + (W_e B_w)/E_o for N with a known aquifer influx.
   *
   * <p>
   * The slope of a plot of $F/E_o$ against $W_e B_w/E_o$ is theoretically unity; the intercept is the OOIP $N$.
   * </p>
   *
   * @param f array of reservoir withdrawal F at each history point
   * @param eo array of oil expansion E_o at each history point
   * @param we array of cumulative aquifer influx at each history point (reservoir volume)
   * @param bw water formation volume factor (rvol/svol)
   * @return {@link Result} with OOIP; m = 0
   */
  public static Result fitWaterDrive(double[] f, double[] eo, double[] we, double bw) {
    validateEqualLength(f, eo);
    if (we == null || we.length != f.length) {
      throw new IllegalArgumentException("we array must match length");
    }
    int n = f.length;
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      if (Math.abs(eo[i]) < 1.0e-30) {
        throw new IllegalArgumentException("E_o must be non-zero for the water-drive plot at index " + i);
      }
      x[i] = we[i] * bw / eo[i];
      y[i] = f[i] / eo[i];
    }
    double[] fit = GasMaterialBalance.linearRegression(x, y);
    // intercept is OOIP
    return new Result(fit[1], 0.0, fit[2]);
  }

  /**
   * Compute fractional drive indices at a single history point.
   *
   * <p>
   * Returns {DDI, SDI, WDI, EDI} = depletion, segregation (gas cap), water and expansion (rock/water) drive indices,
   * each the fraction of reservoir withdrawal supplied by that mechanism. They sum to approximately unity.
   * </p>
   *
   * @param n OOIP (surface oil volume)
   * @param m gas-cap ratio (dimensionless)
   * @param eoTerm oil expansion E_o at the point
   * @param egTerm gas-cap expansion E_g at the point
   * @param efwTerm connate-water/rock expansion E_fw at the point
   * @param we cumulative aquifer influx at the point (reservoir volume)
   * @param bw water formation volume factor (rvol/svol)
   * @param f reservoir withdrawal F at the point (reservoir volume)
   * @return four-element array {DDI, SDI, WDI, EDI}
   */
  public static double[] driveIndices(double n, double m, double eoTerm, double egTerm, double efwTerm, double we,
      double bw, double f) {
    if (Math.abs(f) < 1.0e-30) {
      return new double[] { 0.0, 0.0, 0.0, 0.0 };
    }
    double ddi = n * eoTerm / f;
    double sdi = n * m * egTerm / f;
    double wdi = we * bw / f;
    double edi = n * efwTerm / f;
    return new double[] { ddi, sdi, wdi, edi };
  }

  // ============================================================
  // Regression helpers
  // ============================================================

  /**
   * Least-squares regression y = slope*x forced through the origin.
   *
   * @param x independent variable samples
   * @param y dependent variable samples
   * @return two-element array {slope, rSquared}
   */
  static double[] throughOriginRegression(double[] x, double[] y) {
    return GasMaterialBalance.throughOriginRegression(x, y);
  }

  /**
   * Two-predictor least-squares fit y = a*x1 + b*x2 through the origin via normal equations.
   *
   * @param x1 first predictor samples
   * @param x2 second predictor samples
   * @param y response samples
   * @return two-element array {a, b}
   */
  static double[] twoPredictorThroughOrigin(double[] x1, double[] x2, double[] y) {
    int n = x1.length;
    double s11 = 0.0;
    double s12 = 0.0;
    double s22 = 0.0;
    double s1y = 0.0;
    double s2y = 0.0;
    for (int i = 0; i < n; i++) {
      s11 += x1[i] * x1[i];
      s12 += x1[i] * x2[i];
      s22 += x2[i] * x2[i];
      s1y += x1[i] * y[i];
      s2y += x2[i] * y[i];
    }
    double det = s11 * s22 - s12 * s12;
    if (Math.abs(det) < 1.0e-30) {
      throw new IllegalArgumentException("Two-predictor regression is singular (collinear predictors)");
    }
    double a = (s1y * s22 - s2y * s12) / det;
    double b = (s11 * s2y - s12 * s1y) / det;
    return new double[] { a, b };
  }

  /**
   * Coefficient of determination for the two-predictor through-origin fit.
   *
   * @param x1 first predictor samples
   * @param x2 second predictor samples
   * @param y response samples
   * @param a first coefficient
   * @param b second coefficient
   * @return coefficient of determination (R^2)
   */
  static double twoPredictorRSquared(double[] x1, double[] x2, double[] y, double a, double b) {
    int n = x1.length;
    double sy = 0.0;
    for (int i = 0; i < n; i++) {
      sy += y[i];
    }
    double meanY = sy / n;
    double ssRes = 0.0;
    double ssTot = 0.0;
    for (int i = 0; i < n; i++) {
      double pred = a * x1[i] + b * x2[i];
      ssRes += (y[i] - pred) * (y[i] - pred);
      ssTot += (y[i] - meanY) * (y[i] - meanY);
    }
    return ssTot > 1.0e-30 ? 1.0 - ssRes / ssTot : 1.0;
  }

  /**
   * Validate that two arrays are non-null and of equal length with enough points.
   *
   * @param a first array
   * @param b second array
   */
  private static void validateEqualLength(double[] a, double[] b) {
    if (a == null || b == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    if (a.length != b.length) {
      throw new IllegalArgumentException("input arrays must be of equal length");
    }
    if (a.length < 2) {
      throw new IllegalArgumentException("at least two history points are required");
    }
  }
}
