package neqsim.process.mpc;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Computes linearized process models from NeqSim ProcessSystem using finite differences.
 *
 * <p>
 * The ProcessLinearizer automatically calculates the Jacobian matrices (gain matrices) that
 * describe how controlled variables respond to changes in manipulated variables around an operating
 * point. These matrices are essential for linear MPC algorithms.
 * </p>
 *
 * <p>
 * The linearization uses central finite differences for improved accuracy:
 * </p>
 * 
 * <pre>
 * ∂CV[i]/∂MV[j] ≈ (CV[i](MV[j]+δ) - CV[i](MV[j]-δ)) / (2δ)
 * </pre>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Create linearizer for a process
 * ProcessLinearizer linearizer = new ProcessLinearizer(processSystem);
 *
 * // Define inputs and outputs
 * linearizer.addMV(new ManipulatedVariable("ValveOpening", valve, "opening"));
 * linearizer.addMV(new ManipulatedVariable("HeaterDuty", heater, "duty", "kW"));
 * linearizer.addCV(new ControlledVariable("Pressure", separator, "pressure", "bara"));
 * linearizer.addCV(new ControlledVariable("Temperature", outlet, "temperature", "C"));
 *
 * // Compute linearization
 * LinearizationResult result = linearizer.linearize(0.01); // 1% perturbation
 *
 * // Access gains
 * double[][] gains = result.getGainMatrix();
 * double dPressure_dValve = gains[0][0];
 * double dPressure_dHeater = gains[0][1];
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class ProcessLinearizer {

  /** The process system to linearize. */
  private final ProcessSystem processSystem;

  /** List of manipulated variables. */
  private final List<ManipulatedVariable> manipulatedVariables = new ArrayList<>();

  /** List of controlled variables. */
  private final List<ControlledVariable> controlledVariables = new ArrayList<>();

  /** List of disturbance variables. */
  private final List<DisturbanceVariable> disturbanceVariables = new ArrayList<>();

  /** Default perturbation size as fraction of variable range. */
  private double defaultPerturbationSize = 0.01;

  /** Minimum absolute perturbation to avoid numerical issues. */
  private double minimumAbsolutePerturbation = 1.0e-6;

  /** Whether to use central differences (more accurate) or forward differences (faster). */
  private boolean useCentralDifferences = true;

  /**
   * Construct a linearizer for a ProcessSystem.
   *
   * @param processSystem the NeqSim process to linearize
   */
  public ProcessLinearizer(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("ProcessSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Add a manipulated variable to the linearization.
   *
   * @param mv the manipulated variable
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer addMV(ManipulatedVariable mv) {
    if (mv == null) {
      throw new IllegalArgumentException("ManipulatedVariable must not be null");
    }
    manipulatedVariables.add(mv);
    return this;
  }

  /**
   * Add a controlled variable to the linearization.
   *
   * @param cv the controlled variable
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer addCV(ControlledVariable cv) {
    if (cv == null) {
      throw new IllegalArgumentException("ControlledVariable must not be null");
    }
    controlledVariables.add(cv);
    return this;
  }

  /**
   * Add a disturbance variable to the linearization.
   *
   * @param dv the disturbance variable
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer addDV(DisturbanceVariable dv) {
    if (dv == null) {
      throw new IllegalArgumentException("DisturbanceVariable must not be null");
    }
    disturbanceVariables.add(dv);
    return this;
  }

  /**
   * Get the list of manipulated variables.
   *
   * @return unmodifiable list of MVs
   */
  public List<ManipulatedVariable> getManipulatedVariables() {
    return new ArrayList<>(manipulatedVariables);
  }

  /**
   * Get the list of controlled variables.
   *
   * @return unmodifiable list of CVs
   */
  public List<ControlledVariable> getControlledVariables() {
    return new ArrayList<>(controlledVariables);
  }

  /**
   * Get the list of disturbance variables.
   *
   * @return unmodifiable list of DVs
   */
  public List<DisturbanceVariable> getDisturbanceVariables() {
    return new ArrayList<>(disturbanceVariables);
  }

  /**
   * Set the default perturbation size.
   *
   * @param perturbation the perturbation as a fraction (0.01 = 1%)
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer setDefaultPerturbationSize(double perturbation) {
    if (perturbation <= 0 || perturbation > 1) {
      throw new IllegalArgumentException("Perturbation must be between 0 and 1");
    }
    this.defaultPerturbationSize = perturbation;
    return this;
  }

  /**
   * Set the minimum absolute perturbation.
   *
   * @param minPerturbation the minimum perturbation value
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer setMinimumAbsolutePerturbation(double minPerturbation) {
    if (minPerturbation <= 0) {
      throw new IllegalArgumentException("Minimum perturbation must be positive");
    }
    this.minimumAbsolutePerturbation = minPerturbation;
    return this;
  }

  /**
   * Set whether to use central differences.
   *
   * @param useCentral true for central differences (more accurate), false for forward differences
   *        (faster)
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer setUseCentralDifferences(boolean useCentral) {
    this.useCentralDifferences = useCentral;
    return this;
  }

  /**
   * Clear all variable definitions.
   *
   * @return this linearizer for method chaining
   */
  public ProcessLinearizer clear() {
    manipulatedVariables.clear();
    controlledVariables.clear();
    disturbanceVariables.clear();
    return this;
  }

  /**
   * Perform linearization with the default perturbation size.
   *
   * @return the linearization result containing gain matrices
   */
  public LinearizationResult linearize() {
    return linearize(defaultPerturbationSize);
  }

  /**
   * Perform linearization with a specified perturbation size.
   *
   * <p>
   * The linearization process:
   * </p>
   * <ol>
   * <li>Read baseline CV values at current operating point</li>
   * <li>For each MV, perturb and run simulation to measure CV changes</li>
   * <li>Calculate gain as ΔCV/ΔMV</li>
   * <li>Restore MV to original value</li>
   * <li>Repeat for all MV-CV pairs</li>
   * </ol>
   *
   * @param perturbationSize perturbation as fraction of variable range
   * @return the linearization result
   */
  public LinearizationResult linearize(double perturbationSize) {
    long startTime = System.currentTimeMillis();

    int numMV = manipulatedVariables.size();
    int numCV = controlledVariables.size();
    int numDV = disturbanceVariables.size();

    if (numMV == 0 || numCV == 0) {
      return new LinearizationResult("At least one MV and one CV must be defined",
          System.currentTimeMillis() - startTime);
    }

    try {
      // Run baseline simulation
      processSystem.run();

      // Read baseline values
      double[] mvBaseline = new double[numMV];
      double[] cvBaseline = new double[numCV];
      double[] dvBaseline = new double[numDV];
      String[] mvNames = new String[numMV];
      String[] cvNames = new String[numCV];
      String[] dvNames = new String[numDV];

      for (int i = 0; i < numMV; i++) {
        ManipulatedVariable mv = manipulatedVariables.get(i);
        mvBaseline[i] = mv.readValue();
        mvNames[i] = mv.getName();
      }

      for (int i = 0; i < numCV; i++) {
        ControlledVariable cv = controlledVariables.get(i);
        cvBaseline[i] = cv.readValue();
        cvNames[i] = cv.getName();
      }

      for (int i = 0; i < numDV; i++) {
        DisturbanceVariable dv = disturbanceVariables.get(i);
        dvBaseline[i] = dv.readValue();
        dvNames[i] = dv.getName();
      }

      // Calculate gain matrix (∂CV/∂MV)
      double[][] gainMatrix = new double[numCV][numMV];

      for (int j = 0; j < numMV; j++) {
        ManipulatedVariable mv = manipulatedVariables.get(j);
        double originalValue = mvBaseline[j];

        // Calculate perturbation delta
        double delta = calculatePerturbation(mv, originalValue, perturbationSize);

        double[] cvPlus;
        double[] cvMinus;

        if (useCentralDifferences) {
          // Central difference: perturb both directions
          // Positive perturbation
          mv.writeValue(originalValue + delta);
          processSystem.run();
          cvPlus = readAllCVs();

          // Negative perturbation
          mv.writeValue(originalValue - delta);
          processSystem.run();
          cvMinus = readAllCVs();

          // Calculate gains using central difference
          for (int i = 0; i < numCV; i++) {
            gainMatrix[i][j] = (cvPlus[i] - cvMinus[i]) / (2.0 * delta);
          }
        } else {
          // Forward difference: perturb positive only
          mv.writeValue(originalValue + delta);
          processSystem.run();
          cvPlus = readAllCVs();

          for (int i = 0; i < numCV; i++) {
            gainMatrix[i][j] = (cvPlus[i] - cvBaseline[i]) / delta;
          }
        }

        // Restore original value
        mv.writeValue(originalValue);
      }

      // Restore process to baseline state
      processSystem.run();

      // Calculate disturbance gain matrix (∂CV/∂DV) if DVs are defined
      double[][] disturbanceGainMatrix = new double[numCV][numDV];
      // Note: DV gains would require external perturbation capability
      // For now, use stored sensitivity if available
      for (int k = 0; k < numDV; k++) {
        DisturbanceVariable dv = disturbanceVariables.get(k);
        double[] sensitivity = dv.getCvSensitivity();
        for (int i = 0; i < numCV && i < sensitivity.length; i++) {
          disturbanceGainMatrix[i][k] = sensitivity[i];
        }
      }

      long computationTime = System.currentTimeMillis() - startTime;

      return new LinearizationResult(gainMatrix, disturbanceGainMatrix, mvBaseline, cvBaseline,
          dvBaseline, mvNames, cvNames, dvNames, perturbationSize, computationTime);

    } catch (Exception e) {
      return new LinearizationResult("Linearization failed: " + e.getMessage(),
          System.currentTimeMillis() - startTime);
    }
  }

  /**
   * Calculate the perturbation delta for a variable.
   *
   * @param mv the manipulated variable
   * @param currentValue the current value
   * @param perturbationFraction the perturbation as a fraction
   * @return the perturbation delta
   */
  private double calculatePerturbation(ManipulatedVariable mv, double currentValue,
      double perturbationFraction) {
    double range = mv.getMaxValue() - mv.getMinValue();
    double delta;

    if (Double.isFinite(range) && range > 0) {
      // Use fraction of range
      delta = perturbationFraction * range;
    } else if (Math.abs(currentValue) > minimumAbsolutePerturbation) {
      // Use fraction of current value
      delta = perturbationFraction * Math.abs(currentValue);
    } else {
      // Use minimum absolute perturbation
      delta = minimumAbsolutePerturbation;
    }

    // Ensure delta is at least the minimum
    delta = Math.max(delta, minimumAbsolutePerturbation);

    // Ensure perturbation doesn't exceed bounds
    double maxPositive = mv.getMaxValue() - currentValue;
    double maxNegative = currentValue - mv.getMinValue();
    if (Double.isFinite(maxPositive)) {
      delta = Math.min(delta, maxPositive);
    }
    if (Double.isFinite(maxNegative)) {
      delta = Math.min(delta, maxNegative);
    }

    return delta;
  }

  /**
   * Read all CV values.
   *
   * @return array of current CV values
   */
  private double[] readAllCVs() {
    double[] values = new double[controlledVariables.size()];
    for (int i = 0; i < controlledVariables.size(); i++) {
      values[i] = controlledVariables.get(i).readValue();
    }
    return values;
  }

  /**
   * Perform linearization at multiple operating points for validation.
   *
   * @param numPoints number of operating points to test
   * @param perturbationSize perturbation for each linearization
   * @return list of linearization results at different operating points
   */
  public List<LinearizationResult> linearizeMultiplePoints(int numPoints, double perturbationSize) {
    List<LinearizationResult> results = new ArrayList<>();

    // For each MV, test at different values within its range
    if (manipulatedVariables.isEmpty()) {
      return results;
    }

    ManipulatedVariable primaryMV = manipulatedVariables.get(0);
    double min = primaryMV.getMinValue();
    double max = primaryMV.getMaxValue();

    if (!Double.isFinite(min) || !Double.isFinite(max)) {
      // If no bounds, just do single linearization
      results.add(linearize(perturbationSize));
      return results;
    }

    double originalValue = primaryMV.readValue();

    for (int p = 0; p < numPoints; p++) {
      double fraction = (double) p / (numPoints - 1);
      double testValue = min + fraction * (max - min);

      primaryMV.writeValue(testValue);
      try {
        processSystem.run();
      } catch (Exception e) {
        continue;
      }

      results.add(linearize(perturbationSize));
    }

    // Restore original value
    primaryMV.writeValue(originalValue);
    try {
      processSystem.run();
    } catch (Exception e) {
      // Ignore restore errors
    }

    return results;
  }

  /**
   * Check if the process is approximately linear within a range.
   *
   * @param perturbationSize1 first perturbation size
   * @param perturbationSize2 second perturbation size (should be different)
   * @param tolerance acceptable relative difference in gains
   * @return true if gains are consistent (linear behavior)
   */
  public boolean isApproximatelyLinear(double perturbationSize1, double perturbationSize2,
      double tolerance) {
    LinearizationResult result1 = linearize(perturbationSize1);
    LinearizationResult result2 = linearize(perturbationSize2);

    if (!result1.isSuccessful() || !result2.isSuccessful()) {
      return false;
    }

    double[][] gains1 = result1.getGainMatrix();
    double[][] gains2 = result2.getGainMatrix();

    for (int i = 0; i < gains1.length; i++) {
      for (int j = 0; j < gains1[i].length; j++) {
        double g1 = gains1[i][j];
        double g2 = gains2[i][j];
        double maxAbs = Math.max(Math.abs(g1), Math.abs(g2));
        if (maxAbs > 1e-10) {
          double relDiff = Math.abs(g1 - g2) / maxAbs;
          if (relDiff > tolerance) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
