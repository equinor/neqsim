package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.MassBalanceRate;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.Result;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver.TimeIntegrationMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Time-level, conservation and transactional contracts for unsplit pressure interpolation. */
class UnsplitPressureInterpolationTest {
  private static final double REFERENCE_PRESSURE = 1.0e5;
  private static final double SAVED_TIME_SCALE = 0.125;

  @Test
  void closedHomogeneousCoupledBlockRemainsExactlyZeroDespiteLargeExternalCoefficients() throws Exception {
    double scale = 1.0e8;
    double[][] matrix = { { 1.0, 1.23456789 * scale, -9.87654321 * scale, 2.0, 0.0712345 * scale },
        { 0.0, 2.0, 0.5, 0.0, 0.0 }, { 0.0, -0.125, 3.0, 0.0, 1.0 },
        { 0.25, -0.1234567 * scale, 2.3456789 * scale, 4.0, -1.234567 * scale }, { 0.0, 0.0, 0.25, 0.0, 4.0 } };
    double[] rightHandSide = { -4.0, 0.0, 0.0, -11.5, 0.0 };
    // Unknowns 1, 2 and 4 form a coupled closed homogeneous system. Pivots in the forced rows must not seed them.
    double[] solution = solveLinear(matrix, rightHandSide);
    assertArrayEquals(new double[] { 2.0, 0.0, 0.0, -3.0, 0.0 }, solution, 0.0);
    for (int row = 0; row < rightHandSide.length; row++) {
      double product = 0.0;
      for (int column = 0; column < solution.length; column++) {
        product += matrix[row][column] * solution[column];
      }
      assertEquals(rightHandSide[row], product, 0.0, "The unchanged complete linear system must still be solved");
    }
  }

  @Test
  void zeroRightHandSidesWithForcedDependenciesAreRetainedAndSingularBlocksAreRejected() throws Exception {
    double[][] dependent = { { 2.0, 1.0, 0.0 }, { 0.0, 3.0, 1.0 }, { 0.0, 0.0, 4.0 } };
    assertArrayEquals(new double[] { 1.0 / 6.0, -1.0 / 3.0, 1.0 }, solveLinear(dependent, new double[] { 0, 0, 4 }),
        1.0e-15);
    double[][] singular = { { 1.0, 7.0, 9.0 }, { 0.0, 1.0, 1.0 }, { 0.0, 2.0, 2.0 } };
    InvocationTargetException failure = assertThrows(InvocationTargetException.class,
        () -> solveLinear(singular, new double[] { 3.0, 0.0, 0.0 }));
    assertTrue(failure.getCause() instanceof IllegalStateException,
        "A homogeneous block must be proved nonsingular before using its zero solution");
    double[][] nonfiniteCoupling = { { 1.0, Double.NaN }, { 0.0, 1.0 } };
    InvocationTargetException invalid = assertThrows(InvocationTargetException.class,
        () -> solveLinear(nonfiniteCoupling, new double[] { 1.0, 0.0 }));
    assertTrue(invalid.getCause() instanceof IllegalStateException,
        "Nonfinite coefficients must not disappear when a zero block is isolated");
  }

