package neqsim.processsimulation.util.example;

import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.pipeline.TwoPhasePipeLine;
import neqsim.process.equipment.stream.NeqStream;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>processCO2MDEA class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class processCO2MDEA {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, 10.01325);

        testSystem.addComponent("methane", 0.061152181, 0);
        testSystem.addComponent("CO2", 0.061152181, 0);
        testSystem.addComponent("water", 0.0862204876, 1);
        testSystem.addComponent("MDEA", 0.008, 1);

        testSystem.chemicalReactionInit();
        testSystem.setMixingRule(4);

        Stream stream1 = new NeqStream("stream1", testSystem);

        Pipeline pipe = new TwoPhasePipeLine("pipe", stream1);
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
