package neqsim.thermodynamicoperations.flashops.reactiveflash;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Reactive stability analysis for simultaneous chemical and phase equilibrium.
 *
 * <p>
 * Implements tangent plane distance (TPD) analysis adapted for reactive systems. The approach
 * follows Ascani, Sadowski, Held (2023, Molecules 28, 1768):
 * <ol>
 * <li>First, a homogeneous chemical equilibrium (CE) is computed by minimizing Gibbs energy of the
 * single-phase feed subject to element balance constraints using the modified RAND method.</li>
 * <li>Then, standard (non-reactive) tangent plane stability analysis is performed on the chemically
 * equilibrated feed composition to check for additional phases.</li>
 * <li>For each new trial phase found to be unstable, a homogeneous CE is solved again to bring it
 * to the chemical equilibrium surface.</li>
 * </ol>
 *
 * <p>
 * The key rationale is that the tangent plane condition for reactive stability uses the element
 * mole-fraction space (reduced composition), but for practical purposes using the full component
 * TPD on the chemically equilibrated feed gives equivalent results (Ung and Doherty, 1995; Ascani
 * et al., 2023).
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>Ung, Doherty (1995) Chem. Eng. Sci. 50, 23-48</li>
 * <li>Ascani, Sadowski, Held (2023) Molecules 28, 1768</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * </ul>
 *
 * @author copilot
 * @version 1.0
 */
