package neqsim.thermo.system;

import org.junit.jupiter.api.Test;

import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tests for BNS EOS print output.
 */
public class SystemBnsPrintTest {
  private static final Logger logger = LogManager.getLogger(SystemBnsPrintTest.class);

  @Test
  public void printAll() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(100.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    logger.info("=== Test 100bar ===");
    logger.info("Z = " + sys.getPhase(0).getZvolcorr());
    logger.info("density = " + sys.getDensity("kg/m3"));
    logger.info("cp = " + sys.getPhase(0).getCp("J/molK"));
    logger.info("cv = " + sys.getPhase(0).getCv("J/molK"));
    logger.info("jt = " + sys.getPhase(0).getJouleThomsonCoefficient() * 10.0);
    double gamma = sys.getPhase(0).getCp("J/molK") / sys.getPhase(0).getCv("J/molK");
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R
        * sys.getTemperature() / sys.getMolarMass());
    logger.info("speed = " + speed);
    logger.info("MW = " + sys.getMolarMass());

    SystemBnsEos sys2 = new SystemBnsEos();
    sys2.setTemperature(48.88889 + 273.15);
    sys2.setPressure(13.78948965 * 10.0);
    sys2.setRelativeDensity(0.8);
    sys2.setComposition(0.2, 0.1, 0.02, 0.1);
    sys2.useVolumeCorrection(true);
    sys2.setMixingRule(12);
    ThermodynamicOperations ops2 = new ThermodynamicOperations(sys2);
    ops2.TPflash();
    sys2.initProperties();
    sys2.init(3);
    logger.info("=== Test Python ===");
    logger.info("Z = " + sys2.getZvolcorr());
    logger.info("density = " + sys2.getDensity("kg/m3"));
    logger.info("jt = " + sys2.getPhase(0).getJouleThomsonCoefficient() * 10.0);
    logger.info("cp = " + sys2.getPhase(0).getCp("J/molK"));
    logger.info("MW = " + sys2.getMolarMass());
  }
}
