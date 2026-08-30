package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Smoke tests for the chemistry MCP runner.
 *
 * @author ESOL
 * @version 1.0
 */
class ChemistryRunnerTest {

  @Test
  void electrolyteScaleViaJson() {
    String json = "{\"analysis\":\"electrolyteScale\",\"temperature_C\":60,\"pH\":7.5,"
        + "\"pCO2_bar\":1.0,\"ca_mgL\":600,\"hco3_mgL\":300}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.has("data"));
    assertTrue(obj.getAsJsonObject("data").has("ionicStrengthMolKg")
        || obj.getAsJsonObject("data").has("ionicStrength_molkg") || obj.getAsJsonObject("data").has("ionicStrength"));
  }

  @Test
  void mechanisticCorrosionViaJson() {
    String json = "{\"analysis\":\"mechanisticCorrosion\",\"temperature_C\":60,"
        + "\"pressure_bara\":80,\"co2_mol\":0.05,\"velocity_ms\":2.0," + "\"diameter_m\":0.15,\"dose_mgL\":50}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void langmuirInhibitorViaJson() {
    String json = "{\"analysis\":\"langmuirInhibitor\",\"temperature_C\":60,"
        + "\"dose_mgL\":50,\"targetEfficiency\":0.5}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("data").has("efficiency"));
  }

