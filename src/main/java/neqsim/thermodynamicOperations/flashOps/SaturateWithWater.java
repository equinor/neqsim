package neqsim.thermodynamicOperations.flashOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * SaturateWithWater class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class SaturateWithWater extends QfuncFlash {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(SaturateWithWater.class);

  Flash tpFlash;

  /**
   * <p>
   * Constructor for SaturateWithWater.
   * </p>
   */
  public SaturateWithWater() {}

  /**
   * <p>
   * Constructor for SaturateWithWater.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturateWithWater(SystemInterface system) {
    this.system = system;
    this.tpFlash = new TPflash(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (!system.getPhase(0).hasComponent("water")) {
      system.addComponent("water", system.getTotalNumberOfMoles());
      system.createDatabase(true);
      system.setMixingRule(system.getMixingRule());
      if (system.doMultiPhaseCheck()) {
        system.setMultiPhaseCheck(true);
      }
      system.init(0);
    }
    double dn = 1.0;
    int i = 0;

    this.tpFlash = new TPflash(system);
    tpFlash.run();
    boolean hasAq = false;
    if (system.hasPhaseType("aqueous")) {
      hasAq = true;
    }
    double lastdn = 0.0;
    if (system.hasPhaseType("aqueous")) {
      lastdn = system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase();
    } else {
      lastdn = system.getPhase(0).getNumberOfMolesInPhase() / 100.0;
    }

    do {
      i++;

      if (!hasAq) {
        system.addComponent("water", lastdn * 0.5);
        lastdn *= 0.8;
      } else {
        lastdn = system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase();
        dn = lastdn / system.getNumberOfMoles();
        system.addComponent("water", -lastdn);
      }
      tpFlash.run();
      // system.display();
      hasAq = system.hasPhaseType("aqueous");
    } while ((i < 50 && Math.abs(dn) > 1e-6) || !hasAq && i < 50);
    if (i == 50) {
      logger.error("could not find solution - in water sturate : dn  " + dn);
    }
    // logger.info("i " + i + " dn " + dn);
    // System.out.println("i " + i + " dn " + dn) ; // system.display();
    system.removePhase(system.getNumberOfPhases() - 1);
    tpFlash.run();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 70.0, 150.0);

    testSystem.addComponent("methane", 75.0);
    testSystem.addComponent("ethane", 7.5);
    testSystem.addComponent("propane", 4.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-butane", 0.6);
    testSystem.addComponent("n-hexane", 0.3);
    testSystem.addPlusFraction("C6", 1.3, 100.3 / 1000.0, 0.8232);
    // testSystem.addComponent("water", 0.3);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.TPflash();
      // testSystem.display();
      testOps.saturateWithWater();
      // testSystem.display();
      // testSystem.addComponent("water", 1);
      // testOps.saturateWithWater();
      testSystem.display();
      // testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // testSystem.display();
  }
}
