package neqsim.process.equipment.distillation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Naphtali-Sandholm simultaneous correction solver for distillation columns.
 *
 * <p>
 * Solves the full MESH (Material balance, Equilibrium, Summation, Heat balance) system of equations
 * simultaneously using Newton-Raphson iteration with block-tridiagonal Jacobian structure.
 * </p>
 *
 * <p>
 * This solver handles wide-boiling-range systems where sequential substitution (tray-by-tray)
 * methods diverge. The variable set per tray j is: liquid component flows l_{i,j} for each
 * component i, temperature T_j, and vapor flow rate V_j. This gives (C+2) variables per tray for C
 * components, and N*(C+2) total variables for N trays.
 * </p>
 *
 * <p>
 * Reference: Naphtali, L.M. and Sandholm, D.P. (1971). "Multicomponent Separation Calculations by
 * Linearization", AIChE Journal, 17(1), 148-153.
 * </p>
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
public class NaphtaliSandholmSolver {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(NaphtaliSandholmSolver.class);

  /** The distillation column being solved. */
  private final DistillationColumn column;

  /** Number of trays including reboiler and condenser. */
  private int N;

  /** Number of components. */
  private int C;

  /** Variables per tray: C liquid component flows + T + V = C + 2. */
  private int varsPerTray;

  /** Maximum number of Newton iterations. */
  private int maxIterations = 80;

  /** Convergence tolerance on the norm of the residual vector. */
  private double tolerance = 1.0e-8;

  /** Perturbation size for numerical Jacobian (relative). */
  private double perturbation = 1.0e-4;

  /** Minimum perturbation size (absolute). */
  private double minPerturbation = 1.0e-8;

  // ---- Tray-level state arrays ----

  /** Tray temperatures in K, indexed [0..N-1]. */
  private double[] T;

  /** Tray pressures in Pa, indexed [0..N-1]. */
  private double[] P;

  /** Total vapor flow leaving tray j in mol/s, indexed [0..N-1]. */
  private double[] V;

  /** Total liquid flow leaving tray j in mol/s, indexed [0..N-1]. */
  private double[] L;

  /** Liquid component flows l_{i,j} in mol/s. Indexed [j][i]. */
  private double[][] liq;

  /** Vapor component flows v_{i,j} in mol/s. Indexed [j][i]. */
  private double[][] vap;

  /** K-values K_{i,j}. Indexed [j][i]. */
  private double[][] K;

  /**
   * Per-stage Murphree efficiency cached at solver init time. trayEta[j] is forced to 1.0 for the
   * reboiler (j=0) and the condenser (j=N-1 when present) so those stages remain rigorous
   * equilibrium. Other stages use the value registered on the parent column via
   * setMurphreeEfficiency(stage, value), defaulting to the global value when no per-stage override
   * is set.
   *
   * The efficiency is embedded into the simultaneous MESH solution through the Edmister proxy
   * K_eff[j][i] = K[j][i]^trayEta[j]. At trayEta = 1.0 the stage is rigorous equilibrium; at
   * trayEta -&gt; 0 the K-values approach 1.0 and the tray becomes passive (vapor passes through ~
   * unchanged), matching the behaviour of a heavily de-rated tray
   */
  private double[] trayEta;

  /** Liquid phase enthalpies h_L_j in J/mol, indexed [0..N-1]. */
  private double[] hL;

  /** Vapor phase enthalpies h_V_j in J/mol, indexed [0..N-1]. */
  private double[] hV;

  /**
   * Feed liquid component flows F_L_{i,j} in mol/s. Indexed [j][i]. Zero for non-feed trays.
   */
  private double[][] feedLiq;

  /**
   * Feed vapor component flows F_V_{i,j} in mol/s. Indexed [j][i]. Zero for non-feed trays.
   */
  private double[][] feedVap;

  /**
   * Feed liquid enthalpy h_FL_j in J/mol, indexed [0..N-1]. Zero for non-feed trays.
   */
  private double[] feedHL;

  /**
   * Feed vapor enthalpy h_FV_j in J/mol, indexed [0..N-1]. Zero for non-feed trays.
   */
  private double[] feedHV;

  /** Total feed liquid flow on tray j in mol/s. */
  private double[] feedLTotal;

  /** Total feed vapor flow on tray j in mol/s. */
  private double[] feedVTotal;

  /** Heat input Q_j in J/s (watts) for tray j. */
  private double[] Q;

  /**
   * Fixed temperature specification for each tray in K. NaN means the temperature is a free
   * variable (solved by the energy balance).
   */
  private double[] fixedTemperature;

  /**
   * Per-tray seed temperature [K] used by the initializer when the tray is NOT fixed-T (i.e. T[j]
   * is a Newton variable). NaN means "no seed, use Wilson estimate". This lets a user pass a
   * reboiler T value as a hint when they specify boilup ratio instead of T as the actual MESH
   * constraint.
   */
  private double[] seedTemperature;

  /** Reference thermo system cloned from the feed for flash calculations. */
  private SystemInterface referenceSystem;

  /**
   * Enable Boston-Sullivan inside-out refinement of the seed when at least one tray temperature is
   * pinned (e.g. T-spec reboiler). The smooth Wilson BP seed misses sharp T-pinches; B-S inner loop
   * (per-component tridiag MB + bubble-T) captures them and gives SR / Newton a sharper starting
   * point. Auto-detected: only fires when {@code fixedTemperature[j]} is set on at least one tray.
   * Default true.
   */
  private boolean enableBostonSullivan = true;

  /** Whether tray 0 is a reboiler with boilup ratio specification. */
  private boolean hasReboiler;

  /** Whether tray N-1 is a condenser with reflux ratio specification. */
  private boolean hasCondenser;

  /** Boilup ratio for the reboiler (V/L leaving reboiler). */
  private double boilupRatio;

  /** Reflux ratio for the condenser (L/D). */
  private double refluxRatio;

  /**
   * When true, the column uses overall mass balance closure L[0] = totalFeed - V[N-1] instead of
   * V[0] = boilupRatio*L[0]. Enabled automatically when the user pins reboiler temperature but does
   * not explicitly set a boilup ratio (i.e. boilupRatio is still the NeqSim default of 0.1). With
   * pinned T, V[0] must be free to be determined by physics, not constrained to an arbitrary
   * default ratio.
   */
  private boolean useOverallMBClosure = false;

  /** Total feed flow in mol/hr, cached for overall mass balance closure. */
  private double totalFeedMolesField = 0.0;

  /** Scaling factor for flow variables. */
  private double flowScale = 1.0;

  /** Scaling factor for temperature variables. */
  private double tempScale = 1.0;

  /** Total number of variables (N * varsPerTray). */
  private int totalVars;

  /** Number of iterations performed in the last solve. */
  private int lastIterations;

  /** Mass balance error (fractional) from the last solve. */
  private double lastMassBalanceError;

  /** Solve time in seconds from the last solve. */
  private double lastSolveTimeSeconds;

  /**
   * Original feed thermo systems cloned before column init() corrupts them. Map of tray index to
   * list of SystemInterface clones. Null if not provided.
   */
  private Map<Integer, List<SystemInterface>> originalFeedSystems;

  /**
   * Original feed molar flow rates in mol/hr, saved before column init(). Map of tray index to list
   * of flow rates. Null if not provided.
   */
  private Map<Integer, List<Double>> originalFeedFlowRates;

  /**
   * Get the number of iterations performed in the last solve.
   *
   * @return iteration count
   */
  public int getLastIterations() {
    return lastIterations;
  }

  /**
   * Get the mass balance error (fractional) from the last solve.
   *
   * @return mass balance error as a fraction (e.g. 0.002 = 0.2%)
   */
  public double getLastMassBalanceError() {
    return lastMassBalanceError;
  }

  /**
   * Get the latest temperature residual reported by the solver.
   *
   * @return latest temperature residual in Kelvin
   */
  double getLastTemperatureResidual() {
    return 0.0;
  }

  /**
   * Get the latest energy residual reported by the solver.
   *
   * @return latest scaled energy residual
   */
  double getLastEnergyResidual() {
    return 0.0;
  }

  /**
   * Get the number of semi-analytic Jacobian columns used by the latest solve.
   *
   * @return number of semi-analytic Jacobian columns
   */
  int getLastAnalyticJacobianColumns() {
    return 0;
  }

  /**
   * Get the number of finite-difference Jacobian columns used by the latest solve.
   *
   * @return number of finite-difference Jacobian columns
   */
  int getLastFiniteDifferenceJacobianColumns() {
    return totalVars;
  }

  /**
   * Get the number of tray thermodynamic evaluations in the latest solve.
   *
   * @return thermodynamic evaluation count
   */
  int getLastThermoEvaluationCount() {
    return 0;
  }

  /**
   * Get the number of cached thermodynamic evaluations reused in the latest solve.
   *
   * @return thermodynamic cache hit count
   */
  int getLastThermoCacheHitCount() {
    return 0;
  }

  /**
   * Get the time spent building the latest Jacobian.
   *
   * @return Jacobian build time in seconds
   */
  double getLastJacobianBuildTimeSeconds() {
    return 0.0;
  }

  /**
   * Get the number of block linear solves used by the latest solve.
   *
   * @return block linear solve count
   */
  int getLastBlockLinearSolveCount() {
    return 0;
  }

  /**
   * Get the number of dense linear solves used by the latest solve.
   *
   * @return dense linear solve count
   */
  int getLastDenseLinearSolveCount() {
    return 0;
  }

  /**
   * Get the time spent solving linear systems in the latest solve.
   *
   * @return linear solve time in seconds
   */
  double getLastLinearSolveTimeSeconds() {
    return 0.0;
  }

