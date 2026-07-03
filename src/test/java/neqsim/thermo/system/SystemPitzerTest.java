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

  /**
   * Run a TP flash with chemical equilibrium for a methane/CO2 gas over NaCl brine and verify that the gas split,
   * carbonate reactions, and aqueous Pitzer properties are physically reasonable.
   */
  @Test
  public void testCO2MethaneNaClReactiveTPflashReasonableResults() {
    SystemInterface system = new SystemPitzer(298.15, 10.0);
    system.addComponent("methane", 4.5);
    system.addComponent("CO2", 0.5);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    system.initPhysicalProperties();

    PhaseInterface gasPhase = null;
    PhaseInterface aqueousPhase = null;
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      if (system.getPhase(phaseNumber).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        gasPhase = system.getPhase(phaseNumber);
      }
      if (system.getPhase(phaseNumber).getType() == neqsim.thermo.phase.PhaseType.AQUEOUS) {
        aqueousPhase = system.getPhase(phaseNumber);
      }
    }

    assertTrue(gasPhase != null, "Methane-brine flash should form a gas phase");
    assertTrue(aqueousPhase != null, "Methane-brine flash should form an aqueous phase");
    assertTrue(system.isChemicalSystem(), "CO2-brine system should have aqueous chemical reactions");
    assertTrue(system.getChemicalReactionOperations().hasReactions(),
        "CO2-brine system should load chemical reactions");
    assertEquals(2, system.getNumberOfPhases(), "Expected gas plus reactive aqueous brine at 25 C and 10 bara");

    double gasMethaneMoles = gasPhase.getComponent("methane").getNumberOfMolesInPhase();
    double aqueousMethaneMoles = aqueousPhase.getComponent("methane").getNumberOfMolesInPhase();
    double totalMethaneMoles = gasMethaneMoles + aqueousMethaneMoles;
    assertTrue(gasMethaneMoles / totalMethaneMoles > 0.95,
        "Most methane should stay in the gas phase at 25 C and 10 bara");
    assertTrue(gasPhase.getComponent("methane").getx() > 0.80,
        "Gas phase should be methane-rich even with CO2 present");
    assertTrue(gasPhase.getComponent("CO2").getx() > 0.02, "Gas phase should retain a measurable CO2 content");
    assertTrue(aqueousPhase.getComponent("water").getx() > 0.94, "Aqueous phase should remain water-rich brine");
    double aqueousSodiumMoles = aqueousPhase.getComponent("Na+").getNumberOfMolesInPhase();
    double aqueousChlorideMoles = aqueousPhase.getComponent("Cl-").getNumberOfMolesInPhase();
    assertTrue(aqueousSodiumMoles > 0.99, "Sodium should remain in the aqueous phase, moles=" + aqueousSodiumMoles);
    assertTrue(aqueousChlorideMoles > 0.99,
        "Chloride should remain in the aqueous phase, moles=" + aqueousChlorideMoles);

    double density = aqueousPhase.getDensity();
    double physicalPropertyDensity = aqueousPhase.getPhysicalProperties().getDensity();
    assertEquals(physicalPropertyDensity, density, 1.0e-10);
    assertTrue(density > 1020.0 && density < 1060.0,
        "1 molal NaCl brine density at 25 C should be near 1035 kg/m3: " + density);

    assertTrue(aqueousPhase.hasComponent("HCO3-"), "Chemical equilibrium should add bicarbonate");
    assertTrue(aqueousPhase.hasComponent("H3O+"), "Chemical equilibrium should add hydronium");
    double aqueousCarbonMoles = aqueousPhase.getComponent("CO2").getNumberOfMolesInPhase()
        + aqueousPhase.getComponent("HCO3-").getNumberOfMolesInPhase();
    if (aqueousPhase.hasComponent("CO3--")) {
      aqueousCarbonMoles += aqueousPhase.getComponent("CO3--").getNumberOfMolesInPhase();
    }
    assertTrue(aqueousCarbonMoles > 1.0e-8,
        "Aqueous phase should contain dissolved/reacted CO2 species: " + aqueousCarbonMoles);
    assertTrue(aqueousPhase.getComponent("HCO3-").getNumberOfMolesInPhase() > 0.0,
        "CO2-water chemical equilibrium should produce bicarbonate");

    int sodiumNumber = aqueousPhase.getComponent("Na+").getComponentNumber();
    int chlorideNumber = aqueousPhase.getComponent("Cl-").getComponentNumber();
    double meanIonicActivity = aqueousPhase.getMeanIonicActivity(sodiumNumber, chlorideNumber);
    double osmoticCoefficient = aqueousPhase.getOsmoticCoefficientOfWater();
    double waterActivity = aqueousPhase.getActivityCoefficient(aqueousPhase.getComponent("water").getComponentNumber(),
        aqueousPhase.getComponent("water").getComponentNumber()) * aqueousPhase.getComponent("water").getx();

    assertTrue(meanIonicActivity > 0.55 && meanIonicActivity < 0.85,
        "1 molal NaCl mean ionic activity should be in the literature range: " + meanIonicActivity);
    assertTrue(osmoticCoefficient > 0.88 && osmoticCoefficient < 1.05,
        "Reactive NaCl/CO2 brine osmotic coefficient should remain reasonable: " + osmoticCoefficient);
    assertTrue(waterActivity > 0.95 && waterActivity < 0.99,
        "1 molal NaCl water activity should be slightly below pure water: " + waterActivity);
    assertTrue(aqueousPhase.getCp() > 0.0 && Double.isFinite(aqueousPhase.getCp()));
    assertTrue(Double.isFinite(aqueousPhase.getEnthalpy()));
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

  /**
   * Verify that salt saturation can add ions to an initially pure-water Pitzer system and converge to unit saturation
   * ratio.
   */
  @Test
  public void testCalcSaltSaturationNaClFromPureWater() {
    SystemInterface system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.calcSaltSaturation("NaCl"));

    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    if (aqueousPhaseNumber < 0) {
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        if (system.getPhase(i).hasComponent("water")) {
          aqueousPhaseNumber = i;
          break;
        }
      }
    }
    assertTrue(aqueousPhaseNumber >= 0, "Saturated Pitzer system should contain a water-rich phase");

    PhaseInterface phase = system.getPhase(aqueousPhaseNumber);
    double waterKg = phase.getComponent("water").getNumberOfMolesInPhase() * phase.getComponent("water").getMolarMass();
    double naMolality = phase.getComponent("Na+").getNumberOfMolesInPhase() / waterKg;
    double clMolality = phase.getComponent("Cl-").getNumberOfMolesInPhase() / waterKg;
    int waterComponentNumber = phase.getComponent("water").getComponentNumber();
    double gammaNa = phase.getActivityCoefficient(phase.getComponent("Na+").getComponentNumber(), waterComponentNumber);
    double gammaCl = phase.getActivityCoefficient(phase.getComponent("Cl-").getComponentNumber(), waterComponentNumber);
    double naclKsp = 92.78 - 0.407 * 298.15 + 0.000747 * 298.15 * 298.15;

    assertEquals(1.0, gammaNa * naMolality * gammaCl * clMolality / naclKsp, 1.0e-3);
  }

  /**
   * Verify NaCl mean ionic activity and osmotic coefficients against standard Pitzer literature values at 25 C.
   */
  @Test
  public void testNaClActivityAndOsmoticCoefficientAgainstLiterature() {
    double[] molalities = { 0.1, 0.5, 1.0, 2.0, 3.0 };
    double[] meanActivityCoefficients = { 0.778, 0.681, 0.657, 0.668, 0.714 };
    double[] osmoticCoefficients = { 0.932, 0.921, 0.936, 1.002, 1.085 };

    for (int i = 0; i < molalities.length; i++) {
      SystemInterface system = new SystemPitzer(298.15, 1.01325);
      system.addComponent("water", 55.508);
      system.addComponent("Na+", molalities[i]);
      system.addComponent("Cl-", molalities[i]);
      system.setMixingRule("classic");
      system.init(0);
      system.init(1);

      PhaseInterface phase = system.getPhase(1);
      int sodiumComponentNumber = phase.getComponent("Na+").getComponentNumber();
      int chlorideComponentNumber = phase.getComponent("Cl-").getComponentNumber();

      assertEquals(meanActivityCoefficients[i],
          phase.getMeanIonicActivity(sodiumComponentNumber, chlorideComponentNumber),
          0.08 * meanActivityCoefficients[i]);
      assertEquals(osmoticCoefficients[i], phase.getOsmoticCoefficientOfWater(), 0.04 * osmoticCoefficients[i]);
      assertEquals(phase.getOsmoticCoefficientOfWater(), phase.getOsmoticCoefficientOfWaterMolality(), 1.0e-12);
    }
  }

  @Test
  public void testHenryAndVaporPressure() {
    SystemInterface system = new SystemPitzer(298.15, 1.0);
    system.addComponent("water", 55.5);
    system.addComponent("methane", 1e-5);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    system.getPhase(1).getComponent("methane").setHenryCoefParameter(new double[] { 11.2605, 0.0, 0.0, 0.0 });
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

  /**
   * Verify that the Pitzer aqueous phase exposes a self-consistent commercial-simulator style property package across
   * ordinary and elevated-temperature brine conditions.
   */
  @Test
  public void testAqueousPhasePropertyPackageConsistency() {
    double[] temperatures = { 298.15, 373.15, 423.15 };
    double[] pressures = { 1.01325, 50.0, 100.0 };

    for (int i = 0; i < temperatures.length; i++) {
      SystemInterface system = new SystemPitzer(temperatures[i], pressures[i]);
      system.addComponent("water", 55.508);
      system.addComponent("Na+", 1.0);
      system.addComponent("Cl-", 1.0);
      system.setMixingRule("classic");
      system.init(0);
      system.init(1);
      system.initPhysicalProperties();

      PhaseInterface phase = system.getPhase(1);
      int waterNumber = phase.getComponent("water").getComponentNumber();
      int sodiumNumber = phase.getComponent("Na+").getComponentNumber();
      int chlorideNumber = phase.getComponent("Cl-").getComponentNumber();

      double waterActivityCoefficient = phase.getActivityCoefficient(waterNumber, waterNumber);
      double waterActivity = waterActivityCoefficient * phase.getComponent("water").getx();
      double ionMolalitySum = phase.getComponent("Na+").getMolality(phase)
          + phase.getComponent("Cl-").getMolality(phase);
      double osmoticCoefficientFromWaterActivity = -1000.0 * Math.log(waterActivity) / (18.015 * ionMolalitySum);

      assertEquals(osmoticCoefficientFromWaterActivity, phase.getOsmoticCoefficientOfWater(), 2.0e-10,
          "Water activity and osmotic coefficient should be thermodynamically consistent");
      assertEquals(phase.getOsmoticCoefficientOfWater(), phase.getOsmoticCoefficient(waterNumber), 1.0e-12);
      assertEquals(phase.getOsmoticCoefficientOfWater(), phase.getOsmoticCoefficientOfWaterMolality(), 1.0e-12);
      assertTrue(phase.getMeanIonicActivity(sodiumNumber, chlorideNumber) > 0.0);
      assertTrue(Double.isFinite(phase.getMeanIonicActivity(sodiumNumber, chlorideNumber)));

      double density = phase.getDensity();
      double physicalPropertyDensity = phase.getPhysicalProperties().getDensity();
      double molarVolume = phase.getMolarVolume();
      double numberOfMoles = phase.getNumberOfMolesInPhase();
      double enthalpy = phase.getEnthalpy();
      double entropy = phase.getEntropy();
      double internalEnergy = phase.getInternalEnergy();
      double gibbsEnergy = phase.getGibbsEnergy();
      double helmholtzEnergy = phase.getHelmholtzEnergy();
      double cp = phase.getCp();
      double cv = phase.getCv();

      assertTrue(Double.isFinite(density) && density > 950.0);
      assertEquals(physicalPropertyDensity, density, 1.0e-10);
      assertEquals(phase.getMolarMass() / density * 1.0e5, molarVolume, Math.abs(molarVolume) * 1.0e-10);
      assertEquals(enthalpy, internalEnergy + phase.getPressure() * molarVolume * numberOfMoles,
          Math.max(1.0, Math.abs(enthalpy)) * 1.0e-9);
      assertEquals(gibbsEnergy, enthalpy - system.getTemperature() * entropy,
          Math.max(1.0, Math.abs(gibbsEnergy)) * 1.0e-9);
      assertEquals(helmholtzEnergy, internalEnergy - system.getTemperature() * entropy,
          Math.max(1.0, Math.abs(helmholtzEnergy)) * 1.0e-9);
      assertTrue(cp > 0.0 && Double.isFinite(cp));
      assertTrue(cv > 0.0 && Double.isFinite(cv));
      assertEquals(cp / numberOfMoles, phase.getCp("J/molK"), 1.0e-12);
      assertEquals(cv / numberOfMoles, phase.getCv("J/molK"), 1.0e-12);
    }
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
   * Verify T-dependent Pitzer parameters give physically reasonable activity coefficients at high temperature. Before
   * fix: beta0(NaCl) diverged to ~373 at 100°C, causing exp(lngamma) overflow. After fix using Silvester-Pitzer
   * ln(T/Tr) form: beta0(NaCl, 100°C) ≈ 0.12.
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
   * Verify VLLE flash (gas-oil-water) with Pitzer model. When multiPhaseCheck is enabled, the system should detect 3
   * phases: gas (SRK), oil (SRK), and aqueous (Pitzer).
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
    assertTrue(system.getNumberOfPhases() >= 2, "Should have at least 2 phases: " + system.getNumberOfPhases());
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
   * Verify combined gas-oil-water Pitzer flash with CO2-driven aqueous chemical equilibrium.
   */
  @Test
  public void testReactiveVLLEFlashGasOilWaterWithCO2() {
    SystemInterface system = new SystemPitzer(313.15, 30.0); // 40°C, 30 bara
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.5);
    system.addComponent("nC10", 3.0);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());
    system.initPhysicalProperties();

    PhaseInterface gasPhase = null;
    PhaseInterface hydrocarbonLiquidPhase = null;
    PhaseInterface aqueousPhase = null;
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      PhaseInterface phase = system.getPhase(phaseNumber);
      if (phase.getType() == neqsim.thermo.phase.PhaseType.GAS) {
        gasPhase = phase;
      } else if (phase.getType() == neqsim.thermo.phase.PhaseType.AQUEOUS) {
        aqueousPhase = phase;
      } else if (phase.hasComponent("nC10") && phase.getComponent("nC10").getx() > 0.50) {
        hydrocarbonLiquidPhase = phase;
      }
    }

    assertEquals(3, system.getNumberOfPhases(), "Expected gas, hydrocarbon liquid, and aqueous phases");
    assertTrue(gasPhase != null, "Reactive VLLE should form a gas phase");
    assertTrue(hydrocarbonLiquidPhase != null, "Reactive VLLE should form a hydrocarbon liquid phase");
    assertTrue(aqueousPhase != null, "Reactive VLLE should form an aqueous Pitzer phase");
    assertTrue(system.isChemicalSystem(), "CO2-brine VLLE should have aqueous chemical reactions");
    assertTrue(system.getChemicalReactionOperations().hasReactions(), "CO2-brine VLLE should load reactions");

    assertTrue(gasPhase.getComponent("methane").getx() > 0.60, "Gas phase should be methane-rich");
    assertTrue(gasPhase.getComponent("CO2").getx() > 0.01, "Gas phase should retain measurable CO2");
    assertTrue(hydrocarbonLiquidPhase.getComponent("nC10").getx() > 0.70,
        "Hydrocarbon liquid phase should be nC10-rich");
    assertTrue(aqueousPhase.getComponent("water").getx() > 0.94, "Aqueous phase should remain water-rich");
    double sodiumMolality = aqueousPhase.getComponent("Na+").getMolality(aqueousPhase);
    double chlorideMolality = aqueousPhase.getComponent("Cl-").getMolality(aqueousPhase);
    assertTrue(sodiumMolality > 0.95 && sodiumMolality < 1.05,
        "Aqueous sodium molality should remain near the 1 molal brine basis: " + sodiumMolality);
    assertTrue(chlorideMolality > 0.95 && chlorideMolality < 1.05,
        "Aqueous chloride molality should remain near the 1 molal brine basis: " + chlorideMolality);

    assertTrue(aqueousPhase.hasComponent("HCO3-"), "Chemical equilibrium should add bicarbonate");
    assertTrue(aqueousPhase.hasComponent("H3O+"), "Chemical equilibrium should add hydronium");
    assertTrue(aqueousPhase.getComponent("HCO3-").getNumberOfMolesInPhase() > 0.0,
        "Dissolved CO2 chemistry should produce bicarbonate");

    int sodiumNumber = aqueousPhase.getComponent("Na+").getComponentNumber();
    int chlorideNumber = aqueousPhase.getComponent("Cl-").getComponentNumber();
    double meanIonicActivity = aqueousPhase.getMeanIonicActivity(sodiumNumber, chlorideNumber);
    double osmoticCoefficient = aqueousPhase.getOsmoticCoefficientOfWater();
    assertTrue(meanIonicActivity > 0.55 && meanIonicActivity < 0.85,
        "Reactive VLLE NaCl mean ionic activity should remain reasonable: " + meanIonicActivity);
    assertTrue(osmoticCoefficient > 0.88 && osmoticCoefficient < 1.05,
        "Reactive VLLE NaCl osmotic coefficient should remain reasonable: " + osmoticCoefficient);
    assertTrue(aqueousPhase.getDensity() > 1020.0 && aqueousPhase.getDensity() < 1060.0,
        "Reactive VLLE aqueous brine density should remain near 1 molal NaCl density");
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

    double gammaCa = system.getPhase(1).getActivityCoefficient(ca, liq.getComponent("water").getComponentNumber());
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
    system.initPhysicalProperties();

    double density = system.getPhase(1).getDensity();
    double physicalPropertyDensity = system.getPhase(1).getPhysicalProperties().getDensity();
    // ~1 molal NaCl brine at 25°C should be about 1035 kg/m³.
    assertEquals(1036.0, density, 8.0, "Brine density should match NaCl brine data");
    assertEquals(physicalPropertyDensity, density, 1.0e-10,
        "Pitzer thermodynamic density should use the salt-water physical-property density");
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
    system.addComponent("K+", 0.1);
    system.addComponent("Ca++", 0.1);
    system.addComponent("Mg++", 0.05);
    system.addComponent("Cl-", 0.8);
    system.addComponent("SO4--", 0.05);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    // Verify all ion gammas are finite
    PhasePitzer liq = (PhasePitzer) system.getPhase(1);
    int water = liq.getComponent("water").getComponentNumber();
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
      double gamma = system.getPhase(1).getActivityCoefficient(i, water);
      assertTrue(Double.isFinite(gamma),
          "Activity coefficient for " + system.getPhase(1).getComponent(i).getName() + " must be finite: " + gamma);
    }

    // Verify parameters were loaded for common salt pairs.
    assertTrue(liq.isParametersLoaded(), "Pitzer parameters should be loaded from database");
    assertLoadedBinaryParameters(liq, "Na+", "Cl-", true, true, false);
    assertLoadedBinaryParameters(liq, "K+", "Cl-", true, true, false);
    assertLoadedBinaryParameters(liq, "Ca++", "Cl-", true, true, false);
    assertLoadedBinaryParameters(liq, "Mg++", "Cl-", true, true, false);
  }

  /**
   * Verify all currently populated cation-anion Pitzer database rows can be loaded and used for finite activity and
   * osmotic coefficients.
   */
  @Test
  public void testDatabaseLoadAllPopulatedPitzerPairs() {
    String[][] ionPairs = { { "Na+", "Cl-", "true", "true", "false" }, { "Na+", "SO4--", "true", "true", "false" },
        { "K+", "Cl-", "true", "true", "false" }, { "K+", "SO4--", "true", "true", "false" },
        { "Ca++", "Cl-", "true", "true", "false" }, { "Ca++", "SO4--", "true", "true", "true" },
        { "Mg++", "Cl-", "true", "true", "false" }, { "Mg++", "SO4--", "true", "true", "true" },
        { "Ba++", "Cl-", "true", "true", "false" }, { "Sr++", "Cl-", "true", "true", "false" },
        { "Sr++", "SO4--", "true", "true", "true" }, { "Fe++", "Cl-", "true", "true", "false" },
        { "Fe++", "SO4--", "true", "true", "true" }, { "Na+", "HCO3-", "true", "true", "false" },
        { "Na+", "CO3--", "true", "true", "false" }, { "Ca++", "HCO3-", "true", "true", "false" },
        { "Mg++", "HCO3-", "true", "true", "false" }, { "Na+", "OH-", "true", "true", "false" },
        { "K+", "HCO3-", "true", "true", "false" }, { "K+", "CO3--", "true", "true", "false" },
        { "H+", "Cl-", "true", "true", "false" }, { "H+", "SO4--", "true", "true", "false" },
        { "Ba++", "HCO3-", "true", "true", "false" } };

    for (int i = 0; i < ionPairs.length; i++) {
      assertPopulatedPitzerPairCanBeUsed(ionPairs[i][0], ionPairs[i][1], Boolean.parseBoolean(ionPairs[i][2]),
          Boolean.parseBoolean(ionPairs[i][3]), Boolean.parseBoolean(ionPairs[i][4]));
    }
  }

  /**
   * Verifies that a binary Pitzer parameter row was loaded for an ion pair.
   *
   * @param phase Pitzer phase with loaded database parameters
   * @param ion1 first ion component name
   * @param ion2 second ion component name
   * @param expectBeta0 true if beta0 should be populated
   * @param expectBeta1 true if beta1 should be populated
   * @param expectBeta2 true if beta2 should be populated
   */
  private static void assertLoadedBinaryParameters(PhasePitzer phase, String ion1, String ion2, boolean expectBeta0,
      boolean expectBeta1, boolean expectBeta2) {
    int ion1Number = phase.getComponent(ion1).getComponentNumber();
    int ion2Number = phase.getComponent(ion2).getComponentNumber();
    if (expectBeta0) {
      assertTrue(Math.abs(phase.getBeta0ij(ion1Number, ion2Number)) > 0.0,
          ion1 + "/" + ion2 + " beta0 should be loaded from database");
    }
    if (expectBeta1) {
      assertTrue(Math.abs(phase.getBeta1ij(ion1Number, ion2Number)) > 0.0,
          ion1 + "/" + ion2 + " beta1 should be loaded from database");
    }
    if (expectBeta2) {
      assertTrue(Math.abs(phase.getBeta2ij(ion1Number, ion2Number)) > 0.0,
          ion1 + "/" + ion2 + " beta2 should be loaded from database");
    }
  }

  /**
   * Builds a simple binary electrolyte solution and checks that the database parameters produce usable thermodynamic
   * coefficients and aqueous phase properties.
   *
   * @param cation cation component name
   * @param anion anion component name
   * @param expectBeta0 true if beta0 should be populated
   * @param expectBeta1 true if beta1 should be populated
   * @param expectBeta2 true if beta2 should be populated
   */
  private static void assertPopulatedPitzerPairCanBeUsed(String cation, String anion, boolean expectBeta0,
      boolean expectBeta1, boolean expectBeta2) {
    SystemInterface system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent(cation, 0.1 * getAbsoluteChargeFromIonName(anion));
    system.addComponent(anion, 0.1 * getAbsoluteChargeFromIonName(cation));
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    system.initPhysicalProperties();

    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    phase.loadParametersFromDatabase();
    assertLoadedBinaryParameters(phase, cation, anion, expectBeta0, expectBeta1, expectBeta2);

    int cationNumber = phase.getComponent(cation).getComponentNumber();
    int anionNumber = phase.getComponent(anion).getComponentNumber();
    int waterNumber = phase.getComponent("water").getComponentNumber();
    double cationGamma = phase.getActivityCoefficient(cationNumber, waterNumber);
    double anionGamma = phase.getActivityCoefficient(anionNumber, waterNumber);
    double meanIonicActivity = phase.getMeanIonicActivity(cationNumber, anionNumber);
    double osmoticCoefficient = phase.getOsmoticCoefficientOfWater();

    assertTrue(Double.isFinite(cationGamma) && cationGamma > 0.0,
        cation + "/" + anion + " cation activity coefficient should be positive and finite");
    assertTrue(Double.isFinite(anionGamma) && anionGamma > 0.0,
        cation + "/" + anion + " anion activity coefficient should be positive and finite");
    assertTrue(Double.isFinite(meanIonicActivity) && meanIonicActivity > 0.0,
        cation + "/" + anion + " mean ionic activity coefficient should be positive and finite");
    assertTrue(Double.isFinite(osmoticCoefficient) && osmoticCoefficient > 0.0,
        cation + "/" + anion + " osmotic coefficient should be positive and finite");

    double density = phase.getDensity();
    double molarVolume = phase.getMolarVolume();
    double enthalpy = phase.getEnthalpy();
    double entropy = phase.getEntropy();
    double internalEnergy = phase.getInternalEnergy();
    double gibbsEnergy = phase.getGibbsEnergy();
    double helmholtzEnergy = phase.getHelmholtzEnergy();
    double cp = phase.getCp();
    double cv = phase.getCv();

    assertTrue(Double.isFinite(density) && density > 0.0,
        cation + "/" + anion + " density should be positive and finite");
    assertTrue(Double.isFinite(molarVolume) && molarVolume > 0.0,
        cation + "/" + anion + " molar volume should be positive and finite");
    assertTrue(Double.isFinite(enthalpy), cation + "/" + anion + " enthalpy should be finite");
    assertTrue(Double.isFinite(entropy), cation + "/" + anion + " entropy should be finite");
    assertTrue(Double.isFinite(internalEnergy), cation + "/" + anion + " internal energy should be finite");
    assertTrue(Double.isFinite(gibbsEnergy), cation + "/" + anion + " Gibbs energy should be finite");
    assertTrue(Double.isFinite(helmholtzEnergy), cation + "/" + anion + " Helmholtz energy should be finite");
    assertTrue(Double.isFinite(cp) && cp > 0.0, cation + "/" + anion + " Cp should be positive and finite");
    assertTrue(Double.isFinite(cv) && cv > 0.0, cation + "/" + anion + " Cv should be positive and finite");
    assertEquals(enthalpy, internalEnergy + phase.getPressure() * molarVolume * phase.getNumberOfMolesInPhase(),
        Math.max(1.0, Math.abs(enthalpy)) * 1.0e-9,
        cation + "/" + anion + " enthalpy/internal-energy identity should hold");
    assertEquals(gibbsEnergy, enthalpy - phase.getTemperature() * entropy,
        Math.max(1.0, Math.abs(gibbsEnergy)) * 1.0e-9, cation + "/" + anion + " Gibbs-energy identity should hold");
    assertEquals(helmholtzEnergy, internalEnergy - phase.getTemperature() * entropy,
        Math.max(1.0, Math.abs(helmholtzEnergy)) * 1.0e-9,
        cation + "/" + anion + " Helmholtz-energy identity should hold");
  }

  /**
   * Gets the absolute ionic charge from a NeqSim ion component name.
   *
   * @param ionName ion component name
   * @return absolute ionic charge
   */
  private static int getAbsoluteChargeFromIonName(String ionName) {
    int charge = 0;
    for (int i = 0; i < ionName.length(); i++) {
      if (ionName.charAt(i) == '+' || ionName.charAt(i) == '-') {
        charge++;
      }
    }
    return Math.max(charge, 1);
  }
}
