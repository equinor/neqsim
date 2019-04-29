/*
 * SrkTest.java
 * JUnit based test
 *
 * Created on 27. september 2003, 19:51
 */

package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;

/**
 *
 * @author ESOL
 */
@Disabled public class ScrkEosTest extends ModelBaseTest {

    private static final long serialVersionUID = 1000;
    
    @BeforeAll
    public static void setUp(){
    	thermoSystem = new SystemSrkSchwartzentruberEos(298.15, 1.01325);
    	thermoSystem.addComponent("methanol",1.0);
    	thermoSystem.addComponent("water",1.0);
    	thermoSystem.createDatabase(true);
    	thermoSystem.setMixingRule(1);
    }
    
}
