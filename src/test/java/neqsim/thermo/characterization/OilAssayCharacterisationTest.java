package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilAssayCharacterisationTest {
  @Test
  public void testMassBasisAssayApplicationAddsPseudoComponents() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut light = new AssayCut("Light").withWeightPercent(40.0).withSpecificGravity(0.75)
        .withAverageBoilingPointCelsius(200.0);
    AssayCut heavy = new AssayCut("Heavy").withWeightPercent(60.0).withApiGravity(25.0)
        .withAverageBoilingPointCelsius(350.0);

    characterisation.addCut(light);
    characterisation.addCut(heavy);
    characterisation.apply();

    double lightBoilingPoint = 200.0 + 273.15;
    double expectedLightMolarMass = 5.805e-5 * Math.pow(lightBoilingPoint, 2.3776) / Math.pow(0.75, 0.9371);
    ComponentInterface lightComponent = system.getComponent("Light_PC");
    assertNotNull(lightComponent);
    assertEquals(expectedLightMolarMass, lightComponent.getMolarMass(), 1e-8);
    assertEquals(0.4 / expectedLightMolarMass, lightComponent.getNumberOfmoles(), 1e-8);

    double heavyDensity = 141.5 / (25.0 + 131.5) * 0.999016;
    double heavyBoilingPoint = 350.0 + 273.15;
    double expectedHeavyMolarMass = 5.805e-5 * Math.pow(heavyBoilingPoint, 2.3776) / Math.pow(heavyDensity, 0.9371);
    ComponentInterface heavyComponent = system.getComponent("Heavy_PC");
    assertNotNull(heavyComponent);
    assertEquals(expectedHeavyMolarMass, heavyComponent.getMolarMass(), 1e-8);
    assertEquals(0.6 / expectedHeavyMolarMass, heavyComponent.getNumberOfmoles(), 1e-8);
  }

  @Test
  public void testVolumeBasisAssayConvertsToMassFractions() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    characterisation.addCut(
        new AssayCut("Light").withVolumePercent(40.0).withSpecificGravity(0.75).withAverageBoilingPointCelsius(200.0));
    characterisation.addCut(
        new AssayCut("Heavy").withVolumePercent(60.0).withApiGravity(25.0).withAverageBoilingPointCelsius(350.0));

    double heavyDensity = 141.5 / (25.0 + 131.5) * 0.999016;
    double totalRelativeMass = 0.4 * 0.75 + 0.6 * heavyDensity;
    double expectedLightMassFraction = 0.4 * 0.75 / totalRelativeMass;
    double expectedHeavyMassFraction = 0.6 * heavyDensity / totalRelativeMass;

    double[] massFractions = characterisation.getResolvedMassFractions();
    assertEquals(expectedLightMassFraction, massFractions[0], 1e-12);
    assertEquals(expectedHeavyMassFraction, massFractions[1], 1e-12);
    assertEquals(1.0, massFractions[0] + massFractions[1], 1e-12);

    characterisation.apply();
    assertEquals(1.0, reconstructedAssayMass(system, "Light_PC", "Heavy_PC"), 1e-10);
  }

  @Test
  public void testTotalMassScaling() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut cut = new AssayCut("Assay").withMassFraction(1.0).withSpecificGravity(0.82)
        .withAverageBoilingPointCelsius(300.0);
    characterisation.addCut(cut);
    characterisation.setTotalAssayMass(2.0);
    characterisation.apply();

    double boilingPoint = 300.0 + 273.15;
    double expectedMolarMass = 5.805e-5 * Math.pow(boilingPoint, 2.3776) / Math.pow(0.82, 0.9371);
    ComponentInterface component = system.getComponent("Assay_PC");
    assertNotNull(component);
    assertEquals(2.0 / expectedMolarMass, component.getNumberOfmoles(), 1e-8);
    assertEquals(2.0, component.getNumberOfmoles() * component.getMolarMass(), 1e-10);
  }

  @Test
  public void testCharacterisationClonedWithSystem() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation original = system.getOilAssayCharacterisation();
    original.clearCuts();
    original.addCut(new AssayCut("CloneTest").withMassFraction(1.0).withSpecificGravity(0.85)
        .withAverageBoilingPointCelsius(310.0));

    SystemInterface cloned = system.clone();
    OilAssayCharacterisation cloneCharacterisation = cloned.getOilAssayCharacterisation();
    assertEquals(1, cloneCharacterisation.getCuts().size());
    cloneCharacterisation.apply();

    assertTrue(Arrays.asList(cloned.getComponentNames()).contains("CloneTest_PC"));
    assertFalse(Arrays.asList(system.getComponentNames()).contains("CloneTest_PC"));
  }

  @Test
  public void testExplicitMolarMassUsesKgPerMol() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut cut = new AssayCut("ExplicitMW").withMassFraction(1.0).withSpecificGravity(0.8)
        .withMolarMassKgPerMol(0.150);
    characterisation.addCut(cut);
    characterisation.apply();

    ComponentInterface component = system.getComponent("ExplicitMW_PC");
    assertNotNull(component);
    assertEquals(0.150, component.getMolarMass(), 1e-12);
    assertEquals(1.0 / 0.150, component.getNumberOfmoles(), 1e-10);
  }

  @Test
  public void testGramPerMolHelperConvertsExplicitMolarMass() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    characterisation.addCut(
        new AssayCut("ExplicitMW").withMassFraction(1.0).withSpecificGravity(0.8).withMolarMassGramPerMol(150.0));
    characterisation.apply();

    assertEquals(0.150, system.getComponent("ExplicitMW_PC").getMolarMass(), 1e-12);
  }

  @Test
  public void testCalculatedMolarMassStillRequiresBoilingPoint() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(new AssayCut("NoBoilingPoint").withMassFraction(1.0).withSpecificGravity(0.8));

    IllegalStateException exception = assertThrows(IllegalStateException.class, characterisation::apply);
    assertTrue(exception.getMessage().contains("Average boiling point missing"));
  }

  @Test
  public void testMixedExplicitAndCalculatedMolarMass() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut explicitCut = new AssayCut("Explicit").withMassFraction(0.5).withSpecificGravity(0.8)
        .withMolarMassKgPerMol(0.120);
    AssayCut calculatedCut = new AssayCut("Calculated").withMassFraction(0.5).withSpecificGravity(0.85)
        .withAverageBoilingPointCelsius(250.0);

    characterisation.addCut(explicitCut);
    characterisation.addCut(calculatedCut);
    characterisation.apply();

    assertEquals(0.120, system.getComponent("Explicit_PC").getMolarMass(), 1e-12);
    assertTrue(Math.abs(system.getComponent("Calculated_PC").getMolarMass() - 0.120) > 1e-3);
    assertEquals(1.0, reconstructedAssayMass(system, "Explicit_PC", "Calculated_PC"), 1e-10);
  }

  @Test
  public void testMixedMassAndVolumeBasisIsRejected() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(
        new AssayCut("MassCut").withMassFraction(0.4).withSpecificGravity(0.75).withAverageBoilingPointCelsius(180.0));
    characterisation.addCut(new AssayCut("VolumeCut").withVolumeFraction(0.6).withSpecificGravity(0.85)
        .withAverageBoilingPointCelsius(320.0));

    IllegalStateException exception = assertThrows(IllegalStateException.class, characterisation::apply);
    assertTrue(exception.getMessage().contains("cannot mix"));
    assertFalse(system.hasComponent("MassCut_PC", false));
    assertFalse(system.hasComponent("VolumeCut_PC", false));
  }

  @Test
  public void testCutWithTwoFractionBasesIsRejected() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(new AssayCut("Ambiguous").withMassFraction(1.0).withVolumeFraction(1.0)
        .withSpecificGravity(0.8).withAverageBoilingPointCelsius(250.0));

    IllegalStateException exception = assertThrows(IllegalStateException.class, characterisation::apply);
    assertTrue(exception.getMessage().contains("exactly one"));
  }

  @Test
  public void testIncompleteAssayIsRejectedInsteadOfSilentlyRenormalized() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(
        new AssayCut("Cut1").withMassFraction(0.4).withSpecificGravity(0.75).withAverageBoilingPointCelsius(180.0));
    characterisation.addCut(
        new AssayCut("Cut2").withMassFraction(0.4).withSpecificGravity(0.85).withAverageBoilingPointCelsius(320.0));

    IllegalStateException exception = assertThrows(IllegalStateException.class, characterisation::apply);
    assertTrue(exception.getMessage().contains("sum to 1.0"));
  }

  @Test
  public void testRoundingScaleClosureIsNormalized() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(
        new AssayCut("Cut1").withMassFraction(0.3333).withSpecificGravity(0.75).withAverageBoilingPointCelsius(180.0));
    characterisation.addCut(
        new AssayCut("Cut2").withMassFraction(0.3333).withSpecificGravity(0.82).withAverageBoilingPointCelsius(280.0));
    characterisation.addCut(
        new AssayCut("Cut3").withMassFraction(0.3333).withSpecificGravity(0.90).withAverageBoilingPointCelsius(420.0));

    double[] massFractions = characterisation.getResolvedMassFractions();
    assertEquals(1.0, massFractions[0] + massFractions[1] + massFractions[2], 1e-12);
  }

  @Test
  public void testDensityKgPerCubicMetreIsNormalizedBeforeCorrelation() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(new AssayCut("DensityUnits").withMassFraction(1.0).withDensityKgPerCubicMetre(850.0)
        .withAverageBoilingPointCelsius(300.0));
    characterisation.apply();

    double boilingPoint = 300.0 + 273.15;
    double expectedMolarMass = 5.805e-5 * Math.pow(boilingPoint, 2.3776) / Math.pow(0.85, 0.9371);
    assertEquals(expectedMolarMass, system.getComponent("DensityUnits_PC").getMolarMass(), 1e-10);
  }

  @Test
  public void testNegativeApiGravityIsSupportedForDenseCuts() {
    AssayCut denseCut = new AssayCut("Residue").withMassFraction(1.0).withApiGravity(-5.0)
        .withAverageBoilingPointCelsius(520.0);
    assertTrue(denseCut.resolveDensity() > 1.0);
  }

  @Test
  public void testTBPCutBoundariesCreateVolumeBasisAssayAndPreserveRanges() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    double[] cumulativeVolumePercent = { 0.0, 20.0, 55.0, 80.0, 100.0 };
    double[] boilingPointCelsius = { 90.0, 180.0, 270.0, 380.0, 520.0 };
    double[] specificGravity = { 0.70, 0.76, 0.84, 0.93 };
    characterisation.addTBPCutBoundariesCelsius("TBP", cumulativeVolumePercent, boilingPointCelsius, specificGravity);

    assertEquals(4, characterisation.getCuts().size());
    AssayCut firstCut = characterisation.getCuts().get(0);
    assertTrue(firstCut.hasBoilingRange());
    assertEquals(90.0 + 273.15, firstCut.getLowerBoilingPointKelvin(), 1e-12);
    assertEquals(180.0 + 273.15, firstCut.getUpperBoilingPointKelvin(), 1e-12);
    assertEquals(135.0 + 273.15, firstCut.resolveAverageBoilingPoint(), 1e-12);

    double[] massFractions = characterisation.getResolvedMassFractions();
    assertEquals(1.0, massFractions[0] + massFractions[1] + massFractions[2] + massFractions[3], 1e-12);

    characterisation.apply();
    assertTrue(system.hasComponent("TBP1_PC", false));
    assertTrue(system.hasComponent("TBP4_PC", false));
    assertEquals(1.0, reconstructedAssayMass(system, "TBP1_PC", "TBP2_PC", "TBP3_PC", "TBP4_PC"), 1e-10);
  }

  @Test
  public void testInvalidTBPCurveIsRejectedBeforeCutsAreAdded() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    assertThrows(IllegalArgumentException.class,
        () -> characterisation.addTBPCutBoundariesCelsius("TBP", new double[] { 0.0, 60.0, 50.0, 100.0 },
            new double[] { 90.0, 200.0, 300.0, 500.0 }, new double[] { 0.7, 0.8, 0.9 }));
    assertTrue(characterisation.getCuts().isEmpty());
  }

  @Test
  public void testRepeatedApplyIsRejectedWithoutDoublingAssay() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(
        new AssayCut("Once").withMassFraction(1.0).withSpecificGravity(0.82).withAverageBoilingPointCelsius(300.0));

    characterisation.apply();
    double firstMass = reconstructedAssayMass(system, "Once_PC");
    assertThrows(IllegalStateException.class, characterisation::apply);
    assertEquals(firstMass, reconstructedAssayMass(system, "Once_PC"), 1e-12);
  }

  @Test
  public void testDuplicateCutNamesAreRejectedBeforeMutation() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();
    characterisation.addCut(new AssayCut("Duplicate").withMassFraction(0.5).withSpecificGravity(0.75)
        .withAverageBoilingPointCelsius(180.0));
    characterisation.addCut(new AssayCut("Duplicate").withMassFraction(0.5).withSpecificGravity(0.85)
        .withAverageBoilingPointCelsius(320.0));

    assertThrows(IllegalStateException.class, characterisation::apply);
    assertFalse(system.hasComponent("Duplicate_PC", false));
  }

  @Test
  public void testFractionAndPercentInputsAreUnambiguous() {
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("BadFraction").withMassFraction(40.0));
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("BadPercent").withWeightPercent(120.0));
  }

  private static double reconstructedAssayMass(SystemInterface system, String... componentNames) {
    double mass = 0.0;
    for (String componentName : componentNames) {
      ComponentInterface component = system.getComponent(componentName);
      mass += component.getNumberOfmoles() * component.getMolarMass();
    }
    return mass;
  }
}
