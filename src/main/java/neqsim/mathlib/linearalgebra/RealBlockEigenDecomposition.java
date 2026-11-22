package neqsim.mathlib.linearalgebra;

import static neqsim.util.util.ArrayUtils.copyMatrixArray;

/**
 * Immutable real representation of an eigensystem, including complex conjugate pairs.
 *
 * <p>
 * The columns of V form a real block basis, not individual real eigenvectors when imaginary parts are nonzero. In
 * {@code A V = V D}, a conjugate pair {@code a + i b, a - i b} corresponds to the block {@code [[a, b], [-b, a]]} in D.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RealBlockEigenDecomposition {
  private final double[] realParts;
  private final double[] imaginaryParts;
  private final double[][] vectors;

  /**
   * Copy paired eigenvalues and their real block basis in backend order.
   *
   * @param realParts real eigenvalue parts
   * @param imaginaryParts imaginary eigenvalue parts, with positive member first in each conjugate pair
   * @param vectors square real block basis V
   * @throws LinearAlgebraException if dimensions disagree, entries are non-finite or conjugate pairing is invalid
   */
  public RealBlockEigenDecomposition(double[] realParts, double[] imaginaryParts, double[][] vectors) {
    LinearAlgebraValidation.requireVector(realParts, "Real eigenvalue parts");
    LinearAlgebraValidation.requireVector(imaginaryParts, "Imaginary eigenvalue parts");
    LinearAlgebraValidation.requireSquareMatrix(vectors, "Real block basis");
    LinearAlgebraValidation.requireDimension(imaginaryParts.length, realParts.length, "Eigenvalue part count");
    LinearAlgebraValidation.requireDimension(vectors.length, realParts.length, "Real block basis dimension");
    for (int index = 0; index < realParts.length; index++) {
      if (imaginaryParts[index] != 0.0) {
        if (imaginaryParts[index] < 0.0 || index + 1 == realParts.length
            || imaginaryParts[index + 1] != -imaginaryParts[index] || realParts[index + 1] != realParts[index]) {
          throw new LinearAlgebraException(
              "Complex eigenvalues must be adjacent conjugate pairs, positive part first.");
        }
        index++;
      }
    }
    this.realParts = realParts.clone();
    this.imaginaryParts = imaginaryParts.clone();
    this.vectors = copyMatrixArray(vectors);
  }

  /**
   * Return real parts in the same order as the real block basis columns.
   *
   * @return independent real-part array
   */
  public double[] getRealParts() {
    return realParts.clone();
  }

  /**
   * Return imaginary parts without discarding the conjugate-pair information.
   *
   * @return independent imaginary-part array
   */
  public double[] getImaginaryParts() {
    return imaginaryParts.clone();
  }

  /**
   * Return the real block basis V.
   *
   * @return independent square basis matrix
   */
  public double[][] getVectors() {
    return copyMatrixArray(vectors);
  }

  /**
   * Determine whether any conjugate pair is present.
   *
   * @return true if any imaginary part is nonzero
   */
  public boolean hasComplexEigenvalues() {
    for (double value : imaginaryParts) {
      if (value != 0.0) {
        return true;
      }
    }
    return false;
  }
}
