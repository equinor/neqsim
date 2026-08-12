package neqsim.process.equipment.pipeline.twophasepipe.numerics;

/**
 * Local backward-Euler solver for conservative pairwise interphase momentum exchange.
 *
 * <p>
 * For active phases {@code i}, the solver advances velocity with the symmetric system
 * </p>
 *
 * <pre>
 * (m_i + dt sum_j b_ij) u_i - dt sum_j b_ij u_j = p_i,
 * </pre>
 *
 * <p>
 * where {@code m_i} is mass per pipe length, {@code p_i} is momentum per pipe length, and {@code b_ij = b_ji >= 0}.
 * Summing the rows proves conservation of total active-phase momentum. The symmetric non-negative coupling also makes
 * kinetic energy non-increasing.
 * </p>
 */
public final class StiffInterphaseMomentumSolver {
  private static final double SYMMETRY_TOLERANCE = 1.0e-12;

  private StiffInterphaseMomentumSolver() {
  }

  /**
   * Advance phase momenta through one local implicit exchange step.
   *
   * <p>
   * A phase with exactly zero mass is removed from the active matrix. No mass floor is introduced and no phase
   * inventory is created.
   * </p>
   *
   * @param masses phase masses per pipe length in kg/m
   * @param momenta phase momenta per pipe length in kg/s
   * @param pairCoefficients symmetric pair coefficients in kg/(m s)
   * @param timeStep time step in s
   * @return updated phase momenta in kg/s
   * @throws IllegalArgumentException if dimensions disagree or an input is invalid
   * @throws IllegalStateException if the local system cannot be solved dissipatively
   */
  public static double[] solve(double[] masses, double[] momenta, double[][] pairCoefficients, double timeStep) {
    validateInputs(masses, momenta, pairCoefficients, timeStep);
    double[] result = momenta.clone();
    if (timeStep == 0.0) {
      return result;
    }

    int[] active = new int[masses.length];
    int activeCount = 0;
    for (int phase = 0; phase < masses.length; phase++) {
      if (masses[phase] > 0.0) {
        active[activeCount++] = phase;
      } else {
        result[phase] = 0.0;
      }
    }
    if (activeCount == 0) {
      return result;
    }

    double[][] matrix = new double[activeCount][activeCount];
    double[] rightHandSide = new double[activeCount];
    for (int row = 0; row < activeCount; row++) {
      int phase = active[row];
      matrix[row][row] = masses[phase];
      rightHandSide[row] = momenta[phase];
      for (int column = 0; column < activeCount; column++) {
        if (row == column) {
          continue;
        }
        int otherPhase = active[column];
        double coupling = timeStep * pairCoefficients[phase][otherPhase];
        matrix[row][row] += coupling;
        matrix[row][column] -= coupling;
      }
    }

    double[] velocities = solveLinearSystem(matrix, rightHandSide);
    double initialMomentum = 0.0;
    double finalMomentum = 0.0;
    double initialKineticEnergy = 0.0;
    double finalKineticEnergy = 0.0;
    int correctionPhase = active[0];
    for (int row = 0; row < activeCount; row++) {
      int phase = active[row];
      result[phase] = masses[phase] * velocities[row];
      initialMomentum += momenta[phase];
      finalMomentum += result[phase];
      initialKineticEnergy += 0.5 * momenta[phase] * momenta[phase] / masses[phase];
      finalKineticEnergy += 0.5 * result[phase] * result[phase] / masses[phase];
      if (masses[phase] > masses[correctionPhase]) {
        correctionPhase = phase;
      }
    }

    result[correctionPhase] += initialMomentum - finalMomentum;
    finalKineticEnergy = kineticEnergy(masses, result);
    double energyTolerance = 5.0e-13 * Math.max(1.0, initialKineticEnergy);
    if (finalKineticEnergy > initialKineticEnergy + energyTolerance) {
      throw new IllegalStateException("Implicit interphase momentum solve increased kinetic energy");
    }
    return result;
  }

