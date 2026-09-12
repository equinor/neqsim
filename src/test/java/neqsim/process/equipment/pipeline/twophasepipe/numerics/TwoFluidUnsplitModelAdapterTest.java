package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TwoFluidUnsplitModelAdapterTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidUnsplitModelAdapterTest.class);

  @ParameterizedTest
  @CsvSource({ "4, 0.05, false", "4, 0.025, false", "8, 0.05, false", "8, 0.025, false", "4, 0.05, true",
      "4, 0.025, true", "8, 0.05, true", "8, 0.025, true" })
  void fiveSecondIsothermalThreePhaseIntervalsConserveTheAcceptedTransportLedger(int cells, double dt,
      boolean interfacialPressure) {
    double length = 40.0;
    TwoFluidSection[] accepted = new TwoFluidSection[cells];
    for (int cell = 0; cell < cells; cell++) {
      accepted[cell] = section((cell + 0.5) * length / cells);
      accepted[cell].setLength(length / cells);
    }
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setAllowOutletPhaseBackflow(true);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setEnableInterfacialPressure(interfacialPressure);
    equations.setInletPhaseFlowBoundaryState(accepted[0]);
    MassBalanceRate published = equations.getLastMassBalanceRate();
    double[] initialMass = inventories(accepted);
    double[] boundaryTransfer = new double[3];
    double maximumResidual = 0.0;
    long evaluations = 0;
    int rejectedAttempts = 0;
    int acceptedSubsteps = 0;
    double minimumStep = dt;
    double maximumSpeed = 0.0;
    double maximumPressureDeparture = 0.0;
    int steps = (int) Math.round(5.0 / dt);
    UnsplitTransientSolver solver = solver();
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations,
        (cell, state, pressure, time) -> new double[] { 40.0 * pressure / 5.0e6, 700.0, 1000.0 }, solver);
    for (int step = 0; step < steps; step++) {
      double time = step * dt;
      PreparedInterval interval = integrator.prepareInterval(accepted, length / cells, dt, time, 5.0e6, true, 1.0e-8);
      assertEquals(time + dt, interval.getEndTimeSeconds(), 0.0);
      assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
      rejectedAttempts += interval.getRejectedAttempts();
      evaluations += interval.getModelEvaluations();
      for (PreparedStep prepared : interval.getSubsteps()) {
        acceptedSubsteps++;
        minimumStep = Math.min(minimumStep, prepared.getTimeStepSeconds());
        assertFalse(prepared.getMidpointEvaluation().isOutletBackflowClamped());
        maximumResidual = Math.max(maximumResidual, prepared.getMaximumScaledResidual());
        MassBalanceRate balance = prepared.getMidpointEvaluation().getMassBalanceRate();
        for (int phase = 0; phase < 3; phase++) {
          boundaryTransfer[phase] += prepared.getTimeStepSeconds() * (balance.getInletMassFlowKgPerSecond()[phase]
              - balance.getOutletMassFlowKgPerSecond()[phase] + balance.getSourceMassFlowKgPerSecond()[phase]);
        }
        for (TwoFluidSection cell : prepared.getEndpointSections()) {
          maximumSpeed = Math.max(maximumSpeed, Math.abs(cell.getGasVelocity()));
          maximumSpeed = Math.max(maximumSpeed, Math.abs(cell.getOilVelocity()));
          maximumSpeed = Math.max(maximumSpeed, Math.abs(cell.getWaterVelocity()));
          maximumPressureDeparture = Math.max(maximumPressureDeparture, Math.abs(cell.getPressure() - 5.0e6));
        }
      }
      // Only this test-owned local state is advanced; no TwoFluidPipe clock, report or stream is committed.
      accepted = interval.getEndpointSections();
      assertSame(published, equations.getLastMassBalanceRate());
    }
    assertTrue(maximumSpeed < 10.0, "The bounded flowing fixture must not develop runaway phase velocities");
    assertTrue(maximumPressureDeparture < 2.5e5, "Pressure must remain within 5% of the 5 MPa boundary");
    double maximumMassResidual = 0.0;
    double[] finalMass = inventories(accepted);
    for (int phase = 0; phase < 3; phase++) {
      double residual = (finalMass[phase] - initialMass[phase] - boundaryTransfer[phase])
          / Math.max(1.0, initialMass[phase]);
      maximumMassResidual = Math.max(maximumMassResidual, Math.abs(residual));
      assertEquals(0.0, residual, 1.0e-8);
    }
    logger.info(
        "Unsplit synthetic isothermal conservation: cells={}, maximumDt={}, interfacialPressure={}, duration=5 s, "
            + "evaluations={}, acceptedSubsteps={}, rejectedAttempts={}, minimumDt={}, maximumSpeed={}, "
            + "maximumPressureDeparturePa={}, maximumScaledResidual={}, maximumRelativePhaseMassResidual={}",
        cells, dt, interfacialPressure, evaluations, acceptedSubsteps, rejectedAttempts, minimumStep, maximumSpeed,
        maximumPressureDeparture, maximumResidual, maximumMassResidual);
  }

  @Test
  void quiescentClosedThreePhaseStateRemainsAFixedPointAcrossPreparedSteps() {
    TwoFluidSection[] accepted = { section(0.0), section(10.0), section(20.0) };
    for (TwoFluidSection section : accepted) {
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.updateConservativeVariables();
    }
    double[][] initial = states(accepted);
    double[] initialPressure = pressures(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setClosedBoundaries(true, true);
    for (int step = 0; step < 5; step++) {
      TwoFluidUnsplitModelAdapter adapter = adapter(equations, accepted);
      UnsplitTransientSolver.Result result = solver().solve(states(accepted), pressures(accepted), areas(accepted), 1.0,
          step, Double.NaN, false, adapter);
      assertTrue(result.isConverged());
      assertEquals(0, result.getIterations());
      PreparedStep prepared = adapter.prepareStep(result, 1.0, step, Double.NaN, false, 1.0e-10);
      assertArrayEquals(new double[3], prepared.getMassResidualKg(), 0.0);
      accepted = prepared.getEndpointSections();
      assertMatrixEquals(initial, states(accepted));
      assertArrayEquals(initialPressure, pressures(accepted), 0.0);
    }
  }

  @Test
  void rejectsMalformedAcceptedTemplatesBeforeCallingClosures() {
    TwoFluidSection template = section(0.0);
    template.setPressure(Double.NaN);
    assertThrows(IllegalArgumentException.class,
        () -> adapter(isothermalEquations(), new TwoFluidSection[] { template }));
    template.setPressure(5.0e6);
    template.setOilMassPerLength(-1.0);
    assertThrows(IllegalArgumentException.class,
        () -> adapter(isothermalEquations(), new TwoFluidSection[] { template }));
    template.setOilMassPerLength(1.0);
    template.setLength(0.0);
    assertThrows(IllegalArgumentException.class,
        () -> adapter(isothermalEquations(), new TwoFluidSection[] { template }));
  }

  @Test
  void delegatesActiveSetOwnershipWithDefensiveStateCopies() {
    TwoFluidSection[] accepted = { section(0.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    double[][] state = { accepted[0].getStateVector() };
    double[] pressure = { accepted[0].getPressure() };
    int[] calls = new int[3];
    TwoFluidUnsplitModelAdapter.ActiveSetController controller = new TwoFluidUnsplitModelAdapter.ActiveSetController() {
      private static final long serialVersionUID = 1L;

      @Override
      public void beginLinearization(double[][] trialState, double[] trialPressure) {
        calls[0]++;
        trialState[0][0] = -1.0;
        trialPressure[0] = -1.0;
      }

      @Override
      public void endLinearization() {
        calls[1]++;
      }

      @Override
      public boolean update(double[][] trialState, double[] trialPressure) {
        calls[2]++;
        trialState[0][1] = -1.0;
        trialPressure[0] = -1.0;
        return true;
      }
    };
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 10.0,
        (cell, conservativeState, cellPressure, time) -> new double[] { 40.0, 700.0, 1000.0 }, controller);

    adapter.beginLinearization(state, pressure);
    assertTrue(adapter.updateActiveSet(state, pressure));
    adapter.endLinearization();

    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertEquals(1, calls[2]);
    assertTrue(state[0][0] >= 0.0);
    assertTrue(state[0][1] >= 0.0);
    assertTrue(pressure[0] > 0.0);
  }

  @Test
  void preparesARealThreePhaseStepWithExactNonuniformMeshLedgerWithoutCommitting() throws Exception {
    TwoFluidSection[] accepted = { section(0.0), section(5.0), section(15.0) };
    accepted[0].setLength(5.0);
    accepted[2].setLength(15.0);
    TwoFluidConservationEquations equations = isothermalEquations();
    equations.setAllowOutletPhaseBackflow(true);
    equations.setMomentumForceDiagnosticsEnabled(true);
    equations.setOutletBoundaryPressure(4.8e6);
    equations.calcRHS(cloneSections(accepted), 10.0);
    MassBalanceRate published = equations.getLastMassBalanceRate();
    double[][] publishedFaces = equations.getLastPhaseMassFaceFluxes();
    double[][] initial = states(accepted);
    double[] initialPressure = pressures(accepted);
    TwoFluidUnsplitModelAdapter adapter = adapter(equations, accepted);
    UnsplitTransientSolver solver = solver();
    double dt = 0.002;
    double start = 2.0;
    double boundary = 4.999e6;
    UnsplitTransientSolver.Result result = solver.solve(initial, initialPressure, areas(accepted), dt, start, boundary,
        true, adapter);
    assertTrue(result.isConverged(), () -> result.getTerminationReason() + ": " + result.getMaximumScaledResidual());

    PreparedStep prepared = adapter.prepareStep(result, dt, start, boundary, true, 1.0e-8);

    assertTrue(prepared.getMaximumScaledResidual() <= 1.0e-8);
    assertEquals(dt, prepared.getTimeStepSeconds(), 0.0);
    assertEquals(start, prepared.getStartTimeSeconds(), 0.0);
    assertSame(published, equations.getLastMassBalanceRate());
    assertMatrixEquals(publishedFaces, equations.getLastPhaseMassFaceFluxes());
    assertEquals(4.8e6, equations.getOutletBoundaryPressure(), 0.0);
    assertMatrixEquals(initial, states(accepted));
    assertArrayEquals(initialPressure, pressures(accepted), 0.0);
    TwoFluidSection[] endpoint = prepared.getEndpointSections();
    assertMatrixEquals(result.getState(), states(endpoint));
    assertArrayEquals(result.getPressure(), pressures(endpoint), 0.0);
    assertNotEquals(boundary, endpoint[2].getPressure(),
        "The boundary face must not replace the endpoint cell pressure");
    for (double residual : prepared.getMassResidualKg()) {
      assertEquals(0.0, residual, 1.0e-7);
    }
    for (int phase = 0; phase < 3; phase++) {
      double inventory = 0.0;
      for (int cell = 0; cell < accepted.length; cell++) {
        inventory += initial[cell][phase] * accepted[cell].getLength();
      }
      assertEquals(inventory, prepared.getInitialMassKg()[phase], 0.0);
    }
    double savedMass = prepared.getFinalMassKg()[0];
    prepared.getFinalMassKg()[0] = -1.0;
    endpoint[0].setGasMassPerLength(-1.0);
    assertEquals(savedMass, prepared.getFinalMassKg()[0], 0.0);
    assertTrue(prepared.getEndpointSections()[0].getGasMassPerLength() > 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(prepared);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      PreparedStep restored = (PreparedStep) input.readObject();
      assertMatrixEquals(states(prepared.getEndpointSections()), states(restored.getEndpointSections()));
      assertArrayEquals(prepared.getMassResidualKg(), restored.getMassResidualKg(), 0.0);
    }
  }

  @Test
  void rejectsChangedTimeStepBoundaryAndUnsupportedSourceSplits() {
    TwoFluidSection[] accepted = { section(0.0), section(10.0) };
    double[][] before = states(accepted);
    TwoFluidConservationEquations equations = isothermalEquations();
    TwoFluidUnsplitModelAdapter adapter = adapter(equations, accepted);
    double dt = 0.002;
    UnsplitTransientSolver.Result result = solver().solve(states(accepted), pressures(accepted), areas(accepted), dt,
        0.0, 4.999e6, true, adapter);
    assertTrue(result.isConverged(), () -> result.getTerminationReason() + ": " + result.getMaximumScaledResidual());
    adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8);

    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, 10.0 * dt, 0.0, 4.999e6, true, 1.0e-8));
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.0e6, true, 1.0e-8));
    equations.setIncludeEnergyEquation(true);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(true);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    equations.setIncludeMassTransfer(false);
    equations.setHeatTransferCoefficient(10.0);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    equations.setEnableHeatTransfer(false);
    equations.setImplicitInterfacialPressure(true);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    equations.setImplicitInterfacialPressure(false);
    equations.setEnableStiffBubbleDrag(true);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    equations.setEnableStiffBubbleDrag(false);
    equations.setConservativeSlugForceIntegrationEnabled(true);
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(result, dt, 0.0, 4.999e6, true, 1.0e-8));
    assertMatrixEquals(before, states(accepted));
  }

  @Test
  void rejectsAnUnconvergedResultAndAConvergedResultFromAnotherOperator() {
    TwoFluidSection[] accepted = { section(0.0) };
    TwoFluidConservationEquations equations = isothermalEquations();
    TwoFluidUnsplitModelAdapter adapter = adapter(equations, accepted);
    UnsplitTransientSolver.Model otherOperator = (state, pressure, closureState, closurePressure, time, outlet,
        fixed) -> new UnsplitTransientSolver.Evaluation(new double[][] { new double[6] },
            new double[][] { { 40.0 }, { 700.0 }, { 1000.0 } });
    UnsplitTransientSolver.Result unrelated = solver().solve(states(accepted), pressures(accepted), areas(accepted),
        0.1, 0.0, Double.NaN, false, otherOperator);
    assertTrue(unrelated.isConverged());
    assertThrows(IllegalStateException.class,
        () -> adapter.prepareStep(unrelated, 0.1, 0.0, Double.NaN, false, 1.0e-8));

    UnsplitTransientSolver.Model singularOperator = (state, pressure, closureState, closurePressure, time, outlet,
        fixed) -> new UnsplitTransientSolver.Evaluation(new double[][] { { 1.0, 0.0, 0.0, 0.0, 0.0, 0.0 } },
            new double[][] { { 40.0 }, { 700.0 }, { 1000.0 } });
    UnsplitTransientSolver.Result failed = solver().solve(states(accepted), pressures(accepted), areas(accepted), 0.1,
        0.0, Double.NaN, false, singularOperator);
    assertFalse(failed.isConverged());
    assertThrows(IllegalStateException.class, () -> adapter.prepareStep(failed, 0.1, 0.0, Double.NaN, false, 1.0e-8));
    assertThrows(IllegalArgumentException.class,
        () -> adapter.prepareStep(unrelated, 0.0, 0.0, Double.NaN, false, 1.0e-8));
  }

  @Test
  void restoresTheExternalPressureAndDiagnosticsWhenAnRhsProbeFails() {
    TwoFluidSection[] accepted = { section(0.0), section(10.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations() {
      private static final long serialVersionUID = 1L;

      @Override
      public double[][] calcRHS(TwoFluidSection[] sections, double dx) {
        super.calcRHS(sections, dx);
        throw new IllegalStateException("injected RHS failure");
      }
    };
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setOutletBoundaryPressure(4.8e6);
    MassBalanceRate published = equations.getLastMassBalanceRate();
    TwoFluidUnsplitModelAdapter adapter = adapter(equations, accepted);
    double[][] before = states(accepted);

    assertThrows(IllegalStateException.class,
        () -> adapter.evaluate(before, pressures(accepted), before, pressures(accepted), 0.0, 4.0e6, true));

    assertSame(published, equations.getLastMassBalanceRate());
    assertEquals(4.8e6, equations.getOutletBoundaryPressure(), 0.0);
    assertMatrixEquals(before, states(accepted));
  }

  @Test
  void usesTheSameMidpointCoefficientTimeForResidualAndEndpointPreparation() {
    TwoFluidSection[] accepted = { section(0.0) };
    TwoFluidConservationEquations equations = isothermalEquations();
    double start = 1.0;
    double dt = 0.002;
    int[] evaluations = { 0 };
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 10.0,
        (cell, state, pressure, time) -> {
          assertEquals(start + 0.5 * dt, time, 0.0);
          evaluations[0]++;
          return new double[] { 40.0 + 1.0e-6 * (pressure - 5.0e6), 700.0, 1000.0 };
        });
    UnsplitTransientSolver.Result result = solver().solve(states(accepted), pressures(accepted), areas(accepted), dt,
        start, Double.NaN, false, adapter);
    assertTrue(result.isConverged());
    int before = evaluations[0];

    adapter.prepareStep(result, dt, start, Double.NaN, false, 1.0e-8);

    assertEquals(2, evaluations[0] - before, "Midpoint and endpoint each require density evaluation");
    assertThrows(IllegalArgumentException.class, () -> adapter.evaluate(states(accepted), pressures(accepted),
        states(accepted), pressures(accepted), Double.NaN, Double.NaN, false));
  }

  private static TwoFluidConservationEquations isothermalEquations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    return equations;
  }

  private static TwoFluidUnsplitModelAdapter adapter(TwoFluidConservationEquations equations,
      TwoFluidSection[] sections) {
    return new TwoFluidUnsplitModelAdapter(equations, sections, 10.0,
        (cell, state, pressure, time) -> new double[] { 40.0 + 1.0e-6 * (pressure - 5.0e6), 700.0, 1000.0 });
  }

  private static UnsplitTransientSolver solver() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-10);
    return solver;
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

  @Test
  void repeatedResidualProbesAreTransactionalAndUseTheOutletFacePressure() {
    TwoFluidSection[] accepted = { section(0.0), section(10.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);

    equations.calcRHS(cloneSections(accepted), 10.0);
    TwoFluidConservationEquations.MassBalanceRate acceptedBalance = equations.getLastMassBalanceRate();
    double[][] acceptedFaces = equations.getLastPhaseMassFaceFluxes();
    boolean acceptedBackflow = equations.isOutletBackflowClamped();

    double[][] state = { accepted[0].getStateVector(), accepted[1].getStateVector() };
    double[][] acceptedState = { state[0].clone(), state[1].clone() };
    double[] pressure = { accepted[0].getPressure(), accepted[1].getPressure() };
    double[] area = { accepted[0].getArea(), accepted[1].getArea() };
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 10.0,
        (cell, conservativeState, cellPressure,
            time) -> new double[] { 40.0 + 1.0e-6 * (cellPressure - 5.0e6), 700.0, 1000.0 });
    UnsplitTransientSolver solver = new UnsplitTransientSolver();

    double[] free = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, Double.NaN, false, adapter);
    double[] repeated = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, Double.NaN, false, adapter);
    double[] fixed = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, 4.0e6, true, adapter);

    assertArrayEquals(free, repeated, 0.0);
    assertNotEquals(free[10], fixed[10], "Outlet gas momentum must use the prescribed face pressure");
    assertNotEquals(free[11], fixed[11], "Outlet oil momentum must use the prescribed face pressure");
    assertSame(acceptedBalance, equations.getLastMassBalanceRate());
    assertMatrixEquals(acceptedFaces, equations.getLastPhaseMassFaceFluxes());
    assertEquals(acceptedBackflow, equations.isOutletBackflowClamped());
    assertArrayEquals(acceptedState[0], accepted[0].getStateVector(), 0.0);
    assertArrayEquals(acceptedState[1], accepted[1].getStateVector(), 0.0);
    assertArrayEquals(pressure, new double[] { accepted[0].getPressure(), accepted[1].getPressure() }, 0.0);
  }

  private static TwoFluidSection section(double position) {
    TwoFluidSection section = new TwoFluidSection(position, 10.0, 0.1, 0.0);
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

  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], 0.0);
    }
  }
}
