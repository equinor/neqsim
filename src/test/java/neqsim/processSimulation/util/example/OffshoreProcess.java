package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticPhaseMixer;
import neqsim.processSimulation.processEquipment.pipeline.Pipeline;
import neqsim.processSimulation.processEquipment.pipeline.TwoPhasePipeLine;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.NeqStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public class OffshoreProcess {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        double fakt = 1000.0 / 3600.0;
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 - 4.3), 90.2);
        testSystem.addComponent("CO2", (152.6119 + 3513.5) * fakt);
        testSystem.addComponent("nitrogen", (9.8305 + 765 + 6882) * fakt);
        testSystem.addComponent("methane", (2799.24 + 109822.0) * fakt);
        testSystem.addComponent("ethane", (833.76 + 12449.02) * fakt);
        testSystem.addComponent("propane", (781.31 + 5930.5) * fakt);
        testSystem.addComponent("i-butane", (172.97 + 799.91) * fakt);
        testSystem.addComponent("n-butane", (351.05 + 1355.05) * fakt);
        testSystem.addComponent("i-pentane", (99.4368 + 234.68) * fakt);
        testSystem.addComponent("n-pentane", (115.92 + 248.78) * fakt);
        testSystem.addComponent("n-hexane", (42.3335 + 43.66) * fakt);
        testSystem.addComponent("n-heptane", (15.0259 + 8.65) * fakt);
        testSystem.addComponent("n-octane", (5.1311 + 1.635) * fakt);

        testSystem.addComponent("22-dim-C3", (6.3036 + 21.187) * fakt);
        testSystem.addComponent("c-C5", (8.286 + 13.2836) * fakt);
        testSystem.addComponent("2-m-C5", (50.3918 + 64.0821) * fakt);
        testSystem.addComponent("c-C6", (45.4498 + 43.5072) * fakt);
        testSystem.addComponent("benzene", (12.5589 + 113.24) * fakt);
        testSystem.addComponent("2-M-C6", (17.5213 + 12.0841) * fakt);
        testSystem.addComponent("3-M-C6", (35.58 + 20.804) * fakt);

        testSystem.addComponent("toluene", (14.5174 + 7.61) * fakt);
        testSystem.addComponent("22-DM-C5", (7.57 + 2.86) * fakt);
        testSystem.addComponent("M-cy-C5", (7.51 + 2.6357) * fakt);
        //
        testSystem.addComponent("m-Xylene", (5.5397 + 1.36) * fakt);
        testSystem.addComponent("2-M-C8", (2.8017 + 0.5818) * fakt);
        testSystem.addComponent("n-nonane", (1.5738 + 0.258) * fakt);
        //
        testSystem.addComponent("p-Xylene", (1.93 + 1.02) * fakt);
        testSystem.addComponent("o-Xylene", (1.1343 + 0.27) * fakt);
        // //
        testSystem.addComponent("nC10", (1.5464 + 0.145) * fakt);
        testSystem.addComponent("nC12", (0.2741 + 7.8295e-3) * fakt);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);

        Separator separator = new Separator("Separator 1", stream_1);

        StreamInterface stream_2 = separator.getGasOutStream();
        stream_2.setName("gas out stream");

        StreamInterface stream_3 = separator.getLiquidOutStream();
        stream_2.setName("liquid out stream");

        Compressor compressor1 = new Compressor(stream_2);
        compressor1.setOutletPressure(131.3);

        StreamInterface stream_4 = compressor1.getOutStream();
        stream_4.setName("gas compressor out stream");

        Compressor compressor2 = new Compressor(stream_3);
        compressor2.setOutletPressure(131.3);

        StreamInterface stream_5 = compressor2.getOutStream();
        stream_5.setName("liquid compressor out stream");

        Mixer mixer = new StaticPhaseMixer("Mixer 1");
        mixer.addStream(stream_4);
        mixer.addStream(stream_5);

        NeqStream stream_6 = new NeqStream(mixer.getOutStream());
        stream_6.setName("after mixer stream");

        Pipeline pipe = new TwoPhasePipeLine(stream_6);
        pipe.setOutputFileName("c:/tempAsgard.nc");
        pipe.setInitialFlowPattern("annular");
        int numberOfLegs = 1, numberOfNodesInLeg = 120;
        double[] legHeights = { 0, 0 };
        double[] legPositions = { 0.0, 74.0 };
        double[] pipeDiameters = { 1.02507588, 1.02507588 };
        double[] outerTemperature = { 275.0, 275.0 };
        double[] pipeWallRoughness = { 1e-5, 1e-5 };
        pipe.setNumberOfLegs(numberOfLegs);
        pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
        pipe.setLegPositions(legPositions);
        pipe.setHeightProfile(legHeights);
        pipe.setPipeDiameters(pipeDiameters);
        pipe.setPipeWallRoughness(pipeWallRoughness);
        pipe.setOuterTemperatures(outerTemperature);

        pipe.setEquilibriumMassTransfer(false);
        pipe.setEquilibriumHeatTransfer(false);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(separator);
        operations.add(stream_2);
        operations.add(stream_3);
        operations.add(compressor1);
        operations.add(stream_4);
        operations.add(compressor2);
        operations.add(stream_5);
        operations.add(mixer);
        operations.add(stream_6);
        operations.add(pipe);

        operations.run();
        operations.displayResult();

        // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testOps.calcPTphaseEnvelope();
        // testOps.displayResult();
    }
}