/*
 * TPmultiflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.MatrixFeatures_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * TPmultiflash class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPmultiflash extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPmultiflash.class);

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[][] dQdbeta;
  double[][] Qmatrix;
  private double[][] fugacityCoefficients;
  double[] Erow;
  double Q = 0;
  boolean doStabilityAnalysis = true;
  boolean removePhase = false;
  boolean checkOneRemove = false;
  boolean secondTime = false;
  boolean aqueousPhaseSeedAttempted = false;
  boolean postFlashStabilityChecked = false;
  boolean enhancedStabilityChecked = false;
  /** True when the beta loop exited above its own tolerance, i.e. the three-phase solve really stalled. */
  private boolean betaSolveStalled = false;
  private int rerunDepth = 0;
  /** Exact reaction-adjusted overall species inventory during coupled phase/chemical equilibrium. */
  private transient double[] reactiveOverallMoles;
  /** Normalized reaction-adjusted species fractions used by the multiphase beta equations. */
  private transient double[] reactiveOverallFractions;
  /** Positive floor for reaction products introduced at trace level. */
  private static final double MINIMUM_REACTIVE_COMPONENT_MOLES = 1.0e-45;

  double[] multTerm;
  double[] multTerm2;

  /**
   * Constructor for TPmultiflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPmultiflash(SystemInterface system) {
    super(system);
    Erow = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * Constructor for TPmultiflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPmultiflash(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
    Erow = new double[system.getPhase(0).getNumberOfComponents()];
    multTerm = new double[system.getPhase(0).getNumberOfComponents()];
    multTerm2 = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * calcMultiPhaseBeta.
   */
  public void calcMultiPhaseBeta() {
  }

  /**
   * setDoubleArrays.
   */
  public void setDoubleArrays() {
    dQdbeta = new double[system.getNumberOfPhases()][1];
    Qmatrix = new double[system.getNumberOfPhases()][system.getNumberOfPhases()];
    fugacityCoefficients = new double[system.getNumberOfPhases()][system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * setXY.
   */
  public void setXY() {
    boolean coupledReactiveFlash = isCoupledReactiveHydrateFlash() && reactiveOverallFractions != null;
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      boolean isAqueous = system.getPhase(k).getType() == PhaseType.AQUEOUS;
      double ionFractionSum = 0.0;
      double neutralFractionSum = 0.0;

      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        double overallFraction = getFlashOverallFraction(i);
        if (overallFraction > 1e-100) {
          // Check for ions - ions can only exist in aqueous phases
          // This check must happen regardless of isChemicalSystem() status
          if (isIon(i)) {
            // Ions only exist in aqueous phases, near-zero in gas/oil
            if (isAqueous && coupledReactiveFlash) {
              system.getPhase(k).getComponent(i)
                  .setx(overallFraction / Math.max(system.getBeta(k), phaseFractionMinimumLimit));
            } else if (isAqueous) {
              // In aqueous phase, calculate ion x from moles
              double totalMoles = system.getPhase(k).getNumberOfMolesInPhase();
              if (totalMoles > 1e-100) {
                system.getPhase(k).getComponent(i)
                    .setx(system.getPhase(k).getComponent(i).getNumberOfmoles() / totalMoles);
              } else {
                system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz());
              }
            } else {
              // No ions in gas or oil phases
              system.getPhase(k).getComponent(i).setx(1e-50);
            }
          } else {
            // Non-ionic components: normal flash calculation
            double newX = overallFraction / Erow[i] / system.getPhase(k).getComponent(i).getFugacityCoefficient();
            if (!Double.isFinite(newX) || newX <= 0.0) {
              newX = Math.max(overallFraction, 1.0e-30);
            }
            system.getPhase(k).getComponent(i).setx(newX);
          }
          if (isIon(i)) {
            ionFractionSum += system.getPhase(k).getComponent(i).getx();
          } else {
            neutralFractionSum += system.getPhase(k).getComponent(i).getx();
          }
        }
      }

      if (coupledReactiveFlash && isAqueous) {
        if (!(ionFractionSum < 1.0) || !(neutralFractionSum > 0.0)) {
          throw new IllegalStateException(
              "Reactive aqueous composition cannot accommodate the ionic inventory: " + "ionFraction=" + ionFractionSum
                  + ", neutralFraction=" + neutralFractionSum + ", beta=" + system.getBeta(k));
        }
        double neutralScale = (1.0 - ionFractionSum) / neutralFractionSum;
        for (int component = 0; component < system.getPhase(k).getNumberOfComponents(); component++) {
          if (!isIon(component)) {
            ComponentInterface phaseComponent = system.getPhase(k).getComponent(component);
            phaseComponent.setx(phaseComponent.getx() * neutralScale);
          }
        }
      } else {
        system.getPhase(k).normalize();
      }
    }
  }

  /**
   * calcE.
   */
  public void calcE() {
    // E = new double[system.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Erow[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        Erow[i] += system.getPhase(k).getBeta() * inverseFugacityCoefficient(k, i);
      }
      if (Erow[i] < 1e-100) {
        Erow[i] = 1e-100;
      }
      if (Double.isNaN(Erow[i])) {
        logger.error("Erow is NaN for component " + system.getPhase(0).getComponent(i).getName());
        Erow[i] = 1e-100;
      }
    }
  }

  /**
   * calcQ.
   *
   * @return a double
   */
  public double calcQ() {
    /*
     * double betaTotal = 0; for (int k = 0; k < system.getNumberOfPhases(); k++) { betaTotal +=
     * system.getPhase(k).getBeta(); } Q = betaTotal;
     */
    calcEAndCacheFugacityCoefficients();
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { Q -= Math.log(E[i]) *
     * system.getPhase(0).getComponent(i).getz(); }
     */

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      double overallFraction = getFlashOverallFraction(i);
      multTerm[i] = overallFraction / Erow[i];
      multTerm2[i] = overallFraction / (Erow[i] * Erow[i]);
    }

    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      dQdbeta[k][0] = 1.0;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        dQdbeta[k][0] -= multTerm[i] / fugacityCoefficients[k][i];
      }
    }

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      for (int j = 0; j < system.getNumberOfPhases(); j++) {
        Qmatrix[i][j] = 0.0;
        for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
          Qmatrix[i][j] += multTerm2[k] / (fugacityCoefficients[j][k] * fugacityCoefficients[i][k]);
        }
        if (i == j) {
          double reg = 1.0e-3;
          if (shouldApplyEnhancedMultiPhaseCheck()) {
            double absDiag = Math.abs(Qmatrix[i][j]);
            double beta = Math.abs(system.getPhase(i).getBeta());
            // Keep strong regularization for near-singular small-beta phases,
            // but reduce bias in well-conditioned enhanced-mode cases.
            if (beta > 1.0e-8 && absDiag > 1.0e-8) {
              reg = Math.max(1.0e-12, absDiag * 1.0e-8);
            }
          }
          Qmatrix[i][j] += reg;
        }
      }
    }
    return Q;
  }

  /**
   * Calculate the phase-split denominator and cache fugacity coefficients for the gradient and Hessian.
   *
   * <p>
   * Retaining the original division sequence is intentional. Algebraically equivalent reciprocal multiplication changes
   * rounding in repeated reservoir flashes and can alter accepted system-level trajectories.
   * </p>
   */
  private void calcEAndCacheFugacityCoefficients() {
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      Erow[component] = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        double fugacityCoefficient = system.getPhase(phase).getComponent(component).getFugacityCoefficient();
        if (isCoupledReactiveHydrateFlash() && isIon(component)
            && system.getPhase(phase).getType() != PhaseType.AQUEOUS) {
          fugacityCoefficient = Double.POSITIVE_INFINITY;
        }
        fugacityCoefficients[phase][component] = fugacityCoefficient;
        Erow[component] += system.getPhase(phase).getBeta() / fugacityCoefficient;
      }
      if (Erow[component] < 1e-100) {
        Erow[component] = 1e-100;
      }
      if (Double.isNaN(Erow[component])) {
        logger.error("Erow is NaN for component " + system.getPhase(0).getComponent(component).getName());
        Erow[component] = 1e-100;
      }
    }
  }

  /**
   * Return the current overall species fraction used by the phase-equilibrium equations.
   *
   * @param component component index
   * @return reaction-adjusted fraction for a coupled reactive flash, otherwise the system fraction
   */
  private double getFlashOverallFraction(int component) {
    if (reactiveOverallFractions != null && component >= 0 && component < reactiveOverallFractions.length) {
      return reactiveOverallFractions[component];
    }
    return system.getPhase(0).getComponent(component).getz();
  }

  /**
   * Return an allowed inverse fugacity coefficient.
   *
   * <p>
   * Reactive ions are excluded exactly from gas and oil phases instead of relying on a large finite fugacity penalty.
   * </p>
   *
   * @param phase phase index
   * @param component component index
   * @return inverse fugacity coefficient, or zero when an ion is excluded from the phase
   */
  private double inverseFugacityCoefficient(int phase, int component) {
    if (isCoupledReactiveHydrateFlash() && isIon(component) && system.getPhase(phase).getType() != PhaseType.AQUEOUS) {
      return 0.0;
    }
    double fugacityCoefficient = system.getPhase(phase).getComponent(component).getFugacityCoefficient();
    return 1.0 / fugacityCoefficient;
  }

  /**
   * solveBeta.
   *
   * @return a double
   */
  public double solveBeta() {
    int numberOfPhases = system.getNumberOfPhases();
    DMatrixRMaj betaGradient = new DMatrixRMaj(numberOfPhases, 1);
    DMatrixRMaj betaHessian = new DMatrixRMaj(numberOfPhases, numberOfPhases);
    DMatrixRMaj betaCorrection = new DMatrixRMaj(numberOfPhases, 1);
    double err = 1.0;
    double gradResidual = 1.0;
    int iter = 1;
    do {
      iter++;
      calcQ();
      copyBetaSolverInputs(betaGradient, betaHessian);
      gradResidual = NormOps_DDRM.normF(betaGradient);
      boolean solved = false;
      Exception solveException = null;
      try {
        solved = solveBetaCorrection(betaHessian, betaGradient, betaCorrection);
      } catch (Exception ex) {
        solveException = ex;
      }
      if (!solved) {
        if (shouldApplyEnhancedMultiPhaseCheck()) {
          for (int kk = 0; kk < system.getNumberOfPhases(); kk++) {
            Qmatrix[kk][kk] += 1.0e-2;
          }
          copyBetaSolverInputs(betaGradient, betaHessian);
          try {
            solved = solveBetaCorrection(betaHessian, betaGradient, betaCorrection);
          } catch (Exception ex2) {
            solveException = ex2;
          }
        }
        if (!solved) {
          logger.error(solveException == null ? "Beta Hessian solve failed" : solveException.getMessage());
          break;
        }
      }

      // The linear solve already returns a column vector. Apply it directly to avoid allocating
      // transposed, scaled, and subtracted temporary matrices in every beta iteration.
      double betaStepScale = iter / (iter + 3.0);
      removePhase = false;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        double currBeta = system.getPhase(k).getBeta() - betaCorrection.get(k, 0) * betaStepScale;
        if (currBeta < phaseFractionMinimumLimit) {
          system.setBeta(k, phaseFractionMinimumLimit);
          if (checkOneRemove) {
            if (system.getPhase(k).getType() == PhaseType.GAS) {
              system.setPhaseType(k, PhaseType.LIQUID);
            }
            checkOneRemove = false;
            removePhase = true;
          }
          checkOneRemove = true;
        } else if (currBeta > (1.0 - phaseFractionMinimumLimit)) {
          system.setBeta(k, 1.0 - phaseFractionMinimumLimit);
        } else {
          system.setBeta(k, currBeta);
        }
      }
      system.normalizeBeta();
      system.init(1);
      calcE();
      setXY();
      system.init(1);
      err = NormOps_DDRM.normF(betaCorrection);
    } while (((err > 1e-12 || gradResidual > 1e-10) && iter < 50) || iter < 3);
    // logger.info("iterations " + iter);
    return err;
  }

  /**
   * Solve one beta Newton system and reject non-finite corrections.
   *
   * <p>
   * EJML's raw common-operations solve can report success for a singular matrix while writing NaN values to the
   * correction vector. The former {@code SimpleMatrix.solve(...)} path raised a singular-matrix exception in that case,
   * allowing enhanced mode to regularize the Hessian and ordinary mode to stop without corrupting phase fractions.
   * </p>
   *
   * @param betaHessian beta-Hessian matrix
   * @param betaGradient beta-gradient column vector
   * @param betaCorrection destination for the Newton correction
   * @return true only when EJML reports success and every correction entry is finite
   */
  static boolean solveBetaCorrection(DMatrixRMaj betaHessian, DMatrixRMaj betaGradient, DMatrixRMaj betaCorrection) {
    return CommonOps_DDRM.solve(betaHessian, betaGradient, betaCorrection)
        && !MatrixFeatures_DDRM.hasUncountable(betaCorrection);
  }

  /**
   * Copy the current beta gradient and Hessian into reusable EJML work matrices. Every attempt is refreshed so a
   * regularized retry uses the updated Hessian without allocating new wrappers.
   *
   * @param betaGradient reusable beta-gradient column vector
   * @param betaHessian reusable beta-Hessian matrix
   */
  private void copyBetaSolverInputs(DMatrixRMaj betaGradient, DMatrixRMaj betaHessian) {
    int numberOfPhases = system.getNumberOfPhases();
    for (int row = 0; row < numberOfPhases; row++) {
      betaGradient.set(row, 0, dQdbeta[row][0]);
      for (int column = 0; column < numberOfPhases; column++) {
        betaHessian.set(row, column, Qmatrix[row][column]);
      }
    }
  }

  /**
   * Execute a bounded recursive rerun request to avoid unbounded recursion in difficult cases.
   */
  private void requestBoundedRerun() {
    if (rerunDepth >= 4) {
      logger.warn("TPmultiflash rerun depth limit reached, skipping additional rerun");
      return;
    }
    rerunDepth++;
    try {
      run();
    } finally {
      rerunDepth--;
    }
  }

  /**
   * Remove a duplicate phase while conserving its mass by merging its phase fraction into the surviving
   * (near-identical) phase before removal. Two phases flagged as numerical duplicates have essentially identical
   * mole-fraction vectors, so the merged phase fraction is simply the sum of the two betas. Without this merge the
   * removed phase's mass leaks into the remaining phases through {@code normalizeBeta()}, which halves trace liquid
   * dropout (see UMR-PRU trace oil regression).
   *
   * @param keepPhase index of the phase to keep
   * @param removePhase2 index of the duplicate phase to remove
   */
  private void mergeAndRemoveDuplicatePhase(int keepPhase, int removePhase2) {
    double mergedBeta = system.getBeta(keepPhase) + system.getBeta(removePhase2);
    system.removePhaseKeepTotalComposition(removePhase2);
    // After removing removePhase2, indices above it shift down by one.
    int newKeepIndex = keepPhase > removePhase2 ? keepPhase - 1 : keepPhase;
    system.setBeta(newKeepIndex, mergedBeta);
    system.normalizeBeta();
  }

  /** {@inheritDoc} */
  @Override
  public void stabilityAnalysis() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; } double numb = 0;
     * clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0 by Even Solbraa
     * 23.01.2013 - chaged back to 1.0e-12 27.04.13 if (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0;
     * } ( clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1); } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!(( clonedSystem.get(k)) == null)) {
     * sumw[k] += ( clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!(( clonedSystem.get(k)) == null) &&
     * system.getPhase(0).getComponent(k).getx() > 1e-100) { ( clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if (system.getPhase(0).getComponent(k).getx() >
     * 1e-100) { d[k] = Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents ()[k].getIonicCharge()!=0) d[k]=0;
     * } //logger.info("dk: " + d[k]); }
     */

    // Calculate reference fugacities d[k] = ln(x_k) + ln(phi_k) for feed phase
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
    }

    // Initialize logWi array (will be overwritten for each trial)
    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 0.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    // --- O2: Wilson K early exit + O3: K-value based trial phases ---
    int numComp = system.getPhase(0).getNumberOfComponents();
    double tempK = system.getTemperature();
    double presBar = system.getPressure();
    double[] wilsonK = new double[numComp];
    double maxAbsLogK = 0.0;
    boolean[] validComp = new boolean[numComp];

    for (int i = 0; i < numComp; i++) {
      double z = system.getPhase(0).getComponent(i).getz();
      boolean isIon = system.getPhase(0).getComponent(i).getIonicCharge() != 0
          || system.getPhase(0).getComponent(i).isIsIon();
      validComp[i] = z > 1e-100 && !isIon;
      if (validComp[i]) {
        double tc = system.getPhase(0).getComponent(i).getTC();
        double pc = system.getPhase(0).getComponent(i).getPC();
        double omega = system.getPhase(0).getComponent(i).getAcentricFactor();
        double kVal = (pc / presBar) * Math.exp(5.373 * (1.0 + omega) * (1.0 - tc / tempK));
        wilsonK[i] = Math.max(kVal, 1e-20);
        double absLogK = Math.abs(Math.log(wilsonK[i]));
        if (absLogK > maxAbsLogK) {
          maxAbsLogK = absLogK;
        }
      } else {
        wilsonK[i] = 1.0;
      }
    }

    // O2: Early exit — if all K ≈ 1.0 the system is near/above critical.
    // Only skip Wilson K-based trials; still fall through to pure-component trials
    // which use independent initial guesses not affected by K ≈ 1.
    // Furst-electrolyte and UMR-PRU-MC systems retain their established local
    // pure-component stability path unless enhanced checking is explicitly requested.
    // Other model families still require Wilson trials for water-rich and vapor-like splits.
    boolean preserveLocalStabilityPath = !system.doEnhancedMultiPhaseCheck()
        && (system instanceof SystemFurstElectrolyteEos || system instanceof SystemUMRPRUMCEos);
    boolean skipWilsonKTrials = preserveLocalStabilityPath || maxAbsLogK < 0.01;

    // O3: Wilson K-based trial phases — liquid-like (z/K) first, then vapor-like (K·z)
    // Liquid-like trial runs first because most multi-phase systems have liquid-driven
    // instability (water dropout, heavy end fallout). Heavy components have K << 1,
    // so z/K heavily enriches them, creating a trial similar to pure-component heavy trials.
    // Skip when all Wilson K ≈ 1 (near-critical) — these trials produce trivial initial
    // guesses. The pure-component trials below still run and provide independent checks.
    for (int trial = 0; !skipWilsonKTrials && trial < 2; trial++) {
      // Initialize trial composition from Wilson K
      for (int i = 0; i < numComp; i++) {
        if (validComp[i]) {
          double z = system.getPhase(0).getComponent(i).getz();
          // trial 0 = liquid-like (z/K), trial 1 = vapor-like (K*z)
          double wVal = (trial == 0) ? z / wilsonK[i] : wilsonK[i] * z;
          logWi[i] = Math.log(Math.max(wVal, 1e-100));
        } else {
          logWi[i] = -10000.0;
        }
      }
      // Set trial phase composition (unnormalized, same as pure-component trials)
      for (int i = 0; i < numComp; i++) {
        if (clonedSystem.get(0).isPhase(1)) {
          clonedSystem.get(0).getPhase(1).getComponent(i).setx(validComp[i] ? safeExp(logWi[i]) : 1e-50);
        }
      }

      // Successive substitution with Wegstein acceleration (same as pure-component trials)
      int iter = 0;
      err = 1.0e10;
      double errOld = 1.0e100;
      boolean useAccel = true;
      boolean trialInitFailed = false;
      int maxiter = 50;
      do {
        errOld = err;
        iter++;
        err = 0;

        for (int i = 0; i < numComp; i++) {
          oldoldoldlogw[i] = oldoldlogw[i];
          oldoldlogw[i] = oldlogw[i];
          oldlogw[i] = logWi[i];
          oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
          oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
        }
        try {
          clonedSystem.get(0).init(1, 1);
        } catch (Exception ex) {
          trialInitFailed = true;
          break;
        }
        for (int i = 0; i < numComp; i++) {
          if (validComp[i]
              && !Double.isInfinite(clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())) {
            logWi[i] = d[i] - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
          }
          deltalogWi[i] = logWi[i] - oldlogw[i];
          err += Math.abs(deltalogWi[i]);
        }

        // Wegstein acceleration every 7th iteration
        if (iter % 7 == 0 && iter > 7 && useAccel && err < errOld) {
          double prod1 = 0.0;
          double prod2 = 0.0;
          for (int i = 0; i < numComp; i++) {
            if (validComp[i]) {
              prod1 += deltalogWi[i] * oldDeltalogWi[i];
              prod2 += oldDeltalogWi[i] * oldDeltalogWi[i];
            }
          }
          if (prod2 > 1e-20) {
            double lambda = prod1 / prod2;
            if (lambda > 0.0 && lambda < 1.0) {
              double accelFactor = lambda / (1.0 - lambda);
              for (int i = 0; i < numComp; i++) {
                if (validComp[i]) {
                  logWi[i] += accelFactor * deltalogWi[i];
                }
              }
            }
          }
        }
        if (iter > 2 && err > errOld) {
          useAccel = false;
        }

        // Update trial phase composition
        for (int i = 0; i < numComp; i++) {
          clonedSystem.get(0).getPhase(1).getComponent(i).setx(validComp[i] ? safeExp(logWi[i]) : 1e-50);
        }
      } while (!trialInitFailed && (Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);

      if (trialInitFailed) {
        continue;
      }

      // Calculate tangent plane distance and check for instability
      double tmVal = 1.0;
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;
      double[] xTrial = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        if (validComp[i]) {
          tmVal -= safeExp(logWi[i]);
        }
        xTrial[i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        xTrivialCheck0 += Math.abs(xTrial[i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(xTrial[i] - system.getPhase(1).getComponent(i).getx());
      }

      boolean isTrivial = Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4;

      if (!isTrivial && tmVal < -1e-8 && iter < maxiter) {
        // Unstable — add new phase and return
        system.addPhase();
        int newPhaseIdx = system.getNumberOfPhases() - 1;
        for (int i = 0; i < numComp; i++) {
          system.getPhase(newPhaseIdx).getComponent(i).setx(xTrial[i]);
        }
        system.getPhases()[newPhaseIdx].normalize();
        multiPhaseTest = true;
        int dominantComp = 0;
        double maxX = 0;
        for (int i = 0; i < numComp; i++) {
          if (xTrial[i] > maxX) {
            maxX = xTrial[i];
            dominantComp = i;
          }
        }
        system.setBeta(newPhaseIdx, system.getPhase(0).getComponent(dominantComp).getz());
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("K-value trial addPhase init failed: " + ex.getMessage());
          system.removePhaseKeepTotalComposition(newPhaseIdx);
          multiPhaseTest = false;
          return;
        }
        system.normalizeBeta();
        return;
      }
    }

    // Wilson K trial phases can report a comfortable positive TPD while pure-component
    // trial phases still find hydrocarbon liquid-liquid splits. Always keep the
    // pure-component fallback in TPmultiflash so ordinary multiphase scans retain the
    // same LLE coverage as the 3.7.x flash implementation.

    // --- Fallback: Pure-component trials for cases Wilson K trials miss ---
    // (e.g., LLE detection where K-values don't capture polarity-driven splits)
    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }
    // boolean checkdForHCmix = false;
    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon() && j != hydrocarbonTestCompNumb
              && j != lightTestCompNumb)) {
        continue;
      }
      double nomb = 0.0;
      for (int cc = 0; cc < system.getPhase(0).getNumberOfComponents(); cc++) {
        // Pure component trial phase: component j = 1.0, others = trace
        nomb = cc == j ? 1.0 : 1.0e-12;
        if (system.getPhase(0).getComponent(cc).getz() < 1e-100) {
          nomb = 0.0;
        }

        // Initialize logWi to match pure component trial phase (Michelsen's algorithm)
        // For pure component j trial: Wi[j] = 1.0, Wi[others] = trace
        // So logWi[j] = 0, logWi[others] = log(1e-12) ≈ -27.6
        if (system.getPhase(0).getComponent(cc).getz() > 1e-100) {
          logWi[cc] = Math.log(Math.max(nomb, 1e-100));
        } else {
          logWi[cc] = -10000.0;
        }

        if (clonedSystem.get(0).isPhase(1)) {
          try {
            clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb);
            /*
             * if (system.getPhase(1).getType() == PhaseType.AQUEOUS && !checkdForHCmix) {
             * clonedSystem.get(0).getPhase(1).getComponent(cc)
             * .setx(clonedSystem.get(0).getPhase(0).getComponent(cc).getK() /
             * clonedSystem.get(0).getPhase(0).getComponent(cc).getx()); } else {
             * clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb); }
             */
          } catch (Exception ex) {
            logger.warn(ex.getMessage());
          }
        }
      }

      // if (system.getPhase(1).getType() == PhaseType.AQUEOUS && !checkdForHCmix) {
      // checkdForHCmix = true;
      // }

      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      boolean useaccsubst = true;
      boolean pureTrialInitFailed = false;
      int maxsucssubiter = 150;
      int maxiter = 200;

      // Pre-allocate Newton matrices outside the iteration loop to avoid GC pressure
      int nc = system.getPhase(0).getNumberOfComponents();
      DMatrixRMaj newtonF = new DMatrixRMaj(nc, 1);
      DMatrixRMaj newtonJ = new DMatrixRMaj(nc, nc);
      DMatrixRMaj newtonDx = new DMatrixRMaj(nc, 1);

      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          // DEM acceleration every 5th iteration (Michelsen 1982b, Risnes et al. 1981)
          // Uses dominant eigenvalue estimate: λ = (Δg_n · Δg_{n-1}) / (Δg_{n-1} ·
          // Δg_{n-1})
          if (iter % 5 == 0 && iter > 5 && useaccsubst) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (int i = 0; i < nc; i++) {
              // Correct DEM formula: λ = Σ(Δg_n · Δg_{n-1}) / Σ(Δg_{n-1}²)
              prod1 += deltalogWi[i] * oldDeltalogWi[i];
              prod2 += oldDeltalogWi[i] * oldDeltalogWi[i];
            }

            if (prod2 > 1e-20) {
              double lambda = prod1 / prod2;
              // Only accelerate if 0 < λ < 1 (convergent regime)
              if (lambda > 0.0 && lambda < 1.0) {
                double accelFactor = lambda / (1.0 - lambda);
                for (int i = 0; i < nc; i++) {
                  logWi[i] += accelFactor * deltalogWi[i];
                  Wi[j][i] = safeExp(logWi[i]);
                }
              }
            }
            // Must still update compositions after acceleration
            for (int i = 0; i < nc; i++) {
              err += Math.abs(logWi[i] - oldlogw[i]);
            }
          } else {
            for (int i = 0; i < nc; i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            try {
              clonedSystem.get(0).init(1, 1);
            } catch (Exception ex) {
              pureTrialInitFailed = true;
              break;
            }
            for (int i = 0; i < nc; i++) {
              if (!Double.isInfinite(clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getz() > 1e-100) {
                logWi[i] = d[i] - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
            if (iter > 2 && err > errOld) {
              useaccsubst = false;
            }
          }
        } else {
          // Second-order (Newton) method using Michelsen's α-substitution
          // α_i = 2√(W_i), which ensures W_i ≥ 0 (Michelsen 1982a)
          for (int i = 0; i < nc; i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
          }
          // Newton needs fugcoef + composition derivatives
          try {
            clonedSystem.get(0).init(3, 1);
          } catch (Exception ex) {
            pureTrialInitFailed = true;
            break;
          }
          alpha = new double[nc];

          for (int i = 0; i < nc; i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          // Build gradient and Jacobian using raw EJML (no SimpleMatrix allocation)
          for (int i = 0; i < nc; i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              newtonF.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient() - d[i]));
            } else {
              newtonF.set(i, 0, 0.0);
            }
            for (int k = 0; k < nc; k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                newtonJ.set(i, k, kronDelt
                    + Math.sqrt(Wi[j][k] * Wi[j][i]) * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
              } else {
                newtonJ.set(i, k, 0.0);
              }
            }
          }

          // Solve J·dx = -f using raw EJML
          boolean solved = CommonOps_DDRM.solve(newtonJ, newtonF, newtonDx);
          if (!solved) {
            // Regularize: add small diagonal and retry
            for (int i = 0; i < nc; i++) {
              newtonJ.add(i, i, 0.1);
            }
            solved = CommonOps_DDRM.solve(newtonJ, newtonF, newtonDx);
          }

          if (solved) {
            for (int i = 0; i < nc; i++) {
              double alphaNew = alpha[i] - newtonDx.get(i, 0);
              Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                logWi[i] = Math.log(Wi[j][i]);
              }
              if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                  || system.getPhase(0).getComponent(i).isIsIon()) {
                logWi[i] = -1000.0;
              }
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
            }
          }
        }
        // logger.info("err: " + err);

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(safeExp(logWi[i]));
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while (!pureTrialInitFailed && (Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);

      if (pureTrialInitFailed) {
        tm[j] = 10.0;
        continue;
      }

      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        // Use getz() so heavy HCs (with near-zero x in gas phase 0) still contribute to tm
        if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= maxiter) {
        // logger.info("iter > maxiter multiphase stability ");
        // logger.info("error " + Math.abs(err));
        // logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1, system.getPhase(0).getComponent(unstabcomp).getz());
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("stabilityAnalysis addPhase init failed: " + ex.getMessage());
          system.removePhaseKeepTotalComposition(system.getNumberOfPhases() - 1);
          multiPhaseTest = false;
          return;
        }
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }

    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  /**
   * Enhanced stability analysis that uses Wilson K-values for initial guesses and tests multiple trial phase
   * compositions. This method is more robust for detecting liquid-liquid equilibria and three-phase systems (e.g.,
   * CO2/H2S/hydrocarbon mixtures).
   *
   * <p>
   * Key improvements over basic stabilityAnalysis():
   * </p>
   * <ul>
   * <li>Uses Wilson K-value correlation for vapor-liquid equilibrium (VLE) detection</li>
   * <li>Tests vapor-like trial (K), liquid-like trial (1/K), and LLE-specific trial phases</li>
   * <li>LLE trial uses acentric factor-based perturbation (polarity proxy) since Wilson K-values are derived from vapor
   * pressure correlations and may not capture activity coefficient-driven liquid-liquid splits</li>
   * <li>Does not skip non-hydrocarbon components (important for CO2, H2S systems)</li>
   * <li>Tests stability against all existing phases, not just phase 0</li>
   * <li>Includes Wegstein acceleration for faster convergence</li>
   * </ul>
   */
  public void stabilityAnalysisEnhanced() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double[] logWi = new double[numComponents];
    double[] Wi = new double[numComponents];
    double[] oldlogw = new double[numComponents];
    double[] oldoldlogw = new double[numComponents];
    double[] deltalogWi = new double[numComponents];
    double[] oldDeltalogWi = new double[numComponents];
    double[] x = new double[numComponents];
    tm = new double[numComponents];

    // Initialize all tm values to stable (positive)
    for (int i = 0; i < numComponents; i++) {
      tm[i] = 10.0;
    }

    // Clone system once - reuse for all tests
    SystemInterface clonedSystem = system.clone();

    // Calculate Wilson K-values for each component once
    // K = (Pc/P) * exp(5.373 * (1 + omega) * (1 - Tc/T))
    double[] wilsonK = new double[numComponents];
    double[] logWilsonK = new double[numComponents];
    double tempK = system.getTemperature();
    double presBar = system.getPressure();

    // Pre-calculate which components are valid (z > threshold and not ionic)
    boolean[] validComponent = new boolean[numComponents];
    int validCount = 0;
    for (int j = 0; j < numComponents; j++) {
      double z = system.getPhase(0).getComponent(j).getz();
      boolean isIon = system.getPhase(0).getComponent(j).getIonicCharge() != 0;
      validComponent[j] = z > 1e-100 && !isIon;
      if (validComponent[j]) {
        validCount++;
        double tc = system.getPhase(0).getComponent(j).getTC();
        double pc = system.getPhase(0).getComponent(j).getPC();
        double omega = system.getPhase(0).getComponent(j).getAcentricFactor();
        double kVal = (pc / presBar) * Math.exp(5.373 * (1.0 + omega) * (1.0 - tc / tempK));
        wilsonK[j] = Math.max(kVal, 1e-20);
        logWilsonK[j] = Math.log(wilsonK[j]);
      } else {
        wilsonK[j] = 1.0;
        logWilsonK[j] = 0.0;
      }
    }

    // Early exit if no valid components
    if (validCount == 0) {
      system.normalizeBeta();
      return;
    }

    int numPhases = system.getNumberOfPhases();

    // Pre-calculate reference fugacities for all phases
    double[][] dRef = new double[numPhases][numComponents];
    for (int refPhase = 0; refPhase < numPhases; refPhase++) {
      for (int k = 0; k < numComponents; k++) {
        double xk = system.getPhase(refPhase).getComponent(k).getx();
        if (xk > 1e-100) {
          dRef[refPhase][k] = Math.log(xk) + system.getPhase(refPhase).getComponent(k).getLogFugacityCoefficient();
        }
      }
    }

    // Test stability for EACH existing phase as reference phase
    for (int refPhase = 0; refPhase < numPhases; refPhase++) {
      double[] d = dRef[refPhase];

      // Test with three different initial guesses:
      // trialType = 1: Vapor-like trial phase (use Wilson K directly) - for VLE gas detection
      // trialType = -1: Liquid-like trial phase (use 1/Wilson K) - for VLE liquid detection
      // trialType = 0: LLE trial (composition perturbation) - for liquid-liquid equilibrium
      // Wilson K-values are based on vapor pressure and work well for VLE,
      // but LLE is driven by activity coefficient differences (polarity, H-bonding),
      // so we use a different initialization strategy for LLE detection.
      for (int trialType = 1; trialType >= -1; trialType--) {
        // Initialize logWi based on trial type
        for (int j = 0; j < numComponents; j++) {
          if (!validComponent[j]) {
            logWi[j] = -10000.0;
            Wi[j] = 0.0;
          } else if (trialType == 1) {
            // Vapor-like: use Wilson K (volatile components enriched)
            logWi[j] = logWilsonK[j];
            Wi[j] = Math.exp(logWi[j]);
          } else if (trialType == -1) {
            // Liquid-like: use 1/K (heavy components enriched)
            logWi[j] = -logWilsonK[j];
            Wi[j] = Math.exp(logWi[j]);
          } else {
            // LLE trial (trialType == 0): perturb based on hydrocarbon vs non-HC
            // nature
            // Non-HCs (water, CO2, H2S, MEG) are enriched; HCs are depleted in the
            // polar-rich trial phase — physically correct for aqueous LLE
            // detection.
            double z = system.getPhase(0).getComponent(j).getz();
            double perturbFactor = system.getPhase(0).getComponent(j).isHydrocarbon() ? 0.5 : 2.0;
            Wi[j] = z * perturbFactor;
            logWi[j] = Math.log(Math.max(Wi[j], 1e-100));
          }
          oldlogw[j] = logWi[j];
          oldoldlogw[j] = logWi[j];
          deltalogWi[j] = 0.0;
          oldDeltalogWi[j] = 0.0;
        }

        // Force correct EOS root for the trial phase type before evaluating fugacities.
        // Without this, a clone inheriting a GAS phase type would pick the vapor root
        // even for liquid-like and LLE trials, giving wrong fugacity coefficients.
        if (clonedSystem.isPhase(1)) {
          PhaseType trialPhaseType = (trialType == 1) ? PhaseType.GAS : PhaseType.LIQUID;
          clonedSystem.setPhaseType(1, trialPhaseType);
        }

        // Set initial trial phase composition
        for (int cc = 0; cc < numComponents; cc++) {
          if (clonedSystem.isPhase(1)) {
            clonedSystem.getPhase(1).getComponent(cc).setx(validComponent[cc] ? Wi[cc] : 1e-50);
          }
        }

        // Successive substitution iterations with acceleration
        int iter = 0;
        double err = 1.0e10;
        double errOld = 1.0e100;
        int maxiter = 150; // Reduced from 200 - Wilson init converges faster
        boolean useAcceleration = true;

        do {
          errOld = err;
          iter++;
          err = 0;

          // Store old values for acceleration
          for (int i = 0; i < numComponents; i++) {
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldDeltalogWi[i] = deltalogWi[i];
          }

          try {
            clonedSystem.init(1, 1);
          } catch (RuntimeException ex) {
            // Molar volume calculation failed for this trial phase composition
            // Skip this trial - it's not a physically meaningful phase
            logger.debug("Enhanced stability trial init failed: " + ex.getMessage());
            break;
          }

          // Update logWi from fugacity coefficients
          for (int i = 0; i < numComponents; i++) {
            if (validComponent[i]) {
              double logFugCoeff = clonedSystem.getPhase(1).getComponent(i).getLogFugacityCoefficient();
              if (!Double.isInfinite(logFugCoeff)) {
                logWi[i] = d[i] - logFugCoeff;
              }
            }
            deltalogWi[i] = logWi[i] - oldlogw[i];
            err += Math.abs(deltalogWi[i]);
            Wi[i] = safeExp(logWi[i]);
          }

          // Wegstein/GDEM acceleration every 7th iteration
          if (iter % 7 == 0 && iter > 7 && useAcceleration && err < errOld) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (int i = 0; i < numComponents; i++) {
              if (validComponent[i]) {
                double vec1 = deltalogWi[i] * oldDeltalogWi[i];
                double vec2 = oldDeltalogWi[i] * oldDeltalogWi[i];
                prod1 += vec1;
                prod2 += vec2;
              }
            }
            if (prod2 > 1e-20) {
              double lambda = prod1 / prod2;
              if (lambda > 0.0 && lambda < 1.0) {
                double accelFactor = lambda / (1.0 - lambda);
                for (int i = 0; i < numComponents; i++) {
                  if (validComponent[i]) {
                    logWi[i] += accelFactor * deltalogWi[i];
                    Wi[i] = safeExp(logWi[i]);
                  }
                }
              }
            }
          }

          // Disable acceleration if error is increasing
          if (iter > 2 && err > errOld) {
            useAcceleration = false;
          }

          // Update trial phase compositions
          for (int i = 0; i < numComponents; i++) {
            clonedSystem.getPhase(1).getComponent(i).setx(validComponent[i] ? Wi[i] : 1e-50);
          }
        } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);

        // Calculate tangent plane distance
        double tmVal = 1.0;
        for (int i = 0; i < numComponents; i++) {
          if (validComponent[i]) {
            tmVal -= Wi[i];
          }
          x[i] = clonedSystem.getPhase(1).getComponent(i).getx();
        }

        // Check for trivial solution (trial phase same as any existing phase)
        boolean isTrivial = false;
        for (int existingPhase = 0; existingPhase < numPhases; existingPhase++) {
          double xTrivialCheck = 0.0;
          for (int i = 0; i < numComponents; i++) {
            xTrivialCheck += Math.abs(x[i] - system.getPhase(existingPhase).getComponent(i).getx());
          }
          if (xTrivialCheck < 1e-4) {
            isTrivial = true;
            break;
          }
        }

        // If unstable and non-trivial, add new phase and return
        if (!isTrivial && tmVal < -1e-8) {
          system.addPhase();
          int newPhaseIdx = system.getNumberOfPhases() - 1;
          for (int i = 0; i < numComponents; i++) {
            system.getPhase(newPhaseIdx).getComponent(i).setx(x[i]);
          }
          system.getPhases()[newPhaseIdx].normalize();
          multiPhaseTest = true;

          // Set initial beta based on dominant component
          int dominantComp = 0;
          double maxX = 0;
          for (int i = 0; i < numComponents; i++) {
            if (x[i] > maxX) {
              maxX = x[i];
              dominantComp = i;
            }
          }
          system.setBeta(newPhaseIdx, system.getPhase(0).getComponent(dominantComp).getz());
          try {
            system.init(1);
          } catch (Exception ex) {
            logger.warn("Enhanced K-value trial addPhase init failed: " + ex.getMessage());
            system.removePhaseKeepTotalComposition(newPhaseIdx);
            multiPhaseTest = false;
            return;
          }
          system.normalizeBeta();
          return;
        }
      }
    }

    system.normalizeBeta();
  }

  /**
   * Returns a bounded initial fraction for a phase admitted by a Wilson-K stability trial.
   *
   * <p>
   * A negative tangent-plane distance establishes that the current topology is unstable, but it does not determine the
   * equilibrium amount of the new phase. Seeding beta from the trial's dominant overall component can therefore
   * introduce an order-one material phase before the phase-fraction solve. Use the existing ordinary beta solver's
   * regularization scale so the trial is incipient without being pinned below the solver's useful correction scale,
   * then let the beta/equilibrium solve grow or remove it.
   * </p>
   *
   * @param dominantComponent index of the largest component in the trial composition
   * @return bounded incipient phase fraction
   */
  private double getIncipientWilsonPhaseFraction(int dominantComponent) {
    double numericalSeed = Math.max(1.0e-3, 100.0 * phaseFractionMinimumLimit);
    return Math.min(system.getPhase(0).getComponent(dominantComponent).getz(), numericalSeed);
  }

  /**
   * stabilityAnalysis3.
   */
  public void stabilityAnalysis3() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; } double numb = 0;
     * clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0 by Even Solbraa
     * 23.01.2013 - chaged back to 1.0e-12 27.04.13 if (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0;
     * } ( clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1); } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!(( clonedSystem.get(k)) == null)) {
     * sumw[k] += ( clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!(( clonedSystem.get(k)) == null) &&
     * system.getPhase(0).getComponent(k).getx() > 1e-100) { ( clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if (system.getPhase(0).getComponent(k).getx() >
     * 1e-100) { d[k] = Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents ()[k].getIonicCharge()!=0) d[k]=0;
     * } //logger.info("dk: " + d[k]); }
     */
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
    }

    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 1.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }

    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    for (int j = 0; j < system.getNumberOfComponents(); j++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon() && j != hydrocarbonTestCompNumb
              && j != lightTestCompNumb)) {
        continue;
      }

      double nomb = 0.0;
      for (int cc = 0; cc < system.getPhase(0).getNumberOfComponents(); cc++) {
        nomb = cc == j ? 1.0 : 1.0e-12;
        if (system.getPhase(0).getComponent(cc).getz() < 1e-100) {
          nomb = 0.0;
        }

        if (clonedSystem.get(0).isPhase(1)) {
          try {
            clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb);
          } catch (Exception ex) {
            logger.warn(ex.getMessage());
          }
        }
      }
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      boolean useaccsubst = true;
      boolean enhancedTrialInitFailed = false;
      int maxsucssubiter = 150;
      int maxiter = 200;
      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 7 == 0 && useaccsubst) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          } else {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            try {
              clonedSystem.get(0).init(1, 1);
            } catch (Exception ex) {
              enhancedTrialInitFailed = true;
              break;
            }
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i] - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
              useaccsubst = true;
            }
            if (iter > 2 && err > errOld) {
              useaccsubst = false;
            }
          }
        } else {
          SimpleMatrix f = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(), 1);
          SimpleMatrix df = null;
          SimpleMatrix identitytimesConst = null;
          // if (!secondOrderStabilityAnalysis) {
          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          try {
            clonedSystem.get(0).init(3, 1);
          } catch (Exception ex) {
            enhancedTrialInitFailed = true;
            break;
          }
          alpha = new double[clonedSystem.get(0).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());

          for (int i = 0; i < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient() - d[i]));
            }
            for (int k = 0; k < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt
                    + Math.sqrt(Wi[j][k] * Wi[j][i]) * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
              } else {
                df.set(i, k, 0);
              }
            }
          }

          SimpleMatrix dx = null;
          try {
            // Check if the determinant is close to zero
            double determinant = df.determinant();
            if (Math.abs(determinant) < 1e-10) {
              logger.warn("Matrix is nearly singular. Determinant: " + determinant);
              // Add a small regularization term to stabilize the solution
              dx = df.plus(identitytimesConst.scale(1e-6)).solve(f).negative();
            } else {
              dx = df.plus(identitytimesConst).solve(f).negative();
            }
          } catch (Exception e) {
            logger.error("Error solving matrix equation: " + e.getMessage());
            logger.debug("Attempting fallback with scaled regularization...");
            try {
              // Fallback: Add a larger regularization term and retry
              dx = df.plus(identitytimesConst.scale(0.5)).solve(f).negative();
            } catch (Exception ex) {
              logger.error("Fallback matrix solve failed: " + ex.getMessage());
              logger.debug("Attempting pseudo-inverse fallback...");
              try {
                DMatrixRMaj pinv = new DMatrixRMaj(df.numCols(), df.numRows());
                CommonOps_DDRM.pinv(df.getDDRM(), pinv);
                DMatrixRMaj result = new DMatrixRMaj(df.numCols(), 1);
                CommonOps_DDRM.mult(pinv, f.getDDRM(), result);
                dx = SimpleMatrix.wrap(result).negative();
                logger.warn("Used pseudo-inverse matrix solve.");
              } catch (Exception ex2) {
                logger.error("Pseudo-inverse fallback failed: " + ex2.getMessage());
                logger.warn("Setting dx to zero matrix as a last resort.");
                dx = new SimpleMatrix(f.numRows(), f.numCols());
              }
            }
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double alphaNew = alpha[i] + dx.get(i, 0);
            Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              logWi[i] = Math.log(Wi[j][i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -1000.0;
            }
            err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += safeExp(logWi[i]);
        }

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(safeExp(logWi[i]) / sumw[j]);
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while (!enhancedTrialInitFailed && (Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);

      if (enhancedTrialInitFailed) {
        tm[j] = 10.0;
        continue;
      }

      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= maxiter - 1) {
        // logger.info("iter > maxiter multiphase stability ");
        // logger.info("error " + Math.abs(err));
        // logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1, system.getPhase(0).getComponent(unstabcomp).getz());
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("stabilityAnalysisEnhanced pure-comp addPhase init failed: " + ex.getMessage());
          system.removePhaseKeepTotalComposition(system.getNumberOfPhases() - 1);
          multiPhaseTest = false;
          return;
        }
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }

    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  /**
   * stabilityAnalysis2.
   */
  public void stabilityAnalysis2() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0).getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getx() < 1e-100) {
        clonedSystem.add(null);
        continue;
      }
      double numb = 0;
      clonedSystem.add(system.clone());
      // (clonedSystem.get(i)).init(0); commented out sept 2005, Even
      // S.
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        numb = i == j ? 1.0 : 1.0e-12;
        if (system.getPhase(0).getComponent(j).getz() < 1e-100) {
          numb = 0;
        }
        (clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb);
      }
      if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
        (clonedSystem.get(i)).init(1);
      }
    }

    lowestGibbsEnergyPhase = 0;

    // logger.info("low gibbs phase " + lowestGibbsEnergyPhase);
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
        if (!((clonedSystem.get(k)) == null)) {
          sumw[k] += (clonedSystem.get(k)).getPhase(1).getComponent(i).getx();
        }
      }
    }

    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
        if (!((clonedSystem.get(k)) == null) && system.getPhase(0).getComponent(k).getx() > 1e-100) {
          (clonedSystem.get(k)).getPhase(1).getComponent(i)
              .setx((clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]);
        }
        // logger.info("x: " + (
        // clonedSystem.get(k)).getPhase(0).getComponent(i).getx());
      }
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
      // logger.info("dk: " + d[k]);
    }

    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 1.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon() && j != hydrocarbonTestCompNumb
              && j != lightTestCompNumb)) {
        continue;
      }
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= 150 || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 7 == 0) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          } else {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            (clonedSystem.get(j)).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite((clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i] - (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if ((clonedSystem.get(j)).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          }
        } else {
          SimpleMatrix f = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(), 1);
          SimpleMatrix df = null;
          SimpleMatrix identitytimesConst = null;
          // if (!secondOrderStabilityAnalysis) {
          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          (clonedSystem.get(j)).init(3, 1);
          alpha = new double[(clonedSystem.get(j)).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // , system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < (clonedSystem.get(j)).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + (clonedSystem.get(j)).getPhases()[1].getComponent(i).getLogFugacityCoefficient() - d[i]));
            }
            for (int k = 0; k < (clonedSystem.get(j)).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * (clonedSystem.get(j)).getPhases()[1].getComponent(i).getdfugdn(k));
                // *
                // clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // *
                // clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              }
            }
          }
          // f.print(10, 10);
          // df.print(10, 10);
          SimpleMatrix dx = null;
          try {
            // Check if the determinant is close to zero
            double determinant = df.determinant();
            if (Math.abs(determinant) < 1e-10) {
              logger.warn("Matrix is nearly singular. Determinant: " + determinant);
              // Add a small regularization term to stabilize the solution
              dx = df.plus(identitytimesConst.scale(1e-6)).solve(f).negative();
            } else {
              dx = df.plus(identitytimesConst).solve(f).negative();
            }
          } catch (Exception e) {
            logger.error("Error solving matrix equation: " + e.getMessage());
            logger.debug("Attempting fallback with scaled regularization...");
            try {
              // Fallback: Add a larger regularization term and retry
              dx = df.plus(identitytimesConst.scale(0.5)).solve(f).negative();
            } catch (Exception ex) {
              logger.error("Fallback matrix solve failed: " + ex.getMessage());
              throw new RuntimeException("Matrix solve failed after fallback attempts", ex);
            }
          }

          // dx.print(10, 10);

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double alphaNew = alpha[i] + dx.get(i, 0);
            Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              logWi[i] = Math.log(Wi[j][i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -1000.0;
            }
            err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += safeExp(logWi[i]);
        }

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(safeExp(logWi[i]) / sumw[j]);
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < 200);
      if (iter > 198) {
        // System.out.println("too many iterations....." + err + " temperature "
        // + system.getTemperature("C") + " C " + system.getPressure("bara") + " bara");
        throw new RuntimeException(
            new neqsim.util.exception.TooManyIterationsException(this, "stabilityAnalysis2", 200));
      }
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = (clonedSystem.get(j)).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= 199) {
        logger.info("iter > maxiter multiphase stability ");
        logger.info("error " + Math.abs(err));
        logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-6 || Math.abs(xTrivialCheck1) < 1e-6) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1, system.getPhase(0).getComponent(unstabcomp).getz());
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("stabilityAnalysis3 addPhase init failed: " + ex.getMessage());
          system.removePhaseKeepTotalComposition(system.getNumberOfPhases() - 1);
          multiPhaseTest = false;
          return;
        }
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }
    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  /**
   * Adds a bounded vapor-like trial when an aqueous/hydrocarbon endpoint contains no gas phase.
   *
   * <p>
   * The trial uses {@code x_i proportional to z_i K_i^Wilson} in log space. Wilson K-values are only an initial guess;
   * the existing multiphase beta solve, material-balance checks, and fugacity-equality checks determine the accepted
   * equilibrium. Bounding {@code ln(K_i)} avoids overflow for component sets with large volatility contrasts.
   * </p>
   *
   * @return {@code true} when a gas trial was added and initialized
   */
  private boolean seedAdditionalPhaseFromFeed() {
    if (!system.doMultiPhaseCheck()) {
      return false;
    }
    if (system.getNumberOfPhases() >= 3) {
      return false;
    }
    boolean hasAqueous = false;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseType type = system.getPhase(phase).getType();
      if (type == PhaseType.GAS && system.getPhase(phase).getBeta() > 1.0e-6) {
        return false;
      }
      if (type == PhaseType.AQUEOUS) {
        hasAqueous = true;
      }
    }
    if (!hasAqueous) {
      return false;
    }
    double waterZ = 0.0;
    try {
      waterZ = system.getComponent("water").getz();
    } catch (Exception ex) {
      for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
        if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
          waterZ = system.getPhase(0).getComponent(comp).getz();
          break;
        }
      }
    }
    if (waterZ < 1.0e-4) {
      return false;
    }
    boolean hasHydrocarbon = false;
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      if (system.getPhase(0).getComponent(comp).isHydrocarbon()
          && system.getPhase(0).getComponent(comp).getz() > 1.0e-4) {
        hasHydrocarbon = true;
        break;
      }
    }
    if (!hasHydrocarbon) {
      return false;
    }
    system.addPhase();
    int phaseIndex = system.getNumberOfPhases() - 1;
    system.setPhaseType(phaseIndex, PhaseType.GAS);
    double[] logTrialComposition = new double[system.getPhase(0).getNumberOfComponents()];
    double maximumLogTrialComposition = Double.NEGATIVE_INFINITY;
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      ComponentInterface component = system.getPhase(0).getComponent(comp);
      double z = component.getz();
      double logTrial = Math.log(Math.max(z, 1.0e-100));
      double criticalTemperature = component.getTC();
      double criticalPressure = component.getPC();
      if (z > 0.0 && criticalTemperature > 0.0 && criticalPressure > 0.0) {
        double logWilsonK = Math.log(criticalPressure / system.getPressure())
            + 5.373 * (1.0 + component.getAcentricFactor()) * (1.0 - criticalTemperature / system.getTemperature());
        if (Double.isFinite(logWilsonK)) {
          logTrial += Math.max(-50.0, Math.min(50.0, logWilsonK));
        }
      }
      logTrialComposition[comp] = logTrial;
      maximumLogTrialComposition = Math.max(maximumLogTrialComposition, logTrial);
    }
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      double x = Math.exp(logTrialComposition[comp] - maximumLogTrialComposition);
      system.getPhase(phaseIndex).getComponent(comp).setx(Math.max(x, 1.0e-16));
    }
    system.getPhases()[phaseIndex].normalize();
    double initialBeta = Math.max(1.0e-3, 1000.0 * phaseFractionMinimumLimit);
    system.setBeta(phaseIndex, initialBeta);
    system.normalizeBeta();
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("seedGasPhase init failed: " + ex.getMessage());
      return false;
    }
    return true;
  }

  /**
   * Ensures only one aqueous phase exists in the system. The aqueous phase is the one with the highest aqueous
   * component content (water, MEG, TEG, DEG, methanol, ethanol, and ions). Other liquid phases are reclassified as OIL
   * by moving their aqueous components (water, glycols, ions) to the true aqueous phase and keeping hydrocarbons in the
   * oil phase. This method applies to systems with ions (where ions must be confined to the aqueous phase) or chemical
   * systems.
   */
  private void ensureSingleAqueousPhase() {
    // Only needed for systems with ions or chemical systems - skip for simple molecular systems
    if ((!system.isChemicalSystem() && !system.hasIons()) || system.getNumberOfPhases() < 2) {
      return;
    }

    // Count how many non-gas phases are classified as AQUEOUS
    int aqueousCount = 0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        aqueousCount++;
      }
    }

    if (aqueousCount <= 1) {
      return; // Already have at most one aqueous phase
    }

    // Hydrate and non-reactive electrolyte flashes select the aqueous phase containing the largest material amount of
    // aqueous components. Weighting by beta prevents a salt-free numerical phase at the phase-fraction floor from
    // replacing the material brine. Other reactive operations retain their established composition-only selection.
    boolean useMaterialAqueousInventory = !system.isChemicalSystem() || isCoupledReactiveHydrateFlash();
    int bestAqueousPhase = -1;
    double maxAqueousInventory = -1.0;

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if ((useMaterialAqueousInventory && system.getPhase(phase).getType() != PhaseType.AQUEOUS)
          || (!useMaterialAqueousInventory && system.getPhase(phase).getType() == PhaseType.GAS)) {
        continue;
      }

      double aqueousContent = 0.0;
      for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
        ComponentInterface component = system.getPhase(phase).getComponent(comp);
        String name = component.getComponentName().toLowerCase();
        // Count water, glycols, alcohols, and ions as aqueous components
        if (name.equals("water") || name.equals("meg") || name.equals("teg") || name.equals("deg")
            || name.equals("methanol") || name.equals("ethanol") || component.getIonicCharge() != 0
            || component.isIsIon()) {
          aqueousContent += component.getx();
        }
      }

      double aqueousInventory = useMaterialAqueousInventory ? system.getBeta(phase) * aqueousContent : aqueousContent;
      if (aqueousInventory > maxAqueousInventory) {
        maxAqueousInventory = aqueousInventory;
        bestAqueousPhase = phase;
      }
    }

    if (bestAqueousPhase < 0) {
      return;
    }

    // For phases that are AQUEOUS but not the best aqueous phase:
    // Move hydrocarbons to dominate, set aqueous components and ions to trace
    // This will cause init() to reclassify them as OIL
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (phase == bestAqueousPhase || system.getPhase(phase).getType() == PhaseType.GAS) {
        continue;
      }

      if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        // This phase should become OIL - adjust compositions
        // Set ions and most aqueous components to trace amounts
        for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
          ComponentInterface component = system.getPhase(phase).getComponent(comp);
          String name = component.getComponentName().toLowerCase();

          if (component.getIonicCharge() != 0 || component.isIsIon()) {
            // Ions only in aqueous phase
            component.setx(1e-50);
          } else if (name.equals("water")) {
            // Reduce water significantly but keep trace for solubility
            component.setx(Math.min(component.getx() * 0.01, 1e-4));
          } else if (name.equals("meg") || name.equals("teg") || name.equals("deg") || name.equals("methanol")
              || name.equals("ethanol")) {
            // Reduce glycols/alcohols
            component.setx(Math.min(component.getx() * 0.1, 1e-3));
          }
          // Hydrocarbons keep their current x values
        }
        system.getPhase(phase).normalize();
      }
    }

    // Reinitialize - phase types will be recalculated based on new compositions
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("ensureSingleAqueousPhase init failed: " + ex.getMessage());
    }
  }

  private boolean seedHydrocarbonLiquidFromFeed() {
    if (!system.doMultiPhaseCheck()) {
      return false;
    }
    if (system.getNumberOfPhases() >= 3 || system.hasPhaseType(PhaseType.OIL)
        || !system.hasPhaseType(PhaseType.AQUEOUS)) {
      return false;
    }

    double waterZ = 0.0;
    try {
      waterZ = system.getComponent("water").getz();
    } catch (Exception ex) {
      for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
        if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
          waterZ = system.getPhase(0).getComponent(comp).getz();
          break;
        }
      }
    }

    if (waterZ < 1.0e-6) {
      return false;
    }

    double heavyHydrocarbonTotal = 0.0;
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      ComponentInterface component = system.getPhase(0).getComponent(comp);
      if (component.isHydrocarbon() && component.getz() > 1.0e-6 && component.getMolarMass() > 0.045) {
        heavyHydrocarbonTotal += component.getz();
      }
    }
    // Seed oil phase if there's significant heavy hydrocarbon content
    // For electrolyte/chemical systems, allow seeding even when water > hydrocarbons
    // because oil-water separation is physically expected
    boolean shouldSeedOil = heavyHydrocarbonTotal >= 5.0e-3;
    if (!system.isChemicalSystem()) {
      // For non-chemical systems, also require hydrocarbons > water
      shouldSeedOil = shouldSeedOil && heavyHydrocarbonTotal > waterZ;
    }
    if (!shouldSeedOil) {
      return false;
    }

    system.addPhase();
    int phaseIndex = system.getNumberOfPhases() - 1;
    system.setPhaseType(phaseIndex, PhaseType.OIL);

    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      ComponentInterface component = system.getPhase(0).getComponent(comp);
      double z = component.getz();
      double x = 1.0e-16;
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        x = 1.0e-16;
      } else if (component.isHydrocarbon()) {
        if (component.getMolarMass() > 0.045) {
          x = Math.max(z, 1.0e-12);
        } else {
          x = Math.min(z * 1.0e-2, 1.0e-8);
        }
      } else if ("water".equalsIgnoreCase(component.getComponentName())) {
        x = Math.min(z * 1.0e-2, 1.0e-8);
      }
      system.getPhase(phaseIndex).getComponent(comp).setx(x);
    }

    system.getPhases()[phaseIndex].normalize();
    double initialBeta = Math.max(1.0e-5, 10.0 * phaseFractionMinimumLimit);
    system.setBeta(phaseIndex, initialBeta);
    system.normalizeBeta();
    try {
      system.init(1);
    } catch (Exception ex) {
      logger.warn("seedAqueousPhase init failed: " + ex.getMessage());
      return false;
    }
    return true;
  }

  /**
   * Restores ions after phase stability has been evaluated on the ion-free molecular fluid.
   *
   * <p>
   * The ion-free flash returns phase fractions on a molecular-feed basis. Adding the conserved ion inventory to the
   * aqueous phase therefore requires both a phase-fraction transformation and a composition transformation. Assigning
   * an ion mole fraction directly from its overall composition violates {@code z_i = beta_aqueous x_i} and dilutes the
   * brine whenever the aqueous phase occupies less than the complete feed. The transformation below preserves every
   * molecular component, confines ions to one aqueous phase, and keeps both phase fractions and compositions
   * normalized.
   * </p>
   *
   * @param overallZ overall mole fractions captured before the ion-free flash calculation
   * @return index of the aqueous phase that received the ion inventory, or {@code -1} when no aqueous phase exists
   */
  private int restoreIonsToAqueousPhase(double[] overallZ) {
    int aqueousPhase = findPreferredAqueousPhase();
    if (aqueousPhase < 0) {
      logger.warn("Cannot restore ionic inventory because the flash has no aqueous phase");
      return -1;
    }

    double ionicFraction = 0.0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      if (isIon(component)) {
        ionicFraction += Math.max(overallZ[component], 0.0);
      }
    }
    if (ionicFraction <= 0.0) {
      return aqueousPhase;
    }
    if (ionicFraction >= 1.0) {
      throw new IllegalStateException("Overall ionic mole fraction must be smaller than one");
    }

    double molecularFraction = 1.0 - ionicFraction;
    double ionFreeAqueousBeta = system.getBeta(aqueousPhase);
    double aqueousBeta = ionFreeAqueousBeta * molecularFraction + ionicFraction;
    double aqueousMolecularScale = ionFreeAqueousBeta * molecularFraction / aqueousBeta;

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double phaseBeta = system.getBeta(phase);
      system.setBeta(phase, phase == aqueousPhase ? aqueousBeta : phaseBeta * molecularFraction);

      for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
        ComponentInterface phaseComponent = system.getPhase(phase).getComponent(component);
        phaseComponent.setz(overallZ[component]);
        if (isIon(component)) {
          phaseComponent.setx(phase == aqueousPhase ? overallZ[component] / aqueousBeta : 1.0e-50);
        } else if (phase == aqueousPhase) {
          phaseComponent.setx(phaseComponent.getx() * aqueousMolecularScale);
        }
      }
    }

    system.normalizeBeta();
    try {
      system.init(1);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to initialize the ion-restored phase inventory", ex);
    }
    return aqueousPhase;
  }

  /**
   * Project the reaction-adjusted overall inventory onto the current material phase topology.
   *
   * <p>
   * Reactions are confined to the aqueous phase. The non-aqueous phase compositions and phase fractions therefore
   * retain the converged phase-equilibrium state, while the aqueous amount of every species is obtained from the exact
   * balance {@code z_i = sum(beta_p x_i,p)}. This projection keeps fixed salts and generated ions out of gas and oil,
   * closes every final species balance, and provides the next aqueous-activity state for chemical equilibrium.
   * </p>
   *
   * @return maximum absolute species-balance residual
   */
  private double projectReactiveInventoryOntoCurrentPhases() {
    int aqueousPhase = findPreferredAqueousPhase();
    if (aqueousPhase < 0) {
      throw new IllegalStateException("Reactive multiphase equilibrium requires an aqueous phase");
    }
    system.normalizeBeta();
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (phase != aqueousPhase) {
        system.getPhase(phase).normalize();
      }
    }

    double aqueousBeta = system.getBeta(aqueousPhase);
    if (!(aqueousBeta > phaseFractionMinimumLimit)) {
      throw new IllegalStateException("Reactive aqueous phase has an invalid phase fraction: " + aqueousBeta);
    }
    for (int component = 0; component < reactiveOverallFractions.length; component++) {
      double nonAqueousContribution = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        ComponentInterface phaseComponent = system.getPhase(phase).getComponent(component);
        if (phase == aqueousPhase) {
          continue;
        }
        if (isIon(component)) {
          phaseComponent.setx(1.0e-50);
        }
        nonAqueousContribution += system.getBeta(phase) * phaseComponent.getx();
      }
      double aqueousFraction = (reactiveOverallFractions[component] - nonAqueousContribution) / aqueousBeta;
      if (!Double.isFinite(aqueousFraction) || aqueousFraction < -1.0e-10) {
        throw new IllegalStateException("Reactive phase projection produced an invalid aqueous amount for "
            + system.getPhase(0).getComponent(component).getComponentName() + ": " + aqueousFraction);
      }
      system.getPhase(aqueousPhase).getComponent(component).setx(Math.max(aqueousFraction, 1.0e-50));
    }

    double aqueousSum = 0.0;
    for (int component = 0; component < reactiveOverallFractions.length; component++) {
      aqueousSum += system.getPhase(aqueousPhase).getComponent(component).getx();
    }
    if (Math.abs(aqueousSum - 1.0) > 1.0e-8) {
      throw new IllegalStateException("Reactive aqueous composition is not normalized after projection: " + aqueousSum);
    }
    system.getPhase(aqueousPhase).normalize();
    system.init(1);

    double maximumResidual = 0.0;
    for (int component = 0; component < reactiveOverallFractions.length; component++) {
      double recoveredFraction = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recoveredFraction += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(reactiveOverallFractions[component] - recoveredFraction));
    }
    return maximumResidual;
  }

  /**
   * Finds the active aqueous phase containing the largest material amount of water.
   *
   * <p>
   * Multiplying water composition by phase fraction prevents a salt-free numerical phase at the beta floor from
   * being selected ahead of the material brine.
   * </p>
   *
   * @return active aqueous phase index, or {@code -1} when none exists
   */
  private int findPreferredAqueousPhase() {
    int aqueousPhase = -1;
    double highestWaterInventory = -1.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() != PhaseType.AQUEOUS) {
        continue;
      }
      double waterFraction = system.getPhase(phase).hasComponent("water")
          ? system.getPhase(phase).getComponent("water").getx()
          : 0.0;
      double waterInventory = system.getBeta(phase) * waterFraction;
      if (waterInventory > highestWaterInventory) {
        highestWaterInventory = waterInventory;
        aqueousPhase = phase;
      }
    }
    return aqueousPhase;
  }

  /**
   * Removes phases left at the numerical beta floor before ions are restored.
   *
   * <p>
   * Ion-free stability analysis can leave a third gas, oil, or duplicate aqueous phase with a fraction of only a few
   * times {@link neqsim.thermo.ThermodynamicModelSettings#phaseFractionMinimumLimit}. Such a phase is below the
   * incipient-phase seed used by this flash and can make generic aqueous-phase lookup select the wrong liquid. The
   * material aqueous phase is always retained. Material gas-oil-aqueous topology is preserved, while two aqueous
   * phases can collapse to one when the second phase is only numerical storage.
   * </p>
   *
   * @return {@code true} when a numerical phase was removed
   */
  private boolean removeNumericalTracePhasesForIonicFlash() {
    if (!system.hasIons() || system.getNumberOfPhases() <= 1) {
      return false;
    }

    int preferredAqueousPhase = findPreferredAqueousPhase();
    boolean removedPhase = false;
    for (int phase = system.getNumberOfPhases() - 1; phase >= 0 && system.getNumberOfPhases() > 1; phase--) {
      boolean duplicateAqueousPhase = phase != preferredAqueousPhase
          && system.getPhase(phase).getType() == PhaseType.AQUEOUS;
      if ((!duplicateAqueousPhase && system.getNumberOfPhases() <= 2) || phase == preferredAqueousPhase
          || system.getBeta(phase) >= 100.0 * phaseFractionMinimumLimit) {
        continue;
      }
      system.removePhaseKeepTotalComposition(phase);
      removedPhase = true;
      if (phase < preferredAqueousPhase) {
        preferredAqueousPhase--;
      }
    }

    if (removedPhase) {
      system.normalizeBeta();
      system.init(1);
    }
    return removedPhase;
  }

  /**
   * Checks whether a component is ionic in the active thermodynamic model.
   *
   * @param component component index
   * @return {@code true} for charged or explicitly tagged ion components
   */
  private boolean isIon(int component) {
    ComponentInterface feedComponent = system.getPhase(0).getComponent(component);
    return feedComponent.getIonicCharge() != 0 || feedComponent.isIsIon();
  }

  /**
   * Return whether this flash owns coupled phase and reaction equilibrium for an electrolyte hydrate calculation.
   *
   * @return {@code true} for reactive hydrate flashes
   */
  private boolean isCoupledReactiveHydrateFlash() {
    return system.isChemicalSystem() && system.getHydrateCheck();
  }

  /**
   * Capture the exact overall species inventory before coupled phase and chemical equilibrium.
   */
  private void initializeReactiveOverallInventory() {
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    if (reactiveOverallMoles != null && reactiveOverallMoles.length == numberOfComponents) {
      return;
    }
    reactiveOverallMoles = new double[numberOfComponents];
    reactiveOverallFractions = new double[numberOfComponents];
    double systemTotalMoles = system.getNumberOfMoles();
    double totalMoles = 0.0;
    for (int component = 0; component < numberOfComponents; component++) {
      double moles = systemTotalMoles * system.getPhase(0).getComponent(component).getz();
      if (!Double.isFinite(moles) || moles < 0.0) {
        throw new IllegalStateException("Invalid reactive feed amount for "
            + system.getPhase(0).getComponent(component).getComponentName() + ": " + moles);
      }
      reactiveOverallMoles[component] = moles;
      totalMoles += moles;
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      throw new IllegalStateException("Reactive flash requires a finite, positive species inventory");
    }
    for (int component = 0; component < numberOfComponents; component++) {
      reactiveOverallFractions[component] = reactiveOverallMoles[component] / totalMoles;
    }
  }

  /**
   * Solve aqueous chemical equilibrium and propagate its conservative species changes to the overall flash inventory.
   *
   * @param aqueousPhase active aqueous phase index
   * @param initialise whether to request the chemical solver's initial-estimate stage
   * @return sum of absolute aqueous mole-fraction changes
   */
  private double solveReactiveAqueousEquilibrium(int aqueousPhase, boolean initialise) {
    if (aqueousPhase < 0 || aqueousPhase >= system.getNumberOfPhases()) {
      return 0.0;
    }
    initializeReactiveOverallInventory();
    int numberOfComponents = system.getPhase(aqueousPhase).getNumberOfComponents();
    double[] oldComposition = new double[numberOfComponents];
    double[] oldAqueousMoles = new double[numberOfComponents];
    for (int component = 0; component < numberOfComponents; component++) {
      ComponentInterface phaseComponent = system.getPhase(aqueousPhase).getComponent(component);
      oldComposition[component] = phaseComponent.getx();
      oldAqueousMoles[component] = phaseComponent.getNumberOfMolesInPhase();
    }

    if (initialise) {
      system.getChemicalReactionOperations().solveChemEq(aqueousPhase, 0);
    }
    system.getChemicalReactionOperations().solveChemEq(aqueousPhase, 1);

    double[] reactionDeltas = getConservativeReactionDeltas(aqueousPhase, oldAqueousMoles);
    double[] updatedOverallMoles = new double[numberOfComponents];
    for (int component = 0; component < numberOfComponents; component++) {
      updatedOverallMoles[component] = reactiveOverallMoles[component] + reactionDeltas[component];
      if (!Double.isFinite(updatedOverallMoles[component]) || updatedOverallMoles[component] < -1.0e-9) {
        throw new IllegalStateException("Aqueous chemical equilibrium produced an invalid overall amount for "
            + system.getPhase(0).getComponent(component).getComponentName() + ": " + updatedOverallMoles[component]);
      }
      updatedOverallMoles[component] = Math.max(MINIMUM_REACTIVE_COMPONENT_MOLES, updatedOverallMoles[component]);
    }
    for (int component = 0; component < numberOfComponents; component++) {
      double appliedDelta = updatedOverallMoles[component] - reactiveOverallMoles[component];
      double projectedAqueousMoles = oldAqueousMoles[component] + appliedDelta;
      ComponentInterface phaseComponent = system.getPhase(aqueousPhase).getComponent(component);
      system.getPhase(aqueousPhase).addMoles(component,
          projectedAqueousMoles - phaseComponent.getNumberOfMolesInPhase());
      reactiveOverallMoles[component] = updatedOverallMoles[component];
    }
    synchronizeReactiveOverallComposition();

    double chemicalDeviation = 0.0;
    for (int component = 0; component < numberOfComponents; component++) {
      double moleFraction = system.getPhase(aqueousPhase).getComponent(component).getx();
      if (!Double.isFinite(moleFraction) || moleFraction < 0.0) {
        return Double.POSITIVE_INFINITY;
      }
      chemicalDeviation += Math.abs(oldComposition[component] - moleFraction);
    }
    return chemicalDeviation;
  }

  /**
   * Preserve the established chemical-equilibrium iteration for non-hydrate multiphase calculations.
   *
   * @param aqueousPhase active aqueous phase index
   * @param initialise whether to request the chemical solver's initial-estimate stage
   * @return sum of absolute aqueous mole-fraction changes
   */
  private double solveLegacyAqueousEquilibrium(int aqueousPhase, boolean initialise) {
    if (aqueousPhase < 0 || aqueousPhase >= system.getNumberOfPhases()) {
      return 0.0;
    }
    int numberOfComponents = system.getPhase(aqueousPhase).getNumberOfComponents();
    double[] oldComposition = new double[numberOfComponents];
    for (int component = 0; component < numberOfComponents; component++) {
      oldComposition[component] = system.getPhase(aqueousPhase).getComponent(component).getx();
    }
    if (initialise) {
      system.getChemicalReactionOperations().solveChemEq(aqueousPhase, 0);
    }
    system.getChemicalReactionOperations().solveChemEq(aqueousPhase, 1);

    double chemicalDeviation = 0.0;
    for (int component = 0; component < numberOfComponents; component++) {
      chemicalDeviation += Math
          .abs(oldComposition[component] - system.getPhase(aqueousPhase).getComponent(component).getx());
    }
    return chemicalDeviation;
  }

  /**
   * Project a chemical-solver update onto the element-and-charge conservation null space.
   *
   * @param aqueousPhase active aqueous phase index
   * @param oldAqueousMoles aqueous species amounts before chemical equilibrium
   * @return conservative overall species changes
   */
  private double[] getConservativeReactionDeltas(int aqueousPhase, double[] oldAqueousMoles) {
    ComponentInterface[] reactiveComponents = system.getChemicalReactionOperations().getComponents();
    double[][] conservationArray = system.getChemicalReactionOperations().getAmatrix();
    double[] reactionDeltas = new double[system.getPhase(0).getNumberOfComponents()];
    if (reactiveComponents == null || reactiveComponents.length == 0 || conservationArray == null
        || conservationArray.length == 0) {
      return reactionDeltas;
    }

    SimpleMatrix rawDelta = new SimpleMatrix(reactiveComponents.length, 1);
    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      int component = reactiveComponents[reactiveIndex].getComponentNumber();
      double newMoles = system.getPhase(aqueousPhase).getComponent(component).getNumberOfMolesInPhase();
      rawDelta.set(reactiveIndex, 0, newMoles - oldAqueousMoles[component]);
    }
    SimpleMatrix conservationMatrix = new SimpleMatrix(conservationArray);
    SimpleMatrix conservativeDelta = rawDelta
        .minus(conservationMatrix.pseudoInverse().mult(conservationMatrix).mult(rawDelta));
    SimpleMatrix conservationPseudoInverse = conservationMatrix.pseudoInverse();
    for (int iteration = 0; iteration < 100; iteration++) {
      boolean satisfiesLowerBounds = true;
      for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
        int component = reactiveComponents[reactiveIndex].getComponentNumber();
        double lowerBound = MINIMUM_REACTIVE_COMPONENT_MOLES - reactiveOverallMoles[component];
        if (conservativeDelta.get(reactiveIndex, 0) < lowerBound) {
          conservativeDelta.set(reactiveIndex, 0, lowerBound);
          satisfiesLowerBounds = false;
        }
      }
      SimpleMatrix conservationResidual = conservationMatrix.mult(conservativeDelta);
      if (satisfiesLowerBounds && conservationResidual.normF() <= 1.0e-12) {
        break;
      }
      conservativeDelta = conservativeDelta.minus(conservationPseudoInverse.mult(conservationResidual));
    }

    boolean feasible = conservationMatrix.mult(conservativeDelta).normF() <= 1.0e-10;
    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      int component = reactiveComponents[reactiveIndex].getComponentNumber();
      feasible = feasible && reactiveOverallMoles[component] + conservativeDelta.get(reactiveIndex, 0) >= -1.0e-12;
    }
    if (!feasible) {
      logger.warn("Discarding an infeasible reactive species update after element-and-charge projection");
      conservativeDelta = new SimpleMatrix(reactiveComponents.length, 1);
    }
    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      reactionDeltas[reactiveComponents[reactiveIndex].getComponentNumber()] = conservativeDelta.get(reactiveIndex, 0);
    }
    return reactionDeltas;
  }

  /**
   * Synchronize exact reaction-adjusted species amounts and normalized fractions across all allocated phase objects.
   */
  private void synchronizeReactiveOverallComposition() {
    reactiveOverallFractions = new double[reactiveOverallMoles.length];
    double totalMoles = 0.0;
    for (double componentMoles : reactiveOverallMoles) {
      totalMoles += componentMoles;
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      throw new IllegalStateException("Reactive species inventory has an invalid total amount: " + totalMoles);
    }

    system.setTotalNumberOfMoles(totalMoles);
    for (int component = 0; component < reactiveOverallMoles.length; component++) {
      reactiveOverallFractions[component] = reactiveOverallMoles[component] / totalMoles;
    }
    for (int phase = 0; phase < system.getMaxNumberOfPhases(); phase++) {
      if (!system.isPhase(phase)) {
        continue;
      }
      for (int component = 0; component < reactiveOverallMoles.length; component++) {
        ComponentInterface phaseComponent = system.getPhase(phase).getComponent(component);
        phaseComponent.setNumberOfmoles(reactiveOverallMoles[component]);
        phaseComponent.setz(reactiveOverallFractions[component]);
      }
    }
    system.initBeta();
    system.normalizeBeta();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int aqueousPhaseNumber = 0;
    enhancedStabilityChecked = false;
    betaSolveStalled = false;
    if (isCoupledReactiveHydrateFlash()) {
      initializeReactiveOverallInventory();
    }
    // logger.info("Starting multiphase-flash....");

    // For systems with ions, temporarily remove ions before stability analysis.
    // Non-reactive electrolyte systems remain on a normalized molecular-feed basis until the complete multiphase
    // calculation has converged. Reactive systems restore the ions after phase discovery and then couple the
    // reaction-adjusted species inventory to the phase-fraction solve.
    double[] ionFreeOverallZ = null;
    double[] legacyIonicZ = null;
    boolean hasIons = system.hasIons();
    boolean useIonFreeFlash = hasIons && (!system.isChemicalSystem() || isCoupledReactiveHydrateFlash());

    // Hydrate and non-reactive electrolyte flashes stay on a normalized molecular basis until conservative ion
    // restoration. Other reactive operations retain the established ion stripping/restoration path because their
    // component inventories may be intentionally changed by specialized operations such as salt saturation.
    if (useIonFreeFlash) {
      ionFreeOverallZ = new double[system.getPhase(0).getNumberOfComponents()];
      double ionicFraction = 0.0;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        ionFreeOverallZ[i] = getFlashOverallFraction(i);
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0 || system.getPhase(0).getComponent(i).isIsIon()) {
          ionicFraction += Math.max(ionFreeOverallZ[i], 0.0);
          // Temporarily set ion z to near-zero for stability analysis
          for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
            system.getPhase(phase).getComponent(i).setz(1e-100);
            system.getPhase(phase).getComponent(i).setx(1e-50);
          }
        }
      }
      if (ionicFraction >= 1.0) {
        throw new IllegalStateException("Overall ionic mole fraction must be smaller than one");
      }
      double molecularFraction = 1.0 - ionicFraction;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
          if (!isIon(component)) {
            system.getPhase(phase).getComponent(component).setz(ionFreeOverallZ[component] / molecularFraction);
          }
        }
        system.getPhase(phase).normalize();
      }
      try {
        system.init(1);
      } catch (Exception ex) {
        logger.warn("Ion-stripping init failed: " + ex.getMessage());
      }
    } else if (hasIons) {
      legacyIonicZ = new double[system.getPhase(0).getNumberOfComponents()];
      for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
        if (isIon(component)) {
          legacyIonicZ[component] = system.getPhase(0).getComponent(component).getz();
          for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
            system.getPhase(phase).getComponent(component).setz(1.0e-100);
          }
        }
      }
      try {
        system.init(1);
      } catch (Exception ex) {
        logger.warn("Ion-stripping init failed: " + ex.getMessage());
      }
    }

    // system.setNumberOfPhases(system.getNumberOfPhases()+1);
    if (doStabilityAnalysis) {
      stabilityAnalysis();
      // If enhanced stability check is enabled and standard analysis didn't find additional
      // phases, try enhanced version which uses Wilson K-value initial guesses and tests both
      // vapor-like and liquid-like trial phases for more robust detection of liquid-liquid
      // equilibria (e.g., sour gas, CO2 systems)
      if (shouldApplyEnhancedMultiPhaseCheck() && !multiPhaseTest && system.getNumberOfPhases() < 3) {
        enhancedStabilityChecked = true;
        stabilityAnalysisEnhanced();
      }
    }

    // For ionic systems: stability analysis ran on the ion-free system and may have
    // introduced a spurious third phase that is essentially a duplicate of an existing
    // aqueous phase. Stripping ions removes the Debye-Hückel / Born / short-range
    // stabilisation that keeps the aqueous phase distinct, so the algorithm finds a
    // near-identical water-rich trial phase that passes the trivial-solution check
    // (threshold 1e-4). This leads to two nearly-identical aqueous phases that cause
    // solveBeta to diverge, destroying mass balance.
    // Fix: reject the new phase if its non-ionic composition is very similar to an
    // existing water-rich phase — this means it's a spurious duplicate caused by ion
    // stripping, not a genuine new phase. We compare only non-ionic component mole
    // fractions and reject if max |Δx| < 0.05 (true duplicates differ by < 0.01).
    if (hasIons && multiPhaseTest && system.getNumberOfPhases() > 2) {
      int newestPhase = system.getNumberOfPhases() - 1;
      double newestWaterX = 0;
      try {
        newestWaterX = system.getPhase(newestPhase).getComponent("water").getx();
      } catch (Exception ex) {
        // no water component
      }
      boolean isDuplicate = false;
      if (newestWaterX > 0.5) {
        for (int pp = 0; pp < newestPhase && !isDuplicate; pp++) {
          double existingWaterX = 0;
          try {
            existingWaterX = system.getPhase(pp).getComponent("water").getx();
          } catch (Exception ex) {
            continue;
          }
          if (existingWaterX <= 0.5) {
            continue;
          }
          // Both phases are water-dominated — compare non-ionic compositions
          double maxAbsDiff = 0;
          for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
            if (system.getPhase(0).getComponent(k).getIonicCharge() != 0) {
              continue;
            }
            double diff = Math
                .abs(system.getPhase(newestPhase).getComponent(k).getx() - system.getPhase(pp).getComponent(k).getx());
            if (diff > maxAbsDiff) {
              maxAbsDiff = diff;
            }
          }
          if (maxAbsDiff < 0.05) {
            isDuplicate = true;
          }
        }
      }
      if (isDuplicate) {
        // Spurious duplicate — revert to pre-stability state
        logger.debug("Rejecting spurious aqueous duplicate phase from ion-stripped stability analysis");
        system.removePhaseKeepTotalComposition(newestPhase);
        system.normalizeBeta();
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("init after spurious phase rejection failed: " + ex.getMessage());
        }
        multiPhaseTest = false;
      }
    }
    if (!multiPhaseTest && seedAdditionalPhaseFromFeed()) {
      multiPhaseTest = true;
      doStabilityAnalysis = false;
    }
    if (seedHydrocarbonLiquidFromFeed()) {
      multiPhaseTest = true;
      doStabilityAnalysis = false;
    }
    // system.orderByDensity();
    doStabilityAnalysis = true;

    // Debug: Check phases after stability analysis (before ion restoration)
    if (hasIons) {
      logger.debug("After stability analysis (ions removed): {} phases", system.getNumberOfPhases());
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        logger.debug("  Phase {} type: {}", phase, system.getPhase(phase).getType());
      }
    }

    if (hasIons && !useIonFreeFlash && legacyIonicZ != null) {
      for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
        if (!isIon(component) || legacyIonicZ[component] <= 1.0e-100) {
          continue;
        }
        for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
          ComponentInterface phaseComponent = system.getPhase(phase).getComponent(component);
          phaseComponent.setz(legacyIonicZ[component]);
          phaseComponent.setx(system.getPhase(phase).getType() == PhaseType.AQUEOUS ? legacyIonicZ[component]
              : 1.0e-50);
        }
      }
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        system.getPhase(phase).normalize();
      }
      try {
        system.init(1);
      } catch (Exception ex) {
        logger.warn("Ion-restore init failed: " + ex.getMessage());
      }
    }

    // Reactive hydrate systems require the complete ionic inventory before chemical equilibrium is solved. The
    // conservative restore transforms the ion-free phase fractions back to the full species basis.
    if (system.isChemicalSystem() && useIonFreeFlash && ionFreeOverallZ != null) {
      aqueousPhaseNumber = restoreIonsToAqueousPhase(ionFreeOverallZ);
      if (isCoupledReactiveHydrateFlash()) {
        synchronizeReactiveOverallComposition();
      }
    }

    // system.init(1);
    // system.display();
    aqueousPhaseNumber = system.hasPhaseType(PhaseType.AQUEOUS) ? system.getPhaseNumberOfPhase("aqueous") : -1;
    if (system.isChemicalSystem() && aqueousPhaseNumber >= 0) {
      if (isCoupledReactiveHydrateFlash()) {
        solveReactiveAqueousEquilibrium(aqueousPhaseNumber, true);
      } else {
        solveLegacyAqueousEquilibrium(aqueousPhaseNumber, true);
      }
    }

    int iterations = 0;
    if (multiPhaseTest) { // && !system.isChemicalSystem()) {
      double diff = 1.0e10;

      double oldDiff = 1.0e10;
      double chemdev = 0;
      int iterOut = 0;
      double maxerr = 1e-12;

      do {
        iterOut++;
        if (system.isChemicalSystem() && system.hasPhaseType(PhaseType.AQUEOUS)) {
          int currentAqueousPhase = system.getPhaseNumberOfPhase("aqueous");
          boolean initialiseChemistry = false;
          if (currentAqueousPhase != aqueousPhaseNumber) {
            aqueousPhaseNumber = currentAqueousPhase;
            initialiseChemistry = true;
          }

          if (aqueousPhaseNumber >= 0 && aqueousPhaseNumber < system.getNumberOfPhases()) {
            try {
              system.init(1);
              if (isCoupledReactiveHydrateFlash()) {
                chemdev = solveReactiveAqueousEquilibrium(aqueousPhaseNumber, initialiseChemistry);
              } else {
                chemdev = solveLegacyAqueousEquilibrium(aqueousPhaseNumber, initialiseChemistry);
              }
            } catch (Exception ex) {
              logger.warn("Chemical equilibrium init failed: {}", ex.getMessage());
              chemdev = 0.0;
            }
          }
        }
        setDoubleArrays();
        iterations = 0;
        do {
          iterations++;
          // oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
          // system.init(1);
          oldDiff = diff;
          diff = this.solveBeta();
          // diff = Math.abs((system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) /
          // oldBeta);
          // System.out.println("diff multiphase " + diff);
          if (iterations % 50 == 0) {
            maxerr *= 100.0;
          }
        } while (diff > maxerr && !removePhase && (diff < oldDiff || iterations < 50) && iterations < 200);
        // this.solveBeta(true);
        if (iterations >= 199) {
          logger.error("error in multiphase flash..did not solve in 200 iterations");
          logger.error(
              "diff " + diff + " temperaure " + system.getTemperature("C") + " pressure " + system.getPressure("bara"));
          diff = this.solveBeta();
        }
        if (isCoupledReactiveHydrateFlash()) {
          diff = Math.max(diff, projectReactiveInventoryOntoCurrentPhases());
        }
      } while ((Math.abs(chemdev) > 1e-10 && iterOut < 100)
          || (iterOut < 3 && system.isChemicalSystem() && system.hasPhaseType(PhaseType.AQUEOUS)));

      betaSolveStalled = diff > maxerr;

      // After flash converges, check for additional phases (three-phase detection)
      // This is particularly important for systems like CO2/H2S/hydrocarbon mixtures
      // that may exhibit vapor-liquid-liquid equilibrium
      if (system.doMultiPhaseCheck() && system.getNumberOfPhases() >= 2 && system.getNumberOfPhases() < 3
          && !postFlashStabilityChecked && !enhancedStabilityChecked) {
        postFlashStabilityChecked = true;
        int oldNumPhases = system.getNumberOfPhases();
        enhancedStabilityChecked = true;
        stabilityAnalysisEnhanced();
        if (system.getNumberOfPhases() > oldNumPhases) {
          // Found a third phase - re-run the flash calculation
          multiPhaseTest = true;
          doStabilityAnalysis = false;
          requestBoundedRerun();
        }
      }

      // Check if water is present and if an aqueous phase should be seeded
      // Only try to seed aqueous phase once per flash operation (not on recursive calls)
      if (system.hasComponent("water") && !aqueousPhaseSeedAttempted && system.doMultiPhaseCheck()
          && !system.hasPhaseType(PhaseType.AQUEOUS)) {
        aqueousPhaseSeedAttempted = true;
        double waterZ = 0.0;
        int waterComponentIndex = -1;
        try {
          waterZ = system.getComponent("water").getz();
          waterComponentIndex = system.getComponent("water").getComponentNumber();
        } catch (Exception ex) {
          for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
            if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
              waterZ = system.getPhase(0).getComponent(comp).getz();
              waterComponentIndex = comp;
              break;
            }
          }
        }

        // If water content is significant (> 1e-6), seed an aqueous phase.
        // Limit total active phases to a maximum of 3 (e.g. gas, liquid, aqueous) to avoid
        // indexing beyond what downstream algorithms expect. Do not create a new aqueous
        // phase if one already exists.
        if (waterZ > 1.0e-6 && waterComponentIndex >= 0 && system.getNumberOfPhases() < 3
            && !system.hasPhaseType(PhaseType.AQUEOUS)) {
          system.addPhase();
          int aquPhaseIndex = system.getNumberOfPhases() - 1;
          system.setPhaseType(aquPhaseIndex, PhaseType.AQUEOUS);

          // Initialize aqueous phase with water and trace amounts of other components
          for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
            double x = 1.0e-16;
            if (comp == waterComponentIndex) {
              // Concentrate water in aqueous phase
              x = Math.max(waterZ, 1.0e-12);
            } else if (!system.getPhase(0).getComponent(comp).isHydrocarbon()
                && !system.getPhase(0).getComponent(comp).isInert()) {
              // Other aqueous components get trace amounts
              x = Math.min(system.getPhase(0).getComponent(comp).getz() * 1.0e-2, 1.0e-8);
            }
            system.getPhase(aquPhaseIndex).getComponent(comp).setx(x);
          }

          system.getPhases()[aquPhaseIndex].normalize();
          double initialBeta = Math.max(1.0e-5, 10.0 * phaseFractionMinimumLimit);
          system.setBeta(aquPhaseIndex, initialBeta);
          system.normalizeBeta();
          try {
            system.init(1);
          } catch (Exception ex) {
            logger.warn("Aqueous phase seeding init failed, removing phase: " + ex.getMessage());
            system.removePhaseKeepTotalComposition(aquPhaseIndex);
          }
          multiPhaseTest = true;
          doStabilityAnalysis = false;
        }
      }

      rescueStalledThreePhaseEndpoint();

      // For electrolyte systems: ensure only one aqueous phase - the one with most aqueous content
      // Other phases classified as AQUEOUS should be reclassified as OIL with ions removed
      // Also applies to systems with ions even without chemical reactions
      ensureSingleAqueousPhase();

      boolean hasRemovedPhase = false;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        if (system.getBeta(i) < 1.1 * phaseFractionMinimumLimit) {
          // For systems with ions, never remove the only AQUEOUS phase — ions can only
          // exist in aqueous phases. Removing it causes mass balance violations because
          // setXY() forces ion x = 1e-50 in all non-aqueous phases.
          if (hasIons && system.getPhase(i).getType() == PhaseType.AQUEOUS) {
            logger.debug("Protecting aqueous phase {} from removal (beta={}) — ions require aqueous phase", i,
                system.getBeta(i));
            continue;
          }
          system.removePhaseKeepTotalComposition(i);
          doStabilityAnalysis = false;
          hasRemovedPhase = true;
          i--; // indices shift after removal — re-check the (new) phase at i
        }
      }

      // For ionic systems: if the aqueous phase survived with near-zero beta but a
      // non-aqueous phase was introduced by stability analysis (done on the ion-free
      // system) and is also marginal, that third phase is likely spurious. Remove it
      // to let the system settle back to the 2-phase result (gas + aqueous) that the
      // initial TPflash found correctly.
      if (hasIons && !hasRemovedPhase && system.getNumberOfPhases() > 2) {
        int aqIdx = system.hasPhaseType(PhaseType.AQUEOUS) ? system.getPhaseNumberOfPhase("aqueous") : -1;
        if (aqIdx >= 0 && system.getBeta(aqIdx) < 10.0 * phaseFractionMinimumLimit) {
          // Aqueous phase beta is very low — the 3-phase result is not converging
          // properly. Remove the non-aqueous phase with the smallest beta instead.
          int removeIdx = -1;
          double minBeta = Double.MAX_VALUE;
          for (int i = 0; i < system.getNumberOfPhases(); i++) {
            if (i != aqIdx && system.getBeta(i) < minBeta) {
              minBeta = system.getBeta(i);
              removeIdx = i;
            }
          }
          if (removeIdx >= 0) {
            logger.debug("Removing spurious non-aqueous phase {} (beta={}) to preserve ionic aqueous phase", removeIdx,
                minBeta);
            system.removePhaseKeepTotalComposition(removeIdx);
            doStabilityAnalysis = false;
            hasRemovedPhase = true;
            // Re-run beta solver with the 2-phase system to ensure convergence
            setDoubleArrays();
            for (int iter2 = 0; iter2 < 50; iter2++) {
              double d = this.solveBeta();
              if (d < 1e-10) {
                break;
              }
            }
          }
        }
      }

      boolean trivialSolution = false;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = i + 1; j < system.getNumberOfPhases(); j++) {
          if (Math.abs(system.getPhase(i).getDensity() - system.getPhase(j).getDensity()) < 1.1e-5) {
            trivialSolution = true;
            break;
          }
        }
        if (trivialSolution) {
          break;
        }
      }

      if (trivialSolution && !hasRemovedPhase) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          for (int j = i + 1; j < system.getNumberOfPhases(); j++) {
            if (Math.abs(system.getPhase(i).getDensity() - system.getPhase(j).getDensity()) < 1.1e-5) {
              // Determine whether the two near-equal-density phases are
              // genuine numerical
              // duplicates (identical composition) or a legitimate
              // near-critical V/L pair that
              // merely shares a similar density. Only genuine duplicates may
              // have their phase
              // fractions merged; merging a real V/L pair would collapse the
              // flash to a single
              // phase (e.g. TPFlashTest.testRun5).
              double maxCompDiffDup = 0.0;
              for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
                maxCompDiffDup = Math.max(maxCompDiffDup,
                    Math.abs(system.getPhase(i).getComponent(k).getx() - system.getPhase(j).getComponent(k).getx()));
              }
              // Merge the phase fractions (mass-conserving) only when the two
              // phases are genuine
              // composition duplicates AND the system still contains a
              // genuine vapour (GAS) phase.
              // A redundant duplicate that appears alongside a dominant
              // vapour phase (e.g. the
              // trace oil at a dew point in the UMR-PR-UMC trace oil-dropout
              // regression) must have
              // its mass merged back into its twin so the trace liquid is not
              // halved. When NO gas
              // phase is present the multiphase flash has collapsed to a
              // vapour-less trivial
              // solution (e.g. three identical liquid phases in
              // TPFlashTest.testRun5); in that
              // case discard one duplicate and let the bounded rerun
              // re-separate the genuine
              // phases.
              boolean systemHasGasPhase = false;
              for (int p = 0; p < system.getNumberOfPhases(); p++) {
                if (system.getPhase(p).getType() == PhaseType.GAS) {
                  systemHasGasPhase = true;
                  break;
                }
              }
              boolean genuineDuplicate = maxCompDiffDup < 1.0e-4 && systemHasGasPhase;
              // Protect aqueous phase in ionic systems from trivial-solution
              // removal
              if (hasIons && system.getPhase(j).getType() == PhaseType.AQUEOUS) {
                if (genuineDuplicate) {
                  // Remove the non-aqueous duplicate, merging its
                  // mass into the aqueous phase
                  mergeAndRemoveDuplicatePhase(j, i);
                } else {
                  system.removePhaseKeepTotalComposition(i);
                }
              } else if (genuineDuplicate) {
                mergeAndRemoveDuplicatePhase(i, j);
              } else {
                system.removePhaseKeepTotalComposition(j);
              }
              doStabilityAnalysis = false;
              hasRemovedPhase = true;
            }
          }
        }
      }

      // Composition-based trivial solution detection: two phases of the SAME
      // PhaseType with essentially identical mole-fraction vectors are
      // non-converged numerical duplicates. Restricting to same PhaseType
      // avoids removing legitimate near-critical V/L pairs (issue #1980).
      //
      // CPA-family models may produce duplicate phases at material phase fractions
      // (issue #2117). A neutral cubic-EOS aqueous trial can also converge two
      // material liquid fractions to the same hydrocarbon root. Such a three-phase
      // state is a two-phase equilibrium with duplicated phase storage. Chemical and
      // ionic models keep the prior conservative trace-phase restriction.
      String modelName = system.getModelName();
      boolean isCpaModel = modelName != null && modelName.contains("CPA");
      boolean hasTracePhase = false;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        if (system.getBeta(phaseIndex) < 10.0 * phaseFractionMinimumLimit) {
          hasTracePhase = true;
          break;
        }
      }
      boolean neutralAqueousThreePhaseDuplicate = system.getNumberOfPhases() == 3
          && system.hasPhaseType(PhaseType.AQUEOUS) && !system.isChemicalSystem() && !hasIons;
      if (isCpaModel || hasTracePhase || neutralAqueousThreePhaseDuplicate) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          for (int j = i + 1; j < system.getNumberOfPhases(); j++) {
            if (system.getPhase(i).getType() != system.getPhase(j).getType()) {
              continue;
            }
            double maxCompDiff = 0.0;
            for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
              maxCompDiff = Math.max(maxCompDiff,
                  Math.abs(system.getPhase(i).getComponent(k).getx() - system.getPhase(j).getComponent(k).getx()));
            }
            boolean traceDuplicatePair = Math.min(system.getBeta(i), system.getBeta(j)) < 10.0
                * phaseFractionMinimumLimit;
            if (maxCompDiff < 1.0e-6 && (isCpaModel || traceDuplicatePair || neutralAqueousThreePhaseDuplicate)) {
              mergeAndRemoveDuplicatePhase(i, j);
              doStabilityAnalysis = false;
              hasRemovedPhase = true;
              j--; // adjust index after removal
            }
          }
        }
      }
      /*
       * for (int i = 0; i < system.getNumberOfPhases()-1; i++) { if
       * (Math.abs(system.getPhase(i).getDensity()-system.getPhase(i+1).getDensity())< 1e-6 && !hasRemovedPhase) {
       * system.removePhase(i+1); doStabilityAnalysis=false; hasRemovedPhase = true; } }
       */
      if (hasRemovedPhase && !secondTime) {
        secondTime = true;
        stabilityAnalysis3();
        requestBoundedRerun();
      }

      /*
       * if (!secondTime) { secondTime = true; doStabilityAnalysis = false; run(); }
       */
    }

    // A warm-started reactive flash can retain the existing phase topology, leaving multiPhaseTest false. Chemistry
    // still changes the overall species inventory, so couple it to the beta equations even when no new phase was found.
    if (isCoupledReactiveHydrateFlash() && !multiPhaseTest && system.getNumberOfPhases() > 1
        && system.hasPhaseType(PhaseType.AQUEOUS)) {
      aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
      for (int outerIteration = 0; outerIteration < 100; outerIteration++) {
        double chemicalDeviation = solveReactiveAqueousEquilibrium(aqueousPhaseNumber, false);
        setDoubleArrays();
        double phaseEquilibriumResidual = solveBeta();
        double betaResidual = projectReactiveInventoryOntoCurrentPhases();
        if (outerIteration >= 2 && chemicalDeviation <= 1.0e-10 && phaseEquilibriumResidual <= 1.0e-10
            && betaResidual <= 1.0e-10) {
          break;
        }
      }
    }

    // Always leave a reactive multiphase flash on the phase-equilibrium projection of its final reaction-adjusted
    // species inventory. This is required after phase removal or bounded reruns, whose last operation can be chemistry.
    if (isCoupledReactiveHydrateFlash() && system.getNumberOfPhases() > 1) {
      projectReactiveInventoryOntoCurrentPhases();
    }

    if (useIonFreeFlash && !system.isChemicalSystem() && ionFreeOverallZ != null) {
      removeNumericalTracePhasesForIonicFlash();
      if (system.getNumberOfPhases() > 1) {
        setDoubleArrays();
        for (int refinement = 0; refinement < 50; refinement++) {
          if (solveBeta() < 1.0e-10) {
            break;
          }
        }
      }
      if (removeNumericalTracePhasesForIonicFlash() && system.getNumberOfPhases() > 1) {
        setDoubleArrays();
        for (int refinement = 0; refinement < 50; refinement++) {
          if (solveBeta() < 1.0e-10) {
            break;
          }
        }
      }
      restoreIonsToAqueousPhase(ionFreeOverallZ);
    }
  }

  /**
   * Removes a non-persistent phase when a neutral three-phase beta solve stalls above the equilibrium tolerances.
   *
   * <p>
   * The bounded active-set fallback tests each possible phase removal on a clone, accepts only a normalized,
   * material-balanced, fugacity-equal candidate, and selects the lowest-Gibbs candidate. The live system changes only
   * when that candidate also lowers Gibbs energy relative to the stalled three-phase state. Chemical, electrolyte,
   * solid, wax, and already-converged three-phase systems retain their existing paths.
   * </p>
   */
  private void rescueStalledThreePhaseEndpoint() {
    if (!betaSolveStalled || system.getNumberOfPhases() != 3 || system.isChemicalSystem() || system.hasIons()
        || system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck() || isFeasiblePhaseEquilibrium(system)) {
      return;
    }

    system.init(1);
    double stalledGibbsEnergy = system.getGibbsEnergy();
    if (!Double.isFinite(stalledGibbsEnergy)) {
      return;
    }

    int phaseToRemove = -1;
    double lowestGibbsEnergy = Double.POSITIVE_INFINITY;
    for (int phaseIndex = 0; phaseIndex < 3; phaseIndex++) {
      SystemInterface candidate = system.clone();
      candidate.removePhaseKeepTotalComposition(phaseIndex);
      candidate.normalizeBeta();
      candidate.init(1);

      TPmultiflash candidateSolver = new TPmultiflash(candidate, false);
      candidateSolver.setDoubleArrays();
      for (int refinement = 0; refinement < 3 && !isFeasiblePhaseEquilibrium(candidate); refinement++) {
        candidateSolver.solveBeta();
      }
      if (!isFeasiblePhaseEquilibrium(candidate)) {
        continue;
      }

      double candidateGibbsEnergy = candidate.getGibbsEnergy();
      if (Double.isFinite(candidateGibbsEnergy) && candidateGibbsEnergy < lowestGibbsEnergy) {
        lowestGibbsEnergy = candidateGibbsEnergy;
        phaseToRemove = phaseIndex;
      }
    }

    double gibbsTolerance = Math.max(1.0e-6, Math.abs(stalledGibbsEnergy) * 1.0e-8);
    if (phaseToRemove < 0 || lowestGibbsEnergy >= stalledGibbsEnergy - gibbsTolerance) {
      return;
    }

    system.removePhaseKeepTotalComposition(phaseToRemove);
    system.normalizeBeta();
    system.init(1);
    setDoubleArrays();
    for (int refinement = 0; refinement < 3 && !isFeasiblePhaseEquilibrium(system); refinement++) {
      solveBeta();
    }
  }

  private boolean isFeasiblePhaseEquilibrium(SystemInterface candidate) {
    double betaTotal = 0.0;
    int numberOfPhases = candidate.getNumberOfPhases();
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      double beta = candidate.getBeta(phaseIndex);
      if (!Double.isFinite(beta) || beta <= 10.0 * phaseFractionMinimumLimit || beta > 1.0) {
        return false;
      }
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < candidate.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double composition = candidate.getPhase(phaseIndex).getComponent(componentIndex).getx();
        if (!Double.isFinite(composition) || composition < 0.0 || composition > 1.0) {
          return false;
        }
        compositionTotal += composition;
      }
      if (Math.abs(compositionTotal - 1.0) > 1.0e-8) {
        return false;
      }
    }
    if (Math.abs(betaTotal - 1.0) > 1.0e-8) {
      return false;
    }

    int numberOfComponents = candidate.getPhase(0).getNumberOfComponents();
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double feedComposition = candidate.getPhase(0).getComponent(componentIndex).getz();
      double recoveredComposition = 0.0;
      double referenceLogFugacity = Double.NaN;
      for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
        double composition = candidate.getPhase(phaseIndex).getComponent(componentIndex).getx();
        recoveredComposition += candidate.getBeta(phaseIndex) * composition;
        double fugacityCoefficient = candidate.getPhase(phaseIndex).getComponent(componentIndex)
            .getFugacityCoefficient();
        double logFugacity = Math.log(Math.max(composition, Double.MIN_NORMAL)) + Math.log(fugacityCoefficient);
        if (!Double.isFinite(logFugacity)) {
          return false;
        }
        if (phaseIndex == 0) {
          referenceLogFugacity = logFugacity;
        } else if (Math.abs(referenceLogFugacity - logFugacity) > 1.0e-8) {
          return false;
        }
      }
      if (!Double.isFinite(recoveredComposition) || Math.abs(feedComposition - recoveredComposition) > 1.0e-8) {
        return false;
      }
    }
    return true;
  }
}
