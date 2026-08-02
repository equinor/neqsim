package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
  void requirementPacksReferenceLoadableCapabilitiesAndCurrentEditions() throws Exception {
    StandardType[] packedStandards = { StandardType.NORSOK_P_002, StandardType.NORSOK_S_001, StandardType.ISO_10418,
        StandardType.IEC_61511, StandardType.API_520_PART_1, StandardType.NORSOK_M_001, StandardType.API_650,
        StandardType.API_660, StandardType.DNV_ST_F101 };

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
}
