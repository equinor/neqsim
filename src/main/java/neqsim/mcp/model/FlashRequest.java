package neqsim.mcp.model;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Typed request model for flash calculations.
 *
 * <p>
 * Represents the input for a flash calculation with all parameters as typed fields. Supports
 * deserialization from JSON via the {@link #fromJson(JsonObject)} factory, or direct construction
 * via the builder pattern.
 * </p>
 *
 * <h2>JSON format:</h2>
 *
 * <pre>{@code { "model": "SRK", "temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value":
 * 50.0, "unit": "bara"}, "flashType": "TP", "components": {"methane": 0.85, "ethane": 0.10,
 * "propane": 0.05}, "mixingRule": "classic" } }</pre>
 *
 * <p>
 * Alternatively, use {@code e300FilePath} to load fluid composition and EOS parameters from an
 * Eclipse E300 file.
 * </p>
 *
 * @author Even Solbraa @version 1.0
 */
public class FlashRequest {

  private String model;
  private ValueWithUnit temperature;
  private ValueWithUnit pressure;
  private String flashType;
  private Map<String, Double> components;
  private String mixingRule;
  private ValueWithUnit enthalpy;
  private ValueWithUnit entropy;
  private ValueWithUnit volume;
  private String e300FilePath;
  private boolean addWater;
  private double waterKij;

  /**
   * Default constructor. Creates a request with default values (SRK, TP, 15C, 1 atm, classic).
   */
  public FlashRequest() {
    this.model = "SRK";
    this.flashType = "TP";
    this.temperature = new ValueWithUnit(288.15, "K");
    this.pressure = new ValueWithUnit(1.01325, "bara");
    this.mixingRule = "classic";
    this.components = new LinkedHashMap<String, Double>();
    this.addWater = false;
    this.waterKij = 0.5;
  }

  /**
   * Parses a FlashRequest from a parsed JsonObject.
   *
   * @param root the parsed JSON
   * @return the populated FlashRequest
   */
  public static FlashRequest fromJson(JsonObject root) {
    FlashRequest req = new FlashRequest();

    if (root.has("model")) {
      req.model = root.get("model").getAsString().toUpperCase();
    }

    if (root.has("temperature")) {
      ValueWithUnit t = ValueWithUnit.fromJson(root.get("temperature"), "K");
      if (t != null) {
        req.temperature = t;
      }
    }

    if (root.has("pressure")) {
      ValueWithUnit p = ValueWithUnit.fromJson(root.get("pressure"), "bara");
      if (p != null) {
        req.pressure = p;
      }
    }

    if (root.has("flashType")) {
      req.flashType = root.get("flashType").getAsString();
    }

    if (root.has("mixingRule")) {
      req.mixingRule = root.get("mixingRule").getAsString();
    }

    if (root.has("components")) {
      JsonObject comps = root.getAsJsonObject("components");
      for (Map.Entry<String, JsonElement> entry : comps.entrySet()) {
        req.components.put(entry.getKey(), entry.getValue().getAsDouble());
      }
    }

    if (root.has("e300FilePath")) {
      req.e300FilePath = root.get("e300FilePath").getAsString();
    } else if (root.has("e300File")) {
      req.e300FilePath = root.get("e300File").getAsString();
    } else if (root.has("fluidFilePath")) {
      req.e300FilePath = root.get("fluidFilePath").getAsString();
    } else if (root.has("fluidFile")) {
      req.e300FilePath = root.get("fluidFile").getAsString();
    }

    if (root.has("addWater")) {
      req.addWater = root.get("addWater").getAsBoolean();
    }
    if (root.has("waterKij")) {
      req.waterKij = root.get("waterKij").getAsDouble();
    }

    if (root.has("enthalpy")) {
      req.enthalpy = ValueWithUnit.fromJson(root.get("enthalpy"), "J/mol");
    }
    if (root.has("entropy")) {
      req.entropy = ValueWithUnit.fromJson(root.get("entropy"), "J/molK");
    }
    if (root.has("volume")) {
      req.volume = ValueWithUnit.fromJson(root.get("volume"), "m3/mol");
    }

    return req;
  }

  /**
   * Gets the thermodynamic model name.
   *
   * @return the model (e.g., "SRK", "PR", "CPA")
   */
  public String getModel() {
    return model;
  }

  /**
   * Sets the thermodynamic model name.
   *
   * @param model the model name
   * @return this request for chaining
   */
  public FlashRequest setModel(String model) {
    this.model = model;
    return this;
  }

  /**
   * Gets the temperature specification.
   *
   * @return the temperature with unit
   */
  public ValueWithUnit getTemperature() {
    return temperature;
  }

