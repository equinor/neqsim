package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.calculation.EquipmentDesignKernel;
import neqsim.process.engineering.calculation.EquipmentDesignKernelRegistry;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/** Tests typed, reproducible, fail-closed standard selection. */
class StandardSelectionTest {
  private MechanicalDesign mechanicalDesign;

  @BeforeEach
  void setUp() {
    mechanicalDesign = new Separator("Test Separator").getMechanicalDesign();
    StandardRegistry.clearVersionOverrides();
  }

  @Test
  void testEditionIsValidatedAndImmutable() {
    List<String> amendments = new ArrayList<String>();
    amendments.add("Project amendment A");

    StandardEdition edition = StandardEdition.of(StandardType.API_12J, " 8th Ed ", amendments);
    amendments.add("Late mutation");

    assertEquals("8th Ed", edition.getEdition());
    assertEquals(Collections.singletonList("Project amendment A"), edition.getAmendments());
    assertThrows(UnsupportedOperationException.class, () -> edition.getAmendments().add("Mutation"));
    assertEquals(StandardEdition.of(StandardType.API_12J, "8th Ed", Collections.singletonList("Project amendment A")),
        edition);
    assertNotEquals(StandardEdition.of(StandardType.API_12J, "9th Ed"), edition);

    assertThrows(IllegalArgumentException.class, () -> StandardEdition.of(null, "8th Ed"));
    assertThrows(IllegalArgumentException.class, () -> StandardEdition.of(StandardType.API_12J, " "));
    assertThrows(IllegalArgumentException.class,
        () -> StandardEdition.of(StandardType.API_12J, "8th Ed", Arrays.asList("valid", " ")));
  }

  @Test
  void testLegacySelectionStoresExplicitEditionAndAmendments() {
    StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed",
        Arrays.asList("Project amendment A", "Corrigendum 1"));

    mechanicalDesign.setDesignStandard(StandardSelection.legacy(edition));

