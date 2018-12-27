/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */

package neqsim.thermo.util.test;

import junit.framework.*;
import neqsim.thermo.system.SystemFurstElectrolyteEos;

/**
 *
 * @author ESOL
 */
public class ElectrolyteScrkEosTest extends ModelTest {

    private static final long serialVersionUID = 1000;
    
    
    public ElectrolyteScrkEosTest(java.lang.String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ElectrolyteScrkEosTest.class);
        return suite;
    }
    
    public void setUp(){
        testSystem = new SystemFurstElectrolyteEos(298.15, 1.01325);
        testSystem.addComponent("Na+",0.01);
        testSystem.addComponent("Cl-",0.01);
        testSystem.addComponent("water",1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(1);
    }
    
    public void tearDown(){
        
    }
    
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }
}
