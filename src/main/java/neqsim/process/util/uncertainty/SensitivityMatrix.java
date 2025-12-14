package neqsim.process.util.uncertainty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a sensitivity matrix for uncertainty propagation in process simulations.
 *
 * <p>
 * The sensitivity matrix contains partial derivatives of output variables with respect to input
 * variables, enabling uncertainty propagation and sensitivity analysis for optimization.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SensitivityMatrix implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String[] inputVariables;
  private final String[] outputVariables;
  private final double[][] jacobian;
  private final Map<String, Integer> inputIndex;
  private final Map<String, Integer> outputIndex;

  /**
   * Creates a new sensitivity matrix.
   *
   * @param inputVariables names of input variables
   * @param outputVariables names of output variables
   */
  public SensitivityMatrix(String[] inputVariables, String[] outputVariables) {
    this.inputVariables = inputVariables.clone();
    this.outputVariables = outputVariables.clone();
    this.jacobian = new double[outputVariables.length][inputVariables.length];

    this.inputIndex = new HashMap<>();
    for (int i = 0; i < inputVariables.length; i++) {
      inputIndex.put(inputVariables[i], i);
    }

    this.outputIndex = new HashMap<>();
    for (int i = 0; i < outputVariables.length; i++) {
      outputIndex.put(outputVariables[i], i);
    }
  }

  /**
   * Sets a sensitivity value (partial derivative).
   *
   * @param outputVariable the output variable name
   * @param inputVariable the input variable name
   * @param sensitivity the partial derivative d(output)/d(input)
   */
  public void setSensitivity(String outputVariable, String inputVariable, double sensitivity) {
    Integer outIdx = outputIndex.get(outputVariable);
    Integer inIdx = inputIndex.get(inputVariable);
    if (outIdx != null && inIdx != null) {
      jacobian[outIdx][inIdx] = sensitivity;
    }
  }

  /**
   * Gets a sensitivity value.
   *
   * @param outputVariable the output variable name
   * @param inputVariable the input variable name
   * @return the partial derivative, or 0 if not found
   */
  public double getSensitivity(String outputVariable, String inputVariable) {
    Integer outIdx = outputIndex.get(outputVariable);
    Integer inIdx = inputIndex.get(inputVariable);
    if (outIdx != null && inIdx != null) {
      return jacobian[outIdx][inIdx];
    }
    return 0.0;
  }

  /**
   * Gets the raw Jacobian matrix.
   *
   * @return the Jacobian matrix (output x input)
   */
  public double[][] getJacobian() {
    return jacobian.clone();
  }

  /**
   * Gets the sensitivity row for a specific output.
   *
   * @param outputVariable the output variable name
   * @return array of sensitivities to all inputs, or null if not found
   */
  public double[] getSensitivitiesForOutput(String outputVariable) {
    Integer outIdx = outputIndex.get(outputVariable);
    if (outIdx != null) {
      return jacobian[outIdx].clone();
    }
    return null;
  }

  /**
   * Propagates input uncertainties to output uncertainties.
   *
   * <p>
   * Uses the formula: Cov(Y) = J * Cov(X) * J^T where J is the Jacobian.
   * </p>
   *
   * @param inputStdDevs standard deviations of input variables
   * @return standard deviations of output variables
   */
  public double[] propagateUncertainty(double[] inputStdDevs) {
    if (inputStdDevs.length != inputVariables.length) {
      throw new IllegalArgumentException("Input std dev array size mismatch");
    }

    double[] outputStdDevs = new double[outputVariables.length];

    for (int i = 0; i < outputVariables.length; i++) {
      double variance = 0.0;
      for (int j = 0; j < inputVariables.length; j++) {
        variance += Math.pow(jacobian[i][j] * inputStdDevs[j], 2);
      }
      outputStdDevs[i] = Math.sqrt(variance);
    }

    return outputStdDevs;
  }

  /**
   * Propagates uncertainties with correlation.
   *
   * @param inputCovariance covariance matrix of inputs
   * @return covariance matrix of outputs
   */
  public double[][] propagateCovariance(double[][] inputCovariance) {
    int n = inputVariables.length;
    int m = outputVariables.length;

    if (inputCovariance.length != n || inputCovariance[0].length != n) {
      throw new IllegalArgumentException("Input covariance matrix size mismatch");
    }

    // Cov(Y) = J * Cov(X) * J^T
    // First: temp = J * Cov(X)
    double[][] temp = new double[m][n];
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < n; j++) {
        for (int k = 0; k < n; k++) {
          temp[i][j] += jacobian[i][k] * inputCovariance[k][j];
        }
      }
    }

    // Then: result = temp * J^T
    double[][] result = new double[m][m];
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < m; j++) {
        for (int k = 0; k < n; k++) {
          result[i][j] += temp[i][k] * jacobian[j][k];
        }
      }
    }

    return result;
  }

  /**
   * Gets the input variable names.
   *
   * @return array of input variable names
   */
  public String[] getInputVariables() {
    return inputVariables.clone();
  }

  /**
   * Gets the output variable names.
   *
   * @return array of output variable names
   */
  public String[] getOutputVariables() {
    return outputVariables.clone();
  }

  /**
   * Identifies the most influential input for each output.
   *
   * @return map of output variable to most influential input variable
   */
  public Map<String, String> getMostInfluentialInputs() {
    Map<String, String> result = new HashMap<>();

    for (int i = 0; i < outputVariables.length; i++) {
      double maxSens = 0.0;
      int maxIdx = 0;
      for (int j = 0; j < inputVariables.length; j++) {
        if (Math.abs(jacobian[i][j]) > Math.abs(maxSens)) {
          maxSens = jacobian[i][j];
          maxIdx = j;
        }
      }
      result.put(outputVariables[i], inputVariables[maxIdx]);
    }

    return result;
  }

  /**
   * Calculates normalized sensitivities (elasticities).
   *
   * @param inputValues current values of input variables
   * @param outputValues current values of output variables
   * @return normalized sensitivity matrix (% change in output / % change in input)
   */
  public double[][] getNormalizedSensitivities(double[] inputValues, double[] outputValues) {
    double[][] normalized = new double[outputVariables.length][inputVariables.length];

    for (int i = 0; i < outputVariables.length; i++) {
      for (int j = 0; j < inputVariables.length; j++) {
        if (Math.abs(outputValues[i]) > 1e-10 && Math.abs(inputValues[j]) > 1e-10) {
          normalized[i][j] = jacobian[i][j] * inputValues[j] / outputValues[i];
        }
      }
    }

    return normalized;
  }
}
