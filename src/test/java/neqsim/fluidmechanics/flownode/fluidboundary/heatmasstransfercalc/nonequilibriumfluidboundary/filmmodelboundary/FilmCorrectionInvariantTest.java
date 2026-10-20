package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysolver.FluidBoundarySolver;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.FluidBoundarySystemInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.fluidboundarynonreactive.FluidBoundarySystemNonReactive;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.ReactiveFluidBoundary;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.mathlib.linearalgebra.ApacheCommonsMathLinearAlgebra;
import neqsim.mathlib.linearalgebra.EjmlMatrixUtils;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.mathlib.linearalgebra.RealEigenDecomposition;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class FilmCorrectionInvariantTest {
  /** Checks that reactive working state exists when the film constructor dispatches to its state initializer. */
  @Test
  void reactiveBoundaryConstructsFromTheExistingFluidFixture() {
    StratifiedFlowNode node = new StratifiedFlowNode(fluid(), new PipeData(0.1, 0.00025));
    node.initFlowCalc();
    ReactiveNewtonProbe boundary = new ReactiveNewtonProbe(node);
    boundary.assertWorkingArrays();
    new ReactiveNewtonProbe(fluid()).assertWorkingArrays();
    assertEquals(node.getBulkSystem().getNumberOfComponents(), boundary.calcFluxes().length);
  }

  /**
   * Compares actual reactive array equations and damping against the existing film/Ejml references.
   *
   * @throws IOException if working arrays cannot be serialized
   * @throws ClassNotFoundException if working arrays cannot be restored
   */
  @Test
  void reactiveArrayNewtonStepMatchesTheExistingFilmEquations() throws IOException, ClassNotFoundException {
    StratifiedFlowNode node = new StratifiedFlowNode(fluid(), new PipeData(0.1, 0.00025));
    node.initFlowCalc();
    ReactiveNewtonProbe reactive = new ReactiveNewtonProbe(node);
    reactive.setHeatTransferCalc(false);
    reactive.init();
    reactive.setuMassTrans();
    reactive.setfvecMassTrans2();
    reactive.setJacMassTrans2();

    NewtonFilmProbe reference = new NewtonFilmProbe(node);
    reference.setHeatTransferCalc(false);
    reference.init();
    reference.setuMassTrans();
    reference.setfvecMassTrans2();
    reference.setJacMassTrans2();
    reactive.assertAssemblyAndStep(reference.expectedJacobian, reference.expectedResidual);
  }

  /**
   * Exposes the legacy reactive solver on the existing real-fluid fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class ReactiveNewtonProbe extends ReactiveFluidBoundary {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the reactive boundary for a configured flow node.
     *
     * @param node real stratified flow node
     */
    private ReactiveNewtonProbe(FlowNodeInterface node) {
      super(node);
    }

    /**
     * Creates the reactive boundary directly from the existing fluid fixture.
     *
     * @param system real thermodynamic fluid
     */
    private ReactiveNewtonProbe(SystemInterface system) {
      super(system);
    }

    /** Checks the dimensions and ownership of all primitive reactive working arrays. */
    private void assertWorkingArrays() {
      assertEquals(neq, Jac.length);
      assertEquals(neq, Jac[0].length);
      assertEquals(neq, fvec.length);
      assertEquals(neq, dx.length);
      assertEquals(neq, uMassTrans.length);
      assertEquals(neq, Xgij.length);
      assertEquals(4, Xgij[0].length);
      assertNotSame(uMassTrans, uMassTransold);
      assertArrayEquals(uMassTrans, uMassTransold, 0.0);
    }

    /**
     * Compares the assembled equations, direct solve and fixed damping before round-tripping array state.
     *
     * @param referenceJacobian Jacobian assembled by the established nonreactive film on the same fixture
     * @param referenceResidual residual assembled by the established nonreactive film
     * @throws IOException if writing arrays fails
     * @throws ClassNotFoundException if reading arrays fails
     */
    private void assertAssemblyAndStep(SimpleMatrix referenceJacobian, SimpleMatrix referenceResidual)
        throws IOException, ClassNotFoundException {
      for (int row = 0; row < neq; row++) {
        assertEquals(referenceResidual.get(row, 0), fvec[row],
            Math.max(1.0e-12, Math.abs(referenceResidual.get(row, 0)) * 1.0e-12));
        for (int column = 0; column < neq; column++) {
          double expected = referenceJacobian.get(row, column);
          assertEquals(expected, Jac[row][column], Math.max(1.0e-12, Math.abs(expected) * 1.0e-12));
        }
      }

      SimpleMatrix initialJacobian = new SimpleMatrix(Jac).copy();
      double[] initialResidual = fvec.clone();
      double[] initialState = uMassTrans.clone();
      dx = solveReactiveJacobianStep();
      for (double value : dx) {
        assertTrue(Double.isFinite(value), "The real-fluid reactive Newton step must be finite");
      }
      assertLinearResidual(Jac, initialResidual, dx);
      double[] expectedState = initialState.clone();
      for (int row = 0; row < neq; row++) {
        expectedState[row] -= 0.1 * dx[row];
      }
      assertMatrix(initialJacobian, new SimpleMatrix(Jac));
      assertArrayEquals(initialResidual, fvec, 0.0);
      applyReactiveNewtonStep(dx);
      assertArrayEquals(expectedState, uMassTrans, 0.0);

      for (double[] values : new double[][] {dx, fvec, uMassTrans, uMassTransold}) {
        for (double scale : new double[] {1.0e-100, 1.0, 1.0e100}) {
          double[] scaled = values.clone();
          for (int component = 0; component < scaled.length; component++) {
            scaled[component] *= scale;
          }
          double referenceNorm = new SimpleMatrix(scaled.length, 1, true, scaled).normF();
          assertEquals(referenceNorm, reactiveVectorNorm(scaled),
              Math.max(1.0e-300, Math.abs(referenceNorm) * 1.0e-12));
        }
      }

      Object[] state = {Jac, fvec, dx, uMassTrans, uMassTransold, Xgij};
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(state);
      }
      Object[] restored;
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        restored = (Object[]) input.readObject();
      }
      assertMatrix(new SimpleMatrix(Jac), new SimpleMatrix((double[][]) restored[0]));
      assertMatrix(new SimpleMatrix(Xgij), new SimpleMatrix((double[][]) restored[5]));
      for (int index = 1; index < 5; index++) {
        assertNotSame(state[index], restored[index]);
        assertArrayEquals((double[]) state[index], (double[]) restored[index], 0.0);
      }
    }
  }

  /**
   * Compares Newton steps and damping on the existing fluid fixture without replacing the production solver.
   *
   * @throws IOException if working-state serialization fails
   * @throws ClassNotFoundException if a serialized working-matrix class cannot be resolved
   */
  @Test
  void filmNewtonStepsSatisfyEquationsAndPreserveWorkingStateOwnership() throws IOException, ClassNotFoundException {
    StratifiedFlowNode node = new StratifiedFlowNode(fluid(), new PipeData(0.1, 0.00025));
    node.initFlowCalc();
    NewtonFilmProbe film = new NewtonFilmProbe(node);
    film.setHeatTransferCalc(false);
    film.setMassTransferCalc(true);
    film.checkWorkingStateOwnership();
    film.checkWorkingStateSerialization();

    film.massTransSolve();

    assertTrue(film.successfulUpdates > 0, "The fixture must exercise successful Newton updates");
    film.assertSpectralCorrectionMatchesEjml();
  }

  /**
   * Observes actual film equations and updates for comparison with direct Ejml algebra.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class NewtonFilmProbe extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;
    private int assembledSystems;
    private int successfulUpdates;
    private SimpleMatrix expectedJacobian;
    private SimpleMatrix expectedResidual;
    private SimpleMatrix expectedState;

    /** {@inheritDoc} */
    @Override
    public void setfvecMassTrans2() {
      super.setfvecMassTrans2();
      assertMassTransferResidualsMatchEjml();
    }

    /**
     * Creates a film using the real flow-node transport correlations.
     *
     * @param node initialized stratified flow node
     */
    private NewtonFilmProbe(FlowNodeInterface node) {
      super(node);
    }

    /** Compares both phase corrections across scaled real fluxes against the previous complete Ejml expression. */
    private void assertSpectralCorrectionMatchesEjml() {
      double[] solvedFlux = nFlux.getData().clone();
      for (double scale : new double[] {0.0, 1.0e-12, 1.0, -1.0, 1000.0}) {
        for (int component = 0; component < solvedFlux.length; component++) {
          nFlux.set(component, 0, solvedFlux[component] * scale);
        }
        for (int phase = 0; phase < 2; phase++) {
          calcPhiMatrix(phase);
          double spectralScale = 0.0;
          for (double[] row : phiMatrix) {
            for (double value : row) {
              spectralScale = Math.max(spectralScale, Math.abs(value));
            }
          }
          if (spectralScale == 0.0) {
            spectralScale = 1.0;
          }
          LinearAlgebraOperations algebra = new ApacheCommonsMathLinearAlgebra();
          RealEigenDecomposition reference = algebra.eigenDecomposition(algebra.scale(phiMatrix, 1.0 / spectralScale));
          double[] eigenvalues = reference.getEigenvalues();
          for (int index = 0; index < eigenvalues.length; index++) {
            eigenvalues[index] *= spectralScale;
          }
          SimpleMatrix diagonal = new SimpleMatrix(eigenvalues.length, eigenvalues.length);
          for (int index = 0; index < eigenvalues.length; index++) {
            double value = eigenvalues[index];
            double correction = Math.abs(value) < 1.0e-6 ? 1.0 + value / 2.0 + value * value / 12.0
                : value > 0.0 ? value / -Math.expm1(-value) : value * Math.exp(value) / Math.expm1(value);
            diagonal.set(index, index, correction);
          }
          SimpleMatrix vectors = new SimpleMatrix(reference.getEigenvectors());
          SimpleMatrix expected = vectors.mult(diagonal.mult(vectors.invert()));
          calcRedPhiMatrix(phase);
          calcRedCorrectionMatrix(phase);
          calcCorrectionMatrix(phase);
          double residual = expected.minus(new SimpleMatrix(rateCorrectionMatrix[phase])).normF();
          assertTrue(residual <= 1.0e-10 * Math.max(1.0, expected.normF()),
              "Finite-flux correction must match Ejml for phase " + phase + ", flux scale " + scale + ": " + residual
                  + ", reference norm " + expected.normF() + ", reference eigenvalues "
                  + java.util.Arrays.toString(eigenvalues) + ", actual eigenvalues "
                  + java.util.Arrays.toString(redPhiMatrix) + ", phi " + java.util.Arrays.deepToString(phiMatrix));
        }
      }
      for (int component = 0; component < solvedFlux.length; component++) {
        nFlux.set(component, 0, solvedFlux[component]);
      }
    }

    /**
     * Checks state shape, independent initial copies and the live public coefficient-array contract.
     */
    private void checkWorkingStateOwnership() {
      assertEquals(neq, uMassTrans.getNumRows());
      assertEquals(1, uMassTrans.getNumCols());
      assertEquals(neq, Xgij.getNumRows());
      assertEquals(4, Xgij.getNumCols());
      assertNotSame(uMassTrans.getData(), uMassTransold.getData());
      assertArrayEquals(uMassTrans.getData(), uMassTransold.getData(), 0.0);
      NewtonFilmProbe cloned = (NewtonFilmProbe) clone();
      assertSame(Jac, cloned.Jac);
      assertSame(fvec, cloned.fvec);
      assertSame(uMassTrans, cloned.uMassTrans);
      assertSame(uMassTransold, cloned.uMassTransold);
      assertSame(Xgij, cloned.Xgij);
      assertNotSame(nFlux, cloned.nFlux);
      assertArrayEquals(nFlux.getData(), cloned.nFlux.getData(), 0.0);
      double[][][] snapshot = getMassTransferCoefficientArray();
      double original = snapshot[0][0][0];
      snapshot[0][0][0] = original + 1.0;
      assertEquals(original, getMassTransferCoefficientArray()[0][0][0], 0.0);
    }

    /**
     * Round-trips the new working matrices without serializing the surrounding flow-node graph.
     *
     * @throws IOException if writing or reading matrix state fails
     * @throws ClassNotFoundException if a serialized working-matrix class cannot be resolved
     */
    private void checkWorkingStateSerialization() throws IOException, ClassNotFoundException {
      DMatrixRMaj[] matrices = {Jac, fvec, uMassTrans, uMassTransold, Xgij, nFlux};
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(matrices);
      }
      DMatrixRMaj[] restored;
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        restored = (DMatrixRMaj[]) input.readObject();
      }
      for (int index = 0; index < matrices.length; index++) {
        assertEquals(matrices[index].getNumRows(), restored[index].getNumRows());
        assertEquals(matrices[index].getNumCols(), restored[index].getNumCols());
        assertArrayEquals(matrices[index].getData(), restored[index].getData(), 0.0);
        assertNotSame(matrices[index].getData(), restored[index].getData());
      }
      assertNotSame(restored[2].getData(), restored[3].getData());
      assertNotSame(restored[2].getData(), restored[5].getData());
    }

    /**
     * Checks the production residual entries against the former Ejml vector/matrix equations.
     */
    private void assertMassTransferResidualsMatchEjml() {
      int componentCount = bulkSystem.getPhases()[0].getNumberOfComponents();
      SimpleMatrix dx = new SimpleMatrix(1, componentCount - 1);
      SimpleMatrix dy = new SimpleMatrix(1, componentCount - 1);
      SimpleMatrix x = new SimpleMatrix(1, componentCount - 1);
      SimpleMatrix y = new SimpleMatrix(1, componentCount - 1);
      for (int component = 0; component < componentCount - 1; component++) {
        dx.set(0, component, bulkSystem.getPhases()[1].getComponent(component).getx()
            - interphaseSystem.getPhases()[1].getComponent(component).getx());
        dy.set(0, component, bulkSystem.getPhases()[0].getComponent(component).getx()
            - interphaseSystem.getPhases()[0].getComponent(component).getx());
        x.set(0, component, bulkSystem.getPhases()[1].getComponent(component).getx());
        y.set(0, component, bulkSystem.getPhases()[0].getComponent(component).getx());
      }
      SimpleMatrix fluxX = new SimpleMatrix(totalMassTransferCoefficientMatrix[1]).mult(dx.transpose()).scale(
          bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() / bulkSystem.getPhases()[1].getMolarMass());
      SimpleMatrix fluxY = new SimpleMatrix(totalMassTransferCoefficientMatrix[0]).mult(dy.transpose()).scale(
          bulkSystem.getPhases()[0].getPhysicalProperties().getDensity() / bulkSystem.getPhases()[0].getMolarMass());
      SimpleMatrix nFluxEjml = asEjml(nFlux);
      SimpleMatrix errX = nFluxEjml.extractMatrix(0, componentCount - 1, 0, 1).plus(fluxX)
          .minus(x.transpose().scale(totalFlux));
      SimpleMatrix errY = nFluxEjml.extractMatrix(0, componentCount - 1, 0, 1).minus(fluxY)
          .minus(y.transpose().scale(totalFlux));
      for (int component = 0; component < componentCount - 1; component++) {
        assertEquals(errX.get(component, 0), fvec.get(componentCount + 2 + component, 0),
            Math.max(1.0e-12, Math.abs(errX.get(component, 0)) * 1.0e-12));
        assertEquals(errY.get(component, 0), fvec.get(2 * componentCount + 1 + component, 0),
            Math.max(1.0e-12, Math.abs(errY.get(component, 0)) * 1.0e-12));
      }
    }

    /** {@inheritDoc} */
    @Override
    public void setJacMassTrans2() {
      super.setJacMassTrans2();
      assembledSystems++;
      expectedJacobian = asEjml(Jac);
      expectedResidual = asEjml(fvec);
      expectedState = asEjml(uMassTrans);

    }

    /** {@inheritDoc} */
    @Override
    public void updateMassTrans() {
      assertEquals(0.0, expectedJacobian.minus(asEjml(Jac)).normF(), 0.0);
      assertEquals(0.0, expectedResidual.minus(asEjml(fvec)).normF(), 0.0);
      // These film systems can be nearly singular: different valid LU solves can differ along a null direction.
      // Qualify the computed step against the assembled equations, rather than a backend-specific correction vector.
      assertLinearResidual(EjmlMatrixUtils.toArray(expectedJacobian),
          expectedResidual.transpose().getDDRM().getData().clone(), dx.getData());
      SimpleMatrix dampedState = expectedState
          .minus(asEjml(dx).scale((assembledSystems + 1.0) / (assembledSystems + 11.0)));
      assertArrayEquals(dampedState.transpose().getDDRM().getData().clone(), uMassTrans.getData(), 1.0e-10);
      double expectedNorm = asEjml(dx).normF();
      assertEquals(expectedNorm, NormOps_DDRM.normP2(dx), 1.0e-12 * Math.max(1.0, expectedNorm));
      successfulUpdates++;
      super.updateMassTrans();
    }

    /**
     * Copies a dense matrix without changing row and column orientation.
     *
     * @param matrix EJML matrix to copy
     * @return independent Ejml matrix for the reference calculation
     */
    private static SimpleMatrix asEjml(DMatrixRMaj matrix) {
      double[][] values = new double[matrix.getNumRows()][matrix.getNumCols()];
      for (int row = 0; row < matrix.getNumRows(); row++) {
        for (int column = 0; column < matrix.getNumCols(); column++) {
          values[row][column] = matrix.get(row, column);
        }
      }
      return new SimpleMatrix(values);
    }
  }

  /**
   * Compares real finite-volume boundary profiles with dense Ejml solves of the same tridiagonal equations.
   */
  @Test
  void finiteVolumeProfilesMatchEjmlAndRetainIndependentColumns() {
    KrishnaStandartFilmModel film = new KrishnaStandartFilmModel(fluid());
    FluidBoundarySystemNonReactive boundary = new FluidBoundarySystemNonReactive(film);
    boundary.createSystem();
    BoundarySolverProbe solver = new BoundarySolverProbe(boundary);
    solver.initMatrix();
    DMatrixRMaj[] initialProfiles = solver.profiles();
    double[][] expected = new double[initialProfiles.length][];
    for (int component = 0; component < initialProfiles.length; component++) {
      solver.setComponentConservationMatrix(component);
      expected[component] = solver.referenceProfile();
      assertEquals(boundary.getNumberOfNodes(), initialProfiles[component].getNumRows());
      assertEquals(1, initialProfiles[component].getNumCols());
      for (int previous = 0; previous < component; previous++) {
        assertNotSame(initialProfiles[previous].getData(), initialProfiles[component].getData());
      }
    }

    solver.solve();

    DMatrixRMaj[] solvedProfiles = solver.profiles();
    for (int component = 0; component < solvedProfiles.length; component++) {
      assertArrayEquals(expected[component], solvedProfiles[component].getData(), 1.0e-12);
      SimpleMatrix ejml = new SimpleMatrix(solvedProfiles[component]);
      assertEquals(org.ejml.dense.row.NormOps_DDRM.normP1(ejml.getDDRM()),
          NormOps_DDRM.normP1(solvedProfiles[component]), 0.0);
      assertNotSame(initialProfiles[component].getData(), solvedProfiles[component].getData());
      DMatrixRMaj copiedProfile = solvedProfiles[component].copy();
      copiedProfile.zero();
      assertArrayEquals(expected[component], solvedProfiles[component].getData(), 1.0e-12);
      assertNotSame(copiedProfile.getData(), solvedProfiles[component].getData());
      double expectedFlux = boundary.getNode(0).getBulkSystem().getPhase(1).getPhysicalProperties()
          .getEffectiveDiffusionCoefficient(component)
          * (boundary.getNode(0).getBulkSystem().getPhase(1).getComponent(component).getx()
              - boundary.getNode(1).getBulkSystem().getPhase(1).getComponent(component).getx())
          / boundary.getNodeLength();
      assertEquals(expectedFlux, solver.getMolarFlux(component), 0.0);
    }
  }

  /**
   * Exposes actual finite-volume coefficients and stored profiles for migration-contract checks.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class BoundarySolverProbe extends FluidBoundarySolver {
    /**
     * Creates a solver for the real nonreactive boundary fixture.
     *
     * @param boundary initialized finite-volume boundary
     */
    private BoundarySolverProbe(FluidBoundarySystemInterface boundary) {
      super(boundary);
    }

    /**
     * Returns the actual component-profile columns.
     *
     * @return stored component profiles
     */
    private DMatrixRMaj[] profiles() {
      return solMatrix.clone();
    }

    /**
     * Solves the currently assembled tridiagonal system using dense Ejml as an independent algebra check.
     *
     * @return reference component profile
     */
    private double[] referenceProfile() {
      double[][] coefficients = new double[b.length][b.length];
      for (int node = 0; node < b.length; node++) {
        coefficients[node][node] = b[node];
        if (node > 0) {
          coefficients[node][node - 1] = a[node];
        }
        if (node + 1 < b.length) {
          coefficients[node][node + 1] = c[node];
        }
      }
      return new SimpleMatrix(coefficients).solve(new SimpleMatrix(r.length, 1, true, r)).transpose().getDDRM()
          .getData().clone();
    }
  }

  private static SystemInterface fluid() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(1);
    return fluid;
  }

  /**
   * Checks the normwise backward error of a computed step. Film Jacobians can have condition numbers above 1e20, where
   * matching correction vectors from different LU substitution orders is not a meaningful accuracy test.
   *
   * @param matrix coefficient matrix
   * @param rhs right-hand side
   * @param solution computed Newton step
   */
  private static void assertLinearResidual(double[][] matrix, double[] rhs, double[] solution) {
    double matrixNorm = 0.0;
    double rhsNorm = 0.0;
    double solutionNorm = 0.0;
    double residualNorm = 0.0;
    for (double value : solution) {
      assertTrue(Double.isFinite(value));
      solutionNorm = Math.max(solutionNorm, Math.abs(value));
    }
    for (int row = 0; row < matrix.length; row++) {
      double product = 0.0;
      double rowNorm = 0.0;
      for (int column = 0; column < solution.length; column++) {
        product += matrix[row][column] * solution[column];
        rowNorm += Math.abs(matrix[row][column]);
      }
      matrixNorm = Math.max(matrixNorm, rowNorm);
      rhsNorm = Math.max(rhsNorm, Math.abs(rhs[row]));
      residualNorm = Math.max(residualNorm, Math.abs(product - rhs[row]));
    }
    double scale = matrixNorm * solutionNorm + rhsNorm;
    assertTrue(Double.isFinite(residualNorm) && Double.isFinite(scale) && residualNorm <= 1.0e-12 * scale,
        "linear system has backward error " + residualNorm + " at scale " + scale);
  }

  /**
   * Solves an assembled Newton system using an independent partial-pivoting LU reference.
   *
   * @param coefficients coefficient matrix
   * @param rhs right-hand side
   * @return reference solution
   */
  private static SimpleMatrix referenceSolve(SimpleMatrix coefficients, SimpleMatrix rhs) {
    LinearAlgebraOperations algebra = new ApacheCommonsMathLinearAlgebra();
    double[][] matrix = EjmlMatrixUtils.toArray(coefficients);
    SimpleMatrix result = new SimpleMatrix(coefficients.numCols(), rhs.numCols());
    for (int column = 0; column < rhs.numCols(); column++) {
      double[] values = new double[rhs.numRows()];
      for (int row = 0; row < rhs.numRows(); row++) {
        values[row] = rhs.get(row, column);
      }
      double[] solution = algebra.solve(matrix, values);
      for (int row = 0; row < solution.length; row++) {
        result.set(row, column, solution[row]);
      }
    }
    return result;
  }

  private static void assertMatrix(SimpleMatrix expected, SimpleMatrix actual) {
    assertEquals(0.0, expected.minus(actual).normF(), 1e-12);
  }

  private static class Film extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    Film() {
      super(fluid());
      setMassTransferCoefficientArray(1, new double[][] {{2.0, 0.0}, {0.0, 3.0}});
      nonIdealCorrections[1] = new double[][] {{1.5, 0.1}, {0.2, 1.2}};
      rateCorrectionMatrix[1] = EjmlMatrixUtils.toArray(SimpleMatrix.identity(2));
      for (double[] row : binaryMassTransferCoefficient[1]) {
        Arrays.fill(row, 0.01);
      }
    }

    SimpleMatrix evaluate(double flux, boolean thermo, boolean finite) {
      totalFlux = flux;
      useThermodynamicCorrections(thermo, 1);
      useFiniteFluxCorrection(finite, 1);
      calcTotalMassTransferCoefficientArray(1);
      return new SimpleMatrix(totalMassTransferCoefficientMatrix[1]);
    }

    SimpleMatrix calculateMassTransferCoefficients(int phase) {
      calcMassTransferCoefficients(phase);
      return new SimpleMatrix(getMassTransferCoefficientArray(phase));
    }

    SimpleMatrix correctedFlux(double[] flux) {
      nFlux = new DMatrixRMaj(flux.length, 1, true, flux);
      initCorrections(1);
      return new SimpleMatrix(rateCorrectionMatrix[1]);
    }

    double[] calculateFluxTypeCorrection(int phase, int component) {
      calcFluxTypeCorrectionMatrix(phase, component);
      return fluxTypeCorrectionMatrix[phase].clone();
    }

    /**
     * Evaluate the historical scalar modal correction for a prescribed finite-flux matrix.
     *
     * @param matrix finite-flux matrix in the independent-component basis
     * @return the phase-one correction matrix
     */
    SimpleMatrix correctedMatrix(double[][] matrix) {
      phiMatrix = matrix;
      calcRedPhiMatrix(1);
      calcRedCorrectionMatrix(1);
      calcCorrectionMatrix(1);
      return new SimpleMatrix(rateCorrectionMatrix[1]);
    }

  }

  /**
   * Preserve the pre-adapter real-part film correlation when conjugate eigenpairs occur.
   */
  @Test
  void complexConjugateSpectrumPreservesHistoricalRealPartCorrection() {
    Film film = new Film();
    double[][] matrix = {{2.0, -2.0}, {0.5, 2.0}};
    double expected = 2.0 / -Math.expm1(-2.0);
    assertMatrix(SimpleMatrix.identity(2).scale(expected), film.correctedMatrix(matrix));
    assertArrayEquals(new double[] {2.0, 2.0}, film.redPhiMatrix, 1.0e-12);
    assertMatrix(SimpleMatrix.identity(2).scale(expected), film.correctedMatrix(matrix));
  }

  /** Verifies coefficient calculations use the array-based API. */
  @Test
  void arrayCalculationsPreserveCoefficientSnapshots() {
    Film film = new Film();
    film.evaluate(0.0, true, false);
    film.calculateMassTransferCoefficients(1);
    double[][][] snapshot = film.getMassTransferCoefficientArray();
    double original = snapshot[1][0][0];
    snapshot[1][0][0] = original + 1.0;
    assertEquals(original, film.getMassTransferCoefficientArray()[1][0][0], 0.0);
  }

  @Test
  void fluxTypeCorrectionUsesTheIndependentComponentBasis() {
    Film film = new Film();
    int selectedComponent = 1;
    double[] correction = film.calculateFluxTypeCorrection(1, selectedComponent);
    double selectedMoleFraction = film.getBulkSystem().getPhase(1).getComponent(selectedComponent).getx();

    assertEquals(film.getBulkSystem().getPhase(1).getNumberOfComponents() - 1, correction.length);
    for (int component = 0; component < correction.length; component++) {
      assertEquals(component == selectedComponent ? 1.0 / selectedMoleFraction : 0.0, correction[component], 1e-12);
    }
  }

  @Test
  void identityCorrectionAndZeroNetFluxPreserveThermodynamicDrivingForce() {
    Film film = new Film();
    SimpleMatrix expected = new SimpleMatrix(new double[][] {{3.0, 0.2}, {0.6, 3.6}});
    for (double flux : new double[] {0.0, 1e-40, -1e-40, 1e-20, -1e-20, 1.0}) {
      assertMatrix(expected, film.evaluate(flux, true, false));
      assertMatrix(expected, film.evaluate(flux, true, true));
    }
  }

  /**
   * Checks the assembled resistance inverse against raw Ejml on the existing real-fluid film fixture.
   */
  @Test
  void massTransferCoefficientInverseMatchesEjmlReference() {
    Film film = new Film();
    int componentCount = film.getBulkSystem().getPhase(1).getNumberOfComponents();
    int independentComponents = componentCount - 1;
    SimpleMatrix resistance = new SimpleMatrix(independentComponents, independentComponents);
    for (int i = 0; i < independentComponents; i++) {
      double diagonal = 0.0;
      for (int j = 0; j < componentCount; j++) {
        double coefficient = film.binaryMassTransferCoefficient[1][i][j];
        if (i != j) {
          diagonal += film.getBulkSystem().getPhase(1).getComponent(j).getx() / coefficient;
        }
        if (j < independentComponents) {
          resistance.set(i, j, -film.getBulkSystem().getPhase(1).getComponent(i).getx()
              * (1.0 / coefficient - 1.0 / film.binaryMassTransferCoefficient[1][i][independentComponents]));
        }
      }
      resistance.set(i, i, diagonal + film.getBulkSystem().getPhase(1).getComponent(i).getx()
          / film.binaryMassTransferCoefficient[1][i][independentComponents]);
    }
    SimpleMatrix expected = referenceSolve(resistance, SimpleMatrix.identity(independentComponents));

    assertMatrix(expected, film.calculateMassTransferCoefficients(1));
  }

  @Test
  void zeroComponentFluxHasIdentityCorrection() {
    assertMatrix(SimpleMatrix.identity(2), new Film().correctedFlux(new double[] {0.0, 0.0, 0.0}));
  }

  @Test
  void equalDiffusivityCounterdiffusionHasIdentityCorrection() {
    assertMatrix(SimpleMatrix.identity(2), new Film().correctedFlux(new double[] {1.0, -1.0, 0.0}));
  }

  @Test
  void eigenvalueFunctionHasContinuousZeroLimitAndFiniteLargeArguments() {
    Film film = new Film();
    film.redCorrectionMatrix = new double[2];
    for (double z : new double[] {0.0, 1e-12, -1e-12, 1e-7, -1e-7, 1.0, -1.0, 1000.0, -1000.0}) {
      film.redPhiMatrix = new double[] {z, z};
      film.calcRedCorrectionMatrix(1);
      double expected = z == 0.0 ? 1.0 : z > 0.0 ? z / -Math.expm1(-z) : z * Math.exp(z) / Math.expm1(z);
      assertEquals(expected, film.redCorrectionMatrix[0], 1e-12);
    }
  }
}
