package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.mixingrule.EosMixingRuleHandler;
import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;
import neqsim.thermo.phase.PhaseSoreideWhitson;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SoreideWhitsonSystemTest {
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;

  /**
   * Test Soreide-Whitson system with zero salinity. Checks that the phase mole fractions for nitrogen, CO2, methane,
   * ethane, and water in both gas and aqueous phases match the expected values for a system with no added salt.
   */
  @Test
  public void testSoreideWhitsonSetup() {
    // Create a Soreide-Whitson system

    // SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 20.0);
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);
    testSystem.setPressure(40.0, "bara");
    testSystem.setTemperature(45.0, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    // Check phase mole fractions for both gas and aqueous phases
    double[] expectedGasFractions = { 1.10818E-1, 2.21422E-1, 3.32456E-1, 3.32456E-1, 2.84796E-3 };
    double[] expectedAqueousFractions = { 4.43467E-5, 2.07206E-3, 1.26653E-4, 1.29507E-4, 9.97627E-1 };
    String[] componentNames = { "nitrogen", "CO2", "methane", "ethane", "water" };
    double tolerance = 1e-6;

    for (int phaseIdx = 0; phaseIdx < 2; phaseIdx++) {
      for (int compIdx = 0; compIdx < componentNames.length; compIdx++) {
        double moleFrac = testSystem.getPhase(phaseIdx).getComponent(compIdx).getx();
        double expected = (phaseIdx == 0) ? expectedGasFractions[compIdx] : expectedAqueousFractions[compIdx];
        org.junit.jupiter.api.Assertions.assertEquals(expected, moleFrac, tolerance,
            "Phase " + phaseIdx + " component " + componentNames[compIdx] + " mole fraction");
      }
    }
  }

  /**
   * Test Soreide-Whitson system with zero salinity (multiphase).
   */
  @Test
  public void testMultiphaseWhitson() {
    // Create a Soreide-Whitson system

    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("nC10", 0.6, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(40.0, "bara");
    testSystem.setTemperature(45.0, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test Soreide-Whitson system with nonzero salinity (0.05 mole/sec). Checks that the phase mole fractions for
   * nitrogen, CO2, methane, ethane, and water in both gas and aqueous phases match the expected values for a system
   * with added salt. This validates the effect of salinity on phase equilibrium and partitioning.
   */
  @Test
  public void testSoreideWhitsonSetup2() {
    // Create a Soreide-Whitson system
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0.05, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);
    testSystem.setPressure(40.0, "bara");
    testSystem.setTemperature(45.0, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    // Check phase mole fractions for both gas and aqueous phases (with salinity)
    double[] expectedGasFractions = { 1.10836E-1, 2.2151E-1, 3.32509E-1, 3.3251E-1, 2.63456E-3 };
    double[] expectedAqueousFractions = { 2.53209E-5, 1.55814E-3, 7.67909E-5, 7.0922E-5, 9.98269E-1 };
    String[] componentNames = { "nitrogen", "CO2", "methane", "ethane", "water" };
    double tolerance = 0.001;

    for (int phaseIdx = 0; phaseIdx < 2; phaseIdx++) {
      for (int compIdx = 0; compIdx < componentNames.length; compIdx++) {
        double moleFrac = testSystem.getPhase(phaseIdx).getComponent(compIdx).getx();
        double expected = (phaseIdx == 0) ? expectedGasFractions[compIdx] : expectedAqueousFractions[compIdx];
        org.junit.jupiter.api.Assertions.assertEquals(expected, moleFrac, tolerance,
            "Phase " + phaseIdx + " component " + componentNames[compIdx] + " mole fraction");
      }
    }

    // Check salinity concentration in aqueous phase
    double expectedSalinity = 1.8877351154938637;
    double actualSalinity = ((PhaseSoreideWhitson) testSystem.getPhase(1)).getSalinityConcentration();
    org.junit.jupiter.api.Assertions.assertEquals(expectedSalinity, actualSalinity, 0.01,
        "Aqueous phase salinity concentration");
  }

  @Test
  public void testStreamMixingAndSeparationWithSalinity() {
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0.05, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);

    SystemSoreideWhitson testSystem2 = testSystem.clone();
    double[] molarComposition = { 0.2, 0.4, 0.1, 0.2, 0.1 };
    testSystem2.setMolarComposition(molarComposition);
    testSystem2.setSalinity(0.00, "mole/sec");

    Stream stream1 = new Stream("Test Stream1", testSystem);
    stream1.setPressure(40, "bara");
    stream1.setTemperature(15, "C");
    stream1.run();

    Stream stream2 = new Stream("Test Stream2", testSystem2);
    stream2.setPressure(40, "bara");
    stream2.setTemperature(95, "C");
    stream2.run();

    // 3. Create and run the mixer
    StaticMixer mixer = new StaticMixer("Stream Mixer");
    mixer.addStream(stream1);
    mixer.addStream(stream2);
    mixer.run();
    // mixer.getOutletStream().run();

    // 4. Check the salinity concentration in the mixed stream's aqueous phase
    SystemSoreideWhitson mixedSystem = (SystemSoreideWhitson) mixer.getOutletStream().getFluid();
    double mixedSalinity = ((PhaseSoreideWhitson) mixedSystem.getPhase(1)).getSalinityConcentration();

    org.junit.jupiter.api.Assertions.assertTrue(mixedSalinity > 0.96 && mixedSalinity < 0.97,
        "Mixed salinity should be around 0.96 , but was: " + mixedSalinity);

    Separator separator = new Separator("Stream Separator");
    separator.addStream(mixer.getOutletStream());
    separator.run();

    StreamInterface streamGas = separator.getGasOutStream();
    double gasSalinity = ((SystemSoreideWhitson) streamGas.getFluid()).getSalinity();
    StreamInterface streamAqueous = separator.getLiquidOutStream();
    double waterSalinity = ((SystemSoreideWhitson) streamAqueous.getFluid()).getSalinity();

    org.junit.jupiter.api.Assertions.assertEquals(0.0, gasSalinity, 1e-8, "Gas salinity should be 0.0");
    org.junit.jupiter.api.Assertions.assertEquals(0.05, waterSalinity, 1e-8, "Water salinity should be 0.05");
  }

  @Test
  public void testAqueousCO2ParameterizationSelectionAndClone() {
    SystemSoreideWhitson system = new SystemSoreideWhitson(323.15, 100.0);
    assertEquals(SoreideWhitsonParameterization.LEGACY, system.getAqueousCO2Parameterization());

    system.setAqueousCO2Parameterization("m-sw");
    assertEquals(SoreideWhitsonParameterization.CHABAB_2019, system.getAqueousCO2Parameterization());
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      assertEquals(SoreideWhitsonParameterization.CHABAB_2019,
          ((PhaseSoreideWhitson) system.getPhase(phaseNumber)).getAqueousCO2Parameterization());
    }

    SystemSoreideWhitson clonedSystem = system.clone();
    assertEquals(SoreideWhitsonParameterization.CHABAB_2019, clonedSystem.getAqueousCO2Parameterization());

    system.clearAll();
    assertEquals(SoreideWhitsonParameterization.CHABAB_2019, system.getAqueousCO2Parameterization());
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      assertEquals(SoreideWhitsonParameterization.CHABAB_2019,
          ((PhaseSoreideWhitson) system.getPhase(phaseNumber)).getAqueousCO2Parameterization());
    }

    assertThrows(IllegalArgumentException.class, () -> system.setAqueousCO2Parameterization("unknown"));
  }

  @Test
  public void testChabab2019PublishedCorrelation() {
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    EosMixingRuleHandler.WhitsonSoreideMixingRule mixingRule = handler.new WhitsonSoreideMixingRule();

    assertEquals(-0.0344593471194527, mixingRule.calculateChabab2019AqueousCO2Kij(342.82, 3.01), 1.0e-14);
    assertEquals(-0.0709749877386868, mixingRule.calculateChabab2019AqueousCO2Kij(323.02, 1.13), 1.0e-14);
    assertEquals(0.00113995286294937, mixingRule.calculateChabab2019AqueousCO2KijdT(342.82, 3.01), 1.0e-16);
    assertEquals(-7.09064388609506e-7, mixingRule.calculateChabab2019AqueousCO2KijdTdT(3.01), 1.0e-18);
  }

  @Test
  public void testBurgoyneNielsen2026SelectorPropagation() {
    SystemSoreideWhitson system = new SystemSoreideWhitson(320.0, 100.0);
    system.addComponent("methane", 1.0);
    system.addComponent("water", 10.0);

    system.setSoreideWhitsonParameterization("BN_2026");
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026, system.getSoreideWhitsonParameterization());
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026, system.getAqueousCO2Parameterization());
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      PhaseSoreideWhitson phase = (PhaseSoreideWhitson) system.getPhase(phaseNumber);
      assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026, phase.getSoreideWhitsonParameterization());
      assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026, phase.getAqueousCO2Parameterization());
    }

    SystemSoreideWhitson clonedSystem = system.clone();
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026,
        clonedSystem.getSoreideWhitsonParameterization());

    system.clearAll();
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026, system.getSoreideWhitsonParameterization());
  }

  @Test
  public void testChabab2019Table2CO2Solubility() {
    double[][] points = { { 1.13, 323.02, 53.450, 0.01030, 0.010872 }, { 1.13, 322.97, 75.550, 0.01290, 0.013990 },
        { 1.13, 323.03, 100.350, 0.01510, 0.016205 }, { 1.13, 323.04, 145.080, 0.01700, 0.018000 },
        { 3.01, 342.82, 30.391, 0.00441, 0.003586 }, { 3.01, 342.81, 72.559, 0.00880, 0.007407 },
        { 3.01, 342.82, 100.910, 0.01057, 0.009160 } };
    double legacyHighSalinityAbsoluteDeviation = 0.0;
    double chababHighSalinityAbsoluteDeviation = 0.0;

    for (double[] point : points) {
      double legacyValue = calculateAqueousCO2MoleFraction(point[0], point[1], point[2],
          SoreideWhitsonParameterization.LEGACY);
      double chababValue = calculateAqueousCO2MoleFraction(point[0], point[1], point[2],
          SoreideWhitsonParameterization.CHABAB_2019);

      assertEquals(point[4], legacyValue, 2.0e-5, "Legacy default must remain backward compatible");
      assertEquals(point[3], chababValue, point[3] * 0.12,
          "Chabab parameterization should reproduce the Table 2 CO2 solubility point");

      if (point[0] > 3.0) {
        legacyHighSalinityAbsoluteDeviation += Math.abs(legacyValue / point[3] - 1.0);
        chababHighSalinityAbsoluteDeviation += Math.abs(chababValue / point[3] - 1.0);
      }
    }

    assertTrue(chababHighSalinityAbsoluteDeviation < legacyHighSalinityAbsoluteDeviation,
        "Chabab parameterization should reduce high-salinity deviation");
  }

  /**
   * Calculate the aqueous CO2 mole fraction for a Chabab et al. (2019) Table 2 condition.
   *
   * @param molality NaCl molality in mol/kg water
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @param parameterization aqueous CO2-water parameterization
   * @return calculated aqueous CO2 mole fraction
   */
  private double calculateAqueousCO2MoleFraction(double molality, double temperatureK, double pressureBara,
      SoreideWhitsonParameterization parameterization) {
    double waterMolesPerSecond = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    SystemSoreideWhitson system = new SystemSoreideWhitson(temperatureK, pressureBara);
    system.addComponent("CO2", 5.0);
    system.addComponent("water", waterMolesPerSecond);
    system.addSalinity(molality, "mole/sec");
    system.setTotalFlowRate(waterMolesPerSecond + 5.0, "mole/sec");
    system.createDatabase(true);
    system.setMixingRule(11);
    system.setAqueousCO2Parameterization(parameterization);
    system.setMultiPhaseCheck(true);
    system.init(0);
    system.init(1);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    return system.getPhase(PhaseType.AQUEOUS).getComponent("CO2").getx();
  }
}