public class ReactiveStabilityAnalysis implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ReactiveStabilityAnalysis.class);

  /** Maximum iterations for stability analysis successive substitution. */
  private static final int MAX_SS_ITER = 100;

  /** Convergence tolerance for stability analysis. */
  private static final double SS_TOL = 1.0e-9;

  /** Tangent plane distance threshold for instability detection. */
  private static final double TPD_THRESHOLD = -1.0e-8;

  /** Minimum moles to avoid log(0). */
  private static final double MIN_MOLES = 1.0e-30;

  /** The thermodynamic system. */
  private SystemInterface system;

  /** The formula matrix for chemical reactions. */
  private FormulaMatrix formulaMatrix;

  /** Number of components. */
  private int nc;

  /** Reference chemical potentials d[i] = ln(z_i) + ln(phi_i) from equilibrated feed. */
  private double[] d;

  /** Trial compositions that were found unstable. */
  private List<double[]> unstableTrialCompositions;

  /** TPD values for each trial. */
  private List<Double> tpdValues;

  /**
   * Constructor.
   *
   * @param system the thermodynamic system at feed conditions
   * @param formulaMatrix the element-component formula matrix
   */
  public ReactiveStabilityAnalysis(SystemInterface system, FormulaMatrix formulaMatrix) {
    this.system = system;
    this.formulaMatrix = formulaMatrix;
    this.nc = system.getPhase(0).getNumberOfComponents();
    this.unstableTrialCompositions = new ArrayList<double[]>();
    this.tpdValues = new ArrayList<Double>();
  }

  /**
   * Perform reactive stability analysis.
   *
   * <p>
   * Steps:
   * <ol>
   * <li>Solve homogeneous chemical equilibrium on feed (modified RAND, single phase)</li>
   * <li>Compute reference fugacities d_i from equilibrated feed</li>
   * <li>Perform non-reactive TPD analysis using Wilson K-value and pure-component trial phases</li>
   * <li>Check if any trial gives TPD &lt; 0 (unstable)</li>
   * <li>For each unstable trial, solve homogeneous CE to find the equilibrated trial</li>
   * </ol>
   *
   * @return true if the system is unstable (a new phase should be added)
   */
  public boolean run() {
    unstableTrialCompositions.clear();
    tpdValues.clear();

    // Step 1: Solve homogeneous chemical equilibrium on the feed
    solveHomogeneousChemicalEquilibrium();

    // Step 2: Compute reference chemical potentials from equilibrated feed
    computeReferencePotentials();

    // Step 3: Generate trial phases using Wilson K-values
    List<double[]> trialPhases = generateTrialPhases();

    // Step 4: Run stability test for each trial
    for (double[] trial : trialPhases) {
      double tpd = runStabilityTrial(trial);
      if (tpd < TPD_THRESHOLD) {
        // Unstable - this trial composition represents a new phase
        double[] equilibratedTrial = solveTrialChemicalEquilibrium(trial);
        unstableTrialCompositions.add(equilibratedTrial);
        tpdValues.add(tpd);
        logger.debug("ReactiveStabilityAnalysis: found unstable trial, TPD = " + tpd);
      }
    }

    return !unstableTrialCompositions.isEmpty();
  }

  /**
   * Solve homogeneous (single-phase) chemical equilibrium on the feed.
   *
   * <p>
   * This brings the feed composition to the chemical equilibrium surface while remaining in a
   * single phase. Uses the ModifiedRANDSolver with a single phase.
   * </p>
   */
  private void solveHomogeneousChemicalEquilibrium() {
    // Only do this if we have chemical reactions (NR > 0)
    if (formulaMatrix.getNumberOfIndependentReactions() == 0) {
      return; // No reactions - feed is already at CE
    }

    // Create a single-phase system clone for homogeneous CE
    SystemInterface singlePhase = system.clone();
    // Ensure single phase
    while (singlePhase.getNumberOfPhases() > 1) {
      singlePhase.removePhaseKeepTotalComposition(singlePhase.getNumberOfPhases() - 1);
    }
    singlePhase.init(1);

    // Solve CE using modified RAND
    ModifiedRANDSolver ceSolver = new ModifiedRANDSolver(singlePhase, formulaMatrix);
    boolean ceConverged = ceSolver.solve();

    if (ceConverged) {
      // Update the feed composition in the main system with the CE result
      double[][] xCE = ceSolver.getMoleFractions();
      for (int i = 0; i < nc; i++) {
        system.getPhase(0).getComponent(i).setx(xCE[0][i]);
      }
      system.init(1);
      logger.debug("Homogeneous CE converged in " + ceSolver.getIterationsUsed() + " iterations");
    } else {
      logger.warn("Homogeneous CE did not converge, proceeding with original feed composition");
    }
  }

  /**
   * Compute reference chemical potentials d[i] = ln(x_i) + ln(phi_i) from the equilibrated feed
   * phase.
   */
  private void computeReferencePotentials() {
    d = new double[nc];
    PhaseInterface refPhase = system.getPhase(0);
    for (int i = 0; i < nc; i++) {
      double xi = refPhase.getComponent(i).getx();
      if (xi > MIN_MOLES) {
        d[i] = Math.log(xi) + refPhase.getComponent(i).getLogFugacityCoefficient();
      } else {
        d[i] = -100.0; // effectively absent
      }
      // Ions don't participate in phase equilibrium
      if (refPhase.getComponent(i).getIonicCharge() != 0) {
        d[i] = -1000.0;
      }
    }
  }

  /**
   * Generate trial phase compositions for stability analysis.
   *
   * <p>
   * Uses Wilson K-values for vapor-like and liquid-like trials, plus pure-component trials.
   * </p>
   *
   * @return list of trial compositions (unnormalized Wi values)
   */
  private List<double[]> generateTrialPhases() {
    List<double[]> trials = new ArrayList<double[]>();

    double T = system.getTemperature();
    double P = system.getPressure();

    // Wilson K-values
    double[] wilsonK = new double[nc];
    boolean allKnearOne = true;
    for (int i = 0; i < nc; i++) {
      double tc = system.getPhase(0).getComponent(i).getTC();
      double pc = system.getPhase(0).getComponent(i).getPC();
      double omega = system.getPhase(0).getComponent(i).getAcentricFactor();
      if (pc > 0 && tc > 0) {
        wilsonK[i] = (pc / P) * Math.exp(5.373 * (1.0 + omega) * (1.0 - tc / T));
        wilsonK[i] = Math.max(wilsonK[i], 1e-20);
      } else {
        wilsonK[i] = 1.0;
      }
      if (Math.abs(Math.log(wilsonK[i])) > 0.01) {
        allKnearOne = false;
      }
    }

    // Trial 1: Liquid-like (z/K) - enriches heavy components
    if (!allKnearOne) {
      double[] liquidTrial = new double[nc];
      for (int i = 0; i < nc; i++) {
        double zi = system.getPhase(0).getComponent(i).getx();
        liquidTrial[i] = zi > MIN_MOLES ? zi / wilsonK[i] : MIN_MOLES;
      }
      trials.add(liquidTrial);
    }

    // Trial 2: Vapor-like (K*z) - enriches light components
    if (!allKnearOne) {
      double[] vaporTrial = new double[nc];
      for (int i = 0; i < nc; i++) {
        double zi = system.getPhase(0).getComponent(i).getx();
        vaporTrial[i] = zi > MIN_MOLES ? wilsonK[i] * zi : MIN_MOLES;
      }
      trials.add(vaporTrial);
    }

    // Pure component trials
    for (int j = 0; j < nc; j++) {
      if (system.getPhase(0).getComponent(j).getx() > 1e-100
          && system.getPhase(0).getComponent(j).getIonicCharge() == 0) {
        double[] pureTrial = new double[nc];
        for (int i = 0; i < nc; i++) {
          pureTrial[i] = (i == j) ? 1.0 : 1e-12;
        }
        trials.add(pureTrial);
      }
    }

    return trials;
  }

  /**
   * Run a single stability trial using successive substitution on the tangent plane distance
   * function.
   *
   * <p>
   * The TPD at the stationary point is: TPD = 1 + sum_i W_i * (ln(W_i) + ln(phi_i(W)) - d_i - 1) If
   * TPD &lt; 0, the system is unstable with respect to formation of a phase with this composition.
   * </p>
   *
   * @param initialW initial trial composition (unnormalized)
   * @return TPD value at stationary point
   */
  private double runStabilityTrial(double[] initialW) {
    // Clone system for trial phase evaluation
    SystemInterface trialSystem = system.clone();
    while (trialSystem.getNumberOfPhases() > 1) {
      trialSystem.removePhaseKeepTotalComposition(trialSystem.getNumberOfPhases() - 1);
    }

    double[] logW = new double[nc];
    double[] oldLogW = new double[nc];

    // Initialize
    double sumW = 0.0;
    for (int i = 0; i < nc; i++) {
      double w = Math.max(initialW[i], MIN_MOLES);
      logW[i] = Math.log(w);
      sumW += w;
    }

    // Set trial composition (normalized) in the trial system
    for (int i = 0; i < nc; i++) {
      trialSystem.getPhase(0).getComponent(i).setx(Math.exp(logW[i]) / sumW);
    }

    // Successive substitution
    for (int iter = 0; iter < MAX_SS_ITER; iter++) {
      for (int i = 0; i < nc; i++) {
        oldLogW[i] = logW[i];
      }

      try {
        trialSystem.init(1);
      } catch (Exception ex) {
        logger.debug("Trial init failed: " + ex.getMessage());
        return 10.0; // return positive (stable)
      }

      double err = 0.0;
      sumW = 0.0;
      for (int i = 0; i < nc; i++) {
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
          logW[i] = -1000.0;
          continue;
        }
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          logW[i] = d[i] - trialSystem.getPhase(0).getComponent(i).getLogFugacityCoefficient();
        }
        err += Math.abs(logW[i] - oldLogW[i]);
        sumW += Math.exp(logW[i]);
      }

      // Update trial composition
      for (int i = 0; i < nc; i++) {
        double w = Math.exp(logW[i]);
        trialSystem.getPhase(0).getComponent(i).setx(w / sumW);
      }

      if (err < SS_TOL) {
        break;
      }
    }

    // Compute TPD = 1 - sum_i W_i
    double tpd = 1.0;
    for (int i = 0; i < nc; i++) {
      if (system.getPhase(0).getComponent(i).getIonicCharge() == 0
          && system.getPhase(0).getComponent(i).getx() > 1e-100) {
        tpd -= Math.exp(logW[i]);
      }
    }

    // Check for trivial solution (trial ≈ reference)
    double trivialCheck = 0.0;
    for (int i = 0; i < nc; i++) {
      trivialCheck += Math.abs(trialSystem.getPhase(0).getComponent(i).getx()
          - system.getPhase(0).getComponent(i).getx());
    }
    if (trivialCheck < 1e-4) {
      return 10.0; // trivial solution
    }

    return tpd;
  }

  /**
   * Solve homogeneous chemical equilibrium for a trial phase composition.
   *
   * <p>
   * After finding an unstable trial phase, this brings the trial composition to the chemical
   * equilibrium surface before using it as initial guess for the multiphase flash.
   * </p>
   *
   * @param trialW unnormalized trial composition
   * @return equilibrated composition (normalized mole fractions)
   */
  private double[] solveTrialChemicalEquilibrium(double[] trialW) {
    // Normalize trial
    double sumW = 0.0;
    for (int i = 0; i < nc; i++) {
      sumW += Math.max(trialW[i], MIN_MOLES);
    }
    double[] xTrial = new double[nc];
    for (int i = 0; i < nc; i++) {
      xTrial[i] = Math.max(trialW[i], MIN_MOLES) / sumW;
    }

    if (formulaMatrix.getNumberOfIndependentReactions() == 0) {
      return xTrial; // No reactions - no CE needed
    }

    // Create a single-phase clone with trial composition
    SystemInterface trialSystem = system.clone();
    while (trialSystem.getNumberOfPhases() > 1) {
      trialSystem.removePhaseKeepTotalComposition(trialSystem.getNumberOfPhases() - 1);
    }
    for (int i = 0; i < nc; i++) {
      trialSystem.getPhase(0).getComponent(i).setx(xTrial[i]);
    }
    trialSystem.init(1);

    // Solve CE with modified RAND
    ModifiedRANDSolver ceSolver = new ModifiedRANDSolver(trialSystem, formulaMatrix);
    boolean ceConverged = ceSolver.solve();

    if (ceConverged) {
      return ceSolver.getMoleFractions()[0];
    } else {
      logger.debug("Trial CE did not converge, using unequilibrated trial");
      return xTrial;
    }
  }

  /**
   * Get the unstable trial compositions found.
   *
   * @return list of trial compositions (normalized mole fractions)
   */
  public List<double[]> getUnstableTrialCompositions() {
    return unstableTrialCompositions;
  }

  /**
   * Get the TPD values for unstable trials.
   *
   * @return list of TPD values
   */
  public List<Double> getTpdValues() {
    return tpdValues;
  }

  /**
   * Check if the system was found unstable.
   *
   * @return true if at least one unstable trial was found
   */
  public boolean isUnstable() {
    return !unstableTrialCompositions.isEmpty();
  }

  /**
   * Get the number of unstable trials found.
   *
   * @return number of unstable trials
   */
  public int getNumberOfUnstableTrials() {
    return unstableTrialCompositions.size();
  }

  /**
   * Get the most unstable trial composition (most negative TPD).
   *
   * @return mole fractions of the most unstable trial, or null if stable
   */
  public double[] getMostUnstableTrial() {
    if (unstableTrialCompositions.isEmpty()) {
      return null;
    }
    int bestIdx = 0;
    double bestTpd = tpdValues.get(0);
    for (int i = 1; i < tpdValues.size(); i++) {
      if (tpdValues.get(i) < bestTpd) {
        bestTpd = tpdValues.get(i);
        bestIdx = i;
      }
    }
    return unstableTrialCompositions.get(bestIdx);
  }
}
