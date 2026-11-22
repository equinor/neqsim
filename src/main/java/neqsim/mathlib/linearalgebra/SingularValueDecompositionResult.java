package neqsim.mathlib.linearalgebra;

import static neqsim.util.util.ArrayUtils.copyMatrixArray;

/**
 * Immutable compact singular value decomposition with paired columns of U and V.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SingularValueDecompositionResult {
  private final double[] singularValues;
  private final double[][] leftSingularVectors;
  private final double[][] rightSingularVectors;

  /**
   * Copy a compact decomposition satisfying {@code A = U diag(s) V^T}.
   *
   * @param singularValues nonnegative singular values in descending order
   * @param leftSingularVectors m by k matrix U, where k is the smaller input dimension
   * @param rightSingularVectors n by k matrix V, not its transpose
   * @throws LinearAlgebraException if dimensions, ordering or finite-value requirements are violated
   */
  public SingularValueDecompositionResult(double[] singularValues, double[][] leftSingularVectors,
      double[][] rightSingularVectors) {
    LinearAlgebraValidation.requireVector(singularValues, "Singular values");
    LinearAlgebraValidation.requireMatrix(leftSingularVectors, "Left singular vectors");
    LinearAlgebraValidation.requireMatrix(rightSingularVectors, "Right singular vectors");
    int count = singularValues.length;
    LinearAlgebraValidation.requireDimension(leftSingularVectors[0].length, count, "U column count");
    LinearAlgebraValidation.requireDimension(rightSingularVectors[0].length, count, "V column count");
    LinearAlgebraValidation.requireDimension(Math.min(leftSingularVectors.length, rightSingularVectors.length), count,
        "Compact decomposition size");
    for (int index = 0; index < count; index++) {
      if (singularValues[index] < 0.0 || index > 0 && singularValues[index] > singularValues[index - 1]) {
        throw new LinearAlgebraException("Singular values must be nonnegative and in descending order.");
      }
    }
    this.singularValues = singularValues.clone();
    this.leftSingularVectors = copyMatrixArray(leftSingularVectors);
    this.rightSingularVectors = copyMatrixArray(rightSingularVectors);
  }

  /**
   * Return the singular values in descending order.
   *
   * @return an independent singular-value array
   */
  public double[] getSingularValues() {
    return singularValues.clone();
  }

  /**
   * Return U with left singular vectors in its columns.
   *
   * @return an independent m by k matrix
   */
  public double[][] getLeftSingularVectors() {
    return copyMatrixArray(leftSingularVectors);
  }

  /**
   * Return V with right singular vectors in its columns.
   *
   * @return an independent n by k matrix
   */
  public double[][] getRightSingularVectors() {
    return copyMatrixArray(rightSingularVectors);
  }
}
