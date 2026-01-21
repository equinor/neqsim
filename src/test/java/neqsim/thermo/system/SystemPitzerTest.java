package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Consolidated regression tests for the Pitzer activity model in thermodynamic systems.
 */
public class SystemPitzerTest extends neqsim.NeqSimTest {
  @Test
  public void testTPflashNaCl() {
    SystemInterface system = new SystemPitzer(298.15, 10.0);
    system.addComponent("methane", 5.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(2, system.getNumberOfPhases());
    assertEquals(neqsim.thermo.phase.PhaseType.AQUEOUS, system.getPhase(1).getType());
    assertEquals(neqsim.thermo.phase.PhaseType.GAS, system.getPhase(0).getType());
  }

  @Test
  public void testTPflashWithMEG() {
    SystemInterface system = new SystemPitzer(298.15, 10.0);
    system.addComponent("methane", 5.0);
    system.addComponent("water", 55.5);
    system.addComponent("MEG", 1.0);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(2, system.getNumberOfPhases());
    assertEquals(neqsim.thermo.phase.PhaseType.AQUEOUS, system.getPhase(1).getType());
    PhasePitzer aq = (PhasePitzer) system.getPhase(1);
    double waterMass = system.getPhase(1).getComponent("water").getNumberOfMolesInPhase()
        * system.getPhase(1).getComponent("water").getMolarMass();
    assertEquals(waterMass, aq.getSolventWeight(), 1e-12);
  }

  @Test
  public void testPrettyPrintTwoPhase() {
    SystemInterface system = new SystemPitzer(298.15, 10.0);
    system.addComponent("methane", 5.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    String[][] table = system.createTable("test");
    Set<String> expectedPhases = new HashSet<>();
    expectedPhases.add("GAS");
    expectedPhases.add("AQUEOUS");
    Set<String> actualPhases = new HashSet<>();
    actualPhases.add(table[0][2]);
    actualPhases.add(table[0][3]);
    assertEquals(expectedPhases, actualPhases);
    int compRows = system.getPhase(0).getNumberOfComponents();
    Set<String> names = new HashSet<>();
    for (int j = 1; j <= compRows; j++) {
      names.add(table[j][0]);
    }
    assertTrue(names.contains("methane"));
    assertTrue(names.contains("water"));
    assertTrue(names.contains("Na+"));
    assertTrue(names.contains("Cl-"));
    int densityRow = compRows + 2;
    assertFalse(table[densityRow][2].isEmpty());
    assertFalse(table[densityRow][3].isEmpty());
  }

  @Test
  public void testGasOnlyTPflash() {
    SystemInterface system = new SystemPitzer(323.15, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("water", 1e-6);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(1, system.getNumberOfPhases());
    assertEquals(neqsim.thermo.phase.PhaseType.GAS, system.getPhase(0).getType());
  }

  @Test
  public void testPureWaterTPflash() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    assertEquals(1, system.getNumberOfPhases());
  }

  @Test
  public void testHenryAndVaporPressure() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("methane", 1e-5);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    system.getPhase(1).getComponent("methane")
        .setHenryCoefParameter(new double[] {11.2605, 0.0, 0.0, 0.0});
    double henry = system.getPhase(1).getComponent("methane").getHenryCoef(298.15);
    double vap = system.getPhase(1).getComponent("water").getAntoineVaporPressure(298.15);
    assertEquals(1.4e5, henry, 1e3);
    assertEquals(0.0318, vap, 1e-3);
  }

  @Test
  public void testCpCvAndEnthalpyAccess() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    double cpTotal = system.getPhase(1).getCp();
    double cpres = system.getPhase(1).getCpres();
    double cvres = ((PhasePitzer) system.getPhase(1)).getCvres();
    double h = system.getPhase(1).getEnthalpy();
    double cp = system.getPhase(1).getCp("J/molK");
    double cv = system.getPhase(1).getCv("J/molK");

    double cpIdeal = 0.0;
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      cpIdeal += system.getPhase(1).getComponent(i).getx()
          * system.getPhase(1).getComponent(i).getPureComponentCpLiquid(system.getTemperature());
    }
    double n = system.getPhase(1).getNumberOfMolesInPhase();

    assertEquals(cpIdeal * n + cpres, cpTotal, 1e-6);
    assertEquals(cpIdeal, cp, 1e-6,
        "Pitzer Cp defaults to ideal Cp when parameters lack T-dependence");
    assertEquals(0.0, cpres, 1e-8,
        "Residual Cp vanishes for temperature-independent Pitzer parameters");
    assertTrue(cp > 0.0);
    assertTrue(cv > 0.0);
    assertTrue(Double.isFinite(cpres));
    assertTrue(Double.isFinite(cvres));
    assertTrue(Double.isFinite(h));
  }

  @Test
  public void testThermodynamicConsistency() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);

    PhaseInterface phase = system.getPhase(1);
    double n = phase.getNumberOfMolesInPhase();
    double cp = phase.getCp("J/molK");
    double cv = phase.getCv("J/molK");
    double h = phase.getEnthalpy();
    double u = phase.getInternalEnergy();
    double g = phase.getGibbsEnergy();
    double s = phase.getEntropy();
    double v = phase.getMolarVolume();

    assertEquals(h, u + phase.getPressure() * v * n, Math.abs(h) * 1e-9);
    assertEquals(g, h - system.getTemperature() * s, Math.abs(g) * 1e-9);
    assertTrue(cp >= cv);
  }

  @Test
  public void testUnitConversions() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);

    PhaseInterface phase = system.getPhase(1);
    double cpJmol = phase.getCp("J/molK");
    assertEquals(cpJmol / 1000.0, phase.getCp("kJ/molK"), 1e-12);

    double mass = phase.getNumberOfMolesInPhase() * phase.getMolarMass();
    double hkjkg = phase.getEnthalpy() / mass / 1000.0;
    assertEquals(hkjkg, phase.getEnthalpy("kJ/kg"), 1e-12);
  }
}

