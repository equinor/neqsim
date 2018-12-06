package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.pipeline.AdiabaticPipe;
import neqsim.processSimulation.processEquipment.pipeline.OnePhasePipeLine;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class gasPipeline {

    private static final long serialVersionUID = 1000;

    /** This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 200.00);
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
        pipeline.setLegPositions(new double[]{0, 70000,600000});
        pipeline.setHeightProfile(new double[]{0, 0, 0});
        pipeline.setPipeWallRoughness(new double[]{1e-5, 1e-5, 1e-5});
        pipeline.setOuterTemperatures(new double[]{277.0, 277.0, 277.0});
        pipeline.setPipeOuterHeatTransferCoefficients(new double[]{15.0, 15.0, 15.0});
        pipeline.setPipeWallHeatTransferCoefficients(new double[]{15.0, 15.0, 15.0});
        AdiabaticPipe simplePipeline = new AdiabaticPipe(stream_1);
        simplePipeline.setDiameter(1.0);
        simplePipeline.setLength(100);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
     //   operations.add(pipeline);
             operations.add(simplePipeline);

        operations.run();
        pipeline.getOutStream().displayResult();
        //.displayResult();
    }
}
