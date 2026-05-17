package neqsim.process.safety.risk.bowtie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * Unit tests for Bow-Tie Diagram package.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class BowTieTest {

  private BowTieAnalyzer analyzer;
  private SafetyInstrumentedFunction hipps;

  @BeforeEach
  void setUp() {
    analyzer = new BowTieAnalyzer("Platform Safety Analysis");

    // Create HIPPS using builder pattern
    hipps = SafetyInstrumentedFunction.builder().id("SDV-001").name("Main HIPPS")
        .description("High Integrity Pipeline Protection")
        .category(SafetyInstrumentedFunction.SIFCategory.HIPPS).sil(2).pfd(0.005)
        .architecture("1oo2").protectedEquipment(Arrays.asList("Separator", "Pipeline")).build();
  }

  @Test
  void testBowTieAnalyzerCreation() {
    assertNotNull(analyzer, "Analyzer should not be null");
    assertEquals("Platform Safety Analysis", analyzer.getName());
  }

  @Test
  void testBowTieModelCreation() {
    BowTieModel model = new BowTieModel("SEP-001-OVP", "Overpressure in HP Separator");
    assertNotNull(model);
    assertEquals("SEP-001-OVP", model.getHazardId());
    assertEquals("Overpressure in HP Separator", model.getHazardDescription());
  }

  @Test
  void testThreatCreation() {
    BowTieModel.Threat threat =
        new BowTieModel.Threat("T-001", "Blocked outlet causing overpressure", 0.1);

    assertNotNull(threat);
    assertEquals("T-001", threat.getId());
    assertEquals("Blocked outlet causing overpressure", threat.getDescription());
    assertEquals(0.1, threat.getFrequency(), 0.001);
  }

  @Test
  void testConsequenceCreation() {
    BowTieModel.Consequence cons =
        new BowTieModel.Consequence("C-001", "Vessel rupture and release", 5);

    assertNotNull(cons);
    assertEquals("C-001", cons.getId());
    assertEquals("Vessel rupture and release", cons.getDescription());
    assertEquals(5, cons.getSeverity());
  }

  @Test
  void testBarrierCreation() {
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "HIPPS System", 0.005);

    assertNotNull(barrier);
    assertEquals("B-001", barrier.getId());
    assertEquals("HIPPS System", barrier.getDescription());
    assertEquals(0.005, barrier.getPfd(), 0.0001);
  }

  @Test
  void testBarrierRRF() {
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "HIPPS", 0.01);
    assertEquals(100.0, barrier.getRRF(), 0.01); // RRF = 1/PFD = 1/0.01 = 100
  }

  @Test
  void testBarrierTypeEnum() {
    BowTieModel.BarrierType[] types = BowTieModel.BarrierType.values();
    assertEquals(3, types.length); // PREVENTION, MITIGATION, BOTH
    assertEquals(BowTieModel.BarrierType.PREVENTION, BowTieModel.BarrierType.valueOf("PREVENTION"));
    assertEquals(BowTieModel.BarrierType.MITIGATION, BowTieModel.BarrierType.valueOf("MITIGATION"));
    assertEquals(BowTieModel.BarrierType.BOTH, BowTieModel.BarrierType.valueOf("BOTH"));
  }

  @Test
  void testBarrierWithType() {
    BowTieModel.Barrier preventionBarrier = new BowTieModel.Barrier("B-P1", "PSV", 0.01);
    preventionBarrier.setBarrierType(BowTieModel.BarrierType.PREVENTION);

    BowTieModel.Barrier mitigationBarrier = new BowTieModel.Barrier("B-M1", "Deluge", 0.05);
    mitigationBarrier.setBarrierType(BowTieModel.BarrierType.MITIGATION);

    assertEquals(BowTieModel.BarrierType.PREVENTION, preventionBarrier.getBarrierType());
    assertEquals(BowTieModel.BarrierType.MITIGATION, mitigationBarrier.getBarrierType());
  }

  @Test
  void testBowTieModelAddThreat() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");
    BowTieModel.Threat threat = new BowTieModel.Threat("T-001", "Test threat", 0.1);

    model.addThreat(threat);

    assertEquals(1, model.getThreats().size());
    assertEquals("T-001", model.getThreats().get(0).getId());
  }

  @Test
  void testBowTieModelAddConsequence() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");
    BowTieModel.Consequence cons = new BowTieModel.Consequence("C-001", "Test consequence", 3);

    model.addConsequence(cons);

    assertEquals(1, model.getConsequences().size());
    assertEquals("C-001", model.getConsequences().get(0).getId());
  }

  @Test
  void testBowTieModelAddBarrier() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Test barrier", 0.01);

    model.addBarrier(barrier);

    assertEquals(1, model.getBarriers().size());
    assertEquals("B-001", model.getBarriers().get(0).getId());
  }

  @Test
  void testSIFIntegration() {
    // Test that SIF can provide PFD for barrier
    double sifPfd = hipps.getPfdAvg();
    assertTrue(sifPfd > 0, "SIF PFD should be positive");
    assertTrue(sifPfd < 1, "SIF PFD should be less than 1");

    // Create barrier with SIF-derived PFD
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-SIF", "HIPPS Barrier", sifPfd);
    assertEquals(sifPfd, barrier.getPfd(), 0.0001);
  }

  @Test
  void testConsequenceCategory() {
    BowTieModel.Consequence cons = new BowTieModel.Consequence("C-001", "Fire and explosion", 5);
    cons.setCategory("SAFETY");

    assertEquals("SAFETY", cons.getCategory());
  }

  @Test
  void testBowTieModelCalculateRisk() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");

    // Add threats
    BowTieModel.Threat threat1 = new BowTieModel.Threat("T-001", "Threat 1", 0.1);
    BowTieModel.Threat threat2 = new BowTieModel.Threat("T-002", "Threat 2", 0.05);
    model.addThreat(threat1);
    model.addThreat(threat2);

    // Add prevention barrier
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Prevention", 0.01);
    barrier.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    model.addBarrier(barrier);

    // Add consequence
    BowTieModel.Consequence cons = new BowTieModel.Consequence("C-001", "Consequence", 5);
    model.addConsequence(cons);

    // Calculate risk
    model.calculateRisk();

    // Check results
    assertEquals(0.15, model.getUnmitigatedFrequency(), 0.001);
    assertTrue(model.getMitigatedFrequency() < model.getUnmitigatedFrequency());
    assertTrue(model.getTotalRRF() > 1);
    assertEquals(5, model.getMaxSeverity());
  }

  @Test
  void testBowTieModelGetPreventionBarriers() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");

    BowTieModel.Barrier prevention = new BowTieModel.Barrier("B-P1", "Prevention", 0.01);
    prevention.setBarrierType(BowTieModel.BarrierType.PREVENTION);

    BowTieModel.Barrier mitigation = new BowTieModel.Barrier("B-M1", "Mitigation", 0.02);
    mitigation.setBarrierType(BowTieModel.BarrierType.MITIGATION);

    model.addBarrier(prevention);
    model.addBarrier(mitigation);

    assertEquals(1, model.getPreventionBarriers().size());
    assertEquals(1, model.getMitigationBarriers().size());
  }

  @Test
  void testBowTieModelLinkBarrier() {
    BowTieModel model = new BowTieModel("TEST-001", "Test Hazard");

    BowTieModel.Threat threat = new BowTieModel.Threat("T-001", "Threat", 0.1);
    model.addThreat(threat);

    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Barrier", 0.01);
    model.addBarrier(barrier);

    model.linkBarrierToThreat("T-001", "B-001");

    assertTrue(model.getThreats().get(0).getLinkedBarrierIds().contains("B-001"));
  }

  @Test
  void testBowTieAnalyzerCreateBowTie() {
    BowTieModel model = analyzer.createBowTie("HAZ-001", "Overpressure", "OVERPRESSURE");

    assertNotNull(model);
    assertEquals("HAZ-001", model.getHazardId());
    assertEquals("Overpressure", model.getHazardDescription());
    assertEquals("OVERPRESSURE", model.getHazardType());

    // Should auto-populate from library
    assertTrue(model.getThreats().size() > 0);
    assertTrue(model.getConsequences().size() > 0);
  }

  @Test
  void testBowTieAnalyzerGetBowTie() {
    analyzer.createBowTie("HAZ-001", "Process Hazard", "OVERPRESSURE");

    BowTieModel retrieved = analyzer.getBowTie("HAZ-001");
    assertNotNull(retrieved);
    assertEquals("HAZ-001", retrieved.getHazardId());
  }

  @Test
  void testBowTieAnalyzerGetBowTieModels() {
    analyzer.createBowTie("HAZ-001", "Hazard 1", "OVERPRESSURE");
    analyzer.createBowTie("HAZ-002", "Hazard 2", "LOSS_OF_CONTAINMENT");

    assertEquals(2, analyzer.getBowTieModels().size());
  }

  @Test
  void testBowTieAnalyzerAddSIF() {
    analyzer.addAvailableSIF(hipps);
    // Should not throw exception
    assertNotNull(analyzer);
  }

  @Test
  void testBowTieModelToMap() {
    BowTieModel model = new BowTieModel("HAZ-001", "Test Hazard");
    model.setHazardType("OVERPRESSURE");
    model.addThreat(new BowTieModel.Threat("T-001", "Threat", 0.1));
    model.addConsequence(new BowTieModel.Consequence("C-001", "Consequence", 5));
    model.addBarrier(new BowTieModel.Barrier("B-001", "Barrier", 0.01));
    model.calculateRisk();

    java.util.Map<String, Object> map = model.toMap();

    assertNotNull(map);
    assertEquals("HAZ-001", map.get("hazardId"));
    assertEquals("Test Hazard", map.get("hazardDescription"));
    assertNotNull(map.get("threats"));
    assertNotNull(map.get("consequences"));
    assertNotNull(map.get("barriers"));
    assertNotNull(map.get("riskMetrics"));
  }

  @Test
  void testBowTieModelToJson() {
    BowTieModel model = new BowTieModel("HAZ-001", "Test Hazard");
    model.setHazardType("OVERPRESSURE");

    String json = model.toJson();

    assertNotNull(json);
    assertTrue(json.contains("HAZ-001"));
    assertTrue(json.contains("Test Hazard"));
  }

  @Test
  void testBowTieModelToVisualization() {
    BowTieModel model = new BowTieModel("HAZ-001", "Overpressure Event");
    model.addThreat(new BowTieModel.Threat("T-001", "Blocked outlet", 0.1));
    model.addConsequence(new BowTieModel.Consequence("C-001", "Vessel rupture", 5));

    BowTieModel.Barrier prevention = new BowTieModel.Barrier("B-001", "HIPPS", 0.01);
    prevention.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    model.addBarrier(prevention);

    model.calculateRisk();

    String viz = model.toVisualization();

    assertNotNull(viz);
    assertTrue(viz.contains("BOW-TIE"));
    assertTrue(viz.contains("THREATS"));
    assertTrue(viz.contains("CONSEQUENCES"));
  }

  @Test
  void testBowTieModelToString() {
    BowTieModel model = new BowTieModel("HAZ-001", "Test Hazard");
    model.addThreat(new BowTieModel.Threat("T-001", "Threat", 0.1));

    String str = model.toString();

    assertNotNull(str);
    assertTrue(str.contains("HAZ-001"));
    assertTrue(str.contains("threats=1"));
  }

  @Test
  void testThreatActiveFlag() {
    BowTieModel.Threat threat = new BowTieModel.Threat("T-001", "Test threat", 0.1);
    assertTrue(threat.isActive()); // Default is active

    threat.setActive(false);
    assertTrue(!threat.isActive());
  }

  @Test
  void testThreatSetFrequency() {
    BowTieModel.Threat threat = new BowTieModel.Threat("T-001", "Test threat", 0.1);
    threat.setFrequency(0.2);
    assertEquals(0.2, threat.getFrequency(), 0.001);
  }

  @Test
  void testConsequenceProbability() {
    BowTieModel.Consequence cons = new BowTieModel.Consequence("C-001", "Consequence", 5);
    assertEquals(1.0, cons.getProbability(), 0.001); // Default

    cons.setProbability(0.5);
    assertEquals(0.5, cons.getProbability(), 0.001);
  }

  @Test
  void testConsequenceSeverity() {
    BowTieModel.Consequence cons = new BowTieModel.Consequence("C-001", "Consequence", 3);
    cons.setSeverity(5);
    assertEquals(5, cons.getSeverity());
  }

  @Test
  void testBarrierFunctionalFlag() {
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Barrier", 0.01);
    assertTrue(barrier.isFunctional()); // Default is functional

    barrier.setFunctional(false);
    assertTrue(!barrier.isFunctional());
  }

  @Test
  void testBarrierEffectiveness() {
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Barrier", 0.01);
    assertEquals(0.99, barrier.getEffectiveness(), 0.001); // 1 - PFD
  }

  @Test
  void testBarrierSetPfd() {
    BowTieModel.Barrier barrier = new BowTieModel.Barrier("B-001", "Barrier", 0.01);
    barrier.setPfd(0.001);
    assertEquals(0.001, barrier.getPfd(), 0.0001);
    assertEquals(0.999, barrier.getEffectiveness(), 0.0001);
  }

  @Test
  void testBowTieAnalyzerCalculateRisk() {
    analyzer.createBowTie("HAZ-001", "Hazard 1", "OVERPRESSURE");
    analyzer.createBowTie("HAZ-002", "Hazard 2", "LOSS_OF_CONTAINMENT");

    analyzer.calculateRisk();

    // Verify all models have been calculated
    for (BowTieModel model : analyzer.getBowTieModels()) {
      assertTrue(model.getUnmitigatedFrequency() >= 0);
    }
  }

  @Test
  void testBowTieAnalyzerToJson() {
    analyzer.createBowTie("HAZ-001", "Test Hazard", "OVERPRESSURE");

    String json = analyzer.toJson();

    assertNotNull(json);
    assertTrue(json.contains("Platform Safety Analysis"));
    assertTrue(json.contains("bowTies"));
  }

  @Test
  void testBowTieAnalyzerGenerateReport() {
    analyzer.createBowTie("HAZ-001", "Test Hazard", "OVERPRESSURE");
    analyzer.calculateRisk();

    String report = analyzer.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("BOW-TIE ANALYSIS REPORT"));
    assertTrue(report.contains("Platform Safety Analysis"));
  }

  @Test
  void testBowTieAnalyzerToString() {
    analyzer.createBowTie("HAZ-001", "Hazard 1", "OVERPRESSURE");

    String str = analyzer.toString();

    assertNotNull(str);
    assertTrue(str.contains("Platform Safety Analysis"));
    assertTrue(str.contains("bowTies=1"));
  }
}

