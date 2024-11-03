/*
 * LNGfilling.java
 *
 * Created on 6. september 2006, 14:46
 */

package neqsim.process.util.example;

import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;

/**
 * <p>
 * LNGfilling class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGfilling {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 - 163.0), 1.000);
    testSystem.addComponent("methane", 0.6);
    testSystem.addComponent("nitrogen", 1.0e-10);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 1.00);
    testSystem2.addComponent("methane", 1.0e-10);
    testSystem2.addComponent("nitrogen", 1.6e2);
    testSystem2.createDatabase(true);
    testSystem2.setMixingRule(2);

    Stream stream_1 = new Stream("Methane Stream", testSystem);
    Stream stream_2 = new Stream("Nitrogen Stream", testSystem2);

    Mixer mixer = new neqsim.process.equipment.mixer.StaticMixer("LNG Tank Mix");
    mixer.addStream(stream_1);
    mixer.addStream(stream_2);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(stream_2);
    operations.add(mixer);
    operations.run();
    operations.displayResult();

    System.out.println("Volume Methane " + stream_1.getThermoSystem().getVolume());
    System.out.println("Volume Nitrogen " + stream_2.getThermoSystem().getVolume());
    System.out
        .println("Mixer Cooled Nitrogen " + mixer.getOutletStream().getThermoSystem().getVolume());
  }
}
