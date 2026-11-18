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
 * ProcessLinkedMPC provides automatic integration between NeqSim's ProcessSystem simulation and Model Predictive
 * Control. It handles:
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
 * // Tune the local gain calculation
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
  private transient ProcessLinearizer linearizer;

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

  /** Optional measured step-response model for constrained horizon optimization. */
  private HorizonResponseModel horizonModel;

  /** Result of the latest horizon solve, including unsuccessful solver status. */
  private HorizonPlan lastHorizonPlan;

  /** Measurements for the plan awaiting application. */
  private double[] pendingInputs;

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
  public ManipulatedVariable addMV(String equipmentName, String propertyName, double minValue, double maxValue) {
    ManipulatedVariable mv = new ManipulatedVariable(equipmentName + "." + propertyName,
        processSystem.getUnit(equipmentName), propertyName);
    mv.setBounds(minValue, maxValue);
    discardPendingHorizonMove();
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
  public ManipulatedVariable addMV(String equipmentName, String propertyName, double minValue, double maxValue,
      double maxRateOfChange) {
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
    discardPendingHorizonMove();
    controlledVariables.add(cv);
    return cv;
  }

  /**
   * Add a controlled variable with zone control. The local move calculation uses zero tracking error inside the zone
   * and targets the nearest boundary outside it.
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
  public void setConstraint(String equipmentName, String propertyName, double minValue, double maxValue) {
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
    discardPendingHorizonMove();
    disturbanceVariables.add(dv);
    return dv;
  }

  /**
   * Add a state variable (SVR) for nonlinear MPC.
   *
   * <p>
   * State variables are internal model states that evolve according to dynamic equations. They are tracked for model
   * accuracy but not directly controlled. Examples include flow rates, internal pressures, and calculated gains.
   * </p>
   *
   * @param equipmentName the equipment name
   * @param propertyName the property name
   * @return the created StateVariable
   */
  public StateVariable addSVR(String equipmentName, String propertyName) {
    StateVariable svr = new StateVariable(equipmentName + "." + propertyName, processSystem.getUnit(equipmentName),
        propertyName);
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
    discardPendingHorizonMove();
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
    discardPendingHorizonMove();
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
    discardPendingHorizonMove();
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
    discardPendingHorizonMove();
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
   * Check whether the local steady-gain model has been identified.
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
   * Select constrained receding-horizon control using measured or supplied step responses. Supply every MV-to-CV and
   * DV-to-CV path by its registered name, including explicit zero responses. Samples must lie on the controller time
   * grid, starting at zero or one sample interval. The first prediction uses the response at one interval. Final
   * coefficients are held. Configuration starts with steady input history.
   *
   * @param responses absolute output traces in configured engineering units
   */
  public void setHorizonResponses(StepResponse... responses) {
    horizonModel = new HorizonResponseModel(manipulatedVariables, disturbanceVariables, controlledVariables, sampleTime,
        responses);
    lastHorizonPlan = null;
    pendingInputs = null;
    lastMoves = null;
  }

  /** Expire any unapplied horizon command after a change in the model configuration. */
  private void discardPendingHorizonMove() {
    if (horizonModel != null) {
      pendingInputs = null;
      lastMoves = null;
    }
  }

  /**
   * Return to the steady-gain control method. A steady model must have been identified before calculating.
   */
  public void clearHorizonResponses() {
    horizonModel = null;
    lastHorizonPlan = null;
    pendingInputs = null;
    lastMoves = null;
  }

  /**
   * Get the immutable result of the latest horizon solve.
   *
   * @return scan result or null before solving
   */
  public HorizonPlan getLastHorizonPlan() {
    return lastHorizonPlan;
  }

  /**
   * Calculate the first absolute MV settings with a known disturbance trajectory. Row zero is the absolute input on the
   * interval about to start. The last supplied row is held. An empty or null forecast holds the measured disturbances.
   * A failed solve invalidates any preceding moves.
   *
   * @param futureAbsoluteDvs forecast by sample and disturbance, in registration order
   * @return absolute MV settings for the first interval
   * @throws IllegalStateException if no response model is configured or the solver cannot return a plan
   */
  public double[] calculate(double[][] futureAbsoluteDvs) {
    if (horizonModel == null) {
      throw new IllegalStateException("Configure step responses before supplying a disturbance preview");
    }
    lastMoves = null;
    pendingInputs = null;
    lastHorizonPlan = null;
    updateMeasurements();
    double[] inputs = new double[manipulatedVariables.size() + disturbanceVariables.size()];
    for (int i = 0; i < manipulatedVariables.size(); i++) {
      inputs[i] = manipulatedVariables.get(i).readValue();
    }
    for (int i = 0; i < disturbanceVariables.size(); i++) {
      inputs[manipulatedVariables.size() + i] = disturbanceVariables.get(i).readValue();
    }
    lastHorizonPlan = horizonModel.plan(manipulatedVariables, controlledVariables, inputs, futureAbsoluteDvs,
        predictionHorizon, controlHorizon, sampleTime);
    if (!lastHorizonPlan.isSuccessful()) {
      throw new IllegalStateException("Horizon solve failed: " + lastHorizonPlan.getStatus());
    }
    lastMoves = lastHorizonPlan.getFirstMove();
    pendingInputs = inputs;
    return lastMoves.clone();
  }

  /**
   * Calculate the next MV moves.
   *
   * @return array of MV move values
   */
  public double[] calculate() {
    if (horizonModel != null) {
      return calculate(null);
    }
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
        double measurement = cv.readValue();
        double error = cv.getEffectiveSetpoint() - measurement;
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

    double[] scanInputs = pendingInputs;
    if (horizonModel != null) {
      if (scanInputs == null) {
        throw new IllegalStateException("Calculate a fresh horizon move before applying");
      }
      // Consume the request before actuation so a failed write cannot replay stale commands.
      pendingInputs = null;
      for (int i = 0; i < manipulatedVariables.size(); i++) {
        ManipulatedVariable mv = manipulatedVariables.get(i);
        double measured = mv.readValue();
        double low = Math.max(mv.getMinValue(), measured + mv.getMinRateOfChange());
        double high = Math.min(mv.getMaxValue(), measured + mv.getMaxRateOfChange());
        if (measured != scanInputs[i] || !(lastMoves[i] >= low && lastMoves[i] <= high)) {
          lastMoves = null;
          throw new IllegalStateException("MV changed since the scan; recalculate before applying " + mv.getName());
        }
      }
    }
    for (int i = 0; i < Math.min(lastMoves.length, manipulatedVariables.size()); i++) {
      ManipulatedVariable mv = manipulatedVariables.get(i);
      mv.writeValue(lastMoves[i]);
    }
    if (horizonModel != null) {
      horizonModel.advance(scanInputs);
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
   * The exporter can generate model files in formats compatible with industrial MPC platforms, including step response
   * models, gain matrices, and variable configurations.
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
   * The data exchange interface provides standardized methods for bidirectional communication with external control
   * systems, including timestamped data vectors, quality flags, and execution status.
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
   * The SubrModl exporter generates configuration files compatible with industrial nonlinear MPC systems that use
   * programmed model objects. This includes SubrXvr definitions with DtaIx mappings, model parameters, and state
   * variables.
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
      exporter.addStateVariable(svr.getName(), svr.getDtaIx(), svr.getDescription(), svr.getModelValue());
    }

    return exporter;
  }

  @Override
  public String toString() {
    return "ProcessLinkedMPC[" + name + ", MVs=" + manipulatedVariables.size() + ", CVs=" + controlledVariables.size()
        + ", SVRs=" + stateVariables.size() + ", identified=" + modelIdentified + "]";
  }
}
