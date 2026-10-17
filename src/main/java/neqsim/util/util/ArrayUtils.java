package neqsim.util.util;

/**
 * Utilities for defensive copying of arrays and matrix arrays.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ArrayUtils {
  /**
   * Creates an array utility instance.
   */
  private ArrayUtils() {
  }

  /**
   * Copies a primitive double array.
   *
   * @param values source values
   * @return an independent copy, or null if values is null
   */
  public static double[] copyArray(double[] values) {
    return values == null ? null : values.clone();
  }

  /**
   * Copies an array of references. The referenced objects are not copied.
   *
   * @param <T> array element type
   * @param values source values
   * @return an independent array, or null if values is null
   */
  public static <T> T[] copyArray(T[] values) {
    return values == null ? null : values.clone();
  }

  /**
   * Copies a primitive double matrix, including each row.
   *
   * @param values source matrix
   * @return an independent matrix copy, or null if values is null
   */
  public static double[][] copyMatrixArray(double[][] values) {
    if (values == null) {
      return null;
    }
    double[][] copy = values.clone();
    for (int row = 0; row < values.length; row++) {
      copy[row] = copyArray(values[row]);
    }
    return copy;
  }

  /**
   * Copies a reference matrix, including each row. The referenced objects are not copied.
   *
   * @param <T> matrix element type
   * @param values source matrix
   * @return an independent matrix-array copy, or null if values is null
   */
  public static <T> T[][] copyMatrixArray(T[][] values) {
    if (values == null) {
      return null;
    }
    T[][] copy = values.clone();
    for (int row = 0; row < values.length; row++) {
      copy[row] = copyArray(values[row]);
    }
    return copy;
  }

  /**
   * Clears a primitive array by multiplying each value by zero.
   *
   * <p>
   * This retains IEEE NaN and signed-zero behavior of the original multiplication operation.
   * </p>
   *
   * @param values values to clear, or null
   */
  public static void clearArray(double[] values) {
    if (values == null) {
      return;
    }
    for (int index = 0; index < values.length; index++) {
      values[index] *= 0.0;
    }
  }

  /**
   * Clears a primitive matrix by multiplying each value by zero. Null matrices and rows are ignored.
   *
   * <p>
   * This retains IEEE NaN and signed-zero behavior of the original multiplication operation.
   * </p>
   *
   * @param values matrix values to clear, or null
   */
  public static void clearMatrixArray(double[][] values) {
    if (values == null) {
      return;
    }
    for (double[] row : values) {
      clearArray(row);
    }
  }

  /**
   * Extracts a column from a row-major matrix.
   *
   * @param matrix source matrix
   * @param column column index
   * @return copied column values
   */
  public static double[] extractColumn(double[][] matrix, int column) {
    double[] values = new double[matrix.length];
    for (int row = 0; row < matrix.length; row++) {
      values[row] = matrix[row][column];
    }
    return values;
  }

  /**
   * Computes the infinity norm of a vector.
   *
   * @param vector values to measure
   * @return largest absolute component, or zero for an empty vector
   */
  public static double infinityNorm(double[] vector) {
    double maximum = 0.0;
    for (double value : vector) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }
}