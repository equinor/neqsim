package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.heatExchanger.ReBoiler;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.GasScrubberSimple;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

public class TEGAbsorberTest {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemSrkEos testSystem = new neqsim.thermo.system.SystemSrkSchwartzentruberEos(
                (273.15 + 20.0), 80.00);
        testSystem.addComponent("methane", 120.00);
        testSystem.addComponent("water", 0.1);
        testSystem.addComponent("TEG", 1e-10);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        neqsim.thermo.system.SystemSrkEos testSystem2 = new neqsim.thermo.system.SystemSrkSchwartzentruberEos(
                (273.15 + 20.0), 80.00);
        testSystem2.addComponent("methane", 1e-10);
        testSystem2.addComponent("water", 1e-9);
        testSystem2.addComponent("TEG", 0.10);
        testSystem2.setMixingRule(2);

        Stream fluidStreamIn = new Stream("stream to scrubber", testSystem);

        Separator gasScrubber = new GasScrubberSimple("gasInletScrubber", fluidStreamIn);

        Stream gasToAbsorber = new Stream(gasScrubber.getGasOutStream());
        gasToAbsorber.setName("gas from scrubber");

        Stream TEGstreamIn = new Stream("TEGstreamIn", testSystem2);

        SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("SimpleTEGAbsorber");
        absorber.addGasInStream(gasToAbsorber);
        absorber.addSolventInStream(TEGstreamIn);
        absorber.setNumberOfStages(5);
        absorber.setStageEfficiency(0.5);
//
        Stream gasStreamOut = new Stream(absorber.getGasOutStream());
        gasStreamOut.setName("gasStreamOut");
//
        Stream TEGStreamOut = new Stream(absorber.getSolventOutStream());
        TEGStreamOut.setName("TEGStreamOut");

        ThrottlingValve TEG_HPLP_valve = new ThrottlingValve("ventil", TEGStreamOut);
        TEG_HPLP_valve.setOutletPressure(10.0);

        Separator MPseparator = new Separator("Separator_MP", TEG_HPLP_valve.getOutStream());

        StreamInterface MPstreamGas = MPseparator.getGasOutStream();
        MPstreamGas.setName("MPGasStream");

        StreamInterface MPstreamLiq = MPseparator.getLiquidOutStream();
        MPstreamLiq.setName("MPLiqStream");
//
        ThrottlingValve LP_valve = new ThrottlingValve("LPventil", MPstreamLiq);
        LP_valve.setOutletPressure(1.5);

        ReBoiler reboiler = new ReBoiler(LP_valve.getOutStream());
        reboiler.setReboilerDuty(20000.0);

        neqsim.thermo.system.SystemSrkEos testSystem3 = new neqsim.thermo.system.SystemSrkSchwartzentruberEos(
                (273.15 + 20.0), 1.500);
        testSystem3.addComponent("methane", 0.39);
        testSystem3.addComponent("water", 1e-10);
        testSystem3.addComponent("TEG", 1e-10);
        testSystem3.createDatabase(true);
        testSystem3.setMixingRule(2);

        Stream mixStream = new Stream(testSystem3);

        Mixer mix = new Mixer("mixer");
        mix.addStream(reboiler.getOutStream());
        mix.addStream(mixStream);

        Stream ReboilLiqStream = mix.getOutStream();
        ReboilLiqStream.setName("ReboilLiqStream");
//
//         Stream ReboilGasStream = reboiler.getOutStream();
//        ReboilLiqStream.setName("ReboilLiqStream");

//        processSimulation.processEquipment.absorber.SimpleGlycolAbsorber TEGabsorber = new processSimulation.processEquipment.absorber.SimpleGlycolAbsorber(gasStreamIn);
//        TEGabsorber.setName("TEGabsorber");

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(fluidStreamIn);
        operations.add(gasScrubber);
        operations.add(gasToAbsorber);
        operations.add(TEGstreamIn);
        operations.add(absorber);
        operations.add(gasStreamOut);
        operations.add(TEGStreamOut);
        operations.add(TEG_HPLP_valve);
        operations.add(MPseparator);
        operations.add(MPstreamGas);
        operations.add(MPstreamLiq);
        operations.add(LP_valve);
        operations.add(reboiler);
        operations.add(mixStream);
        operations.add(mix);
        operations.add(ReboilLiqStream);

        operations.run();
        mix.displayResult();
        // operations.displayResult();
    }
}