package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.chemicalreactions.kinetics.Kinetics;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction.KineticRateLaw;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;

class ChemicalReactionRateLawTest {
  private ChemicalReaction reaction(double rate, double energy, double referenceTemperature) {
    return new ChemicalReaction("rate getter probe", new String[] {"methane", "ethane"}, new double[] {-1.0, 1.0},
        new double[4], rate, energy, referenceTemperature);
  }

  private PhaseInterface phase(double temperature) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    return fluid.getPhase(0);
  }

  @Test
  void referenceTemperatureReturnsEachSuppliedRateFactor() {
    PhaseInterface phase = phase(298.15);
    assertEquals(1.0, reaction(1.0, 50000.0, 298.15).getRateFactor(phase), 0.0);
    assertEquals(100.0, reaction(100.0, 50000.0, 298.15).getRateFactor(phase), 0.0);
  }

  @Test
  void zeroActivationEnergyGivesTemperatureIndependentRate() {
    ChemicalReaction reaction = reaction(2.0, 0.0, 298.15);
    assertEquals(2.0, reaction.getRateFactor(phase(273.15)), 0.0);
    assertEquals(2.0, reaction.getRateFactor(phase(350.0)), 0.0);
  }

  @Test
  void arrheniusSlopeUsesActivationEnergyInJoulesPerMole() {
    ChemicalReaction reaction = reaction(2.0, 50000.0, 298.15);
    double cold = reaction.getRateFactor(phase(290.0));
    double hot = reaction.getRateFactor(phase(320.0));
    double slope = Math.log(hot / cold) / (1.0 / 290.0 - 1.0 / 320.0);
    assertEquals(50000.0 / ThermodynamicConstantsInterface.R, slope, 1e-8);
    assertTrue(hot > 2.0);
  }

  @Test
  void databaseFactoryRetainsExistingLegacyTemperatureLaw() {
    ChemicalReaction databaseReaction = ChemicalReactionFactory.getChemicalReaction("CO2water");
    assertEquals(KineticRateLaw.LEGACY_TEMPERATURE_CORRELATION, databaseReaction.getKineticRateLaw());
    for (double temperature : new double[] {298.15, 313.15, 350.0}) {
      assertEquals(2.576e9 * Math.exp(-6024.0 / temperature) / 1000.0,
          databaseReaction.getRateFactor(phase(temperature)), 0.0);
    }
  }

  @Test
  void kineticsCallerUsesTheSelectedReactionParameters() {
    // Synthetic first-order conversion tests parameter propagation, not chemical calibration.
    SystemSrkEos fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("ethane", 0.5);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(3);
    fluid.initPhysicalProperties();
    ChemicalReactionOperations operations = new ChemicalReactionOperations(fluid);
    operations.getReactionList().getChemicalReactionList().clear();
    ChemicalReaction reaction = reaction(1.0, 0.0, 298.15);
    operations.getReactionList().getChemicalReactionList().add(reaction);
    Kinetics kinetics = new Kinetics(operations);
    PhaseInterface phase = fluid.getPhase(0);
    double first = kinetics.calcReacMatrix(phase, phase, 0);
    reaction.setRateFactor(100.0);
    double second = kinetics.calcReacMatrix(phase, phase, 0);
    assertEquals(1.0, first, 1e-12);
    assertEquals(100.0, second, 1e-12);
  }

  @Test
  void databaseListUsesExplicitCompatibilityLawUntilRatesAreQualified() {
    ChemicalReactionList reactions = new ChemicalReactionList();
    reactions.readReactions(new SystemSrkEos());
    assertTrue(reactions.getChemicalReactionList().size() > 0);
    for (ChemicalReaction reaction : reactions.getChemicalReactionList()) {
      assertEquals(KineticRateLaw.LEGACY_TEMPERATURE_CORRELATION, reaction.getKineticRateLaw());
    }
  }

  @Test
  void migratingDatabaseKineticsRequiresCompleteUnitQualifiedParameters() {
    ChemicalReaction reaction = ChemicalReactionFactory.getChemicalReaction("CO2water");
    assertThrows(IllegalArgumentException.class, () -> reaction.setReferenceKinetics(1.0, 50000.0, 0.0));
    assertEquals(KineticRateLaw.LEGACY_TEMPERATURE_CORRELATION, reaction.getKineticRateLaw());
    reaction.setReferenceKinetics(2.0, 50000.0, 298.15);
    assertEquals(KineticRateLaw.REFERENCE_ARRHENIUS, reaction.getKineticRateLaw());
    assertEquals(2.0, reaction.getRateFactor(phase(298.15)), 0.0);
    reaction.setRateFactor(3.0);
    reaction.setActivationEnergy(0.0);
    assertEquals(3.0, reaction.getRateFactor(phase(350.0)), 0.0);
    reaction.useLegacyKineticRateLaw();
    assertEquals(2.576e9 * Math.exp(-6024.0 / 350.0) / 1000.0, reaction.getRateFactor(phase(350.0)), 0.0);
  }

  private ChemicalReaction roundTrip(ChemicalReaction reaction) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(reaction);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ChemicalReaction) in.readObject();
    }
  }

  @Test
  void serializationRetainsSelectedLawAndMissingSelectorKeepsLegacyBehavior() throws Exception {
    ChemicalReaction reference = reaction(2.0, 50000.0, 298.15);
    assertEquals(2.0, roundTrip(reference).getRateFactor(phase(298.15)), 0.0);
    reference.useLegacyKineticRateLaw();
    assertEquals(KineticRateLaw.LEGACY_TEMPERATURE_CORRELATION, roundTrip(reference).getKineticRateLaw());
    // Java deserializes a field absent from an older stream as null; emulate that state.
    java.lang.reflect.Field selector = ChemicalReaction.class.getDeclaredField("kineticRateLaw");
    selector.setAccessible(true);
    selector.set(reference, null);
    assertEquals(2.576e9 * Math.exp(-6024.0 / 298.15) / 1000.0, roundTrip(reference).getRateFactor(phase(298.15)), 0.0);
  }
}
