package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.util.Scanner;
import org.junit.jupiter.api.Test;

/** Tests publisher provenance and cross-equipment requirement-pack integrity. */
class StandardCatalogTest {
  @Test
  void everyStandardHasAnExplicitLifecycleRecord() {
    assertEquals(StandardType.values().length, StandardCatalog.getAll().size());
    for (StandardType standardType : StandardType.values()) {
      StandardCatalogEntry entry = StandardCatalog.get(standardType);
      assertNotNull(entry);
      assertEquals(standardType, entry.getStandardType());
      if (entry.getLifecycleStatus() == StandardLifecycleStatus.CURRENT) {
        assertFalse(entry.getPublisherSourceUrl().isEmpty());
        assertFalse(entry.getVerifiedOn().isEmpty());
        assertTrue(entry.isCurrentEdition(StandardEdition.defaultEdition(standardType)));
      }
    }
  }

  @Test
  void supersededNorsokProcessStandardNamesItsReplacement() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.NORSOK_P_001);

    assertEquals(StandardLifecycleStatus.SUPERSEDED, entry.getLifecycleStatus());
    assertEquals(StandardType.NORSOK_P_002, entry.getSupersededBy());
    assertFalse(entry.isCurrentEdition(StandardEdition.defaultEdition(StandardType.NORSOK_P_001)));
  }

  @Test
  void currentApi526EditionDoesNotRelabelHistoricalKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.API_526);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("8th Ed", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("2025-catalog"));
    assertFalse(StandardRegistry.getDesignKernel(StandardType.API_526)
        .supports(StandardEdition.defaultEdition(StandardType.API_526)));
    assertTrue(StandardRegistry.getDesignKernel(StandardType.API_526)
        .supports(StandardEdition.of(StandardType.API_526, "7th Ed")));
  }

  @Test
  void currentNorsokM506EditionHasPublisherReviewEvidenceAndExecutableKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.NORSOK_M_506);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2017", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("systematic-review"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.NORSOK_M_506)
        .supports(StandardEdition.defaultEdition(StandardType.NORSOK_M_506)));
  }

  @Test
  void currentIso5167PartsHavePublisherEvidenceAndPart2Kernel() {
    StandardCatalogEntry general = StandardCatalog.get(StandardType.ISO_5167_1);
    StandardCatalogEntry orifice = StandardCatalog.get(StandardType.ISO_5167_2);

    assertEquals(StandardLifecycleStatus.CURRENT, general.getLifecycleStatus());
    assertEquals(StandardLifecycleStatus.CURRENT, orifice.getLifecycleStatus());
    assertEquals("2022", general.getStandardType().getDefaultVersion());
    assertEquals("2022", orifice.getStandardType().getDefaultVersion());
    assertTrue(general.getPublisherSourceUrl().contains("79179"));
    assertTrue(orifice.getPublisherSourceUrl().contains("79180"));
    assertEquals("2026-08-02", general.getVerifiedOn());
    assertEquals("2026-08-02", orifice.getVerifiedOn());
    assertFalse(StandardRegistry.getDesignKernel(StandardType.ISO_5167_1).isImplemented());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.ISO_5167_2)
        .supports(StandardEdition.defaultEdition(StandardType.ISO_5167_2)));
  }

  @Test
  void currentDnvRpC203EditionHasPublisherEvidenceAndExecutableKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_C203);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2024-10+AMD:2025-10", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-c203"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_C203)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_C203)));
  }

  @Test
  void currentDnvRpF105EditionHasPublisherEvidenceAndExecutableKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_F105);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2025-12", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-f105"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_F105)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F105)));
  }

  @Test
  void currentDnvRpF101EditionHasPublisherEvidenceAndExecutableKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_F101);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2019-09+AMD:2025-09", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-f101"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_F101)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F101)));
  }

  @Test
  void currentApi2000EditionHasPublisherEvidenceAndExecutableKernel() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.API_2000);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("7th Ed", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("2025-catalog"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.API_2000)
        .supports(StandardEdition.defaultEdition(StandardType.API_2000)));
  }

  @Test
  void currentDnvRpF104EditionHasPublisherEvidenceExecutableKernelAndRequirementPack() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_F104);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2021-02+AMD:2021-09", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-f104"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_F104)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F104)));
    assertEquals(6,
        StandardRequirementPackRegistry.lookup(StandardType.DNV_RP_F104).requirePack().getCapabilities().size());
  }

  @Test
  void currentDnvRpF114EditionHasPublisherEvidenceExecutableKernelAndRequirementPack() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_F114);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2021-05", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-f114"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_F114)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F114)));
    assertEquals(4,
        StandardRequirementPackRegistry.lookup(StandardType.DNV_RP_F114).requirePack().getCapabilities().size());
  }

  @Test
  void currentDnvRpF110EditionHasPublisherEvidenceExecutableKernelAndRequirementPack() {
    StandardCatalogEntry entry = StandardCatalog.get(StandardType.DNV_RP_F110);

    assertEquals(StandardLifecycleStatus.CURRENT, entry.getLifecycleStatus());
    assertEquals("2019-09+AMD:2021-09", entry.getStandardType().getDefaultVersion());
    assertTrue(entry.getPublisherSourceUrl().contains("dnv-rp-f110"));
    assertEquals("2026-08-02", entry.getVerifiedOn());
    assertTrue(StandardRegistry.getDesignKernel(StandardType.DNV_RP_F110)
        .supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F110)));
    assertEquals(4,
        StandardRequirementPackRegistry.lookup(StandardType.DNV_RP_F110).requirePack().getCapabilities().size());
  }

  @Test
  void f105ResourceCatalogDoesNotExposeLegacyPseudoCriteriaAsCurrent() {
    String index = resourceText("/designdata/standards/standards_index.csv");
    String values = resourceText("/designdata/standards/dnv_iso_en_standards.csv");
    String processRequirements = resourceText("/designdata/TechnicalRequirements_Process.csv");

    assertTrue(index.contains("\"DNV-RP-F105\",\"2025-12\",\"Free Spanning Pipelines\""));
    assertFalse(values.contains("\"DNV-RP-F105\""));
    assertFalse(processRequirements.contains("\"DNV-RP-F105\",\"Surge pressure allowance\""));
  }

  @Test
  void c203ResourceCatalogUsesTheCurrentEnumEdition() {
    String edition = StandardType.DNV_RP_C203.getDefaultVersion();
    String index = resourceText("/designdata/standards/standards_index.csv");
    String values = resourceText("/designdata/standards/dnv_iso_en_standards.csv");

    assertTrue(index.contains("\"DNV-RP-C203\",\"" + edition + "\""));
    assertTrue(values.contains("\"DNV-RP-C203\",\"" + edition + "\""));
    assertFalse(index.contains("\"DNV-RP-C203\",\"2021\""));
    assertFalse(values.contains("\"DNV-RP-C203\",\"2021\""));
  }

  @Test
  void requirementPacksReferenceLoadableCapabilitiesAndCurrentEditions() throws Exception {
    StandardType[] packedStandards = { StandardType.NORSOK_P_002, StandardType.NORSOK_S_001, StandardType.ISO_10418,
        StandardType.IEC_61511, StandardType.API_520_PART_1, StandardType.NORSOK_M_001, StandardType.API_650,
        StandardType.API_660, StandardType.DNV_ST_F101, StandardType.DNV_RP_F104, StandardType.DNV_RP_F110,
        StandardType.DNV_RP_F114 };

    for (StandardType standardType : packedStandards) {
      StandardRequirementPack pack = StandardRequirementPackRegistry.lookup(standardType).requirePack();
      assertEquals(StandardEdition.defaultEdition(standardType), pack.getEdition());
      assertEquals(StandardLifecycleStatus.CURRENT, StandardCatalog.get(standardType).getLifecycleStatus());
      for (StandardRequirementCapability capability : pack.getCapabilities()) {
        assertNotNull(Class.forName(capability.getImplementationClassName()));
        assertFalse(capability.getBoundary().isEmpty());
      }
    }
  }

  @Test
  void missingRequirementPackIsExplicit() {
    StandardRequirementPackRegistry.Lookup lookup = StandardRequirementPackRegistry.lookup(StandardType.API_614);

    assertFalse(lookup.isImplemented());
    assertThrows(IllegalStateException.class, lookup::requirePack);
    assertThrows(IllegalArgumentException.class, () -> StandardRequirementPackRegistry.lookup(null));
  }

  private static String resourceText(String resourcePath) {
    InputStream stream = StandardCatalogTest.class.getResourceAsStream(resourcePath);
    assertNotNull(stream);
    try (Scanner scanner = new Scanner(stream, "UTF-8")) {
      scanner.useDelimiter("\\A");
      return scanner.hasNext() ? scanner.next() : "";
    }
  }
}
