package neqsim.thermo.util.example;

import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ConsistencyTest{

    private static final long serialVersionUID = 1000;
    
    /** A easy implementation to test a thermodyanmic model
     */
    public static void main(String args[]){
        SystemInterface testSystem = new SystemSrkEos(310.0,10);
        testSystem.addComponent("methane", 10.0);
        testSystem.addComponent("CO2", 10.0);
        
        testSystem.setMixingRule(4);
        testSystem.init(0);
        testSystem.init(1);
        ThermodynamicModelTest testModel = new ThermodynamicModelTest(testSystem);
        testModel.runTest();
    }
    
}
