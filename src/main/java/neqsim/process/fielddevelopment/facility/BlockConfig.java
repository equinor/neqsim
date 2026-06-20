package neqsim.process.fielddevelopment.facility;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for a single facility block.
 *
 * <p>
 * Captures the parameters needed to configure a specific block type within a facility.
 *
 * @author ESOL
 * @version 1.0
 */
public final class BlockConfig implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final BlockType type;
  private final String name;
  private final Map<String, Object> parameters;

  private BlockConfig(BlockType type, String name, Map<String, Object> parameters) {
    this.type = type;
    this.name = name;
    this.parameters = new LinkedHashMap<>(parameters);
  }

  /**
   * Creates a block configuration with default parameters.
   *
   * @param type block type
   * @return block configuration
   */
  public static BlockConfig of(BlockType type) {
    return new BlockConfig(type, type.getDisplayName(), new LinkedHashMap<>());
  }

  /**
   * Creates a block configuration with a custom name.
   *
   * @param type block type
   * @param name custom name
   * @return block configuration
   */
  public static BlockConfig of(BlockType type, String name) {
    return new BlockConfig(type, name, new LinkedHashMap<>());
  }

  /**
   * Creates a block configuration with parameters.
   *
   * @param type block type
   * @param parameters configuration parameters
   * @return block configuration
   */
  public static BlockConfig of(BlockType type, Map<String, Object> parameters) {
    return new BlockConfig(type, type.getDisplayName(), parameters);
  }

  /**
   * Creates a compression block with specified stages.
   *
   * @param stages number of compression stages
   * @return compression block config
   */
  public static BlockConfig compression(int stages) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("stages", stages);
    params.put("intercoolerTemp", 35.0); // degC
    params.put("polytropicEfficiency", 0.80);
    return new BlockConfig(BlockType.COMPRESSION, "Compression Train", params);
  }

  /**
   * Creates a compression block with specified stages and outlet pressure.
   *
   * @param stages number of compression stages
   * @param outletPressure target outlet pressure in bara
   * @return compression block config
   */
  public static BlockConfig compression(int stages, double outletPressure) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("stages", stages);
    params.put("outletPressure", outletPressure);
    params.put("intercoolerTemp", 35.0);
    params.put("polytropicEfficiency", 0.80);
    return new BlockConfig(BlockType.COMPRESSION, "Compression Train", params);
  }

  /**
   * Creates a TEG dehydration block with specified water spec.
   *
   * @param waterSpecPpm target water content in ppm
   * @return TEG block config
   */
  public static BlockConfig tegDehydration(double waterSpecPpm) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("waterSpec", waterSpecPpm);
    params.put("tegPurity", 0.99);
    params.put("absorberStages", 6);
    return new BlockConfig(BlockType.TEG_DEHYDRATION, "TEG Dehydration", params);
  }

  /**
   * Creates a CO2 membrane removal block.
   *
   * @param co2SpecPercent target CO2 content in %
   * @return membrane block config
   */
  public static BlockConfig co2Membrane(double co2SpecPercent) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("co2Spec", co2SpecPercent);
    params.put("membraneStages", 2);
    params.put("recoveryFraction", 0.95);
    return new BlockConfig(BlockType.CO2_REMOVAL_MEMBRANE, "CO2 Membrane", params);
  }

  /**
   * Creates a CO2 amine removal block.
   *
   * @param co2SpecPercent target CO2 content in %
   * @return amine block config
   */
  public static BlockConfig co2Amine(double co2SpecPercent) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("co2Spec", co2SpecPercent);
    params.put("amineType", "MDEA");
    params.put("absorberStages", 20);
    return new BlockConfig(BlockType.CO2_REMOVAL_AMINE, "CO2 Amine Unit", params);
  }

  /**
   * Creates an inlet separation block.
   *
   * @param pressure separation pressure in bara
   * @param temperature separation temperature in degC
   * @return inlet sep block config
   */
  public static BlockConfig inletSeparation(double pressure, double temperature) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("pressure", pressure);
    params.put("temperature", temperature);
    return new BlockConfig(BlockType.INLET_SEPARATION, "Inlet Separator", params);
  }

  /**
   * Creates an oil stabilization block.
   *
   * @param stages number of flash stages
   * @param rvp target RVP in bara
   * @return stabilization block config
   */
  public static BlockConfig oilStabilization(int stages, double rvp) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("stages", stages);
    params.put("targetRvp", rvp);
    return new BlockConfig(BlockType.OIL_STABILIZATION, "Oil Stabilization", params);
  }

  // Getters

  public BlockType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public Map<String, Object> getParameters() {
    return new LinkedHashMap<>(parameters);
  }

  @SuppressWarnings("unchecked")
  public <T> T getParameter(String key, T defaultValue) {
    Object value = parameters.get(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return (T) value;
    } catch (ClassCastException e) {
      return defaultValue;
    }
  }

  public int getIntParameter(String key, int defaultValue) {
    Object value = parameters.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }

  public double getDoubleParameter(String key, double defaultValue) {
    Object value = parameters.get(key);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return defaultValue;
  }

  @Override
  public String toString() {
    return String.format("BlockConfig[%s: %s, params=%s]", type, name, parameters);
  }
}
