package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionValidationStatus;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for kinetic-reaction qualification and transport-timescale diagnostics. */
public class KineticReactionTransportDiagnosticsTest extends NeqSimTest {

  @Test
  void testQualificationFailsClosedOutsideEvidenceBoundary() {
    KineticReactionQualification qualification = new KineticReactionQualification("SO2 oxidation",
        "Public laboratory study", "doi:10.example/kinetics", ChemicalReactionValidationStatus.VALIDATED, 273.15,
        323.15, 50.0, 150.0, "Synthetic test metadata");

    assertTrue(qualification.isWithinRange(298.15, 100.0));
    qualification.requireValidatedAt(298.15, 100.0);
    assertThrows(IllegalStateException.class, () -> qualification.requireValidatedAt(250.0, 100.0));

    KineticReactionQualification unvalidated = new KineticReactionQualification("SO2 oxidation", "Illustrative source",
        "internal:illustrative", ChemicalReactionValidationStatus.UNVALIDATED, 273.15, 323.15, 50.0, 150.0,
        "Not independently validated");
    assertThrows(IllegalStateException.class, () -> unvalidated.requireValidatedAt(298.15, 100.0));
  }

  @Test
  void testDamkohlerDiagnosticSeparatesTransportAndReactionTimescales() {
    SystemSrkEos system = createReactiveSystem();
    KineticReaction reaction = createSyntheticOxidationReaction();

    KineticReactionDiagnostics reference = KineticReactionDiagnostics.evaluate(reaction, system, 0, 1.0);
    assertTrue(reference.getReactionRate() > 0.0);
    assertTrue(reference.getReactionTimeSeconds() > 0.0);
    assertTrue(Double.isFinite(reference.getReactionTimeSeconds()));
    assertTrue(reference.getLimitingReactant().length() > 0);

    double reactionTime = reference.getReactionTimeSeconds();
    KineticReactionDiagnostics slowTransport = KineticReactionDiagnostics.evaluate(reaction, system, 0,
        0.01 * reactionTime);
    KineticReactionDiagnostics coupled = KineticReactionDiagnostics.evaluate(reaction, system, 0, reactionTime);
    KineticReactionDiagnostics fastChemistry = KineticReactionDiagnostics.evaluate(reaction, system, 0,
        100.0 * reactionTime);

    assertEquals(KineticReactionDiagnostics.Regime.TRANSPORT_DOMINATED, slowTransport.getRegime());
    assertEquals(KineticReactionDiagnostics.Regime.COUPLED, coupled.getRegime());
    assertEquals(KineticReactionDiagnostics.Regime.REACTION_DOMINATED, fastChemistry.getRegime());
    assertEquals(0.01, slowTransport.getDamkohlerNumber(), 1.0e-10);
    assertEquals(1.0, coupled.getDamkohlerNumber(), 1.0e-10);
    assertEquals(100.0, fastChemistry.getDamkohlerNumber(), 1.0e-8);
  }

  @Test
  void testInactiveReactionAndUnsupportedBasisAreExplicit() {
    SystemSrkEos system = createReactiveSystem();
    KineticReaction reaction = createSyntheticOxidationReaction();
    reaction.setPreExponentialFactor(0.0);

    KineticReactionDiagnostics inactive = KineticReactionDiagnostics.evaluate(reaction, system, 0, 100.0);
    assertEquals(KineticReactionDiagnostics.Regime.INACTIVE, inactive.getRegime());
    assertEquals(0.0, inactive.getDamkohlerNumber(), 0.0);
    assertTrue(Double.isInfinite(inactive.getReactionTimeSeconds()));

    reaction.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);
    assertThrows(IllegalArgumentException.class, () -> KineticReactionDiagnostics.evaluate(reaction, system, 0, 100.0));
  }

  private SystemSrkEos createReactiveSystem() {
    SystemSrkEos system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("SO2", 1.0e-4);
    system.addComponent("oxygen", 5.0e-4);
    system.setMixingRule("classic");
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();
    return system;
  }

  private KineticReaction createSyntheticOxidationReaction() {
    KineticReaction reaction = new KineticReaction("synthetic SO2 oxidation");
    reaction.addReactant("SO2", 1.0, 1.0);
    reaction.addReactant("oxygen", 0.5, 1.0);
    reaction.addProduct("SO3", 1.0);
    reaction.setPreExponentialFactor(1.0e-3);
    reaction.setActivationEnergy(0.0);
    reaction.setRateBasis(KineticReaction.RateBasis.VOLUME);
    return reaction;
  }
}
