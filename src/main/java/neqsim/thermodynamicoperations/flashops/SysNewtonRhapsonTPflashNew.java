package neqsim.thermodynamicoperations.flashops;

import Jama.Matrix;
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * sysNewtonRhapsonTPflashNew class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonTPflashNew implements java.io.Serializable {
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
  Matrix xcoef;
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonTPflashNew.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflashNew(SystemInterface system, int numberOfPhases,
      int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents + 1;
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    u = new Matrix(neq, 1);
    Xgij = new Matrix(neq, 4);
    setu();
    uold = u.copy();
    // System.out.println("Spec : " +speceq);
    solver = new NewtonRhapson();
    solver.setOrder(3);
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec.set(i, 0,
          u.get(i, 0) + Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
              / system.getPhases()[0].getComponent(i).getFugacityCoefficient()));
    }

    double fsum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      fsum = fsum + system.getPhases()[1].getComponent(i).getx()
          - system.getPhases()[0].getComponent(i).getx();
    }
    fvec.set(numberOfComponents, 0, fsum);
    // fvec.print(0,20);
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);
    double dij = 0.0;
    double[] dxidlnk = new double[numberOfComponents];
    double[] dyidlnk = new double[numberOfComponents];

    double[] dxidbeta = new double[numberOfComponents];
    double[] dyidbeta = new double[numberOfComponents];

    double tempJ = 0.0;
    double sumdyidbeta = 0;
    double sumdxidbeta = 0;
    int nofc = numberOfComponents;
    for (int i = 0; i < numberOfComponents; i++) {
      dxidlnk[i] = -system.getBeta() * system.getPhases()[0].getComponent(i).getx()
          * system.getPhases()[1].getComponent(i).getx()
          / system.getPhases()[0].getComponent(i).getz();
      dyidlnk[i] = system.getPhases()[1].getComponent(i).getx()
          + system.getPhases()[0].getComponent(i).getK() * dxidlnk[i];

      dyidbeta[i] = (system.getPhases()[0].getComponent(i).getK()
          * system.getPhases()[0].getComponent(i).getz()
          * (1 - system.getPhases()[0].getComponent(i).getK()))
          / Math.pow(1 - system.getBeta()
              + system.getBeta() * system.getPhases()[0].getComponent(i).getK(), 2);
      dxidbeta[i] = (system.getPhases()[0].getComponent(i).getz()
          * (1 - system.getPhases()[0].getComponent(i).getK()))
          / Math.pow(1 - system.getBeta()
              + system.getBeta() * system.getPhases()[0].getComponent(i).getK(), 2);

      sumdyidbeta += dyidbeta[i];
      sumdxidbeta += dxidbeta[i];
    }

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij + system.getPhases()[1].getComponent(i).getdfugdx(j) * dyidlnk[j]
            - system.getPhases()[0].getComponent(i).getdfugdx(j) * dxidlnk[j];
        Jac.set(i, j, tempJ);
      }
      Jac.set(i, nofc, system.getPhases()[1].getComponent(i).getdfugdx(i) * dyidbeta[i]
          - system.getPhases()[0].getComponent(i).getdfugdx(i) * dxidbeta[i]);
      Jac.set(nofc, i, dyidlnk[i] - dxidlnk[i]);
    }

    Jac.set(nofc, nofc, sumdyidbeta - sumdxidbeta);
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, Math.log(system.getPhases()[0].getComponent(i).getK()));
    }

    u.set(numberOfComponents, 0, system.getBeta());
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhases()[0].getComponent(i).setK(Math.exp(u.get(i, 0)));
      system.getPhases()[1].getComponent(i).setK(Math.exp(u.get(i, 0)));
    }
    system.setBeta(u.get(numberOfComponents, 0));
    system.calc_x_y();
    system.init(3);
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @param np a int
   */
  public void solve(int np) {
    Matrix dx;
    iter = 0;
    do {
      iter++;
      init();
      setfvec();
      setJac();
      dx = Jac.solve(fvec);
      u.minusEquals(dx);
      // System.out.println("feilen: "+dx.norm2());
    } while (dx.norm2() / u.norm2() > 1.e-10); // && Double.isNaN(dx.norm2()));
    // System.out.println("iter: "+iter);
    init();
  }
}
