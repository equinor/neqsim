package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.pipeline.Pipeline;
import neqsim.processSimulation.processEquipment.pipeline.TwoPhasePipeLine;
import neqsim.processSimulation.processEquipment.stream.NeqStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

public class processCO2MDEA {
    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, 10.01325);

        testSystem.addComponent("methane", 0.061152181, 0);
        testSystem.addComponent("CO2", 0.061152181, 0);
        testSystem.addComponent("water", 0.0862204876, 1);
        testSystem.addComponent("MDEA", 0.008, 1);

        testSystem.chemicalReactionInit();
        testSystem.setMixingRule(4);

        Stream stream1 = new NeqStream(testSystem);

        Pipeline pipe = new TwoPhasePipeLine(stream1);
        pipe.setOutputFileName("c:/tempNew3.nc");
        pipe.setInitialFlowPattern("annular");
        int numberOfLegs = 1, numberOfNodesInLeg = 10;
        double[] legHeights = {0, 0};
        double[] legPositions = {0.0, 0.5};
        double[] pipeDiameters = {0.02507588, 0.02507588};
        double[] outerTemperature = {295.0, 295.0};
        double[] pipeWallRoughness = {1e-5, 1e-5};
        pipe.setNumberOfLegs(numberOfLegs);
        pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
        pipe.setLegPositions(legPositions);
        pipe.setHeightProfile(legHeights);
        pipe.setPipeDiameters(pipeDiameters);
        pipe.setPipeWallRoughness(pipeWallRoughness);
        pipe.setOuterTemperatures(outerTemperature);
        pipe.setEquilibriumMassTransfer(false);
        pipe.setEquilibriumHeatTransfer(true);

        ProcessSystem operations = new ProcessSystem();
        operations.add(stream1);
        operations.add(pipe);

        operations.run();
        operations.displayResult();
    }
}
