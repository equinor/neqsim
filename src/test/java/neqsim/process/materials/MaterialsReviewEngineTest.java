package neqsim.process.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for process-wide materials review functionality.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class MaterialsReviewEngineTest {

  /**
   * Verifies that a wet sour CO2 line produces corrosion and material recommendations.
   */
  @Test
  void testWetSourLineProducesMaterialRecommendation() {
    MaterialsReviewInput input = new MaterialsReviewInput();
    MaterialServiceEnvelope service = new MaterialServiceEnvelope().set("temperature_C", 85.0)
        .set("pressure_bara", 95.0).set("co2_mole_fraction", 0.04).set("h2s_mole_fraction", 0.0008)
        .set("free_water", Boolean.TRUE).set("chloride_mg_per_l", 55000.0).set("pH", 5.2)
        .set("flow_velocity_m_per_s", 7.5).set("nominal_wall_thickness_mm", 18.0)
        .set("current_wall_thickness_mm", 15.2).set("minimum_required_thickness_mm", 11.0);
    input.addItem(new MaterialReviewItem().setTag("DEMO-LINE-001").setEquipmentType("Pipeline")
        .setExistingMaterial("Carbon Steel API 5L X65").setServiceEnvelope(service)
        .addSourceReference("synthetic STID line-list row 1"));

    MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(input);
    JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();

    assertEquals("success", json.get("status").getAsString());
    assertEquals(1, json.get("itemCount").getAsInt());
    JsonObject firstItem = json.getAsJsonArray("items").get(0).getAsJsonObject();
    assertNotNull(firstItem.getAsJsonObject("recommendation").get("recommendedMaterial"));
    assertTrue(report.toJson().contains("CO2 corrosion"));
    assertTrue(report.toJson().contains("Sour service"));
    assertTrue(report.toJson().contains("NORSOK M-001"));
  }

  /**
   * Verifies that dry CO2 service is treated as a dry-operation check rather than wet corrosion.
   */
  @Test
  void testDryCo2ServiceUsesDryOperationScreening() {
    MaterialsReviewInput input = new MaterialsReviewInput();
    MaterialServiceEnvelope service = new MaterialServiceEnvelope().set("temperature_C", 45.0)
        .set("pressure_bara", 70.0).set("co2_mole_fraction", 0.03).set("free_water", Boolean.FALSE);
    input.addItem(new MaterialReviewItem().setTag("G-100").setEquipmentType("Gas piping")
        .setExistingMaterial("Carbon Steel").setServiceEnvelope(service));

    MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(input);
    JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();

    assertEquals("success", json.get("status").getAsString());
    assertTrue(report.toJson().contains("free water was not indicated"));
    assertTrue(json.get("failedItems").getAsInt() == 0);
  }

  /**
   * Verifies that normalized STID records from different arrays merge into a single reviewed item.
   */
  @Test
  void testStidSourceMergesMaterialAndInspectionRecordsByTag() {
    String stidJson = "{\n" + "  \"projectName\": \"Synthetic STID merge test\",\n"
        + "  \"materialsRegister\": [{\n" + "    \"tag\": \"DEMO-PIPING-010\",\n"
        + "    \"equipmentType\": \"Piping\",\n" + "    \"material\": \"316L\",\n"
        + "    \"service\": {\"temperature_C\": 95.0, \"pressure_bara\": 20.0, "
        + "\"free_water\": true, \"chloride_mg_per_l\": 120000.0}\n" + "  }],\n"
        + "  \"inspectionData\": [{\n" + "    \"tag\": \"DEMO-PIPING-010\",\n"
        + "    \"nominalWallThicknessMm\": 10.0,\n" + "    \"currentWallThicknessMm\": 7.0,\n"
        + "    \"minimumRequiredThicknessMm\": 5.5\n" + "  }]\n" + "}";

    MaterialsReviewInput input = new StidMaterialsDataSource(stidJson).read();
    assertEquals(1, input.getItems().size());

    MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(input);
    assertTrue(report.toJson().contains("remainingLife_years"));
    assertTrue(report.toJson().contains("Chloride SCC"));
  }

  /**
   * Verifies that top-level payload fields are retained when stidData is supplied.
   */
  @Test
  void testTopLevelPayloadMergesStidData() {
    String json = "{\n" + "  \"projectName\": \"Synthetic STID payload\",\n" + "  \"stidData\": {\n"
        + "    \"lineList\": [{\n" + "      \"tag\": \"DEMO-LINE-020\",\n"
        + "      \"equipmentType\": \"Piping\",\n" + "      \"material\": \"Carbon Steel\",\n"
        + "      \"service\": {\"temperature_C\": 70.0, \"pressure_bara\": 60.0, "
        + "\"free_water\": true, \"co2_mole_fraction\": 0.03}\n" + "    }]\n" + "  }\n" + "}";

    MaterialsReviewInput input = MaterialsReviewInput.fromJson(json);
    assertEquals("Synthetic STID payload", input.getProjectName());
    assertEquals(1, input.getItems().size());
    assertEquals("DEMO-LINE-020", input.getItems().get(0).getTag());
  }

  /**
   * Verifies process-condition extraction and material-register overlay by tag.
   */
  @Test
  void testProcessSystemReviewWithMaterialRegisterOverlay() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 40.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("HP separator", feed);
    ProcessSystem process = new ProcessSystem("materials process");
    process.add(feed);
    process.add(separator);
    process.run();

    MaterialsReviewInput register = new MaterialsReviewInput();
    register.addItem(new MaterialReviewItem().setTag("HP separator").setEquipmentType("Separator")
        .setExistingMaterial("Carbon Steel").setServiceEnvelope(new MaterialServiceEnvelope()
            .set("free_water", Boolean.TRUE).set("chloride_mg_per_l", 25000.0)));

    MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(process, register);
    assertEquals("success",
        JsonParser.parseString(report.toJson()).getAsJsonObject().get("status").getAsString());
    assertTrue(report.toJson().contains("HP separator"));
    assertTrue(report.toJson().contains("Carbon Steel"));
  }
}
