package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SolidFlash1 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidFlash1 extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SolidFlash1.class);

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[] dQdbeta;
  double[][] Qmatrix;
  double[] E;
  double Q = 0;
  int solidsNumber = 0;
  int solidIndex = 0;
  double totalSolidFrac = 0.0;
  int[] FluidPhaseActiveDescriptors; // 1 = active; 0 = inactive

  /**
   * <p>
   * Constructor for SolidFlash1.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SolidFlash1(SystemInterface system) {
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
              .setx(system.getPhase(PhaseType.SOLID).getComponent(i).getFugacityCoefficient()
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
      // logger.info("x tot " + x + " PHASE " + k);
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
          * (1 - Math.log(system.getPhase(0).getComponent(solidIndex).getz() / system
              .getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()));
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        Q -= system.getBeta(j)
            * system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
            / system.getPhase(j).getComponent(solidIndex).getFugacityCoefficient();
      }
    }
    return Q;
  }

  /**
   * <p>
   * calcQbeta.
   * </p>
   */
  public void calcQbeta() {
    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      dQdbeta[k] = 1.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i == solidIndex) {
          dQdbeta[k] -=
              system.getPhase(PhaseType.SOLID).getComponents()[solidIndex].getFugacityCoefficient()
                  / system.getPhase(k).getComponent(solidIndex).getFugacityCoefficient();
        } else {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }
  }

  /**
   * <p>
   * calcGradientAndHesian.
   * </p>
   */
  public void calcGradientAndHesian() {
    Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases()
        - solidsNumber];
    dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];

    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      dQdbeta[k] = 1.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i == solidIndex) {
          dQdbeta[k] -=
              system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
                  / system.getPhase(k).getComponent(solidIndex).getFugacityCoefficient();
        } else {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      Qmatrix[i][i] = 0;
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
          if (k != solidIndex) {
            Qmatrix[i][j] += system.getPhase(0).getComponent(k).getz()
                / (E[k] * E[k] * system.getPhase(j).getComponent(k).getFugacityCoefficient()
                    * system.getPhase(i).getComponent(k).getFugacityCoefficient());
          }
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 0) {
        dQdbeta[i] = 0.0;
        for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
          Qmatrix[i][j] = 0.0;
        }
        Qmatrix[i][i] = 1.0;
      }
    }
    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 1) {
        Qmatrix[i][i] += 10e-15;
      }
    }
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
    double Qold = 0;
    double betaReductionFactor = 1.0;
    double Qnew = 0;
    do {
      // system.init(1);
      calcE();
      Qold = calcQ();
      calcGradientAndHesian();
      oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
      // newBeta = new double[system.getNumberOfPhases() - solidsNumber];
      iter++;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        oldBeta[k] = system.getBeta(k);
      }

      Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
      Matrix betaMatrixOld = new Matrix(oldBeta, 1).transpose();
      Matrix betaMatrixTemp = new Matrix(oldBeta, 1).transpose();
      Matrix dQM = new Matrix(dQdbeta, 1);
      Matrix dQdBM = new Matrix(Qmatrix);

      try {
        ans = dQdBM.solve(dQM.transpose());
      } catch (Exception ex) {
        // ans = dQdBM.solve(dQM.transpose());
      }
      // dQdBM.print(10, 10);
      // logger.info("BetaStep: ");
      // ans.print(30, 30);

      betaReductionFactor = 1.0;
      // logger.info("Oldbeta befor update");
      // betaMatrix.print(10, 10);
      betaMatrixTemp = betaMatrix.minus(ans.times(betaReductionFactor));
      // logger.info("Beta before multiplying reduction Factoer");
      // betaMatrixTemp.print(10, 2);

      double minBetaTem = 1000000;
      int minBetaTemIndex = 0;

      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        if (betaMatrixTemp.get(k, 0) * FluidPhaseActiveDescriptors[k] < minBetaTem) {
          minBetaTem = betaMatrixTemp.get(k, 0);
          minBetaTemIndex = k;
        }
      }

      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        if ((minBetaTem < -1.0E-10)) {
          betaReductionFactor =
              1.0 + betaMatrixTemp.get(minBetaTemIndex, 0) / ans.get(minBetaTemIndex, 0);
        }
      }
      // logger.info("Reduction Factor " + betaReductionFactor);
      betaMatrixTemp = betaMatrix.minus(ans.times(betaReductionFactor));

      // logger.info("Beta after multiplying reduction Factoer");
      // betaMatrixTemp.print(10, 2);

      betaMatrixOld = betaMatrix.copy();
      betaMatrix = betaMatrixTemp.copy();
      boolean deactivatedPhase = false;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        double currBeta = betaMatrix.get(k, 0);
        if (currBeta < phaseFractionMinimumLimit) {
          system.setBeta(k, phaseFractionMinimumLimit);
          // This used to be verified with abs(betamatrix.get(i,0)) < 1.0e-10
          FluidPhaseActiveDescriptors[k] = 0;
          deactivatedPhase = true;
        } else if (currBeta > (1.0 - phaseFractionMinimumLimit)) {
          system.setBeta(k, 1.0 - phaseFractionMinimumLimit);
        } else {
          system.setBeta(k, currBeta);
        }
      }

      Qnew = calcQ();

      // logger.info("Qold = " + Qold);
      // logger.info("Qnew = " + Qnew);
      if (Qnew > Qold + 1.0e-10 && !deactivatedPhase) {
        // logger.info("Qnew > Qold...............................");
        int iter2 = 0;
        do {
          iter2++;
          betaReductionFactor /= 2.0;
          betaMatrixTemp = betaMatrixOld.minus(ans.times(betaReductionFactor));
          betaMatrix = betaMatrixTemp;
          for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            system.setBeta(i, betaMatrix.get(i, 0));
            if (Math.abs(system.getBeta(i)) < 1.0e-10) {
              FluidPhaseActiveDescriptors[i] = 0;
            } else {
              FluidPhaseActiveDescriptors[i] = 1;
            }
          }
          Qnew = calcQ();
        } while (Qnew > Qold + 1.0e-10 && iter2 < 5);
      }
    } while ((Math.abs(ans.norm1()) > 1e-7 && iter < 100) || iter < 2);
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
   *
   * @return a double
   */
  public double calcSolidBeta() {
    double tempVar = system.getPhase(0).getComponents()[solidIndex].getz();
    // double beta = 1.0;
    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 1) {
        tempVar -= system.getBeta(i)
            * system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
            / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
        // beta -= system.getBeta(i);
      }
    }
    if (tempVar > 0 && tempVar < 1.0) {
      system.setBeta(system.getNumberOfPhases() - 1, tempVar);
      // logger.info("Solid PhaseFraction " + tempVar);
    }
    return tempVar;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int iter = 0;

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.setSolidPhaseCheck(false);
    ops.TPflash(false);
    // system.display();
    FluidPhaseActiveDescriptors = new int[system.getNumberOfPhases()];
    for (int i = 0; i < FluidPhaseActiveDescriptors.length; i++) {
      FluidPhaseActiveDescriptors[i] = 1;
    }

    system.setSolidPhaseCheck(true);
    if (checkAndAddSolidPhase() == 0) {
      system.init(1);
      return;
    }
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      system.init(1);
      if (system.getPhase(0).getFugacity(0) > system.getPhase(PhaseType.SOLID).getFugacity(0)) {
        system.setPhaseIndex(0, 3);
      } else {
      }
      system.setBeta(0, 1.0);
      system.setNumberOfPhases(1);
      system.init(1);
      return;
    }
    // if (system.getPhase(0).getNumberOfComponents() <= 2) {
    // solvebeta1();
    // }else{
    double oldBeta = 0.0;
    double beta = 0.0;
    do {
      oldBeta = beta;
      iter++;
      this.solveBeta();
      calcE();
      setXY();
      system.init(1);
      calcQbeta();

      beta = calcSolidBeta();
      /*
       * for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) { if
       * (FluidPhaseActiveDescriptors[i] == 0) { logger.info("dQdB " + i + " " + dQdbeta[i]); if
       * (dQdbeta[i] < 0) { FluidPhaseActiveDescriptors[i] = 1; } } }
       */
    } while (Math.abs((beta - oldBeta) / beta) > 1e-6 && iter < 20);

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 0) {
        system.deleteFluidPhase(i);
      }
    }
    system.init(1);
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
        solidCandidate[k] = 0;
      } else {
        solidCandidate[k] = system.getPhase(0).getComponent(k).getz();
        system.getPhases()[3].getComponent(k).setx(1.0);
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          // double e = system.getBeta(i)*
          // system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3]);
          solidCandidate[k] -= system.getBeta(i)
              * system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3])
              / system.getPhase(i).getComponent(k).getFugacityCoefficient();
        }
      }
    }
    int oldSolidsNumber = solidsNumber;
    solidsNumber = 0;
    totalSolidFrac = 0;
    for (int i = 0; i < solidCandidate.length; i++) {
      if (solidCandidate[i] > 1e-20) {
        system.getPhases()[3].getComponent(i).setx(1.0);
        solidIndex = i;
        solidsNumber++;
        totalSolidFrac += solidCandidate[i];
      } else {
        system.getPhases()[3].getComponent(i).setx(0.0);
      }
    }
    for (int i = oldSolidsNumber; i < solidsNumber; i++) {
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
        solidCandidate -= system.getPhase(PhaseType.SOLID).getComponent(solidIndex)
            .fugcoef(system.getPhase(PhaseType.SOLID))
            / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
      }
      double dsoliddn = (solidCandidate - solidCandidateOld) / dn;
      dn = -0.5 * solidCandidate / dsoliddn;
      logger.info("solid cand " + solidCandidate);
    } while (solidCandidate > 1e-5 && iter < 50);

    return 1.0;
  }
}
