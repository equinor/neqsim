package neqsim.process.fielddevelopment.tieback.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for host tie-in capacity planning.
 *
 * @author ESOL
 * @version 1.0
 */
class TieInCapacityPlannerTest {

  /**
   * Verifies base-first allocation preserves base host production and holds back satellite gas.
   */
  @Test
  void baseFirstPolicyHoldsBackSatelliteAgainstNameplateGasCapacity() {
    HostFacility host = HostFacility.builder("Host A").gasCapacity(5.0).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 3.0, 0.0, 0.0, 0.0);

    TieInCapacityResult result = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setAllocationPolicy(CapacityAllocationPolicy.BASE_FIRST).run();

    TieInPeriodResult period = result.getPeriodResults().get(0);
    assertEquals(1.0, period.getAcceptedSatellite().getGasRateMSm3d(), 1.0e-9);
    assertEquals(2.0, period.getHeldBackSatellite().getGasRateMSm3d(), 1.0e-9);
    assertEquals("gas capacity", period.getNameplateBottleneck());
    assertTrue(result.hasHoldback());
  }

  /**
   * Verifies pro-rata allocation shares constrained capacity between base and satellite streams.
   */
  @Test
  void proRataPolicySharesConstrainedGasCapacity() {
    HostFacility host = HostFacility.builder("Host B").gasCapacity(6.0).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);

    TieInCapacityResult result = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setAllocationPolicy(CapacityAllocationPolicy.PRO_RATA).run();

    TieInPeriodResult period = result.getPeriodResults().get(0);
    assertEquals(3.0, period.getAcceptedBase().getGasRateMSm3d(), 1.0e-9);
    assertEquals(3.0, period.getAcceptedSatellite().getGasRateMSm3d(), 1.0e-9);
    assertEquals(0.75, period.getSatelliteAllocationScale(), 1.0e-9);
  }

  /**
   * Verifies deferred holdback is carried into later periods and re-tested against ullage.
   */
  @Test
  void deferPolicyCarriesHoldbackIntoLaterYears() {
    HostFacility host = HostFacility.builder("Host C").gasCapacity(5.0).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 4.0, 0.0, 0.0, 0.0)
        .addPeriod(2029, 4.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 3.0, 0.0, 0.0, 0.0)
        .addPeriod(2029, 0.0, 0.0, 0.0, 0.0);

    TieInCapacityResult result = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setHoldbackPolicy(HoldbackPolicy.DEFER_TO_LATER_YEARS).run();

    TieInPeriodResult first = result.getPeriodResults().get(0);
    TieInPeriodResult second = result.getPeriodResults().get(1);
    assertEquals(2.0, first.getDeferredToNextPeriod().getGasRateMSm3d(), 1.0e-9);
    assertEquals(2.0, second.getDeferredIntoPeriod().getGasRateMSm3d(), 1.0e-9);
    assertEquals(1.0, second.getAcceptedSatellite().getGasRateMSm3d(), 1.0e-9);
    assertEquals(1.0, second.getDeferredToNextPeriod().getGasRateMSm3d(), 1.0e-9);
  }

  /**
   * Verifies process-model capacity checks inject the accepted load into the host stream.
   */
  @Test
  void processModelInjectionFindsCapacityBottleneckAndRestoresFeedRate() {
    final Stream hostFeed = createHostFeed();
    hostFeed.addCapacityConstraint(new CapacityConstraint("hostFeedFlow", "kg/hr", ConstraintType.HARD)
        .setDesignValue(2500.0).setValueSupplier(() -> hostFeed.getFlowRate("kg/hr")));
    ProcessSystem process = new ProcessSystem("host process");
    process.add(hostFeed);
    process.run();

    HostFacility host = HostFacility.builder("Host D").gasCapacity(10.0).processSystem(process).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 1.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    HostTieInPoint tieInPoint = new HostTieInPoint("Host Feed", "kg/hr").setGasToProcessRateFactor(1000.0);

    TieInCapacityResult result = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setTieInPoint(tieInPoint).setProcessUtilizationLimit(1.0).run();

    TieInPeriodResult period = result.getPeriodResults().get(0);
    assertTrue(period.isProcessModelUsed());
    assertEquals("Host Feed", period.getProcessBottleneck());
    assertTrue(period.getAcceptedSatellite().getGasRateMSm3d() > 1.49);
    assertTrue(period.getAcceptedSatellite().getGasRateMSm3d() < 1.51);
    assertTrue(period.getHeldBackSatellite().getGasRateMSm3d() > 2.49);
    assertEquals(1000.0, hostFeed.getFlowRate("kg/hr"), 1.0e-6);
    assertFalse(result.getDebottleneckDecisions().isEmpty());
  }

  /**
   * Verifies a multi-area host model applies the satellite at an area-qualified stream, finds the downstream
   * bottleneck, and restores the complete model to its original operating point.
   */
  @Test
  void multiAreaProcessModelFindsDownstreamBottleneckAndRestoresFeedRate() {
    final Stream hostFeed = createHostFeed();
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(hostFeed);

    final Separator hostSeparator = new Separator("Host Separator", hostFeed);
    hostSeparator.addCapacityConstraint(new CapacityConstraint("separatorFeedFlow", "kg/hr", ConstraintType.HARD)
        .setDesignValue(2500.0).setValueSupplier(() -> hostFeed.getFlowRate("kg/hr")));
    ProcessSystem processing = new ProcessSystem("processing");
    processing.add(hostSeparator);

    ProcessModel model = new ProcessModel();
    model.add("gathering", gathering);
    model.add("processing", processing);
    model.run();

    HostFacility host = HostFacility.builder("Host E").gasCapacity(10.0).processModel(model).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 1.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    HostTieInPoint tieInPoint = new HostTieInPoint("gathering::Host Feed", "kg/hr").setGasToProcessRateFactor(1000.0);

    TieInCapacityResult result = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setTieInPoint(tieInPoint).setProcessUtilizationLimit(1.0).run();

    TieInPeriodResult period = result.getPeriodResults().get(0);
    assertTrue(period.isProcessModelUsed());
    assertEquals("processing::Host Separator", period.getProcessBottleneck());
    assertTrue(period.getProcessUtilizationSummary().containsKey("processing::Host Separator"));
    assertTrue(period.getAcceptedSatellite().getGasRateMSm3d() > 1.49);
    assertTrue(period.getAcceptedSatellite().getGasRateMSm3d() < 1.51);
    assertEquals(1000.0, hostFeed.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(1000.0, hostSeparator.getGasOutStream().getFlowRate("kg/hr"), 1.0e-6);

    HostFacility.HostCapacityReport restoredReport = host.assessCapacity(0.0, 0.0, 0.0, 0.0);
    assertTrue(restoredReport.isProcessModelUsed());
    assertEquals("processing::Host Separator", restoredReport.getPrimaryBottleneckName());
    assertEquals(0.4, restoredReport.getPrimaryBottleneckUtilization(), 1.0e-6);
  }

  /**
   * Verifies ProcessModel stream addressing is deterministic and rejects ambiguous bare names.
   */
  @Test
  void processModelStreamResolutionRequiresQualificationForDuplicateNames() {
    Stream firstFeed = createHostFeed();
    Stream secondFeed = createHostFeed();
    ProcessSystem firstArea = new ProcessSystem("first");
    firstArea.add(firstFeed);
    ProcessSystem secondArea = new ProcessSystem("second");
    secondArea.add(secondFeed);
    ProcessModel model = new ProcessModel();
    model.add("first", firstArea);
    model.add("second", secondArea);

    assertSame(firstFeed, model.resolveStreamReference("first::Host Feed"));
    assertNull(model.resolveStreamReference("missing::Host Feed"));
    assertNull(model.resolveStreamReference("first::Missing Feed"));
    assertThrows(IllegalArgumentException.class, () -> model.resolveStreamReference("Host Feed"));
  }

  /**
   * Creates a simple host feed stream for process-capacity tests.
   *
   * @return configured host feed stream
   */
  private Stream createHostFeed() {
    SystemInterface gas = new SystemSrkEos(288.15, 60.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream hostFeed = new Stream("Host Feed", gas);
    hostFeed.setFlowRate(1000.0, "kg/hr");
    return hostFeed;
  }
}
