package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Process-system integration tests for live well/network coupling.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class FieldWellNetworkProcessUnitTest {
  /** Execute the canonical coupled graph before downstream process equipment and expose reversible network actions. */
  @Test
  void runsInsideProcessModelAndExposesNetworkAutomation() {
    FieldWellNetworkProcessUnit unit = createUnit();
    unit.run(UUID.randomUUID());
    assertTrue(unit.solved(),
        unit.getLastCouplingResult().getMessage() + "; rate residual="
            + unit.getLastCouplingResult().getMaximumRateResidualKgS() + "; pressure residual="
            + unit.getLastCouplingResult().getMaximumPressureResidualBar() + "; network residual="
            + unit.getLastCouplingResult().getNetworkResidualPa());
    assertNotNull(unit.getOutletStream("host"));

    Separator separator = new Separator("host separator", unit.getOutletStream("host"));
    ProcessSystem field = new ProcessSystem("field and host");
    field.add(unit);
    field.add(separator);
    ProcessModel model = new ProcessModel();
    model.add("Field", field);
    model.run();

    assertTrue(model.isModelConverged());
    assertTrue(unit.getOutletStream("host").getFlowRate("kg/hr") > 0.0);
    assertTrue(unit.getHydraulicNetwork().isProductionOptimizationApplicable());
    ProcessAutomation automation = model.getAutomation();
    String openingAddress = "Field::field network.choke.production choke.opening";
    assertEquals(75.0, automation.getVariableValue(openingAddress, "%"), 0.0);
    automation.setVariableValue(openingAddress, 65.0, "%");
    model.run();
    assertTrue(model.isModelConverged());
    assertEquals(65.0, automation.getVariableValue(openingAddress, "%"), 0.0);
    assertEquals(1.0, automation.getVariableValue("Field::field network.edge.flowline.availability", "-"), 0.0);
  }

  /** A copied process unit must restore its runtime well bindings and remain executable. */
  @Test
  void copyRestoresRuntimeBindings() {
    FieldWellNetworkProcessUnit original = createUnit();
    FieldWellNetworkProcessUnit copy = (FieldWellNetworkProcessUnit) original.copy();

    assertNotNull(copy.getTopology().getBoundEquipment("producer"));
    copy.run(UUID.randomUUID());

    assertTrue(copy.solved(), copy.getLastCouplingResult().getMessage());
    assertTrue(copy.getOutletStream("host").getFlowRate("kg/hr") > 0.0);
  }

  /** Route selection must switch distinct-pressure paths atomically and preserve live well feedback. */
  @Test
  void switchesExclusivePressureRoutesThroughAutomationAndRestoresExactly() {
    FieldWellNetworkProcessUnit unit = createRoutedUnit();
    unit.run(UUID.randomUUID());
    assertTrue(unit.solved(), unit.getLastCouplingResult().getMessage());

    Separator hpSeparator = new Separator("HP separator", unit.getOutletStream("HP host"));
    Separator lpSeparator = new Separator("LP separator", unit.getOutletStream("LP host"));
    ProcessSystem field = new ProcessSystem("routed field and host");
    field.add(unit);
    field.add(hpSeparator);
    field.add(lpSeparator);
    ProcessModel model = new ProcessModel();
    model.add("Field", field);
    model.run();
    assertTrue(model.isModelConverged());
    double baselineRate = unit.getOutletStream("HP host").getFlowRate("kg/hr");
    double baselineWellPressure = unit.getHydraulicNetwork().getNodePressure("producer");
    assertTrue(baselineRate > 0.0);
    assertEquals(0.0, unit.getOutletStream("LP host").getFlowRate("kg/hr"), 1.0e-5);

    ProcessAutomation automation = model.getAutomation();
    String routeAddress = "Field::routed field network.route.producer pressure level.selection";
    assertTrue(automation.isWritableAddress(routeAddress));
    assertEquals(0.0, automation.getVariableValue(routeAddress, "-"), 0.0);
    automation.setVariableValue(routeAddress, 1.0, "-");
    assertFalse(unit.solved());
    assertEquals(0.0, unit.getHydraulicNetwork().getPipe("HP route").getAvailability(), 0.0);
    assertEquals(1.0, unit.getHydraulicNetwork().getPipe("LP route").getAvailability(), 0.0);
    model.run();

    assertTrue(model.isModelConverged());
    assertEquals(1.0, automation.getVariableValue(routeAddress, "-"), 0.0);
    assertEquals(0.0, unit.getOutletStream("HP host").getFlowRate("kg/hr"), 1.0e-5);
    assertTrue(unit.getOutletStream("LP host").getFlowRate("kg/hr") > baselineRate);
    assertTrue(unit.getHydraulicNetwork().getNodePressure("producer") < baselineWellPressure);

    assertThrows(IllegalArgumentException.class, () -> automation.setVariableValue(routeAddress, 0.5, "-"));
    assertEquals(0.0, unit.getHydraulicNetwork().getPipe("HP route").getAvailability(), 0.0);
    assertEquals(1.0, unit.getHydraulicNetwork().getPipe("LP route").getAvailability(), 0.0);
    automation.setVariableValue(routeAddress, 0.0, "-");
    model.run();
    assertTrue(model.isModelConverged());
    assertEquals(baselineRate, unit.getOutletStream("HP host").getFlowRate("kg/hr"), baselineRate * 1.0e-5);
    assertEquals(baselineWellPressure, unit.getHydraulicNetwork().getNodePressure("producer"), 1.0e-5);

    FieldWellNetworkProcessUnit copy = (FieldWellNetworkProcessUnit) unit.copy();
    assertEquals(Arrays.asList("producer pressure level"), copy.getExclusiveRouteSelectorNames());
    assertEquals(Arrays.asList("HP route", "LP route"), copy.getExclusiveRouteEdges("producer pressure level"));
    copy.setExclusiveRouteSelection("producer pressure level", 1.0);
    assertEquals(0.0, unit.getExclusiveRouteSelection("producer pressure level"), 0.0);
    assertEquals(1.0, copy.getExclusiveRouteSelection("producer pressure level"), 0.0);
  }

  /** Multi-edge direct, brownfield and dual-path line-ups must use one canonical graph and restore exactly. */
  @Test
  void switchesCanonicalMultiEdgeRouteConfigurationsThroughAutomation() {
    FieldWellNetworkProcessUnit unit = createConfiguredUnit();
    unit.run(UUID.randomUUID());
    assertTrue(unit.solved(), unit.getLastCouplingResult().getMessage());

    Separator separator = new Separator("configured host separator", unit.getOutletStream("host"));
    ProcessSystem field = new ProcessSystem("configured field and host");
    field.add(unit);
    field.add(separator);
    ProcessModel model = new ProcessModel();
    model.add("Field", field);
    model.run();
    assertTrue(model.isModelConverged());

    ProcessAutomation automation = model.getAutomation();
    String address = "Field::configured field network.configuration.host route.selection";
    assertTrue(automation.isWritableAddress(address));
    assertEquals(Arrays.asList("primary", "brownfield", "dual path"), unit.getRouteConfigurationNames("host route"));
    assertEquals(Arrays.asList("primary flowline", "primary riser"),
        unit.getRouteConfigurations("host route").get(0).getAvailableEdgeNames());
    assertEquals(0.0, automation.getVariableValue(address, "-"), 0.0);
    double primaryRate = unit.getOutletStream("host").getFlowRate("kg/hr");
    double primaryWellPressure = unit.getHydraulicNetwork().getNodePressure("producer");

    automation.setVariableValue(address, 1.0, "-");
    assertFalse(unit.solved());
    assertConfiguration(unit, 0.0, 0.0, 1.0, 1.0);
    model.run();
    assertTrue(model.isModelConverged());
    double brownfieldRate = unit.getOutletStream("host").getFlowRate("kg/hr");
    assertTrue(brownfieldRate < primaryRate);
    assertTrue(unit.getHydraulicNetwork().getNodePressure("producer") > primaryWellPressure);

    automation.setVariableValue(address, 2.0, "-");
    assertConfiguration(unit, 1.0, 1.0, 1.0, 1.0);
    model.run();
    assertTrue(model.isModelConverged());
    double dualPathRate = unit.getOutletStream("host").getFlowRate("kg/hr");
    assertTrue(dualPathRate > brownfieldRate);
    assertTrue(unit.getHydraulicNetwork().getPipe("primary flowline").getFlowRate() > 0.0);
    assertTrue(unit.getHydraulicNetwork().getPipe("brownfield flowline").getFlowRate() > 0.0);
    assertEquals(0.0, unit.getLastCouplingResult().getMassBalanceResidualKgS(), 1.0e-8);
    assertEquals(LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH,
        unit.getHydraulicNetwork().getEffectiveHydraulicModelType("primary flowline"));

    assertThrows(IllegalArgumentException.class, () -> automation.setVariableValue(address, 1.5, "-"));
    assertConfiguration(unit, 1.0, 1.0, 1.0, 1.0);

    automation.setVariableValue(address, 0.0, "-");
    model.run();
    assertTrue(model.isModelConverged());
    assertEquals(primaryRate, unit.getOutletStream("host").getFlowRate("kg/hr"), primaryRate * 1.0e-5);
    assertEquals(primaryWellPressure, unit.getHydraulicNetwork().getNodePressure("producer"), 1.0e-5);

    FieldWellNetworkProcessUnit copy = (FieldWellNetworkProcessUnit) unit.copy();
    assertEquals(Arrays.asList("host route"), copy.getRouteConfigurationSelectorNames());
    copy.setRouteConfigurationSelection("host route", 1.0);
    assertEquals(0, unit.getRouteConfigurationSelection("host route"));
    assertEquals(1, copy.getRouteConfigurationSelection("host route"));

    assertThrows(IllegalArgumentException.class,
        () -> unit.registerRouteConfigurationSelector("overlap",
            Arrays.asList(
                new FieldWellNetworkProcessUnit.RouteConfiguration("primary only", Arrays.asList("primary flowline")),
                new FieldWellNetworkProcessUnit.RouteConfiguration("choke only", Arrays.asList("production choke"))),
            "invalid overlapping selector"));
    unit.getHydraulicNetwork().getPipe("primary flowline").setAvailability(0.5);
    assertThrows(IllegalStateException.class, () -> unit.getRouteConfigurationSelection("host route"));
    unit.getHydraulicNetwork().getPipe("primary flowline").setAvailability(1.0);
  }

  /** Live injection must switch complete line-ups and respond to controlled reservoir-pressure updates. */
  @Test
  void switchesLiveInjectionRouteConfigurationsAndReservoirState() {
    WellFlow injectionWell = createConfiguredInjectionWell();
    FieldWellNetworkProcessUnit unit = createConfiguredInjectionUnit(injectionWell);
    assertTrue(Double.isNaN(unit.getPower("kW")), "Unsolved mapped network power must fail closed");
    ProcessSystem injection = new ProcessSystem("injection field");
    injection.add(unit);
    ProcessModel model = new ProcessModel();
    model.add("Injection", injection);
    model.run();

    assertTrue(model.isModelConverged());
    assertTrue(unit.solved(), unit.getLastCouplingResult().getMessage());
    String address = "Injection::configured injection network.configuration.injector route.selection";
    ProcessAutomation automation = model.getAutomation();
    assertTrue(automation.isWritableAddress(address));
    double primaryRate = unit.getLastCouplingResult().getWellResults().get(0).getAppliedRateKgS();
    assertTrue(primaryRate > 0.0);
    LoopedPipeNetwork.NetworkPipe injectionPump = unit.getHydraulicNetwork().getPipe("injection pump");
    assertTrue(injectionPump.getPumpModel().getPumpChart().isUsePumpChart());
    assertTrue(injectionPump.getPumpPowerKW() > 0.0);
    assertTrue(Double.isFinite(injectionPump.getPumpNpshaM()));
    assertEquals(injectionPump.getPumpPowerKW(), unit.getPower("kW"), 1.0e-9);
    assertEquals(unit.getPower("kW"), model.getPower("kW"), 1.0e-9);
    assertFalse(injectionWell.getZoneFractureRisk()[0]);
    assertFalse(injectionWell.getZoneFractureRisk()[1]);

    automation.setVariableValue(address, 1.0, "-");
    model.run();
    assertTrue(unit.solved(), couplingDiagnostic(unit));
    assertTrue(model.isModelConverged(), couplingDiagnostic(unit));
    double brownfieldRate = unit.getLastCouplingResult().getWellResults().get(0).getAppliedRateKgS();
    assertTrue(brownfieldRate > 0.0);
    assertEquals(0.0, unit.getHydraulicNetwork().getPipe("primary injection flowline").getFlowRate(), 1.0e-12);
    assertTrue(unit.getHydraulicNetwork().getPipe("brownfield injection flowline").getFlowRate() > 0.0);

    automation.setVariableValue(address, 2.0, "-");
    model.run();
    assertTrue(unit.solved(), couplingDiagnostic(unit));
    assertTrue(model.isModelConverged(), couplingDiagnostic(unit));
    double dualPathRate = unit.getLastCouplingResult().getWellResults().get(0).getAppliedRateKgS();
    assertTrue(dualPathRate > 0.0);
    assertTrue(unit.getHydraulicNetwork().getPipe("primary injection flowline").getFlowRate() > 0.0);
    assertTrue(unit.getHydraulicNetwork().getPipe("brownfield injection flowline").getFlowRate() > 0.0);
    assertEquals(0.0, unit.getLastCouplingResult().getMassBalanceResidualKgS(), 1.0e-8);
    assertEquals(1.0, injectionWell.getZoneAllocationFractions()[0] + injectionWell.getZoneAllocationFractions()[1],
        1.0e-12);

    automation.setVariableValue(address, 0.0, "-");
    model.run();
    assertEquals(primaryRate, unit.getLastCouplingResult().getWellResults().get(0).getAppliedRateKgS(),
        primaryRate * 1.0e-4);
    injectionWell.getLayer(0).setReservoirPressure(115.0, "bara");
    injectionWell.getLayer(1).setReservoirPressure(125.0, "bara");
    assertEquals(115.0, injectionWell.getLayer(0).getReservoirPressure("bara"), 0.0);
    assertEquals(115.0, injectionWell.getLayer(0).stream.getPressure("bara"), 0.0);
    model.run();
    double higherReservoirPressureRate = unit.getLastCouplingResult().getWellResults().get(0).getAppliedRateKgS();
    assertTrue(higherReservoirPressureRate < primaryRate,
        "Higher reservoir pressure must reduce injectivity at unchanged source pressure and route");
    assertTrue(Double.isFinite(unit.getPower("kW")));
    assertTrue(unit.getPower("kW") > 0.0);
    assertTrue(unit.solved(), unit.getLastCouplingResult().getMessage());
    assertFalse(injectionWell.getZoneFractureRisk()[0]);
    assertFalse(injectionWell.getZoneFractureRisk()[1]);
  }

  /** Build a compact failure diagnostic for coupled acceptance assertions. */
  private String couplingDiagnostic(FieldWellNetworkProcessUnit unit) {
    return unit.getLastCouplingResult().getMessage() + "; iterations=" + unit.getLastCouplingResult().getIterations()
        + "; rate residual=" + unit.getLastCouplingResult().getMaximumRateResidualKgS() + "; pressure residual="
        + unit.getLastCouplingResult().getMaximumPressureResidualBar() + "; network residual="
        + unit.getLastCouplingResult().getNetworkResidualPa();
  }

  /** The process unit must remain unsolved when the complete coupled convergence contract is not met. */
  @Test
  void incompleteCouplingFailsClosed() {
    FieldWellNetworkProcessUnit unit = createUnit();
    unit.getCoupler().setMaximumIterations(1);

    unit.run(UUID.randomUUID());

    assertFalse(unit.solved());
    assertFalse(unit.getLastCouplingResult().isConverged());
  }

  /**
   * Create one live gas well, IEC choke, flowline and fixed-pressure host boundary.
   *
   * @return configured process unit
   */
  private FieldWellNetworkProcessUnit createUnit() {
    FieldNetworkTopology topology = new FieldNetworkTopology("field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(150.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addLiveWellNode("producer", "XT-101", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 75.0, -200.0);
    topology.addJunction("header", "MA-101", NodeRole.MANIFOLD, Service.PRODUCTION, -200.0);
    topology.addFixedPressureSink("host", "SEP-101", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    network.addChoke("producer", "header", "production choke", 30.0, 75.0);
    topology.registerExistingEdge("production choke", "XV-101", EdgeRole.CHOKE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well", "header");
    topology.addPipe("flowline", "FL-101", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "header",
        "outlet", "host", "arrival", 1500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    Stream reservoir = new Stream("reservoir", gas(200.0));
    reservoir.setFlowRate(0.02, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow("producer well");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(1.0e-7);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer", well);
    return new FieldWellNetworkProcessUnit("field network", coupler);
  }

  /** Create one live well with mutually exclusive HP and LP host paths. */
  private FieldWellNetworkProcessUnit createRoutedUnit() {
    FieldNetworkTopology topology = new FieldNetworkTopology("routed field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(150.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addLiveWellNode("producer", "XT-201", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 75.0, -200.0);
    topology.addJunction("header", "MA-201", NodeRole.MANIFOLD, Service.PRODUCTION, -200.0);
    topology.addFixedPressureSink("HP host", "SEP-HP", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    topology.addFixedPressureSink("LP host", "SEP-LP", NodeRole.HOST, Service.PRODUCTION, 30.0, 0.0);
    network.addChoke("producer", "header", "production choke", 30.0, 75.0);
    topology.registerExistingEdge("production choke", "XV-201", EdgeRole.CHOKE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well", "header");
    topology.addPipe("HP route", "FL-HP", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "header",
        "HP outlet", "HP host", "arrival", 1500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("LP route", "FL-LP", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "header",
        "LP outlet", "LP host", "arrival", 1500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    network.getPipe("HP route").setAvailability(1.0);
    network.getPipe("LP route").setAvailability(0.0);

    Stream reservoir = new Stream("routed reservoir", gas(200.0));
    reservoir.setFlowRate(0.02, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow("routed producer well");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(1.0e-7);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(100);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer", well);
    return new FieldWellNetworkProcessUnit("routed field network", coupler).registerExclusiveRouteSelector(
        "producer pressure level", Arrays.asList("HP route", "LP route"), "synthetic HP/LP host line-up");
  }

  /** Create one live well with two alternative two-edge host paths and a combined looped line-up. */
  private FieldWellNetworkProcessUnit createConfiguredUnit() {
    FieldNetworkTopology topology = new FieldNetworkTopology("configured field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(150.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addLiveWellNode("producer", "XT-301", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 75.0, -200.0);
    topology.addJunction("header", "MA-301", NodeRole.MANIFOLD, Service.PRODUCTION, -200.0);
    topology.addJunction("primary PLEM", "PLEM-301", NodeRole.PLEM, Service.PRODUCTION, -200.0);
    topology.addJunction("brownfield tie-in", "TI-301", NodeRole.BROWNFIELD_TIE_IN, Service.PRODUCTION, -200.0);
    topology.addFixedPressureSink("host", "SEP-301", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    network.addChoke("producer", "header", "production choke", 30.0, 75.0);
    topology.registerExistingEdge("production choke", "XV-301", EdgeRole.CHOKE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well", "header");
    topology.addPipe("primary flowline", "FL-301", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "header", "primary outlet", "primary PLEM", "inlet", 900.0, 0.22,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("primary riser", "RI-301", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO,
        "primary PLEM", "outlet", "host", "primary arrival", 700.0, 0.22,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("brownfield flowline", "FL-302", EdgeRole.TIE_IN, Service.PRODUCTION, FlowDirection.FROM_TO,
        "header", "brownfield outlet", "brownfield tie-in", "inlet", 2800.0, 0.16,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("brownfield riser", "RI-302", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO,
        "brownfield tie-in", "outlet", "host", "brownfield arrival", 900.0, 0.16,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    network.getPipe("brownfield flowline").setAvailability(0.0);
    network.getPipe("brownfield riser").setAvailability(0.0);

    Stream reservoir = new Stream("configured reservoir", gas(200.0));
    reservoir.setFlowRate(0.02, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow("configured producer well");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(1.0e-7);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(100);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer", well);
    return new FieldWellNetworkProcessUnit("configured field network", coupler).registerRouteConfigurationSelector(
        "host route",
        Arrays.asList(
            new FieldWellNetworkProcessUnit.RouteConfiguration("primary",
                Arrays.asList("primary flowline", "primary riser")),
            new FieldWellNetworkProcessUnit.RouteConfiguration("brownfield",
                Arrays.asList("brownfield flowline", "brownfield riser")),
            new FieldWellNetworkProcessUnit.RouteConfiguration("dual path",
                Arrays.asList("primary flowline", "primary riser", "brownfield flowline", "brownfield riser"))),
        "synthetic direct, brownfield and redundant host line-ups");
  }

  /** Create a live two-zone injector for route and lifecycle acceptance. */
  private WellFlow createConfiguredInjectionWell() {
    WellFlow well = new WellFlow("configured injection well");
    well.setInletStream(new Stream("injection water", water(180.0)));
    well.addInjectionZone("target", new Stream("target reservoir zone", water(100.0)), 100.0, 7.0e-8, 205.0);
    well.addInjectionZone("secondary", new Stream("secondary reservoir zone", water(110.0)), 110.0, 3.0e-8, 200.0);
    well.setTargetZone("target");
    return well;
  }

  /** Create primary, brownfield and dual-path injection line-ups on one canonical graph. */
  private FieldWellNetworkProcessUnit createConfiguredInjectionUnit(WellFlow well) {
    FieldNetworkTopology topology = new FieldNetworkTopology("configured injection network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water(180.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(3000.0);
    topology.addPressureSource("injection source", "HOST-I401", NodeRole.INJECTION_SOURCE, Service.INJECTION, 10.0, 0.0,
        0.0);
    topology.addJunction("injection pump discharge", "P-401-D", NodeRole.JUNCTION, Service.INJECTION, 0.0);
    topology.addJunction("primary injection PLEM", "PLEM-I401", NodeRole.PLEM, Service.INJECTION, 0.0);
    topology.addJunction("brownfield injection tie-in", "TI-I401", NodeRole.BROWNFIELD_TIE_IN, Service.INJECTION, 0.0);
    topology.addLiveWellNode("injector", "XT-I401", NodeRole.INJECTION_WELL, Service.INJECTION, 145.0, 0.0);
    Pump pump = new Pump("injection pump model", new Stream("injection pump reference fluid", water(10.0)));
    pump.setSpeed(3600.0);
    pump.getPumpChart().setCurves(new double[] {288.15, 1.0, 1.0, 1.0}, new double[] {3600.0},
        new double[][] {{0.5, 2.0, 5.0}}, new double[][] {{1900.0, 1800.0, 1550.0}},
        new double[][] {{68.0, 82.0, 72.0}});
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setUsePumpChart(true);
    LoopedPipeNetwork.NetworkPipe pumpEdge = topology.addPumpWithCurve("injection pump", "P-401", Service.INJECTION,
        FlowDirection.FROM_TO, "injection source", "suction", "injection pump discharge", "discharge", pump);
    pumpEdge.setPumpRatedPowerKW(150.0);
    pumpEdge.setPumpMinimumFlowKgS(0.1);
    topology.addPipe("primary injection flowline", "WI-401", EdgeRole.FLOWLINE, Service.INJECTION,
        FlowDirection.FROM_TO, "injection pump discharge", "primary discharge", "primary injection PLEM", "inlet",
        900.0, 0.22, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("primary injection jumper", "JU-I401", EdgeRole.JUMPER, Service.INJECTION, FlowDirection.FROM_TO,
        "primary injection PLEM", "outlet", "injector", "primary injection", 700.0, 0.22,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("brownfield injection flowline", "WI-402", EdgeRole.TIE_IN, Service.INJECTION,
        FlowDirection.FROM_TO, "injection pump discharge", "brownfield discharge", "brownfield injection tie-in",
        "inlet", 2800.0, 0.16, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("brownfield injection jumper", "JU-I402", EdgeRole.JUMPER, Service.INJECTION,
        FlowDirection.FROM_TO, "brownfield injection tie-in", "outlet", "injector", "brownfield injection", 900.0, 0.16,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    network.getPipe("brownfield injection flowline").setAvailability(0.0);
    network.getPipe("brownfield injection jumper").setAvailability(0.0);

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(200);
    coupler.setTolerances(2.0e-7, 3.0e-5);
    coupler.setRelaxationFactor(0.6);
    coupler.bindInjectionWell("injector", well);
    return new FieldWellNetworkProcessUnit("configured injection network", coupler)
        .registerRouteConfigurationSelector("injector route",
            Arrays.asList(
                new FieldWellNetworkProcessUnit.RouteConfiguration("primary",
                    Arrays.asList("primary injection flowline", "primary injection jumper")),
                new FieldWellNetworkProcessUnit.RouteConfiguration("brownfield",
                    Arrays.asList("brownfield injection flowline", "brownfield injection jumper")),
                new FieldWellNetworkProcessUnit.RouteConfiguration("dual path",
                    Arrays.asList("primary injection flowline", "primary injection jumper",
                        "brownfield injection flowline", "brownfield injection jumper"))),
            "synthetic primary, brownfield and redundant injection line-ups");
  }

  /** Assert exact availability on the four controlled configuration edges. */
  private void assertConfiguration(FieldWellNetworkProcessUnit unit, double primaryFlowline, double primaryRiser,
      double brownfieldFlowline, double brownfieldRiser) {
    assertEquals(primaryFlowline, unit.getHydraulicNetwork().getPipe("primary flowline").getAvailability(), 0.0);
    assertEquals(primaryRiser, unit.getHydraulicNetwork().getPipe("primary riser").getAvailability(), 0.0);
    assertEquals(brownfieldFlowline, unit.getHydraulicNetwork().getPipe("brownfield flowline").getAvailability(), 0.0);
    assertEquals(brownfieldRiser, unit.getHydraulicNetwork().getPipe("brownfield riser").getAvailability(), 0.0);
  }

  /**
   * Create a lean SRK gas.
   *
   * @param pressureBara pressure in bara
   * @return initialized gas fluid
   */
  private SystemInterface gas(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(330.15, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Create a single-component water fluid.
   *
   * @param pressureBara pressure in bara
   * @return initialized water fluid
   */
  private SystemInterface water(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }
}
