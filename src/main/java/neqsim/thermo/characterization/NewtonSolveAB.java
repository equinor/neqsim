package neqsim.thermo.characterization;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * NewtonSolveAB class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveAB implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveAB.class);

  private static final LinearAlgebraOperations ALGEBRA = new JamaLinearAlgebra();

  int iter = 0;
  double[][] Jac;
  double[] fvec;
  double[] sol;
  double[] dx;
  int numberOfComponents = 0;
  TBPCharacterize characterizeClass;
  SystemInterface system = null;

  /**
   * Constructor for NewtonSolveAB.
   */
  public NewtonSolveAB() {
  }

  /**
   * Constructor for NewtonSolveAB.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.TBPCharacterize} object
   */
  public NewtonSolveAB(SystemInterface system, TBPCharacterize characterizeClass) {
    this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new double[2][2];
    fvec = new double[2];
    sol = new double[] {characterizeClass.getPlusCoefs(0), characterizeClass.getPlusCoefs(1)};
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    double f0 = -characterizeClass.getZPlus();
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    fvec[0] = f0;

    f0 = -characterizeClass.getMPlus() * characterizeClass.getZPlus();
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i)) * (14.0 * i - 4.0);
    }
    fvec[1] = f0;
  }

  /**
   * setJac.
   */
  public void setJac() {
    double f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac[0][0] = f0 + (0.0);

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += characterizeClass.getPlusCoefs(1)
          * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac[1][0] = f0;

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += (14.0 * i - 4.0) * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac[0][1] = f0;

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += (14.0 * i - 4.0) * characterizeClass.getPlusCoefs(1)
          * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac[1][1] = f0 + (0.0);
  }

  /**
   * solve.
   */
  public void solve() {
    do {
      iter++;
      setfvec();
      logger.debug("Residual: {}", Arrays.toString(fvec));
      setJac();
      logger.debug("Jacobian: {}", Arrays.deepToString(Jac));
      dx = ALGEBRA.solve(Jac, fvec);
      logger.debug("Newton step: {}", Arrays.toString(dx));
      sol = ALGEBRA.subtract(sol, ALGEBRA.scale(dx, iter / (iter + 100.0)));
      characterizeClass.setPlusCoefs(sol.clone());
    } while (((ALGEBRA.euclideanNorm(dx) / ALGEBRA.euclideanNorm(sol) < 1e-6 || iter < 15000) && iter < 50000));
    logger.info("Characterization coefficients: {}", Arrays.toString(sol));
  }
}
