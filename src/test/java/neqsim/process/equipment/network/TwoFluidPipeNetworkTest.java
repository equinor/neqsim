package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

@Tag("slow")
class TwoFluidPipeNetworkTest {
  @Test
  void serialBranchesAndStorageNodeCloseWholeNetworkMass() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("serial network");
    network.addFixedPressureNode("source", 60.0e5);
    network.addCompressibleNode("manifold", createVolume(55.0e5, 1000.0));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("upstream", "source", "manifold", createPipe("upstream"));
    network.addPipe("downstream", "manifold", "sink", createPipe("downstream"));

    network.runTransient(0.01, UUID.randomUUID());

    TwoFluidPipeNetwork.BalanceReport report = network.getLastBalanceReport();
    for (Phase phase : Phase.values()) {
      assertTrue(report.getRelativeResidual(phase) < 1.0e-8,
          phase + " network residual=" + report.getRelativeResidual(phase));
    }
    assertTrue(Double.isFinite(network.getNodePressurePa("manifold")));
    assertEquals(0.01, network.getSimulationTimeSeconds(), 0.0);
  }

  @Test
  void identicalSplitBranchesRemainSymmetric() {
    UpstreamCompressibleVolume splitter = createVolume(60.0e5, 1.0e5);
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("symmetric split");
    network.addCompressibleNode("splitter", splitter);
    network.addFixedPressureNode("sink A", 50.0e5);
    network.addFixedPressureNode("sink B", 50.0e5);
    network.addPipe("branch A", "splitter", "sink A", createPipe("branch A"));
    network.addPipe("branch B", "splitter", "sink B", createPipe("branch B"));

    network.runTransient(0.01, UUID.randomUUID());

    TwoFluidMassBalanceReport first = network.getPipe("branch A").getLastMassBalanceReport();
    TwoFluidMassBalanceReport second = network.getPipe("branch B").getLastMassBalanceReport();
    assertEquals(first.getOutletMassKg(Phase.TOTAL), second.getOutletMassKg(Phase.TOTAL), 1.0e-12);
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
  }

  @Test
  void conservativeMergeClosesComponentsAndEnergyIndependentOfBranchOrder() {
    TwoFluidPipeNetwork first = createConservativeMerge(false);
    TwoFluidPipeNetwork reversed = createConservativeMerge(true);
    double initialMethaneFraction = first.getNodeComponentMassKg("manifold", "methane")
        / (first.getNodeComponentMassKg("manifold", "methane") + first.getNodeComponentMassKg("manifold", "n-heptane"));

    first.runTransient(0.005, UUID.randomUUID());
    reversed.runTransient(0.005, UUID.randomUUID());

    TwoFluidPipeNetwork.ThermodynamicBalanceReport report = first.getLastThermodynamicBalanceReport();
    for (String componentName : report.getComponentNames()) {
      assertTrue(report.getRelativeComponentResidual(componentName) < 1.0e-8,
          componentName + " residual=" + report.getRelativeComponentResidual(componentName));
      assertEquals(first.getNodeComponentMassKg("manifold", componentName),
          reversed.getNodeComponentMassKg("manifold", componentName), 1.0e-9);
    }
    assertTrue(report.getRelativeEnergyResidual() < 1.0e-7, "energy residual=" + report.getRelativeEnergyResidual());
    assertEquals(first.getNodeTemperatureK("manifold"), reversed.getNodeTemperatureK("manifold"), 1.0e-8);
    double finalMethaneFraction = first.getNodeComponentMassKg("manifold", "methane")
        / (first.getNodeComponentMassKg("manifold", "methane") + first.getNodeComponentMassKg("manifold", "n-heptane"));
    assertTrue(Math.abs(finalMethaneFraction - initialMethaneFraction) > 1.0e-10);
  }

  @Test
  void conservativeNetworkSurvivesSerialization() {
    TwoFluidPipeNetwork original = createConservativeMerge(false);
    original.runTransient(0.005, UUID.randomUUID());

    TwoFluidPipeNetwork restored = SerializationUtils.clone(original);
    double frozenTemperature = restored.getNodeTemperatureK("manifold");
    double frozenMethaneFraction = restored.getNodeComponentMassKg("manifold", "methane")
        / (restored.getNodeComponentMassKg("manifold", "methane")
            + restored.getNodeComponentMassKg("manifold", "n-heptane"));
    restored.runTransient(0.005, UUID.randomUUID());

    assertTrue(restored.getLastThermodynamicBalanceReport().getRelativeEnergyResidual() < 1.0e-7);
    assertEquals(frozenTemperature, restored.getPipe("outlet").getInletStream().getTemperature("K"), 1.0e-8);
    assertEquals(frozenMethaneFraction,
        overallComponentMassFraction(restored.getPipe("outlet").getInletStream().getFluid(), "methane"), 1.0e-10);
    for (String componentName : restored.getLastThermodynamicBalanceReport().getComponentNames()) {
      assertTrue(restored.getLastThermodynamicBalanceReport().getRelativeComponentResidual(componentName) < 1.0e-8);
    }
  }

  @Test
  void conservativeNodeSourceClosesComponentsAndEnergy() {
    UpstreamCompressibleVolume manifold = createVolume(55.0e5, 1000.0);
    manifold.setSourceMassFlowRates(0.05, 0.0, 0.0);
    SystemInterface sourceFluid = createFluid(0.55, 0.45);
    double sourceSpecificEnthalpyJPerKg = 2.5e5;

    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("conservative node source");
    network.addConservativeCompressibleNode("manifold", manifold, createFluid(0.80, 0.20));
    network.setConservativeNodeSourceThermodynamics("manifold", sourceFluid, sourceSpecificEnthalpyJPerKg);
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("outlet", "manifold", "sink", createPipe("outlet", 0.80, 0.20));

    network.runTransient(0.005, UUID.randomUUID());

    TwoFluidPipeNetwork.ThermodynamicBalanceReport report = network.getLastThermodynamicBalanceReport();
    double expectedSourceMassKg = 0.05 * 0.005;
    assertEquals(expectedSourceMassKg * overallComponentMassFraction(sourceFluid, "methane"),
        report.getSourceComponentMassKg("methane"), 1.0e-12);
    assertEquals(expectedSourceMassKg * overallComponentMassFraction(sourceFluid, "n-heptane"),
        report.getSourceComponentMassKg("n-heptane"), 1.0e-12);
    double reportedSourceMassKg = 0.0;
    for (String componentName : report.getComponentNames()) {
      reportedSourceMassKg += report.getSourceComponentMassKg(componentName);
      assertTrue(report.getRelativeComponentResidual(componentName) < 1.0e-8,
          componentName + " source residual=" + report.getRelativeComponentResidual(componentName));
    }
    assertEquals(expectedSourceMassKg, reportedSourceMassKg, 1.0e-12);
    assertEquals(expectedSourceMassKg * sourceSpecificEnthalpyJPerKg, report.getSourceEnergyJ(), 1.0e-12);
    assertTrue(report.getRelativeEnergyResidual() < 1.0e-7,
        "source energy residual=" + report.getRelativeEnergyResidual());
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
  }

  @Test
  void conservativeHydraulicSourceRequiresThermodynamicContract() {
    UpstreamCompressibleVolume manifold = createVolume(55.0e5, 1000.0);
    manifold.setSourceMassFlowRates(0.05, 0.0, 0.0);
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("missing source thermodynamics");
    network.addConservativeCompressibleNode("manifold", manifold, createFluid(0.80, 0.20));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("outlet", "manifold", "sink", createPipe("outlet", 0.80, 0.20));

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> network.runTransient(0.005, UUID.randomUUID()));

    assertTrue(error.getMessage().contains("explicit source fluid and enthalpy"));
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  @Test
  void reverseOutletFlowUsesFixedNodeThermodynamicDonorAndClosesNetworkLedgers() {
    SystemInterface storageFluid = createFluid(0.80, 0.20);
    SystemInterface fallbackFluid = createFluid(0.30, 0.70);
    TwoFluidPipe pipe = createPipe("reverse branch", 0.80, 0.20);

    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("reverse conservative branch");
    network.addConservativeCompressibleNode("storage", createVolume(50.0e5, 1000.0), storageFluid);
    network.addFixedPressureNode("high pressure reservoir", 65.0e5, fallbackFluid);
    network.addPipe("reverse branch", "storage", "high pressure reservoir", pipe);
    network.setBranchOutletPhaseBackflowAllowed("reverse branch", true);

    boolean observedSignedFallback = false;
    for (int step = 0; step < 20; step++) {
      network.runTransient(0.05, UUID.randomUUID());
      double[] outletComponentKg = pipe.getLastComponentConservationReport().getOutletBoundaryMassKg();
      observedSignedFallback |= outletComponentKg[0] < 0.0 || outletComponentKg[1] < 0.0;
    }

    assertTrue(observedSignedFallback, "Pressure reversal must exercise signed outlet component transport");
    TwoFluidPipeNetwork.ThermodynamicBalanceReport report = network.getLastThermodynamicBalanceReport();
    for (String componentName : report.getComponentNames()) {
      assertTrue(report.getRelativeComponentResidual(componentName) < 1.0e-8,
          componentName + " reverse-flow residual=" + report.getRelativeComponentResidual(componentName));
    }
    assertTrue(report.getRelativeEnergyResidual() < 1.0e-7,
        "reverse-flow energy residual=" + report.getRelativeEnergyResidual());
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
    assertEquals(overallComponentMassFraction(fallbackFluid, "methane"),
        overallComponentMassFraction(pipe.getOutletBoundaryFluid(), "methane"), 1.0e-12);
  }

  @Test
  void reverseOutletFlowRequiresDownstreamThermodynamicOwner() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("missing reverse donor");
    network.addConservativeCompressibleNode("storage", createVolume(50.0e5, 1000.0), createFluid(0.95, 0.05));
    network.addFixedPressureNode("pressure only reservoir", 65.0e5);
    network.addPipe("reverse branch", "storage", "pressure only reservoir", createPipe("reverse branch"));

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> network.setBranchOutletPhaseBackflowAllowed("reverse branch", true));

    assertTrue(error.getMessage().contains("explicit downstream thermodynamic boundary fluid"));
  }

  @Test
  void reverseOutletFlowUsesFrozenDownstreamStorageState() {
    TwoFluidPipe pipe = createPipe("storage reversal", 0.85, 0.15);
    SystemInterface highPressureFluid = createFluid(0.25, 0.75);
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("two storage reversal");
    network.addConservativeCompressibleNode("low pressure storage", createVolume(50.0e5, 1000.0),
        createFluid(0.85, 0.15));
    network.addConservativeCompressibleNode("high pressure storage", createVolume(65.0e5, 1000.0), highPressureFluid);
    network.addPipe("storage reversal", "low pressure storage", "high pressure storage", pipe);
    network.setBranchOutletPhaseBackflowAllowed("storage reversal", true);
    assertEquals(overallComponentMassFraction(highPressureFluid, "methane"),
        overallComponentMassFraction(pipe.getOutletBoundaryFluid(), "methane"), 1.0e-12);

    boolean observedSignedFallback = false;
    for (int step = 0; step < 20; step++) {
      network.runTransient(0.05, UUID.randomUUID());
      for (double componentKg : pipe.getLastComponentConservationReport().getOutletBoundaryMassKg()) {
        observedSignedFallback |= componentKg < 0.0;
      }
    }

    assertTrue(observedSignedFallback);
    TwoFluidPipeNetwork.ThermodynamicBalanceReport report = network.getLastThermodynamicBalanceReport();
    for (String componentName : report.getComponentNames()) {
      assertTrue(report.getRelativeComponentResidual(componentName) < 1.0e-8);
    }
    assertTrue(report.getRelativeEnergyResidual() < 1.0e-7);
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
  }

  private static TwoFluidPipeNetwork createConservativeMerge(boolean reverseBranchOrder) {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("conservative merge");
    network.addFixedPressureNode("gas source", 61.0e5);
    network.addFixedPressureNode("liquid source", 60.0e5);
    network.addConservativeCompressibleNode("manifold", createVolume(55.0e5, 1000.0), createFluid(0.80, 0.20));
    network.addFixedPressureNode("sink", 50.0e5);
    if (reverseBranchOrder) {
      network.addPipe("outlet", "manifold", "sink", createPipe("outlet", 0.80, 0.20));
      network.addPipe("liquid inlet", "liquid source", "manifold", createPipe("liquid inlet", 0.30, 0.70));
      network.addPipe("gas inlet", "gas source", "manifold", createPipe("gas inlet", 0.98, 0.02));
    } else {
      network.addPipe("gas inlet", "gas source", "manifold", createPipe("gas inlet", 0.98, 0.02));
      network.addPipe("liquid inlet", "liquid source", "manifold", createPipe("liquid inlet", 0.30, 0.70));
      network.addPipe("outlet", "manifold", "sink", createPipe("outlet", 0.80, 0.20));
    }
    return network;
  }

  private static TwoFluidPipe createPipe(String name) {
    return createPipe(name, 0.95, 0.05);
  }

  private static TwoFluidPipe createPipe(String name, double methane, double heptane) {
    SystemInterface fluid = createFluid(methane, heptane);
    fluid.setPressure(60.0, "bara");
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(2.0, "kg/sec");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name, feed);
    pipe.setLength(50.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
    return pipe;
  }

  private static SystemInterface createFluid(double methane, double heptane) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-heptane", heptane);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static double overallComponentMassFraction(SystemInterface fluid, String componentName) {
    double totalMass = 0.0;
    double componentMass = 0.0;
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      double mass = fluid.getComponent(index).getNumberOfmoles() * fluid.getComponent(index).getMolarMass();
      totalMass += mass;
      if (fluid.getComponent(index).getComponentName().equals(componentName)) {
        componentMass = mass;
      }
    }
    return componentMass / totalMass;
  }

  private static UpstreamCompressibleVolume createVolume(double pressurePa, double volumeM3) {
    double[] density = {45.0, 700.0, 1000.0};
    double[] soundSpeed = {350.0, 1200.0, 1450.0};
    double[] mass = {0.90 * volumeM3 * density[0], 0.10 * volumeM3 * density[1], 0.0};
    return new UpstreamCompressibleVolume(volumeM3, pressurePa, mass, density, soundSpeed);
  }
}
