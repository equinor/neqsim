package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>threePhaseSeparation_1 class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class threePhaseSeparation_1 {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAs((273.15 + 25.0),
                50.00);
        testSystem.addComponent("methane", 10.00);
        testSystem.addComponent("n-heptane", 1.0);
        testSystem.addComponent("water", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);

                operations.run();
                // stream_4.displayResult();
                // operations.displayResult();

                stream_2.getThermoSystem().display();
                ThermodynamicOperations ops =
                                new ThermodynamicOperations(stream_2.getThermoSystem());
                double volume = stream_2.getThermoSystem().getVolume();
                stream_2.getThermoSystem()
                                .setTemperature(stream_2.getThermoSystem().getTemperature() - 10.0);
                ops.TVflash(volume);
                stream_2.getThermoSystem().display();

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(separator);
        operations.add(stream_2);
        operations.add(stream_3);
        operations.add(stream_4);

        operations.run();
        // stream_4.displayResult();
        // operations.displayResult();

        stream_2.getThermoSystem().display();
        ThermodynamicOperations ops = new ThermodynamicOperations(stream_2.getThermoSystem());
        double volume = stream_2.getThermoSystem().getVolume();
        stream_2.getThermoSystem()
                .setTemperature(stream_2.getThermoSystem().getTemperature() - 10.0);
        ops.TVflash(volume);
        stream_2.getThermoSystem().display();

        stream_3.getThermoSystem().display();
        ThermodynamicOperations ops2 = new ThermodynamicOperations(stream_3.getThermoSystem());
        volume = stream_3.getThermoSystem().getVolume();
        stream_3.getThermoSystem()
                .setTemperature(stream_3.getThermoSystem().getTemperature() - 10.0);
        ops2.TVflash(volume);
        stream_3.getThermoSystem().display();
    }
}
