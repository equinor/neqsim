package neqsim.fluidMechanics.util.example;

import neqsim.processSimulation.processEquipment.pipeline.Pipeline;
import neqsim.processSimulation.processEquipment.pipeline.TwoPhasePipeLine;
import neqsim.processSimulation.processEquipment.stream.NeqStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class WettedWall_CO2_water {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos((273.15 + 5.0), 45.00);
        testSystem.addComponent("methane", 0.15, 0);
        // testSystem.addComponent("CO2", 0.05, 1);
        testSystem.addComponent("n-heptane", 0.5, 1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream1 = new NeqStream(testSystem);

        Pipeline pipe = new TwoPhasePipeLine(stream1);

        pipe.setOutputFileName("c:/tempNew2.nc");
        pipe.setInitialFlowPattern("annular");

        int numberOfLegs = 1, numberOfNodesInLeg = 500;
        double[] legHeights = {0, 0};
        double[] legPositions = {0.0, 1.5};
        double[] pipeDiameters = {0.02507588, 0.02507588};
        double[] outerTemperature = {295.0, 295.0};
        double[] pipeWallRoughness = {1.0e-5, 1.0e-5};
        pipe.setNumberOfLegs(numberOfLegs);
        pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
        pipe.setLegPositions(legPositions);
        pipe.setHeightProfile(legHeights);
        pipe.setPipeDiameters(pipeDiameters);
        pipe.setPipeWallRoughness(pipeWallRoughness);
        pipe.setOuterTemperatures(outerTemperature);
        pipe.setEquilibriumMassTransfer(false);
        pipe.setEquilibriumHeatTransfer(false);

        ProcessSystem operations = new ProcessSystem();
        operations.add(stream1);
        operations.add(pipe);

        operations.run();
        operations.displayResult();
    }
}
