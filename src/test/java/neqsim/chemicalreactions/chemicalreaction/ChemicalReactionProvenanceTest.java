package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemKentEisenberg;
import neqsim.thermo.system.SystemPitzer;

/**
 * Tests reaction-source selection and equilibrium-parameter provenance diagnostics.
 *
 * @author OpenAI Codex
 * @version 1.0
 */
class ChemicalReactionProvenanceTest {
  private static final double[] STANDARD_CO2_WATER = new double[] { 253.235548, -12865.665607, -39.440767, 0.0 };
  private static final double[] PITZER_CO2_WATER =
      new double[] { 653.705141388, -23927.318205735, -108.892446382, 0.108492068 };
  private static final double[] KENT_CO2_WATER = new double[] { 231.465, -12092.1, -36.7816, 0.0 };

  /** Verify that Kent-Eisenberg explicitly selects its apparent-constant parameter set. */
  @Test
  void kentEisenbergUsesDedicatedApparentConstantSource() {
    SystemInterface system = reactiveCo2WaterSystem(new SystemKentEisenberg(298.15, 1.01325));

    assertEquals(ChemicalReactionDataSource.KENT_EISENBERG, system.getChemicalReactionDataSource());
    assertEquals(ChemicalReactionDataSource.KENT_EISENBERG,
        system.getChemicalReactionOperations().getReactionDataSource());
    assertEquals("reactiondatakenteisenberg",
        system.getChemicalReactionOperations().getReactionDataSource().getDatabaseTableName());

    ChemicalReaction reaction = getCo2WaterReaction(system);
    assertEquals("KentEisenberg1976", reaction.getReference());
    assertArrayEquals(KENT_CO2_WATER, reaction.getEquilibriumConstantCoefficients(), 1.0e-12);
    assertEquals(298.15, reaction.getReferenceTemperature(), 1.0e-12);
  }

  /** Verify source and parameter provenance for the electrolyte EOS model. */
  @Test
  void electrolyteEosUsesStandardReactionSource() {
    assertStandardSource(reactiveCo2WaterSystem(new SystemElectrolyteCPAstatoil(298.15, 1.01325)));
  }

  /** Verify source and molality-standard-state parameter provenance for the Pitzer electrolyte GE model. */
  @Test
  void electrolyteGeUsesDedicatedPitzerReactionSource() {
    SystemInterface system = reactiveCo2WaterSystem(new SystemPitzer(298.15, 1.01325));

    assertEquals(ChemicalReactionDataSource.PITZER, system.getChemicalReactionDataSource());
    assertEquals(ChemicalReactionDataSource.PITZER, system.getChemicalReactionOperations().getReactionDataSource());
    ChemicalReaction reaction = getCo2WaterReaction(system);
    assertEquals("USGS-PHREEQC3-PlummerBusenberg1982-fit-0-90C", reaction.getReference());
    assertArrayEquals(PITZER_CO2_WATER, reaction.getEquilibriumConstantCoefficients(), 1.0e-12);
  }

  /**
   * Verify clone and serialization preservation of the Pitzer source.
   *
   * @throws Exception if Java serialization fails
   */
  @Test
  void pitzerSourceSurvivesCloneAndSerialization() throws Exception {
    SystemInterface original = reactiveCo2WaterSystem(new SystemPitzer(298.15, 1.01325));
    SystemInterface cloned = original.clone();
    SystemInterface restored = roundTrip(original);

    assertEquals(ChemicalReactionDataSource.PITZER,
        cloned.getChemicalReactionOperations().getReactionDataSource());
    assertArrayEquals(PITZER_CO2_WATER, getCo2WaterReaction(cloned).getEquilibriumConstantCoefficients(),
        1.0e-12);
    assertEquals(ChemicalReactionDataSource.PITZER,
        restored.getChemicalReactionOperations().getReactionDataSource());
    assertArrayEquals(PITZER_CO2_WATER, getCo2WaterReaction(restored).getEquilibriumConstantCoefficients(),
        1.0e-12);
  }

