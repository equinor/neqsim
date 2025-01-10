package neqsim.mathlib.nonlinearsolver;

import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * sysNewtonRhapson class.
 * </p>
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
   * Constructor for sysNewtonRhapson.
   * </p>
   */
  public SysNewtonRhapson() {}

  /**
   * <p>
   * Constructor for sysNewtonRhapson.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapson(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents + 2;
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    u = new Matrix(neq, 1);
    Xgij = new Matrix(neq, 4);
    setu();
    uold = u.copy();
    findSpecEqInit();
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
    fvec.set(numberOfComponents + 1, 0, 0.0);
    // fvec.print(0,10);
  }

  /**
   * <p>
   * findSpecEqInit.
   * </p>
   */
  public void findSpecEqInit() {
    speceq = 0;

    int speceqmin = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (system.getPhases()[0].getComponent(i).getTC() > system.getPhases()[0].getComponent(speceq)
          .getTC()) {
        speceq = system.getPhases()[0].getComponent(i).getComponentNumber();
      }
      if (system.getPhases()[0].getComponent(i).getTC() < system.getPhases()[0].getComponent(speceq)
          .getTC()) {
        speceqmin = system.getPhases()[0].getComponent(i).getComponentNumber();
      }
    }
    avscp = (system.getPhases()[0].getComponent(speceq).getTC()
        - system.getPhases()[0].getComponent(speceqmin).getTC()) / 2000;
    System.out.println("avscp: " + avscp);
    dTmax = avscp * 3;
    dPmax = avscp * 1.5;
    System.out.println("dTmax: " + dTmax + "  dPmax: " + dPmax);
  }

  /**
   * <p>
   * findSpecEq.
   * </p>
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
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);
    double dij = 0.0;
    double[] dxidlnk = new double[numberOfComponents];
    double[] dyidlnk = new double[numberOfComponents];
    double tempJ = 0.0;
    int nofc = numberOfComponents;
    for (int i = 0; i < numberOfComponents; i++) {
      dxidlnk[i] = -system.getBeta() * system.getPhases()[0].getComponent(i).getx()
          * system.getPhases()[1].getComponent(i).getx()
          / system.getPhases()[0].getComponent(i).getz();
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
      tempJ = system.getTemperature() * (system.getPhases()[1].getComponent(i).getdfugdt()
          - system.getPhases()[0].getComponent(i).getdfugdt());
      Jac.set(i, nofc, tempJ);
      tempJ = system.getPressure() * (system.getPhases()[1].getComponent(i).getdfugdp()
          - system.getPhases()[0].getComponent(i).getdfugdp());
      Jac.set(i, nofc + 1, tempJ);
      Jac.set(nofc, i, dyidlnk[i] - dxidlnk[i]);
    }
    Jac.set(nofc + 1, speceq, 1.0);
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
    u.set(numberOfComponents, 0, Math.log(system.getTemperature()));
    u.set(numberOfComponents + 1, 0, Math.log(system.getPressure()));
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
    system.setTemperature(Math.exp(u.get(numberOfComponents, 0)));
    system.setPressure(Math.exp(u.get(numberOfComponents + 1, 0)));
    system.calc_x_y();
    system.init(3);
  }

  /**
   * <p>
   * calcInc.
   * </p>
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
    fvec.timesEquals(0.0);
    fvec.set(nofc + 1, 0, 1.0);
    Matrix dxds = Jac.solve(fvec);
    if (np < 5) {
      double dp = 0.01;
      ds = dp / dxds.get(nofc + 1, 0);
      Xgij.setMatrix(0, nofc + 1, np - 1, np - 1, u);
      dxds.timesEquals(ds);
      // dxds.print(0,10);
      u.plusEquals(dxds);
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

        Xgij.setMatrix(0, nofc + 1, 0, 2, Xgij.getMatrix(0, nofc + 1, 1, 3));
        Xgij.setMatrix(0, nofc + 1, 3, 3, u);
        s.setMatrix(0, 0, 0, 3, Xgij.getMatrix(speceq, speceq, 0, 3));
        // s.print(0,10);
        // System.out.println("ds1 : " + ds);
        calcInc2(np);
        // System.out.println("ds2 : " + ds);

        // Here we find the next point from the polynomial.
      }
    }
  }

  /**
   * <p>
   * calcInc2.
   * </p>
   *
   * @param np a int
   */
  public void calcInc2(int np) {
    for (int j = 0; j < neq; j++) {
      xg = Xgij.getMatrix(j, j, 0, 3);
      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }
      xcoef = a.solve(xg.transpose());
      double sny = ds + s.get(0, 3);
      u.set(j, 0, xcoef.get(0, 0)
          + sny * (xcoef.get(1, 0) + sny * (xcoef.get(2, 0) + sny * xcoef.get(3, 0))));
    }
    uold = u.copy();
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
      xg = Xgij.getMatrix(numb, numb, 0, 3);

      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }

      Matrix xcoef = a.solve(xg.transpose());

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
      xg = Xgij.getMatrix(numb, numb, 0, 3);

      for (int i = 0; i < 4; i++) {
        a.set(i, 0, 1.0);
        a.set(i, 1, s.get(0, i));
        a.set(i, 2, s.get(0, i) * s.get(0, i));
        a.set(i, 3, a.get(i, 2) * s.get(0, i));
      }
      // a.print(0,10);
      // xg.print(0,10);

      Matrix xcoef = a.solve(xg.transpose());
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
   * <p>
   * Getter for the field <code>npCrit</code>.
   * </p>
   *
   * @return a int
   */
  public int getNpCrit() {
    return npCrit;
  }

  /**
   * <p>
   * sign.
   * </p>
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
      if (iter > 6) {
        System.out.println("iter > " + iter);
        calcInc(np);
        solve(np);
        break;
      }
      // System.out.println("feilen: "+dx.norm2());
    } while (dx.norm2() / u.norm2() > 1.e-8 && Double.isNaN(dx.norm2()));
    // System.out.println("iter: "+iter);
    init();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    /*
     * sysNewtonRhapson test=new sysNewtonRhapson(); double[] constants = new double[]{0.4,0.4};
     * test.setx(constants); while (test.nonsol()>1.0e-8) { constants=test.getx();
     * System.out.println(constants[0]+" "+constants[1]); } test.nonsol(); constants=test.getf();
     * System.out.println(constants[0]+" "+constants[1]); System.exit(0);
     */
  }
}
