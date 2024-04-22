package neqsim.thermodynamicOperations.flashOps.saturationOps;



import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * waterDewPointTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class waterDewPointTemperatureFlash extends constantDutyTemperatureFlash {
  private static final long serialVersionUID = 1000;
  

  /**
   * <p>
   * Constructor for waterDewPointTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public waterDewPointTemperatureFlash(SystemInterface system) {
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
        system
            .setTemperature(system.getPhases()[0].getComponents()[k].getMeltingPointTemperature());
        for (int l = 0; l < system.getPhases()[0].getNumberOfComponents(); l++) {
          system.getPhase(1).getComponent(l).setx(1e-30);
          // 
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
          funk = system.getPhases()[0].getComponents()[k].getz();

          funk -= system.getPhases()[0].getBeta()
              * system.getPhases()[1].getComponents()[k].getFugacityCoefficient()
              / system.getPhases()[0].getComponents()[k].getFugacityCoefficient();

          // 
          /*
           * deriv -= system.getPhases()[0].getBeta()
           * (system.getPhases()[1].getComponents()[k].getFugacityCoefficient()
           * system.getPhases()[0].getComponents()[k].getdfugdt() * -1.0 /
           * Math.pow(system.getPhases()[0].getComponents()[k] .getFugacityCoefficient(), 2.0) +
           * system.getPhases()[1].getComponents()[k].getdfugdt() /
           * system.getPhases()[i].getComponents()[k] .getFugacityCoefficient());
           *
           * system.setTemperature(system.getTemperature() - funk/deriv);
           */

          system.setTemperature(system.getTemperature() + 100.0 * funk);

          // 
          // if(system.getPhase(0).getComponent(k).getComponentName().equals("MEG"))
          // 
        } while (Math.abs(funk) >= 0.0000001 && iterations < maxNumberOfIterations);

        // 
        if (system.getTemperature() < minTemperature) {
          minTemperature = system.getTemperature();
        }
        if (system.getTemperature() > maxTemperature) {
          maxTemperature = system.getTemperature();
        }
      }
    }
    system.setTemperature(maxTemperature);
    // 
    // 
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
