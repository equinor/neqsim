package neqsim.process.mpc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Bridge class that auto-configures and links MPC to a ProcessSystem.
 *
 * <p>
 * ProcessLinkedMPC provides automatic integration between NeqSim's ProcessSystem simulation and
 * Model Predictive Control. It handles:
 * </p>
 * <ul>
 * <li>Automatic model identification through linearization or step testing</li>
 * <li>Variable binding between MPC and process equipment</li>
 * <li>Model updating during online operation</li>
 * <li>Coordinated execution between controller and simulation</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Build process
 * ProcessSystem process = new ProcessSystem();
 * Stream feed = new Stream("feed", fluid);
 * Valve valve = new Valve("valve", feed);
 * Separator sep = new Separator("separator", valve.getOutletStream());
 * process.add(feed);
 * process.add(valve);
 * process.add(sep);
 * process.run();
 *
 * // Create linked MPC
 * ProcessLinkedMPC mpc = new ProcessLinkedMPC("levelController", process);
 *
 * // Define variables
 * mpc.addMV("valve", "opening", 0.0, 1.0);
 * mpc.addCV("separator", "liquidLevel", 50.0); // setpoint 50%
 * mpc.setConstraint("separator", "liquidLevel", 20.0, 80.0);
 *
 * // Auto-identify model
 * mpc.identifyModel(60.0); // 60s sample time
 *
 * // Configure tuning
 * mpc.setPredictionHorizon(20);
 * mpc.setControlHorizon(5);
 * mpc.setMoveSuppressionWeight("valve", 0.1);
 *
 * // Run control step
 * double[] moves = mpc.calculate();
 * mpc.applyMoves();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 * @see ProcessSystem
 */
