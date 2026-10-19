package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ejml.simple.SimpleMatrix;
import neqsim.mathlib.linearalgebra.EjmlMatrixUtils;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link PedersenPlusModelSolver}.
 *
 * <p>
 * The expected coefficients were captured from the implementation that used {@code SimpleMatrix} directly, before it
 * was migrated to {@code LinearAlgebraOperations}. EJML and EJML may round LU and QR operations differently; compare
 * steps within 32 ulps and independently verify the least-squares optimality condition. Assembly and input ownership
 * remain exact contracts.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class PedersenPlusModelSolverTest {
  /**
   * Characterises a C10+ fraction with the Pedersen model and returns the fitted coefficients.
   *
   * @param plusMoles number of moles in the plus fraction
   * @param plusMolarMass plus-fraction molar mass in kg/mol
   * @param plusDensity plus-fraction density in g/cm3
   * @return the four Pedersen coefficients
   */
  private static double[] characterise(double plusMoles, double plusMolarMass, double plusDensity) {
    SystemInterface system = createFluid(plusMoles, plusMolarMass, plusDensity);
    system.getCharacterization().setPlusFractionModel("Pedersen");
    system.getCharacterization().setLumpingModel("PVTlumpingModel");
    system.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(9);
    system.getCharacterization().characterisePlusFraction();
    return system.getCharacterization().getPlusFractionModel().getCoefs();
  }

  /**
   * Creates the existing C10+ regression fluid before characterization.
   *
   * @param plusMoles number of moles in the plus fraction
   * @param plusMolarMass plus-fraction molar mass in kg/mol
   * @param plusDensity plus-fraction density in g/cm3
   * @return the regression fluid
   */
  private static SystemInterface createFluid(double plusMoles, double plusMolarMass, double plusDensity) {
    SystemInterface system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("methane", 51.0);
    system.addComponent("ethane", 1.0);
    system.addComponent("propane", 1.0);
    system.getCharacterization().setTBPModel("PedersenSRK");
    system.addTBPfraction("C6", 1.0, 0.090, 0.70);
    system.addTBPfraction("C7", 1.0, 0.110, 0.73);
    system.addTBPfraction("C8", 1.0, 0.120, 0.76);
    system.addTBPfraction("C9", 1.0, 0.140, 0.79);
    system.addPlusFraction("C10", plusMoles, plusMolarMass, plusDensity);
    return system;
  }

  /**
   * Verifies that the legacy AB solver assembles the original residual and Jacobian on the regression fluid.
   */
  @Test
  void legacyAbAssemblyMatchesEjml() {
    SystemInterface system = createFluid(11.0, 0.290, 0.82);
    TBPCharacterize characterization = new TBPCharacterize(system);
    NewtonSolveAB solver = new NewtonSolveAB(system, characterization);
    double firstSum = 0.0;
    double secondSum = 0.0;
    double massSum = 0.0;
    double massDerivativeSum = 0.0;
    for (int carbon = characterization.getFirstPlusFractionNumber(); carbon < characterization
        .getLastPlusFractionNumber(); carbon++) {
      double fraction = Math.exp(characterization.getPlusCoefs(0) + characterization.getPlusCoefs(1) * carbon);
      firstSum += fraction;
      secondSum += characterization.getPlusCoefs(1) * fraction;
      massSum += (14.0 * carbon - 4.0) * fraction;
      massDerivativeSum += (14.0 * carbon - 4.0) * characterization.getPlusCoefs(1) * fraction;
    }
    double firstResidual = -characterization.getZPlus();
    double secondResidual = -characterization.getMPlus() * characterization.getZPlus();
    for (int carbon = characterization.getFirstPlusFractionNumber(); carbon < characterization
        .getLastPlusFractionNumber(); carbon++) {
      firstResidual += Math.exp(characterization.getPlusCoefs(0) + characterization.getPlusCoefs(1) * carbon);
      secondResidual += Math.exp(characterization.getPlusCoefs(0) + characterization.getPlusCoefs(1) * carbon)
          * (14.0 * carbon - 4.0);
    }
    SimpleMatrix expectedJacobian = new SimpleMatrix(
        new double[][] {{firstSum, massSum}, {secondSum, massDerivativeSum}});
    SimpleMatrix expectedResidual = new SimpleMatrix(2, 1, true, new double[] {firstResidual, secondResidual});

    solver.setJac();
    solver.setfvec();
    assertArrayEquals(EjmlMatrixUtils.toArray(expectedJacobian)[0], solver.Jac[0], 0.0);
    assertArrayEquals(EjmlMatrixUtils.toArray(expectedJacobian)[1], solver.Jac[1], 0.0);
    assertArrayEquals(expectedResidual.transpose().getDDRM().getData().clone(), solver.fvec, 0.0);
    assertArrayEquals(characterization.getPlusCoefs(), solver.sol, 0.0);
    assertEquals(expectedResidual.normF(), new EjmlLinearAlgebra().euclideanNorm(solver.fvec), 1.0e-12);
  }

  /**
   * Verifies the original LU and QR solves and damped iterations on the existing TBP fractions.
   *
   * @param fractionCount number of existing TBP fractions to fit
   */
  @ParameterizedTest
  @ValueSource(ints = {2, 4})
  void legacyAbcdStepMatchesEjml(int fractionCount) {
    SystemInterface system = createFluid(11.0, 0.290, 0.82);
    system.init(0);
    TBPCharacterize characterization = new TBPCharacterize(system);
    characterization.length = fractionCount;
    characterization.firstPlusFractionNumber = 6;
    characterization.TBPfractions = new double[fractionCount];
    characterization.TBPdens = new double[fractionCount];
    for (int fraction = 0; fraction < fractionCount; fraction++) {
      characterization.TBPfractions[fraction] = system.getPhase(0).getComponent(fraction + 4).getz();
      characterization.TBPdens[fraction] = system.getPhase(0).getComponent(fraction + 4).getNormalLiquidDensity();
    }
    NewtonSolveABCD solver = new NewtonSolveABCD(system, characterization);
    solver.setJac();
    solver.setfvec();
    SimpleMatrix jacobian = new SimpleMatrix(solver.Jac);
    SimpleMatrix residual = new SimpleMatrix(solver.fvec.length, 1, true, solver.fvec);
    double[] expectedStep = jacobian.solve(residual).transpose().getDDRM().getData().clone();
    EjmlLinearAlgebra algebra = new EjmlLinearAlgebra();
    double[] actualStep = fractionCount == 2 ? algebra.solve(solver.Jac, solver.fvec)
        : algebra.solveLeastSquares(solver.Jac, solver.fvec);
    assertEquivalentStep(solver.Jac, solver.fvec, expectedStep, actualStep);
    double[] initialCoefficients = solver.sol.clone();
    SimpleMatrix expectedSolution = new SimpleMatrix(initialCoefficients.length, 1, true, initialCoefficients);
    SimpleMatrix expectedDelta;
    int expectedIterations = 0;
    do {
      expectedIterations++;
      solver.setJac();
      solver.setfvec();
      expectedDelta = new SimpleMatrix(solver.Jac).solve(new SimpleMatrix(solver.fvec.length, 1, true, solver.fvec));
      expectedSolution = expectedSolution.minus(expectedDelta.scale(0.5));
      characterization.setCoefs(expectedSolution.transpose().getDDRM().getData().clone());
    } while ((expectedDelta.normF() / expectedSolution.normF() < 1.0e-6 || expectedIterations < 15)
        && expectedIterations < 50);

    characterization.setCoefs(initialCoefficients);
    NewtonSolveABCD migratedSolver = new NewtonSolveABCD(system, characterization);
    migratedSolver.solve();
    assertEquals(expectedIterations, migratedSolver.iter);
    assertArrayEquals(expectedSolution.transpose().getDDRM().getData().clone(), migratedSolver.sol, 1.0e-14);
    assertArrayEquals(migratedSolver.sol, characterization.coefs, 0.0);
    assertNotSame(migratedSolver.sol, characterization.coefs);
  }

  /**
   * Verifies that the legacy CD-plus step uses the same square LU system and does not mutate its inputs.
   */
  @Test
  void legacyCdPlusStepMatchesEjml() {
    SystemInterface system = createFluid(11.0, 0.290, 0.82);
    TBPCharacterize characterization = new TBPCharacterize(system);
    NewtonSolveCDplus solver = new NewtonSolveCDplus(system, characterization);
    solver.setJac();
    solver.setfvec();
    SimpleMatrix jacobian = new SimpleMatrix(solver.Jac).copy();
    SimpleMatrix residual = new SimpleMatrix(solver.fvec.length, 1, true, solver.fvec);
    double[] expectedStep = jacobian.solve(residual).transpose().getDDRM().getData().clone();
    double[] actualStep = new EjmlLinearAlgebra().solve(solver.Jac, solver.fvec);
    assertEquivalentStep(solver.Jac, solver.fvec, expectedStep, actualStep);
    assertArrayEquals(residual.transpose().getDDRM().getData().clone(), solver.fvec, 0.0);
    for (int row = 0; row < solver.Jac.length; row++) {
      assertArrayEquals(EjmlMatrixUtils.toArray(jacobian)[row], solver.Jac[row], 0.0);
    }
  }

  /**
   * Checks backend agreement and the normal-equation residual for square and overdetermined systems.
   *
   * @param matrix coefficient matrix
   * @param rhs right-hand side
   * @param expected direct EJML reference step
   * @param actual EJML step
   */
  private static void assertEquivalentStep(double[][] matrix, double[] rhs, double[] expected, double[] actual) {
    assertEquals(expected.length, actual.length);
    for (int column = 0; column < expected.length; column++) {
      assertEquals(expected[column], actual[column], 32.0 * Math.ulp(Math.max(1.0, Math.abs(expected[column]))),
          "Backend step at column " + column);
    }
    double[] residual = new double[rhs.length];
    double[] scale = new double[rhs.length];
    for (int row = 0; row < matrix.length; row++) {
      residual[row] = -rhs[row];
      scale[row] = Math.abs(rhs[row]);
      for (int column = 0; column < actual.length; column++) {
        double term = matrix[row][column] * actual[column];
        residual[row] += term;
        scale[row] += Math.abs(term);
      }
    }
    for (int column = 0; column < actual.length; column++) {
      double gradient = 0.0;
      double gradientScale = 0.0;
      for (int row = 0; row < matrix.length; row++) {
        gradient += matrix[row][column] * residual[row];
        gradientScale += Math.abs(matrix[row][column]) * scale[row];
      }
      assertTrue(Math.abs(gradient) <= 64.0 * Math.ulp(1.0) * Math.max(1.0, gradientScale),
          "Least-squares optimality at column " + column);
    }
  }

  /**
   * Verifies the coefficients for a typical C10+ fraction within floating-point tolerance.
   */
  @Test
  void typicalPlusFractionCoefficientsAreUnchanged() {
    assertArrayEquals(new double[] {-3.479373356777761, -0.0855476997870545, 0.7282361992342693, 0.029020154394731944},
        characterise(11.0, 0.290, 0.82), 1.0e-14);
  }

  /**
   * Verifies the coefficients for a heavy, dense C10+ fraction within floating-point tolerance.
   */
  @Test
  void heavyPlusFractionCoefficientsAreUnchanged() {
    assertArrayEquals(
        new double[] {-4.3450040345066885, -0.024758815553877978, 0.6014349180683386, 0.08672990444665372},
        characterise(30.0, 0.500, 0.92), 1.0e-14);
  }
}
