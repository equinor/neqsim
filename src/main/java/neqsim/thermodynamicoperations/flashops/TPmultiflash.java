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
          double newX = system.getPhase(0).getComponent(i).getz() / Erow[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
          if (!Double.isFinite(newX) || newX <= 0.0) {
            newX = Math.max(system.getPhase(0).getComponent(i).getz(), 1.0e-30);
          }
          system.getPhase(k).getComponent(i).setx(newX);
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
      if (Erow[i] < 1e-100)
        Erow[i] = 1e-100;
      if (Double.isNaN(Erow[i])) {
        logger.error("Erow is NaN for component " + system.getPhase(0).getComponent(i).getName());
        Erow[i] = 1e-100;
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
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
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
      double errOld = 1.0e100;
      boolean useaccsubst = true;
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
              Wi[j][i] = Math.exp(logWi[i]);
            }
          } else {
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
                logWi[i] = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
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
          clonedSystem.get(0).init(3, 1);
          alpha = new double[clonedSystem.get(0).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // ,
          // system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
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
              dx = df.plus(identitytimesConst.scale(0.2)).solve(f).negative();
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
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);
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

  /**
   * <p>
   * stabilityAnalysis3.
   * </p>
   */
  public void stabilityAnalysis3() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
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
      double errOld = 1.0e100;
      boolean useaccsubst = true;
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
              Wi[j][i] = Math.exp(logWi[i]);
            }
          } else {
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
                logWi[i] = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
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
          clonedSystem.get(0).init(3, 1);
          alpha = new double[clonedSystem.get(0).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // ,
          // system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
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
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);
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
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
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
              Wi[j][i] = Math.exp(logWi[i]);
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
              if (!Double.isInfinite(
                  (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i]
                    - (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if ((clonedSystem.get(j)).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = Math.exp(logWi[i]);
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
                  + (clonedSystem.get(j)).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
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
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < 200);
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
        do {
          iterations++;
          // oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
          // system.init(1);
          oldDiff = diff;
          diff = this.solveBeta();
          // diff = Math.abs((system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) /
          // oldBeta);
          // logger.info("diff multiphase " + diff);
          if (iterations % 50 == 0) {
            maxerr *= 100.0;
          }
        } while (diff > maxerr && !removePhase && (diff < oldDiff || iterations < 50)
            && iterations < 200);
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
