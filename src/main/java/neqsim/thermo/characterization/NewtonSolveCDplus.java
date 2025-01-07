package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NewtonSolveCDplus class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveCDplus implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveCDplus.class);

  int iter = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix sol;
  Matrix dx;
  int numberOfComponents = 0;
  PlusCharacterize characterizeClass;
  // SystemInterface system = null;

  /**
   * <p>
   * Constructor for NewtonSolveCDplus.
   * </p>
   */
  public NewtonSolveCDplus() {}

  /**
   * <p>
   * Constructor for NewtonSolveCDplus.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.PlusCharacterize} object
   */
  public NewtonSolveCDplus(SystemInterface system, PlusCharacterize characterizeClass) {
    // this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new Matrix(3, 3);
    fvec = new Matrix(3, 1);
    sol = new Matrix(3, 1);
    sol.set(0, 0, characterizeClass.getCoef(0));
    sol.set(1, 0, characterizeClass.getCoef(1));
    sol.set(2, 0, characterizeClass.getCoef(2));
    // sol.set(3,0,characterizeClass.getCoef(3));
    // sol.set(2,0,characterizeClass.getCoef(2));
    // sol.set(3,0,characterizeClass.getCoef(3));
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    double zSum = 0.0;
    double mSum = 0.0;
    double densSum = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      double ztemp = Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i));
      double M = CharacteriseInterface.PVTsimMolarMass[i - 6] / 1000.0;
      double dens = characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i);
      zSum += ztemp;
      mSum += ztemp * M;
      densSum += (ztemp * M / dens);
    }
    densSum = mSum / densSum;
    double lengthPlus = characterizeClass.getLastPlusFractionNumber()
        - characterizeClass.getFirstPlusFractionNumber();
    logger.info("diff " + lengthPlus);
    // Dtot /= lengthPlus;
    // System.out.println("Dtot "+ Dtot);
    logger.info("zsum " + zSum);
    // System.out.println("zplus " + characterizeClass.getZPlus());
    fvec.set(0, 0, zSum - characterizeClass.getZPlus());

    fvec.set(1, 0, mSum / zSum - characterizeClass.getMPlus());

    fvec.set(2, 0, densSum - characterizeClass.getDensPlus());
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);

    double tempJ = 0.0;

    for (int j = 0; j < 3; j++) {
      double nTot = 0.0;
      double nTot2 = 0.0;
      for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
          .getLastPlusFractionNumber(); i++) {
        nTot += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        nTot2 += i * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
      }
      if (j == 0) {
        tempJ = nTot;
      } else if (j == 1) {
        tempJ = nTot2;
      } else {
        tempJ = 0.0;
      }
      Jac.set(0, j, tempJ);
    }

    for (int j = 0; j < 3; j++) {
      double mTot1 = 0.0;
      double mTot2 = 0.0;
      double zSum2 = 0.0;
      double zSum = 0.0;
      double zSum3 = 0.0;
      for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
          .getLastPlusFractionNumber(); i++) {
        mTot1 += (CharacteriseInterface.PVTsimMolarMass[i - 6] / 1000.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        mTot2 += i * (CharacteriseInterface.PVTsimMolarMass[i - 6] / 1000.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        zSum2 += Math.pow(Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i),
            2.0);
        zSum += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        zSum3 += i * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
      }
      if (j == 0) {
        tempJ = (mTot1 * zSum - mTot1 * zSum) / zSum2;
      } else if (j == 1) {
        tempJ = (mTot2 * zSum - mTot1 * zSum3) / zSum2;
      } else {
        tempJ = 0.0;
      }
      Jac.set(1, j, tempJ);
    }

    for (int j = 0; j < 3; j++) {
      double A = 0.0;
      double B = 0.0;
      double Bpow2 = 0.0;
      double Ader1 = 0.0;
      double Bder1 = 0.0;
      double Ader2 = 0.0;
      double Bder2 = 0.0;
      double Bder3 = 0.0;
      double Bder4 = 0.0;
      for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
          .getLastPlusFractionNumber(); i++) {
        double M = CharacteriseInterface.PVTsimMolarMass[i - 6] / 1000.0;
        double dens = characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i);
        A += M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        B += M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens;
        Bpow2 += Math.pow(
            M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens,
            2.0);
        Ader1 = A;
        Bder1 +=
            Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M / dens;
        Ader2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        Bder2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i)
            / dens;
        Bder3 += -Math
            .pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), -2.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M;
        Bder4 += -Math.log(i)
            * Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i),
                -2.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M;
      }
      if (j == 0) {
        tempJ = (Ader1 * B - Bder1 * A) / Bpow2;
      } else if (j == 1) {
        tempJ = (Ader2 * B - Bder2 * A) / Bpow2;
      } else if (j == 2) {
        tempJ = (-Bder3 * A) / Bpow2;
      } else if (j == 3) {
        tempJ = (-Bder4 * A) / Bpow2;
      }
      Jac.set(2, j, tempJ);
    }
  }

  /**
   * <p>
   * solve.
   * </p>
   */
  public void solve() {
    iter = 0;
    do {
      iter++;
      setfvec();
      logger.info("fvec: ");
      fvec.print(10, 17);
      setJac();
      Jac.print(10, 6);
      // System.out.println("rank " + Jac.rank());
      dx = Jac.solve(fvec);
      logger.info("dx: ");
      dx.print(10, 3);
      if (iter < 10) {
        sol.minusEquals(dx.times((iter) / (iter + 5000.0)));
      } else {
        sol.minusEquals(dx.times((iter) / (iter + 50.0)));
      }
      // System.out.println("sol ");
      // sol.print(10,10);
      characterizeClass.setCoefs(sol.transpose().copy().getArray()[0]);
    } while (((fvec.norm2() > 1e-6 || iter < 15) && iter < 3000));
    logger.info("ok char: ");
    sol.print(10, 10);
  }
}
