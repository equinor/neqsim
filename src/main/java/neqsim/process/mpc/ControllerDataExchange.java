package neqsim.process.mpc;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a real-time data exchange interface between NeqSim MPC and external control systems.
 *
 * <p>
 * This class enables bidirectional communication with industrial control systems by providing a
 * standardized interface for reading process values, writing setpoints and constraints, and
 * exchanging model data. The interface is designed to work with common industrial protocols and
 * data historians.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Timestamped data vectors for MVs, CVs, and DVs</li>
 * <li>Status flags for variable quality (good, bad, uncertain)</li>
 * <li>Setpoint and constraint updates from external sources</li>
 * <li>Model refresh triggers for adaptive control</li>
 * <li>Execution status and diagnostics</li>
 * </ul>
 *
 * <p>
 * The data exchange pattern supports both push and pull models:
 * </p>
 * <ul>
 * <li><b>Push:</b> Call {@link #updateInputs(double[], double[], double[])} to provide new process
 * data</li>
 * <li><b>Pull:</b> Call {@link #getOutputs()} to retrieve calculated control moves</li>
 * </ul>
 *
 * <p>
 * Example integration with an external control system:
 * </p>
 * 
 * <pre>
 * {@code
 * // Create data exchange interface
 * ControllerDataExchange exchange = mpc.createDataExchange();
 *
 * // Periodic execution loop
 * while (running) {
 *   // Read current values from plant (via OPC, historian, etc.)
 *   double[] mvValues = readMVsFromPlant();
 *   double[] cvValues = readCVsFromPlant();
 *   double[] dvValues = readDVsFromPlant();
 *
 *   // Update controller inputs
 *   exchange.updateInputs(mvValues, cvValues, dvValues);
 *
 *   // Check for setpoint changes from operator
 *   if (operatorChangedSetpoints) {
 *     exchange.updateSetpoints(newSetpoints);
 *   }
 *
 *   // Execute control calculation
 *   exchange.execute();
 *
 *   // Get outputs and write to plant
 *   ControllerOutput output = exchange.getOutputs();
 *   writeMVsToPlant(output.getMvTargets());
 *
 *   // Wait for next sample
 *   Thread.sleep(sampleTimeMs);
 * }
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class ControllerDataExchange implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Quality status flags. */
  public enum QualityStatus {
    /** Good quality data. */
    GOOD,
    /** Bad quality - value should not be used. */
    BAD,
    /** Uncertain quality - use with caution. */
    UNCERTAIN,
    /** Variable is in manual mode. */
    MANUAL,
    /** Variable is clamped at a limit. */
    CLAMPED
  }

  /** Execution status. */
  public enum ExecutionStatus {
    /** Controller ready, not yet executed. */
    READY,
    /** Controller executed successfully. */
    SUCCESS,
    /** Controller executed with warnings. */
    WARNING,
    /** Controller execution failed. */
    FAILED,
    /** Model needs update. */
    MODEL_STALE
  }

  /** The linked MPC controller. */
  private final ProcessLinkedMPC controller;

  /** Current MV values. */
  private double[] mvValues;

  /** Current CV values. */
  private double[] cvValues;

  /** Current DV values. */
  private double[] dvValues;

  /** MV quality flags. */
  private QualityStatus[] mvQuality;

  /** CV quality flags. */
  private QualityStatus[] cvQuality;

  /** DV quality flags. */
  private QualityStatus[] dvQuality;

  /** Current setpoints. */
  private double[] setpoints;

  /** CV low limits. */
  private double[] cvLowLimits;

  /** CV high limits. */
  private double[] cvHighLimits;

  /** Last input update timestamp. */
  private Instant lastInputUpdate;

  /** Last execution timestamp. */
  private Instant lastExecution;

  /** Last execution status. */
  private ExecutionStatus executionStatus = ExecutionStatus.READY;

  /** Last execution message. */
  private String executionMessage = "";

  /** Calculated MV targets. */
  private double[] mvTargets;

  /** Predicted CV trajectories. */
  private double[][] cvPredictions;

  /** Execution count. */
  private long executionCount = 0;

  /**
   * Construct a data exchange interface for a controller.
   *
   * @param controller the MPC controller
   */
  public ControllerDataExchange(ProcessLinkedMPC controller) {
    if (controller == null) {
      throw new IllegalArgumentException("Controller must not be null");
    }
    this.controller = controller;
    initialize();
  }

  private void initialize() {
    int numMV = controller.getManipulatedVariables().size();
    int numCV = controller.getControlledVariables().size();
    int numDV = controller.getDisturbanceVariables().size();

    mvValues = new double[numMV];
    cvValues = new double[numCV];
    dvValues = new double[numDV];

    mvQuality = new QualityStatus[numMV];
    cvQuality = new QualityStatus[numCV];
    dvQuality = new QualityStatus[numDV];

    for (int i = 0; i < numMV; i++) {
      mvQuality[i] = QualityStatus.GOOD;
    }
    for (int i = 0; i < numCV; i++) {
      cvQuality[i] = QualityStatus.GOOD;
    }
    for (int i = 0; i < numDV; i++) {
      dvQuality[i] = QualityStatus.GOOD;
    }

    setpoints = new double[numCV];
    cvLowLimits = new double[numCV];
    cvHighLimits = new double[numCV];

    List<ControlledVariable> cvs = controller.getControlledVariables();
    for (int i = 0; i < numCV; i++) {
      setpoints[i] = cvs.get(i).getSetpoint();
      cvLowLimits[i] = cvs.get(i).getSoftMin();
      cvHighLimits[i] = cvs.get(i).getSoftMax();
    }

    mvTargets = new double[numMV];
  }

  /**
   * Update input values from external source.
   *
   * @param mvValues current MV values
   * @param cvValues current CV values
   * @param dvValues current DV values (can be null if no DVs)
   */
  public void updateInputs(double[] mvValues, double[] cvValues, double[] dvValues) {
    if (mvValues != null && mvValues.length == this.mvValues.length) {
      System.arraycopy(mvValues, 0, this.mvValues, 0, mvValues.length);
    }
    if (cvValues != null && cvValues.length == this.cvValues.length) {
      System.arraycopy(cvValues, 0, this.cvValues, 0, cvValues.length);
    }
    if (dvValues != null && dvValues.length == this.dvValues.length) {
      System.arraycopy(dvValues, 0, this.dvValues, 0, dvValues.length);
    }
    lastInputUpdate = Instant.now();
  }

  /**
   * Update input values with quality flags.
   *
   * @param mvValues current MV values
   * @param cvValues current CV values
   * @param dvValues current DV values
   * @param mvQuality MV quality flags
   * @param cvQuality CV quality flags
   * @param dvQuality DV quality flags
   */
  public void updateInputsWithQuality(double[] mvValues, double[] cvValues, double[] dvValues,
      QualityStatus[] mvQuality, QualityStatus[] cvQuality, QualityStatus[] dvQuality) {
    updateInputs(mvValues, cvValues, dvValues);

    if (mvQuality != null && mvQuality.length == this.mvQuality.length) {
      System.arraycopy(mvQuality, 0, this.mvQuality, 0, mvQuality.length);
    }
    if (cvQuality != null && cvQuality.length == this.cvQuality.length) {
      System.arraycopy(cvQuality, 0, this.cvQuality, 0, cvQuality.length);
    }
    if (dvQuality != null && dvQuality.length == this.dvQuality.length) {
      System.arraycopy(dvQuality, 0, this.dvQuality, 0, dvQuality.length);
    }
  }

  /**
   * Update setpoints from external source.
   *
   * @param setpoints new setpoint values
   */
  public void updateSetpoints(double[] setpoints) {
    if (setpoints == null || setpoints.length != this.setpoints.length) {
      return;
    }

    List<ControlledVariable> cvs = controller.getControlledVariables();
    for (int i = 0; i < setpoints.length; i++) {
      if (Double.isFinite(setpoints[i])) {
        this.setpoints[i] = setpoints[i];
        cvs.get(i).setSetpoint(setpoints[i]);
      }
    }
  }

  /**
   * Update CV limits from external source.
   *
   * @param lowLimits new low limits
   * @param highLimits new high limits
   */
  public void updateLimits(double[] lowLimits, double[] highLimits) {
    List<ControlledVariable> cvs = controller.getControlledVariables();

    if (lowLimits != null && lowLimits.length == cvLowLimits.length) {
      for (int i = 0; i < lowLimits.length; i++) {
        if (Double.isFinite(lowLimits[i])) {
          cvLowLimits[i] = lowLimits[i];
        }
      }
    }

    if (highLimits != null && highLimits.length == cvHighLimits.length) {
      for (int i = 0; i < highLimits.length; i++) {
        if (Double.isFinite(highLimits[i])) {
          cvHighLimits[i] = highLimits[i];
        }
      }
    }

    // Apply to controller
    for (int i = 0; i < cvs.size(); i++) {
      cvs.get(i).setSoftConstraints(cvLowLimits[i], cvHighLimits[i]);
    }
  }

  /**
   * Execute the control calculation.
   *
   * @return true if execution was successful
   */
  public boolean execute() {
    try {
      // Check for bad quality inputs
      boolean hasGoodData = checkDataQuality();
      if (!hasGoodData) {
        executionStatus = ExecutionStatus.WARNING;
        executionMessage = "Some inputs have bad quality";
      }

      // Calculate control moves
      mvTargets = controller.calculate();

      // Update execution status
      executionStatus = hasGoodData ? ExecutionStatus.SUCCESS : ExecutionStatus.WARNING;
      executionMessage = "Execution completed";
      lastExecution = Instant.now();
      executionCount++;

      return true;
    } catch (Exception e) {
      executionStatus = ExecutionStatus.FAILED;
      executionMessage = "Execution failed: " + e.getMessage();
      lastExecution = Instant.now();
      return false;
    }
  }

  private boolean checkDataQuality() {
    for (QualityStatus q : mvQuality) {
      if (q == QualityStatus.BAD) {
        return false;
      }
    }
    for (QualityStatus q : cvQuality) {
      if (q == QualityStatus.BAD) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the controller outputs.
   *
   * @return a ControllerOutput object with targets and status
   */
  public ControllerOutput getOutputs() {
    return new ControllerOutput(mvTargets.clone(), cvPredictions, executionStatus, executionMessage,
        lastExecution);
  }

  /**
   * Get the MV target values.
   *
   * @return copy of MV target values
   */
  public double[] getMvTargets() {
    return mvTargets != null ? mvTargets.clone() : new double[0];
  }

  /**
   * Get the current setpoints.
   *
   * @return copy of setpoint values
   */
  public double[] getSetpoints() {
    return setpoints.clone();
  }

  /**
   * Get the execution status.
   *
   * @return the current execution status
   */
  public ExecutionStatus getExecutionStatus() {
    return executionStatus;
  }

  /**
   * Get the execution message.
   *
   * @return the last execution message
   */
  public String getExecutionMessage() {
    return executionMessage;
  }

  /**
   * Get the execution count.
   *
   * @return number of executions since creation
   */
  public long getExecutionCount() {
    return executionCount;
  }

  /**
   * Get the last input update time.
   *
   * @return timestamp of last input update
   */
  public Instant getLastInputUpdate() {
    return lastInputUpdate;
  }

  /**
   * Get the last execution time.
   *
   * @return timestamp of last execution
   */
  public Instant getLastExecution() {
    return lastExecution;
  }

  /**
   * Get variable names for external system configuration.
   *
   * @return map of variable lists with names
   */
  public Map<String, List<String>> getVariableNames() {
    Map<String, List<String>> names = new LinkedHashMap<>();

    List<String> mvNames = new ArrayList<>();
    for (ManipulatedVariable mv : controller.getManipulatedVariables()) {
      mvNames.add(mv.getName());
    }
    names.put("MV", mvNames);

    List<String> cvNames = new ArrayList<>();
    for (ControlledVariable cv : controller.getControlledVariables()) {
      cvNames.add(cv.getName());
    }
    names.put("CV", cvNames);

    List<String> dvNames = new ArrayList<>();
    for (DisturbanceVariable dv : controller.getDisturbanceVariables()) {
      dvNames.add(dv.getName());
    }
    names.put("DV", dvNames);

    return names;
  }

  /**
   * Get complete status for external monitoring.
   *
   * @return map of status information
   */
  public Map<String, Object> getStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("controller", controller.getName());
    status.put("executionStatus", executionStatus.name());
    status.put("executionMessage", executionMessage);
    status.put("executionCount", executionCount);
    status.put("lastInputUpdate", lastInputUpdate != null ? lastInputUpdate.toString() : null);
    status.put("lastExecution", lastExecution != null ? lastExecution.toString() : null);
    status.put("modelIdentified", controller.isModelIdentified());
    status.put("sampleTime", controller.getSampleTime());
    status.put("predictionHorizon", controller.getPredictionHorizon());
    status.put("controlHorizon", controller.getControlHorizon());
    status.put("numMV", controller.getManipulatedVariables().size());
    status.put("numCV", controller.getControlledVariables().size());
    status.put("numDV", controller.getDisturbanceVariables().size());
    return status;
  }

  /**
   * Container for controller output data.
   */
  public static class ControllerOutput implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double[] mvTargets;
    private final double[][] cvPredictions;
    private final ExecutionStatus status;
    private final String message;
    private final Instant timestamp;

    /**
     * Construct a controller output.
     *
     * @param mvTargets the MV target values
     * @param cvPredictions the CV prediction trajectories
     * @param status the execution status
     * @param message the execution message
     * @param timestamp the execution timestamp
     */
    public ControllerOutput(double[] mvTargets, double[][] cvPredictions, ExecutionStatus status,
        String message, Instant timestamp) {
      this.mvTargets = mvTargets;
      this.cvPredictions = cvPredictions;
      this.status = status;
      this.message = message;
      this.timestamp = timestamp;
    }

    /**
     * Get MV target values.
     *
     * @return copy of MV targets
     */
    public double[] getMvTargets() {
      return mvTargets != null ? mvTargets.clone() : new double[0];
    }

    /**
     * Get CV predictions.
     *
     * @return copy of CV predictions
     */
    public double[][] getCvPredictions() {
      if (cvPredictions == null) {
        return null;
      }
      double[][] copy = new double[cvPredictions.length][];
      for (int i = 0; i < cvPredictions.length; i++) {
        copy[i] = cvPredictions[i].clone();
      }
      return copy;
    }

    /**
     * Get execution status.
     *
     * @return the status
     */
    public ExecutionStatus getStatus() {
      return status;
    }

    /**
     * Get execution message.
     *
     * @return the message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Get execution timestamp.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
      return timestamp;
    }

    /**
     * Check if execution was successful.
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
      return status == ExecutionStatus.SUCCESS;
    }
  }
}
