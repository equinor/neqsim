package neqsim.process.decisionsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.decisionsupport.EngineeringRecommendation.Verdict;
import neqsim.process.decisionsupport.workflow.DerateOptionsWorkflow;
import neqsim.process.decisionsupport.workflow.EquipmentStatusWorkflow;
import neqsim.process.decisionsupport.workflow.ProductSpecCheckWorkflow;
import neqsim.process.decisionsupport.workflow.RateChangeFeasibilityWorkflow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for the decision support engine with a real ProcessSystem.
 *
 * <p>
 * Sets up a simple gas processing system (feed → separator) and runs the full engine evaluation
 * pipeline including workflow dispatch, audit logging, and result generation.
 * </p>
 *
 * @author NeqSim Development Team
 */
class DecisionSupportIntegrationTest {

  private static ProcessSystem baseProcess;
  private static OperatingSpecification spec;

  /**
   * Sets up a simple process: feed stream → HP separator.
   */
  @BeforeAll
  static void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    Separator separator = new Separator("HP sep", feed);

    baseProcess = new ProcessSystem();
    baseProcess.add(feed);
    baseProcess.add(separator);
    baseProcess.run();

    // Set up operating specs
    spec = new OperatingSpecification("Test Gas Plant");
    spec.addProductSpec("waterDewPoint_C", Double.NaN, -18.0, "C", "ISO 6327");
    spec.addProductSpec("wobbeIndex_MJ", 46.1, 52.2, "MJ/Sm3", "EN 16726");
    spec.addEquipmentLimit("HP sep", "capacity", Double.NaN, 10000.0, "kg/hr", "Design basis");
  }

  @Test
  void testRateChangeFeasibilityWorkflow() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY,
        new RateChangeFeasibilityWorkflow());

    OperatorQuery query =
        OperatorQuery.builder().queryType(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY)
            .description("Can we increase to 6000 kg/hr?").parameter("targetFlowRate", 6000.0)
            .parameter("flowRateUnit", "kg/hr").parameter("feedStreamName", "feed").build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertNotNull(rec.getVerdict());
    assertNotNull(rec.getSummary());
    assertFalse(rec.getSummary().isEmpty());
    assertNotNull(rec.getAuditId());
    assertTrue(rec.getConfidence() > 0);

    // Check audit was logged
    assertTrue(engine.getAuditLogger().getRecordCount() > 0);
  }

  @Test
  void testEquipmentStatusWorkflow() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.EQUIPMENT_STATUS,
        new EquipmentStatusWorkflow());

    OperatorQuery query =
        OperatorQuery.builder().queryType(OperatorQuery.QueryType.EQUIPMENT_STATUS)
            .description("Current equipment status?").build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertNotNull(rec.getVerdict());
    // Equipment status should at least report something
    assertNotNull(rec.getSummary());
    assertTrue(rec.getConfidence() > 0);
  }

  @Test
  void testDerateOptionsWorkflow() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.DERATE_OPTIONS, new DerateOptionsWorkflow());

    OperatorQuery query = OperatorQuery.builder().queryType(OperatorQuery.QueryType.DERATE_OPTIONS)
        .description("Safest derate option now?").parameter("currentFlowRate", 5000.0)
        .parameter("minFlowRate", 3000.0).parameter("flowRateUnit", "kg/hr")
        .parameter("feedStreamName", "feed").parameter("steps", 3).build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertNotNull(rec.getVerdict());
    assertNotNull(rec.getSummary());
  }

  @Test
  void testProductSpecCheckWorkflow() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.PRODUCT_SPEC_CHECK,
        new ProductSpecCheckWorkflow());

    Map<String, Object> productValues = new HashMap<>();
    productValues.put("waterDewPoint_C", -22.0);
    productValues.put("wobbeIndex_MJ", 49.5);

    OperatorQuery query = OperatorQuery.builder()
        .queryType(OperatorQuery.QueryType.PRODUCT_SPEC_CHECK)
        .description("Are we meeting gas spec?").parameter("productValues", productValues).build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertEquals(Verdict.FEASIBLE, rec.getVerdict());
    assertTrue(rec.getSummary().contains("pass"));
  }

  @Test
  void testProductSpecCheckFailure() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.PRODUCT_SPEC_CHECK,
        new ProductSpecCheckWorkflow());

    Map<String, Object> productValues = new HashMap<>();
    productValues.put("waterDewPoint_C", -10.0); // above max of -18
    productValues.put("wobbeIndex_MJ", 55.0); // above max of 52.2

    OperatorQuery query =
        OperatorQuery.builder().queryType(OperatorQuery.QueryType.PRODUCT_SPEC_CHECK)
            .description("Bad spec check").parameter("productValues", productValues).build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertEquals(Verdict.NOT_FEASIBLE, rec.getVerdict());
    assertTrue(rec.getFindings().size() >= 2);
  }

  @Test
  void testFullEngineWithAllWorkflows() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.setModelVersion("test-v1");
    engine.registerWorkflow(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY,
        new RateChangeFeasibilityWorkflow());
    engine.registerWorkflow(OperatorQuery.QueryType.EQUIPMENT_STATUS,
        new EquipmentStatusWorkflow());
    engine.registerWorkflow(OperatorQuery.QueryType.DERATE_OPTIONS, new DerateOptionsWorkflow());
    engine.registerWorkflow(OperatorQuery.QueryType.PRODUCT_SPEC_CHECK,
        new ProductSpecCheckWorkflow());

    List<String> available = engine.getAvailableWorkflows();
    assertEquals(4, available.size());

    // Run multiple queries on the same engine
    OperatorQuery q1 = OperatorQuery.builder().queryType(OperatorQuery.QueryType.EQUIPMENT_STATUS)
        .description("Status check 1").build();

    OperatorQuery q2 = OperatorQuery.builder().queryType(OperatorQuery.QueryType.EQUIPMENT_STATUS)
        .description("Status check 2").build();

    engine.evaluate(q1);
    engine.evaluate(q2);

    // Both should be audited
    assertEquals(2, engine.getAuditLogger().getRecordCount());
  }

  @Test
  void testRecommendationJsonSerializesCleanly() {
    DecisionSupportEngine engine = new DecisionSupportEngine(baseProcess);
    engine.setOperatingSpecification(spec);
    engine.registerWorkflow(OperatorQuery.QueryType.EQUIPMENT_STATUS,
        new EquipmentStatusWorkflow());

    OperatorQuery query = OperatorQuery.builder()
        .queryType(OperatorQuery.QueryType.EQUIPMENT_STATUS).description("JSON check").build();

    EngineeringRecommendation rec = engine.evaluate(query);
    String json = rec.toJson();
    assertNotNull(json);
    assertTrue(json.contains("verdict"));
    assertTrue(json.contains("auditId"));
    assertTrue(json.contains("summary"));

    // Human readable also works
    String readable = rec.toHumanReadable();
    assertNotNull(readable);
    assertTrue(readable.contains("Verdict:"));
  }
}
