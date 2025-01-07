package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * freezingPointTemperatureFlashOld class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FreezingPointTemperatureFlashOld extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezingPointTemperatureFlashOld.class);

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlashOld.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FreezingPointTemperatureFlashOld(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    int iterations = 0;
    // int maxNumberOfIterations = 15000;
    // double yold = 0, ytotal = 1;
    double deriv = 0;
    double funk = 0;
    double funkOld = 0;
    double maxTemperature = 0;
    double minTemperature = 1e6;
    double oldTemperature = 0.0;
    for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
      if (system.getPhase(3).getComponent(k).fugcoef(system.getPhase(3)) < 9e4
          && system.getPhase(3).getComponent(k).doSolidCheck()) {
        // checks if solid can be formed from component k
        system.setTemperature(system.getPhases()[0].getComponent(k).getMeltingPointTemperature());
        system.init(0);
        system.init(1);
        iterations = 0;
        do {
          funk = 0.0;
          deriv = 0.0;
          iterations++;
          system.setSolidPhaseCheck(false);
          ops.TPflash();
          system.getPhase(3).getComponent(k).fugcoef(system.getPhase(3));

          funk = system.getPhases()[0].getComponent(k).getz();
          logger.info("phase " + system.getNumberOfPhases());

          for (int i = 0; i < system.getNumberOfPhases(); i++) {
            funk -= system.getPhases()[i].getBeta()
                * system.getPhases()[3].getComponent(k).getFugacityCoefficient()
                / system.getPhases()[i].getComponent(k).getFugacityCoefficient();
            deriv -= 0.01 * system.getPhases()[i].getBeta()
                * (system.getPhases()[3].getComponent(k).getFugacityCoefficient()
                    * Math.exp(system.getPhases()[i].getComponent(k).getdfugdt()) * -1.0
                    / Math.pow(system.getPhases()[i].getComponent(k).getFugacityCoefficient(), 2.0)
                    + Math.exp(system.getPhases()[3].getComponent(k).getdfugdt())
                        / system.getPhases()[i].getComponent(k).getFugacityCoefficient());
          }
          if (iterations >= 2) {
            deriv = -(funk - funkOld) / (system.getTemperature() - oldTemperature);
          } else {
            deriv = -funk;
          }

          oldTemperature = system.getTemperature();
          funkOld = funk;

          system.setTemperature(
              system.getTemperature() + 0.5 * (iterations / (10.0 + iterations)) * funk / deriv);

          logger.info("funk/deriv " + funk / deriv);
          logger.info("temperature " + system.getTemperature());
        } while ((Math.abs(funk / deriv) >= 1e-6 && iterations < 100));

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
