package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Abstract constantDutyFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class ConstantDutyFlash implements ConstantDutyFlashInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ConstantDutyFlash.class);

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
  double[] tm = {1, 1};
  int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase

  /**
   * <p>
   * Constructor for constantDutyFlash.
   * </p>
   */
  public ConstantDutyFlash() {}

  /**
   * <p>
   * Constructor for constantDutyFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantDutyFlash(SystemInterface system) {
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
        system.getPhases()[0].getComponent(i)
            .setK(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
                / system.getPhases()[1].getComponent(i).getFugacityCoefficient());
        system.getPhases()[1].getComponent(i)
            .setK(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
                / system.getPhases()[1].getComponent(i).getFugacityCoefficient());
      }

      system.calc_x_y();

      funk = 0e0;
      deriv = 0e0;

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        dkidt = (system.getPhases()[0].getComponent(i).getdfugdt()
            - system.getPhases()[1].getComponent(i).getdfugdt())
            * system.getPhases()[0].getComponent(i).getK();
        dxidt = -system.getPhases()[0].getComponent(i).getx()
            * system.getPhases()[0].getComponent(i).getx() * 1.0
            / system.getPhases()[0].getComponent(i).getz() * system.getBeta() * dkidt;
        dyidt = dkidt * system.getPhases()[0].getComponent(i).getx()
            + system.getPhases()[0].getComponent(i).getK() * dxidt;
        funk = funk + system.getPhases()[1].getComponent(i).getx()
            - system.getPhases()[0].getComponent(i).getx();
        deriv = deriv + dyidt - dxidt;
      }

      Told = system.getTemperature();
      system.setTemperature((Told - funk / deriv * 0.9));
      logger.info("Temp: " + system.getTemperature());
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
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

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
  public void addData(String name, double[][] data) {}
}
