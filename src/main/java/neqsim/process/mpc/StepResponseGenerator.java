package neqsim.process.mpc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates step response models by running NeqSim simulations.
 *
 * <p>
 * The StepResponseGenerator automates the process of system identification by performing step tests
 * on a ProcessSystem. For each MV-CV pair, it:
 * </p>
 * <ol>
 * <li>Records the baseline CV value</li>
 * <li>Applies a step change to the MV</li>
 * <li>Runs simulations and records CV response over time</li>
 * <li>Fits transfer function models (FOPDT)</li>
 * </ol>
 *
 * <p>
 * The resulting step response data can be used to:
 * </p>
 * <ul>
 * <li>Configure the existing ModelPredictiveController</li>
 * <li>Export to external MPC packages</li>
 * <li>Validate process linearity</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * StepResponseGenerator generator = new StepResponseGenerator(processSystem);
 *
 * // Add MVs and CVs
 * generator.addMV(
 *     new ManipulatedVariable("Valve", valve, "opening").setBounds(0.0, 1.0).setInitialValue(0.5));
 * generator.addCV(new ControlledVariable("Pressure", separator, "pressure", "bara"));
 *
 * // Configure step test
 * generator.setStepSize(0.10); // 10% step
 * generator.setSettlingTime(60, "min"); // Wait 60 min for steady state
 * generator.setSampleInterval(1, "min"); // Sample every minute
 *
 * // Run step tests
 * StepResponseMatrix responses = generator.generateAllResponses();
 *
 * // Access individual response
 * StepResponse pressureToValve = responses.get("Pressure", "Valve");
 * double gain = pressureToValve.getGain();
 * double tau = pressureToValve.getTimeConstant();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class StepResponseGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system to test. */
  private final transient ProcessSystem processSystem;

  /** List of manipulated variables. */
  private final List<ManipulatedVariable> manipulatedVariables = new ArrayList<>();

  /** List of controlled variables. */
  private final List<ControlledVariable> controlledVariables = new ArrayList<>();

  /** Step size as fraction of MV range. */
  private double stepSizeFraction = 0.10;

  /** Settling time in seconds. */
  private double settlingTimeSeconds = 3600.0;

  /** Sample interval in seconds. */
  private double sampleIntervalSeconds = 60.0;

  /** Whether to use positive step (true) or negative (false). */
  private boolean positiveStep = true;

  /** Whether to run both positive and negative steps for averaging. */
  private boolean bidirectionalTest = false;

  /**
   * Construct a step response generator for a ProcessSystem.
   *
   * @param processSystem the NeqSim process to test
   */
  public StepResponseGenerator(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("ProcessSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Add a manipulated variable for step testing.
   *
   * @param mv the manipulated variable
   * @return this generator for method chaining
   */
  public StepResponseGenerator addMV(ManipulatedVariable mv) {
    if (mv == null) {
      throw new IllegalArgumentException("ManipulatedVariable must not be null");
    }
    manipulatedVariables.add(mv);
    return this;
  }

  /**
   * Add a controlled variable to monitor.
   *
   * @param cv the controlled variable
   * @return this generator for method chaining
   */
  public StepResponseGenerator addCV(ControlledVariable cv) {
    if (cv == null) {
      throw new IllegalArgumentException("ControlledVariable must not be null");
    }
    controlledVariables.add(cv);
    return this;
  }

  /**
   * Set the step size as a fraction of MV range.
   *
   * @param fraction step size (0.10 = 10%)
   * @return this generator for method chaining
   */
  public StepResponseGenerator setStepSize(double fraction) {
    if (fraction <= 0 || fraction > 1) {
      throw new IllegalArgumentException("Step size must be between 0 and 1");
    }
    this.stepSizeFraction = fraction;
    return this;
  }

  /**
   * Set the settling time for step tests.
   *
   * @param value the settling time value
   * @param unit the time unit ("s", "min", "hr")
   * @return this generator for method chaining
   */
  public StepResponseGenerator setSettlingTime(double value, String unit) {
    this.settlingTimeSeconds = convertToSeconds(value, unit);
    return this;
  }

  /**
   * Set the sample interval.
   *
   * @param value the sample interval value
   * @param unit the time unit ("s", "min", "hr")
   * @return this generator for method chaining
   */
  public StepResponseGenerator setSampleInterval(double value, String unit) {
    this.sampleIntervalSeconds = convertToSeconds(value, unit);
    return this;
  }

  /**
   * Set whether to use positive step direction.
   *
   * @param positive true for positive step, false for negative
   * @return this generator for method chaining
   */
  public StepResponseGenerator setPositiveStep(boolean positive) {
    this.positiveStep = positive;
    return this;
  }

  /**
   * Set whether to run bidirectional tests.
   *
   * @param bidirectional true to run both positive and negative steps
   * @return this generator for method chaining
   */
  public StepResponseGenerator setBidirectionalTest(boolean bidirectional) {
    this.bidirectionalTest = bidirectional;
    return this;
  }

  private double convertToSeconds(double value, String unit) {
    if (unit == null) {
      return value;
    }
    switch (unit.toLowerCase()) {
      case "s":
      case "sec":
      case "seconds":
        return value;
      case "min":
      case "minutes":
        return value * 60.0;
      case "hr":
      case "hour":
      case "hours":
        return value * 3600.0;
      default:
        return value;
    }
  }

  /**
   * Generate step responses for all MV-CV pairs.
   *
   * @return matrix of step responses
   */
  public StepResponseMatrix generateAllResponses() {
    int numMV = manipulatedVariables.size();
    int numCV = controlledVariables.size();

    if (numMV == 0 || numCV == 0) {
      throw new IllegalStateException("At least one MV and one CV must be defined");
    }

    Map<String, Map<String, StepResponse>> responses = new HashMap<>();
    String[] mvNames = new String[numMV];
    String[] cvNames = new String[numCV];

    for (int j = 0; j < numMV; j++) {
      mvNames[j] = manipulatedVariables.get(j).getName();
    }
    for (int i = 0; i < numCV; i++) {
      cvNames[i] = controlledVariables.get(i).getName();
    }

    // Run step test for each MV
    for (int j = 0; j < numMV; j++) {
      ManipulatedVariable mv = manipulatedVariables.get(j);
      List<StepResponse> stepResponses = runStepTest(mv);

      for (StepResponse response : stepResponses) {
        String cvName = response.getCvName();
        if (!responses.containsKey(cvName)) {
          responses.put(cvName, new HashMap<>());
        }
        responses.get(cvName).put(mv.getName(), response);
      }
    }

    return new StepResponseMatrix(responses, mvNames, cvNames);
  }

  /**
   * Run a step test for a single MV.
   *
   * @param mv the manipulated variable to step
   * @return list of step responses for all CVs
   */
  public List<StepResponse> runStepTest(ManipulatedVariable mv) {
    List<StepResponse> responses = new ArrayList<>();

    // Calculate step size
    double currentValue = mv.readValue();
    double stepSize = calculateStepSize(mv, currentValue);

    if (bidirectionalTest) {
      // Run positive step
      List<StepResponse> positiveResponses = runSingleStep(mv, currentValue, stepSize);
      // Run negative step
      List<StepResponse> negativeResponses = runSingleStep(mv, currentValue, -stepSize);

      // Average the responses
      for (int i = 0; i < positiveResponses.size() && i < negativeResponses.size(); i++) {
        StepResponse pos = positiveResponses.get(i);
        StepResponse neg = negativeResponses.get(i);
        responses.add(averageResponses(pos, neg));
      }
    } else {
      double actualStep = positiveStep ? stepSize : -stepSize;
      responses = runSingleStep(mv, currentValue, actualStep);
    }

    return responses;
  }

  /**
   * Run a single step test.
   *
   * @param mv the MV to step
   * @param baseValue the baseline MV value
   * @param stepSize the step magnitude (can be negative)
   * @return list of step responses for all CVs
   */
  private List<StepResponse> runSingleStep(ManipulatedVariable mv, double baseValue,
      double stepSize) {
    List<StepResponse> responses = new ArrayList<>();

    int numSamples = (int) Math.ceil(settlingTimeSeconds / sampleIntervalSeconds) + 1;
    double[] time = new double[numSamples];
    double[][] cvData = new double[controlledVariables.size()][numSamples];

    // Run baseline simulation and record initial CV values
    try {
      processSystem.run();
    } catch (Exception e) {
      // Handle simulation error
    }

    double[] cvBaseline = new double[controlledVariables.size()];
    for (int i = 0; i < controlledVariables.size(); i++) {
      cvBaseline[i] = controlledVariables.get(i).readValue();
    }

    // Apply step
    double newValue = baseValue + stepSize;
    // Clamp to bounds
    if (!Double.isInfinite(mv.getMinValue())) {
      newValue = Math.max(mv.getMinValue(), newValue);
    }
    if (!Double.isInfinite(mv.getMaxValue())) {
      newValue = Math.min(mv.getMaxValue(), newValue);
    }
    double actualStep = newValue - baseValue;

    mv.writeValue(newValue);

    // Simulate and record response at each time step
    for (int t = 0; t < numSamples; t++) {
      time[t] = t * sampleIntervalSeconds;

      try {
        processSystem.run();
      } catch (Exception e) {
        // Use last valid values on error
      }

      for (int i = 0; i < controlledVariables.size(); i++) {
        cvData[i][t] = controlledVariables.get(i).readValue();
      }
    }

    // Restore original MV value
    mv.writeValue(baseValue);
    try {
      processSystem.run();
    } catch (Exception e) {
      // Ignore restore errors
    }

    // Create step response objects
    for (int i = 0; i < controlledVariables.size(); i++) {
      ControlledVariable cv = controlledVariables.get(i);
      StepResponse response = new StepResponse(mv.getName(), cv.getName(), time, cvData[i],
          actualStep, cvBaseline[i], sampleIntervalSeconds, mv.getUnit(), cv.getUnit());
      response.fitFOPDT();
      responses.add(response);
    }

    return responses;
  }

  /**
   * Calculate the step size for an MV.
   *
   * @param mv the manipulated variable
   * @param currentValue current MV value
   * @return the step magnitude
   */
  private double calculateStepSize(ManipulatedVariable mv, double currentValue) {
    double range = mv.getMaxValue() - mv.getMinValue();
    if (Double.isFinite(range) && range > 0) {
      return stepSizeFraction * range;
    }
    // Use fraction of current value
    if (Math.abs(currentValue) > 1e-6) {
      return stepSizeFraction * Math.abs(currentValue);
    }
    // Default small step
    return stepSizeFraction;
  }

  /**
   * Average two step responses (for bidirectional testing).
   *
   * @param pos positive step response
   * @param neg negative step response
   * @return averaged response
   */
  private StepResponse averageResponses(StepResponse pos, StepResponse neg) {
    double[] posNorm = pos.getNormalizedResponse();
    double[] negNorm = neg.getNormalizedResponse();

    int len = Math.min(posNorm.length, negNorm.length);
    double[] avgResponse = new double[len];
    double[] time = new double[len];

    double[] posTime = pos.getTime();
    double avgStepSize = (pos.getStepSize() - neg.getStepSize()) / 2.0;

    for (int i = 0; i < len; i++) {
      time[i] = posTime[i];
      // Average normalized responses, but negate the negative step response
      double avgNorm = (posNorm[i] - negNorm[i]) / 2.0;
      avgResponse[i] = pos.getBaselineValue() + avgNorm * avgStepSize;
    }

    StepResponse avg = new StepResponse(pos.getMvName(), pos.getCvName(), time, avgResponse,
        avgStepSize, pos.getBaselineValue(), pos.getSampleTime(), null, null);
    avg.fitFOPDT();
    return avg;
  }

  /**
   * Clear all variable definitions.
   *
   * @return this generator for method chaining
   */
  public StepResponseGenerator clear() {
    manipulatedVariables.clear();
    controlledVariables.clear();
    return this;
  }

  /**
   * Get the configured settling time.
   *
   * @return settling time in seconds
   */
  public double getSettlingTimeSeconds() {
    return settlingTimeSeconds;
  }

  /**
   * Get the configured sample interval.
   *
   * @return sample interval in seconds
   */
  public double getSampleIntervalSeconds() {
    return sampleIntervalSeconds;
  }

  /**
   * Get the configured step size fraction.
   *
   * @return step size as fraction
   */
  public double getStepSizeFraction() {
    return stepSizeFraction;
  }

  /**
   * Matrix container for step responses from all MV-CV pairs.
   */
  public static class StepResponseMatrix implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final Map<String, Map<String, StepResponse>> responses;
    private final String[] mvNames;
    private final String[] cvNames;

    /**
     * Construct a step response matrix.
     *
     * @param responses map of CV name to (MV name to StepResponse)
     * @param mvNames ordered MV names
     * @param cvNames ordered CV names
     */
    public StepResponseMatrix(Map<String, Map<String, StepResponse>> responses, String[] mvNames,
        String[] cvNames) {
      this.responses = responses;
      this.mvNames = mvNames != null ? mvNames.clone() : new String[0];
      this.cvNames = cvNames != null ? cvNames.clone() : new String[0];
    }

    /**
     * Get a specific step response.
     *
     * @param cvName controlled variable name
     * @param mvName manipulated variable name
     * @return the step response, or null if not found
     */
    public StepResponse get(String cvName, String mvName) {
      Map<String, StepResponse> cvResponses = responses.get(cvName);
      if (cvResponses == null) {
        return null;
      }
      return cvResponses.get(mvName);
    }

    /**
     * Get the gain matrix.
     *
     * @return matrix of gains [numCV][numMV]
     */
    public double[][] getGainMatrix() {
      double[][] gains = new double[cvNames.length][mvNames.length];
      for (int i = 0; i < cvNames.length; i++) {
        for (int j = 0; j < mvNames.length; j++) {
          StepResponse resp = get(cvNames[i], mvNames[j]);
          gains[i][j] = resp != null ? resp.getGain() : 0.0;
        }
      }
      return gains;
    }

    /**
     * Get the time constant matrix.
     *
     * @return matrix of time constants [numCV][numMV]
     */
    public double[][] getTimeConstantMatrix() {
      double[][] tau = new double[cvNames.length][mvNames.length];
      for (int i = 0; i < cvNames.length; i++) {
        for (int j = 0; j < mvNames.length; j++) {
          StepResponse resp = get(cvNames[i], mvNames[j]);
          tau[i][j] = resp != null ? resp.getTimeConstant() : 1.0;
        }
      }
      return tau;
    }

    /**
     * Get the dead time matrix.
     *
     * @return matrix of dead times [numCV][numMV]
     */
    public double[][] getDeadTimeMatrix() {
      double[][] theta = new double[cvNames.length][mvNames.length];
      for (int i = 0; i < cvNames.length; i++) {
        for (int j = 0; j < mvNames.length; j++) {
          StepResponse resp = get(cvNames[i], mvNames[j]);
          theta[i][j] = resp != null ? resp.getDeadTime() : 0.0;
        }
      }
      return theta;
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
     * Get the CV names.
     *
     * @return array of CV names
     */
    public String[] getCvNames() {
      return cvNames.clone();
    }

    /**
     * Export to CSV format.
     *
     * @return CSV string
     */
    public String toCSV() {
      StringBuilder sb = new StringBuilder();
      sb.append("CV,MV,Gain,TimeConstant,DeadTime,SettlingTime\n");
      for (String cvName : cvNames) {
        for (String mvName : mvNames) {
          StepResponse resp = get(cvName, mvName);
          if (resp != null) {
            sb.append(cvName).append(",");
            sb.append(mvName).append(",");
            sb.append(String.format("%.6f", resp.getGain())).append(",");
            sb.append(String.format("%.2f", resp.getTimeConstant())).append(",");
            sb.append(String.format("%.2f", resp.getDeadTime())).append(",");
            sb.append(String.format("%.2f", resp.getSettlingTime())).append("\n");
          }
        }
      }
      return sb.toString();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("StepResponseMatrix [\n");
      sb.append("  MVs: ").append(String.join(", ", mvNames)).append("\n");
      sb.append("  CVs: ").append(String.join(", ", cvNames)).append("\n");
      sb.append("  Gain Matrix:\n");
      double[][] gains = getGainMatrix();
      for (int i = 0; i < cvNames.length; i++) {
        sb.append("    ").append(cvNames[i]).append(": ");
        for (int j = 0; j < mvNames.length; j++) {
          sb.append(String.format("%.4f", gains[i][j]));
          if (j < mvNames.length - 1) {
            sb.append(", ");
          }
        }
        sb.append("\n");
      }
      sb.append("]");
      return sb.toString();
    }
  }
}
