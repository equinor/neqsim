package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for StandardType enum.
 */
class StandardTypeTest {

  @Test
  void testGetCode() {
    assertEquals("NORSOK-L-001", StandardType.NORSOK_L_001.getCode());
    assertEquals("ASME-VIII-Div1", StandardType.ASME_VIII_DIV1.getCode());
    assertEquals("API-617", StandardType.API_617.getCode());
    assertEquals("DNV-ST-F101", StandardType.DNV_ST_F101.getCode());
  }

  @Test
  void testGetName() {
    assertEquals("Pipeline systems", StandardType.NORSOK_L_001.getName());
    assertEquals("Pressure Vessels Division 1", StandardType.ASME_VIII_DIV1.getName());
  }

  @Test
  void testGetDefaultVersion() {
    assertEquals("Rev 6", StandardType.NORSOK_L_001.getDefaultVersion());
    assertEquals("2021", StandardType.ASME_VIII_DIV1.getDefaultVersion());
    assertEquals("8th Ed", StandardType.API_617.getDefaultVersion());
  }

  @Test
  void testGetDesignStandardCategory() {
    assertEquals("pipeline design codes", StandardType.NORSOK_L_001.getDesignStandardCategory());
    assertEquals("pressure vessel design code",
        StandardType.ASME_VIII_DIV1.getDesignStandardCategory());
    assertEquals("separator process design", StandardType.API_12J.getDesignStandardCategory());
    assertEquals("compressor design codes", StandardType.API_617.getDesignStandardCategory());
  }

  @Test
  void testAppliesTo() {
    assertTrue(StandardType.NORSOK_L_001.appliesTo("Pipeline"));
    assertTrue(StandardType.NORSOK_L_001.appliesTo("pipeline")); // Case insensitive
    assertTrue(StandardType.NORSOK_L_001.appliesTo("AdiabaticPipe"));
    assertFalse(StandardType.NORSOK_L_001.appliesTo("Compressor"));
    assertFalse(StandardType.NORSOK_L_001.appliesTo(null));

    assertTrue(StandardType.ASME_VIII_DIV1.appliesTo("Separator"));
    assertTrue(StandardType.ASME_VIII_DIV1.appliesTo("ThreePhaseSeparator"));
    assertTrue(StandardType.ASME_VIII_DIV1.appliesTo("GasScrubber"));
    assertFalse(StandardType.ASME_VIII_DIV1.appliesTo("Pipeline"));

    assertTrue(StandardType.API_617.appliesTo("Compressor"));
    assertFalse(StandardType.API_617.appliesTo("Pump"));
  }

  @Test
  void testFromCode() {
    assertEquals(StandardType.NORSOK_L_001, StandardType.fromCode("NORSOK-L-001"));
    assertEquals(StandardType.NORSOK_L_001, StandardType.fromCode("norsok-l-001")); // Case
                                                                                    // insensitive
    assertEquals(StandardType.ASME_VIII_DIV1, StandardType.fromCode("ASME-VIII-Div1"));
    assertEquals(StandardType.API_617, StandardType.fromCode("API-617"));
    assertNull(StandardType.fromCode("INVALID-CODE"));
    assertNull(StandardType.fromCode(null));
  }

  @Test
  void testGetApplicableStandards() {
    List<StandardType> pipelineStandards = StandardType.getApplicableStandards("Pipeline");
    assertFalse(pipelineStandards.isEmpty());
    assertTrue(pipelineStandards.contains(StandardType.NORSOK_L_001));
    assertTrue(pipelineStandards.contains(StandardType.DNV_ST_F101));
    assertTrue(pipelineStandards.contains(StandardType.ASME_B31_8));

    List<StandardType> separatorStandards = StandardType.getApplicableStandards("Separator");
    assertFalse(separatorStandards.isEmpty());
    assertTrue(separatorStandards.contains(StandardType.ASME_VIII_DIV1));
    assertTrue(separatorStandards.contains(StandardType.API_12J));
    assertTrue(separatorStandards.contains(StandardType.NORSOK_P_001));

    List<StandardType> compressorStandards = StandardType.getApplicableStandards("Compressor");
    assertFalse(compressorStandards.isEmpty());
    assertTrue(compressorStandards.contains(StandardType.API_617));
  }

