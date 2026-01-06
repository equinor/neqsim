package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesignResponse;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesignResponse;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesignResponse;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for JSON export of mechanical design data.
 *
 * @author esol
 */
public class MechanicalDesignJsonExportTest {

  private ProcessSystem process;
  private Separator separator;
  private Compressor compressor;
  private ThrottlingValve valve;

  @BeforeEach
  public void setUp() {
    // Create a simple test system
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(100.0, "kg/hr");

    separator = new Separator("separator", inlet);

    Stream gasOut = new Stream("gasOut", separator.getGasOutStream());

    compressor = new Compressor("compressor", gasOut);
    compressor.setOutletPressure(100.0, "bara");

    Stream compressedGas = new Stream("compressedGas", compressor.getOutletStream());

    valve = new ThrottlingValve("valve", compressedGas);
    valve.setOutletPressure(50.0, "bara");

    // Build process
    process = new ProcessSystem();
    process.add(inlet);
    process.add(separator);
    process.add(gasOut);
    process.add(compressor);
    process.add(compressedGas);
    process.add(valve);
    process.run();
  }

  @Test
  public void testSystemMechanicalDesignToJson() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    String json = sysMecDesign.toJson();

    // Verify JSON is not null or empty
    assertNotNull(json);
    assertFalse(json.isEmpty());

