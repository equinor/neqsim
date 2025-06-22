package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SolidFlash class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidFlash extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SolidFlash.class);

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[] dQdbeta;
  double[][] Qmatrix;
  double[] E;
  double Q = 0;
  int solidComponent = 0;
  boolean hasRemovedPhase = false;
  boolean secondTime = false;

  /**
   * <p>
   * Constructor for SolidFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SolidFlash(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * Setter for the field <code>solidComponent</code>.
   * </p>
   *
   * @param i a int
   */
  public void setSolidComponent(int i) {
    solidComponent = i;
  }

  /**
   * <p>
   * Constructor for SolidFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SolidFlash(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
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
    for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz() / E[i]
            / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        /*
         * if (system.getPhase(k).getComponent(i).getx() > 1.0) {
         * system.getPhase(k).getComponent(i).setx(1.0 - 1e-30); } if
         * (system.getPhase(k).getComponent(i).getx() < 0.0) {
         * system.getPhase(k).getComponent(i).setx(1.0e-30); }
         */
      }
      system.getPhase(k).normalize();
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
      for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
        E[i] += system.getBeta(k) / system.getPhase(k).getComponent(i).getFugacityCoefficient();
      }
      // logger.info("Ei " +E[i]);
      // if(
    }
    // E[solidComponent] +=
    // system.getBeta(system.getNumberOfPhases()-1) /
    // system.getPhase(PhaseType.SOLID).getComponent(solidComponent).getFugacityCoefficient();
    E[solidComponent] = system.getPhase(0).getComponent(solidComponent).getz()
        / system.getPhase(PhaseType.SOLID).getComponents()[solidComponent].getFugacityCoefficient();
    // logger.info("Ei " +E[solidComponent]);
    // logger.info("fug "
    // +system.getPhase(PhaseType.SOLID).getComponent(solidComponent).getFugacityCoefficient());
    // logger.info("zi " +system.getPhase(0).getComponent(solidComponent).getz());
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
    dQdbeta = new double[system.getNumberOfPhases() - 1];
    Qmatrix = new double[system.getNumberOfPhases() - 1][system.getNumberOfPhases() - 1];

    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      betaTotal += system.getBeta(k);
    }

    Q = betaTotal;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      Q -= Math.log(E[i]) * system.getPhase(0).getComponent(i).getz();
    }

    for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
      dQdbeta[k] = 1.0 - system.getPhase(PhaseType.SOLID).getComponents()[solidComponent]
          .getFugacityCoefficient()
          / system.getPhase(k).getComponent(solidComponent).getFugacityCoefficient();
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i != solidComponent) {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() * 1.0 / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
      Qmatrix[i][i] = 1.0e-9;
      for (int j = 0; j < system.getNumberOfPhases() - 1; j++) {
        if (i != j) {
          Qmatrix[i][j] = 0;
        }
        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
          if (k != solidComponent) {
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
   *
   * @param ideal a boolean
   */
  public void solveBeta(boolean ideal) {
    double[] oldBeta = new double[system.getNumberOfPhases() - 1];
    // double newBeta[] = new double[system.getNumberOfPhases() - 1];
    int iter = 0;
    Matrix ans = new Matrix(system.getNumberOfPhases() - 1, 1);
    do {
      if (!ideal) {
        system.init(1);
      }
      calcE();
      calcQ();

      oldBeta = new double[system.getNumberOfPhases() - 1];
      // newBeta = new double[system.getNumberOfPhases() - 1];
      iter++;
      for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
        oldBeta[k] = system.getBeta(k);
      }

      Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
      Matrix dQM = new Matrix(dQdbeta, 1);
      Matrix dQdBM = new Matrix(Qmatrix);
      // dQM.print(10,2);
      // dQdBM.print(10,2);
      try {
        ans = dQdBM.solve(dQM.transpose());
      } catch (Exception ex) {
        // ans = dQdBM.solve(dQM.transpose());
      }

      betaMatrix.minusEquals(ans.times((iter + 1.0) / (10.0 + iter)));
      for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
        double currBeta = betaMatrix.get(k, 0);
        if (currBeta < phaseFractionMinimumLimit) {
          system.setBeta(k, phaseFractionMinimumLimit);
        } else if (currBeta > 1) {
          system.setBeta(k, 1 - phaseFractionMinimumLimit);
        } else {
          system.setBeta(k, currBeta);
        }
      }

      // calcSolidBeta();
      calcSolidBeta();

      if (!hasRemovedPhase) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          if (Math.abs(system.getBeta(i)) < 1.01e-9) {
            system.removePhaseKeepTotalComposition(i);
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
        run();
      }
      // system.init(1);
      // calcE();
      // setXY();
      // system.init(1);
      // //checkGibbs();
      // calcE();
      // setXY();
    } while ((ans.norm2() > 1e-8 && iter < 100) || iter < 2);
    // checkX();
    // calcE();
    // setXY();
    system.init(1);
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
    double tempVar = system.getPhase(0).getComponents()[solidComponent].getz();
    // double beta = 1.0;
    for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
      tempVar -= system.getBeta(i)
          * system.getPhase(PhaseType.SOLID).getComponent(solidComponent).getFugacityCoefficient()
          / system.getPhase(i).getComponent(solidComponent).getFugacityCoefficient();
      // beta -= system.getBeta(i);
    }
    if (tempVar > 0 && tempVar < 1.0) {
      system.setBeta(system.getNumberOfPhases() - 1, tempVar);
    }
    // logger.info("beta " + tempVar);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // logger.info("starting ");
    system.setNumberOfPhases(system.getNumberOfPhases());
    double oldBeta = 0.0;
    int iter = 0;
    system.init(1);
    this.solveBeta(true);

    do {
      iter++;
      oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
      setXY();
      system.init(1);

      if (system.getNumberOfPhases() > 1) {
        this.solveBeta(true);
      } else {
        // logger.info("setting beta ");
        system.setBeta(0, 1.0 - 1e-10);
        system.reset_x_y();
        system.init(1);
      }

      // system.display();
      checkX();
      // logger.info("iter " + iter);
    } while ((Math.abs(system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) > 1e-3
        && !(iter > 20)) || iter < 4);
    // checkX();
    // logger.info("iter " + iter);
    // system.setNumberOfPhases(system.getNumberOfPhases()+1);
  }
}
