package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * PSflashSingleComp class.
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSflashSingleComp extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;

  /**
   * Constructor for PSflashSingleComp.
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
    PSFlash.validateInput(system, Sspec);
    double specifiedPressure = system.getPressure();
    neqsim.thermodynamicoperations.ThermodynamicOperations bubOps = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        system);
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
        // A failed saturation calculation cannot supply trustworthy latent-entropy endpoints.
        bubOps.PSflash2(Sspec);
        return;
      }
    } else {
      bubOps.PSflash2(Sspec);
      return;
    }

    // Saturation iterations can leave pure-phase compositions slightly off unity.
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      system.getPhase(phase).getComponent(0).setx(1.0);
    }
    system.init(3);
    double gasEntropy = system.getPhase(0).getEntropy() / system.getPhase(0).getNumberOfMolesInPhase()
        * system.getTotalNumberOfMoles();
    double liqEntropy = system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
        * system.getTotalNumberOfMoles();

    if (Sspec < liqEntropy || Sspec > gasEntropy) {
      system.setTemperature(initTemp);
      bubOps.PSflash2(Sspec);
      return;
    }
    double beta = (Sspec - liqEntropy) / (gasEntropy - liqEntropy);
    system.setBeta(beta);
    system.init(3);
    PSFlash.validateResult(system, Sspec, specifiedPressure);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
