package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.stream.Stream;

public class AbsorberTest {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();
                operations.add(stream_Hot);
                operations.add(absorber1);

                operations.run();
                operations.displayResult();
        }
}
