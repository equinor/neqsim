package neqsim.thermodynamicoperations.flashops.saturationops;

import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * cricondenBarTemp1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondenBarTemp1 implements java.io.Serializable {
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
   * Constructor for cricondenBarTemp1.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CricondenBarTemp1(SystemInterface system) {
    this.system = system;
    this.numberOfComponents = system.getPhase(0).getNumberOfComponents();
    neq = numberOfComponents;
    Jac = new Matrix(neq + 2, neq + 2);
    fvec = new Matrix(neq + 2, 1);
    u = new Matrix(neq + 2, 1);
    Xgij = new Matrix(neq + 2, 4);
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
    double xtot = 0.0;
    double dQdT = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      xtot += system.getPhase(1).getComponent(i).getx();
      dQdT -= system.getPhase(1).getComponent(i).getx()
          * (system.getPhase(0).getComponent(i).getdfugdt()
              - system.getPhase(1).getComponent(i).getdfugdt());
      fvec.set(i, 0,
          Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()
              * system.getPhase(0).getComponent(i).getz() * system.getPressure())
              - Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * system.getPhases()[1].getComponent(i).getx() * system.getPressure()));
    }
    fvec.set(numberOfComponents, 0, 1.0 - xtot);
    fvec.set(numberOfComponents + 1, 0, dQdT);
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
    // double sumdyidbeta = 0, sumdxidbeta = 0;
    // int nofc = numberOfComponents;

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = 1.0 / system.getBeta()
            * (dij / system.getPhases()[0].getComponent(i).getx() - 1.0
                + system.getPhases()[0].getComponent(i).getdfugdx(j))
            + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhases()[1].getComponent(i).getx()
                - 1.0 + system.getPhases()[1].getComponent(i).getdfugdx(j));
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
      u.set(i, 0, system.getBeta() * system.getPhases()[0].getComponent(i).getx());
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
      system.getPhases()[0].getComponent(i).setx(u.get(i, 0) / system.getBeta());
      system.getPhases()[1].getComponent(i).setx(
          (system.getPhases()[0].getComponent(i).getz() - u.get(i, 0)) / (1.0 - system.getBeta()));
      system.getPhases()[0].getComponent(i).setK(system.getPhases()[0].getComponent(i).getx()
          / system.getPhases()[1].getComponent(i).getx());
      system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
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
    iter++;
    init();
    setfvec();
    setJac();
    dx = Jac.solve(fvec);
    dx.print(10, 10);
    u.minusEquals(dx);
    return (dx.norm2() / u.norm2());
  }

  /**
   * <p>
   * run.
   * </p>
   */
  public void run() {
    solve();
  }

  /**
   * <p>
   * getJFreeChart.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link org.jfree.chart.JFreeChart} object
   */
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /**
   * <p>
   * get.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] get(String name) {
    return new double[0];
  }

  /**
   * <p>
   * printToFile.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /**
   * <p>
   * getPoints.
   * </p>
   *
   * @param i a int
   * @return an array of type double
   */
  public double[][] getPoints(int i) {
    return null;
  }

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return null;
  }

  /**
   * <p>
   * getThermoSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem() {
    return system;
  }
}
