package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * VUflashSingleComp class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class VUflashSingleComp extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Uspec = 0;
  double Vspec = 0;

  /**
   * <p>
   * Constructor for VUflashSingleComp.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec a double
   * @param Uspec a double
   */
  public VUflashSingleComp(SystemInterface system, double Vspec, double Uspec) {
    this.system = system;
    this.Vspec = Vspec;
    this.Uspec = Uspec;
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
          try {
            bubOps.dewPointTemperatureFlash();
          } catch (Exception e) {
            system.setTemperature(298.0);
          }
        } else {
          bubOps.bubblePointTemperatureFlash();
        }
      } catch (Exception ex) {
        system.setTemperature(initTemp);
        logger.error(ex.getMessage(), ex);
      }
    } else {
      new VUflash(system, Vspec, Uspec).run();
      return;
    }

    system.init(3);
    double gasU = system.getPhase(0).getInternalEnergy()
        / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
    double liqU = system.getPhase(1).getInternalEnergy()
        / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();

    if (Uspec < liqU || Uspec > gasU) {
      system.setTemperature(initTemp);
      new VUflash(system, Vspec, Uspec).run();
      return;
    }

    double beta = (Uspec - liqU) / (gasU - liqU);
    system.setBeta(beta);
    system.init(3);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
