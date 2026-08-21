package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.EosGeFlashModel;
import neqsim.thermo.system.HybridEosGeFlashModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * TPflash class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash.class);
  /** Local lower-temperature seed step for the rare hydrocarbon endpoint continuation rescue. */
  private static final double MULTIPHASE_RESCUE_TEMPERATURE_STEP = 2.0;
  /** Lower sum(zK) bound for a general gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_K_LOWER_LIMIT = 0.95;
  /** Upper sum(zK) bound for a general gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT = 1.05;
  /** Lower sum(zK) bound for a screened asymmetric-mixture gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_ASYMMETRIC_SUM_Z_K_LOWER_LIMIT = 0.75;
  /** Lower sum(z/K) bound for a general gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_LOWER_LIMIT = 1.05;
  /** Lower sum(z/K) bound for a screened asymmetric-mixture gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_ASYMMETRIC_SUM_Z_OVER_K_LOWER_LIMIT = 0.95;
  /** Upper sum(z/K) bound for gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT = 2.0;
  /** Lower sum(zK) bound for legacy liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_LOWER_LIMIT = 0.95;
  /** Upper sum(zK) bound for legacy liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_UPPER_LIMIT = 1.20;
  /** Minimum sum(z/K) bound for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT = 5.0;
  /** Minimum log K spread for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT = 3.0;
  /** Minimum sum(zK) indicating a near-split liquid endpoint. */
  private static final double MULTIPHASE_RESCUE_LIQUID_NEAR_SPLIT_SUM_Z_K_LIMIT = 1.001;
  /** Minimum sum(z/K) indicating a near-split liquid endpoint. */
  private static final double MULTIPHASE_RESCUE_LIQUID_NEAR_SPLIT_SUM_Z_OVER_K_LIMIT = 0.995;
  /** Minimum feed fraction of non-hydrocarbon components for liquid-liquid refinement. */
  private static final double LIQUID_LIQUID_NON_HYDROCARBON_FRACTION_LIMIT = 0.20;
  /** Minimum active feed fraction used when screening components for liquid-liquid refinement. */
  private static final double LIQUID_LIQUID_ACTIVE_COMPONENT_LIMIT = 1.0e-6;
  /** Minimum critical-temperature span (K) for liquid-liquid refinement. */
  private static final double LIQUID_LIQUID_CRITICAL_TEMPERATURE_SPAN = 150.0;
  /** Minimum critical-temperature margin above the flash temperature for liquid-liquid refinement. */
  private static final double LIQUID_LIQUID_CRITICAL_TEMPERATURE_MARGIN = 115.0;
  /** Minimum critical-temperature margin above the flash temperature for a single-endpoint retry. */
  private static final double MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN = 80.0;
  /** Minimum gas-phase V/B ratio that justifies a second stability minimum search. */
  private static final double METASTABLE_GAS_VOLUME_OVER_B_LIMIT = 4.0;
  /** Maximum beta for replacing an invalid incipient sour-gas phase with a balanced endpoint. */
  private static final double INVALID_INCIPIENT_PHASE_FRACTION_LIMIT = 0.01;
  /** Minimum water feed fraction for ordinary water-rich endpoint refinement. */
  private static final double WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT = 0.01;
  /** Largest incipient secondary-phase fraction eligible for trace-water phase-selection retry. */
  private static final double TRACE_WATER_PHASE_SELECTION_BETA_LIMIT = 1.0e-4;
  /** Largest secondary-phase fraction eligible for the trace-water aqueous-stability trial. */
  private static final double TRACE_WATER_AQUEOUS_STABILITY_BETA_LIMIT = 1.0e-2;
  /** Minimum water enrichment in the hydrocarbon liquid for the aqueous-stability trial. */
  private static final double TRACE_WATER_AQUEOUS_STABILITY_ENRICHMENT_LIMIT = 10.0;
  /**
   * Minimum ratio of water fugacity to pure-water vapor pressure for a CPA single-phase aqueous-stability trial.
   *
   * <p>
   * The value is deliberately below unity so that the cheap screen remains conservative near aqueous phase appearance.
   * It only decides whether to run the tangent-plane trial; it never accepts a phase split.
   * </p>
   */
  private static final double CPA_WATER_SUPERSATURATION_SCREEN_LIMIT = 0.8;
  /** Relative Gibbs-energy tolerance for accepting a balanced incipient CPA aqueous phase. */
  private static final double CPA_AQUEOUS_GIBBS_RELATIVE_TOLERANCE = 1.0e-12;
  /** Absolute Gibbs-energy tolerance (J) for accepting a balanced incipient CPA aqueous phase. */
  private static final double CPA_AQUEOUS_GIBBS_ABSOLUTE_TOLERANCE_J = 1.0e-8;
  /** Maximum stored water K-value that justifies checking a collapsed water-bearing endpoint. */
  private static final double WATER_PHASE_COLLAPSE_WATER_K_UPPER_LIMIT = 1.0e-2;
  /** Minimum stored non-water K-value that justifies checking a collapsed water-bearing endpoint. */
  private static final double WATER_PHASE_COLLAPSE_VOLATILE_K_LOWER_LIMIT = 10.0;
  /** Maximum accepted component material-balance residual for water-rich endpoint refinement. */
  private static final double WATER_RICH_MATERIAL_BALANCE_TOLERANCE = 1.0e-8;
  /** Maximum accepted phase-composition normalization residual for an aqueous trial seed. */
  private static final double AQUEOUS_SEED_COMPOSITION_NORMALIZATION_TOLERANCE = 1.0e-8;
  /** Maximum accepted log-fugacity residual when selecting an alternate cubic root. */
  private static final double PHASE_ROOT_EQUILIBRIUM_TOLERANCE = 1.0e-8;
  /** Maximum absolute Z or composition change for recognizing an unchanged stable one-phase state. */
  private static final double UNCHANGED_SINGLE_PHASE_STATE_TOLERANCE = 1.0e-11;
  /** Maximum final SSI updates retained for legacy neutral endpoint repair. */
  private static final int MAX_FINAL_EQUILIBRIUM_REFINEMENT_ITERATIONS = 8;
  /** Largest residual eligible for legacy bounded final SSI refinement. */
  private static final double MAX_FINAL_EQUILIBRIUM_REFINEMENT_RESIDUAL = 1.0e-5;
  /** Maximum multiphase beta updates used to repair an invalid neutral two-phase endpoint. */
  private static final int MAX_FINAL_BETA_REFINEMENT_ITERATIONS = 20;
  /** Cubic phase roots evaluated by the post-convergence root checks. */
  private static final PhaseType[] CUBIC_ROOT_PHASE_TYPES = { PhaseType.GAS, PhaseType.LIQUID };
  /** Iteration limit for damped direct gamma-phi flashes near a phase-fraction boundary. */
  private static final int DIRECT_GAMMA_PHI_MAXIMUM_ITERATIONS = 500;
  /**
   * Minimum extensive Gibbs-energy reduction (J) required for the spurious-multiphase rescue to collapse a two-phase
   * result to a single phase. Avoids false triggers from numerical noise.
   */
  private static final double SPURIOUS_MULTIPHASE_GIBBS_DROP_THRESHOLD_J = 1.0;
  /**
   * Maximum per-component mole-fraction difference below which two converged phases are treated as identical, i.e. a
   * trivial (non-physical) split of the feed into two copies of itself. Genuine two-phase splits differ by far more
   * than this even when the incipient phase fraction is tiny.
   */
  private static final double TRIVIAL_SPLIT_COMPOSITION_TOLERANCE = 1.0e-6;
  /**
   * Maximum accepted change in ln(K) from one GDEM/DEM extrapolation.
   *
   * <p>
   * A bounded log-space step prevents a nearly unit dominant eigenvalue from turning a small successive-substitution
   * correction into an arbitrarily large K-value jump.
   * </p>
   */
  private static final double MAX_ACCELERATION_LOG_K_STEP = 2.0;
  /** Guard preventing recursive rescue attempts while the local seed flash is running. */
  private static final ThreadLocal<Boolean> MULTIPHASE_RESCUE_ACTIVE = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  SystemInterface clonedSystem;
  double presdiff = 1.0;
  private final RachfordRice rachfordRice = new RachfordRice();
  /**
   * Extensive single-phase Gibbs energy (J) at the overall feed composition, evaluated on the better cubic root (gas or
   * liquid) at the very beginning of {@link #runInternal()}. Used as a cheap reference value by
   * {@link #rescueSpuriousMultiphaseEndpoint()} to decide whether the converged multiphase result is metastable and
   * should be collapsed back to a single phase. NaN means the reference is not available (e.g. single-component or
   * single-phase systems).
   */
  private double referenceSinglePhaseGibbs = Double.NaN;
  /**
   * Phase type corresponding to {@link #referenceSinglePhaseGibbs} — the densest/most-stable cubic root identified at
   * the start of {@link #runInternal()}. Used as the collapse target when the spurious-multiphase rescue triggers.
   */
  private PhaseType referenceSinglePhaseType = null;
  /** True after the bounded water-bearing ordinary-flash retry has been attempted in this run. */
  private boolean waterBearingRescueAttempted = false;
  /** Cold initial state retained only for a screened asymmetric endpoint retry. */
  private transient SystemInterface multiphaseEndpointRescueSeed;
  /** Prevents a bounded water-rich cross-algorithm fallback from recursively starting another fallback. */
  private boolean waterRichCrossAlgorithmFallbackAllowed = true;
  /** Reusable rollback state for GDEM acceleration; transient because it contains no thermodynamic state. */
  private transient double[] accelerationSavedLnK;
  /** Reusable accelerated log K-values; transient because it contains no thermodynamic state. */
  private transient double[] accelerationLnK;
  /** Reusable accelerated K-values; transient because it contains no thermodynamic state. */
  private transient double[] accelerationK;
  /** Opt-in direct gamma-phi strategy, resolved once because a flash operation keeps one system. */
  private final EosGeFlashModel directGammaPhiModel;
  /** Opt-in hybrid multiphase strategy, resolved once because a flash operation keeps one system. */
  private final HybridEosGeFlashModel hybridEosGeFlashModel;

  /** Compact two-phase rollback snapshot used by bounded neutral endpoint refinements. */
  private static final class BalancedTwoPhaseState {
    private final PhaseType[] phaseTypes = new PhaseType[2];
    private final double[] betas = new double[2];
    private final double[][] compositions;
    private final double[] kValues;
    private final double gibbsEnergy;

    private BalancedTwoPhaseState(SystemInterface source) {
      int numberOfComponents = source.getPhase(0).getNumberOfComponents();
      compositions = new double[2][numberOfComponents];
      kValues = new double[numberOfComponents];
      gibbsEnergy = source.getGibbsEnergy();
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        phaseTypes[phaseIndex] = source.getPhase(phaseIndex).getType();
        betas[phaseIndex] = source.getBeta(phaseIndex);
        for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
          compositions[phaseIndex][componentIndex] = source.getPhase(phaseIndex).getComponent(componentIndex).getx();
        }
      }
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        kValues[componentIndex] = source.getPhase(0).getComponent(componentIndex).getK();
      }
    }
  }

  /**
   * Constructor for TPflash.
   */
  public TPflash() {
    directGammaPhiModel = null;
    hybridEosGeFlashModel = null;
  }

  /**
   * Constructor for TPflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPflash(SystemInterface system) {
    this.system = system;
    EosGeFlashModel eosGeModel = resolveEosGeFlashModel(system);
    directGammaPhiModel = eosGeModel != null && eosGeModel.requiresDirectGammaPhiFlash() ? eosGeModel : null;
    hybridEosGeFlashModel = eosGeModel instanceof HybridEosGeFlashModel
        && ((HybridEosGeFlashModel) eosGeModel).requiresHybridEosGeFlash() ? (HybridEosGeFlashModel) eosGeModel : null;
    lnOldOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldoldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
  }

  /**
   * Constructor for TPflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPflash(SystemInterface system, boolean checkForSolids) {
    this(system);
    solidCheck = checkForSolids;
  }

  /**
   * Get the opt-in direct gamma-phi strategy for the active system.
   *
   * @return direct EOS-GE strategy, or {@code null} for the ordinary TP-flash path
   */
  private EosGeFlashModel getDirectGammaPhiModel() {
    return directGammaPhiModel;
  }

  /**
   * Get the opt-in hybrid multiphase strategy for the active system.
   *
   * @return hybrid EOS-GE strategy, or {@code null} for the ordinary TP-flash path
   */
  private HybridEosGeFlashModel getHybridEosGeFlashModel() {
    return hybridEosGeFlashModel;
  }

  /**
   * Resolve the opt-in direct gamma-phi strategy once when the flash operation is created.
   *
   * @param flashSystem thermodynamic system owned by the operation
   * @return direct EOS-GE strategy, or {@code null} for an ordinary EOS system
   */
  private static EosGeFlashModel resolveEosGeFlashModel(SystemInterface flashSystem) {
    if (flashSystem instanceof EosGeFlashModel) {
      return (EosGeFlashModel) flashSystem;
    }
    return null;
  }

  /**
   * sucsSubs. Successive substitutions.
   */
  public void sucsSubs() {
    deviation = 0;
    neqsim.thermo.phase.PhaseInterface phase0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface phase1 = system.getPhase(1);
    int nc = phase0.getNumberOfComponents();

    for (i = 0; i < nc; i++) {
      neqsim.thermo.component.ComponentInterface comp0 = phase0.getComponent(i);
      neqsim.thermo.component.ComponentInterface comp1 = phase1.getComponent(i);
      if (comp0.getIonicCharge() != 0 || comp0.isIsIon()) {
        Kold = comp0.getK();
        comp0.setK(1.0e-40);
        comp1.setK(comp0.getK());
      } else {
        Kold = comp0.getK();
        double Knew = comp1.getFugacityCoefficient() / comp0.getFugacityCoefficient() * presdiff;
        comp0.setK(Knew);
        if (Double.isNaN(Knew)) {
          comp0.setK(Kold);
          system.init(1);
          Knew = comp0.getK();
        }
        comp1.setK(Knew);
        deviation += Math.abs(Math.log(Knew / Kold));
      }
    }

    double oldBeta = system.getBeta();

    try {

      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (IsNaNException ex) {
      logger.warn("Not able to calculate beta. Value is NaN");
      system.setBeta(oldBeta);
    } catch (TooManyIterationsException ex) {
      logger.warn("Not able to calculate beta, calculation is not converging.");
      system.setBeta(oldBeta);
    }
    if (system.getBeta() > 1.0 - phaseFractionMinimumLimit) {
      system.setBeta(1.0 - phaseFractionMinimumLimit);
    }
    if (system.getBeta() < phaseFractionMinimumLimit) {
      system.setBeta(phaseFractionMinimumLimit);
    }
    system.calc_x_y();
    system.init(1);
  }

  /**
   * Run one direct gamma-phi successive-substitution step for package-level numerical tests.
   *
   * @throws IllegalStateException if the system did not opt into direct gamma-phi iteration
   */
  void sucsSubsDirectGammaPhi() {
    EosGeFlashModel gammaPhiModel = getDirectGammaPhiModel();
    if (gammaPhiModel == null) {
      throw new IllegalStateException("The thermodynamic system did not opt into direct gamma-phi iteration.");
    }
    sucsSubsGammaPhi(gammaPhiModel);
  }

  /**
   * Dispatch a successive-substitution step without adding EOS-GE logic to the ordinary EOS component loop.
   *
   * @param gammaPhiModel direct EOS-GE strategy, or {@code null} for the ordinary EOS algorithm
   */
  private void sucsSubsForModel(EosGeFlashModel gammaPhiModel) {
    if (gammaPhiModel == null) {
      sucsSubs();
    } else {
      sucsSubsGammaPhi(gammaPhiModel);
    }
  }

  /**
   * Successive substitution for opt-in direct gamma-phi models.
   *
   * <p>
   * Keeping this model-specific component loop separate preserves the ordinary EOS hot path and avoids a strategy
   * branch for every EOS component update.
   * </p>
   *
   * @param gammaPhiModel direct EOS-GE strategy
   */
  private void sucsSubsGammaPhi(EosGeFlashModel gammaPhiModel) {
    deviation = 0;
    PhaseInterface phase0 = system.getPhase(0);
    PhaseInterface phase1 = system.getPhase(1);
    int numberOfComponents = phase0.getNumberOfComponents();

    for (i = 0; i < numberOfComponents; i++) {
      neqsim.thermo.component.ComponentInterface comp0 = phase0.getComponent(i);
      neqsim.thermo.component.ComponentInterface comp1 = phase1.getComponent(i);
      if (comp0.getIonicCharge() != 0 || comp0.isIsIon()) {
        Kold = comp0.getK();
        comp0.setK(1.0e-40);
        comp1.setK(comp0.getK());
      } else {
        Kold = comp0.getK();
        comp1.fugcoef(phase1);
        double vapourFugacityCoefficient = gammaPhiModel.getGammaPhiVapourFugacityCoefficient(comp0, phase0);
        double targetK = comp1.getFugacityCoefficient() / vapourFugacityCoefficient * presdiff;
        targetK = gammaPhiModel.constrainGammaPhiKValue(comp0, targetK);
        double Knew = gammaPhiModel.relaxGammaPhiKValue(Kold, targetK);
        comp0.setK(Knew);
        if (!Double.isFinite(Knew) || Knew <= 0.0) {
          comp0.setK(Kold);
          system.init(1);
          Knew = comp0.getK();
        }
        if (!Double.isFinite(Knew) || Knew <= 0.0) {
          Knew = Double.isFinite(Kold) && Kold > 0.0 ? Kold : 1.0;
          comp0.setK(Knew);
        }
        comp1.setK(Knew);
        double deviationReferenceK = Double.isFinite(Kold) && Kold > 0.0 ? Kold : 1.0;
        deviation += Math.abs(Math.log(Knew / deviationReferenceK));
      }
    }

    double oldBeta = system.getBeta();

    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (IsNaNException ex) {
      logger.warn("Not able to calculate beta. Value is NaN");
      system.setBeta(oldBeta);
    } catch (TooManyIterationsException ex) {
      logger.warn("Not able to calculate beta, calculation is not converging.");
      system.setBeta(oldBeta);
    }
    if (system.getBeta() > 1.0 - phaseFractionMinimumLimit) {
      system.setBeta(1.0 - phaseFractionMinimumLimit);
    }
    if (system.getBeta() < phaseFractionMinimumLimit) {
      system.setBeta(phaseFractionMinimumLimit);
    }
    system.calc_x_y();
    system.init(1);
  }

  /**
   * accselerateSucsSubs. GDEM with 2-eigenvalue acceleration when sufficient history is available, falling back to
   * standard DEM (Michelsen 1982b, Risnes et al. 1981). The GDEM formulation follows Risnes &amp; Dalen (1984) and
   * Michelsen &amp; Mollerup (2007, section 9.5).
   */
  public void accselerateSucsSubs() {
    int nc = system.getPhase(0).getNumberOfComponents();
    ensureAccelerationWorkspace(nc);

    // Save pre-acceleration lnK state for rollback on failure
    System.arraycopy(lnK, 0, accelerationSavedLnK, 0, nc);

    // Compute dot products for both standard DEM and GDEM-2
    double b11 = 0.0;
    double b12 = 0.0;
    double b22 = 0.0;
    double c1 = 0.0;
    double c2 = 0.0;
    for (i = 0; i < nc; i++) {
      b11 += oldDeltalnK[i] * oldDeltalnK[i];
      b12 += oldDeltalnK[i] * oldoldDeltalnK[i];
      b22 += oldoldDeltalnK[i] * oldoldDeltalnK[i];
      c1 += deltalnK[i] * oldDeltalnK[i];
      c2 += deltalnK[i] * oldoldDeltalnK[i];
    }

    // Standard DEM eigenvalue estimate
    double lambda = (b22 > 1e-30) ? b12 / b22 : 0.0;

    // Try GDEM-2: solve 2x2 system for mu1, mu2
    double det = b11 * b22 - b12 * b12;
    boolean useGDEM = false;
    double mu1 = 0.0;
    double mu2 = 0.0;
    if (Math.abs(det) > 1e-30 * (b11 * b22 + 1e-100)) {
      mu1 = (c1 * b22 - c2 * b12) / det;
      mu2 = (b11 * c2 - b12 * c1) / det;
      // Use GDEM-2 only when eigenvalue estimates indicate smooth, contractive convergence
      useGDEM = mu1 > 0 && mu2 > 0 && mu1 < 1.5 && mu2 < 1.5;
    }

    boolean safeAcceleration = true;
    if (!useGDEM) {
      if (!Double.isFinite(lambda) || Math.abs(1.0 - lambda) < 1.0e-12) {
        safeAcceleration = false;
      } else {
        double lambdaFactor = lambda / (1.0 - lambda);
        if (!Double.isFinite(lambdaFactor)) {
          safeAcceleration = false;
        }
        for (i = 0; i < nc; i++) {
          accelerationLnK[i] = lnK[i] + lambdaFactor * deltalnK[i];
        }
      }
    } else {
      for (i = 0; i < nc; i++) {
        accelerationLnK[i] = lnK[i] + mu1 * deltalnK[i] + mu2 * oldDeltalnK[i];
      }
    }

    if (safeAcceleration) {
      for (i = 0; i < nc; i++) {
        double logKStep = accelerationLnK[i] - accelerationSavedLnK[i];
        if (!Double.isFinite(accelerationLnK[i]) || Math.abs(logKStep) > MAX_ACCELERATION_LOG_K_STEP) {
          safeAcceleration = false;
          break;
        }
        accelerationK[i] = Math.exp(accelerationLnK[i]);
        if (!Double.isFinite(accelerationK[i]) || accelerationK[i] <= 0.0) {
          safeAcceleration = false;
          break;
        }
      }
    }

    if (!safeAcceleration) {
      sucsSubs();
      return;
    }

    neqsim.thermo.phase.PhaseInterface ph0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface ph1 = system.getPhase(1);
    for (i = 0; i < nc; i++) {
      lnK[i] = accelerationLnK[i];
      ph0.getComponent(i).setK(accelerationK[i]);
      ph1.getComponent(i).setK(accelerationK[i]);
    }
    double oldBeta = system.getBeta();
    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (Exception ex) {
      system.setBeta(rachfordRice.getBeta()[0]);
      if (system.getBeta() > 1.0 - phaseFractionMinimumLimit || system.getBeta() < phaseFractionMinimumLimit) {
        system.setBeta(oldBeta);
      }
      logger.error(ex.getMessage(), ex);
    }
    system.calc_x_y();
    try {
      system.init(1);
    } catch (Exception initEx) {
      // GDEM extrapolation produced bad compositions - restore pre-acceleration state
      logger.debug("accselerateSucsSubs init failed, reverting: {}", initEx.getMessage());
      System.arraycopy(accelerationSavedLnK, 0, lnK, 0, nc);
      for (i = 0; i < nc; i++) {
        double expK = Math.exp(accelerationSavedLnK[i]);
        ph0.getComponent(i).setK(expK);
        ph1.getComponent(i).setK(expK);
      }
      system.setBeta(oldBeta);
      system.calc_x_y();
      system.init(1);
    }
  }

  /**
   * Ensures that GDEM acceleration has component-sized reusable work arrays.
   *
   * <p>
   * The arrays are allocated lazily so deserialized and default-constructed flash operations remain supported. They are
   * resized defensively if a caller replaces the underlying system before reusing the operation.
   * </p>
   *
   * @param numberOfComponents active component count
   */
  private void ensureAccelerationWorkspace(int numberOfComponents) {
    if (accelerationSavedLnK == null || accelerationSavedLnK.length != numberOfComponents) {
      accelerationSavedLnK = new double[numberOfComponents];
      accelerationLnK = new double[numberOfComponents];
      accelerationK = new double[numberOfComponents];
    }
  }

  /**
   * setNewK.
   */
  public void setNewK() {
    neqsim.thermo.phase.PhaseInterface phase0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface phase1 = system.getPhase(1);
    int nc = phase0.getNumberOfComponents();
    for (i = 0; i < nc; i++) {
      lnOldOldOldK[i] = lnOldOldK[i];
      lnOldOldK[i] = lnOldK[i];
      lnOldK[i] = lnK[i];
      lnK[i] = Math
          .log(phase1.getComponent(i).getFugacityCoefficient() / phase0.getComponent(i).getFugacityCoefficient());

      oldoldDeltalnK[i] = lnOldOldK[i] - lnOldOldOldK[i];
      oldDeltalnK[i] = lnOldK[i] - lnOldOldK[i];
      deltalnK[i] = lnK[i] - lnOldK[i];
    }
  }

  /**
   * resetK.
   */
  public void resetK() {
    neqsim.thermo.phase.PhaseInterface phase0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface phase1 = system.getPhase(1);
    int nc = phase0.getNumberOfComponents();
    for (i = 0; i < nc; i++) {
      lnK[i] = lnOldK[i];
      double expK = Math.exp(lnK[i]);
      phase0.getComponent(i).setK(expK);
      phase1.getComponent(i).setK(expK);
    }
    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
      system.calc_x_y();
      system.init(1);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculate the following properties:
   * </p>
   * <ul>
   * <li>minimumGibbsEnergy</li>
   * <li>minGibsPhaseLogZ</li>
   * <li>minGibsLogFugCoef</li>
   * <li>presdiff</li>
   * <li>Component K properties for all phases if required</li>
   * </ul>
   */
  @Override
  public void run() {
    if (system.isForcePhaseTypes() && system.getMaxNumberOfPhases() == 1) {
      system.setNumberOfPhases(1);
      return;
    }

    // Warm-start safety: if the previous flash converged to 3+ phases, the
    // K-values stored on phase[0]/phase[1] only describe the gas ↔ HC-liquid
    // split and are blind to the aqueous (or second liquid) phase. Using them
    // as initial guesses in the 2-phase loop below gives a poor restart for
    // components that distributed mostly to the 3rd phase (water, glycols,
    // methanol in CPA / electrolyte systems). Force Wilson K for this single
    // TPflash call in that case; warm-start remains enabled for the normal
    // 2-phase recycle-loop path where it is both correct and fast.
    final boolean prevWarmStart = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    final boolean disableWarmStart = prevWarmStart && system.getNumberOfPhases() > 2;
    if (disableWarmStart) {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
    }
    try {
      runInternal();
    } finally {
      multiphaseEndpointRescueSeed = null;
      if (disableWarmStart) {
        neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarmStart);
      }
    }
  }

  /**
   * Internal flash body; the public {@link #run()} wraps this with a warm-start guard for systems carrying a stale
   * 3-phase state.
   */
  private void runInternal() {
    resetStabilityDiagnostics();
    waterBearingRescueAttempted = false;
    HybridEosGeFlashModel hybridModel = getHybridEosGeFlashModel();
    if (hybridModel != null) {
      system.init(0);
      new TPHybridEosGeFlash(system, hybridModel).run();
      return;
    }
    EosGeFlashModel gammaPhiModel = getDirectGammaPhiModel();
    if (gammaPhiModel != null && (solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck())) {
      throw new UnsupportedOperationException(
          "Direct EOS-GE TPflash does not support solid or wax phase calculations; disable solid and wax checks.");
    }
    findLowestGibbsPhaseIsChecked = false;
    int minGibbsPhase = 0;
    double minimumGibbsEnergy = 0;

    BalancedTwoPhaseState balancedWaterRichInput = balancedWaterRichInputBeforeOrdinaryIteration();
    system.init(0);
    prepareMultiphaseEndpointRescueSeed();
    if (gammaPhiModel != null) {
      gammaPhiModel.prepareGammaPhiFlash();
    }
    system.init(1);

    if ((system.getPhase(0).getGibbsEnergy() * (1.0 - Math.signum(system.getPhase(0).getGibbsEnergy()) * 1e-8)) < system
        .getPhase(1).getGibbsEnergy()) {
      minGibbsPhase = 0;
    } else {
      minGibbsPhase = 1;
    }
    // logger.debug("minimum gibbs phase " + minGibbsPhase);
    minimumGibbsEnergy = system.getPhase(minGibbsPhase).getGibbsEnergy();
    // Cache the better single-phase Gibbs and its phase type for the post-convergence
    // spurious-multiphase acceptance check at the end of runInternal(). This is the
    // extensive Gibbs at the overall feed composition for the densest cubic root,
    // and is the correct reference for deciding whether the converged multiphase split
    // is metastable. Capturing it here avoids cloning + re-initialising the system later.
    referenceSinglePhaseGibbs = minimumGibbsEnergy;
    referenceSinglePhaseType = system.getPhase(minGibbsPhase).getType();

    if (system.getPhase(0).getNumberOfComponents() == 1 || system.getMaxNumberOfPhases() == 1) {
      system.setNumberOfPhases(1);
      if (minGibbsPhase == 0) {
        system.setPhaseIndex(0, 0);
      } else {
        system.setPhaseIndex(0, 1);
      }
      // Solve chemical equilibrium for single-phase chemical systems
      if (system.isChemicalSystem()) {
        system.getChemicalReactionOperations().solveChemEq(0, 0);
        system.getChemicalReactionOperations().solveChemEq(0, 1);
      }
      if (solidCheck) {
        ThermodynamicOperations operation = new ThermodynamicOperations(system);
        operation.TPSolidflash();
      }
      return;
    }

    minGibsPhaseLogZ = new double[system.getPhase(0).getNumberOfComponents()];
    minGibsLogFugCoef = new double[system.getPhase(0).getNumberOfComponents()];

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(minGibbsPhase).getComponent(i).getz() > 1e-50) {
        minGibsPhaseLogZ[i] = Math.log(system.getPhase(minGibbsPhase).getComponent(i).getz());
      }
      minGibsLogFugCoef[i] = system.getPhase(minGibbsPhase).getComponent(i).getLogFugacityCoefficient();
    }

    presdiff = system.getPhase(1).getPressure() / system.getPhase(0).getPressure();
    if (Math.abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) > 1e-12) {
      for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        system.getPhase(0).getComponent(i).setK(system.getPhase(0).getComponent(i).getK() * presdiff);
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
      }
    }

    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(1, 0);
      system.getChemicalReactionOperations().solveChemEq(1, 1);
    }

    // Calculates phase fractions and initial composition based on Wilson K-factors
    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    system.calc_x_y();
    system.init(1);
    // If phase fraction using Wilson K factor returns pure gas or pure liquid, we
    // try with another K value guess based on calculated fugacities.
    // This solves some problems when we have high volumes of water and heavy
    // hydrocarbons returning only one liquid phase (and this phase desolves all
    // gas)
    if (system.getBeta() > (1.0 - 1.1 * phaseFractionMinimumLimit)
        || system.getBeta() < (1.1 * phaseFractionMinimumLimit)) {
      system.setBeta(0.5);
      sucsSubsForModel(gammaPhiModel);
    }

    // Performs three iterations of successive substitution
    for (int k = 0; k < 3; k++) {
      if (system.getBeta() < (1.0 - 1.1 * phaseFractionMinimumLimit)
          && system.getBeta() > (1.1 * phaseFractionMinimumLimit)) {
        sucsSubsForModel(gammaPhiModel);
        if ((system.getGibbsEnergy() - minimumGibbsEnergy) / Math.abs(minimumGibbsEnergy) < -1e-12) {
          break;
        }
      }
    }

    // System.out.println("beta " + system.getBeta());
    int totiter = 0;
    double tpdx = 1.0;
    double tpdy = 1.0;
    double dgonRT = 1.0;
    boolean passedTests = false;
    if (system.getBeta() > (1.0 - 1.1 * phaseFractionMinimumLimit)
        || system.getBeta() < (1.1 * phaseFractionMinimumLimit)) {
      tpdx = 1.0;
      tpdy = 1.0;
      dgonRT = 1.0;
    } else if (system.getGibbsEnergy() < (minimumGibbsEnergy * (1.0 - 1.0e-12))) {
      tpdx = -1.0;
      tpdy = -1.0;
      dgonRT = -1.0;
    } else {
      for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        // Skip ions in TPD calculation - they don't participate in VLE
        if (system.getPhase(0).getComponent(i).getK() < 1e-30) {
          continue;
        }
        if (system.getComponent(i).getz() > 1e-50) {
          tpdy += system.getPhase(0).getComponent(i).getx()
              * (Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient())
                  + Math.log(system.getPhase(0).getComponent(i).getx()) - minGibsPhaseLogZ[i] - minGibsLogFugCoef[i]);
          tpdx += system.getPhase(1).getComponent(i).getx()
              * (Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
                  + Math.log(system.getPhase(1).getComponent(i).getx()) - minGibsPhaseLogZ[i] - minGibsLogFugCoef[i]);
        }
      }

      dgonRT = system.getPhase(0).getBeta() * tpdy + (1.0 - system.getPhase(0).getBeta()) * tpdx;
      if (dgonRT > 0) {
        if (tpdx < 0) {
          for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            // Preserve ion K-values - they don't participate in VLE
            if (system.getPhase(0).getComponent(i).getK() < 1e-30) {
              continue;
            }
            system.getPhase(0).getComponent(i).setK(
                Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()) - minGibsLogFugCoef[i])
                    * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else if (tpdy < 0) {
          for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            // Preserve ion K-values - they don't participate in VLE
            if (system.getPhase(0).getComponent(i).getK() < 1e-30) {
              continue;
            }
            system.getPhase(0).getComponent(i).setK(
                Math.exp(minGibsLogFugCoef[i] - Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()))
                    * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else {
          passedTests = true;
        }
      }
    }

    if (gammaPhiModel == null
        && (passedTests || (dgonRT > 0 && tpdx > 0 && tpdy > 0) || Double.isNaN(system.getBeta()))) {
      boolean isStable;
      try {
        if (system.checkStability()) {
          isStable = stabilityCheck();
        } else {
          recordStabilityOutcome("skipped by system setting");
          isStable = false;
        }
      } catch (Exception ex) {
        logger.debug("Stability check failed, continuing TPflash iteration: {}", ex.getMessage());
        recordStabilityAnalysisFailure(ex);
        recordStabilityOutcome("stability check failed - continuing TPflash iteration");
        isStable = false;
      }
      if (isStable) {
        PhaseType stableSinglePhaseType = null;
        double stableSinglePhaseZ = Double.NaN;
        double[] stableSinglePhaseComposition = null;
        if (system.doMultiPhaseCheck() && !system.isChemicalSystem() && system.getNumberOfPhases() == 1) {
          stableSinglePhaseType = system.getPhase(0).getType();
          stableSinglePhaseZ = system.getPhase(0).getZ();
          stableSinglePhaseComposition = new double[system.getPhase(0).getNumberOfComponents()];
          for (int componentIndex = 0; componentIndex < stableSinglePhaseComposition.length; componentIndex++) {
            stableSinglePhaseComposition[componentIndex] = system.getPhase(0).getComponent(componentIndex).getx();
          }
        }
        if (system.doMultiPhaseCheck()) {
          // logger.info("one phase flash is stable - checking multiphase flash....");
          TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
          operation.run();
          rescueSinglePhaseWaterBearingEndpoint();
          rescueSinglePhaseMultiphaseEndpointLegacy();
          rescueSinglePhaseMultiphaseEndpoint();
        }
        if (solidCheck) {
          this.solidPhaseFlash();
        }
        if (system.isMultiphaseWaxCheck()) {
          TPmultiflashWAX operation = new TPmultiflashWAX(system, true);
          operation.run();
        }

        system.orderByDensity();
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.debug("Post-stability init failed: {}", ex.getMessage());
        }
        rescueSinglePhaseWaterBearingEndpoint();
        rescueSinglePhaseMultiphaseEndpointLegacy();
        rescueSinglePhaseMultiphaseEndpoint();
        rejectUnnormalizedAqueousEndpointAfterStableSinglePhase();

        // TPmultiflash owns the coupled phase/reaction solve for multiphase chemical systems.
        // Solve chemistry here only when no multiphase calculation was requested.
        if (system.isChemicalSystem() && !system.doMultiPhaseCheck()) {
          for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
            String phaseType = system.getPhase(phaseNum).getPhaseTypeName();
            if ("aqueous".equalsIgnoreCase(phaseType) || "liquid".equalsIgnoreCase(phaseType)) {
              system.getChemicalReactionOperations().solveChemEq(phaseNum, 0);
              system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);
            }
          }
          system.init(1);
        }
        // The multiphase flash above is run precisely because the single-phase stability test may
        // miss a genuine second phase, so a real split found here must be preserved. Only remove a
        // trivial split (two phases with essentially identical composition) — a non-physical
        // solution of the flash equations. The Gibbs-based spurious rescue is intentionally NOT
        // applied on this path, as it can discard a genuine split the stability test overlooked.
        collapseTrivialMultiphaseSplit();
        rescueLowerGibbsPhaseRoot();
        rescueLowerGibbsHydrocarbonPhaseRoots();
        rescueLiquidLiquidEndpointLegacy();
        rescueLowerGibbsNeutralEndpoint();
        rescueWaterRichEndpoint();
        rescueLowerGibbsMultiphaseAqueousRoot();
        refineInvalidAqueousTwoPhaseEndpoint();
        refineInvalidNeutralGasLiquidTwoPhaseEndpointLegacy();
        refineInvalidNeutralTwoPhaseEndpoint();
        normalizeUnchangedStableSinglePhaseEndpoint(stableSinglePhaseType, stableSinglePhaseZ,
            stableSinglePhaseComposition);
        rescueSinglePhaseMultiphaseEndpoint();
        normalizeSourGasSinglePhaseEndpoint();
        return;
      }
    }

    setNewK();

    gibbsEnergy = system.getGibbsEnergy();
    gibbsEnergyOld = gibbsEnergy;

    PhaseType originalPhaseType0 = system.getPhase(0).getType();
    double gasgib = 0.0;
    double liqgib = 0.0;
    if (gammaPhiModel == null) {
      // Checks if gas or oil is the most stable phase for the ordinary EOS flash.
      gasgib = system.getPhase(0).getGibbsEnergy();
      system.setPhaseType(0, PhaseType.LIQUID);
      system.init(1, 0);
      liqgib = system.getPhase(0).getGibbsEnergy();

      if (gasgib * (1.0 - Math.signum(gasgib) * 1e-8) < liqgib) {
        system.setPhaseType(0, PhaseType.GAS);
      } else {
        system.setPhaseType(0, PhaseType.LIQUID);
      }

      if (system.doMultiPhaseCheck() && originalPhaseType0 == PhaseType.OIL) {
        system.setPhaseType(0, PhaseType.OIL);
      }
    } else {
      // Direct gamma-phi iteration owns a fixed EOS-vapour/GE-liquid topology. Trying a liquid
      // cubic root here changes the vapour reference used to form K = phi_liquid / phi_vapour.
      system.setPhaseType(0, originalPhaseType0);
    }
    system.init(1);

    // Reduced acceleration interval for faster convergence
    int accelerateInterval = 5;
    int newtonLimit = 12;
    int timeFromLastGibbsFail = 0;
    int iterationLimit = gammaPhiModel == null ? maxNumberOfIterations
        : Math.max(maxNumberOfIterations, DIRECT_GAMMA_PHI_MAXIMUM_ITERATIONS);

    double chemdev = 0;
    double oldChemDiff = 1.0;
    double diffChem = 1.0;
    do {
      iterations = 0;
      do {
        iterations++;

        int activeNewtonLimit = newtonLimit;
        int activeAccelerateInterval = accelerateInterval;
        if (shouldApplyEnhancedMultiPhaseCheck()) {
          if (deviation < 5e-4) {
            activeNewtonLimit = 8;
            activeAccelerateInterval = 3;
          } else if (deviation < 5e-3) {
            activeNewtonLimit = 10;
            activeAccelerateInterval = 4;
          }
          if (system.getBeta() < 5.0 * phaseFractionMinimumLimit
              || system.getBeta() > 1.0 - 5.0 * phaseFractionMinimumLimit) {
            activeNewtonLimit = Math.max(activeNewtonLimit, 14);
          }
        }

        // A direct gamma-phi model owns its K-value update, damping and vapour reference. The
        // generic cubic-EOS Newton solver does not implement that contract and must not replace it.
        if (iterations < activeNewtonLimit || gammaPhiModel != null || system.isChemicalSystem()
            || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (timeFromLastGibbsFail > 6 && (iterations % activeAccelerateInterval) == 0
              && !(system.isChemicalSystem() || system.doSolidPhaseCheck()) && gammaPhiModel == null) {
            accselerateSucsSubs();
          } else {
            sucsSubsForModel(gammaPhiModel);
          }
        } else if (iterations >= activeNewtonLimit && (!shouldApplyEnhancedMultiPhaseCheck() || deviation < 0.05)
            && Math.abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) < 1e-5) {
          // Recreate the second-order solver only when needed: never created yet, or the
          // component count changed (e.g. solid precipitation removed a component, or the
          // flash instance is being reused on a different system). Avoids re-allocating
          // Jacobian / EJML buffers every time iterations reaches the Newton trigger.
          if (secondOrderSolver == null
              || secondOrderSolver.getNumberOfComponents() != system.getPhases()[0].getNumberOfComponents()) {
            secondOrderSolver = new SysNewtonRhapsonTPflash(system, 2, system.getPhases()[0].getNumberOfComponents());
          } else {
            secondOrderSolver.setSystem(system);
          }
          try {
            deviation = secondOrderSolver.solve();
          } catch (Exception ex) {
            sucsSubs();
          }
        } else {
          sucsSubs();
        }

        gibbsEnergyOld = gibbsEnergy;
        gibbsEnergy = system.getGibbsEnergy();

        if (((gibbsEnergy - gibbsEnergyOld) / Math.abs(gibbsEnergyOld) > 1e-8
            || system.getBeta() < phaseFractionMinimumLimit * 1.01
            || system.getBeta() > (1 - phaseFractionMinimumLimit * 1.01)) && !system.isChemicalSystem()
            && gammaPhiModel == null && timeFromLastGibbsFail > 1) {
          resetK();
          timeFromLastGibbsFail = 0;
        } else {
          timeFromLastGibbsFail++;
          setNewK();
        }
      } while ((deviation > 1e-10) && (iterations < iterationLimit));

      if (system.isChemicalSystem()) {
        oldChemDiff = chemdev;
        chemdev = 0.0;

        double[] xchem = new double[system.getPhase(0).getNumberOfComponents()];

        for (int phaseNum = 1; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          for (i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            xchem[i] = system.getPhase(phaseNum).getComponent(i).getx();
          }

          system.init(1);
          system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);

          for (i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            chemdev += Math.abs(xchem[i] - system.getPhase(phaseNum).getComponent(i).getx()) / xchem[i];
          }
        }
        diffChem = Math.abs(oldChemDiff - chemdev);
      }
      // logger.info("chemdev: " + chemdev + " iter: " + totiter);
      totiter++;
    } while ((diffChem > 1e-6 && chemdev > 1e-6 && totiter < 300) || (system.isChemicalSystem() && totiter < 2));
    if (system.isChemicalSystem()) {
      sucsSubs();
    }
    if (gammaPhiModel != null) {
      if (gammaPhiModel.finishGammaPhiFlash(deviation, phaseFractionMinimumLimit)) {
        return;
      }
      throw new IllegalStateException("Direct EOS-GE TPflash did not produce an acceptable state: "
          + gammaPhiModel.getGammaPhiFlashDiagnostics(deviation, phaseFractionMinimumLimit));
    }
    if (system.doMultiPhaseCheck()) {
      BalancedTwoPhaseState balancedReference = balancedReferenceBeforeMultiphaseCheck();
      TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
      operation.run();
      restoreBalancedAqueousReferenceAfterInvalidPhaseRemoval(balancedReference);
      restoreLowerGibbsReferenceAfterSinglePhaseCollapse(balancedReference);
      rescueSinglePhaseWaterBearingEndpoint();
      rescueSinglePhaseMultiphaseEndpointLegacy();
      rescueSinglePhaseMultiphaseEndpoint();
      // rescueSpuriousMultiphaseEndpoint() is called once at the end of runInternal()
      // after orderByDensity(), so it is intentionally not repeated here.
    } else {
      try {
        // Checks if gas or oil is the most stable phase
        if (system.getPhase(0).getType() == PhaseType.GAS) {
          gasgib = system.getPhase(0).getGibbsEnergy();
          system.setPhaseType(0, PhaseType.LIQUID);

          system.init(1, 0);
          liqgib = system.getPhase(0).getGibbsEnergy();
        } else {
          liqgib = system.getPhase(0).getGibbsEnergy();
          system.setPhaseType(0, PhaseType.GAS);
          system.init(1, 0);
          gasgib = system.getPhase(0).getGibbsEnergy();
        }
        if (gasgib * (1.0 - Math.signum(gasgib) * 1e-8) < liqgib) {
          system.setPhaseType(0, PhaseType.GAS);
        } else {
          system.setPhaseType(0, PhaseType.LIQUID);
        }
      } catch (Exception e) {
        system.setPhaseType(0, PhaseType.GAS);
      }

      system.init(1);
    }

    if (solidCheck) {
      this.solidPhaseFlash();
    }
    if (system.isMultiphaseWaxCheck()) {
      TPmultiflashWAX operation = new TPmultiflashWAX(system, true);
      operation.run();
    }

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getBeta(i) < phaseFractionMinimumLimit * 1.01) {
        system.removePhase(i);
        i--; // indices shift after removal — re-check the (new) phase at i
      }
    }
    rescueSinglePhaseWaterBearingEndpoint();
    rescueSinglePhaseMultiphaseEndpointLegacy();
    rescueSinglePhaseMultiphaseEndpoint();
    system.orderByDensity();
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("Final init after orderByDensity failed: " + ex.getMessage());
    }
    rescueSinglePhaseWaterBearingEndpoint();
    rescueSinglePhaseMultiphaseEndpointLegacy();
    rescueSinglePhaseMultiphaseEndpoint();
    rescueSpuriousMultiphaseEndpoint();
    rescueSinglePhaseWaterBearingEndpoint();
    collapseTrivialMultiphaseSplit();
    normalizeActivePhaseFractions();
    rescueLowerGibbsPhaseRoot();
    rescueLowerGibbsHydrocarbonPhaseRoots();
    rescueLiquidLiquidEndpointLegacy();
    rescueWaterRichEndpoint();
    rescueLowerGibbsMultiphaseAqueousRoot();
    rescueLowerGibbsPhaseRoot();
    refineInvalidAqueousTwoPhaseEndpoint();
    refineInvalidNeutralGasLiquidTwoPhaseEndpointLegacy();
    refineInvalidNeutralTwoPhaseEndpoint();
    rescueLowerGibbsNeutralEndpoint();
    rescueSinglePhaseMultiphaseEndpoint();
    restoreBalancedAqueousReferenceAfterInvalidPhaseRemoval(balancedWaterRichInput);
    restoreLowerGibbsReferenceAfterSinglePhaseCollapse(balancedWaterRichInput);
    normalizeSourGasSinglePhaseEndpoint();

    // TPmultiflash already finalized coupled chemistry on a multiphase configuration. For an
    // ordinary single-topology calculation, solve chemistry after all phase reordering here.
    if (system.isChemicalSystem() && !system.doMultiPhaseCheck()) {
      for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
        String phaseType = system.getPhase(phaseNum).getPhaseTypeName();
        if ("aqueous".equalsIgnoreCase(phaseType) || "liquid".equalsIgnoreCase(phaseType)) {
          system.getChemicalReactionOperations().solveChemEq(phaseNum, 0);
          system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);
        }
      }
      try {
        system.init(1);
      } catch (Exception ex) {
        logger.warn("Final chemical eq init failed: " + ex.getMessage());
      }
    }
  }

  /**
   * Restores beta closure when a multiphase check returns the unchanged stable one-phase state.
   *
   * <p>
   * {@link TPmultiflash} is also used for internal trial states, so normalizing every multiphase return can perturb
   * continuation and chemical-equilibrium calculations. This narrowly handles the no-new-phase result: phase type,
   * compressibility factor, and every phase composition must match the stable state captured before the multiphase
   * check. Only its stale phase fraction is then normalized.
   * </p>
   *
   * @param stablePhaseType phase type before the multiphase check
   * @param stableZ compressibility factor before the multiphase check
   * @param stableComposition phase composition before the multiphase check
   */
  private void normalizeUnchangedStableSinglePhaseEndpoint(PhaseType stablePhaseType, double stableZ,
      double[] stableComposition) {
    double currentZ = system.getNumberOfPhases() == 1 ? system.getPhase(0).getZ() : Double.NaN;
    if (stablePhaseType == null || stableComposition == null || system.getNumberOfPhases() != 1
        || system.getPhase(0).getType() != stablePhaseType || !Double.isFinite(stableZ) || !Double.isFinite(currentZ)
        || Math.abs(currentZ - stableZ) > UNCHANGED_SINGLE_PHASE_STATE_TOLERANCE
        || system.getPhase(0).getNumberOfComponents() != stableComposition.length) {
      return;
    }
    for (int componentIndex = 0; componentIndex < stableComposition.length; componentIndex++) {
      double currentComposition = system.getPhase(0).getComponent(componentIndex).getx();
      if (!Double.isFinite(stableComposition[componentIndex]) || !Double.isFinite(currentComposition) || Math
          .abs(currentComposition - stableComposition[componentIndex]) > UNCHANGED_SINGLE_PHASE_STATE_TOLERANCE) {
        return;
      }
    }
    normalizeActivePhaseFractions();
  }

  /**
   * Refines an ordinary liquid-only endpoint with the multiphase stability solver.
   *
   * <p>
   * The ordinary two-phase flash primarily searches for a vapor-liquid split. For a liquid-only endpoint it can
   * therefore converge to a local single-liquid state or to a metastable liquid-liquid split even though Michelsen
   * tangent-plane stability analysis finds a lower-Gibbs liquid-liquid equilibrium. A cloned candidate is evaluated
   * with multiphase checking enabled and replaces the ordinary result only when the existing strict phase-fraction,
   * distinct-composition, and Gibbs-energy acceptance checks pass.
   * </p>
   *
   * <p>
   * The guard deliberately excludes multi-liquid endpoints, any endpoint containing a gas or aqueous phase, chemical
   * and electrolyte systems, and solid/wax calculations. Thus ordinary gas, gas-liquid, and established liquid-liquid
   * process flashes remain on the existing fast path without an additional flash or property initialization.
   * </p>
   */
  private void rescueLiquidLiquidEndpointLegacy() {
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() != 1 || system.isChemicalSystem() || system.hasIons()
        || solidCheck || system.isMultiphaseWaxCheck()) {
      return;
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      if (!(phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID)) {
        return;
      }
    }

    if (!hasPotentialLiquidLiquidInstabilityLegacy()) {
      return;
    }

    double referenceGibbsEnergy = system.getGibbsEnergy();
    SystemInterface candidate = system.clone();
    try {
      candidate.setMultiPhaseCheck(true);
      candidate.setNumberOfPhases(2);
      candidate.setPhaseIndex(0, 0);
      candidate.setPhaseIndex(1, 1);
      candidate.setPhaseType(0, PhaseType.GAS);
      candidate.setPhaseType(1, PhaseType.OIL);
      candidate.setBeta(0, 0.5);
      candidate.setBeta(1, 0.5);
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        for (int componentIndex = 0; componentIndex < candidate.getPhase(phaseIndex)
            .getNumberOfComponents(); componentIndex++) {
          candidate.getPhase(phaseIndex).getComponent(componentIndex)
              .setx(candidate.getPhase(phaseIndex).getComponent(componentIndex).getz());
        }
        candidate.getPhase(phaseIndex).normalize();
      }
      new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      if (isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy)) {
        copyFlashStateFrom(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Liquid-liquid endpoint refinement failed: {}", ex.getMessage());
    }
  }

  /**
   * Refines a guarded ordinary endpoint with the multiphase stability solver.
   *
   * <p>
   * The ordinary two-phase flash can converge to a local gas/liquid or liquid-only stationary point even though
   * Michelsen tangent-plane stability analysis finds a lower-Gibbs two-phase equilibrium. A cheap feed-composition
   * screen limits the extra stability flash to strongly asymmetric neutral mixtures with substantial non-hydrocarbon
   * content at temperatures where liquid-liquid instability is plausible.
   * </p>
   *
   * <p>
   * The accepted candidate must contain exactly two neutral fluid phases, close material balance and fugacity equality,
   * and lower Gibbs energy. Chemical/electrolyte, aqueous, solid, wax, and compositions outside the instability screen
   * remain on the existing fast path. A screened single-phase sour-gas endpoint is normalized to the feed before its
   * Gibbs energy is compared, because an incipient phase composition is not a valid one-phase reference state.
   * </p>
   */
  private void rescueLowerGibbsNeutralEndpoint() {
    if (!isSourGasConsistencyRefinementCase()) {
      return;
    }
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() < 1 || system.getNumberOfPhases() > 2
        || system.isChemicalSystem() || system.hasIons() || solidCheck || system.isMultiphaseWaxCheck()
        || system.hasPhaseType(PhaseType.AQUEOUS)) {
      return;
    }
    boolean hasGasPhase = false;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      if (phaseType != PhaseType.GAS && phaseType != PhaseType.OIL && phaseType != PhaseType.LIQUID) {
        return;
      }
      hasGasPhase |= phaseType == PhaseType.GAS;
    }
    if (system.getNumberOfPhases() == 1) {
      if (!hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN)
          || !hasPotentialMultiphaseEndpoint(system.getPhase(0).getType())) {
        return;
      }
    } else {
      if (!hasGasPhase || !hasPotentialLiquidLiquidInstability() || !hasPotentialCompetingNeutralMinimum()) {
        return;
      }
    }

    if (system.getNumberOfPhases() == 1) {
      normalizeSourGasSinglePhaseEndpoint();
    } else {
      system.init(1);
    }
    double referenceGibbsEnergy = system.getGibbsEnergy();
    boolean hasColdSeed = multiphaseEndpointRescueSeed != null;
    SystemInterface candidate = hasColdSeed ? multiphaseEndpointRescueSeed : system.clone();
    multiphaseEndpointRescueSeed = null;
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      candidate.setMultiPhaseCheck(true);
      candidate.setEnhancedMultiPhaseCheck(false);
      if (hasColdSeed && system.getNumberOfPhases() == 1) {
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      } else if (system.getNumberOfPhases() == 2 && hasGasPhase) {
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      } else {
        new TPmultiflash(candidate, candidate.doSolidPhaseCheck()).run();
      }
      boolean accepted = isNeutralFluidTwoPhaseCandidate(candidate) && isBalancedEquilibriumCandidate(candidate)
          && isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy);
      if (!accepted && system.getNumberOfPhases() == 2 && hasGasPhase) {
        candidate = system.clone();
        resetNeutralCandidateToFeed(candidate);
        candidate.setMultiPhaseCheck(true);
        candidate.setEnhancedMultiPhaseCheck(false);
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
        accepted = isNeutralFluidTwoPhaseCandidate(candidate) && isBalancedEquilibriumCandidate(candidate)
            && isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy);
      }
      if (!accepted && system.getNumberOfPhases() == 1) {
        candidate = system.clone();
        resetNeutralCandidateToFeed(candidate);
        candidate.setMultiPhaseCheck(true);
        candidate.setEnhancedMultiPhaseCheck(false);
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
        accepted = isNeutralFluidTwoPhaseCandidate(candidate) && isBalancedEquilibriumCandidate(candidate)
            && isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy);
      }
      if (accepted) {
        copyNeutralFlashStatePreservingRoots(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Neutral endpoint stability refinement failed: {}", ex.getMessage());
    } finally {
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Screens an already-balanced gas/liquid split for a competing liquid-like minimum.
   *
   * <p>
   * Michelsen stability analysis is valuable here only when the ordinary flash has selected a very dilute vapor root
   * far from the liquid root. The inexpensive V/B separation check avoids repeating a complete stability-seeded flash
   * for the common, already-converged gas/liquid result. It is only a performance screen; the candidate stability,
   * material-balance, fugacity, and Gibbs checks remain authoritative.
   * </p>
   *
   * @return true when a second stability minimum is plausible
   */
  private boolean hasPotentialCompetingNeutralMinimum() {
    if (system.getNumberOfPhases() != 2) {
      return false;
    }
    double gasVolumeOverB = Double.NaN;
    double liquidVolumeOverB = Double.NaN;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      double volumeOverB = phase.getVolume() / phase.getB();
      if (!Double.isFinite(volumeOverB)) {
        return true;
      }
      if (phase.getType() == PhaseType.GAS) {
        gasVolumeOverB = volumeOverB;
      } else if (phase.getType() == PhaseType.OIL || phase.getType() == PhaseType.LIQUID) {
        liquidVolumeOverB = volumeOverB;
      }
    }
    return Double.isFinite(gasVolumeOverB) && Double.isFinite(liquidVolumeOverB)
        && gasVolumeOverB > METASTABLE_GAS_VOLUME_OVER_B_LIMIT && liquidVolumeOverB < 1.75;
  }

  /**
   * Checks that a liquid-endpoint refinement retained exactly two neutral liquid-like phases.
   *
   * @param candidate candidate returned by the multiphase stability path
   * @return true when the candidate is an oil/liquid two-phase state without gas or aqueous phases
   */
  private boolean isNeutralFluidTwoPhaseCandidate(SystemInterface candidate) {
    return candidate.getNumberOfPhases() == 2 && isNeutralFluidCandidate(candidate);
  }

  /**
   * Checks whether a reciprocal candidate contains only one or two neutral fluid phases.
   *
   * @param candidate candidate returned by the reciprocal flash path
   * @return true when every active phase is gas/oil/liquid and no aqueous phase is present
   */
  private boolean isNeutralFluidCandidate(SystemInterface candidate) {
    if (candidate.getNumberOfPhases() < 1 || candidate.getNumberOfPhases() > 2
        || candidate.hasPhaseType(PhaseType.AQUEOUS)) {
      return false;
    }
    for (int phaseIndex = 0; phaseIndex < candidate.getNumberOfPhases(); phaseIndex++) {
      PhaseType phaseType = candidate.getPhase(phaseIndex).getType();
      if (phaseType != PhaseType.GAS && phaseType != PhaseType.OIL && phaseType != PhaseType.LIQUID) {
        return false;
      }
    }
    return true;
  }

  /**
   * Refines an invalid water-rich endpoint with the alternate flash path.
   *
   * <p>
   * The ordinary flash searches only the cubic gas/oil roots and can therefore leave a substantial water fraction
   * dissolved in a hydrocarbon-labelled phase even when a lower-Gibbs aqueous split exists. Conversely, a multiphase
   * phase-appearance trial can retain an invalid higher-Gibbs aqueous endpoint after its active phase storage changes.
   * An existing aqueous phase label is not by itself proof of equilibrium. Such an endpoint is refined when its
   * component fugacity residual exceeds {@link #PHASE_ROOT_EQUILIBRIUM_TOLERANCE} or its component material balance
   * exceeds {@link #WATER_RICH_MATERIAL_BALANCE_TOLERANCE}. The one-mol-percent feed guard keeps valid trace-water
   * process flashes outside the minor-phase trace-water screen on the existing fast path. A trace-water gas/oil
   * endpoint whose fugacity residual is already outside the equilibrium tolerance may still use the cloned stability
   * calculation because it is not an acceptable result. A cheap aqueous tangent-plane trial and safeguarded multiphase
   * beta solve are used for trace-water gas/oil endpoints whose small hydrocarbon liquid disproportionately
   * concentrates water. Full recursive flashing is avoided. A multiphase-enabled water-rich gas/aqueous endpoint uses
   * one cold ordinary candidate; a genuine oil/aqueous liquid-liquid endpoint remains on the multiphase path. A
   * water-rich multiphase endpoint that collapsed to one hydrocarbon phase also uses a cold ordinary candidate, whose
   * invalid two-phase cubic-root split may seed the multiphase solver. An ordinary neutral non-CPA water-rich
   * asymmetric feed retains its pre-iteration state for this reciprocal calculation; cloning the final endpoint can
   * retain the collapsed phase/root history and miss the cold phase set. For an ordinary endpoint, an existing invalid
   * two-phase split is retained as the multiphase phase-set seed when the existing cold candidate is rejected. Trying
   * the cold candidate first preserves its gas/oil cubic-root classification whenever it already reaches the same
   * feasible equilibrium. The nested candidates cannot start a reciprocal fallback cycle. A candidate replaces the
   * original state only after strict phase-fraction, composition-normalization, material-balance, fugacity,
   * distinct-composition, and lower-Gibbs checks pass. A collapsed multiphase endpoint additionally requires the
   * candidate to restore the missing aqueous phase, keeping ordinary gas appearance outside this fallback's scope.
   * </p>
   */
  private void rescueWaterRichEndpoint() {
    if (!waterRichCrossAlgorithmFallbackAllowed || system.isChemicalSystem() || system.hasIons() || solidCheck
        || system.isMultiphaseWaxCheck() || system.getNumberOfPhases() > 2) {
      return;
    }

    boolean hasAqueousPhase = system.hasPhaseType(PhaseType.AQUEOUS);
    double waterFeedFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        waterFeedFraction = component.getz();
        break;
      }
    }
    boolean singlePhaseCpaAqueousEndpoint = shouldRefineSinglePhaseCpaAqueousEndpoint(waterFeedFraction);
    if (hasAqueousPhase && system.getNumberOfPhases() < 2 && !singlePhaseCpaAqueousEndpoint) {
      return;
    }
    if (waterFeedFraction < WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
        && (waterFeedFraction <= 0.0 || !shouldRefineTraceWaterAqueousEndpoint(waterFeedFraction))) {
      return;
    }
    boolean gasAqueousMultiphaseEndpoint = system.doMultiPhaseCheck() && hasAqueousPhase
        && system.hasPhaseType(PhaseType.GAS);
    boolean singlePhaseWaterRichMultiphaseEndpoint = system.doMultiPhaseCheck() && system.getNumberOfPhases() == 1
        && !hasAqueousPhase;
    if (system.doMultiPhaseCheck() && waterFeedFraction >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
        && !gasAqueousMultiphaseEndpoint && !singlePhaseWaterRichMultiphaseEndpoint) {
      return;
    }
    double materialBalanceResidual = maximumComponentMaterialBalanceResidual(system);
    boolean materialBalanceInvalid = !Double.isFinite(materialBalanceResidual)
        || materialBalanceResidual > WATER_RICH_MATERIAL_BALANCE_TOLERANCE;
    if (hasAqueousPhase && !singlePhaseCpaAqueousEndpoint && !materialBalanceInvalid
        && maximumLogFugacityResidualWithReplacement(0, system.getPhase(0)) < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
      return;
    }

    double referenceGibbsEnergy = system.getGibbsEnergy();
    boolean invalidOrdinaryTwoPhaseSeed = !system.doMultiPhaseCheck() && !hasAqueousPhase
        && system.getNumberOfPhases() == 2 && !isBalancedEquilibriumCandidate(system);
    boolean ordinaryFallback = (gasAqueousMultiphaseEndpoint || singlePhaseWaterRichMultiphaseEndpoint)
        && waterFeedFraction >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT;
    SystemInterface candidate;
    if (!system.doMultiPhaseCheck() && multiphaseEndpointRescueSeed != null) {
      candidate = multiphaseEndpointRescueSeed;
      multiphaseEndpointRescueSeed = null;
    } else if (ordinaryFallback) {
      double totalMoles = system.getTotalNumberOfMoles();
      double[] feedComposition = system.getzvector();
      candidate = system.phaseToSystem(0);
      candidate.setTotalNumberOfMoles(totalMoles);
      candidate.setMolarComposition(feedComposition);
      candidate.setNumberOfPhases(2);
      candidate.setPhaseIndex(0, 0);
      candidate.setPhaseIndex(1, 1);
      candidate.setPhaseType(0, PhaseType.GAS);
      candidate.setPhaseType(1, PhaseType.OIL);
    } else {
      candidate = system.clone();
    }
    try {
      candidate.setMultiPhaseCheck(!system.doMultiPhaseCheck());
      boolean candidateConverged;
      if (waterFeedFraction < WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT) {
        candidateConverged = refineTraceWaterAqueousCandidateActiveSet(candidate);
      } else {
        TPflash candidateFlash = new TPflash(candidate, candidate.doSolidPhaseCheck());
        candidateFlash.waterRichCrossAlgorithmFallbackAllowed = singlePhaseWaterRichMultiphaseEndpoint;
        candidateFlash.run();
        candidateConverged = true;
      }
      boolean incipientCpaAqueousTrial = system.getNumberOfPhases() == 1 && !system.doMultiPhaseCheck()
          && system.getModelName() != null && system.getModelName().contains("CPA");
      boolean restoresCollapsedAqueousPhase = !singlePhaseWaterRichMultiphaseEndpoint
          || candidate.hasPhaseType(PhaseType.AQUEOUS);
      if (candidateConverged && restoresCollapsedAqueousPhase && candidate.getNumberOfPhases() == 2
          && isBalancedEquilibriumCandidate(candidate) && shouldAcceptWaterRichCandidate(candidate,
              referenceGibbsEnergy, materialBalanceInvalid, incipientCpaAqueousTrial)) {
        if (gasAqueousMultiphaseEndpoint) {
          runAcceptedOrdinaryWaterRichFallback(candidate);
        } else {
          copyFlashStateFrom(candidate);
        }
        return;
      }
    } catch (Exception ex) {
      logger.debug("Water-rich endpoint refinement failed: {}", ex.getMessage());
    }
    if (invalidOrdinaryTwoPhaseSeed) {
      trySeededWaterRichPhaseSet(referenceGibbsEnergy, materialBalanceInvalid);
    }
  }

  /**
   * Screens a single-phase CPA aqueous endpoint for a missed hydrocarbon-liquid phase.
   *
   * <p>
   * A substantial water feed can make the ordinary vapor-liquid stability trial select the aqueous minimum and never
   * test the competing hydrocarbon-liquid minimum. The screen is restricted to an ordinary, neutral CPA aqueous
   * endpoint with at least one substantial hydrocarbon whose critical temperature remains well above the flash
   * temperature. Water fugacity must also be near pure-water saturation. These checks use only the converged endpoint
   * and immutable component data; the subsequent multiphase stability calculation and strict balance, fugacity, phase
   * fraction, distinct-composition, and lower-Gibbs gates remain authoritative.
   * </p>
   *
   * @param waterFeedFraction overall water mole fraction
   * @return true when a guarded multiphase stability refinement is justified
   */
  private boolean shouldRefineSinglePhaseCpaAqueousEndpoint(double waterFeedFraction) {
    String modelName = system.getModelName();
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() != 1 || !system.hasPhaseType(PhaseType.AQUEOUS)
        || modelName == null || !modelName.contains("CPA")
        || waterFeedFraction < WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
        || !isCpaWaterNearSaturation(CPA_WATER_SUPERSATURATION_SCREEN_LIMIT)) {
      return false;
    }
    double condensableHydrocarbonFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      double feedFraction = component.getz();
      if (feedFraction <= LIQUID_LIQUID_ACTIVE_COMPONENT_LIMIT || !component.isHydrocarbon()) {
        continue;
      }
      if (component.getTC() > system.getTemperature() + MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN) {
        condensableHydrocarbonFraction += feedFraction;
      }
    }
    return condensableHydrocarbonFraction >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT;
  }

  /**
   * Refines an invalid ordinary two-phase water-rich endpoint from its converged phase-set seed.
   *
   * <p>
   * A cold multiphase calculation can collapse before reaching the aqueous split, while the ordinary flash has already
   * produced two distinct compositions that provide a useful stability seed. The fully initialized multiphase solver is
   * therefore run on a clone of that split after the existing cold candidate is rejected. Rejected, three-phase,
   * unbalanced, non-equilibrium, or higher-Gibbs trials leave the original endpoint untouched.
   * </p>
   *
   * @param referenceGibbsEnergy Gibbs energy of the invalid ordinary endpoint
   * @param referenceMaterialBalanceInvalid whether the reference endpoint fails component material balance
   * @return true when a strict lower-Gibbs two-phase candidate replaced the endpoint
   */
  private boolean trySeededWaterRichPhaseSet(double referenceGibbsEnergy, boolean referenceMaterialBalanceInvalid) {
    SystemInterface candidate = system.clone();
    try {
      candidate.setMultiPhaseCheck(true);
      new TPmultiflash(candidate, candidate.doSolidPhaseCheck()).run();
      candidate.init(1);
      if (candidate.getNumberOfPhases() == 2 && isBalancedEquilibriumCandidate(candidate)
          && shouldAcceptWaterRichCandidate(candidate, referenceGibbsEnergy, referenceMaterialBalanceInvalid, false)) {
        copyFlashStateFrom(candidate);
        return true;
      }
    } catch (Exception ex) {
      logger.debug("Seeded water-rich phase-set refinement failed: {}", ex.getMessage());
    }
    return false;
  }

  /**
   * Screens a trace-water gas/oil endpoint for a missed lower-Gibbs aqueous split.
   *
   * @param waterFeedFraction overall water mole fraction
   * @return true when the endpoint is already invalid, its CPA water fugacity is near pure-water saturation, or its
   * phase fraction and water enrichment justify an aqueous stability trial
   */
  private boolean shouldRefineTraceWaterAqueousEndpoint(double waterFeedFraction) {
    if (isInvalidTraceWaterGasOilEndpoint()) {
      return true;
    }
    if (!system.doMultiPhaseCheck() && system.getNumberOfPhases() == 1) {
      String modelName = system.getModelName();
      return modelName != null && modelName.contains("CPA")
          && isCpaWaterNearSaturation(CPA_WATER_SUPERSATURATION_SCREEN_LIMIT);
    }
    if (system.getNumberOfPhases() != 2 || system.hasPhaseType(PhaseType.AQUEOUS)
        || Math.min(system.getBeta(0), system.getBeta(1)) > TRACE_WATER_AQUEOUS_STABILITY_BETA_LIMIT) {
      return false;
    }
    boolean hasGasPhase = false;
    int hydrocarbonLiquidPhase = -1;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      hasGasPhase |= phaseType == PhaseType.GAS;
      if (phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID) {
        hydrocarbonLiquidPhase = phaseIndex;
      }
    }
    if (!hasGasPhase || hydrocarbonLiquidPhase < 0) {
      return false;
    }
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      if ("water".equalsIgnoreCase(system.getPhase(0).getComponent(componentIndex).getComponentName())) {
        double liquidWaterFraction = system.getPhase(hydrocarbonLiquidPhase).getComponent(componentIndex).getx();
        return liquidWaterFraction >= TRACE_WATER_AQUEOUS_STABILITY_ENRICHMENT_LIMIT * waterFeedFraction;
      }
    }
    return false;
  }

  /**
   * Compares water fugacity in the current CPA phase with pure-water vapor pressure.
   *
   * <p>
   * This is a performance screen, not a stability criterion. The subsequent tangent-plane-distance calculation and
   * strict candidate acceptance checks decide whether an aqueous phase is stable.
   * </p>
   *
   * @param minimumRatio minimum fugacity-to-vapor-pressure ratio that triggers a stability trial
   * @return true when a finite water supersaturation ratio reaches the specified limit
   */
  private boolean isCpaWaterNearSaturation(double minimumRatio) {
    PhaseInterface phase = system.getPhase(0);
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      if (!"water".equalsIgnoreCase(component.getComponentName())) {
        continue;
      }
      double vaporPressure = component.getAntoineVaporPressure(system.getTemperature());
      double waterFugacity = component.getx() * component.getFugacityCoefficient() * system.getPressure();
      double ratio = waterFugacity / vaporPressure;
      return Double.isFinite(ratio) && ratio >= minimumRatio;
    }
    return false;
  }

  /**
   * Runs the existing tangent-plane trial and reconverges the reduced active phase set.
   *
   * <p>
   * The first multiphase beta solve identifies a disappearing phase. Removing only a phase at the solver's numerical
   * phase-fraction floor and rebuilding the beta workspace turns the trial into a well-conditioned two-phase solve. If
   * the solver returns two duplicate phases instead, exactly one duplicate pair may be merged before the active set is
   * rebuilt. The caller performs the final thermodynamic acceptance checks.
   * </p>
   *
   * @param candidate cloned gas/oil endpoint
   * @return true when exactly one disappearing or duplicate phase was removed and the two remaining phases converged
   */
  private boolean refineTraceWaterAqueousCandidateActiveSet(SystemInterface candidate) {
    int initialPhaseCount = candidate.getNumberOfPhases();
    TPmultiflash operation = new TPmultiflash(candidate, false);
    operation.stabilityAnalysis();
    if (candidate.getNumberOfPhases() <= initialPhaseCount) {
      return false;
    }
    operation.setDoubleArrays();
    operation.solveBeta();
    if (initialPhaseCount == 1 && candidate.getNumberOfPhases() == 2) {
      candidate.normalizeBeta();
      operation.setDoubleArrays();
      double activeSetResidual = operation.solveBeta();
      candidate.init(1);
      return Double.isFinite(activeSetResidual) && activeSetResidual < 1.0e-10;
    }
    int removedPhaseCount = 0;
    for (int phaseIndex = candidate.getNumberOfPhases() - 1; phaseIndex >= 0; phaseIndex--) {
      if (candidate.getNumberOfPhases() > 1 && candidate.getBeta(phaseIndex) <= 10.0 * phaseFractionMinimumLimit) {
        candidate.removePhaseKeepTotalComposition(phaseIndex);
        removedPhaseCount++;
      }
    }
    if (removedPhaseCount == 0 && candidate.getNumberOfPhases() == 3) {
      for (int firstPhase = 0; firstPhase < candidate.getNumberOfPhases() - 1; firstPhase++) {
        for (int secondPhase = firstPhase + 1; secondPhase < candidate.getNumberOfPhases(); secondPhase++) {
          double maximumCompositionDifference = 0.0;
          for (int componentIndex = 0; componentIndex < candidate.getPhase(0)
              .getNumberOfComponents(); componentIndex++) {
            maximumCompositionDifference = Math.max(maximumCompositionDifference,
                Math.abs(candidate.getPhase(firstPhase).getComponent(componentIndex).getx()
                    - candidate.getPhase(secondPhase).getComponent(componentIndex).getx()));
          }
          if (maximumCompositionDifference <= TRIVIAL_SPLIT_COMPOSITION_TOLERANCE) {
            double mergedBeta = candidate.getBeta(firstPhase) + candidate.getBeta(secondPhase);
            candidate.removePhaseKeepTotalComposition(secondPhase);
            candidate.setBeta(firstPhase, mergedBeta);
            removedPhaseCount++;
            break;
          }
        }
        if (removedPhaseCount > 0) {
          break;
        }
      }
    }
    if (removedPhaseCount != 1 || candidate.getNumberOfPhases() != 2) {
      return false;
    }
    candidate.normalizeBeta();
    operation.setDoubleArrays();
    double activeSetResidual = operation.solveBeta();
    candidate.init(1);
    return Double.isFinite(activeSetResidual) && activeSetResidual < 1.0e-10;
  }

  /**
   * Checks whether an ordinary trace-water gas/oil endpoint already fails fugacity equality.
   *
   * <p>
   * Valid trace-water flashes with established phase fractions return before a component residual scan. For an
   * incipient secondary phase, a valid residual returns without another initialization or stability calculation. Only
   * an invalid neutral gas/oil endpoint is reinitialized to confirm the residual before the guarded lower-Gibbs aqueous
   * candidate is evaluated.
   * </p>
   *
   * @return true when the endpoint is a neutral gas/oil split whose confirmed fugacity residual is non-finite or not
   * below the equilibrium tolerance
   */
  private boolean isInvalidTraceWaterGasOilEndpoint() {
    if (system.getNumberOfPhases() != 2 || system.hasPhaseType(PhaseType.AQUEOUS)) {
      return false;
    }
    boolean hasGasPhase = false;
    boolean hasLiquidPhase = false;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      hasGasPhase |= phaseType == PhaseType.GAS;
      hasLiquidPhase |= phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID;
    }
    if (!hasGasPhase || !hasLiquidPhase) {
      return false;
    }
    if (Math.min(system.getBeta(0), system.getBeta(1)) > TRACE_WATER_PHASE_SELECTION_BETA_LIMIT) {
      return false;
    }

    double residual = maximumLogFugacityResidual(system.getPhase(0), system.getPhase(1));
    if (Double.isFinite(residual) && residual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
      return false;
    }
    system.init(1);
    residual = maximumLogFugacityResidual(system.getPhase(0), system.getPhase(1));
    return !Double.isFinite(residual) || residual >= PHASE_ROOT_EQUILIBRIUM_TOLERANCE;
  }

  /**
   * Performs a bounded final active-set refinement of an invalid neutral aqueous two-phase endpoint.
   *
   * <p>
   * Phase-appearance cleanup and post-convergence cubic-root selection can leave the correct two active phases with
   * stale phase fractions or compositions. The phase set is retained, but component material balance or fugacity
   * equality can then remain outside the ordinary flash tolerance. A {@link TPmultiflash} beta update is phase-order
   * independent and restores the constrained phase split without rerunning stability analysis. The refinement is
   * attempted for substantial-water endpoints and for trace-water endpoints whose already-active aqueous split is
   * non-conservative. The latter bypasses only the water-feed threshold, not phase stability or phase appearance. The
   * pre-refinement state is retained as a compact snapshot and restored unless the active-set update passes the
   * existing strict equilibrium and Gibbs checks.
   * </p>
   */
  private void refineInvalidAqueousTwoPhaseEndpoint() {
    if (system.getNumberOfPhases() != 2 || !system.hasPhaseType(PhaseType.AQUEOUS) || system.isChemicalSystem()
        || system.hasIons() || solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()) {
      return;
    }

    double waterFeedFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        waterFeedFraction = component.getz();
        break;
      }
    }
    if (waterFeedFraction < WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT) {
      double preRefinementMaterialResidual = maximumComponentMaterialBalanceResidual(system);
      if (Double.isFinite(preRefinementMaterialResidual)
          && preRefinementMaterialResidual <= WATER_RICH_MATERIAL_BALANCE_TOLERANCE) {
        return;
      }
    }

    system.init(1);
    double referenceMaterialResidual = maximumComponentMaterialBalanceResidual(system);
    double referenceFugacityResidual = maximumLogFugacityResidual(system.getPhase(0), system.getPhase(1));
    if (Double.isFinite(referenceMaterialResidual) && Double.isFinite(referenceFugacityResidual)
        && referenceMaterialResidual <= WATER_RICH_MATERIAL_BALANCE_TOLERANCE
        && referenceFugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
      return;
    }

    BalancedTwoPhaseState referenceState = new BalancedTwoPhaseState(system);
    double referenceGibbsEnergy = referenceState.gibbsEnergy;
    try {
      TPmultiflash endpointSolver = new TPmultiflash(system, false);
      endpointSolver.setDoubleArrays();
      for (int refinement = 0; refinement < 3 && !isBalancedEquilibriumCandidate(system); refinement++) {
        endpointSolver.solveBeta();
      }
      if (!isBalancedEquilibriumCandidate(system) || !preservesTwoPhaseActiveSet(system, referenceState.phaseTypes)) {
        restoreTwoPhaseIterationState(referenceState);
        return;
      }

      boolean referenceMaterialBalanceInvalid = !Double.isFinite(referenceMaterialResidual)
          || referenceMaterialResidual > WATER_RICH_MATERIAL_BALANCE_TOLERANCE;
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
      if (!referenceMaterialBalanceInvalid && system.getGibbsEnergy() > referenceGibbsEnergy + gibbsTolerance) {
        restoreTwoPhaseIterationState(referenceState);
      }
    } catch (Exception ex) {
      restoreTwoPhaseIterationState(referenceState);
      logger.debug("Final aqueous endpoint refinement failed: {}", ex.getMessage());
    }
  }

  /**
   * Performs a bounded final SSI refinement of a stale neutral gas/liquid two-phase endpoint.
   *
   * <p>
   * Post-convergence phase-root selection can leave a gas/oil split with valid material balance but component
   * fugacities just outside the flash tolerance. The refinement is attempted only for a neutral, non-aqueous,
   * exactly-two-phase endpoint whose material balance already closes. It retains the selected active set and accepts
   * the result only when phase fractions, compositions, material balance, fugacity equality, and Gibbs energy pass the
   * existing strict checks. Otherwise the complete two-phase iteration state is restored.
   * </p>
   */
  private void refineInvalidNeutralGasLiquidTwoPhaseEndpointLegacy() {
    if (system.getNumberOfPhases() != 2 || system.hasPhaseType(PhaseType.AQUEOUS) || system.isChemicalSystem()
        || system.hasIons() || solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()) {
      return;
    }
    boolean hasGasPhase = false;
    boolean hasLiquidPhase = false;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      if (phaseType != PhaseType.GAS && phaseType != PhaseType.OIL && phaseType != PhaseType.LIQUID) {
        return;
      }
      hasGasPhase |= phaseType == PhaseType.GAS;
      hasLiquidPhase |= phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID;
    }
    if (!hasGasPhase || !hasLiquidPhase) {
      return;
    }

    system.init(1);
    double referenceMaterialResidual = maximumComponentMaterialBalanceResidual(system);
    double referenceFugacityResidual = maximumLogFugacityResidual(system.getPhase(0), system.getPhase(1));
    if (!Double.isFinite(referenceMaterialResidual) || !Double.isFinite(referenceFugacityResidual)
        || referenceMaterialResidual > WATER_RICH_MATERIAL_BALANCE_TOLERANCE
        || referenceFugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE
        || referenceFugacityResidual > MAX_FINAL_EQUILIBRIUM_REFINEMENT_RESIDUAL) {
      return;
    }

    BalancedTwoPhaseState referenceState = new BalancedTwoPhaseState(system);
    try {
      for (int refinement = 0; refinement < MAX_FINAL_EQUILIBRIUM_REFINEMENT_ITERATIONS
          && !isBalancedEquilibriumCandidate(system); refinement++) {
        sucsSubs();
      }
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceState.gibbsEnergy) * 1.0e-8);
      if (!isBalancedEquilibriumCandidate(system) || !preservesTwoPhaseActiveSet(system, referenceState.phaseTypes)
          || system.getGibbsEnergy() > referenceState.gibbsEnergy + gibbsTolerance) {
        restoreTwoPhaseIterationState(referenceState);
      }
    } catch (Exception ex) {
      restoreTwoPhaseIterationState(referenceState);
      logger.debug("Final neutral hydrocarbon endpoint refinement failed: {}", ex.getMessage());
    }
  }

  /**
   * Performs a bounded final SSI refinement of a stale neutral gas/liquid two-phase endpoint.
   *
   * <p>
   * Post-convergence phase-root selection can leave a gas/oil split with valid material balance but component
   * fugacities just outside the flash tolerance. The refinement is attempted only for a neutral, non-aqueous,
   * exactly-two-phase endpoint whose material balance already closes. It retains the selected active set and accepts
   * the result only when phase fractions, compositions, material balance, fugacity equality, and Gibbs energy pass the
   * existing strict checks. Otherwise the complete two-phase iteration state is restored.
   * </p>
   */
  private void refineInvalidNeutralTwoPhaseEndpoint() {
    if (system.getNumberOfPhases() != 2 || system.hasPhaseType(PhaseType.AQUEOUS) || system.isChemicalSystem()
        || system.hasIons() || solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()) {
      return;
    }
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      if (phaseType != PhaseType.GAS && phaseType != PhaseType.OIL && phaseType != PhaseType.LIQUID) {
        return;
      }
    }

    if (!isSourGasConsistencyRefinementCase()) {
      return;
    }

    system.init(1);
    double referenceMaterialResidual = maximumComponentMaterialBalanceResidual(system);
    double referenceFugacityResidual = maximumLogFugacityResidual(system.getPhase(0), system.getPhase(1));
    if (Double.isFinite(referenceMaterialResidual) && referenceMaterialResidual <= WATER_RICH_MATERIAL_BALANCE_TOLERANCE
        && Double.isFinite(referenceFugacityResidual) && referenceFugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
      return;
    }

    BalancedTwoPhaseState referenceState = new BalancedTwoPhaseState(system);
    boolean referenceMaterialBalanceInvalid = !Double.isFinite(referenceMaterialResidual)
        || referenceMaterialResidual > WATER_RICH_MATERIAL_BALANCE_TOLERANCE;
    boolean referenceWasInvalid = !Double.isFinite(referenceMaterialResidual)
        || referenceMaterialResidual > WATER_RICH_MATERIAL_BALANCE_TOLERANCE
        || !Double.isFinite(referenceFugacityResidual) || referenceFugacityResidual >= PHASE_ROOT_EQUILIBRIUM_TOLERANCE;
    try {
      TPmultiflash endpointSolver = new TPmultiflash(system, false);
      endpointSolver.setDoubleArrays();
      for (int refinement = 0; refinement < MAX_FINAL_BETA_REFINEMENT_ITERATIONS
          && !isBalancedEquilibriumCandidate(system); refinement++) {
        endpointSolver.solveBeta();
      }
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceState.gibbsEnergy) * 1.0e-8);
      if (isBalancedEquilibriumCandidate(system) && preservesTwoPhaseActiveSet(system, referenceState.phaseTypes)
          && (referenceWasInvalid || system.getGibbsEnergy() <= referenceState.gibbsEnergy + gibbsTolerance)) {
        rescueLowerGibbsHydrocarbonPhaseRoots();
        system.orderByDensity();
        system.init(1);
        if (isBalancedEquilibriumCandidate(system)) {
          return;
        }
      }
      restoreTwoPhaseIterationState(referenceState);
    } catch (Exception ex) {
      restoreTwoPhaseIterationState(referenceState);
      logger.debug("Final neutral two-phase beta refinement failed: {}", ex.getMessage());
    }

    if (MULTIPHASE_RESCUE_ACTIVE.get().booleanValue()) {
      return;
    }
    SystemInterface candidate = system.clone();
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      resetNeutralCandidateToFeed(candidate);
      candidate.setMultiPhaseCheck(!system.doMultiPhaseCheck());
      candidate.setEnhancedMultiPhaseCheck(false);
      new TPflash(candidate, false).run();
      candidate.init(1);
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceState.gibbsEnergy) * 1.0e-8);
      boolean replacesInvalidIncipientPhase = !Double.isFinite(referenceFugacityResidual)
          || referenceFugacityResidual >= PHASE_ROOT_EQUILIBRIUM_TOLERANCE;
      replacesInvalidIncipientPhase &= candidate.getNumberOfPhases() == 1
          && Math.min(referenceState.betas[0], referenceState.betas[1]) < INVALID_INCIPIENT_PHASE_FRACTION_LIMIT
          && candidate.getGibbsEnergy() <= referenceState.gibbsEnergy + gibbsTolerance;
      if (isNeutralFluidCandidate(candidate) && isBalancedEquilibriumCandidate(candidate)
          && (referenceMaterialBalanceInvalid
              || candidate.getGibbsEnergy() < referenceState.gibbsEnergy - gibbsTolerance
              || replacesInvalidIncipientPhase)) {
        copyNeutralFlashStatePreservingRoots(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Final neutral two-phase cross-algorithm refinement failed: {}", ex.getMessage());
    } finally {
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Resets a neutral cross-algorithm candidate to the overall feed before reflashing.
   *
   * <p>
   * Copying an invalid endpoint also copies stale phase fractions, compositions, and cubic roots. Starting the
   * reciprocal flash from that state can reproduce the same invalid stationary point. The reset is confined to the
   * already-failed fallback path and reconstructs the ordinary two-phase TP-flash starting state without allocating a
   * new thermodynamic system.
   * </p>
   *
   * @param candidate cloned candidate to reset
   */
  private void resetNeutralCandidateToFeed(SystemInterface candidate) {
    candidate.setNumberOfPhases(2);
    candidate.setPhaseIndex(0, 0);
    candidate.setPhaseIndex(1, 1);
    candidate.setPhaseType(0, PhaseType.GAS);
    candidate.setPhaseType(1, PhaseType.OIL);
    candidate.setBeta(0, 0.5);
    candidate.setBeta(1, 0.5);
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      for (int componentIndex = 0; componentIndex < candidate.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        neqsim.thermo.component.ComponentInterface component = candidate.getPhase(phaseIndex)
            .getComponent(componentIndex);
        component.setx(component.getz());
        double logWilsonK = Math.log(component.getPC() / candidate.getPressure())
            + 5.373 * (1.0 + component.getAcentricFactor()) * (1.0 - component.getTC() / candidate.getTemperature());
        component.setK(Math.exp(Math.max(-50.0, Math.min(50.0, logWilsonK))));
      }
      candidate.getPhase(phaseIndex).normalize();
    }
  }

  /**
   * Copies a neutral two-phase candidate while retaining its selected cubic roots.
   *
   * <p>
   * A converged phase may expose a gas/oil label while its lower-Gibbs cubic root is stored separately by the system.
   * {@link #copyFlashStateFrom(SystemInterface)} copies the public label and can therefore reinitialize the phase on a
   * different root. The reciprocal fallback infers each selected root from the candidate compressibility factor and
   * then lets the final EOS initialization assign the public gas/oil label deterministically from V/B. Retaining a
   * stale candidate label here can otherwise make ordinary and multiphase flashes report different phase types for
   * numerically identical states.
   * </p>
   *
   * @param source accepted neutral two-phase candidate
   */
  private void copyNeutralFlashStatePreservingRoots(SystemInterface source) {
    int numberOfPhases = source.getNumberOfPhases();
    PhaseType[] rootTypes = new PhaseType[numberOfPhases];
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      rootTypes[phaseIndex] = inferSelectedCubicRoot(source, phaseIndex);
    }
    copyFlashStateFrom(source);
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      system.setPhaseType(phaseIndex, rootTypes[phaseIndex]);
    }
    system.init(1);
  }

  /**
   * Infers the cubic root that reproduces a converged phase's compressibility factor.
   *
   * <p>
   * If retained cubic-root history makes both trial types reproduce the same compressibility factor, the candidate's
   * declared gas/liquid class breaks the numerical tie. Otherwise iteration order could replace an accepted liquid root
   * with a gas root during state transfer.
   * </p>
   *
   * @param source converged candidate system
   * @param phaseIndex active phase index
   * @return gas-like or liquid-like cubic root closest to the converged phase
   */
  private PhaseType inferSelectedCubicRoot(SystemInterface source, int phaseIndex) {
    PhaseType selectedRoot = source.getPhase(phaseIndex).getType();
    PhaseType declaredRoot = selectedRoot == PhaseType.GAS ? PhaseType.GAS : PhaseType.LIQUID;
    double selectedDifference = Double.POSITIVE_INFINITY;
    for (PhaseType trialRoot : CUBIC_ROOT_PHASE_TYPES) {
      try {
        PhaseInterface trialPhase = source.getPhase(phaseIndex).clone();
        trialPhase.init(source.getTotalNumberOfMoles(), trialPhase.getNumberOfComponents(), 1, trialRoot,
            source.getBeta(phaseIndex));
        double difference = Math.abs(trialPhase.getZ() - source.getPhase(phaseIndex).getZ());
        boolean tiedDeclaredRoot = Double.isFinite(difference) && Double.isFinite(selectedDifference)
            && Math.abs(difference - selectedDifference) <= UNCHANGED_SINGLE_PHASE_STATE_TOLERANCE
            && trialRoot == declaredRoot;
        if (Double.isFinite(difference) && (difference < selectedDifference || tiedDeclaredRoot)) {
          selectedDifference = difference;
          selectedRoot = trialRoot;
        }
      } catch (Exception ex) {
        logger.debug("Cubic-root inference failed for phase {} root {}: {}", phaseIndex, trialRoot, ex.getMessage());
      }
    }
    return selectedRoot;
  }

  /**
   * Checks that a bounded beta refinement has not changed either selected phase identity.
   *
   * @param candidate refined thermodynamic system
   * @param referencePhaseTypes phase types captured before refinement
   * @return true when the same two phase types remain at the same phase indices
   */
  static boolean preservesTwoPhaseActiveSet(SystemInterface candidate, PhaseType[] referencePhaseTypes) {
    return candidate.getNumberOfPhases() == 2 && referencePhaseTypes != null && referencePhaseTypes.length == 2
        && candidate.getPhase(0).getType() == referencePhaseTypes[0]
        && candidate.getPhase(1).getType() == referencePhaseTypes[1];
  }

  /**
   * Restores beta, compositions, and K-values changed by a rejected two-phase endpoint refinement.
   *
   * @param referenceState pre-refinement state
   */
  private void restoreTwoPhaseIterationState(BalancedTwoPhaseState referenceState) {
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      system.setPhaseType(phaseIndex, referenceState.phaseTypes[phaseIndex]);
      system.setBeta(phaseIndex, referenceState.betas[phaseIndex]);
      for (int componentIndex = 0; componentIndex < referenceState.compositions[phaseIndex].length; componentIndex++) {
        system.getPhase(phaseIndex).getComponent(componentIndex)
            .setx(referenceState.compositions[phaseIndex][componentIndex]);
        system.getPhase(phaseIndex).getComponent(componentIndex).setK(referenceState.kValues[componentIndex]);
      }
    }
    system.normalizeBeta();
    system.init(1);
  }

  /**
   * Screens for a non-aqueous, non-hydrocarbon-rich, high-volatility-contrast liquid mixture.
   *
   * <p>
   * Liquid-liquid demixing in non-aqueous cubic-EOS process mixtures is most relevant when a substantial
   * polar/inert/non-hydrocarbon fraction coexists with a much less volatile hydrocarbon. The screen uses only feed
   * composition and immutable component critical data; it performs no property initialization or trial-phase
   * calculation. The subsequent tangent-plane stability calculation remains the authoritative decision.
   * </p>
   *
   * @return true when the cheap composition screen justifies multiphase stability refinement
   */
  private boolean hasPotentialLiquidLiquidInstability() {
    return hasPotentialAsymmetricNeutralInstability(LIQUID_LIQUID_CRITICAL_TEMPERATURE_MARGIN);
  }

  /**
   * Restricts the new reciprocal and beta-refinement paths to the validated sour-gas family.
   *
   * @return true for water-free methane/CO2/H2S-like feeds with substantial CO2 and H2S
   */
  private boolean isSourGasConsistencyRefinementCase() {
    double carbonDioxideFraction = 0.0;
    double hydrogenSulfideFraction = 0.0;
    double hydrocarbonFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      double feedFraction = component.getz();
      String componentName = component.getComponentName();
      if ("water".equalsIgnoreCase(componentName)) {
        return false;
      }
      if ("CO2".equalsIgnoreCase(componentName)) {
        carbonDioxideFraction += feedFraction;
      } else if ("H2S".equalsIgnoreCase(componentName)) {
        hydrogenSulfideFraction += feedFraction;
      } else if (component.isHydrocarbon()) {
        hydrocarbonFraction += feedFraction;
      }
    }
    return carbonDioxideFraction >= 0.05 && hydrogenSulfideFraction >= 0.20
        && carbonDioxideFraction + hydrogenSulfideFraction >= 0.30 && hydrocarbonFraction > 0.0;
  }

  /**
   * Restores exact material closure for a final single-phase sour-gas endpoint.
   *
   * <p>
   * A collapsed trial phase can leave its incipient composition and a beta infinitesimally below unity in the active
   * phase slot. For a one-phase result the composition is, by definition, the overall feed. Resetting it here is an
   * allocation-free finalization step and leaves the already-selected cubic root unchanged.
   * </p>
   */
  private void normalizeSourGasSinglePhaseEndpoint() {
    if (!isSourGasConsistencyRefinementCase() || system.getNumberOfPhases() != 1) {
      return;
    }
    system.setBeta(0, 1.0);
    resetSinglePhaseCompositionToFeed();
    system.init(1, 0);
  }

  /**
   * Screens for a non-aqueous, non-hydrocarbon-rich, high-volatility-contrast liquid mixture.
   *
   * <p>
   * Liquid-liquid demixing in non-aqueous cubic-EOS process mixtures is most relevant when a substantial
   * polar/inert/non-hydrocarbon fraction coexists with a much less volatile hydrocarbon. The screen uses only feed
   * composition and immutable component critical data; it performs no property initialization or trial-phase
   * calculation. The subsequent tangent-plane stability calculation remains the authoritative decision.
   * </p>
   *
   * @return true when the cheap composition screen justifies multiphase stability refinement
   */
  private boolean hasPotentialLiquidLiquidInstabilityLegacy() {
    double nonHydrocarbonFraction = 0.0;
    double minimumCriticalTemperature = Double.POSITIVE_INFINITY;
    double maximumCriticalTemperature = Double.NEGATIVE_INFINITY;
    boolean hasHeavyHydrocarbon = false;
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      double feedFraction = component.getz();
      if (feedFraction <= LIQUID_LIQUID_ACTIVE_COMPONENT_LIMIT) {
        continue;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        return false;
      }
      double criticalTemperature = component.getTC();
      minimumCriticalTemperature = Math.min(minimumCriticalTemperature, criticalTemperature);
      maximumCriticalTemperature = Math.max(maximumCriticalTemperature, criticalTemperature);
      if (component.isHydrocarbon()) {
        if (criticalTemperature > system.getTemperature() + LIQUID_LIQUID_CRITICAL_TEMPERATURE_SPAN) {
          hasHeavyHydrocarbon = true;
        }
      } else {
        nonHydrocarbonFraction += feedFraction;
      }
    }
    return nonHydrocarbonFraction >= LIQUID_LIQUID_NON_HYDROCARBON_FRACTION_LIMIT && hasHeavyHydrocarbon
        && maximumCriticalTemperature - minimumCriticalTemperature >= LIQUID_LIQUID_CRITICAL_TEMPERATURE_SPAN;
  }

  /**
   * Screens for an asymmetric neutral mixture in the temperature range where an extra fluid phase is plausible.
   *
   * @param criticalTemperatureMargin required component critical-temperature margin above the flash temperature
   * @return true when the feed composition and critical-temperature spread justify a guarded stability retry
   */
  private boolean hasPotentialAsymmetricNeutralInstability(double criticalTemperatureMargin) {
    double nonHydrocarbonFraction = 0.0;
    double minimumCriticalTemperature = Double.POSITIVE_INFINITY;
    double maximumCriticalTemperature = Double.NEGATIVE_INFINITY;
    boolean hasCondensableComponent = false;
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      double feedFraction = component.getz();
      if (feedFraction <= LIQUID_LIQUID_ACTIVE_COMPONENT_LIMIT) {
        continue;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        return false;
      }
      double criticalTemperature = component.getTC();
      minimumCriticalTemperature = Math.min(minimumCriticalTemperature, criticalTemperature);
      maximumCriticalTemperature = Math.max(maximumCriticalTemperature, criticalTemperature);
      if (criticalTemperature > system.getTemperature() + criticalTemperatureMargin) {
        hasCondensableComponent = true;
      }
      if (!component.isHydrocarbon()) {
        nonHydrocarbonFraction += feedFraction;
      }
    }
    return nonHydrocarbonFraction >= LIQUID_LIQUID_NON_HYDROCARBON_FRACTION_LIMIT && hasCondensableComponent
        && maximumCriticalTemperature - minimumCriticalTemperature >= LIQUID_LIQUID_CRITICAL_TEMPERATURE_SPAN;
  }

  /**
   * Retries a single-phase hydrocarbon endpoint with a nearby multiphase seed.
   *
   * <p>
   * Near phase boundaries the cold Wilson-seeded TP flash may converge to a local one-phase endpoint even though a
   * nearby two-phase seed converges to a lower-Gibbs solution at the target pressure and temperature. This guarded
   * retry is only used when the user has explicitly enabled multiphase checking and the ordinary TPmultiflash cleanup
   * still leaves one hydrocarbon phase.
   * </p>
   */
  private void rescueSinglePhaseMultiphaseEndpointLegacy() {
    if (!shouldRunMultiphaseEndpointRescueLegacy()) {
      return;
    }

    double targetTemperature = system.getTemperature();
    double targetPressure = system.getPressure();
    system.init(1);
    double referenceGibbsEnergy = system.getGibbsEnergy();
    SystemInterface candidate = system.clone();
    boolean previousWarmStart = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      // Warm start is required here for every model, including CPA. Unlike the iterative outer
      // flashes (PH/PS/PV/TV/...), which are governed by
      // ThermodynamicModelSettings.isInnerFlashWarmStartSafe(system), this rescue deliberately
      // continues from a seed flash at a nearby temperature: carrying the seed K-values over to
      // the target temperature is the mechanism that finds the extra phase. Disabling it would
      // defeat the rescue.
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
      double seedTemperature = Math.max(1.0, targetTemperature - MULTIPHASE_RESCUE_TEMPERATURE_STEP);
      candidate.setTemperature(seedTemperature, "K");
      candidate.setPressure(targetPressure, "bara");
      new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      if (candidate.getNumberOfPhases() < 2) {
        return;
      }
      candidate.setTemperature(targetTemperature, "K");
      candidate.setPressure(targetPressure, "bara");
      new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      if (isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy)) {
        copyFlashStateFrom(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Multiphase endpoint rescue failed: {}", ex.getMessage());
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Retains the cold pre-iteration state for a narrowly screened multiphase endpoint retry.
   *
   * <p>
   * Once a stability trial has collapsed a phase, cloning that endpoint also copies its local cubic-root and
   * phase-storage history. A later retry can then reproduce the same homogeneous minimum even though a cold flash finds
   * a lower-Gibbs split. The seed is therefore captured before the two-phase iteration for multiphase flashes, ordinary
   * sour-gas flashes whose deterministic asymmetric and Wilson endpoint screens justify a possible retry, and neutral
   * non-CPA water-rich asymmetric feeds whose post-flash clone may otherwise retain a collapsed phase history. It is
   * consumed at most once and cleared when the operation returns. Dry, CPA, chemical, ionic, solid, wax, and
   * non-asymmetric water-bearing flashes remain outside the additional water-rich cold-seed allocation.
   * </p>
   */
  private void prepareMultiphaseEndpointRescueSeed() {
    multiphaseEndpointRescueSeed = null;
    boolean ordinarySourGasCandidate = !system.doMultiPhaseCheck() && isSourGasConsistencyRefinementCase();
    boolean ordinaryWaterRichCandidate = hasPotentialWaterRichColdSeedInstability();
    if ((!system.doMultiPhaseCheck() && !ordinarySourGasCandidate && !ordinaryWaterRichCandidate)
        || system.isChemicalSystem() || system.hasIons() || solidCheck || system.doSolidPhaseCheck()
        || system.isMultiphaseWaxCheck() || directGammaPhiModel != null || hybridEosGeFlashModel != null
        || system.getPhase(0).getNumberOfComponents() <= 1
        || (!ordinaryWaterRichCandidate && (!hasPotentialAsymmetricNeutralInstability(
            MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN)
            || !(hasPotentialMultiphaseEndpoint(PhaseType.GAS) || hasPotentialMultiphaseEndpoint(PhaseType.LIQUID))))) {
      return;
    }
    multiphaseEndpointRescueSeed = system.clone();
  }

  /**
   * Screens a cubic-EOS water-rich asymmetric feed for a cold reciprocal-stability seed.
   *
   * <p>
   * This screen mirrors the existing neutral liquid-liquid composition/critical-temperature gate while permitting the
   * water component that defines this fallback. A substantial non-hydrocarbon fraction and a condensable hydrocarbon
   * are both required, so hydrocarbon/water process flashes do not allocate a seed merely because water is present. The
   * later reciprocal solve and strict feasibility, equilibrium, and Gibbs gates decide stability.
   * </p>
   *
   * @return true when retaining one cold pre-iteration state is justified
   */
  private boolean hasPotentialWaterRichColdSeedInstability() {
    String modelName = system.getModelName();
    if (modelName != null && modelName.contains("CPA")) {
      return false;
    }
    double waterFraction = 0.0;
    double nonHydrocarbonFraction = 0.0;
    double minimumCriticalTemperature = Double.POSITIVE_INFINITY;
    double maximumCriticalTemperature = Double.NEGATIVE_INFINITY;
    double condensableHydrocarbonFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      double feedFraction = component.getz();
      if (feedFraction <= LIQUID_LIQUID_ACTIVE_COMPONENT_LIMIT) {
        continue;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        waterFraction += feedFraction;
        nonHydrocarbonFraction += feedFraction;
        continue;
      }
      double criticalTemperature = component.getTC();
      minimumCriticalTemperature = Math.min(minimumCriticalTemperature, criticalTemperature);
      maximumCriticalTemperature = Math.max(maximumCriticalTemperature, criticalTemperature);
      if (!component.isHydrocarbon()) {
        nonHydrocarbonFraction += feedFraction;
      } else if (criticalTemperature > system.getTemperature() + MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN) {
        condensableHydrocarbonFraction += feedFraction;
      }
    }
    return waterFraction >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
        && nonHydrocarbonFraction >= LIQUID_LIQUID_NON_HYDROCARBON_FRACTION_LIMIT
        && condensableHydrocarbonFraction >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
        && maximumCriticalTemperature - minimumCriticalTemperature >= LIQUID_LIQUID_CRITICAL_TEMPERATURE_SPAN;
  }

  /**
   * Retries a single-phase hydrocarbon endpoint through the ordinary two-phase path.
   *
   * <p>
   * A multiphase cleanup can collapse a valid gas/liquid split even though Wilson K-values satisfy the guarded
   * Rachford-Rice endpoint tests. This guarded retry is only used when the user has explicitly enabled multiphase
   * checking and those inexpensive tests indicate a split. A screened cold seed avoids reusing cubic-root history from
   * a collapsed asymmetric endpoint, where a single ordinary flash is sufficient. A strong hydrocarbon Wilson split
   * retains the established nearby-temperature continuation only when the cheaper ordinary retry fails.
   * </p>
   */
  private void rescueSinglePhaseMultiphaseEndpoint() {
    if (!isSourGasConsistencyRefinementCase()) {
      return;
    }
    if (!shouldRunMultiphaseEndpointRescue()) {
      return;
    }

    normalizeActivePhaseFractions();
    system.init(1);
    double referenceGibbsEnergy = system.getGibbsEnergy();
    boolean hasColdSeed = multiphaseEndpointRescueSeed != null;
    SystemInterface candidate = hasColdSeed ? multiphaseEndpointRescueSeed : system.clone();
    multiphaseEndpointRescueSeed = null;
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      if (!hasColdSeed && hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN)) {
        resetNeutralCandidateToFeed(candidate);
      }
      candidate.setMultiPhaseCheck(false);
      candidate.setEnhancedMultiPhaseCheck(false);
      new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      if (isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy)) {
        copyFlashStateFrom(candidate);
        return;
      }
      if (hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN)) {
        return;
      }

      double targetTemperature = system.getTemperature();
      double targetPressure = system.getPressure();
      candidate = system.clone();
      candidate.setMultiPhaseCheck(true);
      candidate.setEnhancedMultiPhaseCheck(false);
      boolean previousWarmStart = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
      try {
        neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
        candidate.setTemperature(Math.max(1.0, targetTemperature - MULTIPHASE_RESCUE_TEMPERATURE_STEP), "K");
        candidate.setPressure(targetPressure, "bara");
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
        if (candidate.getNumberOfPhases() < 2) {
          return;
        }
        candidate.setTemperature(targetTemperature, "K");
        candidate.setPressure(targetPressure, "bara");
        new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
        if (isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy)) {
          copyFlashStateFrom(candidate);
        }
      } finally {
        neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
      }
    } catch (Exception ex) {
      logger.debug("Multiphase endpoint rescue failed: {}", ex.getMessage());
    } finally {
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Checks if the endpoint rescue should run for the current flash result.
   *
   * @return true when the result is a single hydrocarbon phase from an explicit multiphase flash
   */
  private boolean shouldRunMultiphaseEndpointRescueLegacy() {
    if (!system.doMultiPhaseCheck() || system.getNumberOfPhases() != 1 || system.isChemicalSystem()
        || MULTIPHASE_RESCUE_ACTIVE.get().booleanValue()) {
      return false;
    }
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    int numberOfComponents = phase.getNumberOfComponents();
    if (numberOfComponents <= 1) {
      return false;
    }
    PhaseType phaseType = phase.getType();
    if (!(phaseType == PhaseType.GAS || phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID)) {
      return false;
    }
    boolean hasHydrocarbon = false;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      if (component.getz() < 1.0e-50) {
        continue;
      }
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        return false;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        return false;
      }
      if (!component.isHydrocarbon() && !component.isInert()) {
        return false;
      }
      if (component.isHydrocarbon()) {
        hasHydrocarbon = true;
      }
    }
    return hasHydrocarbon && hasPotentialMultiphaseEndpointLegacy(phaseType);
  }

  /**
   * Checks if the endpoint rescue should run for the current flash result.
   *
   * @return true when the result is a single hydrocarbon phase from an explicit multiphase flash
   */
  private boolean shouldRunMultiphaseEndpointRescue() {
    if (!system.doMultiPhaseCheck() || system.getNumberOfPhases() != 1 || system.isChemicalSystem()
        || MULTIPHASE_RESCUE_ACTIVE.get().booleanValue()) {
      return false;
    }
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    int numberOfComponents = phase.getNumberOfComponents();
    if (numberOfComponents <= 1) {
      return false;
    }
    PhaseType phaseType = phase.getType();
    if (!(phaseType == PhaseType.GAS || phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID)) {
      return false;
    }
    boolean hasHydrocarbon = false;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      if (component.getz() < 1.0e-50) {
        continue;
      }
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        return false;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        return false;
      }
      if (component.isHydrocarbon()) {
        hasHydrocarbon = true;
      }
    }
    return hasHydrocarbon && hasPotentialMultiphaseEndpoint(phaseType);
  }

  /**
   * Checks whether stored K-values indicate a nearby split worth a local endpoint rescue.
   *
   * @param phaseType phase type of the current single-phase endpoint
   * @return true when the endpoint is close enough to a potential phase split to retry
   */
  private boolean hasPotentialMultiphaseEndpointLegacy(PhaseType phaseType) {
    double sumZK = 0.0;
    double sumZOverK = 0.0;
    double maxAbsLogK = 0.0;
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    int numberOfComponents = phase.getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      double z = component.getz();
      if (z < 1.0e-50) {
        continue;
      }
      double kValue = component.getK();
      if (kValue <= 0.0 || Double.isNaN(kValue) || Double.isInfinite(kValue)) {
        return true;
      }
      sumZK += z * kValue;
      sumZOverK += z / kValue;
      maxAbsLogK = Math.max(maxAbsLogK, Math.abs(Math.log(kValue)));
    }
    if (phaseType == PhaseType.GAS) {
      return sumZK > MULTIPHASE_RESCUE_GAS_SUM_Z_K_LOWER_LIMIT && sumZK < MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT
          && sumZOverK > MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_LOWER_LIMIT
          && sumZOverK < MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT;
    }
    return sumZK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_LOWER_LIMIT && sumZK < MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_UPPER_LIMIT
        && sumZOverK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT
        && maxAbsLogK > MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT;
  }

  /**
   * Checks whether deterministic Wilson K-values indicate a split worth a local endpoint rescue.
   *
   * @param phaseType phase type of the current single-phase endpoint
   * @return true when the endpoint is close enough to a potential phase split to retry
   */
  private boolean hasPotentialMultiphaseEndpoint(PhaseType phaseType) {
    double sumZK = 0.0;
    double sumZOverK = 0.0;
    double maxAbsLogK = 0.0;
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    double temperature = system.getTemperature();
    double pressure = system.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      double z = component.getz();
      if (z < 1.0e-50) {
        continue;
      }
      double criticalTemperature = component.getTC();
      double criticalPressure = component.getPC();
      double acentricFactor = component.getAcentricFactor();
      if (!Double.isFinite(temperature) || temperature <= 0.0 || !Double.isFinite(pressure) || pressure <= 0.0
          || !Double.isFinite(criticalTemperature) || criticalTemperature <= 0.0 || !Double.isFinite(criticalPressure)
          || criticalPressure <= 0.0 || !Double.isFinite(acentricFactor)) {
        return true;
      }
      double logK = Math.log(criticalPressure / pressure)
          + 5.373 * (1.0 + acentricFactor) * (1.0 - criticalTemperature / temperature);
      double kValue = Math.exp(Math.max(-50.0, Math.min(50.0, logK)));
      sumZK += z * kValue;
      sumZOverK += z / kValue;
      maxAbsLogK = Math.max(maxAbsLogK, Math.abs(logK));
    }
    if (phaseType == PhaseType.GAS) {
      boolean generalNearSplit = sumZK > MULTIPHASE_RESCUE_GAS_SUM_Z_K_LOWER_LIMIT
          && sumZK < MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT
          && sumZOverK > MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_LOWER_LIMIT
          && sumZOverK < MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT;
      boolean asymmetricNearSplit = sumZK > MULTIPHASE_RESCUE_GAS_ASYMMETRIC_SUM_Z_K_LOWER_LIMIT
          && sumZOverK > MULTIPHASE_RESCUE_GAS_ASYMMETRIC_SUM_Z_OVER_K_LOWER_LIMIT;
      boolean stronglyAsymmetric = sumZOverK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT
          && maxAbsLogK > MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT;
      boolean strongWilsonSplit = sumZK > MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT
          && sumZOverK > MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT
          && maxAbsLogK > MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT;
      return generalNearSplit || strongWilsonSplit || (asymmetricNearSplit || stronglyAsymmetric)
          && hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN);
    }
    boolean nearSplit = sumZK > MULTIPHASE_RESCUE_LIQUID_NEAR_SPLIT_SUM_Z_K_LIMIT
        && sumZOverK > MULTIPHASE_RESCUE_LIQUID_NEAR_SPLIT_SUM_Z_OVER_K_LIMIT;
    boolean stronglyAsymmetric = sumZOverK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT
        && maxAbsLogK > MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT;
    return nearSplit || stronglyAsymmetric
        && hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN);
  }

  /**
   * Checks if a candidate should replace the current single-phase endpoint.
   *
   * @param candidate candidate system produced by the local seed retry
   * @param referenceGibbsEnergy Gibbs energy of the original one-phase endpoint
   * @param referenceMaterialBalanceInvalid whether the reference is non-conservative and its Gibbs energy cannot be
   * compared
   * @return true when the candidate is multiphase and has a lower Gibbs energy
   */
  private boolean shouldAcceptWaterRichCandidate(SystemInterface candidate, double referenceGibbsEnergy,
      boolean referenceMaterialBalanceInvalid) {
    return shouldAcceptWaterRichCandidate(candidate, referenceGibbsEnergy, referenceMaterialBalanceInvalid, false);
  }

  /**
   * Checks whether a water-rich candidate can replace its reference endpoint.
   *
   * @param candidate candidate system produced by the local seed retry
   * @param referenceGibbsEnergy Gibbs energy of the original endpoint
   * @param referenceMaterialBalanceInvalid whether the reference is non-conservative and its Gibbs energy cannot be
   * compared
   * @param incipientCpaAqueousTrial whether a one-phase CPA endpoint produced the candidate aqueous split
   * @return true when the candidate passes the applicable strict thermodynamic acceptance gate
   */
  private boolean shouldAcceptWaterRichCandidate(SystemInterface candidate, double referenceGibbsEnergy,
      boolean referenceMaterialBalanceInvalid, boolean incipientCpaAqueousTrial) {
    if (referenceMaterialBalanceInvalid) {
      return isBalancedEquilibriumCandidate(candidate);
    }
    if (incipientCpaAqueousTrial) {
      double gibbsTolerance = Math.max(CPA_AQUEOUS_GIBBS_ABSOLUTE_TOLERANCE_J,
          Math.abs(referenceGibbsEnergy) * CPA_AQUEOUS_GIBBS_RELATIVE_TOLERANCE);
      return hasAcceptableMultiphaseCandidate(candidate)
          && candidate.getGibbsEnergy() < referenceGibbsEnergy - gibbsTolerance;
    }
    return isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy);
  }

  private boolean isLowerGibbsMultiphaseCandidate(SystemInterface candidate, double referenceGibbsEnergy) {
    if (!hasAcceptableMultiphaseCandidate(candidate)) {
      return false;
    }
    double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
    return candidate.getGibbsEnergy() < referenceGibbsEnergy - gibbsTolerance;
  }

  /**
   * Checks phase fractions and distinct compositions before comparing candidate Gibbs energy.
   *
   * @param candidate candidate system produced by a guarded stability trial
   * @return true when the candidate has a normalized, non-vanishing, distinct multiphase active set
   */
  private boolean hasAcceptableMultiphaseCandidate(SystemInterface candidate) {
    if (candidate.getNumberOfPhases() < 2) {
      return false;
    }
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < candidate.getNumberOfPhases(); phaseIndex++) {
      if (candidate.getBeta(phaseIndex) <= 10.0 * phaseFractionMinimumLimit) {
        return false;
      }
      betaTotal += candidate.getBeta(phaseIndex);
    }
    if (Math.abs(betaTotal - 1.0) > 1.0e-6 || !hasDistinctPhaseCompositions(candidate)) {
      return false;
    }
    return true;
  }

  /**
   * Checks whether a two-phase candidate closes material balance and component fugacity equality.
   *
   * @param candidate candidate system to inspect
   * @return true when phase fractions, compositions, material balance, and equilibrium residuals are finite and satisfy
   * the strict two-phase endpoint tolerances
   */
  private boolean isBalancedEquilibriumCandidate(SystemInterface candidate) {
    if (candidate.getNumberOfPhases() < 1 || candidate.getNumberOfPhases() > 2) {
      return false;
    }
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < candidate.getNumberOfPhases(); phaseIndex++) {
      double phaseFraction = candidate.getBeta(phaseIndex);
      if (!Double.isFinite(phaseFraction) || phaseFraction <= 10.0 * phaseFractionMinimumLimit) {
        return false;
      }
      betaTotal += phaseFraction;
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < candidate.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double phaseComposition = candidate.getPhase(phaseIndex).getComponent(componentIndex).getx();
        if (!Double.isFinite(phaseComposition) || phaseComposition < 0.0 || phaseComposition > 1.0) {
          return false;
        }
        compositionTotal += phaseComposition;
      }
      if (!Double.isFinite(compositionTotal)
          || Math.abs(compositionTotal - 1.0) > WATER_RICH_MATERIAL_BALANCE_TOLERANCE) {
        return false;
      }
    }
    if (!Double.isFinite(betaTotal) || Math.abs(betaTotal - 1.0) > 1.0e-6
        || candidate.getNumberOfPhases() == 2 && !hasDistinctPhaseCompositions(candidate)) {
      return false;
    }
    if (maximumComponentMaterialBalanceResidual(candidate) > WATER_RICH_MATERIAL_BALANCE_TOLERANCE) {
      return false;
    }
    if (candidate.getNumberOfPhases() == 1) {
      return true;
    }
    double maximumFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < candidate.getPhase(0).getNumberOfComponents(); componentIndex++) {
      if (candidate.getPhase(0).getComponent(componentIndex).getz() <= 1.0e-50) {
        continue;
      }
      double firstLogFugacity = Math
          .log(Math.max(candidate.getPhase(0).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(candidate.getPhase(0).getComponent(componentIndex).getFugacityCoefficient());
      double secondLogFugacity = Math
          .log(Math.max(candidate.getPhase(1).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(candidate.getPhase(1).getComponent(componentIndex).getFugacityCoefficient());
      if (!Double.isFinite(firstLogFugacity) || !Double.isFinite(secondLogFugacity)) {
        return false;
      }
      maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(firstLogFugacity - secondLogFugacity));
    }
    return maximumFugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE;
  }

  /**
   * Captures a feasible equilibrium before multiphase phase-appearance trials.
   *
   * <p>
   * The compact snapshot is restricted to neutral, exactly-two-phase endpoints that already satisfy the strict
   * feasibility and equilibrium checks. Water-bearing states are retained for the existing active-set recovery. Dry
   * states are retained only for the inexpensive asymmetric-mixture screen, where a multiphase stability pass can
   * otherwise collapse a lower-Gibbs liquid-liquid split. Common dry flashes allocate no snapshot.
   * </p>
   *
   * @return balanced state, or {@code null} when recovery is not applicable
   */
  private BalancedTwoPhaseState balancedReferenceBeforeMultiphaseCheck() {
    if (system.getNumberOfPhases() != 2 || system.isChemicalSystem() || system.hasIons() || solidCheck
        || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck() || !isBalancedEquilibriumCandidate(system)) {
      return null;
    }
    if (!system.hasPhaseType(PhaseType.AQUEOUS)
        && !hasPotentialAsymmetricNeutralInstability(MULTIPHASE_ENDPOINT_CRITICAL_TEMPERATURE_MARGIN)) {
      return null;
    }
    return new BalancedTwoPhaseState(system);
  }

  /**
   * Captures a feasible water-rich ordinary input before the two-phase iteration mutates it.
   *
   * <p>
   * Repeating an ordinary flash on an already converged OIL+AQUEOUS state can collapse the split before the reciprocal
   * stability fallback runs. The retained state is eligible only when it is independently balanced and equilibrated at
   * the current temperature, pressure, and composition. Changed-state inputs that are no longer equilibrium therefore
   * cannot be restored as stale results.
   * </p>
   *
   * @return balanced current-state snapshot, or {@code null} when repeat protection is not applicable
   */
  private BalancedTwoPhaseState balancedWaterRichInputBeforeOrdinaryIteration() {
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() != 2 || !system.hasPhaseType(PhaseType.AQUEOUS)
        || system.isChemicalSystem() || system.hasIons() || solidCheck || system.doSolidPhaseCheck()
        || system.isMultiphaseWaxCheck()) {
      return null;
    }
    SystemInterface candidate = system.clone();
    try {
      candidate.init(1);
      if (isBalancedEquilibriumCandidate(candidate)) {
        return new BalancedTwoPhaseState(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Water-rich input repeat snapshot failed: {}", ex.getMessage());
    }
    return null;
  }

  /**
   * Restores a feasible aqueous equilibrium when a rejected phase-appearance trial leaves an invalid two-phase
   * endpoint.
   *
   * <p>
   * A balanced gas/aqueous flash can be temporarily expanded to three phases when tangent-plane stability testing finds
   * a near-boundary hydrocarbon-liquid trial. If that trial disappears during {@link TPmultiflash} cleanup, the
   * remaining phases can retain phase fractions from the rejected three-phase iterate. Composition normalization alone
   * does not repair the resulting component material-balance or fugacity residuals. The pre-trial state is restored
   * only when the final endpoint still has exactly two phases including an aqueous phase and fails the same strict
   * acceptance checks. Genuine three-phase results and feasible multiphase refinements are unchanged.
   * </p>
   *
   * @param balancedReference feasible pre-trial state, or {@code null} when recovery is not applicable
   */
  private void restoreBalancedAqueousReferenceAfterInvalidPhaseRemoval(BalancedTwoPhaseState balancedReference) {
    if (balancedReference == null || system.getNumberOfPhases() != 2 || !system.hasPhaseType(PhaseType.AQUEOUS)
        || isBalancedEquilibriumCandidate(system)) {
      return;
    }
    restoreBalancedTwoPhaseState(balancedReference);
  }

  /**
   * Restores a lower-Gibbs feasible split when multiphase cleanup collapses it to one phase.
   *
   * <p>
   * A successful ordinary two-phase flash is already a feasible phase-split candidate. If the subsequent multiphase
   * stability path removes a phase and returns a one-phase state with higher extensive Gibbs energy, the collapse
   * cannot represent the stable minimum. This gate is limited to neutral water-bearing or screened asymmetric systems
   * and requires the ordinary reference to pass the strict material-balance, composition, phase-fraction, and fugacity
   * checks before it is captured.
   * </p>
   *
   * @param balancedReference feasible pre-trial state, or {@code null} when recovery is not applicable
   */
  private void restoreLowerGibbsReferenceAfterSinglePhaseCollapse(BalancedTwoPhaseState balancedReference) {
    if (balancedReference == null || system.getNumberOfPhases() != 1) {
      return;
    }
    system.init(1);
    double referenceGibbsEnergy = balancedReference.gibbsEnergy;
    double collapsedGibbsEnergy = system.getGibbsEnergy();
    if (!Double.isFinite(referenceGibbsEnergy) || !Double.isFinite(collapsedGibbsEnergy)) {
      return;
    }
    double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
    if (referenceGibbsEnergy >= collapsedGibbsEnergy - gibbsTolerance) {
      return;
    }
    restoreBalancedTwoPhaseState(balancedReference);
  }

  /**
   * Restores phase types, fractions, compositions, and K-values from a compact two-phase snapshot.
   *
   * @param balancedReference feasible pre-trial state to restore
   */
  private void restoreBalancedTwoPhaseState(BalancedTwoPhaseState balancedReference) {
    system.setNumberOfPhases(2);
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      system.setPhaseIndex(phaseIndex, phaseIndex);
      system.setPhaseType(phaseIndex, balancedReference.phaseTypes[phaseIndex]);
      system.setBeta(phaseIndex, balancedReference.betas[phaseIndex]);
      for (int componentIndex = 0; componentIndex < balancedReference.compositions[phaseIndex].length; componentIndex++) {
        system.getPhase(phaseIndex).getComponent(componentIndex)
            .setx(balancedReference.compositions[phaseIndex][componentIndex]);
        system.getPhase(phaseIndex).getComponent(componentIndex).setK(balancedReference.kValues[componentIndex]);
      }
    }
    system.normalizeBeta();
    system.init(1);
  }

  /**
   * Retries a suspicious water-bearing single-phase collapse through the ordinary flash path.
   *
   * <p>
   * In some high-pressure CO2/water states the multiphase solver removes an aqueous phase even though the ordinary
   * flash converges to a feasible lower-Gibbs oil/aqueous split. For a screened neutral non-CPA water-rich feed, the
   * retry consumes the cold pre-iteration state rather than cloning phase-storage and cubic-root history from the
   * collapsed endpoint. Other eligible collapses retain the post-removal K-value screen and clone fallback. The
   * ordinary result replaces the collapsed state only after it passes the existing phase-fraction,
   * distinct-composition, material-balance, fugacity, and lower-Gibbs acceptance checks. Reciprocal candidates observe
   * the same thread-local guard, preventing fallback ping-pong.
   * </p>
   */
  private void rescueSinglePhaseWaterBearingEndpoint() {
    boolean hasScreenedColdSeed = !MULTIPHASE_RESCUE_ACTIVE.get().booleanValue() && system.doMultiPhaseCheck()
        && system.getNumberOfPhases() == 1 && multiphaseEndpointRescueSeed != null
        && hasPotentialWaterRichColdSeedInstability();
    if (waterBearingRescueAttempted || (!hasScreenedColdSeed && !shouldRetryCollapsedWaterBearingEndpoint())) {
      return;
    }
    waterBearingRescueAttempted = true;
    system.init(1);
    double referenceGibbsEnergy = system.getGibbsEnergy();
    SystemInterface candidate = hasScreenedColdSeed ? multiphaseEndpointRescueSeed : system.clone();
    if (hasScreenedColdSeed) {
      multiphaseEndpointRescueSeed = null;
    }
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      candidate.setMultiPhaseCheck(false);
      candidate.setEnhancedMultiPhaseCheck(false);
      new TPflash(candidate, candidate.doSolidPhaseCheck()).run();
      if (isLowerGibbsMultiphaseCandidate(candidate, referenceGibbsEnergy)
          && isBalancedEquilibriumCandidate(candidate)) {
        copyFlashStateFrom(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Water-bearing endpoint recovery failed: {}", ex.getMessage());
    } finally {
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Screens a collapsed water-bearing endpoint using retained phase-preference K-values.
   *
   * @return true when a bounded ordinary-flash retry is justified
   */
  private boolean shouldRetryCollapsedWaterBearingEndpoint() {
    if (!system.doMultiPhaseCheck() || system.getNumberOfPhases() != 1 || system.isChemicalSystem() || system.hasIons()
        || solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()
        || MULTIPHASE_RESCUE_ACTIVE.get().booleanValue()) {
      return false;
    }
    boolean hasSubstantialWaterWithSmallK = false;
    boolean hasVolatileNonWaterComponent = false;
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      if (component.getz() <= 1.0e-50) {
        continue;
      }
      double kValue = component.getK();
      if (!Double.isFinite(kValue) || kValue <= 0.0) {
        return false;
      }
      if ("water".equalsIgnoreCase(component.getComponentName())) {
        hasSubstantialWaterWithSmallK = component.getz() >= WATER_RICH_REFINEMENT_FEED_FRACTION_LIMIT
            && kValue < WATER_PHASE_COLLAPSE_WATER_K_UPPER_LIMIT;
      } else if (kValue > WATER_PHASE_COLLAPSE_VOLATILE_K_LOWER_LIMIT) {
        hasVolatileNonWaterComponent = true;
      }
    }
    return hasSubstantialWaterWithSmallK && hasVolatileNonWaterComponent;
  }

  /**
   * Calculates the maximum absolute component material-balance residual.
   *
   * @param candidate system to inspect
   * @return maximum absolute difference between feed and phase-recombined composition, or positive infinity for a
   * non-finite feed or phase state
   */
  private double maximumComponentMaterialBalanceResidual(SystemInterface candidate) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < candidate.getPhase(0).getNumberOfComponents(); componentIndex++) {
      double feedComposition = candidate.getPhase(0).getComponent(componentIndex).getz();
      if (!Double.isFinite(feedComposition)) {
        return Double.POSITIVE_INFINITY;
      }
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < candidate.getNumberOfPhases(); phaseIndex++) {
        double phaseFraction = candidate.getBeta(phaseIndex);
        double phaseComposition = candidate.getPhase(phaseIndex).getComponent(componentIndex).getx();
        if (!Double.isFinite(phaseFraction) || !Double.isFinite(phaseComposition)) {
          return Double.POSITIVE_INFINITY;
        }
        recoveredFeed += phaseFraction * phaseComposition;
        if (!Double.isFinite(recoveredFeed)) {
          return Double.POSITIVE_INFINITY;
        }
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(feedComposition - recoveredFeed));
    }
    return maximumResidual;
  }

  /**
   * Checks whether candidate phases have meaningfully different compositions.
   *
   * @param candidate candidate system to inspect
   * @return true when all phase pairs have distinct active-component compositions
   */
  private boolean hasDistinctPhaseCompositions(SystemInterface candidate) {
    for (int firstPhase = 0; firstPhase < candidate.getNumberOfPhases(); firstPhase++) {
      for (int secondPhase = firstPhase + 1; secondPhase < candidate.getNumberOfPhases(); secondPhase++) {
        double l1Difference = 0.0;
        for (int componentIndex = 0; componentIndex < candidate.getPhase(0).getNumberOfComponents(); componentIndex++) {
          if (candidate.getPhase(0).getComponent(componentIndex).getz() > 1.0e-50) {
            l1Difference += Math.abs(candidate.getPhase(firstPhase).getComponent(componentIndex).getx()
                - candidate.getPhase(secondPhase).getComponent(componentIndex).getx());
          }
        }
        if (l1Difference < 1.0e-4) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Copies phase composition, type, and beta state from a lower-Gibbs candidate.
   *
   * @param source source system whose converged flash state should replace the current state
   */
  private void copyFlashStateFrom(SystemInterface source) {
    system.setNumberOfPhases(source.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < source.getNumberOfPhases(); phaseIndex++) {
      system.setPhaseIndex(phaseIndex, phaseIndex);
      system.setPhase(source.getPhase(phaseIndex).clone(), phaseIndex);
      system.setPhaseType(phaseIndex, source.getPhase(phaseIndex).getType());
      system.setBeta(phaseIndex, source.getBeta(phaseIndex));
    }
    system.normalizeBeta();
    system.init(1);
  }

  /**
   * Repeats an accepted cold ordinary fallback directly on the live system.
   *
   * <p>
   * A multiphase trial can move the active aqueous phase to a different internal storage slot. Copying only the
   * candidate phases back into those mutated slots does not reliably reproduce the candidate cubic-root state. Once an
   * independent cold ordinary candidate has passed the strict acceptance gate, reset the live system to its feed and
   * repeat the same bounded ordinary flash. The recursion guard prevents this nested flash from starting another
   * cross-algorithm fallback.
   * </p>
   *
   * @param acceptedCandidate accepted ordinary candidate providing the feed state
   */
  private void runAcceptedOrdinaryWaterRichFallback(SystemInterface acceptedCandidate) {
    system.setTotalNumberOfMoles(acceptedCandidate.getTotalNumberOfMoles());
    system.setMolarComposition(acceptedCandidate.getzvector());
    system.setNumberOfPhases(2);
    system.setPhaseIndex(0, 0);
    system.setPhaseIndex(1, 1);
    system.setPhaseType(0, PhaseType.GAS);
    system.setPhaseType(1, PhaseType.OIL);
    boolean multiphaseCheck = system.doMultiPhaseCheck();
    try {
      system.setMultiPhaseCheck(false);
      TPflash fallback = new TPflash(system, system.doSolidPhaseCheck());
      fallback.waterRichCrossAlgorithmFallbackAllowed = false;
      fallback.run();
    } finally {
      system.setMultiPhaseCheck(multiphaseCheck);
    }
  }

  /**
   * Refines a feasible multiphase gas/aqueous endpoint when its gas phase has a lower-Gibbs cubic root.
   *
   * <p>
   * {@link TPmultiflash} can converge a balanced gas/aqueous split on a higher-Gibbs cubic root while the ordinary
   * two-phase path reaches the lower root and a slightly adjusted equilibrium composition. A cheap alternate-root
   * comparison screens the converged gas phase before any retry. Only a lower root beyond numerical noise starts an
   * ordinary TP flash on a clone; the candidate is replayed on the live system only when it retains exactly gas and
   * aqueous phases, passes the existing strict phase-fraction, normalization, material-balance, distinct-composition,
   * and fugacity checks, and lowers total extensive Gibbs energy beyond the same tolerance. Three-phase results and
   * chemical, electrolyte, solid, and wax calculations remain on their existing paths.
   * </p>
   */
  private void rescueLowerGibbsMultiphaseAqueousRoot() {
    if (!system.doMultiPhaseCheck() || system.getNumberOfPhases() != 2 || system.isChemicalSystem() || system.hasIons()
        || solidCheck || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()
        || !system.hasPhaseType(PhaseType.GAS) || !system.hasPhaseType(PhaseType.AQUEOUS)
        || !waterRichCrossAlgorithmFallbackAllowed || MULTIPHASE_RESCUE_ACTIVE.get().booleanValue()
        || !isBalancedEquilibriumCandidate(system) || !hasLowerGibbsAlternateGasRoot()) {
      return;
    }

    double referenceGibbsEnergy = system.getGibbsEnergy();
    SystemInterface candidate = system.clone();
    MULTIPHASE_RESCUE_ACTIVE.set(Boolean.TRUE);
    try {
      candidate.setMultiPhaseCheck(false);
      new TPflash(candidate, false).run();
      candidate.init(1);
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
      if (candidate.getNumberOfPhases() == 2 && candidate.hasPhaseType(PhaseType.GAS)
          && candidate.hasPhaseType(PhaseType.AQUEOUS) && isBalancedEquilibriumCandidate(candidate)
          && candidate.getGibbsEnergy() < referenceGibbsEnergy - gibbsTolerance) {
        runAcceptedOrdinaryWaterRichFallback(candidate);
      }
    } catch (Exception ex) {
      logger.debug("Multiphase aqueous lower-Gibbs root refinement failed: {}", ex.getMessage());
    } finally {
      MULTIPHASE_RESCUE_ACTIVE.set(Boolean.FALSE);
    }
  }

  /**
   * Screens the converged gas phase for a lower-Gibbs alternate cubic root.
   *
   * @return true when another cubic root lowers the gas-phase Gibbs energy beyond numerical noise
   */
  private boolean hasLowerGibbsAlternateGasRoot() {
    int gasPhaseIndex = system.getPhaseNumberOfPhase(PhaseType.GAS);
    PhaseInterface gasPhase = system.getPhase(gasPhaseIndex);
    double referenceGibbsEnergy = gasPhase.getGibbsEnergy();
    double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
    for (PhaseType trialRoot : CUBIC_ROOT_PHASE_TYPES) {
      try {
        PhaseInterface trialPhase = gasPhase.clone();
        trialPhase.init(system.getTotalNumberOfMoles(), trialPhase.getNumberOfComponents(), 1, trialRoot,
            system.getBeta(gasPhaseIndex));
        double gibbsReduction = referenceGibbsEnergy - trialPhase.getGibbsEnergy();
        if (Double.isFinite(gibbsReduction) && gibbsReduction > gibbsTolerance) {
          return true;
        }
      } catch (Exception ex) {
        logger.debug("Multiphase gas-root screen failed for {}: {}", trialRoot, ex.getMessage());
      }
    }
    return false;
  }

  /**
   * Selects a lower-Gibbs cubic root for an already-converged ordinary aqueous split.
   *
   * <p>
   * A cubic EOS can have both vapor-like and liquid-like roots at the converged phase composition. The ordinary flash
   * may retain the higher-Gibbs root while the multiphase path retains the lower root, even when both paths return
   * identical phase fractions and compositions. Each cubic root is therefore evaluated on a cloned non-aqueous phase.
   * The live phase is replaced only when the alternate root lowers extensive Gibbs energy and already satisfies
   * component fugacity equality against the unchanged aqueous phase within {@link #PHASE_ROOT_EQUILIBRIUM_TOLERANCE}.
   * Material balance and phase fractions are unchanged.
   * </p>
   *
   * <p>
   * The check is limited to neutral, ordinary, exactly-two-phase aqueous results. Dry hydrocarbon roots are handled by
   * {@link #rescueLowerGibbsHydrocarbonPhaseRoots()}; multiphase-enabled flashes, chemical/electrolyte systems, and
   * solid/wax calculations remain on their existing paths.
   * </p>
   */
  private void rescueLowerGibbsPhaseRoot() {
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() != 2 || system.isChemicalSystem() || system.hasIons()
        || solidCheck || system.isMultiphaseWaxCheck() || !system.hasPhaseType(PhaseType.AQUEOUS)) {
      return;
    }

    int selectedPhase = -1;
    PhaseType selectedRoot = null;
    PhaseInterface selectedPhaseState = null;
    double maximumGibbsReduction = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      PhaseType phaseType = phase.getType();
      if (!(phaseType == PhaseType.GAS || phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID)) {
        continue;
      }
      double currentGibbs = phase.getGibbsEnergy();
      for (PhaseType trialRoot : CUBIC_ROOT_PHASE_TYPES) {
        try {
          PhaseInterface trialPhase = phase.clone();
          trialPhase.init(system.getTotalNumberOfMoles(), trialPhase.getNumberOfComponents(), 1, trialRoot,
              system.getBeta(phaseIndex));
          for (int componentIndex = 0; componentIndex < trialPhase.getNumberOfComponents(); componentIndex++) {
            trialPhase.getComponent(componentIndex).fugcoef(trialPhase);
          }
          double gibbsReduction = currentGibbs - trialPhase.getGibbsEnergy();
          double fugacityResidual = maximumLogFugacityResidualWithReplacement(phaseIndex, trialPhase);
          if (Double.isFinite(gibbsReduction) && gibbsReduction > maximumGibbsReduction
              && fugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
            maximumGibbsReduction = gibbsReduction;
            selectedPhase = phaseIndex;
            selectedRoot = trialRoot;
            selectedPhaseState = trialPhase;
          }
        } catch (Exception ex) {
          logger.debug("Alternate phase-root comparison failed for {}: {}", trialRoot, ex.getMessage());
        }
      }
    }
    double gibbsTolerance = Math.max(1.0e-6, Math.abs(system.getGibbsEnergy()) * 1.0e-8);
    if (selectedPhase < 0 || maximumGibbsReduction <= gibbsTolerance) {
      return;
    }

    int storageIndex = system.getPhaseIndex(selectedPhase);
    system.setPhase(selectedPhaseState, storageIndex);
    system.setPhaseType(selectedPhase, selectedRoot);
  }

  /**
   * Calculates the largest equilibrium residual after replacing one phase with an alternate cubic root.
   *
   * @param phaseIndex active phase to replace
   * @param replacement initialized replacement phase
   * @return maximum absolute log-fugacity residual
   */
  private double maximumLogFugacityResidualWithReplacement(int phaseIndex, PhaseInterface replacement) {
    int otherPhaseIndex = phaseIndex == 0 ? 1 : 0;
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < replacement.getNumberOfComponents(); componentIndex++) {
      if (system.getPhase(0).getComponent(componentIndex).getz() <= 1.0e-50) {
        continue;
      }
      double replacementLogFugacity = Math
          .log(Math.max(replacement.getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(replacement.getComponent(componentIndex).getFugacityCoefficient());
      double otherLogFugacity = Math
          .log(Math.max(system.getPhase(otherPhaseIndex).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(otherPhaseIndex).getComponent(componentIndex).getFugacityCoefficient());
      if (!Double.isFinite(replacementLogFugacity) || !Double.isFinite(otherLogFugacity)) {
        return Double.POSITIVE_INFINITY;
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(replacementLogFugacity - otherLogFugacity));
    }
    return maximumResidual;
  }

  /**
   * Corrects an inconsistent vapor/liquid cubic-root assignment in an ordinary hydrocarbon flash.
   *
   * <p>
   * Near a critical boundary the ordinary two-phase iteration can converge the light and heavy compositions while
   * retaining the liquid cubic root on the light phase and the vapor root on the heavy phase. A normally ordered split
   * can likewise retain one stale cubic root and therefore fail component fugacity equality after convergence. Both are
   * higher-Gibbs states than the same composition split with consistently selected roots. A cheap molar-mass and
   * current fugacity-residual check avoids trial-root work on already consistent results. An inverted split evaluates
   * both roots together; a normally ordered split evaluates both roots on one phase at a time while the other remains
   * fixed. A replacement is accepted only when it lowers extensive Gibbs energy beyond numerical noise and satisfies
   * component fugacity equality within {@link #PHASE_ROOT_EQUILIBRIUM_TOLERANCE}. Compositions, phase fractions, and
   * material balance are unchanged.
   * </p>
   *
   * <p>
   * The safeguard is limited to neutral, ordinary, exactly-two-phase hydrocarbon/inert systems. Multiphase-enabled,
   * aqueous, chemical/electrolyte, and solid/wax calculations retain their existing paths.
   * </p>
   */
  private void rescueLowerGibbsHydrocarbonPhaseRoots() {
    if (system.doMultiPhaseCheck() || system.getNumberOfPhases() != 2 || system.isChemicalSystem() || system.hasIons()
        || solidCheck || system.isMultiphaseWaxCheck() || system.hasPhaseType(PhaseType.AQUEOUS)) {
      return;
    }

    int gasPhaseIndex = -1;
    int liquidPhaseIndex = -1;
    boolean hasHydrocarbon = false;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType phaseType = system.getPhase(phaseIndex).getType();
      if (phaseType == PhaseType.GAS) {
        gasPhaseIndex = phaseIndex;
      } else if (phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID) {
        liquidPhaseIndex = phaseIndex;
      } else {
        return;
      }
    }
    if (gasPhaseIndex < 0 || liquidPhaseIndex < 0) {
      return;
    }
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if (component.getz() <= 1.0e-50) {
        continue;
      }
      if (!component.isHydrocarbon() && !component.isInert()) {
        return;
      }
      hasHydrocarbon |= component.isHydrocarbon();
    }
    if (!hasHydrocarbon) {
      return;
    }

    boolean invertedCompositionOrder = system.getPhase(gasPhaseIndex).getMolarMass() > system.getPhase(liquidPhaseIndex)
        .getMolarMass();
    double currentFugacityResidual = maximumLogFugacityResidual(system.getPhase(gasPhaseIndex),
        system.getPhase(liquidPhaseIndex));
    if (!invertedCompositionOrder && currentFugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
      return;
    }
    if (!invertedCompositionOrder) {
      rescueLowerGibbsIndividualPhaseRoot();
      return;
    }

    try {
      PhaseInterface lightPhase = system.getPhase(liquidPhaseIndex).clone();
      lightPhase.init(system.getTotalNumberOfMoles(), lightPhase.getNumberOfComponents(), 1, PhaseType.GAS,
          system.getBeta(liquidPhaseIndex));
      PhaseInterface heavyPhase = system.getPhase(gasPhaseIndex).clone();
      heavyPhase.init(system.getTotalNumberOfMoles(), heavyPhase.getNumberOfComponents(), 1, PhaseType.LIQUID,
          system.getBeta(gasPhaseIndex));
      for (int componentIndex = 0; componentIndex < lightPhase.getNumberOfComponents(); componentIndex++) {
        lightPhase.getComponent(componentIndex).fugcoef(lightPhase);
        heavyPhase.getComponent(componentIndex).fugcoef(heavyPhase);
      }
      double fugacityResidual = maximumLogFugacityResidual(lightPhase, heavyPhase);
      double trialGibbsEnergy = lightPhase.getGibbsEnergy() + heavyPhase.getGibbsEnergy();
      double currentGibbsEnergy = system.getGibbsEnergy();
      double gibbsReduction = currentGibbsEnergy - trialGibbsEnergy;
      double gibbsTolerance = Math.max(1.0e-6, Math.abs(currentGibbsEnergy) * 1.0e-8);
      if (!Double.isFinite(gibbsReduction) || gibbsReduction <= gibbsTolerance
          || fugacityResidual >= PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
        return;
      }

      lightPhase.setType(PhaseType.GAS);
      heavyPhase.setType(PhaseType.OIL);
      int lightStorageIndex = system.getPhaseIndex(liquidPhaseIndex);
      int heavyStorageIndex = system.getPhaseIndex(gasPhaseIndex);
      system.setPhase(lightPhase, lightStorageIndex);
      system.setPhase(heavyPhase, heavyStorageIndex);
      system.setPhaseType(liquidPhaseIndex, PhaseType.GAS);
      system.setPhaseType(gasPhaseIndex, PhaseType.OIL);
      system.setPhaseIndex(0, lightStorageIndex);
      system.setPhaseIndex(1, heavyStorageIndex);
    } catch (Exception ex) {
      logger.debug("Hydrocarbon phase-root comparison failed: {}", ex.getMessage());
    }
  }

  /**
   * Replaces one stale cubic root in an otherwise normally ordered gas/liquid split.
   *
   * <p>
   * Each available root is initialized on a clone of one phase while the other phase remains unchanged. The best
   * candidate must lower extensive Gibbs energy and restore component fugacity equality. The phase composition,
   * fraction, storage order, and public phase label remain unchanged.
   * </p>
   */
  private void rescueLowerGibbsIndividualPhaseRoot() {
    int selectedPhaseIndex = -1;
    PhaseType selectedPhaseType = null;
    PhaseType selectedRoot = null;
    PhaseInterface selectedPhase = null;
    double maximumGibbsReduction = 0.0;
    double currentGibbsEnergy = system.getGibbsEnergy();

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseType originalPhaseType = system.getPhase(phaseIndex).getType();
      double currentPhaseGibbsEnergy = system.getPhase(phaseIndex).getGibbsEnergy();
      for (PhaseType trialRoot : CUBIC_ROOT_PHASE_TYPES) {
        try {
          PhaseInterface trialPhase = system.getPhase(phaseIndex).clone();
          trialPhase.init(system.getTotalNumberOfMoles(), trialPhase.getNumberOfComponents(), 1, trialRoot,
              system.getBeta(phaseIndex));
          for (int componentIndex = 0; componentIndex < trialPhase.getNumberOfComponents(); componentIndex++) {
            trialPhase.getComponent(componentIndex).fugcoef(trialPhase);
          }
          double trialGibbsEnergy = currentGibbsEnergy - currentPhaseGibbsEnergy + trialPhase.getGibbsEnergy();
          double gibbsReduction = currentGibbsEnergy - trialGibbsEnergy;
          double fugacityResidual = maximumLogFugacityResidualWithReplacement(phaseIndex, trialPhase);
          if (Double.isFinite(gibbsReduction) && gibbsReduction > maximumGibbsReduction
              && fugacityResidual < PHASE_ROOT_EQUILIBRIUM_TOLERANCE) {
            maximumGibbsReduction = gibbsReduction;
            selectedPhaseIndex = phaseIndex;
            selectedPhaseType = originalPhaseType;
            selectedRoot = trialRoot;
            selectedPhase = trialPhase;
          }
        } catch (Exception ex) {
          logger.debug("Individual phase-root comparison failed for phase {} root {}: {}", phaseIndex, trialRoot,
              ex.getMessage());
        }
      }
    }

    double gibbsTolerance = Math.max(1.0e-6, Math.abs(currentGibbsEnergy) * 1.0e-8);
    if (selectedPhaseIndex < 0 || maximumGibbsReduction <= gibbsTolerance) {
      return;
    }

    selectedPhase.setType(selectedPhaseType);
    system.setPhase(selectedPhase, system.getPhaseIndex(selectedPhaseIndex));
    system.setPhaseType(selectedPhaseIndex, selectedRoot);
  }

  /**
   * Calculates the largest equilibrium residual between two initialized trial phases.
   *
   * @param firstPhase first initialized phase
   * @param secondPhase second initialized phase
   * @return maximum absolute log-fugacity residual
   */
  private double maximumLogFugacityResidual(PhaseInterface firstPhase, PhaseInterface secondPhase) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < firstPhase.getNumberOfComponents(); componentIndex++) {
      if (system.getPhase(0).getComponent(componentIndex).getz() <= 1.0e-50) {
        continue;
      }
      double firstLogFugacity = Math.log(Math.max(firstPhase.getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(firstPhase.getComponent(componentIndex).getFugacityCoefficient());
      double secondLogFugacity = Math.log(Math.max(secondPhase.getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(secondPhase.getComponent(componentIndex).getFugacityCoefficient());
      if (!Double.isFinite(firstLogFugacity) || !Double.isFinite(secondLogFugacity)) {
        return Double.POSITIVE_INFINITY;
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(firstLogFugacity - secondLogFugacity));
    }
    return maximumResidual;
  }

  /**
   * Post-convergence acceptance test that collapses a spurious multiphase result to a single phase when the converged
   * multiphase Gibbs energy is higher than the reference single-phase Gibbs captured at the start of
   * {@link #runInternal()}.
   *
   * <p>
   * This closes a structural gap in TPflash/TPmultiflash: the two-phase Newton/successive- substitution loop converges
   * to a stationary point of the equilibrium equations that is a local Gibbs minimum on the multiphase manifold but is
   * not guaranteed to be lower than the homogeneous single-phase Gibbs at the overall feed composition. Michelsen's
   * stability analysis in TPmultiflash only decides whether to <em>add</em> a phase; it never tests whether the
   * converged multiphase state should be <em>collapsed</em>. Near the cubic-EOS critical region (typically PR/SRK with
   * light + heavy hydrocarbons) this manifests as isolated low-density "spike" cells in density / phase maps where the
   * Newton has locked onto a metastable VLE root.
   * </p>
   *
   * <p>
   * The reference single-phase Gibbs ({@link #referenceSinglePhaseGibbs}) is the better of the two cubic roots (gas and
   * liquid) evaluated on the feed at the start of the flash, so the comparison is exact, requires no cloning, and adds
   * only a single subtraction beyond the {@code getGibbsEnergy()} call (no extra {@code init} is performed —
   * {@code init(1)} from the preceding {@code orderByDensity} is sufficient). A threshold of
   * {@link #SPURIOUS_MULTIPHASE_GIBBS_DROP_THRESHOLD_J} joules avoids triggering on numerical noise. Skipped for
   * chemical / electrolyte systems.
   * </p>
   *
   * <p>
   * Hot-path cost when the rescue does not trigger: a handful of field reads plus one
   * {@link SystemInterface#getGibbsEnergy()} call (which sums per-phase Gibbs). Guards are ordered cheapest-first so
   * single-phase results — the dominant case after the {@code removePhase} cleanup immediately preceding this call —
   * exit after two field reads.
   * </p>
   */
  private void rescueSpuriousMultiphaseEndpoint() {
    // Cheapest-first guards combined into a single short-circuit expression. Single-phase
    // results (the dominant case after the removePhase cleanup just above the call site) bail
    // on the first term without touching any property method.
    final int numPhases = system.getNumberOfPhases();
    if (numPhases < 2 || !system.doMultiPhaseCheck() || referenceSinglePhaseType == null
        || Double.isNaN(referenceSinglePhaseGibbs) || system.getPhase(0).getNumberOfComponents() <= 1) {
      return;
    }
    // Smallest beta — fast path for the 2-phase case (overwhelmingly common); generic loop
    // for 3+. Reject if any phase fraction is already negligible: the post-init cleanup will
    // collapse it and the multiphase Gibbs is essentially equal to the single-phase value.
    double smallestBeta;
    if (numPhases == 2) {
      final double b0 = system.getBeta(0);
      final double b1 = system.getBeta(1);
      smallestBeta = (b0 < b1) ? b0 : b1;
    } else {
      smallestBeta = system.getBeta(0);
      for (int i = 1; i < numPhases; i++) {
        final double b = system.getBeta(i);
        if (b < smallestBeta) {
          smallestBeta = b;
        }
      }
    }
    if (smallestBeta < 1.0e-3) {
      return;
    }
    // hasIons() loops components; defer until we know we actually have a candidate.
    if (system.isChemicalSystem() || system.hasIons()) {
      return;
    }
    // Compare current multiphase Gibbs to the cached single-phase reference at the feed.
    // Negated > guards against NaN naturally. init(1) (from the preceding orderByDensity) is
    // sufficient — getGibbsEnergy() is used after init(1) throughout runInternal().
    final double currentGibbs = system.getGibbsEnergy();
    if (!(currentGibbs - referenceSinglePhaseGibbs > SPURIOUS_MULTIPHASE_GIBBS_DROP_THRESHOLD_J)) {
      return;
    }
    // Spurious multiphase confirmed: single-phase reference is more stable. Collapse.
    if (logger.isDebugEnabled()) {
      logger.debug("Collapsing spurious multiphase result: G_multiphase={} J, G_reference_single={} J ({}), drop={} J",
          currentGibbs, referenceSinglePhaseGibbs, referenceSinglePhaseType, currentGibbs - referenceSinglePhaseGibbs);
    }
    collapseToReferenceSinglePhase();
  }

  /**
   * Collapses a converged two-phase result to a single phase when the two phases are essentially identical, i.e. a
   * trivial solution of the flash equations.
   *
   * <p>
   * Near the critical point and along the dew line a TP flash can converge to a trivial split in which both phases
   * carry the overall feed composition (all K-values approximately unity, equal densities). Such a split has the same
   * Gibbs energy as the single-phase feed, so {@link #rescueSpuriousMultiphaseEndpoint()} — which only collapses
   * results whose Gibbs energy is strictly higher than the single-phase reference — does not catch it. Splitting a
   * fluid into two identical copies is physically meaningless (it is a single phase), so this guard removes the
   * artifact and keeps the reported phase boundary consistent with the phase-envelope saturation solver. Only the
   * two-phase case is handled; genuine two-phase splits have compositionally distinct phases (even when the incipient
   * phase fraction is tiny) and are left untouched. Skipped for chemical / electrolyte systems.
   * </p>
   */
  private void collapseTrivialMultiphaseSplit() {
    if (system.getNumberOfPhases() != 2 || !system.doMultiPhaseCheck() || referenceSinglePhaseType == null
        || system.isChemicalSystem() || system.hasIons()) {
      return;
    }
    neqsim.thermo.phase.PhaseInterface phaseA = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface phaseB = system.getPhase(1);
    int numberOfComponents = phaseA.getNumberOfComponents();
    if (numberOfComponents <= 1) {
      return;
    }
    double maxCompositionDifference = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double difference = Math
          .abs(phaseA.getComponent(componentIndex).getx() - phaseB.getComponent(componentIndex).getx());
      if (difference > maxCompositionDifference) {
        maxCompositionDifference = difference;
      }
    }
    if (maxCompositionDifference >= TRIVIAL_SPLIT_COMPOSITION_TOLERANCE) {
      return;
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Collapsing trivial two-phase split: max composition difference {} < {}", maxCompositionDifference,
          TRIVIAL_SPLIT_COMPOSITION_TOLERANCE);
    }
    collapseToReferenceSinglePhase();
  }

  /**
   * Rejects an unnormalized aqueous trial phase created while checking an already stable single-phase state.
   *
   * <p>
   * The multiphase stability path can seed an aqueous trial phase at a small positive phase fraction. If the beta
   * solver does not move that seed, its composition can remain unnormalized and survive the generic minimum-beta
   * cleanup. Such a structurally invalid trial must not replace the accepted homogeneous state.
   * </p>
   *
   * <p>
   * This guard deliberately does not impose a universal material-balance or fugacity-residual threshold. Some
   * specialized fluid and solid-phase models use model-specific convergence and refinement paths, so normalized
   * endpoints remain available to those paths. Chemical, ionic, solid, wax, and non-aqueous endpoints are excluded.
   * </p>
   */
  private void rejectUnnormalizedAqueousEndpointAfterStableSinglePhase() {
    if (system.getNumberOfPhases() != 2 || system.isChemicalSystem() || system.hasIons() || solidCheck
        || system.isMultiphaseWaxCheck() || referenceSinglePhaseType == null
        || !system.hasPhaseType(PhaseType.AQUEOUS)) {
      return;
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double phaseComposition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        if (!Double.isFinite(phaseComposition) || phaseComposition < 0.0 || phaseComposition > 1.0) {
          if (logger.isDebugEnabled()) {
            logger.debug("Rejecting invalid aqueous endpoint composition: phase={}, component={}, x={}", phaseIndex,
                componentIndex, phaseComposition);
          }
          collapseToReferenceSinglePhase();
          return;
        }
        compositionTotal += phaseComposition;
      }
      if (!Double.isFinite(compositionTotal)
          || Math.abs(compositionTotal - 1.0) > AQUEOUS_SEED_COMPOSITION_NORMALIZATION_TOLERANCE) {
        if (logger.isDebugEnabled()) {
          logger.debug("Rejecting unnormalized aqueous endpoint composition: phase={}, sum(x)={}", phaseIndex,
              compositionTotal);
        }
        collapseToReferenceSinglePhase();
        return;
      }
    }
  }

  /**
   *
   * <p>
   * The collapse must not call {@code system.init(0)} because that method resets a
   * {@link neqsim.thermo.system.SystemThermo} phase list to its default two active phases. Instead the selected active
   * phase is explicitly reset to the overall feed composition and reinitialized at level 1 so the chosen cubic root is
   * recalculated without reopening a duplicate phase.
   * </p>
   */
  private void collapseToReferenceSinglePhase() {
    system.setNumberOfPhases(1);
    system.setPhaseIndex(0, 0);
    system.setBeta(0, 1.0);
    system.setPhaseType(0, referenceSinglePhaseType);
    resetSinglePhaseCompositionToFeed();
    system.init(1, 0);
  }

  /**
   * Normalizes active phase fractions before returning the final TPflash state.
   *
   * <p>
   * Late phase-removal and rescue paths can leave the active beta values slightly below or above unity even when the
   * remaining phase compositions are valid. Calling this helper at a validated acceptance point restores phase-fraction
   * closure and reinitializes level 1 properties for the adjusted phase amounts.
   * </p>
   */
  private void normalizeActivePhaseFractions() {
    int numberOfPhases = system.getNumberOfPhases();
    if (numberOfPhases == 1) {
      double beta = system.getBeta(0);
      if (Double.isFinite(beta) && beta > 0.0 && Math.abs(beta - 1.0) >= 1.0e-12) {
        system.normalizeBeta();
        system.init(1);
      }
      return;
    }
    if (numberOfPhases == 2) {
      double beta0 = system.getBeta(0);
      double beta1 = system.getBeta(1);
      if (!Double.isFinite(beta0) || !Double.isFinite(beta1) || beta0 < 0.0 || beta1 < 0.0) {
        return;
      }
      double betaTotal = beta0 + beta1;
      if (betaTotal <= 0.0 || Math.abs(betaTotal - 1.0) < 1.0e-12) {
        return;
      }
      system.normalizeBeta();
      system.init(1);
      return;
    }
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      double beta = system.getBeta(phaseIndex);
      if (!Double.isFinite(beta) || beta < 0.0) {
        return;
      }
      betaTotal += beta;
    }
    if (betaTotal <= 0.0 || Math.abs(betaTotal - 1.0) < 1.0e-12) {
      return;
    }
    system.normalizeBeta();
    system.init(1);
  }

  /**
   * Resets phase zero composition to the overall feed composition before a single-phase collapse.
   */
  private void resetSinglePhaseCompositionToFeed() {
    neqsim.thermo.phase.PhaseInterface phase = system.getPhase(0);
    int numberOfComponents = phase.getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = phase.getComponent(componentIndex);
      component.setx(component.getz());
    }
    phase.normalize();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TPflash other = (TPflash) obj;
    // Compare relevant fields for equality
    if (Double.compare(presdiff, other.presdiff) != 0) {
      return false;
    }
    if (solidCheck != other.solidCheck) {
      return false;
    }
    if (system == null) {
      if (other.system != null) {
        return false;
      }
    } else if (!system.equals(other.system)) {
      return false;
    }
    return true;
  }
}
