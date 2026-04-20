package neqsim.process.decisionsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintCheckResult;
import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintStatus;
import neqsim.process.decisionsupport.EngineeringRecommendation.DerateOption;
import neqsim.process.decisionsupport.EngineeringRecommendation.Finding;
import neqsim.process.decisionsupport.EngineeringRecommendation.Severity;
import neqsim.process.decisionsupport.EngineeringRecommendation.Verdict;

/**
 * Unit tests for the decision support core model classes.
 *
 * @author NeqSim Development Team
 */
class DecisionSupportModelTest {

  @Test
  void testOperatorQueryBuilder() {
    OperatorQuery query =
        OperatorQuery.builder().queryType(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY)
            .description("Can we increase to 150 t/hr?").requestedBy("operator-1")
            .urgency(OperatorQuery.Urgency.PRIORITY).parameter("targetFlowRate", 150.0)
            .parameter("flowRateUnit", "kg/hr").currentCondition("temperature_C", 25.0)
            .constraintName("K-100 surge margin").build();

    assertNotNull(query.getQueryId());
    assertEquals(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY, query.getQueryType());
    assertEquals("operator-1", query.getRequestedBy());
    assertEquals(OperatorQuery.Urgency.PRIORITY, query.getUrgency());
    assertEquals(150.0, query.getParameterAsDouble("targetFlowRate", 0.0), 0.01);
    assertEquals("kg/hr", query.getParameterAsString("flowRateUnit", ""));
    assertEquals(25.0, query.getCurrentConditions().get("temperature_C"), 0.01);
    assertTrue(query.getConstraintNames().contains("K-100 surge margin"));
  }

  @Test
  void testOperatorQueryJsonRoundTrip() {
    OperatorQuery query =
        OperatorQuery.builder().queryType(OperatorQuery.QueryType.GAS_QUALITY_IMPACT)
            .description("New gas from well B").build();

    String json = query.toJson();
    assertNotNull(json);
    assertTrue(json.contains("GAS_QUALITY_IMPACT"));

    OperatorQuery restored = OperatorQuery.fromJson(json);
    assertNotNull(restored);
    assertEquals(query.getQueryId(), restored.getQueryId());
    assertEquals(OperatorQuery.QueryType.GAS_QUALITY_IMPACT, restored.getQueryType());
  }

  @Test
  void testEngineeringRecommendationBuilder() {
    Map<String, ConstraintStatus> statuses = new HashMap<>();
    statuses.put("K-100", ConstraintStatus.PASS);

    EngineeringRecommendation rec = EngineeringRecommendation.builder()
        .verdict(Verdict.FEASIBLE_WITH_WARNINGS).summary("Rate increase possible with warnings")
        .addFinding(new Finding("Surge margin low", Severity.WARNING, "K-100.surgeMargin", 0.08,
            0.10, "fraction"))
        .addFinding(new Finding("General note", Severity.INFO))
        .addConstraintCheck(new ConstraintCheckResult("K-100 capacity", ConstraintStatus.WARN, 8.0,
            92.0, 100.0, "%"))
        .addOperatingEnvelopeStatus("K-100", "NEAR LIMIT (92%)")
        .addDerateOption(new DerateOption(130.0, "kg/hr", 15.0, "Low", "K-100", statuses))
        .addAssumption("Steady-state model").addLimitation("No dynamic analysis").confidence(0.85)
        .modelVersion("v1.0").queryId("test-query-id").build();

    assertEquals(Verdict.FEASIBLE_WITH_WARNINGS, rec.getVerdict());
    assertEquals(2, rec.getFindings().size());
    assertEquals(1, rec.getConstraintChecks().size());
    assertEquals(1, rec.getDerateOptions().size());
    assertEquals(0.85, rec.getConfidence(), 0.001);
    assertNotNull(rec.getAuditId());
    assertNotNull(rec.getTimestamp());
  }

  @Test
  void testEngineeringRecommendationToJson() {
    EngineeringRecommendation rec =
        EngineeringRecommendation.builder().verdict(Verdict.FEASIBLE).summary("All good").build();

    String json = rec.toJson();
    assertNotNull(json);
    assertTrue(json.contains("FEASIBLE"));
    assertTrue(json.contains("All good"));
  }

