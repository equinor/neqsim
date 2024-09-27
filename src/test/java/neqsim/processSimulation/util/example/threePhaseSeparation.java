package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.MoleFractionControllerUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>
 * threePhaseSeparation class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class threePhaseSeparation {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface system1 =
        new neqsim.thermo.system.SystemSrkCPAs((273.15 + 15.0), 80.00);
    system1.addComponent("CO2", 0.309);
    system1.addComponent("nitrogen", 1.854);
    system1.addComponent("methane", 94.90446);
    system1.addComponent("ethane", 1.623);
    system1.addComponent("propane", 0.535);
    system1.addComponent("i-butane", 0.111293);
    system1.addComponent("n-butane", 0.1547122);
    system1.addComponent("iC5", 0.05894036);
    system1.addComponent("n-pentane", 0.04441738);
    system1.addComponent("benzene", 0.001207753);
    system1.addComponent("toluene", 0.002350627);
    system1.addComponent("m-Xylene", 0.00359331);
    system1.addTBPfraction("C6", 0.04242109, 85.11 / 1000.0, 0.724);
    system1.addTBPfraction("C7", 0.05719361, 98.4 / 1000.0, 0.751);
    system1.addTBPfraction("C8", 0.03807916, 111.74 / 1000.0, 0.779);
    system1.addTBPfraction("C9", 0.0203721, 125.19 / 1000.0, 0.793);
    system1.addTBPfraction("C10", 0.01497714, 137.83 / 1000.0, 0.798);
    system1.addTBPfraction("C11", 0.00929271, 149.0 / 1000.0, 0.803);
    system1.addTBPfraction("C12", 0.00619347, 163.0 / 1000.0, 0.809);
    system1.addTBPfraction("C13", 0.004102369, 176.0 / 1000.0, 0.815);
    system1.addTBPfraction("C14", 0.002625117, 191.0 / 1000.0, 0.824);
    system1.addTBPfraction("C15", 0.00168187, 207.0 / 1000.0, 0.829);
    system1.addTBPfraction("C16", 0.001092967, 221.0 / 1000.0, 0.843);
    system1.addTBPfraction("C17", 0.0006937096, 237.0 / 1000.0, 0.848);
    system1.addTBPfraction("C18", 0.0004341923, 249.0 / 1000.0, 0.852);
    system1.addTBPfraction("C19", 0.000329387, 261.0 / 1000.0, 0.855);
    system1.addTBPfraction("CN1", 0.0008534124, 304.7 / 1000.0, 0.865);
    system1.addTBPfraction("CN2", 5.340066E-005, 432.55 / 1000.0, 0.88);
    system1.addComponent("TEG", 1.0e-10);
    system1.addComponent("MEG", .3);
    system1.addComponent("water", 1.0);

    system1.createDatabase(true);
    system1.setMixingRule(9);
    system1.setMultiPhaseCheck(true);
    Stream stream_1 = new Stream("Stream1", system1);

    ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator", stream_1);

    Stream stream_2 = new Stream("gas from scrubber", separator.getGasOutStream());
    // Stream stream_3 = new Stream("oil from scrubber", separator.getOilOutStream());
    // Stream stream_4 = new Stream("water from scrubber", separator.getWaterOutStream());

    MoleFractionControllerUtil waterRemoval = new MoleFractionControllerUtil(stream_2);
    // waterRemoval.setMoleFraction("water", 15.0e-6);
    waterRemoval.setComponentRate("TEG", 55.0, "litre/MSm^3");
    // werRemoval.setRelativeMoleFractionReduction("water", -0.99);
    // waterRemoval.getOutStream();

    MoleFractionControllerUtil TEGsaturator =
        new MoleFractionControllerUtil(waterRemoval.getOutletStream());
    TEGsaturator.setMoleFraction("water", 5.0e-6);
    // TEGsaturator.getOutStream();

    ThrottlingValve LP_valve = new ThrottlingValve("LPventil", TEGsaturator.getOutletStream());
    LP_valve.setOutletPressure(5.0);

    // ThreePhaseSeparator separator2 = new ThreePhaseSeparator("Separator LP",
    // LP_valve.getOutStream());

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(separator);
    // operations.add(stream_2);
    // operations.add(stream_3);
    // operations.add(stream_4);
    // operations.add(waterRemoval);
    // operations.add(TEGsaturator);
    // operations.add(LP_valve);
    // operations.add(separator2);

    operations.run();
    operations.displayResult();
    // stream_1.displayResult();
    // waterRemoval.displayResult();
  }
}
