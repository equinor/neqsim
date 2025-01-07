package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * constantDutyTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ConstantDutyTemperatureFlash extends ConstantDutyFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for constantDutyTemperatureFlash.
   * </p>
   */
  public ConstantDutyTemperatureFlash() {}

  /**
   * <p>
   * Constructor for constantDutyTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantDutyTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    system.init(0);
    system.init(2);

    int iterations = 0;
    double deriv = 0;

    double funk = 0;
    double dkidt = 0;
    double dyidt = 0;
    double dxidt = 0;
    double Told = 0;
    do {
      iterations++;
      // system.setBeta(beta+0.65);
      system.init(2);

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        system.getPhases()[0].getComponent(i)
            .setK(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
                / system.getPhases()[1].getComponent(i).getFugacityCoefficient());
        system.getPhases()[1].getComponent(i)
            .setK(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
                / system.getPhases()[1].getComponent(i).getFugacityCoefficient());
      }

      system.calc_x_y_nonorm();

      funk = 0e0;
      deriv = 0e0;

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        dkidt = (system.getPhases()[0].getComponent(i).getdfugdt()
            - system.getPhases()[1].getComponent(i).getdfugdt())
            * system.getPhases()[0].getComponent(i).getK();
        // dxidt=-system.getPhases()[0].getComponent(i).getx() *
        // system.getPhases()[0].getComponent(i).getx()*1.0/system.getPhases()[0].getComponent(i).getz()*system.getBeta()*dkidt;
        dxidt = -system.getPhases()[0].getComponent(i).getz() * system.getBeta() * dkidt
            / Math.pow(1.0 - system.getBeta()
                + system.getBeta() * system.getPhases()[0].getComponent(i).getK(), 2);
        dyidt = dkidt * system.getPhases()[0].getComponent(i).getx()
            + system.getPhases()[0].getComponent(i).getK() * dxidt;
        funk = funk + system.getPhases()[1].getComponent(i).getx()
            - system.getPhases()[0].getComponent(i).getx();
        deriv = deriv + dyidt - dxidt;
      }

      Told = system.getTemperature();
      system.setTemperature((Told - funk / deriv * 0.7));
      // System.out.println("Temp: " + system.getTemperature() + " funk " + funk);
    } while ((Math.abs((system.getTemperature() - Told) / system.getTemperature()) > 1e-7
        && iterations < 300) || iterations < 3);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }
}
