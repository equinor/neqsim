package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.processmodules.SeparationTrainModule;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the Phase-0 machine-readable dynamic capability contract. */
public class DynamicCapabilityReportTest extends neqsim.NeqSimTest {

  /** Core audited equipment categories remain explicit and conservative. */
  @Test
  public void coreEquipmentHasExpectedCapability() {
    Stream feed = createFeed("feed");
    Stream cold = createFeed("cold");

    assertEquals(DynamicCapability.ALGEBRAIC, feed.getDynamicCapability());
    assertEquals(DynamicCapability.ALGEBRAIC,
        new PassiveDerivedStream("derived feed", createFluid()).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new Separator("separator", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new Tank("tank", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new HeatExchanger("hx", feed, cold).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new Compressor("compressor", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new Pump("pump", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, new ThrottlingValve("valve", feed).getDynamicCapability());
    assertEquals(DynamicCapability.BOUNDARY_DYNAMIC, new SimpleReservoir("reservoir").getDynamicCapability());
  }

  /** Distributed pipe models are distinguished from lumped process equipment. */
  @Test
  public void distributedPipeModelsAreClassified() {
    Stream feed = createFeed("pipe feed");

    assertEquals(DynamicCapability.DYNAMIC_DISTRIBUTED, new OnePhasePipeLine("one phase", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_DISTRIBUTED, new TwoFluidPipe("two fluid", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_DISTRIBUTED, new TransientPipe("drift flux", feed).getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_DISTRIBUTED,
        new WaterHammerPipe("water hammer", feed).getDynamicCapability());
  }

  /** Control and instrumentation elements are reported separately from process-equipment physics. */
  @Test
  public void controlElementsUseControlDynamicCategory() {
    Stream feed = createFeed("control feed");
    PressureTransmitter transmitter = new PressureTransmitter("PT-100", feed);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-100");
    controller.setTransmitter(transmitter);

    assertEquals(DynamicCapability.CONTROL_DYNAMIC, transmitter.getDynamicCapability());
    assertEquals(DynamicCapability.CONTROL_DYNAMIC, controller.getDynamicCapability());
  }

  /** An audited algebraic energy-network step may participate without creating a strict-preflight review item. */
  @Test
  public void algebraicEnergyNetworkSolverPassesCapabilityPreflight() {
    EnergyNetworkSolver energyNetwork = new EnergyNetworkSolver("energy network");

    assertEquals(DynamicCapability.ALGEBRAIC, energyNetwork.getDynamicCapability());

    ProcessSystem process = new ProcessSystem("algebraic energy adapter");
    process.add(energyNetwork);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);
    assertTrue(report.isStrictPreflightReady());
    assertTrue(report.isFullyAudited());
    assertTrue(report.getReviewItems().isEmpty());
    assertTrue(report.getExecutionIssues().isEmpty());
    assertEquals(1, report.getCapabilityCounts().get(DynamicCapability.ALGEBRAIC).intValue());
  }

  /** Parallel transient execution passes strict preflight once worker failures propagate fail-loudly. */
  @Test
  public void parallelExecutionIsNotAStandaloneStrictPreflightBlocker() {
    ProcessSystem parallel = new ProcessSystem("parallel area");
    parallel.add(createFeed("parallel feed"));
    parallel.setParallelTransientEnabled(true);

    DynamicCapabilityReport parallelReport = DynamicCapabilityReport.from(parallel);
    assertEquals("1.2", parallelReport.getSchemaVersion());
    assertTrue(parallelReport.getExecutionIssues().isEmpty());
    assertFalse(parallelReport.hasBlockingIssues());
    assertTrue(parallelReport.isStrictPreflightReady());
  }

  /** Adaptive execution remains an explicit strict-preflight blocker for ProcessSystem and ProcessModel. */
  @Test
  public void adaptiveExecutionIsAnExplicitStrictPreflightBlocker() {
    ProcessSystem adaptive = new ProcessSystem("adaptive area");
    adaptive.add(createFeed("adaptive feed"));
    adaptive.setAdaptiveTimestepEnabled(true);

    DynamicCapabilityReport adaptiveReport = DynamicCapabilityReport.from(adaptive);
    assertEquals(1, adaptiveReport.getExecutionIssues().size());
    assertTrue(adaptiveReport.getExecutionIssues().get(0).contains("runTransientAdaptive"));
    assertTrue(adaptiveReport.getExecutionIssues().get(0).contains("rejected-step rollback"));
    assertTrue(adaptiveReport.hasBlockingIssues());
    assertFalse(adaptiveReport.isStrictPreflightReady());

    ProcessSystem parallel = new ProcessSystem("parallel area");
    parallel.add(createFeed("parallel feed"));
    parallel.setParallelTransientEnabled(true);

    ProcessModel model = new ProcessModel();
    model.add("subsea", parallel);
    model.add("topside", adaptive);

    DynamicCapabilityReport modelReport = DynamicCapabilityReport.from(model);
    assertEquals(1, modelReport.getExecutionIssues().size());
    assertFalse(containsDiagnostic(modelReport.getExecutionIssues(), "subsea"));
    assertTrue(containsDiagnostic(modelReport.getExecutionIssues(), "topside"));
    assertTrue(modelReport.toJson().contains("executionIssues"));
  }

  /** An unaudited custom runTransient override is visible instead of being promoted to a dynamic model. */
  @Test
  public void customTransientOverrideRequiresReview() {
    CustomTransientStream custom = new CustomTransientStream("custom", createFluid());
    assertEquals(DynamicCapability.UNCLASSIFIED_DYNAMIC, custom.getDynamicCapability());

    ProcessSystem process = new ProcessSystem("custom process");
    process.add(custom);
    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

    assertFalse(report.hasBlockingIssues());
    assertEquals(1, report.getReviewItems().size());
    assertFalse(report.isFullyAudited());
    assertTrue(report.getReviewItems().get(0).contains("custom"));
    assertFalse(report.isStrictPreflightReady());
    assertEquals(1, report.getStrictPreflightIssues().size());
    IllegalStateException exception = assertThrows(IllegalStateException.class, report::assertStrictTransientReady);
    assertTrue(exception.getMessage().contains("custom"));
  }

  /** Adsorption and battery state both remain visible after their built-in capability audits. */
  @Test
  public void adsorptionAndBatteryAreFullyAudited() {
    Stream feed = createFeed("adsorption feed");
    AdsorptionBed bed = new AdsorptionBed("adsorption bed", feed);
    BatteryStorage battery = new BatteryStorage("battery", 1000.0);

    assertEquals(DynamicCapability.DYNAMIC_DISTRIBUTED, bed.getDynamicCapability());
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, battery.getDynamicCapability());

    ProcessSystem process = new ProcessSystem("treatment and power");
    process.add(feed);
    process.add(bed);
    process.add(battery);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

    assertEquals(0, report.getCapabilityCounts().get(DynamicCapability.UNCLASSIFIED_DYNAMIC).intValue());
    assertEquals(1, report.getCapabilityCounts().get(DynamicCapability.DYNAMIC_DISTRIBUTED).intValue());
    assertEquals(1, report.getCapabilityCounts().get(DynamicCapability.DYNAMIC_LUMPED).intValue());
    assertTrue(report.getReviewItems().isEmpty());
    assertTrue(report.isFullyAudited());
    assertTrue(report.isStrictPreflightReady());
  }

  /** Runtime activation is a separate audited dimension from state ownership and requested dynamic mode. */
  @Test
  public void heatExchangerActivationRequiresItsActualRuntimePrerequisites() {
    Stream hot = createFeed("hot feed");
    Stream cold = createFeed("cold feed");
    HeatExchanger exchanger = new HeatExchanger("dynamic hx", hot, cold);

    assertEquals(DynamicCapability.DYNAMIC_LUMPED, exchanger.getDynamicCapability());
    assertEquals(DynamicActivationStatus.INACTIVE, DynamicActivationResolver.resolve(exchanger));
    assertTrue(DynamicActivationResolver.diagnostic(exchanger).contains("dynamicModelEnabled is false"));

    exchanger.setCalculateSteadyState(false);
    assertEquals(DynamicActivationStatus.INCOMPLETE_CONFIGURATION, DynamicActivationResolver.resolve(exchanger));
    assertTrue(DynamicActivationResolver.diagnostic(exchanger).contains("calculateSteadyState"));

    exchanger.setDynamicModelEnabled(true);
    assertEquals(DynamicActivationStatus.INCOMPLETE_CONFIGURATION, DynamicActivationResolver.resolve(exchanger));
    assertTrue(DynamicActivationResolver.diagnostic(exchanger).contains("wallMass"));
    assertTrue(DynamicActivationResolver.diagnostic(exchanger).contains("heatTransferArea"));

    exchanger.setWallMass(1000.0);
    exchanger.setHeatTransferArea(50.0);
    assertEquals(DynamicActivationStatus.ACTIVE, DynamicActivationResolver.resolve(exchanger));
    assertTrue(DynamicActivationResolver.diagnostic(exchanger).contains("wall-energy path is active"));

    assertEquals(DynamicActivationStatus.NOT_APPLICABLE, DynamicActivationResolver.resolve(hot));
    assertEquals(DynamicActivationStatus.UNVERIFIED,
        DynamicActivationResolver.resolve(new Separator("unqualified activation", hot)));
  }

  /**
   * Algebraic equipment is valid in transient flowsheets until its unsupported difference-equation mode is requested.
   */
  @Test
  public void algebraicElementForcedIntoDynamicModeIsBlocking() {
    Stream feed = createFeed("feed");
    Separator separator = new Separator("separator", feed);
    separator.setCalculateSteadyState(false);

    ProcessSystem process = new ProcessSystem("process");
    process.add(feed);
    process.add(separator);

    DynamicCapabilityReport validReport = DynamicCapabilityReport.from(process);
    assertFalse(validReport.hasBlockingIssues());
    assertTrue(validReport.getInactiveAuditedDynamicElements().isEmpty());
    assertTrue(validReport.isStrictPreflightReady());

    feed.setCalculateSteadyState(false);
    DynamicCapabilityReport invalidReport = DynamicCapabilityReport.from(process);
    assertTrue(invalidReport.hasBlockingIssues());
    assertEquals(1, invalidReport.getBlockingIssues().size());
    assertTrue(invalidReport.getBlockingIssues().get(0).contains("feed"));
    assertTrue(invalidReport.getBlockingIssues().get(0).contains("ALGEBRAIC"));
    assertFalse(invalidReport.isStrictPreflightReady());
  }

  /** Attached controllers are included once even when also registered standalone. */
  @Test
  public void reportDeduplicatesAttachedAndStandaloneControllerIdentity() {
    Stream feed = createFeed("feed");
    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    PressureTransmitter transmitter = new PressureTransmitter("PT-100", feed);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PC-100");
    controller.setTransmitter(transmitter);
    valve.addController("PC-100", controller);

    ProcessSystem process = new ProcessSystem("controlled process");
    process.add(feed);
    process.add(valve);
    process.add(transmitter);
    process.add(controller);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);
    Map<DynamicCapability, Integer> counts = report.getCapabilityCounts();

    assertEquals(2, counts.get(DynamicCapability.CONTROL_DYNAMIC).intValue());
  }

  /** Multi-area reports preserve area identity so identical unit names remain distinguishable. */
  @Test
  public void processModelReportQualifiesAreas() {
    ProcessSystem upstream = new ProcessSystem("upstream");
    upstream.add(createFeed("feed"));
    ProcessSystem topside = new ProcessSystem("topside");
    topside.add(createFeed("feed"));

    ProcessModel model = new ProcessModel();
    model.add("reservoir and wells", upstream);
    model.add("topside", topside);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(model);

    assertEquals(2, report.getEntries().size());
    assertEquals("reservoir and wells::feed", report.getEntries().get(0).getQualifiedName());
    assertEquals("topside::feed", report.getEntries().get(1).getQualifiedName());
    assertEquals(2, report.getCapabilityCounts().get(DynamicCapability.ALGEBRAIC).intValue());
    assertTrue(report.toJson().contains("reservoir and wells"));
    assertTrue(report.toJson().contains("ALGEBRAIC"));
  }

  /** Initialized process-module contents are recursively inventoried with stable diagnostic paths. */
  @Test
  public void reportRecursesThroughInitializedProcessModules() {
    Stream feed = createFeed("module feed");
    SeparationTrainModule module = new SeparationTrainModule("separation train");
    module.addInputStream("feed stream", feed);

    ProcessSystem process = new ProcessSystem("module process");
    process.add(module);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

    assertTrue(report.getEntries().size() > 10);
    assertTrue(hasQualifiedEntry(report, "separation train::Inlet separator"));
    assertTrue(hasQualifiedEntry(report, "separation train::HP gas scrubber"));
    assertTrue(report.getCapabilityCounts().get(DynamicCapability.DYNAMIC_LUMPED).intValue() > 0);
    assertEquals(0, report.getCapabilityCounts().get(DynamicCapability.UNCLASSIFIED_DYNAMIC).intValue());
    assertTrue(report.isFullyAudited());
    assertTrue(report.isStrictPreflightReady());
  }

  /** Nested module paths remain area-qualified in multi-area ProcessModel reports. */
  @Test
  public void nestedModuleEntriesRetainProcessModelAreaIdentity() {
    Stream feed = createFeed("module feed");
    SeparationTrainModule module = new SeparationTrainModule("separation train");
    module.addInputStream("feed stream", feed);

    ProcessSystem process = new ProcessSystem("topside");
    process.add(module);
    ProcessModel model = new ProcessModel();
    model.add("topside", process);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(model);

    assertTrue(hasQualifiedEntry(report, "topside::separation train::Inlet separator"));
    assertTrue(report.toJson().contains("containerPath"));
  }

  /** Audited dynamic equipment left in steady-state mode is reported as an engineering review aid, not an error. */
  @Test
  public void inactiveAuditedDynamicStateIsVisibleButNotBlocking() {
    Stream feed = createFeed("feed");
    Separator separator = new Separator("separator", feed);

    ProcessSystem process = new ProcessSystem("process");
    process.add(feed);
    process.add(separator);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

    assertFalse(report.hasBlockingIssues());
    assertEquals(1, report.getInactiveAuditedDynamicElements().size());
    assertEquals("separator", report.getInactiveAuditedDynamicElements().get(0));
  }

  private static boolean hasQualifiedEntry(DynamicCapabilityReport report, String qualifiedName) {
    for (DynamicCapabilityReport.Entry entry : report.getEntries()) {
      if (qualifiedName.equals(entry.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsDiagnostic(java.util.List<String> diagnostics, String text) {
    for (String diagnostic : diagnostics) {
      if (diagnostic.contains(text)) {
        return true;
      }
    }
    return false;
  }

  private static Stream createFeed(String name) {
    Stream stream = new Stream(name, createFluid());
    stream.setFlowRate(1000.0, "kg/hr");
    stream.setPressure(50.0, "bara");
    stream.setTemperature(25.0, "C");
    return stream;
  }

  private static SystemSrkEos createFluid() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static final class CustomTransientStream extends Stream {
    private static final long serialVersionUID = 1000L;

    private CustomTransientStream(String name, SystemSrkEos fluid) {
      super(name, fluid);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      run(id);
      increaseTime(dt);
      setCalculationIdentifier(id);
    }
  }

  private static final class PassiveDerivedStream extends Stream {
    private static final long serialVersionUID = 1000L;

    private PassiveDerivedStream(String name, SystemSrkEos fluid) {
      super(name, fluid);
    }
  }
}
