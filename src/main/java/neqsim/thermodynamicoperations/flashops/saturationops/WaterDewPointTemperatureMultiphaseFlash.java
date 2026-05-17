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

    // Phase 1: Bracket the dew point using coarse steps down from a high temperature.
    // Start at 500 K and step down by 20 K to find a temperature where aqueous phase appears.
    double Thigh = 500.0; // Upper bound (no aqueous phase expected here)
    double Tlow = 200.0; // Lower bound default
    double step = 20.0;

    system.setTemperature(Thigh);
    TPflashOps.TPflash();
    i++;

    if (system.hasPhaseType("aqueous")) {
      // Already has aqueous phase at 500K — dew point is above; expand upward
      Tlow = Thigh;
      Thigh = 600.0;
      system.setTemperature(Thigh);
      TPflashOps.TPflash();
      i++;
    } else {
      // Step down to find where aqueous phase first appears
      double T = Thigh - step;
      while (T >= Tlow && i < 50) {
        system.setTemperature(T);
        TPflashOps.TPflash();
        i++;
        if (system.hasPhaseType("aqueous")) {
          Tlow = T;
          Thigh = T + step;
          break;
        }
        T -= step;
      }
      if (!system.hasPhaseType("aqueous")) {
        // No aqueous phase found even at 200 K — set temperature and return
        system.setTemperature(Tlow);
        TPflashOps.TPflash();
        i++;
        logger.info("Water dew point not found above {} K in {} iterations", Tlow, i);
        return;
      }
    }

    // Phase 2: Bisection to converge on the dew point temperature.
    // Thigh = no aqueous, Tlow = has aqueous
    double tolerance = 1e-4; // ~0.0001 K convergence
    while ((Thigh - Tlow) > tolerance && i < 100) {
      double Tmid = 0.5 * (Thigh + Tlow);
      system.setTemperature(Tmid);
      TPflashOps.TPflash();
      i++;
      if (system.hasPhaseType("aqueous")) {
        Tlow = Tmid;
      } else {
        Thigh = Tmid;
      }
    }

    // Final state: set temperature to the converged dew point
    system.setTemperature(0.5 * (Thigh + Tlow));
    TPflashOps.TPflash();
    i++;
    logger.info("Water dew point converged at {} K in {} iterations", system.getTemperature(), i);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
