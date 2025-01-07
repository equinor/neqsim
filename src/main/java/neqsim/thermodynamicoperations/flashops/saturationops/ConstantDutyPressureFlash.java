package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * constantDutyPressureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ConstantDutyPressureFlash extends ConstantDutyFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for constantDutyPressureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantDutyPressureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // system.calc_x_y();
    // system.init(2);

    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(0);
    }

    int iterations = 0;
    double deriv = 0;

    double funk = 0;
    double dkidp = 0;
    double dyidp = 0;
    double dxidp = 0;
    double Pold = 0;
    do {
      // system.setBeta(beta+0.65);
      system.init(2);
      iterations++;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        system.getPhases()[0].getComponent(i)
            .setK(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
                / system.getPhases()[0].getComponent(i).getFugacityCoefficient());
        system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
      }

      system.calc_x_y_nonorm();

      funk = 0.0;
      deriv = 0.0;

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        dkidp = (system.getPhases()[1].getComponent(i).getdfugdp()
            - system.getPhases()[0].getComponent(i).getdfugdp())
            * system.getPhases()[1].getComponent(i).getK();
        dxidp = -system.getPhases()[1].getComponent(i).getz() * system.getBeta() * dkidp
            / Math.pow(1.0 - system.getBeta()
                + system.getBeta() * system.getPhases()[1].getComponent(i).getK(), 2.0);
        dyidp = dkidp * system.getPhases()[1].getComponent(i).getx()
            + system.getPhases()[1].getComponent(i).getK() * dxidp;
        funk += system.getPhases()[0].getComponent(i).getx()
            - system.getPhases()[1].getComponent(i).getx();
        deriv += dyidp - dxidp;
      }

      // System.out.println("Pressure: " + system.getPressure() + " funk " + funk);

      Pold = system.getPressure();
      double pres = Math.abs(Pold - 0.5 * funk / deriv);
      system.setPressure(pres);
    } while ((Math.abs((system.getPressure() - Pold) / system.getPressure()) > 1e-10
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
