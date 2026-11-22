package neqsim.process.engineering;

import java.util.EnumSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.safety.api14c.Api14cDeviceDeclaration;
import neqsim.process.safety.api14c.Api14cDeviceType;
import neqsim.process.safety.api14c.Api14cEquipmentCategory;
import neqsim.process.safety.api14c.Api14cSafeChartBuilder;
import neqsim.process.safety.api14c.Api14cSafetyAnalysisTable;

/**
 * Joins project-declared RP 14C device functions to calculated installation checks and shutdown test evidence.
 * 
 * @author ESOL
 * @version 1.0
 */
final class Api14cProtectionReview {
  private Api14cProtectionReview() {
  }

  /**
   * Builds equipment rows, keeping unevaluated components and unresolved device references visible.
   * 
   * @param project engineering model with device declarations
   * @param reliefChecks calculated installed-device screens
   * @param sequences evaluated sequences with source actions and any recorded dynamic verdict
   * @return equipment protection review in process order
   */
  static JsonArray build(EngineeringProject project, JsonArray reliefChecks, JsonArray sequences) {
    JsonArray rows = new JsonArray();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        continue;
      }
      String equipment = unit.getName();
      Api14cEquipmentCategory category = Api14cSafeChartBuilder.classify(unit);
      JsonObject row = new JsonObject();
      row.addProperty("equipmentTag", equipment);
      row.addProperty("category", category == null ? "UNCLASSIFIED" : category.name());
      EnumSet<Api14cDeviceType> declared = EnumSet.noneOf(Api14cDeviceType.class);
      JsonArray functions = new JsonArray();
      for (Api14cDeviceDeclaration device : project.getApi14cDevices()) {
        if (equipment.equals(device.getEquipmentTag())) {
          declared.add(device.getType());
          functions.add(function(device, reliefChecks, sequences));
        }
      }
      JsonArray missing = new JsonArray();
      JsonArray required = new JsonArray();
      if (category != null) {
        for (Api14cDeviceType type : Api14cDeviceType.values()) {
          if (Api14cSafetyAnalysisTable.getRequiredDevices(category).contains(type)) {
            required.add(type.name());
            if (!declared.contains(type)) {
              missing.add(type.name());
            }
          }
        }
      }
      row.add("screeningDeviceTypes", required);
      row.add("undeclaredDeviceTypes", missing);
      row.add("declaredFunctions", functions);
      row.addProperty("status", category == null ? "CLASSIFICATION_REQUIRED" : "PROJECT_SAFETY_REVIEW_REQUIRED");
      rows.add(row);
    }
    return rows;
  }

  /**
   * Resolves exact device and sequence references within one protected equipment boundary.
   * 
   * @param device declaration supported by a project record
   * @param reliefChecks installation screens by device and equipment
   * @param sequences shutdown evidence by sequence and equipment
   * @return declared function with traceability and explicit missing evidence
   */
  private static JsonObject function(Api14cDeviceDeclaration device, JsonArray reliefChecks, JsonArray sequences) {
    JsonObject row = new JsonObject();
    row.addProperty("deviceTag", device.getDeviceTag());
    row.addProperty("deviceType", device.getType().name());
    row.addProperty("evidenceReference", device.getEvidenceReference());
    if (device.getType() == Api14cDeviceType.PSV) {
      JsonArray matches = matches(reliefChecks, "equipmentTag", device.getEquipmentTag(), "deviceTag",
          device.getDeviceTag());
      row.addProperty("installedReliefStatus",
          matches.size() == 1 ? matches.get(0).getAsJsonObject().get("status").getAsString()
              : matches.size() == 0 ? "MATCHING_PSV_SCREEN_REQUIRED" : "AMBIGUOUS_PSV_SCREEN");
      if (matches.size() == 1) {
        row.add("installedReliefScreen", matches.get(0));
      }
    }
    if (!device.getSequenceId().isEmpty()) {
      row.addProperty("sequenceId", device.getSequenceId());
      JsonArray matches = matches(sequences, "protectedEquipmentTag", device.getEquipmentTag(), "sequenceId",
          device.getSequenceId());
      row.addProperty("sequenceLinkStatus",
          matches.size() == 1 ? "MATCHED" : matches.size() == 0 ? "UNRESOLVED" : "AMBIGUOUS");
      if (matches.size() == 1) {
        row.add("shutdownSequence", matches.get(0));
      }
    }
    return row;
  }

  /**
   * Finds exact matches so that ambiguous or mismatched records cannot supply verification.
   * 
   * @param rows existing calculation records
   * @param equipmentField equipment key
   * @param equipment equipment value
   * @param idField device or sequence key
   * @param id device or sequence identifier
   * @return all matching records
   */
  private static JsonArray matches(JsonArray rows, String equipmentField, String equipment, String idField, String id) {
    JsonArray matches = new JsonArray();
    for (JsonElement entry : rows) {
      JsonObject row = entry.getAsJsonObject();
      if (equipment.equals(row.get(equipmentField).getAsString()) && id.equals(row.get(idField).getAsString())) {
        matches.add(entry);
      }
    }
    return matches;
  }
}
