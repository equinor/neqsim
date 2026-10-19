package neqsim.thermo.characterization;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * NewtonSolveABCD class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveABCD implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveABCD.class);

  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();

  int iter = 0;
  double[][] Jac;
  double[] fvec;
  double[] sol;
  double[] dx;
  int numberOfComponents = 0;
  TBPCharacterize characterizeClass;
  double[] calcTPBfraction = null;

  /**
   * Constructor for NewtonSolveABCD.
   */
  public NewtonSolveABCD() {
  }

  /**
   * Constructor for NewtonSolveABCD.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.TBPCharacterize} object
   */
  public NewtonSolveABCD(SystemInterface system, TBPCharacterize characterizeClass) {
    // this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new double[2 * characterizeClass.getLength()][4];
    fvec = new double[2 * characterizeClass.getLength()];
    sol = new double[] {characterizeClass.getCoef(0), characterizeClass.getCoef(1), characterizeClass.getCoef(2),
        characterizeClass.getCoef(3)};
    calcTPBfraction = new double[characterizeClass.getLength()];
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    for (int i = 0; i < characterizeClass.getLength(); i++) {
      fvec[i] = Math.log(characterizeClass.getTBPfractions(i)) - characterizeClass.getCoef(0)
          - characterizeClass.getCoef(1) * (i + characterizeClass.getFirstPlusFractionNumber());
    }

    for (int i = characterizeClass.getLength(); i < 2 * characterizeClass.getLength(); i++) {
      fvec[i] = characterizeClass.getTBPdens(i - characterizeClass.getLength()) - characterizeClass.getCoef(2)
          - characterizeClass.getCoef(3)
              * Math.log((i + characterizeClass.getFirstPlusFractionNumber() - characterizeClass.getLength()));
    }

    for (int i = 0; i < characterizeClass.getLength(); i++) {
      calcTPBfraction[i] = Math.exp(characterizeClass.getCoef(0)
          + characterizeClass.getCoef(1) * (i + characterizeClass.getFirstPlusFractionNumber()));
    }
    characterizeClass.setCalcTBPfractions(calcTPBfraction);
  }

  /**
   * setJac.
   */
  public void setJac() {
    double tempJ = 0.0;

    for (int i = 0; i < characterizeClass.getLength(); i++) {
      for (int j = 0; j < 4; j++) {
        if (j == 0) {
          tempJ = -1.0;
        } else if (j == 1) {
          tempJ = -(i + characterizeClass.getFirstPlusFractionNumber());
        } else {
          tempJ = 0.0;
        }
        Jac[i][j] = tempJ;
      }
    }

    for (int i = characterizeClass.getLength(); i < 2 * characterizeClass.getLength(); i++) {
      for (int j = 0; j < 4; j++) {
        if (j == 2) {
          tempJ = -1.0;
        } else if (j == 3) {
          tempJ = -Math.log(i + characterizeClass.getFirstPlusFractionNumber() - characterizeClass.getLength());
        } else {
          tempJ = 0.0;
        }
        Jac[i][j] = tempJ;
      }
    }
  }

  /**
   * solve.
   */
  public void solve() {
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
      dx = Jac.length == sol.length ? ALGEBRA.solve(Jac, fvec) : ALGEBRA.solveLeastSquares(Jac, fvec);
      if (logger.isDebugEnabled()) {
        logger.debug("Newton step: {}", Arrays.toString(dx));
      }
      sol = ALGEBRA.subtract(sol, ALGEBRA.scale(dx, 0.5));
      characterizeClass.setCoefs(sol.clone());
    } while (((ALGEBRA.euclideanNorm(dx) / ALGEBRA.euclideanNorm(sol) < 1e-6 || iter < 15) && iter < 50));
    logger.info("Characterization coefficients: {}", Arrays.toString(sol));
  }
}
