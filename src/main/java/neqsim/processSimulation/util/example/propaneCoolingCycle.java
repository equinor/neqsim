/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 *
 * @author esol
 */
public class propaneCoolingCycle {

    private static final long serialVersionUID = 1000;
    
    public static void main(String args[]) {
        
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos((273.15 + 50.0), 25.00);
        testSystem.addComponent("propane", 4759.0, "kg/hr");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        
        
        Stream stream_1 = new Stream("Stream1", testSystem);
        stream_1.setSpecification("bubT");
        stream_1.run();
        
        ThrottlingValve JTvalve = new ThrottlingValve(stream_1);
        
        Cooler cooler = new Cooler(JTvalve.getOutStream());
        cooler.setPressureDrop(0.35);
        cooler.setSpecification("out stream");
        
        Stream stream_2 = new Stream(cooler.getOutStream());
        stream_2.setSpecification("dewT");
        stream_2.getThermoSystem().setTemperature(273.15 - 10.0);
        stream_2.run();
        
        
        cooler.setOutStream(stream_2);
        JTvalve.setOutletPressure(stream_2.getPressure());
        
        Compressor compressor1 = new Compressor(stream_2);
        //compressor1.setIsentropicEfficiency(0.75);
       // compressor1.setPower(180000);
        compressor1.setSpecification("out stream");
        compressor1.setOutletPressure(stream_1.getPressure());
        
        
        Heater heater = new Heater(compressor1.getOutStream());
        heater.setPressureDrop(0.07);
        heater.setSpecification("out stream");
        heater.setOutStream(stream_1);
        
        
        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(JTvalve);
        operations.add(cooler);
        operations.add(stream_2);
        operations.add(compressor1);
        operations.add(heater);
        
        operations.run();
        operations.displayResult();
        
        System.out.println("compressor work" + compressor1.getEnergy());
        System.out.println("compressor isentropic ef " + compressor1.getIsentropicEfficiency());
        System.out.println("cooler duty " + cooler.getEnergyInput());
        System.out.println("heater duty " + heater.getEnergyInput());
        
    }
}
