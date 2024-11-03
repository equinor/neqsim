package neqsim.processsimulation.util.example;

import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * WellStreamMixer class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class WellStreamMixer {
    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(354.2, 120.0);

        testSystem.addComponent("nitrogen", 1.0);
        testSystem.addComponent("CO2", 1.42);
        testSystem.addComponent("methane", 60.88);
        testSystem.addComponent("ethane", 8.07);
        testSystem.addComponent("propane", 3.54);
        testSystem.addComponent("i-butane", 0.54);
        testSystem.addComponent("n-butane", 0.2);
        testSystem.addComponent("i-pentane", 0.21);
        testSystem.addComponent("n-pentane", 0.19);
        testSystem.addComponent("n-hexane", 0.28);
        testSystem.addComponent("n-heptane", 5.0);
        testSystem.addTBPfraction("C7_well1", 1.0, 100.0 / 1000.0, 0.8);
        testSystem.addTBPfraction("C8_well1", 1.0, 120.0 / 1000.0, 0.81);
        testSystem.addTBPfraction("C7_well2", 1.0, 110.0 / 1000.0, 0.8);
        testSystem.addTBPfraction("C8_well2", 1.0, 122.0 / 1000.0, 0.81);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);

        testSystem.setMolarComposition(new double[] {1.0, 1.42, 70.1, 8, 3.54, 0.54, 0.2, 0.21,
                0.19, 0.28, 5.0, 1.0, 1.0, 0, 0});

        neqsim.thermo.system.SystemInterface testSystem2 = testSystem.clone();
        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);
        testSystem2.setMultiPhaseCheck(true);
        testSystem2.setMolarComposition(new double[] {1.0, 1.42, 70.1, 8, 3.54, 0.54, 0.2, 0.21,
                0.19, 0.28, 5.0, 0.0, 0.0, 1, 1});

        Stream wellStream_1 = new Stream("well stream", testSystem);
        wellStream_1.setFlowRate(14.23, "MSm3/day");
        wellStream_1.setTemperature(40.0, "C");
        wellStream_1.setPressure(120.0, "bara");

        Stream wellStream_2 = new Stream("well stream2", testSystem2);
        wellStream_2.setFlowRate(0.23, "MSm3/day");
        wellStream_2.setTemperature(40.0, "C");
        wellStream_2.setPressure(120.0, "bara");

        Mixer wellStramMixer = new StaticMixer("well mixer");
        wellStramMixer.addStream(wellStream_1);
        wellStramMixer.addStream(wellStream_2);

        Stream mixerdStream = new Stream("mixed stream", wellStramMixer.getOutletStream());

        neqsim.process.processmodel.ProcessSystem operations =
                new neqsim.process.processmodel.ProcessSystem();
        operations.add(wellStream_1);
        operations.add(wellStream_2);
        operations.add(wellStramMixer);
        operations.add(mixerdStream);

        operations.run();
        operations.save("c:/temp/wellMixer1.neqsim");
        // wellStream_1.displayResult();
        // wellStream_2.displayResult();
        mixerdStream.displayResult();
    }
}
