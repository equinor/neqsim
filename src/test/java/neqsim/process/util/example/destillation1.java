package neqsim.process.util.example;

import neqsim.process.equipment.distillation.Condenser;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.Reboiler;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * destillation1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class destillation1 {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 63.0), 16.00);
    // testSystem.addComponent("methane", 1.00);
    testSystem.addComponent("ethane", 0.002);
    // testSystem.addComponent("CO2", 10.00);
    testSystem.addComponent("propane", 0.605900);
    testSystem.addComponent("i-butane", 0.1473);
    testSystem.addComponent("n-butane", 0.2414);
    testSystem.addComponent("i-pentane", 0.00322);
    testSystem.addComponent("n-pentane", 0.0002);
    testSystem.addComponent("methanol", 0.00005);
    // testSystem.addComponent("n-heptane", 100.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.display();

    Stream stream_1 = new Stream("Stream1", testSystem);

    DistillationColumn column = new DistillationColumn("distColumn", 9, true, true);
    column.addFeedStream(stream_1, 4);
    ((Reboiler) column.getReboiler()).setRefluxRatio(3.7);
    ((Condenser) column.getCondenser()).setRefluxRatio(10.7);
    // column.setReboilerTemperature(360);
    // column.setReboilerTemperature(300);
    /*
     * Heater heater = new Heater((Stream) column.getGasOutStream()); heater.setdT(-15.0);
     *
     * DistillationColumn column2 = new DistillationColumn(4, true, true);
     * column2.addFeedStream(heater.getOutStream(), 2); ((Reboiler)
     * column2.getReboiler()).setRefluxRatio(0.01); ((Condenser)
     * column2.getCondenser()).setRefluxRatio(0.01);
     */

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(column);
    // operations.add(heater);
    // operations.add(column2);

    operations.run();
    // column.getReboiler().displayResult();
    // column.getCondenser().displayResult();
    // operations.displayResult();
  }
}
