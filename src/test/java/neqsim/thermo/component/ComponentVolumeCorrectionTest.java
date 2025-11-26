package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/** Tests for explicit component volume correction handling. */
public class ComponentVolumeCorrectionTest extends neqsim.NeqSimTest {

  private static final double TEMPERATURE = 273.15; // K
  private static final double PRESSURE = 50.0; // bara

  @Test
  public void explicitVolumeCorrectionInfluencesLiquidDensity() {
    SystemInterface system = createSystem();
    ComponentInterface component = system.getComponent(0);

    double baselineDensity = calculateMassDensity(system);
    double originalCorrection = component.getVolumeCorrection();

    component.setVolumeCorrection(originalCorrection * 1.5);
    double increasedCorrectionDensity = calculateMassDensity(system);

    component.setVolumeCorrection(originalCorrection * 0.5);
    double decreasedCorrectionDensity = calculateMassDensity(system);

    assertEquals("n-heptane", component.getName());
    assertTrue(increasedCorrectionDensity < baselineDensity);
    assertTrue(decreasedCorrectionDensity > baselineDensity);
    assertTrue(decreasedCorrectionDensity > increasedCorrectionDensity);
  }

  @Test
  public void explicitVolumeCorrectionMatchesSpecifiedValue() {
    SystemInterface system = createSystem();
    ComponentInterface component = system.getComponent(0);

    double originalCorrection = component.getVolumeCorrection();
    double explicitCorrection = originalCorrection * 1.25;
    component.setVolumeCorrection(explicitCorrection);

    assertEquals(explicitCorrection, component.getVolumeCorrection());
  }

  private static SystemInterface createSystem() {
    SystemInterface system = new SystemPrEos(TEMPERATURE, PRESSURE);
    system.addComponent("n-heptane", 1.0);
    system.init(0);
    system.init(1);
    return system;
  }

  private static double calculateMassDensity(SystemInterface system) {
    system.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    return system.getDensity("kg/m3");
  }
}
