package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionValidationStatus;
import neqsim.process.equipment.reactor.KineticReaction;
import neqsim.process.equipment.reactor.KineticReactionDiagnostics;
import neqsim.process.equipment.reactor.KineticReactionQualification;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Executes the safe workflow documented by the CO2 transport reaction-kinetics guide. */
class CO2TransportReactionKineticsDocumentationTest {

  @Test
  void testQualifiedScreenExecutesDocumentedWorkflow() {
    SystemInterface fluid = createInitializedFluid();
    KineticReaction reaction = createSyntheticReaction();
    KineticReactionQualification qualification = createQualification(
        ChemicalReactionValidationStatus.VALIDATED);

    KineticReactionDiagnostics initial =
        evaluateQualified(reaction, qualification, fluid, 0, 1.0);
    assertTrue(initial.getReactionTimeSeconds() > 0.0);
    assertTrue(Double.isFinite(initial.getReactionTimeSeconds()));

    KineticReactionDiagnostics diagnostic = evaluateQualified(reaction, qualification, fluid, 0,
        initial.getReactionTimeSeconds());

    assertEquals(reaction.getName(), diagnostic.getReactionName());
    assertEquals(1.0, diagnostic.getDamkohlerNumber(), 1.0e-10);
    assertEquals(KineticReactionDiagnostics.Regime.COUPLED, diagnostic.getRegime());
    assertFalse(diagnostic.getLimitingReactant().isEmpty());
    assertTrue(Double.isFinite(diagnostic.getReactionRate()));
    assertEquals(KineticReaction.RateBasis.VOLUME, diagnostic.getRateBasis());
  }

  @Test
  void testQualificationAndRateBasisFailClosed() {
    SystemInterface fluid = createInitializedFluid();
    KineticReaction reaction = createSyntheticReaction();
    KineticReactionQualification unvalidated = createQualification(
        ChemicalReactionValidationStatus.UNVALIDATED);

    assertThrows(IllegalStateException.class,
        () -> evaluateQualified(reaction, unvalidated, fluid, 0, 1.0));

    reaction.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);
    KineticReactionQualification validated = createQualification(
        ChemicalReactionValidationStatus.VALIDATED);
    assertThrows(IllegalArgumentException.class,
        () -> evaluateQualified(reaction, validated, fluid, 0, 1.0));
  }

  private static KineticReactionDiagnostics evaluateQualified(KineticReaction reaction,
      KineticReactionQualification qualification, SystemInterface fluid, int phaseIndex,
      double residenceTimeSeconds) {
    qualification.requireValidatedAt(fluid.getTemperature(), fluid.getPressure());

    KineticReactionDiagnostics diagnostic =
        KineticReactionDiagnostics.evaluate(reaction, fluid, phaseIndex, residenceTimeSeconds);

    diagnostic.getReactionName();
    diagnostic.getDamkohlerNumber();
    diagnostic.getRegime();
    diagnostic.getLimitingReactant();
    return diagnostic;
  }

  private static SystemInterface createInitializedFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("SO2", 1.0e-4);
    fluid.addComponent("oxygen", 5.0e-4);
    fluid.setMixingRule("classic");
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
    return fluid;
  }

  private static KineticReaction createSyntheticReaction() {
    KineticReaction reaction = new KineticReaction("synthetic SO2 oxidation");
    reaction.addReactant("SO2", 1.0, 1.0);
    reaction.addReactant("oxygen", 0.5, 1.0);
    reaction.addProduct("SO3", 1.0);
    reaction.setPreExponentialFactor(1.0e-3);
    reaction.setActivationEnergy(0.0);
    reaction.setRateBasis(KineticReaction.RateBasis.VOLUME);
    return reaction;
  }

  private static KineticReactionQualification createQualification(
      ChemicalReactionValidationStatus status) {
    return new KineticReactionQualification("synthetic SO2 oxidation",
        "Synthetic software-regression fixture", "internal:documentation-test", status, 273.15,
        323.15, 50.0, 150.0, "Not an engineering kinetic parameterization");
  }
}
