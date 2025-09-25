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
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TPmultiflash class.
 * </p>
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
  double[] Erow;
  double Q = 0;
  boolean doStabilityAnalysis = true;
  boolean removePhase = false;
  boolean checkOneRemove = false;
  boolean secondTime = false;

  double[] multTerm;
  double[] multTerm2;

  private static final double[] NEWTON_DAMPING_STEPS = {0.0, 1.0e-10, 1.0e-6};
  private static final double LOG_WI_BOUND = 100.0;


  /**
   * <p>
   * Constructor for TPmultiflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPmultiflash(SystemInterface system) {
    super(system);
    Erow = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * <p>
   * Constructor for TPmultiflash.
   * </p>
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
   * <p>
   * calcMultiPhaseBeta.
   * </p>
   */
  public void calcMultiPhaseBeta() {}

  /**
   * <p>
   * setDoubleArrays.
   * </p>
   */
  public void setDoubleArrays() {
    dQdbeta = new double[system.getNumberOfPhases()][1];
    Qmatrix = new double[system.getNumberOfPhases()][system.getNumberOfPhases()];
  }

  /**
   * <p>
   * setXY.
   * </p>
   */
  public void setXY() {
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
          system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz()
              / Erow[i] / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        }
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
            || system.getPhase(0).getComponent(i).isIsIon()
                && system.getPhase(k).getType() != PhaseType.AQUEOUS) {
          system.getPhase(k).getComponent(i).setx(1e-50);
        }
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
            || system.getPhase(0).getComponent(i).isIsIon()
                && system.getPhase(k).getType() == PhaseType.AQUEOUS) {
          system.getPhase(k).getComponent(i)
              .setx(system.getPhase(k).getComponent(i).getNumberOfmoles()
                  / system.getPhase(k).getNumberOfMolesInPhase());
        }
      }

      system.getPhase(k).normalize();
    }
  }

  /**
   * <p>
   * calcE.
   * </p>
   */
  public void calcE() {
    // E = new double[system.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Erow[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        Erow[i] += system.getPhase(k).getBeta()
            / system.getPhase(k).getComponent(i).getFugacityCoefficient();
      }
    }
  }

  /**
   * <p>
   * calcQ.
   * </p>
   *
   * @return a double
   */
  public double calcQ() {
    /*
     * double betaTotal = 0; for (int k = 0; k < system.getNumberOfPhases(); k++) { betaTotal +=
     * system.getPhase(k).getBeta(); } Q = betaTotal;
     */
    this.calcE();
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { Q -= Math.log(E[i]) *
     * system.getPhase(0).getComponent(i).getz(); }
     */

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      multTerm[i] = system.getPhase(0).getComponent(i).getz() / Erow[i];
      multTerm2[i] = system.getPhase(0).getComponent(i).getz() / (Erow[i] * Erow[i]);
    }

    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      dQdbeta[k][0] = 1.0;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        dQdbeta[k][0] -= multTerm[i] / system.getPhase(k).getComponent(i).getFugacityCoefficient();
      }
    }

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      for (int j = 0; j < system.getNumberOfPhases(); j++) {
        Qmatrix[i][j] = 0.0;
        for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
          Qmatrix[i][j] +=
              multTerm2[k] / (system.getPhase(j).getComponent(k).getFugacityCoefficient()
                  * system.getPhase(i).getComponent(k).getFugacityCoefficient());
        }
        if (i == j) {
          Qmatrix[i][j] += 1.0e-3;
        }
      }
    }
    return Q;
  }

  /**
   * <p>
   * solveBeta.
   * </p>
   *
   * @return a double
   */
  public double solveBeta() {
    SimpleMatrix betaMatrix = new SimpleMatrix(1, system.getNumberOfPhases());
    SimpleMatrix ans = null;
    double err = 1.0;
    int iter = 1;
    do {
      iter++;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        betaMatrix.set(0, k, system.getPhase(k).getBeta());
      }

      calcQ();
      SimpleMatrix dQM = new SimpleMatrix(dQdbeta);
      SimpleMatrix dQdBM = new SimpleMatrix(Qmatrix);
      try {
        ans = dQdBM.solve(dQM).transpose();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        break;
      }
      betaMatrix = betaMatrix.minus(ans.scale(iter / (iter + 3.0)));
      removePhase = false;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        double currBeta = betaMatrix.get(0, k);
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
      err = ans.normF();
    } while ((err > 1e-12 && iter < 50) || iter < 3);
    // logger.info("iterations " + iter);
    return err;
  }

  /** {@inheritDoc} */
  @Override
  public void stabilityAnalysis() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double[] logWi = new double[numComponents];
    double[][] Wi = new double[numComponents][numComponents];

    double[] deltalogWi = new double[numComponents];
    double[] oldDeltalogWi = new double[numComponents];
    double[] oldoldDeltalogWi = new double[numComponents];
    double err = 0.0;
    double[] oldlogw = new double[numComponents];
    double[] oldoldlogw = new double[numComponents];
    double[] oldoldoldlogw = new double[numComponents];
    double[] d = new double[numComponents];
    double[][] x = new double[numComponents][numComponents];
    tm = new double[numComponents];

    double[] alpha = new double[numComponents];
    double[] acceleratedLogWi = new double[numComponents];
    SimpleMatrix fMatrix = new SimpleMatrix(numComponents, 1);
    SimpleMatrix dfMatrix = new SimpleMatrix(numComponents, numComponents);
    DMatrixRMaj newtonRhs = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj newtonStep = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj workJacobian = new DMatrixRMaj(numComponents, numComponents);
    DMatrixRMaj pseudoInverseWorkspace = new DMatrixRMaj(numComponents, numComponents);
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; }
     * double numb = 0; clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0
     * by Even Solbraa 23.01.2013 - chaged back to 1.0e-12 27.04.13 if
     * (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0; } (
     * clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1);
     * } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null)) { sumw[k] += (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for
     * (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null) && system.getPhase(0).getComponent(k).getx() > 1e-100) { (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[0]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if
     * (system.getPhase(0).getComponent(k).getx() > 1e-100) { d[k] =
     * Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents
     * ()[k].getIonicCharge()!=0) d[k]=0; } //logger.info("dk: " + d[k]); }
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
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }
    // boolean checkdForHCmix = false;
    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
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
      boolean useaccsubst = true;
      int maxsucssubiter = 150;
      int maxiter = 200;
      int residualIncreaseCount = 0;
      while (true) {
        double previousErr = err;
        iter++;
        err = 0.0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          boolean accelerated = false;
          if (iter % 7 == 0 && useaccsubst) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              double vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              double vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            if (Math.abs(prod2) > 1e-24) {
              double lambda = prod1 / prod2;
              double denom = 1.0 - lambda;
              if (Double.isFinite(lambda) && Math.abs(denom) > 1e-12) {
                double factor = lambda / denom;
                boolean validAcceleration = true;
                for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                  double candidateLog = logWi[i] + factor * deltalogWi[i];
                  if (Double.isFinite(candidateLog) && Math.abs(candidateLog) < LOG_WI_BOUND) {
                    acceleratedLogWi[i] = sanitizeLogWi(candidateLog);
                  } else {
                    validAcceleration = false;
                    break;
                  }
                }

                if (validAcceleration) {
                  for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    logWi[i] = acceleratedLogWi[i];
                    err += relativeChange(logWi[i], oldlogw[i]);
                    Wi[j][i] = Math.exp(logWi[i]);
                  }
                  accelerated = true;
                }
              }
            }

            if (!accelerated) {
              useaccsubst = false;
            }
          }

          if (!accelerated) {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            clonedSystem.get(0).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(
                  clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                double updatedLogWi = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  updatedLogWi = -LOG_WI_BOUND;
                }
                logWi[i] = sanitizeLogWi(updatedLogWi);
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
              useaccsubst = true;
            }
            if (iter > 2 && err > previousErr) {
              useaccsubst = false;
            }
          }
        } else {
          for (int i = 0; i < numComponents; i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          clonedSystem.get(0).init(3, 1);
          for (int i = 0; i < numComponents; i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          fMatrix.zero();
          dfMatrix.zero();

          for (int i = 0; i < numComponents; i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              fMatrix.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < numComponents; k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                dfMatrix.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
              } else {
                dfMatrix.set(i, k, 0.0);
              }
            }
          }

          boolean solved =
              solveNewtonSystem(dfMatrix, fMatrix, newtonRhs, newtonStep, workJacobian,
                  pseudoInverseWorkspace);
          if (!solved) {
            logger.warn("Stability Newton step defaulted to zero update.");
          }

          for (int i = 0; i < numComponents; i++) {
            double alphaStep = newtonStep.get(i, 0);
            double updatedWi = safeUpdateWi(alpha[i], alphaStep);
            Wi[j][i] = updatedWi;
            
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              double candidateLog = Math.log(updatedWi);
              logWi[i] = sanitizeLogWi(Double.isFinite(candidateLog) ? candidateLog : oldlogw[i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -LOG_WI_BOUND;
            }
            err += relativeChange(logWi[i], oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(Math.exp(logWi[i]));
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
        if (iter > 1 && err > previousErr) {
          residualIncreaseCount++;
        } else {
          residualIncreaseCount = 0;
        }

        if (!((Math.abs(err) > 1e-9 || err > previousErr) && iter < maxiter
            && residualIncreaseCount < 3)) {
          break;
        }
      }
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= Math.exp(logWi[i]);
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
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
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

  private boolean solveNewtonSystem(SimpleMatrix jacobian, SimpleMatrix residual,
      DMatrixRMaj rhsWorkspace, DMatrixRMaj solutionWorkspace, DMatrixRMaj jacobianWorkspace,
      DMatrixRMaj pseudoInverseWorkspace) {
    rhsWorkspace.setTo(residual.getDDRM());
    CommonOps_DDRM.scale(-1.0, rhsWorkspace);

    for (double damping : NEWTON_DAMPING_STEPS) {
      jacobianWorkspace.setTo(jacobian.getDDRM());
      if (damping > 0.0) {
        addDiagonal(jacobianWorkspace, damping);
      }
      try {
        LinearSolverDense<DMatrixRMaj> solver =
            LinearSolverFactory_DDRM.linear(jacobianWorkspace.numRows);
        if (!solver.setA(jacobianWorkspace)) {
          continue;
        }
        solver.solve(rhsWorkspace, solutionWorkspace);
        return true;
      } catch (RuntimeException e) {
        logger.debug("Linear solve failed (damping=" + damping + "): " + e.getMessage(), e);
      }
    }

    try {
      CommonOps_DDRM.pinv(jacobian.getDDRM(), pseudoInverseWorkspace);
      CommonOps_DDRM.mult(pseudoInverseWorkspace, rhsWorkspace, solutionWorkspace);
      logger.warn("Used pseudo-inverse fallback in stability analysis.");
      return true;
    } catch (RuntimeException e) {
      logger.error("Pseudo-inverse fallback failed: " + e.getMessage());
    }

    solutionWorkspace.zero();
    return false;
  }

  private static void addDiagonal(DMatrixRMaj matrix, double value) {
    int n = Math.min(matrix.numRows, matrix.numCols);
    for (int i = 0; i < n; i++) {
      matrix.add(i, i, value);
    }
  }

  private static double relativeChange(double newValue, double oldValue) {
    double scale = Math.max(Math.abs(oldValue), 1.0e-12);
    return Math.abs(newValue - oldValue) / scale;
  }

  private static double sanitizeLogWi(double value) {
    if (!Double.isFinite(value)) {
      return value;
    }
    if (value > LOG_WI_BOUND) {
      return LOG_WI_BOUND;
    }
    if (value < -LOG_WI_BOUND) {
      return -LOG_WI_BOUND;
    }
    return value;
  }

  private static double safeUpdateWi(double alpha, double step) {
    double sanitizedStep = Double.isFinite(step) ? step : 0.0;
    double candidateAlpha = alpha + sanitizedStep;
    if (!Double.isFinite(candidateAlpha) || candidateAlpha <= 0.0) {
      candidateAlpha = Math.max(alpha, 1.0e-12);
    }
    double candidateWi = Math.pow(candidateAlpha / 2.0, 2.0);
    if (!Double.isFinite(candidateWi) || candidateWi <= 0.0) {
      double fallbackAlpha = Math.max(alpha, 1.0e-12);
      candidateWi = Math.pow(fallbackAlpha / 2.0, 2.0);
    }
    return candidateWi;
  }

  /**
   * <p>
   * stabilityAnalysis3.
   * </p>
   */
  public void stabilityAnalysis3() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double[] logWi = new double[numComponents];
    double[][] Wi = new double[numComponents][numComponents];

    double[] deltalogWi = new double[numComponents];
    double[] oldDeltalogWi = new double[numComponents];
    double[] oldoldDeltalogWi = new double[numComponents];
    double[] sumw = new double[numComponents];
    double err = 0.0;
    double[] oldlogw = new double[numComponents];
    double[] oldoldlogw = new double[numComponents];
    double[] oldoldoldlogw = new double[numComponents];
    double[] d = new double[numComponents];
    double[][] x = new double[numComponents][numComponents];
    tm = new double[numComponents];

    double[] alpha = new double[numComponents];
    double[] acceleratedLogWi = new double[numComponents];
    SimpleMatrix fMatrix = new SimpleMatrix(numComponents, 1);
    SimpleMatrix dfMatrix = new SimpleMatrix(numComponents, numComponents);
    DMatrixRMaj newtonRhs = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj newtonStep = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj workJacobian = new DMatrixRMaj(numComponents, numComponents);
    DMatrixRMaj pseudoInverseWorkspace = new DMatrixRMaj(numComponents, numComponents);
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; }
     * double numb = 0; clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0
     * by Even Solbraa 23.01.2013 - chaged back to 1.0e-12 27.04.13 if
     * (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0; } (
     * clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1);
     * } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null)) { sumw[k] += (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for
     * (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null) && system.getPhase(0).getComponent(k).getx() > 1e-100) { (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[0]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if
     * (system.getPhase(0).getComponent(k).getx() > 1e-100) { d[k] =
     * Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents
     * ()[k].getIonicCharge()!=0) d[k]=0; } //logger.info("dk: " + d[k]); }
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
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    for (int j = 0; j < system.getNumberOfComponents(); j++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
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
      boolean useaccsubst = true;
      int maxsucssubiter = 150;
      int maxiter = 200;
      int residualIncreaseCount = 0;
      while (true) {
        double previousErr = err;
        iter++;
        err = 0.0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          boolean accelerated = false;
          if (iter % 7 == 0 && useaccsubst) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              double vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              double vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            if (Math.abs(prod2) > 1e-24) {
              double lambda = prod1 / prod2;
              double denom = 1.0 - lambda;
              if (Double.isFinite(lambda) && Math.abs(denom) > 1e-12) {
                double factor = lambda / denom;
                boolean validAcceleration = true;
                for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                  double candidateLog = logWi[i] + factor * deltalogWi[i];
                  if (Double.isFinite(candidateLog) && Math.abs(candidateLog) < LOG_WI_BOUND) {
                    acceleratedLogWi[i] = sanitizeLogWi(candidateLog);
                  } else {
                    validAcceleration = false;
                    break;
                  }
                }

                if (validAcceleration) {
                  for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    logWi[i] = acceleratedLogWi[i];
                    err += relativeChange(logWi[i], oldlogw[i]);
                    Wi[j][i] = Math.exp(logWi[i]);
                  }
                  accelerated = true;
                }
              }
            }

            if (!accelerated) {
              useaccsubst = false;
            }
          }

          if (!accelerated) {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            clonedSystem.get(0).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(
                  clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                double updatedLogWi = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  updatedLogWi = -LOG_WI_BOUND;
                }
                logWi[i] = sanitizeLogWi(updatedLogWi);
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
              useaccsubst = true;
            }
            if (iter > 2 && err > previousErr) {
              useaccsubst = false;
            }
          }
        } else {
          for (int i = 0; i < numComponents; i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          clonedSystem.get(0).init(3, 1);
          for (int i = 0; i < numComponents; i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          fMatrix.zero();
          dfMatrix.zero();

          for (int i = 0; i < numComponents; i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              fMatrix.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < numComponents; k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                dfMatrix.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
              } else {
                dfMatrix.set(i, k, 0.0);
              }
            }
          }

          boolean solved =
              solveNewtonSystem(dfMatrix, fMatrix, newtonRhs, newtonStep, workJacobian,
                  pseudoInverseWorkspace);
          if (!solved) {
            logger.warn("Stability Newton step defaulted to zero update.");
          }

          for (int i = 0; i < numComponents; i++) {
            double alphaStep = newtonStep.get(i, 0);
            double updatedWi = safeUpdateWi(alpha[i], alphaStep);
            Wi[j][i] = updatedWi;
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              double candidateLog = Math.log(updatedWi);
              logWi[i] = sanitizeLogWi(Double.isFinite(candidateLog) ? candidateLog : oldlogw[i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -LOG_WI_BOUND;
            }
            err += relativeChange(logWi[i], oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += Math.exp(logWi[i]);
        }

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(Math.exp(logWi[i]) / sumw[j]);
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
        if (iter > 1 && err > previousErr) {
          residualIncreaseCount++;
        } else {
          residualIncreaseCount = 0;
        }

        if (!((Math.abs(err) > 1e-9 || err > previousErr) && iter < maxiter
            && residualIncreaseCount < 3)) {
          break;
        }
      }
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= Math.exp(logWi[i]);
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
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
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
   * <p>
   * stabilityAnalysis2.
   * </p>
   */
  public void stabilityAnalysis2() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double[] logWi = new double[numComponents];
    double[][] Wi = new double[numComponents][numComponents];

    double[] deltalogWi = new double[numComponents];
    double[] oldDeltalogWi = new double[numComponents];
    double[] oldoldDeltalogWi = new double[numComponents];
    double[] sumw = new double[numComponents];
    double err = 0.0;
    double[] oldlogw = new double[numComponents];
    double[] oldoldlogw = new double[numComponents];
    double[] oldoldoldlogw = new double[numComponents];
    double[] d = new double[numComponents];
    double[][] x = new double[numComponents][numComponents];
    tm = new double[numComponents];

    double[] alpha = new double[numComponents];
    double[] acceleratedLogWi = new double[numComponents];
    SimpleMatrix fMatrix = new SimpleMatrix(numComponents, 1);
    SimpleMatrix dfMatrix = new SimpleMatrix(numComponents, numComponents);
    DMatrixRMaj newtonRhs = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj newtonStep = new DMatrixRMaj(numComponents, 1);
    DMatrixRMaj workJacobian = new DMatrixRMaj(numComponents, numComponents);
    DMatrixRMaj pseudoInverseWorkspace = new DMatrixRMaj(numComponents, numComponents);
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
        if (!((clonedSystem.get(k)) == null)
            && system.getPhase(0).getComponent(k).getx() > 1e-100) {
          (clonedSystem.get(k)).getPhase(1).getComponent(i)
              .setx((clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[0]);
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
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
        continue;
      }
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      int residualIncreaseCount = 0;
      while (true) {
        double previousErr = err;
        iter++;
        err = 0.0;

        if (iter <= 150 || !system.isImplementedCompositionDeriativesofFugacity()) {
          boolean accelerated = false;
          if (iter % 7 == 0) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              double vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              double vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            if (Math.abs(prod2) > 1e-24) {
              double lambda = prod1 / prod2;
              double denom = 1.0 - lambda;
              if (Double.isFinite(lambda) && Math.abs(denom) > 1e-12) {
                double factor = lambda / denom;
                boolean validAcceleration = true;
                for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                  double candidateLog = logWi[i] + factor * deltalogWi[i];
                  if (Double.isFinite(candidateLog) && Math.abs(candidateLog) < LOG_WI_BOUND) {
                    acceleratedLogWi[i] = sanitizeLogWi(candidateLog);
                  } else {
                    validAcceleration = false;
                    break;
                  }
                }

                if (validAcceleration) {
                  for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    logWi[i] = acceleratedLogWi[i];
                    err += relativeChange(logWi[i], oldlogw[i]);
                    Wi[j][i] = Math.exp(logWi[i]);
                  }
                  accelerated = true;
                }
              }
            }
          }

          if (!accelerated) {
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
              if (!Double.isInfinite(
                  (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                double updatedLogWi = d[i]
                    - (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if ((clonedSystem.get(j)).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  updatedLogWi = -LOG_WI_BOUND;
                }
                logWi[i] = sanitizeLogWi(updatedLogWi);
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
            }
          }
        } else {
          SystemInterface cloned = clonedSystem.get(j);
          for (int i = 0; i < numComponents; i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          cloned.init(3, 1);
          for (int i = 0; i < numComponents; i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          fMatrix.zero();
          dfMatrix.zero();

          for (int i = 0; i < numComponents; i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              fMatrix.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + cloned.getPhases()[1].getComponent(i).getLogFugacityCoefficient() - d[i]));
            }
            for (int k = 0; k < numComponents; k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                dfMatrix.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * cloned.getPhases()[1].getComponent(i).getdfugdn(k));
              } else {
                dfMatrix.set(i, k, 0.0);
              }
            }
          }

          boolean solved =
              solveNewtonSystem(dfMatrix, fMatrix, newtonRhs, newtonStep, workJacobian,
                  pseudoInverseWorkspace);
          if (!solved) {
            logger.warn("Stability Newton step defaulted to zero update.");
          }

          for (int i = 0; i < numComponents; i++) {
            double alphaStep = newtonStep.get(i, 0);
            double updatedWi = safeUpdateWi(alpha[i], alphaStep);
            Wi[j][i] = updatedWi;
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              double candidateLog = Math.log(updatedWi);
              logWi[i] = sanitizeLogWi(Double.isFinite(candidateLog) ? candidateLog : oldlogw[i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -LOG_WI_BOUND;
            }
            err += relativeChange(logWi[i], oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += Math.exp(logWi[i]);
        }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
              (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(Math.exp(logWi[i]) / sumw[j]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(1e-50);
            }
          }
        if (iter > 1 && err > previousErr) {
          residualIncreaseCount++;
        } else {
          residualIncreaseCount = 0;
        }

        if (!((Math.abs(err) > 1e-9 || err > previousErr) && iter < 200
            && residualIncreaseCount < 3)) {
          break;
        }
      }
      if (iter > 198) {
        // System.out.println("too many iterations....." + err + " temperature "
        // + system.getTemperature("C") + " C " + system.getPressure("bara") + " bara");
        new neqsim.util.exception.TooManyIterationsException(this, "stabilityAnalysis2", 200);
      }
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= Math.exp(logWi[i]);
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
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
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

  /** {@inheritDoc} */
  @Override
  public void run() {
    int aqueousPhaseNumber = 0;
    // logger.info("Starting multiphase-flash....");

    // system.setNumberOfPhases(system.getNumberOfPhases()+1);
    if (doStabilityAnalysis) {
      stabilityAnalysis();
    }
    // system.orderByDensity();
    doStabilityAnalysis = true;
    // system.init(1);
    // system.display();
    aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(system.getPhaseNumberOfPhase("aqueous"),
          0);
      system.getChemicalReactionOperations().solveChemEq(system.getPhaseNumberOfPhase("aqueous"),
          1);
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
        if (system.isChemicalSystem()) {
          if (system.getPhaseNumberOfPhase("aqueous") != aqueousPhaseNumber) {
            aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
            system.getChemicalReactionOperations()
                .solveChemEq(system.getPhaseNumberOfPhase("aqueous"), 0);
            // system.getChemicalReactionOperations().solveChemEq(system.getPhaseNumberOfPhase("aqueous"),
            // 1);
          }

          for (int phaseNum = system.getPhaseNumberOfPhase("aqueous"); phaseNum < system
              .getPhaseNumberOfPhase("aqueous") + 1; phaseNum++) {
            chemdev = 0.0;
            double[] xchem = new double[system.getPhase(phaseNum).getNumberOfComponents()];

            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              xchem[i] = system.getPhase(phaseNum).getComponent(i).getx();
            }

            system.init(1);
            system.getChemicalReactionOperations()
                .solveChemEq(system.getPhaseNumberOfPhase("aqueous"), 1);

            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              chemdev += Math.abs(xchem[i] - system.getPhase(phaseNum).getComponent(i).getx());
            }
            // logger.info("chemdev: " + chemdev);
          }
        }
        setDoubleArrays();
        iterations = 0;
        int diffIncreaseCount = 0;
        do {
          iterations++;
          // oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
          // system.init(1);
          double previousDiff = diff;
          oldDiff = previousDiff;
          diff = this.solveBeta();
          // diff = Math.abs((system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) /
          // oldBeta);
          // logger.info("diff multiphase " + diff);
          if (iterations % 50 == 0) {
            maxerr = Math.min(maxerr * 10.0, 1.0e-6);
          }
          if (diff > previousDiff) {
            diffIncreaseCount++;
          } else {
            diffIncreaseCount = 0;
          }
        } while (diff > maxerr && !removePhase && (diff < oldDiff || iterations < 50)
            && iterations < 200 && diffIncreaseCount < 3);
        // this.solveBeta(true);
        if (iterations >= 199) {
          logger.error("error in multiphase flash..did not solve in 200 iterations");
          logger.error("diff " + diff + " temperaure " + system.getTemperature("C") + " pressure "
              + system.getPressure("bara"));
          diff = this.solveBeta();
        }
      } while ((Math.abs(chemdev) > 1e-10 && iterOut < 100)
          || (iterOut < 3 && system.isChemicalSystem()));

      boolean hasRemovedPhase = false;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        if (system.getBeta(i) < 1.1 * phaseFractionMinimumLimit) {
          system.removePhaseKeepTotalComposition(i);
          doStabilityAnalysis = false;
          hasRemovedPhase = true;
        }
      }

      boolean trivialSolution = false;
      for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
        for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
          if (Math.abs(
              system.getPhase(i).getDensity() - system.getPhase(i + 1).getDensity()) < 1.1e-5) {
            trivialSolution = true;
          }
        }
      }

      if (trivialSolution && !hasRemovedPhase) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          if (Math.abs(
              system.getPhase(i).getDensity() - system.getPhase(i + 1).getDensity()) < 1.1e-5) {
            system.removePhaseKeepTotalComposition(i + 1);
            doStabilityAnalysis = false;
            hasRemovedPhase = true;
          }
        }
      }

      /*
       * for (int i = 0; i < system.getNumberOfPhases()-1; i++) { if
       * (Math.abs(system.getPhase(i).getDensity()-system.getPhase(i+1).getDensity())< 1e-6 &&
       * !hasRemovedPhase) { system.removePhase(i+1); doStabilityAnalysis=false; hasRemovedPhase =
       * true; } }
       */
      if (hasRemovedPhase && !secondTime) {
        secondTime = true;
        stabilityAnalysis3();
        run();
      }
      /*
       * if (!secondTime) { secondTime = true; doStabilityAnalysis = false; run(); }
       */
    }
  }
}