  @Test
  void testGetNorsokStandards() {
    List<StandardType> norsokStandards = StandardType.getNorsokStandards();
    assertFalse(norsokStandards.isEmpty());
    assertTrue(norsokStandards.contains(StandardType.NORSOK_L_001));
    assertTrue(norsokStandards.contains(StandardType.NORSOK_P_001));
    assertFalse(norsokStandards.contains(StandardType.ASME_VIII_DIV1));
  }

  @Test
  void testGetAsmeStandards() {
    List<StandardType> asmeStandards = StandardType.getAsmeStandards();
    assertFalse(asmeStandards.isEmpty());
    assertTrue(asmeStandards.contains(StandardType.ASME_VIII_DIV1));
    assertTrue(asmeStandards.contains(StandardType.ASME_B31_3));
    assertFalse(asmeStandards.contains(StandardType.API_617));
  }

  @Test
  void testGetApiStandards() {
    List<StandardType> apiStandards = StandardType.getApiStandards();
    assertFalse(apiStandards.isEmpty());
    assertTrue(apiStandards.contains(StandardType.API_617));
    assertTrue(apiStandards.contains(StandardType.API_650));
    assertFalse(apiStandards.contains(StandardType.NORSOK_L_001));
  }

  @Test
  void testGetDnvStandards() {
    List<StandardType> dnvStandards = StandardType.getDnvStandards();
    assertFalse(dnvStandards.isEmpty());
    assertTrue(dnvStandards.contains(StandardType.DNV_ST_F101));
    assertFalse(dnvStandards.contains(StandardType.API_617));
  }

  @Test
  void testGetIsoStandards() {
    List<StandardType> isoStandards = StandardType.getIsoStandards();
    assertFalse(isoStandards.isEmpty());
    assertTrue(isoStandards.contains(StandardType.ISO_13623));
    assertFalse(isoStandards.contains(StandardType.API_617));
  }

  @Test
  void testGetAstmStandards() {
    List<StandardType> astmStandards = StandardType.getAstmStandards();
    assertFalse(astmStandards.isEmpty());
    assertTrue(astmStandards.contains(StandardType.ASTM_A516));
    assertTrue(astmStandards.contains(StandardType.ASTM_A106));
    assertFalse(astmStandards.contains(StandardType.NORSOK_L_001));
  }

  @Test
  void testGetByCategory() {
    List<StandardType> pvStandards = StandardType.getByCategory("pressure vessel design code");
    assertFalse(pvStandards.isEmpty());
    assertTrue(pvStandards.contains(StandardType.ASME_VIII_DIV1));
    assertTrue(pvStandards.contains(StandardType.EN_13445));

    List<StandardType> pipelineStandards = StandardType.getByCategory("pipeline design codes");
    assertFalse(pipelineStandards.isEmpty());
    assertTrue(pipelineStandards.contains(StandardType.NORSOK_L_001));
    assertTrue(pipelineStandards.contains(StandardType.DNV_ST_F101));
  }

  @Test
  void testGetAllCategories() {
    List<String> categories = StandardType.getAllCategories();
    assertFalse(categories.isEmpty());
    assertTrue(categories.contains("pressure vessel design code"));
    assertTrue(categories.contains("pipeline design codes"));
    assertTrue(categories.contains("separator process design"));
    assertTrue(categories.contains("compressor design codes"));
  }

  @Test
  void testToString() {
    String str = StandardType.ASME_VIII_DIV1.toString();
    assertNotNull(str);
    assertTrue(str.contains("ASME-VIII-Div1"));
    assertTrue(str.contains("2021"));
  }
}
