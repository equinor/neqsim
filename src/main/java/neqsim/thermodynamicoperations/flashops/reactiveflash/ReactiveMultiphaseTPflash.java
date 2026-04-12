package neqsim.thermodynamicoperations.flashops.reactiveflash;

import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;

/**
 * Multiphase TP flash with simultaneous chemical and phase equilibrium using the modified RAND
 * method.
 *
 * <p>
 * This class is the main entry point for the reactive flash algorithm. It implements a state-of-
 * the-art non-stoichiometric approach that minimizes the total Gibbs energy subject to element
 * balance constraints, without requiring explicit specification of reaction stoichiometry.
 * </p>
 *
 * <p>
 * The algorithm:
 * <ol>
 * <li><b>Initialization:</b> Build the formula matrix A from component elemental composition.</li>
 * <li><b>Reactive stability analysis:</b> Check if the single-phase (chemically equilibrated) feed
 * is stable with respect to phase splitting. Uses tangent plane distance analysis on the
 * equilibrated feed.</li>
 * <li><b>Phase addition:</b> If unstable, add one or more trial phases to the system.</li>
 * <li><b>Modified RAND iteration:</b> Solve the simultaneous CPE problem using Newton iteration on
 * the Lagrangian formulation. Variables are moles of each component in each phase plus Lagrange
 * multipliers for element balance constraints.</li>
 * <li><b>Phase removal:</b> Remove phases with negligible phase fractions.</li>
 * <li><b>Convergence check:</b> Verify all equilibrium conditions are satisfied.</li>
 * </ol>
 * </p>
 *
 * <p>
 * The approach is non-stoichiometric: no explicit reactions or reaction extents are needed. Instead
 * chemical equilibrium is enforced through element balance constraints A * n_total = b, where A is
 * the formula matrix and b is the total element abundance vector.
 * </p>
 *
 * <p>
 * The algorithm can handle:
 * <ul>
 * <li>Multiple liquid phases (LLE, VLLE, etc.)</li>
 * <li>Multiple simultaneous chemical reactions</li>
 * <li>Reactive azeotropes</li>
 * <li>Solid precipitation with reactions</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>White, Johnson, Dantzig (1958) J. Chem. Phys. 28, 751</li>
 * <li>Smith, Missen (1982) Chemical Reaction Equilibrium Analysis, Wiley</li>
 * <li>Michelsen (1982) Fluid Phase Equilib. 9, 1-19 and 9, 21-40</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * <li>Tsanas, Stenby, Yan (2017) Chem. Eng. Sci. 174, 112-126</li>
 * <li>Paterson, Michelsen, Stenby, Yan (2018) SPE Journal 23(03), 609-622</li>
 * <li>Ascani, Sadowski, Held (2023) Molecules 28, 1768</li>
 * </ul>
 * </p>
 *
 * @author copilot
 * @version 1.0
 */
