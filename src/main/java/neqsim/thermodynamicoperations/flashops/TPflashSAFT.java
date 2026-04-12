package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSAFTVRMie;

/**
 * TP flash for SAFT-VR Mie equation of state.
 *
 * <p>
 * Uses successive substitution with separate single-phase systems for liquid and gas volume
 * solving. This avoids the issue where the SAFT volume solver cannot find both gas and liquid roots
 * at the feed composition (unlike cubic EOS where the cubic always has mathematical roots).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TPflashSAFT extends TPflash {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TPflashSAFT.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Maximum number of successive substitution iterations. */
  private static final int MAX_SS_ITER = 50;
  /** K-value relative convergence tolerance. */
  private static final double K_TOL = 1.0e-6;
  /** Phase fraction minimum limit. */
  private static final double BETA_MIN = 1.0e-10;

  /**
   * Constructor for TPflashSAFT.
   *
   * @param system the thermodynamic system to flash
   */
  public TPflashSAFT(SystemInterface system) {
    super(system);
  }

  /**
   * Constructor for TPflashSAFT.
   *
   * @param system the thermodynamic system to flash
   * @param checkForSolids Set true to do solid phase check
   */
  public TPflashSAFT(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int nc = system.getNumberOfComponents();
    double T = system.getTemperature();
    double P = system.getPressure();

    // Get feed composition
    double[] z = new double[nc];
    for (int i = 0; i < nc; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }

    // Initialize K-values from Wilson equation
    double[] K = new double[nc];
    system.init(0);
    for (int i = 0; i < nc; i++) {
      K[i] = system.getPhase(0).getComponent(i).getK();
      if (K[i] <= 0 || Double.isNaN(K[i])) {
        K[i] = 1.0;
      }
    }

    // Successive substitution loop for 2-phase VLE
    double[] x = new double[nc];
    double[] y = new double[nc];
    double beta = 0.5;
    boolean converged = false;

    for (int iter = 0; iter < MAX_SS_ITER; iter++) {
      // Solve Rachford-Rice for vapor fraction beta
      beta = solveRachfordRice(z, K, beta);

      if (beta < BETA_MIN || beta > 1.0 - BETA_MIN) {
        break;
      }

      // Compute phase compositions
      for (int i = 0; i < nc; i++) {
        x[i] = z[i] / (1.0 + beta * (K[i] - 1.0));
        y[i] = K[i] * x[i];
      }

      // Normalize (numerical safety)
      double sumx = 0;
      double sumy = 0;
      for (int i = 0; i < nc; i++) {
        sumx += x[i];
        sumy += y[i];
      }
      for (int i = 0; i < nc; i++) {
        x[i] /= sumx;
        y[i] /= sumy;
      }

      // Create separate systems for each phase and solve volumes
      double[] phiL = computeFugacityCoefficients(T, P, x, PhaseType.LIQUID);
      double[] phiG = computeFugacityCoefficients(T, P, y, PhaseType.GAS);

      if (phiL == null || phiG == null) {
        logger.debug("SAFT flash: fugacity computation failed at iter {}", iter);
        break;
      }

      // Update K-values: K_i = phi_liquid_i / phi_gas_i
      double maxDeltaK = 0;
      for (int i = 0; i < nc; i++) {
        double Knew = phiL[i] / phiG[i];
        if (Double.isNaN(Knew) || Knew <= 0) {
          Knew = K[i];
        }
        double dK = Math.abs(Knew - K[i]) / Math.max(Math.abs(K[i]), 1e-10);
        maxDeltaK = Math.max(maxDeltaK, dK);
        K[i] = Knew;
      }

      if (maxDeltaK < K_TOL) {
        converged = true;
        break;
      }
    }

    // Apply results to system
    if (converged && beta > BETA_MIN && beta < 1.0 - BETA_MIN) {
      // Two-phase result
      system.setNumberOfPhases(2);
      system.setBeta(beta);

      for (int i = 0; i < nc; i++) {
        system.getPhase(0).getComponent(i).setK(K[i]);
        system.getPhase(1).getComponent(i).setK(K[i]);
      }
      system.calc_x_y();

      system.setPhaseType(0, PhaseType.GAS);
      system.setPhaseType(1, PhaseType.LIQUID);
      system.init(1);
      system.orderByDensity();

      // If multi-phase check enabled, try to split liquid phase into two
      if (system.doMultiPhaseCheck()) {
        tryLiquidLiquidSplit(T, P, z, K, beta, x, y);
      }
    } else {
      // Single phase: determine if gas or liquid by Gibbs energy
      system.setNumberOfPhases(1);

      double gGas = computeGibbsEnergy(T, P, z, PhaseType.GAS);
      double gLiq = computeGibbsEnergy(T, P, z, PhaseType.LIQUID);

      if (Double.isFinite(gGas) && Double.isFinite(gLiq)) {
        if (gGas < gLiq) {
          system.setPhaseType(0, PhaseType.GAS);
        } else {
          system.setPhaseType(0, PhaseType.LIQUID);
        }
      }

      system.init(1);
    }
  }

  /**
   * Attempt to split the liquid phase into two liquid phases (VLLE).
   *
   * <p>
   * After a converged VLE flash, checks if the liquid phase is unstable with respect to
   * liquid-liquid splitting. Uses successive substitution on a 3-phase Rachford-Rice formulation.
   * </p>
   *
   * @param T temperature in K
   * @param P pressure in bar
   * @param z feed mole fractions
   * @param Kvle VLE K-values from 2-phase flash
   * @param betaVLE vapor fraction from 2-phase flash
   * @param xLiq liquid composition from 2-phase flash
   * @param yGas gas composition from 2-phase flash
   */
  private void tryLiquidLiquidSplit(double T, double P, double[] z, double[] Kvle, double betaVLE,
      double[] xLiq, double[] yGas) {
    int nc = z.length;

    // Initialize K_LL values for liquid-liquid split
    // Use heuristic: find component with highest and lowest fugacity coefficient ratio
    double[] phiL = computeFugacityCoefficients(T, P, xLiq, PhaseType.LIQUID);
    if (phiL == null) {
      return;
    }

    // Try a perturbation of the liquid composition toward each pure component
    // to see if a second liquid phase is more stable
    double[] K_LL = new double[nc];
    boolean foundInstability = false;

    // For each component, try a composition enriched in that component
    for (int trial = 0; trial < nc && !foundInstability; trial++) {
      double[] wTrial = new double[nc];
      for (int i = 0; i < nc; i++) {
        wTrial[i] = 0.01 / (nc - 1);
      }
      wTrial[trial] = 0.99;

      double[] phiTrial = computeFugacityCoefficients(T, P, wTrial, PhaseType.LIQUID);
      if (phiTrial == null) {
        continue;
      }

      // Tangent plane distance: TPD = sum_i w_i * [ln(w_i) + ln(phi_trial_i) - ln(x_i) -
      // ln(phi_x_i)]
      double tpd = 0;
      for (int i = 0; i < nc; i++) {
        if (wTrial[i] > 1e-15 && xLiq[i] > 1e-15) {
          tpd += wTrial[i] * (Math.log(wTrial[i]) + Math.log(phiTrial[i]) - Math.log(xLiq[i])
              - Math.log(phiL[i]));
        }
      }

      if (tpd < -1e-4) {
        foundInstability = true;
        for (int i = 0; i < nc; i++) {
          K_LL[i] = (phiL[i] > 1e-15) ? phiTrial[i] / phiL[i] : 1.0;
          if (K_LL[i] <= 0 || Double.isNaN(K_LL[i])) {
            K_LL[i] = 1.0;
          }
        }
        logger.debug("SAFT VLLE: TPD={} for trial enriched in component {}", tpd, trial);
      }
    }

    if (!foundInstability) {
      return;
    }

    // 3-phase successive substitution
    // Phase arrangement: gas(V), liquid1(L1), liquid2(L2)
    // K_V_i = y_i / s_i, K_L1_i = x1_i / s_i where s_i=L2 composition
    double[] K_V = new double[nc];
    for (int i = 0; i < nc; i++) {
      K_V[i] = Kvle[i]; // K_V from VLE flash
    }

    double betaV = betaVLE;
    double betaL1 = 0.4 * (1.0 - betaV); // Initial guess: split remaining equally
    boolean vlleConverged = false;

    for (int iter = 0; iter < MAX_SS_ITER; iter++) {
      double betaL2 = 1.0 - betaV - betaL1;

      // 3-phase compositions: s_i = z_i / [1 + betaV*(K_V_i-1) + betaL1*(K_LL_i-1)]
      double[] s = new double[nc]; // L2 composition (reference phase)
      double[] x1 = new double[nc]; // L1 composition
      double[] w = new double[nc]; // Gas composition

      double sumS = 0;
      double sumX1 = 0;
      double sumW = 0;
      for (int i = 0; i < nc; i++) {
        double denom = 1.0 + betaV * (K_V[i] - 1.0) + betaL1 * (K_LL[i] - 1.0);
        if (denom < 1e-15) {
          denom = 1e-15;
        }
        s[i] = z[i] / denom;
        x1[i] = K_LL[i] * s[i];
        w[i] = K_V[i] * s[i];
        sumS += s[i];
        sumX1 += x1[i];
        sumW += w[i];
      }

      // Normalize
      for (int i = 0; i < nc; i++) {
        s[i] /= sumS;
        x1[i] /= sumX1;
        w[i] /= sumW;
      }

      // Compute fugacity coefficients for all three phases
      double[] phiG = computeFugacityCoefficients(T, P, w, PhaseType.GAS);
      double[] phiL1 = computeFugacityCoefficients(T, P, x1, PhaseType.LIQUID);
      double[] phiL2 = computeFugacityCoefficients(T, P, s, PhaseType.LIQUID);

      if (phiG == null || phiL1 == null || phiL2 == null) {
        return;
      }

      // Update K-values
      double maxDeltaK = 0;
      for (int i = 0; i < nc; i++) {
        double newKV = phiL2[i] / phiG[i];
        double newKLL = phiL2[i] / phiL1[i];

        if (Double.isNaN(newKV) || newKV <= 0) {
          newKV = K_V[i];
        }
        if (Double.isNaN(newKLL) || newKLL <= 0) {
          newKLL = K_LL[i];
        }

        double dKV = Math.abs(newKV - K_V[i]) / Math.max(Math.abs(K_V[i]), 1e-10);
        double dKLL = Math.abs(newKLL - K_LL[i]) / Math.max(Math.abs(K_LL[i]), 1e-10);
        maxDeltaK = Math.max(maxDeltaK, Math.max(dKV, dKLL));

        K_V[i] = newKV;
        K_LL[i] = newKLL;
      }

      // Solve 3-phase Rachford-Rice for betaV and betaL1
      double[] betas = solve3PhaseRachfordRice(z, K_V, K_LL, betaV, betaL1);
      betaV = betas[0];
      betaL1 = betas[1];

      // Check convergence
      if (maxDeltaK < K_TOL) {
        betaL2 = 1.0 - betaV - betaL1;
        if (betaV > BETA_MIN && betaL1 > BETA_MIN && betaL2 > BETA_MIN) {
          vlleConverged = true;
        }
        break;
      }

      // Check if any phase vanishes
      betaL2 = 1.0 - betaV - betaL1;
      if (betaV < BETA_MIN || betaL1 < BETA_MIN || betaL2 < BETA_MIN) {
        break;
      }
    }

    if (!vlleConverged) {
      return;
    }

    // Apply 3-phase result
    double betaL2 = 1.0 - betaV - betaL1;
    logger.debug("SAFT VLLE converged: betaV={}, betaL1={}, betaL2={}", betaV, betaL1, betaL2);

    system.setNumberOfPhases(3);

    // Phase 0 = gas, Phase 1 = liquid1, Phase 2 = liquid2
    // Compute final compositions
    double[] sFinal = new double[nc];
    double[] x1Final = new double[nc];
    double[] wFinal = new double[nc];
    for (int i = 0; i < nc; i++) {
      double denom = 1.0 + betaV * (K_V[i] - 1.0) + betaL1 * (K_LL[i] - 1.0);
      sFinal[i] = z[i] / denom;
      x1Final[i] = K_LL[i] * sFinal[i];
      wFinal[i] = K_V[i] * sFinal[i];
    }
    normalize(sFinal);
    normalize(x1Final);
    normalize(wFinal);

    // Set phase types
    system.setPhaseType(0, PhaseType.GAS);
    system.setPhaseType(1, PhaseType.LIQUID);
    system.setPhaseType(2, PhaseType.LIQUID);

    // Set beta (fraction of gas phase)
    system.setBeta(betaV);
    system.setBeta(2, betaL1);
    system.setBeta(3, betaL2);

    // Set compositions directly
    for (int i = 0; i < nc; i++) {
      system.getPhase(0).getComponent(i).setx(wFinal[i]);
      system.getPhase(1).getComponent(i).setx(x1Final[i]);
      system.getPhase(2).getComponent(i).setx(sFinal[i]);

      system.getPhase(0).getComponent(i).setK(K_V[i]);
      system.getPhase(1).getComponent(i).setK(K_V[i]);
      system.getPhase(2).getComponent(i).setK(K_V[i]);
    }

    // Initialize phases
    try {
      system.init(1);
      system.orderByDensity();
    } catch (Exception e) {
      logger.debug("SAFT VLLE: init failed, reverting to VLE: {}", e.getMessage());
      // Revert to the 2-phase result
      system.setNumberOfPhases(2);
      system.setBeta(betaVLE);
      for (int i = 0; i < nc; i++) {
        system.getPhase(0).getComponent(i).setK(Kvle[i]);
        system.getPhase(1).getComponent(i).setK(Kvle[i]);
      }
      system.calc_x_y();
      system.setPhaseType(0, PhaseType.GAS);
      system.setPhaseType(1, PhaseType.LIQUID);
      system.init(1);
      system.orderByDensity();
    }
  }

  /**
   * Solve Rachford-Rice equation for vapor fraction using bisection.
   *
   * @param z feed mole fractions
   * @param K K-values
   * @param betaGuess initial guess for vapor fraction
   * @return vapor fraction beta
   */
  private double solveRachfordRice(double[] z, double[] K, double betaGuess) {
    double lo = 0.0;
    double hi = 1.0;
    double beta = betaGuess;

    for (int iter = 0; iter < 200; iter++) {
      double f = 0;
      for (int i = 0; i < z.length; i++) {
        f += z[i] * (K[i] - 1.0) / (1.0 + beta * (K[i] - 1.0));
      }
      if (f > 0) {
        lo = beta;
      } else {
        hi = beta;
      }
      beta = 0.5 * (lo + hi);
      if (hi - lo < 1e-14) {
        break;
      }
    }
    return beta;
  }

  /**
   * Solve 3-phase Rachford-Rice equations using Newton's method.
   *
   * <p>
   * The 3-phase R-R equations are: F1 = sum_i z_i*(K_V_i-1) / [1 + betaV*(K_V_i-1) +
   * betaL1*(K_LL_i-1)] = 0 F2 = sum_i z_i*(K_LL_i-1) / [1 + betaV*(K_V_i-1) + betaL1*(K_LL_i-1)] =
   * 0
   * </p>
   *
   * @param z feed mole fractions
   * @param KV vapor K-values (relative to L2 reference phase)
   * @param KLL liquid-liquid K-values (L2/L1)
   * @param betaV0 initial guess for vapor fraction
   * @param betaL10 initial guess for liquid1 fraction
   * @return array {betaV, betaL1}
   */
  private double[] solve3PhaseRachfordRice(double[] z, double[] KV, double[] KLL, double betaV0,
      double betaL10) {
    double betaV = betaV0;
    double betaL1 = betaL10;
    int nc = z.length;

    for (int iter = 0; iter < 100; iter++) {
      double f1 = 0;
      double f2 = 0;
      double df1dV = 0;
      double df1dL1 = 0;
      double df2dV = 0;
      double df2dL1 = 0;

      for (int i = 0; i < nc; i++) {
        double denom = 1.0 + betaV * (KV[i] - 1.0) + betaL1 * (KLL[i] - 1.0);
        if (Math.abs(denom) < 1e-15) {
          denom = 1e-15;
        }
        double denom2 = denom * denom;

        f1 += z[i] * (KV[i] - 1.0) / denom;
        f2 += z[i] * (KLL[i] - 1.0) / denom;

        df1dV -= z[i] * (KV[i] - 1.0) * (KV[i] - 1.0) / denom2;
        df1dL1 -= z[i] * (KV[i] - 1.0) * (KLL[i] - 1.0) / denom2;
        df2dV -= z[i] * (KLL[i] - 1.0) * (KV[i] - 1.0) / denom2;
        df2dL1 -= z[i] * (KLL[i] - 1.0) * (KLL[i] - 1.0) / denom2;
      }

      // Newton step: solve [J] * [delta] = -[F]
      double det = df1dV * df2dL1 - df1dL1 * df2dV;
      if (Math.abs(det) < 1e-30) {
        break;
      }

      double dBetaV = -(df2dL1 * f1 - df1dL1 * f2) / det;
      double dBetaL1 = -(df1dV * f2 - df2dV * f1) / det;

      // Damping to keep in feasible region
      double scale = 1.0;
      while (scale > 0.01) {
        double newBV = betaV + scale * dBetaV;
        double newBL1 = betaL1 + scale * dBetaL1;
        if (newBV > BETA_MIN && newBL1 > BETA_MIN && (newBV + newBL1) < 1.0 - BETA_MIN) {
          betaV = newBV;
          betaL1 = newBL1;
          break;
        }
        scale *= 0.5;
      }

      if (Math.abs(f1) < 1e-12 && Math.abs(f2) < 1e-12) {
        break;
      }
    }

    return new double[] {betaV, betaL1};
  }

  /**
   * Normalize a composition array in place.
   *
   * @param comp composition array to normalize
   */
  private void normalize(double[] comp) {
    double sum = 0;
    for (double c : comp) {
      sum += c;
    }
    if (sum > 0) {
      for (int i = 0; i < comp.length; i++) {
        comp[i] /= sum;
      }
    }
  }

  /**
   * Compute fugacity coefficients for a given composition using a fresh SAFT system.
   *
   * @param T temperature in K
   * @param P pressure in bar
   * @param comp mole fractions
   * @param pt phase type (determines which volume root to find)
   * @return array of fugacity coefficients, or null if computation fails
   */
  private double[] computeFugacityCoefficients(double T, double P, double[] comp, PhaseType pt) {
    int nc = comp.length;
    SystemInterface sys = new SystemSAFTVRMie(T, P);

    // Add components with same names as the main system
    for (int i = 0; i < nc; i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      sys.addComponent(name, comp[i]);
    }
    sys.setMixingRule("classic");
    sys.init(0);
    sys.setPhaseType(0, pt);

    try {
      sys.init(1);
    } catch (Exception e) {
      logger.debug("SAFT fugacity computation failed for {}: {}", pt, e.getMessage());
      return null;
    }

    double[] phi = new double[nc];
    for (int i = 0; i < nc; i++) {
      phi[i] = sys.getPhase(0).getComponent(i).getFugacityCoefficient();
      if (phi[i] <= 0 || Double.isNaN(phi[i])) {
        return null;
      }
    }
    return phi;
  }

  /**
   * Compute Gibbs energy for a composition and phase type.
   *
   * @param T temperature in K
   * @param P pressure in bar
   * @param comp mole fractions
   * @param pt phase type
   * @return Gibbs energy, or NaN if computation fails
   */
  private double computeGibbsEnergy(double T, double P, double[] comp, PhaseType pt) {
    int nc = comp.length;
    SystemInterface sys = new SystemSAFTVRMie(T, P);

    for (int i = 0; i < nc; i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      sys.addComponent(name, comp[i]);
    }
    sys.setMixingRule("classic");
    sys.init(0);
    sys.setPhaseType(0, pt);

    try {
      sys.init(1);
    } catch (Exception e) {
      return Double.NaN;
    }
    return sys.getPhase(0).getGibbsEnergy();
  }
}
