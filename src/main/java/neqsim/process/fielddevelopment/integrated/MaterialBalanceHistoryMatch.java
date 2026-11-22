package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.Arrays;

/**
 * History matching of a gas material balance from pressure-proxy points.
 *
 * <p>
 * Static shut-in wellhead pressures are converted to bottom-hole pressure with {@link #staticBottomholePressure}, then
 * a straight line {@code p/z = a - b Gp} is fitted with iterative outlier rejection. The intercept gives the initial
 * p/z and {@code a / b} the gas initially in place. Shut-in wellhead readings taken before a well has stabilised are
 * typical outliers, which is why rejection is built in.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see RealGasMaterialBalanceDrive
 */
public final class MaterialBalanceHistoryMatch {

  /** Result of a p/z line fit. */
  public static final class PzFit implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double intercept;
    private final double slope;
    private final double rms;
    private final boolean[] kept;

    /**
     * Creates a fit result.
     *
     * @param intercept initial p/z in bara
     * @param slope p/z decline per unit cumulative production (bara per Sm3)
     * @param rms root-mean-square residual of the kept points in bara
     * @param kept mask of points used in the final fit
     */
    PzFit(double intercept, double slope, double rms, boolean[] kept) {
      this.intercept = intercept;
      this.slope = slope;
      this.rms = rms;
      this.kept = kept;
    }

    /**
     * Returns the initial p/z.
     *
     * @return intercept in bara
     */
    public double getInitialPOverZ() {
      return intercept;
    }

    /**
     * Returns the gas initially in place.
     *
     * @return GIIP in the same volume unit as the cumulative production input
     */
    public double getGiip() {
      return intercept / slope;
    }

    /**
     * Returns the fit residual.
     *
     * @return rms residual in bara
     */
    public double getRmsResidual() {
      return rms;
    }

    /**
     * Returns which points were kept.
     *
     * @return copy of the keep mask
     */
    public boolean[] getKept() {
      return Arrays.copyOf(kept, kept.length);
    }

    /**
     * Returns the number of points kept.
     *
     * @return count of points used in the final fit
     */
    public int getKeptCount() {
      int n = 0;
      for (boolean k : kept) {
        if (k) {
          n++;
        }
      }
      return n;
    }
  }

  /** Utility class; not instantiable. */
  private MaterialBalanceHistoryMatch() {
  }

  /**
   * Converts a shut-in wellhead pressure to bottom-hole pressure with the static gas-column equation.
   *
   * @param wellheadPressureBarg shut-in wellhead pressure in barg
   * @param molarMassKgPerMol gas molar mass in kg/mol
   * @param trueVerticalDepthM true vertical depth of the gauge or mid-perforation in m
   * @param meanTemperatureK mean column temperature in K
   * @param z deviation-factor model
   * @return bottom-hole pressure in bara
   */
  public static double staticBottomholePressure(double wellheadPressureBarg, double molarMassKgPerMol,
      double trueVerticalDepthM, double meanTemperatureK, GasZFactor z) {
    double pwh = wellheadPressureBarg + 1.01325;
    double pbh = pwh;
    for (int i = 0; i < 40; i++) {
      double zBar = z.z(0.5 * (pwh + pbh), meanTemperatureK);
      pbh = pwh * Math.exp(molarMassKgPerMol * 9.80665 * trueVerticalDepthM / (zBar * 8.314462618 * meanTemperatureK));
    }
    return pbh;
  }

  /**
   * Fits {@code p/z = a - b Gp} with iterative sigma-clipping.
   *
   * @param cumulativeProduction cumulative production at each point
   * @param pOverZ p/z in bara at each point
   * @param rejectSigma points further than this many standard deviations from the line are dropped and the fit repeated
   * @return the fit
   */
  public static PzFit fitPzLine(double[] cumulativeProduction, double[] pOverZ, double rejectSigma) {
    int n = cumulativeProduction.length;
    if (n != pOverZ.length || n < 3) {
      throw new IllegalArgumentException("need at least three points of equal length");
    }
    boolean[] keep = new boolean[n];
    Arrays.fill(keep, true);
    double a = 0.0;
    double b = 0.0;
    double rms = 0.0;
    for (int iter = 0; iter < 8; iter++) {
      double sx = 0.0;
      double sy = 0.0;
      double sxx = 0.0;
      double sxy = 0.0;
      int m = 0;
      for (int i = 0; i < n; i++) {
        if (keep[i]) {
          sx += cumulativeProduction[i];
          sy += pOverZ[i];
          sxx += cumulativeProduction[i] * cumulativeProduction[i];
          sxy += cumulativeProduction[i] * pOverZ[i];
          m++;
        }
      }
      double slopeLs = (m * sxy - sx * sy) / (m * sxx - sx * sx);
      a = (sy - slopeLs * sx) / m;
      b = -slopeLs;
      double ss = 0.0;
      double[] res = new double[n];
      for (int i = 0; i < n; i++) {
        res[i] = pOverZ[i] - (a - b * cumulativeProduction[i]);
        if (keep[i]) {
          ss += res[i] * res[i];
        }
      }
      rms = Math.sqrt(ss / m);
      boolean changed = false;
      double limit = rejectSigma * Math.max(rms, 1.0e-12);
      for (int i = 0; i < n; i++) {
        boolean k = Math.abs(res[i]) < limit;
        if (k != keep[i]) {
          keep[i] = k;
          changed = true;
        }
      }
      if (!changed) {
        break;
      }
    }
    return new PzFit(a, b, rms, keep);
  }
}
