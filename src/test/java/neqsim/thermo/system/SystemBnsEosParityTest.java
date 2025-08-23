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
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R * sys.getTemperature()
        / sys.getMolarMass());
    assertEquals(21.832364037459712, cp, 0.01);
    assertEquals(12.821736403643044, cv, 0.01);
    assertEquals(7.8812898778587845, jt, 0.01);
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
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R * sys.getTemperature()
        / sys.getMolarMass());
    assertEquals(30.504016321027333, cp, 0.01);
    assertEquals(12.821736403643044, cv, 0.01);
    assertEquals(5.09508498857797, jt, 0.01);
    assertEquals(95.44916709290638, density, 0.01);
    assertEquals(492.2469125804624, speed, 1.0);
  }
}
