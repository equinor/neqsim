package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 * ModelBaseTest class.
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public abstract class ModelBaseTest extends neqsim.NeqSimTest {
  public static SystemInterface thermoSystem = null;
  neqsim.thermo.ThermodynamicModelTest fugTest;

  /**
   * testInit0.
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
   * testInit1.
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
   * testActivity.
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
   * testVolume.
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
   * testGibbs.
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
   * testFugasities.
   */
  @Test
  public void testFugasities() {
    thermoSystem.init(0);
    thermoSystem.init(1);
    fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    assertTrue(fugTest.checkFugacityCoefficients());
  }

  /**
   * testFugasitiesdT.
   */
  @Test
  public void testFugasitiesdT() {
    thermoSystem.init(0);
    thermoSystem.init(3);
    fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    assertTrue(fugTest.checkFugacityCoefficientsDT());
  }

  /**
   * testFugasitiesdP.
   */
  @Test
  public void testFugasitiesdP() {
    thermoSystem.init(0);
    thermoSystem.init(3);
    fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    assertTrue(fugTest.checkFugacityCoefficientsDP());
  }

  /**
   * testFugasitiesdn.
   */
  @Test
  public void testFugasitiesdn() {
    thermoSystem.init(0);
    thermoSystem.init(3);
    fugTest = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    assertTrue(fugTest.checkFugacityCoefficientsDn());
  }
}