    DesignStandard standard = mechanicalDesign.getDesignStandard().get("separator process design");
    assertEquals("API-12J 8th Ed [amendments: Project amendment A; Corrigendum 1]", standard.getStandardName());
  }

  @Test
  void testExecutableSelectionRejectsUnimplementedAmendments() {
    StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed",
        Collections.singletonList("Project amendment A"));

    StandardSelectionException exception = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireDesignKernel(StandardSelection.historical(edition)));

    assertEquals(StandardSelectionException.Reason.AMENDMENTS_NOT_IMPLEMENTED, exception.getReason());
  }

  @Test
  void testExplicitEditionDoesNotUseGlobalOverride() {
    StandardRegistry.setVersionOverride(StandardType.API_12J, "global override");

    DesignStandard standard = StandardRegistry.createStandard(
        StandardSelection.historical(StandardEdition.of(StandardType.API_12J, "8th Ed")), mechanicalDesign);

    assertEquals("API-12J 8th Ed", standard.getStandardName());
  }

  @Test
  void testApplicabilityIsStructured() {
    StandardApplicability applicable = StandardRegistry.assessApplicability(StandardType.API_12J, mechanicalDesign);
    StandardApplicability notApplicable = StandardRegistry.assessApplicability(StandardType.API_617, mechanicalDesign);
    StandardApplicability unknown = StandardRegistry.assessApplicability(StandardType.API_12J, null);

    assertEquals(StandardApplicability.Status.APPLICABLE, applicable.getStatus());
    assertTrue(applicable.isApplicable());
    assertEquals("Separator", applicable.getEquipmentType());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, notApplicable.getStatus());
    assertFalse(notApplicable.isApplicable());
    assertEquals(StandardApplicability.Status.UNKNOWN, unknown.getStatus());
  }

  @Test
  void testStrictSelectionRejectsStandardWithoutCommonKernel() {
    StandardSelectionException exception = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_660), mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.KERNEL_NOT_IMPLEMENTED, exception.getReason());
    assertEquals(StandardType.API_660, exception.getStandardType());
  }

  @Test
  void testCurrentStrictSelectionRejectsOutdatedApi610Kernel() {
    MechanicalDesign pumpDesign = new Pump("Test Pump").getMechanicalDesign();

    assertEquals(EquipmentDesignKernelRegistry.Status.IMPLEMENTED,
        StandardRegistry.getDesignKernel(StandardType.API_610).getStatus());
    StandardSelectionException currentEdition = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_610), pumpDesign));
    assertEquals(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, currentEdition.getReason());

    DesignStandard historical = StandardRegistry.createStandard(
        StandardSelection.historical(StandardEdition.of(StandardType.API_610, "13th Ed")), pumpDesign);
    assertEquals("API-610 13th Ed", historical.getStandardName());
  }

  @Test
  void testExplicitSelectionCanRequireExactKernelEdition() {
    StandardSelection selection = StandardSelection
        .historical(StandardEdition.of(StandardType.API_610, "13th Ed"));

    EquipmentDesignKernel<?, ?> kernel = StandardRegistry.requireDesignKernel(selection);

    assertEquals(StandardType.API_610, kernel.standard());
    assertEquals("api-610-pump-screening", kernel.getMethod());
    StandardSelectionException missingKernel = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireDesignKernel(StandardSelection.strict(StandardType.API_660)));
    assertEquals(StandardSelectionException.Reason.KERNEL_NOT_IMPLEMENTED, missingKernel.getReason());
    StandardSelectionException oldEdition = assertThrows(StandardSelectionException.class, () -> StandardRegistry
        .requireDesignKernel(StandardSelection.strict(StandardEdition.of(StandardType.API_610, "12th Ed"))));
    assertEquals(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, oldEdition.getReason());
    StandardSelectionException currentApi526 = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireDesignKernel(StandardSelection.strict(StandardType.API_526)));
    assertEquals(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, currentApi526.getReason());
    StandardSelectionException wrongContract = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireDesignKernel(StandardSelection.strictRequirements(StandardType.API_520_PART_1)));
    assertEquals(StandardSelectionException.Reason.EXECUTION_REQUIREMENT_MISMATCH, wrongContract.getReason());
    assertEquals(StandardSelectionException.Reason.MISSING_SELECTION,
        assertThrows(StandardSelectionException.class, () -> StandardRegistry.requireDesignKernel(null)).getReason());
  }

  @Test
  void testStrictSelectionRejectsInapplicableEquipment() {
    StandardSelectionException exception = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(
            StandardSelection.historical(StandardEdition.of(StandardType.API_617, "8th Ed")), mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.NOT_APPLICABLE, exception.getReason());
    assertEquals("Separator", exception.getEquipmentType());
  }

  @Test
  void testStrictSelectionRejectsMissingContext() {
    StandardSelectionException missingEquipment = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_521), null));
    StandardSelectionException missingSelection = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard((StandardSelection) null, mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.MISSING_EQUIPMENT, missingEquipment.getReason());
    assertEquals(StandardSelectionException.Reason.MISSING_SELECTION, missingSelection.getReason());
  }

  @Test
  void testLegacyCompatibleSelectionPreservesPermissiveFactory() {
    DesignStandard standard = StandardRegistry.createStandard(StandardSelection.legacy(StandardType.API_660),
        mechanicalDesign);

    assertEquals(DesignStandard.class, standard.getClass());
    assertEquals("API-660 10th Ed", standard.getStandardName());
  }

  @Test
  void testCurrentSelectionRejectsSupersededAndUnverifiedCatalogEntries() {
    StandardSelectionException superseded = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.NORSOK_P_001), mechanicalDesign));
    StandardSelectionException unverified = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_670), mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.STANDARD_NOT_CURRENT, superseded.getReason());
    assertTrue(superseded.getMessage().contains("NORSOK-P-002"));
    assertEquals(StandardSelectionException.Reason.LIFECYCLE_UNVERIFIED, unverified.getReason());
  }

  @Test
  void testCurrentRequirementPackIsExplicitAndEditionBound() {
    StandardSelection selection = StandardSelection.strictRequirements(StandardType.NORSOK_P_002);

    StandardRequirementPack pack = StandardRegistry.requireRequirementPack(selection);

    assertEquals(StandardEdition.defaultEdition(StandardType.NORSOK_P_002), pack.getEdition());
    assertEquals(3, pack.getCapabilities().size());
    StandardSelectionException missing = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireRequirementPack(
            StandardSelection.strictRequirements(StandardType.ASME_VIII_DIV1)));
    assertEquals(StandardSelectionException.Reason.REQUIREMENT_PACK_NOT_IMPLEMENTED, missing.getReason());
    StandardSelectionException wrongContract = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.requireRequirementPack(StandardSelection.strict(StandardType.API_521)));
    assertEquals(StandardSelectionException.Reason.EXECUTION_REQUIREMENT_MISMATCH, wrongContract.getReason());
  }
}
