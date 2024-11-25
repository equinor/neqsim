package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * dewPointTemperatureFlashDer class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class DewPointTemperatureFlashDer extends ConstantDutyTemperatureFlash {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for dewPointTemperatureFlashDer.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public DewPointTemperatureFlashDer(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.getPhase(0).getNumberOfComponents() == 1
        && system.getPressure() > system.getPhase(0).getComponent(0).getPC()) {
      setSuperCritical(true);
    }

    // System.out.println("starting");
    system.init(0);
    system.setBeta(0, 1.0 - 1e-15);
    system.setBeta(1, 1e-15);
    system.init(1);
    system.setNumberOfPhases(2);

    double oldTemp = 0;
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(0);
      system.getChemicalReactionOperations().solveChemEq(1);
    }

    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      system.getPhase(0).getComponent(i).setx(system.getPhase(0).getComponent(i).getz());
      if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
        system.getPhase(0).getComponent(i).setx(1e-40);
      } else {
        if (system.getPhase(1).getComponent(i).getName().equals("water")) {
          system.getPhase(1).getComponent(i).setx(1.0);
        } else if (system.getPhase(1).hasComponent("water")) {
          system.getPhase(1).getComponent(i).setx(1.0e-10);
        } else {
          system.getPhase(1).getComponent(i).setx(1.0 / system.getPhase(0).getComponent(i).getK()
              * system.getPhase(1).getComponent(i).getz());
        }
      }
    }

    // system.setPressure(system.getPhase(0).getAntoineVaporPressure(system.getTemperature()));
    double xtotal = 0.0;
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      xtotal += system.getPhase(1).getComponent(i).getx();
    }
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      system.getPhase(1).getComponent(i).setx(system.getPhase(1).getComponent(i).getx() / xtotal);
    }

    int iterations = 0;
    int maxNumberOfIterations = 1000;
    double ktot = 0.0;
    do {
      oldTemp = system.getTemperature();
      iterations++;
      system.init(2);

      xtotal = 0.0;
      double dfdT = 0.0;
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        xtotal += 1.0 / system.getPhase(0).getComponent(i).getK()
            * system.getPhase(1).getComponent(i).getz();
        dfdT -= 1.0 / system.getPhase(0).getComponent(i).getK()
            * system.getPhase(1).getComponent(i).getz()
            * (system.getPhase(1).getComponent(i).getdfugdt()
                - system.getPhase(0).getComponent(i).getdfugdt());
      }
      double f = xtotal - 1.0;
      // fold = f;

      // System.out.println("x" + xtotal);
      // oldTemperature = system.getTemperature();

      if (iterations < 5) {
        system.setTemperature(system.getTemperature() + iterations / (iterations + 100.0)
            * (xtotal * system.getTemperature() - system.getTemperature()));
      } else {
        system
            .setTemperature(system.getTemperature() - iterations / (10.0 + iterations) * f / dfdT);
      }
      // System.out.println("temperature " + system.getTemperature());

      system.init(1);

      ktot = 0.0;
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
          system.getPhase(0).getComponent(i).setK(1e-40);
        } else {
          system.getPhase(0).getComponent(i)
              .setK(Math.exp(system.getPhase(1).getComponent(i).getLogFugacityCoefficient()
                  - system.getPhase(0).getComponent(i).getLogFugacityCoefficient()));
        }
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
        system.getPhase(1).getComponent(i).setx(1.0 / system.getPhase(0).getComponent(i).getK()
            * system.getPhase(1).getComponent(i).getz());
        ktot += Math.abs(system.getPhase(1).getComponent(i).getK() - 1.0);
      }
      // system.init_x_y();

      xtotal = 0.0;
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        xtotal += system.getPhase(1).getComponent(i).getx();
      }
      // System.out.println("xtotal " + xtotal);
      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        system.getPhase(1).getComponent(i).setx(system.getPhase(1).getComponent(i).getx() / xtotal);
      }
    } while (((Math.abs(xtotal - 1.0) > 1e-6)
        || Math.abs(oldTemp - system.getTemperature()) / oldTemp > 1e-4)
        || iterations < 3 && (iterations < maxNumberOfIterations));
    if (Math.abs(xtotal - 1.0) > 1e-5
        || ktot < 1.0e-3 && system.getPhase(0).getNumberOfComponents() > 1) {
      setSuperCritical(true);
    }
    if (ktot < 1.0e-3) {
      if (system.getTemperature() < 90.0) {
        setSuperCritical(true);
      } else {
        setSuperCritical(false);
        // system.setTemperature(system.getTemperature() - 10.0);
        // run();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
