package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.gerg.NeqSimEOSCG;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for pure-carbon-dioxide EOS-CG volume-energy flashes. */
class VUFlashEOSCGTest {
  private static final double RELATIVE_TOLERANCE = 2.0e-8;

  @Test
  void directEOSCGEvaluationIsFiniteAtInitialDenseState() {
    SystemInterface system = new SystemEOSCGEos(294.25, 125.4);
    system.addComponent("CO2", 1.0);
    system.init(0);

    NeqSimEOSCG eosCG = new NeqSimEOSCG(system.getPhase(0));
    double molarDensityMolPerL = eosCG.getMolarDensity();
    double[] properties = eosCG.propertiesEOSCG();

    assertTrue(Double.isFinite(molarDensityMolPerL));
    assertTrue(molarDensityMolPerL > 0.0);
    assertTrue(Double.isFinite(properties[0]));
    assertTrue(properties[1] > 0.0);
    assertEquals(12540.0, eosCG.getPressure(molarDensityMolPerL), RELATIVE_TOLERANCE * 12540.0);
    assertEquals(12540.0, eosCG.propertiesEOSCG(molarDensityMolPerL)[0], RELATIVE_TOLERANCE * 12540.0);
    assertEquals(system.getPhase(0).getDensity_EOSCG(), system.getPhase(0).getDensity("kg/m3"),
        RELATIVE_TOLERANCE * system.getPhase(0).getDensity("kg/m3"));
  }

  @ParameterizedTest
  @CsvSource({ "10.0, 0.50", "30.0, 0.05", "52.0, 0.25" })
  void twoPhaseRoundTripPreservesVolumeEnergyAndPhaseCount(double pressureBar, double vaporFraction) throws Exception {
    SystemInterface reference = createSaturationReference(pressureBar, vaporFraction);
    double targetVolumeM3 = reference.getVolume("m3");
    double targetInternalEnergyJ = reference.getInternalEnergy("J");
    SystemInterface system = createPureCarbonDioxideSystem(294.25, 125.4);

    new ThermodynamicOperations(system).VUflash(targetVolumeM3, targetInternalEnergyJ, "m3", "J");
    system.init(3);

    assertEquals(2, system.getNumberOfPhases(),
        "reference pressure=" + pressureBar + " bar, vapor fraction=" + vaporFraction + ", result temperature="
            + system.getTemperature() + " K, result pressure=" + system.getPressure() + " bar");
    assertSpecifications(system, targetVolumeM3, targetInternalEnergyJ);
  }

  @Test
  void singlePhaseRoundTripPreservesVolumeAndEnergy() {
    SystemInterface system = createPureCarbonDioxideSystem(294.25, 125.4);
    double targetVolumeM3 = system.getVolume("m3");
    double targetInternalEnergyJ = system.getInternalEnergy("J");

    new ThermodynamicOperations(system).VUflash(targetVolumeM3, targetInternalEnergyJ, "m3", "J");
    system.init(3);

    assertEquals(1, system.getNumberOfPhases());
    assertSpecifications(system, targetVolumeM3, targetInternalEnergyJ);
  }

