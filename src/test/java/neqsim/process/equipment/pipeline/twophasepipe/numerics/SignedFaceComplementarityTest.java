package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Conservation, phase-limit and structural tests for signed face complementarity. */
class SignedFaceComplementarityTest {
  @Test
  void solvedFaceReplacementIsConservativeOnNonuniformCells() {
    double[][] rates = new double[2][6];
    DirectionalPhaseFaceFlux[][] faces = {faces(0.4, 1.2, 0.2, 0.3, 0.6, -1.5)};
    UnsplitTransientSolver.Evaluation free = evaluation(rates, faces, new double[] {2.0, 5.0});
    double[][] endpoint = {{0.2, 1.0, 1.0, 0.6, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
    SignedFaceComplementarity.State state = SignedFaceComplementarity.initialize(free, endpoint);
    double[] values = state.getValues();
    values[SignedFaceComplementarity.MASS_FLOW] = 0.25;
    values[SignedFaceComplementarity.ADVECTIVE_MOMENTUM] = 0.75;
    state.setValues(values);

    SignedFaceComplementarity.Evaluation corrected = SignedFaceComplementarity.evaluate(free, endpoint, state);
    double[][] correctedRates = corrected.getConservativeRates();
    assertEquals(0.0, correctedRates[0][0] * 2.0 + correctedRates[1][0] * 5.0, 1.0e-15);
    assertEquals(0.0, correctedRates[0][3] * 2.0 + correctedRates[1][3] * 5.0, 1.0e-15);
    assertEquals(0.25, corrected.getSolvedFaces()[0][0].getSignedMassFlowKgPerSecond(), 0.0);
    assertEquals(0.75, corrected.getSolvedFaces()[0][0].getAdvectiveMomentumN(), 0.0);
    assertEquals(0.0, corrected.getResidual()[SignedFaceComplementarity.ADVECTIVE_MOMENTUM], 1.0e-15);
  }

  @Test
  void absentDonorUsesExactMassAndMomentumRows() {
    DirectionalPhaseFaceFlux[][] faces = {faces(0.4, 1.2, 0.0, 0.3, 0.0, -1.5)};
    UnsplitTransientSolver.Evaluation free = evaluation(new double[2][6], faces, new double[] {1.0, 1.0});
    double[][] endpoint = {{0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
    SignedFaceComplementarity.State state = SignedFaceComplementarity.initialize(free, endpoint);
    assertTrue(state.isConstrained(0, 0));
    double[] values = state.getValues();
    values[SignedFaceComplementarity.MASS_FLOW] = 0.0;
    values[SignedFaceComplementarity.ADVECTIVE_MOMENTUM] = 0.0;
    values[SignedFaceComplementarity.MULTIPLIER] = 0.4;
    state.setValues(values);

    double[] residual = SignedFaceComplementarity.evaluate(free, endpoint, state).getResidual();
    assertEquals(0.0, residual[SignedFaceComplementarity.MASS_FLOW], 0.0);
    assertEquals(0.0, residual[SignedFaceComplementarity.ADVECTIVE_MOMENTUM], 0.0);
    assertEquals(0.0, residual[SignedFaceComplementarity.MULTIPLIER], 0.0);
  }

  @Test
  void presentTraceDonorRelationRemainsNormalized() {
    for (double mass : new double[] {1.0e-3, 1.0e-6, 1.0e-9, 1.0e-12}) {
      double velocity = 3.0;
      double flow = 0.2;
      DirectionalPhaseFaceFlux[][] faces = {faces(flow, flow * velocity, mass, 0.3, mass * velocity, -1.5)};
      UnsplitTransientSolver.Evaluation free = evaluation(new double[2][6], faces, new double[] {1.0, 1.0});
      double[][] endpoint = {{mass, 1.0, 1.0, mass * velocity, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
      SignedFaceComplementarity.State state = SignedFaceComplementarity.initialize(free, endpoint);
      assertEquals(0.0, SignedFaceComplementarity.evaluate(free, endpoint, state)
          .getResidual()[SignedFaceComplementarity.ADVECTIVE_MOMENTUM], 1.0e-15);
    }
  }

  @Test
  void reversingFreeFluxSelectsPresentOppositeDonorAndReleasesConstraint() {
    DirectionalPhaseFaceFlux[][] forwardFaces = {faces(0.4, 1.2, 0.0, 0.3, 0.0, -1.5)};
    double[][] endpoint = {{0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
    SignedFaceComplementarity.State state = SignedFaceComplementarity
        .initialize(evaluation(new double[2][6], forwardFaces, new double[] {1.0, 1.0}), endpoint);
    assertTrue(state.isConstrained(0, 0));
    assertEquals(1, state.getDirection(0, 0));

    DirectionalPhaseFaceFlux[][] reverseFaces = {faces(-0.4, -1.2, 0.0, 0.3, 0.0, -1.5)};
    boolean changed = SignedFaceComplementarity
        .refreshActiveSet(evaluation(new double[2][6], reverseFaces, new double[] {1.0, 1.0}), endpoint, state);

    assertTrue(changed);
    assertEquals(-1, state.getDirection(0, 0));
    assertFalse(state.isConstrained(0, 0));
  }

  @Test
  void structuralScheduleUsesFiftyThreeConflictFreeColors() {
    for (int cellCount : new int[] {16, 24}) {
      boolean[] seen = new boolean[SignedFaceComplementarity.TOTAL_COLOR_COUNT];
      for (int column = 0; column < SignedFaceComplementarity.dimension(cellCount); column++) {
        int color = SignedFaceComplementarity.color(cellCount, column);
        assertTrue(color >= 0 && color < seen.length);
        seen[color] = true;
      }
      for (boolean used : seen) {
        assertTrue(used);
      }
      for (int face = 0; face < cellCount - 1; face++) {
        for (int variable = 0; variable < SignedFaceComplementarity.VARIABLES_PER_FACE; variable++) {
          int left = SignedFaceComplementarity.column(cellCount, face, variable / 3, variable % 3);
          if (face + 1 < cellCount - 1) {
            int right = SignedFaceComplementarity.column(cellCount, face + 1, variable / 3, variable % 3);
            assertNotEquals(SignedFaceComplementarity.color(cellCount, left),
                SignedFaceComplementarity.color(cellCount, right));
          }
        }
      }
    }
  }

  @Test
  void augmentedSolverStopsOutflowFromExactlyAbsentDonorWithoutClippingMass() {
    double[][] initial = {{0.0, 0.5, 0.5, 0.0, 0.0, 0.0, 0.0}, {0.5, 0.25, 0.25, 0.0, 0.0, 0.0, 0.0}};
    double[] pressure = {2.0e5, 2.0e5};
    double[] areas = {1.0, 1.0};
    UnsplitTransientSolver.Model model = (state, selectedPressure, closureState, closurePressure, time, outletPressure,
        outletPressureFixed) -> {
      double[][] rates = {{-0.2, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.2, 0.0, 0.0, 0.0, 0.0, 0.0}};
      DirectionalPhaseFaceFlux[][] faces = {faces(0.2, 0.0, state[0][0], state[1][0], state[0][3], state[1][3])};
      double[][] densities = new double[3][2];
      for (int phase = 0; phase < 3; phase++) {
        densities[phase][0] = closurePressure[0] / 2.0e5;
        densities[phase][1] = closurePressure[1] / 2.0e5;
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities, faces, new double[] {1.0, 1.0}, new double[2][3]);
    };
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setDirectionalComplementarityEnabled(true);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    solver.setRelativeTolerance(1.0e-10);
    UnsplitTransientSolver.Result result = solver.solve(initial, pressure, areas, 1.0, 0.0, Double.NaN, false, model);

    assertTrue(result.isConverged(), result.getTerminationReason().name());
    assertEquals(0.0, result.getState()[0][0], 1.0e-10);
    assertEquals(0.5, result.getState()[1][0], 1.0e-10);
    assertEquals(0.0, result.getDirectionalPhaseFaceFluxes()[0][0].getSignedMassFlowKgPerSecond(), 1.0e-10);
    assertEquals(2.0, result.getState()[0][0] + result.getState()[0][1] + result.getState()[0][2]
        + result.getState()[1][0] + result.getState()[1][1] + result.getState()[1][2], 1.0e-12);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.5, 1.0, 2.0})
  void augmentedSolverLandsOnPhaseDisappearanceAndTransfersOnlyAvailableMass(double timeStep) {
    double[][] initial = {{0.1, 0.45, 0.45, 0.0, 0.0, 0.0, 0.0}, {0.5, 0.25, 0.25, 0.0, 0.0, 0.0, 0.0}};
    double[] pressure = {2.0e5, 2.0e5};
    double[] areas = {1.0, 1.0};
    UnsplitTransientSolver.Model model = (state, selectedPressure, closureState, closurePressure, time, outletPressure,
        outletPressureFixed) -> {
      double[][] rates = {{-0.2, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.2, 0.0, 0.0, 0.0, 0.0, 0.0}};
      DirectionalPhaseFaceFlux[][] faces = {faces(0.2, 0.0, state[0][0], state[1][0], state[0][3], state[1][3])};
      double[][] densities = new double[3][2];
      for (int phase = 0; phase < 3; phase++) {
        densities[phase][0] = closurePressure[0] / 2.0e5;
        densities[phase][1] = closurePressure[1] / 2.0e5;
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities, faces, new double[] {1.0, 1.0}, new double[2][3]);
    };
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setDirectionalComplementarityEnabled(true);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    solver.setRelativeTolerance(1.0e-10);
    UnsplitTransientSolver.Result result = solver.solve(initial, pressure, areas, timeStep, 0.0, Double.NaN, false,
        model);

    assertTrue(result.isConverged(), result.getTerminationReason().name());
    assertEquals(0.0, result.getState()[0][0], 0.0);
    assertEquals(0.6, result.getState()[1][0], 1.0e-10);
    assertEquals(0.1 / timeStep, result.getDirectionalPhaseFaceFluxes()[0][0].getSignedMassFlowKgPerSecond(), 1.0e-10);
    assertEquals(2.0, result.getState()[0][0] + result.getState()[0][1] + result.getState()[0][2]
        + result.getState()[1][0] + result.getState()[1][1] + result.getState()[1][2], 1.0e-12);
  }

  /** Build gas plus stationary oil/water ledgers at one face. */
  private static DirectionalPhaseFaceFlux[] faces(double gasMassFlow, double gasMomentum, double leftGasMass,
      double rightGasMass, double leftGasMomentum, double rightGasMomentum) {
    return new DirectionalPhaseFaceFlux[] {
        new DirectionalPhaseFaceFlux(gasMassFlow, gasMomentum, 7.0, leftGasMass, rightGasMass, leftGasMomentum,
            rightGasMomentum),
        new DirectionalPhaseFaceFlux(0.0, 0.0, 8.0, 1.0, 1.0, 0.0, 0.0),
        new DirectionalPhaseFaceFlux(0.0, 0.0, 9.0, 1.0, 1.0, 0.0, 0.0)};
  }

  /** Build a complete solver evaluation. */
  private static UnsplitTransientSolver.Evaluation evaluation(double[][] rates, DirectionalPhaseFaceFlux[][] faces,
      double[] lengths) {
    double[][] densities = {{10.0, 10.0}, {800.0, 800.0}, {1000.0, 1000.0}};
    return new UnsplitTransientSolver.Evaluation(rates, densities, faces, lengths, new double[2][3]);
  }
}
