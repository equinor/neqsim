package neqsim.process.mpc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Performs multi-step ahead prediction using full NeqSim nonlinear simulation.
 *
 * <p>
 * The NonlinearPredictor uses the actual NeqSim ProcessSystem to predict future CV trajectories
 * given a sequence of MV moves. Unlike linear prediction which uses gain matrices, this approach
 * captures all nonlinear process behavior.
 * </p>
 *
 * <p>
 * Use cases:
 * </p>
 * <ul>
 * <li>Validate linear MPC predictions</li>
 * <li>Nonlinear MPC with full simulation in the loop</li>
 * <li>What-if scenario analysis</li>
 * <li>Operator training simulators</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * NonlinearPredictor predictor = new NonlinearPredictor(processSystem);
 *
 * // Configure prediction
 * predictor.addMV(new ManipulatedVariable("Valve", valve, "opening"));
 * predictor.addCV(new ControlledVariable("Pressure", separator, "pressure", "bara"));
 * predictor.setPredictionHorizon(20);
 * predictor.setSampleTime(60.0); // 60 seconds
 *
 * // Define MV trajectory
 * MVTrajectory trajectory = new MVTrajectory();
 * trajectory.addMove("Valve", 0.52);
 * trajectory.addMove("Valve", 0.54);
 * trajectory.addMove("Valve", 0.55);
 *
 * // Run prediction
 * PredictionResult result = predictor.predict(trajectory);
 *
 * // Access results
 * double[] futurePressure = result.getTrajectory("Pressure");
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class NonlinearPredictor implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system to use for prediction. */
  private final transient ProcessSystem processSystem;

  /** List of manipulated variables. */
  private final List<ManipulatedVariable> manipulatedVariables = new ArrayList<>();

  /** List of controlled variables. */
  private final List<ControlledVariable> controlledVariables = new ArrayList<>();

  /** Prediction horizon (number of steps). */
  private int predictionHorizon = 20;

  /** Sample time in seconds. */
  private double sampleTimeSeconds = 60.0;

  /** Whether to clone the process for each prediction (safer but slower). */
  private boolean cloneProcess = false;

  /**
   * Construct a nonlinear predictor for a ProcessSystem.
   *
   * @param processSystem the NeqSim process to use for prediction
   */
  public NonlinearPredictor(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("ProcessSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Add a manipulated variable.
   *
   * @param mv the manipulated variable
   * @return this predictor for method chaining
   */
  public NonlinearPredictor addMV(ManipulatedVariable mv) {
    if (mv == null) {
      throw new IllegalArgumentException("ManipulatedVariable must not be null");
    }
    manipulatedVariables.add(mv);
    return this;
  }

  /**
   * Add a controlled variable.
   *
   * @param cv the controlled variable
   * @return this predictor for method chaining
   */
  public NonlinearPredictor addCV(ControlledVariable cv) {
    if (cv == null) {
      throw new IllegalArgumentException("ControlledVariable must not be null");
    }
    controlledVariables.add(cv);
    return this;
  }

  /**
   * Set the prediction horizon.
   *
   * @param horizon number of prediction steps
   * @return this predictor for method chaining
   */
  public NonlinearPredictor setPredictionHorizon(int horizon) {
    if (horizon <= 0) {
      throw new IllegalArgumentException("Prediction horizon must be positive");
    }
    this.predictionHorizon = horizon;
    return this;
  }

  /**
   * Set the sample time.
   *
   * @param seconds sample time in seconds
   * @return this predictor for method chaining
   */
  public NonlinearPredictor setSampleTime(double seconds) {
    if (seconds <= 0) {
      throw new IllegalArgumentException("Sample time must be positive");
    }
    this.sampleTimeSeconds = seconds;
    return this;
  }

  /**
   * Set whether to clone the process for each prediction.
   *
   * @param clone true to clone (safer), false to modify in-place (faster)
   * @return this predictor for method chaining
   */
  public NonlinearPredictor setCloneProcess(boolean clone) {
    this.cloneProcess = clone;
    return this;
  }

  /**
   * Get the prediction horizon.
   *
   * @return number of prediction steps
   */
  public int getPredictionHorizon() {
    return predictionHorizon;
  }

  /**
   * Get the sample time.
   *
   * @return sample time in seconds
   */
  public double getSampleTimeSeconds() {
    return sampleTimeSeconds;
  }

  /**
   * Predict CV trajectories given an MV trajectory.
   *
   * @param trajectory the MV trajectory to apply
   * @return prediction result with CV trajectories
   */
  public PredictionResult predict(MVTrajectory trajectory) {
    if (trajectory == null) {
      throw new IllegalArgumentException("Trajectory must not be null");
    }

    int numCV = controlledVariables.size();
    int numMV = manipulatedVariables.size();

    // Store original MV values for restoration
    double[] originalMV = new double[numMV];
    for (int j = 0; j < numMV; j++) {
      originalMV[j] = manipulatedVariables.get(j).readValue();
    }

    // Initialize result arrays
    double[][] cvTrajectories = new double[numCV][predictionHorizon];
    double[][] mvTrajectories = new double[numMV][predictionHorizon];
    double[] time = new double[predictionHorizon];

    try {
      // Run initial simulation to establish baseline
      processSystem.run();

      // Simulate each step
      for (int k = 0; k < predictionHorizon; k++) {
        time[k] = k * sampleTimeSeconds;

        // Apply MV values for this step
        for (int j = 0; j < numMV; j++) {
          ManipulatedVariable mv = manipulatedVariables.get(j);
          double value = trajectory.getValue(mv.getName(), k);
          if (!Double.isNaN(value)) {
            mv.writeValue(value);
          }
          mvTrajectories[j][k] = mv.getCurrentValue();
        }

        // Run simulation
        processSystem.run();

        // Record CV values
        for (int i = 0; i < numCV; i++) {
          cvTrajectories[i][k] = controlledVariables.get(i).readValue();
        }
      }
    } finally {
      // Restore original MV values
      for (int j = 0; j < numMV; j++) {
        manipulatedVariables.get(j).writeValue(originalMV[j]);
      }
      try {
        processSystem.run();
      } catch (Exception e) {
        // Ignore restore errors
      }
    }

    // Build result
    String[] cvNames = new String[numCV];
    String[] mvNames = new String[numMV];
    for (int i = 0; i < numCV; i++) {
      cvNames[i] = controlledVariables.get(i).getName();
    }
    for (int j = 0; j < numMV; j++) {
      mvNames[j] = manipulatedVariables.get(j).getName();
    }

    return new PredictionResult(cvTrajectories, mvTrajectories, time, cvNames, mvNames,
        sampleTimeSeconds);
  }

  /**
   * Predict CV trajectories given constant MV values.
   *
   * @param mvValues constant MV values (one per MV)
   * @return prediction result
   */
  public PredictionResult predictConstant(double... mvValues) {
    MVTrajectory trajectory = new MVTrajectory();
    for (int j = 0; j < manipulatedVariables.size() && j < mvValues.length; j++) {
      String name = manipulatedVariables.get(j).getName();
      for (int k = 0; k < predictionHorizon; k++) {
        trajectory.addMove(name, mvValues[j]);
      }
    }
    return predict(trajectory);
  }

  /**
   * Clear all variable definitions.
   *
   * @return this predictor for method chaining
   */
  public NonlinearPredictor clear() {
    manipulatedVariables.clear();
    controlledVariables.clear();
    return this;
  }

  /**
   * Represents a trajectory of MV values over time.
   */
  public static class MVTrajectory implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<String> mvNames = new ArrayList<>();
    private final List<List<Double>> mvMoves = new ArrayList<>();

    /**
     * Add a move (value) for an MV.
     *
     * @param mvName the MV name
     * @param value the value at the next time step
     * @return this trajectory for method chaining
     */
    public MVTrajectory addMove(String mvName, double value) {
      int index = mvNames.indexOf(mvName);
      if (index < 0) {
        mvNames.add(mvName);
        mvMoves.add(new ArrayList<>());
        index = mvNames.size() - 1;
      }
      mvMoves.get(index).add(value);
      return this;
    }

    /**
     * Set a complete trajectory for an MV.
     *
     * @param mvName the MV name
     * @param values array of values over time
     * @return this trajectory for method chaining
     */
    public MVTrajectory setMoves(String mvName, double[] values) {
      int index = mvNames.indexOf(mvName);
      if (index < 0) {
        mvNames.add(mvName);
        mvMoves.add(new ArrayList<>());
        index = mvNames.size() - 1;
      } else {
        mvMoves.get(index).clear();
      }
      for (double v : values) {
        mvMoves.get(index).add(v);
      }
      return this;
    }

    /**
     * Get the MV value at a specific time step.
     *
     * @param mvName the MV name
     * @param step the time step index
     * @return the value, or NaN if not defined
     */
    public double getValue(String mvName, int step) {
      int index = mvNames.indexOf(mvName);
      if (index < 0) {
        return Double.NaN;
      }
      List<Double> moves = mvMoves.get(index);
      if (step < 0) {
        return Double.NaN;
      }
      if (step < moves.size()) {
        return moves.get(step);
      }
      // Hold last value
      if (!moves.isEmpty()) {
        return moves.get(moves.size() - 1);
      }
      return Double.NaN;
    }

    /**
     * Get the trajectory length.
     *
     * @param mvName the MV name
     * @return number of defined steps
     */
    public int getLength(String mvName) {
      int index = mvNames.indexOf(mvName);
      if (index < 0) {
        return 0;
      }
      return mvMoves.get(index).size();
    }

    /**
     * Clear the trajectory.
     *
     * @return this trajectory for method chaining
     */
    public MVTrajectory clear() {
      mvNames.clear();
      mvMoves.clear();
      return this;
    }
  }

  /**
   * Result of a prediction containing CV and MV trajectories.
   */
  public static class PredictionResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double[][] cvTrajectories;
    private final double[][] mvTrajectories;
    private final double[] time;
    private final String[] cvNames;
    private final String[] mvNames;
    private final double sampleTime;

    /**
     * Construct a prediction result.
     *
     * @param cvTrajectories CV values [numCV][numSteps]
     * @param mvTrajectories MV values [numMV][numSteps]
     * @param time time points
     * @param cvNames CV names
     * @param mvNames MV names
     * @param sampleTime sample interval
     */
    public PredictionResult(double[][] cvTrajectories, double[][] mvTrajectories, double[] time,
        String[] cvNames, String[] mvNames, double sampleTime) {
      this.cvTrajectories = cvTrajectories;
      this.mvTrajectories = mvTrajectories;
      this.time = time != null ? time.clone() : new double[0];
      this.cvNames = cvNames != null ? cvNames.clone() : new String[0];
      this.mvNames = mvNames != null ? mvNames.clone() : new String[0];
      this.sampleTime = sampleTime;
    }

    /**
     * Get a CV trajectory by name.
     *
     * @param cvName the CV name
     * @return the trajectory array
     */
    public double[] getTrajectory(String cvName) {
      for (int i = 0; i < cvNames.length; i++) {
        if (cvName.equals(cvNames[i])) {
          return cvTrajectories[i].clone();
        }
      }
      return new double[0];
    }

    /**
     * Get a CV trajectory by index.
     *
     * @param index the CV index
     * @return the trajectory array
     */
    public double[] getTrajectory(int index) {
      if (index >= 0 && index < cvTrajectories.length) {
        return cvTrajectories[index].clone();
      }
      return new double[0];
    }

    /**
     * Get an MV trajectory by name.
     *
     * @param mvName the MV name
     * @return the trajectory array
     */
    public double[] getMVTrajectory(String mvName) {
      for (int j = 0; j < mvNames.length; j++) {
        if (mvName.equals(mvNames[j])) {
          return mvTrajectories[j].clone();
        }
      }
      return new double[0];
    }

    /**
     * Get the time array.
     *
     * @return time points
     */
    public double[] getTime() {
      return time.clone();
    }

    /**
     * Get the sample time.
     *
     * @return sample interval in seconds
     */
    public double getSampleTime() {
      return sampleTime;
    }

    /**
     * Get the prediction horizon.
     *
     * @return number of steps
     */
    public int getHorizon() {
      return time.length;
    }

    /**
     * Get the CV names.
     *
     * @return array of CV names
     */
    public String[] getCvNames() {
      return cvNames.clone();
    }

    /**
     * Get the MV names.
     *
     * @return array of MV names
     */
    public String[] getMvNames() {
      return mvNames.clone();
    }

    /**
     * Get the final predicted value for a CV.
     *
     * @param cvName the CV name
     * @return the final predicted value
     */
    public double getFinalValue(String cvName) {
      double[] traj = getTrajectory(cvName);
      return traj.length > 0 ? traj[traj.length - 1] : Double.NaN;
    }

    /**
     * Calculate the integrated squared error from a setpoint.
     *
     * @param cvName the CV name
     * @param setpoint the target setpoint
     * @return the ISE
     */
    public double getISE(String cvName, double setpoint) {
      double[] traj = getTrajectory(cvName);
      double ise = 0.0;
      for (double value : traj) {
        double error = value - setpoint;
        ise += error * error * sampleTime;
      }
      return ise;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("PredictionResult {\n");
      sb.append("  horizon: ").append(getHorizon()).append(" steps\n");
      sb.append("  sampleTime: ").append(sampleTime).append(" s\n");
      sb.append("  CVs:\n");
      for (int i = 0; i < cvNames.length; i++) {
        double[] traj = cvTrajectories[i];
        sb.append("    ").append(cvNames[i]).append(": ");
        sb.append(String.format("%.4f", traj[0])).append(" -> ");
        sb.append(String.format("%.4f", traj[traj.length - 1])).append("\n");
      }
      sb.append("}");
      return sb.toString();
    }
  }
}
