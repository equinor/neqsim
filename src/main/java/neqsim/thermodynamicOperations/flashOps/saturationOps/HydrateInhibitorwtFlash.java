package neqsim.thermodynamicOperations.flashOps.saturationOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HydrateInhibitorwtFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HydrateInhibitorwtFlash extends constantDutyTemperatureFlash {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(HydrateInhibitorwtFlash.class);

  double wtfrac = 0.5;
  String inhibitor = "MEG";

  /**
   * <p>
   * Constructor for HydrateInhibitorwtFlash.
   * </p>
   */
  public HydrateInhibitorwtFlash() {}

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
          double correction = newC * 0.5; // (newC -
                                          // system.getPhase(0).getComponent(inhibitor).getNumberOfmoles())
                                          // *
                                          // 0.5;

          system.addComponent(inhibitor, correction);
        }
        system.init(0);
        system.init(1);
        ops.TPflash();
        double wtp = 0.0;
        if (system.hasPhaseType("aqueous")) {
          wtp = system.getPhase("aqueous").getComponent(inhibitor).getx()
              * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass()
              / (system.getPhase("aqueous").getComponent(inhibitor).getx()
                  * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass()
                  + system.getPhase("aqueous").getComponent("water").getx()
                      * system.getPhase("aqueous").getComponent("water").getMolarMass());
        } else {
          system.addComponent(inhibitor, system.getTotalNumberOfMoles());
          ops.TPflash();
          wtp = system.getPhase("aqueous").getComponent(inhibitor).getx()
              * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass()
              / (system.getPhase("aqueous").getComponent(inhibitor).getx()
                  * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass()
                  + system.getPhase("aqueous").getComponent("water").getx()
                      * system.getPhase("aqueous").getComponent("water").getMolarMass());
        }
        error = -(wtp - wtfrac);

        logger.info("error " + error);
      } catch (Exception ex) {
        logger.error("error", ex);
      }
    } while ((Math.abs(error) > 1e-5 && iter < 100) || iter < 3);
    // system.display();
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 100.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("nitrogen", 79.0);
    testSystem.addComponent("oxygen", 21.0);
    // testSystem.addComponent("ethane", 0.10);
    // testSystem.addComponent("propane", 0.050);
    // testSystem.addComponent("i-butane", 0.0050);
    testSystem.addComponent("MEG", 0.000001);
    testSystem.addComponent("water", 0.0010);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);

    testSystem.init(0);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);

    try {
      testOps.hydrateInhibitorConcentrationSet("MEG", 0.99);
      double cons = 100 * testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
          * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
          / (testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
              * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
              + testSystem.getPhase(0).getComponent("water").getNumberOfmoles()
                  * testSystem.getPhase(0).getComponent("water").getMolarMass());
      logger.info("hydrate inhibitor concentration " + cons + " wt%");
    } catch (Exception ex) {
      ex.toString();
    }
    testSystem.display();
  }
}
