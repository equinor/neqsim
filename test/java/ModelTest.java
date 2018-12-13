/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */

package neqsim.thermo.util.test;

import junit.framework.*;
import neqsim.thermo.system.SystemInterface;
/**
 *
 * @author ESOL
 */
public class ModelTest extends TestCase {

    private static final long serialVersionUID = 1000;
    
    SystemInterface testSystem = null;
    neqsim.thermo.ThermodynamicModelTest fugTest;
    
    public ModelTest(java.lang.String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ModelTest.class);
        return suite;
    }
    
    public void setUp(){
    }
    
    public void tearDown(){
        
    }
    
    public void testInit0(){
        try{
            testSystem.init(0);
        } catch (Exception success) {
            fail("Error running init0");
        }
        
    }
    
    public void testInit1(){
        try{
            testSystem.init(1);
        } catch (Exception success) {
            fail("Error running init1");
        }
    }
    
    public void testActivity(){
        testSystem.init(0);
        testSystem.init(1);
        double activ1 = testSystem.getPhase(1).getActivityCoefficient(0);
        System.out.println("activity = " + activ1);
        testSystem.init(0);
        testSystem.init(1);
        double activ2 = testSystem.getPhase(1).getActivityCoefficient(0);
        assertTrue(Math.abs((activ1-activ2))<1e-6);
    }
    
    public void testVolume(){
        testSystem.init(0);
        testSystem.init(1);
        double dens1 = testSystem.getPhase(0).getDensity();
        System.out.println("density gas start = " + dens1);
        testSystem.init(0);
        testSystem.init(1);
        double dens2 = testSystem.getPhase(1).getDensity();
        System.out.println("density liq start = " + dens2);
        assertTrue(dens2>dens1);
    }
    
     public void testGibbs(){
        testSystem.init(0);
        testSystem.init(1);
        double gibbs1 = testSystem.getPhase(0).getGibbsEnergy();
         testSystem.init(0);
        testSystem.init(1);
        double gibbs2 = testSystem.getPhase(1).getGibbsEnergy();
        assertTrue(gibbs2<gibbs1);
    }
    
    public void testFugasities(){
        testSystem.init(0);
        testSystem.init(1);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        System.out.println("components " + testSystem.getPhase(0).getNumberOfComponents());
        assertTrue(fugTest.checkFugasityCoeffisients());
    }
    
    public void testFugasitiesdT(){
        testSystem.init(0);
        testSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        System.out.println("components " + testSystem.getPhase(0).getNumberOfComponents());
        assertTrue(fugTest.checkFugasityCoeffisientsDT());
    }
    
    public void testFugasitiesdP(){
        testSystem.init(0);
        testSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        System.out.println("components " + testSystem.getPhase(0).getNumberOfComponents());
        assertTrue(fugTest.checkFugasityCoeffisientsDP());
    }
    
    public void testFugasitiesdn(){
        testSystem.init(0);
        testSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        System.out.println("components " + testSystem.getPhase(0).getNumberOfComponents());
        assertTrue(fugTest.checkFugasityCoeffisientsDn());
    }
    
    
    
    
}
