package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;

/**
 * Broyden quasi-Newton implicit CPA phase solver.
 *
 * <p>
 * Improves on the fully implicit coupled Newton-Raphson approach of Igben et al. (2026) by using
 * Broyden's rank-1 update of the inverse Jacobian after the first full Newton step. This reduces
 * the per-iteration cost from O(n_s^3) (Gaussian elimination) to O(n_s^2) (rank-1 update via the
 * Sherman-Morrison formula), while maintaining superlinear convergence.
 * </p>
 *
 * <p>
 * Algorithm:
 * </p>
 * <ol>
 * <li>Iteration 1: Compute full analytic Jacobian J, invert to get H = J^{-1}, solve delta_x = -H *
 * R</li>
 * <li>Iterations 2+: Update H via Broyden's "good" formula: H_{k+1} = H_k + (dx - H_k*df) * (dx^T *
 * H_k) / (dx^T * H_k * df)</li>
 * <li>Periodic refresh: Recompute full Jacobian when convergence stalls (||R_new||/||R_old|| &gt;
 * 0.5 for 2 consecutive steps)</li>
 * </ol>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>C.G. Broyden, A class of methods for solving nonlinear simultaneous equations, Math. Comput.
 * 19 (1965) 577-593.</li>
 * <li>O.N. Igben et al., Fully implicit algorithm for CPA EOS, Fluid Phase Equilib. 608 (2026)
 * 114734.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseSrkCPABroydenImplicit extends PhaseSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseSrkCPABroydenImplicit.class);

  /** Maximum iterations for the coupled solver. */
  private static final int MAX_ITERATIONS = 100;

  /** Convergence tolerance for the coupled system. */
  private static final double CONVERGENCE_TOL = 1.0e-12;

  /** Maximum relative step size per iteration. */
  private static final double MAX_REL_STEP = 0.5;

  /** Restart threshold parameter alpha. */
  private static final double RESTART_ALPHA = 0.1;

  /** Minimum iterations before allowing Broyden updates. */
  private static final int MIN_NEWTON_STEPS = 2;

  /** Residual threshold below which Broyden updates are allowed. */
  private static final double BROYDEN_SWITCH_TOL = 1.0e-4;

  /** Stall detection: refresh Jacobian if ratio exceeds this for consecutive steps. */
  private static final double STALL_RATIO = 0.3;

  // --- Profiling counters ---
  /** Total molarVolume calls. */
  private static volatile long callCount = 0;
  /** Total iterations across all calls. */
  private static volatile long totalIters = 0;
  /** Total Jacobian evaluations (full Newton steps). */
  private static volatile long jacobianEvals = 0;
  /** Total Broyden updates (quasi-Newton steps). */
  private static volatile long broydenUpdates = 0;
  /** Total fallbacks to nested solver. */
  private static volatile long fallbackCount = 0;

  /**
   * Reset profiling counters.
   */
  public static void resetProfileCounters() {
    callCount = 0;
    totalIters = 0;
    jacobianEvals = 0;
    broydenUpdates = 0;
    fallbackCount = 0;
  }

  /**
   * Get profiling summary string.
   *
   * @return a summary of profiling data
   */
  public static String getProfileSummary() {
    double avgIters = callCount > 0 ? (double) totalIters / callCount : 0;
    double broydenFrac = (jacobianEvals + broydenUpdates) > 0
        ? (double) broydenUpdates / (jacobianEvals + broydenUpdates) * 100
        : 0;
    return String.format(
        "Calls=%d  AvgIters=%.1f  JacobianEvals=%d  BroydenUpdates=%d (%.0f%%)  " + "Fallbacks=%d",
        callCount, avgIters, jacobianEvals, broydenUpdates, broydenFrac, fallbackCount);
  }

  /**
   * Constructor for PhaseSrkCPABroydenImplicit.
   */
  public PhaseSrkCPABroydenImplicit() {
    super();
    thermoPropertyModelName = "SRK-CPA-EoS-BroydenImplicit";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPABroydenImplicit clone() {
    PhaseSrkCPABroydenImplicit clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPABroydenImplicit) super.clone();
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
   * Broyden quasi-Newton implicit molar volume calculation. Uses the coupled (n_s+1)-dimensional
   * system but replaces full Jacobian evaluation with Broyden rank-1 updates after the first Newton
   * step. The inverse Jacobian H is maintained directly and updated via the Sherman-Morrison
   * formula.
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

    // Gas phase → nested solver (weak association, coupling unnecessary)
    if (pt == PhaseType.GAS) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    callCount++;

    double Btemp = getB();
    if (Btemp <= 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // --- Initial guess for zeta = B/(n*V) ---
    double zeta;
    if (pt == PhaseType.GAS) {
      zeta = pressure * Btemp / (numberOfMolesInPhase * temperature * R);
    } else {
      zeta = 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    }
    zeta = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, zeta));

    double molarVol = Btemp / (numberOfMolesInPhase * zeta);
    setMolarVolume(molarVol);

    // Compute deltaNog once (temperature-dependent)
    calcDelta();

    // Initialize site fractions
    gcpa = calc_g();
    gcpav = calc_lngV();
    updateDeltaWithG();
    int dim = ns + 1;
    double[] xSite = new double[ns];
    readXsiteFromComponents(xSite);
    boolean needsInit = false;
    for (int i = 0; i < ns; i++) {
      if (xSite[i] <= 1.0e-15 || xSite[i] >= 1.0 || Double.isNaN(xSite[i])) {
        needsInit = true;
        break;
      }
    }
    if (needsInit) {
      for (int i = 0; i < ns; i++) {
        xSite[i] = 0.5;
      }
      setXsiteOnComponents(xSite);
      solveX2(10);
      readXsiteFromComponents(xSite);
    }

    // --- Pre-allocate arrays ---
    double[] residual = new double[dim];
    double[] residualOld = new double[dim];
    double[][] jacobian = new double[dim][dim];
    double[][] invJac = new double[dim][dim]; // H = J^{-1}
    double[] dx = new double[dim];
    double[] xOld = new double[dim];
    double[] df = new double[dim]; // change in residual

    // Cache moles per site
    double[] siteMoles = new double[ns];
    for (int i = 0; i < ns; i++) {
      siteMoles[i] = componentArray[moleculeNumber[i]].getNumberOfMolesInPhase();
    }

    int iterations = 0;
    boolean converged = false;
    boolean restartTriggered = false;
    boolean usebroyden = false;
    int stallCount = 0;
    double prevResNorm = Double.MAX_VALUE;

    do {
      iterations++;

      // --- Set volume from current zeta ---
      molarVol = Btemp / (numberOfMolesInPhase * zeta);
      setMolarVolume(molarVol);
      double totalVol = molarVol * numberOfMolesInPhase;
      Z = pressure * molarVol / (R * temperature);

      // --- Update g-function and delta ---
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
        totalVol = getMolarVolume() * numberOfMolesInPhase;
        zeta = Btemp / (numberOfMolesInPhase * getMolarVolume());
        molarVol = getMolarVolume();
      }
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
      updateDeltaWithG();

      // --- Compute CPA pressure derivatives ---
      double gdv1 = gcpav - 1.0 / totalVol;
      double totalVol2 = totalVol * totalVol;
      double sumFV = 0.0;
      double sumFVV = 0.0;
      for (int i = 0; i < ns; i++) {
        for (int j = i; j < ns; j++) {
          double klk = siteMoles[i] * siteMoles[j] / totalVol * delta[i][j];
          double xixj = xSite[i] * xSite[j];
          double klkXiXj = klk * xixj;
          double symFactor = (i == j) ? 1.0 : 2.0;
          sumFV += symFactor * klkXiXj * gdv1;
          sumFVV += symFactor * klkXiXj * (gdv1 * gdv1 + gcpavv + 1.0 / totalVol2);
        }
      }
      dFCPAdV = -0.5 * sumFV;
      dFCPAdVdV = -0.5 * sumFVV;

      // --- Build residual vector ---
      for (int k = 0; k < ns; k++) {
        double sumKJ = 0.0;
        for (int j = 0; j < ns; j++) {
          sumKJ += siteMoles[j] * delta[k][j] * xSite[j];
        }
        residual[k] = xSite[k] - 1.0 / (1.0 + sumKJ / totalVol);
      }
      double h = zeta - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      residual[ns] = h;

      // --- Check convergence ---
      double maxResidual = 0.0;
      for (int i = 0; i < dim; i++) {
        maxResidual = Math.max(maxResidual, Math.abs(residual[i]));
      }
      if (maxResidual < CONVERGENCE_TOL && iterations > 1) {
        converged = true;
        break;
      }

      // --- Decide: full Newton or Broyden update ---
      if (!usebroyden || iterations <= MIN_NEWTON_STEPS || maxResidual > BROYDEN_SWITCH_TOL) {
        // Full Newton step: compute Jacobian and invert
        buildJacobian(jacobian, xSite, siteMoles, totalVol, gdv1, zeta, Btemp, dim, ns);
        boolean ok = invertMatrix(jacobian, invJac, dim);
        if (!ok) {
          fallbackCount++;
          return super.molarVolume(pressure, temperature, A, B, pt);
        }
        jacobianEvals++;
        if (maxResidual <= BROYDEN_SWITCH_TOL && iterations > MIN_NEWTON_STEPS) {
          usebroyden = true;
        }
        stallCount = 0;
      } else {
        // Broyden rank-1 update of invJac
        // df = R_new - R_old
        for (int i = 0; i < dim; i++) {
          df[i] = residual[i] - residualOld[i];
        }
        // dx_prev = x_new - x_old (already saved in xOld before update)
        double[] dxPrev = new double[dim];
        for (int k = 0; k < ns; k++) {
          dxPrev[k] = xSite[k] - xOld[k];
        }
        dxPrev[ns] = zeta - xOld[ns];

        // H_new = H + (dx - H*df) * (dx^T * H) / (dx^T * H * df)
        broydenUpdate(invJac, dxPrev, df, dim);
        broydenUpdates++;

        // Check for stalling — refresh immediately if residual increases
        if (maxResidual > prevResNorm) {
          // Residual increased — revert to full Newton
          buildJacobian(jacobian, xSite, siteMoles, totalVol, gdv1, zeta, Btemp, dim, ns);
          boolean ok = invertMatrix(jacobian, invJac, dim);
          if (!ok) {
            fallbackCount++;
            return super.molarVolume(pressure, temperature, A, B, pt);
          }
          jacobianEvals++;
          stallCount = 0;
        } else if (maxResidual > prevResNorm * STALL_RATIO) {
          stallCount++;
          if (stallCount >= 2) {
            // Refresh: recompute full Jacobian
            buildJacobian(jacobian, xSite, siteMoles, totalVol, gdv1, zeta, Btemp, dim, ns);
            boolean ok = invertMatrix(jacobian, invJac, dim);
            if (!ok) {
              fallbackCount++;
              return super.molarVolume(pressure, temperature, A, B, pt);
            }
            jacobianEvals++;
            stallCount = 0;
          }
        } else {
          stallCount = 0;
        }
      }

      prevResNorm = maxResidual;

      // Save current state for Broyden update
      for (int k = 0; k < ns; k++) {
        xOld[k] = xSite[k];
      }
      xOld[ns] = zeta;
      System.arraycopy(residual, 0, residualOld, 0, dim);

      // --- Compute step: dx = -H * R ---
      for (int i = 0; i < dim; i++) {
        double s = 0.0;
        for (int j = 0; j < dim; j++) {
          s += invJac[i][j] * residual[j];
        }
        dx[i] = -s;
      }

      // --- Step limiting ---
      double maxStep = 1.0;
      for (int k = 0; k < ns; k++) {
        double proposedX = xSite[k] + maxStep * dx[k];
        if (proposedX < 1.0e-15) {
          double limit = MAX_REL_STEP * xSite[k] / Math.abs(dx[k]);
          maxStep = Math.min(maxStep, limit);
        }
        if (proposedX > 1.0) {
          double limit = (1.0 - xSite[k]) / dx[k];
          maxStep = Math.min(maxStep, Math.max(0.1, limit));
        }
      }
      double proposedZeta = zeta + maxStep * dx[ns];
      if (proposedZeta < 1.0e-10) {
        double limit = MAX_REL_STEP * zeta / Math.abs(dx[ns]);
        maxStep = Math.min(maxStep, limit);
      }
      if (proposedZeta > 1.0 - 1.0e-10) {
        double limit = (1.0 - 1.0e-10 - zeta) / dx[ns];
        maxStep = Math.min(maxStep, Math.max(0.1, limit));
      }
      if (Math.abs(dx[ns]) / Math.max(zeta, 1.0e-10) > 0.3) {
        maxStep = Math.min(maxStep, 0.3 * zeta / Math.abs(dx[ns]));
      }

      // --- Apply update ---
      for (int k = 0; k < ns; k++) {
        xSite[k] += maxStep * dx[k];
        xSite[k] = Math.max(1.0e-15, Math.min(1.0, xSite[k]));
      }
      zeta += maxStep * dx[ns];
      zeta = Math.max(1.0e-10, Math.min(1.0 - 1.0e-10, zeta));

      // --- Restart criterion ---
      if (iterations > 20 && maxResidual > 0.1 && !restartTriggered) {
        restartTriggered = true;
        usebroyden = false;
        if (pt == PhaseType.GAS) {
          zeta = 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
        } else {
          zeta = pressure * Btemp / (numberOfMolesInPhase * temperature * R);
        }
        zeta = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, zeta));
        for (int i = 0; i < ns; i++) {
          xSite[i] = 0.5;
        }
      }

    } while (iterations < MAX_ITERATIONS);

    if (!converged) {
      fallbackCount++;
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    totalIters += iterations;

    // --- Finalization ---
    double finalMolarVol = Btemp / (numberOfMolesInPhase * zeta);
    setMolarVolume(finalMolarVol);
    Z = pressure * finalMolarVol / (R * temperature);
    setXsiteOnComponents(xSite);

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
   * Build the analytic Jacobian for the coupled (n_s+1)-dimensional system.
   *
   * @param jac the Jacobian matrix to populate (dim x dim)
   * @param xSite site fraction values
   * @param siteMoles moles per site
   * @param totalVol total volume V
   * @param gdv1 g'(V) - 1/V
   * @param zeta current B/(nV)
   * @param btemp co-volume B
   * @param dim system dimension (ns+1)
   * @param ns number of association sites
   */
  private void buildJacobian(double[][] jac, double[] xSite, double[] siteMoles, double totalVol,
      double gdv1, double zeta, double btemp, int dim, int ns) {

    // J_XX block
    for (int k = 0; k < ns; k++) {
      double xk2 = xSite[k] * xSite[k];
      for (int j = 0; j < ns; j++) {
        double dkj = (k == j) ? 1.0 : 0.0;
        jac[k][j] = dkj + xk2 * siteMoles[j] * delta[k][j] / totalVol;
      }
    }

    // j_X_zeta column
    double dVdZeta = -btemp / (numberOfMolesInPhase * zeta * zeta);
    for (int k = 0; k < ns; k++) {
      double xk2 = xSite[k] * xSite[k];
      double sumDeriv = 0.0;
      for (int j = 0; j < ns; j++) {
        double deltaKJ = delta[k][j];
        double dDeltadV = deltaKJ * gcpav;
        sumDeriv +=
            siteMoles[j] * xSite[j] * (dDeltadV * totalVol - deltaKJ) / (totalVol * totalVol);
      }
      jac[k][ns] = xk2 * sumDeriv * dVdZeta;
    }

    // j_zeta_X row
    for (int k = 0; k < ns; k++) {
      double sumJ = 0.0;
      for (int j = 0; j < ns; j++) {
        sumJ += siteMoles[j] * delta[k][j] * xSite[j];
      }
      jac[ns][k] = btemp / numberOfMolesInPhase * cpaon * (siteMoles[k] / totalVol) * gdv1 * sumJ;
    }

    // J_zeta_zeta scalar
    double BonV2 = zeta * zeta;
    jac[ns][ns] = 1.0 + btemp / (BonV2) * (btemp / numberOfMolesInPhase * dFdVdV());
  }

  /**
   * Invert matrix A into Ainv using Gauss-Jordan elimination with partial pivoting.
   *
   * @param a input matrix (will be modified)
   * @param ainv output inverse matrix
   * @param n matrix dimension
   * @return true if successful, false if singular
   */
  private static boolean invertMatrix(double[][] a, double[][] ainv, int n) {
    // Set ainv = identity
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        ainv[i][j] = (i == j) ? 1.0 : 0.0;
      }
    }

    // Make copy of a (we'll modify it)
    double[][] work = new double[n][n];
    for (int i = 0; i < n; i++) {
      System.arraycopy(a[i], 0, work[i], 0, n);
    }

    // Forward elimination with partial pivoting
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(work[col][col]);
      for (int row = col + 1; row < n; row++) {
        double val = Math.abs(work[row][col]);
        if (val > maxVal) {
          maxVal = val;
          maxRow = row;
        }
      }
      if (maxVal < 1.0e-30) {
        return false;
      }
      if (maxRow != col) {
        double[] tempRow = work[col];
        work[col] = work[maxRow];
        work[maxRow] = tempRow;
        tempRow = ainv[col];
        ainv[col] = ainv[maxRow];
        ainv[maxRow] = tempRow;
      }
      double pivot = work[col][col];
      for (int k = 0; k < n; k++) {
        work[col][k] /= pivot;
        ainv[col][k] /= pivot;
      }
      for (int row = 0; row < n; row++) {
        if (row == col) {
          continue;
        }
        double factor = work[row][col];
        for (int k = 0; k < n; k++) {
          work[row][k] -= factor * work[col][k];
          ainv[row][k] -= factor * ainv[col][k];
        }
      }
    }
    return true;
  }

  /**
   * Broyden's "good" rank-1 update of the inverse Jacobian H.
   *
   * <p>
   * Updates H in-place using: H_new = H + (dx - H*df) * (dx^T * H) / (dx^T * H * df)
   * </p>
   *
   * <p>
   * This is the Sherman-Morrison formula applied to the secant condition. Cost: O(n^2).
   * </p>
   *
   * @param invJ inverse Jacobian matrix H (updated in-place)
   * @param dxVec step vector: x_{k+1} - x_k
   * @param dfVec residual change vector: R(x_{k+1}) - R(x_k)
   * @param n system dimension
   */
  private static void broydenUpdate(double[][] invJ, double[] dxVec, double[] dfVec, int n) {
    // Compute H * df
    double[] hdf = new double[n];
    for (int i = 0; i < n; i++) {
      double s = 0.0;
      for (int j = 0; j < n; j++) {
        s += invJ[i][j] * dfVec[j];
      }
      hdf[i] = s;
    }

    // Compute dx^T * H (row vector)
    double[] dxH = new double[n];
    for (int j = 0; j < n; j++) {
      double s = 0.0;
      for (int i = 0; i < n; i++) {
        s += dxVec[i] * invJ[i][j];
      }
      dxH[j] = s;
    }

    // Compute denominator: dx^T * H * df = dxH . df
    double denom = 0.0;
    for (int j = 0; j < n; j++) {
      denom += dxH[j] * dfVec[j];
    }

    // Guard against zero denominator
    if (Math.abs(denom) < 1.0e-30) {
      return; // Skip update if denominator is negligible
    }

    // Compute numerator vector: dx - H*df
    double[] numVec = new double[n];
    for (int i = 0; i < n; i++) {
      numVec[i] = dxVec[i] - hdf[i];
    }

    // Update: H += numVec * dxH^T / denom
    double invDenom = 1.0 / denom;
    for (int i = 0; i < n; i++) {
      double numI = numVec[i] * invDenom;
      for (int j = 0; j < n; j++) {
        invJ[i][j] += numI * dxH[j];
      }
    }
  }

  // --- Inherited helper methods (same as PhaseSrkCPAfullyImplicit) ---

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

  // --- Work arrays for initCPAMatrix type 1 (same as fully implicit) ---
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
   * Override CPA matrix initialization for efficient volume derivative computation.
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

    if (type > 1) {
      solveX();
      super.initCPAMatrix(type);
      return;
    }

    // Type 1: compute volume derivatives from scratch using GE
    ensureWorkArrays(ns);

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;

    double gv = getGcpav();
    double fV = gv - 1.0 / totalVolume;
    double fVV = fV * fV + gcpavv + 1.0 / totalVolume2;
    double fVVV =
        fV * fV * fV + 3.0 * fV * (gcpavv + 1.0 / totalVolume2) + gcpavvv - 2.0 / totalVolume3;

    int idx = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        workKsi[idx] = ((ComponentSrkCPA) componentArray[i]).getXsite()[j];
        workM[idx] = ni;
        idx++;
      }
    }

    double invV = 1.0 / totalVolume;
    for (int i = 0; i < ns; i++) {
      double miV = workM[i] * invV;
      for (int j = i; j < ns; j++) {
        double k = miV * workM[j] * delta[i][j];
        workKlk[i][j] = k;
        workKlk[j][i] = k;
      }
    }

    for (int i = 0; i < ns; i++) {
      double s = 0;
      for (int j = 0; j < ns; j++) {
        s += workKlk[i][j] * workKsi[j];
      }
      workKlkKsi[i] = s;
    }

    for (int i = 0; i < ns; i++) {
      for (int j = 0; j < ns; j++) {
        workHess[i][j] = -workKlk[i][j];
      }
      workHess[i][i] -= workM[i] / (workKsi[i] * workKsi[i]);
    }

    for (int i = 0; i < ns; i++) {
      workXV[i] = fV * workKlkKsi[i];
    }
    solveLinearSystem(workHess, workXV, ns);

    double dotKsiKlkKsi = 0;
    double dotKlkKsiXV = 0;
    double fcpa = 0;
    for (int i = 0; i < ns; i++) {
      dotKsiKlkKsi += workKsi[i] * workKlkKsi[i];
      dotKlkKsiXV += workKlkKsi[i] * workXV[i];
      fcpa += workM[i] * (Math.log(workKsi[i]) - workKsi[i] / 2.0 + 0.5);
    }
    FCPA = fcpa;
    dFCPAdV = -0.5 * fV * dotKsiKlkKsi;
    dFCPAdVdV = -0.5 * fVV * dotKsiKlkKsi - fV * dotKlkKsiXV;

    double dotXVKlkXV = 0;
    for (int i = 0; i < ns; i++) {
      double s = 0;
      for (int j = 0; j < ns; j++) {
        s += workKlk[i][j] * workXV[j];
      }
      dotXVKlkXV += workXV[i] * s;
    }

    double sumQXV = 0;
    double sumXV2 = 0;
    for (int i = 0; i < ns; i++) {
      sumQXV += workXV[i] * 2.0 * workM[i] / (workKsi[i] * workKsi[i] * workKsi[i]);
      sumXV2 += workXV[i] * workXV[i];
    }

    dFCPAdVdVdV = -0.5 * fVVV * dotKsiKlkKsi - 3.0 * fVV * dotKlkKsiXV - 3.0 * fV * dotXVKlkXV
        + sumQXV * sumXV2;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return super.molarVolumeChangePhase(pressure, temperature, A, B, pt);
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
}
