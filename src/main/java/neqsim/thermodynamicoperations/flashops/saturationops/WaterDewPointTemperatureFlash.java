package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * waterDewPointTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WaterDewPointTemperatureFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaterDewPointTemperatureFlash.class);

  /**
   * <p>
   * Constructor for waterDewPointTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WaterDewPointTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int iterations = 0;
    int maxNumberOfIterations = 10000;
    // double yold = 0, ytotal = 1, deriv = 0;
    double funk = 0;
    double maxTemperature = 0;
    double minTemperature = 1e6;
    system.init(0);

    // system.display();

    system.setNumberOfPhases(2);

    for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getComponentName().equals("water")
          || system.getPhase(0).getComponent(k).getComponentName().equals("MEG")) {
        system.setTemperature(system.getPhases()[0].getComponent(k).getMeltingPointTemperature());
        for (int l = 0; l < system.getPhases()[0].getNumberOfComponents(); l++) {
          system.getPhase(1).getComponent(l).setx(1e-30);
          // logger.info("here");
        }
        system.getPhase(1).getComponent(k).setx(1.0);
        system.init(1);
        // system.display();
        iterations = 0;
        do {
          funk = 0;
          // deriv = 0.0;
          iterations++;
          system.init(3);
          funk = system.getPhases()[0].getComponent(k).getz();

          funk -= system.getPhases()[0].getBeta()
              * system.getPhases()[1].getComponent(k).getFugacityCoefficient()
              / system.getPhases()[0].getComponent(k).getFugacityCoefficient();

          // logger.info("funk " + funk);
          /*
           * deriv -= system.getPhases()[0].getBeta()
           * (system.getPhases()[1].getComponent(k).getFugacityCoefficient()
           * system.getPhases()[0].getComponent(k).getdfugdt() * -1.0 /
           * Math.pow(system.getPhases()[0].getComponent(k) .getFugacityCoefficient(), 2.0) +
           * system.getPhases()[1].getComponent(k).getdfugdt() /
           * system.getPhases()[i].getComponent(k) .getFugacityCoefficient());
           *
           * system.setTemperature(system.getTemperature() - funk/deriv);
           */

          system.setTemperature(system.getTemperature() + 100.0 * funk);

          // logger.info("temp " + system.getTemperature());
          // if(system.getPhase(0).getComponent(k).getComponentName().equals("MEG"))
          // logger.info("funk " + funk + " temp " + system.getTemperature());
        } while (Math.abs(funk) >= 0.0000001 && iterations < maxNumberOfIterations);

        // logger.info("funk " + funk + k + " " + system.getTemperature());
        if (system.getTemperature() < minTemperature) {
          minTemperature = system.getTemperature();
        }
        if (system.getTemperature() > maxTemperature) {
          maxTemperature = system.getTemperature();
        }
      }
    }
    system.setTemperature(maxTemperature);
    // logger.info("min freezing temp " + minTemperature);
    // logger.info("max freezing temp " + maxTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
