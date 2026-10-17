package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysolver.FluidBoundarySolver;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.FluidBoundarySystemInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.fluidboundarynonreactive.FluidBoundarySystemNonReactive;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.ReactiveFluidBoundary;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.ReactiveKrishnaStandartFilmModel;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactor;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
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
   * Compares actual reactive array equations and damping against the existing film/Jama references.
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
    private void assertAssemblyAndStep(Matrix referenceJacobian, Matrix referenceResidual)
        throws IOException, ClassNotFoundException {
      for (int row = 0; row < neq; row++) {
        assertEquals(referenceResidual.get(row, 0), fvec[row],
            Math.max(1.0e-12, Math.abs(referenceResidual.get(row, 0)) * 1.0e-12));
        for (int column = 0; column < neq; column++) {
          double expected = referenceJacobian.get(row, column);
          assertEquals(expected, Jac[row][column], Math.max(1.0e-12, Math.abs(expected) * 1.0e-12));
        }
      }

      Matrix initialJacobian = new Matrix(Jac).copy();
      double[] initialResidual = fvec.clone();
      Matrix expectedStep = new Matrix(Jac).solve(new Matrix(fvec, fvec.length));
      Matrix expectedState = new Matrix(uMassTrans, uMassTrans.length).minus(expectedStep.times(0.1));
      dx = solveReactiveJacobianStep();
      for (double value : dx) {
        assertTrue(Double.isFinite(value), "The real-fluid reactive Newton step must be finite");
      }
      assertArrayEquals(expectedStep.getColumnPackedCopy(), dx, 0.0);
      assertMatrix(initialJacobian, new Matrix(Jac));
      assertArrayEquals(initialResidual, fvec, 0.0);
      applyReactiveNewtonStep(dx);
      assertArrayEquals(expectedState.getColumnPackedCopy(), uMassTrans, 0.0);

      for (double[] values : new double[][] {dx, fvec, uMassTrans, uMassTransold}) {
        for (double scale : new double[] {1.0e-100, 1.0, 1.0e100}) {
          double[] scaled = values.clone();
          for (int component = 0; component < scaled.length; component++) {
            scaled[component] *= scale;
          }
          double referenceNorm = new Matrix(scaled, scaled.length).norm2();
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
      assertMatrix(new Matrix(Jac), new Matrix((double[][]) restored[0]));
      assertMatrix(new Matrix(Xgij), new Matrix((double[][]) restored[5]));
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
  void filmNewtonStepsMatchJamaAndPreserveWorkingStateOwnership() throws IOException, ClassNotFoundException {
    StratifiedFlowNode node = new StratifiedFlowNode(fluid(), new PipeData(0.1, 0.00025));
    node.initFlowCalc();
    NewtonFilmProbe film = new NewtonFilmProbe(node);
    film.setHeatTransferCalc(false);
    film.setMassTransferCalc(true);
    film.checkWorkingStateOwnership();
    film.checkWorkingStateSerialization();

    film.massTransSolve();

    assertTrue(film.successfulUpdates > 0, "The fixture must exercise successful Newton updates");
    assertEquals(film.finiteReferenceSteps, film.successfulUpdates);
    film.assertSpectralCorrectionMatchesJama();
  }

  /**
   * Observes actual film equations and updates for comparison with direct Jama algebra.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class NewtonFilmProbe extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;
    private int assembledSystems;
    private int finiteReferenceSteps;
    private int successfulUpdates;
    private Matrix expectedJacobian;
    private Matrix expectedResidual;
    private Matrix expectedStep;
    private Matrix expectedState;

    /** {@inheritDoc} */
    @Override
    public void setfvecMassTrans2() {
      super.setfvecMassTrans2();
      assertMassTransferResidualsMatchJama();
    }

    /**
     * Creates a film using the real flow-node transport correlations.
     *
     * @param node initialized stratified flow node
     */
    private NewtonFilmProbe(FlowNodeInterface node) {
      super(node);
    }

    /** Compares both phase corrections across scaled real fluxes against the previous complete Jama expression. */
    private void assertSpectralCorrectionMatchesJama() {
      double[] solvedFlux = nFlux.getData().clone();
      for (double scale : new double[] {0.0, 1.0e-12, 1.0, -1.0, 1000.0}) {
        for (int component = 0; component < solvedFlux.length; component++) {
          nFlux.set(component, 0, solvedFlux[component] * scale);
        }
        for (int phase = 0; phase < 2; phase++) {
          calcPhiMatrix(phase);
          Jama.EigenvalueDecomposition reference = new Matrix(phiMatrix).eig();
          double[] eigenvalues = reference.getRealEigenvalues();
          Matrix diagonal = new Matrix(eigenvalues.length, eigenvalues.length);
          for (int index = 0; index < eigenvalues.length; index++) {
            double value = eigenvalues[index];
            double correction = Math.abs(value) < 1.0e-6 ? 1.0 + value / 2.0 + value * value / 12.0
                : value > 0.0 ? value / -Math.expm1(-value) : value * Math.exp(value) / Math.expm1(value);
            diagonal.set(index, index, correction);
          }
          Matrix vectors = reference.getV();
          Matrix expected = vectors.times(diagonal.times(vectors.inverse()));
          calcRedPhiMatrix(phase);
          calcRedCorrectionMatrix(phase);
          calcCorrectionMatrix(phase);
          double residual = expected.minus(new Matrix(rateCorrectionMatrix[phase])).normF();
          assertTrue(residual <= 1.0e-10 * Math.max(1.0, expected.normF()),
              "Finite-flux correction must match Jama for phase " + phase + ", flux scale " + scale + ": " + residual
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
    @SuppressWarnings("deprecation")
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
      Matrix[] exported = getMassTransferCoefficientMatrix();
      assertSame(massTransferCoefficientMatrix, exported);
      Matrix replacement = exported[0].copy();
      exported[0] = replacement;
      assertSame(replacement, massTransferCoefficientMatrix[0]);
      double original = replacement.get(0, 0);
      double[][][] snapshot = getMassTransferCoefficientArray();
      assertNotSame(replacement.getArray(), snapshot[0]);
      snapshot[0][0][0] = original + 1.0;
      assertEquals(original, getMassTransferCoefficientArray()[0][0][0], 0.0);
      replacement.set(0, 0, original + 1.0);
      assertEquals(original + 1.0, getMassTransferCoefficientArray()[0][0][0], 0.0);
      replacement.set(0, 0, original);
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
     * Checks the production residual entries against the former Jama vector/matrix equations.
     */
    private void assertMassTransferResidualsMatchJama() {
      int componentCount = bulkSystem.getPhases()[0].getNumberOfComponents();
      Matrix dx = new Matrix(1, componentCount - 1);
      Matrix dy = new Matrix(1, componentCount - 1);
      Matrix x = new Matrix(1, componentCount - 1);
      Matrix y = new Matrix(1, componentCount - 1);
      for (int component = 0; component < componentCount - 1; component++) {
        dx.set(0, component, bulkSystem.getPhases()[1].getComponent(component).getx()
            - interphaseSystem.getPhases()[1].getComponent(component).getx());
        dy.set(0, component, bulkSystem.getPhases()[0].getComponent(component).getx()
            - interphaseSystem.getPhases()[0].getComponent(component).getx());
        x.set(0, component, bulkSystem.getPhases()[1].getComponent(component).getx());
        y.set(0, component, bulkSystem.getPhases()[0].getComponent(component).getx());
      }
      Matrix fluxX = new Matrix(totalMassTransferCoefficientMatrix[1]).times(dx.transpose()).times(
          bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() / bulkSystem.getPhases()[1].getMolarMass());
      Matrix fluxY = new Matrix(totalMassTransferCoefficientMatrix[0]).times(dy.transpose()).times(
          bulkSystem.getPhases()[0].getPhysicalProperties().getDensity() / bulkSystem.getPhases()[0].getMolarMass());
      Matrix nFluxJama = asJama(nFlux);
      Matrix errX = nFluxJama.getMatrix(0, componentCount - 2, 0, 0).plus(fluxX).minus(x.transpose().times(totalFlux));
      Matrix errY = nFluxJama.getMatrix(0, componentCount - 2, 0, 0).minus(fluxY).minus(y.transpose().times(totalFlux));
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
      expectedJacobian = asJama(Jac);
      expectedResidual = asJama(fvec);
      try {
        expectedStep = expectedJacobian.solve(expectedResidual);
      } catch (RuntimeException exception) {
        expectedStep = null;
        return;
      }
      for (double value : expectedStep.getColumnPackedCopy()) {
        if (!Double.isFinite(value)) {
          return;
        }
      }
      finiteReferenceSteps++;
      expectedState = asJama(uMassTrans)
          .minus(expectedStep.times((assembledSystems + 1.0) / (assembledSystems + 11.0)));
    }

    /** {@inheritDoc} */
    @Override
    public void updateMassTrans() {
      assertEquals(0.0, expectedJacobian.minus(asJama(Jac)).normF(), 0.0);
      assertEquals(0.0, expectedResidual.minus(asJama(fvec)).normF(), 0.0);
      assertArrayEquals(expectedStep.getColumnPackedCopy(), dx.getData(), 1.0e-10, "Newton system " + assembledSystems);
      assertArrayEquals(expectedState.getColumnPackedCopy(), uMassTrans.getData(), 1.0e-10);
      double expectedNorm = expectedStep.norm2();
      assertEquals(expectedNorm, NormOps_DDRM.normP2(dx), 1.0e-12 * Math.max(1.0, expectedNorm));
      successfulUpdates++;
      super.updateMassTrans();
    }

    /**
     * Copies a dense matrix without changing row and column orientation.
     *
     * @param matrix EJML matrix to copy
     * @return independent Jama matrix for the reference calculation
     */
    private static Matrix asJama(DMatrixRMaj matrix) {
      double[][] values = new double[matrix.getNumRows()][matrix.getNumCols()];
      for (int row = 0; row < matrix.getNumRows(); row++) {
        for (int column = 0; column < matrix.getNumCols(); column++) {
          values[row][column] = matrix.get(row, column);
        }
      }
      return new Matrix(values);
    }
  }

  /**
   * Compares real finite-volume boundary profiles with dense Jama solves of the same tridiagonal equations.
   */
  @Test
  void finiteVolumeProfilesMatchJamaAndRetainIndependentColumns() {
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
      Matrix jama = new Matrix(solvedProfiles[component].getData(), boundary.getNumberOfNodes());
      assertEquals(jama.norm1(), NormOps_DDRM.normP1(solvedProfiles[component]), 0.0);
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
     * Solves the currently assembled tridiagonal system using dense Jama as an independent algebra check.
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
      return new Matrix(coefficients).solve(new Matrix(r, r.length)).getColumnPackedCopy();
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

  private static void assertMatrix(Matrix expected, Matrix actual) {
    assertEquals(0.0, expected.minus(actual).normF(), 1e-12);
  }

  private static class Film extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    Film() {
      super(fluid());
      setMassTransferCoefficientArray(1, new double[][] {{2.0, 0.0}, {0.0, 3.0}});
      nonIdealCorrections[1] = new double[][] {{1.5, 0.1}, {0.2, 1.2}};
      rateCorrectionMatrix[1] = Matrix.identity(2, 2).getArray();
      for (double[] row : binaryMassTransferCoefficient[1]) {
        Arrays.fill(row, 0.01);
      }
    }

    Matrix evaluate(double flux, boolean thermo, boolean finite) {
      totalFlux = flux;
      useThermodynamicCorrections(thermo, 1);
      useFiniteFluxCorrection(finite, 1);
      calcTotalMassTransferCoefficientArray(1);
      return new Matrix(totalMassTransferCoefficientMatrix[1]);
    }

    Matrix calculateMassTransferCoefficients(int phase) {
      calcMassTransferCoefficients(phase);
      return new Matrix(getMassTransferCoefficientArray(phase));
    }

    Matrix correctedFlux(double[] flux) {
      nFlux = new DMatrixRMaj(flux.length, 1, true, flux);
      initCorrections(1);
      return new Matrix(rateCorrectionMatrix[1]);
    }

    void setRate(Matrix correction) {
      rateCorrectionMatrix[1] = correction.getArrayCopy();
    }

    double[] calculateFluxTypeCorrection(int phase, int component) {
      calcFluxTypeCorrectionMatrix(phase, component);
      return fluxTypeCorrectionMatrix[phase].clone();
    }

    /**
     * Reports whether compatibility views have been materialized.
     *
     * @return true if a legacy Matrix array exists
     */
    @SuppressWarnings("deprecation")
    boolean hasLegacyCoefficientViews() {
      return massTransferCoefficientMatrix != null;
    }
  }

  /** Verifies array-primary calculations do not allocate legacy Matrix coefficient views. */
  @Test
  void arrayCalculationsDoNotMaterializeLegacyMatrices() {
    Film film = new Film();
    assertFalse(film.hasLegacyCoefficientViews());
    film.evaluate(0.0, true, false);
    film.calculateMassTransferCoefficients(1);
    assertFalse(film.hasLegacyCoefficientViews());
  }

  /**
   * Checks that array access is primary while public Matrix signatures remain explicitly deprecated.
   *
   * @throws NoSuchMethodException if a compatibility or array entry point has been removed
   */
  @Test
  void arrayPrimaryApiRetainsDeprecatedMatrixEntryPoints() throws NoSuchMethodException {
    assertEquals(double[][][].class,
        FluidBoundaryInterface.class.getMethod("getMassTransferCoefficientArray").getReturnType());
    assertFalse(FluidBoundaryInterface.class.getMethod("getMassTransferCoefficientArray").isDefault());
    assertEquals(Matrix[].class,
        FluidBoundaryInterface.class.getMethod("getMassTransferCoefficientMatrix").getReturnType());
    assertTrue(FluidBoundaryInterface.class.getMethod("getMassTransferCoefficientMatrix").isDefault());
    assertTrue(FluidBoundaryInterface.class.getMethod("getMassTransferCoefficientMatrix")
        .isAnnotationPresent(Deprecated.class));
    assertTrue(KrishnaStandartFilmModel.class.getMethod("calcTotalMassTransferCoefficientMatrix", int.class)
        .isAnnotationPresent(Deprecated.class));
    assertTrue(ReactiveKrishnaStandartFilmModel.class.getMethod("calcTotalMassTransferCoefficientMatrix", int.class)
        .isAnnotationPresent(Deprecated.class));
  }

  /**
   * Verifies the deprecated live view remains connected to canonical arrays and survives serialization.
   *
   * @throws IOException if the real-fluid boundary cannot be serialized
   * @throws ClassNotFoundException if the boundary class cannot be restored
   */
  @SuppressWarnings("deprecation")
  @Test
  void arrayCoefficientStoragePreservesLiveViewsAndSerialization() throws IOException, ClassNotFoundException {
    Film film = new Film();
    Matrix[] exported = film.getMassTransferCoefficientMatrix();
    assertSame(exported, film.getMassTransferCoefficientMatrix());
    exported[1].set(0, 1, 0.5);
    assertMatrix(exported[1], film.evaluate(0.0, false, false));
    exported[1] = exported[1].times(2.0);
    assertMatrix(exported[1], film.evaluate(0.0, false, false));

    Matrix recomputed = film.calculateMassTransferCoefficients(1).copy();
    assertSame(exported, film.getMassTransferCoefficientMatrix());
    assertMatrix(recomputed, exported[1]);

    Film cloned = (Film) film.clone();
    assertSame(exported, cloned.getMassTransferCoefficientMatrix());

    Matrix pendingReplacement = exported[1].times(2.0);
    exported[1] = pendingReplacement;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(film);
    }
    Film restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Film) input.readObject();
    }
    assertMatrix(pendingReplacement, new Matrix(restored.getMassTransferCoefficientArray()[1]));
    Matrix[] restoredViews = restored.getMassTransferCoefficientMatrix();
    assertNotSame(exported, restoredViews);
    assertMatrix(pendingReplacement, restoredViews[1]);
    double changedValue = restoredViews[1].get(0, 0) + 1.0;
    restoredViews[1].set(0, 0, changedValue);
    assertEquals(changedValue, restored.getMassTransferCoefficientArray()[1][0][0], 0.0);
    assertMatrix(restoredViews[1], restored.evaluate(0.0, false, false));
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
    Matrix expected = new Matrix(new double[][] {{3.0, 0.2}, {0.6, 3.6}});
    for (double flux : new double[] {0.0, 1e-40, -1e-40, 1e-20, -1e-20, 1.0}) {
      assertMatrix(expected, film.evaluate(flux, true, false));
      assertMatrix(expected, film.evaluate(flux, true, true));
    }
  }

  @Test
  void correctionSwitchesUseOneOrderForNoncommutingMatrices() {
    Film film = new Film();
    Matrix correction = new Matrix(new double[][] {{1.1, 0.2}, {0.3, 0.9}});
    film.setRate(correction);
    Matrix k = film.evaluate(1.0, false, false).copy();
    Matrix thermodynamic = film.evaluate(1.0, true, false).copy();
    assertMatrix(correction.times(k), film.evaluate(1.0, false, true));
    assertMatrix(correction.times(thermodynamic), film.evaluate(1.0, true, true));
  }

  /**
   * Checks the assembled resistance inverse against raw Jama on the existing real-fluid film fixture.
   */
  @Test
  void massTransferCoefficientInverseMatchesJamaReference() {
    Film film = new Film();
    int componentCount = film.getBulkSystem().getPhase(1).getNumberOfComponents();
    int independentComponents = componentCount - 1;
    Matrix resistance = new Matrix(independentComponents, independentComponents);
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
    Matrix expected = resistance.inverse();

    assertMatrix(expected, film.calculateMassTransferCoefficients(1));
  }

  @Test
  void zeroComponentFluxHasIdentityCorrection() {
    assertMatrix(Matrix.identity(2, 2), new Film().correctedFlux(new double[] {0.0, 0.0, 0.0}));
  }

  @Test
  void equalDiffusivityCounterdiffusionHasIdentityCorrection() {
    assertMatrix(Matrix.identity(2, 2), new Film().correctedFlux(new double[] {1.0, -1.0, 0.0}));
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

  private static class ReactiveFilm extends ReactiveKrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    ReactiveFilm() {
      super(fluid());
      setMassTransferCoefficientArray(1, new double[][] {{2.0, 0.4}, {0.5, 3.0}});
      nonIdealCorrections[1] = new double[][] {{1.0, 0.0}, {0.0, 1.0}};
      rateCorrectionMatrix[1] = Matrix.identity(2, 2).getArray();
      enhancementFactor = new EnhancementFactor(this) {
        @Override
        public void calcEnhancementVec(int phase) {
          enhancementVec = new double[] {2.0, 1.0, 1.0};
        }
      };
    }

    @SuppressWarnings("deprecation")
    void checkRepeatedCalculation(boolean thermo, boolean finite, double flux) {
      useThermodynamicCorrections(thermo, 1);
      useFiniteFluxCorrection(finite, 1);
      totalFlux = flux;
      Matrix base = new Matrix(getMassTransferCoefficientArray(1)).copy();
      calcTotalMassTransferCoefficientArray(1);
      assertNotSame(getMassTransferCoefficientArray(1), totalMassTransferCoefficientMatrix[1]);
      assertMatrix(base, new Matrix(getMassTransferCoefficientArray(1)));
      Matrix expected = new Matrix(new double[][] {{4.0, 0.8}, {0.5, 3.0}});
      assertMatrix(expected, new Matrix(totalMassTransferCoefficientMatrix[1]));
      calcTotalMassTransferCoefficientMatrix(1);
      assertMatrix(base, new Matrix(getMassTransferCoefficientArray(1)));
      assertMatrix(expected, new Matrix(totalMassTransferCoefficientMatrix[1]));
    }
  }

  @Test
  void enhancementDoesNotMutateOrCompoundAcrossCorrectionSwitches() {
    for (boolean thermo : new boolean[] {false, true}) {
      for (boolean finite : new boolean[] {false, true}) {
        for (double flux : new double[] {0.0, 1.0}) {
          new ReactiveFilm().checkRepeatedCalculation(thermo, finite, flux);
        }
      }
    }
  }
}
