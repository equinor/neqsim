package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class AcidTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AcidTest.class);

  /**
   * <p>
   * testAcid.
   * </p>
   */
  @Test
  @DisplayName("test equilibrium of formic acid")
  public void testAcid() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 1.0, "kg/sec");
    testSystem.addComponent("formic acid", 25.0, "kg/sec");
    testSystem.addComponent("water", 100.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * <p>
   * testtestBubpAcid.
   * </p>
   */
  @Test
  @DisplayName("test bublepoint of formic acid")
  public void testtestBubpAcid() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(373.0,
        1.3501325);
    testSystem.addComponent("formic acid", 25.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointPressureFlash(false);
      testSystem.initProperties();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    assertEquals(33.2215005, testSystem.getPressure(), 0.001);
    assertEquals(928.6031593220451, testSystem.getDensity("kg/m3"), 0.001);

    double t = 373.0;
    double vapp = Math.exp(50.323 + -5378.2 / t + -4.2030 * Math.log(t) + 3.4697e-6 * Math.pow(t, 2)) / 1e5;

    assertEquals(0.9857520491, vapp, 0.001);

    // double dens = 1.938 / (0.24225 * (1 + Math.pow(1 - t / 588, 0.24435)));// *
    // 46.025;
    // assertEquals(1002.54762, dens, 0.001);

    String scheme = testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid").getAssociationScheme();
    double aCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid"))
        .geta();
    double bCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid"))
        .getb();
    double boundvol = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid"))
        .getAssociationVolume();
    double assenergy = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid"))
        .getAssociationEnergy();
    double m = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent("formic acid"))
        .getAttractiveTerm().getm();
    assertEquals("1A", scheme);
    assertEquals(53663.0, aCPA);
    assertEquals(3.0, bCPA);
    assertEquals(0.0155, boundvol);
    assertEquals(41917.0, assenergy);
    assertEquals(0.3338, m);
  }

  /**
   * <p>
   * testtestBubpAcid.
   * </p>
   */
  @Test
  @DisplayName("test bublepoint of acetic acid")
  public void testtestBubpaceticacid() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 117.9,
        10.01325);
    testSystem.addComponent("acetic acid", 25.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointPressureFlash();
      testSystem.initProperties();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    assertEquals(16.6071906279100, testSystem.getPressure(), 0.001);
    assertEquals(826.2416600071817, testSystem.getDensity("kg/m3"), 0.001);

    double t = 373.0;
    double vapp = Math.exp(50.323 + -5378.2 / t + -4.2030 * Math.log(t) + 3.4697e-6 * Math.pow(t, 2)) / 1e5;

    assertEquals(0.9857520491, vapp, 0.001);

    // double dens = 1.938 / (0.24225 * (1 + Math.pow(1 - t / 588, 0.24435)));// *
    // 46.025;
    // assertEquals(1002.54762, dens, 0.001);

    String scheme = testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0).getAssociationScheme();
    double aCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).geta();
    double bCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).getb();
    double boundvol = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationVolume();
    double assenergy = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationEnergy();
    double m = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAttractiveTerm().getm();
    assertEquals("1A", scheme);
    assertEquals(91195.7, aCPA);
    assertEquals(4.6818, bCPA);
    assertEquals(0.00452, boundvol);
    assertEquals(40323.0, assenergy);
    assertEquals(0.4644000000000, m, 0.0001);
  }

  /**
   * <p>
   * testtestBubpAcid.
   * </p>
   */
  @Test
  @DisplayName("test bublepoint of water")
  public void testtestWater() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(398.0, 1.01325);
    testSystem.addComponent("water", 25.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointTemperatureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    String scheme = testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0).getAssociationScheme();
    double aCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).geta();
    double bCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).getb();
    double boundvol = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationVolume();
    double assenergy = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationEnergy();
    double m = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAttractiveTerm().getm();
    assertEquals("4C", scheme);
    assertEquals(12277.0, aCPA);
    assertEquals(1.4515, bCPA);
    assertEquals(0.0692, boundvol);
    assertEquals(16655.0, assenergy);
    assertEquals(.6735900000000007, m, 0.00001);
  }

  /**
   * <p>
   * testtestBubpAcid.
   * </p>
   */
  @Test
  @DisplayName("test bublepoint of MEG")
  public void testtestMEG() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(398.0, 1.01325);
    testSystem.addComponent("MEG", 25.0, "kg/sec");
    testSystem.setMixingRule(10);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointTemperatureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    String scheme = testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0).getAssociationScheme();
    double aCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).geta();
    double bCPA = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0)).getb();
    double boundvol = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationVolume();
    double assenergy = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAssociationEnergy();
    double m = ((ComponentSrkCPA) testSystem.getPhase(PhaseType.AQUEOUS).getComponent(0))
        .getAttractiveTerm().getm();
    assertEquals("4C", scheme);
    assertEquals(108190.0, aCPA);
    assertEquals(5.14, bCPA);
    assertEquals(0.0141, boundvol);
    assertEquals(19752.0, assenergy);
    assertEquals(0.6743999999999983, m);
  }
}
