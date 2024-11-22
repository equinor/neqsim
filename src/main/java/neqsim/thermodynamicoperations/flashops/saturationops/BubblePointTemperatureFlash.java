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
  private static final long serialVersionUID = 1000;
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
    int iterations = 0;
    int maxNumberOfIterations = 10000;
    double yold = 0;
    double ytotal = 1;
    double deriv = 0;

    double funk = 0;
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      system.getPhase(1).getComponent(i).setx(system.getPhase(0).getComponent(i).getz());
      system.getPhase(0).getComponent(i).setx(
          system.getPhase(0).getComponent(i).getK() * system.getPhase(1).getComponent(i).getx());
    }
    system.setNumberOfPhases(2);
    do {
      system.setTemperature((system.getTemperature() + system.getTemperature() / ytotal) / 10);
      // logger.info("temp . " + system.getTemperature());
      funk = 0;
      deriv = 0;
      ytotal = 0;
      system.init(2);
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        do {
          iterations++;

          yold = system.getPhase(0).getComponent(i).getx();
          system.getPhase(0).getComponent(i)
              .setK(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                  / system.getPhase(0).getComponent(i).getFugacityCoefficient());
          system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          system.getPhase(0).getComponent(i)
              .setx(system.getPhase(1).getComponent(i).getx()
                  * system.getPhase(1).getComponent(i).getFugacityCoefficient()
                  / system.getPhase(0).getComponent(i).getFugacityCoefficient());
        } while ((Math.abs(yold - system.getPhase(1).getComponent(i).getx()) > 1e-10)
            && (iterations < maxNumberOfIterations));

        ytotal += system.getPhase(0).getComponent(i).getx();
        funk +=
            system.getPhase(1).getComponent(i).getx() * system.getPhase(1).getComponent(i).getK();
        deriv +=
            system.getPhase(1).getComponent(i).getx() * system.getPhase(1).getComponent(i).getK()
                * (system.getPhase(1).getComponent(i).getdfugdt()
                    - system.getPhase(0).getComponent(i).getdfugdt());
      }

      // logger.info("FUNK: " + funk);
      logger.info("temp: " + system.getTemperature());
      // system.setPressure(-Math.log(funk)/(deriv/funk)+system.getPressure());
      system.setTemperature(-(funk - 1) / deriv + system.getTemperature());
    } while ((Math.abs(ytotal - 1) > 1e-10) && (iterations < maxNumberOfIterations));
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
