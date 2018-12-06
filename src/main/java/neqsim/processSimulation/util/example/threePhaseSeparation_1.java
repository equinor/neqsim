package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class threePhaseSeparation_1{

    private static final long serialVersionUID = 1000;
    
    /** This method is just meant to test the thermo package.
     */
    public static void main(String args[]){
        
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAs((273.15+25.0),50.00);
        testSystem.addComponent("methane", 10.00);
        testSystem.addComponent("n-heptane", 1.0);
        testSystem.addComponent("water", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);
        
        Stream stream_1 = new Stream("Stream1", testSystem);
        
        
        ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator", stream_1);
        
        Stream stream_2 = new Stream(separator.getGasOutStream());
        stream_2.setName("gas from separator");
        Stream stream_3 = new Stream(separator.getOilOutStream());
        stream_3.setName("oil from separator");
        Stream stream_4 = new Stream(separator.getWaterOutStream());
        stream_4.setName("water from separator");
        
    
        
        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(separator);
        operations.add(stream_2);
        operations.add(stream_3);
        operations.add(stream_4);
        
        operations.run();
        operations.displayResult();
    }
}