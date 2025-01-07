package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PedersenPlusModelSolver class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PedersenPlusModelSolver implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PedersenPlusModelSolver.class);

  int iter = 0;
  Matrix JacAB;
  Matrix JacCD;
  Matrix fvecAB;
  Matrix fvecCD;
  Matrix solAB;
  Matrix solCD;
  Matrix dx;
  int numberOfComponents = 0;
  PlusFractionModel.PedersenPlusModel characterizeClass;
  SystemInterface system = null;

  /**
   * <p>
   * Constructor for PedersenPlusModelSolver.
   * </p>
   */
  public PedersenPlusModelSolver() {}

  /**
   * <p>
   * Constructor for PedersenPlusModelSolver.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a
   *        {@link neqsim.thermo.characterization.PlusFractionModel.PedersenPlusModel} object
   */
  public PedersenPlusModelSolver(SystemInterface system,
      PlusFractionModel.PedersenPlusModel characterizeClass) {
    this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();

    JacAB = new Matrix(2, 2);
    fvecAB = new Matrix(2, 1);
    solAB = new Matrix(2, 1);
    solAB.set(0, 0, characterizeClass.getCoef(0));
    solAB.set(1, 0, characterizeClass.getCoef(1));

    JacCD = new Matrix(2, 2);
    fvecCD = new Matrix(2, 1);
    solCD = new Matrix(2, 1);
    solCD.set(0, 0, characterizeClass.getCoef(2));
    solCD.set(1, 0, characterizeClass.getCoef(3));
  }

  /**
   * <p>
   * Setter for the field <code>fvecAB</code>.
   * </p>
   */
  public void setfvecAB() {
    double zSum = 0.0;
    double mSum = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      double ztemp = Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i));
      double M = characterizeClass.PVTsimMolarMass[i - 6] / 1000.0;
      zSum += ztemp;
      mSum += ztemp * M;
    }
    // double lengthPlus = characterizeClass.getLastPlusFractionNumber() -
    // characterizeClass.getFirstPlusFractionNumber();

    fvecAB.set(0, 0, zSum - characterizeClass.getZPlus());

    fvecAB.set(1, 0, mSum / zSum - characterizeClass.getMPlus());
  }

  /**
   * <p>
   * setJacAB.
   * </p>
   */
  public void setJacAB() {
    JacAB.timesEquals(0.0);

    double tempJ = 0.0;

    for (int j = 0; j < 2; j++) {
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
      }
      JacAB.set(0, j, tempJ);
    }

    for (int j = 0; j < 2; j++) {
      double mTot1 = 0.0;
      double mTot2 = 0.0;
      double zSum2 = 0.0;
      double zSum = 0.0;
      double zSum3 = 0.0;
      for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
          .getLastPlusFractionNumber(); i++) {
        mTot1 += (characterizeClass.PVTsimMolarMass[i - 6] / 1000.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        mTot2 += i * (characterizeClass.PVTsimMolarMass[i - 6] / 1000.0)
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
      }
      JacAB.set(1, j, tempJ);
    }
  }

  /**
   * <p>
   * Setter for the field <code>fvecCD</code>.
   * </p>
   */
  public void setfvecCD() {
    double densTBO =
        characterizeClass.PVTsimDensities[characterizeClass.getFirstPlusFractionNumber() - 6];
    // 0.71;
    // //characterizeClass.getDensLastTBP();
    fvecCD.set(0, 0, (characterizeClass.getCoef(2) + characterizeClass.getCoef(3)
        * Math.log(characterizeClass.getFirstPlusFractionNumber() - 1)) - densTBO);
    double temp = 0.0;
    double temp2 = 0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      temp += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i))
          * characterizeClass.PVTsimMolarMass[i - 6];
      temp2 += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i))
          * characterizeClass.PVTsimMolarMass[i - 6]
          / (characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i));
    }
    fvecCD.set(1, 0, temp / temp2 - characterizeClass.getDensPlus());
  }

  /**
   * <p>
   * setJacCD.
   * </p>
   */
  public void setJacCD() {
    JacCD.timesEquals(0.0);

    JacCD.set(0, 0, 1);
    JacCD.set(0, 1, Math.log(characterizeClass.getFirstPlusFractionNumber() - 1));

    double temp = 0.0;
    double temp2 = 0;
    double temp3 = 0;
    // double deriv = 0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      temp += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i))
          * characterizeClass.PVTsimMolarMass[i - 6];
      temp2 += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i))
          * characterizeClass.PVTsimMolarMass[i - 6]
          / (characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i));
      temp3 -= Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i))
          * characterizeClass.PVTsimMolarMass[i - 6] / Math
              .pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), 2.0);
      /*
       * deriv += 1.0 / ((characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i))
       * characterizeClass.PVTsimMolarMass[i - 6] / Math.pow(characterizeClass.getCoef(2) +
       * characterizeClass.getCoef(3) * Math.log(i), 2.0));
       */
    }

    double dAdC = temp3 * 1;
    double ans = temp / (temp2 * temp2) * dAdC;

    // double dAdD = temp3 * Math.log(1);
    // double ans2 = -temp / (temp2 * temp2);

    JacCD.set(1, 0, ans);
    // JacCD.set(1, 1, ans2);
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
      setfvecAB();
      setJacAB();
      dx = JacAB.solve(fvecAB);
      // logger.info("dx: ");
      // dx.print(10, 3);

      solAB.minusEquals(dx.times((iter) / (iter + 50.0)));
      characterizeClass.setCoefs(solAB.transpose().copy().getArray()[0]);
    } while (((fvecAB.norm2() > 1e-6 || iter < 3) && iter < 200));
    // logger.info("ok char: ");
    // solAB.print(10, 10);

    iter = 0;
    do {
      iter++;
      setfvecCD();
      setJacCD();
      dx = JacCD.solve(fvecCD);
      // logger.info("dxCD: ");
      // dx.print(10, 3);

      solCD.minusEquals(dx.times((iter) / (iter + 5.0)));
      characterizeClass.setCoefs(solCD.transpose().copy().getArray()[0][0], 2);
      characterizeClass.setCoefs(solCD.transpose().copy().getArray()[0][1], 3);
    } while (((fvecCD.norm2() > 1e-6 || iter < 3) && iter < 200));
    // solCD.print(10, 10);
  }
}