  /** Verify that callers cannot mutate stored equilibrium-constant coefficients. */
  @Test
  void coefficientDiagnosticReturnsDefensiveCopy() {
    SystemInterface system = reactiveCo2WaterSystem(new SystemElectrolyteCPAstatoil(298.15, 1.01325));
    ChemicalReaction reaction = getCo2WaterReaction(system);
    double[] first = reaction.getEquilibriumConstantCoefficients();
    double[] second = reaction.getEquilibriumConstantCoefficients();

    assertNotSame(first, second);
    first[0] = -1.0;
    assertArrayEquals(STANDARD_CO2_WATER, reaction.getEquilibriumConstantCoefficients(), 1.0e-12);
  }

  /** Verify that factory-created reactions preserve their database reference. */
  @Test
  void factoryPreservesStandardParameterReference() {
    ChemicalReaction reaction = ChemicalReactionFactory.getChemicalReaction("CO2water");

    assertEquals("Plummer-Busenberg1982", reaction.getReference());
    assertArrayEquals(STANDARD_CO2_WATER, reaction.getEquilibriumConstantCoefficients(), 1.0e-12);
  }

  /**
   * Verify clone and serialization preservation of source and parameter provenance.
   *
   * @throws Exception if Java serialization fails
   */
  @Test
  void provenanceSurvivesCloneAndSerialization() throws Exception {
    SystemInterface original = reactiveCo2WaterSystem(new SystemKentEisenberg(298.15, 1.01325));
    SystemInterface cloned = original.clone();
    SystemInterface restored = roundTrip(original);

    assertEquals(ChemicalReactionDataSource.KENT_EISENBERG,
        cloned.getChemicalReactionOperations().getReactionDataSource());
    assertEquals("KentEisenberg1976", getCo2WaterReaction(cloned).getReference());
    assertEquals(ChemicalReactionDataSource.KENT_EISENBERG,
        restored.getChemicalReactionOperations().getReactionDataSource());
    assertEquals("KentEisenberg1976", getCo2WaterReaction(restored).getReference());
  }

  /**
   * Initialize a two-component reactive CO2-water system.
   *
   * @param system thermodynamic system to initialize
   * @return initialized reactive system
   */
  private static SystemInterface reactiveCo2WaterSystem(SystemInterface system) {
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 0.99);
    system.chemicalReactionInit();
    return system;
  }

  /**
   * Get the loaded CO2 hydration reaction.
   *
   * @param system initialized reactive system
   * @return loaded CO2-water reaction
   */
  private static ChemicalReaction getCo2WaterReaction(SystemInterface system) {
    ChemicalReaction reaction = system.getChemicalReactionOperations().getReactionList().getReaction("CO2water");
    assertNotNull(reaction);
    return reaction;
  }

  /**
   * Assert the standard source and CO2-water parameter provenance.
   *
   * @param system initialized electrolyte system
   */
  private static void assertStandardSource(SystemInterface system) {
    assertEquals(ChemicalReactionDataSource.STANDARD, system.getChemicalReactionDataSource());
    assertEquals(ChemicalReactionDataSource.STANDARD, system.getChemicalReactionOperations().getReactionDataSource());
    assertEquals("standard", system.getChemicalReactionOperations().getReactionDataSource().getIdentifier());

    ChemicalReaction reaction = getCo2WaterReaction(system);
    assertEquals("Plummer-Busenberg1982", reaction.getReference());
    assertArrayEquals(STANDARD_CO2_WATER, reaction.getEquilibriumConstantCoefficients(), 1.0e-12);
    assertEquals(298.15, reaction.getReferenceTemperature(), 1.0e-12);
  }

  /**
   * Serialize and restore a thermodynamic system.
   *
   * @param system system to serialize
   * @return restored system
   * @throws Exception if Java serialization fails
   */
  private static SystemInterface roundTrip(SystemInterface system) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SystemInterface) input.readObject();
    }
  }
}
