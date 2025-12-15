package neqsim.process.mpc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Exports soft-sensor and estimator configurations for integration with external calculation
 * engines.
 *
 * <p>
 * This class provides export capabilities for soft-sensors (calculated values derived from
 * thermodynamic models) that can be used by external control and optimization systems. Common
 * soft-sensors include:
 * </p>
 * <ul>
 * <li>Composition estimators based on pressure, temperature, and flow measurements</li>
 * <li>Phase fraction calculators for multiphase systems</li>
 * <li>Energy balance estimators</li>
 * <li>Property calculations (density, viscosity, heat capacity)</li>
 * </ul>
 *
 * <p>
 * The exports include input/output mappings, calculation parameters, and model coefficients that
 * can be loaded into external calculation engines for real-time estimation.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Create soft-sensor exporter
 * SoftSensorExporter exporter = new SoftSensorExporter(processSystem);
 *
 * // Add soft-sensors
 * exporter.addDensitySensor("feed_density", "feed", "kg/m3");
 * exporter.addCompositionEstimator("methane_fraction", "separator", "methane");
 * exporter.addPhaseFractionSensor("gas_fraction", "separator");
 *
 * // Export configuration
 * exporter.exportConfiguration("soft_sensors.json");
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class SoftSensorExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system containing the equipment. */
  private final ProcessSystem processSystem;

  /** List of soft-sensor definitions. */
  private final List<SoftSensorDefinition> sensors = new ArrayList<>();

  /** Application name for exports. */
  private String applicationName = "NeqSim";

  /** Tag prefix for OPC-style naming. */
  private String tagPrefix = "";

  /**
   * Construct an exporter for a process system.
   *
   * @param processSystem the process system
   */
  public SoftSensorExporter(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("ProcessSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Set the tag prefix for variable naming.
   *
   * @param prefix the tag prefix
   * @return this exporter for method chaining
   */
  public SoftSensorExporter setTagPrefix(String prefix) {
    this.tagPrefix = prefix != null ? prefix : "";
    return this;
  }

  /**
   * Set the application name for exports.
   *
   * @param name the application name
   * @return this exporter for method chaining
   */
  public SoftSensorExporter setApplicationName(String name) {
    this.applicationName = name != null ? name : "NeqSim";
    return this;
  }

  /**
   * Add a density soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @param unit output unit (kg/m3, lb/ft3, etc.)
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addDensitySensor(String name, String equipmentName, String unit) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.DENSITY);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit(unit);
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a viscosity soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @param unit output unit (cP, Pa.s, etc.)
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addViscositySensor(String name, String equipmentName, String unit) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.VISCOSITY);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit(unit);
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a phase fraction soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addPhaseFractionSensor(String name, String equipmentName) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.PHASE_FRACTION);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit("fraction");
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensor.addInput("flowRate", "kg/hr");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a composition estimator soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @param componentName component to estimate
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addCompositionEstimator(String name, String equipmentName,
      String componentName) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.COMPOSITION);
    sensor.setEquipmentName(equipmentName);
    sensor.setComponentName(componentName);
    sensor.setOutputUnit("mole fraction");
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a molecular weight estimator soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addMolecularWeightSensor(String name, String equipmentName) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.MOLECULAR_WEIGHT);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit("kg/kmol");
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a compressibility factor (Z) soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addCompressibilitySensor(String name, String equipmentName) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.COMPRESSIBILITY);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit("dimensionless");
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a heat capacity soft-sensor.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @param unit output unit (J/mol.K, kJ/kg.K, etc.)
   * @return this exporter for method chaining
   */
  public SoftSensorExporter addHeatCapacitySensor(String name, String equipmentName, String unit) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, SensorType.HEAT_CAPACITY);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit(unit);
    sensor.addInput("temperature", "C");
    sensor.addInput("pressure", "bara");
    sensors.add(sensor);
    return this;
  }

  /**
   * Add a custom soft-sensor with specified inputs and calculation type.
   *
   * @param name sensor name
   * @param equipmentName equipment to monitor
   * @param sensorType the type of sensor
   * @param outputUnit the output unit
   * @return the created sensor definition for further configuration
   */
  public SoftSensorDefinition addCustomSensor(String name, String equipmentName,
      SensorType sensorType, String outputUnit) {
    SoftSensorDefinition sensor = new SoftSensorDefinition(name, sensorType);
    sensor.setEquipmentName(equipmentName);
    sensor.setOutputUnit(outputUnit);
    sensors.add(sensor);
    return sensor;
  }

  /**
   * Export all soft-sensor configurations to JSON.
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportConfiguration(String filename) throws IOException {
    Map<String, Object> export = new LinkedHashMap<>();

    // Header
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("format", "soft_sensor_configuration");
    header.put("version", "1.0");
    header.put("application", applicationName);
    header.put("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    header.put("sourceSimulator", "NeqSim");
    export.put("header", header);

    // Sensors
    List<Map<String, Object>> sensorList = new ArrayList<>();
    for (SoftSensorDefinition sensor : sensors) {
      sensorList.add(sensor.toMap(tagPrefix));
    }
    export.put("softSensors", sensorList);

    // Write JSON
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(export));
    }
  }

  /**
   * Export soft-sensor configurations as CVT (Calculated Value Table) format.
   *
   * <p>
   * CVT format is a tabular format commonly used by industrial control systems for defining
   * calculated values and their update schedules.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportCVTFormat(String filename) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Header line
      writer.write("Name,Tag,Type,Equipment,Inputs,OutputUnit,UpdateRate,Description");
      writer.newLine();

      // Data rows
      for (SoftSensorDefinition sensor : sensors) {
        writer.write(sensor.getName());
        writer.write("," + tagPrefix + sensor.getName());
        writer.write("," + sensor.getSensorType().name());
        writer.write("," + sensor.getEquipmentName());

        // Inputs as semicolon-separated list
        StringBuilder inputs = new StringBuilder();
        for (Map.Entry<String, String> input : sensor.getInputs().entrySet()) {
          if (inputs.length() > 0) {
            inputs.append(";");
          }
          inputs.append(input.getKey()).append("[").append(input.getValue()).append("]");
        }
        writer.write(",\"" + inputs.toString() + "\"");

        writer.write("," + sensor.getOutputUnit());
        writer.write("," + sensor.getUpdateRateSeconds());
        writer
            .write(",\"" + (sensor.getDescription() != null ? sensor.getDescription() : "") + "\"");
        writer.newLine();
      }
    }
  }

  /**
   * Get the list of defined soft-sensors.
   *
   * @return unmodifiable list of sensor definitions
   */
  public List<SoftSensorDefinition> getSensors() {
    return new ArrayList<>(sensors);
  }

  /**
   * Clear all defined soft-sensors.
   *
   * @return this exporter for method chaining
   */
  public SoftSensorExporter clear() {
    sensors.clear();
    return this;
  }

  /**
   * Soft-sensor types.
   */
  public enum SensorType {
    /** Fluid density calculation. */
    DENSITY,
    /** Fluid viscosity calculation. */
    VISCOSITY,
    /** Phase fraction (gas/liquid/aqueous). */
    PHASE_FRACTION,
    /** Component composition estimation. */
    COMPOSITION,
    /** Molecular weight calculation. */
    MOLECULAR_WEIGHT,
    /** Compressibility factor (Z). */
    COMPRESSIBILITY,
    /** Heat capacity calculation. */
    HEAT_CAPACITY,
    /** Enthalpy calculation. */
    ENTHALPY,
    /** Entropy calculation. */
    ENTROPY,
    /** Custom user-defined calculation. */
    CUSTOM
  }

  /**
   * Definition of a soft-sensor.
   */
  public static class SoftSensorDefinition implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final SensorType sensorType;
    private String equipmentName;
    private String componentName;
    private String outputUnit;
    private String description;
    private double updateRateSeconds = 1.0;
    private final Map<String, String> inputs = new LinkedHashMap<>();
    private final Map<String, Double> parameters = new LinkedHashMap<>();

    /**
     * Construct a soft-sensor definition.
     *
     * @param name the sensor name
     * @param sensorType the sensor type
     */
    public SoftSensorDefinition(String name, SensorType sensorType) {
      this.name = name;
      this.sensorType = sensorType;
    }

    /**
     * Get the sensor name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Get the sensor type.
     *
     * @return the type
     */
    public SensorType getSensorType() {
      return sensorType;
    }

    /**
     * Get the equipment name.
     *
     * @return the equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Set the equipment name.
     *
     * @param equipmentName the equipment name
     */
    public void setEquipmentName(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    /**
     * Get the component name (for composition sensors).
     *
     * @return the component name
     */
    public String getComponentName() {
      return componentName;
    }

    /**
     * Set the component name.
     *
     * @param componentName the component name
     */
    public void setComponentName(String componentName) {
      this.componentName = componentName;
    }

    /**
     * Get the output unit.
     *
     * @return the output unit
     */
    public String getOutputUnit() {
      return outputUnit;
    }

    /**
     * Set the output unit.
     *
     * @param outputUnit the output unit
     */
    public void setOutputUnit(String outputUnit) {
      this.outputUnit = outputUnit;
    }

    /**
     * Get the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Set the description.
     *
     * @param description the description
     * @return this definition for method chaining
     */
    public SoftSensorDefinition setDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * Get the update rate in seconds.
     *
     * @return the update rate
     */
    public double getUpdateRateSeconds() {
      return updateRateSeconds;
    }

    /**
     * Set the update rate.
     *
     * @param seconds update rate in seconds
     * @return this definition for method chaining
     */
    public SoftSensorDefinition setUpdateRateSeconds(double seconds) {
      this.updateRateSeconds = seconds;
      return this;
    }

    /**
     * Add an input to this sensor.
     *
     * @param inputName the input name
     * @param unit the input unit
     * @return this definition for method chaining
     */
    public SoftSensorDefinition addInput(String inputName, String unit) {
      inputs.put(inputName, unit);
      return this;
    }

    /**
     * Get the inputs.
     *
     * @return map of input names to units
     */
    public Map<String, String> getInputs() {
      return new LinkedHashMap<>(inputs);
    }

    /**
     * Add a parameter to this sensor.
     *
     * @param paramName the parameter name
     * @param value the parameter value
     * @return this definition for method chaining
     */
    public SoftSensorDefinition addParameter(String paramName, double value) {
      parameters.put(paramName, value);
      return this;
    }

    /**
     * Get the parameters.
     *
     * @return map of parameter names to values
     */
    public Map<String, Double> getParameters() {
      return new LinkedHashMap<>(parameters);
    }

    /**
     * Convert to map for JSON export.
     *
     * @param tagPrefix the tag prefix
     * @return map representation
     */
    public Map<String, Object> toMap(String tagPrefix) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", name);
      map.put("tag", tagPrefix + name);
      map.put("type", sensorType.name());
      map.put("equipmentName", equipmentName);
      if (componentName != null) {
        map.put("componentName", componentName);
      }
      map.put("outputUnit", outputUnit);
      map.put("updateRateSeconds", updateRateSeconds);
      if (description != null) {
        map.put("description", description);
      }

      // Inputs
      List<Map<String, String>> inputList = new ArrayList<>();
      for (Map.Entry<String, String> entry : inputs.entrySet()) {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("name", entry.getKey());
        input.put("unit", entry.getValue());
        input.put("tag", tagPrefix + equipmentName + "." + entry.getKey());
        inputList.add(input);
      }
      map.put("inputs", inputList);

      // Parameters
      if (!parameters.isEmpty()) {
        map.put("parameters", parameters);
      }

      return map;
    }
  }
}
