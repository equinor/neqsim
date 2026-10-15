package neqsim.thermo.characterization;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * NewtonSolveCDplus class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveCDplus implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveCDplus.class);

  private static final LinearAlgebraOperations ALGEBRA = new JamaLinearAlgebra();

  int iter = 0;
  double[][] Jac;
  double[] fvec;
  double[] sol;
  double[] dx;
  int numberOfComponents = 0;
  PlusCharacterize characterizeClass;
  // SystemInterface system = null;

  /**
   * Constructor for NewtonSolveCDplus.
   */
  public NewtonSolveCDplus() {
  }

  /**
   * Constructor for NewtonSolveCDplus.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.PlusCharacterize} object
   */
  public NewtonSolveCDplus(SystemInterface system, PlusCharacterize characterizeClass) {
    // this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new double[3][3];
    fvec = new double[3];
    sol = new double[] {characterizeClass.getCoef(0), characterizeClass.getCoef(1), characterizeClass.getCoef(2)};
  }

  /**
   * Setter for the field <code>fvec</code>.
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
    double lengthPlus = characterizeClass.getLastPlusFractionNumber() - characterizeClass.getFirstPlusFractionNumber();
    logger.debug("Plus-fraction range: {}", lengthPlus);
    // Dtot /= lengthPlus;
    // System.out.println("Dtot "+ Dtot);
    logger.debug("Plus-fraction sum: {}", zSum);
    // System.out.println("zplus " + characterizeClass.getZPlus());
    fvec[0] = zSum - characterizeClass.getZPlus();

    fvec[1] = mSum / zSum - characterizeClass.getMPlus();

    fvec[2] = densSum - characterizeClass.getDensPlus();
  }

  /**
   * setJac.
   */
  public void setJac() {
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
      Jac[0][j] = tempJ;
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
        zSum2 += Math.pow(Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i), 2.0);
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
      Jac[1][j] = tempJ;
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
        Bpow2 += Math.pow(M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens, 2.0);
        Ader1 = A;
        Bder1 += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M / dens;
        Ader2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
        Bder2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens;
        Bder3 += -Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), -2.0)
            * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M;
        Bder4 += -Math.log(i)
            * Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), -2.0)
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
      Jac[2][j] = tempJ;
    }
  }

  /**
   * solve.
   */
  public void solve() {
    iter = 0;
    do {
      iter++;
      setfvec();
      if (logger.isDebugEnabled()) {
        logger.debug("Residual: {}", Arrays.toString(fvec));
      }
      setJac();
      if (logger.isDebugEnabled()) {
        logger.debug("Jacobian: {}", Arrays.deepToString(Jac));
      }
      dx = ALGEBRA.solve(Jac, fvec);
      if (logger.isDebugEnabled()) {
        logger.debug("Newton step: {}", Arrays.toString(dx));
      }
      if (iter < 10) {
        sol = ALGEBRA.subtract(sol, ALGEBRA.scale(dx, (iter) / (iter + 5000.0)));
      } else {
        sol = ALGEBRA.subtract(sol, ALGEBRA.scale(dx, (iter) / (iter + 50.0)));
      }
      characterizeClass.setCoefs(sol.clone());
    } while (((ALGEBRA.euclideanNorm(fvec) > 1e-6 || iter < 15) && iter < 3000));
    logger.info("Characterization coefficients: {}", Arrays.toString(sol));
  }
}