  @Test
  void testEngineeringRecommendationToHumanReadable() {
    EngineeringRecommendation rec =
        EngineeringRecommendation.builder().verdict(Verdict.NOT_FEASIBLE).summary("Rate too high")
            .addFinding(new Finding("Overloaded", Severity.ERROR, "sep.util", 105.0, 100.0, "%"))
            .addConstraintCheck(
                new ConstraintCheckResult("V-100", ConstraintStatus.FAIL, -5.0, 105.0, 100.0, "%"))
            .confidence(0.80).build();

    String readable = rec.toHumanReadable();
    assertNotNull(readable);
    assertTrue(readable.contains("NOT_FEASIBLE"));
    assertTrue(readable.contains("Rate too high"));
    assertTrue(readable.contains("Overloaded"));
    assertTrue(readable.contains("Audit ID:"));
  }

  @Test
  void testOperatingSpecificationCheckValues() {
    OperatingSpecification spec = new OperatingSpecification("Test Plant");
    spec.addProductSpec("waterDewPoint_C", Double.NaN, -18.0, "C", "ISO 6327");
    spec.addProductSpec("wobbeIndex_MJ", 46.1, 52.2, "MJ/Sm3", "EN 16726");
    spec.addEquipmentLimit("K-100", "surgeMargin", 0.10, Double.NaN, "fraction", "API 617");

    Map<String, Double> values = new HashMap<>();
    values.put("waterDewPoint_C", -22.0);
    values.put("wobbeIndex_MJ", 49.5);

    List<OperatingSpecification.SpecCheckResult> results = spec.checkValues(values);
    assertEquals(2, results.size());

    for (OperatingSpecification.SpecCheckResult r : results) {
      assertEquals(ConstraintStatus.PASS, r.getStatus(),
          "Spec " + r.getSpecName() + " should pass");
    }

    // Test failing spec
    values.put("waterDewPoint_C", -15.0);
    results = spec.checkValues(values);
    boolean foundFail = false;
    for (OperatingSpecification.SpecCheckResult r : results) {
      if (r.getSpecName().equals("waterDewPoint_C")) {
        assertEquals(ConstraintStatus.FAIL, r.getStatus());
        foundFail = true;
      }
    }
    assertTrue(foundFail, "Water dew point should fail when above max");
  }

  @Test
  void testOperatingSpecificationJsonRoundTrip() {
    OperatingSpecification spec = new OperatingSpecification("Gas Export");
    spec.addProductSpec("waterDewPoint_C", Double.NaN, -18.0, "C", "ISO 6327");

    String json = spec.toJson();
    assertNotNull(json);

    OperatingSpecification restored = OperatingSpecification.fromJson(json);
    assertNotNull(restored);
    assertEquals("Gas Export", restored.getName());
    assertEquals(1, restored.getProductSpecs().size());
  }

  @Test
  void testInMemoryAuditLogger() {
    InMemoryAuditLogger logger = new InMemoryAuditLogger();
    assertEquals(0, logger.getRecordCount());

    Instant now = Instant.now();
    AuditRecord record =
        new AuditRecord("audit-1", now, "{}", "{}", "hash1", 100L, "1.0", "test-workflow");
    logger.log(record);

    assertEquals(1, logger.getRecordCount());
    assertNotNull(logger.getRecord("audit-1"));
    assertEquals("test-workflow", logger.getRecord("audit-1").getWorkflowId());

    List<AuditRecord> records = logger.getRecords(now.minusSeconds(60), now.plusSeconds(60));
    assertEquals(1, records.size());

    logger.clear();
    assertEquals(0, logger.getRecordCount());
  }

  @Test
  void testAuditRecordFields() {
    Instant ts = Instant.now();
    AuditRecord record =
        new AuditRecord("id-1", ts, "queryJson", "recJson", "abc123", 250L, "3.7.0", "rate-change");

    assertEquals("id-1", record.getAuditId());
    assertEquals(ts, record.getTimestamp());
    assertEquals("queryJson", record.getQueryJson());
    assertEquals("recJson", record.getRecommendationJson());
    assertEquals("abc123", record.getModelStateHash());
    assertEquals(250L, record.getSimulationDurationMs());
    assertEquals("3.7.0", record.getNeqsimVersion());
    assertEquals("rate-change", record.getWorkflowId());
    assertNotNull(record.toString());
  }

