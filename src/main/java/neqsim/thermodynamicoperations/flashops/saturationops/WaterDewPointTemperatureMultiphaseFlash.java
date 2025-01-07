package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * waterDewPointTemperatureMultiphaseFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WaterDewPointTemperatureMultiphaseFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaterDewPointTemperatureMultiphaseFlash.class);

  /**
   * <p>
   * Constructor for waterDewPointTemperatureMultiphaseFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WaterDewPointTemperatureMultiphaseFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations TPflashOps = new ThermodynamicOperations(system);
    system.setMultiPhaseCheck(true);
    double dT = 0.1;
    system.setTemperature(600.0);
    do {
      i++;
      TPflashOps.TPflash();
      if (system.hasPhaseType("aqueous")) {
        dT = system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase()
            / system.getPhase(0).getComponent("water").getNumberOfmoles();
        if (dT > 1.0) {
          dT = 1.0;
        }
        system.setTemperature(system.getTemperature() + dT);
      } else {
        dT = -10.0;
        // system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase() /
        // system.getNumberOfMoles();
        system.setTemperature(system.getTemperature() + dT);
        // system.display();
      }
      // logger.info("dew temperature " + system.getTemperature());
    } while ((i < 350 && Math.abs(dT) > 1e-5));
    logger.info("i " + i);
    // system.display();
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
