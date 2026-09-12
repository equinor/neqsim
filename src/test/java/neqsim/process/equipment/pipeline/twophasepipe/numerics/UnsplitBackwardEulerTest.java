package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.Evaluation;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.Model;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.Result;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.TimeIntegrationMethod;
import org.junit.jupiter.api.Test;

/** Temporal-method qualification independent of the riser benchmark's physical closures. */
class UnsplitBackwardEulerTest {
  private static final double REFERENCE_PRESSURE = 1.0e5;

  @Test
  void backwardEulerDampsStiffDecayWhileTheDefaultRetainsMidpointAmplification() {
    for (double decayRate : new double[] { 100.0, 10000.0 }) {
      double[][] initial = decayState();
      double[] pressure = { REFERENCE_PRESSURE };
      UnsplitTransientSolver midpoint = newSolver(TimeIntegrationMethod.IMPLICIT_MIDPOINT);
      assertSame(TimeIntegrationMethod.IMPLICIT_MIDPOINT, new UnsplitTransientSolver().getTimeIntegrationMethod());
      Result centered = midpoint.solve(initial, pressure, new double[] { 1.0 }, 1.0, 0.0, Double.NaN, false,
          decayModel(decayRate));
      Result damped = newSolver(TimeIntegrationMethod.BACKWARD_EULER).solve(initial, pressure, new double[] { 1.0 },
          1.0, 0.0, Double.NaN, false, decayModel(decayRate));

      assertConverged(centered);
      assertConverged(damped);
      assertEquals(0.5, centered.getTimeIntegrationWeight(), 0.0);
      assertEquals(1.0, damped.getTimeIntegrationWeight(), 0.0);
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(initial[0][phase], damped.getState()[0][phase], 1.0e-12);
        assertEquals(initial[0][phase + 3] / (1.0 + decayRate), damped.getState()[0][phase + 3], 1.0e-10);
        assertEquals(initial[0][phase + 3] * (1.0 - 0.5 * decayRate) / (1.0 + 0.5 * decayRate),
            centered.getState()[0][phase + 3], 1.0e-10);
        assertTrue(damped.getState()[0][phase + 3] * initial[0][phase + 3] > 0.0);
        assertTrue(centered.getState()[0][phase + 3] * initial[0][phase + 3] < 0.0);
      }
      assertEquals(initial[0][6], damped.getState()[0][6], 0.0);
    }
  }

  @Test
  void stepRefinementShowsFirstOrderEulerAndSecondOrderMidpoint() {
    double exact = 2.0 * Math.exp(-1.0);
    double eulerCoarse = Math.abs(integrateDecay(TimeIntegrationMethod.BACKWARD_EULER, 10) - exact);
    double eulerFine = Math.abs(integrateDecay(TimeIntegrationMethod.BACKWARD_EULER, 20) - exact);
    double midpointCoarse = Math.abs(integrateDecay(TimeIntegrationMethod.IMPLICIT_MIDPOINT, 10) - exact);
    double midpointFine = Math.abs(integrateDecay(TimeIntegrationMethod.IMPLICIT_MIDPOINT, 20) - exact);
    assertTrue(eulerCoarse / eulerFine > 1.9 && eulerCoarse / eulerFine < 2.1,
        "Backward Euler must retain first-order temporal convergence");
    assertTrue(midpointCoarse / midpointFine > 3.9 && midpointCoarse / midpointFine < 4.1,
        "The default midpoint method must retain second-order temporal convergence");
    assertTrue(midpointFine < eulerFine);
  }

  @Test
  void everyProbeAndActiveSetRefreshUsesTheCapturedEndpointTimeLevel() {
    UnsplitTransientSolver solver = newSolver(TimeIntegrationMethod.BACKWARD_EULER);
    double[][] initial = decayState();
    double[] pressure = { REFERENCE_PRESSURE };
    double startTime = 2.0;
    double dt = 0.25;
    int[] counts = new int[4];
    double[][] lastEvaluationState = new double[1][];
    Model model = new Model() {
      @Override
      public Evaluation evaluate(double[][] state, double[] trialPressure, double[][] closureState,
          double[] closurePressure, double time, double outletPressure, boolean outletPressureFixed) {
        counts[0]++;
        assertEquals(startTime + dt, time, 0.0);
        assertArrayEquals(closureState[0], state[0], 0.0);
        assertArrayEquals(closurePressure, trialPressure, 0.0);
        lastEvaluationState[0] = state[0].clone();
        if (counts[0] == 1) {
          // Configuration changes during a callback must not change the solve already in progress.
          solver.setTimeIntegrationMethod(TimeIntegrationMethod.IMPLICIT_MIDPOINT);
        }
        double[][] rates = new double[1][6];
        rates[0][3] = time;
        return new Evaluation(rates, densities(closurePressure));
      }

      @Override
      public void beginLinearization(double[][] state, double[] trialPressure) {
        counts[1]++;
        assertArrayEquals(lastEvaluationState[0], state[0], 0.0);
      }

      @Override
      public void endLinearization() {
        counts[2]++;
      }

      @Override
      public boolean updateActiveSet(double[][] state, double[] trialPressure) {
        assertArrayEquals(lastEvaluationState[0], state[0], 0.0);
        return counts[3]++ == 0;
      }
    };

    Result result = solver.solve(initial, pressure, new double[] { 1.0 }, dt, startTime, Double.NaN, false, model);
    assertConverged(result);
    assertEquals(initial[0][3] + dt * (startTime + dt), result.getState()[0][3], 1.0e-10);
    assertEquals(counts[0], result.getModelEvaluations());
    assertTrue(counts[1] > 0);
    assertEquals(counts[1], counts[2]);
    assertTrue(counts[3] >= 3);
    assertEquals(1.0, result.getTimeIntegrationWeight(), 0.0);
    assertSame(TimeIntegrationMethod.IMPLICIT_MIDPOINT, solver.getTimeIntegrationMethod());

    solver.setTimeIntegrationMethod(TimeIntegrationMethod.BACKWARD_EULER);
    double[] residual = solver.residual(initial, pressure, result.getState(), result.getPressure(),
        new double[] { 1.0 }, dt, startTime, Double.NaN, false, model);
    assertArrayEquals(new double[7], residual, 1.0e-10);
    double[][] jacobian = solver.scaledJacobian(initial, pressure, result.getState(), result.getPressure(),
        new double[] { 1.0 }, dt, startTime, Double.NaN, false, model);
    assertEquals(1.0, jacobian[3][3], 1.0e-8);
  }

  @Test
  void threePhasePreparationUsesCandidateTimeAndEndpointBoundaryTransfersAfterConfigurationChanges() throws Exception {
    TwoFluidSection[] initial = { flowingSection(0.0, 5.0), flowingSection(5.0, 10.0), flowingSection(15.0, 15.0) };
    double[][] previous = new double[initial.length][];
    double[] pressure = new double[initial.length];
    double[] areas = new double[initial.length];
    for (int cell = 0; cell < initial.length; cell++) {
      previous[cell] = initial[cell].getStateVector();
      pressure[cell] = initial[cell].getPressure();
      areas[cell] = initial[cell].getArea();
    }
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setAllowOutletPhaseBackflow(true);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setInletPhaseFlowBoundaryState(initial[0]);
    TwoFluidConservationEquations.MassBalanceRate published = equations.getLastMassBalanceRate();
    double startTime = 2.0;
    double dt = 0.002;
    double[] expectedTime = { startTime + dt };
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, initial, 10.0,
        (cell, state, trialPressure, time) -> {
          assertEquals(expectedTime[0], time, 0.0, "All trial and endpoint densities must use the selected time");
          return new double[] { 40.0 + 1.0e-6 * (trialPressure - 5.0e6) + 0.25 * (time - startTime), 700.0, 1000.0 };
        });
    UnsplitTransientSolver solver = newSolver(TimeIntegrationMethod.BACKWARD_EULER);
    Result candidate = solver.solve(previous, pressure, areas, dt, startTime, 4.999e6, true, adapter);
    assertConverged(candidate);
    candidate = roundTrip(candidate, null);
    solver.setTimeIntegrationMethod(TimeIntegrationMethod.IMPLICIT_MIDPOINT);

    PreparedStep step = adapter.prepareStep(candidate, dt, startTime, 4.999e6, true, 1.0e-8);
    assertEquals(1.0, step.getTimeIntegrationWeight(), 0.0);
    assertEquals(startTime + dt, step.getEvaluationTimeSeconds(), 0.0);
    assertSame(step.getEvaluation(), step.getMidpointEvaluation());
    assertSame(published, equations.getLastMassBalanceRate());
    TwoFluidSection[] endpoint = step.getEndpointSections();
    double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
    double[][] rates = step.getEvaluation().getRates();
    double[][] solved = candidate.getState();
    for (int phase = 0; phase < 3; phase++) {
      double massChange = 0.0;
      for (int cell = 0; cell < initial.length; cell++) {
        massChange += initial[cell].getLength() * (solved[cell][phase] - previous[cell][phase]);
        assertEquals(solved[cell][phase] - previous[cell][phase], dt * rates[cell][phase], 1.0e-9);
      }
      // Prescribed inlet flow is unchanged; the open outlet transports endpoint phase momentum.
      assertEquals(previous[0][phase + 3], faces[0][phase], 1.0e-9);
      assertEquals(solved[initial.length - 1][phase + 3], faces[initial.length][phase], 1.0e-8);
      assertEquals(dt * (previous[0][phase + 3] - solved[initial.length - 1][phase + 3]), massChange, 1.0e-8);
    }
    assertArrayEquals(new double[3], step.getMassResidualKg(), 1.0e-8);
    for (int cell = 0; cell < initial.length; cell++) {
      assertArrayEquals(previous[cell], initial[cell].getStateVector(), 0.0);
      assertArrayEquals(solved[cell], endpoint[cell].getStateVector(), 0.0);
      assertEquals(1.0, endpoint[cell].getGasHoldup() + endpoint[cell].getOilHoldup() + endpoint[cell].getWaterHoldup(),
          1.0e-8);
    }
    PreparedStep restored = roundTrip(step, null);
    assertEquals(1.0, restored.getTimeIntegrationWeight(), 0.0);
    assertEquals(startTime + dt, restored.getEvaluationTimeSeconds(), 0.0);
    assertArrayEquals(step.getMassResidualKg(), restored.getMassResidualKg(), 0.0);

    expectedTime[0] = startTime + 0.5 * dt;
    double[] wrongMethodResidual = solver.residual(previous, pressure, candidate.getState(), candidate.getPressure(),
        areas, dt, startTime, 4.999e6, true, adapter);
    assertTrue(maximumAbsolute(wrongMethodResidual) > 1.0e-8,
        "A midpoint verification must not silently accept the backward-Euler candidate's different time level");
  }

  @Test
  void serializationWithoutTheNewFieldsRetainsTheMidpointDefaults() throws Exception {
    UnsplitTransientSolver solver = newSolver(TimeIntegrationMethod.IMPLICIT_MIDPOINT);
    assertThrows(IllegalArgumentException.class, () -> solver.setTimeIntegrationMethod(null));
    Result result = solver.solve(decayState(), new double[] { REFERENCE_PRESSURE }, new double[] { 1.0 }, 0.2, 0.0,
        Double.NaN, false, decayModel(1.0));
    assertConverged(result);
    UnsplitTransientSolver legacySolver = roundTrip(solver, "timeIntegrationMethod");
    Result legacyResult = roundTrip(result, "timeIntegrationWeight");
    assertSame(TimeIntegrationMethod.IMPLICIT_MIDPOINT, legacySolver.getTimeIntegrationMethod());
    assertEquals(0.5, legacyResult.getTimeIntegrationWeight(), 0.0);
    assertArrayEquals(result.getState()[0], legacyResult.getState()[0], 0.0);
    solver.setTimeIntegrationMethod(TimeIntegrationMethod.BACKWARD_EULER);
    UnsplitTransientSolver restoredEuler = roundTrip(solver, null);
    assertSame(TimeIntegrationMethod.BACKWARD_EULER, restoredEuler.getTimeIntegrationMethod());
  }

  private static UnsplitTransientSolver newSolver(TimeIntegrationMethod method) {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setTimeIntegrationMethod(method);
    solver.setRelativeTolerance(1.0e-11);
    return solver;
  }

  private static double[][] decayState() {
    return new double[][] { { 2.0, 3.0, 5.0, 2.0, -3.0, 5.0, 17.0 } };
  }

  private static double[][] densities(double[] pressure) {
    return new double[][] { { 10.0 * pressure[0] / REFERENCE_PRESSURE }, { 10.0 }, { 10.0 } };
  }

  private static Model decayModel(double decayRate) {
    return (state, pressure, closureState, closurePressure, time, outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[1][6];
      for (int phase = 0; phase < 3; phase++) {
        rates[0][phase + 3] = -decayRate * state[0][phase + 3];
      }
      return new Evaluation(rates, densities(closurePressure));
    };
  }

  private static double integrateDecay(TimeIntegrationMethod method, int steps) {
    UnsplitTransientSolver solver = newSolver(method);
    double[][] state = decayState();
    double[] pressure = { REFERENCE_PRESSURE };
    double dt = 1.0 / steps;
    for (int step = 0; step < steps; step++) {
      Result result = solver.solve(state, pressure, new double[] { 1.0 }, dt, step * dt, Double.NaN, false,
          decayModel(1.0));
      assertConverged(result);
      state = result.getState();
      pressure = result.getPressure();
    }
    return state[0][3];
  }

  private static TwoFluidSection flowingSection(double position, double length) {
    TwoFluidSection section = new TwoFluidSection(position, length, 0.1, 0.0);
    section.setPressure(5.0e6);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    section.setGasViscosity(1.2e-5);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setLiquidViscosity(1.0e-3);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setSurfaceTension(0.02);
    section.setGasHoldup(0.6);
    section.setLiquidHoldup(0.4);
    section.setOilHoldup(0.2);
    section.setWaterHoldup(0.2);
    section.setWaterCut(0.5);
    section.setOilFractionInLiquid(0.5);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(0.5);
    section.setOilVelocity(0.55);
    section.setWaterVelocity(0.45);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private static double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  private static void assertConverged(Result result) {
    assertTrue(result.isConverged(), () -> result.getTerminationReason() + ": " + result.getMaximumScaledResidual());
  }

  /** Rename an optional serialized field to exercise Java's missing-field defaults with the existing serial UID. */
  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T value, String missingField) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(output)) {
      stream.writeObject(value);
    }
    byte[] data = output.toByteArray();
    if (missingField != null) {
      byte[] name = missingField.getBytes(StandardCharsets.UTF_8);
      boolean renamed = false;
      for (int index = 0; index <= data.length - name.length && !renamed; index++) {
        boolean matches = true;
        for (int offset = 0; offset < name.length; offset++) {
          matches &= data[index + offset] == name[offset];
        }
        if (matches) {
          data[index] = (byte) 'x';
          renamed = true;
        }
      }
      assertTrue(renamed, "The compatibility fixture must contain the new field before removing its name");
    }
    try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(data))) {
      return (T) stream.readObject();
    }
  }
}
