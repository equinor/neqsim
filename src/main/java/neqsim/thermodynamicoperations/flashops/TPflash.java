package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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

  SystemInterface clonedSystem;
  double presdiff = 1.0;
  private final RachfordRice rachfordRice = new RachfordRice();
  private static final Map<SystemInterface, WarmStartState> warmStartCache =
      Collections.synchronizedMap(new WeakHashMap<SystemInterface, WarmStartState>());

  private static class WarmStartState {
    SystemInterface owner;
    double temperature;
    double pressure;
    double beta;
    double[] lnK;

    WarmStartState(SystemInterface owner, double temperature, double pressure, double beta,
        double[] lnK) {
      this.owner = owner;
      this.temperature = temperature;
      this.pressure = pressure;
      this.beta = beta;
      this.lnK = lnK;
    }
  }

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

    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
          || system.getPhase(0).getComponent(i).isIsIon()) {
        Kold = system.getPhase(0).getComponent(i).getK();
        system.getPhase(0).getComponent(i).setK(1.0e-40);
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
      } else {
        Kold = system.getPhase(0).getComponent(i).getK();
        system.getPhase(0).getComponent(i)
            .setK(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                / system.getPhase(0).getComponent(i).getFugacityCoefficient() * presdiff);
        if (Double.isNaN(system.getPhase(0).getComponent(i).getK())) {
          system.getPhase(0).getComponent(i).setK(Kold);
          system.init(1);
        }
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
        deviation += Math.abs(Math.log(system.getPhase(0).getComponent(i).getK()) - Math.log(Kold));
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

    // Compute dot products for both standard DEM and GDEM-2:
    // Standard DEM: lambda = (Dg_{n-1} . Dg_{n-2}) / (Dg_{n-2} . Dg_{n-2})
    // GDEM-2 matrix B and vector c for the 2x2 system B*mu = c
    double b11 = 0.0; // Dg_{n-1} . Dg_{n-1}
    double b12 = 0.0; // Dg_{n-1} . Dg_{n-2}
    double b22 = 0.0; // Dg_{n-2} . Dg_{n-2}
    double c1 = 0.0; // Dg_n . Dg_{n-1}
    double c2 = 0.0; // Dg_n . Dg_{n-2}
    for (i = 0; i < nc; i++) {
      b11 += oldDeltalnK[i] * oldDeltalnK[i];
      b12 += oldDeltalnK[i] * oldoldDeltalnK[i];
      b22 += oldoldDeltalnK[i] * oldoldDeltalnK[i];
      c1 += deltalnK[i] * oldDeltalnK[i];
      c2 += deltalnK[i] * oldoldDeltalnK[i];
    }

    // Standard DEM eigenvalue estimate (always computed for tracking)
    double lambda = (b22 > 1e-30) ? b12 / b22 : 0.0;

    // Try GDEM-2: solve 2x2 system for mu1, mu2
    double det = b11 * b22 - b12 * b12;
    boolean useGDEM = false;
    double mu1 = 0.0;
    double mu2 = 0.0;
    if (Math.abs(det) > 1e-30 * (b11 * b22 + 1e-100)) {
      mu1 = (c1 * b22 - c2 * b12) / det;
      mu2 = (b11 * c2 - b12 * c1) / det;
      // Use GDEM only if coefficients are bounded (prevents wild extrapolation)
      useGDEM = Math.abs(mu1) < 5.0 && Math.abs(mu2) < 5.0;
    }

    if (!useGDEM) {
      // Fallback: standard 1-eigenvalue DEM extrapolation
      // lnK += lambda / (1 - lambda) * Dg_n
      for (i = 0; i < nc; i++) {
        lnK[i] += lambda / (1.0 - lambda) * deltalnK[i];
        system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
        system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
      }
    } else {
      // GDEM-2: lnK += mu1 * Dg_n + mu2 * Dg_{n-1}
      for (i = 0; i < nc; i++) {
        lnK[i] += mu1 * deltalnK[i] + mu2 * oldDeltalnK[i];
        system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
        system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
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
      // GDEM extrapolation produced bad compositions — restore pre-acceleration state
      logger.debug("accselerateSucsSubs init failed, reverting: {}", initEx.getMessage());
      System.arraycopy(savedLnK, 0, lnK, 0, nc);
      for (i = 0; i < nc; i++) {
        system.getPhase(0).getComponent(i).setK(Math.exp(savedLnK[i]));
        system.getPhase(1).getComponent(i).setK(Math.exp(savedLnK[i]));
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
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      lnOldOldOldK[i] = lnOldOldK[i];
      lnOldOldK[i] = lnOldK[i];
      lnOldK[i] = lnK[i];
      lnK[i] = Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
          - Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient());

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
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      lnK[i] = lnOldK[i];
      system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
      system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
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
   * Apply cached K-values and beta for nearby TP states on the same system instance.
   */
  private void applyWarmStartIfAvailable() {
    if (system == null || system.getMaxNumberOfPhases() < 2) {
      return;
    }
    WarmStartState state = warmStartCache.get(system);
    if (state == null || state.lnK == null
        || state.lnK.length != system.getPhase(0).getNumberOfComponents()) {
      return;
    }
    if (state.owner != system) {
      return;
    }
    double dT = Math.abs(system.getTemperature() - state.temperature);
    double dP = Math.abs(system.getPressure() - state.pressure);
    double pScale = Math.max(Math.abs(state.pressure), 1.0);
    if (dT > 20.0 || dP / pScale > 0.3) {
      return;
    }

    for (int ic = 0; ic < state.lnK.length; ic++) {
      double ki = safeExp(state.lnK[ic]);
      if (!Double.isFinite(ki) || ki < 1e-30) {
        continue;
      }
      system.getPhase(0).getComponent(ic).setK(ki);
      system.getPhase(1).getComponent(ic).setK(ki);
    }
    if (Double.isFinite(state.beta) && state.beta > phaseFractionMinimumLimit
        && state.beta < 1.0 - phaseFractionMinimumLimit) {
      system.setBeta(state.beta);
    }
    try {
      system.calc_x_y();
      system.init(1);
    } catch (Exception ex) {
      logger.debug("Warm-start apply failed: {}", ex.getMessage());
    }
  }

  /**
   * Store the converged K-values and beta to warm-start future nearby flashes.
   */
  private void storeWarmStartState() {
    if (system == null || system.getMaxNumberOfPhases() < 2) {
      return;
    }
    int nc = system.getPhase(0).getNumberOfComponents();
    double[] lnKState = new double[nc];
    for (int ic = 0; ic < nc; ic++) {
      double ki = system.getPhase(0).getComponent(ic).getK();
      if (!Double.isFinite(ki) || ki <= 0.0) {
        ki = 1.0;
      }
      lnKState[ic] = Math.log(ki);
    }
    warmStartCache.put(system, new WarmStartState(system, system.getTemperature(),
        system.getPressure(), system.getBeta(), lnKState));
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

    findLowestGibbsPhaseIsChecked = false;
    int minGibbsPhase = 0;
    double minimumGibbsEnergy = 0;

    system.init(0);
    system.init(1);
    applyWarmStartIfAvailable();

    if ((system.getPhase(0).getGibbsEnergy()
        * (1.0 - Math.signum(system.getPhase(0).getGibbsEnergy()) * 1e-8)) < system.getPhase(1)
            .getGibbsEnergy()) {
      minGibbsPhase = 0;
    } else {
      minGibbsPhase = 1;
    }
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
        isStable = system.checkStability() && stabilityCheck();
      } catch (Exception ex) {
        logger.debug("Stability check failed, treating as stable: {}", ex.getMessage());
        isStable = true;
      }
      if (isStable) {
        if (system.doMultiPhaseCheck()) {
          TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
          operation.run();
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
        if (system.doEnhancedMultiPhaseCheck()) {
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
            && (!system.doEnhancedMultiPhaseCheck() || deviation < 0.05) && Math
                .abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) < 1e-5) {
          if (iterations == activeNewtonLimit || secondOrderSolver == null) {
            secondOrderSolver = new SysNewtonRhapsonTPflash(system, 2,
                system.getPhases()[0].getNumberOfComponents());
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
      totiter++;
    } while ((diffChem > 1e-6 && chemdev > 1e-6 && totiter < 300)
        || (system.isChemicalSystem() && totiter < 2));
    if (system.isChemicalSystem()) {
      sucsSubs();
    }
    if (system.doMultiPhaseCheck()) {
      TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
      operation.run();
    } else {
      // Post-convergence stability verification (Michelsen & Mollerup, 2007):
      // If the 2-phase flash converged to essentially single phase (beta at limits),
      // re-run stability analysis to verify that no phase split was missed.
      // This block includes LLE detection via pure-component trials when enabled.
      if (system.getBeta() > (1.0 - phaseFractionMinimumLimit * 1.01)
          || system.getBeta() < (phaseFractionMinimumLimit * 1.01)) {
        findLowestGibbsPhaseIsChecked = false;
        boolean isStable = false;
        try {
          isStable = system.checkStability() && stabilityCheck();
        } catch (Exception ex) {
          isStable = true;
        }
        if (!isStable) {
          // Stability analysis found instability — re-run flash with updated K-values
          system.calc_x_y();
          system.init(1);
          for (int reIter = 0; reIter < maxNumberOfIterations; reIter++) {
            sucsSubs();
            if (deviation < 1e-10) {
              break;
            }
          }
          // If still single phase after VLE-type K-values, try pure-component trials
          // for LLE detection when LLE checking is enabled or auto-detected.
          boolean doLLESupplementaryCheck =
              system.doCheckForLiquidLiquidSplit() || shouldRunAutomaticLLECheck();
          if (doLLESupplementaryCheck
              && (system.getBeta() > (1.0 - phaseFractionMinimumLimit * 1.01)
                  || system.getBeta() < (phaseFractionMinimumLimit * 1.01))) {
            boolean lleFound = pureComponentStabilityTrials();
            if (lleFound) {
              try {
                system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
              } catch (Exception ex) {
                system.setBeta(0.5);
              }
              system.calc_x_y();
              system.init(1);
              for (int reIter = 0; reIter < maxNumberOfIterations; reIter++) {
                sucsSubs();
                if (deviation < 1e-10) {
                  break;
                }
              }
            }
          }
        }
      }

      // Volume root selection for single-phase results (Michelsen & Mollerup, 2007):
      // Compare Gibbs energy of gas and liquid roots of the cubic EOS and select
      // the thermodynamically stable root (lower Gibbs energy).
      // For two-phase results, root assignment is handled by orderByDensity().
      if (system.getBeta() > (1.0 - phaseFractionMinimumLimit * 1.01)
          || system.getBeta() < (phaseFractionMinimumLimit * 1.01)) {
        try {
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
      }
    }
    system.orderByDensity();
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("Final init after orderByDensity failed: " + ex.getMessage());
    }

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
    storeWarmStartState();
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
