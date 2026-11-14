package neqsim.process.equipment.distillation;

/**
 * Anderson mixing for a fixed-point iteration {@code x = G(x)}.
 *
 * <p>
 * Plain substitution uses only the latest evaluation and converges at the rate of the slowest mode of {@code G}.
 * Anderson mixing keeps the last few input/output pairs and returns the affine combination of the outputs whose
 * combined residual {@code G(x) - x} is smallest in a weighted least-squares sense. For a linear map this is equivalent
 * to GMRES on the residual equation, so several slow or oscillating modes are removed together instead of one at a time
 * (Walker and Ni, SIAM J. Numer. Anal. 49, 2011).
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class AndersonMixer {
  /** Relative Tikhonov regularization of the least-squares normal equations. */
  private static final double REGULARIZATION = 1.0e-10;

  /** Largest accepted mixing coefficient magnitude before the oldest pair is discarded. */
  private static final double MAX_COEFFICIENT = 1.0e3;

  /** Stored outputs {@code G(x)}, oldest first. */
  private final double[][] outputs;

  /** Stored weighted residuals {@code w * (G(x) - x)}, oldest first. */
  private final double[][] residuals;

  /** Number of stored pairs. */
  private int size = 0;

  /**
   * Create a mixer.
   *
   * @param depth number of residual differences to combine; at least one
   */
  AndersonMixer(int depth) {
    int capacity = Math.max(1, depth) + 1;
    outputs = new double[capacity][];
    residuals = new double[capacity][];
  }

  /** Discard all stored pairs. */
  void reset() {
    for (int i = 0; i < outputs.length; i++) {
      outputs[i] = null;
      residuals[i] = null;
    }
    size = 0;
  }

  /**
   * Get the number of stored input/output pairs.
   *
   * @return stored pair count
   */
  int size() {
    return size;
  }

  /**
   * Store one evaluation of the fixed-point map.
   *
   * @param input state the map was evaluated at
   * @param output map result for that state
   * @param weights positive weight per vector entry that makes the residual entries comparable
   */
  void add(double[] input, double[] output, double[] weights) {
    if (size > 0 && outputs[0].length != output.length) {
      reset();
    }
    if (size == outputs.length) {
      System.arraycopy(outputs, 1, outputs, 0, size - 1);
      System.arraycopy(residuals, 1, residuals, 0, size - 1);
      size--;
    }
    double[] residual = new double[output.length];
    for (int i = 0; i < output.length; i++) {
      residual[i] = weights[i] * (output[i] - input[i]);
    }
    outputs[size] = output.clone();
    residuals[size] = residual;
    size++;
  }

  /**
   * Get the weighted Euclidean norm of the latest residual.
   *
   * @return latest residual norm, or NaN when nothing is stored
   */
  double latestResidualNorm() {
    if (size == 0) {
      return Double.NaN;
    }
    double sum = 0.0;
    for (double value : residuals[size - 1]) {
      sum += value * value;
    }
    return Math.sqrt(sum);
  }

  /**
   * Calculate the Anderson-mixed next iterate.
   *
   * @return mixed state, or {@code null} when fewer than two pairs are stored or the least-squares problem is
   * degenerate
   */
  double[] mix() {
    // Use the longest usable history; drop the oldest pair while the coefficients are degenerate.
    double[] coefficients = null;
    int first = 0;
    while (first < size - 1 && coefficients == null) {
      coefficients = solveCoefficients(first);
      if (coefficients == null) {
        first++;
      }
    }
    if (coefficients == null) {
      return null;
    }
    double[] mixed = outputs[size - 1].clone();
    for (int j = 0; j < coefficients.length; j++) {
      double[] newer = outputs[first + j + 1];
      double[] older = outputs[first + j];
      for (int i = 0; i < mixed.length; i++) {
        mixed[i] -= coefficients[j] * (newer[i] - older[i]);
      }
    }
    for (double value : mixed) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        return null;
      }
    }
    return mixed;
  }

  /**
   * Solve the least-squares coefficients for the residual differences starting at one stored pair.
   *
   * @param first index of the oldest pair to use
   * @return coefficients per residual difference, or {@code null} when they are not finite or too large
   */
  private double[] solveCoefficients(int first) {
    int columns = size - 1 - first;
    double[] latest = residuals[size - 1];
    double[][] differences = new double[columns][];
    for (int j = 0; j < columns; j++) {
      double[] newer = residuals[first + j + 1];
      double[] older = residuals[first + j];
      differences[j] = new double[latest.length];
      for (int i = 0; i < latest.length; i++) {
        differences[j][i] = newer[i] - older[i];
      }
    }
    double[][] normal = new double[columns][columns + 1];
    double largestDiagonal = 0.0;
    for (int a = 0; a < columns; a++) {
      for (int b = a; b < columns; b++) {
        double sum = 0.0;
        for (int i = 0; i < latest.length; i++) {
          sum += differences[a][i] * differences[b][i];
        }
        normal[a][b] = sum;
        normal[b][a] = sum;
      }
      double rhs = 0.0;
      for (int i = 0; i < latest.length; i++) {
        rhs += differences[a][i] * latest[i];
      }
      normal[a][columns] = rhs;
      largestDiagonal = Math.max(largestDiagonal, normal[a][a]);
    }
    if (!(largestDiagonal > 0.0)) {
      return null;
    }
    for (int a = 0; a < columns; a++) {
      normal[a][a] += REGULARIZATION * largestDiagonal;
    }
    for (int pivot = 0; pivot < columns; pivot++) {
      int best = pivot;
      for (int row = pivot + 1; row < columns; row++) {
        if (Math.abs(normal[row][pivot]) > Math.abs(normal[best][pivot])) {
          best = row;
        }
      }
      double[] swap = normal[pivot];
      normal[pivot] = normal[best];
      normal[best] = swap;
      if (Math.abs(normal[pivot][pivot]) < 1.0e-14 * largestDiagonal) {
        return null;
      }
      for (int row = pivot + 1; row < columns; row++) {
        double factor = normal[row][pivot] / normal[pivot][pivot];
        for (int col = pivot; col <= columns; col++) {
          normal[row][col] -= factor * normal[pivot][col];
        }
      }
    }
    double[] coefficients = new double[columns];
    for (int row = columns - 1; row >= 0; row--) {
      double sum = normal[row][columns];
      for (int col = row + 1; col < columns; col++) {
        sum -= normal[row][col] * coefficients[col];
      }
      coefficients[row] = sum / normal[row][row];
      if (Double.isNaN(coefficients[row]) || Math.abs(coefficients[row]) > MAX_COEFFICIENT) {
        return null;
      }
    }
    return coefficients;
  }
}
