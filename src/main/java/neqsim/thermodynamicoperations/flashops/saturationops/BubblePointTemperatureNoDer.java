package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * bubblePointTemperatureNoDer class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubblePointTemperatureNoDer extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubblePointTemperatureNoDer.class);

  /**
   * <p>
   * Constructor for bubblePointTemperatureNoDer.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BubblePointTemperatureNoDer(SystemInterface system) {
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
    int maxNumberOfIterations = 1000;
    double yold = 0;
    double ytotal = 1;
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(1, 1.0 - 1e-10);
    system.setBeta(0, 1e-10);
    // need to fix this close to critical point
    if (system.getPhase(0).getNumberOfComponents() == 1
        && system.getPressure() < system.getPhase(0).getComponent(0).getPC()) {
      system.setTemperature(
          system.getPhase(0).getComponent(0).getAntoineVaporTemperature(system.getPressure()));
    }

    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(0);
      system.getChemicalReactionOperations().solveChemEq(1);
    }

    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[1].getComponent(i).setx(system.getPhases()[0].getComponent(i).getz());
      if (system.getPhases()[0].getComponent(i).getIonicCharge() != 0) {
        system.getPhases()[0].getComponent(i).setx(1e-40);
      } else {
        system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
            * system.getPhases()[1].getComponent(i).getz());
      }
    }
    ytotal = 0.0;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      ytotal += system.getPhases()[0].getComponent(i).getx();
    }
    double oldTemp = 10.0;

    double ktot = 0.0;
    do {
      iterations++;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        system.getPhases()[0].getComponent(i)
            .setx(system.getPhases()[0].getComponent(i).getx() / ytotal);
      }
      if (system.isChemicalSystem() && (iterations % 2) == 0) {
        system.getChemicalReactionOperations().solveChemEq(1);
      }
      system.init(1);
      oldTemp = system.getTemperature();
      ktot = 0.0;
      for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
        do {
          yold = system.getPhases()[0].getComponent(i).getx();
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            system.getPhases()[0].getComponent(i).setK(1e-40);
          } else {
            system.getPhases()[0].getComponent(i).setK(
                Math.exp(Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient())
                    - Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())));
          }
          system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
          system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
              * system.getPhases()[1].getComponent(i).getz());
          // logger.info("y err " +
          // Math.abs(system.getPhases()[0].getComponent(i).getx()-yold));
        } while (Math.abs(system.getPhases()[0].getComponent(i).getx() - yold) > 1e-4);

        ktot += Math.abs(system.getPhases()[1].getComponent(i).getK() - 1.0);
      }
      ytotal = 0.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        ytotal += system.getPhases()[0].getComponent(i).getx();
      }
      if (ytotal > 1.2) {
        ytotal = 1.2;
      }
      if (ytotal < 0.8) {
        ytotal = 0.8;
      }
      // logger.info("y tot " + ytotal);

      // system.setTemperature(system.getTemperature() +
      // 0.05*(1.0/ytotal*system.getTemperature()-system.getTemperature()));
      system
          .setTemperature(system.getTemperature() + 0.1 * system.getTemperature() * (1.0 - ytotal));
      // for (int i=0;i<system.getPhases()[1].getNumberOfComponents();i++){
      // system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getx()
      // + 0.5 *
      // (system.getPhases()[0].getComponent(i).getK()*system.getPhases()[1].getComponent(i).getx()
      // * 1.0 / ytotal-system.getPhases()[0].getComponent(i).getx()));
      // }
      // logger.info("temperature " + system.getTemperature());
    } while ((((Math.abs(ytotal) - 1.0) > 1e-9)
        || Math.abs(oldTemp - system.getTemperature()) / oldTemp > 1e-8)
        && (iterations < maxNumberOfIterations));
    // logger.info("iter " + iterations + " ktot " + ktot);
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
      throw new IllegalStateException("System is supercritical");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
