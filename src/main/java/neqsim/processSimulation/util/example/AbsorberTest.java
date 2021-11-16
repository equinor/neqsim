package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.stream.Stream;

public class AbsorberTest {
        /**
         * This method is just meant to test the thermo package.
         */
        public static void main(String args[]) {
                neqsim.thermo.system.SystemFurstElectrolyteEos testSystem =
                                new neqsim.thermo.system.SystemFurstElectrolyteEos((273.15 + 80.0),
                                                50.00);
                testSystem.addComponent("methane", 120.00);
                testSystem.addComponent("CO2", 20.0);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);

                Stream stream_Hot = new Stream("Stream1", testSystem);

                neqsim.processSimulation.processEquipment.absorber.SimpleAbsorber absorber1 =
                                new neqsim.processSimulation.processEquipment.absorber.SimpleAbsorber(
                                                stream_Hot);
                absorber1.setAproachToEquilibrium(0.75);
                absorber1.setName("absorber");

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();
                operations.add(stream_Hot);
                operations.add(absorber1);

                operations.run();
                operations.displayResult();
        }
}
