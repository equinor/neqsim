package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 * <p>
 * ModelBaseTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public abstract class ModelBaseTest {
    static SystemInterface thermoSystem = null;
    neqsim.thermo.ThermodynamicModelTest fugTest;

    /**
     * <p>
     * testInit0.
     * </p>
     */
    @Test
    public void testInit0() {
        try {
            thermoSystem.init(0);
        } catch (Exception success) {
            fail("Error running init0");
        }
    }

    /**
     * <p>
     * testInit1.
     * </p>
     */
    @Test
    public void testInit1() {
        try {
            thermoSystem.init(1);
        } catch (Exception success) {
            fail("Error running init1");
        }
    }

    /**
     * <p>
     * testActivity.
     * </p>
     */
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

    /**
     * <p>
     * testVolume.
     * </p>
     */
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

    /**
     * <p>
     * testGibbs.
     * </p>
     */
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

    /**
     * <p>
     * testFugasities.
     * </p>
     */
    @Test
    public void testFugasities() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugacityCoefficients());
    }

    /**
     * <p>
     * testFugasitiesdT.
     * </p>
     */
    @Test
    public void testFugasitiesdT() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugacityCoefficientsDT());
    }

    /**
     * <p>
     * testFugasitiesdP.
     * </p>
     */
    @Test
    public void testFugasitiesdP() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugacityCoefficientsDP());
    }

    /**
     * <p>
     * testFugasitiesdn.
     * </p>
     */
    @Test
    public void testFugasitiesdn() {
        thermoSystem.init(0);
        thermoSystem.init(3);
        fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
        assertTrue(fugTest.checkFugacityCoefficientsDn());
    }
}
