package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * TPflash class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash.class);
  /** Local lower-temperature seed step for multiphase endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_TEMPERATURE_STEP = 2.0;
  /** Lower sum(zK) bound for gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_K_LOWER_LIMIT = 0.95;
  /** Upper sum(zK) bound for gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT = 1.05;
  /** Lower sum(z/K) bound for gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_LOWER_LIMIT = 1.05;
  /** Upper sum(z/K) bound for gas endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT = 2.0;
  /** Lower sum(zK) bound for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_LOWER_LIMIT = 0.95;
  /** Upper sum(zK) bound for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_UPPER_LIMIT = 1.20;
  /** Minimum sum(z/K) bound for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT = 5.0;
  /** Minimum log K spread for liquid endpoint rescue. */
  private static final double MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT = 3.0;
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
   * <p>
   * Constructor for TPflash.
   * </p>
   */
  public TPflash() {}

  /**
   * <p>
   * Constructor for TPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPflash(SystemInterface system) {
    this.system = system;
    lnOldOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldoldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
  }

  /**
   * <p>
   * Constructor for TPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPflash(SystemInterface system, boolean checkForSolids) {
    this(system);
    solidCheck = checkForSolids;
  }

  /**
   * <p>
   * sucsSubs. Successive substitutions.
   * </p>
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
   * <p>
   * accselerateSucsSubs. GDEM with 2-eigenvalue acceleration when sufficient history is available,
   * falling back to standard DEM (Michelsen 1982b, Risnes et al. 1981). The GDEM formulation
   * follows Risnes &amp; Dalen (1984) and Michelsen &amp; Mollerup (2007, section 9.5).
   * </p>
   */
  public void accselerateSucsSubs() {
    int nc = system.getPhase(0).getNumberOfComponents();

    // Save pre-acceleration lnK state for rollback on failure
    double[] savedLnK = new double[nc];
    System.arraycopy(lnK, 0, savedLnK, 0, nc);

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

    neqsim.thermo.phase.PhaseInterface ph0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface ph1 = system.getPhase(1);
    if (!useGDEM) {
      double lambdaFactor = lambda / (1.0 - lambda);
      for (i = 0; i < nc; i++) {
        lnK[i] += lambdaFactor * deltalnK[i];
        double expK = Math.exp(lnK[i]);
        ph0.getComponent(i).setK(expK);
        ph1.getComponent(i).setK(expK);
      }
    } else {
      for (i = 0; i < nc; i++) {
        lnK[i] += mu1 * deltalnK[i] + mu2 * oldDeltalnK[i];
        double expK = Math.exp(lnK[i]);
        ph0.getComponent(i).setK(expK);
        ph1.getComponent(i).setK(expK);
      }
    }
    double oldBeta = system.getBeta();
    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (Exception ex) {
      system.setBeta(rachfordRice.getBeta()[0]);
      if (system.getBeta() > 1.0 - phaseFractionMinimumLimit
          || system.getBeta() < phaseFractionMinimumLimit) {
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
      System.arraycopy(savedLnK, 0, lnK, 0, nc);
      for (i = 0; i < nc; i++) {
        double expK = Math.exp(savedLnK[i]);
        ph0.getComponent(i).setK(expK);
        ph1.getComponent(i).setK(expK);
      }
      system.setBeta(oldBeta);
      system.calc_x_y();
      system.init(1);
    }
  }

  /**
   * <p>
   * setNewK.
   * </p>
   */
  public void setNewK() {
    neqsim.thermo.phase.PhaseInterface phase0 = system.getPhase(0);
    neqsim.thermo.phase.PhaseInterface phase1 = system.getPhase(1);
    int nc = phase0.getNumberOfComponents();
    for (i = 0; i < nc; i++) {
      lnOldOldOldK[i] = lnOldOldK[i];
      lnOldOldK[i] = lnOldK[i];
      lnOldK[i] = lnK[i];
      lnK[i] = Math.log(phase1.getComponent(i).getFugacityCoefficient()
          / phase0.getComponent(i).getFugacityCoefficient());

      oldoldDeltalnK[i] = lnOldOldK[i] - lnOldOldOldK[i];
      oldDeltalnK[i] = lnOldK[i] - lnOldOldK[i];
      deltalnK[i] = lnK[i] - lnOldK[i];
    }
  }

  /**
   * <p>
   * resetK.
   * </p>
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
      if (disableWarmStart) {
        neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarmStart);
      }
    }
  }

  /**
   * Internal flash body; the public {@link #run()} wraps this with a warm-start guard for systems
   * carrying a stale 3-phase state.
   */
  private void runInternal() {
    resetStabilityDiagnostics();
    findLowestGibbsPhaseIsChecked = false;
    int minGibbsPhase = 0;
    double minimumGibbsEnergy = 0;

    system.init(0);
    system.init(1);

    if ((system.getPhase(0).getGibbsEnergy()
        * (1.0 - Math.signum(system.getPhase(0).getGibbsEnergy()) * 1e-8)) < system.getPhase(1)
            .getGibbsEnergy()) {
      minGibbsPhase = 0;
    } else {
      minGibbsPhase = 1;
    }
    // logger.debug("minimum gibbs phase " + minGibbsPhase);
    minimumGibbsEnergy = system.getPhase(minGibbsPhase).getGibbsEnergy();

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
      minGibsLogFugCoef[i] =
          system.getPhase(minGibbsPhase).getComponent(i).getLogFugacityCoefficient();
    }

    presdiff = system.getPhase(1).getPressure() / system.getPhase(0).getPressure();
    if (Math.abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) > 1e-12) {
      for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        system.getPhase(0).getComponent(i)
            .setK(system.getPhase(0).getComponent(i).getK() * presdiff);
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
      sucsSubs();
    }

    // Performs three iterations of successive substitution
    for (int k = 0; k < 3; k++) {
      if (system.getBeta() < (1.0 - 1.1 * phaseFractionMinimumLimit)
          && system.getBeta() > (1.1 * phaseFractionMinimumLimit)) {
        sucsSubs();
        if ((system.getGibbsEnergy() - minimumGibbsEnergy)
            / Math.abs(minimumGibbsEnergy) < -1e-12) {
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
                  + Math.log(system.getPhase(0).getComponent(i).getx()) - minGibsPhaseLogZ[i]
                  - minGibsLogFugCoef[i]);
          tpdx += system.getPhase(1).getComponent(i).getx()
              * (Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
                  + Math.log(system.getPhase(1).getComponent(i).getx()) - minGibsPhaseLogZ[i]
                  - minGibsLogFugCoef[i]);
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
            system.getPhase(0).getComponent(i)
                .setK(Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
                    - minGibsLogFugCoef[i]) * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else if (tpdy < 0) {
          for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            // Preserve ion K-values - they don't participate in VLE
            if (system.getPhase(0).getComponent(i).getK() < 1e-30) {
              continue;
            }
            system.getPhase(0).getComponent(i)
                .setK(Math
                    .exp(minGibsLogFugCoef[i]
                        - Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()))
                    * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else {
          passedTests = true;
        }
      }
    }

    if (passedTests || (dgonRT > 0 && tpdx > 0 && tpdy > 0) || Double.isNaN(system.getBeta())) {
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
        if (system.doMultiPhaseCheck()) {
          // logger.info("one phase flash is stable - checking multiphase flash....");
          TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
          operation.run();
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
        rescueSinglePhaseMultiphaseEndpoint();

        // Chemical equilibrium for stable single-phase case
        if (system.isChemicalSystem()) {
          for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
            String phaseType = system.getPhase(phaseNum).getPhaseTypeName();
            if ("aqueous".equalsIgnoreCase(phaseType) || "liquid".equalsIgnoreCase(phaseType)) {
              system.getChemicalReactionOperations().solveChemEq(phaseNum, 0);
              system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);
            }
          }
          system.init(1);
        }
        return;
      }
    }

    setNewK();

    gibbsEnergy = system.getGibbsEnergy();
    gibbsEnergyOld = gibbsEnergy;

    // Checks if gas or oil is the most stable phase
    PhaseType originalPhaseType0 = system.getPhase(0).getType();
    double gasgib = system.getPhase(0).getGibbsEnergy();
    system.setPhaseType(0, PhaseType.LIQUID);
    system.init(1, 0);
    double liqgib = system.getPhase(0).getGibbsEnergy();

    if (gasgib * (1.0 - Math.signum(gasgib) * 1e-8) < liqgib) {
      system.setPhaseType(0, PhaseType.GAS);
    } else {
      system.setPhaseType(0, PhaseType.LIQUID);
    }

    if (system.doMultiPhaseCheck() && originalPhaseType0 == PhaseType.OIL) {
      system.setPhaseType(0, PhaseType.OIL);
    }
    system.init(1);

    // Reduced acceleration interval for faster convergence
    int accelerateInterval = 5;
    int newtonLimit = 12;
    int timeFromLastGibbsFail = 0;

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

        if (iterations < activeNewtonLimit || system.isChemicalSystem()
            || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (timeFromLastGibbsFail > 6 && (iterations % activeAccelerateInterval) == 0
              && !(system.isChemicalSystem() || system.doSolidPhaseCheck())) {
            accselerateSucsSubs();
          } else {
            sucsSubs();
          }
        } else if (iterations >= activeNewtonLimit
            && (!shouldApplyEnhancedMultiPhaseCheck() || deviation < 0.05) && Math
                .abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) < 1e-5) {
          // Recreate the second-order solver only when needed: never created yet, or the
          // component count changed (e.g. solid precipitation removed a component, or the
          // flash instance is being reused on a different system). Avoids re-allocating
          // Jacobian / EJML buffers every time iterations reaches the Newton trigger.
          if (secondOrderSolver == null || secondOrderSolver
              .getNumberOfComponents() != system.getPhases()[0].getNumberOfComponents()) {
            secondOrderSolver = new SysNewtonRhapsonTPflash(system, 2,
                system.getPhases()[0].getNumberOfComponents());
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
            || system.getBeta() > (1 - phaseFractionMinimumLimit * 1.01))
            && !system.isChemicalSystem() && timeFromLastGibbsFail > 1) {
          resetK();
          timeFromLastGibbsFail = 0;
        } else {
          timeFromLastGibbsFail++;
          setNewK();
        }
      } while ((deviation > 1e-10) && (iterations < maxNumberOfIterations));

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
            chemdev +=
                Math.abs(xchem[i] - system.getPhase(phaseNum).getComponent(i).getx()) / xchem[i];
          }
        }
        diffChem = Math.abs(oldChemDiff - chemdev);
      }
      // logger.info("chemdev: " + chemdev + " iter: " + totiter);
      totiter++;
    } while ((diffChem > 1e-6 && chemdev > 1e-6 && totiter < 300)
        || (system.isChemicalSystem() && totiter < 2));
    if (system.isChemicalSystem()) {
      sucsSubs();
    }
    if (system.doMultiPhaseCheck()) {
      TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
      operation.run();
      rescueSinglePhaseMultiphaseEndpoint();
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
    rescueSinglePhaseMultiphaseEndpoint();
    system.orderByDensity();
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("Final init after orderByDensity failed: " + ex.getMessage());
    }
    rescueSinglePhaseMultiphaseEndpoint();

    // Final chemical equilibrium call after all phase reordering
    // This ensures chemical equilibrium is solved on the final phase configuration
    if (system.isChemicalSystem()) {
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
   * Retries a single-phase hydrocarbon endpoint with a nearby multiphase seed.
   *
   * <p>
   * Near phase boundaries the cold Wilson-seeded TP flash may converge to a local one-phase
   * endpoint even though a nearby two-phase seed converges to a lower-Gibbs solution at the target
   * pressure and temperature. This guarded retry is only used when the user has explicitly enabled
   * multiphase checking and the ordinary TPmultiflash cleanup still leaves one hydrocarbon phase.
   * </p>
   */
  private void rescueSinglePhaseMultiphaseEndpoint() {
    if (!shouldRunMultiphaseEndpointRescue()) {
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
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
      double seedTemperature =
          Math.max(1.0, targetTemperature - MULTIPHASE_RESCUE_TEMPERATURE_STEP);
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
   * Checks if the endpoint rescue should run for the current flash result.
   *
   * @return true when the result is a single hydrocarbon phase from an explicit multiphase flash
   */
  private boolean shouldRunMultiphaseEndpointRescue() {
    if (MULTIPHASE_RESCUE_ACTIVE.get().booleanValue() || !system.doMultiPhaseCheck()
        || system.getNumberOfPhases() != 1 || system.getPhase(0).getNumberOfComponents() <= 1
        || system.isChemicalSystem() || system.hasIons()) {
      return false;
    }
    PhaseType phaseType = system.getPhase(0).getType();
    if (!(phaseType == PhaseType.GAS || phaseType == PhaseType.OIL
        || phaseType == PhaseType.LIQUID)) {
      return false;
    }
    boolean hasHydrocarbon = false;
    for (int componentIndex = 0; componentIndex < system.getPhase(0)
        .getNumberOfComponents(); componentIndex++) {
      if (system.getPhase(0).getComponent(componentIndex).getz() < 1.0e-50) {
        continue;
      }
      if ("water"
          .equalsIgnoreCase(system.getPhase(0).getComponent(componentIndex).getComponentName())) {
        return false;
      }
      if (!system.getPhase(0).getComponent(componentIndex).isHydrocarbon()
          && !system.getPhase(0).getComponent(componentIndex).isInert()) {
        return false;
      }
      if (system.getPhase(0).getComponent(componentIndex).isHydrocarbon()) {
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
  private boolean hasPotentialMultiphaseEndpoint(PhaseType phaseType) {
    double sumZK = 0.0;
    double sumZOverK = 0.0;
    double maxAbsLogK = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      double z = system.getPhase(0).getComponent(componentIndex).getz();
      if (z < 1.0e-50) {
        continue;
      }
      double kValue = system.getPhase(0).getComponent(componentIndex).getK();
      if (kValue <= 0.0 || Double.isNaN(kValue) || Double.isInfinite(kValue)) {
        return true;
      }
      sumZK += z * kValue;
      sumZOverK += z / kValue;
      maxAbsLogK = Math.max(maxAbsLogK, Math.abs(Math.log(kValue)));
    }
    if (phaseType == PhaseType.GAS) {
      return sumZK > MULTIPHASE_RESCUE_GAS_SUM_Z_K_LOWER_LIMIT
          && sumZK < MULTIPHASE_RESCUE_GAS_SUM_Z_K_UPPER_LIMIT
          && sumZOverK > MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_LOWER_LIMIT
          && sumZOverK < MULTIPHASE_RESCUE_GAS_SUM_Z_OVER_K_UPPER_LIMIT;
    }
    return sumZK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_LOWER_LIMIT
        && sumZK < MULTIPHASE_RESCUE_LIQUID_SUM_Z_K_UPPER_LIMIT
        && sumZOverK > MULTIPHASE_RESCUE_LIQUID_SUM_Z_OVER_K_LIMIT
        && maxAbsLogK > MULTIPHASE_RESCUE_LIQUID_LOG_K_SPREAD_LIMIT;
  }

  /**
   * Checks if a candidate should replace the current single-phase endpoint.
   *
   * @param candidate candidate system produced by the local seed retry
   * @param referenceGibbsEnergy Gibbs energy of the original one-phase endpoint
   * @return true when the candidate is multiphase and has a lower Gibbs energy
   */
  private boolean isLowerGibbsMultiphaseCandidate(SystemInterface candidate,
      double referenceGibbsEnergy) {
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
    double gibbsTolerance = Math.max(1.0e-6, Math.abs(referenceGibbsEnergy) * 1.0e-8);
    return candidate.getGibbsEnergy() < referenceGibbsEnergy - gibbsTolerance;
  }

  /**
   * Checks whether candidate phases have meaningfully different compositions.
   *
   * @param candidate candidate system to inspect
   * @return true when all phase pairs have distinct active-component compositions
   */
  private boolean hasDistinctPhaseCompositions(SystemInterface candidate) {
    for (int firstPhase = 0; firstPhase < candidate.getNumberOfPhases(); firstPhase++) {
      for (int secondPhase = firstPhase + 1; secondPhase < candidate
          .getNumberOfPhases(); secondPhase++) {
        double l1Difference = 0.0;
        for (int componentIndex = 0; componentIndex < candidate.getPhase(0)
            .getNumberOfComponents(); componentIndex++) {
          if (candidate.getPhase(0).getComponent(componentIndex).getz() > 1.0e-50) {
            l1Difference +=
                Math.abs(candidate.getPhase(firstPhase).getComponent(componentIndex).getx()
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
