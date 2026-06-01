package neqsim.process.materials;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Normalized STID or technical-database JSON bridge for the materials review engine.
 *
 * <p>
 * This class intentionally does not connect to STID directly. Retrieval and document parsing remain
 * in the existing Python/devtools layer. The Java core consumes the normalized JSON extract so the
 * same engine can be used with STID, P&amp;ID, line-list, equipment-register, material-certificate,
 * and inspection-data sources.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidMaterialsDataSource implements MaterialsReviewDataSource {
  /** Normalized STID JSON object. */
  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID/technical-database JSON
   */
  public StidMaterialsDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized STID/technical-database JSON object
   */
  public StidMaterialsDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized STID/technical-database JSON object
   * @return data source
   */
  public static StidMaterialsDataSource fromJsonObject(JsonObject source) {
    return new StidMaterialsDataSource(source);
  }

  /**
   * Reads normalized material review input.
   *
   * @return materials review input
   */
  @Override
  public MaterialsReviewInput read() {
    MaterialsReviewInput input = new MaterialsReviewInput();
    if (source.has("projectName")) {
      input.setProjectName(source.get("projectName").getAsString());
    }
    if (source.has("designLifeYears")) {
      input.setDesignLifeYears(source.get("designLifeYears").getAsDouble());
    }
    addArray(input, "materialsRegister");
    addArray(input, "lineList");
    addArray(input, "equipment");
    addArray(input, "inspectionData");
    addArray(input, "materialCertificates");
    return input;
  }

  /**
   * Adds all records from a named array.
   *
   * @param input input object receiving records
   * @param key array key in the source object
   */
  private void addArray(MaterialsReviewInput input, String key) {
    if (!source.has(key) || !source.get(key).isJsonArray()) {
      return;
    }
    JsonArray array = source.getAsJsonArray(key);
    for (int i = 0; i < array.size(); i++) {
      JsonElement element = array.get(i);
      if (element.isJsonObject()) {
        mergeItem(input, fromRecord(element.getAsJsonObject(), key));
      }
    }
  }

  /**
   * Merges one parsed record into the input by tag.
   *
   * @param input input to update
   * @param item parsed item
   */
  private void mergeItem(MaterialsReviewInput input, MaterialReviewItem item) {
    MaterialsReviewInput single = new MaterialsReviewInput();
    single.addItem(item);
    input.mergeFrom(single);
  }

  /**
   * Converts one STID-like record to a material review item.
   *
   * @param record record object from a normalized STID extract
   * @param sourceKey name of the source array
   * @return material review item
   */
  private MaterialReviewItem fromRecord(JsonObject record, String sourceKey) {
    MaterialReviewItem item = new MaterialReviewItem();
    item.setTag(
        firstString(record, "tag", "lineNumber", "lineNo", "equipmentTag", "assetTag", "name"));
    item.setEquipmentType(firstString(record, "equipmentType", "type", "assetType", "service"));
    item.setExistingMaterial(firstString(record, "existingMaterial", "material", "materialGrade",
        "materialClass", "pipingMaterialClass", "mds"));
    item.addSourceReference(sourceKey);
    if (record.has("sourceDocument")) {
      item.addSourceReference(record.get("sourceDocument").getAsString());
    }
    MaterialServiceEnvelope envelope = new MaterialServiceEnvelope();
    copyDirect(record, envelope, "operatingTemperatureC", "temperature_C");
    copyDirect(record, envelope, "designTemperatureC", "design_temperature_C");
    copyDirect(record, envelope, "operatingPressureBara", "pressure_bara");
    copyDirect(record, envelope, "designPressureBara", "design_pressure_bara");
    copyDirect(record, envelope, "corrosionAllowanceMm", "corrosion_allowance_mm");
    copyDirect(record, envelope, "nominalWallThicknessMm", "nominal_wall_thickness_mm");
    copyDirect(record, envelope, "currentWallThicknessMm", "current_wall_thickness_mm");
    copyDirect(record, envelope, "minimumRequiredThicknessMm", "minimum_required_thickness_mm");
    copyDirect(record, envelope, "insulationType", "insulation_type");
    copyDirect(record, envelope, "coatingAgeYears", "coating_age_years");
    copyDirect(record, envelope, "marineEnvironment", "marine_environment");
    mergeObject(record, envelope, "service");
    mergeObject(record, envelope, "environment");
    item.setServiceEnvelope(envelope);
    return item;
  }

  /**
   * Copies one field under a normalized target key.
   *
   * @param record source record
   * @param envelope target envelope
   * @param sourceKey source key
   * @param targetKey target key
   */
  private void copyDirect(JsonObject record, MaterialServiceEnvelope envelope, String sourceKey,
      String targetKey) {
    if (record.has(sourceKey) && !record.get(sourceKey).isJsonNull()) {
      JsonElement value = record.get(sourceKey);
      if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
        envelope.set(targetKey, Boolean.valueOf(value.getAsBoolean()));
      } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
        envelope.set(targetKey, Double.valueOf(value.getAsDouble()));
      } else {
        envelope.set(targetKey, value.getAsString());
      }
    }
  }

  /**
   * Merges a nested JSON object into a service envelope.
   *
   * @param record source record
   * @param envelope target envelope
   * @param objectKey nested object key
   */
  private void mergeObject(JsonObject record, MaterialServiceEnvelope envelope, String objectKey) {
    if (!record.has(objectKey) || !record.get(objectKey).isJsonObject()) {
      return;
    }
    JsonObject object = record.getAsJsonObject(objectKey);
    for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
      JsonElement value = entry.getValue();
      if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
        envelope.set(entry.getKey(), Boolean.valueOf(value.getAsBoolean()));
      } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
        envelope.set(entry.getKey(), Double.valueOf(value.getAsDouble()));
      } else if (!value.isJsonNull()) {
        envelope.set(entry.getKey(), value.getAsString());
      }
    }
  }

  /**
   * Returns the first available string for a set of keys.
   *
   * @param object JSON object
   * @param keys keys to test
   * @return first available value, or empty string
   */
  private String firstString(JsonObject object, String... keys) {
    for (String key : keys) {
      if (object.has(key) && !object.get(key).isJsonNull()) {
        return object.get(key).getAsString();
      }
    }
    return "";
  }
}