  /**
   * Sets the temperature specification.
   *
   * @param temperature the temperature with unit
   * @return this request for chaining
   */
  public FlashRequest setTemperature(ValueWithUnit temperature) {
    this.temperature = temperature;
    return this;
  }

  /**
   * Gets the pressure specification.
   *
   * @return the pressure with unit
   */
  public ValueWithUnit getPressure() {
    return pressure;
  }

  /**
   * Sets the pressure specification.
   *
   * @param pressure the pressure with unit
   * @return this request for chaining
   */
  public FlashRequest setPressure(ValueWithUnit pressure) {
    this.pressure = pressure;
    return this;
  }

  /**
   * Gets the flash type.
   *
   * @return the flash type (e.g., "TP", "PH", "PS")
   */
  public String getFlashType() {
    return flashType;
  }

  /**
   * Sets the flash type.
   *
   * @param flashType the flash type
   * @return this request for chaining
   */
  public FlashRequest setFlashType(String flashType) {
    this.flashType = flashType;
    return this;
  }

  /**
   * Gets the component map (name to mole fraction).
   *
   * @return the component map
   */
  public Map<String, Double> getComponents() {
    return components;
  }

  /**
   * Sets the component map.
   *
   * @param components the component map
   * @return this request for chaining
   */
  public FlashRequest setComponents(Map<String, Double> components) {
    this.components = components;
    return this;
  }

  /**
   * Adds a component with mole fraction.
   *
   * @param name the component name
   * @param moleFraction the mole fraction
   * @return this request for chaining
   */
  public FlashRequest addComponent(String name, double moleFraction) {
    this.components.put(name, moleFraction);
    return this;
  }

  /**
   * Gets the mixing rule.
   *
   * @return the mixing rule string
   */
  public String getMixingRule() {
    return mixingRule;
  }

  /**
   * Sets the mixing rule.
   *
   * @param mixingRule the mixing rule
   * @return this request for chaining
   */
  public FlashRequest setMixingRule(String mixingRule) {
    this.mixingRule = mixingRule;
    return this;
  }

  /**
   * Gets the enthalpy specification (for PH flash).
   *
   * @return the enthalpy with unit, or null if not set
   */
  public ValueWithUnit getEnthalpy() {
    return enthalpy;
  }

  /**
   * Sets the enthalpy specification.
   *
   * @param enthalpy the enthalpy with unit
   * @return this request for chaining
   */
  public FlashRequest setEnthalpy(ValueWithUnit enthalpy) {
    this.enthalpy = enthalpy;
    return this;
  }

  /**
   * Gets the entropy specification (for PS flash).
   *
   * @return the entropy with unit, or null if not set
   */
  public ValueWithUnit getEntropy() {
    return entropy;
  }

  /**
   * Sets the entropy specification.
   *
   * @param entropy the entropy with unit
   * @return this request for chaining
   */
  public FlashRequest setEntropy(ValueWithUnit entropy) {
    this.entropy = entropy;
    return this;
  }

  /**
   * Gets the volume specification (for TV flash).
   *
   * @return the volume with unit, or null if not set
   */
  public ValueWithUnit getVolume() {
    return volume;
  }

  /**
   * Sets the volume specification.
   *
   * @param volume the volume with unit
   * @return this request for chaining
   */
  public FlashRequest setVolume(ValueWithUnit volume) {
    this.volume = volume;
    return this;
  }

  /**
   * Gets the Eclipse E300 file path used as an alternate fluid source.
   *
   * @return the E300 file path, or null if components define the fluid
   */
  public String getE300FilePath() {
    return e300FilePath;
  }

  /**
   * Sets the Eclipse E300 file path used as an alternate fluid source.
   *
   * @param e300FilePath path to the E300 file
   * @return this request for chaining
   */
  public FlashRequest setE300FilePath(String e300FilePath) {
    this.e300FilePath = e300FilePath;
    return this;
  }

  /**
   * Gets whether a zero-fraction water component should be added to E300 fluids when absent.
   *
   * @return true if water should be added when absent
   */
  public boolean isAddWater() {
    return addWater;
  }

  /**
   * Sets whether a zero-fraction water component should be added to E300 fluids when absent.
   *
   * @param addWater true to add water when absent
   * @return this request for chaining
   */
  public FlashRequest setAddWater(boolean addWater) {
    this.addWater = addWater;
    return this;
  }

  /**
   * Gets the binary interaction parameter used when adding water to an E300 fluid.
   *
   * @return the water binary interaction parameter
   */
  public double getWaterKij() {
    return waterKij;
  }

  /**
   * Sets the binary interaction parameter used when adding water to an E300 fluid.
   *
   * @param waterKij binary interaction parameter for water against all existing components
   * @return this request for chaining
   */
  public FlashRequest setWaterKij(double waterKij) {
    this.waterKij = waterKij;
    return this;
  }
}
