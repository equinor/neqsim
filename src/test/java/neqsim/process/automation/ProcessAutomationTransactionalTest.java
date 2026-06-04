package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessAutomation#setValuesTransactional(Map, String)} and
 * {@link ProcessAutomation#setVariableValueValidated(String, double, String)}.
 */
class ProcessAutomationTransactionalTest {

  private ProcessSystem process;
  private ProcessAutomation automation;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator sep = new Separator("HP Sep", feed);
    Compressor k1 = new Compressor("K-101", sep.getGasOutStream());
    k1.setOutletPressure(120.0);
    Cooler c1 = new Cooler("C-101", k1.getOutletStream());
    c1.setOutletTemperature(30.0, "C");
    ThrottlingValve v1 = new ThrottlingValve("V-101", c1.getOutletStream());
    v1.setOutletPressure(50.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(k1);
    process.add(c1);
    process.add(v1);
    process.run();

    automation = new ProcessAutomation(process);
  }

  @Test
  void defaultRegistryIsPresent() {
    assertNotNull(automation.getWriteValidatorRegistry());
    assertTrue(automation.getWriteValidatorRegistry().getRegisteredClasses()
        .containsKey(Compressor.class));
  }

  @Test
  void validatedWriteAcceptsGoodValue() {
    WriteValidationResult r =
        automation.setVariableValueValidated("K-101.outletPressure", 130.0, "bara");
    assertTrue(r.isAllowed());
    assertTrue(automation.isDirty());
  }

  @Test
  void validatedWriteRejectsBadValue() {
    boolean threw = false;
    try {
      automation.setVariableValueValidated("K-101.outletPressure", 10.0, "bara");
    } catch (IllegalArgumentException ex) {
      threw = true;
      assertTrue(ex.getMessage().contains("OUTLET_PRESSURE_BELOW_INLET"));
    }
    assertTrue(threw, "expected validator to reject outlet below inlet");
    // dirty flag must not flip when validation rejected the write
    assertFalse(automation.isDirty());
  }

  @Test
  void transactionalBatchCommitsValidWrites() {
    double pBefore = automation.getVariableValue("K-101.outletPressure", "bara");
    double tBefore = automation.getVariableValue("C-101.outletTemperature", "C");

    Map<String, Double> updates = new LinkedHashMap<String, Double>();
    updates.put("K-101.outletPressure", 140.0);
    updates.put("C-101.outletTemperature", 25.0);

    TransactionalBatchResult r = automation.setValuesTransactional(updates, null);
    // Different addresses use different units — first uses bara, second uses C — but
    // the registry only cares about ordering of validation. We pass null unit so each
    // value is in the variable's default unit.
    assertTrue(r.isCommitted(), "batch should commit; got: " + r.toJson());
    assertEquals(2, r.getWrites().size());
    for (TransactionalBatchResult.WriteOutcome wo : r.getWrites()) {
      assertTrue(wo.isApplied());
      assertEquals(WriteValidationResult.Severity.OK, wo.getValidation().getSeverity());
    }
    double pAfter = automation.getVariableValue("K-101.outletPressure", "bara");
    assertEquals(140.0, pAfter, 0.5);
    // Sanity: previous values were captured
    assertNotNull(r.getWrites().get(0).getPreviousValue());
    assertEquals(pBefore, r.getWrites().get(0).getPreviousValue(), 0.5);
    // Suppress unused warning
    if (tBefore < -999) {
      throw new IllegalStateException("unreachable");
    }
  }

  @Test
  void transactionalBatchRollsBackOnValidationError() {
    double pBefore = automation.getVariableValue("K-101.outletPressure", "bara");

    Map<String, Double> updates = new LinkedHashMap<String, Double>();
    updates.put("K-101.outletPressure", 140.0);
    // Bad: outlet pressure below inlet pressure of valve
    updates.put("V-101.outletPressure", 200.0);

    TransactionalBatchResult r = automation.setValuesTransactional(updates, "bara");

    assertTrue(r.isRolledBack());
    assertEquals(TransactionalBatchResult.RollbackCategory.VALIDATION_FAILED,
        r.getRollbackCategory());
    assertNotNull(r.getRollbackReason());
    assertTrue(r.getRollbackReason().contains("OUTLET_PRESSURE_ABOVE_INLET"));

    // No writes should have been applied
    for (TransactionalBatchResult.WriteOutcome wo : r.getWrites()) {
      assertFalse(wo.isApplied(), "no writes should be applied on validation failure");
    }
    double pAfter = automation.getVariableValue("K-101.outletPressure", "bara");
    assertEquals(pBefore, pAfter, 0.5,
        "K-101.outletPressure must be untouched after validation rollback");
  }

  @Test
  void toJsonHasStableFields() {
    Map<String, Double> updates = new LinkedHashMap<String, Double>();
    updates.put("K-101.outletPressure", 140.0);
    TransactionalBatchResult r = automation.setValuesTransactional(updates, "bara");
    String json = r.toJson().toString();
    assertTrue(json.contains("\"schemaVersion\""));
    assertTrue(json.contains("\"committed\""));
    assertTrue(json.contains("\"rolledBack\""));
    assertTrue(json.contains("\"writes\""));
  }
}
