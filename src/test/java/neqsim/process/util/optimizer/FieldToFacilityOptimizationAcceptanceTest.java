package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.energy.Gearbox;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldWellNetworkCoupler;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.network.FieldWellNetworkProcessUnit;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic end-to-end acceptance for live wells, reversible separator routing and host constraints.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class FieldToFacilityOptimizationAcceptanceTest {
  /** Complete mutable field-to-host fixture. */
  private static final class Fixture {
    private final ProcessModel model;
    private final FieldWellNetworkProcessUnit networkUnit;
    private final Separator highPressureSeparator;
    private final Separator lowPressureSeparator;
    private final Separator highPressureScrubber;
    private final Separator lowPressureScrubber;
    private final Compressor highPressureCompressor;
    private final Compressor lowPressureCompressor;
    private final Mixer exportMixer;
    private final ThrottlingValve exportValve;
    private final MechanicalShaft shaft;
    private final EnergyPort driverPort;
    private final CompressorDriver driver;
    private final Gearbox gearbox;
    private final double sharedPowerLimitKw;
    private final double baselineRouteAHigh;
    private final List<String> wellSuffixes;

    /**
     * Create one fixture holder.
     *
     * @param model coupled process model
     * @param networkUnit live well/network unit
     * @param highPressureSeparator high-pressure separator
     * @param lowPressureSeparator low-pressure separator
     * @param highPressureScrubber high-pressure compressor suction scrubber
     * @param lowPressureScrubber low-pressure compressor suction scrubber
     * @param highPressureCompressor high-pressure compression casing
     * @param lowPressureCompressor low-pressure compression casing
     * @param exportMixer common gas-export boundary mixer
     * @param exportValve gas-export pressure-control valve
     * @param shaft common shaft
     * @param driverPort driver output port
     * @param driver driver rating
     * @param gearbox gearbox rating
     * @param sharedPowerLimitKw shared plant power limit in kW
     * @param baselineRouteAHigh baseline producer-A high-pressure route flow in kg/hr
     * @param wellSuffixes deterministic well identities
     */
    private Fixture(ProcessModel model, FieldWellNetworkProcessUnit networkUnit, Separator highPressureSeparator,
        Separator lowPressureSeparator, Separator highPressureScrubber, Separator lowPressureScrubber,
        Compressor highPressureCompressor, Compressor lowPressureCompressor, Mixer exportMixer,
        ThrottlingValve exportValve, MechanicalShaft shaft, EnergyPort driverPort, CompressorDriver driver,
        Gearbox gearbox, double sharedPowerLimitKw, double baselineRouteAHigh, List<String> wellSuffixes) {
      this.model = model;
      this.networkUnit = networkUnit;
      this.highPressureSeparator = highPressureSeparator;
      this.lowPressureSeparator = lowPressureSeparator;
      this.highPressureScrubber = highPressureScrubber;
      this.lowPressureScrubber = lowPressureScrubber;
      this.highPressureCompressor = highPressureCompressor;
      this.lowPressureCompressor = lowPressureCompressor;
      this.exportMixer = exportMixer;
      this.exportValve = exportValve;
      this.shaft = shaft;
      this.driverPort = driverPort;
      this.driver = driver;
      this.gearbox = gearbox;
      this.sharedPowerLimitKw = sharedPowerLimitKw;
      this.baselineRouteAHigh = baselineRouteAHigh;
      this.wellSuffixes = Collections.unmodifiableList(new ArrayList<String>(wellSuffixes));
    }

    /**
     * Freeze common-shaft evidence for one evaluator candidate.
     *
     * @param calculationId evaluator-owned candidate identity
     * @return complete common-shaft evidence
     */
    private PlantCommonShaftEvidence commonShaftEvidence(String calculationId) {
      shaft.solveBalance();
      String sourceCalculationId = highPressureCompressor.getCalculationIdentifier().toString();
      assertEquals(sourceCalculationId, lowPressureCompressor.getCalculationIdentifier().toString());
      return PlantCommonShaftEvidence
          .builder("synthetic field", "Host", "export common shaft", calculationId, shaft,
              "coupled field-to-facility candidate")
          .sourceCalculationId(sourceCalculationId)
          .casing(highPressureCompressor.getEnergyPort("shaftPower").getParticipantId(), highPressureCompressor)
          .casing(lowPressureCompressor.getEnergyPort("shaftPower").getParticipantId(), lowPressureCompressor)
          .driver(driverPort.getParticipantId(), driver).gearbox("export gearbox", gearbox).speedToleranceRpm(1.0)
          .powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(1.0e6).convergenceComplete(model.isModelConverged()).build();
    }
  }

  /**
   * Verify route swaps, choke changes, per-line operating limits, separator limits, mapped compression, shared power,
   * bottleneck attribution and exact baseline restoration in one optimizer workflow.
   */
  @Test
  void evaluatesReversibleMultiWellFieldToFacilityEnvelope() {
    Fixture fixture = createFixture();
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture);

    ProcessModelOperatingAction chokeA = ProcessModelOperatingAction.continuous("choke-a", "Producer A choke",
        "Field::field network.choke.choke A.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction chokeB = ProcessModelOperatingAction.continuous("choke-b", "Producer B choke",
        "Field::field network.choke.choke B.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction routeA = ProcessModelOperatingAction.discrete("route-a",
        "Producer A separator pressure level", "Field::field network.route.producer A pressure level.selection",
        new double[] {0.0, 1.0}, "-", "synthetic qualified HP/LP route line-up");
    ProcessModelOperatingAction routeB = ProcessModelOperatingAction.discrete("route-b",
        "Producer B separator pressure level", "Field::field network.route.producer B pressure level.selection",
        new double[] {0.0, 1.0}, "-", "synthetic qualified HP/LP route line-up");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "field-to-facility-actions", "Field-to-facility actions", "synthetic coupled acceptance", simulation,
        Arrays.asList(chokeA, chokeB, routeA, routeB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer A deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer B deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
            "A HP route capacity", "synthetic per-line operating limit");

    CandidateSetEvaluationResult swapped = candidates.evaluate(new double[] {60.0, 70.0, 1.0, 0.0});
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, swapped.getOutcome(), swapped.getDiagnostics().toString());
    assertTrue(swapped.isBaselineRestored());
    assertTrue(swapped.isBaselineSimulationConverged());
    assertTrue(swapped.getPlantConstraintEvidence().stream().allMatch(PlantConstraintEvidence::hasAvailableEvidence));
    assertEquals(1, swapped.getProcessBoundaryConstraintEvidence().size());
    assertTrue(swapped.getProcessBoundaryConstraintEvidence().get(0).isCalculable());
    assertTrue(swapped.getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> "HP suction scrubber".equals(evidence.getEquipmentName())
            && evidence.getQualifiedConstraintName().endsWith("/gas handling")));
    assertTrue(swapped.getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> "LP suction scrubber".equals(evidence.getEquipmentName())
            && evidence.getQualifiedConstraintName().endsWith("/gas handling")));
    assertBaselineRestored(fixture);

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("field-to-facility-envelope",
        "Field-to-facility envelope", "synthetic coupled field and host acceptance", candidates);
    SliceResult slice = study.evaluateOneDimensional("choke-a", new double[] {70.0, 90.0},
        new double[] {70.0, 70.0, 0.0, 1.0});

    assertTrue(slice.isComplete());
    assertTrue(slice.getPoints().get(0).isFeasible(), slice.getPoints().get(0).getEvaluation().getOutcome() + ": "
        + slice.getPoints().get(0).getEvaluation().getDiagnostics());
    assertFalse(slice.getPoints().get(1).isFeasible());
    LeadingConstraintEvidence leading = slice.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.REQUIRED_HYDRAULIC, leading.getSource());
    assertTrue(leading.getQualifiedConstraintName().endsWith("/A HP route capacity"));
    assertEquals("REQUIRED_HYDRAULIC", leading.getApplicableLimitRole());
    assertTrue(slice.getPoints().get(1).getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> evidence.getQualifiedConstraintName().endsWith("/A HP route capacity")
            && evidence.getApplicableLimitRole() == CapacityConstraint.ApplicableLimitRole.CONFIGURED_OPERATING));
    assertTrue(slice.getBottleneckTransitions().isEmpty());
    assertBaselineRestored(fixture);
  }

  /**
   * Verify mixed choke/routing search and replay scale the complete evidence contract to ten live wells.
   */
  @Test
  void evaluatesTenWellExportBoundaryWithCompleteConstraintEvidence() {
    Fixture fixture = createFixture(10);
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture);
    List<ProcessModelOperatingAction> actions = createFieldActions(fixture);
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "ten-well-field-actions", "Ten-well field actions", "synthetic full-model mixed search", simulation, actions);
    for (String suffix : fixture.wellSuffixes) {
      candidates
          .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
              "producer " + suffix + " deliverability", "synthetic well deliverability")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " HP route capacity", "synthetic per-line operating limit")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " LP route capacity", "synthetic per-line operating limit");
    }
    double[] seed = new double[actions.size()];
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      seed[index] = 70.0;
      seed[index + fixture.wellSuffixes.size()] = index % 2;
    }
    ProcessModelOperatingPointOptimizer optimizer = new ProcessModelOperatingPointOptimizer("ten-well-field-search",
        "Ten-well field search", "synthetic bounded choke and HP/LP routing search", candidates)
        .setInitialCandidate(seed).setMaximumEvaluations(35).setInitialStepFraction(0.5).setRelativeStepTolerance(0.1)
        .setObjectiveImprovementTolerance(1.0e-6, "synthetic export-rate comparison tolerance");
    OperatingPointSearchResult result = optimizer.optimize();

    assertTrue(result.isAcceptedPointReplayed(), result.getOutcome() + ": " + result.getDiagnostics());
    assertTrue(result.getAcceptedPointReplay().getEvaluation().isFeasible());
    assertTrue(result.getAcceptedPointReplay().getRawObjective() > result.getCandidates().get(0).getRawObjective());
    assertTrue(result.isCandidateFinite());
    assertTrue(result.isConstraintEvidenceComplete());
    assertTrue(result.isActionsComplete());
    assertTrue(result.getEvaluationCount() <= 35);
    assertTrue(Double.isFinite(result.getRuntimeSeconds()) && result.getRuntimeSeconds() >= 0.0);
    Map<String, Object> evidence = result.getOptimizerEvidence("synthetic-cycle-1");
    assertEquals("synthetic-cycle-1", evidence.get("cycle_id"));
    assertEquals(Boolean.TRUE, evidence.get("simulation_converged"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_feasible"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_finite"));
    assertEquals(Boolean.TRUE, evidence.get("constraint_evidence_complete"));
    assertEquals(Boolean.TRUE, evidence.get("state_restore_complete"));
    assertEquals(Boolean.TRUE, evidence.get("accepted_point_replayed"));
    assertEquals(Boolean.TRUE, evidence.get("actions_complete"));
    assertEquals(result.getEvaluationCount(), evidence.get("evaluation_count"));
    assertEquals(10, fixture.networkUnit.getLastCouplingResult().getWellResults().size());
    assertEquals(10, fixture.networkUnit.getExclusiveRouteSelectorNames().size());
    assertTrue(result.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().size() >= 34);
    assertTrue(result.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
        .allMatch(capacityEvidence -> capacityEvidence.hasFiniteEvidence()
            && Double.isFinite(capacityEvidence.getNormalizedUtilization())));
    assertEquals(1, result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().size());
    assertTrue(
        result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().get(0).isCalculable());
    assertTrue(
        result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().get(0).isFeasible());

    assertEquals(100.0, fixture.exportValve.getOutletStream().getPressure("bara"), 1.0e-6);
    assertBaselineRestored(fixture);
  }

  /** Create all ten-well choke and HP/LP route actions in deterministic groups. */
  private List<ProcessModelOperatingAction> createFieldActions(Fixture fixture) {
    List<ProcessModelOperatingAction> actions = new ArrayList<ProcessModelOperatingAction>();
    for (String suffix : fixture.wellSuffixes) {
      actions.add(ProcessModelOperatingAction.continuous("choke-" + suffix.toLowerCase(),
          "Producer " + suffix + " choke", "Field::field network.choke.choke " + suffix + ".opening", 50.0, 95.0, "%",
          "synthetic choke travel envelope"));
    }
    for (String suffix : fixture.wellSuffixes) {
      actions.add(ProcessModelOperatingAction.discrete("route-" + suffix.toLowerCase(),
          "Producer " + suffix + " separator pressure level",
          "Field::field network.route.producer " + suffix + " pressure level.selection", new double[] {0.0, 1.0}, "-",
          "synthetic qualified HP/LP route line-up"));
    }
    return actions;
  }

  /** Build and calibrate the complete synthetic field-to-host process. */
  private Fixture createFixture() {
    return createFixture(2);
  }

  /**
   * Build and calibrate a deterministic synthetic field-to-export process.
   *
   * @param wellCount number of live wells between 2 and 20
   * @return complete coupled fixture
   */
  private Fixture createFixture(int wellCount) {
    if (wellCount < 2 || wellCount > 20) {
      throw new IllegalArgumentException("Synthetic acceptance fixture requires 2 to 20 wells");
    }
    FieldNetworkTopology topology = new FieldNetworkTopology("field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(140.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    List<String> wellSuffixes = new ArrayList<String>();
    for (int index = 0; index < wellCount; index++) {
      String suffix = String.valueOf((char) ('A' + index));
      wellSuffixes.add(suffix);
      addWellRoute(topology, suffix, 75.0, 70.0);
    }
    topology.addFixedPressureSink("HP host", "V-HP", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    topology.addFixedPressureSink("LP host", "V-LP", NodeRole.HOST, Service.PRODUCTION, 30.0, 0.0);
    for (int index = 0; index < wellSuffixes.size(); index++) {
      addPressureRoutes(topology, wellSuffixes.get(index), index % 2 == 0);
    }

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(100);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setRelaxationFactor(0.7);
    for (int index = 0; index < wellSuffixes.size(); index++) {
      String suffix = wellSuffixes.get(index);
      double reservoirPressureBara = index == 0 ? 190.0 : index == 1 ? 185.0 : 184.0 - index;
      double productivityIndex = index == 0 ? 1.1e-7 : index == 1 ? 0.9e-7 : (0.90 - 0.02 * index) * 1.0e-7;
      coupler.bindProductionWell("producer " + suffix,
          well("producer " + suffix, reservoirPressureBara, productivityIndex));
    }
    FieldWellNetworkProcessUnit networkUnit = new FieldWellNetworkProcessUnit("field network", coupler);
    for (String suffix : wellSuffixes) {
      networkUnit.registerExclusiveRouteSelector("producer " + suffix + " pressure level",
          Arrays.asList(suffix + " HP route", suffix + " LP route"), "synthetic producer " + suffix + " HP/LP line-up");
    }
    networkUnit.run(UUID.randomUUID());
    assertTrue(networkUnit.solved(),
        networkUnit.getLastCouplingResult().getMessage() + "; rate residual="
            + networkUnit.getLastCouplingResult().getMaximumRateResidualKgS() + "; pressure residual="
            + networkUnit.getLastCouplingResult().getMaximumPressureResidualBar() + "; network residual="
            + networkUnit.getLastCouplingResult().getNetworkResidualPa() + "; mass balance="
            + networkUnit.getLastCouplingResult().getMassBalanceResidualKgS());
    assertTrue(network.isProductionOptimizationApplicable());

    Separator highPressureSeparator = new Separator("HP separator", networkUnit.getOutletStream("HP host"));
    Separator lowPressureSeparator = new Separator("LP separator", networkUnit.getOutletStream("LP host"));
    Separator highPressureScrubber = new Separator("HP suction scrubber", highPressureSeparator.getGasOutStream());
    Separator lowPressureScrubber = new Separator("LP suction scrubber", lowPressureSeparator.getGasOutStream());
    Compressor highPressureCompressor = new Compressor("HP compressor", highPressureScrubber.getGasOutStream());
    Compressor lowPressureCompressor = new Compressor("LP compressor", lowPressureScrubber.getGasOutStream());
    highPressureCompressor.setOutletPressure(100.0, "bara");
    lowPressureCompressor.setOutletPressure(100.0, "bara");
    Mixer exportMixer = new Mixer("gas export boundary");
    exportMixer.addStream(highPressureCompressor.getOutletStream());
    exportMixer.addStream(lowPressureCompressor.getOutletStream());
    ThrottlingValve exportValve = new ThrottlingValve("gas export pressure control", exportMixer.getOutletStream());
    exportValve.setOutletPressure(100.0, "bara");

    ProcessSystem process = new ProcessSystem("field to facility");
    process.add(networkUnit);
    process.add(highPressureSeparator);
    process.add(lowPressureSeparator);
    process.add(highPressureScrubber);
    process.add(lowPressureScrubber);
    process.add(highPressureCompressor);
    process.add(lowPressureCompressor);
    process.add(exportMixer);
    process.add(exportValve);
    ProcessModel model = new ProcessModel();
    model.add("Field", process);
    model.run();
    assertTrue(model.isModelConverged());
    highPressureCompressor.generateCompressorChart("normal curves", 5);
    lowPressureCompressor.generateCompressorChart("normal curves", 5);
    highPressureCompressor.getCompressorChart().setUseCompressorChart(true);
    lowPressureCompressor.getCompressorChart().setUseCompressorChart(true);
    model.run();
    assertTrue(model.isModelConverged());

    MechanicalShaft shaft = new MechanicalShaft("export common shaft");
    shaft.setSpeed(highPressureCompressor.getSpeed());
    shaft.setMaximumSpeed(highPressureCompressor.getSpeed() * 1.2);
    highPressureCompressor.getEnergyPort("shaftPower").connect(shaft);
    lowPressureCompressor.getEnergyPort("shaftPower").connect(shaft);
    UUID shaftCalculationId = UUID.randomUUID();
    highPressureCompressor.run(shaftCalculationId);
    lowPressureCompressor.run(shaftCalculationId);
    model.run();
    assertTrue(model.isModelConverged());
    EnergyPort driverPort = new EnergyPort("export driver", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    driverPort.connect(shaft);
    double baselinePowerKw = model.getPower("kW");
    double driverLimitKw = baselinePowerKw * 1.8 / 0.98;
    CompressorDriver driver = new CompressorDriver(DriverType.ELECTRIC_MOTOR, driverLimitKw);
    driver.setMaxPower(driverLimitKw);
    driver.setMinSpeed(shaft.getSpeed() * 0.8);
    driver.setMaxSpeed(shaft.getSpeed() * 1.2);
    driverPort.setDuty(driverLimitKw, "kW");
    Gearbox gearbox = new Gearbox("export gearbox");
    gearbox.setEfficiency(0.98);
    gearbox.setSpeedRatio(1.0);
    gearbox.setMaximumInputPower(driverLimitKw * 1.2 * 1000.0);

    double initialRouteAHigh = massFlow(network, "A HP route");
    network.getPipe("choke A").setChokeOpening(90.0);
    model.run();
    assertTrue(model.isModelConverged());
    assertTrue(network.isProductionOptimizationApplicable());
    double highRouteAHigh = massFlow(network, "A HP route");
    assertTrue(highRouteAHigh > initialRouteAHigh);
    network.getPipe("choke A").setChokeOpening(70.0);
    model.run();
    assertTrue(model.isModelConverged());
    double baselineRouteAHigh = massFlow(network, "A HP route");
    assertTrue(highRouteAHigh > baselineRouteAHigh);
    double routeAHighOperatingLimit = 0.5 * (baselineRouteAHigh + highRouteAHigh);

    configureInstalledLimits(networkUnit, highPressureSeparator, lowPressureSeparator, highPressureScrubber,
        lowPressureScrubber, network, routeAHighOperatingLimit, wellSuffixes);
    return new Fixture(model, networkUnit, highPressureSeparator, lowPressureSeparator, highPressureScrubber,
        lowPressureScrubber, highPressureCompressor, lowPressureCompressor, exportMixer, exportValve, shaft, driverPort,
        driver, gearbox, baselinePowerKw * 1.8, baselineRouteAHigh, wellSuffixes);
  }

  /** Add one live well node, header and production choke. */
  private void addWellRoute(FieldNetworkTopology topology, String suffix, double initialPressureBara,
      double chokeOpening) {
    String wellNode = "producer " + suffix;
    String headerNode = suffix + " header";
    topology.addLiveWellNode(wellNode, "XT-" + suffix, NodeRole.PRODUCTION_WELL, Service.PRODUCTION,
        initialPressureBara, -200.0);
    topology.addJunction(headerNode, "MA-" + suffix, NodeRole.MANIFOLD, Service.PRODUCTION, -200.0);
    topology.getHydraulicNetwork().setNodePressure(headerNode, initialPressureBara - 5.0);
    topology.getHydraulicNetwork().addChoke(wellNode, headerNode, "choke " + suffix, 30.0, chokeOpening);
    topology.registerExistingEdge("choke " + suffix, "XV-" + suffix, EdgeRole.CHOKE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well", "header");
  }

  /** Add mutually exclusive well-specific routes to distinct fixed-pressure host levels. */
  private void addPressureRoutes(FieldNetworkTopology topology, String suffix, boolean highPressureSelected) {
    String header = suffix + " header";
    topology.addPipe(suffix + " HP route", "FL-" + suffix + "-HP", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, header, "HP outlet", "HP host", suffix + " arrival", 1200.0, 0.20,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe(suffix + " LP route", "FL-" + suffix + "-LP", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, header, "LP outlet", "LP host", suffix + " arrival", 1200.0, 0.20,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.getHydraulicNetwork().getPipe(suffix + " HP route").setAvailability(highPressureSelected ? 1.0 : 0.0);
    topology.getHydraulicNetwork().getPipe(suffix + " LP route").setAvailability(highPressureSelected ? 0.0 : 1.0);
  }

  /** Add operating/default limits for wells, lines and both separators. */
  private void configureInstalledLimits(FieldWellNetworkProcessUnit networkUnit, Separator highPressureSeparator,
      Separator lowPressureSeparator, Separator highPressureScrubber, Separator lowPressureScrubber,
      LoopedPipeNetwork network, double routeAHighOperatingLimit, List<String> wellSuffixes) {
    networkUnit.clearCapacityConstraints();
    for (String suffix : wellSuffixes) {
      String nodeId = "producer " + suffix;
      networkUnit.addCapacityConstraint(maximumConstraint(nodeId + " deliverability", "kg/hr",
          wellRate(networkUnit, nodeId) * 1.5, wellRate(networkUnit, nodeId) * 1.3, () -> wellRate(networkUnit, nodeId),
          "synthetic well " + suffix + " operating procedure"));
      double highLimit = "A".equals(suffix) ? routeAHighOperatingLimit
          : Math.max(100.0, massFlow(network, suffix + " HP route") * 1.5);
      addLineConstraint(networkUnit, network, suffix + " HP route", highLimit);
      addLineConstraint(networkUnit, network, suffix + " LP route",
          Math.max(100.0, massFlow(network, suffix + " LP route") * 1.5));
    }

    highPressureSeparator.clearCapacityConstraints();
    lowPressureSeparator.clearCapacityConstraints();
    highPressureScrubber.clearCapacityConstraints();
    lowPressureScrubber.clearCapacityConstraints();
    double highSeparatorFlow = highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double lowSeparatorFlow = lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double totalSeparatorFlow = highSeparatorFlow + lowSeparatorFlow;
    highPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic HP separator operating procedure"));
    lowPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic LP separator operating procedure"));
    highPressureScrubber.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> highPressureScrubber.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic HP suction scrubber operating procedure"));
    lowPressureScrubber.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> lowPressureScrubber.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic LP suction scrubber operating procedure"));
  }

  /** Add one per-line capacity constraint with a preserved design rating and configured operating limit. */
  private void addLineConstraint(FieldWellNetworkProcessUnit unit, LoopedPipeNetwork network, String lineName,
      double operatingLimit) {
    unit.addCapacityConstraint(maximumConstraint(lineName + " capacity", "kg/hr", operatingLimit * 1.25, operatingLimit,
        () -> massFlow(network, lineName), "synthetic line operating procedure"));
  }

  /** Build one configured maximum-directed capacity constraint. */
  private CapacityConstraint maximumConstraint(String name, String unit, double designLimit, double operatingLimit,
      java.util.function.DoubleSupplier valueSupplier, String operatingReference) {
    return new CapacityConstraint(name, unit, ConstraintType.HARD).setDesignValue(designLimit)
        .setSource(ConstraintSource.VENDOR_DATASHEET, "synthetic installed rating").setDataSource("synthetic design")
        .setSeverity(ConstraintSeverity.HARD).setConfidence(1.0).setValidityRange(0.0, designLimit * 1.5)
        .setOperatingLimit(operatingLimit, ConstraintSource.USER_RULE, operatingReference)
        .setOperatingLimitConfidence(1.0).setOperatingLimitValidityRange(0.0, designLimit * 1.5)
        .setValueSupplier(valueSupplier);
  }

  /** Build one production WellFlow. */
  private WellFlow well(String name, double reservoirPressureBara, double productivityIndex) {
    Stream reservoir = new Stream(name + " reservoir", gas(reservoirPressureBara));
    reservoir.setFlowRate(0.03, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow(name + " well");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(productivityIndex);
    return well;
  }

  /** Get one well's latest applied production rate in kg/hr. */
  private double wellRate(FieldWellNetworkProcessUnit unit, String nodeId) {
    for (WellResult result : unit.getLastCouplingResult().getWellResults()) {
      if (nodeId.equals(result.getNodeId())) {
        return result.getAppliedRateKgS() * 3600.0;
      }
    }
    throw new IllegalArgumentException("Missing well result " + nodeId);
  }

  /** Get absolute network-edge mass flow in kg/hr. */
  private double massFlow(LoopedPipeNetwork network, String lineName) {
    return Math.abs(network.getPipe(lineName).getFlowRate()) * 3600.0;
  }

  /** Create the exact shared shaft-power definition for the host. */
  private PlantConstraintDefinition sharedPowerDefinition() {
    return PlantConstraintDefinition
        .builder("total-shaft-power", PlantConstraintScope.sharedResource("Host", "shared plant power"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kW")
        .basis("compressor and pump shaft power").provenance("synthetic shared plant power budget")
        .participant(PlantConstraintParticipant.direct("Field", "kW", "compressor and pump shaft power")).build();
  }

  /**
   * Configure the common objective, equipment, shaft, shared-power and export-boundary evidence.
   *
   * @param fixture complete coupled field-to-export fixture
   * @return configured fail-closed evaluator
   */
  private ProcessModelSimulationEvaluator createSimulationEvaluator(Fixture fixture) {
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("validated gas export",
        model -> Math.abs(fixture.exportValve.getOutletStream().getFlowRate("kg/hr")),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.addEquipmentCapacityConstraints();

    PlantCommonShaftEvidence commonShaftRegistration = fixture.commonShaftEvidence("common-shaft-registration");
    assertTrue(commonShaftRegistration.isComplete(), commonShaftRegistration.getDiagnostics().toString());
    simulation.addCommonShaftConstraintGroup("export-common-shaft", commonShaftRegistration,
        (model, calculationId) -> fixture.commonShaftEvidence(calculationId));
    PlantConstraintDefinition sharedPower = sharedPowerDefinition();
    simulation.addPlantConstraint(sharedPower,
        (model,
            calculationId) -> PlantSharedResourceEvidence.fromProcessModelShaftPower(sharedPower, calculationId,
                fixture.sharedPowerLimitKw, model, model.isModelConverged(), "synthetic shared plant power limit")
                .toPlantConstraintSample());

    ProcessBoundaryConstraintEvidence.Metadata exportPressure = new ProcessBoundaryConstraintEvidence.Metadata(
        "gas-export-pressure", "Field", "gas export boundary", ProcessBoundaryConstraintEvidence.Kind.EXPORT_CAPACITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.OUT_OF_PROCESS, NetworkDecisionVariable.RateBasis.MASS,
        "synthetic gas export specification", 1.0, null, null,
        ProcessBoundaryConstraintEvidence.ApplicabilityStatus.APPLICABLE, "pressure", "NeqSim process replay", null,
        -1);
    simulation.addBoundaryConstraint("gas export pressure", exportPressure,
        model -> ProcessBoundaryConstraintEvidence.Sample
            .available(fixture.exportValve.getOutletStream().getPressure("bara")),
        ProcessModelSimulationEvaluator.ConstraintDefinition.Type.EQUALITY, 100.0, Double.POSITIVE_INFINITY, 0.1,
        "bara", true, 1.0, 0.1);
    return simulation;
  }

  /** Verify every action and the physical network returned to the baseline route. */
  private void assertBaselineRestored(Fixture fixture) {
    LoopedPipeNetwork network = fixture.networkUnit.getHydraulicNetwork();
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      String suffix = fixture.wellSuffixes.get(index);
      int expectedRoute = index % 2;
      assertEquals(70.0, network.getPipe("choke " + suffix).getChokeOpening(), 0.0);
      assertEquals(expectedRoute,
          fixture.networkUnit.getExclusiveRouteSelection("producer " + suffix + " pressure level"));
      assertEquals(expectedRoute == 0 ? 1.0 : 0.0, network.getPipe(suffix + " HP route").getAvailability(), 0.0);
      assertEquals(expectedRoute == 1 ? 1.0 : 0.0, network.getPipe(suffix + " LP route").getAvailability(), 0.0);
    }
    assertEquals(fixture.baselineRouteAHigh, massFlow(network, "A HP route"), fixture.baselineRouteAHigh * 1.0e-5);
    assertTrue(fixture.highPressureScrubber.solved());
    assertTrue(fixture.lowPressureScrubber.solved());
    assertTrue(fixture.exportMixer.solved());
    assertTrue(fixture.exportValve.solved());
    assertEquals(100.0, fixture.exportValve.getOutletStream().getPressure("bara"), 1.0e-6);
    assertTrue(fixture.model.isModelConverged());
    assertTrue(fixture.networkUnit.getLastCouplingResult().isConverged());
  }

  /** Create a lean SRK gas for every live well and route. */
  private SystemInterface gas(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(330.15, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }
}
