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

    assertEquals(39.11774916142968, cp, 0.1);
    assertEquals(29.815115496874455, cv, 0.1);
    assertEquals(5.456788345314515, jt, 0.2);
    assertEquals(7.765971461648, density, 0.001);
    assertEquals(411.4566767812817, speed, 1.0);


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

    assertEquals(0.78913033594, Z, 0.01);
    assertEquals(95.66601863781, density, 0.01);
    assertEquals(53.7112082181241, cp, 1.0);
    assertEquals(30.73168795781629, cv, 1.0);
    assertEquals(3.800162780643346, jt, 0.1);
    assertEquals(427.91066622776856, speed, 10.0);


  }

  @Test
  public void testCO2test() {
    SystemBnsEos system = new SystemBnsEos();
    system.setTemperature(60.0, "F");
    system.setPressure(2000.0, "psia");
    system.setAssociatedGas(false);
    // system.setRelativeDensity(0.65);
    system.setComposition(1.00, 0.0, 0.00, 0.0);
    system.useVolumeCorrection(true);
    system.setMixingRule(12);
    new ThermodynamicOperations(system).TPflash();

    system.initProperties();
    // system.prettyPrint();
    assertEquals(0.27805301704, system.getZvolcorr(), 1e-7);
  }
}