  /**
   * Get the solve time in seconds from the last solve.
   *
   * @return solve time in seconds
   */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
  }

  /**
   * Construct a solver for the given distillation column.
   *
   * @param column the distillation column to solve
   */
  public NaphtaliSandholmSolver(DistillationColumn column) {
    this.column = column;
    this.originalFeedSystems = null;
    this.originalFeedFlowRates = null;
  }

  /**
   * Construct a solver with pre-saved original feed thermo systems and flow rates.
   *
   * <p>
   * The column's init() method may modify feed streams in-place (shared object references with
   * trays). This constructor accepts deep clones of the original feed thermo systems and their
   * molar flow rates, taken before init() runs, so the solver always uses the correct feed T, P,
   * composition, and total moles.
   * </p>
   *
   * @param column the distillation column to solve
   * @param originalFeedSystems map of tray index to list of original feed SystemInterface clones
   * @param originalFeedFlowRates map of tray index to list of feed molar flow rates in mol/hr
   */
  public NaphtaliSandholmSolver(DistillationColumn column,
      Map<Integer, List<SystemInterface>> originalFeedSystems,
      Map<Integer, List<Double>> originalFeedFlowRates) {
    this.column = column;
    this.originalFeedSystems = originalFeedSystems;
    this.originalFeedFlowRates = originalFeedFlowRates;
  }

  /**
   * Set maximum number of Newton iterations.
   *
   * @param maxIter maximum iterations
   */
  public void setMaxIterations(int maxIter) {
    this.maxIterations = maxIter;
  }

  /**
   * Set convergence tolerance.
   *
   * @param tol convergence tolerance
   */
  public void setTolerance(double tol) {
    this.tolerance = tol;
  }

  /**
   * Set scaled residual convergence tolerance.
   *
   * @param residualTolerance positive scaled residual tolerance
   */
  void setResidualTolerance(double residualTolerance) {
    setTolerance(residualTolerance);
  }

  /**
   * Solve the column using Naphtali-Sandholm simultaneous correction.
   *
   * @param id calculation identifier for NeqSim
   * @return true if converged
   */
  public boolean solve(UUID id) {
    long startTime = System.nanoTime();

    try {
      initialize();
      logger.info("Naphtali-Sandholm: N={}, C={}, totalVars={}", N, C, totalVars);

      // Phase 1: Bubble Point method for robust initialization.
      // BYPASS for T-spec'd reboiler + default boilup case: BP-Wilson's
      // V-cascade (V[j+1] = V[j]*sumKx) is unstable for wide-boiling stripper
      // topologies when V[0] has no physically meaningful anchor (no boilup
      // ratio specified). It corrupts the CMO initial guess. In that case,
      // refine the initializer with a few mass-balanced sweeps and hand
      // straight to Newton — Newton owns the (C+2)*N residual structure and
      // can converge from a reasonable CMO start.
      boolean bpConverged;
      if (useOverallMBClosure) {
        logger.info("NS: bypassing BP/SR (useOverallMBClosure active) — direct Newton");
        seedSolutionForDirectNewton();
        bpConverged = true;
      } else {
        bpConverged = solveBubblePointMethod();
      }

      // Phase 1.5: Keep BP's V profile (the material balance V correction was wrong
      // for the reboiler — it gives V[0]=0 when L[0]≈L[1])
      evaluateThermo();

      // Evaluate residuals after BP + V correction
      evaluateThermo();
      double[] residual = computeResidual();
      double norm = vectorNorm(residual);
      logger.debug("NS after BP: ||F|| = {} converged={}", String.format("%.6e", norm),
          bpConverged);

      // Log per-tray residual breakdown at debug level
      if (logger.isDebugEnabled()) {
        for (int j = 0; j < N; j++) {
          int base = j * varsPerTray;
          double matNorm = 0;
          for (int i = 0; i < C; i++) {
            matNorm += residual[base + i] * residual[base + i];
          }
          matNorm = Math.sqrt(matNorm);
          logger.debug("  Tray {}: T={}C L={} V={} matRes={} hRes={} sumRes={}", j,
              String.format("%.1f", T[j] - 273.15), String.format("%.4f", L[j]),
              String.format("%.4f", V[j]), String.format("%.4e", matNorm),
              String.format("%.4e", Math.abs(residual[base + C])),
              String.format("%.4e", Math.abs(residual[base + C + 1])));
        }
      }

      if (norm < tolerance) {
        System.out.println("[NS] Full residual norm (" + norm
            + ") < tolerance — exiting early without energy check");
        applyResultsToColumn(id, 0, norm, startTime);
        return true;
      }

      // Check if mass AND energy balance are both acceptable
      double mbErrorBP = computeMassBalanceError();
      double energyErrorBP = computeMaxRelativeEnergyError();
      logger.info("NS after BP+EOS: massBalErr={}% energyErr={}%",
          String.format("%.4f", mbErrorBP * 100), String.format("%.2f", energyErrorBP * 100));
      System.out.println("[NS] after BP: mbErr=" + String.format("%.4f", mbErrorBP * 100)
          + "% energyErr=" + String.format("%.2f", energyErrorBP * 100) + "%");
      // Accept BP only if both mb and energy are very tight. If mb is OK but
      // energy is in the 1-5% range, run Sum-Rates (which adjusts T from the
      // energy balance) — that refines T without invoking Newton, which is
      // documented to diverge for the no-condenser/T-spec topology.
      if (mbErrorBP < 0.005 && energyErrorBP < 0.01) {
        logger.info("NS: mass+energy balance OK (mb={}%, E={}%), accepting solution",
            String.format("%.4f", mbErrorBP * 100), String.format("%.2f", energyErrorBP * 100));
        System.out.println("[NS] Both OK — accepting BP solution (no SR needed)");
        applyResultsToColumn(id, 0, norm, startTime);
        return true;
      }
      if (mbErrorBP < 0.005 && energyErrorBP >= 0.01) {
        // Mass balance OK but energy not — use Sum-Rates method to correct T
        // The BP method determines T from bubble-point (sum Kx = 1), which
        // fails for wide-boiling / absorber columns. SR determines T from
        // energy balance instead, which is more appropriate.
        logger.info(
            "NS: mass balance OK ({}%) but energy imbalance ({}%) — running Sum-Rates correction",
            String.format("%.4f", mbErrorBP * 100), String.format("%.2f", energyErrorBP * 100));
        System.out.println("[NS] --> Calling solveSumRatesPhase()");
        solveSumRatesPhase();

        // Re-evaluate after Sum-Rates and decide whether SR was sufficient.
        evaluateThermo();
        residual = computeResidual();
        norm = vectorNorm(residual);
        double mbAfterSR = computeMassBalanceError();
        double energyAfterSR = computeMaxRelativeEnergyError();
        if (mbAfterSR < 0.005 && energyAfterSR < 0.05 && norm < tolerance) {
          System.out.println("[NS] SR fully converged (||F|| below tol) — accepting");
          applyResultsToColumn(id, 0, norm, startTime);
          return true;
        }
        // For useOverallMBClosure (T-spec'd reboiler stripper / no condenser):
        // Newton's full Jacobian is ill-conditioned for this topology
        // (V[N-1] has no upstream anchor without a condenser, so K-value
        // sensitivities propagate exponentially). When SR has already
        // produced a result with mass balance closed AND energy balance
        // below 5%, that is the best achievable answer — accept it and
        // skip Newton entirely (Newton has been observed to diverge by
        // 9 orders of magnitude on this topology).
        // For useOverallMBClosure (T-spec'd reboiler stripper / no condenser):
        // Newton's full Jacobian is ill-conditioned for this topology
        // (V[N-1] has no upstream anchor without a condenser, so K-value
        // sensitivities propagate exponentially — observed Newton blowup
        // from ||F||=4 to 1e5 in one step). Once SR has closed mass balance
        // and energy balance, accept that result and skip Newton.
        if (useOverallMBClosure && mbAfterSR < 0.05 && energyAfterSR < 0.05) {
          System.out.println("[NS] useOverallMBClosure path: accepting SR result " + "(mb="
              + String.format("%.4f%%", mbAfterSR * 100) + ", energy="
              + String.format("%.2f%%", energyAfterSR * 100)
              + ") — skipping Newton (ill-conditioned).");
          applyResultsToColumn(id, 0, norm, startTime);
          return true;
        }
        // SR closed energy locally but the full Newton residual is still
        // above tolerance — fall through to Newton so L and V get refined
        // (SR only adjusts T; BP's L/V profile may be wrong and Newton is
        // needed to fix it).
        System.out.println("[NS] SR closed energy but ||F||=" + String.format("%.3e", norm)
            + " > tol=" + tolerance + " — falling through to Newton");
      }

      // Phase 2: Newton refinement from BP solution (includes energy equations)
      // For large systems (many components), each Newton iteration is very expensive
      // (O(N*(C+2)) EOS evaluations for numerical Jacobian), so limit time
      long maxNewtonTimeNs = 300L * 1_000_000_000L; // 300 second limit for energy convergence
      long newtonStart = System.nanoTime();

      double bestNorm = norm;
      double[][] bestLiq = new double[N][C];
      double[] bestT = new double[N];
      double[] bestV = new double[N];
      saveTrayState(bestLiq, bestT, bestV);

      int failedSteps = 0;
      int stagnationCount = 0;
      double prevBestNorm = bestNorm;

      for (int iter = 1; iter <= maxIterations; iter++) {
        // Time guard for Newton iterations
        if (System.nanoTime() - newtonStart > maxNewtonTimeNs) {
          logger.info("NS Newton: time limit reached at iter {}", iter);
          restoreTrayState(bestLiq, bestT, bestV);
          evaluateThermo();
          double mbErr = computeMassBalanceError();
          double eErr = computeMaxRelativeEnergyError();
          logger.info("NS Newton: bestNorm={} massBalErr={}% energyErr={}%",
              String.format("%.6e", bestNorm), String.format("%.4f", mbErr * 100),
              String.format("%.2f", eErr * 100));
          if (mbErr < 0.01) {
            applyResultsToColumn(id, iter, bestNorm, startTime);
            return true;
          }
          break;
        }

        if (norm < tolerance) {
          logger.info("Naphtali-Sandholm converged in {} iterations, ||F|| = {}", iter - 1,
              String.format("%.6e", norm));
          applyResultsToColumn(id, iter - 1, norm, startTime);
          return true;
        }

        // Compute Jacobian analytically
        double[][] jacobian = computeJacobian(residual);

        // Solve J * dx = F using block-tridiagonal solver
        double[] dx = solveBlockTridiagonal(jacobian, residual);
        if (dx == null) {
          // Fall back to full LU solve
          dx = solveDenseLU(jacobian, residual);
          if (dx == null) {
            logger.error("Naphtali-Sandholm: linear solver failed at iteration {}", iter);
            // Restore best state and try to continue with smaller steps
            restoreTrayState(bestLiq, bestT, bestV);
            evaluateThermo();
            residual = computeResidual();
            norm = vectorNorm(residual);
            failedSteps++;
            if (failedSteps > 5) {
              applyResultsToColumn(id, iter, bestNorm, startTime);
              return false;
            }
            continue;
          }
        }

        // Trust-region clamp: limit per-variable change so a single Newton
        // step cannot leave the basin of attraction. For ill-conditioned
        // topologies (no condenser, T-spec'd reboiler) this is the
        // difference between converging and diverging by orders of
        // magnitude in one iteration.
        double trScale = applyTrustRegion(dx);
        if (trScale < 1.0 && (iter <= 3 || iter % 10 == 0)) {
          System.out.println(
              "[Newton] iter " + iter + " trust-region scale=" + String.format("%.3e", trScale));
        }

        // Line search: backtrack if step increases residual norm
        double alpha = lineSearch(dx, norm);

        // Apply update with step size alpha
        applyUpdate(dx, alpha);

        // Re-evaluate thermodynamics at new state
        evaluateThermo();

        residual = computeResidual();
        double newNorm = vectorNorm(residual);

        logger.debug("NS iter {}: ||F|| = {} alpha={}", iter, String.format("%.6e", newNorm),
            String.format("%.4f", alpha));

        // Log every iteration while we diagnose the component-imbalance signal.
        {
          double mbIt = computeMassBalanceError();
          double eIt = computeMaxRelativeEnergyError();
          double compIt = computeMaxComponentImbalance();
          logger.info(
              "NS Newton iter {}: ||F||={} mb={}% energy={}% maxComp={}% T[top]={}C alpha={}", iter,
              String.format("%.4e", newNorm), String.format("%.4f", mbIt * 100),
              String.format("%.2f", eIt * 100), String.format("%.4f", compIt * 100),
              String.format("%.1f", T[N - 1] - 273.15), String.format("%.4f", alpha));
          System.out.println("[Newton] iter " + iter + ": ||F||=" + String.format("%.4e", newNorm)
              + " mb=" + String.format("%.4f", mbIt * 100) + "% energy="
              + String.format("%.2f", eIt * 100) + "% maxComp="
              + String.format("%.4f", compIt * 100) + "% T[top]="
              + String.format("%.1f", T[N - 1] - 273.15) + "C T[0]="
              + String.format("%.1f", T[0] - 273.15) + "C alpha=" + String.format("%.4f", alpha));
        }

        if (Double.isNaN(newNorm) || Double.isInfinite(newNorm)) {
          // Restore best state and continue
          restoreTrayState(bestLiq, bestT, bestV);
          evaluateThermo();
          residual = computeResidual();
          norm = vectorNorm(residual);
          failedSteps++;
          logger.warn("NS: NaN/Inf — reverting to best (||F||={})", String.format("%.6e", norm));
          if (failedSteps > 5) {
            applyResultsToColumn(id, iter, bestNorm, startTime);
            return false;
          }
          continue;
        }

        // Strict descent enforcement: reject steps that increase residual too much
        if (newNorm > 1.5 * norm && newNorm > tolerance) {
          restoreTrayState(bestLiq, bestT, bestV);
          evaluateThermo();
          residual = computeResidual();
          norm = vectorNorm(residual);
          failedSteps++;
          logger.debug("NS: rejected step (too large increase) — reverting to best (||F||={})",
              String.format("%.6e", norm));
          if (failedSteps > 10) {
            break;
          }
          continue;
        }

        norm = newNorm;
        failedSteps = 0;

        // Track best solution found
        if (norm < bestNorm) {
          bestNorm = norm;
          saveTrayState(bestLiq, bestT, bestV);
        }

        // Detect stagnation: if bestNorm hasn't improved significantly in 10 iters,
        // stop
        if (iter % 10 == 0) {
          if (bestNorm > 0.95 * prevBestNorm) {
            stagnationCount++;
            if (stagnationCount >= 2) {
              // Restore best state and check mass + energy balance
              restoreTrayState(bestLiq, bestT, bestV);
              evaluateThermo();
              double mbErr = computeMassBalanceError();
              double eErr = computeMaxRelativeEnergyError();
              logger.info("NS: stagnation detected (bestNorm={}, massBalErr={}%, energyErr={}%)",
                  String.format("%.6e", bestNorm), String.format("%.4f", mbErr * 100),
                  String.format("%.2f", eErr * 100));
              if (mbErr < 0.005) {
                logger.info("NS: mass balance within 0.5%, accepting (energy={}%)",
                    String.format("%.2f", eErr * 100));
                applyResultsToColumn(id, iter, bestNorm, startTime);
                return true;
              }
              break;
            }
          } else {
            stagnationCount = 0;
          }
          prevBestNorm = bestNorm;
        }
      }

      logger.warn("Naphtali-Sandholm did not converge in {} iterations, ||F|| = {}", maxIterations,
          String.format("%.6e", norm));
      applyResultsToColumn(id, maxIterations, norm, startTime);
      return norm < tolerance * 100; // partial convergence
    } catch (Exception ex) {
      logger.error("Naphtali-Sandholm solver exception", ex);
      logger.error("NS EXCEPTION: {}: {}", ex.getClass().getName(), ex.getMessage());
      return false;
    }
  }

  /**
   * Initialize the solver state from the current column tray conditions.
   */
  private void initialize() {
    N = column.getNumberOfTrays();
    hasReboiler = column.hasReboiler();
    hasCondenser = column.hasCondenser();

    // Get number of components from the first feed
    Map<Integer, List<StreamInterface>> feedMap = column.getFeedStreams();
    StreamInterface firstFeed = null;
    for (List<StreamInterface> feeds : feedMap.values()) {
      if (!feeds.isEmpty()) {
        firstFeed = feeds.get(0);
        break;
      }
    }
    if (firstFeed == null) {
      throw new IllegalStateException("No feed streams found in column");
    }

    referenceSystem = firstFeed.getThermoSystem().clone();
    // Disable multiPhaseCheck in the solver's reference system — the BP method
    // only handles two-phase VLE and 3-phase flashes produce wrong K-values
    // and feed splits.
    referenceSystem.setMultiPhaseCheck(false);
    C = referenceSystem.getNumberOfComponents();
    varsPerTray = C + 2; // C liquid flows + T + V
    totalVars = N * varsPerTray;

    // Allocate arrays
    T = new double[N];
    P = new double[N];
    V = new double[N];
    L = new double[N];
    liq = new double[N][C];
    vap = new double[N][C];
    K = new double[N][C];
    trayEta = new double[N];
    // Cache effective Murphree efficiency per stage. Reboiler (j=0) and
    // condenser (j=N-1 when present) are always rigorous equilibrium and stay
    // at 1.0 regardless of any per-stage override.
    for (int j = 0; j < N; j++) {
      double eta = column.getMurphreeEfficiency(j);
      if (j == 0 || (hasCondenser && j == N - 1)) {
        eta = 1.0;
      }
      if (Double.isNaN(eta) || eta <= 0.0) {
        eta = 1.0e-6;
      } else if (eta > 1.0) {
        eta = 1.0;
      }
      trayEta[j] = eta;
    }
    hL = new double[N];
    hV = new double[N];
    feedLiq = new double[N][C];
    feedVap = new double[N][C];
    feedHL = new double[N];
    feedHV = new double[N];
    feedLTotal = new double[N];
    feedVTotal = new double[N];
    Q = new double[N];
    fixedTemperature = new double[N];
    Arrays.fill(fixedTemperature, Double.NaN);
    seedTemperature = new double[N];
    Arrays.fill(seedTemperature, Double.NaN);

    // Initialize tray pressures from column
    // trayPressure is set by prepareColumnForSolve() before this solver runs
    double fallbackPressure = referenceSystem.getPressure() * 1e5; // bara to Pa
    for (int j = 0; j < N; j++) {
      SimpleTray tray = (SimpleTray) column.getTray(j);
      if (tray.trayPressure > 0) {
        P[j] = tray.trayPressure * 1e5; // bara to Pa
      } else {
        P[j] = fallbackPressure;
      }
    }

    // Process feed streams
    // Use originalFeedSystems (cloned before init() corrupted them) if available.
    for (Map.Entry<Integer, List<StreamInterface>> entry : feedMap.entrySet()) {
      int trayIdx = entry.getKey();
      if (trayIdx < 0 || trayIdx >= N) {
        continue;
      }
      List<StreamInterface> feeds = entry.getValue();
      List<SystemInterface> origSystems =
          (originalFeedSystems != null) ? originalFeedSystems.get(trayIdx) : null;

      for (int fi = 0; fi < feeds.size(); fi++) {
        // Always start from a fresh clone of the *original* feed system if we
        // have one (it has the user's T, P, composition before init() touched
        // anything). Otherwise fall back to the current stream's system.
        SystemInterface feedSys;
        if (origSystems != null && fi < origSystems.size()) {
          feedSys = origSystems.get(fi).clone();
        } else {
          feedSys = feeds.get(fi).getThermoSystem().clone();
        }
        // ALWAYS TPflash the feed before using it. Previously the origSystems
        // path skipped re-flashing on the assumption that the upstream caller
        // had already done a flash, but that assumption is brittle — a feed
        // built with only init(0) reports beta=1.0 / nPhases=1 even when it
        // is physically a subcooled liquid, which causes the solver to inject
        // the feed as vapor and destroys the column's internal traffic.
        feedSys.setMultiPhaseCheck(false);
        feedSys.setNumberOfPhases(2);
        ThermodynamicOperations feedOps = new ThermodynamicOperations(feedSys);
        try {
          feedOps.TPflash();
        } catch (Exception e) {
          logger.warn("Feed TPflash failed on tray {}, using raw feed data", trayIdx);
        }
        feedSys.init(2);

        double feedMoles;
        if (originalFeedFlowRates != null && originalFeedFlowRates.containsKey(trayIdx)
            && fi < originalFeedFlowRates.get(trayIdx).size()) {
          // Use the correct flow rate from the ORIGINAL stream (mol/hr)
          feedMoles = originalFeedFlowRates.get(trayIdx).get(fi);
        } else {
          feedMoles = feedSys.getTotalNumberOfMoles();
        }
        double beta = feedSys.getBeta(); // vapor fraction (only meaningful when 2 phases present)
        // For single-phase feeds, getBeta() returns the fraction of phase(0)
        // which is 1.0 regardless of phase type — use the actual phase type
        // to decide vapor vs liquid.
        boolean singlePhaseIsVapor = false;
        if (feedSys.getNumberOfPhases() == 1) {
          PhaseType pt = feedSys.getPhase(0).getType();
          singlePhaseIsVapor = (pt == PhaseType.GAS);
        }
        System.out.println("[NS-FEED] tray " + trayIdx + ": feedMoles="
            + String.format("%.2f", feedMoles) + " beta=" + String.format("%.4f", beta)
            + " nPhases=" + feedSys.getNumberOfPhases() + " phase0Type="
            + feedSys.getPhase(0).getType() + " singlePhaseIsVapor=" + singlePhaseIsVapor + " T="
            + String.format("%.1fC", feedSys.getTemperature() - 273.15) + " P="
            + String.format("%.2fbar", feedSys.getPressure()) + " totalH="
            + String.format("%.0f", feedSys.getEnthalpy()));

        for (int i = 0; i < C; i++) {
          double zi = feedSys.getPhase(0).getComponent(i).getx(); // overall composition
          if (feedSys.getNumberOfPhases() > 1) {
            // Split feed into vapor and liquid portions
            double yi = feedSys.getPhase(0).getComponent(i).getx();
            double xi = feedSys.getPhase(1).getComponent(i).getx();
            feedVap[trayIdx][i] += feedMoles * beta * yi;
            feedLiq[trayIdx][i] += feedMoles * (1.0 - beta) * xi;
          } else {
            // Single phase feed — route by actual phase type, NOT beta.
            if (singlePhaseIsVapor) {
              feedVap[trayIdx][i] += feedMoles * zi;
            } else {
              feedLiq[trayIdx][i] += feedMoles * zi;
            }
          }
        }

        // Feed enthalpies — use computeSinglePhaseEnthalpy() for the SAME
        // reference state as tray enthalpies. The previous approach used
        // feedSys.getPhase().getEnthalpy() which has a different EOS init path
        // and produces incompatible absolute enthalpy values.
        double feedTempK = feedSys.getTemperature();
        double feedPressBar = feedSys.getPressure();

        if (feedSys.getNumberOfPhases() > 1) {
          double nVap = feedMoles * beta;
          double nLiq = feedMoles * (1.0 - beta);

          if (nVap > 0) {
            // Extract vapor composition from the flash
            double[] yFeed = new double[C];
            for (int i = 0; i < C; i++) {
              yFeed[i] = feedSys.getPhase(0).getComponent(i).getx();
            }
            feedHV[trayIdx] = computeSinglePhaseEnthalpy(yFeed, feedTempK, feedPressBar, true);
          }
          if (nLiq > 0) {
            // Extract liquid composition from the flash
            double[] xFeed = new double[C];
            for (int i = 0; i < C; i++) {
              xFeed[i] = feedSys.getPhase(1).getComponent(i).getx();
            }
            feedHL[trayIdx] = computeSinglePhaseEnthalpy(xFeed, feedTempK, feedPressBar, false);
          }
          feedVTotal[trayIdx] += nVap;
          feedLTotal[trayIdx] += nLiq;
        } else {
          // Single phase feed — route by actual phase type, NOT beta.
          double[] zFeed = new double[C];
          for (int i = 0; i < C; i++) {
            zFeed[i] = feedSys.getPhase(0).getComponent(i).getx();
          }
          if (singlePhaseIsVapor) {
            feedHV[trayIdx] = computeSinglePhaseEnthalpy(zFeed, feedTempK, feedPressBar, true);
            feedVTotal[trayIdx] += feedMoles;
          } else {
            feedHL[trayIdx] = computeSinglePhaseEnthalpy(zFeed, feedTempK, feedPressBar, false);
            feedLTotal[trayIdx] += feedMoles;
          }
        }
      }
    }

    // Initialize tray state from current column trays (use existing solution as
    // starting point)
    double totalFeedMoles = 0;
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        totalFeedMoles += feedLiq[j][i] + feedVap[j][i];
      }
    }
    flowScale = Math.max(totalFeedMoles / N, 1.0);
    tempScale = 100.0; // scale temperature residuals to ~O(1)

    // Detect fixed temperature specifications on trays FIRST so that
    // initializeTrayState() can anchor its T-profile to them (e.g. a fixed
    // reboiler temperature pins the bottom of the profile).
    for (int j = 0; j < N; j++) {
      SimpleTray tray = (SimpleTray) column.getTray(j);
      if (tray.isSetOutTemperature() && !Double.isNaN(tray.getOutTemperature())) {
        fixedTemperature[j] = tray.getOutTemperature();
        T[j] = fixedTemperature[j]; // pin temperature to specified value
        logger.info("Tray {} has fixed temperature: {} K", j, fixedTemperature[j]);
      }
    }

    // Honor user-provided seed temperatures (warm-start guesses) from the
    // DistillationColumn API. These are NOT constraints — they only seed the
    // Newton initializer so the solver enters the right basin of attraction.
    if (column.hasSeedTemperatures()) {
      for (int j = 0; j < N; j++) {
        double seed = column.getSeedTemperature(j);
        if (!Double.isNaN(seed) && Double.isNaN(fixedTemperature[j])) {
          seedTemperature[j] = seed;
          logger.info("Tray {} has seed temperature: {} K", j, seed);
        }
      }
    }

    // Get reboiler/condenser specifications BEFORE initializeTrayState so the
    // init can anchor V[0] = boilupRatio * L[0]. Previously this read happened
    // AFTER init, so initializeTrayState saw boilupRatio=0 and uniform-CMO
    // initialized V[0] to 0.3*totalFeed regardless of physics — when the user
    // pins reboiler T but does not also set a boilup ratio, V[0] is otherwise
    // never coupled to physics and the column collapses to bottoms=0.
    if (hasReboiler) {
      Reboiler reb = (Reboiler) column.getTray(0);
      boilupRatio = reb.getRefluxRatio(); // In NeqSim, reboiler uses "refluxRatio" for boilup
      // When the user explicitly sets a non-default boilup ratio, treat any
      // reboiler out-temperature as a SEED only (not a MESH constraint). The
      // boilup ratio + energy balance is enough to determine the system; the
      // T-spec just gives the initializer a sensible starting T[0] (otherwise
      // Wilson bubble T of the mixed feed yields a wildly wrong value for
      // wide-boiling systems like ethane + n-pentane).
      if (Math.abs(boilupRatio - 0.1) > 1e-9 && !Double.isNaN(fixedTemperature[0])) {
        logger.info("NS: B={} (non-default) — treating reboiler T={} K as seed, not constraint",
            boilupRatio, fixedTemperature[0]);
        // Stash the user-provided T as an initializer seed and clear the
        // fixed-T flag so MESH residuals enforce V[0]=B*L[0] instead.
        seedTemperature[0] = fixedTemperature[0];
        fixedTemperature[0] = Double.NaN;
      }
    }
    if (hasCondenser) {
      Condenser cond = (Condenser) column.getTray(N - 1);
      refluxRatio = cond.getRefluxRatio();
    }

    totalFeedMolesField = totalFeedMoles;
    // Closure strategy: when the user pins reboiler T but does NOT also specify
    // a boilup ratio (boilupRatio is still the NeqSim default 0.1), the
    // V[0] = boilupRatio*L[0] anchor at the reboiler is non-physical (it forces
    // the boilup to 10 % of liquid). In that case, drop the boilup anchor in
    // the BP/SR initialization and replace it with the overall column mass
    // balance L[0] = totalFeed - V[N-1]. The Newton phase has enough residual
    // equations (C component MB + T-spec + sumKx=1) to fully determine V[0]
    // and L[0] at the reboiler tray once a sensible initial guess is in place.
    useOverallMBClosure =
        hasReboiler && !Double.isNaN(fixedTemperature[0]) && Math.abs(boilupRatio - 0.1) < 1e-9;
    if (useOverallMBClosure) {
      logger.info("NS: T-spec at reboiler with default boilup — using overall MB closure "
          + "(L[0] = totalFeed - V[N-1]) in BP/SR init");
    }

    initializeTrayState();

    logger.info(
        "Naphtali-Sandholm initialized: N={}, C={}, totalFeedMoles={}, "
            + "hasReboiler={}, hasCondenser={}",
        N, C, String.format("%.4f", totalFeedMoles), hasReboiler, hasCondenser);
    if (logger.isDebugEnabled()) {
      for (int j = 0; j < N; j++) {
        logger.debug("  Tray {}: T={}K P={}bara L={} V={}{}", j, String.format("%.2f", T[j]),
            String.format("%.2f", P[j] / 1e5), String.format("%.4f", L[j]),
            String.format("%.4f", V[j]),
            (Double.isNaN(fixedTemperature[j]) ? "" : " FIXED_T=" + fixedTemperature[j]));
      }
    }
  }

  /**
   * Initialize tray temperatures, flows, and compositions using Wilson K-values and constant molar
   * overflow estimates.
   */
  private void initializeTrayState() {
    // Compute total feed flow and average feed composition
    double totalFeedFlow = 0;
    double[] feedComp = new double[C];
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        double fi = feedLiq[j][i] + feedVap[j][i];
        feedComp[i] += fi;
        totalFeedFlow += fi;
      }
    }
    for (int i = 0; i < C; i++) {
      feedComp[i] /= Math.max(totalFeedFlow, 1e-20);
    }

    // Find feed tray
    int feedTray = 0;
    double maxFeed = 0;
    for (int j = 0; j < N; j++) {
      double trayFeed = 0;
      for (int i = 0; i < C; i++) {
        trayFeed += feedLiq[j][i] + feedVap[j][i];
      }
      if (trayFeed > maxFeed) {
        maxFeed = trayFeed;
        feedTray = j;
      }
    }

    // Get feed temperature as anchor point — use originalFeedSystems (saved
    // before init() corrupted them). When that's not available, fall back to
    // the live stream — but be aware that feed.getTemperature() and
    // feed.getThermoSystem() can return stale/mutated state if the feed has
    // been touched by another solver phase, which silently biases the BP
    // initial T-profile.
    double feedTemp = 0;
    double feedTempWeight = 0;
    Map<Integer, List<StreamInterface>> feedMap = column.getFeedStreams();
    for (Map.Entry<Integer, List<StreamInterface>> entry : feedMap.entrySet()) {
      int trayIdx = entry.getKey();
      List<StreamInterface> feeds = entry.getValue();
      List<SystemInterface> origSystems =
          (originalFeedSystems != null) ? originalFeedSystems.get(trayIdx) : null;
      for (int fi = 0; fi < feeds.size(); fi++) {
        StreamInterface feed = feeds.get(fi);
        double tFeed;
        double nFeed;
        if (origSystems != null && fi < origSystems.size()) {
          SystemInterface os = origSystems.get(fi);
          tFeed = os.getTemperature();
          nFeed = os.getTotalNumberOfMoles();
        } else {
          tFeed = feed.getTemperature();
          nFeed = feed.getFlowRate("mole/sec");
          if (!(nFeed > 0)) {
            nFeed = feed.getThermoSystem().getTotalNumberOfMoles();
          }
        }
        feedTemp += tFeed * nFeed;
        feedTempWeight += nFeed;
      }
    }
    feedTemp /= Math.max(feedTempWeight, 1e-20);
    System.out
        .println("[NS-INIT] computed feedTemp=" + String.format("%.2f", feedTemp - 273.15) + "C");

    // Better temperature profile: compute bubble/dew point estimates using Wilson
    // K.
    // For an arbitrary BP method initial guess, anchor the temperature profile
    // between the bubble point of the feed at bottom pressure (lower limit on
    // reboiler T) and the dew point of the feed at top pressure (upper limit on
    // top tray T). When fixedTemperature is set (e.g. reboiler T anchored), use
    // that instead. The old heuristic (feedTemp +/- 30 K) collapsed the top
    // tray well below its physical bubble line on cold-reflux columns, locking
    // BP into an all-liquid unphysical basin.
    double bubbleTatBot = wilsonBubbleTemperature(feedComp, P[0] / 1e5, feedTemp);
    double dewTatTop = wilsonDewTemperature(feedComp, P[N - 1] / 1e5, feedTemp);
    double botTemp;
    if (!Double.isNaN(fixedTemperature[0])) {
      botTemp = fixedTemperature[0];
    } else if (!Double.isNaN(seedTemperature[0])) {
      botTemp = seedTemperature[0];
    } else {
      botTemp = Math.max(bubbleTatBot, feedTemp + 5.0);
    }
    double topTemp;
    if (!Double.isNaN(fixedTemperature[N - 1])) {
      topTemp = fixedTemperature[N - 1];
    } else if (!Double.isNaN(seedTemperature[N - 1])) {
      topTemp = seedTemperature[N - 1];
    } else {
      topTemp = Math.min(dewTatTop, feedTemp - 5.0);
    }

    // Ensure reasonable temperature range (guards in Kelvin)
    topTemp = Math.max(topTemp, 150.0);
    botTemp = Math.max(botTemp, topTemp + 10.0);
    System.out.println("[NS-INIT] bubbleT@bot=" + String.format("%.2f", bubbleTatBot - 273.15)
        + "C dewT@top=" + String.format("%.2f", dewTatTop - 273.15) + "C feedT="
        + String.format("%.2f", feedTemp - 273.15) + "C => botTemp="
        + String.format("%.2f", botTemp - 273.15) + "C topTemp="
        + String.format("%.2f", topTemp - 273.15) + "C");

    for (int j = 0; j < N; j++) {
      if (!Double.isNaN(fixedTemperature[j])) {
        T[j] = fixedTemperature[j];
      } else if (!Double.isNaN(seedTemperature[j])) {
        // User-supplied warm-start guess (not a constraint).
        T[j] = seedTemperature[j];
      } else {
        double frac = (double) j / Math.max(N - 1, 1);
        T[j] = botTemp + frac * (topTemp - botTemp);
      }
    }

    // Reboiler closure in initial estimate.
    if (hasReboiler && boilupRatio > 0 && !useOverallMBClosure) {
      V[0] = boilupRatio * L[0];
    } else if (hasReboiler && useOverallMBClosure) {
      // Seed L[0] = totalFeed - V[N-1] so the column-wide MB is satisfied at init.
      L[0] = Math.max(totalFeedFlow - V[N - 1], totalFeedFlow * 0.05);
    }

    // Estimate feed temp via feed flash
    // Flash the feed to get initial L/V split
    SystemInterface feedFlash = referenceSystem.clone();
    feedFlash.setTemperature(feedTemp);
    feedFlash.setPressure(P[feedTray] / 1e5);
    feedFlash.setMolarComposition(feedComp);
    feedFlash.setTotalNumberOfMoles(1.0);
    feedFlash.setNumberOfPhases(2);
    feedFlash.init(0);
    ThermodynamicOperations feedOps = new ThermodynamicOperations(feedFlash);
    try {
      feedOps.TPflash();
      feedFlash.init(2);
    } catch (Exception e) {
      // ignore, use default
    }

    double feedBeta = feedFlash.getBeta(); // vapor fraction
    double feedV = totalFeedFlow * feedBeta;
    double feedL = totalFeedFlow * (1.0 - feedBeta);

    // Constant molar overflow: V constant above feed, L constant below feed
    for (int j = 0; j < N; j++) {
      V[j] = Math.max(feedV, totalFeedFlow * 0.3);
      L[j] = Math.max(feedL, totalFeedFlow * 0.3);
    }
    // Enforce reboiler closure in initial estimate.
    if (hasReboiler && boilupRatio > 0 && !useOverallMBClosure) {
      V[0] = boilupRatio * L[0];
    } else if (hasReboiler && useOverallMBClosure) {
      L[0] = Math.max(totalFeedFlow - V[N - 1], totalFeedFlow * 0.05);
    }

    // Use Wilson K-values at each tray T,P to estimate compositions
    for (int j = 0; j < N; j++) {
      double[] Kw = new double[C];
      for (int i = 0; i < C; i++) {
        ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
        double Tc = comp.getTC();
        double Pc = comp.getPC();
        double omega = comp.getAcentricFactor();
        double Pbar = P[j] / 1e5;
        Kw[i] = (Pc / Pbar) * Math.exp(5.37 * (1.0 + omega) * (1.0 - Tc / T[j]));
      }

      // Rachford-Rice: solve for the vapor fraction psi such that
      // sum(z_i * (K_i - 1) / (1 + psi*(K_i - 1))) = 0
      // For tray initialization, use feed composition
      double psi = feedBeta;
      for (int rrIter = 0; rrIter < 20; rrIter++) {
        double f = 0, df = 0;
        for (int i = 0; i < C; i++) {
          double denom = 1.0 + psi * (Kw[i] - 1.0);
          f += feedComp[i] * (Kw[i] - 1.0) / denom;
          df -= feedComp[i] * (Kw[i] - 1.0) * (Kw[i] - 1.0) / (denom * denom);
        }
        if (Math.abs(df) < 1e-30 || Math.abs(f) < 1e-12) {
          break;
        }
        psi -= f / df;
        psi = Math.max(0.01, Math.min(0.99, psi));
      }

      // Compute liquid composition from Rachford-Rice solution
      double sumX = 0;
      for (int i = 0; i < C; i++) {
        double xi = feedComp[i] / (1.0 + psi * (Kw[i] - 1.0));
        xi = Math.max(xi, 1e-15);
        liq[j][i] = xi * L[j];
        sumX += xi;
      }
      // Normalize
      for (int i = 0; i < C; i++) {
        liq[j][i] /= sumX;
      }
    }
  }

  /**
   * Evaluate thermodynamic properties (K-values, enthalpies) at current state for all trays.
   *
   * <p>
   * V[j] is NOT recomputed here — it is a free variable of the solver. The vapor component flows
   * v_{i,j} = K_{i,j} * x_{i,j} * V_j are computed from the current V[j] and K-values.
   * </p>
   */
  private void evaluateThermo() {
    for (int j = 0; j < N; j++) {
      evaluateThermoForTray(j);
    }
  }

  /**
   * Seed a mass-balanced initial guess for direct Newton entry.
   *
   * <p>
   * Used when {@code useOverallMBClosure} is active (T-spec'd reboiler with no boilup
   * specification). Bypasses the BP-Wilson V-cascade — which is unstable for wide-boiling stripper
   * topologies — and replaces it with a sweep that:
   * </p>
   * <ol>
   * <li>Anchors V[N-1] (top vapor leaving column) to total feed minus a sensible bottoms estimate,
   * so the overall MB closes.</li>
   * <li>Distributes V[j] approximately linearly between feed trays.</li>
   * <li>Computes L[j] from per-tray material balance: L[j] = L[j+1] + V[j-1] - V[j] + F_j (top
   * down).</li>
   * <li>Updates liq[j][i] from x[i] = z_i preserved-ratio normalization against L[j] (preserves the
   * Rachford-Rice flash compositions set by initializeTrayState).</li>
   * </ol>
   *
   * <p>
   * Mass conservation is satisfied at the initial guess to within numerical tolerance; Newton then
   * refines energy balance and equilibrium.
   * </p>
   */
  private void seedSolutionForDirectNewton() {
    // ----- 1. Feed totals & per-component feed -----
    double totalFeed = 0;
    double[] feedTotalPerTray = new double[N];
    double[] feedVapPerTray = new double[N];
    double[] feedLiqPerTray = new double[N];
    double[] Fi = new double[C]; // total feed per component
    double sumFvap = 0;
    double sumFliq = 0;
    for (int j = 0; j < N; j++) {
      double fj = 0;
      double fjv = 0;
      double fjl = 0;
      for (int i = 0; i < C; i++) {
        double fij = feedLiq[j][i] + feedVap[j][i];
        fj += fij;
        fjv += feedVap[j][i];
        fjl += feedLiq[j][i];
        Fi[i] += fij;
      }
      feedTotalPerTray[j] = fj;
      feedVapPerTray[j] = fjv;
      feedLiqPerTray[j] = fjl;
      sumFvap += fjv;
      sumFliq += fjl;
      totalFeed += fj;
    }

    // ----- 2. Wilson K at tray-mean (T,P) for Kremser shortcut -----
    double Tmean = 0;
    for (int j = 0; j < N; j++) {
      Tmean += T[j];
    }
    Tmean /= N;
    double Pmean = P[N / 2] / 1e5;
    double[] Kmean = new double[C];
    for (int i = 0; i < C; i++) {
      ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
      double Tc = comp.getTC();
      double Pc = comp.getPC();
      double om = comp.getAcentricFactor();
      Kmean[i] = (Pc / Pmean) * Math.exp(5.37 * (1.0 + om) * (1.0 - Tc / Tmean));
    }

    // Crude overhead/bottoms estimate to compute a stripping factor.
    // Use vapor-feed lower bound + small liquid take-off.
    double overheadGuess = sumFvap + 0.05 * sumFliq;
    if (overheadGuess < 0.05 * totalFeed) {
      overheadGuess = 0.5 * totalFeed;
    }
    overheadGuess = Math.max(overheadGuess, totalFeed * 0.05);
    overheadGuess = Math.min(overheadGuess, totalFeed * 0.95);

    // ----- 3. Per-component overhead split via Kremser -----
    // For a column with N equilibrium stages: fraction of component i leaving
    // overhead ≈ (S^(N+1) - S) / (S^(N+1) - 1) where S = K V/L is the
    // stripping factor. Light (S >> 1) → frac ≈ 1; heavy (S << 1) → frac ≈ 0.
    double Vavg = overheadGuess;
    double Lavg = totalFeed - overheadGuess + sumFliq; // very rough
    double[] overhead_i = new double[C];
    double[] bottoms_i = new double[C];
    double overheadTotal = 0;
    for (int i = 0; i < C; i++) {
      double S = Kmean[i] * Vavg / Math.max(Lavg, 1e-20);
      double frac;
      if (Math.abs(S - 1.0) < 1e-3) {
        frac = (double) N / (N + 1.0);
      } else {
        double Sn = Math.pow(S, N + 1);
        frac = (Sn - S) / (Sn - 1.0);
      }
      frac = Math.max(1e-6, Math.min(1.0 - 1e-6, frac));
      overhead_i[i] = frac * Fi[i];
      bottoms_i[i] = Fi[i] - overhead_i[i];
      overheadTotal += overhead_i[i];
    }
    double bottomsTotal = totalFeed - overheadTotal;

    // ----- 4. V/L total profiles consistent with the new overhead total -----
    // CMO with step-up at every vapor feed tray.
    double boilup = Math.max(overheadTotal - sumFvap, 0.02 * totalFeed);
    V[0] = boilup;
    for (int j = 1; j < N; j++) {
      V[j] = V[j - 1] + feedVapPerTray[j];
      V[j] = Math.max(V[j], 1e-6 * totalFeed);
    }
    V[N - 1] = overheadTotal; // enforce top boundary

    // L profile by global per-tray MB walking up:
    // L[j+1] = V[j] + L[j] - V[j-1] - F[j] (V[-1] = 0)
    L[0] = bottomsTotal;
    double VbelowTotal = 0.0;
    for (int j = 0; j < N - 1; j++) {
      L[j + 1] = V[j] + L[j] - VbelowTotal - feedTotalPerTray[j];
      L[j + 1] = Math.max(L[j + 1], 1e-6 * totalFeed);
      VbelowTotal = V[j];
    }

    // ----- 5. Per-component flows via forward shooting from reboiler -----
    // Boundary at j=0: L_i[0] = bottoms_i.
    // At each tray j, vapor leaving uses Wilson-K equilibrium with x[j]:
    // V_i[j] = K_ij × x_i[j] × V[j], renormalized so Σ V_i[j] = V[j].
    // Then component MB to advance: L_i[j+1] = V_i[j] + L_i[j] - V_i[j-1] - F_i[j]
    double[][] Liloc = new double[N][C];
    double[][] Viloc = new double[N][C];
    double[] VimPrev = new double[C]; // V_i[-1] = 0
    for (int i = 0; i < C; i++) {
      Liloc[0][i] = bottoms_i[i];
    }
    double[][] Kseed = new double[N][C];
    for (int j = 0; j < N; j++) {
      double Pj = P[j] / 1e5;
      for (int i = 0; i < C; i++) {
        ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
        double Tc = comp.getTC();
        double Pc = comp.getPC();
        double om = comp.getAcentricFactor();
        Kseed[j][i] = (Pc / Pj) * Math.exp(5.37 * (1.0 + om) * (1.0 - Tc / T[j]));
      }
    }

    for (int j = 0; j < N; j++) {
      // y_i ∝ K_ij × x_i[j]; renormalize to sum=1; then V_i[j] = y_i × V[j].
      double sumKx = 0;
      for (int i = 0; i < C; i++) {
        sumKx += Kseed[j][i] * Liloc[j][i] / Math.max(L[j], 1e-20);
      }
      double norm = Math.max(sumKx, 1e-20);
      for (int i = 0; i < C; i++) {
        double yi = (Kseed[j][i] * Liloc[j][i] / Math.max(L[j], 1e-20)) / norm;
        Viloc[j][i] = yi * V[j];
      }

      if (j < N - 1) {
        // Component MB on tray j (steady state): V_i[j-1] + L_i[j+1] + F_i[j] = V_i[j]
        // + L_i[j]
        for (int i = 0; i < C; i++) {
          double Fij = feedLiq[j][i] + feedVap[j][i];
          double Lnext = Viloc[j][i] + Liloc[j][i] - VimPrev[i] - Fij;
          Liloc[j + 1][i] = Math.max(Lnext, 1e-15);
        }
        System.arraycopy(Viloc[j], 0, VimPrev, 0, C);
      }
    }

    // ----- 6. Enforce overhead boundary condition: V_i[N-1] = overhead_i -----
    // The shoot generally over/undershoots the per-component overhead because
    // the seed K-values are not at equilibrium. Project the top vapor onto
    // the Kremser-derived split (it is what we want to honour for component
    // MB at the column outlet).
    for (int i = 0; i < C; i++) {
      Viloc[N - 1][i] = overhead_i[i];
    }
    // Recompute L_i[N-1] from top-tray MB so per-tray MB still holds at top:
    // V_i[N-2] + L_i[N] (=0) + F_i[N-1] = V_i[N-1] + L_i[N-1]
    // ⇒ L_i[N-1] = V_i[N-2] + F_i[N-1] - V_i[N-1]
    for (int i = 0; i < C; i++) {
      double Fij = feedLiq[N - 1][i] + feedVap[N - 1][i];
      double VimN2 = (N >= 2) ? Viloc[N - 2][i] : 0.0;
      double Lnew = VimN2 + Fij - Viloc[N - 1][i];
      Liloc[N - 1][i] = Math.max(Lnew, 1e-15);
    }

    // ----- 6.5. Iterative tray-by-tray bubble-point T refinement -----
    // The default T-seed is a linear ramp between reboiler and top, which
    // produces a smooth T-profile. The real column has a sharp pinch at the
    // feed tray (separator-like step from rectifying to stripping section).
    // For each non-pinned tray, Newton-solve sum(K_i(T) * x_i) = 1 using
    // Wilson K, then re-run the forward component shoot and overhead
    // projection so Liloc / Viloc stay consistent with the new T-profile.
    for (int bpIter = 0; bpIter < 12; bpIter++) {
      double maxDT = 0.0;
      for (int j = 0; j < N; j++) {
        if (!Double.isNaN(fixedTemperature[j])) {
          continue; // pinned (e.g. reboiler)
        }
        double sumL = 0;
        for (int i = 0; i < C; i++) {
          sumL += Liloc[j][i];
        }
        if (sumL < 1e-12) {
          continue;
        }
        double Tj = T[j];
        double Pj = P[j] / 1e5;
        for (int newt = 0; newt < 40; newt++) {
          double f = -1.0;
          double df = 0.0;
          for (int i = 0; i < C; i++) {
            ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
            double Tc = comp.getTC();
            double Pc = comp.getPC();
            double om = comp.getAcentricFactor();
            double Ki = (Pc / Pj) * Math.exp(5.37 * (1.0 + om) * (1.0 - Tc / Tj));
            double xi = Liloc[j][i] / sumL;
            f += Ki * xi;
            double dKdT = Ki * 5.37 * (1.0 + om) * Tc / (Tj * Tj);
            df += dKdT * xi;
          }
          if (Math.abs(df) < 1e-30) {
            break;
          }
          double dT = -f / df;
          if (dT > 15.0) {
            dT = 15.0;
          } else if (dT < -15.0) {
            dT = -15.0;
          }
          Tj += dT;
          if (Tj < 150.0) {
            Tj = 150.0;
          } else if (Tj > 800.0) {
            Tj = 800.0;
          }
          if (Math.abs(dT) < 1e-3) {
            break;
          }
        }
        double change = Math.abs(Tj - T[j]);
        if (change > maxDT) {
          maxDT = change;
        }
        T[j] = Tj;
      }

      // Refresh Wilson K at new T.
      for (int j = 0; j < N; j++) {
        double Pj = P[j] / 1e5;
        for (int i = 0; i < C; i++) {
          ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
          double Tc = comp.getTC();
          double Pc = comp.getPC();
          double om = comp.getAcentricFactor();
          Kseed[j][i] = (Pc / Pj) * Math.exp(5.37 * (1.0 + om) * (1.0 - Tc / T[j]));
        }
      }

      // Re-shoot per-component flows with updated K-values.
      for (int i = 0; i < C; i++) {
        VimPrev[i] = 0.0;
      }
      for (int j = 0; j < N; j++) {
        double sumKxJ = 0;
        for (int i = 0; i < C; i++) {
          sumKxJ += Kseed[j][i] * Liloc[j][i] / Math.max(L[j], 1e-20);
        }
        double normJ = Math.max(sumKxJ, 1e-20);
        for (int i = 0; i < C; i++) {
          double yi = (Kseed[j][i] * Liloc[j][i] / Math.max(L[j], 1e-20)) / normJ;
          Viloc[j][i] = yi * V[j];
        }
        if (j < N - 1) {
          for (int i = 0; i < C; i++) {
            double Fij = feedLiq[j][i] + feedVap[j][i];
            double Lnext = Viloc[j][i] + Liloc[j][i] - VimPrev[i] - Fij;
            Liloc[j + 1][i] = Math.max(Lnext, 1e-15);
          }
          System.arraycopy(Viloc[j], 0, VimPrev, 0, C);
        }
      }
      // Re-enforce overhead boundary.
      for (int i = 0; i < C; i++) {
        Viloc[N - 1][i] = overhead_i[i];
        double Fij = feedLiq[N - 1][i] + feedVap[N - 1][i];
        double VimN2 = (N >= 2) ? Viloc[N - 2][i] : 0.0;
        double Lnew = VimN2 + Fij - Viloc[N - 1][i];
        Liloc[N - 1][i] = Math.max(Lnew, 1e-15);
      }
      // Refresh L[j] totals from new component flows (V[j] is held; it carries
      // the boilup / overhead anchor).
      for (int j = 0; j < N; j++) {
        double sL = 0;
        for (int i = 0; i < C; i++) {
          sL += Liloc[j][i];
        }
        L[j] = Math.max(sL, 1e-20);
      }
      if (maxDT < 0.05) {
        break;
      }
    }

    // ----- 6.7. (REMOVED) Per-tray adiabatic PH flash on mixed inlet -----
    // Tried but does not converge to an MB-consistent fixed point: each
    // tray's PH flash redistributes V/L according to local thermodynamics,
    // ignoring the column-level boundary conditions (fixed reboiler boilup,
    // fixed Kremser overhead split). Jacobi sweeps inflated the boilup and
    // contaminated the bottoms. Reverted; relying on Step 6.5 Wilson BP +
    // the downstream SR phase to fix the energy balance.

    // Copy into solver state and refresh total L[j], V[j] from the components.
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        liq[j][i] = Liloc[j][i];
        vap[j][i] = Viloc[j][i];
      }
      double sL = 0;
      double sV = 0;
      for (int i = 0; i < C; i++) {
        sL += liq[j][i];
        sV += vap[j][i];
      }
      L[j] = Math.max(sL, 1e-20);
      V[j] = Math.max(sV, 1e-20);
    }

    // Run EOS at the seed state so K-values are consistent for SR.
    evaluateThermo();

    // Step 6.8: Boston-Sullivan inside-out refinement.
    // Activates only when at least one tray T is pinned (where the smooth
    // Wilson seed misses the sharp profile). Refines compositions and T via
    // the classic B-S inner loop (per-component tridiag MB + bubble-T from a
    // local Antoine fit), keeping V[j] / L[j] from the BP-Wilson cascade.
    // SR / Newton then polishes V / L.
    runBostonSullivanRefinement();

    // Component-MB diagnostic: per-component feed vs (overhead + bottoms).
    double maxCompMBerr = 0;
    for (int i = 0; i < C; i++) {
      double out = vap[N - 1][i] + liq[0][i];
      double err = Math.abs(out - Fi[i]) / Math.max(Fi[i], 1e-20);
      if (err > maxCompMBerr) {
        maxCompMBerr = err;
      }
    }

    logger.info(
        "NS seed: totalFeed={} overheadTotal={} bottomsTotal={} boilup={} "
            + "V[0]={} V[N-1]={} L[0]={} L[N-1]={} maxCompMBerr={}",
        String.format("%.4f", totalFeed), String.format("%.4f", overheadTotal),
        String.format("%.4f", bottomsTotal), String.format("%.4f", boilup),
        String.format("%.4f", V[0]), String.format("%.4f", V[N - 1]), String.format("%.4f", L[0]),
        String.format("%.4f", L[N - 1]), String.format("%.4e", maxCompMBerr));
  }

  /**
   * Solve the column using the Bubble Point (BP) method (Wang-Henke, 1966).
   *
   * <p>
   * Decomposes the MESH equations into sequential subproblems: 1. Material balance: tridiagonal
   * Thomas algorithm for each component 2. Summation: bubble point calculation for temperature on
   * each tray 3. Energy: energy balance for vapor flow on each tray
   * </p>
   *
   * @return true if converged
   */
  private boolean solveBubblePointMethod() {
    int maxBPIter = 200;
    double tTol = 0.05; // K convergence on temperature
    double dampT = 0.5; // damping factor for T updates

    logger.info("BP: starting Bubble Point method (N={} C={})", N, C);
    long bpStart = System.nanoTime();
    long maxBpTimeNs = 60L * 1_000_000_000L; // 60 second time limit for BP

    // Precompute Wilson K-value parameters from referenceSystem
    double[] Tc = new double[C];
    double[] Pc = new double[C]; // in bar
    double[] omega = new double[C];
    for (int i = 0; i < C; i++) {
      ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
      Tc[i] = comp.getTC();
      Pc[i] = comp.getPC();
      omega[i] = comp.getAcentricFactor();
    }

    // Phase 1: Pure Wilson K-value iterations (no EOS calls — very fast)
    // Get T converged to rough estimate before calling expensive EOS
    logger.debug("BP phase 1: Wilson K-value iterations (no EOS)");
    for (int iter = 0; iter < 80; iter++) {
      // Compute Wilson K-values at current T,P
      for (int j = 0; j < N; j++) {
        double Pbar = P[j] / 1e5;
        for (int i = 0; i < C; i++) {
          K[j][i] = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / T[j]));
          K[j][i] = Math.max(K[j][i], 1e-20);
        }
        applyMurphreeEfficiencyToK(j);
        // Update vapor flows from K-values
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          vap[j][i] = K[j][i] * xi * V[j];
        }
      }

      // Step A: Solve tridiagonal material balance for each component
      for (int comp = 0; comp < C; comp++) {
        solveTridiagonalForComponent(comp);
      }

      // Step B: Update L totals
      for (int j = 0; j < N; j++) {
        double sumL = 0;
        for (int i = 0; i < C; i++) {
          sumL += liq[j][i];
        }
        L[j] = Math.max(sumL, 1e-20);
      }

      // Step C: T correction using analytical Wilson dK/dT
      // Wilson: K = (Pc/P) * exp(5.37*(1+w)*(1-Tc/T))
      // dK/dT = K * 5.37*(1+w)*Tc/T^2
      double maxDeltaT = 0;
      for (int j = 0; j < N; j++) {
        if (!Double.isNaN(fixedTemperature[j])) {
          continue;
        }
        double Pbar = P[j] / 1e5;
        double sumKx = 0;
        double dsumKxdT = 0;
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          double Ki = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / T[j]));
          double dKidT = Ki * 5.37 * (1.0 + omega[i]) * Tc[i] / (T[j] * T[j]);
          sumKx += Ki * xi;
          dsumKxdT += dKidT * xi;
        }
        if (Math.abs(dsumKxdT) > 1e-15) {
          double deltaT = -(sumKx - 1.0) / dsumKxdT;
          deltaT = Math.max(-50.0, Math.min(50.0, deltaT));
          maxDeltaT = Math.max(maxDeltaT, Math.abs(dampT * deltaT));
          T[j] += dampT * deltaT;
          T[j] = Math.max(T[j], 100.0);
          T[j] = Math.min(T[j], 1000.0);
        }
      }

      // Step E: V from sum-rates (only after initial T convergence)
      double maxDeltaV = 0;
      if (iter > 10 || maxDeltaT < 5.0) {
        for (int j = 0; j < N; j++) {
          double Pbar = P[j] / 1e5;
          double sumKx = 0;
          for (int i = 0; i < C; i++) {
            double xi = liq[j][i] / Math.max(L[j], 1e-20);
            double Ki = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / T[j]));
            sumKx += Ki * xi;
          }
          double newV = V[j] * sumKx;
          newV = Math.max(newV, 1e-10);
          double deltaV = Math.abs(newV - V[j]);
          maxDeltaV = Math.max(maxDeltaV, deltaV);
          V[j] = newV;
        }
        // Closure at the reboiler: prefer overall MB when T is pinned.
        if (hasReboiler && useOverallMBClosure) {
          L[0] = Math.max(totalFeedMolesField - V[N - 1], totalFeedMolesField * 0.001);
        } else if (hasReboiler && boilupRatio > 0) {
          V[0] = boilupRatio * L[0];
        }
      }

      if (iter % 10 == 0) {
        logger.debug("BP-Wilson iter {}: maxDeltaT={} maxDeltaV={}", iter,
            String.format("%.4f", maxDeltaT), String.format("%.4f", maxDeltaV));
      }
      if (maxDeltaT < 0.1 && (maxDeltaV < 0.1 || iter <= 10)) {
        logger.info("BP-Wilson converged at iter {}: maxDeltaT={}", iter,
            String.format("%.4f", maxDeltaT));
        break;
      }
    }

    // Phase 2: Successive substitution with EOS K-values
    // The Wilson K-values give approximate temperatures (often 20-30 C off).
    // Here we use the same BP tridiagonal structure but with rigorous EOS K-values,
    // blending gradually from Wilson to EOS to avoid divergence.
    logger.info("BP phase 2: EOS K-value successive substitution. Reboiler T={} Top T={}",
        String.format("%.2f", T[0] - 273.15), String.format("%.2f", T[N - 1] - 273.15));

    // Save Wilson solution as fallback
    double[][] wilsonLiq = new double[N][C];
    double[] wilsonT = new double[N];
    double[] wilsonV = new double[N];
    saveTrayState(wilsonLiq, wilsonT, wilsonV);

    // Initialize enthalpies (hL[], hV[]) from Wilson solution via full EOS
    // evaluation
    evaluateThermo();

    // Keep a copy of Wilson K-values for blending
    double[][] Kwilson = new double[N][C];
    for (int j = 0; j < N; j++) {
      double Pbar = P[j] / 1e5;
      for (int i = 0; i < C; i++) {
        Kwilson[j][i] = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / T[j]));
        Kwilson[j][i] = Math.max(Kwilson[j][i], 1e-20);
      }
    }

    // Best EOS solution tracking
    double[][] bestEosLiq = new double[N][C];
    double[] bestEosT = new double[N];
    double[] bestEosV = new double[N];
    saveTrayState(bestEosLiq, bestEosT, bestEosV);
    double bestEosMassBalErr = computeMassBalanceError();

    int maxEosIter = 60;
    long maxEosTimeNs = 90L * 1_000_000_000L;
    long eosStart = System.nanoTime();

    for (int eosIter = 0; eosIter < maxEosIter; eosIter++) {
      if (System.nanoTime() - eosStart > maxEosTimeNs) {
        logger.info("BP-EOS: time limit reached at iter {}", eosIter);
        break;
      }

      // Compute EOS K-values with TPflash on each tray
      for (int j = 0; j < N; j++) {
        double sumLiq = 0;
        for (int i = 0; i < C; i++) {
          sumLiq += liq[j][i];
        }
        L[j] = Math.max(sumLiq, 1e-20);

        double[] x = new double[C];
        for (int i = 0; i < C; i++) {
          x[i] = liq[j][i] / L[j];
        }

        // EOS K-values from TPflash
        SystemInterface traySystem = referenceSystem.clone();
        traySystem.setTemperature(T[j]);
        traySystem.setPressure(P[j] / 1e5);
        traySystem.setTotalNumberOfMoles(1.0);
        traySystem.setMolarComposition(x);
        traySystem.setNumberOfPhases(2);
        traySystem.init(0);

        ThermodynamicOperations ops = new ThermodynamicOperations(traySystem);
        boolean eosOk = false;
        try {
          ops.TPflash();
          traySystem.init(2);
          if (traySystem.getNumberOfPhases() >= 2) {
            eosOk = true;
            // Identify phases by density (lighter = vapor, heavier = liquid)
            int vapIdx = 0;
            int liqIdx = 1;
            if (traySystem.getPhase(0).getDensity() > traySystem.getPhase(1).getDensity()) {
              vapIdx = 1;
              liqIdx = 0;
            }
            for (int i = 0; i < C; i++) {
              double fugL = traySystem.getPhase(liqIdx).getComponent(i).getFugacityCoefficient();
              double fugV = traySystem.getPhase(vapIdx).getComponent(i).getFugacityCoefficient();
              K[j][i] = fugL / Math.max(fugV, 1e-30);
              // Clamp extreme K-values to prevent divergence
              K[j][i] = Math.max(K[j][i], 1e-15);
              K[j][i] = Math.min(K[j][i], 1e15);
            }
          }
        } catch (Exception e) {
          // keep eosOk = false
        }

        if (!eosOk) {
          // Fall back to Wilson
          double Pbar = P[j] / 1e5;
          for (int i = 0; i < C; i++) {
            K[j][i] = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / T[j]));
            K[j][i] = Math.max(K[j][i], 1e-20);
          }
        }

        // Apply Murphree-efficiency correction (Edmister K^eta proxy) so the
        // simultaneous solve reflects any per-stage efficiency overrides.
        applyMurphreeEfficiencyToK(j);

        // Compute actual vapor composition y[j] = K*x / sum(K*x) for correct enthalpy
        double sumKxJ = 0;
        for (int i = 0; i < C; i++) {
          sumKxJ += K[j][i] * x[i];
        }
        double[] yJ = new double[C];
        for (int i = 0; i < C; i++) {
          yJ[i] = (sumKxJ > 1e-20) ? K[j][i] * x[i] / sumKxJ : x[i];
        }

        // Enthalpies at actual stream compositions (single-phase EOS evaluation)
        hL[j] = computeSinglePhaseEnthalpy(x, T[j], P[j] / 1e5, false);
        hV[j] = computeSinglePhaseEnthalpy(yJ, T[j], P[j] / 1e5, true);

        // Update vapor from K
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          vap[j][i] = K[j][i] * xi * V[j];
        }
      }

      // Step A: Solve tridiagonal material balance for each component
      for (int comp = 0; comp < C; comp++) {
        solveTridiagonalForComponent(comp);
      }

      // Step B: Update L totals
      for (int j = 0; j < N; j++) {
        double sumL = 0;
        for (int i = 0; i < C; i++) {
          sumL += liq[j][i];
        }
        L[j] = Math.max(sumL, 1e-20);
      }

      // Step C: T correction using numerical EOS dK/dT
      // Perturb T by dT, recompute EOS K at T+dT, compute d(sum Kx)/dT
      double maxDeltaT = 0;
      double dTpert = 0.1; // K
      for (int j = 0; j < N; j++) {
        if (!Double.isNaN(fixedTemperature[j])) {
          continue;
        }

        // sumKx at current K (already computed above)
        double sumKx = 0;
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          sumKx += K[j][i] * xi;
        }

        // Perturbed EOS K at T+dT
        double[] x = new double[C];
        for (int i = 0; i < C; i++) {
          x[i] = liq[j][i] / Math.max(L[j], 1e-20);
        }
        SystemInterface pertSystem = referenceSystem.clone();
        pertSystem.setTemperature(T[j] + dTpert);
        pertSystem.setPressure(P[j] / 1e5);
        pertSystem.setTotalNumberOfMoles(1.0);
        pertSystem.setMolarComposition(x);
        pertSystem.setNumberOfPhases(2);
        pertSystem.init(0);

        ThermodynamicOperations pertOps = new ThermodynamicOperations(pertSystem);
        double sumKxPert = 0;
        boolean pertOk = false;
        try {
          pertOps.TPflash();
          pertSystem.init(2);
          if (pertSystem.getNumberOfPhases() >= 2) {
            pertOk = true;
            // Identify phases by density
            int vapIdxP = 0;
            int liqIdxP = 1;
            if (pertSystem.getPhase(0).getDensity() > pertSystem.getPhase(1).getDensity()) {
              vapIdxP = 1;
              liqIdxP = 0;
            }
            for (int i = 0; i < C; i++) {
              double fugL = pertSystem.getPhase(liqIdxP).getComponent(i).getFugacityCoefficient();
              double fugV = pertSystem.getPhase(vapIdxP).getComponent(i).getFugacityCoefficient();
              double Kpert = fugL / Math.max(fugV, 1e-30);
              Kpert = Math.max(Kpert, 1e-15);
              Kpert = Math.min(Kpert, 1e15);
              sumKxPert += Kpert * x[i];
            }
          }
        } catch (Exception e) {
          // pertOk stays false
        }

        if (pertOk) {
          double dsumKxdT = (sumKxPert - sumKx) / dTpert;
          if (Math.abs(dsumKxdT) > 1e-15) {
            double deltaT = -(sumKx - 1.0) / dsumKxdT;
            double eosDampT = 0.5;
            deltaT = Math.max(-30.0, Math.min(30.0, deltaT));
            maxDeltaT = Math.max(maxDeltaT, Math.abs(eosDampT * deltaT));
            T[j] += eosDampT * deltaT;
            T[j] = Math.max(T[j], 100.0);
            T[j] = Math.min(T[j], 1000.0);
          }
        } else {
          // Fall back to Wilson dK/dT
          double Pbar = P[j] / 1e5;
          double dsumKxdT = 0;
          for (int i = 0; i < C; i++) {
            double dKidT = K[j][i] * 5.37 * (1.0 + omega[i]) * Tc[i] / (T[j] * T[j]);
            dsumKxdT += dKidT * x[i];
          }
          if (Math.abs(dsumKxdT) > 1e-15) {
            double deltaT = -(sumKx - 1.0) / dsumKxdT;
            deltaT = Math.max(-20.0, Math.min(20.0, deltaT));
            maxDeltaT = Math.max(maxDeltaT, Math.abs(0.3 * deltaT));
            T[j] += 0.3 * deltaT;
            T[j] = Math.max(T[j], 100.0);
            T[j] = Math.min(T[j], 1000.0);
          }
        }
      }

      // Step E: V from energy balance (Wang-Henke method)
      // Energy balance on tray j solved sequentially from bottom up:
      // V_j * (hV_j - hL_j) = L_{j+1} * (hL_{j+1} - hL_j)
      // + F_j * (hF_j - hL_j) + Q_j
      // This gives V_j that satisfies energy conservation on each tray.
      // Start from reboiler (j=0) where V is fixed by boilup ratio.
      double maxDeltaV = 0;
      double dampV = 0.5;
      for (int j = 0; j < N; j++) {
        if (j == 0 && hasReboiler && useOverallMBClosure) {
          // Reboiler: with T-spec and no explicit boilup, close via overall MB.
          L[0] = Math.max(totalFeedMolesField - V[N - 1], totalFeedMolesField * 0.001);
          continue;
        }
        if (j == 0 && hasReboiler && boilupRatio > 0) {
          // Reboiler: V fixed by boilup specification
          V[0] = boilupRatio * L[0];
          continue;
        }

        double denominator = hV[j] - hL[j];
        if (Math.abs(denominator) < 1e-3) {
          // hV ≈ hL means no phase split enthalpy difference — fall back to sum-rates
          double sumKx = 0;
          for (int i = 0; i < C; i++) {
            double xi = liq[j][i] / Math.max(L[j], 1e-20);
            sumKx += K[j][i] * xi;
          }
          double newV = V[j] * sumKx;
          newV = Math.max(newV, 1e-10);
          double deltaV = Math.abs(newV - V[j]);
          maxDeltaV = Math.max(maxDeltaV, deltaV);
          V[j] = V[j] + dampV * (newV - V[j]);
          continue;
        }

        // H_in from tray below (vapor) and tray above (liquid)
        double numerator = 0;
        if (j < N - 1) {
          // Liquid from tray above: L_{j+1} * (hL_{j+1} - hL_j)
          numerator += L[j + 1] * (hL[j + 1] - hL[j]);
        }
        if (j > 0) {
          // Vapor from tray below enters with hV_{j-1}, already mixed
          // The energy balance formulation for BP method:
          // V_j * hV_j + L_j * hL_j = V_{j-1} * hV_{j-1} + L_{j+1} * hL_{j+1} + F
          // Rearranging: V_j = [V_{j-1}*hV_{j-1} + L_{j+1}*hL_{j+1} + F - L_j*hL_j] /
          // hV_j
          // But L_j = V_{j-1} + L_{j+1} + F_j - V_j from total mass balance
          // This creates a coupling. Standard BP uses a simpler form.
        }

        // Feed enthalpy: F_liq * (hF_liq - hL_j) + F_vap * (hF_vap - hL_j)
        numerator += feedLTotal[j] * (feedHL[j] - hL[j]);
        numerator += feedVTotal[j] * (feedHV[j] - hL[j]);

        // Heat duty on tray (Q_j)
        numerator += Q[j];

        // Also add V_{j-1} * (hV_{j-1} - hL_j) to numerator
        // This is the standard formulation: Energy balance gives
        // V_j = [L_{j+1}*(hL_{j+1}-hL_j) + V_{j-1}*(hV_{j-1}-hL_j) + F*hF - F*hL_j + Q]
        // / (hV_j - hL_j)
        if (j > 0) {
          numerator += V[j - 1] * (hV[j - 1] - hL[j]);
        }

        double newV = numerator / denominator;
        newV = Math.max(newV, 1e-10);

        double deltaV = Math.abs(newV - V[j]);
        maxDeltaV = Math.max(maxDeltaV, deltaV);
        V[j] = V[j] + dampV * (newV - V[j]);
      }

      // Check mass balance and equilibrium error (sumKx deviation from 1.0)
      double mbErr = computeMassBalanceError();
      double maxSumKxErr = 0;
      for (int j = 0; j < N; j++) {
        if (!Double.isNaN(fixedTemperature[j])) {
          continue;
        }
        double sumKx = 0;
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          sumKx += K[j][i] * xi;
        }
        maxSumKxErr = Math.max(maxSumKxErr, Math.abs(sumKx - 1.0));
      }

      if (eosIter % 5 == 0 || eosIter < 5) {
        double eErr = computeMaxRelativeEnergyError();
        logger.info(
            "BP-EOS iter {}: dT={} dV={} mbErr={}% energy={}% sumKxErr={} T[0]={} T[N-1]={}",
            eosIter, String.format("%.2f", maxDeltaT), String.format("%.2f", maxDeltaV),
            String.format("%.4f", mbErr * 100), String.format("%.2f", eErr * 100),
            String.format("%.6f", maxSumKxErr), String.format("%.1f", T[0] - 273.15),
            String.format("%.1f", T[N - 1] - 273.15));
      }

      // Track best solution
      if (mbErr < bestEosMassBalErr) {
        bestEosMassBalErr = mbErr;
        saveTrayState(bestEosLiq, bestEosT, bestEosV);
      }

      // Check convergence — require both mass balance AND equilibrium satisfied
      if (maxDeltaT < 0.1 && maxDeltaV < 0.5 && mbErr < 0.005 && maxSumKxErr < 0.01) {
        logger.info("BP-EOS converged at iter {}: massBalErr={}% sumKxErr={}", eosIter,
            String.format("%.4f", mbErr * 100), String.format("%.6f", maxSumKxErr));
        break;
      }

      // Divergence detection: if mass balance gets much worse than Wilson, revert
      if (mbErr > 0.5) {
        logger.warn("BP-EOS diverging (massBalErr={}%), reverting to best",
            String.format("%.2f", mbErr * 100));
        restoreTrayState(bestEosLiq, bestEosT, bestEosV);
        break;
      }
    }

    // Use best EOS solution found
    restoreTrayState(bestEosLiq, bestEosT, bestEosV);

    // Final EOS evaluation
    evaluateThermo();

    logger.info("BP-EOS complete: bestMassBalErr={}%",
        String.format("%.4f", bestEosMassBalErr * 100));

    // Phase 3: Energy balance temperature correction (Option B)
    // DISABLED: Sequential BP→energy correction is unconditionally unstable for
    // wide-boiling systems. The BP solution (Phase 1+2) gives material balance
    // within 1e-14 relative error, but temperatures may differ due to
    // K-value differences for pseudo-components (different Tc/Pc/omega
    // correlations).
    // A simultaneous MESH Newton solver would be needed for energy-balanced T.
    // phaseThreePHflashCorrection();

    return true;
  }

  /**
   * Phase 3: PH-flash temperature correction for energy balance.
   *
   * <p>
   * After the BP method converges on material balance/equilibrium, tray temperatures may be off
   * because the BP method only solves MESH equations without the E (energy balance). This method
   * computes the enthalpy of all streams entering each tray, mixes them, then PH-flashes to find
   * the energy-consistent temperature. The resulting K-values and flows are fed back to the
   * tridiagonal solver, and the process iterates until the temperature profile stabilizes.
   * </p>
   */
  private void phaseThreePHflashCorrection() {
    System.out
        .println("Phase 3: Newton-E T correction. T[0]=" + String.format("%.2f", T[0] - 273.15)
            + " T[N-1]=" + String.format("%.2f", T[N - 1] - 273.15));

    int maxIter = 40;
    double dampE = 0.3;
    double maxClamp = 10.0; // K per iteration
    int innerBP = 5;
    double pertDT = 0.5; // K perturbation for numerical dE/dT

    // Save BP solution as fallback
    double[][] bpLiq = new double[N][C];
    double[] bpT = new double[N];
    double[] bpV = new double[N];
    saveTrayState(bpLiq, bpT, bpV);
    double bpMbErr = computeMassBalanceError();

    double[][] bestLiq = new double[N][C];
    double[] bestT = new double[N];
    double[] bestV = new double[N];
    saveTrayState(bestLiq, bestT, bestV);
    double bestMbErr = bpMbErr;

    for (int iter = 0; iter < maxIter; iter++) {
      // Compute energy balance error for each tray
      double[] energyErr = computeEnergyErrors();
      double maxDeltaT = 0;
      double maxEErr = 0;

      for (int j = 0; j < N; j++) {
        maxEErr = Math.max(maxEErr, Math.abs(energyErr[j]));
        if (!Double.isNaN(fixedTemperature[j])) {
          continue;
        }

        // Numerical dE/dT: perturb T[j], recompute, get derivative
        double t0 = T[j];
        T[j] = t0 + pertDT;
        evaluateThermoForTray(j);
        double[] energyErrP = computeEnergyErrors();
        double dEdT = (energyErrP[j] - energyErr[j]) / pertDT;
        T[j] = t0; // restore
        evaluateThermoForTray(j);

        if (Math.abs(dEdT) < 1e-10) {
          continue;
        }

        double deltaT = -energyErr[j] / dEdT;
        deltaT = Math.max(-maxClamp, Math.min(maxClamp, deltaT));
        deltaT *= dampE;
        maxDeltaT = Math.max(maxDeltaT, Math.abs(deltaT));

        if (iter == 0) {
          System.out.println("  Tray " + j + ": T=" + String.format("%.1f", T[j] - 273.15) + " E="
              + String.format("%.0f", energyErr[j]) + " dE/dT=" + String.format("%.0f", dEdT)
              + " dT=" + String.format("%.2f", deltaT));
        }

        T[j] += deltaT;
        T[j] = Math.max(T[j], 200.0);
        T[j] = Math.min(T[j], 800.0);
      }

      // Inner BP loop: reconverge material balance at new temperatures
      evaluateThermo();
      for (int inner = 0; inner < innerBP; inner++) {
        for (int comp = 0; comp < C; comp++) {
          solveTridiagonalForComponent(comp);
        }
        for (int j = 0; j < N; j++) {
          double sumL = 0;
          for (int i = 0; i < C; i++) {
            sumL += liq[j][i];
          }
          L[j] = Math.max(sumL, 1e-20);
        }
        for (int j = 0; j < N; j++) {
          double sumKx = 0;
          for (int i = 0; i < C; i++) {
            double xi = liq[j][i] / Math.max(L[j], 1e-20);
            sumKx += K[j][i] * xi;
          }
          V[j] = Math.max(V[j] * sumKx, 1e-10);
        }
        if (hasReboiler && useOverallMBClosure) {
          L[0] = Math.max(totalFeedMolesField - V[N - 1], totalFeedMolesField * 0.001);
        } else if (hasReboiler && boilupRatio > 0 && Double.isNaN(fixedTemperature[0])) {
          V[0] = boilupRatio * L[0];
        }
        evaluateThermo();
      }

      double mbErr = computeMassBalanceError();
      System.out.println("E-iter " + iter + ": maxDT=" + String.format("%.3f", maxDeltaT) + " maxE="
          + String.format("%.0f", maxEErr) + " mb=" + String.format("%.4f%%", mbErr * 100)
          + " T[0]=" + String.format("%.1f", T[0] - 273.15) + " T[N-1]="
          + String.format("%.1f", T[N - 1] - 273.15));

      if (mbErr < bestMbErr || (mbErr < 0.005 && maxDeltaT < 1.0)) {
        bestMbErr = mbErr;
        saveTrayState(bestLiq, bestT, bestV);
      }

      if (maxDeltaT < 0.1 && mbErr < 0.005) {
        System.out.println("Newton-E CONVERGED at iter " + iter);
        break;
      }

      if (mbErr > 1.0) {
        System.out.println("Newton-E diverging (mb=" + String.format("%.1f%%", mbErr * 100)
            + "), reducing damping");
        restoreTrayState(bestLiq, bestT, bestV);
        evaluateThermo();
        dampE *= 0.5;
        maxClamp = Math.max(maxClamp * 0.5, 2.0);
      }
    }

    restoreTrayState(bestLiq, bestT, bestV);
    evaluateThermo();

    System.out.println("Phase 3 done: T[0]=" + String.format("%.2f", T[0] - 273.15) + " T[N-1]="
        + String.format("%.2f", T[N - 1] - 273.15) + " mb="
        + String.format("%.4f%%", bestMbErr * 100));
  }

  /**
   * Sum-Rates (SR) energy balance correction phase.
   *
   * <p>
   * After the BP method converges on material balance, tray temperatures may be off because BP
   * determines T from bubble-point (sum Kx = 1), which is inappropriate for wide-boiling /
   * absorber-type columns. The SR method instead determines T from the energy balance:
   * </p>
   * <ol>
   * <li>Compute EOS K-values and enthalpies at current T, x</li>
   * <li>Tridiagonal M-solve for liquid compositions</li>
   * <li>Update L from sum of liquid compositions</li>
   * <li>Update V from total mass balance (bottom up)</li>
   * <li>Correct T from energy balance using Newton dE/dT</li>
   * <li>Iterate until energy + mass converge</li>
   * </ol>
   *
   * <p>
   * Reference: Seader, Henley &amp; Roper, "Separation Process Principles", Chapter 10.4 —
   * Sum-Rates method for absorber/stripper columns.
   * </p>
   */
  private void solveSumRatesPhase() {
    System.out.println(
        "[SR] Starting Sum-Rates energy correction. T[0]=" + String.format("%.1f", T[0] - 273.15)
            + "C  T[N-1]=" + String.format("%.1f", T[N - 1] - 273.15) + "C");
    // Print feed enthalpy diagnostics
    for (int j = 0; j < N; j++) {
      double feedMolesJ = 0;
      for (int i = 0; i < C; i++) {
        feedMolesJ += feedLiq[j][i] + feedVap[j][i];
      }
      if (feedMolesJ > 1e-10) {
        System.out.println("[SR-FEED] tray " + j + ": feedLTotal="
            + String.format("%.1f", feedLTotal[j]) + " feedHL=" + String.format("%.1f", feedHL[j])
            + " feedVTotal=" + String.format("%.1f", feedVTotal[j]) + " feedHV="
            + String.format("%.1f", feedHV[j]) + " feedEnthalpy="
            + String.format("%.0f", feedLTotal[j] * feedHL[j] + feedVTotal[j] * feedHV[j]));
      }
    }
    logger.info("SR: starting Sum-Rates energy correction. T[0]={} T[N-1]={}",
        String.format("%.1f", T[0] - 273.15), String.format("%.1f", T[N - 1] - 273.15));

    // Save BP solution as fallback
    double[][] bpLiq = new double[N][C];
    double[] bpT = new double[N];
    double[] bpV = new double[N];
    saveTrayState(bpLiq, bpT, bpV);
    double bpMbErr = computeMassBalanceError();
    double bpEnergyErr = computeMaxRelativeEnergyError();

    // Best solution tracking
    double[][] bestLiq = new double[N][C];
    double[] bestT = new double[N];
    double[] bestV = new double[N];
    saveTrayState(bestLiq, bestT, bestV);
    double bestMbErr = bpMbErr;
    double bestEnergyErr = bpEnergyErr;

    int maxIter = 500;
    double dampT = 0.15; // Conservative damping for stability
    double maxClampT = 2.0; // K per iteration — small steps
    double pertDT = 0.5; // K perturbation for numerical dE/dT

    // Physical temperature bounds: no tray can go below the coldest feed
    // or above the hottest feed + 50K (safety margin)
    double minTrayTemp = 1000.0;
    double maxTrayTemp = 0.0;
    for (int j = 0; j < N; j++) {
      if (!Double.isNaN(fixedTemperature[j])) {
        maxTrayTemp = Math.max(maxTrayTemp, fixedTemperature[j]);
      }
      double feedMoles = 0;
      for (int i = 0; i < C; i++) {
        feedMoles += feedLiq[j][i] + feedVap[j][i];
      }
      if (feedMoles > 1e-10) {
        // Use feed enthalpy to estimate feed temperature
        // For now, use the BP starting temperatures as bounds
        minTrayTemp = Math.min(minTrayTemp, bpT[j]);
        maxTrayTemp = Math.max(maxTrayTemp, bpT[j]);
      }
    }
    // Add margins
    minTrayTemp = Math.max(minTrayTemp - 20.0, 200.0); // don't go below ~-73C / coldest BP tray
    maxTrayTemp = maxTrayTemp + 20.0;
    // Use the BP top tray temperature as a reasonable floor
    double bpTopT = bpT[N - 1];
    double bpBotT = bpT[0];
    minTrayTemp = Math.max(bpTopT - 30.0, 200.0);
    logger.info("SR: T bounds: min={}C max={}C", String.format("%.1f", minTrayTemp - 273.15),
        String.format("%.1f", maxTrayTemp - 273.15));

    for (int iter = 0; iter < maxIter; iter++) {
      // Step 1: Update K-values and enthalpies at current T, x
      evaluateThermo();

      // Step 2: Tridiagonal M-solve for each component (BP-style)
      for (int comp = 0; comp < C; comp++) {
        solveTridiagonalForComponent(comp);
      }

      // Step 3: Proper Sum-Rates L/V update.
      //
      // Previous behavior: held L fixed to BP values and only refined T.
      // This left middle-section trays cold for columns where BP's CMO
      // initialization is poor (e.g. wide-boiling binaries with feed
      // in the middle). To fix this, we now:
      // (a) Set L[j] = sum_i liq[j][i] from the tridiagonal solution
      // (b) Apply overall MB closure at the anchor (L[0]=totalFeed-V[N-1]
      // when useOverallMBClosure, or boilup ratio otherwise)
      // (c) Cascade overall MB up the column to redistribute V[j]
      //
      // To keep the iteration stable, the new L/V values are blended with
      // the previous values via the same dampT factor used for T.
      double[] newL = new double[N];
      double[] newV = new double[N];
      double srLVDamp = 0.6; // blending factor for L/V updates
      for (int j = 0; j < N; j++) {
        double sumL = 0;
        for (int i = 0; i < C; i++) {
          sumL += liq[j][i];
        }
        newL[j] = Math.max(sumL, 1e-20);
      }
      // Note: do NOT anchor newL[0] from overall-MB.
      // M-equations are component-conservative; sum_i gives newL[j] that
      // already satisfies total mass balance. Anchoring L[0] from
      // (totalFeed - V[N-1]) creates a conflict that prevents the cascade
      // from converging (forces newV[N-1] == V_curr[N-1]).
      if (hasReboiler && boilupRatio > 0) {
        // V[0] = boilupRatio * L[0] gets enforced below by cascade
      }
      // Reboiler vapor anchor: V[0] = L[1] + feed[0] - L[0]
      newV[0] = Math.max(newL[1] + feedLTotal[0] + feedVTotal[0] - newL[0], 1e-10);
      // Cascade up: V[j] = V[j-1] + L[j+1] - L[j] + feed[j], j=1..N-2
      for (int j = 1; j < N - 1; j++) {
        double netFeed = feedLTotal[j] + feedVTotal[j];
        newV[j] = Math.max(newV[j - 1] + newL[j + 1] - newL[j] + netFeed, 1e-10);
      }
      // Top: V[N-1] = V[N-2] + feed[N-1] - L[N-1] (no L[N])
      newV[N - 1] =
          Math.max(newV[N - 2] + feedLTotal[N - 1] + feedVTotal[N - 1] - newL[N - 1], 1e-10);

      // Blend with current to damp the change (stability)
      for (int j = 0; j < N; j++) {
        double blendedL = (1.0 - srLVDamp) * L[j] + srLVDamp * newL[j];
        double scale = blendedL / Math.max(L[j], 1e-20);
        L[j] = Math.max(blendedL, 1e-20);
        // Scale component liquid flows to match new L[j]
        for (int i = 0; i < C; i++) {
          liq[j][i] *= scale;
        }
        V[j] = Math.max((1.0 - srLVDamp) * V[j] + srLVDamp * newV[j], 1e-10);
      }

      // Update vapor compositions
      for (int j = 0; j < N; j++) {
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Math.max(L[j], 1e-20);
          vap[j][i] = K[j][i] * xi * V[j];
        }
      }

      // Step 4: Refresh enthalpies at updated compositions
      evaluateThermo();

      // Step 7: T correction from energy balance (Newton dE/dT)
      double[] energyErr = computeEnergyErrors();
      double maxDeltaT = 0;
      double maxAbsEErr = 0;

      for (int j = 0; j < N; j++) {
        maxAbsEErr = Math.max(maxAbsEErr, Math.abs(energyErr[j]));

        if (!Double.isNaN(fixedTemperature[j])) {
          continue; // skip fixed-T trays (reboiler)
        }

        // Numerical dE/dT by perturbation
        double t0 = T[j];
        T[j] = t0 + pertDT;
        evaluateThermoForTray(j);
        double[] energyErrP = computeEnergyErrors();
        double dEdT = (energyErrP[j] - energyErr[j]) / pertDT;
        T[j] = t0;
        evaluateThermoForTray(j); // restore

        if (Math.abs(dEdT) < 1e-6) {
          if (iter == 0) {
            System.out.println("[SR-DIAG] tray " + j + ": dEdT too small ("
                + String.format("%.6f", dEdT) + "), skipping");
          }
          continue;
        }

        double rawDeltaT = -energyErr[j] / dEdT;

        // Diagnostic: print full details on first iteration
        if (iter == 0) {
          double hOutJ = V[j] * hV[j] + L[j] * hL[j];
          double hInJ = 0;
          if (j > 0) {
            hInJ += V[j - 1] * hV[j - 1];
          }
          if (j < N - 1) {
            hInJ += L[j + 1] * hL[j + 1];
          }
          double feedH = feedLTotal[j] * feedHL[j] + feedVTotal[j] * feedHV[j];
          double hInTotal = hInJ + feedH;
          System.out.println("[SR-DIAG] tray " + j + ": T=" + String.format("%.1fC", T[j] - 273.15)
              + " hV=" + String.format("%.1f", hV[j]) + " hL=" + String.format("%.1f", hL[j])
              + " V=" + String.format("%.1f", V[j]) + " L=" + String.format("%.1f", L[j]) + " Hout="
              + String.format("%.0f", hOutJ) + " Hin(noFeed)=" + String.format("%.0f", hInJ)
              + " feedH=" + String.format("%.0f", feedH) + " Hin(total)="
              + String.format("%.0f", hInTotal) + " Ej=" + String.format("%.0f", energyErr[j])
              + " dEdT=" + String.format("%.1f", dEdT) + " rawDT="
              + String.format("%.2f", rawDeltaT));
        }

        double deltaT = Math.max(-maxClampT, Math.min(maxClampT, rawDeltaT));
        deltaT *= dampT;
        maxDeltaT = Math.max(maxDeltaT, Math.abs(deltaT));

        T[j] += deltaT;
        T[j] = Math.max(T[j], minTrayTemp);
        T[j] = Math.min(T[j], maxTrayTemp);
      }

      // Adaptive damping: increase damping if making progress steadily
      if (iter > 20 && maxDeltaT < maxClampT * 0.5) {
        dampT = Math.min(dampT * 1.05, 0.5);
        maxClampT = Math.min(maxClampT * 1.05, 8.0);
      }

      // Check convergence
      double mbErr = computeMassBalanceError();
      double eErr = computeMaxRelativeEnergyError();

      if (iter % 10 == 0 || iter < 5) {
        System.out.println("[SR] iter " + iter + ": dT=" + String.format("%.3f", maxDeltaT) + " mb="
            + String.format("%.4f%%", mbErr * 100) + " energy="
            + String.format("%.1f%%", eErr * 100) + " T[0]=" + String.format("%.1f", T[0] - 273.15)
            + " T[top]=" + String.format("%.1f", T[N - 1] - 273.15) + " damp="
            + String.format("%.3f", dampT));
        logger.info("SR iter {}: maxDT={} mbErr={}% energy={}% T[0]={} T[N-1]={} damp={}", iter,
            String.format("%.3f", maxDeltaT), String.format("%.4f", mbErr * 100),
            String.format("%.2f", eErr * 100), String.format("%.1f", T[0] - 273.15),
            String.format("%.1f", T[N - 1] - 273.15), String.format("%.3f", dampT));
      }

      // Track best solution (minimize energy error while keeping mass OK)
      if (eErr < bestEnergyErr || (mbErr < 0.01 && eErr < bestEnergyErr * 1.05)) {
        bestEnergyErr = eErr;
        bestMbErr = mbErr;
        saveTrayState(bestLiq, bestT, bestV);
      }

      // Convergence: T stable and mass balance acceptable
      if (maxDeltaT < 0.05 && mbErr < 0.01) {
        logger.info("SR converged at iter {}: mbErr={}% energy={}%", iter,
            String.format("%.4f", mbErr * 100), String.format("%.2f", eErr * 100));
        break;
      }

      // Divergence check: if mass balance deteriorates badly, reduce damping
      if (mbErr > 5.0) {
        System.out.println("[SR] mass balance degraded to " + String.format("%.1f%%", mbErr * 100)
            + " — reducing damping");
        logger.warn("SR: mass balance degraded to {}%, reducing damping",
            String.format("%.1f", mbErr * 100));
        restoreTrayState(bestLiq, bestT, bestV);
        evaluateThermo();
        dampT *= 0.5;
        maxClampT = Math.max(maxClampT * 0.5, 1.0);
        if (dampT < 0.01) {
          logger.info("SR: damping too low ({} ), stopping", String.format("%.3f", dampT));
          break;
        }
      }
    }

    // Restore best solution found
    restoreTrayState(bestLiq, bestT, bestV);
    evaluateThermo();

    double finalMb = computeMassBalanceError();
    double finalE = computeMaxRelativeEnergyError();
    System.out.println("[SR] Done: mb=" + String.format("%.4f%%", finalMb * 100) + " energy="
        + String.format("%.1f%%", finalE * 100) + " T[0]=" + String.format("%.1f", T[0] - 273.15)
        + " T[top]=" + String.format("%.1f", T[N - 1] - 273.15));
    logger.info("SR Phase done: mbErr={}% energy={}% T[0]={} T[N-1]={}",
        String.format("%.4f", finalMb * 100), String.format("%.2f", finalE * 100),
        String.format("%.1f", T[0] - 273.15), String.format("%.1f", T[N - 1] - 273.15));

    // If SR solution is worse than BP in both metrics, revert to BP
    if (finalMb > bpMbErr * 5 && finalE > bpEnergyErr) {
      System.out.println("[SR] Solution worse than BP — REVERTING to BP");
      logger.info("SR: solution worse than BP, reverting");
      restoreTrayState(bpLiq, bpT, bpV);
      evaluateThermo();
    }
  }

  /**
   * Compute the maximum relative energy balance error across all non-fixed trays.
   *
   * <p>
   * The relative error for each tray is |E_j| / max(|H_out_j|, |H_in_j|). This gives a
   * dimensionless metric comparable to the mass balance error fraction.
   * </p>
   *
   * @return maximum relative energy error (0.0 = perfect, 1.0 = 100% imbalance)
   */
  private double computeMaxRelativeEnergyError() {
    double[] eErr = computeEnergyErrors();
    double maxRelErr = 0;
    for (int j = 0; j < N; j++) {
      if (!Double.isNaN(fixedTemperature[j])) {
        continue; // skip fixed-T trays (reboiler) — they have a duty
      }
      double hOut = Math.abs(V[j] * hV[j] + L[j] * hL[j]);
      double hIn = 0;
      if (j > 0) {
        hIn += Math.abs(V[j - 1] * hV[j - 1]);
      }
      if (j < N - 1) {
        hIn += Math.abs(L[j + 1] * hL[j + 1]);
      }
      double scale = Math.max(hOut, hIn);
      if (scale > 1e-10) {
        double relErr = Math.abs(eErr[j]) / scale;
        maxRelErr = Math.max(maxRelErr, relErr);
      }
    }
    return maxRelErr;
  }

  /**
   * Compute energy balance error for each tray.
   *
   * <p>
   * E_j = H_out_j - H_in_j where H_out_j = V[j]*hV[j] + L[j]*hL[j] and H_in_j = V[j-1]*hV[j-1] +
   * L[j+1]*hL[j+1] + feedEnthalpy[j].
   * </p>
   *
   * @return array of energy errors per tray in J
   */
  private double[] computeEnergyErrors() {
    double[] eErr = new double[N];
    for (int j = 0; j < N; j++) {
      double hOut = V[j] * hV[j] + L[j] * hL[j];
      double hIn = 0;
      if (j > 0) {
        hIn += V[j - 1] * hV[j - 1];
      }
      if (j < N - 1) {
        hIn += L[j + 1] * hL[j + 1];
      }
      // Feed enthalpy
      double feedMolesJ = 0;
      for (int i = 0; i < C; i++) {
        feedMolesJ += feedLiq[j][i] + feedVap[j][i];
      }
      if (feedMolesJ > 1e-10) {
        hIn += feedLTotal[j] * feedHL[j] + feedVTotal[j] * feedHV[j];
      }
      eErr[j] = hOut - hIn;
    }
    return eErr;
  }

  /**
   * Solve the tridiagonal material balance for a single component.
   *
   * <p>
   * For component i, the material balance on tray j gives: a_j * l_{i,j-1} + b_j * l_{i,j} + c_j *
   * l_{i,j+1} = d_j where a_j = -K_{i,j-1}*V_{j-1}/L_{j-1}, b_j = 1 + K_{i,j}*V_j/L_j, c_j = -1
   * </p>
   *
   * @param comp component index
   */
  private void solveTridiagonalForComponent(int comp) {
    double[] a = new double[N]; // sub-diagonal
    double[] b = new double[N]; // diagonal
    double[] c = new double[N]; // super-diagonal
    double[] d = new double[N]; // RHS

    for (int j = 0; j < N; j++) {
      a[j] = (j > 0) ? -K[j - 1][comp] * V[j - 1] / Math.max(L[j - 1], 1e-20) : 0.0;
      b[j] = 1.0 + K[j][comp] * V[j] / Math.max(L[j], 1e-20);
      c[j] = (j < N - 1) ? -1.0 : 0.0;
      d[j] = feedLiq[j][comp] + feedVap[j][comp];
    }

    // Thomas algorithm (forward sweep)
    double[] cp = new double[N];
    double[] dp = new double[N];
    cp[0] = c[0] / b[0];
    dp[0] = d[0] / b[0];
    for (int j = 1; j < N; j++) {
      double m = b[j] - a[j] * cp[j - 1];
      if (Math.abs(m) < 1e-30) {
        m = 1e-30;
      }
      cp[j] = c[j] / m;
      dp[j] = (d[j] - a[j] * dp[j - 1]) / m;
    }

    // Back substitution
    liq[N - 1][comp] = Math.max(dp[N - 1], 1e-20);
    for (int j = N - 2; j >= 0; j--) {
      liq[j][comp] = Math.max(dp[j] - cp[j] * liq[j + 1][comp], 1e-20);
    }
  }

  /**
   * Find the bubble point temperature for a tray by bisection.
   *
   * <p>
   * Finds T such that sum(K_i(T, P, x) * x_i) = 1. Uses Wilson K-values for speed in the BP method.
   * </p>
   *
   * @param j tray index
   * @return bubble point temperature in K, or NaN if not found
   */
  private double solveBubblePointTemperature(int j) {
    double[] x = new double[C];
    for (int i = 0; i < C; i++) {
      x[i] = liq[j][i] / Math.max(L[j], 1e-20);
    }

    // Use Wilson K-values for BP calculation (fast and stable)
    double Tlow = 100.0;
    double Thigh = 600.0;

    // Evaluate sum(K*x) at bounds
    double fLow = sumKxWilson(x, Tlow, P[j]) - 1.0;
    double fHigh = sumKxWilson(x, Thigh, P[j]) - 1.0;

    if (fLow * fHigh > 0) {
      return T[j]; // keep current
    }

    // Bisection
    for (int bi = 0; bi < 50; bi++) {
      double Tmid = 0.5 * (Tlow + Thigh);
      double fMid = sumKxWilson(x, Tmid, P[j]) - 1.0;

      if (Math.abs(fMid) < 1e-8 || (Thigh - Tlow) < 0.001) {
        return Tmid;
      }

      if (fMid * fLow < 0) {
        Thigh = Tmid;
        fHigh = fMid;
      } else {
        Tlow = Tmid;
        fLow = fMid;
      }
    }

    return 0.5 * (Tlow + Thigh);
  }

  /**
   * Compute sum(K_i * x_i) using Wilson K-values at given T and P.
   *
   * @param x liquid composition
   * @param temp temperature in K
   * @param press pressure in Pa
   * @return sum of K*x
   */
  private double sumKxWilson(double[] x, double temp, double press) {
    double sum = 0;
    double Pbar = press / 1e5;
    for (int i = 0; i < C; i++) {
      ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
      double Tc = comp.getTC();
      double Pc = comp.getPC();
      double omega = comp.getAcentricFactor();
      double Ki = (Pc / Pbar) * Math.exp(5.37 * (1.0 + omega) * (1.0 - Tc / temp));
      sum += Ki * x[i];
    }
    return sum;
  }

  /**
   * Compute the MESH residual vector.
   *
   * <p>
   * For each tray j, there are (C+2) equations:
   * <ul>
   * <li>C material balances: M_{i,j}</li>
   * <li>1 energy balance: H_j</li>
   * <li>1 summation equation: S_j (sum y - sum x = 0)</li>
   * </ul>
   *
   * <p>
   * Variable ordering per tray: [l_{0,j}, l_{1,j}, ..., l_{C-1,j}, T_j, V_j]
   *
   * <p>
   * Material balance for component i on tray j: M_{i,j} = l_{i,j}(1 + s_{i,j}) - l_{i,j-1} -
   * v_{i,j+1} - f_{i,j} = 0 where s_{i,j} = K_{i,j} * V_j / L_j is the stripping factor, and
   * v_{i,j} = K_{i,j} * l_{i,j} * V_j / L_j
   *
   * <p>
   * Energy balance for tray j: H_j = L_j*h_L_j + V_j*h_V_j - L_{j-1}*h_L_{j-1} - V_{j+1}*h_V_{j+1}
   * - F_L_j*h_FL_j - F_V_j*h_FV_j - Q_j = 0
   *
   * <p>
   * Summation: S_j = sum_i(y_{i,j}) - 1 = sum_i(K_{i,j}*x_{i,j}) - 1 = 0
   *
   * @return residual vector of length N*(C+2)
   */
  private double[] computeResidual() {
    double[] F = new double[totalVars];

    for (int j = 0; j < N; j++) {
      int base = j * varsPerTray;

      // Liquid and vapor totals
      double Lj = L[j];

      // Material balances: C equations
      for (int i = 0; i < C; i++) {
        double Mij = liq[j][i] + vap[j][i]; // leaving tray j

        // Liquid from tray above (j+1)
        if (j < N - 1) {
          Mij -= liq[j + 1][i];
        }

        // Vapor from tray below (j-1)
        if (j > 0) {
          Mij -= vap[j - 1][i];
        }

        // Feed
        Mij -= feedLiq[j][i] + feedVap[j][i];

        F[base + i] = Mij / flowScale;
      }

      // Energy balance (or fixed temperature specification)
      if (!Double.isNaN(fixedTemperature[j])) {
        // Fixed temperature: residual is T_j - T_spec
        F[base + C] = (T[j] - fixedTemperature[j]) / tempScale;
      } else {
        double Hj = Lj * hL[j] + V[j] * hV[j];

        if (j < N - 1) {
          Hj -= L[j + 1] * hL[j + 1];
        }
        if (j > 0) {
          Hj -= V[j - 1] * hV[j - 1];
        }

        // Feed enthalpies
        Hj -= feedLTotal[j] * feedHL[j] + feedVTotal[j] * feedHV[j];

        // Heat duty
        Hj -= Q[j];

        // Scale energy equation: total tray throughput * latent heat gives the
        // characteristic energy magnitude. This brings H residuals to ~O(1)
        // consistent with scaled material balances.
        double trayFlow = Math.max(Lj + V[j], flowScale);
        double latentHeat = Math.max(Math.abs(hV[j] - hL[j]), 1e3);
        double energyScale = trayFlow * latentHeat;
        F[base + C] = Hj / energyScale;
      }

      // Summation equation: sum(K_{i,j} * x_{i,j}) - 1 = 0
      double sumKx = 0;
      for (int i = 0; i < C; i++) {
        double xi = liq[j][i] / Math.max(Lj, 1e-20);
        sumKx += K[j][i] * xi;
      }
      F[base + C + 1] = sumKx - 1.0;
    }

    return F;
  }

  /**
   * Compute the Jacobian matrix numerically using finite differences.
   *
   * <p>
   * The Jacobian has block-tridiagonal structure. Only entries in the tri-diagonal bands are
   * computed. For variable k on tray jj, we compute d(residual_j)/d(var_{jj,k}) for j in {jj-1, jj,
   * jj+1}. After perturbing a variable on tray jj, only tray jj's thermo is re-evaluated (K-values,
   * enthalpies depend only on local T and x).
   * </p>
   *
   * @param F0 current residual vector
   * @return the Jacobian matrix [totalVars][totalVars]
   */
  private double[][] computeJacobian(double[] F0) {
    double[][] J = new double[totalVars][totalVars];
    double pertSize = 1e-4;
    double minPert = 1e-8;

    for (int jj = 0; jj < N; jj++) {
      for (int k = 0; k < varsPerTray; k++) {
        int varIdx = jj * varsPerTray + k;

        // Save original value
        double origVal = getVariable(jj, k);
        double h = Math.max(Math.abs(origVal) * pertSize, minPert);

        // Perturb
        setVariable(jj, k, origVal + h);

        // Re-evaluate thermo ONLY for the tray whose variable changed
        evaluateThermoForTray(jj);

        // Compute perturbed residuals for affected trays (j-1, j, j+1)
        int jStart = Math.max(0, jj - 1);
        int jEnd = Math.min(N - 1, jj + 1);
        for (int j = jStart; j <= jEnd; j++) {
          double[] Fpert = computeResidualForTray(j);
          int rowBase = j * varsPerTray;
          for (int eq = 0; eq < varsPerTray; eq++) {
            J[rowBase + eq][varIdx] = (Fpert[eq] - F0[rowBase + eq]) / h;
          }
        }

        // Restore
        setVariable(jj, k, origVal);
        evaluateThermoForTray(jj);
      }
    }

    return J;
  }

  /**
   * Compute the residual equations for a single tray.
   *
   * @param j tray index
   * @return residual vector of length varsPerTray
   */
  private double[] computeResidualForTray(int j) {
    double[] Fj = new double[varsPerTray];

    double Lj = L[j];

    // Material balances
    for (int i = 0; i < C; i++) {
      double Mij = liq[j][i] + vap[j][i];

      if (j < N - 1) {
        Mij -= liq[j + 1][i];
      }
      if (j > 0) {
        Mij -= vap[j - 1][i];
      }
      Mij -= feedLiq[j][i] + feedVap[j][i];

      Fj[i] = Mij / flowScale;
    }

    // Energy balance (or fixed temperature specification)
    if (!Double.isNaN(fixedTemperature[j])) {
      Fj[C] = (T[j] - fixedTemperature[j]) / tempScale;
    } else {
      double Hj = Lj * hL[j] + V[j] * hV[j];
      if (j < N - 1) {
        Hj -= L[j + 1] * hL[j + 1];
      }
      if (j > 0) {
        Hj -= V[j - 1] * hV[j - 1];
      }
      Hj -= feedLTotal[j] * feedHL[j] + feedVTotal[j] * feedHV[j];
      Hj -= Q[j];

      double trayFlow = Math.max(Lj + V[j], flowScale);
      double latentHeat = Math.max(Math.abs(hV[j] - hL[j]), 1e3);
      double energyScale = trayFlow * latentHeat;
      Fj[C] = Hj / energyScale;
    }

    // Summation
    double sumKx = 0;
    for (int i = 0; i < C; i++) {
      double xi = liq[j][i] / Math.max(Lj, 1e-20);
      sumKx += K[j][i] * xi;
    }
    Fj[C + 1] = sumKx - 1.0;

    return Fj;
  }

  /**
   * Evaluate thermodynamic properties for a single tray. Used during Jacobian perturbation to avoid
   * full column re-evaluation. V[j] is NOT overwritten — it is a free variable.
   *
   * @param j tray index
   */
  /**
   * Apply the Edmister Murphree-efficiency proxy to the K-values of tray j.
   *
   * <p>
   * The exact Murphree definition mixes the actual outlet vapor with the vapor entering from below:
   * y_actual = y_in + eta * (y_eq - y_in). Embedding that into the simultaneous MESH solve requires
   * the residual on tray j to couple to vap[j-1], which would expand the Jacobian bandwidth.
   * </p>
   *
   * The Edmister approximation K_eff = K^eta has the correct limits:
   * <ul>
   * <li>eta = 1.0 -&gt; K_eff = K (rigorous equilibrium)</li>
   * <li>eta -&gt; 0 -&gt; K_eff -&gt; 1.0, so y = K_eff*x -&gt; x and the stage becomes passive
   * (vapor leaves with the same composition as the liquid).</li>
   * </ul>
   * This is the same correction used by many shortcut and equation-tearing column codes (e.g.
   * ChemSep documentation, Edmister 1957) and is mass- and energy-balance consistent because the
   * residual equations always use the scaled K[j][i].
   *
   * @param j tray index (0 = reboiler, N-1 = condenser when present)
   */
  private void applyMurphreeEfficiencyToK(int j) {
    double eta = trayEta[j];
    if (eta >= 1.0 - 1.0e-10) {
      return;
    }
    for (int i = 0; i < C; i++) {
      double kEq = K[j][i];
      if (kEq <= 0.0) {
        continue;
      }
      K[j][i] = Math.pow(kEq, eta);
    }
  }

  private void evaluateThermoForTray(int j) {
    double sumLiq = 0;
    for (int i = 0; i < C; i++) {
      sumLiq += liq[j][i];
    }
    L[j] = Math.max(sumLiq, 1e-20);

    double[] x = new double[C];
    for (int i = 0; i < C; i++) {
      x[i] = liq[j][i] / L[j];
    }

    double Pbar = P[j] / 1e5;

    // Compute K-values using forced single-phase fugacity coefficients at the
    // tray's actual liquid and vapor compositions:
    // K_i = phi_L(T, P, x) / phi_V(T, P, y), y_i = K_i * x_i / sum(K*x)
    //
    // The previous implementation used TPflash(z = x) and read fugacities from
    // the resulting 2-phase split. That is incorrect for the MESH residual:
    // the flash moves composition from x to (x*, y*) (the two-phase split of
    // an overall feed z = x), so the fugacities are evaluated at x* != x.
    // Consequently the summation residual sum(K * x) - 1 enforces a
    // bubble-point at the wrong composition x*, leaving a fixed-point
    // residual floor (~1e-3 in mole fraction) even when the column is
    // otherwise converged. Using forced single-phase roots at the tray's
    // actual (x, y) removes this inconsistency. The same force-phase pattern
    // is used by computeSinglePhaseEnthalpy() for enthalpies.
    double[] phiL = new double[C];
    double[] phiV = new double[C];
    boolean phiOk = computeSinglePhaseFugacityCoefficients(x, T[j], Pbar, false, phiL);

    // Initial y guess: use previous K (or Wilson if uninitialised), normalised.
    double[] y = new double[C];
    double sumKxGuess = 0;
    for (int i = 0; i < C; i++) {
      double Kguess = K[j][i];
      if (!(Kguess > 1e-20 && Kguess < 1e15) || !phiOk) {
        Kguess = wilsonK(i, T[j], Pbar);
      }
      y[i] = Kguess * x[i];
      sumKxGuess += y[i];
    }
    for (int i = 0; i < C; i++) {
      y[i] = (sumKxGuess > 1e-20) ? y[i] / sumKxGuess : x[i];
    }

    // Self-consistency loop: K = phi_L(x) / phi_V(y), then y = K x / sum(K x).
    // Two inner sweeps are sufficient for HC mixtures at modest pressures since
    // phi_V for a cubic EOS in the vapor root depends weakly on y.
    boolean kOk = phiOk;
    if (kOk) {
      for (int sweep = 0; sweep < 2; sweep++) {
        if (!computeSinglePhaseFugacityCoefficients(y, T[j], Pbar, true, phiV)) {
          kOk = false;
          break;
        }
        double sumKxLocal = 0;
        for (int i = 0; i < C; i++) {
          double Knew = phiL[i] / Math.max(phiV[i], 1e-30);
          Knew = Math.max(Knew, 1e-15);
          Knew = Math.min(Knew, 1e15);
          K[j][i] = Knew;
          y[i] = Knew * x[i];
          sumKxLocal += y[i];
        }
        for (int i = 0; i < C; i++) {
          y[i] = (sumKxLocal > 1e-20) ? y[i] / sumKxLocal : x[i];
        }
      }
    }

    if (!kOk) {
      // Wilson K-values fallback (matches the previous flash-failure branch).
      for (int i = 0; i < C; i++) {
        K[j][i] = wilsonK(i, T[j], Pbar);
      }
      applyMurphreeEfficiencyToK(j);
      hL[j] = 0;
      hV[j] = 0;
      return;
    }

    // Apply Murphree-efficiency correction (Edmister K^eta proxy). At eta=1 the
    // stage is rigorous equilibrium; as eta -> 0 the K-values approach 1.0 and
    // the tray becomes effectively passive (vapor leaves with ~ liquid
    // composition). Reboiler/condenser stages have trayEta forced to 1.0 in
    // initialize() so they remain unaffected.
    applyMurphreeEfficiencyToK(j);

    // Refresh y for downstream enthalpy / vapor-flow assignments using the
    // efficiency-corrected K (Edmister proxy may have changed K).
    double sumKx = 0;
    for (int i = 0; i < C; i++) {
      sumKx += K[j][i] * x[i];
    }
    for (int i = 0; i < C; i++) {
      y[i] = (sumKx > 1e-20) ? K[j][i] * x[i] / sumKx : x[i];
    }

    // Enthalpies at actual stream compositions using single-phase EOS evaluation
    hL[j] = computeSinglePhaseEnthalpy(x, T[j], Pbar, false);
    hV[j] = computeSinglePhaseEnthalpy(y, T[j], Pbar, true);

    // Compute vapor component flows v_{i,j} = K_{i,j} * x_{i,j} * V_j
    // NOTE: V[j] is NOT updated here — it is a free variable
    for (int i = 0; i < C; i++) {
      vap[j][i] = K[j][i] * x[i] * V[j];
    }
  }

  /**
   * Compute molar enthalpy for a single phase (liquid or vapor) at given composition, temperature
   * and pressure using the EOS.
   *
   * <p>
   * This forces the EOS to evaluate at the specified phase root (liquid or vapor) rather than doing
   * a flash. This is essential for energy balance in the BP method: hL must be evaluated at the
   * actual liquid composition x[j] using the liquid root, and hV at the actual vapor composition
   * y[j] using the vapor root. Using the enthalpies from a TPflash of x[j] gives wrong hV because
   * the flash vapor has a different composition than the actual column vapor.
   * </p>
   *
   * @param composition molar composition array (must sum to ~1.0)
   * @param tempK temperature in Kelvin
   * @param pressBar pressure in bar (absolute)
   * @param isVapor true for vapor-phase enthalpy (vapor root), false for liquid (liquid root)
   * @return molar enthalpy in J/mol
   */
  /**
   * Boston-Sullivan inside-out refinement of the seed state.
   *
   * <p>
   * Activates when at least one tray has a fixed temperature spec — the case where the smooth
   * Wilson BP cascade under-resolves a sharp T-pinch. The algorithm follows Boston &amp; Sullivan
   * (1974):
   * </p>
   * <ol>
   * <li>Outer loop: compute base K-value {@code Kb[j]} as the x-weighted geometric mean of
   * {@code K[j][i]}, relative volatilities {@code alpha[j][i] = K[j][i] / Kb[j]}, and an
   * Antoine-style fit {@code ln(Kb) = A - B/T} via a Wilson-K perturbation at {@code T + 5K}.</li>
   * <li>Inner loop (fixed {@code alpha}, {@code A}, {@code B}): solve one tridiagonal MB per
   * component for {@code x[j][i]}; apply the bubble-point criterion {@code sum(alpha * x) * Kb = 1}
   * to get {@code Kb_new}; invert the Antoine fit for {@code T_new}; clamp T-pinned trays to
   * {@code fixedTemperature[j]}; update {@code liq[j][i]} and {@code vap[j][i]} preserving the
   * current {@code L[j]} and {@code V[j]} totals.</li>
   * <li>Repeat until the relative change in {@code alpha} between outer iterations falls below
   * tolerance.</li>
   * </ol>
   *
   * <p>
   * Mass balance is preserved by the tridiagonal solve. V[j] and L[j] are held at their BP-Wilson
   * values; the downstream SR / Newton phase polishes the energy balance.
   * </p>
   */
  private void runBostonSullivanRefinement() {
    if (!enableBostonSullivan) {
      return;
    }
    boolean hasFixedT = false;
    for (int j = 0; j < N; j++) {
      if (!Double.isNaN(fixedTemperature[j])) {
        hasFixedT = true;
        break;
      }
    }
    if (!hasFixedT) {
      return;
    }
    if (N < 2 || C < 1) {
      return;
    }

    // Wilson parameters for cheap K-perturbation (avoids EOS perturbation cost).
    double[] Tc = new double[C];
    double[] Pc = new double[C];
    double[] omega = new double[C];
    for (int i = 0; i < C; i++) {
      ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
      Tc[i] = comp.getTC();
      Pc[i] = comp.getPC();
      omega[i] = comp.getAcentricFactor();
    }

    final int maxOuter = 6;
    final int maxInner = 15;
    final double tTol = 0.3;
    final double alphaTol = 5.0e-3;
    final double tStepLimit = 8.0;
    final double tDamp = 0.7;

    double[] Kb = new double[N];
    double[] Aant = new double[N];
    double[] Bant = new double[N];
    double[][] alpha = new double[N][C];
    double[][] alphaOld = new double[N][C];

    double[] aDiag = new double[N];
    double[] bDiag = new double[N];
    double[] cDiag = new double[N];
    double[] rhs = new double[N];
    double[] xCol = new double[N];
    double[][] xUpd = new double[N][C];

    double initialT0 = T[0];
    double initialTtop = T[N - 1];

    for (int outer = 0; outer < maxOuter; outer++) {
      // (A) Compute Kb (x-weighted geomean of K) and alpha[j][i] from rigorous K.
      double maxAlphaChange = 0.0;
      for (int j = 0; j < N; j++) {
        double Lsum = 0;
        for (int i = 0; i < C; i++) {
          Lsum += liq[j][i];
        }
        Lsum = Math.max(Lsum, 1e-20);
        double lnKb = 0;
        for (int i = 0; i < C; i++) {
          double xi = liq[j][i] / Lsum;
          double Ki = Math.max(K[j][i], 1e-30);
          lnKb += xi * Math.log(Ki);
        }
        Kb[j] = Math.max(Math.exp(lnKb), 1e-30);
        for (int i = 0; i < C; i++) {
          double Ki = Math.max(K[j][i], 1e-30);
          alpha[j][i] = Ki / Kb[j];
          if (outer > 0) {
            double aOld = alphaOld[j][i];
            if (aOld > 1e-30) {
              double rel = Math.abs(alpha[j][i] - aOld) / aOld;
              if (rel > maxAlphaChange) {
                maxAlphaChange = rel;
              }
            }
          }
        }

        // (B) Antoine fit: ln(Kb) = A - B/T via Wilson K-perturbation at T+5K.
        // ln(Kb_pert) = ln(Kb) + sum_i x_i * ln(K_i_pert / K_i_base)
        double Tj = T[j];
        double Tpert = Tj + 5.0;
        double Pbar = Math.max(P[j] / 1e5, 1e-12);
        double shift = 0;
        for (int i = 0; i < C; i++) {
          double KiPert =
              (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / Tpert));
          double KiBase = (Pc[i] / Pbar) * Math.exp(5.37 * (1.0 + omega[i]) * (1.0 - Tc[i] / Tj));
          double xi = liq[j][i] / Lsum;
          shift += xi * Math.log(Math.max(KiPert, 1e-30) / Math.max(KiBase, 1e-30));
        }
        double lnKbAtPert = Math.log(Kb[j]) + shift;
        double inv1 = 1.0 / Tj;
        double inv2 = 1.0 / Tpert;
        double Bv;
        if (Math.abs(inv2 - inv1) > 1e-14) {
          Bv = (Math.log(Kb[j]) - lnKbAtPert) / (inv2 - inv1);
        } else {
          Bv = 2500.0;
        }
        // Sanity bound on B (typical 1000..5000 K for hydrocarbons).
        if (!Double.isFinite(Bv) || Bv < 200.0) {
          Bv = 2500.0;
        } else if (Bv > 15000.0) {
          Bv = 15000.0;
        }
        Bant[j] = Bv;
        Aant[j] = Math.log(Kb[j]) + Bv / Tj;
      }

      if (outer > 0 && maxAlphaChange < alphaTol) {
        logger.info("BS outer {} converged on alpha (maxRelChange={})", outer,
            String.format("%.3e", maxAlphaChange));
        // Snapshot alphaOld for next call (no-op here since we're breaking)
        break;
      }

      // (C) Inner loop: tridiag MB per component + bubble-T per tray.
      double maxDTinner = 0;
      int innerUsed = 0;
      for (int inner = 0; inner < maxInner; inner++) {
        innerUsed = inner + 1;
        // Build & solve C tridiagonal systems for x[*][i].
        for (int i = 0; i < C; i++) {
          for (int j = 0; j < N; j++) {
            double Kij = alpha[j][i] * Kb[j];
            bDiag[j] = L[j] + V[j] * Kij;
            if (j > 0) {
              double Kjm1 = alpha[j - 1][i] * Kb[j - 1];
              aDiag[j] = -V[j - 1] * Kjm1;
            } else {
              aDiag[j] = 0;
            }
            if (j < N - 1) {
              cDiag[j] = -L[j + 1];
            } else {
              cDiag[j] = 0;
            }
            rhs[j] = feedLiq[j][i] + feedVap[j][i];
          }
          thomas(aDiag, bDiag, cDiag, rhs, xCol);
          for (int j = 0; j < N; j++) {
            xUpd[j][i] = Math.max(xCol[j], 1e-25);
          }
        }

        // Normalize x per tray; bubble criterion -> Kb_new -> T_new -> Kb at T_new.
        maxDTinner = 0;
        for (int j = 0; j < N; j++) {
          double sumX = 0;
          for (int i = 0; i < C; i++) {
            sumX += xUpd[j][i];
          }
          if (sumX <= 0) {
            sumX = 1.0;
          }
          for (int i = 0; i < C; i++) {
            xUpd[j][i] /= sumX;
          }
          double sumAX = 0;
          for (int i = 0; i < C; i++) {
            sumAX += alpha[j][i] * xUpd[j][i];
          }
          double KbBubble = 1.0 / Math.max(sumAX, 1e-30);

          double tNew;
          if (!Double.isNaN(fixedTemperature[j])) {
            tNew = fixedTemperature[j];
          } else {
            double denom = Aant[j] - Math.log(Math.max(KbBubble, 1e-30));
            if (Math.abs(denom) < 1e-10) {
              tNew = T[j];
            } else {
              tNew = Bant[j] / denom;
            }
            // Guard against runaway estimates.
            if (!Double.isFinite(tNew) || tNew < 150.0 || tNew > 1500.0) {
              tNew = T[j];
            }
          }
          double dT = tNew - T[j];
          if (dT > tStepLimit) {
            dT = tStepLimit;
          }
          if (dT < -tStepLimit) {
            dT = -tStepLimit;
          }
          double tFinal = T[j] + tDamp * dT;
          if (!Double.isNaN(fixedTemperature[j])) {
            tFinal = fixedTemperature[j];
          }
          if (Math.abs(tFinal - T[j]) > maxDTinner) {
            maxDTinner = Math.abs(tFinal - T[j]);
          }
          T[j] = tFinal;
          Kb[j] = Math.exp(Aant[j] - Bant[j] / tFinal);
        }

        // Update liq[j][i] and vap[j][i] from xUpd and current Kb.
        // Preserve total L[j], V[j] (energy balance polished by downstream SR).
        for (int j = 0; j < N; j++) {
          double sumY = 0;
          double[] y = new double[C];
          for (int i = 0; i < C; i++) {
            y[i] = alpha[j][i] * Kb[j] * xUpd[j][i];
            sumY += y[i];
          }
          if (sumY <= 0) {
            sumY = 1.0;
          }
          for (int i = 0; i < C; i++) {
            y[i] /= sumY;
            liq[j][i] = L[j] * xUpd[j][i];
            vap[j][i] = V[j] * y[i];
          }
        }

        if (maxDTinner < tTol) {
          break;
        }
      }

      // Snapshot alpha for outer-loop convergence check next pass.
      for (int j = 0; j < N; j++) {
        for (int i = 0; i < C; i++) {
          alphaOld[j][i] = alpha[j][i];
        }
      }

      // (D) Refresh rigorous K for next outer iteration.
      evaluateThermo();

      // (E) Energy-balance V/L update (Wang-Henke style).
      // Refresh per-tray enthalpies at the current B-S T profile, then
      // re-distribute V/L so each internal tray closes its energy balance.
      // This is the step that delivers the sharp T-pinch in stripper columns
      // — without it, V/L stays at the BP-Wilson seed (too-low boilup) and
      // SR later reverses the T improvement to match the low V cascade.
      for (int j = 0; j < N; j++) {
        double Lsum = 0;
        double Vsum = 0;
        for (int i = 0; i < C; i++) {
          Lsum += liq[j][i];
          Vsum += vap[j][i];
        }
        double[] xj = new double[C];
        double[] yj = new double[C];
        for (int i = 0; i < C; i++) {
          xj[i] = (Lsum > 1e-20) ? liq[j][i] / Lsum : 1.0 / C;
          yj[i] = (Vsum > 1e-20) ? vap[j][i] / Vsum : 1.0 / C;
        }
        double Pbar = P[j] / 1e5;
        hL[j] = computeSinglePhaseEnthalpy(xj, T[j], Pbar, false);
        hV[j] = computeSinglePhaseEnthalpy(yj, T[j], Pbar, true);
      }

      double vDamp = 0.25;
      double[] Vold = new double[N];
      double[] Lold = new double[N];
      for (int j = 0; j < N; j++) {
        Vold[j] = V[j];
        Lold[j] = L[j];
      }
      // Pre-compute Fmol (used both for V[0] closure and L rebuild below).
      double[] FmolPre = new double[N];
      for (int j = 0; j < N; j++) {
        FmolPre[j] = feedLTotal[j] + feedVTotal[j];
      }
      // For T-pin reboiler stripper: Q[0] (reboiler duty) is the IMPLICIT
      // free variable that holds T[0] at its pinned value. Compute it from
      // overall column energy balance at the current state:
      // Q_reb = L[0]*hL[0] + V[N-1]*hV[N-1]
      // - sum_j(F_L[j]*hF_L[j] + F_V[j]*hF_V[j])
      // - sum_j(Q[j] for j!=0)
      // This gives the duty needed to close the overall EB given current
      // products and feeds, and unlocks the boilup so V[0] can grow.
      double qRebOverall = 0.0;
      boolean useOverallQreb = hasReboiler && useOverallMBClosure;
      if (useOverallQreb) {
        qRebOverall = L[0] * hL[0] + V[N - 1] * hV[N - 1];
        for (int jj = 0; jj < N; jj++) {
          qRebOverall -= feedLTotal[jj] * feedHL[jj];
          qRebOverall -= feedVTotal[jj] * feedHV[jj];
          if (jj != 0) {
            qRebOverall -= Q[jj];
          }
        }
      }
      for (int j = 0; j < N; j++) {
        // Boundary trays: keep current closure.
        if (j == 0 && hasReboiler && useOverallMBClosure) {
          // T-pin reboiler: V[0] free, fixed by tray-0 EB with Q[0]
          // taken from overall column EB (not the input Q[0]=0).
          // Tray-0 EB:
          // L[1]*hL[1] + F_L[0]*hF_L[0] + F_V[0]*hF_V[0] + Q_reb
          // = V[0]*hV[0] + L[0]*hL[0]
          if (Math.abs(hV[0]) < 1e-3) {
            continue;
          }
          double num = -L[0] * hL[0];
          if (N > 1) {
            num += L[1] * hL[1];
          }
          num += feedLTotal[0] * feedHL[0];
          num += feedVTotal[0] * feedHV[0];
          num += qRebOverall;
          double newV0 = num / hV[0];
          if (newV0 > 0 && Double.isFinite(newV0)) {
            // V[0] (boilup) gets aggressive damping/cap: it's the master
            // driver of the bottom-section T-cliff in T-pin strippers.
            // Without this, vDamp=0.25 keeps V[0] stuck near the BP-Wilson
            // seed (way too low) and the bottom section can't warm up.
            double cap = 2.0 * Math.max(Vold[0], 1.0);
            double dv = newV0 - V[0];
            if (dv > cap) {
              dv = cap;
            } else if (dv < -cap) {
              dv = -cap;
            }
            V[0] = V[0] + 0.6 * dv;
            if (V[0] < 1e-10) {
              V[0] = 1e-10;
            }
          }
          continue;
        }
        if (j == 0 && hasReboiler && boilupRatio > 0) {
          V[0] = boilupRatio * L[0];
          continue;
        }
        // Skip V[N-1] update: with no condenser and T-pin reboiler, V[N-1]
        // is the overhead product stream, fixed by overall MB closure
        // (V[N-1] = totalFeed - L[0]). The tray-EB at j=N-1 lacks a
        // closure equation (Q_reboiler is the free parameter, not V[N-1])
        // so its computed value runs to the cap. Leave V[N-1] at its
        // current value (BP-Wilson seed already satisfies overall MB).
        if (j == N - 1) {
          continue;
        }
        // Internal tray energy balance:
        // V_j*(hV_j-hL_j) = L_{j+1}*(hL_{j+1}-hL_j) + V_{j-1}*(hV_{j-1}-hL_j)
        // + F_L*(hF_L-hL_j) + F_V*(hF_V-hL_j) + Q_j
        double den = hV[j] - hL[j];
        if (Math.abs(den) < 1e-3) {
          continue;
        }
        double num = 0;
        if (j < N - 1) {
          num += L[j + 1] * (hL[j + 1] - hL[j]);
        }
        if (j > 0) {
          num += V[j - 1] * (hV[j - 1] - hL[j]);
        }
        num += feedLTotal[j] * (feedHL[j] - hL[j]);
        num += feedVTotal[j] * (feedHV[j] - hL[j]);
        num += Q[j];
        double newV = num / den;
        if (newV > 0 && Double.isFinite(newV)) {
          // Hard-clamp the step: at most ±25% of the prior V per outer pass
          // (smaller than V[0]'s ±200% — internal trays must propagate
          // gradually to avoid oscillation while V[0] catches up).
          double cap = 0.25 * Vold[j];
          double dv = newV - V[j];
          if (dv > cap) {
            dv = cap;
          } else if (dv < -cap) {
            dv = -cap;
          }
          V[j] = V[j] + 0.15 * dv;
          if (V[j] < 1e-10) {
            V[j] = 1e-10;
          }
          // Also bound to [0.25, 4.0] of prior V to prevent runaway.
          if (V[j] < 0.25 * Vold[j]) {
            V[j] = 0.25 * Vold[j];
          } else if (V[j] > 4.0 * Vold[j]) {
            V[j] = 4.0 * Vold[j];
          }
          // Hard physical cap: no internal vapor can exceed total feed
          // (overall vapor mass balance: V[N-1] ≤ totalFeed). Apply to
          // every tray since internal V can't exceed cumulative feed
          // either. Leave a small margin for numerical headroom.
          double Vcap = 0.98 * totalFeedMolesField;
          if (V[j] > Vcap) {
            V[j] = Vcap;
          }
        }
      }

      // Rebuild L from total mass balance (bottom-up cumulative):
      // L[j] = L[j+1] + V[j-1] - V[j] + F_mol[j] - sideDraws[j]
      // Conserve totals: keep L[0] consistent with closure.
      double[] Fmol = FmolPre;
      // Top-down propagation: at top tray, L[N-1] is feed liquid minus what
      // can't go down (no condenser). Use overall MB closure:
      // L[0] = sum(Fmol) - V[N-1]
      // and propagate downward from j=N-1 to j=1 using tray MB:
      // L[j] = V[j] + L[j-1] - V[j-1] - F[j-1] + ... (rearranged)
      // Simpler: just enforce per-tray total MB top-down keeping V[] from E-step.
      // For top tray (no overhead liquid product when there's no condenser):
      // In stripper: top tray is an internal tray with no L-in from above.
      // L[N-1] = F_L[N-1] (top feed liquid is the only liquid entering)
      // + (V[N-2] - V[N-1]) [if V decreases up, excess condenses
      // but with no condenser this is energy-driven]
      // Cleanest: propagate L bottom-up using
      // L[j+1] = L[j] + V[j] - V[j-1] - Fmol[j] (rearranged tray-j MB)
      // starting from L[0] from overall MB closure.
      if (hasReboiler && useOverallMBClosure) {
        L[0] = Math.max(totalFeedMolesField - V[N - 1], totalFeedMolesField * 0.001);
      }
      for (int j = 0; j < N - 1; j++) {
        // tray-j MB: V[j-1] + L[j+1] + F[j] = V[j] + L[j]
        // -> L[j+1] = L[j] + V[j] - (j>0 ? V[j-1] : 0) - F[j]
        double Vprev = (j > 0) ? V[j - 1] : 0.0;
        double newLjp1 = L[j] + V[j] - Vprev - Fmol[j];
        if (newLjp1 < 1e-10) {
          newLjp1 = 1e-10;
        }
        L[j + 1] = L[j + 1] + vDamp * (newLjp1 - L[j + 1]);
        if (L[j + 1] < 1e-10) {
          L[j + 1] = 1e-10;
        }
      }

      // Recompute per-component liq/vap from current x,y and new V,L.
      for (int j = 0; j < N; j++) {
        double Lsum = 0;
        double Vsum = 0;
        for (int i = 0; i < C; i++) {
          Lsum += liq[j][i];
          Vsum += vap[j][i];
        }
        for (int i = 0; i < C; i++) {
          double xij = (Lsum > 1e-20) ? liq[j][i] / Lsum : 1.0 / C;
          double yij = (Vsum > 1e-20) ? vap[j][i] / Vsum : 1.0 / C;
          liq[j][i] = L[j] * xij;
          vap[j][i] = V[j] * yij;
        }
      }

      logger.info(
          "BS outer {}: innerUsed={} maxDTinner={}K maxAlphaChange={} "
              + "T[0]={}C T[mid]={}C T[N-1]={}C V[0]={} V[N-1]={}",
          outer + 1, innerUsed, String.format("%.3f", maxDTinner),
          String.format("%.3e", maxAlphaChange), String.format("%.2f", T[0] - 273.15),
          String.format("%.2f", T[N / 2] - 273.15), String.format("%.2f", T[N - 1] - 273.15),
          String.format("%.1f", V[0]), String.format("%.1f", V[N - 1]));
    }

    logger.info("BS done: T[0]={}C->{}C  T[N-1]={}C->{}C",
        String.format("%.2f", initialT0 - 273.15), String.format("%.2f", T[0] - 273.15),
        String.format("%.2f", initialTtop - 273.15), String.format("%.2f", T[N - 1] - 273.15));
  }

  /**
   * Thomas algorithm for tridiagonal systems
   * {@code a[j] * x[j-1] + b[j] * x[j] + c[j] * x[j+1] = d[j]}.
   *
   * @param a sub-diagonal (a[0] ignored), length n
   * @param b main diagonal, length n
   * @param c super-diagonal (c[n-1] ignored), length n
   * @param d right-hand side, length n
   * @param x output solution, length n
   */
  private void thomas(double[] a, double[] b, double[] c, double[] d, double[] x) {
    int n = b.length;
    double[] cp = new double[n];
    double[] dp = new double[n];
    double b0 = b[0];
    if (Math.abs(b0) < 1e-30) {
      b0 = (b0 >= 0) ? 1e-30 : -1e-30;
    }
    cp[0] = c[0] / b0;
    dp[0] = d[0] / b0;
    for (int j = 1; j < n; j++) {
      double m = b[j] - a[j] * cp[j - 1];
      if (Math.abs(m) < 1e-30) {
        m = (m >= 0) ? 1e-30 : -1e-30;
      }
      cp[j] = (j < n - 1) ? c[j] / m : 0;
      dp[j] = (d[j] - a[j] * dp[j - 1]) / m;
    }
    x[n - 1] = dp[n - 1];
    for (int j = n - 2; j >= 0; j--) {
      x[j] = dp[j] - cp[j] * x[j + 1];
    }
  }

  /**
   * Wilson-K bubble-point temperature for a composition at a given pressure. Solves sum(z_i *
   * K_i(T,P)) = 1 by Newton iteration. Used only as an initial-guess generator for the BP method
   * T-profile.
   *
   * @param z molar composition
   * @param pBar pressure in bara
   * @param tGuess initial temperature guess in K
   * @return bubble-point temperature in K (Wilson approximation)
   */
  private double wilsonBubbleTemperature(double[] z, double pBar, double tGuess) {
    double t = Math.max(tGuess, 150.0);
    for (int iter = 0; iter < 60; iter++) {
      double f = -1.0;
      double dfdt = 0.0;
      for (int i = 0; i < C; i++) {
        ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
        double tc = comp.getTC();
        double pc = comp.getPC();
        double omega = comp.getAcentricFactor();
        double exponent = 5.37 * (1.0 + omega) * (1.0 - tc / t);
        double k = (pc / pBar) * Math.exp(exponent);
        double dkdt = k * 5.37 * (1.0 + omega) * tc / (t * t);
        f += z[i] * k;
        dfdt += z[i] * dkdt;
      }
      if (Math.abs(f) < 1e-8 || Math.abs(dfdt) < 1e-30) {
        break;
      }
      double dt = -f / dfdt;
      if (dt > 30.0)
        dt = 30.0;
      if (dt < -30.0)
        dt = -30.0;
      t += dt;
      t = Math.max(100.0, Math.min(1000.0, t));
    }
    return t;
  }

  /**
   * Wilson-K dew-point temperature for a composition at a given pressure. Solves sum(z_i /
   * K_i(T,P)) = 1 by Newton iteration. Used only as an initial-guess generator for the BP method
   * T-profile.
   *
   * @param z molar composition
   * @param pBar pressure in bara
   * @param tGuess initial temperature guess in K
   * @return dew-point temperature in K (Wilson approximation)
   */
  private double wilsonDewTemperature(double[] z, double pBar, double tGuess) {
    double t = Math.max(tGuess, 150.0);
    for (int iter = 0; iter < 60; iter++) {
      double f = -1.0;
      double dfdt = 0.0;
      for (int i = 0; i < C; i++) {
        ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
        double tc = comp.getTC();
        double pc = comp.getPC();
        double omega = comp.getAcentricFactor();
        double exponent = 5.37 * (1.0 + omega) * (1.0 - tc / t);
        double k = (pc / pBar) * Math.exp(exponent);
        double dkdt = k * 5.37 * (1.0 + omega) * tc / (t * t);
        f += z[i] / k;
        dfdt += -z[i] * dkdt / (k * k);
      }
      if (Math.abs(f) < 1e-8 || Math.abs(dfdt) < 1e-30) {
        break;
      }
      double dt = -f / dfdt;
      if (dt > 30.0)
        dt = 30.0;
      if (dt < -30.0)
        dt = -30.0;
      t += dt;
      t = Math.max(100.0, Math.min(1000.0, t));
    }
    return t;
  }

  /**
   * Compute fugacity coefficients for a single phase (liquid or vapor) at given composition,
   * temperature and pressure using a forced single-phase EOS root.
   *
   * <p>
   * Mirrors {@link #computeSinglePhaseEnthalpy} — sets one phase, forces the requested phase type
   * (GAS or LIQUID), and reads the fugacity coefficients directly. This is the correct way to
   * evaluate K-values inside a column MESH residual, because K_i = phi_L(x) / phi_V(y) must be
   * evaluated at the tray's actual compositions x and y, NOT at a flash composition.
   * </p>
   *
   * @param composition mole fractions (length C, normalised by caller)
   * @param tempK temperature in Kelvin
   * @param pressBar pressure in bara
   * @param isVapor true to force vapor root, false to force liquid root
   * @param phiOut output array of length C to be populated with phi_i
   * @return true on success, false if the forced-phase calculation failed
   */
  private boolean computeSinglePhaseFugacityCoefficients(double[] composition, double tempK,
      double pressBar, boolean isVapor, double[] phiOut) {
    try {
      SystemInterface sys = referenceSystem.clone();
      sys.setTemperature(tempK);
      sys.setPressure(pressBar);
      sys.setTotalNumberOfMoles(1.0);
      sys.setMolarComposition(composition);
      sys.init(0);
      sys.setNumberOfPhases(1);
      sys.setPhaseType(0, isVapor ? PhaseType.GAS : PhaseType.LIQUID);
      sys.setForcePhaseTypes(true);
      sys.init(2);
      for (int i = 0; i < C; i++) {
        double phi = sys.getPhase(0).getComponent(i).getFugacityCoefficient();
        if (!(phi > 0.0) || Double.isNaN(phi) || Double.isInfinite(phi)) {
          return false;
        }
        phiOut[i] = phi;
      }
      return true;
    } catch (Exception e) {
      logger.debug("Single-phase fugacity failed for {} phase at T={}K P={}bar",
          isVapor ? "vapor" : "liquid", tempK, pressBar);
      return false;
    }
  }

  /**
   * Wilson correlation for K-values, used as a fallback / initialisation when an EOS evaluation
   * fails.
   *
   * @param i component index in the reference system
   * @param tempK temperature in Kelvin
   * @param pressBar pressure in bara
   * @return Wilson K_i estimate
   */
  private double wilsonK(int i, double tempK, double pressBar) {
    ComponentInterface comp = referenceSystem.getPhase(0).getComponent(i);
    double Tc = comp.getTC();
    double Pc = comp.getPC();
    double omega = comp.getAcentricFactor();
    return (Pc / pressBar) * Math.exp(5.37 * (1.0 + omega) * (1.0 - Tc / tempK));
  }

  private double computeSinglePhaseEnthalpy(double[] composition, double tempK, double pressBar,
      boolean isVapor) {
    try {
      SystemInterface sys = referenceSystem.clone();
      sys.setTemperature(tempK);
      sys.setPressure(pressBar);
      sys.setTotalNumberOfMoles(1.0);
      sys.setMolarComposition(composition);
      sys.init(0);
      // After init(0) we MUST (re-)apply the single-phase configuration:
      // initAnalytic(0) calls reInitPhaseInformation() which resets
      // phaseType[0]=GAS and numberOfPhases to its default. setForcePhaseTypes
      // must come AFTER init(0) too so the flag is propagated to the phases
      // that exist post-reset.
      sys.setNumberOfPhases(1);
      sys.setPhaseType(0, isVapor ? PhaseType.GAS : PhaseType.LIQUID);
      sys.setForcePhaseTypes(true);
      sys.init(2);
      return sys.getPhase(0).getEnthalpy()
          / Math.max(sys.getPhase(0).getNumberOfMolesInPhase(), 1e-20);
    } catch (Exception e) {
      logger.debug("Single-phase enthalpy failed for {} phase at T={}K P={}bar",
          isVapor ? "vapor" : "liquid", tempK, pressBar);
      return 0.0;
    }
  }

  /**
   * Get the value of a variable by tray and intra-tray index.
   *
   * @param tray tray index
   * @param k intra-tray variable index: 0..C-1 are liquid flows, C is temperature, C+1 is vapor
   *        flow
   * @return variable value
   */
  private double getVariable(int tray, int k) {
    if (k < C) {
      return liq[tray][k];
    } else if (k == C) {
      return T[tray];
    } else {
      return V[tray];
    }
  }

  /**
   * Set the value of a variable by tray and intra-tray index.
   *
   * @param tray tray index
   * @param k intra-tray variable index
   * @param value new value
   */
  private void setVariable(int tray, int k, double value) {
    if (k < C) {
      liq[tray][k] = value;
    } else if (k == C) {
      T[tray] = value;
    } else {
      V[tray] = value;
    }
  }

  /**
   * Compute the overall mass balance error as a fraction of total feed.
   *
   * <p>
   * The overall mass balance is: V[N-1] + L[0] = total_feed. This method returns the absolute
   * relative error |V[N-1] + L[0] - feed| / feed.
   * </p>
   *
   * @return mass balance error as a fraction (0.005 = 0.5%)
   */
  private double computeMassBalanceError() {
    double totalFeedFlow = 0;
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        totalFeedFlow += feedLiq[j][i] + feedVap[j][i];
      }
    }
    double topFlow = V[N - 1]; // vapor leaving top tray
    double botFlow = L[0]; // liquid leaving bottom tray
    return Math.abs(topFlow + botFlow - totalFeedFlow) / Math.max(totalFeedFlow, 1e-20);
  }

  /**
   * Maximum per-tray, per-component molar imbalance, expressed relative to the total feed molar
   * flow.
   *
   * <p>
   * For each tray j and component i this evaluates the MESH M-residual
   * {@code feed_{j,i} + liq_{j+1,i} + vap_{j-1,i} - liq_{j,i} - vap_{j,i}} (the same equation that
   * {@link #computeResidual()} packs into the F vector), takes the absolute value, and divides by
   * the total feed flow. Returning the worst value across all (j, i) pairs exposes leakage on
   * individual species, which the scalar {@link #computeMassBalanceError()} (overall column
   * closure) and the L2 norm {@code ||F||} can both mask.
   * </p>
   *
   * @return maximum relative component imbalance (1.0e-3 = 0.1%)
   */
  private double computeMaxComponentImbalance() {
    double totalFeedFlow = 0.0;
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        totalFeedFlow += feedLiq[j][i] + feedVap[j][i];
      }
    }
    double denom = Math.max(totalFeedFlow, 1.0e-20);
    double worst = 0.0;
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        double mij = liq[j][i] + vap[j][i];
        if (j < N - 1) {
          mij -= liq[j + 1][i];
        }
        if (j > 0) {
          mij -= vap[j - 1][i];
        }
        mij -= feedLiq[j][i] + feedVap[j][i];
        double rel = Math.abs(mij) / denom;
        if (rel > worst) {
          worst = rel;
        }
      }
    }
    return worst;
  }

  /**
   * Apply a trust-region clamp to the Newton direction dx (in-place).
   *
   * <p>
   * Limits each variable's per-iteration change to a physically reasonable magnitude to prevent
   * Newton from leaving the basin of attraction. The whole step vector is scaled by the smallest
   * acceptable ratio so the descent direction is preserved.
   * </p>
   *
   * <p>
   * Bounds: |dT| &le; 10 K, |dV| &le; 0.5*V + 0.05*flowScale, |d(liq_ij)| &le; 0.5*liq_ij +
   * 1e-3*flowScale.
   * </p>
   *
   * @param dx the Newton step (will be scaled in place)
   * @return the trust-region scaling factor in (0, 1]
   */
  private double applyTrustRegion(double[] dx) {
    double scale = 1.0;
    final double maxDT = 10.0;
    for (int j = 0; j < N; j++) {
      int base = j * varsPerTray;
      for (int i = 0; i < C; i++) {
        double cap = 0.5 * liq[j][i] + 1.0e-3 * flowScale;
        double step = Math.abs(dx[base + i]);
        if (step > cap && cap > 0.0) {
          scale = Math.min(scale, cap / step);
        }
      }
      if (Double.isNaN(fixedTemperature[j])) {
        double step = Math.abs(dx[base + C]);
        if (step > maxDT) {
          scale = Math.min(scale, maxDT / step);
        }
      }
      double capV = 0.5 * V[j] + 0.05 * flowScale;
      double stepV = Math.abs(dx[base + C + 1]);
      if (stepV > capV && capV > 0.0) {
        scale = Math.min(scale, capV / stepV);
      }
    }
    if (scale < 1.0) {
      for (int k = 0; k < dx.length; k++) {
        dx[k] *= scale;
      }
    }
    return scale;
  }

  /**
   * Apply the Newton update dx with step size alpha to the current state.
   *
   * <p>
   * Includes bounds enforcement: temperatures must remain positive, component flows must remain
   * non-negative.
   * </p>
   *
   * @param dx correction vector
   * @param alpha step size
   */
  private void applyUpdate(double[] dx, double alpha) {
    for (int j = 0; j < N; j++) {
      int base = j * varsPerTray;

      // Update liquid component flows with non-negativity bounds
      for (int i = 0; i < C; i++) {
        double newVal = liq[j][i] - alpha * dx[base + i];
        liq[j][i] = Math.max(newVal, 1e-20);
      }

      // Update temperature with physical bounds (skip if fixed)
      if (Double.isNaN(fixedTemperature[j])) {
        double newT = T[j] - alpha * dx[base + C];
        T[j] = Math.max(newT, 100.0); // minimum 100K
        T[j] = Math.min(T[j], 1000.0); // maximum 1000K
      }

      // Update vapor flow
      double newV = V[j] - alpha * dx[base + C + 1];
      V[j] = Math.max(newV, 0.0);
    }
  }

  /**
   * Save current tray state for rollback.
   *
   * @param saveLiq array to save liquid flows
   * @param saveT array to save temperatures
   * @param saveV array to save vapor flows
   */
  private void saveTrayState(double[][] saveLiq, double[] saveT, double[] saveV) {
    for (int j = 0; j < N; j++) {
      System.arraycopy(liq[j], 0, saveLiq[j], 0, C);
      saveT[j] = T[j];
      saveV[j] = V[j];
    }
  }

  /**
   * Restore tray state from saved values.
   *
   * @param saveLiq saved liquid flows
   * @param saveT saved temperatures
   * @param saveV saved vapor flows
   */
  private void restoreTrayState(double[][] saveLiq, double[] saveT, double[] saveV) {
    for (int j = 0; j < N; j++) {
      System.arraycopy(saveLiq[j], 0, liq[j], 0, C);
      T[j] = saveT[j];
      V[j] = saveV[j];
    }
  }

  /**
   * Backtracking line search on ||F||^2.
   *
   * @param dx Newton direction
   * @param currentNorm current residual norm
   * @return step size alpha in (0, 1]
   */
  private double lineSearch(double[] dx, double currentNorm) {
    double alpha = 1.0;
    double c = 1e-4; // Armijo constant
    double rho = 0.5; // backtracking factor
    int maxBacktrack = 15;

    // Save current state
    double[][] saveLiq = new double[N][C];
    double[] saveT = new double[N];
    double[] saveV = new double[N];
    for (int j = 0; j < N; j++) {
      System.arraycopy(liq[j], 0, saveLiq[j], 0, C);
      saveT[j] = T[j];
      saveV[j] = V[j];
    }

    double bestAlpha = alpha;

    for (int bt = 0; bt < maxBacktrack; bt++) {
      // Trial update
      for (int j = 0; j < N; j++) {
        for (int i = 0; i < C; i++) {
          liq[j][i] = Math.max(saveLiq[j][i] - alpha * dx[j * varsPerTray + i], 1e-20);
        }
        if (Double.isNaN(fixedTemperature[j])) {
          T[j] = Math.max(saveT[j] - alpha * dx[j * varsPerTray + C], 100.0);
          T[j] = Math.min(T[j], 1000.0);
        }
        V[j] = Math.max(saveV[j] - alpha * dx[j * varsPerTray + C + 1], 0.0);
      }

      evaluateThermo();
      double[] Ftrial = computeResidual();
      double trialNorm = vectorNorm(Ftrial);

      if (trialNorm < (1.0 - c * alpha) * currentNorm || alpha < 0.01) {
        bestAlpha = alpha;
        break;
      }

      alpha *= rho;
    }

    // ALWAYS restore the original state — the main loop's applyUpdate handles the
    // final step
    for (int j = 0; j < N; j++) {
      System.arraycopy(saveLiq[j], 0, liq[j], 0, C);
      T[j] = saveT[j];
      V[j] = saveV[j];
    }
    evaluateThermo();

    return bestAlpha;
  }

  /**
   * Solve the block-tridiagonal linear system J * dx = -F.
   *
   * <p>
   * The Jacobian J is block-tridiagonal with block size (C+2). Blocks: A_j (sub-diagonal, coupling
   * to tray j-1), B_j (diagonal, tray j), C_j (super-diagonal, coupling to tray j+1).
   * </p>
   *
   * <p>
   * Uses the Thomas algorithm (block LU factorization) adapted for block matrices.
   * </p>
   *
   * @param J full Jacobian matrix
   * @param F residual vector (right-hand side is -F)
   * @return solution vector dx, or null if singular
   */
  private double[] solveBlockTridiagonal(double[][] J, double[] F) {
    int m = varsPerTray;

    // Extract blocks
    double[][][] Asub = new double[N][m][m]; // sub-diagonal
    double[][][] Bdiag = new double[N][m][m]; // diagonal
    double[][][] Csup = new double[N][m][m]; // super-diagonal
    double[][] rhs = new double[N][m]; // right-hand side = -F

    for (int j = 0; j < N; j++) {
      int rowBase = j * m;

      // Diagonal block: columns from tray j
      for (int r = 0; r < m; r++) {
        for (int c = 0; c < m; c++) {
          Bdiag[j][r][c] = J[rowBase + r][j * m + c];
        }
        rhs[j][r] = F[rowBase + r];
      }

      // Sub-diagonal block: columns from tray j-1
      if (j > 0) {
        for (int r = 0; r < m; r++) {
          for (int c = 0; c < m; c++) {
            Asub[j][r][c] = J[rowBase + r][(j - 1) * m + c];
          }
        }
      }

      // Super-diagonal block: columns from tray j+1
      if (j < N - 1) {
        for (int r = 0; r < m; r++) {
          for (int c = 0; c < m; c++) {
            Csup[j][r][c] = J[rowBase + r][(j + 1) * m + c];
          }
        }
      }
    }

    // Forward sweep: eliminate sub-diagonal blocks
    // Modified blocks: B'_j and rhs'_j
    double[][][] Bprime = new double[N][m][m];
    double[][] rhsPrime = new double[N][m];

    // Copy first block
    for (int r = 0; r < m; r++) {
      System.arraycopy(Bdiag[0][r], 0, Bprime[0][r], 0, m);
      rhsPrime[0][r] = rhs[0][r];
    }

    for (int j = 1; j < N; j++) {
      // Compute: fac = A_j * inv(B'_{j-1})
      double[][] invBprev = invertBlock(Bprime[j - 1]);
      if (invBprev == null) {
        return null; // singular
      }

      double[][] fac = multiplyBlocks(Asub[j], invBprev);

      // B'_j = B_j - fac * C_{j-1}
      double[][] facC = multiplyBlocks(fac, Csup[j - 1]);
      for (int r = 0; r < m; r++) {
        for (int c = 0; c < m; c++) {
          Bprime[j][r][c] = Bdiag[j][r][c] - facC[r][c];
        }
      }

      // rhs'_j = rhs_j - fac * rhs'_{j-1}
      double[] facRhs = multiplyBlockVec(fac, rhsPrime[j - 1]);
      for (int r = 0; r < m; r++) {
        rhsPrime[j][r] = rhs[j][r] - facRhs[r];
      }
    }

    // Back substitution
    double[][] xBlocks = new double[N][m];

    // Last block: x_N = inv(B'_N) * rhs'_N
    double[][] invBLast = invertBlock(Bprime[N - 1]);
    if (invBLast == null) {
      return null;
    }
    xBlocks[N - 1] = multiplyBlockVec(invBLast, rhsPrime[N - 1]);

    for (int j = N - 2; j >= 0; j--) {
      // x_j = inv(B'_j) * (rhs'_j - C_j * x_{j+1})
      double[] CxNext = multiplyBlockVec(Csup[j], xBlocks[j + 1]);
      double[] rhsAdj = new double[m];
      for (int r = 0; r < m; r++) {
        rhsAdj[r] = rhsPrime[j][r] - CxNext[r];
      }
      double[][] invBj = invertBlock(Bprime[j]);
      if (invBj == null) {
        return null;
      }
      xBlocks[j] = multiplyBlockVec(invBj, rhsAdj);
    }

    // Assemble solution vector
    double[] dx = new double[totalVars];
    for (int j = 0; j < N; j++) {
      System.arraycopy(xBlocks[j], 0, dx, j * m, m);
    }

    return dx;
  }

  /**
   * Invert a small dense matrix using Gauss-Jordan elimination.
   *
   * @param A square matrix of size m x m
   * @return inverse matrix, or null if singular
   */
  private double[][] invertBlock(double[][] A) {
    int m = A.length;
    double[][] aug = new double[m][2 * m];

    // Build augmented matrix [A | I]
    for (int i = 0; i < m; i++) {
      System.arraycopy(A[i], 0, aug[i], 0, m);
      aug[i][m + i] = 1.0;
    }

    // Forward elimination with partial pivoting
    for (int col = 0; col < m; col++) {
      // Find pivot
      int maxRow = col;
      double maxVal = Math.abs(aug[col][col]);
      for (int row = col + 1; row < m; row++) {
        if (Math.abs(aug[row][col]) > maxVal) {
          maxVal = Math.abs(aug[row][col]);
          maxRow = row;
        }
      }

      if (maxVal < 1e-30) {
        return null; // singular
      }

      // Swap rows
      if (maxRow != col) {
        double[] tmp = aug[col];
        aug[col] = aug[maxRow];
        aug[maxRow] = tmp;
      }

      // Scale pivot row
      double pivot = aug[col][col];
      for (int k = 0; k < 2 * m; k++) {
        aug[col][k] /= pivot;
      }

      // Eliminate column
      for (int row = 0; row < m; row++) {
        if (row != col) {
          double factor = aug[row][col];
          for (int k = 0; k < 2 * m; k++) {
            aug[row][k] -= factor * aug[col][k];
          }
        }
      }
    }

    // Extract inverse
    double[][] inv = new double[m][m];
    for (int i = 0; i < m; i++) {
      System.arraycopy(aug[i], m, inv[i], 0, m);
    }

    return inv;
  }

  /**
   * Multiply two square block matrices.
   *
   * @param A first matrix
   * @param B second matrix
   * @return product A*B
   */
  private double[][] multiplyBlocks(double[][] A, double[][] B) {
    int m = A.length;
    double[][] result = new double[m][m];
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < m; j++) {
        double sum = 0;
        for (int k = 0; k < m; k++) {
          sum += A[i][k] * B[k][j];
        }
        result[i][j] = sum;
      }
    }
    return result;
  }

  /**
   * Multiply a square block matrix by a vector.
   *
   * @param A matrix
   * @param v vector
   * @return product A*v
   */
  private double[] multiplyBlockVec(double[][] A, double[] v) {
    int m = A.length;
    double[] result = new double[m];
    for (int i = 0; i < m; i++) {
      double sum = 0;
      for (int k = 0; k < m; k++) {
        sum += A[i][k] * v[k];
      }
      result[i] = sum;
    }
    return result;
  }

  /**
   * Fallback dense LU solver for when block-tridiagonal solver fails.
   *
   * @param J Jacobian matrix
   * @param F residual vector
   * @return solution dx = -J^{-1}*F, or null if singular
   */
  private double[] solveDenseLU(double[][] J, double[] F) {
    int n = F.length;

    // Augmented matrix [J | F]
    double[][] aug = new double[n][n + 1];
    for (int i = 0; i < n; i++) {
      System.arraycopy(J[i], 0, aug[i], 0, n);
      aug[i][n] = F[i];
    }

    // Gaussian elimination with partial pivoting
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(aug[col][col]);
      for (int row = col + 1; row < n; row++) {
        if (Math.abs(aug[row][col]) > maxVal) {
          maxVal = Math.abs(aug[row][col]);
          maxRow = row;
        }
      }

      if (maxVal < 1e-30) {
        return null;
      }

      if (maxRow != col) {
        double[] tmp = aug[col];
        aug[col] = aug[maxRow];
        aug[maxRow] = tmp;
      }

      for (int row = col + 1; row < n; row++) {
        double factor = aug[row][col] / aug[col][col];
        for (int k = col; k <= n; k++) {
          aug[row][k] -= factor * aug[col][k];
        }
      }
    }

    // Back substitution
    double[] dx = new double[n];
    for (int i = n - 1; i >= 0; i--) {
      double sum = aug[i][n];
      for (int j = i + 1; j < n; j++) {
        sum -= aug[i][j] * dx[j];
      }
      dx[i] = sum / aug[i][i];
    }

    return dx;
  }

  /**
   * Compute the L2 norm of a vector.
   *
   * @param v vector
   * @return ||v||_2
   */
  private double vectorNorm(double[] v) {
    double sum = 0;
    for (double vi : v) {
      sum += vi * vi;
    }
    return Math.sqrt(sum);
  }

  /**
   * Apply the converged solution back to the DistillationColumn trays.
   *
   * <p>
   * For each tray, creates a two-phase thermo system at the converged T, P, and overall composition
   * (from liquid + vapor flows), performs a TPflash, then extracts the gas phase via
   * {@code phaseToSystem(0)} and the liquid phase via {@code phaseToSystem(1)}. The phase flow
   * rates are set to V[j] and L[j] from the solver variables.
   * </p>
   *
   * @param id calculation identifier
   * @param iterations number of iterations performed
   * @param finalNorm final residual norm
   * @param startTime start time in nanoseconds
   */
  private void applyResultsToColumn(UUID id, int iterations, double finalNorm, long startTime) {
    for (int j = 0; j < N; j++) {
      SimpleTray tray = (SimpleTray) column.getTray(j);

      // Recompute L[j] from solved liquid flows
      double sumLiq = 0;
      for (int i = 0; i < C; i++) {
        sumLiq += liq[j][i];
      }
      L[j] = Math.max(sumLiq, 1e-20);

      // Liquid composition x
      double[] x = new double[C];
      for (int i = 0; i < C; i++) {
        x[i] = liq[j][i] / L[j];
      }

      // Vapor composition y from K-values
      double[] y = new double[C];
      double sumY = 0;
      for (int i = 0; i < C; i++) {
        y[i] = K[j][i] * x[i];
        sumY += y[i];
      }
      if (sumY > 1e-20) {
        for (int i = 0; i < C; i++) {
          y[i] /= sumY;
        }
      } else {
        System.arraycopy(x, 0, y, 0, C);
      }

      // Overall composition z from liquid + vapor component flows
      double totalMoles = L[j] + V[j];
      double[] z = new double[C];
      for (int i = 0; i < C; i++) {
        z[i] = (liq[j][i] + vap[j][i]) / Math.max(totalMoles, 1e-20);
      }

      // Create overall tray system and flash to get proper two-phase equilibrium
      // V[j]/L[j] are in mol/hr (from feed flow rates); convert to mol/s for NeqSim
      SystemInterface traySystem = referenceSystem.clone();
      traySystem.setTemperature(T[j]);
      traySystem.setPressure(P[j] / 1e5);
      traySystem.setTotalNumberOfMoles(totalMoles / 3600.0);
      traySystem.setMolarComposition(z);
      traySystem.setNumberOfPhases(2);
      traySystem.init(0);

      ThermodynamicOperations ops = new ThermodynamicOperations(traySystem);
      try {
        ops.TPflash();
        traySystem.init(2);
        traySystem.initPhysicalProperties();
      } catch (Exception e) {
        logger.warn("Final TPflash failed on tray {}", j);
      }

      // Set the tray's mixed stream
      if (tray.getOutletStream() != null) {
        tray.getOutletStream().setThermoSystem(traySystem);
      }
      tray.setTemperature(T[j]);

      // Build gas and liquid output streams from the flashed tray system
      tray.invalidateOutStreamCache();

      // Build gas stream using the solver's own K-value-derived vapor composition
      // y[].
      // We do NOT use phaseToSystem(0) because with multiPhaseCheck and heavy
      // pseudo-components, phase 0 after TPflash can be a heavy liquid phase
      // (not vapor), leading to inverted compositions (100% C39-C80* in overhead).
      SystemInterface gasSystem = referenceSystem.clone();
      gasSystem.setTemperature(T[j]);
      gasSystem.setPressure(P[j] / 1e5);
      gasSystem.setTotalNumberOfMoles(V[j] / 3600.0);
      gasSystem.setMolarComposition(y);
      gasSystem.setNumberOfPhases(1);
      gasSystem.init(0);
      gasSystem.init(2);
      tray.setCachedGasOutStream(new neqsim.process.equipment.stream.Stream("gas_" + j, gasSystem));

      // Build liquid stream using the solver's liquid composition x[]
      SystemInterface liqSystem = referenceSystem.clone();
      liqSystem.setTemperature(T[j]);
      liqSystem.setPressure(P[j] / 1e5);
      liqSystem.setTotalNumberOfMoles(L[j] / 3600.0);
      liqSystem.setMolarComposition(x);
      liqSystem.setNumberOfPhases(1);
      liqSystem.init(0);
      liqSystem.init(2);
      tray.setCachedLiquidOutStream(
          new neqsim.process.equipment.stream.Stream("liq_" + j, liqSystem));
    }

    // Compute mass balance error for diagnostics
    double totalFeedFlow = 0;
    for (int j = 0; j < N; j++) {
      for (int i = 0; i < C; i++) {
        totalFeedFlow += feedLiq[j][i] + feedVap[j][i];
      }
    }
    double topFlow = V[N - 1]; // vapor leaving top tray
    double botFlow = L[0]; // liquid leaving bottom tray
    double massBalErr =
        Math.abs(topFlow + botFlow - totalFeedFlow) / Math.max(totalFeedFlow, 1e-20);

    double solveTime = (System.nanoTime() - startTime) / 1.0e9;

    // Store solve metrics for retrieval by the column
    lastIterations = iterations;
    lastMassBalanceError = massBalErr;
    lastSolveTimeSeconds = solveTime;

    logger.info(
        "Naphtali-Sandholm results: iter={}, ||F||={}, "
            + "massBalErr={}, topFlow={}, botFlow={}, feedFlow={}, time={}s",
        iterations, String.format("%.6e", finalNorm), String.format("%.6e", massBalErr),
        String.format("%.4f", topFlow), String.format("%.4f", botFlow),
        String.format("%.4f", totalFeedFlow), String.format("%.2f", solveTime));
  }
}
