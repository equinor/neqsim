package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;

/**
 * Fully implicit CPA phase with site type reduction.
 *
 * <p>
 * Combines two acceleration strategies:
 * </p>
 * <ul>
 * <li><b>Fully implicit coupled Newton-Raphson</b> from Igben et al. (2026):
 * simultaneous solution
 * of molar volume and association site fractions, eliminating inner
 * iterations.</li>
 * <li><b>Site type reduction</b>: groups equivalent association sites (same
 * deltaNog row on same
 * component) into types with multiplicities, reducing the system dimension from
 * (n_s + 1) to (p +
 * 1).</li>
 * </ul>
 *
 * <p>
 * The Newton Jacobian is built analytically on the reduced (p+1)-dimensional
 * system at every
 * iteration (no Broyden approximation), solved via Gaussian elimination O(p^3).
 * This gives both the
 * per-iteration cost reduction of dimension reduction AND the quadratic
 * convergence of full Newton.
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
 * <th>Jacobian speedup</th>
 * </tr>
 * <tr>
 * <td>Pure water (4C)</td>
 * <td>4</td>
 * <td>2</td>
 * <td>4.6x</td>
 * </tr>
 * <tr>
 * <td>Water + methanol (4C+2B)</td>
 * <td>6</td>
 * <td>4</td>
 * <td>2.7x</td>
 * </tr>
 * <tr>
 * <td>Water + MEG (4C+4C)</td>
 * <td>8</td>
 * <td>4</td>
 * <td>5.8x</td>
 * </tr>
 * <tr>
 * <td>NG + water + TEG</td>
 * <td>8</td>
 * <td>4</td>
 * <td>5.8x</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseSrkCPAfullyImplicitReduced extends PhaseSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseSrkCPAfullyImplicitReduced.class);

  // --- Solver constants ---
  /** Maximum iterations for the coupled Newton solver. */
  static final int MAX_ITERATIONS = 100;
  /** Convergence tolerance for the max residual. */
  static final double CONVERGENCE_TOL = 1.0e-12;
  /** Maximum relative step size per iteration to prevent divergence. */
  static final double MAX_REL_STEP = 0.5;
  /** Restart threshold parameter alpha (supercritical detection). */
  static final double RESTART_ALPHA = 0.1;

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

  // --- Profiling counters ---
  /** Number of molarVolume calls. */
  long callCount = 0;
  /** Total iterations across all calls. */
  long totalIters = 0;
  /** Number of Jacobian evaluations (full Newton, no Broyden). */
  long jacobianEvals = 0;
  /** Number of solver fallbacks to the base class. */
  long fallbackCount = 0;
  /** Number of site types (last call). */
  int lastNumTypes = 0;
  /** Number of full sites (last call). */
  int lastFullSites = 0;

  // --- Work arrays for initCPAMatrix(1) ---
  private transient double[][] workKlk = null;
  private transient double[][] workHess = null;
  private transient double[] workKsi = null;
  private transient double[] workM = null;
  private transient double[] workKlkKsi = null;
  private transient double[] workXV = null;
  private transient int workP = 0;

  /**
   * Construct a PhaseSrkCPAfullyImplicitReduced phase.
   */
  public PhaseSrkCPAfullyImplicitReduced() {
    super();
    thermoPropertyModelName = "SRK-CPA-EoS-FullyImplicit-Reduced";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPAfullyImplicitReduced clone() {
    PhaseSrkCPAfullyImplicitReduced clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPAfullyImplicitReduced) super.clone();
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
   * Fully implicit + reduced molar volume solver. Operates on the reduced
   * (p+1)-dimensional system
   * where p is the number of unique association site types. Uses full Newton
   * Jacobian at every
   * iteration (no Broyden approximation), solved via Gaussian elimination.
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

    // Build site type map early so initCPAMatrix(1) works even for gas-phase
    // fallback
    calcDelta();
    buildSiteTypeMap(ns);

    // Gas phase: nested solver (weak association, but initCPAMatrix override is
    // active)
    if (pt == PhaseType.GAS) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    callCount++;

    double Btemp = getB();
    if (Btemp <= 0) {
      logger.info("b negative in fully implicit reduced volume calc");
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // If no reduction possible (each site unique), fall back
    if (numTypes == ns) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    lastNumTypes = numTypes;
    lastFullSites = ns;

    int p = numTypes;
    int dim = p + 1;

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

    // --- Initialize site fractions (reduced) ---
    gcpa = calc_g();
    gcpav = calc_lngV();
    updateDeltaWithG(ns);

    double[] xSiteFull = new double[ns];
    readXsiteFromComponents(xSiteFull, ns);

    double[] xType = new double[p];
    boolean needsInit = false;
    for (int t = 0; t < p; t++) {
      double val = xSiteFull[typeRepSite[t]];
      if (val <= 1.0e-15 || val >= 1.0 || Double.isNaN(val)) {
        needsInit = true;
        break;
      }
      xType[t] = val;
    }
    if (needsInit) {
      for (int t = 0; t < p; t++) {
        xType[t] = 0.5;
      }
      expandAndSetSiteFractions(xType, ns);
      solveX2(10);
      readXsiteFromComponents(xSiteFull, ns);
      for (int t = 0; t < p; t++) {
        xType[t] = xSiteFull[typeRepSite[t]];
      }
    }

    // Cache moles per type
    double[] tMoles = new double[p];
    for (int t = 0; t < p; t++) {
      tMoles[t] = componentArray[typeCompIdx[t]].getNumberOfMolesInPhase();
    }

    // --- Pre-allocate work arrays ---
    double[] residual = new double[dim];
    double[][] jacobian = new double[dim][dim];
    double[] dx = new double[dim];

    int iterations = 0;
    boolean converged = false;
    boolean restartTriggered = false;

    // Reusable temp array for expanding reduced types to full site fractions
    double[] xSiteFullTemp = new double[ns];

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
      updateDeltaWithG(ns);

      // --- Compute reduced CPA pressure derivatives ---
      double gdv1 = gcpav - 1.0 / totalVol;
      double totalVol2 = totalVol * totalVol;

      double sumFV = 0.0;
      double sumFVV = 0.0;
      for (int a = 0; a < p; a++) {
        for (int b = a; b < p; b++) {
          double dab = delta[typeRepSite[a]][typeRepSite[b]];
          double kRed = tMoles[a] * tMoles[b] / totalVol * dab * typeMult[a] * typeMult[b];
          double xaxb = xType[a] * xType[b];
          double kx = kRed * xaxb;
          double sym = (a == b) ? 1.0 : 2.0;
          sumFV += sym * kx * gdv1;
          sumFVV += sym * kx * (gdv1 * gdv1 + gcpavv + 1.0 / totalVol2);
        }
      }
      dFCPAdV = -0.5 * sumFV;
      dFCPAdVdV = -0.5 * sumFVV;

      // --- Build reduced residual ---
      double[] redSum = new double[p];
      for (int a = 0; a < p; a++) {
        double s = 0.0;
        for (int b = 0; b < p; b++) {
          s += typeMult[b] * tMoles[b] * delta[typeRepSite[a]][typeRepSite[b]] * xType[b];
        }
        redSum[a] = s;
        residual[a] = xType[a] - 1.0 / (1.0 + redSum[a] / totalVol);
      }
      double h = zeta - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      residual[p] = h;

      // --- Check convergence ---
      double maxResidual = 0.0;
      for (int i = 0; i < dim; i++) {
        maxResidual = Math.max(maxResidual, Math.abs(residual[i]));
      }
      if (maxResidual < CONVERGENCE_TOL && iterations > 1) {
        converged = true;
        break;
      }

      // --- Build full Newton Jacobian (every iteration) ---
      buildReducedJacobian(jacobian, xType, tMoles, redSum, totalVol, gdv1, zeta, Btemp, dim, p);
      jacobianEvals++;

      // --- Solve J * dx = -R using Gaussian elimination ---
      for (int i = 0; i < dim; i++) {
        dx[i] = -residual[i];
      }
      boolean solveOk = solveLinearSystem(jacobian, dx, dim);
      if (!solveOk) {
        fallbackCount++;
        return super.molarVolume(pressure, temperature, A, B, pt);
      }

      // --- Step limiting ---
      double maxStep = 1.0;
      for (int t = 0; t < p; t++) {
        double proposed = xType[t] + maxStep * dx[t];
        if (proposed < 1.0e-15) {
          double limit = MAX_REL_STEP * xType[t] / Math.abs(dx[t]);
          maxStep = Math.min(maxStep, limit);
        }
        if (proposed > 1.0) {
          double limit = (1.0 - xType[t]) / dx[t];
          maxStep = Math.min(maxStep, Math.max(0.1, limit));
        }
      }
      double proposedZeta = zeta + maxStep * dx[p];
      if (proposedZeta < 1.0e-10) {
        double limit = MAX_REL_STEP * zeta / Math.abs(dx[p]);
        maxStep = Math.min(maxStep, limit);
      }
      if (proposedZeta > 1.0 - 1.0e-10) {
        double limit = (1.0 - 1.0e-10 - zeta) / dx[p];
        maxStep = Math.min(maxStep, Math.max(0.1, limit));
      }
      if (Math.abs(dx[p]) / Math.max(zeta, 1.0e-10) > 0.3) {
        maxStep = Math.min(maxStep, 0.3 * zeta / Math.abs(dx[p]));
      }

      // --- Apply update ---
      for (int t = 0; t < p; t++) {
        xType[t] += maxStep * dx[t];
        xType[t] = Math.max(1.0e-15, Math.min(1.0, xType[t]));
      }
      zeta += maxStep * dx[p];
      zeta = Math.max(1.0e-10, Math.min(1.0 - 1.0e-10, zeta));

      // --- Restart criterion (supercritical detection) ---
      if (iterations > 20 && maxResidual > 0.1 && !restartTriggered) {
        restartTriggered = true;
        if (pt == PhaseType.GAS) {
          zeta = 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
        } else {
          zeta = pressure * Btemp / (numberOfMolesInPhase * temperature * R);
        }
        zeta = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, zeta));
        for (int t = 0; t < p; t++) {
          xType[t] = 0.5;
        }
      }

    } while (iterations < MAX_ITERATIONS);

    if (!converged) {
      fallbackCount++;
      if (logger.isDebugEnabled()) {
        logger.debug("Implicit-reduced non-convergence: ns=" + ns + " p=" + numTypes + " pt=" + pt
            + " iters=" + iterations + " zeta=" + zeta + " P="
            + pressure + " T=" + temperature);
      }
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    totalIters += iterations;

    // --- Expand reduced X to individual sites and finalize ---
    double finalMolarVol = Btemp / (numberOfMolesInPhase * zeta);
    setMolarVolume(finalMolarVol);
    Z = pressure * finalMolarVol / (R * temperature);
    expandAndSetSiteFractions(xType, ns);

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
  // Site type mapping
  // ===========================================================================

  /**
   * Build the site type map by grouping equivalent association sites.
   *
   * <p>
   * Two individual sites are equivalent if they belong to the same component and
   * have identical
   * deltaNog rows (same bonding pattern to all other sites). This corresponds to
   * sites with the
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
   * @param ns    total number of individual sites
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
   * @param ns    total number of individual sites
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
  // Reduced-dimension Jacobian
  // ===========================================================================

  /**
   * Build the analytic Jacobian for the reduced (p+1)-dimensional system.
   *
   * <p>
   * Block structure:
   * </p>
   * <ul>
   * <li>J_XX[a][b]: site fraction block with multiplicity weighting</li>
   * <li>j_X_zeta[a]: site fraction sensitivity to volume (via zeta)</li>
   * <li>j_zeta_X[a]: volume equation sensitivity to site fractions</li>
   * <li>j_zeta_zeta: volume equation self-sensitivity</li>
   * </ul>
   *
   * @param jac      Jacobian matrix to populate (dim x dim)
   * @param xType    reduced site fraction values (length p)
   * @param tMoles   moles per type (length p)
   * @param redSum   precomputed reduced summation for each type (length p)
   * @param totalVol total volume V
   * @param gdv1     g'(V) - 1/V
   * @param zeta     current B/(nV)
   * @param btemp    co-volume B
   * @param dim      system dimension (p+1)
   * @param p        number of unique site types
   */
  private void buildReducedJacobian(double[][] jac, double[] xType, double[] tMoles,
      double[] redSum, double totalVol, double gdv1, double zeta, double btemp, int dim, int p) {

    // J_XX block: dR_a/dX_b = delta_ab + X_a^2 * mult_b * n_b * delta(rep_a,rep_b)
    // / V
    for (int a = 0; a < p; a++) {
      double xa2 = xType[a] * xType[a];
      for (int b = 0; b < p; b++) {
        double dab = (a == b) ? 1.0 : 0.0;
        double dlt = delta[typeRepSite[a]][typeRepSite[b]];
        jac[a][b] = dab + xa2 * typeMult[b] * tMoles[b] * dlt / totalVol;
      }
    }

    // j_X_zeta column: dR_a/dzeta
    double dVdZeta = -btemp / (numberOfMolesInPhase * zeta * zeta);
    double totalVol2 = totalVol * totalVol;
    for (int a = 0; a < p; a++) {
      double xa2 = xType[a] * xType[a];
      double sumDeriv = 0.0;
      for (int b = 0; b < p; b++) {
        double dlt = delta[typeRepSite[a]][typeRepSite[b]];
        double dDeltadV = dlt * gcpav;
        sumDeriv += typeMult[b] * tMoles[b] * xType[b] * (dDeltadV * totalVol - dlt) / totalVol2;
      }
      jac[a][p] = xa2 * sumDeriv * dVdZeta;
    }

    // j_zeta_X row: dR_zeta/dX_a
    for (int a = 0; a < p; a++) {
      jac[p][a] = btemp / numberOfMolesInPhase * cpaon * typeMult[a] * (tMoles[a] / totalVol) * gdv1
          * redSum[a];
    }

    // j_zeta_zeta scalar
    double BonV2 = zeta * zeta;
    jac[p][p] = 1.0 + btemp / BonV2 * (btemp / numberOfMolesInPhase * dFdVdV());
  }

  /**
   * Multiply delta by g-function for all individual sites.
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
   * Override type 1 initialization to use reduced-dimension site type
   * computation. Higher-order
   * volume derivatives (FCPA, dFCPAdV, dFCPAdVdV, dFCPAdVdVdV) are computed using
   * the type
   * grouping, reducing linear system size from n_s to p.
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
    double fVVV = fV * fV * fV + 3.0 * fV * (gcpavv + 1.0 / totalVolume2) + gcpavvv - 2.0 / totalVolume3;

    // Read reduced site fractions and moles
    double[] xSiteFull = new double[ns];
    readXsiteFromComponents(xSiteFull, ns);
    for (int t = 0; t < p; t++) {
      workKsi[t] = xSiteFull[typeRepSite[t]];
      workM[t] = componentArray[typeCompIdx[t]].getNumberOfMolesInPhase();
    }

    // Build reduced K matrix: K_red[a][b] = mult_a * mult_b * n_a * n_b *
    // delta(rep_a,rep_b) / V
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

  /**
   * Solve a linear system A*x = b in-place (b overwritten with solution) using
   * Gaussian elimination
   * with partial pivoting.
   *
   * @param a coefficient matrix (modified in-place)
   * @param b right-hand side vector (overwritten with solution)
   * @param n system dimension
   * @return true if successful, false if singular
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

  /** {@inheritDoc} */
  @Override
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return super.molarVolumeChangePhase(pressure, temperature, A, B, pt);
  }

  // ===========================================================================
  // Profiling accessors
  // ===========================================================================

  /**
   * Get the total number of molarVolume calls.
   *
   * @return call count
   */
  public long getCallCount() {
    return callCount;
  }

  /**
   * Get the total Newton iterations across all calls.
   *
   * @return total iteration count
   */
  public long getTotalIters() {
    return totalIters;
  }

  /**
   * Get the number of Jacobian evaluations.
   *
   * @return Jacobian evaluation count
   */
  public long getJacobianEvals() {
    return jacobianEvals;
  }

  /**
   * Get the number of solver fallbacks to the base class.
   *
   * @return fallback count
   */
  public long getFallbackCount() {
    return fallbackCount;
  }

  /**
   * Get the number of unique site types from the last call.
   *
   * @return last number of unique types
   */
  public int getLastNumTypes() {
    return lastNumTypes;
  }

  /**
   * Get the number of full sites from the last call.
   *
   * @return last full site count
   */
  public int getLastFullSites() {
    return lastFullSites;
  }
}
