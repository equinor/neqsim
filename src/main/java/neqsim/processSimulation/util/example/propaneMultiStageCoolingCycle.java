/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;

/**
 *
 * @author esol
 */
public class propaneMultiStageCoolingCycle {

    private static final long serialVersionUID = 1000;

    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 15.00);
        testSystem.addComponent("propane", 421759.0, "kg/hr");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        
        neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 60.00);
        testSystem2.addComponent("methane", 0.9);
        testSystem2.addComponent("ethane", 0.08);
        testSystem2.addComponent("propane", 0.1);
        testSystem2.addComponent("n-butane", 0.01);
        testSystem2.addComponent("n-hexane", 0.006);
        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);
        testSystem2.setTotalFlowRate(10.0, "MSm3/day");
        
        StreamInterface naturalGasInletStream = new Stream("NG stream", testSystem2);
        
        Stream stream_1 = new Stream("Stream1", testSystem);
        stream_1.setSpecification("bubT");

        ThrottlingValve JTvalve1 = new ThrottlingValve(stream_1);
        JTvalve1.setOutletPressure(5.0);
        
        HeatExchanger heatEx1 = new HeatExchanger(naturalGasInletStream);
        heatEx1.setGuessOutTemperature(273.0+10.0);
        heatEx1.setUAvalue(10000.0);
        heatEx1.setFeedStream(1, JTvalve1.getOutStream());

        StreamInterface heatExPropaneOut = heatEx1.getOutStream(1);

        Separator sep1 = new Separator(heatExPropaneOut);
        
        ThrottlingValve JTvalve2 = new ThrottlingValve(sep1.getLiquidOutStream());
        JTvalve2.setOutletPressure(2.0);
        
        HeatExchanger heatEx2 = new HeatExchanger(heatEx1.getOutStream(0));
        heatEx2.setGuessOutTemperature(273.0+10.0);
        heatEx2.setUAvalue(200000.0);
        heatEx2.setFeedStream(1, JTvalve2.getOutStream());
        
        Cooler cooler = new Cooler(JTvalve1.getOutStream());
        cooler.setSpecification("out stream");

        Stream stream_2 = new Stream(cooler.getOutStream());
        stream_2.setSpecification("gas quality");
        stream_2.run();
        cooler.setOutStream(stream_2);

        ThrottlingValve JTvalve3 = new ThrottlingValve(stream_2);
        JTvalve2.setOutletPressure(1.1);
        JTvalve2.run();

        Cooler cooler2 = new Cooler(JTvalve2.getOutStream());
        cooler2.setSpecification("out stream");

        Stream stream_3 = new Stream(cooler2.getOutStream());
        stream_3.setSpecification("dewT");
        stream_3.run();
        cooler2.setOutStream(stream_3);

        Compressor compressor1 = new Compressor(stream_3);
        compressor1.setOutletPressure(stream_1.getPressure());

        Cooler cooler3 = new Cooler(compressor1.getOutStream());
        cooler3.setSpecification("out stream");
        cooler3.setOutStream(stream_1);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(naturalGasInletStream);
        operations.add(JTvalve1);
        operations.add(heatEx1);
        operations.add(heatExPropaneOut);
        operations.add(sep1);
        operations.add(JTvalve2);
        operations.add(heatEx2);
        /*
        operations.add(cooler);
        operations.add(stream_2);
        operations.add(JTvalve2);
        operations.add(cooler2);
        operations.add(stream_3);
        operations.add(compressor1);
        operations.add(cooler3);
        */
        //     operations.add(compressor1);
        //    operations.add(heater);

        operations.run();
        operations.run();
       
        heatEx1.run();
       // JTvalve1.displayResult();
       // heatExPropaneOut.displayResult();
        heatEx1.getOutStream(0).displayResult();
        heatEx1.getInStream(1).displayResult();
        heatEx1.getOutStream(1).displayResult();
        System.out.println("heatex duty " + heatEx1.getDuty());
        heatEx2.run();
        heatEx2.getOutStream(0).displayResult();
        heatEx2.getInStream(1).displayResult();
        heatEx2.getOutStream(1).displayResult();
        System.out.println("heatex duty " + heatEx2.getDuty());
       // stream_3.displayResult();
       // compressor1.displayResult();
       // cooler3.displayResult();
        //   JTvalve.displayResult();
        //  compressor1.displayResult();
        //  stream_2.displayResult();
        // operations.displayResult();
      //  System.out.println("compressor work" + compressor1.getEnergy()/1.0e3 + " kW");
        //   System.out.println("compressor isentropic ef " + compressor1.getIsentropicEfficiency());
      //  System.out.println("cooler duty " + cooler.getEnergyInput()/1.0e3 + " kW");
      //  System.out.println("cooler2 duty " + cooler2.getEnergyInput()/1.0e3 + " kW");
      //  System.out.println("cooler3 duty " + cooler3.getEnergyInput()/1.0e3 + " kW");
        //  System.out.println("heater duty " + heater.getEnergyInput());

    }
}
