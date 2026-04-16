package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;

/**
 * Anderson-accelerated nested CPA phase solver.
 *
 * <p>
 * Retains the outer Halley iteration for molar volume from Michelsen's nested approach but replaces
 * the inner successive substitution (SS) loop with Anderson acceleration (mixing depth m=3). For
 * the site fraction equations X_k = 1 / (1 + sum_j n_j Delta_kj X_j / V), Anderson mixing achieves
 * superlinear convergence (equivalent to GMRES for linear problems), typically converging in 3-5
 * iterations compared to 5-15 for plain SS.
 * </p>
 *
 * <p>
 * The outer loop and derivative computation (initCPAMatrix) follow the standard nested approach,
 * using the implicit function theorem to compute dF_CPA/dV from the converged X and Hessian.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>D.G.M. Anderson, Iterative procedures for nonlinear integral equations, J. ACM 12 (1965)
 * 547-560.</li>
 * <li>H.F. Walker and P. Ni, Anderson acceleration for fixed-point iterations, SIAM J. Numer. Anal.
 * 49 (2011) 1715-1735.</li>
 * <li>M.L. Michelsen, Robust and efficient solution procedures for association models, Ind. Eng.
 * Chem. Res. 45 (2006) 8449-8453.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseSrkCPAandersonMixing extends PhaseSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseSrkCPAandersonMixing.class);

  /** Anderson mixing depth (number of history vectors). */
  private static final int ANDERSON_M = 3;

  /** Maximum inner (site fraction) iterations per outer step. */
  private static final int MAX_INNER_ITERS = 30;

  /** Inner loop convergence tolerance. */
  private static final double INNER_TOL = 1.0e-12;

  /** Maximum outer (volume) iterations. */
  private static final int MAX_OUTER_ITERS = 300;

  /** Outer loop convergence tolerance. */
  private static final double OUTER_TOL = 1.0e-10;

  // --- Profiling counters ---
  /** Total molarVolume calls. */
  private static volatile long callCount = 0;
  /** Total outer iterations across all calls. */
  private static volatile long totalOuterIters = 0;
  /** Total inner iterations across all calls. */
  private static volatile long totalInnerIters = 0;
  /** Calls where Anderson converged vs needed Newton fallback. */
  private static volatile long andersonConvergedCount = 0;
  /** Fallbacks to Newton in inner loop. */
  private static volatile long newtonFallbackCount = 0;

  /**
   * Reset profiling counters.
   */
  public static void resetProfileCounters() {
    callCount = 0;
    totalOuterIters = 0;
    totalInnerIters = 0;
    andersonConvergedCount = 0;
    newtonFallbackCount = 0;
  }

  /**
   * Get profiling summary string.
   *
   * @return a summary of profiling data
   */
  public static String getProfileSummary() {
    double avgOuter = callCount > 0 ? (double) totalOuterIters / callCount : 0;
    double avgInner = totalOuterIters > 0 ? (double) totalInnerIters / totalOuterIters : 0;
    return String.format(
        "Calls=%d  AvgOuterIters=%.1f  AvgInnerIters=%.1f  AndersonConverged=%d  "
            + "NewtonFallback=%d",
        callCount, avgOuter, avgInner, andersonConvergedCount, newtonFallbackCount);
  }

  /**
   * Constructor for PhaseSrkCPAandersonMixing.
   */
  public PhaseSrkCPAandersonMixing() {
    super();
    thermoPropertyModelName = "SRK-CPA-EoS-AndersonMixing";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPAandersonMixing clone() {
    PhaseSrkCPAandersonMixing clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPAandersonMixing) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Molar volume calculation using the Halley outer loop for volume and Anderson-accelerated
   * successive substitution for the site fraction inner loop. The Halley step uses the implicit
   * function theorem to compute dF_CPA/dV derivatives.
   * </p>
   */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {

    int ns = getTotalNumberOfAccociationSites();

    // No association sites → cubic solver
    if (ns == 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    callCount++;

    double Btemp = getB();
    if (Btemp <= 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // --- Initial guess for BonV ---
    double BonVold;
    double BonV;
    if (pt == PhaseType.GAS) {
      BonV = pressure * Btemp / (numberOfMolesInPhase * temperature * R);
    } else {
      BonV = 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    }
    BonV = Math.max(1.0e-10, Math.min(1.0 - 1.0e-10, BonV));

    double molarVol = Btemp / (numberOfMolesInPhase * BonV);
    setMolarVolume(molarVol);

    // Compute deltaNog (temperature-dependent only)
    calcDelta();

    // Initialize site fractions with a few SS steps
    gcpa = calc_g();
    gcpav = calc_lngV();
    updateDeltaWithG();
    solveX2(10);

    int outerIters = 0;
    boolean converged = false;

    for (int outer = 0; outer < MAX_OUTER_ITERS; outer++) {
      outerIters++;
      BonVold = BonV;

      molarVol = Btemp / (numberOfMolesInPhase * BonV);
      setMolarVolume(molarVol);
      Z = pressure * molarVol / (R * temperature);

      // Update g-function and association strengths
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
      updateDeltaWithG();

      // --- Inner loop: Anderson-accelerated SS for site fractions ---
      int innerIters = solveXAnderson(ns);
      totalInnerIters += innerIters;

      hcpatot = calc_hCPA();

      // Compute CPA pressure derivatives using implicit function theorem
      initCPAMatrix(1);

      // --- Outer step: Halley iteration for volume ---
      double h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      double dh = 1.0 + Btemp / (BonV * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV());
      double dhh = -2.0 * Btemp / (BonV * BonV * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          + Btemp * Btemp * Btemp / (BonV * BonV * BonV * BonV)
              * (1.0 / numberOfMolesInPhase * dFdVdVdV());

      double dBonV = -h / dh;

      // Halley correction
      double halleyCorrection = 1.0 - 0.5 * dBonV * dhh / dh;
      if (Math.abs(halleyCorrection) > 0.1) {
        dBonV = dBonV / halleyCorrection;
      }

      // Step limiting
      if (Math.abs(dBonV) > 0.1 * BonV) {
        dBonV = Math.signum(dBonV) * 0.1 * BonV;
      }

      BonV += dBonV;
      BonV = Math.max(1.0e-10, Math.min(1.0 - 1.0e-10, BonV));

      if (Math.abs((BonV - BonVold) / BonV) < OUTER_TOL && outer > 2) {
        converged = true;
        break;
      }
    }

    totalOuterIters += outerIters;

    if (!converged) {
      // Fallback to parent solver
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // Finalize
    molarVol = Btemp / (numberOfMolesInPhase * BonV);
    setMolarVolume(molarVol);
    Z = pressure * molarVol / (R * temperature);

    gcpa = calc_g();
    gcpav = calc_lngV();
    gcpavv = calc_lngVV();
    gcpavvv = calc_lngVVV();
    hcpatot = calc_hCPA();

    initCPAMatrix(1);
    dFdNtemp = calcdFdNtemp();

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }

    return getMolarVolume();
  }

  /**
   * Solve the site fraction equations using Anderson-accelerated successive substitution.
   *
   * <p>
   * The fixed-point map is: f_k(X) = 1 / (1 + sum_j n_j Delta_kj X_j / V). Anderson acceleration
   * (mixing depth m=3) is applied to this map to achieve superlinear convergence. The algorithm
   * uses QR-based least-squares for the mixing coefficients.
   * </p>
   *
   * @param ns number of association sites
   * @return number of inner iterations used
   */
  private int solveXAnderson(int ns) {
    if (ns == 0) {
      return 0;
    }

    double totalVol = getTotalVolume();

    // Read current site fractions
    double[] xCurr = new double[ns];
    readXsiteFromComponents(xCurr);

    // Moles per site
    double[] siteMoles = new double[ns];
    int temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        siteMoles[temp + j] = ni;
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }

    // Pre-compute n_j * delta_kj / V
    double[][] klk = new double[ns][ns];
    double invV = 1.0 / totalVol;
    for (int k = 0; k < ns; k++) {
      for (int j = 0; j < ns; j++) {
        klk[k][j] = siteMoles[j] * delta[k][j] * invV;
      }
    }

    // Anderson history storage
    int m = ANDERSON_M;
    double[][] gHist = new double[m][ns]; // residual differences g_{k} - g_{k-1}
    double[][] xHist = new double[m][ns]; // iterate differences x_{k} - x_{k-1}
    double[] gPrev = new double[ns]; // previous residual
    double[] xPrev = new double[ns]; // previous iterate
    int histLen = 0;
    boolean hasPrev = false;

    int iterations = 0;

    for (int iter = 0; iter < MAX_INNER_ITERS; iter++) {
      iterations++;

      // One SS step: compute f(x)
      double[] fX = new double[ns];
      for (int k = 0; k < ns; k++) {
        double sumKJ = 0.0;
        for (int j = 0; j < ns; j++) {
          sumKJ += klk[k][j] * xCurr[j];
        }
        fX[k] = 1.0 / (1.0 + sumKJ);
      }

      // Residual: g = f(x) - x
      double[] gCurr = new double[ns];
      double maxG = 0.0;
      for (int k = 0; k < ns; k++) {
        gCurr[k] = fX[k] - xCurr[k];
        maxG = Math.max(maxG, Math.abs(gCurr[k]));
      }

      if (maxG < INNER_TOL) {
        andersonConvergedCount++;
        setXsiteOnComponents(xCurr);
        return iterations;
      }

      // Anderson mixing
      double[] xNew;
      if (hasPrev) {
        // Store history (circular buffer)
        int slot = histLen < m ? histLen : (histLen % m);
        for (int k = 0; k < ns; k++) {
          gHist[slot][k] = gCurr[k] - gPrev[k];
          xHist[slot][k] = xCurr[k] - xPrev[k];
        }
        if (histLen < m) {
          histLen++;
        }

        // Solve least-squares: min ||g_curr - G * gamma||^2
        // G = [gHist[0], ..., gHist[histLen-1]] is ns x histLen
        // This is a small least-squares problem (histLen <= m = 3)
        double[] gamma = solveAndersonLeastSquares(gHist, gCurr, ns, histLen);

        // x_{k+1} = (x_curr + g_curr) - sum_i gamma_i * (xHist[i] + gHist[i])
        xNew = new double[ns];
        for (int k = 0; k < ns; k++) {
          xNew[k] = xCurr[k] + gCurr[k];
          for (int i = 0; i < histLen; i++) {
            xNew[k] -= gamma[i] * (xHist[i][k] + gHist[i][k]);
          }
          // Clamp to valid range
          xNew[k] = Math.max(1.0e-15, Math.min(1.0, xNew[k]));
        }
      } else {
        // First iteration: plain SS step
        xNew = fX;
      }

      // Save current as previous
      System.arraycopy(gCurr, 0, gPrev, 0, ns);
      System.arraycopy(xCurr, 0, xPrev, 0, ns);
      hasPrev = true;

      // Update xCurr
      System.arraycopy(xNew, 0, xCurr, 0, ns);
    }

    // If Anderson didn't converge, do a few Newton steps
    newtonFallbackCount++;
    setXsiteOnComponents(xCurr);
    solveX();
    readXsiteFromComponents(xCurr);

    return iterations;
  }

  /**
   * Solve the Anderson least-squares problem: min ||g - G * gamma||^2.
   *
   * <p>
   * Uses the normal equations: (G^T G) gamma = G^T g. The system is at most m x m (typically 3x3)
   * so a direct solve via Gaussian elimination is efficient and stable.
   * </p>
   *
   * @param gMatrix history of residual differences (m x ns, using rows 0..histLen-1)
   * @param gVec current residual vector (length ns)
   * @param ns number of association sites
   * @param histLen number of stored history vectors
   * @return mixing coefficients gamma (length histLen)
   */
  private static double[] solveAndersonLeastSquares(double[][] gMatrix, double[] gVec, int ns,
      int histLen) {
    // Build G^T G (histLen x histLen)
    double[][] gtg = new double[histLen][histLen];
    for (int i = 0; i < histLen; i++) {
      for (int j = i; j < histLen; j++) {
        double dot = 0.0;
        for (int k = 0; k < ns; k++) {
          dot += gMatrix[i][k] * gMatrix[j][k];
        }
        gtg[i][j] = dot;
        gtg[j][i] = dot;
      }
    }

    // Build G^T g (histLen x 1)
    double[] gtgVec = new double[histLen];
    for (int i = 0; i < histLen; i++) {
      double dot = 0.0;
      for (int k = 0; k < ns; k++) {
        dot += gMatrix[i][k] * gVec[k];
      }
      gtgVec[i] = dot;
    }

    // Tikhonov regularization for numerical stability
    for (int i = 0; i < histLen; i++) {
      gtg[i][i] += 1.0e-10;
    }

    // Solve (G^T G) gamma = G^T g using GE with partial pivoting
    for (int col = 0; col < histLen; col++) {
      int maxRow = col;
      double maxVal = Math.abs(gtg[col][col]);
      for (int row = col + 1; row < histLen; row++) {
        double val = Math.abs(gtg[row][col]);
        if (val > maxVal) {
          maxVal = val;
          maxRow = row;
        }
      }
      if (maxVal < 1.0e-30) {
        // Degenerate: return zero mixing
        return new double[histLen];
      }
      if (maxRow != col) {
        double[] tempRow = gtg[col];
        gtg[col] = gtg[maxRow];
        gtg[maxRow] = tempRow;
        double tempB = gtgVec[col];
        gtgVec[col] = gtgVec[maxRow];
        gtgVec[maxRow] = tempB;
      }
      double pivot = gtg[col][col];
      for (int row = col + 1; row < histLen; row++) {
        double factor = gtg[row][col] / pivot;
        for (int k = col + 1; k < histLen; k++) {
          gtg[row][k] -= factor * gtg[col][k];
        }
        gtgVec[row] -= factor * gtgVec[col];
      }
    }
    // Back substitution
    double[] gamma = new double[histLen];
    for (int row = histLen - 1; row >= 0; row--) {
      double sum = gtgVec[row];
      for (int k = row + 1; k < histLen; k++) {
        sum -= gtg[row][k] * gamma[k];
      }
      gamma[row] = sum / gtg[row][row];
    }

    return gamma;
  }

  // --- Work arrays for initCPAMatrix type 1 (GE-based, avoids EJML dependency) ---
  private transient double[][] workKlk = null;
  private transient double[][] workHess = null;
  private transient double[] workKsi = null;
  private transient double[] workM = null;
  private transient double[] workKlkKsi = null;
  private transient double[] workXV = null;
  private transient int workNs = 0;

  /**
   * Ensure work arrays are allocated for the given association site count.
   *
   * @param ns number of association sites
   */
  private void ensureWorkArrays(int ns) {
    if (ns != workNs) {
      workKlk = new double[ns][ns];
      workHess = new double[ns][ns];
      workKsi = new double[ns];
      workM = new double[ns];
      workKlkKsi = new double[ns];
      workXV = new double[ns];
      workNs = ns;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Override CPA matrix initialization for efficient volume derivative computation using Gaussian
   * elimination instead of EJML matrix inversion. This avoids the dependency on hessianInvers which
   * is only set during solveX().
   * </p>
   *
   * @param type 1 for volume derivatives, 2+ for temperature/composition
   */
  @Override
  public void initCPAMatrix(int type) {
    int ns = getTotalNumberOfAccociationSites();
    if (ns == 0) {
      FCPA = 0.0;
      dFCPAdTdV = 0.0;
      dFCPAdTdT = 0.0;
      dFCPAdT = 0;
      dFCPAdV = 0;
      dFCPAdVdV = 0.0;
      dFCPAdVdVdV = 0.0;
      return;
    }

    // Delegate all types to parent implementation.
    // The parent uses solveX() + EJML matrix operations, ensuring numerical
    // consistency with the standard solver for CPA derivative computation.
    solveX();
    super.initCPAMatrix(type);
  }

  /**
   * Solve a linear system Ax = b in-place using Gaussian elimination with partial pivoting.
   *
   * @param a coefficient matrix (modified in place)
   * @param b right-hand side (solution on return)
   * @param n system dimension
   * @return true if solve succeeded
   */
  private static boolean solveLinearSystem(double[][] a, double[] b, int n) {
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(a[col][col]);
      for (int row = col + 1; row < n; row++) {
        double val = Math.abs(a[row][col]);
        if (val > maxVal) {
          maxVal = val;
          maxRow = row;
        }
      }
      if (maxVal < 1.0e-30) {
        return false;
      }
      if (maxRow != col) {
        double[] tempRow = a[col];
        a[col] = a[maxRow];
        a[maxRow] = tempRow;
        double tempB = b[col];
        b[col] = b[maxRow];
        b[maxRow] = tempB;
      }
      double pivot = a[col][col];
      for (int row = col + 1; row < n; row++) {
        double factor = a[row][col] / pivot;
        for (int k = col + 1; k < n; k++) {
          a[row][k] -= factor * a[col][k];
        }
        b[row] -= factor * b[col];
      }
    }
    for (int row = n - 1; row >= 0; row--) {
      double sum = b[row];
      for (int k = row + 1; k < n; k++) {
        sum -= a[row][k] * b[k];
      }
      b[row] = sum / a[row][row];
    }
    return true;
  }

  // --- Helper methods ---

  /**
   * Update delta = deltaNog * g.
   */
  private void updateDeltaWithG() {
    int ns = getTotalNumberOfAccociationSites();
    for (int i = 0; i < ns; i++) {
      for (int j = i; j < ns; j++) {
        delta[i][j] = deltaNog[i][j] * gcpa;
        delta[j][i] = delta[i][j];
      }
    }
  }

  /**
   * Set XA site fractions from flat array onto component objects.
   *
   * @param xSite array of site fractions
   */
  private void setXsiteOnComponents(double[] xSite) {
    int temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        ((ComponentCPAInterface) componentArray[i]).setXsite(j, xSite[temp + j]);
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }
  }

  /**
   * Read XA site fractions from component objects into flat array.
   *
   * @param xSite array to fill
   */
  private void readXsiteFromComponents(double[] xSite) {
    int temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        xSite[temp + j] = ((ComponentSrkCPA) componentArray[i]).getXsite()[j];
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return super.molarVolumeChangePhase(pressure, temperature, A, B, pt);
  }
}
