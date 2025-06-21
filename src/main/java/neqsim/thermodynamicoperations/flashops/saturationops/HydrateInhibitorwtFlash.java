package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * HydrateInhibitorwtFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HydrateInhibitorwtFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateInhibitorwtFlash.class);

  double wtfrac = 0.5;
  String inhibitor = "MEG";

  /**
   * <p>
   * Constructor for HydrateInhibitorwtFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param inhibitor a {@link java.lang.String} object
   * @param wtfr a double
   */
  public HydrateInhibitorwtFlash(SystemInterface system, String inhibitor, double wtfr) {
    super(system);
    wtfrac = wtfr;
    this.inhibitor = inhibitor;
  }

  /**
   * <p>
   * stop.
   * </p>
   */
  public void stop() {
    system = null;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    int iter = 0;
    double error = 1.0;
    double oldError = 1.0;
    double oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();
    double derrordC = 1.0;
    do {
      iter++;
      try {
        derrordC = (error - oldError)
            / (system.getPhase(0).getComponent(inhibitor).getNumberOfmoles() - oldC);
        oldError = error;
        oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();

        if (iter < 4) {
          system.addComponent(inhibitor, error * 0.01);
        } else {
          double newC = -error / derrordC;
          double correction = newC * 0.5;
          // (newC - system.getPhase(0).getComponent(inhibitor).getNumberOfmoles()) * 0.5;

          system.addComponent(inhibitor, correction);
        }
        system.init(0);
        system.init(1);
        ops.TPflash();
        double wtp = 0.0;
        if (system.hasPhaseType(PhaseType.AQUEOUS)) {
          wtp = system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getx()
              * system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getMolarMass()
              / (system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getx()
                  * system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getMolarMass()
                  + system.getPhase(PhaseType.AQUEOUS).getComponent("water").getx()
                      * system.getPhase(PhaseType.AQUEOUS).getComponent("water").getMolarMass());
        } else {
          system.addComponent(inhibitor, system.getTotalNumberOfMoles());
          ops.TPflash();
          wtp = system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getx()
              * system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getMolarMass()
              / (system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getx()
                  * system.getPhase(PhaseType.AQUEOUS).getComponent(inhibitor).getMolarMass()
                  + system.getPhase(PhaseType.AQUEOUS).getComponent("water").getx()
                      * system.getPhase(PhaseType.AQUEOUS).getComponent("water").getMolarMass());
        }
        error = -(wtp - wtfrac);

        logger.info("error " + error);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    } while ((Math.abs(error) > 1e-5 && iter < 100) || iter < 3);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
