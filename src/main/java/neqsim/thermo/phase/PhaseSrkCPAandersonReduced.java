package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;

/**
 * Anderson-accelerated nested CPA phase solver with site symmetry reduction.
 *
 * <p>
 * Combines two orthogonal acceleration strategies:
 * </p>
 * <ol>
 * <li><b>Site symmetry reduction</b>: groups equivalent association sites (same bonding pattern on
 * the same component) into unique site types, reducing the inner loop dimension from n_s to p.</li>
 * <li><b>Anderson acceleration</b>: replaces successive substitution with Anderson mixing (depth
 * m=3) on the reduced p-dimensional site fraction vector, achieving superlinear convergence.</li>
 * </ol>
 *
 * <p>
 * The outer Halley iteration for molar volume and the volume derivative computation
 * ({@code initCPAMatrix(1)}) also use the reduced dimension p, reducing the Hessian linear system
 * from O(n_s^3) to O(p^3).
 * </p>
 *
 * <p>
 * This is a <b>nested-family</b> solver: site fractions are fully converged at each volume step
 * before computing exact volume derivatives via the implicit function theorem. It therefore avoids
 * the coupled-family equilibrium sensitivity documented for the Broyden and fully implicit solvers.
 * </p>
 *
 * <p>
 * Dimension reduction examples:
 * </p>
 *
 * <table>
 * <caption>Dimension reduction for common CPA systems</caption>
 * <tr>
 * <th>System</th>
 * <th>n_s (full)</th>
 * <th>p (reduced)</th>
 * <th>Reduction</th>
 * </tr>
 * <tr>
 * <td>Pure water (4C)</td>
 * <td>4</td>
 * <td>2</td>
 * <td>50%</td>
 * </tr>
 * <tr>
 * <td>Water + methanol (4C+2B)</td>
 * <td>6</td>
 * <td>4</td>
 * <td>33%</td>
 * </tr>
 * <tr>
 * <td>Water + MEG (4C+4C)</td>
 * <td>8</td>
 * <td>4</td>
 * <td>50%</td>
 * </tr>
 * <tr>
 * <td>NG + water + TEG (4C+4C)</td>
 * <td>8</td>
 * <td>4</td>
 * <td>50%</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseSrkCPAandersonReduced extends PhaseSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseSrkCPAandersonReduced.class);

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

  // --- Site type mapping (transient, rebuilt each molarVolume call) ---
  /** Number of unique site types for the current system. */
  private transient int numTypes = -1;
  /** Maps individual site index to type index. */
  private transient int[] siteToType;
  /** Representative individual site index for each type. */
  private transient int[] typeRepSite;
  /** Multiplicity (count of equivalent sites) for each type. */
  private transient int[] typeMult;
  /** Component index for each type. */
  private transient int[] typeCompIdx;

  // --- Work arrays for initCPAMatrix(1) ---
  private transient double[][] workKlk = null;
  private transient double[][] workHess = null;
  private transient double[] workKsi = null;
  private transient double[] workM = null;
  private transient double[] workKlkKsi = null;
  private transient double[] workXV = null;
  private transient int workP = 0;

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
            + "NewtonFallback=%d  NumTypes(last)=%d",
        callCount, avgOuter, avgInner, andersonConvergedCount, newtonFallbackCount,
        callCount > 0 ? 0 : -1);
  }

  /**
   * Get total call count.
   *
   * @return number of molarVolume calls
   */
  public static long getCallCount() {
    return callCount;
  }

  /**
   * Get total Anderson converged count.
   *
   * @return number of inner loops converged by Anderson
   */
  public static long getAndersonConvergedCount() {
    return andersonConvergedCount;
  }

  /**
   * Get total Newton fallback count.
   *
   * @return number of inner loops that fell back to Newton
   */
  public static long getNewtonFallbackCount() {
    return newtonFallbackCount;
  }

  /**
   * Constructor for PhaseSrkCPAandersonReduced.
   */
  public PhaseSrkCPAandersonReduced() {
    super();
    thermoPropertyModelName = "SRK-CPA-EoS-AndersonReduced";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPAandersonReduced clone() {
    PhaseSrkCPAandersonReduced clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPAandersonReduced) super.clone();
      // Transient fields are rebuilt as needed
      clonedPhase.numTypes = -1;
      clonedPhase.siteToType = null;
      clonedPhase.typeRepSite = null;
      clonedPhase.typeMult = null;
      clonedPhase.typeCompIdx = null;
      clonedPhase.workKlk = null;
      clonedPhase.workHess = null;
      clonedPhase.workKsi = null;
      clonedPhase.workM = null;
      clonedPhase.workKlkKsi = null;
      clonedPhase.workXV = null;
      clonedPhase.workP = 0;
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
   * successive substitution on <b>reduced</b> site type fractions. The Halley step uses the
   * implicit function theorem to compute dF_CPA/dV derivatives in the reduced p-dimensional space.
   * </p>
   */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {

    int ns = getTotalNumberOfAccociationSites();

    // No association sites: use cubic solver
    if (ns == 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // Build site type map early so initCPAMatrix(1) works even for gas-phase fallback.
    calcDelta();
    buildSiteTypeMap(ns);

    // If no reduction possible, fall back to the unreduced Anderson solver path from parent
    if (numTypes == ns || numTypes < 1) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    callCount++;

    double Btemp = getB();
    if (Btemp <= 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    int p = numTypes;

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

    // Compute deltaNog (already done by calcDelta above) and initialize g-function
    gcpa = calc_g();
    gcpav = calc_lngV();
    updateDeltaWithG(ns);

    // Initialize site fractions with a few SS steps in full space
    solveX2(10);

    // Read reduced site fractions from converged individual sites
    double[] xSiteFull = new double[ns];
    readXsiteFromComponents(xSiteFull, ns);
    double[] xType = new double[p];
    for (int t = 0; t < p; t++) {
      double val = xSiteFull[typeRepSite[t]];
      if (val <= 1.0e-15 || val >= 1.0 || Double.isNaN(val)) {
        val = 0.5;
      }
      xType[t] = val;
    }
    expandAndSetSiteFractions(xType, ns);

    // Cache moles per type
    double[] tMoles = new double[p];
    for (int t = 0; t < p; t++) {
      tMoles[t] = componentArray[typeCompIdx[t]].getNumberOfMolesInPhase();
    }

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
      updateDeltaWithG(ns);

      // --- Inner loop: Anderson-accelerated SS for reduced site fractions ---
      int innerIters = solveXAndersonReduced(p, tMoles, ns);
      totalInnerIters += innerIters;

      hcpatot = calc_hCPA();

      // Compute CPA pressure derivatives using reduced-dimension implicit function theorem
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

  // ===========================================================================
  // Inner loop: Anderson acceleration on reduced site types
  // ===========================================================================

  /**
   * Solve the reduced site fraction equations using Anderson-accelerated successive substitution.
   *
   * <p>
   * The fixed-point map operates on p site type fractions instead of n_s individual fractions:
   * </p>
   *
   * <pre>
   * f_alpha(X) = 1 / (1 + sum_beta m_beta * n_beta * Delta(rep_a,rep_b) * X_beta / V)
   * </pre>
   *
   * <p>
   * Anderson acceleration (mixing depth m=3) is applied to this reduced map. After convergence, the
   * p type fractions are expanded back to all n_s individual site fractions.
   * </p>
   *
   * @param p number of unique site types
   * @param tMoles moles per site type
   * @param ns total number of individual association sites
   * @return number of inner iterations used
   */
  private int solveXAndersonReduced(int p, double[] tMoles, int ns) {
    if (p == 0) {
      return 0;
    }

    double totalVol = getTotalVolume();
    double invV = 1.0 / totalVol;

    // Read current reduced site fractions
    double[] xSiteFull = new double[ns];
    readXsiteFromComponents(xSiteFull, ns);
    double[] xCurr = new double[p];
    for (int t = 0; t < p; t++) {
      xCurr[t] = xSiteFull[typeRepSite[t]];
      if (xCurr[t] <= 1.0e-15 || xCurr[t] >= 1.0 || Double.isNaN(xCurr[t])) {
        xCurr[t] = 0.5;
      }
    }

    // Pre-compute reduced interaction kernel: klk[a][b] = m_b * n_b * delta(rep_a,rep_b) / V
    double[][] klk = new double[p][p];
    for (int a = 0; a < p; a++) {
      for (int b = 0; b < p; b++) {
        klk[a][b] = typeMult[b] * tMoles[b] * delta[typeRepSite[a]][typeRepSite[b]] * invV;
      }
    }

    // Anderson history storage (dimension p instead of ns)
    int m = ANDERSON_M;
    double[][] gHist = new double[m][p];
    double[][] xHist = new double[m][p];
    double[] gPrev = new double[p];
    double[] xPrev = new double[p];
    int histLen = 0;
    boolean hasPrev = false;

    int iterations = 0;

    for (int iter = 0; iter < MAX_INNER_ITERS; iter++) {
      iterations++;

      // One SS step on reduced types: compute f(x)
      double[] fX = new double[p];
      for (int a = 0; a < p; a++) {
        double sumAB = 0.0;
        for (int b = 0; b < p; b++) {
          sumAB += klk[a][b] * xCurr[b];
        }
        fX[a] = 1.0 / (1.0 + sumAB);
      }

      // Residual: g = f(x) - x
      double[] gCurr = new double[p];
      double maxG = 0.0;
      for (int a = 0; a < p; a++) {
        gCurr[a] = fX[a] - xCurr[a];
        maxG = Math.max(maxG, Math.abs(gCurr[a]));
      }

      if (maxG < INNER_TOL) {
        andersonConvergedCount++;
        expandAndSetSiteFractions(xCurr, ns);
        return iterations;
      }

      // Anderson mixing
      double[] xNew;
      if (hasPrev) {
        // Store history (circular buffer)
        int slot = histLen < m ? histLen : (histLen % m);
        for (int a = 0; a < p; a++) {
          gHist[slot][a] = gCurr[a] - gPrev[a];
          xHist[slot][a] = xCurr[a] - xPrev[a];
        }
        if (histLen < m) {
          histLen++;
        }

        // Solve least-squares: min ||g_curr - G * gamma||^2
        double[] gamma = solveAndersonLeastSquares(gHist, gCurr, p, histLen);

        // x_{k+1} = (x_curr + g_curr) - sum_i gamma_i * (xHist[i] + gHist[i])
        xNew = new double[p];
        for (int a = 0; a < p; a++) {
          xNew[a] = xCurr[a] + gCurr[a];
          for (int i = 0; i < histLen; i++) {
            xNew[a] -= gamma[i] * (xHist[i][a] + gHist[i][a]);
          }
          // Clamp to valid range
          xNew[a] = Math.max(1.0e-15, Math.min(1.0, xNew[a]));
        }
      } else {
        // First iteration: plain SS step
        xNew = fX;
      }

      // Save current as previous
      System.arraycopy(gCurr, 0, gPrev, 0, p);
      System.arraycopy(xCurr, 0, xPrev, 0, p);
      hasPrev = true;

      // Update xCurr
      System.arraycopy(xNew, 0, xCurr, 0, p);
    }

    // If Anderson didn't converge, expand and do Newton fallback in full space
    newtonFallbackCount++;
    expandAndSetSiteFractions(xCurr, ns);
    solveX();

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
   * @param gMatrix history of residual differences (m x p, using rows 0..histLen-1)
   * @param gVec current residual vector (length p)
   * @param p dimension of the vectors (number of site types)
   * @param histLen number of stored history vectors
   * @return mixing coefficients gamma (length histLen)
   */
  private static double[] solveAndersonLeastSquares(double[][] gMatrix, double[] gVec, int p,
      int histLen) {
    // Build G^T G (histLen x histLen)
    double[][] gtg = new double[histLen][histLen];
    for (int i = 0; i < histLen; i++) {
      for (int j = i; j < histLen; j++) {
        double dot = 0.0;
        for (int k = 0; k < p; k++) {
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
      for (int k = 0; k < p; k++) {
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

  // ===========================================================================
  // Site type mapping
  // ===========================================================================

  /**
   * Build the site type map by grouping equivalent association sites.
   *
   * <p>
   * Two individual sites are equivalent if they belong to the same component and have identical
   * deltaNog rows (same bonding pattern to all other sites). This corresponds to sites with the
   * same charge in the CPA association scheme.
   * </p>
   *
   * @param ns total number of individual association sites
   */
  private void buildSiteTypeMap(int ns) {
    siteToType = new int[ns];
    typeRepSite = new int[ns];
    typeMult = new int[ns];
    typeCompIdx = new int[ns];
    numTypes = 0;

    for (int i = 0; i < ns; i++) {
      boolean matched = false;
      for (int t = 0; t < numTypes; t++) {
        if (moleculeNumber[i] != moleculeNumber[typeRepSite[t]]) {
          continue;
        }
        // Same component — check if deltaNog rows are identical
        boolean identical = true;
        for (int j = 0; j < ns; j++) {
          if (Math.abs(deltaNog[i][j] - deltaNog[typeRepSite[t]][j]) > 1.0e-30) {
            identical = false;
            break;
          }
        }
        if (identical) {
          siteToType[i] = t;
          typeMult[t]++;
          matched = true;
          break;
        }
      }
      if (!matched) {
        typeRepSite[numTypes] = i;
        siteToType[i] = numTypes;
        typeMult[numTypes] = 1;
        typeCompIdx[numTypes] = moleculeNumber[i];
        numTypes++;
      }
    }
  }

  /**
   * Expand reduced site fraction values to all individual sites on components.
   *
   * @param xType reduced site fraction array (length p)
   * @param ns total number of individual sites
   */
  private void expandAndSetSiteFractions(double[] xType, int ns) {
    int idx = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        int typeIdx = siteToType[idx];
        ((ComponentCPAInterface) componentArray[i]).setXsite(j, xType[typeIdx]);
        idx++;
      }
    }
  }

  /**
   * Read individual site fractions from component objects into a flat array.
   *
   * @param xSite array to fill (length ns)
   * @param ns total number of individual sites
   */
  private void readXsiteFromComponents(double[] xSite, int ns) {
    int idx = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        xSite[idx] = ((ComponentSrkCPA) componentArray[i]).getXsite()[j];
        idx++;
      }
    }
  }

  // ===========================================================================
  // initCPAMatrix override for reduced-dimension volume derivatives
  // ===========================================================================

  /**
   * Ensure reduced work arrays are allocated.
   *
   * @param p number of unique site types
   */
  private void ensureWorkArrays(int p) {
    if (p != workP) {
      workKlk = new double[p][p];
      workHess = new double[p][p];
      workKsi = new double[p];
      workM = new double[p];
      workKlkKsi = new double[p];
      workXV = new double[p];
      workP = p;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Override type 1 initialization to use reduced-dimension site type computation. Volume
   * derivatives (FCPA, dFCPAdV, dFCPAdVdV, dFCPAdVdVdV) are computed using the type grouping,
   * reducing the Hessian linear system from n_s to p dimensions.
   * </p>
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

    if (type > 1) {
      solveX();
      super.initCPAMatrix(type);
      return;
    }

    // If type map not built or no reduction, delegate to parent
    if (numTypes < 0 || numTypes == ns) {
      super.initCPAMatrix(type);
      return;
    }

    int p = numTypes;
    ensureWorkArrays(p);

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;

    double gv = getGcpav();
    double fV = gv - 1.0 / totalVolume;
    double fVV = fV * fV + gcpavv + 1.0 / totalVolume2;
    double fVVV =
        fV * fV * fV + 3.0 * fV * (gcpavv + 1.0 / totalVolume2) + gcpavvv - 2.0 / totalVolume3;

    // Read reduced site fractions and moles
    double[] xSiteFull = new double[ns];
    readXsiteFromComponents(xSiteFull, ns);
    for (int t = 0; t < p; t++) {
      workKsi[t] = xSiteFull[typeRepSite[t]];
      workM[t] = componentArray[typeCompIdx[t]].getNumberOfMolesInPhase();
    }

    // Build reduced K matrix: K_red[a][b] = mult_a * mult_b * n_a * n_b * delta(rep_a,rep_b) / V
    double invV = 1.0 / totalVolume;
    for (int a = 0; a < p; a++) {
      double maInvV = typeMult[a] * workM[a] * invV;
      for (int b = a; b < p; b++) {
        double k = maInvV * typeMult[b] * workM[b] * delta[typeRepSite[a]][typeRepSite[b]];
        workKlk[a][b] = k;
        workKlk[b][a] = k;
      }
    }

    // K_red * X
    for (int a = 0; a < p; a++) {
      double s = 0.0;
      for (int b = 0; b < p; b++) {
        s += workKlk[a][b] * workKsi[b];
      }
      workKlkKsi[a] = s;
    }

    // Build reduced Hessian for XV linear system
    for (int a = 0; a < p; a++) {
      for (int b = 0; b < p; b++) {
        workHess[a][b] = -workKlk[a][b];
      }
      workHess[a][a] -= typeMult[a] * workM[a] / (workKsi[a] * workKsi[a]);
    }

    // RHS and solve for XV
    for (int a = 0; a < p; a++) {
      workXV[a] = fV * workKlkKsi[a];
    }
    solveLinearSystem(workHess, workXV, p);

    // Compute scalar quantities
    double dotKsiKlkKsi = 0.0;
    double dotKlkKsiXV = 0.0;
    double fcpa = 0.0;
    for (int a = 0; a < p; a++) {
      dotKsiKlkKsi += workKsi[a] * workKlkKsi[a];
      dotKlkKsiXV += workKlkKsi[a] * workXV[a];
      fcpa += typeMult[a] * workM[a] * (Math.log(workKsi[a]) - workKsi[a] / 2.0 + 0.5);
    }
    FCPA = fcpa;
    dFCPAdV = -0.5 * fV * dotKsiKlkKsi;
    dFCPAdVdV = -0.5 * fVV * dotKsiKlkKsi - fV * dotKlkKsiXV;

    // Third derivative
    double dotXVKlkXV = 0.0;
    for (int a = 0; a < p; a++) {
      double s = 0.0;
      for (int b = 0; b < p; b++) {
        s += workKlk[a][b] * workXV[b];
      }
      dotXVKlkXV += workXV[a] * s;
    }

    double sumQXV = 0.0;
    double sumXV2 = 0.0;
    for (int a = 0; a < p; a++) {
      sumQXV += workXV[a] * 2.0 * typeMult[a] * workM[a] / (workKsi[a] * workKsi[a] * workKsi[a]);
      sumXV2 += typeMult[a] * workXV[a] * workXV[a];
    }

    dFCPAdVdVdV = -0.5 * fVVV * dotKsiKlkKsi - 3.0 * fVV * dotKlkKsiXV - 3.0 * fV * dotXVKlkXV
        + sumQXV * sumXV2;
  }

  // ===========================================================================
  // Linear algebra utilities
  // ===========================================================================

  /**
   * Solve the linear system A*x = b in-place (b overwritten with solution) using Gaussian
   * elimination with partial pivoting.
   *
   * @param a coefficient matrix (modified in-place)
   * @param b right-hand side vector (overwritten with solution)
   * @param n system dimension
   */
  private static void solveLinearSystem(double[][] a, double[] b, int n) {
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
      if (maxRow != col) {
        double[] tmpRow = a[col];
        a[col] = a[maxRow];
        a[maxRow] = tmpRow;
        double tmpVal = b[col];
        b[col] = b[maxRow];
        b[maxRow] = tmpVal;
      }
      double pivot = a[col][col];
      if (Math.abs(pivot) < 1.0e-30) {
        continue;
      }
      for (int row = col + 1; row < n; row++) {
        double factor = a[row][col] / pivot;
        for (int k = col + 1; k < n; k++) {
          a[row][k] -= factor * a[col][k];
        }
        b[row] -= factor * b[col];
      }
    }
    for (int row = n - 1; row >= 0; row--) {
      double s = b[row];
      for (int k = row + 1; k < n; k++) {
        s -= a[row][k] * b[k];
      }
      b[row] = s / a[row][row];
    }
  }

  // --- Helper methods ---

  /**
   * Update delta = deltaNog * g for all individual sites.
   *
   * @param ns total number of individual sites
   */
  private void updateDeltaWithG(int ns) {
    for (int i = 0; i < ns; i++) {
      for (int j = i; j < ns; j++) {
        delta[i][j] = deltaNog[i][j] * gcpa;
        delta[j][i] = delta[i][j];
      }
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
