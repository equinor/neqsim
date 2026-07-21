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
  void testStrictSelectionStoresExplicitEditionAndAmendments() {
    StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed",
        Arrays.asList("Project amendment A", "Corrigendum 1"));

    mechanicalDesign.setDesignStandard(StandardSelection.strict(edition));

    DesignStandard standard = mechanicalDesign.getDesignStandard().get("separator process design");
    assertEquals("API-12J 8th Ed [amendments: Project amendment A; Corrigendum 1]", standard.getStandardName());
  }

  @Test
  void testExplicitEditionDoesNotUseGlobalOverride() {
    StandardRegistry.setVersionOverride(StandardType.API_12J, "global override");

    DesignStandard standard = StandardRegistry.createStandard(
        StandardSelection.strict(StandardEdition.of(StandardType.API_12J, "project edition")), mechanicalDesign);

    assertEquals("API-12J project edition", standard.getStandardName());
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
  void testStrictSelectionRejectsCatalogOnlyStandard() {
    StandardSelectionException exception = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_660), mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.CATALOG_ONLY, exception.getReason());
    assertEquals(StandardType.API_660, exception.getStandardType());
  }

  @Test
  void testStrictSelectionUsesConsolidatedApi610Kernel() {
    MechanicalDesign pumpDesign = new Pump("Test Pump").getMechanicalDesign();

    DesignStandard standard =
        StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_610), pumpDesign);

    assertEquals("API-610 13th Ed", standard.getStandardName());
    assertEquals(EquipmentDesignKernelRegistry.Status.IMPLEMENTED,
        StandardRegistry.getDesignKernel(StandardType.API_610).getStatus());

    StandardSelectionException oldEdition = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(
            StandardSelection.strict(StandardEdition.of(StandardType.API_610, "12th Ed")), pumpDesign));
    assertEquals(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, oldEdition.getReason());
  }

  @Test
  void testStrictSelectionRejectsInapplicableEquipment() {
    StandardSelectionException exception = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_617), mechanicalDesign));

    assertEquals(StandardSelectionException.Reason.NOT_APPLICABLE, exception.getReason());
    assertEquals("Separator", exception.getEquipmentType());
  }

  @Test
  void testStrictSelectionRejectsMissingContext() {
    StandardSelectionException missingEquipment = assertThrows(StandardSelectionException.class,
        () -> StandardRegistry.createStandard(StandardSelection.strict(StandardType.API_12J), null));
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
    assertEquals("API-660 9th Ed", standard.getStandardName());
  }
}
