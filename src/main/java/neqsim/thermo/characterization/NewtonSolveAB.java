package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NewtonSolveAB class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class NewtonSolveAB implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NewtonSolveAB.class);

  int iter = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix sol;
  Matrix dx;
  int numberOfComponents = 0;
  TBPCharacterize characterizeClass;
  SystemInterface system = null;

  /**
   * <p>
   * Constructor for NewtonSolveAB.
   * </p>
   */
  public NewtonSolveAB() {}

  /**
   * <p>
   * Constructor for NewtonSolveAB.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param characterizeClass a {@link neqsim.thermo.characterization.TBPCharacterize} object
   */
  public NewtonSolveAB(SystemInterface system, TBPCharacterize characterizeClass) {
    this.system = system;
    this.characterizeClass = characterizeClass;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new Matrix(2, 2);
    fvec = new Matrix(2, 1);
    sol = new Matrix(2, 1);
    sol.set(0, 0, characterizeClass.getPlusCoefs(0));
    sol.set(1, 0, characterizeClass.getPlusCoefs(1));
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    double f0 = -characterizeClass.getZPlus();
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    fvec.set(0, 0, f0);

    f0 = -characterizeClass.getMPlus() * characterizeClass.getZPlus();
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i))
          * (14.0 * i - 4.0);
    }
    fvec.set(1, 0, f0);
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);

    double f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac.set(0, 0, f0 + (0.0));

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += characterizeClass.getPlusCoefs(1)
          * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac.set(1, 0, f0);

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += (14.0 * i - 4.0)
          * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac.set(0, 1, f0);

    f0 = 0.0;
    for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass
        .getLastPlusFractionNumber(); i++) {
      f0 += (14.0 * i - 4.0) * characterizeClass.getPlusCoefs(1)
          * Math.exp(characterizeClass.getPlusCoefs(0) + characterizeClass.getPlusCoefs(1) * (i));
    }
    Jac.set(1, 1, f0 + (0.0));
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
      // System.out.println("dx ");
      // dx.print(10,3);
      sol.minusEquals(dx.times(iter / (iter + 100.0)));
      characterizeClass.setPlusCoefs(sol.transpose().copy().getArray()[0]);
    } while (((dx.norm2() / sol.norm2() < 1e-6 || iter < 15000) && iter < 50000));
    logger.info("ok char: ");
    sol.print(10, 10);
  }
}
