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
  void simultaneousTwoDonorFluxRetainsBothNormalizedStreams() {
    DirectionalPhaseFaceFlux gas = new DirectionalPhaseFaceFlux(0.4, 0.2, 1.2, 1.0, 7.0, 0.2, 0.3, 0.6, -1.5);
    DirectionalPhaseFaceFlux[][] faces = {{gas, new DirectionalPhaseFaceFlux(0.0, 0.0, 8.0, 1.0, 1.0, 0.0, 0.0),
        new DirectionalPhaseFaceFlux(0.0, 0.0, 9.0, 1.0, 1.0, 0.0, 0.0)}};
    UnsplitTransientSolver.Evaluation free = evaluation(new double[2][6], faces, new double[] {1.0, 1.0});
    double[][] endpoint = {{0.2, 1.0, 1.0, 0.6, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
    SignedFaceComplementarity.State state = SignedFaceComplementarity.initialize(free, endpoint);
    SignedFaceComplementarity.Evaluation corrected = SignedFaceComplementarity.evaluate(free, endpoint, state);

    DirectionalPhaseFaceFlux solved = corrected.getSolvedFaces()[0][0];
    assertEquals(0.4, solved.getForwardMassFlowKgPerSecond(), 0.0);
    assertEquals(0.2, solved.getReverseMassFlowKgPerSecond(), 0.0);
    assertEquals(1.2, solved.getForwardAdvectiveMomentumN(), 0.0);
    assertEquals(1.0, solved.getReverseAdvectiveMomentumN(), 0.0);
    for (double residual : corrected.getResidual()) {
      assertEquals(0.0, residual, 1.0e-15);
    }
  }

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
    assertTrue(state.isConstrained(0, 0, SignedFaceComplementarity.FORWARD));
    assertFalse(state.isConstrained(0, 0, SignedFaceComplementarity.REVERSE));
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
  void independentDonorRowsReleaseWithoutSwitchingTheOppositeStream() {
    DirectionalPhaseFaceFlux[][] counterFaces = {twoSidedFaces(0.4, 0.2, 0.0, -1.0, 0.0, 0.3, 0.0, -1.5)};
    double[][] endpoint = {{0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0}, {0.3, 1.0, 1.0, -1.5, 0.0, 0.0, 0.0}};
    SignedFaceComplementarity.State state = SignedFaceComplementarity
        .initialize(evaluation(new double[2][6], counterFaces, new double[] {1.0, 1.0}), endpoint);
    assertTrue(state.isConstrained(0, 0, SignedFaceComplementarity.FORWARD));
    assertFalse(state.isConstrained(0, 0, SignedFaceComplementarity.REVERSE));

    double[][] presentEndpoint = {{0.2, 1.0, 1.0, 0.6, 0.0, 0.0, 0.0}, endpoint[1]};
    DirectionalPhaseFaceFlux[][] presentFaces = {twoSidedFaces(0.4, 0.2, 1.2, -1.0, 0.2, 0.3, 0.6, -1.5)};
    boolean changed = SignedFaceComplementarity
        .refreshActiveSet(evaluation(new double[2][6], presentFaces, new double[] {1.0, 1.0}), presentEndpoint, state);

    assertTrue(changed);
    assertFalse(state.isConstrained(0, 0, SignedFaceComplementarity.FORWARD));
    assertFalse(state.isConstrained(0, 0, SignedFaceComplementarity.REVERSE));
  }

  @Test
  void structuralScheduleUsesSeventyOneConflictFreeColors() {
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
          int phase = variable / SignedFaceComplementarity.VARIABLES_PER_PHASE_FACE;
          int localVariable = variable % SignedFaceComplementarity.VARIABLES_PER_PHASE_FACE;
          int left = SignedFaceComplementarity.column(cellCount, face, phase, localVariable);
          if (face + 1 < cellCount - 1) {
            int right = SignedFaceComplementarity.column(cellCount, face + 1, phase, localVariable);
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

  @Test
  void augmentedSolverKeepsAMultiCellPhaseFreeRegionStructurallyNonsingular() {
    double[][] initial = {{0.5, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.5, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0},
        {0.5, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0}};
    double[] pressure = {2.0e5, 2.0e5, 2.0e5};
    UnsplitTransientSolver.Model model = (state, selectedPressure, closureState, closurePressure, time, outletPressure,
        outletPressureFixed) -> {
      double[][] rates = {{-0.1, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
          {0.1, 0.0, 0.0, 0.0, 0.0, 0.0}};
      DirectionalPhaseFaceFlux[][] ledgers = new DirectionalPhaseFaceFlux[2][3];
      for (int face = 0; face < ledgers.length; face++) {
        ledgers[face][0] = new DirectionalPhaseFaceFlux(0.1, 0.0, 0.0, state[face][0], state[face + 1][0],
            state[face][3], state[face + 1][3]);
        ledgers[face][1] = new DirectionalPhaseFaceFlux(0.0, 0.0, 0.0, state[face][1], state[face + 1][1],
            state[face][4], state[face + 1][4]);
        ledgers[face][2] = new DirectionalPhaseFaceFlux(0.0, 0.0, 0.0, state[face][2], state[face + 1][2],
            state[face][5], state[face + 1][5]);
      }
      double[][] densities = new double[3][3];
      for (int phase = 0; phase < 3; phase++) {
        for (int cell = 0; cell < 3; cell++) {
          densities[phase][cell] = closurePressure[cell] / 2.0e5;
        }
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities, ledgers, new double[] {1.0, 1.0, 1.0},
          new double[3][3]);
    };
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setDirectionalComplementarityEnabled(true);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    solver.setRelativeTolerance(1.0e-10);
    UnsplitTransientSolver.Result result = solver.solve(initial, pressure, new double[] {1.0, 1.0, 1.0}, 0.1, 0.0,
        Double.NaN, false, model);

    assertTrue(result.isConverged(), result.getTerminationReason().name());
    assertEquals(0.49, result.getState()[0][0], 1.0e-12);
    assertEquals(0.5, result.getState()[1][0], 1.0e-12);
    assertEquals(0.51, result.getState()[2][0], 1.0e-12);
    for (double[] cell : result.getState()) {
      assertEquals(0.0, cell[2], 0.0);
    }
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

  /** Build explicit two-sided gas plus stationary oil/water ledgers at one face. */
  private static DirectionalPhaseFaceFlux[] twoSidedFaces(double forwardMassFlow, double reverseMassFlow,
      double forwardMomentum, double reverseMomentum, double leftGasMass, double rightGasMass, double leftGasMomentum,
      double rightGasMomentum) {
    return new DirectionalPhaseFaceFlux[] {
        new DirectionalPhaseFaceFlux(forwardMassFlow, reverseMassFlow, forwardMomentum, reverseMomentum, 7.0,
            leftGasMass, rightGasMass, leftGasMomentum, rightGasMomentum),
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
