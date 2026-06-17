package neqsim.process.safety.opendrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Tests for open-drain review functionality.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class OpenDrainReviewEngineTest {

  /**
   * Verifies that complete synthetic STID evidence returns a passing review.
   */
  @Test
  void testCompliantOpenDrainAreaPasses() {
    OpenDrainReviewInput input = new OpenDrainReviewInput().setProjectName("Synthetic drain review");
    input.addItem(new OpenDrainReviewItem().setAreaId("OD-A01").setAreaType("process area")
        .setDrainSystemType("hazardous open drain")
        .addSourceReference("synthetic STID drawing 001")
        .put("standards", "NORSOK S-001; NORSOK P-002; ISO 13702")
        .put("sourceHasFlammableOrHazardousLiquid", Boolean.TRUE)
        .put("hasOpenDrainMeasures", Boolean.TRUE).put("drainageCapacityKgPerS", 12.0)
        .put("fireWaterCapacityKgPerS", 6.0).put("liquidLeakRateKgPerS", 5.0)
        .put("backflowPrevented", Boolean.TRUE)
        .put("closedOpenDrainInteractionPrevented", Boolean.TRUE)
        .put("hazardousNonHazardousPhysicallySeparated", Boolean.TRUE)
        .put("sealDesignedForMaxBackpressure", Boolean.TRUE)
        .put("ventTerminatedSafe", Boolean.TRUE).put("openDrainDependsOnUtility", Boolean.FALSE));

    OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(input);
    JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();

    assertEquals("success", json.get("status").getAsString());
    assertEquals("PASS", json.get("overallVerdict").getAsString());
    assertEquals(1, json.get("itemCount").getAsInt());
    assertTrue(report.toJson().contains("NORSOK S-001"));
  }

  /**
   * Verifies that top-level stidData arrays merge by area identifier and fail unsafe designs.
   */
  @Test
  void testStidDataMergesByAreaAndFailsUnsafeCapacityAndSump() {
    String json = "{\n" + "  \"projectName\": \"Synthetic STID open drain\",\n"
        + "  \"stidData\": {\n" + "    \"openDrainAreas\": [{\n"
        + "      \"areaId\": \"OD-A02\",\n"
        + "      \"areaType\": \"process hazardous area\",\n"
        + "      \"drainSystemType\": \"hazardous open drain\",\n"
        + "      \"standards\": [\"NORSOK P-002\", \"ISO 13702\"],\n"
        + "      \"sourceHasFlammableOrHazardousLiquid\": true,\n"
        + "      \"hasOpenDrainMeasures\": true,\n"
        + "      \"drainageCapacityKgPerS\": 8.0,\n"
        + "      \"fireWaterCapacityKgPerS\": 6.0,\n"
        + "      \"liquidLeakRateKgPerS\": 5.0,\n"
        + "      \"backflowPrevented\": true,\n"
        + "      \"closedOpenDrainInteractionPrevented\": true,\n"
        + "      \"hazardousNonHazardousPhysicallySeparated\": true,\n"
        + "      \"sealDesignedForMaxBackpressure\": true,\n"
        + "      \"ventTerminatedSafe\": true\n" + "    }],\n"
        + "    \"drainSystems\": [{\n" + "      \"areaId\": \"OD-A02\",\n"
        + "      \"commonCaissonOrSump\": true,\n"
        + "      \"nonHazardousBackflowPrevented\": false\n" + "    }]\n" + "  }\n" + "}";

    OpenDrainReviewInput input = OpenDrainReviewInput.fromJson(json);
    assertEquals(1, input.getItems().size());

    OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(input);
    JsonObject output = JsonParser.parseString(report.toJson()).getAsJsonObject();

    assertEquals("FAIL", output.get("overallVerdict").getAsString());
    assertEquals(1, output.get("failedItems").getAsInt());
    assertTrue(report.toJson().contains("OD-S001-9.4.1-CAPACITY"));
    assertTrue(report.toJson().contains("OD-S001-9.4.2-COMMON-SUMP"));
  }

  /**
   * Verifies that optional tagreader evidence is carried into the report and can drive a warning.
   */
  @Test
  void testOptionalTagreaderEvidenceIsRetained() {
    OpenDrainReviewInput input = new OpenDrainReviewInput().setProjectName("Tagreader drain evidence");
    input.addItem(new OpenDrainReviewItem().setAreaId("OD-A03").setAreaType("process area")
        .setDrainSystemType("hazardous open drain").put("standards", "NORSOK P-002; ISO 13702")
        .put("sourceHasFlammableOrHazardousLiquid", Boolean.TRUE)
        .put("hasOpenDrainMeasures", Boolean.TRUE).put("drainageCapacityKgPerS", 15.0)
        .put("fireWaterCapacityKgPerS", 7.0).put("liquidLeakRateKgPerS", 5.0)
        .put("backflowPrevented", Boolean.TRUE)
        .put("closedOpenDrainInteractionPrevented", Boolean.TRUE)
        .put("hazardousNonHazardousPhysicallySeparated", Boolean.TRUE)
        .put("sealDesignedForMaxBackpressure", Boolean.TRUE)
        .put("ventTerminatedSafe", Boolean.TRUE).put("openDrainDependsOnUtility", Boolean.FALSE)
        .put("tagreaderSource", "synthetic PI export").put("sumpHighLevelEvents", 2.0)
        .put("observedBackflowEvents", 0.0));

    OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(input);

    assertEquals("PASS_WITH_WARNINGS", report.getOverallVerdict());
    assertTrue(report.toJson().contains("synthetic PI export"));
    assertTrue(report.toJson().contains("sump high-level events"));
  }

  /**
   * Verifies that NeqSim process and thermo calculations can populate review evidence directly.
   */
  @Test
  void testNeqSimStreamEvidenceDrivesOpenDrainReview() {
    SystemInterface fluid = new SystemSrkEos(293.15, 8.0);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");
    Stream liquidStream = new Stream("OD process liquid", fluid);
    liquidStream.setFlowRate(8.0, "kg/sec");
    liquidStream.run();

    OpenDrainProcessEvidenceCalculator.DesignBasis basis =
        new OpenDrainProcessEvidenceCalculator.DesignBasis().setAreaId("OD-NEQSIM-01")
            .setSourceReference("NeqSim process model: OD process liquid")
            .setFireWaterAreaM2(20.0).setFireWaterApplicationRateLPerMinM2(18.0)
            .setDrainPipeDiameterM(0.20).setAvailableDrainHeadM(1.0)
            .setCredibleLeakFractionOfLiquidFlow(1.0)
            .setMaximumCredibleLiquidLeakRateKgPerS(5.0);

    OpenDrainReviewInput input = OpenDrainProcessEvidenceCalculator.createInputFromStream(
        "NeqSim calculated open drain review", liquidStream, basis);
    OpenDrainReviewItem item = input.getItems().get(0);

    assertEquals(5.0, item.getDouble(Double.NaN, "liquidLeakRateKgPerS"), 1.0e-8);
    assertEquals(8.0, item.getDouble(Double.NaN, "pressureBara"), 1.0e-8);
    assertTrue(item.getDouble(0.0, "liquidDensityKgPerM3") > 500.0);
    assertTrue(item.getDouble(0.0, "fireWaterCapacityKgPerS") > 0.0);
    assertTrue(item.getDouble(0.0, "drainageCapacityKgPerS") > 10.0);

    OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(input);

    assertEquals("PASS", report.getOverallVerdict());
    assertTrue(report.toJson().contains("NeqSim process and thermodynamic evidence"));
    assertTrue(report.toJson().contains("liquidDensityKgPerM3"));
  }

  /**
   * Verifies the hydraulic capacity calculation uses density, head, and opposing pressure.
   */
  @Test
  void testDrainCapacityUsesHydraulicPressureHead() {
    double freeDrainCapacity = OpenDrainProcessEvidenceCalculator.calculateGravityDrainCapacityKgPerS(
        1000.0, 0.10, 1.0, 0.62, 0.0);
    double blockedDrainCapacity = OpenDrainProcessEvidenceCalculator.calculateGravityDrainCapacityKgPerS(
        1000.0, 0.10, 1.0, 0.62, 0.20);

    assertTrue(freeDrainCapacity > 0.0);
    assertEquals(0.0, blockedDrainCapacity, 1.0e-12);
  }
}