  @Test
  void testFindingWithAndWithoutVariable() {
    Finding withVar =
        new Finding("Surge margin low", Severity.WARNING, "K-100.surge", 0.08, 0.10, "fraction");
    assertEquals("K-100.surge", withVar.getVariable());
    assertEquals(0.08, withVar.getValue(), 0.001);

    Finding withoutVar = new Finding("General note", Severity.INFO);
    assertNotNull(withoutVar.getDescription());
    assertEquals(Severity.INFO, withoutVar.getSeverity());
    assertEquals(null, withoutVar.getVariable());
  }

  @Test
  void testDerateOptionFields() {
    Map<String, ConstraintStatus> statuses = new HashMap<>();
    statuses.put("K-100", ConstraintStatus.PASS);
    statuses.put("V-100", ConstraintStatus.WARN);

    DerateOption opt = new DerateOption(120.0, "kg/hr", 18.5, "Low", "V-100", statuses);

    assertEquals(120.0, opt.getFlowRate(), 0.01);
    assertEquals("kg/hr", opt.getFlowRateUnit());
    assertEquals(18.5, opt.getSafetyMarginPercent(), 0.01);
    assertEquals("Low", opt.getRiskLevel());
    assertEquals("V-100", opt.getLimitingEquipment());
    assertEquals(2, opt.getConstraintStatuses().size());
  }

  @Test
  void testConfidenceClamp() {
    EngineeringRecommendation over = EngineeringRecommendation.builder().confidence(1.5).build();
    assertEquals(1.0, over.getConfidence(), 0.001);

    EngineeringRecommendation under = EngineeringRecommendation.builder().confidence(-0.5).build();
    assertEquals(0.0, under.getConfidence(), 0.001);
  }

  @Test
  void testDecisionSupportEngineNoWorkflow() {
    // We need a minimal ProcessSystem. Just verify the engine handles missing workflow gracefully.
    // Full integration tested separately.
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);

    DecisionSupportEngine engine = new DecisionSupportEngine(process);

    OperatorQuery query = OperatorQuery.builder()
        .queryType(OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY).description("test").build();

    EngineeringRecommendation rec = engine.evaluate(query);
    assertNotNull(rec);
    assertEquals(Verdict.REQUIRES_FURTHER_ANALYSIS, rec.getVerdict());
    assertTrue(rec.getSummary().contains("No workflow registered"));
  }

  @Test
  void testDecisionSupportEngineAvailableWorkflows() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);

    DecisionSupportEngine engine = new DecisionSupportEngine(process);
    engine.registerWorkflow(OperatorQuery.QueryType.EQUIPMENT_STATUS,
        new neqsim.process.decisionsupport.workflow.EquipmentStatusWorkflow());

    List<String> available = engine.getAvailableWorkflows();
    assertEquals(1, available.size());
    assertTrue(available.get(0).contains("EQUIPMENT_STATUS"));
  }

  @Test
  void testOperatorQueryParameterDefaults() {
    OperatorQuery query = OperatorQuery.builder().queryType(OperatorQuery.QueryType.CUSTOM).build();

    assertEquals(42.0, query.getParameterAsDouble("nonexistent", 42.0), 0.01);
    assertEquals("default", query.getParameterAsString("nonexistent", "default"));
    assertNotNull(query.getTimestamp());
    assertFalse(query.getQueryId().isEmpty());
  }

  @Test
  void testEquipmentLimitFields() {
    OperatingSpecification spec = new OperatingSpecification();
    spec.addEquipmentLimit("K-100", "surgeMargin", 0.10, Double.NaN, "fraction", "API 617");

    List<OperatingSpecification.EquipmentLimit> limits = spec.getEquipmentLimits();
    assertEquals(1, limits.size());

    OperatingSpecification.EquipmentLimit limit = limits.get(0);
    assertEquals("K-100", limit.getEquipmentName());
    assertEquals("surgeMargin", limit.getParameterName());
    assertEquals(0.10, limit.getMinValue(), 0.001);
    assertTrue(Double.isNaN(limit.getMaxValue()));
    assertEquals("fraction", limit.getUnit());
    assertEquals("API 617", limit.getStandardRef());
  }
}
