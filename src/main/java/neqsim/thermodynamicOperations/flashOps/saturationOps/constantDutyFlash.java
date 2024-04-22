package neqsim.thermodynamicOperations.flashOps.saturationOps;




import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract constantDutyFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class constantDutyFlash implements ConstantDutyFlashInterface {
  private static final long serialVersionUID = 1000;
  

  SystemInterface system;
  protected boolean superCritical = false;
  int i;
  int j = 0;
  int nummer = 0;
  int iterations = 0;
  int maxNumberOfIterations = 10000;
  double gibbsEnergy = 0;
  double gibbsEnergyOld = 0;
  double Kold;
  double deviation = 0;
  double g0 = 0;
  double g1 = 0;
  double[] lnOldOldK;
  double[] lnK;
  double[] lnOldK;
  double[] oldDeltalnK;
  double[] deltalnK;
  double[] tm = { 1, 1 };
  int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase

  public constantDutyFlash() {
  }

  /**
   * <p>
   * Constructor for constantDutyFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public constantDutyFlash(SystemInterface system) {
    this.system = system;
    lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    system.init(0);
    system.init(2);

    // int iterations = 0, maxNumberOfIterations = 10000;
    // double yold = 0, ytotal = 1;
    double deriv = 0;

    double funk = 0;
    double dkidt = 0;
    double dyidt = 0;
    double dxidt = 0;
    double Told = 0;
    do {
      system.init(2);

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        system.getPhases()[0].getComponents()[i]
            .setK(system.getPhases()[0].getComponents()[i].getFugacityCoefficient()
                / system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
        system.getPhases()[1].getComponents()[i]
            .setK(system.getPhases()[0].getComponents()[i].getFugacityCoefficient()
                / system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
      }

      system.calc_x_y();

      funk = 0e0;
      deriv = 0e0;

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        dkidt = (system.getPhases()[0].getComponents()[i].getdfugdt()
            - system.getPhases()[1].getComponents()[i].getdfugdt())
            * system.getPhases()[0].getComponents()[i].getK();
        dxidt = -system.getPhases()[0].getComponents()[i].getx()
            * system.getPhases()[0].getComponents()[i].getx() * 1.0
            / system.getPhases()[0].getComponents()[i].getz() * system.getBeta() * dkidt;
        dyidt = dkidt * system.getPhases()[0].getComponents()[i].getx()
            + system.getPhases()[0].getComponents()[i].getK() * dxidt;
        funk = funk + system.getPhases()[1].getComponents()[i].getx()
            - system.getPhases()[0].getComponents()[i].getx();
        deriv = deriv + dyidt - dxidt;
      }

      Told = system.getTemperature();
      system.setTemperature((Told - funk / deriv * 0.9));
      
    } while (Math.abs((system.getTemperature() - Told) / system.getTemperature()) > 1e-7);
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public boolean isSuperCritical() {
    return superCritical;
  }

  /**
   * Setter for property superCritical.
   *
   * @param superCritical New value of property superCritical.
   */
  public void setSuperCritical(boolean superCritical) {
    this.superCritical = superCritical;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {
  }
}
