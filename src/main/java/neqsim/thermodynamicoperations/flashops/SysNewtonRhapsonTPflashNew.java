package neqsim.thermodynamicoperations.flashops;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;
import neqsim.thermo.system.SystemInterface;

/**
 * Legacy Newton-Raphson TP-flash solver retained for compatibility.
 *
 * <p>
 * The production TP-flash path uses {@link SysNewtonRhapsonTPflash}. New callers should use
 * {@link neqsim.thermodynamicoperations.ThermodynamicOperations#TPflash()} for phase stability and equilibrium
 * orchestration. The two low-level Newton solvers use different variables and are not interchangeable iteration states.
 * Existing direct users must validate their fluid cases when migrating. Internal Newton matrices use EJML; serialized
 * instances written with the former Jama matrix fields are incompatible.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @deprecated Use the public thermodynamic-operations TP-flash entry point. Retained through the repository's required
 * deprecation period; see issue #4212.
 */
@Deprecated
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
  DMatrixRMaj Jac;
  DMatrixRMaj fvec;
  DMatrixRMaj u;
  DMatrixRMaj uold;
  DMatrixRMaj Xgij;
  SystemInterface system;
  int numberOfComponents;
  int speceq = 0;
  DMatrixRMaj a = new DMatrixRMaj(4, 4);
  DMatrixRMaj s = new DMatrixRMaj(1, 4);
  DMatrixRMaj xg;
  DMatrixRMaj xcoef;
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;

  /**
   * Constructor for sysNewtonRhapsonTPflashNew.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflashNew(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents + 1;
    Jac = new DMatrixRMaj(neq, neq);
    fvec = new DMatrixRMaj(neq, 1);
    u = new DMatrixRMaj(neq, 1);
    Xgij = new DMatrixRMaj(neq, 4);
    setu();
    uold = u.copy();
    // System.out.println("Spec : " +speceq);
    solver = new NewtonRhapson();
    solver.setOrder(3);
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec.set(i, 0, u.get(i, 0) + Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
          / system.getPhases()[0].getComponent(i).getFugacityCoefficient()));
    }

    double fsum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      fsum = fsum + system.getPhases()[1].getComponent(i).getx() - system.getPhases()[0].getComponent(i).getx();
    }
    fvec.set(numberOfComponents, 0, fsum);
    // fvec.print(0,20);
  }

  /**
   * setJac.
   */
  public void setJac() {
    CommonOps_DDRM.scale(0.0, Jac);
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
          * system.getPhases()[1].getComponent(i).getx() / system.getPhases()[0].getComponent(i).getz();
      dyidlnk[i] = system.getPhases()[1].getComponent(i).getx()
          + system.getPhases()[0].getComponent(i).getK() * dxidlnk[i];

      dyidbeta[i] = (system.getPhases()[0].getComponent(i).getK() * system.getPhases()[0].getComponent(i).getz()
          * (1 - system.getPhases()[0].getComponent(i).getK()))
          / Math.pow(1 - system.getBeta() + system.getBeta() * system.getPhases()[0].getComponent(i).getK(), 2);
      dxidbeta[i] = (system.getPhases()[0].getComponent(i).getz() * (1 - system.getPhases()[0].getComponent(i).getK()))
          / Math.pow(1 - system.getBeta() + system.getBeta() * system.getPhases()[0].getComponent(i).getK(), 2);

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
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, Math.log(system.getPhases()[0].getComponent(i).getK()));
    }

    u.set(numberOfComponents, 0, system.getBeta());
  }

  /**
   * init.
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
   * solve.
   *
   * @param np a int
   */
  public void solve(int np) {
    DMatrixRMaj dx;
    iter = 0;
    do {
      iter++;
      init();
      setfvec();
      setJac();
      dx = solveJacobianStep();
      CommonOps_DDRM.subtractEquals(u, dx);
      // System.out.println("feilen: "+dx.norm2());
    } while (NormOps_DDRM.normP2(dx) / NormOps_DDRM.normP2(u) > 1.e-10);
    // System.out.println("iter: "+iter);
    init();
  }

  /**
   * Solves the current Newton system using direct EJML LU without finite-value validation.
   *
   * @return Newton step
   * @throws IllegalStateException if EJML reports the Jacobian as singular
   */
  DMatrixRMaj solveJacobianStep() {
    LinearSolverDense<DMatrixRMaj> linearSolver = LinearSolverFactory_DDRM.lu(neq);
    if (!linearSolver.setA(Jac.copy())) {
      throw new IllegalStateException("TP-flash Newton Jacobian is singular");
    }
    DMatrixRMaj step = new DMatrixRMaj(neq, 1);
    linearSolver.solve(fvec, step);
    return step;
  }
}
