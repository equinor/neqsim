package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * cricondenBarTemp1 class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondenBarTemp1 implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
  private static final Logger logger = LogManager.getLogger(CricondenBarTemp1.class);

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
   * Constructor for cricondenBarTemp1.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CricondenBarTemp1(SystemInterface system) {
    this.system = system;
    this.numberOfComponents = system.getPhase(0).getNumberOfComponents();
    neq = numberOfComponents;
    Jac = new double[neq + 2][neq + 2];
    fvec = new double[neq + 2];
    u = new double[neq + 2];
    Xgij = new double[neq + 2][4];
    setu();
    uold = u.clone();
    // System.out.println("Spec : " +speceq);
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    double xtot = 0.0;
    double dQdT = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      xtot += system.getPhase(1).getComponent(i).getx();
      dQdT -= system.getPhase(1).getComponent(i).getx()
          * (system.getPhase(0).getComponent(i).getdfugdt() - system.getPhase(1).getComponent(i).getdfugdt());
      fvec[i] = Math
          .log(system.getPhase(0).getComponent(i).getFugacityCoefficient() * system.getPhase(0).getComponent(i).getz()
              * system.getPressure())
          - Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
              * system.getPhases()[1].getComponent(i).getx() * system.getPressure());
    }
    fvec[numberOfComponents] = 1.0 - xtot;
    fvec[numberOfComponents + 1] = dQdT;
  }

  /**
   * setJac.
   */
  public void setJac() {
    for (double[] row : Jac) {
      Arrays.fill(row, 0.0);
    }
    double dij = 0.0;

    double tempJ = 0.0;
    // double sumdyidbeta = 0, sumdxidbeta = 0;
    // int nofc = numberOfComponents;

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
    if (logger.isDebugEnabled()) {
      logger.debug("Newton step: {}", Arrays.toString(dx));
    }
    u = ALGEBRA.subtract(u, dx);
    return ALGEBRA.euclideanNorm(dx) / ALGEBRA.euclideanNorm(u);
  }

  /**
   * run.
   */
  public void run() {
    solve();
  }

  /**
   * getJFreeChart.
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link org.jfree.chart.JFreeChart} object
   */
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /**
   * get.
   *
   * @param name a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] get(String name) {
    return new double[0];
  }

  /**
   * printToFile.
   *
   * @param name a {@link java.lang.String} object
   */
  public void printToFile(String name) {
  }

  /**
   * Displays the calculation result.
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  /**
   * getPoints.
   *
   * @param i a int
   * @return an array of type double
   */
  public double[][] getPoints(int i) {
    return null;
  }

  /**
   * getResultTable.
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return null;
  }

  /**
   * getThermoSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem() {
    return system;
  }
}
