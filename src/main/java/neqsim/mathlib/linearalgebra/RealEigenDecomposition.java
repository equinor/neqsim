package neqsim.mathlib.linearalgebra;

import static neqsim.util.util.ArrayUtils.copyMatrixArray;

/**
 * Immutable paired eigenvalues and column eigenvectors for a real eigensystem.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RealEigenDecomposition {
  private final double[] eigenvalues;
  private final double[][] eigenvectors;

  /**
   * Copy a real eigensystem, preserving the backend's ordering and vector normalization.
   *
   * @param eigenvalues eigenvalues in backend order
   * @param eigenvectors square matrix with corresponding eigenvectors in its columns
   * @throws LinearAlgebraException if dimensions disagree or entries are non-finite
   */
  public RealEigenDecomposition(double[] eigenvalues, double[][] eigenvectors) {
    LinearAlgebraValidation.requireVector(eigenvalues, "Eigenvalues");
    LinearAlgebraValidation.requireSquareMatrix(eigenvectors, "Eigenvectors");
    LinearAlgebraValidation.requireDimension(eigenvectors.length, eigenvalues.length, "Eigensystem dimension");
    this.eigenvalues = eigenvalues.clone();
    this.eigenvectors = copyMatrixArray(eigenvectors);
  }

  /**
   * Return eigenvalues in the same order as the eigenvector columns.
   *
   * @return an independent eigenvalue array
   */
  public double[] getEigenvalues() {
    return eigenvalues.clone();
  }

  /**
   * Return the corresponding column eigenvectors.
   *
   * @return an independent eigenvector matrix
   */
  public double[][] getEigenvectors() {
    return copyMatrixArray(eigenvectors);
  }
}
