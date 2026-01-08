package neqsim.process.processmodel.lifecycle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Represents a serializable state snapshot of a ProcessSystem for lifecycle management.
 *
 * <p>
 * This class enables:
 * <ul>
 * <li><b>Checkpointing:</b> Save and restore simulation state</li>
 * <li><b>Digital Twin Lifecycle:</b> Track model evolution through concept → design →
 * operation</li>
 * <li><b>Version Control:</b> Export models to JSON for Git-based versioning</li>
 * <li><b>Knowledge Preservation:</b> Maintain institutional knowledge despite staff turnover</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... configure process ...
 *
 * // Export state
 * ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
 * state.setVersion("1.2.3");
 * state.setDescription("Post-commissioning tuned model");
 * state.saveToFile("asset_model_v1.2.3.json");
 *
 * // Later: restore
 * ProcessSystemState loaded = ProcessSystemState.loadFromFile("asset_model_v1.2.3.json");
 * ProcessSystem restored = loaded.toProcessSystem();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessSystemState implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String name;
  private String version;
  private String description;
  private Instant createdAt;
  private Instant lastModifiedAt;
  private String createdBy;
  private ModelMetadata metadata;
  private List<EquipmentState> equipmentStates;
  private Map<String, Object> customProperties;
  private String checksum;

  /**
   * Default constructor.
   */
  public ProcessSystemState() {
    this.createdAt = Instant.now();
    this.lastModifiedAt = Instant.now();
    this.equipmentStates = new ArrayList<>();
    this.customProperties = new HashMap<>();
    this.metadata = new ModelMetadata();
  }

  /**
   * Creates a state snapshot from a ProcessSystem.
   *
   * @param process the process system to capture
   * @return a new ProcessSystemState representing the current state
   */
  public static ProcessSystemState fromProcessSystem(ProcessSystem process) {
    ProcessSystemState state = new ProcessSystemState();
    state.name = process.getName();
    state.createdAt = Instant.now();
    state.lastModifiedAt = Instant.now();

    // Capture equipment states
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      EquipmentState eqState = EquipmentState.fromEquipment(equipment);
      state.equipmentStates.add(eqState);
    }

    // Generate checksum for integrity verification
    state.updateChecksum();

    return state;
  }

  /**
   * Reconstructs a ProcessSystem from this state.
   *
   * <p>
   * Note: Full reconstruction requires the original equipment classes to be available. This method
   * provides a foundation for model serialization; complete reconstruction may require additional
   * factory methods.
   * </p>
   *
   * @return a new ProcessSystem initialized with the captured state
   */
  public ProcessSystem toProcessSystem() {
    ProcessSystem process = new ProcessSystem(name);
    // Equipment reconstruction would require factory pattern
    // This is a foundation for future implementation
    return process;
  }

  /**
   * Saves this state to a JSON file.
   *
   * @param filePath path to the output file
   */
  public void saveToFile(String filePath) {
    this.lastModifiedAt = Instant.now();
    updateChecksum();

    Gson gson = createGson();
    try (FileWriter writer = new FileWriter(filePath)) {
      gson.toJson(this, writer);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save state to file: " + filePath, e);
    }
  }

  /**
   * Saves this state to a JSON file.
   *
   * @param file the output file
   */
  public void saveToFile(File file) {
    saveToFile(file.getAbsolutePath());
  }

  /**
   * Loads a state from a JSON file.
   *
   * @param filePath path to the input file
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromFile(String filePath) {
    Gson gson = createGson();
    try (FileReader reader = new FileReader(filePath)) {
      return gson.fromJson(reader, ProcessSystemState.class);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Loads a state from a JSON file.
   *
   * @param file the input file
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromFile(File file) {
    return loadFromFile(file.getAbsolutePath());
  }

  /**
   * Saves this state to a GZIP-compressed JSON file.
   *
   * <p>
   * Compressed files typically achieve 5-20x size reduction compared to plain JSON, making them
   * ideal for large process models with many equipment states.
   * </p>
   *
   * @param filePath path to the output file (recommended extension: .neqsim)
   */
  public void saveToCompressedFile(String filePath) {
    this.lastModifiedAt = Instant.now();
    updateChecksum();

    Gson gson = createGson();
    try (BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(filePath));
        GZIPOutputStream gzout = new GZIPOutputStream(fout);
        OutputStreamWriter writer = new OutputStreamWriter(gzout, StandardCharsets.UTF_8)) {
      gson.toJson(this, writer);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save compressed state to file: " + filePath, e);
    }
  }

  /**
   * Loads a state from a GZIP-compressed JSON file.
   *
   * @param filePath path to the compressed input file (.neqsim)
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromCompressedFile(String filePath) {
    Gson gson = createGson();
    try (BufferedInputStream fin = new BufferedInputStream(new FileInputStream(filePath));
        GZIPInputStream gzin = new GZIPInputStream(fin);
        InputStreamReader reader = new InputStreamReader(gzin, StandardCharsets.UTF_8)) {
      return gson.fromJson(reader, ProcessSystemState.class);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Saves this state to a GZIP-compressed JSON file.
   *
   * @param file the output file (recommended extension: .neqsim)
   */
  public void saveToCompressedFile(File file) {
    saveToCompressedFile(file.getAbsolutePath());
  }

  /**
   * Loads a state from a GZIP-compressed JSON file.
   *
   * @param file the compressed input file (.neqsim)
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromCompressedFile(File file) {
    return loadFromCompressedFile(file.getAbsolutePath());
  }

  /**
   * Saves this state to a file, automatically detecting whether to use compression.
   *
   * <p>
   * If the file path ends with ".neqsim", the file will be GZIP-compressed. Otherwise, it will be
   * saved as plain JSON.
   * </p>
   *
   * @param filePath path to the output file
   */
  public void saveToFileAuto(String filePath) {
    if (filePath.toLowerCase().endsWith(".neqsim")) {
      saveToCompressedFile(filePath);
    } else {
      saveToFile(filePath);
    }
  }

  /**
   * Loads a state from a file, automatically detecting whether it is compressed.
   *
   * <p>
   * If the file path ends with ".neqsim", it will be read as a GZIP-compressed file. Otherwise, it
   * will be read as plain JSON.
   * </p>
   *
   * @param filePath path to the input file
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromFileAuto(String filePath) {
    if (filePath.toLowerCase().endsWith(".neqsim")) {
      return loadFromCompressedFile(filePath);
    } else {
      return loadFromFile(filePath);
    }
  }

  /**
   * Saves this state to a file, automatically detecting whether to use compression.
   *
   * @param file the output file
   */
  public void saveToFileAuto(File file) {
    saveToFileAuto(file.getAbsolutePath());
  }

  /**
   * Loads a state from a file, automatically detecting whether it is compressed.
   *
   * @param file the input file
   * @return the loaded ProcessSystemState, or null if loading fails
   */
  public static ProcessSystemState loadFromFileAuto(File file) {
    return loadFromFileAuto(file.getAbsolutePath());
  }

  /**
   * Applies this saved state to an existing ProcessSystem.
   *
   * <p>
   * This method attempts to restore equipment states from the saved snapshot. The process structure
   * must match the saved state for successful restoration.
   * </p>
   *
   * @param process the process system to apply the state to
   */
  public void applyTo(ProcessSystem process) {
    for (EquipmentState eqState : equipmentStates) {
      ProcessEquipmentInterface equipment = process.getUnit(eqState.getName());
      if (equipment != null) {
        // Apply saved properties if we have matching equipment
        // This is a foundation - full implementation would apply numeric/string properties
      }
    }
  }

  /**
   * Exports this state to a JSON string.
   *
   * @return JSON representation of this state
   */
  public String toJson() {
    return createGson().toJson(this);
  }

  /**
   * Creates a ProcessSystemState from a JSON string.
   *
   * @param json the JSON string
   * @return the parsed ProcessSystemState
   */
  public static ProcessSystemState fromJson(String json) {
    return createGson().fromJson(json, ProcessSystemState.class);
  }

  private static Gson createGson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
        .registerTypeAdapter(Instant.class, new InstantAdapter()).create();
  }

  private void updateChecksum() {
    // Simple checksum based on content
    String content = name + version + equipmentStates.size() + lastModifiedAt;
    this.checksum = Integer.toHexString(content.hashCode());
  }

  /**
   * Validates the integrity of this state.
   *
   * @return true if the checksum matches
   */
  public boolean validateIntegrity() {
    String originalChecksum = this.checksum;
    updateChecksum();
    boolean valid = originalChecksum != null && originalChecksum.equals(this.checksum);
    this.checksum = originalChecksum;
    return valid;
  }

  // Getters and setters

  public String getName() {
    return name;
  }

  /**
   * Gets the process name (alias for getName).
   *
   * @return the process name
   */
  public String getProcessName() {
    return name;
  }

  /**
   * Gets the creation timestamp.
   *
   * @return the timestamp when this state was created
   */
  public Instant getTimestamp() {
    return createdAt;
  }

  public void setName(String name) {
    this.name = name;
    this.lastModifiedAt = Instant.now();
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
    this.lastModifiedAt = Instant.now();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
    this.lastModifiedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public ModelMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(ModelMetadata metadata) {
    this.metadata = metadata;
  }

  public List<EquipmentState> getEquipmentStates() {
    return equipmentStates;
  }

  public Map<String, Object> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperty(String key, Object value) {
    this.customProperties.put(key, value);
    this.lastModifiedAt = Instant.now();
  }

  public String getChecksum() {
    return checksum;
  }

  /**
   * Represents the state of a single piece of equipment.
   */
  public static class EquipmentState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String type;
    private Map<String, Double> numericProperties;
    private Map<String, String> stringProperties;
    private FluidState fluidState;

    /**
     * Default constructor.
     */
    public EquipmentState() {
      this.numericProperties = new HashMap<>();
      this.stringProperties = new HashMap<>();
    }

    /**
     * Creates an EquipmentState from an equipment instance.
     *
     * @param equipment the equipment to capture
     * @return a new EquipmentState
     */
    public static EquipmentState fromEquipment(ProcessEquipmentInterface equipment) {
      EquipmentState state = new EquipmentState();
      state.name = equipment.getName();
      state.type = equipment.getClass().getSimpleName();

      // Capture common properties
      SystemInterface thermo = equipment.getThermoSystem();
      if (thermo != null) {
        state.fluidState = FluidState.fromFluid(thermo);
      }

      // Capture equipment-specific properties
      captureEquipmentProperties(equipment, state);

      return state;
    }

    /**
     * Captures equipment-specific numeric and string properties.
     *
     * @param equipment the equipment to capture properties from
     * @param state the state to populate
     */
    private static void captureEquipmentProperties(ProcessEquipmentInterface equipment,
        EquipmentState state) {
      // Compressors (and Expanders which extend Compressor)
      if (equipment instanceof neqsim.process.equipment.compressor.Compressor) {
        neqsim.process.equipment.compressor.Compressor comp =
            (neqsim.process.equipment.compressor.Compressor) equipment;
        state.numericProperties.put("outletPressure", comp.getOutletPressure());
        state.numericProperties.put("polytropicEfficiency", comp.getPolytropicEfficiency());
        state.numericProperties.put("isentropicEfficiency", comp.getIsentropicEfficiency());
        state.numericProperties.put("power", comp.getPower());
        state.numericProperties.put("speed", comp.getSpeed());
      }

      // Pumps
      if (equipment instanceof neqsim.process.equipment.pump.Pump) {
        neqsim.process.equipment.pump.Pump pump = (neqsim.process.equipment.pump.Pump) equipment;
        state.numericProperties.put("outletPressure", pump.getOutletPressure());
        state.numericProperties.put("power", pump.getPower());
      }

      // Valves
      if (equipment instanceof neqsim.process.equipment.valve.ValveInterface) {
        neqsim.process.equipment.valve.ValveInterface valve =
            (neqsim.process.equipment.valve.ValveInterface) equipment;
        state.numericProperties.put("percentValveOpening", valve.getPercentValveOpening());
        if (equipment instanceof neqsim.process.equipment.valve.ThrottlingValve) {
          neqsim.process.equipment.valve.ThrottlingValve tv =
              (neqsim.process.equipment.valve.ThrottlingValve) equipment;
          state.numericProperties.put("outletPressure", tv.getOutletPressure());
          state.numericProperties.put("cv", tv.getCv());
        }
      }

      // Heaters
      if (equipment instanceof neqsim.process.equipment.heatexchanger.Heater) {
        neqsim.process.equipment.heatexchanger.Heater heater =
            (neqsim.process.equipment.heatexchanger.Heater) equipment;
        state.numericProperties.put("duty", heater.getDuty());
        state.numericProperties.put("outletTemperature", heater.getOutletTemperature());
      }

      // Coolers
      if (equipment instanceof neqsim.process.equipment.heatexchanger.Cooler) {
        neqsim.process.equipment.heatexchanger.Cooler cooler =
            (neqsim.process.equipment.heatexchanger.Cooler) equipment;
        state.numericProperties.put("duty", cooler.getDuty());
        state.numericProperties.put("outletTemperature", cooler.getOutletTemperature());
      }

      // Separators
      if (equipment instanceof neqsim.process.equipment.separator.Separator) {
        neqsim.process.equipment.separator.Separator sep =
            (neqsim.process.equipment.separator.Separator) equipment;
        state.numericProperties.put("pressure", sep.getPressure());
        state.numericProperties.put("temperature", sep.getTemperature());
        state.numericProperties.put("liquidLevel", sep.getLiquidLevel());
      }

      // Streams
      if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
        neqsim.process.equipment.stream.StreamInterface stream =
            (neqsim.process.equipment.stream.StreamInterface) equipment;
        state.numericProperties.put("temperature", stream.getTemperature());
        state.numericProperties.put("pressure", stream.getPressure());
        state.numericProperties.put("flowRate", stream.getFlowRate("kg/hr"));
      }
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public Map<String, Double> getNumericProperties() {
      return numericProperties;
    }

    public Map<String, String> getStringProperties() {
      return stringProperties;
    }

    public FluidState getFluidState() {
      return fluidState;
    }
  }

  /**
   * Represents the thermodynamic state of a fluid.
   */
  public static class FluidState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private double temperature; // K
    private double pressure; // Pa
    private int numberOfPhases;
    private Map<String, Double> composition; // mole fractions
    private String thermoModelClass;

    /**
     * Default constructor.
     */
    public FluidState() {
      this.composition = new HashMap<>();
    }

    /**
     * Creates a FluidState from a SystemInterface.
     *
     * @param fluid the fluid to capture
     * @return a new FluidState
     */
    public static FluidState fromFluid(SystemInterface fluid) {
      FluidState state = new FluidState();
      state.temperature = fluid.getTemperature();
      state.pressure = fluid.getPressure();
      state.numberOfPhases = fluid.getNumberOfPhases();
      state.thermoModelClass = fluid.getClass().getSimpleName();

      // Capture composition
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        state.composition.put(fluid.getComponent(i).getName(), fluid.getComponent(i).getz());
      }

      return state;
    }

    public double getTemperature() {
      return temperature;
    }

    public double getPressure() {
      return pressure;
    }

    public int getNumberOfPhases() {
      return numberOfPhases;
    }

    public Map<String, Double> getComposition() {
      return composition;
    }

    public String getThermoModelClass() {
      return thermoModelClass;
    }
  }

  /**
   * Gson adapter for Instant serialization.
   */
  private static class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
      String value = in.nextString();
      return value == null ? null : Instant.parse(value);
    }
  }
}
