package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link EquipmentDesignData}.
 *
 * @author ESOL
 * @version 1.0
 */
class EquipmentDesignDataTest {

  /**
   * Verifies that separator design properties are applied from JSON.
   */
  @Test
  void applySetsDesignPropertiesOnSeparator() {
    ProcessSystem process = buildSimpleProcess();
    Separator sep = (Separator) process.getUnit("HP Sep");

    // Default values before applying design data
    double defaultDiameter = sep.getInternalDiameter();

    JsonObject designCapacities = new JsonObject();
    JsonObject sepDesign = new JsonObject();
    sepDesign.addProperty("internalDiameter", 2.5);
    sepDesign.addProperty("separatorLength", 8.0);
    sepDesign.addProperty("designGasLoadFactor", 0.08);
    designCapacities.add("HP Sep", sepDesign);

    Map<String, EquipmentDesignData.ApplyResult> results =
        EquipmentDesignData.apply(process, designCapacities);

    // Verify result is present and successful
    assertTrue(results.containsKey("HP Sep"));
    EquipmentDesignData.ApplyResult result = results.get("HP Sep");
    assertEquals("applied", result.status);
    assertEquals(3, result.appliedProperties.size());

    // Verify properties were actually set
    assertEquals(2.5, sep.getInternalDiameter(), 1.0e-6);
  }

  /**
   * Verifies that equipment not found in the process is reported.
   */
  @Test
  void applyReportsEquipmentNotFound() {
    ProcessSystem process = buildSimpleProcess();

    JsonObject designCapacities = new JsonObject();
    JsonObject props = new JsonObject();
    props.addProperty("internalDiameter", 2.0);
    designCapacities.add("NonExistentEquipment", props);

    Map<String, EquipmentDesignData.ApplyResult> results =
        EquipmentDesignData.apply(process, designCapacities);

    assertTrue(results.containsKey("NonExistentEquipment"));
    assertEquals("not_found", results.get("NonExistentEquipment").status);
  }

  /**
   * Verifies that null inputs are handled gracefully.
   */
  @Test
  void applyHandlesNullInputs() {
    Map<String, EquipmentDesignData.ApplyResult> results1 =
        EquipmentDesignData.apply(null, new JsonObject());
    assertTrue(results1.isEmpty());

    ProcessSystem process = buildSimpleProcess();
    Map<String, EquipmentDesignData.ApplyResult> results2 =
        EquipmentDesignData.apply(process, null);
    assertTrue(results2.isEmpty());
  }

  /**
   * Verifies that constraints are tagged with the designCapacities data source.
   */
  @Test
  void tagConstraintDataSourcesSetsDesignCapacitiesSource() {
    ProcessSystem process = buildSimpleProcess();
    process.run();

    JsonObject designCapacities = new JsonObject();
    JsonObject sepDesign = new JsonObject();
    sepDesign.addProperty("internalDiameter", 2.5);
    designCapacities.add("HP Sep", sepDesign);

    EquipmentDesignData.apply(process, designCapacities);
    process.run();

    Separator sep = (Separator) process.getUnit("HP Sep");
    Map<String, CapacityConstraint> constraints = sep.getCapacityConstraints();
    if (!constraints.isEmpty()) {
      for (CapacityConstraint constraint : constraints.values()) {
        // Constraints for equipment with design data should be tagged
        assertEquals(EquipmentDesignData.DATA_SOURCE_DESIGN_CAPACITIES,
            constraint.getDataSource(),
            "Constraint '" + constraint.getName() + "' should have designCapacities data source");
      }
    }
  }

  /**
   * Verifies that ApplyResult.toJson() produces the expected structure.
   */
  @Test
  void applyResultToJsonIncludesAllFields() {
    EquipmentDesignData.ApplyResult result =
        new EquipmentDesignData.ApplyResult("TestEquip", "applied", "");
    result.addApplied("diameter", 2.0, "m");
    result.addApplied("length", 6.0, "m");

    JsonObject json = result.toJson();
    assertEquals("TestEquip", json.get("equipmentName").getAsString());
    assertEquals("applied", json.get("status").getAsString());
    assertFalse(json.has("message")); // empty message not included
    assertTrue(json.has("appliedProperties"));
    JsonObject props = json.getAsJsonObject("appliedProperties");
    assertEquals(2.0, props.getAsJsonObject("diameter").get("value").getAsDouble(), 1.0e-6);
    assertEquals("m", props.getAsJsonObject("diameter").get("unit").getAsString());
  }

  /**
   * Creates a simple process system with a separator for testing.
   *
   * @return process system with a feed stream and separator
   */
  private ProcessSystem buildSimpleProcess() {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Separator sep = new Separator("HP Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();
    return process;
  }
}
