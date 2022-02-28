package neqsim.processSimulation.util.example;

import neqsim.processSimulation.measurementDevice.TemperatureTransmitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>TestOnlineProcess class.</p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestOnlineProcess {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
        testSystem.addComponent("methane", 0.900);
        testSystem.addComponent("ethane", 0.100);
        testSystem.addComponent("n-heptane", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);

        TemperatureTransmitter temperatureTransmitter = new TemperatureTransmitter(stream_1);
        temperatureTransmitter.setIsOnlineSignal(true, "Karsto", "21TI1117");

        ThrottlingValve valve_1 = new ThrottlingValve("valve_1", stream_1);
        valve_1.setOutletPressure(5.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(temperatureTransmitter);
        operations.add(valve_1);

        operations.run();

        operations.displayResult();
        operations.reportMeasuredValues();
    }
}
