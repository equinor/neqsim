package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSflashSingleComp class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSflashSingleComp extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;

  /**
   * <p>
   * Constructor for PSflashSingleComp.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   * @param type a int
   */
  public PSflashSingleComp(SystemInterface system, double Sspec, int type) {
    this.system = system;
    this.Sspec = Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    neqsim.thermodynamicoperations.ThermodynamicOperations bubOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
    double initTemp = system.getTemperature();

    if (system.getPressure() < system.getPhase(0).getComponent(0).getPC()) {
      try {
        bubOps.TPflash();
        if (system.getPhase(0).getType() == PhaseType.GAS) {
          bubOps.dewPointTemperatureFlash();
        } else {
          bubOps.bubblePointTemperatureFlash();
        }
      } catch (Exception ex) {
        system.setTemperature(initTemp);
        logger.error(ex.getMessage(), ex);
      }
    } else {
      bubOps.PSflash2(Sspec);
      return;
    }

    system.init(3);
    double gasEntropy = system.getPhase(0).getEntropy()
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqEntropy = system.getPhase(1).getEntropy()
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();

    if (Sspec < liqEntropy || Sspec > gasEntropy) {
      system.setTemperature(initTemp);
      bubOps.PSflash2(Sspec);
      return;
    }
    double beta = (Sspec - liqEntropy) / (gasEntropy - liqEntropy);
    system.setBeta(beta);
    system.init(3);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
