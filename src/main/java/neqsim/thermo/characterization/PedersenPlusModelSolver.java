package neqsim.thermo.characterization;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * PedersenPlusModelSolver class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PedersenPlusModelSolver implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PedersenPlusModelSolver.class);
  private static final LinearAlgebraOperations ALGEBRA = new JamaLinearAlgebra();

  int iter = 0;
  double[][] JacAB;
  double[][] JacCD;
  double[] fvecAB;
  double[] fvecCD;
  double[] solAB;
  double[] solCD;
  double[] dx;
  int numberOfComponents = 0;
  PlusFractionModel.PedersenPlusModel characterizeClass;
  SystemInterface system = null;

  /**
   * Constructor for PedersenPlusModelSolver.
   */
  public PedersenPlusModelSolver() {
  }

  /**
   * Constructor for PedersenPlusModelSolver.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.PlusFractionModel.PedersenPlusModel} object
   */
  public PedersenPlusModelSolver(SystemInterface system, PlusFractionModel.PedersenPlusModel characterizeClass) {
    this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();

    JacAB = new double[2][2];
    fvecAB = new double[2];
    solAB = new double[] {characterizeClass.getCoef(0), characterizeClass.getCoef(1)};

    JacCD = new double[2][2];
    fvecCD = new double[2];
    solCD = new double[] {characterizeClass.getCoef(2), characterizeClass.getCoef(3)};
  }

  /**
   * Setter for the field <code>fvecAB</code>.
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

    fvecAB[0] = zSum - characterizeClass.getZPlus();

    fvecAB[1] = mSum / zSum - characterizeClass.getMPlus();
  }

  /**
   * setJacAB.
   */
  public void setJacAB() {
    for (double[] row : JacAB) {
      Arrays.fill(row, 0.0);
    }

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
      JacAB[0][j] = tempJ;
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
        zSum2 += Math.pow(Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i), 2.0);
        zSum += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        zSum3 += i * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
      }
      if (j == 0) {
        tempJ = (mTot1 * zSum - mTot1 * zSum) / zSum2;
      } else if (j == 1) {
        tempJ = (mTot2 * zSum - mTot1 * zSum3) / zSum2;
      }
      JacAB[1][j] = tempJ;
    }
  }

  /**
   * Setter for the field <code>fvecCD</code>.
   */
  public void setfvecCD() {
    double densTBO = characterizeClass.PVTsimDensities[characterizeClass.getFirstPlusFractionNumber() - 6];
    // 0.71;
    // //characterizeClass.getDensLastTBP();
    fvecCD[0] = (characterizeClass.getCoef(2)
        + characterizeClass.getCoef(3) * Math.log(characterizeClass.getFirstPlusFractionNumber() - 1)) - densTBO;
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
    fvecCD[1] = temp / temp2 - characterizeClass.getDensPlus();
  }

  /**
   * setJacCD.
   */
  public void setJacCD() {
    for (double[] row : JacCD) {
      Arrays.fill(row, 0.0);
    }

    JacCD[0][0] = 1;
    JacCD[0][1] = Math.log(characterizeClass.getFirstPlusFractionNumber() - 1);

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
          * characterizeClass.PVTsimMolarMass[i - 6]
          / Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), 2.0);
      /*
       * deriv += 1.0 / ((characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i))
       * characterizeClass.PVTsimMolarMass[i - 6] / Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3)
       * * Math.log(i), 2.0));
       */
    }

    double dAdC = temp3 * 1;
    double ans = temp / (temp2 * temp2) * dAdC;

    // double dAdD = temp3 * Math.log(1);
    // double ans2 = -temp / (temp2 * temp2);

    JacCD[1][0] = ans;
    // JacCD.set(1, 1, ans2);
  }

  /**
   * solve.
   */
  public void solve() {
    iter = 0;
    do {
      iter++;
      setfvecAB();
      setJacAB();
      dx = ALGEBRA.solve(JacAB, fvecAB);
      // logger.info("dx: ");
      // dx.print(10, 3);

      solAB = ALGEBRA.subtract(solAB, ALGEBRA.scale(dx, (iter) / (iter + 50.0)));
      characterizeClass.setCoefs(solAB);
    } while (((ALGEBRA.euclideanNorm(fvecAB) > 1e-6 || iter < 3) && iter < 200));
    // logger.info("ok char: ");
    // solAB.print(10, 10);

    iter = 0;
    do {
      iter++;
      setfvecCD();
      setJacCD();
      dx = ALGEBRA.solve(JacCD, fvecCD);
      // logger.info("dxCD: ");
      // dx.print(10, 3);

      solCD = ALGEBRA.subtract(solCD, ALGEBRA.scale(dx, (iter) / (iter + 5.0)));
      characterizeClass.setCoefs(solCD[0], 2);
      characterizeClass.setCoefs(solCD[1], 3);
    } while (((ALGEBRA.euclideanNorm(fvecCD) > 1e-6 || iter < 3) && iter < 200));
    // solCD.print(10, 10);
  }
}
