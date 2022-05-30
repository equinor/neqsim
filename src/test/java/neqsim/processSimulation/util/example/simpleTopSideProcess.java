package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.GasScrubberSimple;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>simpleTopSideProcess class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class simpleTopSideProcess {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 50.0), 50.00);
        testSystem.addComponent("methane", 900.00);
        testSystem.addComponent("ethane", 200.00);
        testSystem.addComponent("n-hexane", 200.0);
        testSystem.addComponent("n-nonane", 200.0);
        testSystem.addComponent("nC10", 20.0);
        testSystem.addComponent("nC13", 10.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        Stream stream_1 = new Stream("Stream1", testSystem);

        Mixer mixerHP = new neqsim.processSimulation.processEquipment.mixer.StaticMixer("Mixer HP");
        mixerHP.addStream(stream_1);

        Separator separator = new Separator("Separator 1", mixerHP.getOutStream());

        ThrottlingValve LP_valve = new ThrottlingValve("LPventil", separator.getLiquidOutStream());
        LP_valve.setOutletPressure(5.0);

        Separator LPseparator = new Separator("Separator 1", LP_valve.getOutStream());

        Compressor LPcompressor = new Compressor("LPcompressor", LPseparator.getGasOutStream());
        LPcompressor.setOutletPressure(50.0);

        Heater heaterLP = new Heater("heaterLP", LPcompressor.getOutletStream());
        heaterLP.setOutTemperature(270.25);

        Stream stream_2 = new Stream("cooled gas", heaterLP.getOutStream());

        GasScrubberSimple gasScrubber = new GasScrubberSimple("Scrubber", stream_2);

        Stream stream_3 = new Stream("liq from scrubber gas", gasScrubber.getLiquidOutStream());

        mixerHP.addStream(stream_3);

        Mixer mixer =
                new neqsim.processSimulation.processEquipment.mixer.StaticMixer("Mixer export");
        mixer.addStream(separator.getGasOutStream());
        mixer.addStream(gasScrubber.getGasOutStream());

        Compressor HPcompressor = new Compressor("HPcompressor", mixer.getOutStream());
        HPcompressor.setOutletPressure(200.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(mixerHP);
        operations.add(separator);
        operations.add(LP_valve);
        operations.add(LPseparator);
        operations.add(LPcompressor);
        operations.add(heaterLP);
        operations.add(stream_2);
        operations.add(gasScrubber);
        operations.add(stream_3);
        operations.add(mixer);
        operations.add(HPcompressor);

        operations.run();
        operations.run();
        operations.run();
        operations.run();
        operations.run();
        operations.run();

        operations.displayResult();
    }
}
