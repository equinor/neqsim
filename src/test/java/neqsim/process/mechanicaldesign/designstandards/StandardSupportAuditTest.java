package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the published implementation evidence for catalogued design standards. */
class StandardSupportAuditTest {
  private static final String MATRIX_BEGIN = "<!-- BEGIN GENERATED STANDARD SUPPORT MATRIX -->";
  private static final String MATRIX_END = "<!-- END GENERATED STANDARD SUPPORT MATRIX -->";

  @Test
  void testEveryStandardHasSupportInformation() {
    List<StandardSupport> support = StandardSupportAudit.getAllSupport();

    assertEquals(StandardType.values().length, support.size());
    for (int index = 0; index < StandardType.values().length; index++) {
      assertEquals(StandardType.values()[index], support.get(index).getStandardType());
    }
  }

  @Test
  void testEvidenceLevelsExposeCurrentImplementationBoundary() {
    StandardSupport pumpSupport = StandardSupportAudit.getSupport(StandardType.API_610);
    assertEquals(StandardSupportLevel.SCREENING, pumpSupport.getSupportLevel());
    assertEquals("DesignStandard", pumpSupport.getRegistryImplementation());
    assertEquals("PumpApi610DesignCalculator", pumpSupport.getCalculationImplementation());

    assertEquals(StandardSupportLevel.CATALOGUED,
        StandardSupportAudit.getSupport(StandardType.API_660).getSupportLevel());
    assertEquals(StandardSupportLevel.CATALOGUED,
        StandardSupportAudit.getSupport(StandardType.API_650).getSupportLevel());
    assertEquals(StandardSupportLevel.CATALOGUED,
        StandardSupportAudit.getSupport(StandardType.API_521).getSupportLevel());
    assertEquals(StandardSupportLevel.SCREENING,
        StandardSupportAudit.getSupport(StandardType.ASME_VIII_DIV1).getSupportLevel());
    assertEquals(StandardSupportLevel.SCREENING,
        StandardSupportAudit.getSupport(StandardType.API_12J).getSupportLevel());
  }

  @Test
  void testFactoryMappingCanBeInspectedWithoutCreatingStandard() {
    assertEquals(PressureVesselDesignStandard.class,
        StandardRegistry.getMappedImplementationClass(StandardType.ASME_VIII_DIV1));
    assertEquals(SeparatorDesignStandard.class, StandardRegistry.getMappedImplementationClass(StandardType.API_12J));
    assertEquals(DesignStandard.class, StandardRegistry.getMappedImplementationClass(StandardType.API_610));
    assertEquals(DesignStandard.class, StandardRegistry.getMappedImplementationClass(StandardType.API_660));
    assertThrows(IllegalArgumentException.class, () -> StandardRegistry.getMappedImplementationClass(null));
  }

  @Test
  void testNullStandardIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> StandardSupportAudit.getSupport(null));
  }

  @Test
  void testGeneratedMatrixContainsEveryStandard() {
    String table = StandardSupportAudit.generateMarkdownTable();

    for (StandardType standardType : StandardType.values()) {
      assertTrue(table.contains("| " + standardType.getCode() + " |"));
    }
  }

  @Test
  void testPublishedMatrixMatchesGeneratedAudit() throws IOException {
    Path documentation = Paths.get("docs", "process", "mechanical_design_standards.md");
    String contents = new String(Files.readAllBytes(documentation), StandardCharsets.UTF_8);
    int begin = contents.indexOf(MATRIX_BEGIN);
    int end = contents.indexOf(MATRIX_END);

    assertTrue(begin >= 0, "Generated matrix start marker is missing");
    assertTrue(end > begin, "Generated matrix end marker is missing");

    String published = contents.substring(begin + MATRIX_BEGIN.length(), end).trim();
    assertEquals(StandardSupportAudit.generateMarkdownTable().trim(), published);
  }
}
