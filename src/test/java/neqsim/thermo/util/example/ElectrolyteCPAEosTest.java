/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */

package neqsim.thermo.util.test;

import junit.framework.*;
import neqsim.thermo.system.SystemElectrolyteCPA;

/**
 *
 * @author ESOL
 */
public class ElectrolyteCPAEosTest extends ModelTest {

    private static final long serialVersionUID = 1000;
    
    
    public ElectrolyteCPAEosTest(java.lang.String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ElectrolyteCPAEosTest.class);
        return suite;
    }
    
    public void setUp(){
        testSystem = new SystemElectrolyteCPA(298.15, 1.01325);
        testSystem.addComponent("Na+",0.001);
        testSystem.addComponent("Cl-",0.001);
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
