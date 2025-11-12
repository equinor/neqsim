package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

public class SystemIdealGasTest extends neqsim.NeqSimTest
    implements ThermodynamicConstantsInterface {

  @Test
  public void testFugacityCoefficient() {
    SystemInterface testSystem = new SystemIdealGas(298.15, 10.0);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.getPhase(0).getComponent(0).fugcoef(testSystem.getPhase(0));
    assertEquals(1.0, testSystem.getPhase(0).getComponent(0).getFugacityCoefficient(), 1e-10);
  }

  // test with component from extended databaset
  @Test
  public void testComponentFromExtendedDatabase() {
    SystemInterface testSystem = new SystemIdealGas(298.15, 10.0);

    // NeqSimDataBase.useExtendedComponentDatabase(true);
    testSystem.addComponent("ethylene", 1, "mol/sec");
    testSystem.addComponent("ethylene", 1.0);
    testSystem.getPhase(0).getComponent(0).fugcoef(testSystem.getPhase(0));
    assertEquals(1.0, testSystem.getPhase(0).getComponent(0).getFugacityCoefficient(), 1e-10);
    // testSystem.prettyPrint();
    // we set it back to default after test to avoid having extended database in other tests to
    // reduce running time of creating fluid and adding components due to createion of inmemory
    // databases from csv table files
    NeqSimDataBase.useExtendedComponentDatabase(false);
  }

  @Test
  public void testDensity() {
    double T = 300.0;
    double P = 1.0;
    SystemInterface testSystem = new SystemIdealGas(T, P);
    testSystem.addComponent("methane", 1.0);
    double mw = testSystem.getPhase(0).getMolarMass();
    double expected = P * 1e5 * mw / (R * T);
    assertEquals(expected, testSystem.getPhase(0).getDensity("kg/m3"), 1e-6);
  }

  @Test
  public void testIdealGasProperties() {
    double T = 300.0;
    SystemInterface system = new SystemIdealGas(T, 1.0);
    system.addComponent("nitrogen", 0.5);
    system.addComponent("methane", 0.5);
    system.init(0);
    PhaseInterface phase = system.getPhase(0);
    double cpExpected = 0.0;
    double hExpected = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      double xi = phase.getComponent(i).getx();
      cpExpected += xi * phase.getComponent(i).getCp0(T);
      hExpected += xi * phase.getComponent(i).getHID(T);
    }
    double cvExpected = cpExpected - R;
    double gammaExpected = cpExpected / cvExpected;
    double soundSpeedExpected = Math.sqrt(gammaExpected * R * T / phase.getMolarMass());

    assertEquals(cpExpected, phase.getCp("J/molK"), 1e-6);
    assertEquals(cvExpected, phase.getCv("J/molK"), 1e-6);
    assertEquals(hExpected, phase.getEnthalpy("J/mol"), 1e-6);
    assertEquals(soundSpeedExpected, phase.getSoundSpeed(), 1e-6);
    assertEquals(0.0, phase.getCpres(), 1e-12);
  }

  @Test
  public void testTPflash() {
    SystemInterface system = new SystemIdealGas(298.15, 10.0);
    system.addComponent("nitrogen", 1.0);
    system.addComponent("methane", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(1, system.getNumberOfPhases());
    assertEquals(0.5, system.getPhase(0).getComponent("nitrogen").getx(), 1e-10);
    assertEquals(0.5, system.getPhase(0).getComponent("methane").getx(), 1e-10);
  }

  @Test
  public void testFugacityDerivatives() {
    SystemInterface system = new SystemIdealGas(298.15, 1.0);
    system.addComponent("nitrogen", 1.0);
    system.init(0);
    PhaseInterface phase = system.getPhase(0);
    double dT = phase.getComponent(0).logfugcoefdT(phase);
    double dP = phase.getComponent(0).logfugcoefdP(phase);
    double[] dN = phase.getComponent(0).logfugcoefdN(phase);
    double dNi = phase.getComponent(0).logfugcoefdNi(phase, 0);
    assertEquals(0.0, dT, 1e-12);
    assertEquals(0.0, dP, 1e-12);
    assertEquals(0.0, dN[0], 1e-12);
    assertEquals(0.0, dNi, 1e-12);
  }
}
