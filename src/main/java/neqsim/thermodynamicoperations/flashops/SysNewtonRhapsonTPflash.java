package neqsim.thermodynamicoperations.flashops;

import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * sysNewtonRhapsonTPflash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonTPflash implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  int neq = 0;
  int iter = 0;
  int ic02p = -100;
  int ic03p = -100;
  int testcrit = 0;
  int npCrit = 0;
  double beta = 0;
  double ds = 0;
  double dTmax = 1;
  double dPmax = 1;
  double avscp = 0.1;
  double TC1 = 0;
  double TC2 = 0;
  double PC1 = 0;
  double PC2 = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix u;
  Matrix uold;
  Matrix Xgij;
  SystemInterface system;
  int numberOfComponents;
  int speceq = 0;
  Matrix a = new Matrix(4, 4);
  Matrix s = new Matrix(1, 4);
  Matrix xg;
  Matrix dx;
  Matrix xcoef;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonTPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflash(SystemInterface system, int numberOfPhases,
      int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents;
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    u = new Matrix(neq, 1);
    Xgij = new Matrix(neq, 4);
    setu();
    uold = u.copy();
    // System.out.println("Spec : " +speceq);
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec.set(i, 0,
          Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()
              * system.getPhase(0).getComponent(i).getx() * system.getPressure())
              - Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                  * system.getPhase(1).getComponent(i).getx() * system.getPressure()));
    }
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);
    double dij = 0.0;

    double tempJ = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = 1.0 / system.getBeta()
            * (dij / system.getPhase(0).getComponent(i).getx() - 1.0
                + system.getPhase(0).getComponent(i).getdfugdx(j))
            + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhase(1).getComponent(i).getx()
                - 1.0 + system.getPhase(1).getComponent(i).getdfugdx(j));
        Jac.set(i, j, tempJ);
      }
    }
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, system.getBeta() * system.getPhase(0).getComponent(i).getx());
    }
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    double temp = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      temp += u.get(i, 0);
    }
    system.setBeta(temp);

    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(u.get(i, 0) / system.getBeta());
      system.getPhase(1).getComponent(i).setx(
          (system.getPhase(0).getComponent(i).getz() - u.get(i, 0)) / (1.0 - system.getBeta()));
      system.getPhase(0).getComponent(i).setK(
          system.getPhase(0).getComponent(i).getx() / system.getPhase(1).getComponent(i).getx());
      system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
    }

    system.init(3);
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @return a double
   */
  public double solve() {
    try {
      iter++;
      init();
      setfvec();
      setJac();

      // Check condition number and add regularization if needed
      double jacTrace = 0.0;
      for (int i = 0; i < neq; i++) {
        jacTrace += Math.abs(Jac.get(i, i));
      }
      double diagAvg = jacTrace / neq;
      // Add Levenberg-Marquardt regularization for stability
      double lambda = 1e-8 * diagAvg;
      for (int i = 0; i < neq; i++) {
        Jac.set(i, i, Jac.get(i, i) + lambda);
      }

      dx = Jac.solve(fvec);

      // Trust region: limit maximum step size
      double maxDeltaLnK = 2.0; // Maximum ~7x change in K per iteration
      for (int i = 0; i < neq; i++) {
        double delta = dx.get(i, 0);
        if (Math.abs(delta) > maxDeltaLnK) {
          dx.set(i, 0, Math.signum(delta) * maxDeltaLnK);
        }
      }

      // Damped Newton: reduce step size for large updates
      double stepNorm = dx.norm2();
      double dampingFactor = 1.0;
      if (stepNorm > 1.0) {
        dampingFactor = 1.0 / stepNorm;
        dampingFactor = Math.max(dampingFactor, 0.1); // Minimum 10% step
      }

      u.minusEquals(dx.times(dampingFactor));
      return (dx.norm2() * dampingFactor / Math.max(u.norm2(), 1e-10));
    } catch (Exception ex) {
      throw ex;
    }
  }
}
