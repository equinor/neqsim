package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the runtime-activation dimension of the Phase-0 dynamic capability report. */
public class DynamicActivationReportTest extends neqsim.NeqSimTest {

  /** HeatExchanger activation follows the runtime branch used by runTransient rather than a generic mode flag alone. */
  @Test
  public void heatExchangerActivationIsReportedFromActualPrerequisites() {
    Stream hot = createFeed("hot feed", 330.0);
    Stream cold = createFeed("cold feed", 290.0);
    HeatExchanger exchanger = new HeatExchanger("HX-100", hot, cold);
    ProcessSystem process = new ProcessSystem("topside");
    process.add(exchanger);

    DynamicCapabilityReport inactive = DynamicCapabilityReport.from(process);
    assertEquals("1.2", inactive.getSchemaVersion());
    assertEquals(DynamicActivationStatus.INACTIVE, inactive.getEntries().get(0).getActivationStatus());
    assertFalse(inactive.hasBlockingIssues());
    assertEquals(1, inactive.getInactiveAuditedDynamicElements().size());

    exchanger.setCalculateSteadyState(false);
    DynamicCapabilityReport requestedButDisabled = DynamicCapabilityReport.from(process);
    assertEquals(DynamicActivationStatus.INCOMPLETE_CONFIGURATION,
        requestedButDisabled.getEntries().get(0).getActivationStatus());
    assertTrue(requestedButDisabled.hasBlockingIssues());
    assertFalse(requestedButDisabled.isStrictPreflightReady());
    assertTrue(requestedButDisabled.getBlockingIssues().get(0).contains("dynamicModelEnabled is false"));

    exchanger.setDynamicModelEnabled(true);
    DynamicCapabilityReport missingPhysicalParameters = DynamicCapabilityReport.from(process);
    assertEquals(DynamicActivationStatus.INCOMPLETE_CONFIGURATION,
        missingPhysicalParameters.getEntries().get(0).getActivationStatus());
    assertTrue(missingPhysicalParameters.getBlockingIssues().get(0).contains("wallMass"));
    assertTrue(missingPhysicalParameters.getBlockingIssues().get(0).contains("heatTransferArea"));

    exchanger.setWallMass(1200.0);
    exchanger.setHeatTransferArea(80.0);
    DynamicCapabilityReport active = DynamicCapabilityReport.from(process);
    assertEquals(DynamicActivationStatus.ACTIVE, active.getEntries().get(0).getActivationStatus());
    assertFalse(active.hasBlockingIssues());
    assertTrue(active.getInactiveAuditedDynamicElements().isEmpty());
    assertEquals(1, active.getActivationCounts().get(DynamicActivationStatus.ACTIVE).intValue());
    assertTrue(active.toJson().contains("\"activationStatus\": \"ACTIVE\""));
  }

  /** Battery state is always transient, while zero storage capacity is a blocking configuration error. */
  @Test
  public void batteryActivationDoesNotDependOnGenericSteadyStateFlag() {
    BatteryStorage battery = new BatteryStorage("battery", 0.0);
    ProcessSystem process = new ProcessSystem("electrical system");
    process.add(battery);

    DynamicCapabilityReport incomplete = DynamicCapabilityReport.from(process);
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, incomplete.getEntries().get(0).getCapability());
    assertEquals(DynamicActivationStatus.INCOMPLETE_CONFIGURATION,
        incomplete.getEntries().get(0).getActivationStatus());
    assertTrue(incomplete.hasBlockingIssues());
    assertTrue(incomplete.getBlockingIssues().get(0).contains("storage capacity"));

    battery.setCapacity(1000.0);
    DynamicCapabilityReport active = DynamicCapabilityReport.from(process);
    assertEquals(DynamicActivationStatus.ACTIVE, active.getEntries().get(0).getActivationStatus());
    assertFalse(active.hasBlockingIssues());
    assertTrue(active.getEntries().get(0).getActivationDiagnostic().contains("independently of calculateSteadyState"));
  }

  /** Expander runtime activation follows the branch that integrates nozzle, power and rotor-speed state. */
  @Test
  public void expanderActivationTracksStatefulRuntimeBranch() {
    Stream feed = createFeed("expander feed", 320.0);
    Expander expander = new Expander("expander", feed);
    ProcessSystem process = new ProcessSystem("power recovery");
    process.add(expander);

    DynamicCapabilityReport inactive = DynamicCapabilityReport.from(process);
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, inactive.getEntries().get(0).getCapability());
    assertEquals(DynamicActivationStatus.INACTIVE, inactive.getEntries().get(0).getActivationStatus());
    assertEquals(1, inactive.getInactiveAuditedDynamicElements().size());

    expander.setCalculateSteadyState(false);
    DynamicCapabilityReport active = DynamicCapabilityReport.from(process);
    assertEquals(DynamicActivationStatus.ACTIVE, active.getEntries().get(0).getActivationStatus());
    assertTrue(active.getInactiveAuditedDynamicElements().isEmpty());
    assertFalse(active.hasBlockingIssues());
  }

  /**
   * Activation gaps remain explicit review aids without pretending every DYNAMIC_LUMPED classification is qualified.
   */
  @Test
  public void unverifiedActivationRemainsVisibleForOtherDynamicFamilies() {
    Stream feed = createFeed("separator feed", 300.0);
    Separator separator = new Separator("separator", feed);
    ProcessSystem process = new ProcessSystem("separation");
    process.add(separator);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);

    assertEquals(DynamicCapability.DYNAMIC_LUMPED, report.getEntries().get(0).getCapability());
    assertEquals(DynamicActivationStatus.UNVERIFIED, report.getEntries().get(0).getActivationStatus());
    assertEquals(1, report.getUnverifiedActivationElements().size());
    assertEquals("separator", report.getUnverifiedActivationElements().get(0));
    assertFalse(report.hasBlockingIssues());
  }

  /** Multi-area reports retain area identity for activation diagnostics. */
  @Test
  public void processModelActivationDiagnosticsAreAreaQualified() {
    Stream hot = createFeed("hot feed", 330.0);
    Stream cold = createFeed("cold feed", 290.0);
    HeatExchanger exchanger = new HeatExchanger("HX-200", hot, cold);
    exchanger.setCalculateSteadyState(false);

    ProcessSystem topside = new ProcessSystem("topside system");
    topside.add(exchanger);
    ProcessModel model = new ProcessModel();
    model.add("topside", topside);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(model);

    assertTrue(report.hasBlockingIssues());
    assertTrue(report.getBlockingIssues().get(0).contains("topside::HX-200"));
    assertEquals("topside::HX-200", report.getEntries().get(0).getQualifiedName());
  }

  private static Stream createFeed(String name, double temperature) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    return stream;
  }
}
