package neqsim.processsimulation.util.example;

import neqsim.processsimulation.processequipment.distillation.DistillationColumn;
import neqsim.processsimulation.processequipment.stream.Stream;

/**
 * <p>
 * destillation2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class destillation2 {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 30.0), 50.00);
    testSystem.addComponent("methane", 1.00);
    testSystem.addComponent("water", 100e-6);
    testSystem.addComponent("TEG", 0.0);

    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);

    Stream feedGas = new Stream("feedGas", testSystem);
    feedGas.setFlowRate(5.0, "MSm3/day");
    feedGas.setTemperature(30.0, "C");
    feedGas.setPressure(50.0, "bara");

    neqsim.thermo.system.SystemInterface TEGliq2 = testSystem.clone();
    TEGliq2.setMolarComposition(new double[] {0.0, 0.001, 1.0});

    Stream TEGliq = new Stream("TEG liq", TEGliq2);
    TEGliq.setFlowRate(5000.0, "kg/hr");
    TEGliq.setTemperature(30.0, "C");
    TEGliq.setPressure(50.0, "bara");

    DistillationColumn column = new DistillationColumn("distColumn", 3, false, false);
    column.addFeedStream(feedGas, 0);
    column.getTray(2).addStream(TEGliq);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(feedGas);
    operations.add(TEGliq);
    operations.add(column);
    operations.run();

    column.getGasOutStream().displayResult();
    column.getLiquidOutStream().displayResult();
  }
}
