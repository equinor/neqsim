package neqsim.mathlib.linearalgebra;

/**
 * Argument checks shared by every {@link LinearAlgebraOperations} implementation.
 *
 * <p>
 * The backends differ in what they tolerate: some accept ragged arrays and propagate {@code NaN} silently, others throw
 * library-specific exceptions. Validating here first means every backend rejects the same input the same way, with the
 * same exception type.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
final class LinearAlgebraValidation {
  /**
   * Dummy constructor, not for use. Class is to be considered static.
   */
  private LinearAlgebraValidation() {
  }

  /**
   * Verify that a matrix is non-null, non-empty, rectangular and entirely finite.
   *
   * @param matrix matrix to check
   * @param name argument name used in the failure message
   * @throws LinearAlgebraException if any condition is violated
   */
  static void requireMatrix(double[][] matrix, String name) {
    if (matrix == null) {
      throw new LinearAlgebraException(name + " must not be null.");
    }
    if (matrix.length == 0) {
      throw new LinearAlgebraException(name + " must have at least one row.");
    }
    if (matrix[0] == null || matrix[0].length == 0) {
      throw new LinearAlgebraException(name + " must have at least one column.");
    }
    int columns = matrix[0].length;
    for (int row = 0; row < matrix.length; row++) {
      if (matrix[row] == null) {
        throw new LinearAlgebraException(name + " row " + row + " must not be null.");
      }
      if (matrix[row].length != columns) {
        throw new LinearAlgebraException(name + " must be rectangular, but row " + row + " has " + matrix[row].length
            + " entries where row 0 has " + columns + ".");
      }
      for (int column = 0; column < columns; column++) {
        if (!Double.isFinite(matrix[row][column])) {
          throw new LinearAlgebraException(name + " contains a non-finite value " + matrix[row][column] + " at row "
              + row + ", column " + column + ".");
        }
      }
    }
  }

  /**
   * Verify that a matrix passes {@link #requireMatrix(double[][], String)} and is square.
   *
   * @param matrix matrix to check
   * @param name argument name used in the failure message
   * @throws LinearAlgebraException if the matrix is invalid or not square
   */
  static void requireSquareMatrix(double[][] matrix, String name) {
    requireMatrix(matrix, name);
    if (matrix.length != matrix[0].length) {
      throw new LinearAlgebraException(
          name + " must be square, but has " + matrix.length + " rows and " + matrix[0].length + " columns.");
    }
  }

  /**
   * Verify that a vector is non-null, non-empty and entirely finite.
   *
   * @param vector vector to check
   * @param name argument name used in the failure message
   * @throws LinearAlgebraException if any condition is violated
   */
  static void requireVector(double[] vector, String name) {
    if (vector == null) {
      throw new LinearAlgebraException(name + " must not be null.");
    }
    if (vector.length == 0) {
      throw new LinearAlgebraException(name + " must have at least one entry.");
    }
    for (int index = 0; index < vector.length; index++) {
      if (!Double.isFinite(vector[index])) {
        throw new LinearAlgebraException(
            name + " contains a non-finite value " + vector[index] + " at index " + index + ".");
      }
    }
  }

  /**
   * Verify that two dimensions agree.
   *
   * @param actual the dimension found
   * @param expected the dimension required
   * @param description what the two dimensions represent, used in the failure message
   * @throws LinearAlgebraException if the dimensions differ
   */
  static void requireDimension(int actual, int expected, String description) {
    if (actual != expected) {
      throw new LinearAlgebraException(description + ": expected " + expected + " but found " + actual + ".");
    }
  }

  /**
   * Verify that two already validated matrices have the same dimensions.
   *
   * @param matrixA first matrix
   * @param matrixB second matrix
   * @param operation name of the operation, used in the failure message
   * @throws LinearAlgebraException if the shapes differ
   */
  static void requireSameShape(double[][] matrixA, double[][] matrixB, String operation) {
    if (matrixA.length != matrixB.length || matrixA[0].length != matrixB[0].length) {
      throw new LinearAlgebraException(operation + " needs operands of equal shape, but found " + matrixA.length
          + " by " + matrixA[0].length + " and " + matrixB.length + " by " + matrixB[0].length + ".");
    }
  }

  /**
   * Verify that a scalar argument is finite.
   *
   * @param value the scalar to check
   * @param name argument name used in the failure message
   * @throws LinearAlgebraException if the value is infinite or {@code NaN}
   */
  static void requireFiniteScalar(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new LinearAlgebraException(name + " must be finite, but was " + value + ".");
    }
  }

  /**
   * Verify that a requested matrix size is positive.
   *
   * @param size the size to check
   * @param name argument name used in the failure message
   * @throws LinearAlgebraException if the size is zero or negative
   */
  static void requirePositiveSize(int size, String name) {
    if (size <= 0) {
      throw new LinearAlgebraException(name + " must be positive, but was " + size + ".");
    }
  }

  /**
   * Verify that an inclusive index range lies inside a dimension.
   *
   * @param first index of the first element in the range
   * @param last index of the last element in the range
   * @param bound number of elements available along the dimension
   * @param dimension name of the dimension, used in the failure message
   * @throws LinearAlgebraException if the bounds are out of order or fall outside the dimension
   */
  static void requireIndexRange(int first, int last, int bound, String dimension) {
    if (first < 0 || last < first || last >= bound) {
      throw new LinearAlgebraException("Requested " + dimension + " range " + first + " to " + last
          + " is not inside the available " + bound + " " + dimension + "s.");
    }
  }

  /**
   * Verify that a block fits inside a matrix when placed at a given corner.
   *
   * @param matrix the matrix receiving the block
   * @param firstRow row index where the block starts
   * @param firstColumn column index where the block starts
   * @param block the block to place
   * @throws LinearAlgebraException if the corner is negative or the block extends past an edge
   */
  static void requireBlockFits(double[][] matrix, int firstRow, int firstColumn, double[][] block) {
    if (firstRow < 0 || firstColumn < 0 || firstRow + block.length > matrix.length
        || firstColumn + block[0].length > matrix[0].length) {
      throw new LinearAlgebraException(
          "A " + block.length + " by " + block[0].length + " block placed at row " + firstRow + ", column "
              + firstColumn + " does not fit inside a " + matrix.length + " by " + matrix[0].length + " matrix.");
    }
  }

  /**
   * Verify that a computed result contains no non-finite entry.
   *
   * <p>
   * A backend that fails to detect a singular system typically returns infinities or {@code NaN} rather than throwing,
   * so this is the last line of defence before a meaningless result reaches the caller.
   * </p>
   *
   * @param values computed values to check
   * @param operation name of the operation that produced them, used in the failure message
   * @throws LinearAlgebraException if any value is non-finite
   */
  static void requireFiniteResult(double[] values, String operation) {
    for (int index = 0; index < values.length; index++) {
      if (!Double.isFinite(values[index])) {
        throw new LinearAlgebraException(operation + " produced a non-finite value " + values[index] + " at index "
            + index + "; the system is singular or too ill-conditioned to solve.");
      }
    }
  }
}
