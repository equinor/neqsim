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
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    double cp = sys.getPhase(0).getCp("J/molK");
    double cv = sys.getPhase(0).getCv("J/molK");
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed =
        Math.sqrt(gamma * sys.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R
            * sys.getTemperature() / sys.getMolarMass());
    assertEquals(22.179945824, cp, 0.01);
    assertEquals(12.9109361, cv, 0.01);
    assertEquals(9.424231892, jt, 0.01);
    assertEquals(7.750037498, density, 0.01);
    assertEquals(470.28174491, speed, 1.0);
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
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    double Z = sys.getPhase(0).getZvolcorr();
    double cp = sys.getPhase(0).getCp("J/molK");
    double cv = sys.getPhase(0).getCv("J/molK");
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    double density = sys.getDensity("kg/m3");
    double gamma = cp / cv;
    double speed =
        Math.sqrt(gamma * sys.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R
            * sys.getTemperature() / sys.getMolarMass());

    assertEquals(0.790903069, Z, 0.01);
    assertEquals(95.451592440, density, 0.01);
    assertEquals(36.1808374, cp, 0.01);
    assertEquals(13.82605033, cv, 0.01);
    assertEquals(5.48933866798, jt, 0.01);
    assertEquals(516.2596126, speed, 1.0);
  }
}