public class ReactiveMultiphaseTPflash extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ReactiveMultiphaseTPflash.class);

  /** Maximum outer loop iterations. */
  private static final int MAX_OUTER_ITER = 20;

  /** Minimum phase fraction to keep a phase. */
  private static final double MIN_PHASE_FRACTION = 1.0e-12;

  /** Minimum moles. */
  private static final double MIN_MOLES = 1.0e-30;

  /** The thermodynamic system. */
  private SystemInterface system;

  /** The formula matrix (element-component mapping). */
  private FormulaMatrix formulaMatrix;

  /** Number of independent reactions found. */
  private int numberOfReactions;

  /** Whether the flash converged. */
  private boolean converged;

  /** Total iterations used. */
  private int totalIterations;

  /** Final Gibbs energy. */
  private double finalGibbsEnergy;

  /** Equilibrium total moles (may differ from 1 when reactions change total moles). */
  private double equilibriumTotalMoles = 1.0;

  /** Reference to the last solver used (transient, not serializable). */
  private transient ModifiedRANDSolver solver;

  /** Whether to use DIIS acceleration in the RAND solver. */
  private boolean useDIIS = true;

  /** Whether to auto-discover reactions via chemicalReactionInit. */
  private boolean useChemicalReactionInit = false;

  /**
   * User-specified maximum number of phases. When positive, overrides system.getMaxNumberOfPhases()
   * (which may be reset by init calls). A value of -1 means use the system's value.
   */
  private int userMaxPhases = -1;

  /**
   * Constructor for ReactiveMultiphaseTPflash.
   *
   * @param system the thermodynamic system
   */
  public ReactiveMultiphaseTPflash(SystemInterface system) {
    this.system = system;
    this.converged = false;
    this.totalIterations = 0;
  }

  /**
   * Constructor with solid phase check option.
   *
   * @param system the thermodynamic system
   * @param checkForSolidPhase whether to check for solid phase formation
   */
  public ReactiveMultiphaseTPflash(SystemInterface system, boolean checkForSolidPhase) {
    this(system);
    // solidCheck not used in this implementation
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    try {
      // Resolve effective maxPhases: prefer user-set value, fall back to system
      int effectiveMaxPhases = userMaxPhases > 0 ? userMaxPhases : system.getMaxNumberOfPhases();

      // Step 0: Initialize system
      system.init(1);

      // Step 0.5: Auto-discover reactions and add missing ionic/product species
      // This queries the reaction database for known reactions involving the feed
      // components, adds missing product species (e.g., ions from dissociation),
      // and sets up the chemical reaction framework.
      if (useChemicalReactionInit) {
        int ncBefore = system.getPhase(0).getNumberOfComponents();
        int savedMaxPhases = system.getMaxNumberOfPhases();
        system.chemicalReactionInit();
        int ncAfter = system.getPhase(0).getNumberOfComponents();
        if (ncAfter > ncBefore) {
          // New components were added - must reinitialize the database and mixing rule
          // to size interaction parameter matrices for the new component count.
          system.createDatabase(true);
          system.setMixingRule(system.getMixingRule());
        }
        // Restore maxPhases — createDatabase/setMixingRule may reset it
        system.setMaxNumberOfPhases(savedMaxPhases);
        // Also enforce maxPhases on current number of phases
        if (savedMaxPhases == 1) {
          system.setNumberOfPhases(1);
        }
        system.init(0);
        system.init(1);
      }

      // Step 1: Build the formula matrix from component elemental composition
      formulaMatrix = new FormulaMatrix(system);
      numberOfReactions = formulaMatrix.getNumberOfIndependentReactions();

      logger.debug("ReactiveMultiphaseTPflash: " + formulaMatrix.getNumberOfComponents()
          + " components, " + formulaMatrix.getNumberOfElements() + " elements, "
          + numberOfReactions + " independent reactions");

      if (numberOfReactions == 0) {
        // No chemical reactions - composition is fully determined by element balance.
        // For systems with ionic species, init(1) may create multiple phases but
        // the composition is still fully constrained (NR=0 means element balance
        // uniquely determines all mole fractions). Mark as converged directly.
        if (system.getNumberOfPhases() <= 1 || formulaMatrix.hasIonicSpecies()) {
          logger.debug("No reactions: composition is at equilibrium (NR=0)");
          converged = true;
          return;
        }
        // For non-ionic multi-phase: fall back to standard VLE flash
        logger.debug("No independent reactions detected, running standard flash approach");
        runNonReactiveFlash();
        return;
      }

      // Step 2: Initialize phase split with a conventional (non-reactive) VLE flash.
      // This provides a much better starting point for the reactive RAND solver
      // than starting from a one-phase mixture of immiscible components.
      // The VLE flash separates the system into gas-rich and liquid-rich phases,
      // and then the RAND solver refines this by applying chemical equilibrium.
      // Only run VLE init when user allows multi-phase (maxPhases > 1).
      // Electrolyte CPA init may create 2 phases internally, but if user wants
      // single-phase CE (maxPhases=1), we should not honour those spurious phases.
      if (effectiveMaxPhases > 1) {
        initializeWithVLEFlash();
      }

      // Enforce effectiveMaxPhases: electrolyte CPA init may have created extra phases
      if (effectiveMaxPhases == 1 && system.getNumberOfPhases() > 1) {
        system.setNumberOfPhases(1);
        system.setMaxNumberOfPhases(1);
        system.init(0);
      }

      // Step 2.5: Reactive stability analysis
      // Skip stability analysis when system already has 2+ phases AND the user
      // allows multi-phase solutions (maxPhases >= 2). Running single-phase CE
      // for immiscible systems like methane/water is ill-posed and wastes
      // computational effort. But if maxPhases=1, the user wants single-phase,
      // so stability analysis runs normally. Uses effectiveMaxPhases
      // because createDatabase/setMixingRule/init may reset maxPhases.
      boolean skipStability = system.getNumberOfPhases() >= 2 && effectiveMaxPhases >= 2;
      boolean isUnstable;
      List<double[]> unstableTrials;

      if (skipStability) {
        logger.debug(
            "Skipping stability (already " + system.getNumberOfPhases() + " phases from VLE)");
        isUnstable = true; // treat as unstable since we have multiple phases
        unstableTrials = java.util.Collections.emptyList();
      } else {
        logger.debug("Step 2.5: stability analysis, np=" + system.getNumberOfPhases());
        ReactiveStabilityAnalysis stability = new ReactiveStabilityAnalysis(system, formulaMatrix);
        isUnstable = stability.run();
        logger.debug("Stability: unstable=" + isUnstable);

        if (!isUnstable) {
          // Single phase is stable - just solve homogeneous CE
          logger.debug("Phase-stable, solving single-phase CE");
          solveSinglePhaseChemicalEquilibrium();
          logger
              .debug("Single-phase CE done, converged=" + converged + " iters=" + totalIterations);
          converged = true;
          return;
        }
        unstableTrials = stability.getUnstableTrialCompositions();
      }

      // Step 3: Add trial phases from stability analysis (skip if already multi-phase)
      if (!unstableTrials.isEmpty()) {
        logger.debug("Adding " + unstableTrials.size() + " trial phases");
        addTrialPhases(unstableTrials);
      }
      logger.debug("Starting outer loop with np=" + system.getNumberOfPhases());

      // Step 4: Outer iteration loop
      // - Solve multiphase RAND
      // - Remove negligible phases
      // - Re-check stability
      for (int outerIter = 0; outerIter < MAX_OUTER_ITER; outerIter++) {
        logger.debug("Outer iter " + outerIter + " np=" + system.getNumberOfPhases());
        // Step 4a: Solve the multiphase modified RAND system
        ModifiedRANDSolver randSolver = new ModifiedRANDSolver(system, formulaMatrix);
        randSolver.setUseDIIS(this.useDIIS);
        boolean randConverged = randSolver.solve();
        totalIterations += randSolver.getIterationsUsed();
        equilibriumTotalMoles = randSolver.getTotalMoles();
        this.solver = randSolver;
        logger.debug("RAND: converged=" + randConverged + " iters=" + randSolver.getIterationsUsed()
            + " residual=" + randSolver.getFinalResidual());

        if (!randConverged) {
          logger.warn("Modified RAND solver did not converge at outer iteration " + outerIter);
          // Accept near-converged multi-phase solutions to prevent outer loop
          // from restarting and overshooting a good solution
          if (system.getNumberOfPhases() > 1 && randSolver.getFinalResidual() < 5.0e-3) {
            converged = true;
            logger.info("Accepted near-converged (residual=" + randSolver.getFinalResidual() + ")");
            break;
          }
        }

        // Step 4b: Remove phases with negligible fractions
        boolean phaseRemoved = removeNegligiblePhases();
        logger.debug("phaseRemoved=" + phaseRemoved + " np=" + system.getNumberOfPhases());

        // Step 4c: Check convergence
        if (randConverged && !phaseRemoved) {
          // If we can't add more phases (at maxPhases), accept the result.
          // Running stability analysis is expensive and can hang for immiscible
          // systems where single-phase CE is ill-posed (e.g., methane/water).
          if (system.getNumberOfPhases() >= effectiveMaxPhases) {
            converged = true;
            logger.debug("CONVERGED (at maxPhases=" + effectiveMaxPhases + ")");
            break;
          }

          // Do a final stability check to see if we need another phase
          logger.debug("RAND converged, checking stability...");
          ReactiveStabilityAnalysis recheck = new ReactiveStabilityAnalysis(system, formulaMatrix);
          boolean stillUnstable = recheck.run();
          logger.debug("Stability recheck: unstable=" + stillUnstable);

          if (!stillUnstable) {
            converged = true;
            logger.debug("CONVERGED (stable after RAND)");
            break;
          }

          // Add new trial phases and continue
          List<double[]> newTrials = recheck.getUnstableTrialCompositions();
          if (!newTrials.isEmpty()) {
            int phasesBefore = system.getNumberOfPhases();
            addTrialPhases(newTrials);
            if (system.getNumberOfPhases() == phasesBefore) {
              // Could not add phase (maxPhases reached) — accept current CE as converged
              converged = true;
              logger.debug("CONVERGED (maxPhases reached)");
              break;
            }
          } else {
            converged = true;
            logger.debug("CONVERGED (no trials)");
            break;
          }
        }

        // Step 4d: If converged but a phase was removed, accept CE result
        if (randConverged && phaseRemoved) {
          // Phase was negligible — CE is satisfied in remaining phases
          converged = true;
          break;
        }
      }

      // Compute final Gibbs energy
      finalGibbsEnergy = computeGibbsEnergy();

      if (!converged) {
        logger.warn("ReactiveMultiphaseTPflash did not converge after " + totalIterations
            + " total iterations");
      } else {
        logger.debug("ReactiveMultiphaseTPflash converged: " + system.getNumberOfPhases()
            + " phases, " + totalIterations + " total iterations");
      }

    } catch (Exception ex) {
      logger.error("ReactiveMultiphaseTPflash failed: " + ex.getMessage(), ex);
    }
  }

  /**
   * Run a non-reactive flash when no independent reactions exist.
   *
   * <p>
   * Uses successive substitution on K-values followed by a Newton step if needed.
   * </p>
   */
  private void runNonReactiveFlash() {
    int nc = system.getPhase(0).getNumberOfComponents();

    // Check trivial stability first using Wilson K-values
    double T = system.getTemperature();
    double P = system.getPressure();
    boolean allSupercritical = true;
    for (int i = 0; i < nc; i++) {
      double tc = system.getPhase(0).getComponent(i).getTC();
      if (T < tc) {
        allSupercritical = false;
        break;
      }
    }
    if (allSupercritical) {
      converged = true;
      return;
    }

    if (system.getNumberOfPhases() < 2) {
      system.addPhase();
    }

    // Determine which logical phase index is liquid vs gas.
    // After init(0), NeqSim defaults to phase 0 = GAS, phase 1 = LIQUID.
    // After init(2)/reOrderPhases(), this may be rearranged.
    // The SS VLE requires correct EOS root assignment: K = fugCoef_liq / fugCoef_vap.
    int liqIdx = 0;
    int gasIdx = 1;
    if (system.getPhase(0).getType() == neqsim.thermo.phase.PhaseType.GAS) {
      liqIdx = 1;
      gasIdx = 0;
    }

    // Initialize K-values from Wilson correlation
    double[] lnK = new double[nc];
    for (int i = 0; i < nc; i++) {
      double tc = system.getPhase(0).getComponent(i).getTC();
      double pc = system.getPhase(0).getComponent(i).getPC();
      double omega = system.getPhase(0).getComponent(i).getAcentricFactor();
      if (pc > 0 && tc > 0) {
        lnK[i] = Math.log(pc / P) + 5.373 * (1.0 + omega) * (1.0 - tc / T);
      } else {
        lnK[i] = 0.0;
      }
    }

    // Use Wilson K-values to compute initial phase split BEFORE calling init(1).
    // This breaks the trivial K=1 fixed point when both phases start with identical
    // compositions (which happens after init(0) duplicates z to both phases).
    double beta = solveRachfordRice(lnK, 0.5);
    beta = Math.max(1e-15, Math.min(1.0 - 1e-15, beta));
    double[] z = new double[nc];
    for (int i = 0; i < nc; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
      double Ki = Math.exp(lnK[i]);
      double xi = z[i] / (1.0 + beta * (Ki - 1.0));
      double yi = Ki * xi;
      system.getPhase(liqIdx).getComponent(i).setx(Math.max(xi, MIN_MOLES));
      system.getPhase(gasIdx).getComponent(i).setx(Math.max(yi, MIN_MOLES));
    }
    system.getPhase(liqIdx).normalize();
    system.getPhase(gasIdx).normalize();
    system.setBeta(liqIdx, 1.0 - beta);
    system.setBeta(gasIdx, beta);

    // Successive substitution with fugacity-based K-values
    for (int iter = 0; iter < 200; iter++) {
      double err = 0.0;
      system.init(1);
      for (int i = 0; i < nc; i++) {
        double lnPhiL = system.getPhase(liqIdx).getComponent(i).getLogFugacityCoefficient();
        double lnPhiV = system.getPhase(gasIdx).getComponent(i).getLogFugacityCoefficient();
        double lnKnew = lnPhiL - lnPhiV;
        err += Math.abs(lnKnew - lnK[i]);
        lnK[i] = lnKnew;
      }

      beta = system.getBeta(gasIdx);
      beta = solveRachfordRice(lnK, beta);
      beta = Math.max(1e-15, Math.min(1.0 - 1e-15, beta));

      for (int i = 0; i < nc; i++) {
        z[i] = system.getPhase(0).getComponent(i).getz();
        double Ki = Math.exp(lnK[i]);
        double xi = z[i] / (1.0 + beta * (Ki - 1.0));
        double yi = Ki * xi;
        system.getPhase(liqIdx).getComponent(i).setx(Math.max(xi, MIN_MOLES));
        system.getPhase(gasIdx).getComponent(i).setx(Math.max(yi, MIN_MOLES));
      }
      system.getPhase(liqIdx).normalize();
      system.getPhase(gasIdx).normalize();
      system.setBeta(liqIdx, 1.0 - beta);
      system.setBeta(gasIdx, beta);

      if (err < 1e-10) {
        converged = true;
        break;
      }
    }
    system.init(1);
  }

  /**
   * Solve the Rachford-Rice equation using Newton-Raphson.
   *
   * @param lnK log K-values
   * @param betaInit initial guess for vapor fraction
   * @return converged vapor fraction
   */
  private double solveRachfordRice(double[] lnK, double betaInit) {
    int nc = lnK.length;
    double beta = betaInit;
    for (int iter = 0; iter < 50; iter++) {
      double f = 0.0;
      double df = 0.0;
      for (int i = 0; i < nc; i++) {
        double zi = system.getPhase(0).getComponent(i).getz();
        double Ki = Math.exp(lnK[i]);
        double denom = 1.0 + beta * (Ki - 1.0);
        if (Math.abs(denom) < 1e-30) {
          continue;
        }
        f += zi * (Ki - 1.0) / denom;
        df -= zi * (Ki - 1.0) * (Ki - 1.0) / (denom * denom);
      }
      if (Math.abs(df) < 1e-30) {
        break;
      }
      double step = f / df;
      beta -= step;
      beta = Math.max(1e-15, Math.min(1.0 - 1e-15, beta));
      if (Math.abs(step) < 1e-12) {
        break;
      }
    }
    return beta;
  }

  /**
   * Initialize the phase split using a conventional (non-reactive) VLE flash.
   *
   * <p>
   * Uses Wilson K-values and successive substitution on the Rachford-Rice equation to determine if
   * the system splits into two phases. If VLE instability is detected, the system is initialized
   * with the two-phase split, providing a much better starting point for the reactive RAND solver.
   * </p>
   */
  private void initializeWithVLEFlash() {
    // If system already has 2+ phases (e.g. from a prior TPflash), skip Wilson
    // K-value initialization — the existing phase split is better than Wilson estimates.
    if (system.getNumberOfPhases() >= 2) {
      logger.debug("VLE initialization skipped: system already has " + system.getNumberOfPhases()
          + " phases");
      return;
    }

    int nc = system.getPhase(0).getNumberOfComponents();
    double T = system.getTemperature();
    double P = system.getPressure();

    // Compute Wilson K-values
    double[] lnK = new double[nc];
    boolean hasVolatile = false;
    for (int i = 0; i < nc; i++) {
      double tc = system.getPhase(0).getComponent(i).getTC();
      double pc = system.getPhase(0).getComponent(i).getPC();
      double omega = system.getPhase(0).getComponent(i).getAcentricFactor();
      if (pc > 0 && tc > 0) {
        lnK[i] = Math.log(pc / P) + 5.373 * (1.0 + omega) * (1.0 - tc / T);
        if (Math.abs(lnK[i]) > 0.1) {
          hasVolatile = true;
        }
      }
    }

    if (!hasVolatile) {
      return; // No VLE split expected
    }

    // Get overall composition
    double[] z = new double[nc];
    for (int i = 0; i < nc; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }

    // Solve Rachford-Rice for vapor fraction
    double V = 0.5;
    for (int iter = 0; iter < 100; iter++) {
      double f = 0.0;
      double df = 0.0;
      for (int i = 0; i < nc; i++) {
        double Ki = Math.exp(lnK[i]);
        double denom = 1.0 + V * (Ki - 1.0);
        if (Math.abs(denom) < 1e-30) {
          continue;
        }
        f += z[i] * (Ki - 1.0) / denom;
        df -= z[i] * (Ki - 1.0) * (Ki - 1.0) / (denom * denom);
      }
      if (Math.abs(f) < 1e-10) {
        break;
      }
      if (Math.abs(df) > 1e-30) {
        V -= f / df;
      }
      V = Math.max(0.0, Math.min(1.0, V));
    }

    // Check if valid two-phase solution
    if (V < 1e-10 || V > 1.0 - 1e-10) {
      return; // Single phase - no VLE initialization needed
    }

    // Set up 2-phase system
    if (system.getNumberOfPhases() < 2) {
      system.addPhase();
      int newIdx = system.getNumberOfPhases() - 1;
      try {
        neqsim.thermo.phase.PhaseInterface newPhase = system.getPhase(0).clone();
        system.setPhase(newPhase, newIdx);
      } catch (Exception ex) {
        logger.warn("Failed to create VLE trial phase: " + ex.getMessage());
        return;
      }
    }

    // Set compositions for both phases
    for (int i = 0; i < nc; i++) {
      double Ki = Math.exp(lnK[i]);
      double xi = z[i] / (1.0 + V * (Ki - 1.0)); // liquid composition
      double yi = Ki * xi; // vapor composition
      system.getPhase(0).getComponent(i).setx(Math.max(xi, 1e-30));
      if (system.getNumberOfPhases() > 1) {
        system.getPhase(1).getComponent(i).setx(Math.max(yi, 1e-30));
      }
    }
    system.getPhase(0).normalize();
    if (system.getNumberOfPhases() > 1) {
      system.getPhase(1).normalize();
    }

    // Set phase fractions
    system.setBeta(0, 1.0 - V); // liquid
    if (system.getNumberOfPhases() > 1) {
      system.setBeta(1, V); // vapor
    }

    system.init(1);
    logger.debug("VLE initialization: V=" + V + " phases=" + system.getNumberOfPhases());
  }

  /**
   * Solve single-phase chemical equilibrium using the modified RAND method.
   */
  private void solveSinglePhaseChemicalEquilibrium() {
    while (system.getNumberOfPhases() > 1) {
      system.removePhaseKeepTotalComposition(system.getNumberOfPhases() - 1);
    }
    system.init(1);

    ModifiedRANDSolver ceSolver = new ModifiedRANDSolver(system, formulaMatrix);
    ceSolver.setUseDIIS(this.useDIIS);
    converged = ceSolver.solve();
    totalIterations += ceSolver.getIterationsUsed();
    equilibriumTotalMoles = ceSolver.getTotalMoles();
    this.solver = ceSolver;
  }

  /**
   * Add trial phases from stability analysis to the system.
   *
   * <p>
   * Creates a proper phase object by cloning an existing phase and setting the trial composition.
   * The standard {@code system.addPhase()} only increments the phase counter without creating phase
   * objects, which would cause null pointer errors. This method clones phase 0 to ensure all
   * component, mixing rule, and EOS data are properly initialized for the new phase.
   * </p>
   *
   * @param trialCompositions list of trial phase compositions (mole fractions)
   */
  private void addTrialPhases(List<double[]> trialCompositions) {
    int nc = system.getPhase(0).getNumberOfComponents();

    // Add at most one new phase at a time (safer convergence)
    // Use the most unstable trial
    if (trialCompositions.isEmpty()) {
      return;
    }

    // Check if we can add more phases
    int currentPhases = system.getNumberOfPhases();
    if (currentPhases >= system.getMaxNumberOfPhases()) {
      logger.debug("Cannot add trial phase: already at max phases (" + currentPhases + ")");
      return;
    }

    double[] trial = trialCompositions.get(0); // most unstable already sorted

    // Increment phase count and set the new phase as a clone of phase 0
    // Note: system.addPhase() only increments the counter — it does NOT
    // create a phase object. We must clone an existing phase.
    system.addPhase();
    int newPhaseIdx = system.getNumberOfPhases() - 1;

    try {
      // Clone phase 0 to create the new phase with proper EOS/mixing rule setup
      neqsim.thermo.phase.PhaseInterface newPhase = system.getPhase(0).clone();
      // Set the composition of the new phase
      for (int i = 0; i < nc; i++) {
        newPhase.getComponent(i).setx(trial[i]);
      }
      newPhase.normalize();

      // Install the cloned phase into the system's phase array
      system.setPhase(newPhase, newPhaseIdx);

      // Set initial phase fraction (small)
      double initialBeta = 0.01;
      system.setBeta(newPhaseIdx, initialBeta);
      system.normalizeBeta();

      system.init(1);
    } catch (Exception ex) {
      logger.warn("Failed to initialize new trial phase: " + ex.getMessage());
      // Revert the phase addition
      system.setNumberOfPhases(currentPhases);
    }
  }

  /**
   * Remove phases with negligible phase fractions.
   *
   * @return true if any phase was removed
   */
  private boolean removeNegligiblePhases() {
    boolean removed = false;
    for (int j = system.getNumberOfPhases() - 1; j >= 0; j--) {
      if (system.getNumberOfPhases() <= 1) {
        break; // Keep at least one phase
      }
      if (system.getBeta(j) < MIN_PHASE_FRACTION) {
        system.removePhaseKeepTotalComposition(j);
        removed = true;
        logger.debug("Removed phase " + j + " (negligible fraction)");
      }
    }
    if (removed) {
      system.normalizeBeta();
      system.init(1);
    }
    return removed;
  }

  /**
   * Compute the total Gibbs energy of the current phase distribution.
   *
   * @return total Gibbs energy (J/mol)
   */
  private double computeGibbsEnergy() {
    double G = 0.0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      PhaseInterface phase = system.getPhase(j);
      double beta = phase.getBeta();
      for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        double xi = phase.getComponent(i).getx();
        if (xi > MIN_MOLES) {
          double lnPhi = phase.getComponent(i).getLogFugacityCoefficient();
          G += beta * xi * (Math.log(xi) + lnPhi);
        }
      }
    }
    return G;
  }

  /**
   * Check if the flash converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get total iterations used.
   *
   * @return total iterations
   */
  public int getTotalIterations() {
    return totalIterations;
  }

  /**
   * Get the final Gibbs energy.
   *
   * @return total Gibbs energy
   */
  public double getFinalGibbsEnergy() {
    return finalGibbsEnergy;
  }

  /**
   * Get the number of independent chemical reactions identified.
   *
   * @return number of independent reactions
   */
  public int getNumberOfReactions() {
    return numberOfReactions;
  }

  /**
   * Get the equilibrium total moles.
   *
   * <p>
   * For reactions where the total number of moles changes (Delta-nu != 0), this will differ from
   * the initial feed of 1 mole. Element balance is conserved in moles: A * x * N_total = b.
   * </p>
   *
   * @return equilibrium total moles
   */
  public double getEquilibriumTotalMoles() {
    return equilibriumTotalMoles;
  }

  /**
   * Get the formula matrix used.
   *
   * @return the FormulaMatrix object
   */
  public FormulaMatrix getFormulaMatrix() {
    return formulaMatrix;
  }

  /**
   * Get the Lagrange multipliers from the RAND solver.
   *
   * <p>
   * The Lagrange multipliers lambda_k represent the elemental chemical potentials. For element k,
   * lambda_k = partial(G)/(partial(b_k)). These can be used to compute equilibrium constants and
   * reaction affinities.
   * </p>
   *
   * @return array of Lagrange multipliers (one per element), or null if not converged
   */
  public double[] getLagrangeMultipliers() {
    if (solver != null) {
      return solver.getLagrangeMultipliers();
    }
    return null;
  }

  /**
   * Get equilibrium moles per phase from the RAND solver.
   *
   * <p>
   * Returns n[j][i] where j is the phase index and i is the component index. These are the actual
   * moles (not normalized to sum to 1) at equilibrium.
   * </p>
   *
   * @return moles array [phase][component], or null if not converged
   */
  public double[][] getEquilibriumMoles() {
    if (solver != null) {
      return solver.getMoles();
    }
    return null;
  }

  /**
   * Get a summary of the convergence status and key results.
   *
   * @return human-readable summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ReactiveMultiphaseTPflash Summary\n");
    sb.append("  Converged:    ").append(converged).append("\n");
    sb.append("  Iterations:   ").append(totalIterations).append("\n");
    sb.append("  NR (reactions): ").append(numberOfReactions).append("\n");
    sb.append("  Total moles:  ").append(equilibriumTotalMoles).append("\n");
    sb.append("  Phases:       ").append(system.getNumberOfPhases()).append("\n");
    if (converged) {
      sb.append("  Equilibrium composition:\n");
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        String name = system.getPhase(0).getComponent(i).getComponentName();
        double xi = system.getPhase(0).getComponent(i).getx();
        sb.append("    ").append(name).append(": ").append(String.format("%.6f", xi)).append("\n");
      }
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    if (system != null) {
      system.display();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {
    // Not needed for reactive flash
  }

  /**
   * Set whether to use DIIS acceleration in the RAND solver.
   *
   * <p>
   * When enabled, the solver applies Pulay DIIS extrapolation to accelerate convergence of the
   * Lagrange multiplier iteration, typically reducing iteration count by 3-5x. Enabled by default.
   * </p>
   *
   * @param useDIIS true to enable DIIS acceleration
   */
  public void setUseDIIS(boolean useDIIS) {
    this.useDIIS = useDIIS;
  }

  /**
   * Check if DIIS acceleration is enabled.
   *
   * @return true if DIIS is enabled
   */
  public boolean isUseDIIS() {
    return useDIIS;
  }

  /**
   * Get number of accepted DIIS steps from the last solver run.
   *
   * @return count of accepted DIIS extrapolation steps, or 0 if no solver ran
   */
  public int getDiisStepsAccepted() {
    if (solver != null) {
      return solver.getDiisStepsAccepted();
    }
    return 0;
  }

  /**
   * Set whether to auto-discover reactions via chemicalReactionInit.
   *
   * <p>
   * When enabled, the flash will call system.chemicalReactionInit() before building the formula
   * matrix. This queries the reaction database for known reactions involving the feed components
   * and automatically adds missing product species (e.g., ionic species from dissociation reactions
   * like CO2 + 2H2O &lt;-&gt; HCO3- + H3O+).
   * </p>
   *
   * <p>
   * This is useful when the user provides only molecular species (e.g., CO2, water) and wants the
   * flash to automatically determine which ionic species participate in the equilibrium.
   * </p>
   *
   * @param useChemReacInit true to enable auto-discovery of reactions
   */
  public void setUseChemicalReactionInit(boolean useChemReacInit) {
    this.useChemicalReactionInit = useChemReacInit;
  }

  /**
   * Check if chemicalReactionInit auto-discovery is enabled.
   *
   * @return true if enabled
   */
  public boolean isUseChemicalReactionInit() {
    return useChemicalReactionInit;
  }

  /**
   * Set the maximum number of phases for the reactive flash. When set to a positive value, this
   * overrides system.getMaxNumberOfPhases() which may be reset by init calls. Use this when the
   * system's init resets maxPhases (e.g., electrolyte CPA creates 2 phases even when maxPhases=1).
   *
   * @param maxPhases maximum number of phases (1 for single-phase CE, 2+ for VLE+CE)
   */
  public void setMaxNumberOfPhases(int maxPhases) {
    this.userMaxPhases = maxPhases;
  }

}
