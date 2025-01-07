package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * sysNewtonRhapsonPhaseEnvelope2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonPhaseEnvelope2 implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SysNewtonRhapsonPhaseEnvelope2.class);

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
  double avscp = 0.2;
  double TC1 = 0;
  double TC2 = 0;
  double PC1 = 0;
  double PC2 = 0;
  RealMatrix Jac;
  RealMatrix fvec;
  RealMatrix u;
  RealMatrix uold;
  RealMatrix Xgij;
  SystemInterface system;
  int speceq = 0;
  RealMatrix a = new Array2DRowRealMatrix(4, 4);
  RealMatrix s = new Array2DRowRealMatrix(1, 4);
  RealMatrix xg;
  RealMatrix xcoef;
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonPhaseEnvelope2.
   * </p>
   */
  public SysNewtonRhapsonPhaseEnvelope2() {}

  /**
   * <p>
   * Constructor for sysNewtonRhapsonPhaseEnvelope2.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SysNewtonRhapsonPhaseEnvelope2(SystemInterface system) {
    this.system = system;
    neq = system.getPhase(0).getNumberOfComponents() + 2;
    Jac = new Array2DRowRealMatrix(neq, neq);
    fvec = new Array2DRowRealMatrix(neq, 1);
    u = new Array2DRowRealMatrix(neq, 1);
    Xgij = new Array2DRowRealMatrix(neq, 4);

    setu();
    uold = u.copy();
    findSpecEqInit();
    solver = new NewtonRhapson();
    solver.setOrder(3);
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      fvec.setEntry(i, 0,
          u.getEntry(i, 0) + Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()
              / system.getPhase(1).getComponent(i).getFugacityCoefficient()));
    }
    double fsum = 0.0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      fsum += system.getPhase(0).getComponent(i).getx() - system.getPhase(1).getComponent(i).getx();
    }
    fvec.setEntry(system.getPhase(0).getNumberOfComponents(), 0, fsum);
    fvec.setEntry(system.getPhase(0).getNumberOfComponents() + 1, 0, 0);
  }

  /**
   * <p>
   * findSpecEqInit.
   * </p>
   */
  public void findSpecEqInit() {
    speceq = 0;
    int speceqmin = 0;

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getTC() > system.getPhase(0).getComponent(speceq)
          .getTC()) {
        speceq = system.getPhase(0).getComponent(i).getComponentNumber();
      }
      if (system.getPhase(0).getComponent(i).getTC() < system.getPhase(0).getComponent(speceqmin)
          .getTC()) {
        speceqmin = system.getPhase(0).getComponent(i).getComponentNumber();
      }
    }
    avscp = 0.3;
    // (system.getPhase(0).getComponent(speceq).getTC() -
    // system.getPhase(0).getComponent(speceqmin).getTC()) / 300.0;
    logger.info("avscp: " + avscp);
    dTmax = 10.0; // avscp*10;
    dPmax = 10.0; // avscp*10;
    logger.info("dTmax: " + dTmax + "  dPmax: " + dPmax);
  }

  /**
   * <p>
   * findSpecEq.
   * </p>
   */
  public void findSpecEq() {
    double max = 0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents() + 2; i++) {
      if (Math.abs((u.getEntry(i, 0) - uold.getEntry(i, 0)) / uold.getEntry(i, 0)) > max) {
        speceq = i;
        max = Math.abs((u.getEntry(i, 0) - uold.getEntry(i, 0)) / uold.getEntry(i, 0));
      }
    }

    // logger.info("spec eq: " + speceq);
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac = Jac.scalarMultiply(0.0);
    double dij = 0.0;
    double[] dxidlnk = new double[system.getPhase(0).getNumberOfComponents()];
    double[] dyidlnk = new double[system.getPhase(0).getNumberOfComponents()];
    double tempJ = 0.0;
    int nofc = system.getPhase(0).getNumberOfComponents();
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      dxidlnk[i] = -system.getBeta() * system.getPhase(1).getComponent(i).getx()
          * system.getPhase(0).getComponent(i).getx() / system.getPhase(0).getComponent(i).getz();
      dyidlnk[i] = system.getPhase(0).getComponent(i).getx()
          + system.getPhase(1).getComponent(i).getK() * dxidlnk[i];
      // logger.info("dxidlnk("+i+") "+dxidlnk[i]);
      // logger.info("dyidlnk("+i+") "+dyidlnk[i]);
    }
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij + system.getPhase(0).getComponent(i).getdfugdx(j) * dyidlnk[j]
            - system.getPhase(1).getComponent(i).getdfugdx(j) * dxidlnk[j];
        Jac.setEntry(i, j, tempJ);
      }
      tempJ = system.getTemperature() * (system.getPhase(0).getComponent(i).getdfugdt()
          - system.getPhase(1).getComponent(i).getdfugdt());
      Jac.setEntry(i, nofc, tempJ);
      tempJ = system.getPressure() * (system.getPhase(0).getComponent(i).getdfugdp()
          - system.getPhase(1).getComponent(i).getdfugdp());
      Jac.setEntry(i, nofc + 1, tempJ);
      Jac.setEntry(nofc, i, dyidlnk[i] - dxidlnk[i]);
    }
    Jac.setEntry(nofc + 1, speceq, 1.0);
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      u.setEntry(i, 0, Math.log(system.getPhase(0).getComponent(i).getK()));
    }
    u.setEntry(system.getPhase(0).getNumberOfComponents(), 0, Math.log(system.getTemperature()));
    u.setEntry(system.getPhase(0).getNumberOfComponents() + 1, 0, Math.log(system.getPressure()));
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      system.getPhase(0).getComponent(i).setK(Math.exp(u.getEntry(i, 0)));
      system.getPhase(1).getComponent(i).setK(Math.exp(u.getEntry(i, 0)));
    }
    system.setTemperature(Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents(), 0)));
    system.setPressure(Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents() + 1, 0)));
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
    // First we need the sensitivity vector dX/dS

    findSpecEq();
    int nofc = system.getPhase(0).getNumberOfComponents();
    fvec = fvec.scalarMultiply(0.0);
    fvec.setEntry(nofc + 1, 0, 1.0);
    DecompositionSolver solver2 = new LUDecomposition(Jac).getSolver();
    RealMatrix dxds = solver2.solve(fvec);
    if (np < 5) {
      double dp = 0.1;
      ds = dp / dxds.getEntry(nofc + 1, 0);
      Xgij.setSubMatrix(u.getData(), 0, np - 1);
      dxds = dxds.scalarMultiply(ds);
      u = u.add(dxds);
    } else {
      // logger.info("iter " +iter + " np " + np);
      if (iter > 6) {
        ds *= 0.5;
        logger.info("ds > 6");
      } else {
        if (iter < 3) {
          ds *= 1.3;
        }
        if (iter == 3) {
          ds *= 1.0;
        }
        if (iter == 4) {
          ds *= 0.9;
        }
        if (iter > 4) {
          ds *= 0.7;
        }

        // Now we check wheater this ds is greater than dTmax and dPmax.
        if (Math.abs(system.getTemperature() * dxds.getEntry(nofc, 0) * ds) > dTmax) {
          // logger.info("true T");
          ds = sign(dTmax / system.getTemperature() / Math.abs(dxds.getEntry(nofc, 0)), ds);
        }

        if (Math.abs(system.getPressure() * dxds.getEntry(nofc + 1, 0) * ds) > dPmax) {
          ds = sign(dPmax / system.getPressure() / Math.abs(dxds.getEntry(nofc + 1, 0)), ds);
          // logger.info("true P");
        }

        if (etterCP2) {
          etterCP2 = false;
          // ds = 0.5*ds;
        }
        logger.info("ds here " + ds);

        Xgij.setSubMatrix(Xgij.getSubMatrix(0, nofc + 1, 1, 3).getData(), 0, 0);
        Xgij.setSubMatrix(u.getData(), 0, 3);
        s.setSubMatrix(Xgij.getSubMatrix(speceq, speceq, 0, 3).getData(), 0, 0);
        // s.print(0,10);
        // logger.info("ds1 : " + ds);

        for (int i = 0; i < nofc + 2; i++) {
          logger.info("Xgij " + Xgij.getEntry(i, 0));
          logger.info("Xgij " + Xgij.getEntry(i, 1));
          logger.info("Xgij " + Xgij.getEntry(i, 2));
          logger.info("Xgij " + Xgij.getEntry(i, 3));
        }
        calcInc2(np);

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
      xg = Xgij.getSubMatrix(j, j, 0, 3);
      for (int i = 0; i < 4; i++) {
        a.setEntry(i, 0, 1.0);
        a.setEntry(i, 1, s.getEntry(0, i));
        a.setEntry(i, 2, s.getEntry(0, i) * s.getEntry(0, i));
        a.setEntry(i, 3, a.getEntry(i, 2) * s.getEntry(0, i));
      }
    }
    for (int j = 0; j < neq; j++) {
      xg = Xgij.getSubMatrix(j, j, 0, 3).copy();
      DecompositionSolver solver2 = new LUDecomposition(a).getSolver();
      xcoef = solver2.solve(xg.transpose());
      double sny = ds + s.getEntry(0, 3);
      u.setEntry(j, 0, xcoef.getEntry(0, 0) + sny
          * (xcoef.getEntry(1, 0) + sny * (xcoef.getEntry(2, 0) + sny * xcoef.getEntry(3, 0))));
      logger.info("u" + j + " " + Math.exp(u.getEntry(j, 0)));
    }
    uold = u.copy();

    double xlnkmax = 0;
    int numb = 0;

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (Math.abs(u.getEntry(i, 0)) > xlnkmax) {
        xlnkmax = Math.abs(u.getEntry(i, 0));
        numb = i;
      }
    }

    logger.info(
        "klnmax: " + u.getEntry(numb, 0) + "  np " + np + " xlnmax " + xlnkmax + "avsxp " + avscp);
    // logger.info("np: " + np + " ico2p: " + ic02p + " ic03p " + ic03p);

    if ((testcrit == -3) && ic03p != np) {
      etterCP2 = true;
      etterCP = true;
      logger.info("Etter CP");
      // System.exit(0);
      ic03p = np;
      testcrit = 0;
      xg = Xgij.getSubMatrix(numb, numb, 0, 3);

      for (int i = 0; i < 4; i++) {
        a.setEntry(i, 0, 1.0);
        a.setEntry(i, 1, s.getEntry(0, i));
        a.setEntry(i, 2, s.getEntry(0, i) * s.getEntry(0, i));
        a.setEntry(i, 3, a.getEntry(i, 2) * s.getEntry(0, i));
      }

      DecompositionSolver solver2 = new LUDecomposition(a).getSolver();
      RealMatrix xcoef = solver2.solve(xg.transpose());

      double[] coefs = new double[4];
      coefs[0] = xcoef.getEntry(3, 0);
      coefs[1] = xcoef.getEntry(2, 0);
      coefs[2] = xcoef.getEntry(1, 0);
      coefs[3] = xcoef.getEntry(0, 0) - sign(avscp, -s.getEntry(0, 3));
      solver.setConstants(coefs);

      // logger.info("s4: " + s.get(0,3) + " coefs " + coefs[0] +" "+ coefs[1]+" " +
      // coefs[2]+" " + coefs[3]);
      double nys = solver.solve1order(s.getEntry(0, 3));
      // s = nys - s.get(0,3);
      ds = sign(s.getEntry(0, 3) - nys, ds);
      // logger.info("critpoint: " + ds);

      // ds = -nys - s.get(0,3);
      calcInc2(np);

      TC2 = Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents(), 0));
      PC2 = Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents() + 1, 0));
      system.setTC((TC1 + TC2) * 0.5);
      system.setPC((PC1 + PC2) * 0.5);
      system.invertPhaseTypes();
      logger.info("invert phases....");
      // system.setPhaseType(0,0);
      // system.setPhaseType(1,1);
      return;
    } else if ((xlnkmax < 1.5 && testcrit != 1) && (np != ic03p && !etterCP)) {
      logger.info("hei fra her");
      testcrit = 1;
      xg = Xgij.getSubMatrix(numb, numb, 0, 3);

      for (int i = 0; i < 4; i++) {
        a.setEntry(i, 0, 1.0);
        a.setEntry(i, 1, s.getEntry(0, i));
        a.setEntry(i, 2, s.getEntry(0, i) * s.getEntry(0, i));
        a.setEntry(i, 3, a.getEntry(i, 2) * s.getEntry(0, i));
      }
      // a.print(0,10);
      // xg.print(0,10);

      DecompositionSolver solver2 = new LUDecomposition(a).getSolver();
      RealMatrix xcoef = solver2.solve(xg.transpose());
      // xcoef.print(0,10);

      double[] coefs = new double[4];
      coefs[0] = xcoef.getEntry(3, 0);
      coefs[1] = xcoef.getEntry(2, 0);
      coefs[2] = xcoef.getEntry(1, 0);
      coefs[3] = xcoef.getEntry(0, 0) - sign(avscp, ds);
      solver.setConstants(coefs);

      // logger.info("s4: " + s.get(0,3) + " coefs " + coefs[0] +" "+ coefs[1]+" " +
      // coefs[2]+" " + coefs[3]);
      double nys = solver.solve1order(s.getEntry(0, 3));

      ds = -nys - s.getEntry(0, 3);
      // logger.info("critpoint: " + ds);
      npCrit = np;

      calcInc2(np);

      TC1 = Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents(), 0));
      PC1 = Math.exp(u.getEntry(system.getPhase(0).getNumberOfComponents() + 1, 0));
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
   * critPassed.
   * </p>
   *
   * @return a boolean
   */
  public boolean critPassed() {
    if (testcrit == 1) {
      testcrit = -3;
      return true;
    } else {
      return false;
    }
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
    RealMatrix dx;
    iter = 0;

    do {
      iter++;
      init();
      setfvec();
      setJac();
      DecompositionSolver solver2 = new LUDecomposition(Jac).getSolver();
      dx = solver2.solve(fvec);
      u = u.subtract(dx);

      if (iter > 6) {
        logger.info("iter > " + iter);
        calcInc(np);
        solve(np);
        break;
      }

      logger.info("feilen: " + dx.getNorm() / u.getNorm());
    } while (dx.getNorm() / u.getNorm() > 1.e-10 && !Double.isNaN(dx.getNorm()));
    logger.info("iter: " + iter);
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
     * logger.info(constants[0]+" "+constants[1]); } test.nonsol(); constants=test.getf();
     * logger.info(constants[0]+" "+constants[1]); System.exit(0);
     */
  }
}
