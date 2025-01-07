package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NewtonSolveABCD class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveABCD implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveABCD.class);

  int iter = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix sol;
  Matrix dx;
  int numberOfComponents = 0;
  TBPCharacterize characterizeClass;
  double[] calcTPBfraction = null;

  /**
   * <p>
   * Constructor for NewtonSolveABCD.
   * </p>
   */
  public NewtonSolveABCD() {}

  /**
   * <p>
   * Constructor for NewtonSolveABCD.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.TBPCharacterize} object
   */
  public NewtonSolveABCD(SystemInterface system, TBPCharacterize characterizeClass) {
    // this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new Matrix(2 * characterizeClass.getLength(), 4);
    fvec = new Matrix(2 * characterizeClass.getLength(), 1);
    sol = new Matrix(4, 1);
    sol.set(0, 0, characterizeClass.getCoef(0));
    sol.set(1, 0, characterizeClass.getCoef(1));
    sol.set(2, 0, characterizeClass.getCoef(2));
    sol.set(3, 0, characterizeClass.getCoef(3));
    calcTPBfraction = new double[characterizeClass.getLength()];
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < characterizeClass.getLength(); i++) {
      fvec.set(i, 0, Math.log(characterizeClass.getTBPfractions(i)) - characterizeClass.getCoef(0)
          - characterizeClass.getCoef(1) * (i + characterizeClass.getFirstPlusFractionNumber()));
    }

    for (int i = characterizeClass.getLength(); i < 2 * characterizeClass.getLength(); i++) {
      fvec.set(i, 0, characterizeClass.getTBPdens(i - characterizeClass.getLength())
          - characterizeClass.getCoef(2) - characterizeClass.getCoef(3) * Math.log((i
              + characterizeClass.getFirstPlusFractionNumber() - characterizeClass.getLength())));
    }

    for (int i = 0; i < characterizeClass.getLength(); i++) {
      calcTPBfraction[i] = Math.exp(characterizeClass.getCoef(0)
          + characterizeClass.getCoef(1) * (i + characterizeClass.getFirstPlusFractionNumber()));
    }
    characterizeClass.setCalcTBPfractions(calcTPBfraction);
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);

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
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = characterizeClass.getLength(); i < 2 * characterizeClass.getLength(); i++) {
      for (int j = 0; j < 4; j++) {
        if (j == 2) {
          tempJ = -1.0;
        } else if (j == 3) {
          tempJ = -Math.log(
              i + characterizeClass.getFirstPlusFractionNumber() - characterizeClass.getLength());
        } else {
          tempJ = 0.0;
        }
        Jac.set(i, j, tempJ);
      }
    }
  }

  /**
   * <p>
   * solve.
   * </p>
   */
  public void solve() {
    do {
      iter++;
      setfvec();
      fvec.print(10, 2);
      setJac();
      Jac.print(10, 2);
      dx = Jac.solve(fvec);
      logger.info("dx: ");
      dx.print(10, 3);
      sol.minusEquals(dx.times(0.5));
      characterizeClass.setCoefs(sol.transpose().copy().getArray()[0]);
    } while (((dx.norm2() / sol.norm2() < 1e-6 || iter < 15) && iter < 50));
    logger.info("ok char: ");
    sol.print(10, 10);
  }
}
