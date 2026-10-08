package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
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
}
