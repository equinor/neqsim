package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
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
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldWellNetworkCoupler;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.network.FieldWellNetworkProcessUnit;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
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
    private final Splitter facilityRouter;
    private final Compressor highPressureCompressor;
    private final Compressor lowPressureCompressor;
    private final MechanicalShaft shaft;
    private final EnergyPort driverPort;
    private final CompressorDriver driver;
    private final Gearbox gearbox;
    private final double sharedPowerLimitKw;
    private final double baselineRouteAHigh;

    /**
     * Create one fixture holder.
     *
     * @param model coupled process model
     * @param networkUnit live well/network unit
     * @param highPressureSeparator high-pressure separator
     * @param lowPressureSeparator low-pressure separator
     * @param facilityRouter reversible host routing splitter
     * @param highPressureCompressor high-pressure compression casing
     * @param lowPressureCompressor low-pressure compression casing
     * @param shaft common shaft
     * @param driverPort driver output port
     * @param driver driver rating
     * @param gearbox gearbox rating
     * @param sharedPowerLimitKw shared plant power limit in kW
     * @param baselineRouteAHigh baseline producer-A high-pressure route flow in kg/hr
     */
    private Fixture(ProcessModel model, FieldWellNetworkProcessUnit networkUnit, Separator highPressureSeparator,
        Separator lowPressureSeparator, Splitter facilityRouter, Compressor highPressureCompressor,
        Compressor lowPressureCompressor, MechanicalShaft shaft, EnergyPort driverPort, CompressorDriver driver,
        Gearbox gearbox, double sharedPowerLimitKw, double baselineRouteAHigh) {
      this.model = model;
      this.networkUnit = networkUnit;
      this.highPressureSeparator = highPressureSeparator;
      this.lowPressureSeparator = lowPressureSeparator;
      this.facilityRouter = facilityRouter;
      this.highPressureCompressor = highPressureCompressor;
      this.lowPressureCompressor = lowPressureCompressor;
      this.shaft = shaft;
      this.driverPort = driverPort;
      this.driver = driver;
      this.gearbox = gearbox;
      this.sharedPowerLimitKw = sharedPowerLimitKw;
      this.baselineRouteAHigh = baselineRouteAHigh;
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
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total field production",
        model -> fixture.networkUnit.getOutletStream("host").getFlowRate("kg/hr"),
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

    ProcessModelOperatingAction chokeA = ProcessModelOperatingAction.continuous("choke-a", "Producer A choke",
        "Field::field network.choke.choke A.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction chokeB = ProcessModelOperatingAction.continuous("choke-b", "Producer B choke",
        "Field::field network.choke.choke B.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction routeHigh = ProcessModelOperatingAction.continuous("route-high",
        "Host flow to HP separator", "Field::facility router.splitFactor_0", 0.2, 0.8, "-",
        "synthetic reversible separator allocation");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "field-to-facility-actions", "Field-to-facility actions", "synthetic coupled acceptance", simulation,
        Arrays.asList(chokeA, chokeB, routeHigh))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer A deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer B deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
            "A gathering line capacity", "synthetic per-line operating limit");

    CandidateSetEvaluationResult swapped = candidates.evaluate(new double[] {60.0, 70.0, 0.4});
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, swapped.getOutcome(), swapped.getDiagnostics().toString());
    assertTrue(swapped.isBaselineRestored());
    assertTrue(swapped.isBaselineSimulationConverged());
    assertTrue(swapped.getPlantConstraintEvidence().stream().allMatch(PlantConstraintEvidence::hasAvailableEvidence));
    assertBaselineRestored(fixture);

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("field-to-facility-envelope",
        "Field-to-facility envelope", "synthetic coupled field and host acceptance", candidates);
    SliceResult slice = study.evaluateOneDimensional("choke-a", new double[] {70.0, 90.0},
        new double[] {70.0, 70.0, 0.5});

    assertTrue(slice.isComplete());
    assertTrue(slice.getPoints().get(0).isFeasible(), slice.getPoints().get(0).getEvaluation().getOutcome() + ": "
        + slice.getPoints().get(0).getEvaluation().getDiagnostics());
    assertFalse(slice.getPoints().get(1).isFeasible());
    LeadingConstraintEvidence leading = slice.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.REQUIRED_HYDRAULIC, leading.getSource());
    assertTrue(leading.getQualifiedConstraintName().endsWith("/A gathering line capacity"));
    assertEquals("REQUIRED_HYDRAULIC", leading.getApplicableLimitRole());
    assertTrue(slice.getPoints().get(1).getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> evidence.getQualifiedConstraintName().endsWith("/A gathering line capacity")
            && evidence.getApplicableLimitRole() == CapacityConstraint.ApplicableLimitRole.CONFIGURED_OPERATING));
    assertTrue(slice.getBottleneckTransitions().isEmpty());
    assertBaselineRestored(fixture);
  }

  /** Build and calibrate the complete synthetic field-to-host process. */
  private Fixture createFixture() {
    FieldNetworkTopology topology = new FieldNetworkTopology("field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(140.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    addWellRoute(topology, "A", 75.0, 70.0);
    addWellRoute(topology, "B", 75.0, 70.0);
    topology.addJunction("field manifold", "MA-FIELD", NodeRole.MANIFOLD, Service.PRODUCTION, -150.0);
    topology.addFixedPressureSink("host", "V-HOST", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    addGatheringLine(topology, "A gathering line", "A header", "A inlet");
    addGatheringLine(topology, "B gathering line", "B header", "B inlet");
    topology.addPipe("export riser", "RI-HOST", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO,
        "field manifold", "riser outlet", "host", "arrival", 500.0, 0.25,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer A", well("producer A", 190.0, 1.1e-7));
    coupler.bindProductionWell("producer B", well("producer B", 185.0, 0.9e-7));
    FieldWellNetworkProcessUnit networkUnit = new FieldWellNetworkProcessUnit("field network", coupler);
    networkUnit.run(UUID.randomUUID());
    assertTrue(networkUnit.solved(),
        networkUnit.getLastCouplingResult().getMessage() + "; rate residual="
            + networkUnit.getLastCouplingResult().getMaximumRateResidualKgS() + "; pressure residual="
            + networkUnit.getLastCouplingResult().getMaximumPressureResidualBar() + "; network residual="
            + networkUnit.getLastCouplingResult().getNetworkResidualPa() + "; mass balance="
            + networkUnit.getLastCouplingResult().getMassBalanceResidualKgS());
    assertTrue(network.isProductionOptimizationApplicable());

    Splitter facilityRouter = new Splitter("facility router", networkUnit.getOutletStream("host"), 2);
    facilityRouter.setSplitFactors(new double[] {0.5, 0.5});
    Separator highPressureSeparator = new Separator("HP separator", facilityRouter.getSplitStream(0));
    Separator lowPressureSeparator = new Separator("LP separator", facilityRouter.getSplitStream(1));
    Compressor highPressureCompressor = new Compressor("HP compressor", highPressureSeparator.getGasOutStream());
    Compressor lowPressureCompressor = new Compressor("LP compressor", lowPressureSeparator.getGasOutStream());
    highPressureCompressor.setOutletPressure(100.0, "bara");
    lowPressureCompressor.setOutletPressure(100.0, "bara");

    ProcessSystem process = new ProcessSystem("field to facility");
    process.add(networkUnit);
    process.add(facilityRouter);
    process.add(highPressureSeparator);
    process.add(lowPressureSeparator);
    process.add(highPressureCompressor);
    process.add(lowPressureCompressor);
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

    double initialRouteAHigh = massFlow(network, "A gathering line");
    network.getPipe("choke A").setChokeOpening(90.0);
    model.run();
    assertTrue(model.isModelConverged());
    assertTrue(network.isProductionOptimizationApplicable());
    double highRouteAHigh = massFlow(network, "A gathering line");
    assertTrue(highRouteAHigh > initialRouteAHigh);
    network.getPipe("choke A").setChokeOpening(70.0);
    model.run();
    assertTrue(model.isModelConverged());
    double baselineRouteAHigh = massFlow(network, "A gathering line");
    assertTrue(highRouteAHigh > baselineRouteAHigh);
    double routeAHighOperatingLimit = 0.5 * (baselineRouteAHigh + highRouteAHigh);

    configureInstalledLimits(networkUnit, highPressureSeparator, lowPressureSeparator, network,
        routeAHighOperatingLimit);
    return new Fixture(model, networkUnit, highPressureSeparator, lowPressureSeparator, facilityRouter,
        highPressureCompressor, lowPressureCompressor, shaft, driverPort, driver, gearbox, baselinePowerKw * 1.8,
        baselineRouteAHigh);
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

  /** Add one well-specific gathering line to the shared field manifold. */
  private void addGatheringLine(FieldNetworkTopology topology, String name, String from, String arrivalPort) {
    topology.addPipe(name, "FL-" + name, EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, from, "outlet",
        "field manifold", arrivalPort, 1200.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
  }

  /** Add operating/default limits for wells, lines and both separators. */
  private void configureInstalledLimits(FieldWellNetworkProcessUnit networkUnit, Separator highPressureSeparator,
      Separator lowPressureSeparator, LoopedPipeNetwork network, double routeAHighOperatingLimit) {
    networkUnit.clearCapacityConstraints();
    networkUnit.addCapacityConstraint(maximumConstraint("producer A deliverability", "kg/hr",
        wellRate(networkUnit, "producer A") * 1.5, wellRate(networkUnit, "producer A") * 1.3,
        () -> wellRate(networkUnit, "producer A"), "synthetic well A operating procedure"));
    networkUnit.addCapacityConstraint(maximumConstraint("producer B deliverability", "kg/hr",
        wellRate(networkUnit, "producer B") * 1.5, wellRate(networkUnit, "producer B") * 1.3,
        () -> wellRate(networkUnit, "producer B"), "synthetic well B operating procedure"));
    addLineConstraint(networkUnit, network, "A gathering line", routeAHighOperatingLimit);
    addLineConstraint(networkUnit, network, "B gathering line",
        Math.max(100.0, massFlow(network, "B gathering line") * 1.5));

    highPressureSeparator.clearCapacityConstraints();
    lowPressureSeparator.clearCapacityConstraints();
    double highSeparatorFlow = highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double lowSeparatorFlow = lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double totalSeparatorFlow = highSeparatorFlow + lowSeparatorFlow;
    highPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic HP separator operating procedure"));
    lowPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic LP separator operating procedure"));
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

  /** Verify every action and the physical network returned to the baseline route. */
  private void assertBaselineRestored(Fixture fixture) {
    LoopedPipeNetwork network = fixture.networkUnit.getHydraulicNetwork();
    assertEquals(70.0, network.getPipe("choke A").getChokeOpening(), 0.0);
    assertEquals(70.0, network.getPipe("choke B").getChokeOpening(), 0.0);
    assertEquals(0.5, fixture.facilityRouter.getSplitFactors()[0], 1.0e-12);
    assertEquals(0.5, fixture.facilityRouter.getSplitFactors()[1], 1.0e-12);
    assertEquals(fixture.baselineRouteAHigh, massFlow(network, "A gathering line"),
        fixture.baselineRouteAHigh * 1.0e-5);
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
