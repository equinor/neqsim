package neqsim.process.mechanicaldesign.torg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/**
 * Tests for TechnicalRequirementsDocument class.
 */
class TechnicalRequirementsDocumentTest {

  @Test
  void testBuilderCreatesDocument() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-001").projectName("Test Project")
            .companyIdentifier("TestCo").revision("1").issueDate("2024-01-01").build();

    assertNotNull(torg);
    assertEquals("TEST-001", torg.getProjectId());
    assertEquals("Test Project", torg.getProjectName());
    assertEquals("TestCo", torg.getCompanyIdentifier());
    assertEquals("1", torg.getRevision());
    assertEquals("2024-01-01", torg.getIssueDate());
  }

  @Test
  void testAddStandardToCategory() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-002")
            .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1)
            .addStandard("separator process design", StandardType.API_12J)
            .addStandard("pipeline design codes", StandardType.NORSOK_L_001).build();

    List<StandardType> pvStandards = torg.getStandardsForCategory("pressure vessel design code");
    assertEquals(1, pvStandards.size());
    assertEquals(StandardType.ASME_VIII_DIV1, pvStandards.get(0));

    List<StandardType> sepStandards = torg.getStandardsForCategory("separator process design");
    assertEquals(1, sepStandards.size());
    assertEquals(StandardType.API_12J, sepStandards.get(0));
  }

  @Test
  void testAddMultipleStandardsToSameCategory() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-003").addStandard("separator process design", StandardType.API_12J)
        .addStandard("separator process design", StandardType.NORSOK_P_001).build();

    List<StandardType> standards = torg.getStandardsForCategory("separator process design");
    assertEquals(2, standards.size());
    assertTrue(standards.contains(StandardType.API_12J));
    assertTrue(standards.contains(StandardType.NORSOK_P_001));
  }

  @Test
  void testSetStandardsReplacesExisting() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-004").addStandard("pipeline design codes", StandardType.NORSOK_L_001)
        .setStandards("pipeline design codes",
            Arrays.asList(StandardType.DNV_ST_F101, StandardType.ISO_13623))
        .build();

    List<StandardType> standards = torg.getStandardsForCategory("pipeline design codes");
    assertEquals(2, standards.size());
    assertTrue(standards.contains(StandardType.DNV_ST_F101));
    assertTrue(standards.contains(StandardType.ISO_13623));
    assertFalse(standards.contains(StandardType.NORSOK_L_001));
  }

  @Test
  void testAddEquipmentSpecificStandard() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-005").addEquipmentStandard("Separator", StandardType.API_12J)
        .addEquipmentStandard("Separator", StandardType.ASME_VIII_DIV1).build();

    List<StandardType> standards = torg.getStandardsForEquipment("Separator");
    assertEquals(2, standards.size());
    assertTrue(standards.contains(StandardType.API_12J));
    assertTrue(standards.contains(StandardType.ASME_VIII_DIV1));
  }

  @Test
  void testGetAllApplicableStandards() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-006").addEquipmentStandard("Separator", StandardType.API_12J)
        .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1).build();

    List<StandardType> applicable = torg.getAllApplicableStandards("Separator");
    assertTrue(applicable.contains(StandardType.API_12J));
    assertTrue(applicable.contains(StandardType.ASME_VIII_DIV1));
  }

  @Test
  void testEnvironmentalConditions() {
    TechnicalRequirementsDocument.EnvironmentalConditions env =
        new TechnicalRequirementsDocument.EnvironmentalConditions(-40.0, 45.0, 4.0, "0", 30.0, 15.0,
            "North Sea");

    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-007").environmentalConditions(env).build();

    assertNotNull(torg.getEnvironmentalConditions());
    assertEquals(-40.0, torg.getEnvironmentalConditions().getMinAmbientTemperature(), 0.01);
    assertEquals(45.0, torg.getEnvironmentalConditions().getMaxAmbientTemperature(), 0.01);
    assertEquals(4.0, torg.getEnvironmentalConditions().getDesignSeawaterTemperature(), 0.01);
    assertEquals("0", torg.getEnvironmentalConditions().getSeismicZone());
    assertEquals("North Sea", torg.getEnvironmentalConditions().getLocation());
  }

  @Test
  void testSimplifiedEnvironmentalConditions() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-008").environmentalConditions(-46.0, 40.0).build();

    assertNotNull(torg.getEnvironmentalConditions());
    assertEquals(-46.0, torg.getEnvironmentalConditions().getMinAmbientTemperature(), 0.01);
    assertEquals(40.0, torg.getEnvironmentalConditions().getMaxAmbientTemperature(), 0.01);
  }

  @Test
  void testSafetyFactors() {
    TechnicalRequirementsDocument.SafetyFactors safety =
        new TechnicalRequirementsDocument.SafetyFactors(1.1, 10.0, 3.0, 0.125, 1.0);

    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-009").safetyFactors(safety).build();

    assertNotNull(torg.getSafetyFactors());
    assertEquals(1.1, torg.getSafetyFactors().getPressureSafetyFactor(), 0.01);
    assertEquals(10.0, torg.getSafetyFactors().getTemperatureSafetyMargin(), 0.01);
    assertEquals(3.0, torg.getSafetyFactors().getCorrosionAllowance(), 0.01);
    assertEquals(0.125, torg.getSafetyFactors().getWallThicknessTolerance(), 0.01);
    assertEquals(1.0, torg.getSafetyFactors().getLoadFactor(), 0.01);
  }

  @Test
  void testMaterialSpecifications() {
    TechnicalRequirementsDocument.MaterialSpecifications materials =
        new TechnicalRequirementsDocument.MaterialSpecifications("A516-70", "A106-B", -46.0, 300.0,
            true, "ASTM");

    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-010").materialSpecifications(materials).build();

    assertNotNull(torg.getMaterialSpecifications());
    assertEquals("A516-70", torg.getMaterialSpecifications().getDefaultPlateMaterial());
    assertEquals("A106-B", torg.getMaterialSpecifications().getDefaultPipeMaterial());
    assertEquals(-46.0, torg.getMaterialSpecifications().getMinDesignTemperature(), 0.01);
    assertEquals(300.0, torg.getMaterialSpecifications().getMaxDesignTemperature(), 0.01);
    assertTrue(torg.getMaterialSpecifications().isImpactTestingRequired());
    assertEquals("ASTM", torg.getMaterialSpecifications().getMaterialStandard());
  }

  @Test
  void testCustomParameters() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-011").customParameter("maxFlowRate", 1000.0)
        .customParameter("fluidType", "Natural Gas").customParameter("corrosive", true).build();

    assertEquals(1000.0, torg.getCustomParameter("maxFlowRate", Double.class), 0.01);
    assertEquals("Natural Gas", torg.getCustomParameter("fluidType", String.class));
    assertEquals(true, torg.getCustomParameter("corrosive", Boolean.class));
  }

  @Test
  void testHasStandardsForCategory() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-012")
            .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1).build();

    assertTrue(torg.hasStandardsForCategory("pressure vessel design code"));
    assertFalse(torg.hasStandardsForCategory("pipeline design codes"));
  }

  @Test
  void testGetDefinedCategories() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-013")
            .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1)
            .addStandard("separator process design", StandardType.API_12J).build();

    java.util.Set<String> categories = torg.getDefinedCategories();
    assertEquals(2, categories.size());
    assertTrue(categories.contains("pressure vessel design code"));
    assertTrue(categories.contains("separator process design"));
  }

  @Test
  void testToString() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("TEST-014").projectName("Test Project").companyIdentifier("TestCo")
        .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1).build();

    String str = torg.toString();
    assertTrue(str.contains("TEST-014"));
    assertTrue(str.contains("Test Project"));
    assertTrue(str.contains("TestCo"));
    assertTrue(str.contains("ASME-VIII-Div1"));
  }

  @Test
  void testEmptyStandardsForCategory() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("TEST-015").build();

    List<StandardType> standards = torg.getStandardsForCategory("nonexistent category");
    assertNotNull(standards);
    assertTrue(standards.isEmpty());
  }
}
