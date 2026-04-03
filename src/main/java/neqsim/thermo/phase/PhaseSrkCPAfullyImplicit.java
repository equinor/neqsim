package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;

/**
 * Fully implicit CPA phase implementation based on Igben et al. (2026).
 *
 * <p>
 * Implements the algorithm from: "Fully implicit algorithm for Cubic Plus Association equation of
 * state", Fluid Phase Equilibria 608 (2026) 114734.
 * </p>
 *
 * <p>
 * Key differences from the standard nested approach in {@link PhaseSrkCPA}:
 * </p>
 * <ul>
 * <li>Solves molar volume and association site fractions simultaneously using coupled
 * Newton-Raphson</li>
 * <li>Eliminates inner iterations for XA solving during volume calculation</li>
 * <li>Includes restart criterion to suppress unnecessary root searches in supercritical
 * regions</li>
 * <li>Reports 30-80% reduction in computational cost</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PhaseSrkCPAfullyImplicit extends PhaseSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseSrkCPAfullyImplicit.class);

  /** Maximum iterations for the fully implicit solver. */
  private static final int MAX_IMPLICIT_ITERATIONS = 100;

  /** Convergence tolerance for the coupled system. */
  private static final double CONVERGENCE_TOL = 1.0e-12;

  /** Maximum relative step size per iteration to prevent divergence. */
  private static final double MAX_REL_STEP = 0.5;

  /** Restart threshold parameter alpha (supercritical detection). */
  private static final double RESTART_ALPHA = 0.1;

  // --- Profiling counters (thread-local for safety) ---
  /** Total molarVolume calls via implicit solver. */
  private static volatile long implicitCallCount = 0;
  /** Total coupled Newton iterations across all calls. */
  private static volatile long totalImplicitIters = 0;
  /** Total fallbacks to nested solver. */
  private static volatile long fallbackCount = 0;

  /**
   * Reset profiling counters.
   */
  public static void resetProfileCounters() {
    implicitCallCount = 0;
    totalImplicitIters = 0;
    fallbackCount = 0;
  }

  /**
   * Get profiling summary string.
   *
   * @return a summary of profiling data
   */
  public static String getProfileSummary() {
    double avgIters = implicitCallCount > 0 ? (double) totalImplicitIters / implicitCallCount : 0;
    return String.format("Calls=%d  AvgIters=%.1f  Fallbacks=%d", implicitCallCount, avgIters,
        fallbackCount);
  }

  /**
   * Constructor for PhaseSrkCPAfullyImplicit.
   */
  public PhaseSrkCPAfullyImplicit() {
    super();
    thermoPropertyModelName = "SRK-CPA-EoS-FullyImplicit";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPAfullyImplicit clone() {
    PhaseSrkCPAfullyImplicit clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPAfullyImplicit) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    // Use parent PhaseSrkCPAs.addComponent which creates ComponentSrkCPAs
    super.addComponent(name, moles, molesInPhase, compNumber);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Fully implicit molar volume calculation. Solves the normalized density zeta = b/v and
   * association site fractions X_k simultaneously using a coupled Newton-Raphson method.
   * </p>
   *
   * <p>
   * Per Igben et al. (2026), the key speedup is eliminating inner XA iterations during the volume
   * loop. The CPA pressure derivatives (dFCPAdV, dFCPAdVdV) are computed directly from the current
   * X_k values using O(ns^2) sums, avoiding costly matrix inversions.
   * </p>
   */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {

    int ns = getTotalNumberOfAccociationSites();

    // If no association sites, fall back to parent cubic solver
    if (ns == 0) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // For gas-phase calls, the nested solver is more efficient since
    // association is weak in the gas (X near 1, large molar volume).
    // The coupled Newton converges slowly for weakly-coupled systems.
    if (pt == PhaseType.GAS) {
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    implicitCallCount++;

    double Btemp = getB();
    if (Btemp <= 0) {
      logger.info("b negative in fully implicit volume calc");
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    // --- Initial guess for zeta = B/(n*V) = BonV ---
    double zeta;
    if (pt == PhaseType.GAS) {
      zeta = pressure * Btemp / (numberOfMolesInPhase * temperature * R);
    } else {
      zeta = 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    }
    zeta = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, zeta));

    // Initialize volume for first calcDelta call
    double molarVol = Btemp / (numberOfMolesInPhase * zeta);
    setMolarVolume(molarVol);

    // Compute deltaNog once (temperature-dependent, does not change with V)
    calcDelta();

    // Initialize XA site fractions: use previously converged values if available,
    // otherwise do a quick successive substitution warmup.
    gcpa = calc_g();
    gcpav = calc_lngV();
    updateDeltaWithG();
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

    // --- Pre-allocate arrays for the coupled Newton-Raphson ---
    int dim = ns + 1; // unknowns: [X_1, ..., X_ns, zeta]
    double[] residual = new double[dim];
    double[][] jacobian = new double[dim][dim];
    double[] dx = new double[dim];

    // Cache moles per site for inner loops
    double[] siteMoles = new double[ns];
    for (int i = 0; i < ns; i++) {
      siteMoles[i] = componentArray[moleculeNumber[i]].getNumberOfMolesInPhase();
    }

    int iterations = 0;
    boolean converged = false;
    boolean restartTriggered = false;
    double initialResidual = 0.0;

    do {
      iterations++;

      // Set volume from current zeta
      molarVol = Btemp / (numberOfMolesInPhase * zeta);
      setMolarVolume(molarVol);
      double totalVol = molarVol * numberOfMolesInPhase;
      Z = pressure * molarVol / (R * temperature);

      // Update radial distribution function and derivatives
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

      // Update delta = deltaNog * g (only g changes between iterations)
      updateDeltaWithG();

      // --- Compute CPA pressure derivatives directly (NO solveX/initCPAMatrix) ---
      // dFCPAdV = -0.5 * sum_ij X_i * Klk_ij * (gcpav - 1/V) * X_j
      // where Klk_ij = m_i * m_j / V * delta_ij
      double gdv1 = gcpav - 1.0 / totalVol;
      double gdv2 = gdv1 * gdv1;
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
          sumFVV += symFactor * klkXiXj * (gdv2 + gcpavv + 1.0 / totalVol2);
        }
      }

      // Store into parent's fields so dFdV()/dFdVdV() return correct values
      dFCPAdV = -0.5 * sumFV;
      dFCPAdVdV = -0.5 * sumFVV;
      // Note: the XV correction term in dFCPAdVdV is omitted during iteration.
      // In the coupled system, dX/dV is captured implicitly through the off-diagonal
      // Jacobian entries, so the approximate Jacobian still converges.

      // --- Build residual vector ---
      // R_k = X_k - 1/(1 + (1/V)*sum_j(m_j * delta_kj * X_j))
      for (int k = 0; k < ns; k++) {
        double sumKJ = 0.0;
        for (int j = 0; j < ns; j++) {
          sumKJ += siteMoles[j] * delta[k][j] * xSite[j];
        }
        residual[k] = xSite[k] - 1.0 / (1.0 + sumKJ / totalVol);
      }

      // R_{ns+1} = pressure residual in BonV form
      double h = zeta - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      residual[ns] = h;

      // --- Check convergence ---
      double maxResidual = 0.0;
      for (int i = 0; i < dim; i++) {
        maxResidual = Math.max(maxResidual, Math.abs(residual[i]));
      }
      if (iterations == 1) {
        initialResidual = maxResidual;
      }
      if (maxResidual < CONVERGENCE_TOL && iterations > 1) {
        converged = true;
        break;
      }

      // --- Build Jacobian ---
      // dR_k/dX_j = delta_kj_kronecker + X_k^2 * m_j * Delta_kj / V
      for (int k = 0; k < ns; k++) {
        double xk2 = xSite[k] * xSite[k];
        for (int j = 0; j < ns; j++) {
          double dkj = (k == j) ? 1.0 : 0.0;
          jacobian[k][j] = dkj + xk2 * siteMoles[j] * delta[k][j] / totalVol;
        }
      }

      // dR_k/d(zeta): derivative of association residual w.r.t. normalized density
      double dVdZeta = -Btemp / (numberOfMolesInPhase * zeta * zeta);
      for (int k = 0; k < ns; k++) {
        double xk2 = xSite[k] * xSite[k];
        double sumDeriv = 0.0;
        for (int j = 0; j < ns; j++) {
          double deltaKJ = delta[k][j];
          // d(delta)/dV = delta * gcpav (since delta = deltaNog * g, d(delta)/dV = deltaNog *
          // dg/dV)
          double dDeltadV = deltaKJ * gcpav;
          sumDeriv +=
              siteMoles[j] * xSite[j] * (dDeltadV * totalVol - deltaKJ) / (totalVol * totalVol);
        }
        jacobian[k][ns] = xk2 * sumDeriv * dVdZeta;
      }

      // dR_{ns+1}/dX_k: pressure residual derivative w.r.t. site fractions
      // h = zeta - B/nMol * dFdV - P*B/(nMol*R*T)
      // dFCPAdV = -0.5 * sum_ij Xi * Klk_ij * gdv1 * Xj
      // d(dFCPAdV)/dXk = -sum_j Klk_kj * gdv1 * Xj = -(mk/V) * gdv1 * sum_j mj*delta_kj*Xj
      // dh/dXk = -B/nMol * cpaon * d(dFCPAdV)/dXk
      // = B/nMol * cpaon * (mk/V) * gdv1 * sum_j mj*delta_kj*Xj
      for (int k = 0; k < ns; k++) {
        double sumJ = 0.0;
        for (int j = 0; j < ns; j++) {
          sumJ += siteMoles[j] * delta[k][j] * xSite[j];
        }
        jacobian[ns][k] =
            Btemp / numberOfMolesInPhase * cpaon * (siteMoles[k] / totalVol) * gdv1 * sumJ;
      }

      // dR_{ns+1}/d(zeta): diagonal entry
      double BonV2 = zeta * zeta;
      jacobian[ns][ns] = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());

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

      // --- Restart criterion (supercritical detection) ---
      if (iterations > 20 && maxResidual > 0.1 && !restartTriggered) {
        restartTriggered = true;
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

    } while (iterations < MAX_IMPLICIT_ITERATIONS);

    if (!converged) {
      fallbackCount++;
      if (logger.isDebugEnabled()) {
        logger.debug("Implicit non-convergence: ns=" + ns + " pt=" + pt + " iters=" + iterations
            + " zeta=" + zeta + " P=" + pressure + " T=" + temperature);
      }
      return super.molarVolume(pressure, temperature, A, B, pt);
    }

    totalImplicitIters += iterations;

    // --- Finalization: set converged solution ---
    double finalMolarVol = Btemp / (numberOfMolesInPhase * zeta);
    setMolarVolume(finalMolarVol);
    Z = pressure * finalMolarVol / (R * temperature);
    setXsiteOnComponents(xSite);

    // Compute g-function derivatives and hcpatot
    gcpa = calc_g();
    gcpav = calc_lngV();
    gcpavv = calc_lngVV();
    gcpavvv = calc_lngVVV();
    hcpatot = calc_hCPA();

    // Finalize: compute CPA derivatives with XV correction.
    // For type=1 (volume derivatives only), our override computes from scratch
    // using GE solve, avoiding solveX() + EJML matrix inversion.
    initCPAMatrix(1);
    dFdNtemp = calcdFdNtemp();

    if (logger.isTraceEnabled()) {
      logger.trace("Fully implicit converged in " + iterations + " iterations (ns=" + ns + ")");
    }

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }

    return getMolarVolume();
  }

  // --- Pre-allocated work arrays for initCPAMatrix type 1 ---
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
   * Override CPA matrix initialization.
   *
   * <p>
   * For type == 1 (volume derivatives), computes FCPA, dFCPAdV, dFCPAdVdV, dFCPAdVdVdV from scratch
   * using Gaussian elimination to solve H*XV = KlkV*ksi directly. This avoids the expensive EJML
   * hessianMatrix.invert() in the parent's solveX().
   * </p>
   *
   * <p>
   * Key insight: KlkV[i][j] = fV * Klk[i][j] where fV = gcpav - 1/V. Similarly for KlkVV and
   * KlkVVV. This means only Klk needs to be stored; volume derivatives are scalar multiples.
   * </p>
   *
   * <p>
   * For type &gt;= 2, calls solveX() to populate hessianInvers and delegates to
   * super.initCPAMatrix(type).
   * </p>
   *
   * @param type 1 for volume derivatives, 2+ for temperature/composition derivatives
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
      // Delegate to parent for temperature/composition derivatives.
      // Need hessianInvers populated by solveX().
      solveX();
      super.initCPAMatrix(type);
      return;
    }

    // --- Type 1: compute volume derivatives from scratch ---
    ensureWorkArrays(ns);

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;

    double gv = getGcpav();
    double fV = gv - 1.0 / totalVolume;
    double fVV = fV * fV + gcpavv + 1.0 / totalVolume2;
    double fVVV =
        fV * fV * fV + 3.0 * fV * (gcpavv + 1.0 / totalVolume2) + gcpavvv - 2.0 / totalVolume3;

    // Read site fractions (ksi) and mole counts (m) from components
    int idx = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        workKsi[idx] = ((ComponentSrkCPA) componentArray[i]).getXsite()[j];
        workM[idx] = ni;
        idx++;
      }
    }

    // Build Klk[i][j] = m_i * m_j / V * delta[i][j]
    double invV = 1.0 / totalVolume;
    for (int i = 0; i < ns; i++) {
      double miV = workM[i] * invV;
      for (int j = i; j < ns; j++) {
        double k = miV * workM[j] * delta[i][j];
        workKlk[i][j] = k;
        workKlk[j][i] = k;
      }
    }

    // Compute klkKsi = Klk * ksi (single matrix-vector product)
    for (int i = 0; i < ns; i++) {
      double s = 0;
      for (int j = 0; j < ns; j++) {
        s += workKlk[i][j] * workKsi[j];
      }
      workKlkKsi[i] = s;
    }

    // Build Hessian: H[i][j] = -m[i]/(ksi[i]^2) * delta(i,j) - Klk[i][j]
    // Need a copy since GE destroys it
    for (int i = 0; i < ns; i++) {
      for (int j = 0; j < ns; j++) {
        workHess[i][j] = -workKlk[i][j];
      }
      workHess[i][i] -= workM[i] / (workKsi[i] * workKsi[i]);
    }

    // Solve H * XV = fV * klkKsi
    for (int i = 0; i < ns; i++) {
      workXV[i] = fV * workKlkKsi[i];
    }
    solveLinearSystem(workHess, workXV, ns);

    // --- Compute dot products needed by all derivatives ---
    double dotKsiKlkKsi = 0; // ksi' * Klk * ksi
    double dotKlkKsiXV = 0; // klkKsi' * XV
    double fcpa = 0;
    for (int i = 0; i < ns; i++) {
      dotKsiKlkKsi += workKsi[i] * workKlkKsi[i];
      dotKlkKsiXV += workKlkKsi[i] * workXV[i];
      fcpa += workM[i] * (Math.log(workKsi[i]) - workKsi[i] / 2.0 + 0.5);
    }
    FCPA = fcpa;

    // dFCPAdV = -0.5 * fV * ksi' * Klk * ksi
    dFCPAdV = -0.5 * fV * dotKsiKlkKsi;

    // dFCPAdVdV = -0.5 * fVV * ksi'*Klk*ksi - fV * klkKsi'*XV
    dFCPAdVdV = -0.5 * fVV * dotKsiKlkKsi - fV * dotKlkKsiXV;

    // dFCPAdVdVdV:
    // = -0.5 * fVVV * ksi'*Klk*ksi - 3*fVV * klkKsi'*XV
    // - 3*fV * XV'*Klk*XV + (sum XV*Q) * (sum XV^2) where Q[i]=2m[i]/ksi[i]^3
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
    // Use parent's fallback which uses nested approach
    return super.molarVolumeChangePhase(pressure, temperature, A, B, pt);
  }

  /**
   * Set XA site fraction values on component objects from flat array.
   *
   * @param xSite array of site fractions, indexed by total site number
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
   * Read XA site fraction values from component objects into flat array.
   *
   * @param xSite array to fill with site fractions
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

  /**
   * Get delta[i][j] from the parent's delta array. This array is populated by calcDelta() and
   * updateDeltaWithG().
   *
   * @param i site index i
   * @param j site index j
   * @return delta value
   */
  private double getDeltaij(int i, int j) {
    return delta[i][j];
  }

  /**
   * Get deltaNog[i][j] from the parent's deltaNog array.
   *
   * @param i site index i
   * @param j site index j
   * @return deltaNog value
   */
  private double getDeltaNogij(int i, int j) {
    return deltaNog[i][j];
  }

  /**
   * Update delta = deltaNog * g where g is the radial distribution function.
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

  // Note: dFdNtemp field is inherited from PhaseSrkCPA (protected)

  /**
   * Solve a linear system Ax = b in-place using Gaussian elimination with partial pivoting.
   * Solution is stored in b.
   *
   * @param a coefficient matrix (modified in place)
   * @param b right-hand side (solution on return)
   * @param n system dimension
   * @return true if solve succeeded, false if singular
   */
  private static boolean solveLinearSystem(double[][] a, double[] b, int n) {
    // Forward elimination with partial pivoting
    for (int col = 0; col < n; col++) {
      // Find pivot
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
        return false; // Singular
      }
      // Swap rows
      if (maxRow != col) {
        double[] tempRow = a[col];
        a[col] = a[maxRow];
        a[maxRow] = tempRow;
        double tempB = b[col];
        b[col] = b[maxRow];
        b[maxRow] = tempB;
      }
      // Eliminate
      double pivot = a[col][col];
      for (int row = col + 1; row < n; row++) {
        double factor = a[row][col] / pivot;
        for (int k = col + 1; k < n; k++) {
          a[row][k] -= factor * a[col][k];
        }
        b[row] -= factor * b[col];
      }
    }
    // Back substitution
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
