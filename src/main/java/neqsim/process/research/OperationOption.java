package neqsim.process.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Candidate unit operation used in process-network synthesis.
 *
 * <p>
 * Operation options are the operation vertices in a P-graph-style representation: they consume one
 * or more named material states and produce one or more named material states. The class also keeps
 * the NeqSim JSON equipment type and property values required to build the candidate process.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperationOption {
  private final String name;
  private final String equipmentType;
  private final List<String> inputMaterials = new ArrayList<>();
  private final List<String> outputMaterials = new ArrayList<>();
  private final Map<String, JsonElement> properties = new LinkedHashMap<>();
  private String description = "";

  /**
   * Creates an operation option.
   *
   * @param name operation name; must be non-empty
   * @param equipmentType NeqSim JSON equipment type, e.g. Separator or GibbsReactor
   */
  public OperationOption(String name, String equipmentType) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Operation option name cannot be empty");
    }
    if (equipmentType == null || equipmentType.trim().isEmpty()) {
      throw new IllegalArgumentException("Equipment type cannot be empty");
    }
    this.name = name;
    this.equipmentType = equipmentType;
  }

  /**
   * Adds an input material state.
   *
   * @param materialName material state consumed by this operation
   * @return this operation option
   */
  public OperationOption addInputMaterial(String materialName) {
    if (materialName != null && !materialName.trim().isEmpty()) {
      inputMaterials.add(materialName);
    }
    return this;
  }

  /**
   * Adds an output material state.
   *
   * @param materialName material state produced by this operation
   * @return this operation option
   */
  public OperationOption addOutputMaterial(String materialName) {
    if (materialName != null && !materialName.trim().isEmpty()) {
      outputMaterials.add(materialName);
    }
    return this;
  }

  /**
   * Sets a numeric equipment property.
   *
   * @param propertyName property setter name without the set prefix
   * @param value property value
   * @return this operation option
   */
  public OperationOption setProperty(String propertyName, double value) {
    properties.put(propertyName, new JsonPrimitive(value));
    return this;
  }

  /**
   * Sets a string equipment property.
   *
   * @param propertyName property setter name without the set prefix
   * @param value property value
   * @return this operation option
   */
  public OperationOption setProperty(String propertyName, String value) {
    properties.put(propertyName, new JsonPrimitive(value));
    return this;
  }

  /**
   * Sets a numeric equipment property with a unit.
   *
   * @param propertyName property setter name without the set prefix
   * @param value numeric value
   * @param unit unit string accepted by the target equipment setter
   * @return this operation option
   */
  public OperationOption setProperty(String propertyName, double value, String unit) {
    JsonArray array = new JsonArray();
    array.add(value);
    array.add(unit);
    properties.put(propertyName, array);
    return this;
  }

  /**
   * Sets an operation description.
   *
   * @param description operation description
   * @return this operation option
   */
  public OperationOption setDescription(String description) {
    this.description = description == null ? "" : description;
    return this;
  }

  /**
   * Gets the operation name.
   *
   * @return operation name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the NeqSim equipment type.
   *
   * @return equipment type string
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Gets input material names.
   *
   * @return unmodifiable input material names
   */
  public List<String> getInputMaterials() {
    return Collections.unmodifiableList(inputMaterials);
  }

  /**
   * Gets output material names.
   *
   * @return unmodifiable output material names
   */
  public List<String> getOutputMaterials() {
    return Collections.unmodifiableList(outputMaterials);
  }

  /**
   * Gets the operation properties.
   *
   * @return unmodifiable property map
   */
  public Map<String, JsonElement> getProperties() {
    return Collections.unmodifiableMap(properties);
  }

  /**
   * Gets the description.
   *
   * @return description, possibly empty
   */
  public String getDescription() {
    return description;
  }

  /**
   * Converts operation properties to a JSON object.
   *
   * @return JSON object containing equipment properties
   */
  public JsonObject propertiesToJson() {
    JsonObject object = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
      object.add(entry.getKey(), entry.getValue());
    }
    return object;
  }
}
