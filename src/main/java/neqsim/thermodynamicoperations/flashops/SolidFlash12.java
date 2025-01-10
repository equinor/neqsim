/*
 * SolidFlash12.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SolidFlash12 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidFlash12 extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SolidFlash12.class);

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[] dQdbeta;
  double[][] Qmatrix;
  double[] E;
  double Q = 0;
  int solidsNumber = 0;
  int solidIndex = 0;

  /**
   * <p>
   * Constructor for SolidFlash12.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SolidFlash12(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * calcMultiPhaseBeta.
   * </p>
   */
  public void calcMultiPhaseBeta() {}

  /**
   * <p>
   * setXY.
   * </p>
   */
  public void setXY() {
    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i != solidIndex) {
          system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        } else {
          system.getPhase(k).getComponent(i)
              .setx(system.getPhases()[3].getComponent(i).getFugacityCoefficient()
                  / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        }
      }
    }
  }

  /**
   * <p>
   * checkX.
   * </p>
   */
  public void checkX() {
    for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
      double x = 0.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        x += system.getPhase(k).getComponent(i).getx();
      }
      logger.info("x tot " + x + " PHASE " + k);
      if (x < 1.0 - 1e-6) {
        // logger.info("removing phase " + k);
        system.setBeta(system.getNumberOfPhases() - 2,
            system.getBeta(system.getNumberOfPhases() - 1));
        system.setBeta(0, 1.0 - system.getBeta(system.getNumberOfPhases() - 1));
        system.setNumberOfPhases(system.getNumberOfPhases() - 1);
        system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
        system.init(1);
        calcE();
        setXY();
        return;
      }
    }
  }

  /**
   * <p>
   * calcE.
   * </p>
   */
  public void calcE() {
    E = new double[system.getPhases()[0].getNumberOfComponents()];

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      E[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        E[i] += system.getBeta(k) / system.getPhase(k).getComponent(i).getFugacityCoefficient();
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
    Q = 0;
    double betaTotal = 0;
    dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];
    Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases()
        - solidsNumber];

    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      betaTotal += system.getBeta(k);
    }

    Q = betaTotal;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      if (i != solidIndex) {
        Q -= Math.log(E[i]) * system.getPhase(0).getComponent(i).getz();
      }
    }

    for (int i = 0; i < solidsNumber; i++) {
      Q += system.getPhase(0).getComponent(solidIndex).getz()
          * (1 - Math.log(system.getPhase(0).getComponent(solidIndex).getz()
              / system.getPhases()[3].getComponent(solidIndex).getFugacityCoefficient()));
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        Q -= system.getBeta(j)
            * system.getPhases()[3].getComponent(solidIndex).getFugacityCoefficient()
            / system.getPhase(j).getComponent(solidIndex).getFugacityCoefficient();
      }
    }

    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      dQdbeta[k] = 1.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i == solidIndex) {
          dQdbeta[k] -= system.getPhases()[3].getComponents()[solidIndex].getFugacityCoefficient()
              / system.getPhase(k).getComponent(solidIndex).getFugacityCoefficient();
        } else {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        Qmatrix[i][j] = 0.0;
        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
          if (k != solidIndex) {
            Qmatrix[i][j] += system.getPhase(0).getComponent(k).getz()
                / (E[k] * E[k] * system.getPhase(j).getComponent(k).getFugacityCoefficient()
                    * system.getPhase(i).getComponent(k).getFugacityCoefficient());
          }
        }
      }
    }
    return Q;
  }

  /**
   * <p>
   * solveBeta.
   * </p>
   */
  public void solveBeta() {
    double[] oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
    // double newBeta[] = new double[system.getNumberOfPhases() - solidsNumber];
    int iter = 0;
    Matrix ans = new Matrix(system.getNumberOfPhases() - solidsNumber, 1);
    do {
      system.init(1);
      calcE();
      // setXY();
      calcQ();

      oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
      // newBeta = new double[system.getNumberOfPhases() - solidsNumber];
      iter++;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        oldBeta[k] = system.getBeta(k);
      }

      Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
      // Matrix betaMatrixTemp = new Matrix(oldBeta, 1).transpose();
      Matrix dQM = new Matrix(dQdbeta, 1);
      Matrix dQdBM = new Matrix(Qmatrix);

      try {
        ans = dQdBM.solve(dQM.transpose());
      } catch (Exception ex) {
        // ans = dQdBM.solve(dQM.transpose());
      }
      dQM.print(10, 10);
      dQdBM.print(10, 10);
      ans.print(30, 30);
      // double betaReductionFactor = 1.0;
      // betaMatrixTemp = betaMatrix.minus(ans.times(betaReductionFactor));
      // betaMatrixTemp.print(10, 2);

      // double minBetaTem = 1000000;
      // int minBetaIndex = 0;

      /*
       * for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) { if
       * (betaMatrixTemp.get(i, 0) < minBetaTem) { minBetaTem = betaMatrixTemp.get(i, 0);
       * minBetaIndex = i; } }
       */

      /*
       * for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) { if ((minBetaTem <
       * -1.0E-10)) { betaReductionFactor = 1 - betaMatrixTemp.get(minBetaIndex, 0) /
       * ans.get(minBetaIndex, 0); } }
       */

      betaMatrix.minusEquals(ans.times((iter + 1.0) / (100.0 + iter)));
      // betaMatrix.print(10, 2);

      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        system.setBeta(k, betaMatrix.get(k, 0));
        if (betaMatrix.get(k, 0) < 0) {
          system.setBeta(k, 1e-9);
        }
        if (betaMatrix.get(k, 0) > 1) {
          system.setBeta(k, 1 - 1e-9);
        }
      }
      calcE();
      setXY();
      calcSolidBeta();
    } while ((ans.norm2() > 1e-8 && iter < 100) || iter < 2);

    system.init(1);
    setXY();
  }

  /**
   * <p>
   * checkGibbs.
   * </p>
   */
  public void checkGibbs() {
    double gibbs1 = 0;
    double gibbs2 = 0;
    for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
      system.setPhaseType(i, PhaseType.LIQUID);
      system.init(1);
      gibbs1 = system.getPhase(i).getGibbsEnergy();
      system.setPhaseType(i, PhaseType.GAS);
      system.init(1);
      gibbs2 = system.getPhase(i).getGibbsEnergy();
      if (gibbs1 < gibbs2) {
        system.setPhaseType(i, PhaseType.LIQUID);
      } else {
        system.setPhaseType(i, PhaseType.GAS);
      }
      system.init(1);
    }
  }

  /**
   * <p>
   * calcSolidBeta.
   * </p>
   */
  public void calcSolidBeta() {
    double tempVar = system.getPhase(0).getComponents()[solidIndex].getz();
    // double beta = 1.0;
    for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
      tempVar -=
          system.getBeta(i) * system.getPhase(3).getComponent(solidIndex).getFugacityCoefficient()
              / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
      // beta -= system.getBeta(i);
    }
    if (tempVar > 0 && tempVar < 1.0) {
      system.setBeta(system.getNumberOfPhases() - 1, tempVar);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int iter = 0;

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.setSolidPhaseCheck(false);
    ops.TPflash();
    system.setSolidPhaseCheck(true);
    if (checkAndAddSolidPhase() == 0) {
      return;
    }

    if (system.getPhase(0).getNumberOfComponents() <= 2) {
      solvebeta1();
    }
    do {
      iter++;
      this.solveBeta();
      // checkX();
    } while (iter < 1);
  }

  /**
   * <p>
   * checkAndAddSolidPhase.
   * </p>
   *
   * @return a int
   */
  public int checkAndAddSolidPhase() {
    double[] solidCandidate = new double[system.getPhases()[0].getNumberOfComponents()];

    for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getTemperature() > system.getPhase(0).getComponent(k)
          .getTriplePointTemperature()) {
        solidCandidate[k] = -10;
      } else {
        solidCandidate[k] = system.getPhase(0).getComponent(k).getz();
        system.getPhases()[3].getComponent(k).setx(1.0);

        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          solidCandidate[k] -= system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3])
              / system.getPhase(i).getComponent(k).getFugacityCoefficient();
        }
      }
    }

    for (int i = 0; i < solidCandidate.length; i++) {
      if (solidCandidate[i] > 0.0) {
        system.getPhases()[3].getComponent(i).setx(1.0);
        solidIndex = i;
        solidsNumber++;
      } else {
        system.getPhases()[3].getComponent(i).setx(0.0);
      }
    }

    for (int i = 0; i < solidsNumber; i++) {
      system.setNumberOfPhases(system.getNumberOfPhases() + 1);
      system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
      system.setBeta(system.getNumberOfPhases() - 1, solidCandidate[solidIndex]);
      // system.setBeta(system.getNumberOfPhases() - 2,
      // system.getBeta(system.getNumberOfPhases() - 2) - solidCandidate[solidIndex]);
    }
    return solidsNumber;
  }

  /**
   * <p>
   * solvebeta1.
   * </p>
   *
   * @return a double
   */
  public double solvebeta1() {
    // double numberOfMolesFreeze =
    // system.getPhase(0).getComponent(solidIndex).getNumberOfmoles();
    double solidCandidate = 0;
    int iter = 0;
    double dn = -0.01;
    double solidCandidateOld = 0;
    do {
      solidCandidateOld = solidCandidate;
      system.addComponent(system.getPhase(0).getComponent(solidIndex).getComponentName(), dn);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      // system.init(0);
      system.setSolidPhaseCheck(false);
      ops.TPflash();
      // system.setSolidPhaseCheck(true);

      iter++;
      solidCandidate = system.getPhase(0).getComponents()[solidIndex].getz();
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        solidCandidate -=
            system.getPhases()[3].getComponent(solidIndex).fugcoef(system.getPhases()[3])
                / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
      }
      double dsoliddn = (solidCandidate - solidCandidateOld) / dn;
      dn = -0.5 * solidCandidate / dsoliddn;
      logger.info("solid cand " + solidCandidate);
    } while (solidCandidate > 1e-5 && iter < 50);

    return 1.0;
  }
}
