package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import Jama.Matrix;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link PedersenPlusModelSolver}.
 *
 * <p>
 * The expected coefficients were captured from the implementation that used {@code Jama.Matrix} directly, before it was
 * migrated to {@code LinearAlgebraOperations}. Both paths run the same JAMA LU solve, so the values must match exactly.
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
  void legacyAbAssemblyMatchesJama() {
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
    Matrix expectedJacobian = new Matrix(new double[][] {{firstSum, massSum}, {secondSum, massDerivativeSum}});
    Matrix expectedResidual = new Matrix(new double[] {firstResidual, secondResidual}, 2);

    solver.setJac();
    solver.setfvec();
    assertArrayEquals(expectedJacobian.getArray()[0], solver.Jac[0], 0.0);
    assertArrayEquals(expectedJacobian.getArray()[1], solver.Jac[1], 0.0);
    assertArrayEquals(expectedResidual.getColumnPackedCopy(), solver.fvec, 0.0);
    assertArrayEquals(characterization.getPlusCoefs(), solver.sol, 0.0);
    assertEquals(expectedResidual.norm2(), new JamaLinearAlgebra().euclideanNorm(solver.fvec), 1.0e-12);
  }

  /**
   * Verifies the original LU and QR solves and damped iterations on the existing TBP fractions.
   *
   * @param fractionCount number of existing TBP fractions to fit
   */
  @ParameterizedTest
  @ValueSource(ints = {2, 4})
  void legacyAbcdStepMatchesJama(int fractionCount) {
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
    Matrix jacobian = new Matrix(solver.Jac);
    Matrix residual = new Matrix(solver.fvec, solver.fvec.length);
    double[] expectedStep = jacobian.solve(residual).getColumnPackedCopy();
    JamaLinearAlgebra algebra = new JamaLinearAlgebra();
    double[] actualStep = fractionCount == 2 ? algebra.solve(solver.Jac, solver.fvec)
        : algebra.solveLeastSquares(solver.Jac, solver.fvec);
    assertArrayEquals(expectedStep, actualStep, 0.0);
    double[] initialCoefficients = solver.sol.clone();
    Matrix expectedSolution = new Matrix(initialCoefficients, initialCoefficients.length);
    Matrix expectedDelta;
    int expectedIterations = 0;
    do {
      expectedIterations++;
      solver.setJac();
      solver.setfvec();
      expectedDelta = new Matrix(solver.Jac).solve(new Matrix(solver.fvec, solver.fvec.length));
      expectedSolution.minusEquals(expectedDelta.times(0.5));
      characterization.setCoefs(expectedSolution.getColumnPackedCopy());
    } while ((expectedDelta.norm2() / expectedSolution.norm2() < 1.0e-6 || expectedIterations < 15)
        && expectedIterations < 50);

    characterization.setCoefs(initialCoefficients);
    NewtonSolveABCD migratedSolver = new NewtonSolveABCD(system, characterization);
    migratedSolver.solve();
    assertEquals(expectedIterations, migratedSolver.iter);
    assertArrayEquals(expectedSolution.getColumnPackedCopy(), migratedSolver.sol, 1.0e-14);
    assertArrayEquals(migratedSolver.sol, characterization.coefs, 0.0);
    assertNotSame(migratedSolver.sol, characterization.coefs);
  }

  /**
   * Verifies that the legacy CD-plus step uses the same square LU system and does not mutate its inputs.
   */
  @Test
  void legacyCdPlusStepMatchesJama() {
    SystemInterface system = createFluid(11.0, 0.290, 0.82);
    TBPCharacterize characterization = new TBPCharacterize(system);
    NewtonSolveCDplus solver = new NewtonSolveCDplus(system, characterization);
    solver.setJac();
    solver.setfvec();
    Matrix jacobian = new Matrix(solver.Jac).copy();
    Matrix residual = new Matrix(solver.fvec, solver.fvec.length);
    double[] expectedStep = jacobian.solve(residual).getColumnPackedCopy();
    double[] actualStep = new JamaLinearAlgebra().solve(solver.Jac, solver.fvec);
    assertArrayEquals(expectedStep, actualStep, 0.0);
    assertArrayEquals(residual.getColumnPackedCopy(), solver.fvec, 0.0);
    for (int row = 0; row < solver.Jac.length; row++) {
      assertArrayEquals(jacobian.getArray()[row], solver.Jac[row], 0.0);
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