  @Test
  void gasOilInterpolationNeverSeedsAbsentWaterMassOrMomentumAcrossPreparedSteps() {
    TwoFluidSection[] accepted = sections(5, true, 0.1, false);
    for (TwoFluidSection section : accepted) {
      section.setWaterCut(0.0);
      section.setOilFractionInLiquid(1.0);
      section.setOilHoldup(0.3);
      section.setWaterHoldup(0.0);
      section.setLiquidDensity(700.0);
      section.setGasVelocity(3.0);
      section.setLiquidVelocity(0.5);
      section.setOilVelocity(0.5);
      section.setWaterVelocity(0.0);
      section.updateConservativeVariables();
      section.updateDerivedQuantities();
    }
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, densityModel(),
        solver(TimeIntegrationMethod.BACKWARD_EULER), 3, 64);
    integrator.setPressureInterpolationEnabled(true);
    PreparedInterval interval = integrator.prepareInterval(accepted, 1.0, 1.0 / 256.0, 0.0, Double.NaN, false, 1.0e-8,
        1.0 / 2048.0);
    TwoFluidSection[] previous = accepted;
    for (PreparedStep step : interval.getSubsteps()) {
      assertStrictEndpointAndCellLedger(previous, step);
      for (TwoFluidSection endpoint : step.getEndpointSections()) {
        assertEquals(0.0, endpoint.getWaterMassPerLength(), 0.0);
        assertEquals(0.0, endpoint.getWaterMomentumPerLength(), 0.0);
      }
      for (double[] face : step.getEvaluation().getPhaseMassFaceFluxes()) {
        assertEquals(0.0, face[2], 0.0);
      }
      previous = step.getEndpointSections();
    }
    assertArrayEquals(inventories(accepted), inventories(interval.getEndpointSections()), 1.0e-8);
    published.assertUnchanged(equations);
  }

  @ParameterizedTest
  @CsvSource({ "5,false", "7,false", "5,true", "7,true" })
  void backwardEulerDampsClosedCheckerboardsWithNonuniformDensityAndConservesEveryPhase(int cells, boolean threePhase) {
    TwoFluidSection[] accepted = sections(cells, threePhase, 1.0, true);
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    double dt = 1.0 / 32.0;
    UnsplitTransientSolver solver = solver(TimeIntegrationMethod.BACKWARD_EULER);
    TwoFluidUnsplitModelAdapter enabled = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(), dt);
    Result candidate = solve(solver, enabled, accepted, dt, 0.0);
    assertConverged(candidate);
    PreparedStep step = enabled.prepareStep(candidate, dt, 0.0, Double.NaN, false, 1.0e-8);
    TwoFluidUnsplitModelAdapter disabled = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel());
    Result baseline = solve(solver, disabled, accepted, dt, 0.0);
    assertConverged(baseline);

    double initialAmplitude = checkerboardAmplitude(pressures(accepted), accepted);
    double correctedAmplitude = checkerboardAmplitude(candidate.getPressure(), accepted);
    assertTrue(correctedAmplitude < 0.5 * initialAmplitude,
        "The compact pressure coupling must damp the initially volume-exact alternating pressure mode");
    assertTrue(correctedAmplitude < checkerboardAmplitude(baseline.getPressure(), accepted),
        "Interpolation must add pressure-mode damping beyond the same backward-Euler step without interpolation");
    assertEquals(dt, step.getPressureInterpolationTimeScale(), 0.0);
    assertEquals(1.0, step.getTimeIntegrationWeight(), 0.0);
    assertStrictEndpointAndCellLedger(accepted, step);
    assertArrayEquals(inventories(accepted), inventories(step.getEndpointSections()), 1.0e-8);
    double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
    assertArrayEquals(new double[3], faces[0], 0.0);
    assertArrayEquals(new double[3], faces[cells], 0.0);
    for (TwoFluidSection endpoint : step.getEndpointSections()) {
      if (!threePhase) {
        assertEquals(0.0, endpoint.getOilMassPerLength(), 0.0);
        assertEquals(0.0, endpoint.getWaterMassPerLength(), 0.0);
      }
    }
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);
  }

  @Test
  void sevenCellColoredJacobianMatchesIndependentFullWidthProbes() {
    TwoFluidSection[] accepted = sections(7, true, 0.25, true);
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    double dt = 1.0 / 128.0;
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(), dt);
    UnsplitTransientSolver solver = solver(TimeIntegrationMethod.BACKWARD_EULER);
    solver.setCellStencilHalfWidth(2);
    double[][] colored = solver.scaledJacobian(states(accepted), pressures(accepted), states(accepted),
        pressures(accepted), areas(accepted), dt, 0.0, Double.NaN, false, adapter);
    solver.setCellStencilHalfWidth(accepted.length - 1);
    double[][] full = solver.scaledJacobian(states(accepted), pressures(accepted), states(accepted),
        pressures(accepted), areas(accepted), dt, 0.0, Double.NaN, false, adapter);
    assertMatrixEquals(full, colored, 0.0);
    published.assertUnchanged(equations);
  }

  @ParameterizedTest
  @EnumSource(TimeIntegrationMethod.class)
  void preparationRejectsAnIncorrectPositiveImpulseEvenWhenTheStateIsAnExactFixedPoint(TimeIntegrationMethod method) {
    TwoFluidSection[] accepted = sections(5, true, 0.0, false);
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    double dt = 1.0 / 64.0;
    double weight = method == TimeIntegrationMethod.BACKWARD_EULER ? 1.0 : 0.5;
    TwoFluidUnsplitModelAdapter correct = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(),
        weight * dt);
    Result candidate = solve(solver(method), correct, accepted, dt, 2.0);
    assertConverged(candidate);
    assertEquals(0, candidate.getIterations(), "A constant-pressure contact must not need a nonlinear correction");
    TwoFluidUnsplitModelAdapter mismatched = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(),
        0.75 * weight * dt);
    assertThrows(IllegalArgumentException.class,
        () -> mismatched.prepareStep(candidate, dt, 2.0, Double.NaN, false, 1.0e-8));
    PreparedStep valid = correct.prepareStep(candidate, dt, 2.0, Double.NaN, false, 1.0e-8);
    assertEquals(weight * dt, valid.getPressureInterpolationTimeScale(), 0.0);
    published.assertUnchanged(equations);
  }

  @Test
  void failedResidualAndPreparationRestoreTheEquationImpulseAndAllPublishedDiagnostics() {
    TwoFluidSection[] accepted = sections(5, true, 0.0, false);
    TwoFluidSection[] original = cloneSections(accepted);
    ThrowingEquations equations = new ThrowingEquations();
    configureClosed(equations);
    equations.expectedTimeScale = SAVED_TIME_SCALE;
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    double dt = 1.0 / 64.0;
    equations.expectedTimeScale = dt;
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(), dt);
    Result candidate = solve(solver(TimeIntegrationMethod.BACKWARD_EULER), adapter, accepted, dt, 2.0);
    assertConverged(candidate);
    equations.fail = true;

    assertSame(equations.failure, assertThrows(IllegalStateException.class, () -> adapter.evaluate(states(accepted),
        pressures(accepted), states(accepted), pressures(accepted), 2.0 + dt, Double.NaN, false)));
    published.assertUnchanged(equations);
    assertSame(equations.failure, assertThrows(IllegalStateException.class,
        () -> adapter.prepareStep(candidate, dt, 2.0, Double.NaN, false, 1.0e-8)));
    published.assertUnchanged(equations);
    assertSectionsEqual(original, accepted);
  }

  @ParameterizedTest
  @EnumSource(TimeIntegrationMethod.class)
  void intervalRecordsTheActualTimeWeightAndStepLengthInEveryIndependentLedger(TimeIntegrationMethod method) {
    TwoFluidSection[] accepted = sections(5, true, 0.1, false);
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, densityModel(), solver(method), 2,
        16);
    assertFalse(integrator.isPressureInterpolationEnabled());
    integrator.setPressureInterpolationEnabled(true);
    double start = 4.5;
    double duration = 1.0 / 256.0;
    PreparedInterval interval = integrator.prepareInterval(accepted, 1.0, duration, start, Double.NaN, false, 1.0e-8,
        duration / 4.0);
    assertEquals(4, interval.getSubsteps().size());
    assertEquals(0, interval.getRejectedAttempts(), "The smooth small-amplitude fixture must not need subdivision");
    double[][] integratedFaces = new double[accepted.length + 1][3];
    double[][] integratedSources = new double[accepted.length][3];
    TwoFluidSection[] previous = accepted;
    double time = start;
    double weight = method == TimeIntegrationMethod.BACKWARD_EULER ? 1.0 : 0.5;
    for (PreparedStep step : interval.getSubsteps()) {
      double dt = step.getTimeStepSeconds();
      assertEquals(time, step.getStartTimeSeconds(), 0.0);
      assertEquals(weight, step.getTimeIntegrationWeight(), 0.0);
      assertEquals(weight * dt, step.getPressureInterpolationTimeScale(), 0.0);
      assertEquals(time + weight * dt, step.getEvaluationTimeSeconds(), 0.0);
      assertStrictEndpointAndCellLedger(previous, step);
      double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
      double[][] sources = step.getEvaluation().getPhaseMassSourcesPerLength();
      for (int face = 0; face < faces.length; face++) {
        for (int phase = 0; phase < 3; phase++) {
          integratedFaces[face][phase] += dt * faces[face][phase];
        }
      }
      for (int cell = 0; cell < accepted.length; cell++) {
        for (int phase = 0; phase < 3; phase++) {
          integratedSources[cell][phase] += dt * sources[cell][phase] * accepted[cell].getLength();
        }
      }
      previous = step.getEndpointSections();
      time += dt;
    }
    assertEquals(start + duration, time, 0.0);
    assertEquals(time, interval.getEndTimeSeconds(), 0.0);
    assertMatrixEquals(integratedFaces, interval.getPhaseMassFaceTransferKg(), 0.0);
    assertMatrixEquals(integratedSources, interval.getPhaseMassSourceTransferKg(), 0.0);
    TwoFluidSection[] endpoint = interval.getEndpointSections();
    for (int cell = 0; cell < accepted.length; cell++) {
      for (int phase = 0; phase < 3; phase++) {
        double inventoryChange = (endpoint[cell].getStateVector()[phase] - accepted[cell].getStateVector()[phase])
            * accepted[cell].getLength();
        double transferred = integratedFaces[cell][phase] - integratedFaces[cell + 1][phase]
            + integratedSources[cell][phase];
        assertEquals(inventoryChange, transferred, 1.0e-8);
      }
    }
    assertArrayEquals(inventories(accepted), inventories(endpoint), 1.0e-8);
    assertArrayEquals(new double[3], interval.getMassResidualKg(), 1.0e-8);
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);
  }

  @Test
  void zeroImpulseAndDisabledIntervalReproduceTheExistingConstructorExactly() {
    TwoFluidSection[] accepted = sections(5, true, 0.1, false);
    TwoFluidConservationEquations equations = closedEquations();
    PublishedDiagnostics published = publishDiagnostics(equations, accepted);
    double dt = 1.0 / 1024.0;
    UnsplitTransientSolver solver = solver(TimeIntegrationMethod.IMPLICIT_MIDPOINT);
    TwoFluidUnsplitModelAdapter original = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel());
    TwoFluidUnsplitModelAdapter explicitZero = new TwoFluidUnsplitModelAdapter(equations, accepted, 1.0, densityModel(),
        0.0);
    double[] baselineResidual = solver.residual(states(accepted), pressures(accepted), states(accepted),
        pressures(accepted), areas(accepted), dt, 0.0, Double.NaN, false, original);
    double[] zeroResidual = solver.residual(states(accepted), pressures(accepted), states(accepted),
        pressures(accepted), areas(accepted), dt, 0.0, Double.NaN, false, explicitZero);
    assertArrayEquals(baselineResidual, zeroResidual, 0.0);
    Result baseline = solve(solver, original, accepted, dt, 0.0);
    Result zero = solve(solver, explicitZero, accepted, dt, 0.0);
    assertConverged(baseline);
    assertConverged(zero);
    assertMatrixEquals(baseline.getState(), zero.getState(), 0.0);
    assertArrayEquals(baseline.getPressure(), zero.getPressure(), 0.0);
    assertEquals(baseline.getModelEvaluations(), zero.getModelEvaluations());
    PreparedStep direct = original.prepareStep(baseline, dt, 0.0, Double.NaN, false, 1.0e-8);
    assertEquals(0.0, direct.getPressureInterpolationTimeScale(), 0.0);

    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, densityModel(), solver, 0, 1);
    assertFalse(integrator.isPressureInterpolationEnabled());
    integrator.setPressureInterpolationEnabled(true);
    integrator.setPressureInterpolationEnabled(false);
    PreparedInterval interval = integrator.prepareInterval(accepted, 1.0, dt, 0.0, Double.NaN, false, 1.0e-8);
    assertEquals(1, interval.getSubsteps().size());
    assertEquals(0.0, interval.getSubsteps().get(0).getPressureInterpolationTimeScale(), 0.0);
    assertSectionsEqual(direct.getEndpointSections(), interval.getEndpointSections());
    assertEquals(baseline.getModelEvaluations(), interval.getModelEvaluations());
    assertMatrixEquals(direct.getEvaluation().getPhaseMassFaceFluxes(),
        interval.getSubsteps().get(0).getEvaluation().getPhaseMassFaceFluxes(), 0.0);
    published.assertUnchanged(equations);
  }

  private static void assertStrictEndpointAndCellLedger(TwoFluidSection[] previous, PreparedStep step) {
    TwoFluidSection[] endpoint = step.getEndpointSections();
    double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
    double[][] sources = step.getEvaluation().getPhaseMassSourcesPerLength();
    double[][] rates = step.getEvaluation().getRates();
    for (int cell = 0; cell < previous.length; cell++) {
      double[] before = previous[cell].getStateVector();
      double[] after = endpoint[cell].getStateVector();
      assertTrue(Double.isFinite(endpoint[cell].getPressure()) && endpoint[cell].getPressure() > 0.0);
      assertEquals(1.0, endpoint[cell].getGasHoldup() + endpoint[cell].getOilHoldup() + endpoint[cell].getWaterHoldup(),
          1.0e-8);
      assertEquals(before[6], after[6], 0.0);
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(after[phase] >= 0.0 && Double.isFinite(after[phase]));
        double change = (after[phase] - before[phase]) * endpoint[cell].getLength();
        double transferred = step.getTimeStepSeconds()
            * (faces[cell][phase] - faces[cell + 1][phase] + sources[cell][phase] * endpoint[cell].getLength());
        assertEquals(change, transferred, 1.0e-8);
        assertEquals(after[phase + 3] - before[phase + 3], step.getTimeStepSeconds() * rates[cell][phase + 3], 1.0e-8);
      }
    }
    assertArrayEquals(new double[3], step.getMassResidualKg(), 1.0e-8);
  }

  private static Result solve(UnsplitTransientSolver solver, TwoFluidUnsplitModelAdapter adapter,
      TwoFluidSection[] accepted, double dt, double start) {
    return solver.solve(states(accepted), pressures(accepted), areas(accepted), dt, start, Double.NaN, false, adapter);
  }

  private static UnsplitTransientSolver solver(TimeIntegrationMethod method) {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setTimeIntegrationMethod(method);
    solver.setRelativeTolerance(1.0e-10);
    return solver;
  }

  private static PhaseDensityModel densityModel() {
    return (cell, state, pressure, time) -> new double[] { gasDensity(cell, pressure), 700.0, 1000.0 };
  }

  private static double gasDensity(int cell, double pressure) {
    return 1.0e-5 * (1.0 + 0.05 * cell) * pressure;
  }

  private static TwoFluidSection[] sections(int count, boolean threePhase, double amplitude, boolean checkerboard) {
    TwoFluidSection[] result = new TwoFluidSection[count];
    double left = 0.0;
    for (int cell = 0; cell < count; cell++) {
      double length = 1.0 + 0.2 * cell;
      double mode = checkerboard ? (cell % 2 == 0 ? 1.0 : -1.0) : Math.cos(Math.PI * (cell + 0.5) / count);
      double pressure = REFERENCE_PRESSURE + amplitude * mode;
      TwoFluidSection section = new TwoFluidSection(left + 0.5 * length, length, 0.1, 0.0);
      section.setPressure(pressure);
      section.setTemperature(300.0);
      section.setGasDensity(gasDensity(cell, pressure));
      section.setOilDensity(700.0);
      section.setWaterDensity(1000.0);
      section.setLiquidDensity(threePhase ? 820.0 : 700.0);
      section.setGasViscosity(1.2e-5);
      section.setOilViscosity(1.0e-3);
      section.setWaterViscosity(1.0e-3);
      section.setLiquidViscosity(1.0e-3);
      section.setGasSoundSpeed(Math.sqrt(pressure / section.getGasDensity()));
      section.setLiquidSoundSpeed(1200.0);
      section.setSurfaceTension(0.02);
      section.setGasHoldup(threePhase ? 0.7 : 1.0);
      section.setLiquidHoldup(threePhase ? 0.3 : 0.0);
      section.setOilHoldup(threePhase ? 0.18 : 0.0);
      section.setWaterHoldup(threePhase ? 0.12 : 0.0);
      section.setWaterCut(threePhase ? 0.4 : 0.0);
      section.setOilFractionInLiquid(threePhase ? 0.6 : 1.0);
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.updateConservativeVariables();
      section.updateDerivedQuantities();
      result[cell] = section;
      left += length;
    }
    return result;
  }

  private static TwoFluidConservationEquations closedEquations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    configureClosed(equations);
    return equations;
  }

  private static void configureClosed(TwoFluidConservationEquations equations) {
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setClosedBoundaries(true, true);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setMomentumForceDiagnosticsEnabled(true);
  }

  private static PublishedDiagnostics publishDiagnostics(TwoFluidConservationEquations equations,
      TwoFluidSection[] sections) {
    equations.setPressureInterpolationTimeScale(SAVED_TIME_SCALE);
    equations.calcRHS(cloneSections(sections), 1.0);
    return new PublishedDiagnostics(equations);
  }

  private static double checkerboardAmplitude(double[] pressure, TwoFluidSection[] sections) {
    double totalLength = 0.0;
    double mean = 0.0;
    for (int cell = 0; cell < pressure.length; cell++) {
      totalLength += sections[cell].getLength();
      mean += sections[cell].getLength() * pressure[cell];
    }
    mean /= totalLength;
    double projection = 0.0;
    for (int cell = 0; cell < pressure.length; cell++) {
      projection += (cell % 2 == 0 ? 1.0 : -1.0) * sections[cell].getLength() * (pressure[cell] - mean);
    }
    return Math.abs(projection) / totalLength;
  }

  private static double[][] states(TwoFluidSection[] sections) {
    double[][] result = new double[sections.length][];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].getStateVector();
    }
    return result;
  }

  private static double[] pressures(TwoFluidSection[] sections) {
    double[] result = new double[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].getPressure();
    }
    return result;
  }

  private static double[] areas(TwoFluidSection[] sections) {
    double[] result = new double[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].getArea();
    }
    return result;
  }

  private static double[] inventories(TwoFluidSection[] sections) {
    double[] result = new double[3];
    for (TwoFluidSection section : sections) {
      for (int phase = 0; phase < 3; phase++) {
        result[phase] += section.getStateVector()[phase] * section.getLength();
      }
    }
    return result;
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] result = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].clone();
    }
    return result;
  }

  private static void assertSectionsEqual(TwoFluidSection[] expected, TwoFluidSection[] actual) {
    assertMatrixEquals(states(expected), states(actual), 0.0);
    assertArrayEquals(pressures(expected), pressures(actual), 0.0);
    for (int cell = 0; cell < expected.length; cell++) {
      assertEquals(expected[cell].getGasDensity(), actual[cell].getGasDensity(), 0.0);
      assertEquals(expected[cell].getGasHoldup(), actual[cell].getGasHoldup(), 0.0);
      assertEquals(expected[cell].getOilHoldup(), actual[cell].getOilHoldup(), 0.0);
      assertEquals(expected[cell].getWaterHoldup(), actual[cell].getWaterHoldup(), 0.0);
    }
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], tolerance, "row=" + row);
    }
  }

  private static void assertConverged(Result result) {
    assertTrue(result.isConverged(), () -> result.getTerminationReason() + ": " + result.getMaximumScaledResidual());
  }

  private static double[] solveLinear(double[][] matrix, double[] rightHandSide) throws Exception {
    Method solve = UnsplitTransientSolver.class.getDeclaredMethod("solveDense", double[][].class, double[].class);
    solve.setAccessible(true);
    return (double[]) solve.invoke(null, matrix, rightHandSide);
  }

  private static final class PublishedDiagnostics {
    private final MassBalanceRate massBalance;
    private final double[][] faces;
    private final double[][] forces;
    private final double timeScale;
    private final double outletPressure;
    private final boolean backflow;

    private PublishedDiagnostics(TwoFluidConservationEquations equations) {
      massBalance = equations.getLastMassBalanceRate();
      faces = equations.getLastPhaseMassFaceFluxes();
      forces = equations.getLastMomentumSourceForcesPerLength();
      timeScale = equations.getPressureInterpolationTimeScale();
      outletPressure = equations.getOutletBoundaryPressure();
      backflow = equations.isOutletBackflowClamped();
    }

    private void assertUnchanged(TwoFluidConservationEquations equations) {
      assertSame(massBalance, equations.getLastMassBalanceRate());
      assertMatrixEquals(faces, equations.getLastPhaseMassFaceFluxes(), 0.0);
      assertMatrixEquals(forces, equations.getLastMomentumSourceForcesPerLength(), 0.0);
      assertEquals(timeScale, equations.getPressureInterpolationTimeScale(), 0.0);
      assertEquals(outletPressure, equations.getOutletBoundaryPressure(), 0.0);
      assertEquals(backflow, equations.isOutletBackflowClamped());
    }
  }

  private static final class ThrowingEquations extends TwoFluidConservationEquations {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure = new IllegalStateException("Injected after spatial diagnostics");
    private double expectedTimeScale;
    private boolean fail;

    @Override
    public double[][] calcRHS(TwoFluidSection[] sections, double dx) {
      assertEquals(expectedTimeScale, getPressureInterpolationTimeScale(), 0.0);
      double[][] result = super.calcRHS(sections, dx);
      if (fail) {
        throw failure;
      }
      return result;
    }
  }
}
