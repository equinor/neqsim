package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemKentEisenberg;
import neqsim.thermo.system.SystemPitzer;

/** Tests model-to-model reaction set and parameter audit diagnostics. */
class ChemicalReactionModelAuditTest {
  /** Electrolyte CPA and Pitzer expose their distinct reaction standard states. */
  @Test
  void electrolyteEosAndGeReportDedicatedReactionSources() {
    SystemInterface cpa = reactiveCo2WaterSystem(new SystemElectrolyteCPAstatoil(298.15, 1.01325));
    SystemInterface pitzer = reactiveCo2WaterSystem(new SystemPitzer(298.15, 1.01325));

    ChemicalReactionModelAudit.AuditSnapshot cpaAudit = ChemicalReactionModelAudit.inspect(cpa);
    ChemicalReactionModelAudit.AuditSnapshot pitzerAudit = ChemicalReactionModelAudit.inspect(pitzer);
    ChemicalReactionModelAudit.AuditComparison comparison = ChemicalReactionModelAudit.compare(cpaAudit, pitzerAudit);

    assertNotEquals(cpaAudit.getModelName(), pitzerAudit.getModelName());
    assertTrue(cpaAudit.getReactionCount() > 0);
    assertTrue(pitzerAudit.getReactionCount() > 0);
    assertTrue(pitzerAudit.hasValidatedEvidenceForAllActiveReactions());
    assertTrue(pitzerAudit.getReactionsWithoutValidatedEvidence().isEmpty());
    assertFalse(cpaAudit.hasValidatedEvidenceForAllActiveReactions());
    assertTrue(cpaAudit.getReactionsWithoutValidatedEvidence().contains("CO2water"));
    assertFalse(comparison.hasSameReactionDataSource());
    assertFalse(comparison.hasSameReactionConcentrationBasis());
    assertTrue(comparison.getReactionsOnlyInFirst().isEmpty());
    assertTrue(comparison.getReactionsOnlyInSecond().isEmpty());
    assertTrue(comparison.getParameterDifferences().contains("CO2water"));
    assertTrue(comparison.getParameterDifferences().contains("carbonate"));
    assertTrue(comparison.getParameterDifferences().contains("waterreac"));
    assertFalse(comparison.isEquivalent());
    assertNotNull(findReaction(cpaAudit, "CO2water"));
    assertEquals(ChemicalReactionValidationStatus.VALIDATED,
        findReaction(pitzerAudit, "CO2water").getValidationStatus());
    assertEquals(ChemicalReactionValidationStatus.UNSPECIFIED,
        findReaction(cpaAudit, "CO2water").getValidationStatus());
  }

  /** Pitzer activates only the independently supported first sulfide dissociation. */
  @Test
  void pitzerReportsValidatedFirstSulfideDissociationOnly() {
    SystemInterface pitzer = new SystemPitzer(298.15, 1.01325);
    pitzer.addComponent("H2S", 0.01);
    pitzer.addComponent("water", 0.99);
    pitzer.chemicalReactionInit();

    ChemicalReactionModelAudit.AuditSnapshot audit = ChemicalReactionModelAudit.inspect(pitzer);

    assertTrue(audit.hasValidatedEvidenceForAllActiveReactions());
    assertTrue(audit.getReactionsWithoutValidatedEvidence().isEmpty());
    assertEquals(ChemicalReactionValidationStatus.VALIDATED, findReaction(audit, "water-H2S").getValidationStatus());
    assertNull(findReaction(audit, "water-HS"));
    assertThrows(UnsupportedOperationException.class, () -> audit.getReactionsWithoutValidatedEvidence().clear());
  }

  /** A dedicated Kent-Eisenberg source is reported as a parameter/source difference. */
  @Test
  void dedicatedApparentConstantSourceDoesNotCompareEquivalentToStandard() {
    SystemInterface cpa = reactiveCo2WaterSystem(new SystemElectrolyteCPAstatoil(298.15, 1.01325));
    SystemInterface kent = reactiveCo2WaterSystem(new SystemKentEisenberg(298.15, 1.01325));

    ChemicalReactionModelAudit.AuditComparison comparison = ChemicalReactionModelAudit.compare(cpa, kent);

    assertFalse(comparison.hasSameReactionDataSource());
    assertFalse(comparison.isEquivalent());
    assertTrue(comparison.getParameterDifferences().contains("CO2water"));
  }

  /** Returned audit arrays and lists cannot mutate the underlying reaction data. */
  @Test
  void auditSnapshotsAreDefensiveAndImmutable() {
    SystemInterface cpa = reactiveCo2WaterSystem(new SystemElectrolyteCPAstatoil(298.15, 1.01325));
    ChemicalReactionModelAudit.AuditSnapshot audit = ChemicalReactionModelAudit.inspect(cpa);
    ChemicalReactionModelAudit.ReactionParameterSnapshot reaction = findReaction(audit, "CO2water");
    assertNotNull(reaction);

    double[] original = reaction.getEquilibriumConstantCoefficients();
    double[] modified = reaction.getEquilibriumConstantCoefficients();
    modified[0] = -123.0;
    assertArrayEquals(original, reaction.getEquilibriumConstantCoefficients(), 0.0);
    assertNotSame(audit.getReactions(), audit.getReactions());
    assertThrows(UnsupportedOperationException.class, () -> audit.getReactions().clear());
  }

  /** Audit deliberately refuses to mutate an uninitialized system. */
  @Test
  void auditRequiresExplicitReactionInitialization() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 0.99);

    assertThrows(IllegalStateException.class, () -> ChemicalReactionModelAudit.inspect(system));
  }

  private static SystemInterface reactiveCo2WaterSystem(SystemInterface system) {
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 0.99);
    system.chemicalReactionInit();
    return system;
  }

  private static ChemicalReactionModelAudit.ReactionParameterSnapshot findReaction(
      ChemicalReactionModelAudit.AuditSnapshot audit, String name) {
    List<ChemicalReactionModelAudit.ReactionParameterSnapshot> reactions = audit.getReactions();
    for (ChemicalReactionModelAudit.ReactionParameterSnapshot reaction : reactions) {
      if (name.equals(reaction.getName())) {
        return reaction;
      }
    }
    return null;
  }
}
