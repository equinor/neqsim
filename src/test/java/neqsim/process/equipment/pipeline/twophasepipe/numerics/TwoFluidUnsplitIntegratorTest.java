package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.MassBalanceRate;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.IntervalPreparationException;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import org.junit.jupiter.api.Test;

class TwoFluidUnsplitIntegratorTest {

  @Test
  void matchesASingleVerifiedStepWithImmutableNonuniformMassAccounting() throws Exception {
    TwoFluidSection[] accepted = nonuniformSections();
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setAllowOutletPhaseBackflow(true);
    equations.setMomentumForceDiagnosticsEnabled(true);
    equations.setOutletBoundaryPressure(4.8e6);
    equations.calcRHS(cloneSections(accepted), 10.0);
    PublishedDiagnostics published = new PublishedDiagnostics(equations);
    UnsplitTransientSolver solver = solver();
    double start = 2.0;
    double duration = 1.0 / 512.0;
    double outlet = 4.999e6;

    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 10.0, densityModel());
    UnsplitTransientSolver.Result candidate = solver.solve(states(accepted), pressures(accepted), areas(accepted),
        duration, start, outlet, true, adapter);
    assertTrue(candidate.isConverged(),
        () -> candidate.getTerminationReason() + ": " + candidate.getMaximumScaledResidual());
    PreparedStep direct = adapter.prepareStep(candidate, duration, start, outlet, true, 1.0e-8);

    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, densityModel(), solver, 0, 1);
    PreparedInterval interval = integrator.prepareInterval(accepted, 10.0, duration, start, outlet, true, 1.0e-8);

    assertEquals(1, interval.getSubsteps().size());
    assertEquals(0, interval.getRejectedAttempts());
    assertEquals(candidate.getModelEvaluations(), interval.getModelEvaluations());
    assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
    assertEquals(start, interval.getStartTimeSeconds(), 0.0);
    assertEquals(start + duration, interval.getEndTimeSeconds(), 0.0);
    assertEquals(duration, interval.getSubsteps().get(0).getTimeStepSeconds(), 0.0);
    assertSectionsEqual(direct.getEndpointSections(), interval.getEndpointSections());
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);

    double[][] expectedFaces = direct.getMidpointEvaluation().getPhaseMassFaceFluxes();
    double[][] expectedSources = direct.getMidpointEvaluation().getPhaseMassSourcesPerLength();
    for (int face = 0; face < expectedFaces.length; face++) {
      for (int phase = 0; phase < 3; phase++) {
        expectedFaces[face][phase] *= duration;
      }
    }
    for (int cell = 0; cell < expectedSources.length; cell++) {
      for (int phase = 0; phase < 3; phase++) {
        expectedSources[cell][phase] *= duration * accepted[cell].getLength();
      }
    }
    assertMatrixEquals(expectedFaces, interval.getPhaseMassFaceTransferKg());
    assertMatrixEquals(expectedSources, interval.getPhaseMassSourceTransferKg());
    assertArrayEquals(direct.getMassResidualKg(), interval.getMassResidualKg(), 1.0e-12);

    double[] initialMass = inventories(accepted);
    double[] finalMass = inventories(interval.getEndpointSections());
    double[][] endpointState = states(interval.getEndpointSections());
    double[] independentResidual = new double[3];
    for (int phase = 0; phase < 3; phase++) {
      independentResidual[phase] = finalMass[phase] - initialMass[phase] - expectedFaces[0][phase]
          + expectedFaces[accepted.length][phase];
      for (int cell = 0; cell < accepted.length; cell++) {
        independentResidual[phase] -= expectedSources[cell][phase];
        double cellInventoryChange = (endpointState[cell][phase] - accepted[cell].getStateVector()[phase])
            * accepted[cell].getLength();
        double cellTransfer = expectedFaces[cell][phase] - expectedFaces[cell + 1][phase]
            + expectedSources[cell][phase];
        double cellScale = Math.max(1.0, accepted[cell].getStateVector()[phase] * accepted[cell].getLength());
        assertEquals(0.0, (cellInventoryChange - cellTransfer) / cellScale, 1.0e-8);
      }
      assertEquals(0.0, independentResidual[phase] / Math.max(1.0, initialMass[phase]), 1.0e-8);
    }
    assertArrayEquals(independentResidual, interval.getMassResidualKg(), 1.0e-12);

    TwoFluidSection[] savedEndpoint = interval.getEndpointSections();
    interval.getEndpointSections()[0].setGasMassPerLength(-1.0);
    interval.getEndpointSections()[0].setPressure(1.0);
    interval.getPhaseMassFaceTransferKg()[0][0] = -1.0;
    interval.getPhaseMassSourceTransferKg()[0][0] = -1.0;
    interval.getMassResidualKg()[0] = -1.0;
    interval.getSubsteps().get(0).getMidpointEvaluation().getPhaseMassFaceFluxes()[0][0] = -1.0;
    assertThrows(UnsupportedOperationException.class, () -> interval.getSubsteps().clear());
    assertSectionsEqual(savedEndpoint, interval.getEndpointSections());
    assertMatrixEquals(expectedFaces, interval.getPhaseMassFaceTransferKg());
    assertMatrixEquals(expectedSources, interval.getPhaseMassSourceTransferKg());
    assertArrayEquals(independentResidual, interval.getMassResidualKg(), 1.0e-12);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(interval);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      PreparedInterval restored = (PreparedInterval) input.readObject();
      assertSectionsEqual(savedEndpoint, restored.getEndpointSections());
      assertMatrixEquals(expectedFaces, restored.getPhaseMassFaceTransferKg());
      assertMatrixEquals(expectedSources, restored.getPhaseMassSourceTransferKg());
      assertArrayEquals(interval.getMassResidualKg(), restored.getMassResidualKg(), 0.0);
      assertEquals(interval.getStartTimeSeconds(), restored.getStartTimeSeconds(), 0.0);
      assertEquals(interval.getEndTimeSeconds(), restored.getEndTimeSeconds(), 0.0);
      assertEquals(interval.getRejectedAttempts(), restored.getRejectedAttempts());
      assertEquals(interval.getModelEvaluations(), restored.getModelEvaluations());
      assertEquals(interval.getSubsteps().size(), restored.getSubsteps().size());
      assertThrows(UnsupportedOperationException.class, () -> restored.getSubsteps().clear());
    }
    assertEquals(1.0e-10, solver.getRelativeTolerance(), 0.0);
    published.assertUnchanged(equations);
    assertSectionsEqual(original, accepted);
  }

  @Test
  void rejectsUnsupportedOperatorsBeforeCallingTheDensityModel() {
    TwoFluidSection[] accepted = nonuniformSections();
    TwoFluidConservationEquations[] unsupported = { isothermalEquations(), isothermalEquations(), isothermalEquations(),
        isothermalEquations(), isothermalEquations(), isothermalEquations() };
    unsupported[0].setIncludeEnergyEquation(true);
    unsupported[1].setIncludeMassTransfer(true);
    unsupported[2].setHeatTransferCoefficient(10.0);
    unsupported[3].setImplicitInterfacialPressure(true);
    unsupported[4].setEnableStiffBubbleDrag(true);
    unsupported[5].setConservativeSlugForceIntegrationEnabled(true);
    int[] calls = { 0 };
    PhaseDensityModel density = (cell, state, pressure, time) -> {
      calls[0]++;
      throw new AssertionError("Unsupported operators must fail before evaluating densities");
    };
    for (TwoFluidConservationEquations equations : unsupported) {
      TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, density, solver(), 3, 8);
      assertThrows(IllegalStateException.class,
          () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, 5.0e6, true, 1.0e-8));
    }
    assertEquals(0, calls[0]);
  }

  @Test
  void rejectsInvalidIntervalsBeforeCallingTheDensityModel() {
    TwoFluidSection[] accepted = nonuniformSections();
    int[] calls = { 0 };
    PhaseDensityModel density = (cell, state, pressure, time) -> {
      calls[0]++;
      throw new AssertionError("Invalid arguments must fail before evaluating densities");
    };
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(isothermalEquations(), density, solver(), 3,
        8);
    double[][] invalidTimes = { { 0.0, 0.0 }, { -1.0, 0.0 }, { Double.NaN, 0.0 }, { Double.POSITIVE_INFINITY, 0.0 },
        { 1.0, Double.NaN }, { 1.0, Double.POSITIVE_INFINITY }, { 1.0, 1.0e20 },
        { Double.MAX_VALUE, Double.MAX_VALUE } };
    for (double[] time : invalidTimes) {
      assertThrows(IllegalArgumentException.class,
          () -> integrator.prepareInterval(accepted, 10.0, time[0], time[1], 5.0e6, true, 1.0e-8));
    }
    assertThrows(IllegalArgumentException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, 0.0, true, 1.0e-8));
    assertThrows(IllegalArgumentException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, Double.NaN, true, 1.0e-8));
    assertThrows(IllegalArgumentException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, 5.0e6, true, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, 5.0e6, true, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> integrator.prepareInterval(accepted, 0.0, 0.1, 0.0, 5.0e6, true, 1.0e-8));
    assertEquals(0, calls[0]);
  }

  @Test
  void retryExhaustionLeavesCallerAndEquationStateUntouched() {
    TwoFluidSection[] accepted = nonuniformSections();
    accepted[1].setGasVelocity(4.0);
    accepted[1].setOilVelocity(0.7);
    accepted[1].setWaterVelocity(0.3);
    accepted[1].updateConservativeVariables();
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setAllowOutletPhaseBackflow(true);
    equations.setMomentumForceDiagnosticsEnabled(true);
    equations.setOutletBoundaryPressure(4.8e6);
    equations.calcRHS(cloneSections(accepted), 10.0);
    PublishedDiagnostics published = new PublishedDiagnostics(equations);
    UnsplitTransientSolver solver = solver();
    solver.setMaximumIterations(1);
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, densityModel(), solver, 0, 4);
    double start = 1.25;
    double duration = 0.2;

    IntervalPreparationException failure = assertThrows(IntervalPreparationException.class,
        () -> integrator.prepareInterval(accepted, 10.0, duration, start, 4.0e6, true, 1.0e-8));

    assertEquals(start, failure.getFailureTimeSeconds(), 0.0);
    assertEquals(start + duration - start, failure.getAttemptedTimeStepSeconds(), 0.0);
    assertEquals(0, failure.getPreparedSubsteps());
    assertEquals(1, failure.getRejectedAttempts());
    assertNotNull(failure.getTerminationReason());
    assertEquals(1, solver.getMaximumIterations());
    assertEquals(1.0e-10, solver.getRelativeTolerance(), 0.0);
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);
  }

  @Test
  void substepExhaustionDiscardsALocallyAcceptedPrefix() {
    TwoFluidSection[] accepted = { section(0.0, 10.0) };
    accepted[0].setGasVelocity(0.0);
    accepted[0].setLiquidVelocity(0.0);
    accepted[0].setOilVelocity(0.0);
    accepted[0].setWaterVelocity(0.0);
    accepted[0].updateConservativeVariables();
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setClosedBoundaries(true, true);
    equations.setMomentumForceDiagnosticsEnabled(true);
    equations.setOutletBoundaryPressure(4.8e6);
    equations.calcRHS(cloneSections(accepted), 10.0);
    PublishedDiagnostics published = new PublishedDiagnostics(equations);
    // Manufactured incompatible volume closure: the full interval has no pressure root,
    // while its left half is the original quiescent fixed point. This is a rollback fixture.
    PhaseDensityModel density = (cell, state, pressure,
        time) -> new double[] { time < 0.5 ? 40.0 : 80.0, 700.0, 1000.0 };
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, density, solver(), 2, 1);

    IntervalPreparationException failure = assertThrows(IntervalPreparationException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 1.0, 0.0, Double.NaN, false, 1.0e-8));

    assertEquals(0.5, failure.getFailureTimeSeconds(), 0.0);
    assertEquals(0.5, failure.getAttemptedTimeStepSeconds(), 0.0);
    assertEquals(1, failure.getPreparedSubsteps());
    assertEquals(1, failure.getRejectedAttempts());
    assertNull(failure.getTerminationReason(), "The interval stopped at the accepted-substep budget");
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);
  }

  @Test
  void propagatesADensityCallbackFailureWithoutRetryingOrPublishing() {
    TwoFluidSection[] accepted = nonuniformSections();
    TwoFluidSection[] original = cloneSections(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setOutletBoundaryPressure(4.8e6);
    equations.calcRHS(cloneSections(accepted), 10.0);
    PublishedDiagnostics published = new PublishedDiagnostics(equations);
    IllegalStateException injected = new IllegalStateException("Injected density callback failure");
    int[] calls = { 0 };
    PhaseDensityModel density = (cell, state, pressure, time) -> {
      calls[0]++;
      throw injected;
    };
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations, density, solver(), 3, 8);

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> integrator.prepareInterval(accepted, 10.0, 0.1, 0.0, 4.0e6, true, 1.0e-8));

    assertSame(injected, failure);
    assertEquals(1, calls[0]);
    assertSectionsEqual(original, accepted);
    published.assertUnchanged(equations);
  }

  private static TwoFluidConservationEquations isothermalEquations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    return equations;
  }

  private static UnsplitTransientSolver solver() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-10);
    return solver;
  }

  private static PhaseDensityModel densityModel() {
    return (cell, state, pressure, time) -> new double[] { 40.0 + 1.0e-6 * (pressure - 5.0e6), 700.0, 1000.0 };
  }

  private static TwoFluidSection[] nonuniformSections() {
    return new TwoFluidSection[] { section(2.5, 5.0), section(10.0, 10.0), section(22.5, 15.0) };
  }

  private static TwoFluidSection section(double position, double length) {
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

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      copy[cell] = sections[cell].clone();
    }
    return copy;
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
      double[] state = section.getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        result[phase] += state[phase] * section.getLength();
      }
    }
    return result;
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], 0.0);
    }
  }

  private static void assertSectionsEqual(TwoFluidSection[] expected, TwoFluidSection[] actual) {
    assertMatrixEquals(states(expected), states(actual));
    assertArrayEquals(pressures(expected), pressures(actual), 0.0);
    for (int cell = 0; cell < expected.length; cell++) {
      assertEquals(expected[cell].getLength(), actual[cell].getLength(), 0.0);
      assertEquals(expected[cell].getGasDensity(), actual[cell].getGasDensity(), 0.0);
      assertEquals(expected[cell].getOilDensity(), actual[cell].getOilDensity(), 0.0);
      assertEquals(expected[cell].getWaterDensity(), actual[cell].getWaterDensity(), 0.0);
      assertEquals(expected[cell].getGasHoldup(), actual[cell].getGasHoldup(), 0.0);
      assertEquals(expected[cell].getOilHoldup(), actual[cell].getOilHoldup(), 0.0);
      assertEquals(expected[cell].getWaterHoldup(), actual[cell].getWaterHoldup(), 0.0);
      assertEquals(expected[cell].getGasVelocity(), actual[cell].getGasVelocity(), 0.0);
      assertEquals(expected[cell].getOilVelocity(), actual[cell].getOilVelocity(), 0.0);
      assertEquals(expected[cell].getWaterVelocity(), actual[cell].getWaterVelocity(), 0.0);
    }
  }

  private static final class PublishedDiagnostics {
    private final MassBalanceRate balance;
    private final double[][] faces;
    private final double[][] forces;
    private final boolean backflowClamped;
    private final double outletPressure;

    private PublishedDiagnostics(TwoFluidConservationEquations equations) {
      balance = equations.getLastMassBalanceRate();
      faces = equations.getLastPhaseMassFaceFluxes();
      forces = equations.getLastMomentumSourceForcesPerLength();
      backflowClamped = equations.isOutletBackflowClamped();
      outletPressure = equations.getOutletBoundaryPressure();
    }

    private void assertUnchanged(TwoFluidConservationEquations equations) {
      assertSame(balance, equations.getLastMassBalanceRate());
      assertMatrixEquals(faces, equations.getLastPhaseMassFaceFluxes());
      assertMatrixEquals(forces, equations.getLastMomentumSourceForcesPerLength());
      assertEquals(backflowClamped, equations.isOutletBackflowClamped());
      assertEquals(outletPressure, equations.getOutletBoundaryPressure(), 0.0);
    }
  }
}
