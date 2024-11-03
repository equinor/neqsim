package neqsim.process.util.example;

import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.stream.Stream;

/**
 * <p>gasPipeline class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class gasPipeline {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 200.00);
        testSystem.addComponent("methane", 22000.00);
        testSystem.addComponent("ethane", 12.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.initPhysicalProperties();
        Stream stream_1 = new Stream("Stream1", testSystem);

        OnePhasePipeLine pipeline = new OnePhasePipeLine(stream_1);
        double[] dima = {1.0, 1.0, 1.0};
        pipeline.setNumberOfLegs(dima.length - 1);
        pipeline.setPipeDiameters(dima);
        pipeline.setLegPositions(new double[] {0, 70000, 600000});
        pipeline.setHeightProfile(new double[] {0, 0, 0});
        pipeline.setPipeWallRoughness(new double[] {1e-5, 1e-5, 1e-5});
        pipeline.setOuterTemperatures(new double[] {277.0, 277.0, 277.0});
        pipeline.setPipeOuterHeatTransferCoefficients(new double[] {15.0, 15.0, 15.0});
        pipeline.setPipeWallHeatTransferCoefficients(new double[] {15.0, 15.0, 15.0});
        AdiabaticPipe simplePipeline = new AdiabaticPipe("simplePipeline", stream_1);
        simplePipeline.setDiameter(10.2);
        simplePipeline.setLength(100);
        simplePipeline.setInletElevation(0.0);
        simplePipeline.setOutletElevation(-100.0);

        AdiabaticTwoPhasePipe simplePipeline2phase =
                new AdiabaticTwoPhasePipe("simplePipeline2phase", stream_1);
        simplePipeline2phase.setDiameter(10.2);
        simplePipeline2phase.setLength(100);
        simplePipeline2phase.setInletElevation(0.0);
        simplePipeline2phase.setOutletElevation(-100.0);

        neqsim.process.processmodel.ProcessSystem operations =
                new neqsim.process.processmodel.ProcessSystem();
        operations.add(stream_1);
        operations.add(pipeline);
        // operations.add(simplePipeline);
        // operations.add(simplePipeline2phase);

        operations.run();
        //pipeline.getOutletStream().displayResult();
        // simplePipeline.getOutStream().displayResult();
        // simplePipeline2phase.getOutStream().displayResult();

        // .displayResult();
    }
}
