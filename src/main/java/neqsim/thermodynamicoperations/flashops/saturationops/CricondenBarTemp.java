package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * cricondenBarTemp class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondenBarTemp implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
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
  double[][] Jac;
  double[] fvec;
  double[] u;
  double[] uold;
  double[][] Xgij;
  SystemInterface system;
  int numberOfComponents;
  int speceq = 0;
  double[][] a = new double[4][4];
  double[][] s = new double[1][4];
  double[][] xg;
  double[] dx;
  double[][] xcoef;

  /**
   * Constructor for cricondenBarTemp.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public CricondenBarTemp(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents;
    Jac = new double[neq][neq];
    fvec = new double[neq];
    u = new double[neq];
    Xgij = new double[neq][4];
    setu();
    uold = u.clone();
    // System.out.println("Spec : " +speceq);
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec[i] = Math
          .log(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * system.getPhases()[0].getComponent(i).getx() * system.getPressure())
          - Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
              * system.getPhases()[1].getComponent(i).getx() * system.getPressure());
    }
  }

  /**
   * setJac.
   */
  public void setJac() {
    double dij = 0.0;

    double tempJ = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = 1.0 / system.getBeta()
            * (dij / system.getPhases()[0].getComponent(i).getx() - 1.0
                + system.getPhases()[0].getComponent(i).getdfugdx(j))
            + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhases()[1].getComponent(i).getx() - 1.0
                + system.getPhases()[1].getComponent(i).getdfugdx(j));
        Jac[i][j] = tempJ;
      }
    }
  }

  /**
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u[i] = system.getBeta() * system.getPhases()[0].getComponent(i).getx();
    }
  }

  /**
   * init.
   */
  public void init() {
    double temp = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      temp += u[i];
    }
    system.setBeta(temp);

    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhases()[0].getComponent(i).setx(u[i] / system.getBeta());
      system.getPhases()[1].getComponent(i)
          .setx((system.getPhases()[0].getComponent(i).getz() - u[i]) / (1.0 - system.getBeta()));
      system.getPhases()[0].getComponent(i)
          .setK(system.getPhases()[0].getComponent(i).getx() / system.getPhases()[1].getComponent(i).getx());
      system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
    }

    system.init(3);
  }

  /**
   * solve.
   *
   * @return a double
   */
  public double solve() {
    iter++;
    init();
    setfvec();
    setJac();
    dx = ALGEBRA.solve(Jac, fvec);
    u = ALGEBRA.subtract(u, dx);
    return ALGEBRA.euclideanNorm(dx) / ALGEBRA.euclideanNorm(u);
  }
}
