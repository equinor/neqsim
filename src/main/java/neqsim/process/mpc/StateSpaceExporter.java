package neqsim.process.mpc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Exports process models in state-space form for use with external MPC solvers.
 *
 * <p>
 * The StateSpaceExporter converts linearization results into standard state-space representation
 * (A, B, C, D matrices) and exports them in various formats for integration with industrial MPC
 * packages and control libraries.
 * </p>
 *
 * <p>
 * State-space representation:
 * </p>
 * 
 * <pre>
 * x(k+1) = A * x(k) + B * u(k)
 * y(k) = C * x(k) + D * u(k)
 * </pre>
 *
 * <p>
 * Supported export formats:
 * </p>
 * <ul>
 * <li>JSON - for Python control libraries</li>
 * <li>CSV - for spreadsheet analysis</li>
 * <li>MATLAB .m file - for MATLAB MPC Toolbox</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Create exporter from linearization result
 * StateSpaceExporter exporter = new StateSpaceExporter(linearizationResult);
 *
 * // Generate discrete-time state-space model
 * StateSpaceModel model = exporter.toDiscreteStateSpace(60.0); // 60s sample time
 *
 * // Export to various formats
 * exporter.exportJSON("model.json");
 * exporter.exportMATLAB("model.m");
 * exporter.exportCSV("model_"); // Creates model_A.csv, model_B.csv, etc.
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class StateSpaceExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The linearization result to export. */
  private final LinearizationResult linearizationResult;

  /** Step response matrix for more detailed model. */
  private StepResponseGenerator.StepResponseMatrix stepResponseMatrix;

  /** Generated state-space model. */
  private StateSpaceModel stateSpaceModel;

  /**
   * Construct an exporter from a linearization result.
   *
   * @param linearizationResult the linearization to export
   */
  public StateSpaceExporter(LinearizationResult linearizationResult) {
    if (linearizationResult == null) {
      throw new IllegalArgumentException("LinearizationResult must not be null");
    }
    this.linearizationResult = linearizationResult;
  }

  /**
   * Construct an exporter from a step response matrix.
   *
   * @param stepResponseMatrix the step responses to export
   */
  public StateSpaceExporter(StepResponseGenerator.StepResponseMatrix stepResponseMatrix) {
    if (stepResponseMatrix == null) {
      throw new IllegalArgumentException("StepResponseMatrix must not be null");
    }
    this.stepResponseMatrix = stepResponseMatrix;

    // Create a linearization result from step response gains
    double[][] gains = stepResponseMatrix.getGainMatrix();
    String[] mvNames = stepResponseMatrix.getMvNames();
    String[] cvNames = stepResponseMatrix.getCvNames();

    this.linearizationResult =
        new LinearizationResult(gains, new double[cvNames.length][0], new double[mvNames.length],
            new double[cvNames.length], new double[0], mvNames, cvNames, new String[0], 0.0, 0);
  }

  /**
   * Set the step response matrix for detailed model generation.
   *
   * @param matrix the step response matrix
   * @return this exporter for method chaining
   */
  public StateSpaceExporter setStepResponseMatrix(StepResponseGenerator.StepResponseMatrix matrix) {
    this.stepResponseMatrix = matrix;
    return this;
  }

  /**
   * Generate a discrete-time state-space model.
   *
   * <p>
   * For a first-order system with gain K and time constant τ, the discrete-time state-space
   * representation with sample time Ts is:
   * </p>
   * 
   * <pre>
   * A = exp(-Ts/τ)
   * B = K * (1 - exp(-Ts/τ))
   * C = 1
   * D = 0
   * </pre>
   *
   * @param sampleTimeSeconds the sample time in seconds
   * @return the state-space model
   */
  public StateSpaceModel toDiscreteStateSpace(double sampleTimeSeconds) {
    if (sampleTimeSeconds <= 0) {
      throw new IllegalArgumentException("Sample time must be positive");
    }

    int numMV = linearizationResult.getNumMV();
    int numCV = linearizationResult.getNumCV();
    double[][] gains = linearizationResult.getGainMatrix();

    // For a simple gain model without dynamics, we use:
    // x(k+1) = x(k) (states = outputs for static gain)
    // y(k) = x(k) + D * u(k)
    // This is a simplification; for full dynamics we need time constants

    double[][] tauMatrix = null;
    if (stepResponseMatrix != null) {
      tauMatrix = stepResponseMatrix.getTimeConstantMatrix();
    }

    // Use default time constant if not available
    double defaultTau = 60.0; // 60 seconds default

    // Build state-space matrices
    // For MIMO first-order systems, each CV has its own state
    double[][] A = new double[numCV][numCV];
    double[][] B = new double[numCV][numMV];
    double[][] C = new double[numCV][numCV];
    double[][] D = new double[numCV][numMV];

    for (int i = 0; i < numCV; i++) {
      // Each CV is a separate first-order state
      double tau = defaultTau;
      if (tauMatrix != null && tauMatrix[i].length > 0) {
        // Use average time constant for this CV
        double sum = 0;
        int count = 0;
        for (double t : tauMatrix[i]) {
          if (t > 0) {
            sum += t;
            count++;
          }
        }
        if (count > 0) {
          tau = sum / count;
        }
      }

      // Discrete-time state transition for first-order system
      double a = Math.exp(-sampleTimeSeconds / tau);
      A[i][i] = a;

      // Input matrix (discrete)
      for (int j = 0; j < numMV; j++) {
        double K = gains[i][j];
        B[i][j] = K * (1.0 - a);
      }

      // Output matrix (states are outputs)
      C[i][i] = 1.0;

      // Feedthrough (zero for first-order systems)
      // D[i][j] = 0;
    }

    stateSpaceModel = new StateSpaceModel(A, B, C, D, sampleTimeSeconds,
        linearizationResult.getMvNames(), linearizationResult.getCvNames());

    return stateSpaceModel;
  }

  /**
   * Get the last generated state-space model.
   *
   * @return the state-space model, or null if not yet generated
   */
  public StateSpaceModel getStateSpaceModel() {
    return stateSpaceModel;
  }

  /**
   * Export the model to JSON format.
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportJSON(String filename) throws IOException {
    if (stateSpaceModel == null) {
      toDiscreteStateSpace(60.0); // Default sample time
    }

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(stateSpaceModel.toMap());

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(json);
    }
  }

  /**
   * Export the model to CSV format.
   *
   * <p>
   * Creates multiple files: prefix_A.csv, prefix_B.csv, prefix_C.csv, prefix_D.csv
   * </p>
   *
   * @param filenamePrefix the output filename prefix
   * @throws IOException if writing fails
   */
  public void exportCSV(String filenamePrefix) throws IOException {
    if (stateSpaceModel == null) {
      toDiscreteStateSpace(60.0);
    }

    exportMatrix(stateSpaceModel.getA(), filenamePrefix + "A.csv");
    exportMatrix(stateSpaceModel.getB(), filenamePrefix + "B.csv");
    exportMatrix(stateSpaceModel.getC(), filenamePrefix + "C.csv");
    exportMatrix(stateSpaceModel.getD(), filenamePrefix + "D.csv");

    // Also export step response coefficients if available
    if (stepResponseMatrix != null) {
      try (BufferedWriter writer =
          new BufferedWriter(new FileWriter(filenamePrefix + "step_response.csv"))) {
        writer.write(stepResponseMatrix.toCSV());
      }
    }
  }

  private void exportMatrix(double[][] matrix, String filename) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      for (double[] row : matrix) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < row.length; j++) {
          if (j > 0) {
            sb.append(",");
          }
          sb.append(String.format("%.10g", row[j]));
        }
        writer.write(sb.toString());
        writer.newLine();
      }
    }
  }

  /**
   * Export the model to MATLAB .m file format.
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportMATLAB(String filename) throws IOException {
    if (stateSpaceModel == null) {
      toDiscreteStateSpace(60.0);
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write("% NeqSim State-Space Model Export\n");
      writer.write("% Generated by StateSpaceExporter\n");
      writer.write("%\n");
      writer
          .write(String.format("%% Sample Time: %.4f seconds\n", stateSpaceModel.getSampleTime()));
      writer.write("%\n\n");

      // Write A matrix
      writer.write("A = [\n");
      writeMatrixMATLAB(writer, stateSpaceModel.getA());
      writer.write("];\n\n");

      // Write B matrix
      writer.write("B = [\n");
      writeMatrixMATLAB(writer, stateSpaceModel.getB());
      writer.write("];\n\n");

      // Write C matrix
      writer.write("C = [\n");
      writeMatrixMATLAB(writer, stateSpaceModel.getC());
      writer.write("];\n\n");

      // Write D matrix
      writer.write("D = [\n");
      writeMatrixMATLAB(writer, stateSpaceModel.getD());
      writer.write("];\n\n");

      // Write sample time
      writer.write(String.format("Ts = %.6f;\n\n", stateSpaceModel.getSampleTime()));

      // Write variable names
      writer.write("% Input (MV) names:\n");
      writer.write("InputNames = {");
      String[] mvNames = stateSpaceModel.getInputNames();
      for (int i = 0; i < mvNames.length; i++) {
        if (i > 0) {
          writer.write(", ");
        }
        writer.write("'" + mvNames[i] + "'");
      }
      writer.write("};\n\n");

      writer.write("% Output (CV) names:\n");
      writer.write("OutputNames = {");
      String[] cvNames = stateSpaceModel.getOutputNames();
      for (int i = 0; i < cvNames.length; i++) {
        if (i > 0) {
          writer.write(", ");
        }
        writer.write("'" + cvNames[i] + "'");
      }
      writer.write("};\n\n");

      // Create ss object
      writer.write("% Create discrete-time state-space model\n");
      writer.write("sys = ss(A, B, C, D, Ts);\n");
      writer.write("sys.InputName = InputNames;\n");
      writer.write("sys.OutputName = OutputNames;\n");
    }
  }

  private void writeMatrixMATLAB(BufferedWriter writer, double[][] matrix) throws IOException {
    for (int i = 0; i < matrix.length; i++) {
      writer.write("  ");
      for (int j = 0; j < matrix[i].length; j++) {
        if (j > 0) {
          writer.write("  ");
        }
        writer.write(String.format("%12.6g", matrix[i][j]));
      }
      if (i < matrix.length - 1) {
        writer.write(";\n");
      } else {
        writer.write("\n");
      }
    }
  }

  /**
   * Export step response coefficients for DMC-style controllers.
   *
   * @param filename the output filename
   * @param numCoefficients number of step coefficients per MV-CV pair
   * @throws IOException if writing fails
   */
  public void exportStepCoefficients(String filename, int numCoefficients) throws IOException {
    if (stepResponseMatrix == null) {
      throw new IllegalStateException("Step response matrix not available. "
          + "Use setStepResponseMatrix() or construct from StepResponseMatrix.");
    }

    String[] mvNames = stepResponseMatrix.getMvNames();
    String[] cvNames = stepResponseMatrix.getCvNames();

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Header
      writer.write("Step,");
      for (String cvName : cvNames) {
        for (String mvName : mvNames) {
          writer.write(cvName + "_" + mvName + ",");
        }
      }
      writer.newLine();

      // Data rows
      for (int k = 0; k < numCoefficients; k++) {
        writer.write(String.valueOf(k));
        for (String cvName : cvNames) {
          for (String mvName : mvNames) {
            StepResponse resp = stepResponseMatrix.get(cvName, mvName);
            double[] coeffs = resp != null ? resp.getStepCoefficients(numCoefficients)
                : new double[numCoefficients];
            writer.write("," + String.format("%.6f", coeffs[k]));
          }
        }
        writer.newLine();
      }
    }
  }

  /**
   * Represents a discrete-time state-space model.
   */
  public static class StateSpaceModel implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double[][] A;
    private final double[][] B;
    private final double[][] C;
    private final double[][] D;
    private final double sampleTime;
    private final String[] inputNames;
    private final String[] outputNames;

    /**
     * Construct a state-space model.
     *
     * @param A state transition matrix
     * @param B input matrix
     * @param C output matrix
     * @param D feedthrough matrix
     * @param sampleTime sample time in seconds
     * @param inputNames names of inputs (MVs)
     * @param outputNames names of outputs (CVs)
     */
    public StateSpaceModel(double[][] A, double[][] B, double[][] C, double[][] D,
        double sampleTime, String[] inputNames, String[] outputNames) {
      this.A = deepCopy(A);
      this.B = deepCopy(B);
      this.C = deepCopy(C);
      this.D = deepCopy(D);
      this.sampleTime = sampleTime;
      this.inputNames = inputNames != null ? inputNames.clone() : new String[0];
      this.outputNames = outputNames != null ? outputNames.clone() : new String[0];
    }

    private double[][] deepCopy(double[][] matrix) {
      if (matrix == null) {
        return new double[0][0];
      }
      double[][] copy = new double[matrix.length][];
      for (int i = 0; i < matrix.length; i++) {
        copy[i] = matrix[i] != null ? matrix[i].clone() : new double[0];
      }
      return copy;
    }

    /**
     * Get the A matrix.
     *
     * @return copy of state transition matrix
     */
    public double[][] getA() {
      return deepCopy(A);
    }

    /**
     * Get the B matrix.
     *
     * @return copy of input matrix
     */
    public double[][] getB() {
      return deepCopy(B);
    }

    /**
     * Get the C matrix.
     *
     * @return copy of output matrix
     */
    public double[][] getC() {
      return deepCopy(C);
    }

    /**
     * Get the D matrix.
     *
     * @return copy of feedthrough matrix
     */
    public double[][] getD() {
      return deepCopy(D);
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
     * Get the input names.
     *
     * @return copy of input names
     */
    public String[] getInputNames() {
      return inputNames.clone();
    }

    /**
     * Get the output names.
     *
     * @return copy of output names
     */
    public String[] getOutputNames() {
      return outputNames.clone();
    }

    /**
     * Get the number of states.
     *
     * @return number of states
     */
    public int getNumStates() {
      return A.length;
    }

    /**
     * Get the number of inputs.
     *
     * @return number of inputs
     */
    public int getNumInputs() {
      return B.length > 0 ? B[0].length : 0;
    }

    /**
     * Get the number of outputs.
     *
     * @return number of outputs
     */
    public int getNumOutputs() {
      return C.length;
    }

    /**
     * Get the steady-state gain from input j to output i.
     *
     * <p>
     * For a stable system, the DC gain is: G = C * (I - A)^-1 * B + D
     * </p>
     *
     * @param outputIndex the output index
     * @param inputIndex the input index
     * @return the steady-state gain
     */
    public double getSteadyStateGain(int outputIndex, int inputIndex) {
      // Simplified: For diagonal A near identity, approximate DC gain
      // G ≈ B / (1 - A) for first-order
      if (outputIndex >= 0 && outputIndex < A.length && inputIndex >= 0
          && inputIndex < getNumInputs()) {
        double a = A[outputIndex][outputIndex];
        double b = B[outputIndex][inputIndex];
        double d = D[outputIndex][inputIndex];
        if (Math.abs(1 - a) > 1e-10) {
          return b / (1 - a) + d;
        }
      }
      return 0.0;
    }

    /**
     * Get the dominant time constant for an output.
     *
     * @param outputIndex the output index
     * @return the time constant in seconds
     */
    public double getDominantTimeConstant(int outputIndex) {
      if (outputIndex >= 0 && outputIndex < A.length) {
        double a = A[outputIndex][outputIndex];
        if (a > 0 && a < 1) {
          // τ = -Ts / ln(a)
          return -sampleTime / Math.log(a);
        }
      }
      return sampleTime;
    }

    /**
     * Simulate one step.
     *
     * @param x current state
     * @param u input
     * @return next state
     */
    public double[] stepState(double[] x, double[] u) {
      int n = A.length;
      double[] xNext = new double[n];
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          xNext[i] += A[i][j] * x[j];
        }
        for (int j = 0; j < u.length && j < B[i].length; j++) {
          xNext[i] += B[i][j] * u[j];
        }
      }
      return xNext;
    }

    /**
     * Calculate output from state.
     *
     * @param x current state
     * @param u input
     * @return output
     */
    public double[] getOutput(double[] x, double[] u) {
      int p = C.length;
      double[] y = new double[p];
      for (int i = 0; i < p; i++) {
        for (int j = 0; j < x.length && j < C[i].length; j++) {
          y[i] += C[i][j] * x[j];
        }
        for (int j = 0; j < u.length && j < D[i].length; j++) {
          y[i] += D[i][j] * u[j];
        }
      }
      return y;
    }

    /**
     * Convert to a Map for JSON serialization.
     *
     * @return map representation
     */
    public java.util.Map<String, Object> toMap() {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("sampleTime", sampleTime);
      map.put("sampleTimeUnit", "seconds");
      map.put("numStates", getNumStates());
      map.put("numInputs", getNumInputs());
      map.put("numOutputs", getNumOutputs());
      map.put("inputNames", java.util.Arrays.asList(inputNames));
      map.put("outputNames", java.util.Arrays.asList(outputNames));
      map.put("A", matrixToList(A));
      map.put("B", matrixToList(B));
      map.put("C", matrixToList(C));
      map.put("D", matrixToList(D));
      return map;
    }

    private List<List<Double>> matrixToList(double[][] matrix) {
      List<List<Double>> list = new java.util.ArrayList<>();
      for (double[] row : matrix) {
        List<Double> rowList = new java.util.ArrayList<>();
        for (double v : row) {
          rowList.add(v);
        }
        list.add(rowList);
      }
      return list;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("StateSpaceModel {\n");
      sb.append("  sampleTime: ").append(sampleTime).append(" s\n");
      sb.append("  states: ").append(getNumStates()).append("\n");
      sb.append("  inputs: ").append(getNumInputs()).append(" ")
          .append(java.util.Arrays.toString(inputNames)).append("\n");
      sb.append("  outputs: ").append(getNumOutputs()).append(" ")
          .append(java.util.Arrays.toString(outputNames)).append("\n");
      sb.append("}");
      return sb.toString();
    }
  }
}
