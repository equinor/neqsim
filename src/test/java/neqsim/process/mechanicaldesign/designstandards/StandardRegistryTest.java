package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Tests for StandardRegistry class.
 */
class StandardRegistryTest {
  private MechanicalDesign mechanicalDesign;

  @BeforeEach
  void setUp() {
    Separator separator = new Separator("Test Separator");
    mechanicalDesign = separator.getMechanicalDesign();

    // Clear any version overrides between tests
    StandardRegistry.clearVersionOverrides();
  }

  @Test
  void testCreateStandardWithType() {
    DesignStandard standard =
        StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, mechanicalDesign);

    assertNotNull(standard);
    assertTrue(standard instanceof PressureVesselDesignStandard);
    assertTrue(standard.getStandardName().contains("ASME-VIII-Div1"));
  }

  @Test
  void testCreateStandardWithVersion() {
    DesignStandard standard =
        StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, "2019", mechanicalDesign);

    assertNotNull(standard);
    assertTrue(standard.getStandardName().contains("2019"));
  }

  @Test
  void testCreateStandardThrowsOnNull() {
    assertThrows(IllegalArgumentException.class,
        () -> StandardRegistry.createStandard(null, mechanicalDesign));
  }

  @Test
  void testCreateDifferentStandardTypes() {
    // Pressure vessel
    DesignStandard pvStandard =
        StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, mechanicalDesign);
    assertTrue(pvStandard instanceof PressureVesselDesignStandard);

    // Separator
    DesignStandard sepStandard =
        StandardRegistry.createStandard(StandardType.API_12J, mechanicalDesign);
    assertTrue(sepStandard instanceof SeparatorDesignStandard);

    // Pipeline
    DesignStandard pipeStandard =
        StandardRegistry.createStandard(StandardType.NORSOK_L_001, mechanicalDesign);
    assertTrue(pipeStandard instanceof PipelineDesignStandard);

    // Compressor
    DesignStandard compStandard =
        StandardRegistry.createStandard(StandardType.API_617, mechanicalDesign);
    assertTrue(compStandard instanceof CompressorDesignStandard);
  }

  @Test
  void testVersionOverride() {
    // Set override
    StandardRegistry.setVersionOverride(StandardType.ASME_VIII_DIV1, "2019");
    assertEquals("2019", StandardRegistry.getEffectiveVersion(StandardType.ASME_VIII_DIV1));

    // Create standard uses override
    DesignStandard standard =
        StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, mechanicalDesign);
    assertTrue(standard.getStandardName().contains("2019"));

    // Clear override
    StandardRegistry.setVersionOverride(StandardType.ASME_VIII_DIV1, null);
    assertEquals("2021", StandardRegistry.getEffectiveVersion(StandardType.ASME_VIII_DIV1));
  }

  @Test
  void testClearVersionOverrides() {
    StandardRegistry.setVersionOverride(StandardType.ASME_VIII_DIV1, "2019");
    StandardRegistry.setVersionOverride(StandardType.API_617, "7th Ed");

    StandardRegistry.clearVersionOverrides();

    assertEquals("2021", StandardRegistry.getEffectiveVersion(StandardType.ASME_VIII_DIV1));
    assertEquals("8th Ed", StandardRegistry.getEffectiveVersion(StandardType.API_617));
  }

  @Test
  void testGetApplicableStandards() {
    List<StandardType> standards = StandardRegistry.getApplicableStandards("Separator");

    assertFalse(standards.isEmpty());
    assertTrue(standards.contains(StandardType.ASME_VIII_DIV1));
    assertTrue(standards.contains(StandardType.API_12J));
  }

  @Test
  void testGetStandardsByOrganization() {
    List<StandardType> norsokStandards = StandardRegistry.getStandardsByOrganization("NORSOK");
    assertFalse(norsokStandards.isEmpty());
    assertTrue(norsokStandards.contains(StandardType.NORSOK_L_001));

    List<StandardType> asmeStandards = StandardRegistry.getStandardsByOrganization("ASME");
    assertFalse(asmeStandards.isEmpty());
    assertTrue(asmeStandards.contains(StandardType.ASME_VIII_DIV1));

    List<StandardType> apiStandards = StandardRegistry.getStandardsByOrganization("API");
    assertFalse(apiStandards.isEmpty());
    assertTrue(apiStandards.contains(StandardType.API_617));
  }

  @Test
  void testGetStandardsByCategory() {
    List<StandardType> pvStandards =
        StandardRegistry.getStandardsByCategory("pressure vessel design code");
    assertFalse(pvStandards.isEmpty());
    assertTrue(pvStandards.contains(StandardType.ASME_VIII_DIV1));
  }

  @Test
  void testFindByCode() {
    assertEquals(StandardType.ASME_VIII_DIV1, StandardRegistry.findByCode("ASME-VIII-Div1"));
    assertEquals(StandardType.NORSOK_L_001, StandardRegistry.findByCode("NORSOK-L-001"));
  }

  @Test
  void testGetAllCategories() {
    List<String> categories = StandardRegistry.getAllCategories();
    assertFalse(categories.isEmpty());
    assertTrue(categories.contains("pressure vessel design code"));
    assertTrue(categories.contains("pipeline design codes"));
  }

  @Test
  void testGetAllStandards() {
    StandardType[] allStandards = StandardRegistry.getAllStandards();
    assertNotNull(allStandards);
    assertTrue(allStandards.length > 0);
  }

  @Test
  void testIsApplicable() {
    assertTrue(StandardRegistry.isApplicable(StandardType.ASME_VIII_DIV1, "Separator"));
    assertFalse(StandardRegistry.isApplicable(StandardType.API_617, "Separator"));
    assertTrue(StandardRegistry.isApplicable(StandardType.API_617, "Compressor"));
    assertFalse(StandardRegistry.isApplicable(null, "Separator"));
  }

  @Test
  void testGetRecommendedStandards() {
    Map<String, List<StandardType>> recommended =
        StandardRegistry.getRecommendedStandards("Separator");

    assertFalse(recommended.isEmpty());
    assertTrue(recommended.containsKey("pressure vessel design code"));
    assertTrue(recommended.containsKey("separator process design"));
  }

  @Test
  void testGetSummary() {
    String summary = StandardRegistry.getSummary();

    assertNotNull(summary);
    assertTrue(summary.contains("NORSOK"));
    assertTrue(summary.contains("ASME"));
    assertTrue(summary.contains("API"));
    assertTrue(summary.contains("DNV"));
  }
}