    // Verify key fields are present
    assertTrue(json.contains("\"isSystemLevel\": true"), "Should be system level");
    assertTrue(json.contains("\"equipmentCount\""), "Should have equipment count");
    assertTrue(json.contains("\"totalWeight\""), "Should have total weight");
    assertTrue(json.contains("\"totalPowerRequired\""), "Should have power required");
    assertTrue(json.contains("\"weightByType\""), "Should have weight by type");
    assertTrue(json.contains("\"weightByDiscipline\""), "Should have weight by discipline");
    assertTrue(json.contains("\"equipmentList\""), "Should have equipment list");
  }

  @Test
  public void testSystemMechanicalDesignToCompactJson() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    String json = sysMecDesign.toJson();
    String compactJson = sysMecDesign.toCompactJson();

    // Compact should be shorter (no pretty printing)
    assertNotNull(compactJson);
    assertFalse(compactJson.isEmpty());
    assertTrue(compactJson.length() < json.length(), "Compact should be shorter");
    assertFalse(compactJson.contains("\n  "), "Should not have formatted indentation");
  }

  @Test
  public void testEquipmentMechanicalDesignToJson() {
    separator.getMechanicalDesign().calcDesign();

    String json = separator.getMechanicalDesign().toJson();

    // Verify JSON is not null or empty
    assertNotNull(json);
    assertFalse(json.isEmpty());

    // Verify key fields are present
    assertTrue(json.contains("\"isSystemLevel\": false"), "Should not be system level");
    assertTrue(json.contains("\"totalWeight\""), "Should have total weight");
    assertTrue(json.contains("\"maxDesignPressure\""), "Should have design pressure");
    assertTrue(json.contains("\"maxDesignTemperature\""), "Should have design temperature");
  }

  @Test
  public void testMechanicalDesignResponseFromSystemDesign() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    MechanicalDesignResponse response = sysMecDesign.getResponse();

    // Verify response object is populated
    assertNotNull(response);
    assertTrue(response.isSystemLevel());
    assertTrue(response.getEquipmentCount() > 0, "Should have equipment");
    assertTrue(response.getTotalWeight() > 0, "Should have total weight");
    assertNotNull(response.getWeightByType());
    assertFalse(response.getWeightByType().isEmpty(), "Should have weight by type");
    assertNotNull(response.getEquipmentList());
    assertFalse(response.getEquipmentList().isEmpty(), "Should have equipment list");
  }

  @Test
  public void testMechanicalDesignResponseFromEquipment() {
    separator.getMechanicalDesign().calcDesign();

    MechanicalDesignResponse response = separator.getMechanicalDesign().getResponse();

    // Verify response object is populated
    assertNotNull(response);
    assertFalse(response.isSystemLevel());
    assertTrue(response.getTotalWeight() >= 0, "Weight should be non-negative");
  }

  @Test
  public void testJsonRoundTrip() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    String json = sysMecDesign.toJson();
    MechanicalDesignResponse parsed = MechanicalDesignResponse.fromJson(json);

    // Verify round trip
    assertNotNull(parsed);
    assertTrue(parsed.isSystemLevel());
    assertTrue(parsed.getEquipmentCount() > 0);
  }

  @Test
  public void testMergeWithEquipmentJson() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    MechanicalDesignResponse response = sysMecDesign.getResponse();

    // Simulate equipment JSON (mock)
    String equipmentJson = "{\"name\":\"testProcess\",\"units\":[]}";

    String mergedJson = response.mergeWithEquipmentJson(equipmentJson);

    assertNotNull(mergedJson);
    assertTrue(mergedJson.contains("\"processData\""), "Should have processData section");
    assertTrue(mergedJson.contains("\"mechanicalDesign\""), "Should have mechanicalDesign section");
  }

  @Test
  public void testWeightByDisciplineInJson() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    String json = sysMecDesign.toJson();

    // Check discipline categories are present
    assertTrue(json.contains("Mechanical") || json.contains("mechanical"),
        "Should have mechanical discipline");
  }

  @Test
  public void testEquipmentListInJson() {
    SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
    sysMecDesign.runDesignCalculation();

    String json = sysMecDesign.toJson();

    // Check that equipment names appear in the JSON
    assertTrue(json.contains("separator") || json.contains("compressor"),
        "Should have equipment names in list");
  }

  @Test
  public void testCompressorSpecificResponse() {
    compressor.getMechanicalDesign().calcDesign();

    // Get the specialized response
    MechanicalDesignResponse response = compressor.getMechanicalDesign().getResponse();

    // Verify it's the correct subtype
    assertTrue(response instanceof CompressorMechanicalDesignResponse,
        "Should return CompressorMechanicalDesignResponse");

    CompressorMechanicalDesignResponse compResponse = (CompressorMechanicalDesignResponse) response;

    // Verify compressor-specific fields are present
    String json = compressor.getMechanicalDesign().toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentType\": \"Compressor\""), "Should have Compressor type");
    assertTrue(json.contains("\"numberOfStages\""), "Should have number of stages");
    assertTrue(json.contains("\"driverPower\""), "Should have driver power");
  }

  @Test
  public void testSeparatorSpecificResponse() {
    separator.getMechanicalDesign().calcDesign();

    // Get the specialized response
    MechanicalDesignResponse response = separator.getMechanicalDesign().getResponse();

    // Verify it's the correct subtype
    assertTrue(response instanceof SeparatorMechanicalDesignResponse,
        "Should return SeparatorMechanicalDesignResponse");

    // Verify separator-specific fields are present
    String json = separator.getMechanicalDesign().toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentType\": \"Separator\""), "Should have Separator type");
  }

  @Test
  public void testValveSpecificResponse() {
    valve.getMechanicalDesign().calcDesign();

    // Get the specialized response
    MechanicalDesignResponse response = valve.getMechanicalDesign().getResponse();

    // Verify it's the correct subtype
    assertTrue(response instanceof ValveMechanicalDesignResponse,
        "Should return ValveMechanicalDesignResponse");

    // Verify valve-specific fields are present
    String json = valve.getMechanicalDesign().toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentType\": \"Valve\""), "Should have Valve type");
    assertTrue(json.contains("\"ansiPressureClass\""), "Should have ANSI pressure class");
    assertTrue(json.contains("\"valveType\""), "Should have valve type");
  }

  @Test
  public void testSpecializedResponseInheritance() {
    compressor.getMechanicalDesign().calcDesign();
    CompressorMechanicalDesignResponse response =
        (CompressorMechanicalDesignResponse) compressor.getMechanicalDesign().getResponse();

    // Verify inherited fields from base class are populated
    assertNotNull(response.getName());
    assertTrue(response.getTotalWeight() >= 0, "Weight should be non-negative");

    // Verify design standard is set
    assertNotNull(response.getDesignStandard());
  }
}
