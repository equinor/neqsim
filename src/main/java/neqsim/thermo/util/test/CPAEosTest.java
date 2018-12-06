/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */

package neqsim.thermo.util.test;

import junit.framework.*;
import neqsim.thermo.system.SystemSrkCPAs;

/**
 *
 * @author ESOL
 */
public class CPAEosTest extends ModelTest {

    private static final long serialVersionUID = 1000;
    
    
    public CPAEosTest(java.lang.String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(CPAEosTest.class);
        return suite;
    }
    
    public void setUp(){
        testSystem = new SystemSrkCPAs(298.15, 1.01325);
        testSystem.addComponent("methanol",1.0);
        testSystem.addComponent("water",1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);
         testSystem.init(0);
    }
    
    public void tearDown(){
        
    }
    
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }
}
