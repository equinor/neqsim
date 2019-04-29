/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */
package neqsim.thermo.util.example;


import static org.junit.jupiter.api.Assertions.*;
import neqsim.thermo.system.SystemFurstElectrolyteEos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
/**
 *
 * @author ESOL
 */
@Disabled public class ElectrolyteScrkEosTest extends ModelBaseTest{

    private static final long serialVersionUID = 1000;
    
    
    
    @BeforeAll
    public static void setUp(){
    	thermoSystem = new SystemFurstElectrolyteEos(298.15, 1.01325);
    	thermoSystem.addComponent("Na+",0.01);
    	thermoSystem.addComponent("Cl-",0.01);
    	thermoSystem.addComponent("water",1.0);
    	thermoSystem.createDatabase(true);
    	thermoSystem.setMixingRule(1);
    }
    
    public static void tearDown(){
        
    }
}
