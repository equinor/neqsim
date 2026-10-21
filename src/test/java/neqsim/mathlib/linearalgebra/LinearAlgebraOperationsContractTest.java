package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract every {@link LinearAlgebraOperations} backend must satisfy.
 *
 * <p>
 * The interface exists so a caller can swap backends without changing any calling code, which only holds if the
 * backends behave identically. Each test here therefore runs once per backend, against the same input. Register a new
 * backend in {@link #backends()} and it inherits the whole contract; anything specific to one library belongs in that
 * backend's own test class instead.
 * </p>
 */
class LinearAlgebraOperationsContractTest {
  /**
   * Every backend under test.
   *
   * @return one instance per {@link LinearAlgebraOperations} implementation
   */
  static Stream<LinearAlgebraOperations> backends() {
    return Stream.of(new EjmlLinearAlgebra());
  }

  /**
   * Verify that decomposition result constructors do not retain mutable caller storage.
   */
  @Test
  void decompositionResultsCopyConstructorArguments() {
    double[] values = {2.0, 1.0};
    double[][] vectors = {{1.0, 0.0}, {0.0, 1.0}};
    RealEigenDecomposition eigen = new RealEigenDecomposition(values, vectors);
    SingularValueDecompositionResult svd = new SingularValueDecompositionResult(values, vectors, vectors);
    values[0] = Double.NaN;
    vectors[0][0] = Double.NaN;
    assertEquals(2.0, eigen.getEigenvalues()[0]);
    assertEquals(1.0, eigen.getEigenvectors()[0][0]);
    assertEquals(2.0, svd.getSingularValues()[0]);
    assertEquals(1.0, svd.getLeftSingularVectors()[0][0]);
    assertEquals(1.0, svd.getRightSingularVectors()[0][0]);
  }

  /**
   * Verify invalid result shapes and singular-value ordering cannot enter the public result objects.
   */
  @Test
  void decompositionResultsRejectInconsistentShapes() {
    double[][] identity = {{1.0, 0.0}, {0.0, 1.0}};
    assertThrows(LinearAlgebraException.class, () -> new RealEigenDecomposition(new double[] {1.0}, identity));
    assertThrows(LinearAlgebraException.class,
        () -> new SingularValueDecompositionResult(new double[] {1.0}, identity, identity));
    assertThrows(LinearAlgebraException.class,
        () -> new SingularValueDecompositionResult(new double[] {1.0, 2.0}, identity, identity));
    assertThrows(LinearAlgebraException.class,
        () -> new SingularValueDecompositionResult(new double[] {1.0, -1.0}, identity, identity));
  }

  /**
   * Verify that decomposition does not modify its input arrays.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void decompositionsLeaveArgumentsUnmodified(LinearAlgebraOperations algebra) {
    double[][] matrix = {{2.0, 1.0}, {1.0, 3.0}};
    algebra.eigenDecomposition(matrix);
    algebra.singularValueDecomposition(matrix);
    assertArrayEquals(new double[] {2.0, 1.0}, matrix[0], 0.0);
    assertArrayEquals(new double[] {1.0, 3.0}, matrix[1], 0.0);
  }

  /**
   * Verify eigenpair residuals without relying on backend ordering or eigenvector signs.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void exposesPairedRealEigenvectors(LinearAlgebraOperations algebra) {
    for (double[][] matrix : new double[][][] {{{2.0, 1.0}, {1.0, 2.0}}, {{1.0, 2.0}, {0.0, 3.0}},
        {{0.0, 0.0}, {0.0, 0.0}}, {{4.0}}}) {
      RealEigenDecomposition result = algebra.eigenDecomposition(matrix);
      double[] values = result.getEigenvalues();
      double[][] vectors = result.getEigenvectors();
      assertEquals(matrix.length, values.length);
      assertEquals(matrix.length, vectors.length);
      assertEquals(matrix.length, vectors[0].length);
      double[][] product = algebra.multiply(matrix, vectors);
      for (int column = 0; column < values.length; column++) {
        double norm = 0.0;
        for (int row = 0; row < matrix.length; row++) {
          norm = Math.hypot(norm, vectors[row][column]);
          assertEquals(vectors[row][column] * values[column], product[row][column], 1.0e-12);
        }
        assertTrue(norm > 0.0, "Eigenvectors must not be zero");
      }
      values[0] = Double.NaN;
      vectors[0][0] = Double.NaN;
      assertTrue(Double.isFinite(result.getEigenvalues()[0]));
      assertTrue(Double.isFinite(result.getEigenvectors()[0][0]));
    }
  }

  /**
   * Verify compact dimensions, ordering, orthogonality and reconstruction for every matrix shape.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void exposesPairedCompactSingularVectors(LinearAlgebraOperations algebra) {
    for (double[][] matrix : new double[][][] {{{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}}, {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}},
        {{1.0, 2.0}, {2.0, 4.0}}, {{0.0, 0.0}, {0.0, 0.0}}, {{3.0}}, {{2.0, 0.0}, {0.0, 2.0}}}) {
      SingularValueDecompositionResult result = algebra.singularValueDecomposition(matrix);
      double[] values = result.getSingularValues();
      double[][] left = result.getLeftSingularVectors();
      double[][] right = result.getRightSingularVectors();
      int count = Math.min(matrix.length, matrix[0].length);
      assertEquals(count, values.length);
      assertEquals(matrix.length, left.length);
      assertEquals(matrix[0].length, right.length);
      assertEquals(count, left[0].length);
      assertEquals(count, right[0].length);
      double[][] diagonal = new double[count][count];
      for (int index = 0; index < count; index++) {
        assertTrue(values[index] >= 0.0);
        if (index > 0) {
          assertTrue(values[index] <= values[index - 1]);
        }
        diagonal[index][index] = values[index];
      }
      double[][] reconstructed = algebra.multiply(algebra.multiply(left, diagonal), algebra.transpose(right));
      for (int row = 0; row < matrix.length; row++) {
        assertArrayEquals(matrix[row], reconstructed[row], 1.0e-12);
      }
      double[][] leftGram = algebra.multiply(algebra.transpose(left), left);
      double[][] rightGram = algebra.multiply(algebra.transpose(right), right);
      for (int row = 0; row < count; row++) {
        assertArrayEquals(algebra.identity(count)[row], leftGram[row], 1.0e-12);
        assertArrayEquals(algebra.identity(count)[row], rightGram[row], 1.0e-12);
      }
      values[0] = Double.NaN;
      left[0][0] = Double.NaN;
      right[0][0] = Double.NaN;
      assertTrue(Double.isFinite(result.getSingularValues()[0]));
      assertTrue(Double.isFinite(result.getLeftSingularVectors()[0][0]));
      assertTrue(Double.isFinite(result.getRightSingularVectors()[0][0]));
    }
  }

  /**
   * Verify rejection of invalid decomposition inputs and complex spectra.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void rejectsInvalidDecompositionInputs(LinearAlgebraOperations algebra) {
    assertThrows(LinearAlgebraException.class, () -> algebra.eigenDecomposition(null));
    assertThrows(LinearAlgebraException.class, () -> algebra.eigenDecomposition(new double[][] {{1.0, 2.0}}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.eigenDecomposition(new double[][] {{0.0, -1.0}, {1.0, 0.0}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.eigenDecomposition(new double[][] {{Double.NaN}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.singularValueDecomposition(null));
    assertThrows(LinearAlgebraException.class, () -> algebra.singularValueDecomposition(new double[0][]));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.singularValueDecomposition(new double[][] {{1.0}, {1.0, 2.0}}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.singularValueDecomposition(new double[][] {{Double.POSITIVE_INFINITY}}));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void reportsANonEmptyBackendName(LinearAlgebraOperations algebra) {
    assertEquals(algebra.getName(), algebra.getName().trim());
    assertEquals(true, algebra.getName().length() > 0);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void solvesSquareSystemAgainstAnalyticalSolution(LinearAlgebraOperations algebra) {
    double[] solution = algebra.solve(new double[][] {{4.0, 1.0}, {1.0, 3.0}}, new double[] {1.0, 2.0});

    assertEquals(1.0 / 11.0, solution[0], 1.0e-12);
    assertEquals(7.0 / 11.0, solution[1], 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void solvesOverdeterminedSystemInLeastSquaresSense(LinearAlgebraOperations algebra) {
    // Straight-line fit through (0,1), (1,2), (2,4); normal equations give intercept 2.5/3 and slope 1.5.
    double[][] matrixA = {{1.0, 0.0}, {1.0, 1.0}, {1.0, 2.0}};
    double[] solution = algebra.solveLeastSquares(matrixA, new double[] {1.0, 2.0, 4.0});

    assertEquals(2.5 / 3.0, solution[0], 1.0e-10);
    assertEquals(1.5, solution[1], 1.0e-10);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void invertsMatrixAndReproducesIdentity(LinearAlgebraOperations algebra) {
    double[][] matrix = {{4.0, 1.0}, {1.0, 3.0}};
    double[][] inverse = algebra.invert(matrix);

    assertEquals(3.0 / 11.0, inverse[0][0], 1.0e-12);
    assertEquals(-1.0 / 11.0, inverse[0][1], 1.0e-12);
    assertEquals(-1.0 / 11.0, inverse[1][0], 1.0e-12);
    assertEquals(4.0 / 11.0, inverse[1][1], 1.0e-12);

    double[][] identity = algebra.multiply(matrix, inverse);
    assertEquals(1.0, identity[0][0], 1.0e-12);
    assertEquals(0.0, identity[0][1], 1.0e-12);
    assertEquals(0.0, identity[1][0], 1.0e-12);
    assertEquals(1.0, identity[1][1], 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void multipliesMatricesAndVectors(LinearAlgebraOperations algebra) {
    double[][] matrixA = {{1.0, 2.0}, {3.0, 4.0}};
    double[][] product = algebra.multiply(matrixA, new double[][] {{5.0, 6.0}, {7.0, 8.0}});

    assertArrayEquals(new double[] {19.0, 22.0}, product[0], 1.0e-12);
    assertArrayEquals(new double[] {43.0, 50.0}, product[1], 1.0e-12);
    assertArrayEquals(new double[] {3.0, 7.0}, algebra.multiply(matrixA, new double[] {1.0, 1.0}), 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void addsAndSubtractsMatrices(LinearAlgebraOperations algebra) {
    double[][] matrixA = {{1.0, 2.0}, {3.0, 4.0}};
    double[][] matrixB = {{5.0, 6.0}, {7.0, 8.0}};

    double[][] sum = algebra.add(matrixA, matrixB);
    assertArrayEquals(new double[] {6.0, 8.0}, sum[0], 1.0e-12);
    assertArrayEquals(new double[] {10.0, 12.0}, sum[1], 1.0e-12);

    double[][] difference = algebra.subtract(matrixA, matrixB);
    assertArrayEquals(new double[] {-4.0, -4.0}, difference[0], 1.0e-12);
    assertArrayEquals(new double[] {-4.0, -4.0}, difference[1], 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void addsAndSubtractsVectors(LinearAlgebraOperations algebra) {
    double[] vectorA = {1.0, 2.0, 3.0};
    double[] vectorB = {0.5, -1.0, 4.0};

    assertArrayEquals(new double[] {1.5, 1.0, 7.0}, algebra.add(vectorA, vectorB), 1.0e-12);
    assertArrayEquals(new double[] {0.5, 3.0, -1.0}, algebra.subtract(vectorA, vectorB), 1.0e-12);

    // The damped Newton update the flash solvers use.
    assertArrayEquals(new double[] {0.75, 2.5, 1.0}, algebra.subtract(vectorA, algebra.scale(vectorB, 0.5)), 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void scalesMatrixAndVector(LinearAlgebraOperations algebra) {
    double[][] scaled = algebra.scale(new double[][] {{1.0, 2.0}, {3.0, 4.0}}, -1.0);

    assertArrayEquals(new double[] {-1.0, -2.0}, scaled[0], 1.0e-12);
    assertArrayEquals(new double[] {-3.0, -4.0}, scaled[1], 1.0e-12);
    assertArrayEquals(new double[] {0.5, -1.0}, algebra.scale(new double[] {1.0, -2.0}, 0.5), 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void buildsIdentityMatrix(LinearAlgebraOperations algebra) {
    double[][] identity = algebra.identity(3);

    assertEquals(3, identity.length);
    assertArrayEquals(new double[] {1.0, 0.0, 0.0}, identity[0], 0.0);
    assertArrayEquals(new double[] {0.0, 1.0, 0.0}, identity[1], 0.0);
    assertArrayEquals(new double[] {0.0, 0.0, 1.0}, identity[2], 0.0);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void transposesNonSquareMatrix(LinearAlgebraOperations algebra) {
    double[][] transposed = algebra.transpose(new double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}});

    assertEquals(3, transposed.length);
    assertEquals(2, transposed[0].length);
    assertArrayEquals(new double[] {1.0, 4.0}, transposed[0], 1.0e-12);
    assertArrayEquals(new double[] {2.0, 5.0}, transposed[1], 1.0e-12);
    assertArrayEquals(new double[] {3.0, 6.0}, transposed[2], 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void computesDeterminant(LinearAlgebraOperations algebra) {
    assertEquals(11.0, algebra.determinant(new double[][] {{4.0, 1.0}, {1.0, 3.0}}), 1.0e-12);
    assertEquals(0.0, algebra.determinant(new double[][] {{1.0, 2.0}, {2.0, 4.0}}), 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void computesNorms(LinearAlgebraOperations algebra) {
    assertEquals(5.0, algebra.euclideanNorm(new double[] {3.0, -4.0}), 1.0e-12);

    // Largest absolute column sum: column 0 gives 4, column 1 gives 6.
    assertEquals(6.0, algebra.oneNorm(new double[][] {{1.0, -2.0}, {-3.0, 4.0}}), 1.0e-12);

    assertEquals(2.0, algebra.spectralNorm(new double[][] {{2.0, 0.0}, {0.0, 1.0}}), 1.0e-12);
    assertEquals(5.0, algebra.spectralNorm(new double[][] {{3.0}, {4.0}}), 1.0e-12);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void extractsAndReplacesSubmatrix(LinearAlgebraOperations algebra) {
    double[][] matrix = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}, {7.0, 8.0, 9.0}};

    double[][] block = algebra.submatrix(matrix, 0, 1, 1, 2);
    assertEquals(2, block.length);
    assertArrayEquals(new double[] {2.0, 3.0}, block[0], 1.0e-12);
    assertArrayEquals(new double[] {5.0, 6.0}, block[1], 1.0e-12);

    double[][] patched = algebra.withSubmatrix(matrix, 2, 1, new double[][] {{0.0, 0.0}});
    assertArrayEquals(new double[] {7.0, 0.0, 0.0}, patched[2], 1.0e-12);
    assertArrayEquals(new double[] {7.0, 8.0, 9.0}, matrix[2], 0.0);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void detectsRankDeficiency(LinearAlgebraOperations algebra) {
    assertEquals(2, algebra.rank(new double[][] {{1.0, 0.0}, {0.0, 1.0}}));
    assertEquals(1, algebra.rank(new double[][] {{1.0, 2.0}, {2.0, 4.0}}));
    assertEquals(2, algebra.rank(new double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void computesConditionNumberFromSingularValues(LinearAlgebraOperations algebra) {
    assertEquals(1.0, algebra.conditionNumber(new double[][] {{1.0, 0.0}, {0.0, 1.0}}), 1.0e-12);
    assertEquals(2.0, algebra.conditionNumber(new double[][] {{2.0, 0.0}, {0.0, 1.0}}), 1.0e-12);

    // A rank-deficient matrix must report an infinite condition number, not a large finite one.
    assertEquals(Double.POSITIVE_INFINITY, algebra.conditionNumber(new double[][] {{1.0, 2.0}, {2.0, 4.0}}));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void rejectsSingularSystem(LinearAlgebraOperations algebra) {
    double[][] singular = {{1.0, 2.0}, {2.0, 4.0}};

    assertThrows(LinearAlgebraException.class, () -> algebra.solve(singular, new double[] {1.0, 2.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.invert(singular));
    assertThrows(LinearAlgebraException.class, () -> algebra.solveLeastSquares(singular, new double[] {1.0, 2.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra
        .solveLeastSquares(new double[][] {{1.0, 2.0}, {2.0, 4.0}, {3.0, 6.0}}, new double[] {1.0, 2.0, 3.0}));
  }

  /**
   * Distinguish finite LU output from an explicit numerical rank assessment.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void finiteLuResultsDoNotImplyGoodConditioning(LinearAlgebraOperations algebra) {
    double[][] matrix = {{1.0, 0.0}, {0.0, 1.0e-18}};
    double[] rhs = {1.0, 1.0e-18};

    // LU can solve this diagonal system despite its numerical rank being one at the SVD tolerance.
    assertEquals(1, algebra.rank(matrix));
    assertEquals(Double.POSITIVE_INFINITY, algebra.conditionNumber(matrix));
    double[] solution = algebra.solve(matrix, rhs);
    assertArrayEquals(new double[] {1.0, 1.0}, solution, 1.0e-12);
    assertArrayEquals(new double[] {1.0, 0.0}, algebra.invert(matrix)[0], 1.0e-12);
    assertEquals(1.0, algebra.invert(matrix)[1][1] * 1.0e-18, 1.0e-12);
    assertThrows(LinearAlgebraException.class, () -> algebra.solveLeastSquares(matrix, rhs));

    // A tiny absolute RHS perturbation gives an order-one change despite finite solutions and zero residuals.
    double[] perturbedRhs = {1.0, 2.0e-18};
    double[] perturbedSolution = algebra.solve(matrix, perturbedRhs);
    assertEquals(1.0, perturbedSolution[1] - solution[1], 1.0e-12);
    assertArrayEquals(perturbedRhs, algebra.multiply(matrix, perturbedSolution), 1.0e-30);
  }

  /**
   * Verify non-finite result rejection separately from numerical conditioning checks.
   *
   * @param algebra backend under test
   */
  @ParameterizedTest
  @MethodSource("backends")
  void rejectsOverflowingLuResults(LinearAlgebraOperations algebra) {
    double[][] matrix = {{1.0e-310}};
    assertThrows(LinearAlgebraException.class, () -> algebra.solve(matrix, new double[] {1.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.invert(matrix));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void rejectsInvalidInput(LinearAlgebraOperations algebra) {
    double[][] square = {{4.0, 1.0}, {1.0, 3.0}};

    assertThrows(LinearAlgebraException.class, () -> algebra.solve(null, new double[] {1.0, 2.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.solve(square, null));
    assertThrows(LinearAlgebraException.class, () -> algebra.solve(square, new double[] {1.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.solve(square, new double[] {1.0, Double.NaN}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.solve(new double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}, new double[] {1.0, 2.0}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.solve(new double[][] {{1.0, Double.POSITIVE_INFINITY}, {1.0, 3.0}}, new double[] {1.0, 2.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.multiply(square, new double[][] {{1.0, 2.0, 3.0}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.multiply(square, new double[] {1.0, 2.0, 3.0}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.solveLeastSquares(new double[][] {{1.0, 2.0, 3.0}}, new double[] {1.0}));
    assertThrows(LinearAlgebraException.class, () -> algebra.add(square, new double[][] {{1.0, 2.0, 3.0}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.subtract(square, new double[][] {{1.0, 2.0, 3.0}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.scale(square, Double.NaN));
    assertThrows(LinearAlgebraException.class, () -> algebra.scale(new double[] {1.0}, Double.NaN));
    assertThrows(LinearAlgebraException.class, () -> algebra.identity(0));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.determinant(new double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}));
    assertThrows(LinearAlgebraException.class, () -> algebra.submatrix(square, 0, 2, 0, 1));
    assertThrows(LinearAlgebraException.class, () -> algebra.submatrix(square, 1, 0, 0, 1));
    assertThrows(LinearAlgebraException.class, () -> algebra.withSubmatrix(square, 1, 1, new double[][] {{1.0, 2.0}}));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void rejectsRaggedMatrix(LinearAlgebraOperations algebra) {
    double[][] ragged = new double[2][];
    ragged[0] = new double[] {1.0, 2.0};
    ragged[1] = new double[] {3.0};

    assertThrows(LinearAlgebraException.class, () -> algebra.transpose(ragged));
  }

  @ParameterizedTest
  @MethodSource("backends")
  void leavesArgumentsUnmodified(LinearAlgebraOperations algebra) {
    double[][] matrixA = {{4.0, 1.0}, {1.0, 3.0}};
    double[] vectorB = {1.0, 2.0};

    algebra.solve(matrixA, vectorB);
    algebra.invert(matrixA);
    algebra.multiply(matrixA, vectorB);
    algebra.rank(matrixA);
    algebra.scale(matrixA, 2.0);
    algebra.scale(vectorB, 2.0);
    algebra.withSubmatrix(matrixA, 0, 0, new double[][] {{0.0}});

    assertArrayEquals(new double[] {4.0, 1.0}, matrixA[0], 0.0);
    assertArrayEquals(new double[] {1.0, 3.0}, matrixA[1], 0.0);
    assertArrayEquals(new double[] {1.0, 2.0}, vectorB, 0.0);
  }

  @ParameterizedTest
  @MethodSource("backends")
  void producesTheSameNumbersAsEveryOtherBackend(LinearAlgebraOperations algebra) {
    LinearAlgebraOperations reference = new EjmlLinearAlgebra();
    double[][] matrixA = {{4.0, 1.0, 0.5}, {1.0, 3.0, 0.25}, {0.5, 0.25, 2.0}};
    double[][] matrixB = {{1.0, -2.0, 3.0}, {-4.0, 5.0, -6.0}, {7.0, -8.0, 9.0}};
    double[] vectorB = {1.0, 2.0, 3.0};

    assertArrayEquals(reference.solve(matrixA, vectorB), algebra.solve(matrixA, vectorB), 1.0e-10);
    assertArrayEquals(reference.solveLeastSquares(matrixA, vectorB), algebra.solveLeastSquares(matrixA, vectorB),
        1.0e-10);
    assertEquals(reference.conditionNumber(matrixA), algebra.conditionNumber(matrixA), 1.0e-10);
    assertEquals(reference.rank(matrixA), algebra.rank(matrixA));
    assertEquals(reference.determinant(matrixA), algebra.determinant(matrixA), 1.0e-10);
    assertEquals(reference.oneNorm(matrixB), algebra.oneNorm(matrixB), 1.0e-10);
    assertEquals(reference.spectralNorm(matrixB), algebra.spectralNorm(matrixB), 1.0e-10);
    assertEquals(reference.euclideanNorm(vectorB), algebra.euclideanNorm(vectorB), 1.0e-12);

    for (int row = 0; row < matrixA.length; row++) {
      assertArrayEquals(reference.invert(matrixA)[row], algebra.invert(matrixA)[row], 1.0e-10);
      assertArrayEquals(reference.add(matrixA, matrixB)[row], algebra.add(matrixA, matrixB)[row], 1.0e-12);
      assertArrayEquals(reference.subtract(matrixA, matrixB)[row], algebra.subtract(matrixA, matrixB)[row], 1.0e-12);
      assertArrayEquals(reference.scale(matrixA, -1.0)[row], algebra.scale(matrixA, -1.0)[row], 1.0e-12);
      assertArrayEquals(reference.multiply(matrixA, matrixB)[row], algebra.multiply(matrixA, matrixB)[row], 1.0e-10);
      assertArrayEquals(reference.submatrix(matrixB, 0, 2, 1, 2)[row], algebra.submatrix(matrixB, 0, 2, 1, 2)[row],
          1.0e-12);
    }
  }
}
