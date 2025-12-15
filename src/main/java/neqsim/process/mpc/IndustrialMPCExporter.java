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

/**
 * Exports MPC models and configurations in formats compatible with industrial control systems.
 *
 * <p>
 * This class provides export capabilities for integrating NeqSim-generated models with external MPC
 * platforms commonly used in process industries. It supports multiple standard formats including:
 * </p>
 * <ul>
 * <li>Step response coefficients (DMC-style) for dynamic matrix controllers</li>
 * <li>Transfer function parameters (FOPDT, SOPDT) for model-based controllers</li>
 * <li>Variable configuration with OPC-style naming conventions</li>
 * <li>Gain matrices and time constants for linear MPC</li>
 * </ul>
 *
 * <p>
 * The exports include complete variable definitions with engineering units, limits, tuning
 * parameters, and model data in formats that can be directly imported into industrial MPC
 * configuration tools.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Configure and identify model
 * ProcessLinkedMPC mpc = new ProcessLinkedMPC("separator_control", process);
 * mpc.addMV("valve", "opening", 0.0, 100.0);
 * mpc.addCV("separator", "pressure", 30.0);
 * mpc.identifyModel(60.0);
 *
 * // Export for industrial MPC
 * IndustrialMPCExporter exporter = new IndustrialMPCExporter(mpc);
 * exporter.exportStepResponseModel("model_step_response.json");
 * exporter.exportVariableConfiguration("variables.json");
 * exporter.exportGainMatrix("gains.csv");
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class IndustrialMPCExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The MPC controller to export. */
  private final ProcessLinkedMPC controller;

  /** Step response generator for detailed model export. */
  private StepResponseGenerator stepResponseGenerator;

  /** Number of step response coefficients to export. */
  private int numStepCoefficients = 60;

  /** Default time constant for FOPDT models (seconds). */
  private double defaultTimeConstant = 60.0;

  /** Default dead time for FOPDT models (seconds). */
  private double defaultDeadTime = 0.0;

  /** Application name for exported files. */
  private String applicationName = "NeqSim";

  /** Tag prefix for variable naming. */
  private String tagPrefix = "";

  /**
   * Construct an exporter for a ProcessLinkedMPC.
   *
   * @param controller the MPC controller to export
   */
  public IndustrialMPCExporter(ProcessLinkedMPC controller) {
    if (controller == null) {
      throw new IllegalArgumentException("Controller must not be null");
    }
    this.controller = controller;
  }

  /**
   * Set the number of step response coefficients to export.
   *
   * @param numCoefficients number of coefficients (typically 30-120)
   * @return this exporter for method chaining
   */
  public IndustrialMPCExporter setNumStepCoefficients(int numCoefficients) {
    if (numCoefficients < 5 || numCoefficients > 500) {
      throw new IllegalArgumentException("Number of coefficients must be between 5 and 500");
    }
    this.numStepCoefficients = numCoefficients;
    return this;
  }

  /**
   * Set the default time constant for FOPDT models.
   *
   * @param timeConstant time constant in seconds
   * @return this exporter for method chaining
   */
  public IndustrialMPCExporter setDefaultTimeConstant(double timeConstant) {
    if (timeConstant <= 0) {
      throw new IllegalArgumentException("Time constant must be positive");
    }
    this.defaultTimeConstant = timeConstant;
    return this;
  }

  /**
   * Set the tag prefix for variable naming.
   *
   * <p>
   * This prefix is prepended to all variable names in exports, useful for matching plant tag naming
   * conventions.
   * </p>
   *
   * @param prefix the tag prefix (e.g., "UNIT1.")
   * @return this exporter for method chaining
   */
  public IndustrialMPCExporter setTagPrefix(String prefix) {
    this.tagPrefix = prefix != null ? prefix : "";
    return this;
  }

  /**
   * Set the application name for exported files.
   *
   * @param name the application name
   * @return this exporter for method chaining
   */
  public IndustrialMPCExporter setApplicationName(String name) {
    this.applicationName = name != null ? name : "NeqSim";
    return this;
  }

  /**
   * Export the complete MPC model in step response coefficient format.
   *
   * <p>
   * This format is commonly used by Dynamic Matrix Control (DMC) style controllers. Each MV-CV pair
   * is represented by a vector of step response coefficients that describe the dynamic response.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportStepResponseModel(String filename) throws IOException {
    LinearizationResult result = controller.getLinearizationResult();
    if (result == null) {
      throw new IllegalStateException(
          "Model not identified. Call controller.identifyModel() first");
    }

    Map<String, Object> model = new LinkedHashMap<>();
    model.put("format", "step_response_model");
    model.put("version", "1.0");
    model.put("application", applicationName);
    model.put("controller", controller.getName());
    model.put("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    model.put("sampleTime", controller.getSampleTime());
    model.put("sampleTimeUnit", "seconds");
    model.put("predictionHorizon", controller.getPredictionHorizon());
    model.put("controlHorizon", controller.getControlHorizon());
    model.put("numCoefficients", numStepCoefficients);

    // Export MV configuration
    List<Map<String, Object>> mvList = new ArrayList<>();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    for (int i = 0; i < mvs.size(); i++) {
      ManipulatedVariable mv = mvs.get(i);
      Map<String, Object> mvConfig = createMVConfig(mv, i);
      mvList.add(mvConfig);
    }
    model.put("manipulatedVariables", mvList);

    // Export CV configuration
    List<Map<String, Object>> cvList = new ArrayList<>();
    List<ControlledVariable> cvs = controller.getControlledVariables();
    for (int i = 0; i < cvs.size(); i++) {
      ControlledVariable cv = cvs.get(i);
      Map<String, Object> cvConfig = createCVConfig(cv, i);
      cvList.add(cvConfig);
    }
    model.put("controlledVariables", cvList);

    // Export step response matrix
    List<Map<String, Object>> responses = new ArrayList<>();
    double[][] gains = result.getGainMatrix();

    for (int cvIdx = 0; cvIdx < cvs.size(); cvIdx++) {
      for (int mvIdx = 0; mvIdx < mvs.size(); mvIdx++) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cv", tagPrefix + cvs.get(cvIdx).getName());
        response.put("mv", tagPrefix + mvs.get(mvIdx).getName());
        response.put("cvIndex", cvIdx);
        response.put("mvIndex", mvIdx);

        double gain = gains[cvIdx][mvIdx];
        double tau = defaultTimeConstant;

        // Generate step response coefficients
        double[] coefficients = generateStepCoefficients(gain, tau, controller.getSampleTime());
        response.put("gain", gain);
        response.put("timeConstant", tau);
        response.put("deadTime", defaultDeadTime);
        response.put("coefficients", coefficients);

        responses.add(response);
      }
    }
    model.put("stepResponses", responses);

    // Write JSON
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(model));
    }
  }

  /**
   * Export variable configuration in a format suitable for industrial control systems.
   *
   * <p>
   * The output includes all MVs, CVs, and DVs with their limits, setpoints, engineering units, and
   * tuning parameters in a standardized format.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportVariableConfiguration(String filename) throws IOException {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("format", "variable_configuration");
    config.put("version", "1.0");
    config.put("application", applicationName);
    config.put("controller", controller.getName());
    config.put("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

    // Controller parameters
    Map<String, Object> controllerParams = new LinkedHashMap<>();
    controllerParams.put("sampleTime", controller.getSampleTime());
    controllerParams.put("sampleTimeUnit", "seconds");
    controllerParams.put("predictionHorizon", controller.getPredictionHorizon());
    controllerParams.put("controlHorizon", controller.getControlHorizon());
    config.put("controllerParameters", controllerParams);

    // MVs
    List<Map<String, Object>> mvList = new ArrayList<>();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    for (int i = 0; i < mvs.size(); i++) {
      mvList.add(createMVConfig(mvs.get(i), i));
    }
    config.put("manipulatedVariables", mvList);

    // CVs
    List<Map<String, Object>> cvList = new ArrayList<>();
    List<ControlledVariable> cvs = controller.getControlledVariables();
    for (int i = 0; i < cvs.size(); i++) {
      cvList.add(createCVConfig(cvs.get(i), i));
    }
    config.put("controlledVariables", cvList);

    // DVs
    List<Map<String, Object>> dvList = new ArrayList<>();
    List<DisturbanceVariable> dvs = controller.getDisturbanceVariables();
    for (int i = 0; i < dvs.size(); i++) {
      dvList.add(createDVConfig(dvs.get(i), i));
    }
    config.put("disturbanceVariables", dvList);

    // Write JSON
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(config));
    }
  }

  /**
   * Export the gain matrix in CSV format.
   *
   * <p>
   * The matrix shows the steady-state gain from each MV to each CV. Row headers are CV names,
   * column headers are MV names.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportGainMatrix(String filename) throws IOException {
    LinearizationResult result = controller.getLinearizationResult();
    if (result == null) {
      throw new IllegalStateException("Model not identified");
    }

    double[][] gains = result.getGainMatrix();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    List<ControlledVariable> cvs = controller.getControlledVariables();

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Header row
      writer.write("CV\\MV");
      for (ManipulatedVariable mv : mvs) {
        writer.write("," + tagPrefix + mv.getName());
      }
      writer.newLine();

      // Data rows
      for (int i = 0; i < cvs.size(); i++) {
        writer.write(tagPrefix + cvs.get(i).getName());
        for (int j = 0; j < mvs.size(); j++) {
          writer.write("," + gains[i][j]);
        }
        writer.newLine();
      }
    }
  }

  /**
   * Export transfer function parameters in CSV format.
   *
   * <p>
   * Each row contains the FOPDT (First Order Plus Dead Time) parameters for one MV-CV pair: gain,
   * time constant, and dead time.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportTransferFunctions(String filename) throws IOException {
    LinearizationResult result = controller.getLinearizationResult();
    if (result == null) {
      throw new IllegalStateException("Model not identified");
    }

    double[][] gains = result.getGainMatrix();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    List<ControlledVariable> cvs = controller.getControlledVariables();

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Header
      writer.write("CV,MV,Gain,TimeConstant,DeadTime,ModelType");
      writer.newLine();

      // Data
      for (int i = 0; i < cvs.size(); i++) {
        for (int j = 0; j < mvs.size(); j++) {
          writer.write(tagPrefix + cvs.get(i).getName());
          writer.write("," + tagPrefix + mvs.get(j).getName());
          writer.write("," + gains[i][j]);
          writer.write("," + defaultTimeConstant);
          writer.write("," + defaultDeadTime);
          writer.write(",FOPDT");
          writer.newLine();
        }
      }
    }
  }

  /**
   * Export the complete model configuration as a single comprehensive JSON file.
   *
   * <p>
   * This format includes all information needed to configure an external MPC controller: variable
   * definitions, model parameters, tuning weights, and constraints.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportComprehensiveConfiguration(String filename) throws IOException {
    LinearizationResult result = controller.getLinearizationResult();
    if (result == null) {
      throw new IllegalStateException("Model not identified");
    }

    Map<String, Object> export = new LinkedHashMap<>();

    // Header
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("format", "comprehensive_mpc_configuration");
    header.put("version", "1.0");
    header.put("application", applicationName);
    header.put("sourceSimulator", "NeqSim");
    header.put("controller", controller.getName());
    header.put("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    export.put("header", header);

    // Controller tuning
    Map<String, Object> tuning = new LinkedHashMap<>();
    tuning.put("sampleTime", controller.getSampleTime());
    tuning.put("sampleTimeUnit", "seconds");
    tuning.put("predictionHorizon", controller.getPredictionHorizon());
    tuning.put("controlHorizon", controller.getControlHorizon());
    tuning.put("numStepCoefficients", numStepCoefficients);
    export.put("tuning", tuning);

    // Variables
    Map<String, Object> variables = new LinkedHashMap<>();

    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    List<ControlledVariable> cvs = controller.getControlledVariables();
    List<DisturbanceVariable> dvs = controller.getDisturbanceVariables();

    List<Map<String, Object>> mvList = new ArrayList<>();
    for (int i = 0; i < mvs.size(); i++) {
      mvList.add(createMVConfig(mvs.get(i), i));
    }
    variables.put("mv", mvList);

    List<Map<String, Object>> cvList = new ArrayList<>();
    for (int i = 0; i < cvs.size(); i++) {
      cvList.add(createCVConfig(cvs.get(i), i));
    }
    variables.put("cv", cvList);

    List<Map<String, Object>> dvList = new ArrayList<>();
    for (int i = 0; i < dvs.size(); i++) {
      dvList.add(createDVConfig(dvs.get(i), i));
    }
    variables.put("dv", dvList);

    export.put("variables", variables);

    // Model matrices
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("type", "gain_matrix");
    model.put("gains", result.getGainMatrix());
    model.put("mvOperatingPoint", result.getMvOperatingPoint());
    model.put("cvOperatingPoint", result.getCvOperatingPoint());

    // Step response coefficients
    double[][] gains = result.getGainMatrix();
    double[][][] stepCoeffs = new double[cvs.size()][mvs.size()][numStepCoefficients];
    for (int i = 0; i < cvs.size(); i++) {
      for (int j = 0; j < mvs.size(); j++) {
        stepCoeffs[i][j] =
            generateStepCoefficients(gains[i][j], defaultTimeConstant, controller.getSampleTime());
      }
    }
    model.put("stepResponseCoefficients", stepCoeffs);

    export.put("model", model);

    // Write JSON
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(export));
    }
  }

  /**
   * Get a real-time data exchange object for online integration.
   *
   * @return a new ControllerDataExchange instance
   */
  public ControllerDataExchange createDataExchange() {
    return new ControllerDataExchange(controller);
  }

  // ================== Helper Methods ==================

  private Map<String, Object> createMVConfig(ManipulatedVariable mv, int index) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("index", index);
    config.put("name", mv.getName());
    config.put("tag", tagPrefix + mv.getName());
    config.put("description", mv.getDescription() != null ? mv.getDescription() : mv.getName());
    config.put("unit", mv.getUnit() != null ? mv.getUnit() : "");

    // Limits
    Map<String, Double> limits = new LinkedHashMap<>();
    limits.put("low", mv.getMinValue());
    limits.put("high", mv.getMaxValue());
    limits.put("rateLow", mv.getMinRateOfChange());
    limits.put("rateHigh", mv.getMaxRateOfChange());
    config.put("limits", limits);

    // Tuning
    Map<String, Double> tuning = new LinkedHashMap<>();
    tuning.put("moveWeight", mv.getMoveWeight());
    config.put("tuning", tuning);

    // Current value
    config.put("currentValue", mv.readValue());

    return config;
  }

  private Map<String, Object> createCVConfig(ControlledVariable cv, int index) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("index", index);
    config.put("name", cv.getName());
    config.put("tag", tagPrefix + cv.getName());
    config.put("description", cv.getDescription() != null ? cv.getDescription() : cv.getName());
    config.put("unit", cv.getUnit() != null ? cv.getUnit() : "");

    // Setpoint
    config.put("setpoint", cv.getSetpoint());
    config.put("zoneControl", cv.isZoneControl());
    if (cv.isZoneControl()) {
      config.put("zoneLow", cv.getZoneLower());
      config.put("zoneHigh", cv.getZoneUpper());
    }

    // Limits
    Map<String, Double> limits = new LinkedHashMap<>();
    limits.put("softLow", cv.getSoftMin());
    limits.put("softHigh", cv.getSoftMax());
    limits.put("hardLow", cv.getHardMin());
    limits.put("hardHigh", cv.getHardMax());
    config.put("limits", limits);

    // Tuning
    Map<String, Double> tuning = new LinkedHashMap<>();
    tuning.put("weight", cv.getWeight());
    tuning.put("softConstraintPenalty", cv.getSoftConstraintPenalty());
    config.put("tuning", tuning);

    // Current value
    config.put("currentValue", cv.readValue());

    return config;
  }

  private Map<String, Object> createDVConfig(DisturbanceVariable dv, int index) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("index", index);
    config.put("name", dv.getName());
    config.put("tag", tagPrefix + dv.getName());
    config.put("description", dv.getDescription() != null ? dv.getDescription() : dv.getName());
    config.put("unit", dv.getUnit() != null ? dv.getUnit() : "");

    // Predicted value if available
    if (Double.isFinite(dv.getPredictedValue())) {
      config.put("predictedValue", dv.getPredictedValue());
    }

    // Current value
    config.put("currentValue", dv.readValue());

    return config;
  }

  /**
   * Generate step response coefficients for a first-order system.
   *
   * @param gain steady-state gain
   * @param tau time constant (seconds)
   * @param sampleTime sample time (seconds)
   * @return array of step response coefficients
   */
  private double[] generateStepCoefficients(double gain, double tau, double sampleTime) {
    double[] coeffs = new double[numStepCoefficients];
    for (int k = 0; k < numStepCoefficients; k++) {
      double t = k * sampleTime;
      coeffs[k] = gain * (1.0 - Math.exp(-t / tau));
    }
    return coeffs;
  }

  /**
   * Export step response coefficients in tabular CSV format.
   *
   * <p>
   * This format is designed for easy import into industrial MPC systems. Each row contains the step
   * response coefficient for one time step, with columns for each MV-CV pair.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportStepResponseCSV(String filename) throws IOException {
    LinearizationResult result = controller.getLinearizationResult();
    if (result == null) {
      throw new IllegalStateException("Model not identified");
    }

    double[][] gains = result.getGainMatrix();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    List<ControlledVariable> cvs = controller.getControlledVariables();
    double sampleTime = controller.getSampleTime();

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Header: Step, then MV-CV pairs
      writer.write("Step,Time");
      for (int cvIdx = 0; cvIdx < cvs.size(); cvIdx++) {
        for (int mvIdx = 0; mvIdx < mvs.size(); mvIdx++) {
          writer.write("," + cvs.get(cvIdx).getName() + "/" + mvs.get(mvIdx).getName());
        }
      }
      writer.newLine();

      // Data rows
      for (int k = 0; k < numStepCoefficients; k++) {
        double t = k * sampleTime;
        writer.write(k + "," + t);

        for (int cvIdx = 0; cvIdx < cvs.size(); cvIdx++) {
          for (int mvIdx = 0; mvIdx < mvs.size(); mvIdx++) {
            double gain = gains[cvIdx][mvIdx];
            double coeff = gain * (1.0 - Math.exp(-t / defaultTimeConstant));
            writer.write("," + coeff);
          }
        }
        writer.newLine();
      }
    }
  }

  /**
   * Export model object structure as a hierarchical configuration.
   *
   * <p>
   * This format mirrors the object structure used by industrial control system cores for
   * configuration storage and data logging. It provides a complete description of the controller
   * that can be serialized/deserialized.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportObjectStructure(String filename) throws IOException {
    Map<String, Object> structure = new LinkedHashMap<>();

    // Root object
    structure.put("objectType", "MPCController");
    structure.put("objectName", controller.getName());
    structure.put("version", "1.0");
    structure.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

    // Controller configuration
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("sampleTime", controller.getSampleTime());
    config.put("predictionHorizon", controller.getPredictionHorizon());
    config.put("controlHorizon", controller.getControlHorizon());
    config.put("modelType", "LinearStepResponse");
    config.put("numStepCoefficients", numStepCoefficients);
    structure.put("configuration", config);

    // Variables as child objects
    List<Map<String, Object>> mvObjects = new ArrayList<>();
    List<ManipulatedVariable> mvs = controller.getManipulatedVariables();
    for (int i = 0; i < mvs.size(); i++) {
      Map<String, Object> mvObj = new LinkedHashMap<>();
      mvObj.put("objectType", "ManipulatedVariable");
      mvObj.put("objectName", mvs.get(i).getName());
      mvObj.put("index", i);
      mvObj.put("properties", createMVConfig(mvs.get(i), i));
      mvObjects.add(mvObj);
    }
    structure.put("manipulatedVariables", mvObjects);

    List<Map<String, Object>> cvObjects = new ArrayList<>();
    List<ControlledVariable> cvs = controller.getControlledVariables();
    for (int i = 0; i < cvs.size(); i++) {
      Map<String, Object> cvObj = new LinkedHashMap<>();
      cvObj.put("objectType", "ControlledVariable");
      cvObj.put("objectName", cvs.get(i).getName());
      cvObj.put("index", i);
      cvObj.put("properties", createCVConfig(cvs.get(i), i));
      cvObjects.add(cvObj);
    }
    structure.put("controlledVariables", cvObjects);

    List<Map<String, Object>> dvObjects = new ArrayList<>();
    List<DisturbanceVariable> dvs = controller.getDisturbanceVariables();
    for (int i = 0; i < dvs.size(); i++) {
      Map<String, Object> dvObj = new LinkedHashMap<>();
      dvObj.put("objectType", "DisturbanceVariable");
      dvObj.put("objectName", dvs.get(i).getName());
      dvObj.put("index", i);
      dvObj.put("properties", createDVConfig(dvs.get(i), i));
      dvObjects.add(dvObj);
    }
    structure.put("disturbanceVariables", dvObjects);

    // Model data
    LinearizationResult result = controller.getLinearizationResult();
    if (result != null) {
      Map<String, Object> model = new LinkedHashMap<>();
      model.put("gains", result.getGainMatrix());
      model.put("mvOperatingPoint", result.getMvOperatingPoint());
      model.put("cvOperatingPoint", result.getCvOperatingPoint());

      // Generate step coefficients
      double[][] gains = result.getGainMatrix();
      double[][][] stepCoeffs = new double[cvs.size()][mvs.size()][numStepCoefficients];
      for (int i = 0; i < cvs.size(); i++) {
        for (int j = 0; j < mvs.size(); j++) {
          stepCoeffs[i][j] = generateStepCoefficients(gains[i][j], defaultTimeConstant,
              controller.getSampleTime());
        }
      }
      model.put("stepResponseCoefficients", stepCoeffs);
      structure.put("model", model);
    }

    // Write JSON
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(structure));
    }
  }

  /**
   * Create a soft-sensor exporter for the process system used by this controller.
   *
   * @return a new SoftSensorExporter
   */
  public SoftSensorExporter createSoftSensorExporter() {
    return new SoftSensorExporter(controller.getProcessSystem()).setTagPrefix(tagPrefix)
        .setApplicationName(applicationName);
  }
}
