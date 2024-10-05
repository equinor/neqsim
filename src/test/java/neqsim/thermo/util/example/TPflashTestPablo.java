package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * TPflashTestPablo class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashTestPablo {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 35.0, 90.0);
    fluid.addComponent("water", 0.0078);
    fluid.addComponent("MEG", 0.0042);
    fluid.addComponent("nitrogen", 2.205);
    fluid.addComponent("CO2", 6.078);
    fluid.addComponent("methane", 81.479);
    fluid.addComponent("ethane", 4.455);
    fluid.addComponent("propane", 1.967);
    fluid.addTBPfraction("C6", 0.244, 85.2 / 1000, .6671);
    fluid.addTBPfraction("C7", 0.316, 91.9 / 1000, .7432);
    fluid.addTBPfraction("C8", 0.288, 104.8 / 1000, .7665);
    fluid.addTBPfraction("C9", 0.150, 120.1 / 1000, .7779);
    fluid.addTBPfraction("C10", 0.068, 133.5 / 1000, .7861);
    fluid.addTBPfraction("C11", 0.050, 147.0 / 1000, .7924);
    fluid.addTBPfraction("C12", 0.042, 159.9 / 1000, .8016);
    fluid.addTBPfraction("C13", 0.032, 173.9 / 1000, .8114);
    fluid.addTBPfraction("C14", 0.022, 188.9 / 1000, .8198);
    fluid.addTBPfraction("C15", 0.017, 203.8 / 1000, .8264);
    fluid.addTBPfraction("C16", 0.011, 217.4 / 1000, .8331);
    fluid.addTBPfraction("C17", 0.030, 269.9 / 1000, .8523);
    fluid.addTBPfraction("C18", 0.008, 299.5 / 1000, .8577);

    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid);
    try {
      testOps.TPflash();
      fluid.display();
    } catch (Exception ex) {
    }

    SystemInterface oilstream = fluid.phaseToSystem("aqueous");
    oilstream.setTotalFlowRate(0.013 * 1 * oilstream.getDensity(), "kg/hr");

    ThermodynamicOperations testOps3 = new ThermodynamicOperations(oilstream);
    try {
      testOps3.TPflash();
      oilstream.display();
    } catch (Exception ex) {
    }

    SystemInterface newstream = fluid.phaseToSystem("gas");
    newstream.setTotalFlowRate(1.0, "MSm^3/hr");
    newstream.setTemperature(35.1, "C");
    ThermodynamicOperations testOps4 = new ThermodynamicOperations(newstream);
    try {
      testOps4.TPflash();
      newstream.display();
    } catch (Exception ex) {
    }

    newstream.addFluid(oilstream);
    newstream.init(0);
    ThermodynamicOperations testOps2 = new ThermodynamicOperations(newstream);

    try {
      testOps2.TPflash();
      newstream.display();
    } catch (Exception ex) {
    }
  }
}
