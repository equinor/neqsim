/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.TwoPhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 */
public class OffshoreProcess3 {

    private static final long serialVersionUID = 1000;

    public static void main(String[] args) {
        double reservoirTemperature = 350.0, reservoirPressure = 110.0;
        SystemInterface testSystem = new SystemSrkCPAstatoil(reservoirTemperature, reservoirPressure);

        testSystem.addComponent("nitrogen", 0.9);
       testSystem.addComponent("CO2", 1.3);
        testSystem.addComponent("methane", 40.3);
        testSystem.addComponent("ethane", 3);
        testSystem.addComponent("propane", 2);
        testSystem.addComponent("i-butane", 1);
        testSystem.addComponent("n-heptane", 11);
        testSystem.addComponent("water", 11);
      //  testSystem.addTBPfraction("C7", 4.3, 90.7 / 1000.0, 0.745);
     //   testSystem.addTBPfraction("C10", 10.3, 110.7 / 1000.0, 0.79);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream wellStream = new Stream("WellStream", testSystem);
        wellStream.setFlowRate(11.23, "MSm3/day");
        ThrottlingValve valve = new ThrottlingValve(wellStream);
        valve.setOutletPressure(75.0);
        Heater cooler1 = new Heater(valve.getOutStream());
        cooler1.setOutTemperature(298.15);
        Separator inletSeparator = new Separator("LuvaSeparator", cooler1.getOutStream());

        ThrottlingValve valve2 = new ThrottlingValve(inletSeparator.getLiquidOutStream());
        valve2.setOutletPressure(15.0);

        Separator mpseparator = new Separator("LuvaMPSeparator", valve2.getOutStream());
        Heater mpcondesateheater = new Heater(mpseparator.getLiquidOutStream());
        mpcondesateheater.setOutTemperature(100.0);
        ThrottlingValve valvempValve = new ThrottlingValve(mpcondesateheater.getOutStream());
        valvempValve.setOutletPressure(2.8);

        TwoPhaseSeparator lpseparator = new TwoPhaseSeparator("LuvaLPSeparator", valvempValve.getOutStream());
        Heater lpgasheater = new Heater(lpseparator.getGasOutStream());
        lpgasheater.setOutTemperature(290.0);
        Separator lpscrubber = new Separator("LuvaLPScrubber", lpgasheater.getOutStream());
        Pump lppump = new Pump("lpcondpump", lpscrubber.getLiquidOutStream());
        lppump.setOutletPressure(15.0);

        Compressor lpcompressor = new Compressor(lpscrubber.getGasOutStream());
        lpcompressor.setOutletPressure(15.0);
        //mpseparator.addStream(lppump.getOutStream()); //recycle
        Mixer mixermp = new Mixer("mpmixer");
        mixermp.addStream(lpcompressor.getOutStream());
        mixermp.addStream(mpseparator.getGasOutStream());
        //mixermp.isSetOutTemperature(true);
        Heater mpgasheater = new Heater(mixermp.getOutStream());
        mpgasheater.setOutTemperature(290.0);

        Compressor compressor2stage = new Compressor(mpseparator.getGasOutStream());
        compressor2stage.setOutletPressure(50.0);
        Mixer mixer = new Mixer("MPmixer");
        mixer.addStream(compressor2stage.getOutStream());
        mixer.addStream(inletSeparator.getGasOutStream());

        TwoPhaseSeparator mpscrubber = new TwoPhaseSeparator("LuvaMPScrubber", mixer.getOutStream());
        ThrottlingValve mpValve = new ThrottlingValve(mpscrubber.getLiquidOutStream());
        mpValve.setOutletPressure(15.0);
        //mpseparator.addStream(mpValve.getOutStream()); //recycle

        Compressor compressor1stage = new Compressor(mpscrubber.getGasOutStream());
        compressor1stage.setOutletPressure(75.0);

        Mixer mixerHP = new Mixer("HPmixer");
        mixerHP.addStream(compressor1stage.getOutStream());
        mixerHP.addStream(inletSeparator.getGasOutStream());

        Heater cooler1stagecomp = new Heater(mixerHP.getOutStream());
        cooler1stagecomp.setName("outlet cooler");
        cooler1stagecomp.setOutTemperature(273.15+34.21);
        cooler1stagecomp.setOutPressure(52.21);
        
        StreamInterface outletStream = cooler1stagecomp.getOutStream();
        outletStream.setName("richgas");
        

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(wellStream);
        operations.add(valve);
        operations.add(cooler1);
        operations.add(inletSeparator);
        operations.add(valve2);
        operations.add(mpseparator);
        operations.add(mpcondesateheater);
        operations.add(valvempValve);
        operations.add(lpseparator);
        operations.add(lpgasheater);
        operations.add(lpscrubber);
        operations.add(lppump);
        operations.add(lpcompressor);
        operations.add(mixermp);
        operations.add(mpgasheater);
        operations.add(compressor2stage);
        operations.add(mixer);
        operations.add(mpscrubber);
        operations.add(mpValve);
        operations.add(compressor1stage);
        operations.add(mixerHP);
        operations.add(cooler1stagecomp);
        operations.add(outletStream);
        operations.run();
        operations.run();
        operations.displayResult();
        //mixerHP.displayResult();
        //lppump.displayResult();
        operations.save("c:/temp/offshorePro.neqsim");
        ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
        operations2.run();
        //cooler1stagecomp.getOutStream().phaseEnvelope();
    }
}
