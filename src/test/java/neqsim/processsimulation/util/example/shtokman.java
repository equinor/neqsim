package neqsim.processsimulation.util.example;

import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.stream.StreamInterface;

/**
 * <p>
 * shtokman class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class shtokman {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkCPAs((273.15 + 35.0), 135.00);

    // testSystem.addComponent("MEG", 30.0);
    testSystem.addComponent("CO2", 10.44);
    testSystem.addComponent("methane", 0.1);
    // testSystem.addComponent("ethane", 2.0);

    testSystem.addComponent("TEG", 11.0);

    testSystem.addComponent("water", 0.101);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(7);

    // ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    // ops.TPflash();
    // testSystem.display();
    Stream stream_1 = new Stream("Stream1", testSystem);

    Separator separator = new Separator("Separator 1", stream_1);
    StreamInterface stream_2 = separator.getGasOutStream();

    neqsim.processsimulation.processequipment.heatexchanger.Heater heater =
        new neqsim.processsimulation.processequipment.heatexchanger.Heater("heater", stream_2);
    heater.setOutTemperature(273.15 + 35);
    heater.setPressureDrop(134);
    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(separator);
    operations.add(stream_2);
    operations.add(heater);
    operations.run();
    operations.displayResult();

    // stream_2.getThermoSystem().setPressure(30.0);
    // stream_2.getThermoSystem().setTemperature(273.15 - 0);
    // stream_2.getThermoSystem().setHydrateCheck(true);
    // stream_2.getThermoSystem().init(0);
    // ThermodynamicOperations ops = new ThermodynamicOperations(stream_2.getThermoSystem());
    /*
     * try { // ops.TPflash();
     *
     * ops.waterPrecipitationTemperature(); stream_2.getThermoSystem().display();
     * stream_2.getThermoSystem().init(0); ops.hydrateFormationTemperature(2);
     * stream_2.getThermoSystem().display(); // stream_2.getThermoSystem().display(); //
     * stream_2.getThermoSystem().setTemperature(250.0); // ops.dewPointTemperatureFlash(); } catch
     * (Exception ex) { logger.error(ex.getMessage(), ex); } double wtMEG =
     * stream_2.getThermoSystem().getPhase(1).getComponent("MEG").getx()*stream_2.
     * getThermoSystem().getPhase(1).getComponent("MEG").getMolarMass(); double wtwater =
     * stream_2.getThermoSystem().getPhase(1).getComponent("water").getx()*stream_2.
     * getThermoSystem().getPhase(1).getComponent("water").getMolarMass();
     *
     * System.out.println("wt% MEG " + wtMEG/(wtMEG+wtwater)*100); // operations.displayResult();
     */
  }
}