  @Test
  void infeasibleSpecificationFailsExplicitly() {
    SystemInterface system = createPureCarbonDioxideSystem(294.25, 125.4);
    double targetVolumeM3 = system.getVolume("m3");
    double infeasibleInternalEnergyJ = system.getInternalEnergy("J") + 1.0e12;

    assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(system).VUflash(targetVolumeM3, infeasibleInternalEnergyJ, "m3", "J"));
  }

  @Test
  void nearCriticalDegenerateSaturationIsNotAcceptedAsSinglePhase() throws Exception {
    SystemInterface reference = createSaturationReference(70.0, 0.5);
    double targetVolumeM3 = reference.getVolume("m3");
    double targetInternalEnergyJ = reference.getInternalEnergy("J");
    SystemInterface system = createPureCarbonDioxideSystem(294.25, 125.4);

    try {
      new ThermodynamicOperations(system).VUflash(targetVolumeM3, targetInternalEnergyJ, "m3", "J");
      system.init(3);
      assertEquals(2, system.getNumberOfPhases());
      PhaseInterface gasPhase = phaseOfType(system, PhaseType.GAS);
      PhaseInterface liquidPhase = otherPhase(system, gasPhase);
      assertNotEquals(gasPhase.getDensity("kg/m3"), liquidPhase.getDensity("kg/m3"), 1.0);
    } catch (IllegalStateException expectedFailure) {
      assertTrue(expectedFailure.getMessage().contains("could not find a stable"));
    }
  }

  @ParameterizedTest
  @CsvSource({ "0.001", "0.999" })
  void nearSaturationLimitStateRemainsTwoPhase(double vaporFraction) throws Exception {
    SystemInterface reference = createSaturationReference(52.0, vaporFraction);
    double targetVolumeM3 = reference.getVolume("m3");
    double targetInternalEnergyJ = reference.getInternalEnergy("J");
    SystemInterface system = createPureCarbonDioxideSystem(294.25, 125.4);

    new ThermodynamicOperations(system).VUflash(targetVolumeM3, targetInternalEnergyJ, "m3", "J");
    system.init(3);

    assertEquals(2, system.getNumberOfPhases());
    assertSpecifications(system, targetVolumeM3, targetInternalEnergyJ);
  }

  @Test
  void compressedLiquidTPFlashSelectsDenseRoot() {
    SystemInterface system = new SystemEOSCGEos(286.570871248841, 53.400617864310);
    system.addComponent("CO2", 1.0);
    system.setMultiPhaseCheck(true);

    new ThermodynamicOperations(system).TPflash();
    system.init(3);

    assertEquals(1, system.getNumberOfPhases());
    assertEquals(5.221579133419594, system.getVolume(), RELATIVE_TOLERANCE * 5.221579133419594);
    assertTrue(system.getDensity("kg/m3") > 800.0);
  }

  @Test
  void saturatedPhasesUseDistinctEOSCGDensityRoots() throws Exception {
    SystemInterface system = createPureCarbonDioxideSystem(285.0, 52.0);

    new ThermodynamicOperations(system).bubblePointTemperatureFlash();
    system.init(3);

    assertEquals(2, system.getNumberOfPhases());
    PhaseInterface gasPhase = phaseOfType(system, PhaseType.GAS);
    PhaseInterface liquidPhase = otherPhase(system, gasPhase);
    assertNotEquals(gasPhase.getDensity("kg/m3"), liquidPhase.getDensity("kg/m3"), 1.0);
    assertPhasePropertiesMatchDirectEOSCG(gasPhase);
    assertPhasePropertiesMatchDirectEOSCG(liquidPhase);
  }

  private static void assertSpecifications(SystemInterface system, double targetVolumeM3,
      double targetInternalEnergyJ) {
    assertEquals(targetVolumeM3, system.getVolume("m3"), RELATIVE_TOLERANCE * targetVolumeM3);
    assertEquals(targetInternalEnergyJ, system.getInternalEnergy("J"),
        RELATIVE_TOLERANCE * Math.abs(targetInternalEnergyJ));
  }

  private static SystemInterface createSaturationReference(double pressureBar, double vaporFraction) throws Exception {
    SystemInterface system = createPureCarbonDioxideSystem(285.0, pressureBar);
    new ThermodynamicOperations(system).bubblePointTemperatureFlash();
    int gasPhaseIndex = phaseIndex(system, PhaseType.GAS);
    int liquidPhaseIndex = otherPhaseIndex(system, gasPhaseIndex);
    system.setBeta(gasPhaseIndex, vaporFraction);
    system.setBeta(liquidPhaseIndex, 1.0 - vaporFraction);
    system.init(3);
    return system;
  }

  private static SystemInterface createPureCarbonDioxideSystem(double temperatureK, double pressureBar) {
    SystemInterface system = new SystemEOSCGEos(temperatureK, pressureBar);
    system.addComponent("CO2", 1.0);
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private static PhaseInterface phaseOfType(SystemInterface system, PhaseType phaseType) {
    return system.getPhase(phaseIndex(system, phaseType));
  }

  private static int phaseIndex(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    throw new AssertionError("Missing phase type " + phaseType);
  }

  private static PhaseInterface otherPhase(SystemInterface system, PhaseInterface phase) {
    return system.getPhase(otherPhaseIndex(system, phaseIndex(system, phase.getType())));
  }

  private static int otherPhaseIndex(SystemInterface system, int phaseIndex) {
    for (int candidate = 0; candidate < system.getNumberOfPhases(); candidate++) {
      if (candidate != phaseIndex) {
        return candidate;
      }
    }
    throw new AssertionError("Missing second fluid phase");
  }

  private static void assertPhasePropertiesMatchDirectEOSCG(PhaseInterface phase) {
    double[] directProperties = phase.getProperties_EOSCG();
    assertEquals(directProperties[6], phase.getInternalEnergy("J/mol"),
        RELATIVE_TOLERANCE * Math.max(Math.abs(directProperties[6]), 1.0));
    assertEquals(directProperties[8], phase.getEntropy("J/molK"),
        RELATIVE_TOLERANCE * Math.max(Math.abs(directProperties[8]), 1.0));
  }

}
