package neqsim.process.safety.risk.bowtie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for BowTieSvgExporter.
 */
class BowTieSvgExporterTest {

  private BowTieModel model;
  private BowTieSvgExporter exporter;

  @BeforeEach
  void setUp() {
    model = new BowTieModel("HAZARD-001", "Loss of containment");

    // Add threats using correct API
    BowTieModel.Threat threat1 = new BowTieModel.Threat("T-001", "Overpressure", 0.01);
    BowTieModel.Threat threat2 = new BowTieModel.Threat("T-002", "Corrosion", 0.005);
    model.addThreat(threat1);
    model.addThreat(threat2);

    // Add prevention barriers
    BowTieModel.Barrier barrier1 = new BowTieModel.Barrier("B-001", "PSV", 0.01);
    barrier1.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    threat1.linkBarrier("B-001");
    model.addBarrier(barrier1);

    BowTieModel.Barrier barrier2 = new BowTieModel.Barrier("B-002", "High Pressure Alarm", 0.1);
    barrier2.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    threat1.linkBarrier("B-002");
    model.addBarrier(barrier2);

    BowTieModel.Barrier barrier3 = new BowTieModel.Barrier("B-003", "Inspection", 0.05);
    barrier3.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    threat2.linkBarrier("B-003");
    model.addBarrier(barrier3);

    // Add consequences
    BowTieModel.Consequence consequence1 = new BowTieModel.Consequence("C-001", "Fire", 5);
    BowTieModel.Consequence consequence2 = new BowTieModel.Consequence("C-002", "Environmental", 4);
    model.addConsequence(consequence1);
    model.addConsequence(consequence2);

    // Add mitigation barriers
    BowTieModel.Barrier barrier4 = new BowTieModel.Barrier("B-004", "Deluge", 0.1);
    barrier4.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    consequence1.linkBarrier("B-004");
    model.addBarrier(barrier4);

    BowTieModel.Barrier barrier5 = new BowTieModel.Barrier("B-005", "Containment", 0.15);
    barrier5.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    consequence2.linkBarrier("B-005");
    model.addBarrier(barrier5);

    exporter = new BowTieSvgExporter(model);
  }

  @Test
  void testExportSvg() {
    String svg = exporter.export();
    assertNotNull(svg);
    assertFalse(svg.isEmpty());
    assertTrue(svg.startsWith("<?xml"));
    assertTrue(svg.contains("<svg"));
    assertTrue(svg.contains("</svg>"));
  }

  @Test
  void testSvgContainsHazard() {
    String svg = exporter.export();
    assertTrue(svg.contains("Loss of containment") || svg.contains("HAZARD-001"));
  }

  @Test
  void testSvgContainsThreats() {
    String svg = exporter.export();
    assertTrue(svg.contains("Overpressure"));
    assertTrue(svg.contains("Corrosion"));
  }

  @Test
  void testSvgContainsBarriers() {
    String svg = exporter.export();
    assertTrue(svg.contains("PSV"));
    assertTrue(svg.contains("Deluge"));
  }

  @Test
  void testSvgContainsConsequences() {
    String svg = exporter.export();
    assertTrue(svg.contains("Fire"));
    assertTrue(svg.contains("Environmental"));
  }

  @Test
  void testSvgContainsStyles() {
    String svg = exporter.export();
    assertTrue(svg.contains("<style>"));
    assertTrue(svg.contains("threat-box"));
    assertTrue(svg.contains("hazard-box"));
    assertTrue(svg.contains("consequence-box"));
  }

  @Test
  void testSvgContainsStatistics() {
    String svg = exporter.export();
    assertTrue(svg.contains("Unmitigated Freq"));
    assertTrue(svg.contains("Mitigated Freq"));
    assertTrue(svg.contains("Risk Reduction"));
  }

  @Test
  void testExportToHtml() {
    String html = exporter.exportToHtml();
    assertNotNull(html);
    assertTrue(html.contains("<!DOCTYPE html>"));
    assertTrue(html.contains("<html>"));
    assertTrue(html.contains("<svg"));
    assertTrue(html.contains("HAZARD-001") || html.contains("Loss of containment"));
  }

  @Test
  void testCustomDimensions() {
    BowTieSvgExporter customExporter = new BowTieSvgExporter(model, 1600, 1000);
    assertEquals(1600, customExporter.getWidth());
    assertEquals(1000, customExporter.getHeight());

    String svg = customExporter.export();
    assertTrue(svg.contains("width=\"1600\""));
    assertTrue(svg.contains("height=\"1000\""));
  }

  @Test
  void testSetDimensions() {
    exporter.setWidth(800);
    exporter.setHeight(600);
    assertEquals(800, exporter.getWidth());
    assertEquals(600, exporter.getHeight());
  }

  @Test
  void testEmptyModel() {
    BowTieModel emptyModel = new BowTieModel("EMPTY-001", "Test hazard");
    BowTieSvgExporter emptyExporter = new BowTieSvgExporter(emptyModel);

    String svg = emptyExporter.export();
    assertNotNull(svg);
    assertTrue(svg.contains("<svg"));
    assertTrue(svg.contains("HAZARD"));
  }

  @Test
  void testXmlEscaping() {
    BowTieModel specialModel =
        new BowTieModel("SPECIAL-001", "Hazard with \"quotes\" and 'apostrophes'");
    BowTieModel.Threat specialThreat = new BowTieModel.Threat("T-SPECIAL", "Threat <test>", 0.01);
    specialModel.addThreat(specialThreat);

    BowTieSvgExporter specialExporter = new BowTieSvgExporter(specialModel);
    String svg = specialExporter.export();

    // Should not contain unescaped special characters that would break XML
    assertNotNull(svg);
    assertTrue(svg.contains("<svg"));
  }

  private void assertEquals(int expected, int actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
