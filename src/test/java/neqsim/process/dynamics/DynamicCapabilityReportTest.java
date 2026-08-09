package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
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
import neqsim.thermo.system.SystemSrkEos;

/** Tests the Phase-0 machine-readable dynamic capability contract. */
public class DynamicCapabilityReportTest extends neqsim.NeqSimTest {

  /** Core audited equipment categories remain explicit and conservative. */
  @Test
  public void coreEquipmentHasExpectedCapability() {
    Stream feed = createFeed("feed");
    Stream cold = createFeed("cold");

    assertEquals(DynamicCapability.ALGEBRAIC, feed.getDynamicCapability());
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
    assertTrue(report.getReviewItems().get(0).contains("custom"));
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

    feed.setCalculateSteadyState(false);
    DynamicCapabilityReport invalidReport = DynamicCapabilityReport.from(process);
    assertTrue(invalidReport.hasBlockingIssues());
    assertEquals(1, invalidReport.getBlockingIssues().size());
    assertTrue(invalidReport.getBlockingIssues().get(0).contains("feed"));
    assertTrue(invalidReport.getBlockingIssues().get(0).contains("ALGEBRAIC"));
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
}
