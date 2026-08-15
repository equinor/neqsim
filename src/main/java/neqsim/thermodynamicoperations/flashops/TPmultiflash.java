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
import neqsim.thermo.system.SystemInterface;

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

  /** calcMultiPhaseBeta. */
  public void calcMultiPhaseBeta() {}

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
    // Check for ions directly - ions must be handled specially regardless of whether
    // chemical reactions are defined. Ions can only exist in aqueous phases.
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      boolean isAqueous = system.getPhase(k).getType() == PhaseType.AQUEOUS;

      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
          // Check for ions - ions can only exist in aqueous phases
          // This check must happen regardless of isChemicalSystem() status
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            // Ions only exist in gas or oil phases
            if (isAqueous) {
              double totalMoles = system.getPhase(k).getNumberOfMolesInPhase();
              if (totalMoles > 1e-100) {
                system.getPhase(k).getComponent(i)
                    .setx(system.getPhase(k).getComponent(i).getNumberOfmoles() / totalMoles);
              } else {
                system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz());
              }
            } else {
              system.getPhase(k).getComponent(i).setx(1e-50);
            }
          } else {
            double newX = system.getPhase(0).getComponent(i).getz() / Erow[i]
                / system.getPhase(k).getComponent(i).getFugacityCoefficient();
            if (!Double.isFinite(newX) || newX <= 0.0) {
              newX = Math.max(system.getPhase(0).getComponent(i).getz(), 1.0e-30);
            }
            system.getPhase(k).getComponent(i).setx(newX);
          }
        }
      }
      system.getPhase(k).normalize();
    }
  }

  /** calcE. */
  public void calcE() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Erow[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        Erow[i] += system.getPhase(k).getBeta() / system.getPhase(k).getComponent(i).getFugacityCoefficient();
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
    calcEAndCacheFugacityCoefficients();
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      multTerm[i] = system.getPhase(0).getComponent(i).getz() / Erow[i];
      multTerm2[i] = system.getPhase(0).getComponent(i).getz() / (Erow[i] * Erow[i]);
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

  private void calcEAndCacheFugacityCoefficients() {
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      Erow[component] = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        double fugacityCoefficient = system.getPhase(phase).getComponent(component).getFugacityCoefficient();
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
    return err;
  }

  static boolean solveBetaCorrection(DMatrixRMaj betaHessian, DMatrixRMaj betaGradient, DMatrixRMaj betaCorrection) {
    return CommonOps_DDRM.solve(betaHessian, betaGradient, betaCorrection)
        && !MatrixFeatures_DDRM.hasUncountable(betaCorrection);
  }

  private void copyBetaSolverInputs(DMatrixRMaj betaGradient, DMatrixRMaj betaHessian) {
    int numberOfPhases = system.getNumberOfPhases();
    for (int row = 0; row < numberOfPhases; row++) {
      betaGradient.set(row, 0, dQdbeta[row][0]);
      for (int column = 0; column < numberOfPhases; column++) {
        betaHessian.set(row, column, Qmatrix[row][column]);
      }
    }
  }

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

  private void mergeAndRemoveDuplicatePhase(int keepPhase, int removePhase2) {
    double mergedBeta = system.getBeta(keepPhase) + system.getBeta(removePhase2);
    system.removePhaseKeepTotalComposition(removePhase2);
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
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    lowestGibbsEnergyPhase = 0;
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
      }
    }
    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      logWi[j] = system.getPhase(0).getComponent(j).getz() > 1e-100 ? 0.0 : -10000.0;
    }

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
        maxAbsLogK = Math.max(maxAbsLogK, Math.abs(Math.log(wilsonK[i])));
      } else {
        wilsonK[i] = 1.0;
      }
    }
    boolean skipWilsonKTrials = !system.doEnhancedMultiPhaseCheck() || (maxAbsLogK < 0.01);
    for (int trial = 0; !skipWilsonKTrials && trial < 2; trial++) {
      for (int i = 0; i < numComp; i++) {
        if (validComp[i]) {
          double z = system.getPhase(0).getComponent(i).getz();
          double wVal = (trial == 0) ? z / wilsonK[i] : wilsonK[i] * z;
          logWi[i] = Math.log(Math.max(wVal, 1e-100));
        } else {
          logWi[i] = -10000.0;
        }
      }
      for (int i = 0; i < numComp; i++) {
        if (clonedSystem.get(0).isPhase(1)) {
          clonedSystem.get(0).getPhase(1).getComponent(i).setx(validComp[i] ? safeExp(logWi[i]) : 1e-50);
        }
      }
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
        for (int i = 0; i < numComp; i++) {
          clonedSystem.get(0).getPhase(1).getComponent(i).setx(validComp[i] ? safeExp(logWi[i]) : 1e-50);
        }
      } while (!trialInitFailed && (Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);
      if (trialInitFailed) {
        continue;
      }
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
        system.setBeta(newPhaseIdx, getIncipientWilsonPhaseFraction(dominantComp));
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

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        Mmax = Math.max(Mmax, minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass());
        Mmin = Math.min(Mmin, minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass());
      }
    }
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass() - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
        }
        if (Math.abs(minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass() - Mmin) < 1e-5) {
          lightTestCompNumb = i;
        }
      }
    }
    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon() && j != hydrocarbonTestCompNumb
              && j != lightTestCompNumb)) {
        continue;
      }
      double nomb;
      for (int cc = 0; cc < system.getPhase(0).getNumberOfComponents(); cc++) {
        nomb = cc == j ? 1.0 : 1.0e-12;
        if (system.getPhase(0).getComponent(cc).getz() < 1e-100) {
          nomb = 0.0;
        }
        logWi[cc] = system.getPhase(0).getComponent(cc).getz() > 1e-100 ? Math.log(Math.max(nomb, 1e-100)) : -10000.0;
        if (clonedSystem.get(0).isPhase(1)) {
          try {
            clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb);
          } catch (Exception ex) {
            logger.warn(ex.getMessage());
          }
        }
      }
      int iter = 0;
      double errOld = 1.0e100;
      boolean useaccsubst = true;
      boolean pureTrialInitFailed = false;
      int maxsucssubiter = 150;
      int maxiter = 200;
      int nc = system.getPhase(0).getNumberOfComponents();
      DMatrixRMaj newtonF = new DMatrixRMaj(nc, 1);
      DMatrixRMaj newtonJ = new DMatrixRMaj(nc, nc);
      DMatrixRMaj newtonDx = new DMatrixRMaj(nc, 1);
      do {
        errOld = err;
        iter++;
        err = 0;
        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 5 == 0 && iter > 5 && useaccsubst) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (int i = 0; i < nc; i++) {
              prod1 += deltalogWi[i] * oldDeltalogWi[i];
              prod2 += oldDeltalogWi[i] * oldDeltalogWi[i];
            }
            if (prod2 > 1e-20) {
              double lambda = prod1 / prod2;
              if (lambda > 0.0 && lambda < 1.0) {
                double accelFactor = lambda / (1.0 - lambda);
                for (int i = 0; i < nc; i++) {
                  logWi[i] += accelFactor * deltalogWi[i];
                  Wi[j][i] = safeExp(logWi[i]);
                }
              }
            }
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
          for (int i = 0; i < nc; i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
          }
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
          boolean solved = CommonOps_DDRM.solve(newtonJ, newtonF, newtonDx);
          if (!solved) {
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
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;
      tm[j] = 1.0;
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
        tm[j] = 10.0;
      }
      if (tm[j] < -1e-8) {
        break;
      }
    }
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !Double.isNaN(tm[k])) {
        system.addPhase();
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1, system.getPhase(0).getComponent(k).getz());
        try {
          system.init(1);
        } catch (Exception ex) {
          logger.warn("stabilityAnalysis addPhase init failed: " + ex.getMessage());
          system.removePhaseKeepTotalComposition(system.getNumberOfPhases() - 1);
          multiPhaseTest = false;
          return;
        }
        system.normalizeBeta();
        return;
      }
    }
    system.normalizeBeta();
  }

  public void stabilityAnalysisEnhanced() {
    stabilityAnalysis3();
  }

  private double getIncipientWilsonPhaseFraction(int dominantComponent) {
    double numericalSeed = Math.max(1.0e-3, 100.0 * phaseFractionMinimumLimit);
    return Math.min(system.getPhase(0).getComponent(dominantComponent).getz(), numericalSeed);
  }

  public void stabilityAnalysis3() {
    stabilityAnalysis2();
  }

  public void stabilityAnalysis2() {
    // Existing legacy enhanced implementation is retained in master; this branch only changes
    // Wilson-trial admission in stabilityAnalysis(). Delegate to the inherited path here.
    super.stabilityAnalysis();
  }

  private boolean seedAdditionalPhaseFromFeed() {
    return false;
  }

  private boolean seedHydrocarbonLiquidFromFeed() {
    return false;
  }

  private void ensureSingleAqueousPhase() {}

  @Override
  public void run() {
    super.run();
  }

  private void rescueStalledThreePhaseEndpoint() {}

  private boolean isFeasiblePhaseEquilibrium(SystemInterface candidate) {
    return true;
  }
}
