package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.process.equipment.pipeline.TwoFluidConservativeEnergyReport;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
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
  void mixedBranchSubcyclesRetainOneOuterSynchronizationBoundary() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("mixed subcycle network");
    network.addFixedPressureNode("source", 60.0e5);
    network.addCompressibleNode("manifold", createVolume(55.0e5, 1000.0));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("upstream", "source", "manifold", createPipe("subcycled upstream"));
    network.addPipe("downstream", "manifold", "sink", createPipe("subcycled downstream"));
    network.setBranchSubcycles("upstream", 4);
    network.setBranchSubcycles("downstream", 2);

    network.runTransient(0.01, UUID.randomUUID());

    assertEquals(4, network.getBranchSubcycles("upstream"));
    assertEquals(2, network.getBranchSubcycles("downstream"));
    assertEquals(0.01, network.getSimulationTimeSeconds(), 0.0);
    assertEquals(0.01, network.getPipe("upstream").getSimulationTime(), 1.0e-15);
    assertEquals(0.01, network.getPipe("downstream").getSimulationTime(), 1.0e-15);
    assertEquals(0.01, network.getPipe("upstream").getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-15);
    assertEquals(0.01, network.getPipe("downstream").getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-15);
    assertTrue(network.getPipe("upstream").getLastMassBalanceReport().getAcceptedSubsteps() >= 4);
    assertTrue(network.getPipe("downstream").getLastMassBalanceReport().getAcceptedSubsteps() >= 2);
    for (Phase phase : Phase.values()) {
      assertTrue(network.getLastBalanceReport().getRelativeResidual(phase) < 1.0e-8);
    }

    TwoFluidPipeNetwork restored = SerializationUtils.clone(network);
    assertEquals(4, restored.getBranchSubcycles("upstream"));
    assertEquals(2, restored.getBranchSubcycles("downstream"));
    assertThrows(IllegalArgumentException.class, () -> restored.setBranchSubcycles("upstream", 0));
    assertThrows(IllegalArgumentException.class, () -> restored.getBranchSubcycles("missing"));
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
  void laterBranchPublicationFailureRollsBackEarlierBranchAndAllNodeState() {
    TwoFluidPipe upstream = createPipe("transaction upstream");
    TwoFluidPipe downstream = createPipe("transaction downstream");
    RejectingPublicationStream rejectingOutlet = new RejectingPublicationStream(
        downstream.getOutletStream().getFluid());
    downstream.setOutletStream(rejectingOutlet);
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("transaction rollback");
    network.addFixedPressureNode("source", 60.0e5);
    network.addCompressibleNode("manifold", createVolume(55.0e5, 1000.0));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("upstream", "source", "manifold", upstream);
    network.addPipe("downstream", "manifold", "sink", downstream);
    network.setBranchSubcycles("upstream", 3);
    network.setBranchSubcycles("downstream", 2);
    network.initialize(UUID.randomUUID());
    TwoFluidSection[] upstreamBefore = upstream.getSectionSnapshots();
    TwoFluidSection[] downstreamBefore = downstream.getSectionSnapshots();
    double pressureBefore = network.getNodePressurePa("manifold");
    rejectingOutlet.rejectNextPublication = true;

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> network.runTransient(0.01, UUID.randomUUID()));

    assertEquals("injected network publication failure", failure.getMessage());
    assertTrue(rejectingOutlet.rejected);
    assertPipeSectionsEqual(upstreamBefore, upstream.getSectionSnapshots());
    assertPipeSectionsEqual(downstreamBefore, downstream.getSectionSnapshots());
    assertEquals(pressureBefore, network.getNodePressurePa("manifold"), 0.0);
    assertEquals(0.0, upstream.getSimulationTime(), 0.0);
    assertEquals(0.0, downstream.getSimulationTime(), 0.0);
    assertNull(upstream.getLastMassBalanceReport());
    assertNull(downstream.getLastMassBalanceReport());
    assertNull(network.getLastBalanceReport());
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  @Test
  void nodeRecoveryFailureRejectsEveryPreparedBranchBeforePublication() {
    TwoFluidPipe branch = createPipe("depleting branch");
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("node recovery rollback");
    network.addCompressibleNode("tiny storage", createVolume(55.0e5, 1.0e-6));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("branch", "tiny storage", "sink", branch);
    network.initialize(UUID.randomUUID());
    TwoFluidSection[] sectionsBefore = branch.getSectionSnapshots();
    double pressureBefore = network.getNodePressurePa("tiny storage");

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> network.runTransient(0.01, UUID.randomUUID()));

    assertTrue(failure.getMessage().contains("depleted by pipe withdrawal"));
    assertPipeSectionsEqual(sectionsBefore, branch.getSectionSnapshots());
    assertEquals(pressureBefore, network.getNodePressurePa("tiny storage"), 0.0);
    assertEquals(0.0, branch.getSimulationTime(), 0.0);
    assertNull(branch.getLastMassBalanceReport());
    assertNull(network.getLastBalanceReport());
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  private static void assertPipeSectionsEqual(TwoFluidSection[] expected, TwoFluidSection[] actual) {
    assertEquals(expected.length, actual.length);
    for (int index = 0; index < expected.length; index++) {
      org.junit.jupiter.api.Assertions.assertArrayEquals(expected[index].getStateVector(),
          actual[index].getStateVector(), 0.0);
    }
  }

  @Test
  void conservativeMergeClosesComponentsAndEnergyIndependentOfBranchOrder() {
    TwoFluidPipeNetwork first = createConservativeMerge(false);
    TwoFluidPipeNetwork reversed = createConservativeMerge(true);
    for (TwoFluidPipeNetwork network : new TwoFluidPipeNetwork[] {first, reversed}) {
      network.setBranchSubcycles("gas inlet", 3);
      network.setBranchSubcycles("liquid inlet", 2);
      network.setBranchSubcycles("outlet", 4);
    }
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
    assertEquals(0.005, first.getPipe("gas inlet").getLastComponentConservationReport().getElapsedTimeSeconds(),
        1.0e-15);
    assertEquals(0.005, first.getPipe("outlet").getLastConservativeEnergyReport().getElapsedTimeSeconds(), 1.0e-15);
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
      TwoFluidPipeNetwork.ThermodynamicBalanceReport report = network.getLastThermodynamicBalanceReport();
      assertEquals(0.0, report.getExternalInletEnergyJ(), 0.0);
      assertEquals(0.0, report.getExternalOutletEnergyJ(), 0.0);
      assertEquals(0.0, report.getSourceEnergyJ(), 0.0);
      assertEquals(0.0, report.getGravitationalWorkJ(), 0.0);
      assertEquals(0.0, report.getWallHeatJ(), 0.0);
      // This closed, adiabatic network has zero net transfer. A relative residual alone divides storage
      // subtraction roundoff by itself. Bound every interval by four ulps of the three-inventory sum instead.
      double storedEnergyScale = Math.max(Math.abs(report.getInitialEnergyJ()), Math.abs(report.getFinalEnergyJ()));
      assertEquals(0.0, report.getEnergyResidualJ(), 4.0 * Math.ulp(storedEnergyScale),
          "Closed-network energy must be conserved to storage-summation roundoff at step " + step);
      for (String componentName : report.getComponentNames()) {
        assertTrue(report.getRelativeComponentResidual(componentName) < 1.0e-8);
      }
      assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
    }

    assertTrue(observedSignedFallback);
  }

  @Test
  void acceptedBranchLedgersShareOneMassAndTimeBasis() {
    TwoFluidPipeNetwork merge = createConservativeMerge(false);
    merge.runTransient(0.005, UUID.randomUUID());
    assertBranchLedgerConsistency(merge, "gas inlet");
    assertBranchLedgerConsistency(merge, "liquid inlet");
    assertBranchLedgerConsistency(merge, "outlet");

    TwoFluidPipeNetwork reverse = new TwoFluidPipeNetwork("reverse ledger basis");
    reverse.addConservativeCompressibleNode("storage", createVolume(50.0e5, 1000.0), createFluid(0.80, 0.20));
    reverse.addFixedPressureNode("reservoir", 65.0e5, createFluid(0.30, 0.70));
    reverse.addPipe("reverse branch", "storage", "reservoir", createPipe("reverse branch", 0.80, 0.20));
    reverse.setBranchOutletPhaseBackflowAllowed("reverse branch", true);
    for (int step = 0; step < 20; step++) {
      reverse.runTransient(0.05, UUID.randomUUID());
      assertBranchLedgerConsistency(reverse, "reverse branch");
    }
  }

  @Test
  void algebraicJunctionSolvesCommonPressureAndMixedDonorConservatively() {
    TwoFluidPipeNetwork network = createAlgebraicMerge(false);

    network.runTransient(0.005, UUID.randomUUID());

    TwoFluidPipeNetwork.AlgebraicJunctionReport report = network.getLastAlgebraicJunctionReport();
    assertNotNull(report);
    assertEquals("zero-volume manifold", report.getNodeName());
    assertTrue(report.getPressurePa() > 50.0e5);
    assertTrue(report.getPressurePa() < 65.0e5);
    assertTrue(report.getMaximumRelativePhaseResidual() < 1.0e-8);
    assertTrue(report.getMaximumRelativeComponentResidual() < 1.0e-8);
    assertTrue(report.getRelativeEnergyResidual() < 1.0e-8);
    assertTrue(Math.abs(report.getPhaseResidualKg(Phase.TOTAL)) < 1.0e-9);
    assertTrue(Math.abs(report.getInterphaseTransferKg(Phase.GAS)) > 1.0e-9);
    assertTrue(Math.abs(report.getInterphaseTransferKg(Phase.TOTAL)) < 1.0e-9);
    for (String componentName : report.getComponentNames()) {
      assertTrue(Math.abs(report.getComponentResidualKg(componentName)) < 1.0e-9);
    }
    assertTrue(Math.abs(report.getEnergyResidualJ()) < 1.0e-3);
    double methaneFraction = network.getAlgebraicNodeComponentMassFraction("zero-volume manifold", "methane");
    assertTrue(Double.isFinite(methaneFraction));
    assertTrue(methaneFraction > 0.0 && methaneFraction < 1.0);
    assertEquals(report.getPressurePa(), network.getNodePressurePa("zero-volume manifold"), 0.0);
    assertEquals(report.getInterphaseTransferKg(Phase.GAS),
        network.getLastBalanceReport().getInternalPhaseTransferKg(Phase.GAS), 0.0);
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
    assertTrue(network.getLastThermodynamicBalanceReport().getRelativeEnergyResidual() < 1.0e-7);
  }

  @Test
  void algebraicJunctionIsBranchOrderIndependentAndSerializable() {
    TwoFluidPipeNetwork ordered = createAlgebraicMerge(false);
    TwoFluidPipeNetwork reversed = createAlgebraicMerge(true);

    ordered.runTransient(0.005, UUID.randomUUID());
    reversed.runTransient(0.005, UUID.randomUUID());

    assertEquals(ordered.getNodePressurePa("zero-volume manifold"), reversed.getNodePressurePa("zero-volume manifold"),
        1.0e-2);
    assertEquals(ordered.getNodeTemperatureK("zero-volume manifold"),
        reversed.getNodeTemperatureK("zero-volume manifold"), 1.0e-8);
    assertEquals(ordered.getAlgebraicNodeComponentMassFraction("zero-volume manifold", "methane"),
        reversed.getAlgebraicNodeComponentMassFraction("zero-volume manifold", "methane"), 1.0e-10);

    TwoFluidPipeNetwork restored = SerializationUtils.clone(ordered);
    restored.runTransient(0.005, UUID.randomUUID());
    assertNotNull(restored.getLastAlgebraicJunctionReport());
    assertTrue(restored.getLastAlgebraicJunctionReport().getMaximumRelativePhaseResidual() < 1.0e-8);
    assertTrue(restored.getLastAlgebraicJunctionReport().getMaximumRelativeComponentResidual() < 1.0e-8);
  }

  @Test
  void coupledAlgebraicJunctionsSolveGloballyIndependentOfBranchOrderAndSerialize() {
    TwoFluidPipeNetwork ordered = createCoupledAlgebraicDaisy(false);
    TwoFluidPipeNetwork reversed = createCoupledAlgebraicDaisy(true);

    ordered.runTransient(0.001, UUID.randomUUID());
    reversed.runTransient(0.001, UUID.randomUUID());

    assertEquals(2, ordered.getLastAlgebraicJunctionNames().length);
    assertEquals("upstream manifold", ordered.getLastAlgebraicJunctionNames()[0]);
    assertEquals("downstream PLEM", ordered.getLastAlgebraicJunctionNames()[1]);
    double upstreamPressurePa = ordered.getNodePressurePa("upstream manifold");
    double downstreamPressurePa = ordered.getNodePressurePa("downstream PLEM");
    assertTrue(upstreamPressurePa > downstreamPressurePa);
    assertTrue(upstreamPressurePa < 70.0e5);
    assertTrue(downstreamPressurePa > 50.0e5);
    for (String nodeName : ordered.getLastAlgebraicJunctionNames()) {
      TwoFluidPipeNetwork.AlgebraicJunctionReport report = ordered.getLastAlgebraicJunctionReport(nodeName);
      assertTrue(report.getMaximumRelativePhaseResidual() < 1.0e-8);
      for (String componentName : report.getComponentNames()) {
        assertTrue(Math.abs(report.getComponentResidualKg(componentName)) < 1.0e-9);
      }
      assertTrue(report.getRelativeEnergyResidual() < 1.0e-8 || Math.abs(report.getEnergyResidualJ()) < 1.0e-4);
      assertEquals(ordered.getNodePressurePa(nodeName), reversed.getNodePressurePa(nodeName), 1.0e-2);
      assertEquals(ordered.getNodeTemperatureK(nodeName), reversed.getNodeTemperatureK(nodeName), 1.0e-8);
    }
    assertTrue(ordered.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
    assertTrue(ordered.getLastThermodynamicBalanceReport().getRelativeEnergyResidual() < 1.0e-7);

    TwoFluidPipeNetwork restored = SerializationUtils.clone(ordered);
    restored.runTransient(0.001, UUID.randomUUID());
    assertEquals(2, restored.getLastAlgebraicJunctionNames().length);
    TwoFluidPipeNetwork.AlgebraicJunctionReport restoredReport = restored
        .getLastAlgebraicJunctionReport("upstream manifold");
    for (String componentName : restoredReport.getComponentNames()) {
      assertTrue(Math.abs(restoredReport.getComponentResidualKg(componentName)) < 1.0e-9);
    }
  }

  private static void assertBranchLedgerConsistency(TwoFluidPipeNetwork network, String branchName) {
    TwoFluidPipe pipe = network.getPipe(branchName);
    TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
    TwoFluidComponentConservationReport component = pipe.getLastComponentConservationReport();
    TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();

    assertEquals(mass.getElapsedTimeSeconds(), component.getElapsedTimeSeconds(), 0.0);
    assertEquals(mass.getElapsedTimeSeconds(), energy.getElapsedTimeSeconds(), 0.0);
    assertEquals(mass.getAcceptedSubsteps(), component.getAcceptedSubsteps());
    assertSameMassBasis(branchName + " initial", mass.getInitialMassKg(Phase.TOTAL),
        sum(component.getInitialInventoryKg()));
    assertSameMassBasis(branchName + " final", mass.getFinalMassKg(Phase.TOTAL), sum(component.getFinalInventoryKg()));
    assertSameMassBasis(branchName + " inlet", mass.getInletMassKg(Phase.TOTAL),
        sum(component.getInletBoundaryMassKg()));
    assertSameMassBasis(branchName + " outlet", mass.getOutletMassKg(Phase.TOTAL),
        sum(component.getOutletBoundaryMassKg()));
  }

  private static void assertSameMassBasis(String label, double phaseMassKg, double componentMassKg) {
    double scale = Math.max(Math.max(Math.abs(phaseMassKg), Math.abs(componentMassKg)), 1.0e-12);
    assertEquals(phaseMassKg, componentMassKg, 1.0e-10 * scale, label);
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
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

  private static TwoFluidPipeNetwork createAlgebraicMerge(boolean reverseBranchOrder) {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("algebraic merge");
    network.addFixedPressureNode("rich source", 65.0e5, createFluid(0.99, 0.01));
    network.addFixedPressureNode("lean source", 60.0e5, createFluid(0.90, 0.10));
    network.addAlgebraicJunction("zero-volume manifold", 55.0e5, createFluid(0.95, 0.05));
    network.addFixedPressureNode("sink", 50.0e5, createFluid(0.95, 0.05));
    if (reverseBranchOrder) {
      network.addPipe("outlet", "zero-volume manifold", "sink", createPipe("outlet", 0.95, 0.05));
      network.addPipe("lean inlet", "lean source", "zero-volume manifold", createPipe("lean inlet", 0.90, 0.10));
      network.addPipe("rich inlet", "rich source", "zero-volume manifold", createPipe("rich inlet", 0.99, 0.01));
    } else {
      network.addPipe("rich inlet", "rich source", "zero-volume manifold", createPipe("rich inlet", 0.99, 0.01));
      network.addPipe("lean inlet", "lean source", "zero-volume manifold", createPipe("lean inlet", 0.90, 0.10));
      network.addPipe("outlet", "zero-volume manifold", "sink", createPipe("outlet", 0.95, 0.05));
    }
    return network;
  }

  private static TwoFluidPipeNetwork createCoupledAlgebraicDaisy(boolean reverseBranchOrder) {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("coupled algebraic daisy");
    network.addFixedPressureNode("source", 70.0e5, createFluid(0.99, 0.01));
    network.addAlgebraicJunction("upstream manifold", 62.0e5, createFluid(0.97, 0.03));
    network.addAlgebraicJunction("downstream PLEM", 58.0e5, createFluid(0.95, 0.05));
    network.addFixedPressureNode("sink", 50.0e5, createFluid(0.95, 0.05));
    if (reverseBranchOrder) {
      network.addPipe("export", "downstream PLEM", "sink", createPipe("export", 0.95, 0.05));
      network.addPipe("daisy jumper", "upstream manifold", "downstream PLEM", createPipe("daisy jumper", 0.96, 0.04));
      network.addPipe("production flowline", "source", "upstream manifold",
          createPipe("production flowline", 0.99, 0.01));
    } else {
      network.addPipe("production flowline", "source", "upstream manifold",
          createPipe("production flowline", 0.99, 0.01));
      network.addPipe("daisy jumper", "upstream manifold", "downstream PLEM", createPipe("daisy jumper", 0.96, 0.04));
      network.addPipe("export", "downstream PLEM", "sink", createPipe("export", 0.95, 0.05));
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

  /** Fail one live outlet publication after the detached branch calculation has completed. */
  private static final class RejectingPublicationStream extends Stream {
    private static final long serialVersionUID = 1L;
    private transient boolean rejectNextPublication;
    private transient boolean rejected;

    private RejectingPublicationStream(SystemInterface fluid) {
      super("rejecting network outlet", fluid);
    }

    @Override
    public void setFluid(SystemInterface fluid) {
      super.setFluid(fluid);
      if (rejectNextPublication) {
        rejectNextPublication = false;
        rejected = true;
        throw new IllegalStateException("injected network publication failure");
      }
    }
  }
}
