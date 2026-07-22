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