  @Test
  void packedBedScavengerViaJson() {
    String json = "{\"analysis\":\"packedBedScavenger\",\"diameter_m\":0.5,"
        + "\"height_m\":2.0,\"k_per_s\":8.0,\"cInlet_molm3\":1.0,"
        + "\"flow_m3s\":0.005,\"nCells\":20,\"nTimeSteps\":50,\"simTime_s\":864000}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void rejectsUnknownAnalysis() {
    String out = ChemistryRunner.run("{\"analysis\":\"unknown\"}");
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void electrolyteScaleEquilibriumPitzerUsesAuthoritativePrecipitationOperation() {
    String input = "{\"analysis\":\"electrolyteScaleEquilibrium\",\"model\":\"pitzer\","
        + "\"dataset\":\"phreeqc-ca-mg-cl-so4\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"mineral\":\"CaSO4_A\","
        + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Ca++\":0.2," + "\"Mg++\":0.0,\"Cl-\":1.0,\"SO4--\":0.2}}";

    JsonObject result = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject();
    JsonObject data = result.getAsJsonObject("data");

    assertEquals("success", result.get("status").getAsString());
    assertEquals("ThermodynamicOperations.precipitateScale", data.get("authoritativeJavaOperation").getAsString());
    assertTrue(data.get("precipitatedSolid").getAsBoolean());
    assertEquals(1.0, data.get("finalSaturationRatio").getAsDouble(), 1.0e-6);
    assertTrue(data.get("complementarityViolation_log10SR").getAsDouble() <= 1.0e-6);
    assertTrue(data.get("maximumIonBalanceResidual_mol").getAsDouble() <= 1.0e-10);
    assertTrue(data.getAsJsonObject("aqueousPhaseState").get("normalizedNonNegative").getAsBoolean());
    assertEquals(0.0, data.getAsJsonObject("aqueousPhaseState").get("chargeResidual_molPerKgWater").getAsDouble(),
        1.0e-10);
    assertTrue(data.get("engineeringGatesPass").getAsBoolean());
    assertFalse(data.get("mineralTargetQualified").getAsBoolean());
    assertFalse(data.get("publicationReady").getAsBoolean());
  }

  @Test
  void electrolyteScaleEquilibriumElectrolyteCpaUsesItsOwnAqueousModel() {
    String input = "{\"analysis\":\"electrolyteScaleEquilibrium\",\"model\":\"electrolyte-cpa\","
        + "\"temperature_K\":298.15,\"pressure_bara\":1.01325,\"mineral\":\"CaSO4_A\","
        + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Ca++\":0.2," + "\"Cl-\":1.0,\"SO4--\":0.2}}";

    JsonObject data = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject().getAsJsonObject("data");

    assertEquals("electrolyte-cpa", data.get("model").getAsString());
    assertTrue(data.get("precipitatedSolid").getAsBoolean());
    assertEquals(1.0, data.get("finalSaturationRatio").getAsDouble(), 1.0e-6);
    assertTrue(data.get("maximumIonBalanceResidual_mol").getAsDouble() <= 1.0e-10);
    assertTrue(data.get("engineeringGatesPass").getAsBoolean());
    assertTrue(data.get("pitzerQualificationLevel").isJsonNull());
    assertFalse(data.get("publicationReady").getAsBoolean());
  }

  @Test
  void electrolyteScaleEquilibriumIsDeterministicAndChangedStateIsFresh() {
    String template = "{\"analysis\":\"electrolyteScaleEquilibrium\",\"model\":\"pitzer\","
        + "\"dataset\":\"phreeqc-ca-mg-cl-so4\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"mineral\":\"CaSO4_A\","
        + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Ca++\":%s," + "\"Mg++\":0.0,\"Cl-\":1.0,\"SO4--\":%s}}";
    String supersaturated = String.format(template, "0.2", "0.2");
    String undersaturated = String.format(template, "0.0001", "0.0001");

    JsonObject first = JsonParser.parseString(ChemistryRunner.run(supersaturated)).getAsJsonObject()
        .getAsJsonObject("data");
    JsonObject repeated = JsonParser.parseString(ChemistryRunner.run(supersaturated)).getAsJsonObject()
        .getAsJsonObject("data");
    JsonObject changed = JsonParser.parseString(ChemistryRunner.run(undersaturated)).getAsJsonObject()
        .getAsJsonObject("data");

    assertEquals(first, repeated);
    assertTrue(first.get("precipitatedSolid").getAsBoolean());
    assertFalse(changed.get("precipitatedSolid").getAsBoolean());
    assertTrue(changed.get("finalSaturationRatio").getAsDouble() < 1.0);
    assertEquals(0.0, changed.get("complementarityViolation_log10SR").getAsDouble(), 0.0);
  }

  @Test
  void electrolyteScaleEquilibriumRejectsNonElectroneutralInputBeforeCalculation() {
    String input = "{\"analysis\":\"electrolyteScaleEquilibrium\",\"model\":\"pitzer\","
        + "\"temperature_K\":298.15,\"pressure_bara\":1.01325,\"mineral\":\"CaSO4_A\","
        + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Cl-\":0.8}}";

    JsonObject result = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject();

    assertEquals("error", result.get("status").getAsString());
    assertTrue(result.getAsJsonArray("errors").get(0).getAsJsonObject().get("message").getAsString()
        .contains("not electroneutral"));
  }

  @Test
  void qualifiedPitzerTopologyIsAcceptedForItsObservableAndEnvelope() {
    String input = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"dataset\":\"phreeqc-na-k-cl\","
        + "\"validationTarget\":\"AQUEOUS_ACTIVITY_COEFFICIENTS\","
        + "\"components\":{\"water\":55.508,\"Na+\":0.5,\"K+\":0.5,\"Cl-\":1.0}}";

    JsonObject result = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject();
    JsonObject data = result.getAsJsonObject("data");

    assertEquals("success", result.get("status").getAsString());
    assertEquals("VALIDATED_WITHIN_DECLARED_ENVELOPE", data.get("qualificationLevel").getAsString());
    assertTrue(data.get("completeTopology").getAsBoolean());
    assertTrue(data.get("targetQualified").getAsBoolean());
    assertTrue(data.getAsJsonObject("stateRange").get("withinRange").getAsBoolean());
    assertTrue(data.getAsJsonObject("aqueousPhaseState").get("normalizedNonNegative").getAsBoolean());
    assertEquals(1.0, data.getAsJsonObject("aqueousPhaseState").get("moleFractionSum").getAsDouble(), 1.0e-12);
    assertTrue(data.get("publicationReady").getAsBoolean());
    assertEquals("ACCEPTED", data.get("decision").getAsString());
    assertEquals(0.0, data.getAsJsonObject("inputValidation").get("chargeResidual_mol").getAsDouble(), 1.0e-15);
  }

  @Test
  void PitzerQualificationRejectsUnqualifiedVleWithoutHidingActivityEvidence() {
    String input = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":319.63,"
        + "\"pressure_bara\":80.9,\"dataset\":\"phreeqc-co2-na2so4\"," + "\"validationTarget\":\"GAS_AQUEOUS_VLE\","
        + "\"components\":{\"water\":55.508,\"CO2\":0.6,\"Na+\":2.0,\"SO4--\":1.0}}";

    JsonObject data = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject().getAsJsonObject("data");

    assertTrue(data.get("completeTopology").getAsBoolean());
    assertFalse(data.get("targetQualified").getAsBoolean());
    assertFalse(data.get("publicationReady").getAsBoolean());
    assertTrue(data.get("qualificationDiagnostic").getAsString().contains("32.6-43.8%"));
    assertTrue(data.get("diagnostic").getAsString().contains("GAS_AQUEOUS_VLE"));
  }

  @Test
  void PitzerQualificationRejectsOutsideEvidenceEnvelopeAndChangedStateIsFresh() {
    String template = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":%s,"
        + "\"pressure_bara\":1.01325,\"dataset\":\"phreeqc-na-k-cl\","
        + "\"validationTarget\":\"WATER_ACTIVITY_AND_OSMOTIC_COEFFICIENT\","
        + "\"components\":{\"water\":55.508,\"Na+\":0.5,\"K+\":0.5,\"Cl-\":1.0}}";

    JsonObject inside = JsonParser.parseString(ChemistryRunner.run(String.format(template, "298.15"))).getAsJsonObject()
        .getAsJsonObject("data");
    JsonObject outside = JsonParser.parseString(ChemistryRunner.run(String.format(template, "450.0"))).getAsJsonObject()
        .getAsJsonObject("data");

    assertTrue(inside.get("publicationReady").getAsBoolean());
    assertFalse(outside.get("publicationReady").getAsBoolean());
    assertFalse(outside.getAsJsonObject("stateRange").get("withinRange").getAsBoolean());
    assertTrue(outside.get("diagnostic").getAsString().contains("outside"));
  }

  @Test
  void PitzerQualificationRejectsNonElectroneutralInputBeforeDatasetSelection() {
    String input = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"dataset\":\"phreeqc-na-k-cl\","
        + "\"validationTarget\":\"AQUEOUS_ACTIVITY_COEFFICIENTS\","
        + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Cl-\":0.8}}";

    JsonObject data = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject().getAsJsonObject("data");

    assertFalse(data.getAsJsonObject("inputValidation").get("valid").getAsBoolean());
    assertEquals(0.2, data.getAsJsonObject("inputValidation").get("chargeResidual_mol").getAsDouble(), 1.0e-15);
    assertFalse(data.get("publicationReady").getAsBoolean());
    assertEquals("REJECTED", data.get("decision").getAsString());
    assertFalse(data.has("datasetId"), "Dataset selection must not run for a rejected ionic feed");
  }

  @Test
  void PitzerQualificationExposesUnsupportedH2sNeutralSelfTopology() {
    String input = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"dataset\":\"auto\"," + "\"validationTarget\":\"REACTIVE_SPECIATION\","
        + "\"components\":{\"water\":55.508,\"H2S\":0.01}}";

    JsonObject result = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject();
    JsonObject data = result.getAsJsonObject("data");

    assertEquals("success", result.get("status").getAsString());
    assertFalse(data.get("completeTopology").getAsBoolean());
    assertFalse(data.get("publicationReady").getAsBoolean());
    assertTrue(
        data.getAsJsonObject("neutralCoverage").getAsJsonArray("missingLambdaPairs").toString().contains("H2S|H2S"));
  }

  @Test
  void PitzerQualificationDataIsDeterministicAcrossRepeatedCalls() {
    String input = "{\"analysis\":\"pitzerQualification\",\"temperature_K\":298.15,"
        + "\"pressure_bara\":1.01325,\"dataset\":\"phreeqc-na-k-cl\","
        + "\"validationTarget\":\"AQUEOUS_ACTIVITY_COEFFICIENTS\","
        + "\"components\":{\"Cl-\":1.0,\"water\":55.508,\"K+\":0.5,\"Na+\":0.5}}";

    JsonObject first = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject().getAsJsonObject("data");
    JsonObject second = JsonParser.parseString(ChemistryRunner.run(input)).getAsJsonObject().getAsJsonObject("data");

    assertEquals(first, second);
  }
}
