package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
@Disabled
public class ModelBaseTest {
    static SystemInterface thermoSystem = null;
    neqsim.thermo.ThermodynamicModelTest fugTest;

    @BeforeAll
    public static void setUp() {}

    @AfterAll
    public static void tearDown() {}

    @Test
    public void testInit0() {
        try {
            thermoSystem.init(0);
        } catch (Exception success) {
            fail("Error running init0");
        }
    }

    @Test
    public void testInit1() {
        try {
            thermoSystem.init(1);
        } catch (Exception success) {
            fail("Error running init1");
        }
    }

    @Test
    public void testActivity() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        double activ1 = thermoSystem.getPhase(1).getActivityCoefficient(0);
        thermoSystem.init(0);
        thermoSystem.init(1);
        double activ2 = thermoSystem.getPhase(1).getActivityCoefficient(0);
        assertTrue(Math.abs((activ1 - activ2)) < 1e-6);
    }

    @Test
    public void testVolume() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        double dens1 = thermoSystem.getPhase(0).getDensity();
        thermoSystem.init(0);
        thermoSystem.init(1);
        double dens2 = thermoSystem.getPhase(1).getDensity();
        assertTrue(dens2 > dens1);
    }

    @Test
    public void testGibbs() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        double gibbs1 = thermoSystem.getPhase(0).getGibbsEnergy();
        thermoSystem.init(0);
        thermoSystem.init(1);
        double gibbs2 = thermoSystem.getPhase(1).getGibbsEnergy();
        assertTrue(gibbs2 < gibbs1);
    }

    @Test
    public void testFugasities() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugasityCoeffisients());
    }

    @Test
    public void testFugasitiesdT() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugasityCoeffisientsDT());
    }

    @Test
    public void testFugasitiesdP() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugasityCoeffisientsDP());
    }

    @Test
    public void testFugasitiesdn() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugasityCoeffisientsDn());
    }
}
