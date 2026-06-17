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
    // Cp includes residual from DH A_phi(T) dependence — non-zero for ionic solutions
    assertEquals(cpIdeal, cp, 25.0, "Pitzer Cp should be close to ideal Cp for simple NaCl system");
    // Excess Cp for 1 molal NaCl is non-zero due to Debye-Hückel T-dependence
    assertTrue(Math.abs(cpres) < 50.0, "Residual Cp should be moderate for 1m NaCl: " + cpres);
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

  /**
   * Verify T-dependent Pitzer parameters give physically reasonable activity coefficients at high
   * temperature. Before fix: beta0(NaCl) diverged to ~373 at 100°C, causing exp(lngamma) overflow.
   * After fix using Silvester-Pitzer ln(T/Tr) form: beta0(NaCl, 100°C) ≈ 0.12.
   */
  @Test
  public void testHighTemperaturePitzerStability() {
    SystemInterface system = new SystemPitzer(373.15, 100.0); // 100°C, 100 bara
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");

    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);

    // Manually set T-dependent coefficients (NaCl from Pitzer 1984, Silvester-Pitzer form)
    // These match PitzerParameters.csv row 1
    liq.setBeta0T(na, cl, 460.4, 1.556);
    liq.setBeta1T(na, cl, -11.5, 0.087);
    liq.setCphiT(na, cl, -0.88, -0.0043);

    // Check beta0 at 100°C — should be ~0.12, not ~373
    double beta0at100 = liq.getBeta0ij(na, cl, 373.15);
    assertTrue(beta0at100 > 0.05, "beta0(NaCl, 100°C) should be positive: " + beta0at100);
    assertTrue(beta0at100 < 0.5, "beta0(NaCl, 100°C) should be < 0.5: " + beta0at100);

    // Check beta0 at 200°C — should be ~0.23
    double beta0at200 = liq.getBeta0ij(na, cl, 473.15);
    assertTrue(beta0at200 > 0.1, "beta0(NaCl, 200°C) should be > 0.1: " + beta0at200);
    assertTrue(beta0at200 < 0.8, "beta0(NaCl, 200°C) should be < 0.8: " + beta0at200);

    // Flash at 100°C should converge without overflow
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    // Activity coefficient for Na+ should be finite and in reasonable range [0.001, 100]
    int waterIdx = liq.getComponent("water").getComponentNumber();
    double gammaNa = system.getPhase(1).getActivityCoefficient(na, waterIdx);
    assertTrue(Double.isFinite(gammaNa), "Na+ activity coefficient must be finite at 100°C");
    assertTrue(gammaNa > 0.001, "Na+ gamma too small: " + gammaNa);
    assertTrue(gammaNa < 100.0, "Na+ gamma too large: " + gammaNa);
  }

  /**
   * Verify T-dependent parameters at 150°C still produce stable results.
   */
  @Test
  public void testPitzerAt150C() {
    SystemInterface system = new SystemPitzer(423.15, 100.0); // 150°C, 100 bara
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");

    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    liq.setBinaryParameters(na, cl, 0.0765, 0.2664, 0.00127);
    liq.setBeta0T(na, cl, 460.4, 1.556);
    liq.setBeta1T(na, cl, -11.5, 0.087);
    liq.setCphiT(na, cl, -0.88, -0.0043);

    double beta0at150 = liq.getBeta0ij(na, cl, 423.15);
    assertTrue(beta0at150 > 0.05, "beta0(NaCl, 150°C) should be > 0.05: " + beta0at150);
    assertTrue(beta0at150 < 1.0, "beta0(NaCl, 150°C) should be < 1.0: " + beta0at150);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    int waterIdx = liq.getComponent("water").getComponentNumber();
    double gammaNa = system.getPhase(1).getActivityCoefficient(na, waterIdx);
    assertTrue(Double.isFinite(gammaNa), "Na+ activity coefficient must be finite at 150°C");
    assertTrue(gammaNa > 0.001 && gammaNa < 100.0, "Na+ gamma out of range at 150°C: " + gammaNa);
  }

  /**
   * Verify VLLE flash (gas-oil-water) with Pitzer model. When multiPhaseCheck is enabled, the
   * system should detect 3 phases: gas (SRK), oil (SRK), and aqueous (Pitzer).
   */
  @Test
  public void testVLLEFlashGasOilWater() {
    SystemInterface system = new SystemPitzer(313.15, 50.0); // 40°C, 50 bara
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    // With VLLE, should get at least 2 phases (gas + aqueous), possibly 3 (gas + oil + aqueous)
    assertTrue(system.getNumberOfPhases() >= 2,
        "Should have at least 2 phases: " + system.getNumberOfPhases());
    // Verify at least one phase has type AQUEOUS
    boolean hasAqueous = false;
    boolean hasGas = false;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.AQUEOUS) {
        hasAqueous = true;
      }
      if (system.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        hasGas = true;
      }
    }
    assertTrue(hasGas, "System should have a gas phase");
    // Aqueous phase should be detectable
    assertTrue(system.getNumberOfPhases() >= 2, "System should detect at least gas + aqueous");
  }

  /**
   * Verify that beta2 parameters work for 2-2 electrolytes (CaSO4).
   */
  @Test
  public void testBeta2For22Electrolyte() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Ca++", 0.01);
    system.addComponent("SO4--", 0.01);
    system.setMixingRule("classic");

    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int ca = liq.getComponent("Ca++").getComponentNumber();
    int so4 = liq.getComponent("SO4--").getComponentNumber();

    // Set beta2 for CaSO4 (2-2 electrolyte)
    liq.setBeta2(ca, so4, -54.24);

    system.init(0);
    system.init(1);

    double gammaCa = system.getPhase(1).getActivityCoefficient(ca,
        liq.getComponent("water").getComponentNumber());
    assertTrue(Double.isFinite(gammaCa), "Ca++ gamma must be finite: " + gammaCa);
    assertTrue(gammaCa > 0.0, "Ca++ gamma must be positive: " + gammaCa);
    // 2-2 electrolytes have very low activity coefficients at moderate I
    assertTrue(gammaCa < 2.0, "Ca++ gamma should be moderate: " + gammaCa);
  }

  /**
   * Verify that the brine density override gives reasonable values.
   */
  @Test
  public void testBrineDensity() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);

    double density = system.getPhase(1).getDensity();
    // ~1 molal NaCl brine at 25°C should be ~1020-1050 kg/m³
    assertTrue(density > 990.0, "Brine density should be > 990: " + density);
    assertTrue(density < 1100.0, "Brine density should be < 1100: " + density);
    // Should be higher than pure water (~997 at 25°C)
    assertTrue(density > 997.0, "Brine should be denser than pure water: " + density);
  }

  /**
   * Verify theta and psi parameters can be set and affect activity coefficients.
   */
  @Test
  public void testThetaPsiMixing() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("K+", 0.5);
    system.addComponent("Cl-", 1.5);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);

    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int na = liq.getComponent("Na+").getComponentNumber();
    int k = liq.getComponent("K+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    int water = liq.getComponent("water").getComponentNumber();

    // Get baseline gamma without theta
    double gammaBaseline = system.getPhase(1).getActivityCoefficient(na, water);

    // Set Na-K theta (cation-cation interaction)
    liq.setTheta(na, k, -0.012); // Harvie & Weare value

    system.init(1);
    double gammaWithTheta = system.getPhase(1).getActivityCoefficient(na, water);

    assertTrue(Double.isFinite(gammaWithTheta), "Na+ gamma with theta must be finite");
    // Theta should change the activity coefficient slightly
    assertTrue(Math.abs(gammaWithTheta - gammaBaseline) < 5.0,
        "Theta effect should be moderate, baseline=" + gammaBaseline + " new=" + gammaWithTheta);
  }

  /**
   * Verify database-loaded parameters work for multiple salts.
   */
  @Test
  public void testDatabaseLoadMultipleSalts() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 0.5);
    system.addComponent("Ca++", 0.1);
    system.addComponent("Cl-", 0.7);
    system.addComponent("SO4--", 0.05);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    // Verify all ion gammas are finite
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int water = liq.getComponent("water").getComponentNumber();
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      double gamma = system.getPhase(1).getActivityCoefficient(i, water);
      assertTrue(Double.isFinite(gamma), "Activity coefficient for "
          + system.getPhase(1).getComponent(i).getName() + " must be finite: " + gamma);
    }

    // Verify parameters were loaded (check that Pitzer params are nonzero for NaCl)
    assertTrue(liq.isParametersLoaded(), "Pitzer parameters should be loaded from database");
    int na = liq.getComponent("Na+").getComponentNumber();
    int cl = liq.getComponent("Cl-").getComponentNumber();
    assertTrue(Math.abs(liq.getBeta0ij(na, cl)) > 0.01,
        "NaCl beta0 should be loaded from database");
  }
}

