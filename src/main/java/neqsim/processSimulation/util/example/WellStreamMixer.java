package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 */
public class WellStreamMixer {
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(354.2, 120.0);

                neqsim.thermo.system.SystemInterface testSystem2 =
                                (SystemInterface) testSystem.clone();
                testSystem2.createDatabase(true);
                testSystem2.setMixingRule(2);
                testSystem2.setMultiPhaseCheck(true);
                testSystem2.setMolarComposition(new double[] {1.0, 1.42, 70.1, 8, 3.54, 0.54, 0.2,
                                0.21, 0.19, 0.28, 5.0, 0.0, 0.0, 1, 1});

                // testSystem.renameComponent("nitrogen", "nitrogen_well1");
                // testSystem.setComponentNameTagOnNormalComponents("_well1");
                // testSystem2.setComponentNameTag("_well2");

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

                Stream mixerdStream = new Stream(wellStramMixer.getOutStream());
                mixerdStream.setName("mixed stream");

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();
                operations.add(wellStream_1);
                operations.add(wellStream_2);
                operations.add(wellStramMixer);
                operations.add(mixerdStream);

        Stream mixerdStream = new Stream(wellStramMixer.getOutStream());
        mixerdStream.setName("mixed stream");

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
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
