package neqsim.process.util.example;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGfilling2 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGfilling2 {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 - 150.3), 1.02);
    testSystem.addComponent("nitrogen", 0.1e-6);
    testSystem.addComponent("methane", 90e-6);
    testSystem.addComponent("ethane", 5e-6);
    testSystem.addComponent("propane", 3e-6);
    testSystem.addComponent("i-butane", 3e-6);
    testSystem.addComponent("n-butane", 3e-6);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();

    Stream stream_1 = new Stream("Methane Stream", testSystem);

    Tank tank = new neqsim.process.equipment.tank.Tank("tank");
    tank.addStream(stream_1);

    StreamInterface liqstream = tank.getLiquidOutStream();

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(tank);
    operations.add(liqstream);
    operations.run();
    operations.displayResult();

    operations.setTimeStep(1);
    stream_1.getThermoSystem().setTotalNumberOfMoles(1e-10);
    tank.getGasOutStream().getThermoSystem().setTotalNumberOfMoles(1e-6);
    tank.getLiquidOutStream().getThermoSystem().setTotalNumberOfMoles(0.000001);

    operations.runTransient(1);
    // operations.displayResult();
  }
}
