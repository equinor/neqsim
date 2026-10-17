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
}