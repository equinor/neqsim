package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.equipment.network.NetworkConstraint;
import neqsim.process.equipment.network.NetworkConstraintResult;
import neqsim.process.equipment.pipeline.RadialThermalLayer;
import neqsim.process.mechanicaldesign.subsea.SURFCostEstimator.LineDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Acceptance tests for canonical line thermal design and solved arrival constraints. */
class SubseaLineThermalDesignTest {
  /** Apply a typed route design without changing topology or hydraulic fidelity. */
  @Test
  void appliesTypedThermalDesignAndEvaluatesArrivalConstraint() {
    FieldNetworkTopology topology = createTopology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    NetworkPipe pipe = network.getPipe("production-flowline");
    PipeModelType originalModel = pipe.getHydraulicModelType();

    SubseaProductionSystem subsea = new SubseaProductionSystem("thermal design");
    LineDesign design = LineDesign.builder(8.625).wallThicknessMm(8.0)
        .insulation(RadialThermalLayer.MaterialType.PU_FOAM, 40.0).thermalBoundary(4.0, 300.0, 100.0)
        .minimumOutletTemperatureC(15.0).build();
    subsea.setLineDesign("production-flowline", design);

    assertEquals(1, subsea.applyLineThermalDesigns(topology));
    assertTrue(network.isThermalHydraulicsEnabled());
    assertEquals(originalModel, pipe.getHydraulicModelType());
    assertEquals(0.008, pipe.getWallThickness(), 1.0e-12);
    assertEquals(design.resolveOverallHeatTransferCoefficientWm2K(), pipe.getOverallHeatTransferCoeff(), 1.0e-12);
    assertEquals(277.15, pipe.getAmbientTemperature(), 1.0e-12);

    network.run();
    assertTrue(network.isConverged());
    assertTrue(Double.isFinite(pipe.getOutletTemperature()));
    assertTrue(pipe.getOutletTemperature() < 333.15);
    assertTrue(pipe.getOutletTemperature() > 277.15);

    List<NetworkConstraint> constraints = subsea.createMinimumOutletTemperatureConstraints(topology, true);
    assertEquals(1, constraints.size());
    NetworkConstraintResult configured = constraints.get(0).evaluate(network);
    double outletC = pipe.getOutletTemperature() - 273.15;
    assertEquals(Math.max(0.0, 15.0 - outletC), configured.getResidual(), 1.0e-9);

    NetworkConstraintResult passing = neqsim.process.equipment.network.NetworkConstraints
        .edgeMinimumOutletTemperature("production-flowline", outletC - 1.0, true).evaluate(network);
    NetworkConstraintResult failing = neqsim.process.equipment.network.NetworkConstraints
        .edgeMinimumOutletTemperature("production-flowline", outletC + 1.0, true).evaluate(network);
    assertTrue(passing.isSatisfied());
    assertFalse(failing.isSatisfied());
    assertEquals(1.0, failing.getResidual(), 1.0e-9);
  }

  /** Uniform route data must not overwrite explicit route-dependent thermal profiles. */
  @Test
  void rejectsProfileOverwriteAndUnsolvedConstraint() {
    FieldNetworkTopology topology = createTopology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    NetworkPipe pipe = network.getPipe("production-flowline");
    pipe.setHeatTransferProfile(new double[] {0.0, pipe.getLength()}, new double[] {2.0, 1.5});

    SubseaProductionSystem subsea = new SubseaProductionSystem("profile authority");
    subsea.setLineDesign("production-flowline", LineDesign.builder(8.625).wallThicknessMm(8.0)
        .overallHeatTransferCoefficientWm2K(1.0).minimumOutletTemperatureC(10.0).build());
    assertThrows(IllegalStateException.class, () -> subsea.applyLineThermalDesigns(topology));

    NetworkConstraintResult unsolved = subsea.createMinimumOutletTemperatureConstraints(topology, true).get(0)
        .evaluate(network);
    assertFalse(unsolved.isSatisfied());
    assertEquals(1.0e6, unsolved.getResidual(), 1.0e-12);
  }

  /** The solved steady arrival temperature must match the independent plug-flow energy equation. */
  @Test
  void matchesAnalyticalPlugFlowTemperatureForSolvedState() {
    FieldNetworkTopology topology = createTopology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    NetworkPipe pipe = network.getPipe("production-flowline");
    double uValue = 2.0;
    double ambientK = 277.15;
    SubseaProductionSystem subsea = new SubseaProductionSystem("analytical arrival");
    subsea.setLineDesign("production-flowline", LineDesign.builder(8.625).wallThicknessMm(8.0)
        .overallHeatTransferCoefficientWm2K(uValue).thermalBoundary(4.0, 300.0, 100.0).build());
    subsea.applyLineThermalDesigns(topology);

    network.run();

    SystemInterface pressureAdjusted = pipe.getInletFluid();
    double inletEnthalpy = pressureAdjusted.getEnthalpy();
    pressureAdjusted.setPressure(pipe.getOutletFluid().getPressure("bara"), "bara");
    ThermodynamicOperations operations = new ThermodynamicOperations(pressureAdjusted);
    operations.PHflash(inletEnthalpy);
    pressureAdjusted.initProperties();
    double conductance = uValue * Math.PI * pipe.getDiameter() * pipe.getLength();
    double exponent = -conductance / (Math.abs(pipe.getFlowRate()) * pressureAdjusted.getCp("J/kgK"));
    double expectedTemperatureK = ambientK + (pressureAdjusted.getTemperature("K") - ambientK) * Math.exp(exponent);

    assertEquals(expectedTemperatureK, pipe.getOutletTemperature(), 1.0e-8);
  }

  /** Build one canonical production route with hydraulics and thermal state on the same edge. */
  private FieldNetworkTopology createTopology() {
    FieldNetworkTopology topology = new FieldNetworkTopology("thermal tieback");
    topology.addPressureSource("well", "W-01", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -100.0);
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 3600.0, -100.0);
    topology.addPipe("production-flowline", "FL-01", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "well", "out", "host", "in", 5000.0, 0.203075, PipeModelType.DARCY_WEISBACH);

    SystemInterface gas = new SystemSrkEos(333.15, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas);
    network.setNodeFluid("well", gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(10.0);
    network.setMaxIterations(80);
    return topology;
  }
}
