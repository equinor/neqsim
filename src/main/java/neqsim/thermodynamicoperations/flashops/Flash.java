/*
 * Flash.java
 *
 * Created on 2. oktober 2000, 22:22
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Abstract base class for all flash classes.
 *
 * @author Even Solbraa
 */
public abstract class Flash extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Flash.class);

  SystemInterface system;
  SystemInterface minimumGibbsEnergySystem;

  public double[] minGibsPhaseLogZ;
  public double[] minGibsLogFugCoef;
  int i = 0;
  int j = 0;
  int iterations = 0;
  int maxNumberOfIterations = 50;
  double gibbsEnergy = 0;
  double gibbsEnergyOld = 0;
  double Kold = 0;
  double deviation = 0;
  double g0 = 0;
  double g1 = 0;
  double[] lnOldOldOldK;
  double[] lnOldOldK;
  double[] lnK;
  double[] lnOldK;
  double[] oldoldDeltalnK;
  double[] oldDeltalnK;
  double[] deltalnK;
  double[] tm;
  double tmLimit = -1e-8;
  private static final double LOG_MIN_EXP = Math.log(Double.MIN_NORMAL);
  private static final double LOG_MAX_EXP = Math.log(Double.MAX_VALUE);
  /** Reusable RachfordRice solver for stability analysis. */
  protected RachfordRice stabilityRachfordRice = new RachfordRice();

  protected double safeExp(double value) {
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    if (value < LOG_MIN_EXP) {
      return Double.MIN_NORMAL;
    }
    if (value > LOG_MAX_EXP) {
      return Double.MAX_VALUE;
    }
    return Math.exp(value);
  }

  int lowestGibbsEnergyPhase = 0;
  SysNewtonRhapsonTPflash secondOrderSolver;
  /** Set true to do solid phase check and calculations */
  protected boolean solidCheck = false;
  protected boolean stabilityCheck = false;
  protected boolean findLowestGibbsPhaseIsChecked = false;

  /**
   * <p>
   * findLowestGibbsEnergyPhase.
   * </p>
   *
   * @return a int
   */
  public int findLowestGibbsEnergyPhase() {
    if (!findLowestGibbsPhaseIsChecked) {
      minimumGibbsEnergySystem = system.clone();
      minimumGibbsEnergySystem.init(0);
      if (minimumGibbsEnergySystem.getTotalNumberOfMoles() < 1e-20) {
        minimumGibbsEnergySystem.setTotalNumberOfMoles(1.0);
      }
      minimumGibbsEnergySystem.init(1);
      if ((minimumGibbsEnergySystem.getPhase(0).getGibbsEnergy()
          * (1.0 - Math.signum(minimumGibbsEnergySystem.getPhase(0).getGibbsEnergy())
              * 1e-8)) < minimumGibbsEnergySystem.getPhase(1).getGibbsEnergy()) {
        lowestGibbsEnergyPhase = 0;
      } else {
        lowestGibbsEnergyPhase = 1;
      }
      // logger.info("Lowest Gibbs energy phase determined: Phase {}",
      // lowestGibbsEnergyPhase);

      findLowestGibbsPhaseIsChecked = true;
    }
    return lowestGibbsEnergyPhase;
  }

  /**
   * <p>
   * stabilityAnalysis.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public void stabilityAnalysis() throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double[] logWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[][] Wi = new double[2][system.getPhases()[0].getNumberOfComponents()];

    boolean secondOrderStabilityAnalysis = false;
    double[] oldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhases()[0].getNumberOfComponents()];
    double[][] x = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[] error = new double[2];
    tm = new double[2];
    double[] alpha = null;
    Matrix f = new Matrix(system.getPhases()[0].getNumberOfComponents(), 1);
    Matrix df = null;
    int maxiterations = 100;
    double fNorm = 1.0e10;
    double fNormOld = 0.0;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      d[i] = minGibsPhaseLogZ[i] + minGibsLogFugCoef[i];
    }

    SystemInterface clonedSystem = minimumGibbsEnergySystem;
    double[] sumw = new double[2];
    sumw[1] = 0.0;
    sumw[0] = 0.0;
    for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
      sumw[1] += clonedSystem.getPhase(0).getComponent(i).getz()
          / clonedSystem.getPhase(0).getComponent(i).getK();
      if (clonedSystem.getPhase(0).getComponent(i).getz() > 0) {
        sumw[0] += clonedSystem.getPhase(0).getComponent(i).getK()
            * clonedSystem.getPhase(0).getComponent(i).getz();
      }
    }

    int start = 0;
    int end = 1;
    int mult = 1;

    if (lowestGibbsEnergyPhase == 0) {
      start = end;
      end = 0;
      mult = -1;
    }

    for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
      clonedSystem.getPhase(1).getComponent(i).setx(clonedSystem.getPhase(0).getComponent(i).getz()
          / clonedSystem.getPhase(0).getComponent(i).getK() / sumw[1]);
      clonedSystem.getPhase(0).getComponent(i).setx(clonedSystem.getPhase(0).getComponent(i).getK()
          * clonedSystem.getPhase(0).getComponent(i).getz() / sumw[0]);
    }

    // for (int j = 0; j < clonedSystem.getNumberOfPhases(); j++) {
    for (int j = start; j >= end; j = j + mult) {
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        Wi[j][i] = clonedSystem.getPhase(j).getComponent(i).getx();
        logWi[i] = Math.log(Wi[j][i]);
      }
      iterations = 0;
      fNorm = 1.0e10;
      boolean acceleration = true;
      double olderror = 1.0e10;

      do {
        iterations++;
        error[j] = 0.0;

        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          oldoldoldlogw[i] = oldoldlogw[i];
          oldoldlogw[i] = oldlogw[i];
          oldlogw[i] = logWi[i];

          oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
          oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
        }

        if ((iterations <= maxiterations - 10)
            || !system.isImplementedCompositionDeriativesofFugacity()) {
          try {
            clonedSystem.init(1, j);
          } catch (Exception e) {
            logger.error(e.toString());
            throw e;
          }
          fNormOld = fNorm;
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                + clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient() - d[i]));
          }
          fNorm = f.norm2();
          if (fNorm > fNormOld && iterations > 3 && (iterations - 1) % 7 != 0) {
            if (iterations > 10) {
              break;
            }
          }
          if (iterations % 7 == 0 && fNorm < fNormOld && !secondOrderStabilityAnalysis
              && acceleration) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
            if (error[j] > olderror) {
              acceleration = false;
            }
          } else {
            // succsessive substitution
            for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
              logWi[i] =
                  d[i] - clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient();
              error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          }
        } else {
          if (!secondOrderStabilityAnalysis) {
            alpha = new double[system.getPhases()[0].getNumberOfComponents()];
            df = new Matrix(system.getPhases()[0].getNumberOfComponents(),
                system.getPhases()[0].getNumberOfComponents());
            secondOrderStabilityAnalysis = true;
          }

          clonedSystem.init(3, j);
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                + clonedSystem.getPhase(j).getComponent(i).getLogFugacityCoefficient() - d[i]));
            for (int k = 0; k < clonedSystem.getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.5 : 0.0; // adding 0.5 to diagonal
              df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                  * clonedSystem.getPhase(j).getComponent(i).getdfugdn(k)); // *
                                                                            // clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
            }
          }

          Matrix dx = df.solve(f).times(-1.0);
          for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
            Wi[j][i] = Math.pow((alpha[i] + dx.get(i, 0)) / 2.0, 2.0);
            logWi[i] = Math.log(Wi[j][i]);
            error[j] += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

          // logger.info("err newton " + error[j]);
        }

        // logger.info("norm f " + f.norm1());
        // clonedSystem.display();
        sumw[j] = 0.0;
        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          sumw[j] += Wi[j][i];
        }

        for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
          deltalogWi[i] = logWi[i] - oldlogw[i];
          clonedSystem.getPhase(j).getComponent(i).setx(Wi[j][i] / sumw[j]);
        }
        // logger.info("fnorm " + f.norm1() + " err " + error[j] + " iterations " +
        // iterations
        // + " phase " + j);
        // logger.info("error " + error[j]);
        olderror = error[j];
      } while ((f.norm1() > 1e-3 && error[j] > 1e-3 && iterations < maxiterations)
          || (iterations % 7) == 0 || iterations < 3);
      // (error[j]<oldErr && oldErr<oldOldErr) &&
      // logger.info("err " + error[j]);
      // logger.info("iterations " + iterations);
      // logger.info("f.norm1() " + f.norm1());
      if (iterations >= maxiterations) {
        throw new neqsim.util.exception.TooManyIterationsException("too many iterations", null,
            maxiterations);
      }

      tm[j] = 1.0;
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        tm[j] -= Wi[j][i];
        x[j][i] = clonedSystem.getPhase(j).getComponent(i).getx();
      }

      if (tm[j] < tmLimit && error[j] < 1e-6) {
        break;
      } else {
        tm[j] = 1.0;
      }
    }

    // check for trivial solution
    double diffx = 0.0;
    for (int i = 0; i < clonedSystem.getPhase(0).getNumberOfComponents(); i++) {
      diffx += Math.abs(clonedSystem.getPhase(j).getComponent(i).getx()
          - minimumGibbsEnergySystem.getPhase(0).getComponent(i).getx());
    }
    if (diffx < 1e-10) {
      tm[0] = 0.0;
      tm[1] = 0.0;
    }

    if (((tm[0] < tmLimit) || (tm[1] < tmLimit))
        && !(Double.isNaN(tm[0]) || (Double.isNaN(tm[1])))) {
      for (int i = 0; i < clonedSystem.getPhases()[0].getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() < 1e-100) {
          continue;
        }
        if (tm[0] < tmLimit) {
          system.getPhases()[1].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
          system.getPhases()[0].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
        } else if (tm[1] < tmLimit) {
          system.getPhases()[1].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
          system.getPhases()[0].getComponent(i).setK(clonedSystem.getPhase(0).getComponent(i).getx()
              / clonedSystem.getPhase(1).getComponent(i).getx());
        } else {
          logger.info("error in stability anlysis");
          system.init(0);
        }

        if (Double.isNaN(tm[j])) {
          tm[j] = 0;
        }
      }
    }

    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
  }

  /**
   * <p>
   * stabilityCheck.
   * </p>
   *
   * @return a boolean
   */
  public boolean stabilityCheck() {
    boolean stable = false;
    // logger.info("starting stability analysis....");
    lowestGibbsEnergyPhase = findLowestGibbsEnergyPhase();
    if (system.getPhase(lowestGibbsEnergyPhase).getNumberOfComponents() > 1) {
      try {
        stabilityAnalysis();
      } catch (Exception ex) {
        logger.error("error ", ex);
      }
    }
    if (tm[0] > tmLimit && tm[1] > tmLimit || system.getPhase(0).getNumberOfComponents() == 1) {
      stable = true;
      system.init(0);
      // logger.info("system is stable");
      // logger.info("Stable phase is : " + lowestGibbsEnergyPhase);
      system.setNumberOfPhases(1);

      if (lowestGibbsEnergyPhase == 0) {
        system.setPhaseType(0, PhaseType.GAS);
      } else {
        system.setPhaseType(0, PhaseType.LIQUID);
      }
      system.init(1);
      if (solidCheck && !system.doMultiPhaseCheck()) {
        this.solidPhaseFlash();
      }
    } else {
      try {
        system.setBeta(stabilityRachfordRice.calcBeta(system.getKvector(),
            minimumGibbsEnergySystem.getzvector()));
      } catch (Exception ex) {
        if (!Double.isNaN(stabilityRachfordRice.getBeta()[0])) {
          system.setBeta(stabilityRachfordRice.getBeta()[0]);
        } else {
          system.setBeta(Double.NaN);
        }
        logger.error(ex.getMessage(), ex);
      }
      system.calc_x_y();
      system.init(1);
    }

    return stable;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /**
   * <p>
   * solidPhaseFlash.
   * </p>
   */
  public void solidPhaseFlash() {
    boolean solidPhase = false;
    double frac = 0;
    int solid = 0;
    double[] tempVar = new double[system.getPhases()[0].getNumberOfComponents()];

    if (!system.hasSolidPhase()) {
      system.setNumberOfPhases(system.getNumberOfPhases() + 1);
      system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
    }
    // logger.info("numb " + system.getNumberOfPhases());
    system.init(1);

    for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).doSolidCheck()) {
        tempVar[k] = system.getPhase(0).getComponent(k).getz();
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          tempVar[k] -= system.getBeta(i)
              * system.getPhase(PhaseType.SOLID).getComponent(k).getFugacityCoefficient()
              / system.getPhase(i).getComponent(k).getFugacityCoefficient();
        }

        if (tempVar[k] > 0.0 && tempVar[k] > frac) {
          solidPhase = true;
          solid = k;
          frac = tempVar[k];
          for (int p = 0; p < system.getPhases()[0].getNumberOfComponents(); p++) {
            system.getPhase(PhaseType.SOLID).getComponent(p).setx(1.0e-20);
          }
          system.getPhase(PhaseType.SOLID).getComponents()[solid].setx(1.0);
        }
        // logger.info("tempVar: " + tempVar[k]);
      }
    }

    if (solidPhase) {
      if (frac < system.getPhases()[0].getComponents()[solid].getz() + 1e10) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          // system.getPhases()[i].getComponents()[solid].setx(1.0e-10);
        }
        system.init(1);
        // logger.info("solid phase will form..." + system.getNumberOfPhases());
        // logger.info("freezing component " + solid);
        system.setBeta(system.getNumberOfPhases() - 1, frac);
        system.initBeta();
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(PhaseType.SOLID).getComponent(solid).getNumberOfmoles()
                / system.getNumberOfMoles());
        // double phasetot=0.0;
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // phasetot += system.getPhase(ph).getBeta();
        // }
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // system.setBeta(ph, system.getPhase(ph).getBeta()/phasetot);
        // }
        system.init(1);
        // for(int ph=0;ph<system.getNumberOfPhases();ph++){
        // logger.info("beta " + system.getPhase(ph).getBeta());
        // }
        // TPmultiflash operation = new TPmultiflash(system, true);
        // operation.run();
        SolidFlash solflash = new SolidFlash(system);
        solflash.setSolidComponent(solid);
        solflash.run();
      } else {
        // logger.info("all liquid will freeze out - removing liquid phase..");
        // int phasesNow = system.getNumberOfPhases()-1;
        // system.init(0);
        // system.setNumberOfPhases(phasesNow);
        // system.setNumberOfPhases(system.getNumberOfPhases()-1);
        // system.setPhaseIndex(system.getNumberOfPhases()-1, 3);
        // system.setBeta(1-system.getPhases()[0].getComponents()[solid].getz());
        // system.init(1);
        // system.init_x_y();
        // system.init(1);
        // solidPhaseFlash();
        // solid-vapor flash
      }
    } else {
      // system.setPhaseIndex(system.getNumberOfPhases() - 1,
      // system.getNumberOfPhases() - 1);
      system.setNumberOfPhases(system.getNumberOfPhases() - 1);
      // logger.info("no solid phase will form..");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {}
}
