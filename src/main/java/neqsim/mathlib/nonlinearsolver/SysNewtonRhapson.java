package neqsim.mathlib.nonlinearsolver;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * sysNewtonRhapson class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapson implements java.io.Serializable {
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
  private DMatrixRMaj jacWork;
  private DMatrixRMaj dxds;
  private DMatrixRMaj dx;
  private DMatrixRMaj aWork;
  private DMatrixRMaj xgTranspose;
  private DMatrixRMaj xcoef;
  private transient LinearSolverDense<DMatrixRMaj> jacSolver;
  private transient LinearSolverDense<DMatrixRMaj> polynomialSolver;
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;

  /**
   * Constructor for sysNewtonRhapson.
   */
  public SysNewtonRhapson() {
  }

  /**
   * Constructor for sysNewtonRhapson.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapson(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents + 2;
    Jac = new DMatrixRMaj(neq, neq);
    fvec = new DMatrixRMaj(neq, 1);
    u = new DMatrixRMaj(neq, 1);
    uold = new DMatrixRMaj(neq, 1);
    Xgij = new DMatrixRMaj(neq, 4);
    jacWork = new DMatrixRMaj(neq, neq);
    dxds = new DMatrixRMaj(neq, 1);
    dx = new DMatrixRMaj(neq, 1);
    aWork = new DMatrixRMaj(4, 4);
    xgTranspose = new DMatrixRMaj(4, 1);
    xcoef = new DMatrixRMaj(4, 1);
    initializeSolvers();
    setu();
    uold.setTo(u);
    findSpecEqInit();
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
    fvec.set(numberOfComponents + 1, 0, 0.0);
    // fvec.print(0,10);
  }

  /**
   * findSpecEqInit.
   */
  public void findSpecEqInit() {
    speceq = 0;

    int speceqmin = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (system.getPhases()[0].getComponent(i).getTC() > system.getPhases()[0].getComponent(speceq).getTC()) {
        speceq = system.getPhases()[0].getComponent(i).getComponentNumber();
      }
      if (system.getPhases()[0].getComponent(i).getTC() < system.getPhases()[0].getComponent(speceq).getTC()) {
        speceqmin = system.getPhases()[0].getComponent(i).getComponentNumber();
      }
    }
    avscp = (system.getPhases()[0].getComponent(speceq).getTC() - system.getPhases()[0].getComponent(speceqmin).getTC())
        / 2000;
    System.out.println("avscp: " + avscp);
    dTmax = avscp * 3;
    dPmax = avscp * 1.5;
    System.out.println("dTmax: " + dTmax + "  dPmax: " + dPmax);
  }

  /**
   * findSpecEq.
   */
  public void findSpecEq() {
    double max = 0;
    for (int i = 0; i < numberOfComponents + 2; i++) {
      if (Math.abs(u.get(i, 0) - uold.get(i, 0) / uold.get(i, 0)) > max) {
        max = Math.abs(u.get(i, 0) - uold.get(i, 0) / uold.get(i, 0));
      }
    }
  }

  /**
   * setJac.
   */
  public void setJac() {
    Jac.zero();
    double dij = 0.0;
    double[] dxidlnk = new double[numberOfComponents];
    double[] dyidlnk = new double[numberOfComponents];
    double tempJ = 0.0;
    int nofc = numberOfComponents;
    for (int i = 0; i < numberOfComponents; i++) {
      dxidlnk[i] = -system.getBeta() * system.getPhases()[0].getComponent(i).getx()
          * system.getPhases()[1].getComponent(i).getx() / system.getPhases()[0].getComponent(i).getz();
      dyidlnk[i] = system.getPhases()[1].getComponent(i).getx()
          + system.getPhases()[0].getComponent(i).getK() * dxidlnk[i];
      // System.out.println("dxidlnk("+i+") "+dxidlnk[i]);
      // System.out.println("dyidlnk("+i+") "+dyidlnk[i]);
    }
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij + system.getPhases()[1].getComponent(i).getdfugdx(j) * dyidlnk[j]
            - system.getPhases()[0].getComponent(i).getdfugdx(j) * dxidlnk[j];
        Jac.set(i, j, tempJ);
      }
      tempJ = system.getTemperature()
          * (system.getPhases()[1].getComponent(i).getdfugdt() - system.getPhases()[0].getComponent(i).getdfugdt());
      Jac.set(i, nofc, tempJ);
      tempJ = system.getPressure()
          * (system.getPhases()[1].getComponent(i).getdfugdp() - system.getPhases()[0].getComponent(i).getdfugdp());
      Jac.set(i, nofc + 1, tempJ);
      Jac.set(nofc, i, dyidlnk[i] - dxidlnk[i]);
    }
    Jac.set(nofc + 1, speceq, 1.0);
  }

  /**
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, Math.log(system.getPhases()[0].getComponent(i).getK()));
    }
    u.set(numberOfComponents, 0, Math.log(system.getTemperature()));
    u.set(numberOfComponents + 1, 0, Math.log(system.getPressure()));
  }

  /**
   * init.
   */
  public void init() {
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhases()[0].getComponent(i).setK(Math.exp(u.get(i, 0)));
      system.getPhases()[1].getComponent(i).setK(Math.exp(u.get(i, 0)));
    }
    system.setTemperature(Math.exp(u.get(numberOfComponents, 0)));
    system.setPressure(Math.exp(u.get(numberOfComponents + 1, 0)));
    system.calc_x_y();
    system.init(3);
  }

  /**
   * calcInc.
   *
   * @param np a int
   */
  public void calcInc(int np) {
    // u.print(0,10);
    // fvec.print(0,10);
    // Jac.print(0,10);

    // First we need the sensitivity vector dX/dS

    findSpecEq();
    int nofc = numberOfComponents;
    fvec.zero();
    fvec.set(nofc + 1, 0, 1.0);
    solveJacobian(fvec, dxds);
    if (np < 5) {
      double dp = 0.01;
      ds = dp / dxds.get(nofc + 1, 0);
      CommonOps_DDRM.insert(u, Xgij, 0, np - 1);
      CommonOps_DDRM.scale(ds, dxds);
      // dxds.print(0,10);
      CommonOps_DDRM.addEquals(u, dxds);
      // Xgij.print(0,10);
      // u.print(0,10);
    } else {
      // System.out.println("iter " +iter + " np " + np);
      if (iter > 6) {
        ds *= 0.5;
        System.out.println("ds > 6");
      } else {
        if (iter < 3) {
          ds *= 1.5;
        }
        if (iter == 3) {
          ds *= 1.1;
        }
        if (iter == 4) {
          ds *= 1.0;
        }
        if (iter > 4) {
          ds *= 0.5;
        }

        // Now we check wheater this ds is greater than dTmax and dPmax.
        if (Math.abs(system.getTemperature() * dxds.get(nofc, 0) * ds) > dTmax) {
          // System.out.println("true T");
          ds = sign(dTmax / system.getTemperature() / Math.abs(dxds.get(nofc, 0)), ds);
        }

        if (Math.abs(system.getPressure() * dxds.get(nofc + 1, 0) * ds) > dPmax) {
          ds = sign(dPmax / system.getPressure() / Math.abs(dxds.get(nofc + 1, 0)), ds);
          // System.out.println("true P");
        }
        if (etterCP2) {
          etterCP2 = false;
          ds = 0.5 * ds;
        }

        for (int row = 0; row < neq; row++) {
          for (int column = 0; column < 3; column++) {
            Xgij.set(row, column, Xgij.get(row, column + 1));
          }
        }
        CommonOps_DDRM.insert(u, Xgij, 0, 3);
        for (int column = 0; column < 4; column++) {
          s.set(0, column, Xgij.get(speceq, column));
        }
        // s.print(0,10);
        // System.out.println("ds1 : " + ds);
        calcInc2(np);
        // System.out.println("ds2 : " + ds);

        // Here we find the next point from the polynomial.
      }
    }
  }

  /**
   * calcInc2.
   *
   * @param np a int
   */
  public void calcInc2(int np) {
    for (int j = 0; j < neq; j++) {
      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }
      solvePolynomial(j);
      double sny = ds + s.get(0, 3);
      u.set(j, 0, xcoef.get(0, 0) + sny * (xcoef.get(1, 0) + sny * (xcoef.get(2, 0) + sny * xcoef.get(3, 0))));
    }
    uold.setTo(u);
    // s.print(0,10);
    // Xgij.print(0,10);
    double xlnkmax = 0;
    int numb = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (Math.abs(u.get(i, 0)) > xlnkmax) {
        xlnkmax = Math.abs(u.get(i, 0));
        numb = i;
      }
    }
    // System.out.println("klnmax: " + u.get(numb,0) + " np " + np + " xlnmax " +
    // xlnkmax + "avsxp " + avscp);
    // System.out.println("np: " + np + " ico2p: " + ic02p + " ic03p " + ic03p);

    if ((testcrit == -3) && ic03p != np) {
      etterCP2 = true;
      etterCP = true;
      // System.out.println("Etter CP");
      // System.exit(0);
      ic03p = np;
      testcrit = 0;
      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }

      solvePolynomial(numb);

      double[] coefs = new double[4];
      coefs[0] = xcoef.get(3, 0);
      coefs[1] = xcoef.get(2, 0);
      coefs[2] = xcoef.get(1, 0);
      coefs[3] = xcoef.get(0, 0) - sign(avscp, -s.get(0, 3));
      solver.setConstants(coefs);

      // System.out.println("s4: " + s.get(0,3) + " coefs " + coefs[0] +" "+
      // coefs[1]+" " + coefs[2]+" " + coefs[3]);
      double nys = solver.solve1order(s.get(0, 3));
      // s = nys - s.get(0,3);
      ds = sign(s.get(0, 3) - nys, ds);
      // System.out.println("critpoint: " + ds);

      // ds = -nys - s.get(0,3);
      calcInc2(np);

      TC2 = Math.exp(u.get(numberOfComponents, 0));
      PC2 = Math.exp(u.get(numberOfComponents + 1, 0));
      system.setTC((TC1 + TC2) * 0.5);
      system.setPC((PC1 + PC2) * 0.5);
      system.setPhaseType(0, PhaseType.GAS);
      system.setPhaseType(1, PhaseType.LIQUID);
      return;
    } else if ((xlnkmax < avscp && testcrit != 1) && (np != ic03p && !etterCP)) {
      // System.out.println("hei fra her");
      testcrit = 1;
      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }
      // a.print(0,10);
      // xg.print(0,10);

      solvePolynomial(numb);
      // xcoef.print(0,10);

      double[] coefs = new double[4];
      coefs[0] = xcoef.get(3, 0);
      coefs[1] = xcoef.get(2, 0);
      coefs[2] = xcoef.get(1, 0);
      coefs[3] = xcoef.get(0, 0) - sign(avscp, ds);
      solver.setConstants(coefs);

      // System.out.println("s4: " + s.get(0,3) + " coefs " + coefs[0] +" "+
      // coefs[1]+" " + coefs[2]+" " + coefs[3]);
      double nys = solver.solve1order(s.get(0, 3));

      ds = -nys - s.get(0, 3);
      // System.out.println("critpoint: " + ds);
      npCrit = np;

      calcInc2(np);

      TC1 = Math.exp(u.get(numberOfComponents, 0));
      PC1 = Math.exp(u.get(numberOfComponents + 1, 0));
      return;
    }

    if (testcrit == 1) {
      testcrit = -3;
    }
  }

  /**
   * Getter for the field <code>npCrit</code>.
   *
   * @return a int
   */
  public int getNpCrit() {
    return npCrit;
  }

  /**
   * sign.
   *
   * @param a a double
   * @param b a double
   * @return a double
   */
  public double sign(double a, double b) {
    a = Math.abs(a);
    b = b >= 0 ? 1.0 : -1.0;
    return a * b;
  }

  /**
   * solve.
   *
   * @param np a int
   */
  public void solve(int np) {
    ensureSolversInitialized();
    iter = 0;
    do {
      iter++;
      init();
      setfvec();
      setJac();
      solveJacobian(fvec, dx);
      CommonOps_DDRM.subtractEquals(u, dx);
      if (iter > 6) {
        System.out.println("iter > " + iter);
        calcInc(np);
        solve(np);
        break;
      }
      // System.out.println("feilen: "+dx.norm2());
    } while (NormOps_DDRM.normF(dx) / NormOps_DDRM.normF(u) > 1.e-8 && Double.isNaN(NormOps_DDRM.normF(dx)));
    // System.out.println("iter: "+iter);
    init();
  }

  /** Initializes transient EJML solvers, including after deserialization. */
  private void initializeSolvers() {
    jacSolver = LinearSolverFactory_DDRM.lu(neq);
    polynomialSolver = LinearSolverFactory_DDRM.lu(4);
  }

  /** Ensures the transient EJML solvers are available. */
  private void ensureSolversInitialized() {
    if (jacSolver == null || polynomialSolver == null) {
      initializeSolvers();
    }
  }

  /**
   * Solves the system Jacobian for the supplied right-hand side.
   *
   * @param rightHandSide right-hand-side vector
   * @param solution destination for the solution vector
   * @throws IllegalStateException if the Jacobian is singular
   */
  private void solveJacobian(DMatrixRMaj rightHandSide, DMatrixRMaj solution) {
    ensureSolversInitialized();
    jacWork.setTo(Jac);
    if (!jacSolver.setA(jacWork)) {
      throw new IllegalStateException("Phase-envelope Jacobian is singular");
    }
    jacSolver.solve(rightHandSide, solution);
  }

  /**
   * Solves the cubic predictor interpolation system for one history row.
   *
   * @param historyRow row in the continuation history matrix
   * @throws IllegalStateException if the interpolation matrix is singular
   */
  private void solvePolynomial(int historyRow) {
    ensureSolversInitialized();
    for (int column = 0; column < 4; column++) {
      xgTranspose.set(column, 0, Xgij.get(historyRow, column));
    }
    aWork.setTo(a);
    if (!polynomialSolver.setA(aWork)) {
      throw new IllegalStateException("Phase-envelope predictor interpolation matrix is singular");
    }
    polynomialSolver.solve(xgTranspose, xcoef);
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    /*
     * sysNewtonRhapson test=new sysNewtonRhapson(); double[] constants = new double[]{0.4,0.4}; test.setx(constants);
     * while (test.nonsol()>1.0e-8) { constants=test.getx(); System.out.println(constants[0]+" "+constants[1]); }
     * test.nonsol(); constants=test.getf(); System.out.println(constants[0]+" "+constants[1]); System.exit(0);
     */
  }
}
