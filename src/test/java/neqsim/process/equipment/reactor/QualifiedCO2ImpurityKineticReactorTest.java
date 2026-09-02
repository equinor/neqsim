package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionValidationStatus;

/** Tests the fail-closed evidence gate for the experimental CO2 impurity reactor. */
public class QualifiedCO2ImpurityKineticReactorTest extends NeqSimTest {
  private static final double TEMPERATURE_K = 298.15;
  private static final double PRESSURE_BARA = 100.0;

  @Test
  void testEmptyRegistryFailsClosedWithAllRequiredIds() {
    QualifiedCO2ImpurityKineticReactor reactor = new QualifiedCO2ImpurityKineticReactor("qualified");

    assertArrayEquals(new String[] { "R1", "R2", "R3A", "R3B", "R4", "R5", "R6", "R7", "R8CS" },
        reactor.getRequiredReactionIds());
    assertArrayEquals(reactor.getRequiredReactionIds(),
        reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));
    assertThrows(IllegalStateException.class, () -> reactor.requireValidatedKineticsAt(TEMPERATURE_K, PRESSURE_BARA));
  }

  @Test
  void testCompleteValidatedRegistryPassesDeterministically() {
    QualifiedCO2ImpurityKineticReactor reactor = qualifiedReactor();

    assertArrayEquals(new String[0], reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));
    reactor.requireValidatedKineticsAt(TEMPERATURE_K, PRESSURE_BARA);
    reactor.requireValidatedKineticsAt(TEMPERATURE_K, PRESSURE_BARA);
    assertEquals("doi:10.example/r1", reactor.getReactionQualification("r1").getSourceIdentifier());
  }

  @Test
  void testUnvalidatedOutOfRangeAndMismatchedEvidenceFailClosed() {
    QualifiedCO2ImpurityKineticReactor reactor = qualifiedReactor();
    reactor.setReactionQualification("R2", qualification("R2", ChemicalReactionValidationStatus.UNVALIDATED));

    assertArrayEquals(new String[] { "R2" }, reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));
    assertThrows(IllegalStateException.class, () -> reactor.requireValidatedKineticsAt(TEMPERATURE_K, PRESSURE_BARA));
    assertThrows(IllegalArgumentException.class, () -> reactor.setReactionQualification("R3A",
        qualification("R3B", ChemicalReactionValidationStatus.VALIDATED)));
    assertArrayEquals(reactor.getRequiredReactionIds(), reactor.getUnqualifiedReactionIds(280.0, PRESSURE_BARA));
    assertThrows(IllegalArgumentException.class, () -> reactor.getUnqualifiedReactionIds(Double.NaN, PRESSURE_BARA));
  }

  @Test
  void testChangingConstantsInvalidatesOnlyReplacedParameterization() {
    QualifiedCO2ImpurityKineticReactor reactor = qualifiedReactor();

    reactor.setReactionConstants("R3A", 2.0, 30.0);

    assertNull(reactor.getReactionQualification("R3A"));
    assertArrayEquals(new String[] { "R3A" }, reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));
  }

  @Test
  void testMaterialSelectionUsesOnlySelectedR8Family() {
    QualifiedCO2ImpurityKineticReactor reactor = qualifiedReactor();
    reactor.setMaterial("stainless_steel");

    assertArrayEquals(new String[] { "R1", "R2", "R3A", "R3B", "R4", "R5", "R6", "R7", "R8SS" },
        reactor.getRequiredReactionIds());
    assertArrayEquals(new String[] { "R8SS" }, reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));

    reactor.setReactionQualification("R8", qualification("R8SS", ChemicalReactionValidationStatus.VALIDATED));
    reactor.requireValidatedKineticsAt(TEMPERATURE_K, PRESSURE_BARA);
    reactor.setReactionConstants("R8", 3.0, 40.0);
    assertArrayEquals(new String[] { "R8SS" }, reactor.getUnqualifiedReactionIds(TEMPERATURE_K, PRESSURE_BARA));
  }

  @Test
  void testQualificationReportExplainsFailureReasonsAndPreservesLegacyOrder() {
    QualifiedCO2ImpurityKineticReactor reactor = qualifiedReactor();
    reactor.setReactionQualification("R2", qualification("R2", ChemicalReactionValidationStatus.UNVALIDATED));
    reactor.setReactionConstants("R3A", 2.0, 30.0);

    CO2ImpurityKineticsQualificationReport report =
        reactor.getQualificationReport(312.0, PRESSURE_BARA);

    assertEquals(312.0, report.getTemperatureK());
    assertEquals(PRESSURE_BARA, report.getPressureBara());
    assertEquals("carbon_steel", report.getMaterial());
    assertFalse(report.isQualified());
    assertEquals(CO2ImpurityKineticsQualificationReport.QualificationState.OUT_OF_RANGE,
        report.getEntries().get(0).getState());
    assertEquals(CO2ImpurityKineticsQualificationReport.QualificationState.NOT_VALIDATED,
        report.getEntries().get(1).getState());
    assertEquals(CO2ImpurityKineticsQualificationReport.QualificationState.MISSING,
        report.getEntries().get(2).getState());
    assertEquals("R8CS", report.getEntries().get(8).getReactionId());
    assertArrayEquals(reactor.getUnqualifiedReactionIds(312.0, PRESSURE_BARA),
        report.getBlockedReactionIds());
    assertThrows(UnsupportedOperationException.class, () -> report.getEntries().add(null));

    String[] blocked = report.getBlockedReactionIds();
    blocked[0] = "changed";
    assertEquals("R1", report.getBlockedReactionIds()[0]);
    assertTrue(qualifiedReactor().getQualificationReport(TEMPERATURE_K, PRESSURE_BARA).isQualified());
  }

  @Test
  void testRegistryInputValidation() {
    QualifiedCO2ImpurityKineticReactor reactor = new QualifiedCO2ImpurityKineticReactor("qualified");

    assertThrows(IllegalArgumentException.class,
        () -> reactor.setReactionQualification(null, qualification("R1", ChemicalReactionValidationStatus.VALIDATED)));
    assertThrows(IllegalArgumentException.class, () -> reactor.setReactionQualification("R1", null));
    assertThrows(IllegalArgumentException.class, () -> reactor.getReactionQualification("R9"));
  }

  private static QualifiedCO2ImpurityKineticReactor qualifiedReactor() {
    QualifiedCO2ImpurityKineticReactor reactor = new QualifiedCO2ImpurityKineticReactor("qualified");
    for (String reactionId : reactor.getRequiredReactionIds()) {
      reactor.setReactionQualification(reactionId,
          qualification(reactionId, ChemicalReactionValidationStatus.VALIDATED));
    }
    return reactor;
  }

  private static KineticReactionQualification qualification(String reactionId,
      ChemicalReactionValidationStatus validationStatus) {
    return new KineticReactionQualification(reactionId, "Public primary source for " + reactionId,
        "doi:10.example/" + reactionId.toLowerCase(), validationStatus, 288.15, 310.15, 1.0, 200.0,
        "Test-only evidence boundary");
  }
}
