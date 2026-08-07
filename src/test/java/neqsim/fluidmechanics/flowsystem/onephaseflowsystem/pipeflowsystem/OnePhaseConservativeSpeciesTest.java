package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.ConstantAxialDispersion;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests conservative one-phase species transport coupled to hydraulic/EOS state. */
class OnePhaseConservativeSpeciesTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 288.15;
  private static final double PRESSURE_BARA = 70.0;
  private static final double MASS_FLOW_KG_PER_SECOND = 50.0;

  @Test
  void compositionStepClosesEveryComponentAndSynchronizesThermodynamics() {
    PipeFlowSystem pipe = runCompositionStep(40, 30.0);
    OnePhaseSpeciesConservationReport report = pipe.getSpeciesConservationReport();
    OnePhaseFlowConvergenceReport flowReport = pipe.getConvergenceReport();

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.CONVERGED, report.getReason(),
        report.getMessage());
    assertTrue(flowReport.isConverged(), flowReport.getMessage());
    assertTrue(flowReport.getMaximumRelativeDensityResidual() <= flowReport.getDensityRelativeTolerance());
    assertTrue(flowReport.isNonlinearMetricEquationResidual());
    double[] aggregateHistory = flowReport.getNonlinearUpdateHistory();
    double[] massEquationHistory = flowReport.getScaledMassEquationResidualHistory();
    double[] momentumEquationHistory = flowReport.getScaledMomentumEquationResidualHistory();
    assertEquals(flowReport.getNonlinearIterations() + 1, aggregateHistory.length);
    assertEquals(aggregateHistory.length, massEquationHistory.length);
    assertEquals(aggregateHistory.length, momentumEquationHistory.length);
    for (int iteration = 0; iteration < aggregateHistory.length; iteration++) {
      assertEquals(aggregateHistory[iteration],
          Math.max(massEquationHistory[iteration], momentumEquationHistory[iteration]), 0.0);
    }
    assertEquals(massEquationHistory[massEquationHistory.length - 1], flowReport.getMaximumScaledMassEquationResidual(),
        0.0);
    assertEquals(momentumEquationHistory[momentumEquationHistory.length - 1],
        flowReport.getMaximumScaledMomentumEquationResidual(), 0.0);
    massEquationHistory[0] = Double.NaN;
    assertTrue(Double.isFinite(flowReport.getScaledMassEquationResidualHistory()[0]));
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-8, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    assertTrue(report.getMaximumMassFractionSumError() <= 1.0e-12, report.getMessage());
    assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
    assertTrue(report.getCouplingIterations() > 0, report.getMessage());
    assertEquals(report.getCouplingIterations(), report.getMaximumMassFractionChangeHistory().length);
    assertEquals(report.getCouplingIterations(), report.getDensityResidualHistory().length);
    assertTrue(report.getMaximumMassFractionChangeHistory()[report.getCouplingIterations() - 1] <= 1.0e-10,
        report.getMessage());
    assertTrue(report.getDensityResidualHistory()[report.getCouplingIterations() - 1] <= pipe.getConvergenceReport()
        .getDensityRelativeTolerance(), report.getMessage());

    int nitrogen = 1;
    assertEquals(report.getFinalInventoryKg()[nitrogen] - report.getInitialInventoryKg()[nitrogen],
        report.getInletBoundaryMassKg()[nitrogen] - report.getOutletBoundaryMassKg()[nitrogen],
        Math.max(1.0, report.getInitialInventoryKg()[nitrogen]) * 1.0e-8);
    double firstCellNitrogen = pipe.getNode(1).getBulkSystem().getPhase(0).getComponent(nitrogen).getx();
    assertTrue(firstCellNitrogen > 0.05 && firstCellNitrogen < 0.20,
        "The bounded inlet front must enter the first physical cell: " + firstCellNitrogen);
  }

  @Test
  void lowPositiveFlowSteadyInitializationConvergesBeforeConservativeSpeciesStep() {
    PipeFlowSystem first = runCompositionStep(40, 30.0, 2.5);
    PipeFlowSystem repeated = runCompositionStep(40, 30.0, 2.5);
    PipeFlowSystem nearby = createInitializedPipe(40, 15000.0, 1.25);

    OnePhaseFlowConvergenceReport firstFlow = first.getConvergenceReport();
    OnePhaseSpeciesConservationReport firstSpecies = first.getSpeciesConservationReport();
    assertTrue(firstFlow.isConverged(), firstFlow.getMessage());
    assertTrue(firstFlow.isNonlinearMetricEquationResidual(), firstFlow.getMessage());
    assertTrue(firstFlow.getMaximumScaledMassEquationResidual() <= 1.0e-10, firstFlow.getMessage());
    assertTrue(firstFlow.getMaximumScaledMomentumEquationResidual() <= 1.0e-10, firstFlow.getMessage());
    assertTrue(firstSpecies.isConverged(), firstSpecies.getMessage());
    assertTrue(firstSpecies.getMaximumRelativeInventoryResidual() < 1.0e-8, firstSpecies.getMessage());
    assertTrue(firstSpecies.getMaximumThermodynamicMassFractionError() <= 1.0e-10, firstSpecies.getMessage());
    assertArrayEquals(firstSpecies.getFinalInventoryKg(), repeated.getSpeciesConservationReport().getFinalInventoryKg(),
        0.0);
    assertArrayEquals(firstSpecies.getMassFractionProfile()[1],
        repeated.getSpeciesConservationReport().getMassFractionProfile()[1], 0.0);

    OnePhaseFlowConvergenceReport nearbyFlow = nearby.getConvergenceReport();
    assertTrue(nearbyFlow.isConverged(), nearbyFlow.getMessage());
    assertTrue(nearbyFlow.getMaximumScaledMassEquationResidual() <= 1.0e-10, nearbyFlow.getMessage());
    assertTrue(nearbyFlow.getMaximumScaledMomentumEquationResidual() <= 1.0e-10, nearbyFlow.getMessage());
    for (int node = 0; node < nearby.getTotalNumberOfNodes(); node++) {
      assertTrue(Double.isFinite(nearby.getNode(node).getVelocity()));
      assertTrue(nearby.getNode(node).getVelocity() > 0.0,
          "Supported positive-flow initialization must remain positive at node " + node);
    }
  }

  @Test
  void unsupportedLowerFlowSteadyInitializationFailsWithResidualDiagnostics() {
    PipeFlowSystem pipe = createConfiguredPipe(40, 15000.0, 0.1);

    assertFalse(pipe.isFailOnNonConvergence());
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.solveSteadyState(1));
    OnePhaseFlowConvergenceReport report = pipe.getConvergenceReport();

    assertFalse(report.isConverged());
    assertTrue(report.isNonlinearMetricEquationResidual());
    assertTrue(report.getNonlinearIterations() > 0);
    assertEquals(report.getMessage(), exception.getMessage());
    assertTrue(exception.getMessage().contains("scaled equation residual="));
    assertTrue(exception.getMessage().contains("Final scaled continuity residual="));
    assertTrue(exception.getMessage().contains("momentum residual="));
  }

  @Test
  void highResolutionSpeciesStepRetainsHydraulicEosSynchronization() {
    PipeFlowSystem pipe = createInitializedPipe(12, 3000.0);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setSpeciesAdvectionScheme(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2);
    pipe.setAxialDispersionModel(new ConstantAxialDispersion(0.5));
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0 });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    OnePhaseSpeciesConservationReport report = pipe.getSpeciesConservationReport();

    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertEquals(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, report.getTransportDiagnostics().getScheme());
    assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
    assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    assertTrue(report.getTransportDiagnostics().getMaximumCellCourantNumber() > 0.0);
    assertTrue(report.getTransportDiagnostics().getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond() > 0.0);
    assertTrue(report.getTransportDiagnostics().isPhysicalDispersionIncluded());
    assertEquals("constant", report.getTransportDiagnostics().getPhysicalDispersionModelName());
    assertEquals(0.5, report.getTransportDiagnostics().getMaximumPhysicalAxialDispersionM2PerSecond(), 0.0);
    assertTrue(report.getTransportDiagnostics().getCellPecletNumbers()[0] > 0.0);
  }

  @Test
  void conservativeSpeciesStepIsDeterministicAndTimestepSensitive() {
    PipeFlowSystem thirtySecondA = runCompositionStep(12, 30.0);
    PipeFlowSystem thirtySecondB = runCompositionStep(12, 30.0);
    PipeFlowSystem fifteenSecond = runCompositionStep(12, 15.0);

    OnePhaseSpeciesConservationReport reportA = thirtySecondA.getSpeciesConservationReport();
    OnePhaseSpeciesConservationReport reportB = thirtySecondB.getSpeciesConservationReport();
    assertArrayEquals(reportA.getFinalInventoryKg(), reportB.getFinalInventoryKg(), 0.0);
    assertArrayEquals(reportA.getInventoryResidualKg(), reportB.getInventoryResidualKg(), 0.0);
    assertArrayEquals(reportA.getMassFractionProfile()[1], reportB.getMassFractionProfile()[1], 0.0);

    double thirtySecondFront = thirtySecondA.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    double fifteenSecondFront = fifteenSecond.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    assertTrue(thirtySecondFront > fifteenSecondFront,
        "A longer first-order implicit step must advance more inlet tracer mass.");
    assertTrue(fifteenSecond.getSpeciesConservationReport().isConverged());
  }

  @Test
  void multiStepSolvePublishesTimeAlignedPythonAccessibleHistory() {
    PipeFlowSystem first = runThreeStepCompositionEvent();
    PipeFlowSystem repeated = runThreeStepCompositionEvent();

    OnePhaseSpeciesConservationHistory history = first.getSpeciesConservationHistory();
    assertEquals(3, history.size());
    assertArrayEquals(new double[] { 30.0, 60.0, 90.0 }, history.getElapsedTimeSeconds(), 0.0);
    assertEquals(history.toJson(), repeated.getSpeciesConservationHistory().toJson());
    assertTrue(history.toJson().contains("\"elapsedTimeSeconds\""));
    assertTrue(history.toJson().contains("\"finalInventoryKg\""));

    double[] returnedTimes = history.getElapsedTimeSeconds();
    returnedTimes[0] = Double.NaN;
    assertEquals(30.0, history.getElapsedTimeSeconds()[0], 0.0);
    OnePhaseSpeciesConservationReport[] returnedReports = history.getReports();
    returnedReports[0] = null;
    assertTrue(history.getReport(0).isConverged());

    double[] initialInventory = history.getReport(0).getInitialInventoryKg();
    double[] finalInventory = history.getReport(history.size() - 1).getFinalInventoryKg();
    double[] cumulativeInlet = new double[initialInventory.length];
    double[] cumulativeOutlet = new double[initialInventory.length];
    for (OnePhaseSpeciesConservationReport report : history.getReports()) {
      assertTrue(report.isConverged(), report.getMessage());
      for (int component = 0; component < initialInventory.length; component++) {
        cumulativeInlet[component] += report.getInletBoundaryMassKg()[component];
        cumulativeOutlet[component] += report.getOutletBoundaryMassKg()[component];
      }
    }
    for (int component = 0; component < initialInventory.length; component++) {
      double cumulativeResidual = finalInventory[component] - initialInventory[component] - cumulativeInlet[component]
          + cumulativeOutlet[component];
      assertEquals(0.0, cumulativeResidual, Math.max(1.0, initialInventory[component]) * 1.0e-8,
          "history must telescope the component inventory for component " + component);
    }

    OnePhaseSpeciesConservationReport latest = first.getSpeciesConservationReport();
    assertArrayEquals(latest.getFinalInventoryKg(), finalInventory, 0.0);
    assertArrayEquals(latest.getMassFractionProfile()[1],
        history.getReport(history.size() - 1).getMassFractionProfile()[1], 0.0);
  }

  @Test
  void historyBuilderScalesToManyAcceptedStepsAndPreservesValidation() {
    OnePhaseSpeciesConservationHistory.Builder builder = OnePhaseSpeciesConservationHistory.builder();
    OnePhaseSpeciesConservationReport report = OnePhaseSpeciesConservationReport.notRun();
    builder.append(0.5, report);
    OnePhaseSpeciesConservationHistory partialHistory = builder.build();
    int acceptedSteps = 10000;
    for (int step = 1; step <= acceptedSteps; step++) {
      builder.append(step, report);
    }

    OnePhaseSpeciesConservationHistory history = builder.build();
    assertEquals(1, partialHistory.size());
    assertEquals(0.5, partialHistory.getElapsedTimeSeconds()[0], 0.0);
    assertEquals(acceptedSteps + 1, history.size());
    assertEquals(0.5, history.getElapsedTimeSeconds()[0], 0.0);
    assertEquals(acceptedSteps, history.getElapsedTimeSeconds()[acceptedSteps], 0.0);
    assertEquals(report, history.getReport(acceptedSteps));

    assertThrows(IllegalArgumentException.class, () -> builder.append(acceptedSteps, report));
    assertThrows(IllegalArgumentException.class,
        () -> OnePhaseSpeciesConservationHistory.builder().append(Double.NaN, report));
    assertThrows(IllegalArgumentException.class, () -> OnePhaseSpeciesConservationHistory.builder().append(1.0, null));
  }

  @Test
  void repeatedNodeInitializationDoesNotAccumulateMolarFlowDrift() {
    PipeFlowSystem pipe = createInitializedPipe(24, 3000.0);
    neqsim.fluidmechanics.flownode.FlowNodeInterface node = pipe.getNode(22);
    node.init();

    double[] componentMoles = new double[node.getBulkSystem().getPhase(0).getNumberOfComponents()];
    double[] overallComponentMoles = new double[componentMoles.length];
    for (int component = 0; component < componentMoles.length; component++) {
      componentMoles[component] = node.getBulkSystem().getPhase(0).getComponent(component).getNumberOfMolesInPhase();
      overallComponentMoles[component] = node.getBulkSystem().getPhase(0).getComponent(component).getNumberOfmoles();
    }
    double phaseMoles = node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
    double systemMoles = node.getBulkSystem().getTotalNumberOfMoles();
    double density = node.getBulkSystem().getPhase(0).getDensity();
    double massFlow = node.getMassFlowRate(0);
    double reynoldsNumber = node.getReynoldsNumber();
    double frictionFactor = node.getWallFrictionFactor();

    node.init();

    for (int component = 0; component < componentMoles.length; component++) {
      assertRelativeEquals(componentMoles[component],
          node.getBulkSystem().getPhase(0).getComponent(component).getNumberOfMolesInPhase(),
          "phase component amount must be idempotent for component " + component);
      assertRelativeEquals(overallComponentMoles[component],
          node.getBulkSystem().getPhase(0).getComponent(component).getNumberOfmoles(),
          "overall component amount must be idempotent for component " + component);
    }
    assertRelativeEquals(phaseMoles, node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase(),
        "phase reference amount must be idempotent");
    assertRelativeEquals(systemMoles, node.getBulkSystem().getTotalNumberOfMoles(),
        "system reference amount must be idempotent");
    assertRelativeEquals(density, node.getBulkSystem().getPhase(0).getDensity(), "EOS density must be idempotent");
    assertRelativeEquals(massFlow, node.getMassFlowRate(0), "mass flow must be idempotent");
    assertRelativeEquals(reynoldsNumber, node.getReynoldsNumber(), "Reynolds number must be idempotent");
    assertRelativeEquals(frictionFactor, node.getWallFrictionFactor(), "friction factor must be idempotent");
  }

  @Test
  void zeroVelocityPreservesFiniteThermodynamicReferenceState() {
    PipeFlowSystem pipe = createInitializedPipe(12, 3000.0);
    neqsim.fluidmechanics.flownode.FlowNodeInterface node = pipe.getNode(10);
    node.init();

    double phaseMoles = node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
    double methaneFraction = node.getBulkSystem().getPhase(0).getComponent(0).getx();

    node.setVelocity(0.0);
    assertDoesNotThrow(node::init);

    assertEquals(0.0, node.getVelocity(), 0.0);
    assertEquals(0.0, node.getMassFlowRate(0), 0.0);
    assertRelativeEquals(phaseMoles, node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase(),
        "zero hydraulic flow must retain a positive EOS reference amount");
    assertRelativeEquals(methaneFraction, node.getBulkSystem().getPhase(0).getComponent(0).getx(),
        "zero hydraulic flow must preserve thermodynamic composition");
    assertTrue(Double.isFinite(node.getBulkSystem().getPhase(0).getDensity()));
    assertTrue(node.getBulkSystem().getPhase(0).getDensity() > 0.0);
  }

  @Test
  void optInSpeciesTransportFailsLoudlyForReversedFlowWithoutLegacyStrictFlag() {
    PipeFlowSystem pipe = createInitializedPipe(3);
    pipe.setConservativeSpeciesTransport(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0 });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.getNode(2).setVelocityIn(-Math.abs(pipe.getNode(2).getVelocityIn().doubleValue()));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.solveTransient(1));
    assertTrue(exception.getMessage().contains("supports positive flow only"));
  }

  @Test
  void reportDoesNotRemainStaleAfterNonConservativeSolverPath() {
    PipeFlowSystem pipe = runCompositionStep(3, 30.0);
    assertTrue(pipe.getSpeciesConservationReport().isConverged());

    pipe.solveTransient(0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.NOT_RUN,
        pipe.getSpeciesConservationReport().getReason());
  }

  @Test
  @Tag("slow")
  void thirtyMinutePulseBreaksThroughRecoversAndClosesCumulativeInventory() {
    IntegratedPulseResult pulse = runIntegratedPulse();
    IntegratedPulseResult repeatedPulse = runIntegratedPulse();

    assertRawBitsEqual(pulse.outletNitrogenHistory, repeatedPulse.outletNitrogenHistory,
        "Independent coupled pulse runs must have bit-identical outlet histories.");
    assertRawBitsEqual(pulse.densityResidualHistory, repeatedPulse.densityResidualHistory,
        "Independent coupled pulse runs must have bit-identical EOS/FV density residuals.");
    assertArrayEquals(pulse.couplingIterationHistory, repeatedPulse.couplingIterationHistory,
        "Independent coupled pulse runs must perform identical fixed-point work.");
    assertRawBitsEqual(pulse.finalInventoryKg, repeatedPulse.finalInventoryKg,
        "Independent coupled pulse runs must have bit-identical final inventories.");
    assertEquals(pulse.finalMassFractionProfile.length, repeatedPulse.finalMassFractionProfile.length);
    for (int component = 0; component < pulse.finalMassFractionProfile.length; component++) {
      assertRawBitsEqual(pulse.finalMassFractionProfile[component], repeatedPulse.finalMassFractionProfile[component],
          "Independent coupled pulse runs must have bit-identical final profiles.");
    }
    assertRawBitsEqual(pulse.cumulativeInletKg, repeatedPulse.cumulativeInletKg,
        "Independent coupled pulse runs must have bit-identical cumulative inlet mass.");
    assertRawBitsEqual(pulse.cumulativeOutletKg, repeatedPulse.cumulativeOutletKg,
        "Independent coupled pulse runs must have bit-identical cumulative outlet mass.");

    double eventAmplitude = pulse.pulseInletMassFraction - pulse.baselineOutlet;
    double cumulativeScaleKg = Math.max(Math.max(pulse.initialInventoryKg, pulse.cumulativeInletKg), 1.0);

    assertTrue(eventAmplitude > 0.0);
    assertTrue(pulse.pulseEndOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude,
        "The 30-minute event must break through before the inlet returns to baseline.");
    assertTrue(pulse.peakOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude);
    assertEquals(pulse.baselineOutlet, pulse.recoveredOutlet, 2.0e-3 * eventAmplitude,
        "The outlet composition must recover after purging the pulse.");
    assertEquals(0.0, pulse.cumulativeResidualKg, cumulativeScaleKg * 1.0e-7,
        "Cumulative nitrogen inventory must equal integrated inlet minus outlet mass.");
  }

  @Test
  @Tag("slow")
  void coupledPulseGridAndTimestepRefinementReducesOutletDifference() {
    IntegratedPulseResult coarse = runIntegratedPulse(6, 120.0);
    IntegratedPulseResult medium = runIntegratedPulse(12, 60.0);
    IntegratedPulseResult fine = runIntegratedPulse(24, 30.0);

    assertPulseEngineeringGates(coarse);
    assertPulseEngineeringGates(medium);
    assertPulseEngineeringGates(fine);

    double coarseToMedium = commonTimeMeanAbsoluteDifference(coarse.outletNitrogenHistory, 120.0,
        medium.outletNitrogenHistory, 60.0, 120.0);
    double mediumToFine = commonTimeMeanAbsoluteDifference(medium.outletNitrogenHistory, 60.0,
        fine.outletNitrogenHistory, 30.0, 120.0);

    assertTrue(coarseToMedium > 0.0, "The refinement study must resolve a non-zero discretization difference.");
    assertTrue(mediumToFine < coarseToMedium,
        "Joint grid/timestep refinement must reduce the common-time outlet difference: coarse-to-medium="
            + coarseToMedium + ", medium-to-fine=" + mediumToFine);
  }

  private static IntegratedPulseResult runIntegratedPulse() {
    return runIntegratedPulse(12, 60.0);
  }

  private static IntegratedPulseResult runIntegratedPulse(int nodes, double timeStepSeconds) {
    int nitrogen = 1;
    int pulseSteps = exactStepCount(1800.0, timeStepSeconds);
    int recoverySteps = exactStepCount(3600.0, timeStepSeconds);
    PipeFlowSystem pipe = createInitializedPipe(nodes, 3000.0);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    SystemInterface baselineGas = createGas(0.95, 0.05);
    SystemInterface pulseGas = createGas(0.80, 0.20);

    OnePhaseSpeciesConservationReport baseline = runTransientStepWithContext(pipe, baselineGas.clone(), timeStepSeconds,
        nodes, -1, false);
    assertTrue(baseline.isConverged(), baseline.getMessage());
    double baselineOutlet = last(baseline.getMassFractionProfile()[nitrogen]);
    double initialInventoryKg = baseline.getFinalInventoryKg()[nitrogen];
    double cumulativeInletKg = 0.0;
    double cumulativeOutletKg = 0.0;
    double pulseInletMassFraction = Double.NaN;
    double pulseEndOutlet = Double.NaN;
    double peakOutlet = baselineOutlet;
    double[] outletNitrogenHistory = new double[pulseSteps + recoverySteps];
    double[] densityResidualHistory = new double[pulseSteps + recoverySteps];
    int[] couplingIterationHistory = new int[pulseSteps + recoverySteps];
    OnePhaseSpeciesConservationReport report = baseline;

    for (int step = 0; step < pulseSteps + recoverySteps; step++) {
      boolean pulseActive = step < pulseSteps;
      SystemInterface inlet = pulseActive ? pulseGas.clone() : baselineGas.clone();
      report = runTransientStepWithContext(pipe, inlet, timeStepSeconds, nodes, step, pulseActive);

      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
      assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
      cumulativeInletKg += report.getInletBoundaryMassKg()[nitrogen];
      cumulativeOutletKg += report.getOutletBoundaryMassKg()[nitrogen];
      double outlet = last(report.getMassFractionProfile()[nitrogen]);
      outletNitrogenHistory[step] = outlet;
      densityResidualHistory[step] = pipe.getConvergenceReport().getMaximumRelativeDensityResidual();
      couplingIterationHistory[step] = report.getCouplingIterations();
      peakOutlet = Math.max(peakOutlet, outlet);
      if (step == 0) {
        assertEquals(initialInventoryKg, report.getInitialInventoryKg()[nitrogen],
            Math.max(1.0, initialInventoryKg) * 1.0e-10);
        pulseInletMassFraction = report.getInletBoundaryMassKg()[nitrogen] / sum(report.getInletBoundaryMassKg());
      }
      if (step == pulseSteps - 1) {
        pulseEndOutlet = outlet;
      }
    }

    double finalInventoryKg = report.getFinalInventoryKg()[nitrogen];
    double cumulativeResidualKg = finalInventoryKg - initialInventoryKg - cumulativeInletKg + cumulativeOutletKg;
    double recoveredOutlet = last(report.getMassFractionProfile()[nitrogen]);
    return new IntegratedPulseResult(baselineOutlet, initialInventoryKg, cumulativeInletKg, cumulativeOutletKg,
        pulseInletMassFraction, pulseEndOutlet, peakOutlet, recoveredOutlet, cumulativeResidualKg,
        outletNitrogenHistory, densityResidualHistory, couplingIterationHistory, report.getFinalInventoryKg(),
        report.getMassFractionProfile());
  }

  static PipeFlowSystem runCompositionStep(int nodes, double timeStep) {
    return runCompositionStep(nodes, timeStep, MASS_FLOW_KG_PER_SECOND);
  }

  private static PipeFlowSystem runCompositionStep(int nodes, double timeStep, double massFlowKgPerSecond) {
    PipeFlowSystem pipe = createInitializedPipe(nodes, 15000.0, massFlowKgPerSecond);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStep });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe;
  }

  private static PipeFlowSystem runThreeStepCompositionEvent() {
    PipeFlowSystem pipe = createInitializedPipe(12, 3000.0);
    pipe.setConservativeSpeciesTransport(true);
    assertFalse(pipe.isSpeciesConservationHistoryStorageEnabled());
    pipe.setStoreSpeciesConservationHistory(true);
    assertTrue(pipe.isSpeciesConservationHistoryStorageEnabled());
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0, 60.0, 90.0 });
    pipe.getTimeSeries().setInletThermoSystems(
        new SystemInterface[] { createGas(0.80, 0.20), createGas(0.80, 0.20), createGas(0.95, 0.05) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.getTimeSeries().setOutletMolarFlowRate(null);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe;
  }

  private static OnePhaseSpeciesConservationReport runTransientStepWithContext(PipeFlowSystem pipe,
      SystemInterface inlet, double timeStepSeconds, int nodes, int step, boolean pulseActive) {
    try {
      return runTransientStep(pipe, inlet, timeStepSeconds);
    } catch (AssertionError exception) {
      String phase = step < 0 ? "baseline" : (pulseActive ? "pulse" : "recovery");
      throw new AssertionError("Coupled pulse failed for nodes=" + nodes + ", timestep=" + timeStepSeconds
          + " s, phase=" + phase + ", event step=" + step + ": " + exception.getMessage() + System.lineSeparator()
          + pipe.getConvergenceReport().toJson(), exception);
    }
  }

  private static OnePhaseSpeciesConservationReport runTransientStep(PipeFlowSystem pipe, SystemInterface inlet,
      double timeStepSeconds) {
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStepSeconds });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { inlet });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe.getSpeciesConservationReport();
  }

  private static PipeFlowSystem createInitializedPipe(int nodes) {
    return createInitializedPipe(nodes, 15000.0);
  }

  private static PipeFlowSystem createInitializedPipe(int nodes, double lengthMeters) {
    return createInitializedPipe(nodes, lengthMeters, MASS_FLOW_KG_PER_SECOND);
  }

  private static PipeFlowSystem createInitializedPipe(int nodes, double lengthMeters, double massFlowKgPerSecond) {
    PipeFlowSystem pipe = createConfiguredPipe(nodes, lengthMeters, massFlowKgPerSecond);
    pipe.solveSteadyState(1);
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    return pipe;
  }

  private static PipeFlowSystem createConfiguredPipe(int nodes, double lengthMeters, double massFlowKgPerSecond) {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setInletThermoSystem(createGas(0.95, 0.05, massFlowKgPerSecond));
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(nodes);
    GeometryDefinitionInterface[] geometry = { new PipeData(), new PipeData() };
    for (GeometryDefinitionInterface section : geometry) {
      section.setDiameter(0.5);
      section.setInnerSurfaceRoughness(1.0e-5);
    }
    pipe.setEquipmentGeometry(geometry);
    pipe.setLegHeights(new double[] { 0.0, 0.0 });
    pipe.setLegPositions(new double[] { 0.0, lengthMeters });
    pipe.setLegOuterTemperatures(new double[] { TEMPERATURE_K, TEMPERATURE_K });
    pipe.setLegWallHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.setLegOuterHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.createSystem();
    pipe.init();
    pipe.setConservativeSpeciesTransport(true);
    return pipe;
  }

  private static void assertPulseEngineeringGates(IntegratedPulseResult pulse) {
    double eventAmplitude = pulse.pulseInletMassFraction - pulse.baselineOutlet;
    double cumulativeScaleKg = Math.max(Math.max(pulse.initialInventoryKg, pulse.cumulativeInletKg), 1.0);

    assertTrue(eventAmplitude > 0.0);
    assertTrue(pulse.pulseEndOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude,
        "The 30-minute event must break through before the inlet returns to baseline.");
    assertTrue(pulse.peakOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude);
    assertEquals(pulse.baselineOutlet, pulse.recoveredOutlet, 2.0e-3 * eventAmplitude,
        "The outlet composition must recover after purging the pulse.");
    assertEquals(0.0, pulse.cumulativeResidualKg, cumulativeScaleKg * 1.0e-7,
        "Cumulative nitrogen inventory must equal integrated inlet minus outlet mass.");
  }

  private static int exactStepCount(double durationSeconds, double timeStepSeconds) {
    int steps = (int) Math.round(durationSeconds / timeStepSeconds);
    assertEquals(durationSeconds, steps * timeStepSeconds, 1.0e-12,
        "Event durations must contain an integer number of timesteps.");
    return steps;
  }

  private static double commonTimeMeanAbsoluteDifference(double[] first, double firstTimeStepSeconds, double[] second,
      double secondTimeStepSeconds, double sampleIntervalSeconds) {
    int firstStride = exactStepCount(sampleIntervalSeconds, firstTimeStepSeconds);
    int secondStride = exactStepCount(sampleIntervalSeconds, secondTimeStepSeconds);
    assertEquals(first.length * firstTimeStepSeconds, second.length * secondTimeStepSeconds, 1.0e-12,
        "Histories must span the same physical duration.");
    assertEquals(0, first.length % firstStride, "First history must end on a common sample time.");
    assertEquals(0, second.length % secondStride, "Second history must end on a common sample time.");
    int samples = first.length / firstStride;
    assertEquals(samples, second.length / secondStride);
    double difference = 0.0;
    for (int sample = 1; sample <= samples; sample++) {
      int firstIndex = sample * firstStride - 1;
      int secondIndex = sample * secondStride - 1;
      difference += Math.abs(first[firstIndex] - second[secondIndex]);
    }
    return difference / samples;
  }

  private static double last(double[] values) {
    return values[values.length - 1];
  }

  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }

  private static void assertRelativeEquals(double expected, double actual, String message) {
    double scale = Math.max(Math.max(Math.abs(expected), Math.abs(actual)), 1.0e-30);
    assertEquals(expected, actual, 1.0e-12 * scale, message);
  }

  private static void assertRawBitsEqual(double expected, double actual, String message) {
    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), message);
  }

  private static void assertRawBitsEqual(double[] expected, double[] actual, String message) {
    assertEquals(expected.length, actual.length, message);
    for (int index = 0; index < expected.length; index++) {
      assertRawBitsEqual(expected[index], actual[index], message + " index=" + index);
    }
  }

  private static final class IntegratedPulseResult {
    private final double baselineOutlet;
    private final double initialInventoryKg;
    private final double cumulativeInletKg;
    private final double cumulativeOutletKg;
    private final double pulseInletMassFraction;
    private final double pulseEndOutlet;
    private final double peakOutlet;
    private final double recoveredOutlet;
    private final double cumulativeResidualKg;
    private final double[] outletNitrogenHistory;
    private final double[] densityResidualHistory;
    private final int[] couplingIterationHistory;
    private final double[] finalInventoryKg;
    private final double[][] finalMassFractionProfile;

    private IntegratedPulseResult(double baselineOutlet, double initialInventoryKg, double cumulativeInletKg,
        double cumulativeOutletKg, double pulseInletMassFraction, double pulseEndOutlet, double peakOutlet,
        double recoveredOutlet, double cumulativeResidualKg, double[] outletNitrogenHistory,
        double[] densityResidualHistory, int[] couplingIterationHistory, double[] finalInventoryKg,
        double[][] finalMassFractionProfile) {
      this.baselineOutlet = baselineOutlet;
      this.initialInventoryKg = initialInventoryKg;
      this.cumulativeInletKg = cumulativeInletKg;
      this.cumulativeOutletKg = cumulativeOutletKg;
      this.pulseInletMassFraction = pulseInletMassFraction;
      this.pulseEndOutlet = pulseEndOutlet;
      this.peakOutlet = peakOutlet;
      this.recoveredOutlet = recoveredOutlet;
      this.cumulativeResidualKg = cumulativeResidualKg;
      this.outletNitrogenHistory = outletNitrogenHistory;
      this.densityResidualHistory = densityResidualHistory;
      this.couplingIterationHistory = couplingIterationHistory;
      this.finalInventoryKg = finalInventoryKg;
      this.finalMassFractionProfile = finalMassFractionProfile;
    }
  }

  private static SystemInterface createGas(double methane, double nitrogen) {
    return createGas(methane, nitrogen, MASS_FLOW_KG_PER_SECOND);
  }

  private static SystemInterface createGas(double methane, double nitrogen, double massFlowKgPerSecond) {
    SystemInterface gas = new SystemSrkEos(TEMPERATURE_K, PRESSURE_BARA);
    gas.addComponent("methane", methane);
    gas.addComponent("nitrogen", nitrogen);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    gas.setTotalFlowRate(massFlowKgPerSecond, "kg/sec");
    return gas;
  }
}
