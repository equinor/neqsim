package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PHflashSingleComp class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PHflashSingleComp extends Flash {
  private static final long serialVersionUID = 1000;

  double Hspec = 0;

  /**
   * <p>
   * Constructor for PHflashSingleComp.
   * </p>
   */
  public PHflashSingleComp() {}

  /**
   * <p>
   * Constructor for PHflashSingleComp.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   * @param type a int
   */
  public PHflashSingleComp(SystemInterface system, double Hspec, int type) {
    this.system = system;
    this.Hspec = Hspec;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    neqsim.thermodynamicOperations.ThermodynamicOperations bubOps =
        new neqsim.thermodynamicOperations.ThermodynamicOperations(system);
    double initTemp = system.getTemperature();

    if (system.getPressure() < system.getPhase(0).getComponent(0).getPC()) {
      try {
        bubOps.TPflash();
        if (system.getPhase(0).getPhaseTypeName().equals("gas")) {
          bubOps.dewPointTemperatureFlash();
        } else {
          bubOps.bubblePointTemperatureFlash();
        }
      } catch (Exception ex) {
        system.setTemperature(initTemp);
        logger.error(ex.getMessage(), ex)e(), ex)e(), ex)e(), ex);
      }
    } else {
      bubOps.PHflash2(Hspec, 0);
      return;
    }

    system.init(3);
    double gasEnthalpy = system.getPhase(0).getEnthalpy()
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqEnthalpy = system.getPhase(1).getEnthalpy()
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();

    /*
     * double solidEnthalpy = 0.0;
     * 
     * if (system.doSolidPhaseCheck()) { system.init(3, 3); solidEnthalpy =
     * system.getPhases()[3].getEnthalpy() / system.getPhases()[3].getNumberOfMolesInPhase()
     * system.getTotalNumberOfMoles();
     * 
     * if (Hspec < liqEnthalpy && Hspec > solidEnthalpy) { double solidbeta = (Hspec - liqEnthalpy)
     * / (gasEnthalpy - liqEnthalpy); } }
     */

    if (Hspec < liqEnthalpy || Hspec > gasEnthalpy) {
      system.setTemperature(initTemp);
      bubOps.PHflash2(Hspec, 0);
      return;
    }
    double beta = (Hspec - liqEnthalpy) / (gasEnthalpy - liqEnthalpy);

    system.setBeta(beta);

    system.init(3);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
