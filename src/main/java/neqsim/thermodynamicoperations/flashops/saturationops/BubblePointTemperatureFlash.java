package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * bubblePointTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubblePointTemperatureFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubblePointTemperatureFlash.class);

  /**
   * <p>
   * Constructor for bubblePointTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BubblePointTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.getPhase(0).getNumberOfComponents() == 1
        && system.getPressure() >= system.getPhase(0).getComponent(0).getPC()) {
      throw new IllegalStateException("System is supercritical");
    }
    int iterations = 0;
    int maxNumberOfIterations = 10000;
    double yold = 0;
    double ytotal = 1;
    double deriv = 0;

    double funk = 0;
    double ktot = 0.0;
    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[1].getComponent(i).setx(system.getPhases()[0].getComponent(i).getz());
      system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
          * system.getPhases()[1].getComponent(i).getx());
    }
    system.setNumberOfPhases(2);
    do {
      system.setTemperature((system.getTemperature() + system.getTemperature() / ytotal) / 10);
      // logger.info("temp . " + system.getTemperature());
      funk = 0;
      deriv = 0;
      ytotal = 0;
      ktot = 0.0;
      system.init(2);
      for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
        do {
          iterations++;

          yold = system.getPhases()[0].getComponent(i).getx();
          system.getPhases()[0].getComponent(i)
              .setK(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  / system.getPhases()[0].getComponent(i).getFugacityCoefficient());
          system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
          system.getPhases()[0].getComponent(i)
              .setx(system.getPhases()[1].getComponent(i).getx()
                  * system.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  / system.getPhases()[0].getComponent(i).getFugacityCoefficient());
        } while ((Math.abs(yold - system.getPhases()[1].getComponent(i).getx()) > 1e-10)
            && (iterations < maxNumberOfIterations));

        ytotal += system.getPhases()[0].getComponent(i).getx();
        funk += system.getPhases()[1].getComponent(i).getx()
            * system.getPhases()[1].getComponent(i).getK();
        deriv += system.getPhases()[1].getComponent(i).getx()
            * system.getPhases()[1].getComponent(i).getK()
            * (system.getPhases()[1].getComponent(i).getdfugdt()
                - system.getPhases()[0].getComponent(i).getdfugdt());
        ktot += Math.abs(system.getPhases()[1].getComponent(i).getK() - 1.0);
      }

      // logger.info("FUNK: " + funk);
      logger.info("temp: " + system.getTemperature());
      // system.setPressure(-Math.log(funk)/(deriv/funk)+system.getPressure());
      system.setTemperature(-(funk - 1) / deriv + system.getTemperature());
    } while ((Math.abs(ytotal - 1) > 1e-10) && (iterations < maxNumberOfIterations));
    if (Math.abs(ytotal - 1.0) >= 1e-5
        || ktot < 1e-3 && system.getPhase(0).getNumberOfComponents() > 1) {
      setSuperCritical(true);
    }
    if (system.getPhase(0).getNumberOfComponents() == 1
        && Math.abs(system.getPhases()[1].getComponent(0).getFugacityCoefficient()
            / system.getPhases()[0].getComponent(0).getFugacityCoefficient() - 1.0) < 1e-20) {
      setSuperCritical(true);
    }
    if (isSuperCritical()) {
      // throw new IllegalStateException("System is supercritical");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
