package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * BNS gas-property parity with Mark Burgoyne's November 2026 Python reference.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemBnsEosParityTest {
  /** Checks condensate-gas properties at 10 bara. */
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
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R
        * sys.getTemperature() / sys.getMolarMass());

    assertEquals(39.115886093, cp, 0.01);
    assertEquals(29.81775015631597, cv, 0.1);
    assertEquals(5.316778088, jt, 0.002);
    assertEquals(7.7497757585082, density, 0.001);
    assertEquals(410.7584666804723, speed, 1.0);
  }

  /** Checks condensate-gas properties at 100 bara. */
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
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R
        * sys.getTemperature() / sys.getMolarMass());

    assertEquals(0.790903069244, Z, 0.01);
    assertEquals(95.4492017141868, density, 0.5);
    assertEquals(53.708650105, cp, 0.01);
    assertEquals(30.73168795781629, cv, 1.0);
    assertEquals(3.698193588, jt, 0.002);
    assertEquals(427.91066622776856, speed, 10.0);
  }

  /** Checks the mixed-gas example published with the upstream implementation. */
  @Test
  public void testPythonExample() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(48.88889 + 273.15);
    sys.setPressure(13.78948965 * 10.0);
    sys.setRelativeDensity(0.8);
    sys.setComposition(0.2, 0.1, 0.02, 0.1);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    sys.initProperties();
    sys.init(3);

    neqsim.thermo.ThermodynamicModelTest modtest = new neqsim.thermo.ThermodynamicModelTest(sys);
    modtest.setMaxError(1e-2);
    assertTrue(modtest.checkFugacityCoefficientsDT());
    double Z = sys.getZvolcorr();
    double density = sys.getDensity("kg/m3");
    double jt = sys.getPhase(0).getJouleThomsonCoefficient() * 10.0;
    double cp = sys.getPhase(0).getCp("J/molK");

    assertEquals(0.7941023149604872, Z, 1e-3);
    assertEquals(150.3030335721797, density, 0.5);
    assertEquals(2.902817099, jt, 0.002);
    assertEquals(53.928871473, cp, 0.01);
    assertEquals(0.017905435, sys.getViscosity("cP"), 1e-7);
  }

  /** Checks the dense pure-carbon-dioxide reference state. */
  @Test
  public void testCO2test() {
    SystemBnsEos system = new SystemBnsEos();
    system.setTemperature(60.0, "F");
    system.setPressure(2000.0, "psia");
    system.setAssociatedGas(false);
    system.setRelativeDensity(0.65);
    system.setComposition(1.00, 0.0, 0.00, 0.0);
    system.useVolumeCorrection(true);
    system.setMixingRule(12);
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    assertEquals(0.2778261815698205, system.getZvolcorr(), 1e-7);
    assertEquals(92.144033406 / 44.01, system.getCp("kJ/kgK"), 3e-4);
    assertEquals(0.094165933, system.getViscosity("cP"), 2e-6);
  }
}
