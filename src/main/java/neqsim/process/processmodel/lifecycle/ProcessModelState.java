package neqsim.process.processmodel.lifecycle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
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
   * Serializes this state to a JSON string.
   *
   * @return JSON string representation
   */
  public String toJson() {
    Gson gson = createGson();
    return gson.toJson(this);
  }

  /**
   * Deserializes a ProcessModelState from a JSON string.
   *
   * @param json the JSON string to parse
   * @return the deserialized ProcessModelState
   */
  public static ProcessModelState fromJson(String json) {
    Gson gson = createGson();
    ProcessModelState state = gson.fromJson(json, ProcessModelState.class);
    if (state != null) {
      state.migrateIfNeeded();
    }
    return state;
  }

  /**
   * Saves this state to a GZIP-compressed JSON file.
   *
   * @param filename the file path (typically ending in .json.gz)
   */
  public void saveToCompressedFile(String filename) {
    if (!filename.endsWith(".gz")) {
      filename = filename + ".gz";
    }
    saveToFile(filename);
  }

  /**
   * Loads a ProcessModelState from a GZIP-compressed JSON file.
   *
   * @param filename the file path (typically ending in .json.gz)
   * @return the loaded ProcessModelState
   */
  public static ProcessModelState loadFromCompressedFile(String filename) {
    return loadFromFile(filename);
  }

  /**
   * Adds an inter-process connection manually.
   *
   * @param connection the connection to add
   */
  public void addInterProcessConnection(InterProcessConnection connection) {
    if (this.interProcessConnections == null) {
      this.interProcessConnections = new ArrayList<>();
    }
    this.interProcessConnections.add(connection);
  }

  /**
   * Gets all inter-process connections targeting a specific process.
   *
   * @param processName the target process name
   * @return list of connections targeting the specified process
   */
  public List<InterProcessConnection> getConnectionsTo(String processName) {
    List<InterProcessConnection> result = new ArrayList<>();
    if (interProcessConnections != null) {
      for (InterProcessConnection conn : interProcessConnections) {
        if (processName.equals(conn.getTargetProcess())) {
          result.add(conn);
        }
      }
    }
    return result;
  }

  /**
   * Serializes this state to a compressed byte array (GZIP-compressed JSON).
   *
   * @return the compressed byte array
   * @throws IOException if compression fails
   */
  public byte[] toCompressedBytes() throws IOException {
    Gson gson = createGson();
    String json = gson.toJson(this);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
        OutputStreamWriter writer = new OutputStreamWriter(gzos, StandardCharsets.UTF_8)) {
      writer.write(json);
    }
    return baos.toByteArray();
  }

  /**
   * Deserializes a ProcessModelState from a compressed byte array (GZIP-compressed JSON).
   *
   * @param data the compressed byte array
   * @return the deserialized ProcessModelState
   * @throws IOException if decompression fails
   */
  public static ProcessModelState fromCompressedBytes(byte[] data) throws IOException {
    Gson gson = createGson();
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    try (GZIPInputStream gzis = new GZIPInputStream(bais);
        InputStreamReader reader = new InputStreamReader(gzis, StandardCharsets.UTF_8)) {
      ProcessModelState state = gson.fromJson(reader, ProcessModelState.class);
      state.migrateIfNeeded();
      return state;
    }
  }

  /**
   * Serializes this state to JSON with the given serialization options.
   *
   * @param options serialization options controlling output format
   * @return JSON string
   */
  public String toJson(SerializationOptions options) {
    this.lastModifiedAt = Instant.now();
    GsonBuilder builder = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantAdapter())
        .serializeSpecialFloatingPointValues();
    if (options != null && options.isPrettyPrint()) {
      builder.setPrettyPrinting();
    }
    return builder.create().toJson(this);
  }

  /**
   * Compares two ProcessModelState instances and returns a diff describing the changes.
   *
   * @param oldState the baseline state
   * @param newState the updated state
   * @return a ModelDiff describing differences
   */
  public static ModelDiff compare(ProcessModelState oldState, ProcessModelState newState) {
    ModelDiff diff = new ModelDiff();
    Set<String> oldKeys =
        oldState.processStates != null ? oldState.processStates.keySet() : new HashSet<String>();
    Set<String> newKeys =
        newState.processStates != null ? newState.processStates.keySet() : new HashSet<String>();

    // Added processes
    for (String key : newKeys) {
      if (!oldKeys.contains(key)) {
        diff.addedEquipment.add(key);
      }
    }

    // Removed processes
    for (String key : oldKeys) {
      if (!newKeys.contains(key)) {
        diff.removedEquipment.add(key);
      }
    }

    // Modified - compare equipment counts and versions
    for (String key : newKeys) {
      if (oldKeys.contains(key)) {
        ProcessSystemState oldPs = oldState.processStates.get(key);
        ProcessSystemState newPs = newState.processStates.get(key);
        int oldEqCount = oldPs.getEquipmentStates() != null ? oldPs.getEquipmentStates().size() : 0;
        int newEqCount = newPs.getEquipmentStates() != null ? newPs.getEquipmentStates().size() : 0;
        if (oldEqCount != newEqCount) {
          diff.modifiedParameters.put(key, "equipmentCount: " + oldEqCount + " -> " + newEqCount);
        }
      }
    }

    // Compare versions
    if (oldState.version != null && newState.version != null
        && !oldState.version.equals(newState.version)) {
      diff.modifiedParameters.put("version", oldState.version + " -> " + newState.version);
    }

    return diff;
  }

  /**
   * Migrates a ProcessModelState to the specified target schema version.
   *
   * @param state the state to migrate
   * @param targetVersion the target schema version string
   * @return the migrated state (may be the same instance if already at target version)
   */
  public static ProcessModelState migrate(ProcessModelState state, String targetVersion) {
    if (state == null) {
      return null;
    }
    state.migrateIfNeeded();
    return state;
  }

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
   * Gets a single custom property by key.
   *
   * @param key property key
   * @return the property value, or null if not found
   */
  public Object getCustomProperty(String key) {
    return customProperties != null ? customProperties.get(key) : null;
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

    /**
     * Gets source process name.
     *
     * @return the source process name
     */
    public String getSourceProcess() {
      return sourceProcess;
    }

    /**
     * Sets source process name.
     *
     * @param sourceProcess the source process name
     */
    public void setSourceProcess(String sourceProcess) {
      this.sourceProcess = sourceProcess;
    }

    /**
     * Gets stream name.
     *
     * @return the stream name
     */
    public String getStreamName() {
      return streamName;
    }

    /**
     * Sets the stream name. Also available as {@link #setSourceStream(String)}.
     *
     * @param streamName the stream name
     */
    public void setStreamName(String streamName) {
      this.streamName = streamName;
    }

    /**
     * Sets the source stream name. Alias for {@link #setStreamName(String)}.
     *
     * @param streamName the source stream name
     */
    public void setSourceStream(String streamName) {
      this.streamName = streamName;
    }

    /**
     * Gets target process name.
     *
     * @return the target process name
     */
    public String getTargetProcess() {
      return targetProcess;
    }

    /**
     * Sets target process name.
     *
     * @param targetProcess the target process name
     */
    public void setTargetProcess(String targetProcess) {
      this.targetProcess = targetProcess;
    }

    /**
     * Gets target port.
     *
     * @return the target port
     */
    public String getTargetPort() {
      return targetPort;
    }

    /**
     * Sets target port. Also available as {@link #setTargetStream(String)}.
     *
     * @param targetPort the target port
     */
    public void setTargetPort(String targetPort) {
      this.targetPort = targetPort;
    }

    /**
     * Sets target stream name. Alias for {@link #setTargetPort(String)}.
     *
     * @param targetStream the target stream name
     */
    public void setTargetStream(String targetStream) {
      this.targetPort = targetStream;
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
    private String solverType = "sequential";
    private double tolerance = 1e-6;
    private boolean parallelExecution = false;
    private int numberOfThreads = 1;

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

    /**
     * Gets flow tolerance.
     *
     * @return the flow tolerance
     */
    public double getFlowTolerance() {
      return flowTolerance;
    }

    /**
     * Sets flow tolerance.
     *
     * @param flowTolerance the flow tolerance to set
     */
    public void setFlowTolerance(double flowTolerance) {
      this.flowTolerance = flowTolerance;
    }

    /**
     * Gets temperature tolerance.
     *
     * @return the temperature tolerance
     */
    public double getTemperatureTolerance() {
      return temperatureTolerance;
    }

    /**
     * Sets temperature tolerance.
     *
     * @param temperatureTolerance the temperature tolerance to set
     */
    public void setTemperatureTolerance(double temperatureTolerance) {
      this.temperatureTolerance = temperatureTolerance;
    }

    /**
     * Gets pressure tolerance.
     *
     * @return the pressure tolerance
     */
    public double getPressureTolerance() {
      return pressureTolerance;
    }

    /**
     * Sets pressure tolerance.
     *
     * @param pressureTolerance the pressure tolerance to set
     */
    public void setPressureTolerance(double pressureTolerance) {
      this.pressureTolerance = pressureTolerance;
    }

    /**
     * Checks if optimized execution is enabled.
     *
     * @return true if optimized execution is enabled
     */
    public boolean isUseOptimizedExecution() {
      return useOptimizedExecution;
    }

    /**
     * Sets optimized execution flag.
     *
     * @param useOptimizedExecution true to enable optimized execution
     */
    public void setUseOptimizedExecution(boolean useOptimizedExecution) {
      this.useOptimizedExecution = useOptimizedExecution;
    }

    /**
     * Gets the solver type.
     *
     * @return the solver type string
     */
    public String getSolverType() {
      return solverType;
    }

    /**
     * Sets the solver type.
     *
     * @param solverType the solver type to set (e.g., "sequential", "simultaneous")
     */
    public void setSolverType(String solverType) {
      this.solverType = solverType;
    }

    /**
     * Gets the generic convergence tolerance.
     *
     * @return the tolerance value
     */
    public double getTolerance() {
      return tolerance;
    }

    /**
     * Sets the generic convergence tolerance.
     *
     * @param tolerance the tolerance value to set
     */
    public void setTolerance(double tolerance) {
      this.tolerance = tolerance;
    }

    /**
     * Checks if parallel execution is enabled.
     *
     * @return true if parallel execution is enabled
     */
    public boolean isParallelExecution() {
      return parallelExecution;
    }

    /**
     * Sets parallel execution flag.
     *
     * @param parallelExecution true to enable parallel execution
     */
    public void setParallelExecution(boolean parallelExecution) {
      this.parallelExecution = parallelExecution;
    }

    /**
     * Gets the number of threads for parallel execution.
     *
     * @return the number of threads
     */
    public int getNumberOfThreads() {
      return numberOfThreads;
    }

    /**
     * Sets the number of threads for parallel execution.
     *
     * @param numberOfThreads the number of threads to use
     */
    public void setNumberOfThreads(int numberOfThreads) {
      this.numberOfThreads = numberOfThreads;
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

    /**
     * Adds a warning.
     *
     * @param warning the warning message to add
     */
    public void addWarning(String warning) {
      warnings.add(warning);
    }

    /**
     * Checks if valid (no errors).
     *
     * @return true if there are no errors
     */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /**
     * Gets errors.
     *
     * @return the list of error messages
     */
    public List<String> getErrors() {
      return errors;
    }

    /**
     * Gets warnings.
     *
     * @return the list of warning messages
     */
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

  /**
   * Represents the difference between two ProcessModelState instances.
   */
  public static class ModelDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> addedEquipment = new ArrayList<>();
    private List<String> removedEquipment = new ArrayList<>();
    private Map<String, String> modifiedParameters = new HashMap<>();

    /**
     * Gets the list of added equipment or process names.
     *
     * @return list of added names
     */
    public List<String> getAddedEquipment() {
      return addedEquipment;
    }

    /**
     * Gets the list of removed equipment or process names.
     *
     * @return list of removed names
     */
    public List<String> getRemovedEquipment() {
      return removedEquipment;
    }

    /**
     * Gets modified parameters with change descriptions.
     *
     * @return map of parameter name to change description
     */
    public Map<String, String> getModifiedParameters() {
      return modifiedParameters;
    }

    /**
     * Checks if there are any differences.
     *
     * @return true if there are changes
     */
    public boolean hasChanges() {
      return !addedEquipment.isEmpty() || !removedEquipment.isEmpty()
          || !modifiedParameters.isEmpty();
    }

    @Override
    public String toString() {
      return "ModelDiff{added=" + addedEquipment.size() + ", removed=" + removedEquipment.size()
          + ", modified=" + modifiedParameters.size() + "}";
    }
  }

  /**
   * Options controlling JSON serialization of ProcessModelState.
   */
  public static class SerializationOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean prettyPrint = true;
    private boolean includeTimestamps = true;
    private boolean compressStreams = false;
    private boolean schemaValidation = true;

    /**
     * Checks if pretty printing is enabled.
     *
     * @return true if pretty printing is enabled
     */
    public boolean isPrettyPrint() {
      return prettyPrint;
    }

    /**
     * Sets pretty printing.
     *
     * @param prettyPrint true to enable pretty printing
     */
    public void setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
    }

    /**
     * Checks if timestamps are included.
     *
     * @return true if timestamps are included
     */
    public boolean isIncludeTimestamps() {
      return includeTimestamps;
    }

    /**
     * Sets whether to include timestamps.
     *
     * @param includeTimestamps true to include timestamps
     */
    public void setIncludeTimestamps(boolean includeTimestamps) {
      this.includeTimestamps = includeTimestamps;
    }

    /**
     * Checks if stream compression is enabled.
     *
     * @return true if stream compression is enabled
     */
    public boolean isCompressStreams() {
      return compressStreams;
    }

    /**
     * Sets stream compression.
     *
     * @param compressStreams true to enable stream compression
     */
    public void setCompressStreams(boolean compressStreams) {
      this.compressStreams = compressStreams;
    }

    /**
     * Checks if schema validation is enabled.
     *
     * @return true if schema validation is enabled
     */
    public boolean isSchemaValidation() {
      return schemaValidation;
    }

    /**
     * Sets schema validation.
     *
     * @param schemaValidation true to enable schema validation
     */
    public void setSchemaValidation(boolean schemaValidation) {
      this.schemaValidation = schemaValidation;
    }
  }
}
