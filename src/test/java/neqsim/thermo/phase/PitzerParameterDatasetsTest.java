package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Scientific and provenance regressions for versioned Pitzer parameter datasets. */
class PitzerParameterDatasetsTest extends neqsim.NeqSimTest {
  private static final double REFERENCE_TEMPERATURE = 298.15;

  @Test
  void exactPhreeqcRowsAndCoverageAreAppliedAsOneDataset() {
    PhasePitzer phase = createPhase(REFERENCE_TEMPERATURE);
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);

    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int sulfate = index(phase, "SO4--");
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_NA2SO4_ID, phase.getParameterDatasetId());
    assertEquals(0.0273, phase.getBeta0ij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.956, phase.getBeta1ij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.003418, phase.getCphiij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.0134, phase.getLambda(carbonDioxide, carbonDioxide, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.085, phase.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.075, phase.getLambda(carbonDioxide, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.015, phase.getZeta(carbonDioxide, sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertTrue(phase.getPitzerParameterCoverage().isComplete());
    assertTrue(phase.auditNeutralPitzerParameterCoverage().isComplete());
  }

  @Test
  void co2ActivityMatchesIndependentIphreeqc373ReferenceStates() {
    // Generated with IPhreeqc 3.7.3, exact public-domain pitzer.dat blob
    // 324f852784be84650b77bd7f07f8316aafd8188b, MacInnes scaling disabled.
    assertPhreeqcCo2LogGamma(298.15, 0.009335381788874181, 0.2, 0.09997223611354554, 0.048445563893430764);
    assertPhreeqcCo2LogGamma(298.15, 0.08226801278545755, 2.0, 0.9999294802590848, 0.457786331769995);
    assertPhreeqcCo2LogGamma(373.15, 0.08136555608701694, 2.0, 0.9995278707441787, 0.44890462098965683);
    assertPhreeqcCo2LogGamma(423.15, 0.14946601518796535, 4.0, 1.9980632335950677, 0.836784371391815);
  }

  @Test
  void sodiumSulfateActivityAndOsmoticCoefficientMatchIphreeqcAt298K() {
    PhasePitzer phase = createPhase(REFERENCE_TEMPERATURE);
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);
    setMolality(phase, "CO2", 0.0);
    setMolality(phase, "Na+", 2.0);
    setMolality(phase, "SO4--", 0.9999992982438045);

    int sodium = index(phase, "Na+");
    int sulfate = index(phase, "SO4--");
    ComponentGePitzer sodiumComponent = (ComponentGePitzer) phase.getComponent(sodium);
    ComponentGePitzer sulfateComponent = (ComponentGePitzer) phase.getComponent(sulfate);
    double gammaSodium = sodiumComponent.getGamma(phase, phase.getNumberOfComponents(), REFERENCE_TEMPERATURE,
        phase.getPressure(), phase.getType());
    double gammaSulfate = sulfateComponent.getGamma(phase, phase.getNumberOfComponents(), REFERENCE_TEMPERATURE,
        phase.getPressure(), phase.getType());
    double meanGamma = Math.pow(gammaSodium * gammaSodium * gammaSulfate, 1.0 / 3.0);

    assertEquals(0.20137297119267375, meanGamma, 1.3e-3);
    assertEquals(0.6422470176005581, phase.getOsmoticCoefficientOfWater(), 7.0e-4);
  }

  @Test
  void carbonDioxideShowsHeldOutSodiumSulfateSaltingOutTrend() {
    // Dos Santos et al. (2020), DOI 10.1021/acs.jced.0c00230, independently reports
    // lower CO2 solubility as Na2SO4 molality increases. At fixed aqueous CO2 molality,
    // this parameter subset must therefore increase the CO2 activity coefficient.
    double oneMolalLogGamma = carbonDioxideLogGamma(373.15, 0.08, 2.0, 1.0);
    double twoMolalLogGamma = carbonDioxideLogGamma(373.15, 0.08, 4.0, 2.0);

    assertTrue(twoMolalLogGamma > oneMolalLogGamma, "CO2 activity coefficient must increase from 1 to 2 molal Na2SO4: "
        + oneMolalLogGamma + " -> " + twoMolalLogGamma);
  }

  @Test
  void validationEnvelopeAndUnsupportedTopologyAreExplicit() {
    assertTrue(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(303.15, 1.0));
    assertTrue(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(423.15, 2.0));
    assertFalse(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(298.15, 1.0));
    assertFalse(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(373.15, 2.1));

    SystemInterface incomplete = new SystemPitzer(REFERENCE_TEMPERATURE, 1.01325);
    incomplete.addComponent("water", 55.508);
    incomplete.addComponent("CO2", 0.1);
    incomplete.addComponent("Na+", 2.0);
    incomplete.init(0);
    PhasePitzer phase = (PhasePitzer) incomplete.getPhase(1);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase));
    assertTrue(error.getMessage().contains("SO4--"));
  }

  @Test
  void systemApiRetainsQualifiedDatasetAcrossInitializationAndClone() {
    SystemPitzer system = createSystem(REFERENCE_TEMPERATURE);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    system.init(0);
    PhasePitzer original = (PhasePitzer) system.getPhase(1);
    int carbonDioxide = index(original, "CO2");
    int sodium = index(original, "Na+");
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_NA2SO4_ID, original.getParameterDatasetId());

    SystemPitzer clonedSystem = system.clone();
    PhasePitzer cloned = (PhasePitzer) clonedSystem.getPhase(1);
    cloned.setLambda(carbonDioxide, sodium, 0.25);
    assertNotEquals(original.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE),
        cloned.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE));
    assertEquals(0.085, original.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE), 0.0);
  }

  @Test
  void qualifiedSubsetFailsClosedForAnAdditionalActiveNeutral() {
    SystemPitzer system = createSystem(REFERENCE_TEMPERATURE);
    system.addComponent("methane", 0.1);
    system.init(0);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);

    PitzerNeutralParameterCoverage coverage = phase.auditNeutralPitzerParameterCoverage();
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getMissingLambdaPairs().toString().contains("methane"));
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> phase.getNeutralPitzerLogGammaContribution(index(phase, "CO2"), REFERENCE_TEMPERATURE));
    assertTrue(error.getMessage().contains("methane"));
  }

  private static void assertPhreeqcCo2LogGamma(double temperature, double carbonDioxideMolality, double sodiumMolality,
      double sulfateMolality, double expectedNaturalLogGamma) {
    assertEquals(expectedNaturalLogGamma,
        carbonDioxideLogGamma(temperature, carbonDioxideMolality, sodiumMolality, sulfateMolality), 1.5e-5);
  }

  private static double carbonDioxideLogGamma(double temperature, double carbonDioxideMolality, double sodiumMolality,
      double sulfateMolality) {
    PhasePitzer phase = createPhase(temperature);
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);
    setMolality(phase, "CO2", carbonDioxideMolality);
    setMolality(phase, "Na+", sodiumMolality);
    setMolality(phase, "SO4--", sulfateMolality);
    int carbonDioxide = index(phase, "CO2");
    return phase.getNeutralPitzerLogGammaContribution(carbonDioxide, temperature);
  }

  private static PhasePitzer createPhase(double temperature) {
    return (PhasePitzer) createSystem(temperature).getPhase(1);
  }

  private static SystemPitzer createSystem(double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.1);
    system.addComponent("Na+", 2.0);
    system.addComponent("SO4--", 1.0);
    system.init(0);
    return system;
  }

  private static void setMolality(PhasePitzer phase, String componentName, double molality) {
    double targetMoles = molality * phase.getSolventWeight();
    double moleFraction = phase.getComponent(componentName).getx();
    phase.getComponent(componentName).setNumberOfMolesInPhase(targetMoles == 0.0 ? 0.0 : targetMoles / moleFraction);
  }

  private static int index(PhasePitzer phase, String componentName) {
    return phase.getComponent(componentName).getComponentNumber();
  }
}