public class ProcessLinkedMPC implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Controller name. */
  private final String name;

  /** The process system being controlled. */
  private final ProcessSystem processSystem;

  /** Manipulated variables. */
  private final List<ManipulatedVariable> manipulatedVariables;

  /** Controlled variables. */
  private final List<ControlledVariable> controlledVariables;

  /** Disturbance variables. */
  private final List<DisturbanceVariable> disturbanceVariables;

  /** State variables (for nonlinear MPC). */
  private final List<StateVariable> stateVariables;

  /** Linearizer for model identification. */
  private ProcessLinearizer linearizer;

  /** Last linearization result. */
  private LinearizationResult linearizationResult;

  /** Sample time in seconds. */
  private double sampleTime = 60.0;

  /** Prediction horizon (number of samples). */
  private int predictionHorizon = 20;

  /** Control horizon (number of samples). */
  private int controlHorizon = 5;

  /** Whether model has been identified. */
  private boolean modelIdentified = false;

  /** Last calculated MV moves. */
  private double[] lastMoves;

  /** Whether to use nonlinear prediction. */
  private boolean useNonlinearPrediction = false;

  /** Nonlinear predictor. */
  private NonlinearPredictor nonlinearPredictor;

  /** Model update interval (number of steps). */
  private int modelUpdateInterval = 0;

  /** Steps since last model update. */
  private int stepsSinceModelUpdate = 0;

  /** UUID for execution tracking. */
  private UUID executionId;

  /** Move suppression weights for MVs. */
  private double[] moveSuppressionWeights;

  /** Error weights for CVs. */
  private double[] errorWeights;

  /**
   * Construct a process-linked MPC controller.
   *
   * @param name the controller name
   * @param processSystem the process system to control
   */
  public ProcessLinkedMPC(String name, ProcessSystem processSystem) {
    this.name = name;
    this.processSystem = processSystem;
    this.manipulatedVariables = new ArrayList<>();
    this.controlledVariables = new ArrayList<>();
    this.disturbanceVariables = new ArrayList<>();
    this.stateVariables = new ArrayList<>();
    this.executionId = UUID.randomUUID();
  }

  /**
   * Add a manipulated variable.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name (opening, duty, flowRate, etc.)
   * @param minValue minimum value
   * @param maxValue maximum value
   * @return the created ManipulatedVariable
   */
  public ManipulatedVariable addMV(String equipmentName, String propertyName, double minValue,
      double maxValue) {
    ManipulatedVariable mv = new ManipulatedVariable(equipmentName + "." + propertyName,
        processSystem.getUnit(equipmentName), propertyName);
    mv.setBounds(minValue, maxValue);
    manipulatedVariables.add(mv);
    return mv;
  }

  /**
   * Add a manipulated variable with rate limits.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @param minValue minimum value
   * @param maxValue maximum value
   * @param maxRateOfChange maximum rate of change per sample
   * @return the created ManipulatedVariable
   */
  public ManipulatedVariable addMV(String equipmentName, String propertyName, double minValue,
      double maxValue, double maxRateOfChange) {
    ManipulatedVariable mv = addMV(equipmentName, propertyName, minValue, maxValue);
    mv.setRateLimit(-maxRateOfChange, maxRateOfChange);
    return mv;
  }

  /**
   * Add a controlled variable with setpoint.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name (pressure, temperature, level, etc.)
   * @param setpoint the setpoint value
   * @return the created ControlledVariable
   */
  public ControlledVariable addCV(String equipmentName, String propertyName, double setpoint) {
    ControlledVariable cv = new ControlledVariable(equipmentName + "." + propertyName,
        processSystem.getUnit(equipmentName), propertyName);
    cv.setSetpoint(setpoint);
    controlledVariables.add(cv);
    return cv;
  }

  /**
   * Add a controlled variable with zone control.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @param lowSetpoint low zone boundary
   * @param highSetpoint high zone boundary
   * @return the created ControlledVariable
   */
  public ControlledVariable addCVZone(String equipmentName, String propertyName, double lowSetpoint,
      double highSetpoint) {
    ControlledVariable cv = addCV(equipmentName, propertyName, (lowSetpoint + highSetpoint) / 2);
    cv.setZone(lowSetpoint, highSetpoint);
    return cv;
  }

  /**
   * Set constraints on a controlled variable.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @param minValue minimum constraint
   * @param maxValue maximum constraint
   */
  public void setConstraint(String equipmentName, String propertyName, double minValue,
      double maxValue) {
    String fullName = equipmentName + "." + propertyName;
    for (ControlledVariable cv : controlledVariables) {
      if (cv.getName().equals(fullName)) {
        cv.setHardConstraints(minValue, maxValue);
        return;
      }
    }
    throw new IllegalArgumentException("CV not found: " + fullName);
  }

  /**
   * Add a disturbance variable.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @return the created DisturbanceVariable
   */
  public DisturbanceVariable addDV(String equipmentName, String propertyName) {
    DisturbanceVariable dv = new DisturbanceVariable(equipmentName + "." + propertyName,
        processSystem.getUnit(equipmentName), propertyName);
    disturbanceVariables.add(dv);
    return dv;
  }

  /**
   * Add a state variable (SVR) for nonlinear MPC.
   *
   * <p>
   * State variables are internal model states that evolve according to dynamic equations. They are
   * tracked for model accuracy but not directly controlled. Examples include flow rates, internal
   * pressures, and calculated gains.
   * </p>
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @return the created StateVariable
   */
  public StateVariable addSVR(String equipmentName, String propertyName) {
    StateVariable svr = new StateVariable(equipmentName + "." + propertyName,
        processSystem.getUnit(equipmentName), propertyName);
    stateVariables.add(svr);
    return svr;
  }

  /**
   * Add a state variable with a data index.
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @param dtaIx data index for C++ code linking
   * @return the created StateVariable
   */
  public StateVariable addSVR(String equipmentName, String propertyName, String dtaIx) {
    StateVariable svr = addSVR(equipmentName, propertyName);
    svr.setDtaIx(dtaIx);
    return svr;
  }

  /**
   * Get all state variables.
   *
   * @return list of SVRs
   */
  public List<StateVariable> getStateVariables() {
    return new ArrayList<>(stateVariables);
  }

  /**
   * Get all manipulated variables.
   *
   * @return list of MVs
   */
  public List<ManipulatedVariable> getManipulatedVariables() {
    return new ArrayList<>(manipulatedVariables);
  }

  /**
   * Get all controlled variables.
   *
   * @return list of CVs
   */
  public List<ControlledVariable> getControlledVariables() {
    return new ArrayList<>(controlledVariables);
  }

  /**
   * Get all disturbance variables.
   *
   * @return list of DVs
   */
  public List<DisturbanceVariable> getDisturbanceVariables() {
    return new ArrayList<>(disturbanceVariables);
  }

  /**
   * Get the process system being controlled.
   *
   * @return the process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Set the sample time.
   *
   * @param sampleTimeSeconds sample time in seconds
   */
  public void setSampleTime(double sampleTimeSeconds) {
    this.sampleTime = sampleTimeSeconds;
  }

  /**
   * Get the sample time.
   *
   * @return sample time in seconds
   */
  public double getSampleTime() {
    return sampleTime;
  }

  /**
   * Set the prediction horizon.
   *
   * @param horizon number of samples
   */
  public void setPredictionHorizon(int horizon) {
    this.predictionHorizon = horizon;
  }

  /**
   * Get the prediction horizon.
   *
   * @return number of samples
   */
  public int getPredictionHorizon() {
    return predictionHorizon;
  }

  /**
   * Set the control horizon.
   *
   * @param horizon number of samples
   */
  public void setControlHorizon(int horizon) {
    this.controlHorizon = horizon;
  }

  /**
   * Get the control horizon.
   *
   * @return number of samples
   */
  public int getControlHorizon() {
    return controlHorizon;
  }

  /**
   * Set move suppression weight for an MV.
   *
   * @param mvName the MV name
   * @param weight the suppression weight
   */
  public void setMoveSuppressionWeight(String mvName, double weight) {
    for (int i = 0; i < manipulatedVariables.size(); i++) {
      ManipulatedVariable mv = manipulatedVariables.get(i);
      if (mv.getName().equals(mvName) || mv.getName().endsWith("." + mvName)) {
        mv.setMoveWeight(weight);
        return;
      }
    }
    throw new IllegalArgumentException("MV not found: " + mvName);
  }

  /**
   * Set error weight for a CV.
   *
   * @param cvName the CV name
   * @param weight the error weight
   */
  public void setErrorWeight(String cvName, double weight) {
    for (ControlledVariable cv : controlledVariables) {
      if (cv.getName().equals(cvName) || cv.getName().endsWith("." + cvName)) {
        cv.setWeight(weight);
        return;
      }
    }
    throw new IllegalArgumentException("CV not found: " + cvName);
  }

  /**
   * Enable nonlinear prediction using full NeqSim simulation.
   *
   * @param enable true to enable
   */
  public void setUseNonlinearPrediction(boolean enable) {
    this.useNonlinearPrediction = enable;
  }

  /**
   * Set the model update interval.
   *
   * @param steps number of control steps between model updates (0 = no updates)
   */
  public void setModelUpdateInterval(int steps) {
    this.modelUpdateInterval = steps;
  }

  /**
   * Identify the process model using linearization.
   *
   * @param sampleTimeSeconds the sample time in seconds
   */
  public void identifyModel(double sampleTimeSeconds) {
    this.sampleTime = sampleTimeSeconds;

    // Create linearizer
    linearizer = new ProcessLinearizer(processSystem);
    for (ManipulatedVariable mv : manipulatedVariables) {
      linearizer.addMV(mv);
    }
    for (ControlledVariable cv : controlledVariables) {
      linearizer.addCV(cv);
    }
    for (DisturbanceVariable dv : disturbanceVariables) {
      linearizer.addDV(dv);
    }

    // Perform linearization
    linearizationResult = linearizer.linearize();

    // Initialize weights
    moveSuppressionWeights = new double[manipulatedVariables.size()];
    Arrays.fill(moveSuppressionWeights, 0.1);

    errorWeights = new double[controlledVariables.size()];
    Arrays.fill(errorWeights, 1.0);

    // Initialize nonlinear predictor if needed
    if (useNonlinearPrediction) {
      nonlinearPredictor = new NonlinearPredictor(processSystem);
      nonlinearPredictor.setPredictionHorizon(predictionHorizon);
      nonlinearPredictor.setSampleTime(sampleTimeSeconds);
      for (ManipulatedVariable mv : manipulatedVariables) {
        nonlinearPredictor.addMV(mv);
      }
      for (ControlledVariable cv : controlledVariables) {
        nonlinearPredictor.addCV(cv);
      }
    }

    modelIdentified = true;
    stepsSinceModelUpdate = 0;
  }

  /**
   * Check if the model has been identified.
   *
   * @return true if model is available
   */
  public boolean isModelIdentified() {
    return modelIdentified;
  }

  /**
   * Get the linearization result.
   *
   * @return the linearization result, or null if not identified
   */
  public LinearizationResult getLinearizationResult() {
    return linearizationResult;
  }

  /**
   * Update CV measurements from the process.
   */
  public void updateMeasurements() {
    for (ControlledVariable cv : controlledVariables) {
      cv.readValue();
    }
  }

  /**
   * Update DV measurements from the process.
   */
  public void updateDisturbances() {
    for (DisturbanceVariable dv : disturbanceVariables) {
      dv.readValue();
    }
  }

  /**
   * Calculate the next MV moves.
   *
   * @return array of MV move values
   */
  public double[] calculate() {
    if (!modelIdentified) {
      throw new IllegalStateException("Model not identified. Call identifyModel() first.");
    }

    // Update measurements
    updateMeasurements();
    updateDisturbances();

    // Check if model update is needed
    if (modelUpdateInterval > 0) {
      stepsSinceModelUpdate++;
      if (stepsSinceModelUpdate >= modelUpdateInterval) {
        updateModel();
      }
    }

    // Calculate control moves using linear model
    lastMoves = calculateLinear();

    return lastMoves.clone();
  }

  private double[] calculateLinear() {
    double[][] gains = linearizationResult.getGainMatrix();
    double[] moves = new double[manipulatedVariables.size()];

    // Simple proportional control with gain inversion
    // For each CV, calculate required MV change based on error
    for (int i = 0; i < manipulatedVariables.size(); i++) {
      ManipulatedVariable mv = manipulatedVariables.get(i);
      double mvValue = mv.readValue();
      moves[i] = mvValue;

      // Calculate weighted contribution from all CVs
      double totalError = 0;
      double totalGain = 0;

      for (int j = 0; j < controlledVariables.size(); j++) {
        ControlledVariable cv = controlledVariables.get(j);
        double error = cv.getSetpoint() - cv.readValue();
        double gain = gains[j][i];
        double weight = cv.getWeight();

        if (Math.abs(gain) > 1e-10) {
          totalError += weight * error / gain;
          totalGain += weight * Math.abs(1.0 / gain);
        }
      }

      if (totalGain > 0) {
        double delta = totalError / totalGain * 0.3; // Conservative gain factor

        // Apply move suppression
        double moveWeight = mv.getMoveWeight();
        if (moveWeight > 0) {
          delta = delta / (1.0 + moveWeight);
        }

        // Apply rate limits
        double maxRate = mv.getMaxRateOfChange();
        if (Double.isFinite(maxRate)) {
          delta = Math.max(-maxRate, Math.min(maxRate, delta));
        }

        moves[i] = mvValue + delta;

        // Apply bounds
        moves[i] = Math.max(mv.getMinValue(), Math.min(mv.getMaxValue(), moves[i]));
      }
    }

    return moves;
  }

  /**
   * Apply the calculated MV moves to the process.
   */
  public void applyMoves() {
    if (lastMoves == null) {
      throw new IllegalStateException("No moves calculated. Call calculate() first.");
    }

    for (int i = 0; i < Math.min(lastMoves.length, manipulatedVariables.size()); i++) {
      ManipulatedVariable mv = manipulatedVariables.get(i);
      mv.writeValue(lastMoves[i]);
    }
  }

  /**
   * Run the process simulation.
   */
  public void runProcess() {
    processSystem.run(executionId);
  }

  /**
   * Execute one complete control step: calculate, apply, and run.
   *
   * @return the applied MV values
   */
  public double[] step() {
    double[] moves = calculate();
    applyMoves();
    runProcess();
    return moves;
  }

  /**
   * Update the process model at current operating point.
   */
  public void updateModel() {
    if (linearizer != null) {
      linearizationResult = linearizer.linearize();
      stepsSinceModelUpdate = 0;
    }
  }

  /**
   * Export the model to a state-space representation.
   *
   * @return a StateSpaceExporter for export operations
   */
  public StateSpaceExporter exportModel() {
    if (linearizationResult == null) {
      throw new IllegalStateException("Model not identified. Call identifyModel() first.");
    }
    return new StateSpaceExporter(linearizationResult);
  }

  /**
   * Get the last calculated moves.
   *
   * @return copy of last moves, or null if none calculated
   */
  public double[] getLastMoves() {
    return lastMoves != null ? lastMoves.clone() : null;
  }

  /**
   * Set a CV setpoint.
   *
   * @param cvName the CV name
   * @param setpoint the new setpoint
   */
  public void setSetpoint(String cvName, double setpoint) {
    for (ControlledVariable cv : controlledVariables) {
      if (cv.getName().equals(cvName) || cv.getName().endsWith("." + cvName)) {
        cv.setSetpoint(setpoint);
        return;
      }
    }
    throw new IllegalArgumentException("CV not found: " + cvName);
  }

  /**
   * Get current CV values.
   *
   * @return array of current CV values
   */
  public double[] getCurrentCVs() {
    double[] values = new double[controlledVariables.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = controlledVariables.get(i).readValue();
    }
    return values;
  }

  /**
   * Get current MV values.
   *
   * @return array of current MV values
   */
  public double[] getCurrentMVs() {
    double[] values = new double[manipulatedVariables.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = manipulatedVariables.get(i).readValue();
    }
    return values;
  }

  /**
   * Get the controller name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Get a summary of the controller configuration.
   *
   * @return configuration summary string
   */
  public String getConfigurationSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ProcessLinkedMPC: ").append(name).append("\n");
    sb.append("  Sample Time: ").append(sampleTime).append(" s\n");
    sb.append("  Prediction Horizon: ").append(predictionHorizon).append("\n");
    sb.append("  Control Horizon: ").append(controlHorizon).append("\n");
    sb.append("  Model Identified: ").append(modelIdentified).append("\n");
    sb.append("  Nonlinear Prediction: ").append(useNonlinearPrediction).append("\n");
    sb.append("\n  MVs (").append(manipulatedVariables.size()).append("):\n");
    for (ManipulatedVariable mv : manipulatedVariables) {
      sb.append("    - ").append(mv.getName()).append(" [").append(mv.getMinValue()).append(", ")
          .append(mv.getMaxValue()).append("]\n");
    }
    sb.append("\n  CVs (").append(controlledVariables.size()).append("):\n");
    for (ControlledVariable cv : controlledVariables) {
      sb.append("    - ").append(cv.getName()).append(" SP=").append(cv.getSetpoint()).append("\n");
    }
    if (!disturbanceVariables.isEmpty()) {
      sb.append("\n  DVs (").append(disturbanceVariables.size()).append("):\n");
      for (DisturbanceVariable dv : disturbanceVariables) {
        sb.append("    - ").append(dv.getName()).append("\n");
      }
    }
    return sb.toString();
  }

  /**
   * Create an industrial MPC exporter for this controller.
   *
   * <p>
   * The exporter can generate model files in formats compatible with industrial MPC platforms,
   * including step response models, gain matrices, and variable configurations.
   * </p>
   *
   * @return a new IndustrialMPCExporter instance
   */
  public IndustrialMPCExporter createIndustrialExporter() {
    return new IndustrialMPCExporter(this);
  }

  /**
   * Create a data exchange interface for real-time integration.
   *
   * <p>
   * The data exchange interface provides standardized methods for bidirectional communication with
   * external control systems, including timestamped data vectors, quality flags, and execution
   * status.
   * </p>
   *
   * @return a new ControllerDataExchange instance
   */
  public ControllerDataExchange createDataExchange() {
    return new ControllerDataExchange(this);
  }

  /**
   * Create a SubrModl exporter for nonlinear MPC integration.
   *
   * <p>
   * The SubrModl exporter generates configuration files compatible with industrial nonlinear MPC
   * systems that use programmed model objects. This includes SubrXvr definitions with DtaIx
   * mappings, model parameters, and state variables.
   * </p>
   *
   * @return a new SubrModlExporter instance populated from this controller
   */
  public SubrModlExporter createSubrModlExporter() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setModelName(name);
    exporter.setSampleTime(sampleTime);
    exporter.populateFromMPC(this);

    // Add state variables
    for (StateVariable svr : stateVariables) {
      exporter.addStateVariable(svr.getName(), svr.getDtaIx(), svr.getDescription(),
          svr.getModelValue());
    }

    return exporter;
  }

  @Override
  public String toString() {
    return "ProcessLinkedMPC[" + name + ", MVs=" + manipulatedVariables.size() + ", CVs="
        + controlledVariables.size() + ", SVRs=" + stateVariables.size() + ", identified="
        + modelIdentified + "]";
  }
}
