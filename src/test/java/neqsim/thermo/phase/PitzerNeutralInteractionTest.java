package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Tests PHREEQC-compatible neutral-solute Pitzer interaction families. */
class PitzerNeutralInteractionTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE = 298.15;

  @Test
  void lambdaAndZetaMatchPhreeqcActivityAndOsmoticPlacement() {
    PhasePitzer phase = createSingleNeutralBrine();
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int chloride = index(phase, "Cl-");
    phase.setLambda(carbonDioxide, carbonDioxide, 0.0);
    phase.setLambda(carbonDioxide, sodium, 0.10);
    phase.setLambda(carbonDioxide, chloride, -0.02);
    phase.setZeta(carbonDioxide, sodium, chloride, 0.03);

    double mNeutral = molality(phase, carbonDioxide);
    double mSodium = molality(phase, sodium);
    double mChloride = molality(phase, chloride);
    assertEquals(2.0 * mSodium * 0.10 - 2.0 * mChloride * 0.02 + mSodium * mChloride * 0.03,
        phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE), 1.0e-14);
    assertEquals(2.0 * mNeutral * 0.10 + mNeutral * mChloride * 0.03,
        phase.getNeutralPitzerLogGammaContribution(sodium, TEMPERATURE), 1.0e-14);
    assertEquals(-2.0 * mNeutral * 0.02 + mNeutral * mSodium * 0.03,
        phase.getNeutralPitzerLogGammaContribution(chloride, TEMPERATURE), 1.0e-14);
    assertEquals(mNeutral * mSodium * 0.10 - mNeutral * mChloride * 0.02 + mNeutral * mSodium * mChloride * 0.03,
        phase.getNeutralPitzerOsmoticContribution(TEMPERATURE), 1.0e-14);
  }

  @Test
  void repeatedNeutralLambdaUsesPhreeqcMultiplicity() {
    PhasePitzer phase = createSingleNeutralBrine();
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int chloride = index(phase, "Cl-");
    phase.setLambda(carbonDioxide, carbonDioxide, -0.0134);
    phase.setLambda(carbonDioxide, sodium, 0.0);
    phase.setLambda(carbonDioxide, chloride, 0.0);
    phase.setZeta(carbonDioxide, sodium, chloride, 0.0);

    double mCarbonDioxide = molality(phase, carbonDioxide);
    assertEquals(2.0 * mCarbonDioxide * -0.0134, phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE),
        1.0e-14);
    assertEquals(0.5 * mCarbonDioxide * mCarbonDioxide * -0.0134,
        phase.getNeutralPitzerOsmoticContribution(TEMPERATURE), 1.0e-14);
  }

  @Test
  void muAndEtaUsePhreeqcCombinatorialMultiplicities() {
    PhasePitzer phase = createHigherOrderPhase();
    int carbonDioxide = index(phase, "CO2");
    int methane = index(phase, "methane");
    int sodium = index(phase, "Na+");
    int potassium = index(phase, "K+");
    PitzerTemperatureFunction parameter = new PitzerTemperatureFunction(TEMPERATURE,
        new double[] { 0.04, 0.0, 0.0, 0.0, 0.0, 0.0 });

    PitzerNeutralInteraction mu = new PitzerNeutralInteraction(PhasePitzer.NEUTRAL_FAMILY_MU,
        new int[] { carbonDioxide, carbonDioxide, methane }, parameter);
    double mCarbonDioxide = molality(phase, carbonDioxide);
    double mMethane = molality(phase, methane);
    assertEquals(6.0 * mCarbonDioxide * mMethane * 0.04, mu.logGammaContribution(carbonDioxide, phase, TEMPERATURE),
        1.0e-14);
    assertEquals(3.0 * mCarbonDioxide * mCarbonDioxide * 0.04, mu.logGammaContribution(methane, phase, TEMPERATURE),
        1.0e-14);
    assertEquals(3.0 * mCarbonDioxide * mCarbonDioxide * mMethane * 0.04, mu.osmoticContribution(phase, TEMPERATURE),
        1.0e-14);

    PitzerNeutralInteraction eta = new PitzerNeutralInteraction(PhasePitzer.NEUTRAL_FAMILY_ETA,
        new int[] { carbonDioxide, sodium, potassium }, parameter);
    double mSodium = molality(phase, sodium);
    double mPotassium = molality(phase, potassium);
    assertEquals(mSodium * mPotassium * 0.04, eta.logGammaContribution(carbonDioxide, phase, TEMPERATURE), 1.0e-14);
    assertEquals(mCarbonDioxide * mPotassium * 0.04, eta.logGammaContribution(sodium, phase, TEMPERATURE), 1.0e-14);
    assertEquals(mCarbonDioxide * mSodium * mPotassium * 0.04, eta.osmoticContribution(phase, TEMPERATURE), 1.0e-14);
  }

  @Test
  void temperatureFunctionsCoverEveryNeutralFamily() {
    PhasePitzer phase = createHigherOrderPhase();
    int carbonDioxide = index(phase, "CO2");
    int methane = index(phase, "methane");
    int sodium = index(phase, "Na+");
    int potassium = index(phase, "K+");
    int chloride = index(phase, "Cl-");
    double[] coefficients = { 0.12, 250.0, -0.035, 4.2e-4, -3.1e-7, 18000.0 };
    phase.setLambdaTemperatureCoefficients(carbonDioxide, sodium, TEMPERATURE, coefficients);
    phase.setZetaTemperatureCoefficients(carbonDioxide, sodium, chloride, TEMPERATURE, coefficients);
    phase.setMuTemperatureCoefficients(carbonDioxide, carbonDioxide, methane, TEMPERATURE, coefficients);
    phase.setEtaTemperatureCoefficients(carbonDioxide, sodium, potassium, TEMPERATURE, coefficients);

    double expected = -0.11371073586719137;
    assertEquals(expected, phase.getLambda(carbonDioxide, sodium, 373.15), 2.0e-15);
    assertEquals(expected, phase.getZeta(carbonDioxide, sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expected, phase.getMu(methane, carbonDioxide, carbonDioxide, 373.15), 2.0e-15);
    assertEquals(expected, phase.getEta(potassium, carbonDioxide, sodium, 373.15), 2.0e-15);
  }

  @Test
  void configuredNeutralDatasetFailsClosedWhenRequiredRowsAreMissing() {
    PhasePitzer phase = createSingleNeutralBrine();
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    phase.setLambda(carbonDioxide, sodium, 0.10);

    PitzerNeutralParameterCoverage coverage = phase.auditNeutralPitzerParameterCoverage();
    assertEquals(2, coverage.getMissingLambdaPairs().size());
    assertEquals(1, coverage.getMissingZetaTuples().size());
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE));
    assertTrue(error.getMessage().contains("missingLambda"));
    assertTrue(error.getMessage().contains("missingZeta"));
  }

  @Test
  void waterAndPhaseOsmoticPathsRemainConsistentWithNeutralTerms() {
    PhasePitzer phase = createSingleNeutralBrine();
    int water = index(phase, "water");
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int chloride = index(phase, "Cl-");
    ComponentGePitzer waterComponent = (ComponentGePitzer) phase.getComponent(water);
    double ionicMolality = molality(phase, sodium) + molality(phase, chloride);
    double baselinePhasePhi = phase.getOsmoticCoefficientOfWater();
    double baselineWaterGamma = waterComponent.getGamma(phase, phase.getNumberOfComponents(), TEMPERATURE,
        phase.getPressure(), phase.getType());
    double baselineWaterPhi = -Math.log(baselineWaterGamma * waterComponent.getx()) * 1000.0 / (18.015 * ionicMolality);

    phase.setLambda(carbonDioxide, sodium, 0.10);
    phase.setLambda(carbonDioxide, chloride, -0.02);
    phase.setLambda(carbonDioxide, carbonDioxide, 0.0);
    phase.setZeta(carbonDioxide, sodium, chloride, 0.03);

    double neutralOsmoticSum = phase.getNeutralPitzerOsmoticContribution(TEMPERATURE);
    double totalSoluteMolality = ionicMolality + molality(phase, carbonDioxide);
    double expectedPhasePhi = 1.0
        + ((baselinePhasePhi - 1.0) * ionicMolality + 2.0 * neutralOsmoticSum) / totalSoluteMolality;
    assertEquals(expectedPhasePhi, phase.getOsmoticCoefficientOfWater(), 2.0e-12);

    double waterGamma = waterComponent.getGamma(phase, phase.getNumberOfComponents(), TEMPERATURE, phase.getPressure(),
        phase.getType());
    double waterActivity = waterGamma * waterComponent.getx();
    double phiFromWaterActivity = -Math.log(waterActivity) * 1000.0 / (18.015 * totalSoluteMolality);
    double expectedWaterPhi = 1.0
        + ((baselineWaterPhi - 1.0) * ionicMolality + 2.0 * neutralOsmoticSum) / totalSoluteMolality;
    assertEquals(expectedWaterPhi, phiFromWaterActivity, 2.0e-12);
  }

  @Test
  void cloneSerializationAndLegacyBypassProtectState() throws Exception {
    PhasePitzer phase = createSingleNeutralBrine();
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int chloride = index(phase, "Cl-");
    assertEquals(0.0, phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE), 0.0);

    configureCompleteSingleNeutralDataset(phase, carbonDioxide, sodium, chloride);
    double original = phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE);
    PhasePitzer clone = phase.clone();
    clone.setLambda(carbonDioxide, sodium, 0.25);
    double changed = clone.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE);
    assertNotEquals(original, changed);
    assertEquals(original, phase.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(phase);
    }
    PhasePitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (PhasePitzer) input.readObject();
    }
    assertEquals(original, restored.getNeutralPitzerLogGammaContribution(carbonDioxide, TEMPERATURE), 0.0);
  }

  @Test
  void rejectsSpeciesWithWrongFamilyRoles() {
    PhasePitzer phase = createSingleNeutralBrine();
    int water = index(phase, "water");
    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int chloride = index(phase, "Cl-");
    assertThrows(IllegalArgumentException.class, () -> phase.setLambda(water, sodium, 0.1));
    assertThrows(IllegalArgumentException.class, () -> phase.setLambda(carbonDioxide, water, 0.1));
    assertThrows(IllegalArgumentException.class, () -> phase.setZeta(carbonDioxide, chloride, sodium, 0.1));
    assertThrows(IllegalArgumentException.class, () -> phase.setEta(carbonDioxide, sodium, chloride, 0.1));
  }

  private static void configureCompleteSingleNeutralDataset(PhasePitzer phase, int neutral, int cation, int anion) {
    phase.setLambda(neutral, neutral, 0.0);
    phase.setLambda(neutral, cation, 0.10);
    phase.setLambda(neutral, anion, -0.02);
    phase.setZeta(neutral, cation, anion, 0.03);
  }

  private static PhasePitzer createSingleNeutralBrine() {
    SystemInterface system = new SystemPitzer(TEMPERATURE, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.2);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.init(0);
    return (PhasePitzer) system.getPhase(1);
  }

  private static PhasePitzer createHigherOrderPhase() {
    SystemInterface system = new SystemPitzer(TEMPERATURE, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.2);
    system.addComponent("methane", 0.1);
    system.addComponent("Na+", 0.8);
    system.addComponent("K+", 0.2);
    system.addComponent("Cl-", 1.0);
    system.init(0);
    return (PhasePitzer) system.getPhase(1);
  }

  private static int index(PhasePitzer phase, String componentName) {
    return phase.getComponent(componentName).getComponentNumber();
  }

  private static double molality(PhasePitzer phase, int componentIndex) {
    return phase.getComponent(componentIndex).getMolality(phase);
  }
}