  private static void validateInputs(double[] masses, double[] momenta, double[][] pairCoefficients, double timeStep) {
    if (masses == null || momenta == null || pairCoefficients == null || masses.length == 0
        || masses.length != momenta.length || pairCoefficients.length != masses.length) {
      throw new IllegalArgumentException("Mass, momentum, and pair-coefficient dimensions must agree");
    }
    if (!Double.isFinite(timeStep) || timeStep < 0.0) {
      throw new IllegalArgumentException("Time step must be finite and non-negative");
    }
    for (int phase = 0; phase < masses.length; phase++) {
      if (!Double.isFinite(masses[phase]) || masses[phase] < 0.0 || !Double.isFinite(momenta[phase])) {
        throw new IllegalArgumentException("Phase masses and momenta must be finite and masses non-negative");
      }
      if (pairCoefficients[phase] == null || pairCoefficients[phase].length != masses.length) {
        throw new IllegalArgumentException("Pair-coefficient matrix must be square");
      }
    }
    for (int phase = 0; phase < masses.length; phase++) {
      for (int otherPhase = 0; otherPhase < masses.length; otherPhase++) {
        double coefficient = pairCoefficients[phase][otherPhase];
        if (!Double.isFinite(coefficient) || coefficient < 0.0) {
          throw new IllegalArgumentException("Pair coefficients must be finite and non-negative");
        }
        if (phase == otherPhase && coefficient != 0.0) {
          throw new IllegalArgumentException("Pair-coefficient diagonal must be zero");
        }
        double reverse = pairCoefficients[otherPhase][phase];
        double tolerance = SYMMETRY_TOLERANCE * Math.max(1.0, Math.max(coefficient, reverse));
        if (!Double.isFinite(reverse) || Math.abs(coefficient - reverse) > tolerance) {
          throw new IllegalArgumentException("Pair coefficients must be symmetric");
        }
      }
    }
  }

  private static double[] solveLinearSystem(double[][] matrix, double[] rightHandSide) {
    int size = rightHandSide.length;
    for (int pivotColumn = 0; pivotColumn < size; pivotColumn++) {
      int pivotRow = pivotColumn;
      for (int row = pivotColumn + 1; row < size; row++) {
        if (Math.abs(matrix[row][pivotColumn]) > Math.abs(matrix[pivotRow][pivotColumn])) {
          pivotRow = row;
        }
      }
      if (matrix[pivotRow][pivotColumn] <= 0.0) {
        throw new IllegalStateException("Interphase momentum matrix is not positive definite");
      }
      if (pivotRow != pivotColumn) {
        double[] temporaryRow = matrix[pivotColumn];
        matrix[pivotColumn] = matrix[pivotRow];
        matrix[pivotRow] = temporaryRow;
        double temporaryValue = rightHandSide[pivotColumn];
        rightHandSide[pivotColumn] = rightHandSide[pivotRow];
        rightHandSide[pivotRow] = temporaryValue;
      }

      for (int row = pivotColumn + 1; row < size; row++) {
        double factor = matrix[row][pivotColumn] / matrix[pivotColumn][pivotColumn];
        for (int column = pivotColumn; column < size; column++) {
          matrix[row][column] -= factor * matrix[pivotColumn][column];
        }
        rightHandSide[row] -= factor * rightHandSide[pivotColumn];
      }
    }

    double[] solution = new double[size];
    for (int row = size - 1; row >= 0; row--) {
      double value = rightHandSide[row];
      for (int column = row + 1; column < size; column++) {
        value -= matrix[row][column] * solution[column];
      }
      solution[row] = value / matrix[row][row];
    }
    return solution;
  }

  private static double kineticEnergy(double[] masses, double[] momenta) {
    double energy = 0.0;
    for (int phase = 0; phase < masses.length; phase++) {
      if (masses[phase] > 0.0) {
        energy += 0.5 * momenta[phase] * momenta[phase] / masses[phase];
      }
    }
    return energy;
  }
}
