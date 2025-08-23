package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemBnsEosParityTest {
  @Test
  public void testProperties10Bar() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(10.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();
    double cp = sys.getPhase(0).getCp("J/molK");
    double cv = sys.getPhase(0).getCv("J/molK");
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R * sys.getTemperature()
        / sys.getMolarMass());
    assertEquals(21.73104479328527, cp, 1.0);
    assertEquals(7.749773205766539, density, 0.01);
    assertEquals(468.20247728371015, speed, 1.0);
  }

  @Test
  public void testProperties100Bar() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(100.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();
    double cp = sys.getPhase(0).getCp("J/molK");
    double cv = sys.getPhase(0).getCv("J/molK");
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R * sys.getTemperature()
        / sys.getMolarMass());
    assertEquals(29.83813477796911, cp, 1.0);
    assertEquals(95.44916709290638, density, 0.01);
    assertEquals(492.2469125804624, speed, 1.0);
  }
}
