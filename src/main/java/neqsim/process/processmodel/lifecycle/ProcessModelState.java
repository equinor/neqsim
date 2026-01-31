package neqsim.process.processmodel.lifecycle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Represents a serializable state snapshot of a ProcessModel containing multiple ProcessSystems.
 *
 * <p>
 * This class enables:
 * <ul>
 * <li><b>Multi-Process Checkpointing:</b> Save and restore complete multi-system models</li>
 * <li><b>Cross-Process Connections:</b> Track streams shared between ProcessSystems</li>
 * <li><b>Version Control:</b> Export models to JSON for Git-based versioning</li>
 * <li><b>Digital Twin Lifecycle:</b> Track model evolution through concept → design →
 * operation</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessModel model = new ProcessModel();
 * model.add("upstream", upstreamProcess);
 * model.add("downstream", downstreamProcess);
 * model.run();
 *
 * // Export state
 * ProcessModelState state = ProcessModelState.fromProcessModel(model);
 * state.setVersion("1.0.0");
 * state.setDescription("Full field development model");
 * state.saveToFile("field_model_v1.json");
 *
 * // Later: restore
 * ProcessModelState loaded = ProcessModelState.loadFromFile("field_model_v1.json");
 * ProcessModel restored = loaded.toProcessModel();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessModelState implements Serializable {
  private static final long serialVersionUID = 1001L;

  /** Current schema version for JSON format compatibility. */
  private static final String CURRENT_SCHEMA_VERSION = "1.0";

  /** Logger for this class. */
  private static final org.apache.logging.log4j.Logger logger =
      org.apache.logging.log4j.LogManager.getLogger(ProcessModelState.class);

  /** Schema version of this state (for migration support). */
  private String schemaVersion = CURRENT_SCHEMA_VERSION;

  private String name;
  private String version;
  private String description;
  private Instant createdAt;
  private Instant lastModifiedAt;
  private String createdBy;

  /** Map of process name to its state. */
  private Map<String, ProcessSystemState> processStates;

  /** Connections between different ProcessSystems. */
  private List<InterProcessConnection> interProcessConnections;

  /** Custom properties for extensibility. */
  private Map<String, Object> customProperties;

  /** Execution configuration. */
  private ExecutionConfig executionConfig;

  /**
   * Default constructor.
   */
  public ProcessModelState() {
    this.createdAt = Instant.now();
    this.lastModifiedAt = Instant.now();
    this.processStates = new LinkedHashMap<>();
    this.interProcessConnections = new ArrayList<>();
    this.customProperties = new HashMap<>();
    this.executionConfig = new ExecutionConfig();
  }

  /**
   * Creates a state snapshot from a ProcessModel.
   *
   * @param model the process model to capture
   * @return a new ProcessModelState representing the current state
   */
  public static ProcessModelState fromProcessModel(ProcessModel model) {
    ProcessModelState state = new ProcessModelState();
    state.createdAt = Instant.now();
    state.lastModifiedAt = Instant.now();

    // Capture execution configuration
    state.executionConfig.maxIterations = model.getMaxIterations();
    state.executionConfig.flowTolerance = model.getFlowTolerance();
    state.executionConfig.temperatureTolerance = model.getTemperatureTolerance();
    state.executionConfig.pressureTolerance = model.getPressureTolerance();
    state.executionConfig.useOptimizedExecution = model.isUseOptimizedExecution();

    // Capture each ProcessSystem's state
    for (ProcessSystem process : model.getAllProcesses()) {
      ProcessSystemState processState = ProcessSystemState.fromProcessSystem(process);
      state.processStates.put(process.getName(), processState);
    }

    // Capture inter-process connections
    state.captureInterProcessConnections(model);

    return state;
  }

  /**
   * Captures connections between different ProcessSystems in the model.
   *
   * <p>
   * This detects when streams from one ProcessSystem are referenced by another, enabling proper
   * reconstruction of complex multi-process models.
   * </p>
   *
   * @param model the process model to analyze
   */
  private void captureInterProcessConnections(ProcessModel model) {
    // Build a map of all stream names to their source process
    Map<String, String> streamToProcess = new HashMap<>();
    for (ProcessSystem process : model.getAllProcesses()) {
      for (Object unit : process.getUnitOperations()) {
        if (unit instanceof neqsim.process.equipment.stream.StreamInterface) {
          neqsim.process.equipment.stream.StreamInterface stream =
              (neqsim.process.equipment.stream.StreamInterface) unit;
          streamToProcess.put(stream.getName(), process.getName());
        }
      }
    }

    // Look for cross-references (streams used in processes they weren't defined in)
    // This is a heuristic - more sophisticated tracking could be added
    for (ProcessSystem process : model.getAllProcesses()) {
      String processName = process.getName();
      for (Object unit : process.getUnitOperations()) {
        // Check equipment that has input streams
        try {
          if (unit instanceof neqsim.process.equipment.separator.Separator) {
            neqsim.process.equipment.separator.Separator sep =
                (neqsim.process.equipment.separator.Separator) unit;
            checkAndAddInterProcessConnection(sep.getFeedStream(), processName, streamToProcess);
          } else if (unit instanceof neqsim.process.equipment.heatexchanger.Heater) {
            neqsim.process.equipment.heatexchanger.Heater heater =
                (neqsim.process.equipment.heatexchanger.Heater) unit;
            checkAndAddInterProcessConnection(heater.getInletStream(), processName,
                streamToProcess);
          } else if (unit instanceof neqsim.process.equipment.valve.ThrottlingValve) {
            neqsim.process.equipment.valve.ThrottlingValve valve =
                (neqsim.process.equipment.valve.ThrottlingValve) unit;
            checkAndAddInterProcessConnection(valve.getInletStream(), processName, streamToProcess);
          } else if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
            neqsim.process.equipment.compressor.Compressor comp =
                (neqsim.process.equipment.compressor.Compressor) unit;
            checkAndAddInterProcessConnection(comp.getInletStream(), processName, streamToProcess);
          } else if (unit instanceof neqsim.process.equipment.mixer.Mixer) {
            neqsim.process.equipment.mixer.Mixer mixer =
                (neqsim.process.equipment.mixer.Mixer) unit;
            for (int i = 0; i < mixer.getNumberOfInputStreams(); i++) {
              neqsim.process.equipment.stream.StreamInterface inStream = mixer.getStream(i);
              checkAndAddInterProcessConnection(inStream, processName, streamToProcess);
            }
          }
        } catch (Exception e) {
          logger.debug("Could not analyze inter-process connections for: " + unit, e);
        }
      }
    }
  }

  /**
   * Checks if a stream comes from a different process and adds an inter-process connection.
   *
   * @param stream the stream to check
   * @param currentProcess the name of the current process
   * @param streamToProcess map from stream names to their originating process
   */
  private void checkAndAddInterProcessConnection(
      neqsim.process.equipment.stream.StreamInterface stream, String currentProcess,
      Map<String, String> streamToProcess) {
    if (stream != null) {
      String sourceProcess = streamToProcess.get(stream.getName());
      if (sourceProcess != null && !sourceProcess.equals(currentProcess)) {
        interProcessConnections.add(
            new InterProcessConnection(sourceProcess, stream.getName(), currentProcess, "inlet"));
      }
    }
  }

  /**
   * Reconstructs a ProcessModel from this state.
   *
   * <p>
   * This creates ProcessSystems for each captured state. Full equipment reconstruction requires the
   * original equipment classes to be available.
   * </p>
   *
   * @return a new ProcessModel initialized with the captured state
   */
  public ProcessModel toProcessModel() {
    ProcessModel model = new ProcessModel();

    // Restore execution configuration
    model.setMaxIterations(executionConfig.maxIterations);
    model.setFlowTolerance(executionConfig.flowTolerance);
    model.setTemperatureTolerance(executionConfig.temperatureTolerance);
    model.setPressureTolerance(executionConfig.pressureTolerance);
    model.setUseOptimizedExecution(executionConfig.useOptimizedExecution);

    // Reconstruct each ProcessSystem
    for (Map.Entry<String, ProcessSystemState> entry : processStates.entrySet()) {
      String processName = entry.getKey();
      ProcessSystemState processState = entry.getValue();
      ProcessSystem process = processState.toProcessSystem();
      model.add(processName, process);
    }

    return model;
  }

  // ============ FILE I/O ============

  /**
   * Saves this state to a file (JSON or compressed JSON based on extension).
   *
   * @param filename the file path (use .json for plain JSON, .json.gz for compressed)
   */
  public void saveToFile(String filename) {
    this.lastModifiedAt = Instant.now();

    Gson gson = createGson();

    try {
      if (filename.endsWith(".gz")) {
        // Compressed JSON
        try (
            GZIPOutputStream gzOut =
                new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
            OutputStreamWriter writer = new OutputStreamWriter(gzOut, StandardCharsets.UTF_8)) {
          gson.toJson(this, writer);
        }
      } else {
        // Plain JSON
        try (OutputStreamWriter writer = new OutputStreamWriter(
            new BufferedOutputStream(new FileOutputStream(filename)), StandardCharsets.UTF_8)) {
          gson.toJson(this, writer);
        }
      }
      logger.debug("ProcessModelState saved to: " + filename);
    } catch (IOException e) {
      logger.error("Failed to save ProcessModelState to: " + filename, e);
      throw new RuntimeException("Failed to save ProcessModelState", e);
    }
  }

  /**
   * Loads a ProcessModelState from a file.
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModelState
   */
  public static ProcessModelState loadFromFile(String filename) {
    Gson gson = createGson();

    try {
      ProcessModelState state;
      if (filename.endsWith(".gz")) {
        // Compressed JSON
        try (
            GZIPInputStream gzIn =
                new GZIPInputStream(new BufferedInputStream(new FileInputStream(filename)));
            InputStreamReader reader = new InputStreamReader(gzIn, StandardCharsets.UTF_8)) {
          state = gson.fromJson(reader, ProcessModelState.class);
        }
      } else {
        // Plain JSON
        try (InputStreamReader reader = new InputStreamReader(
            new BufferedInputStream(new FileInputStream(filename)), StandardCharsets.UTF_8)) {
          state = gson.fromJson(reader, ProcessModelState.class);
        }
      }

      // Migrate if needed
      if (state != null) {
        state.migrateIfNeeded();
      }

      logger.debug("ProcessModelState loaded from: " + filename);
      return state;
    } catch (IOException e) {
      logger.error("Failed to load ProcessModelState from: " + filename, e);
      throw new RuntimeException("Failed to load ProcessModelState", e);
    }
  }

  /**
   * Migrates state from older schema versions if needed.
   */
  private void migrateIfNeeded() {
    if (schemaVersion == null) {
      schemaVersion = "1.0";
    }
    // Add migration logic here as schema evolves
    // Example:
    // if ("1.0".equals(schemaVersion)) {
    // // Migrate from 1.0 to 1.1
    // schemaVersion = "1.1";
    // }
  }

  /**
   * Creates a Gson instance with custom type adapters.
   *
   * @return configured Gson instance
   */
  private static Gson createGson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls()
        .serializeSpecialFloatingPointValues()
        .registerTypeAdapter(Instant.class, new InstantAdapter()).create();
  }

  // ============ VALIDATION ============

  /**
   * Validates the state for completeness and consistency.
   *
   * @return a ValidationResult with any issues found
   */
  public ValidationResult validate() {
    ValidationResult result = new ValidationResult();

    if (processStates == null || processStates.isEmpty()) {
      result.addError("No process states captured");
    }

    // Validate each process state
    if (processStates != null) {
      for (Map.Entry<String, ProcessSystemState> entry : processStates.entrySet()) {
        String processName = entry.getKey();
        ProcessSystemState processState = entry.getValue();
        if (processState == null) {
          result.addError("Null state for process: " + processName);
        } else {
          ProcessSystemState.ValidationResult psResult = processState.validate();
          if (!psResult.isValid()) {
            for (String error : psResult.getErrors()) {
              result.addError("[" + processName + "] " + error);
            }
            for (String warning : psResult.getWarnings()) {
              result.addWarning("[" + processName + "] " + warning);
            }
          }
        }
      }
    }

    // Validate inter-process connections
    if (interProcessConnections != null) {
      for (InterProcessConnection conn : interProcessConnections) {
        if (!processStates.containsKey(conn.sourceProcess)) {
          result.addWarning(
              "Inter-process connection references unknown source process: " + conn.sourceProcess);
        }
        if (!processStates.containsKey(conn.targetProcess)) {
          result.addWarning(
              "Inter-process connection references unknown target process: " + conn.targetProcess);
        }
      }
    }

    return result;
  }

  // ============ GETTERS AND SETTERS ============

  /**
   * Gets the schema version.
   *
   * @return the schema version
   */
  public String getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the version.
   *
   * @return the version
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the version.
   *
   * @param version the version
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Gets the description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets when this state was created.
   *
   * @return creation timestamp
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Gets when this state was last modified.
   *
   * @return last modification timestamp
   */
  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  /**
   * Gets who created this state.
   *
   * @return creator identifier
   */
  public String getCreatedBy() {
    return createdBy;
  }

  /**
   * Sets who created this state.
   *
   * @param createdBy creator identifier
   */
  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  /**
   * Gets the process states map.
   *
   * @return map of process name to ProcessSystemState
   */
  public Map<String, ProcessSystemState> getProcessStates() {
    return processStates;
  }

  /**
   * Gets inter-process connections.
   *
   * @return list of inter-process connections
   */
  public List<InterProcessConnection> getInterProcessConnections() {
    return interProcessConnections;
  }

  /**
   * Gets custom properties.
   *
   * @return custom properties map
   */
  public Map<String, Object> getCustomProperties() {
    return customProperties;
  }

  /**
   * Sets a custom property.
   *
   * @param key property key
   * @param value property value
   */
  public void setCustomProperty(String key, Object value) {
    this.customProperties.put(key, value);
  }

  /**
   * Gets the execution configuration.
   *
   * @return execution configuration
   */
  public ExecutionConfig getExecutionConfig() {
    return executionConfig;
  }

  /**
   * Gets the number of ProcessSystems in this state.
   *
   * @return number of process systems
   */
  public int getProcessCount() {
    return processStates != null ? processStates.size() : 0;
  }

  // ============ INNER CLASSES ============

  /**
   * Represents a connection between two ProcessSystems.
   */
  public static class InterProcessConnection implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceProcess;
    private String streamName;
    private String targetProcess;
    private String targetPort;

    /**
     * Default constructor.
     */
    public InterProcessConnection() {}

    /**
     * Creates an inter-process connection.
     *
     * @param sourceProcess name of the source ProcessSystem
     * @param streamName name of the connecting stream
     * @param targetProcess name of the target ProcessSystem
     * @param targetPort port on the target equipment
     */
    public InterProcessConnection(String sourceProcess, String streamName, String targetProcess,
        String targetPort) {
      this.sourceProcess = sourceProcess;
      this.streamName = streamName;
      this.targetProcess = targetProcess;
      this.targetPort = targetPort;
    }

    /** Gets source process name. */
    public String getSourceProcess() {
      return sourceProcess;
    }

    /** Gets stream name. */
    public String getStreamName() {
      return streamName;
    }

    /** Gets target process name. */
    public String getTargetProcess() {
      return targetProcess;
    }

    /** Gets target port. */
    public String getTargetPort() {
      return targetPort;
    }

    @Override
    public String toString() {
      return sourceProcess + "/" + streamName + " -> " + targetProcess + ":" + targetPort;
    }
  }

  /**
   * Execution configuration for ProcessModel.
   */
  public static class ExecutionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maxIterations = 50;
    private double flowTolerance = 1e-4;
    private double temperatureTolerance = 1e-4;
    private double pressureTolerance = 1e-4;
    private boolean useOptimizedExecution = true;

    /**
     * Gets max iterations.
     *
     * @return maximum number of iterations
     */
    public int getMaxIterations() {
      return maxIterations;
    }

    /**
     * Sets max iterations.
     *
     * @param maxIterations maximum number of iterations to set
     */
    public void setMaxIterations(int maxIterations) {
      this.maxIterations = maxIterations;
    }

    /** Gets flow tolerance. */
    public double getFlowTolerance() {
      return flowTolerance;
    }

    /** Sets flow tolerance. */
    public void setFlowTolerance(double flowTolerance) {
      this.flowTolerance = flowTolerance;
    }

    /** Gets temperature tolerance. */
    public double getTemperatureTolerance() {
      return temperatureTolerance;
    }

    /** Sets temperature tolerance. */
    public void setTemperatureTolerance(double temperatureTolerance) {
      this.temperatureTolerance = temperatureTolerance;
    }

    /** Gets pressure tolerance. */
    public double getPressureTolerance() {
      return pressureTolerance;
    }

    /** Sets pressure tolerance. */
    public void setPressureTolerance(double pressureTolerance) {
      this.pressureTolerance = pressureTolerance;
    }

    /** Checks if optimized execution is enabled. */
    public boolean isUseOptimizedExecution() {
      return useOptimizedExecution;
    }

    /** Sets optimized execution flag. */
    public void setUseOptimizedExecution(boolean useOptimizedExecution) {
      this.useOptimizedExecution = useOptimizedExecution;
    }
  }

  /**
   * Validation result for ProcessModelState.
   */
  public static class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    /**
     * Adds an error.
     *
     * @param error the error message to add
     */
    public void addError(String error) {
      errors.add(error);
    }

    /** Adds a warning. */
    public void addWarning(String warning) {
      warnings.add(warning);
    }

    /** Checks if valid (no errors). */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /** Gets errors. */
    public List<String> getErrors() {
      return errors;
    }

    /** Gets warnings. */
    public List<String> getWarnings() {
      return warnings;
    }
  }

  /**
   * Instant type adapter for Gson.
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
      if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      String value = in.nextString();
      if (value == null || value.isEmpty()) {
        return null;
      }
      return Instant.parse(value);
    }
  }
}
