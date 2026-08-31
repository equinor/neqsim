package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.system.SystemPitzer;

/** Tests the published aqueous CO2 hydration/dehydration bridge. */
public class AqueousCO2HydrationKineticsTest extends NeqSimTest {

  @Test
  void testPublishedRateCorrelationsAt25C() {
    double temperatureK = 298.15;

    assertEquals(0.030258620071021758, AqueousCO2HydrationKinetics.hydrationRateConstant(temperatureK), 1.0e-14);
    assertEquals(25.98439659038074, AqueousCO2HydrationKinetics.dehydrationRateConstant(temperatureK), 1.0e-11);
    assertEquals(1.1644919275217386e-3, AqueousCO2HydrationKinetics.carbonicAcidToCO2EquilibriumRatio(temperatureK),
        1.0e-15);
    assertEquals("doi:10.1016/S0304-4203(02)00010-5", AqueousCO2HydrationKinetics.SOURCE_IDENTIFIER);
    assertTrue(AqueousCO2HydrationKinetics.SOURCE_ACCESS_STATUS.contains("without redistributing"));
  }

  @Test
  void testGenericReactionMatchesPublishedForwardAndReverseCorrelations() {
    double temperatureK = 298.15;
    KineticReaction reaction = AqueousCO2HydrationKinetics.createReaction();

    assertEquals(AqueousCO2HydrationKinetics.hydrationRateConstant(temperatureK),
        reaction.calculateRateConstant(temperatureK), 1.0e-14);
    assertEquals(AqueousCO2HydrationKinetics.carbonicAcidToCO2EquilibriumRatio(temperatureK),
        reaction.calculateEquilibriumConstant(temperatureK), 1.0e-15);
    assertEquals(-1.0, reaction.getStoichiometricCoefficient("CO2"), 0.0);
    assertEquals(-1.0, reaction.getStoichiometricCoefficient("water"), 0.0);
    assertEquals(1.0, reaction.getStoichiometricCoefficient("H2CO3"), 0.0);
    assertEquals(KineticReaction.RateBasis.VOLUME, reaction.getRateBasis());
    assertTrue(reaction.isReversible());
  }

  @Test
  void testAnalyticalAdvanceConservesCarbonAndRecoversEquilibrium() {
    double initialCO2 = 1000.0;
    double initialCarbonicAcid = 0.0;
    double temperatureK = 298.15;

    AqueousCO2HydrationKinetics.Result unchanged = AqueousCO2HydrationKinetics.advance(initialCO2, initialCarbonicAcid,
        0.0, temperatureK);
    assertEquals(initialCO2, unchanged.getCO2Concentration(), 0.0);
    assertEquals(initialCarbonicAcid, unchanged.getCarbonicAcidConcentration(), 0.0);

    AqueousCO2HydrationKinetics.Result equilibrated = AqueousCO2HydrationKinetics.advance(initialCO2,
        initialCarbonicAcid, 10.0, temperatureK);
    assertEquals(initialCO2 + initialCarbonicAcid,
        equilibrated.getCO2Concentration() + equilibrated.getCarbonicAcidConcentration(), 1.0e-12);
    assertEquals(0.0, equilibrated.getCarbonBalanceResidual(), 1.0e-12);
    assertTrue(equilibrated.getCO2Concentration() >= 0.0);
    assertTrue(equilibrated.getCarbonicAcidConcentration() >= 0.0);
    assertEquals(AqueousCO2HydrationKinetics.carbonicAcidToCO2EquilibriumRatio(temperatureK),
        equilibrated.getCarbonicAcidConcentration() / equilibrated.getCO2Concentration(), 1.0e-15);

    AqueousCO2HydrationKinetics.Result repeated = AqueousCO2HydrationKinetics.advance(initialCO2, initialCarbonicAcid,
        10.0, temperatureK);
    assertEquals(equilibrated.getCO2Concentration(), repeated.getCO2Concentration(), 0.0);
    assertEquals(equilibrated.getCarbonicAcidConcentration(), repeated.getCarbonicAcidConcentration(), 0.0);
  }

  @Test
  void testLumpedSpeciationBridgeConservesCarbonAndRetainsSeparate25CCheck() {
    double totalMolecularCO2 = 1000.0;
    double temperatureK = 298.15;

    AqueousCO2HydrationKinetics.SpeciationBridgeResult partition = AqueousCO2HydrationKinetics
        .partitionLumpedMolecularCO2(totalMolecularCO2, temperatureK);

    assertEquals(totalMolecularCO2, partition.getTotalMolecularCO2Concentration(), 0.0);
    assertEquals(totalMolecularCO2, partition.getCO2Concentration() + partition.getCarbonicAcidConcentration(),
        1.0e-12);
    assertEquals(0.0, partition.getCarbonBalanceResidual(), 1.0e-12);
    assertEquals(AqueousCO2HydrationKinetics.carbonicAcidToCO2EquilibriumRatio(temperatureK),
        partition.getCarbonicAcidToCO2EquilibriumRatio(), 0.0);
    assertEquals(partition.getCarbonicAcidToCO2EquilibriumRatio(),
        partition.getCarbonicAcidConcentration() / partition.getCO2Concentration(), 1.0e-15);
    assertEquals(totalMolecularCO2, AqueousCO2HydrationKinetics.collapseExplicitPairToLumpedCO2(
        partition.getCO2Concentration(), partition.getCarbonicAcidConcentration()), 0.0);

    assertEquals(848.0, AqueousCO2HydrationKinetics.REPORTED_CO2_TO_H2CO3_RATIO_AT_25_C, 0.0);
    assertEquals(298.15, AqueousCO2HydrationKinetics.REPORTED_RATIO_TEMPERATURE_K, 0.0);
    double relativeDifference = (partition.getCO2ToCarbonicAcidEquilibriumRatio()
        - AqueousCO2HydrationKinetics.REPORTED_CO2_TO_H2CO3_RATIO_AT_25_C)
        / AqueousCO2HydrationKinetics.REPORTED_CO2_TO_H2CO3_RATIO_AT_25_C;
    assertTrue(relativeDifference > 0.01 && relativeDifference < 0.02,
        "The separately reported 25 C value must remain visible rather than tuning the two rate fits to it");

    AqueousCO2HydrationKinetics.SpeciationBridgeResult repeated = AqueousCO2HydrationKinetics
        .partitionLumpedMolecularCO2(totalMolecularCO2, temperatureK);
    assertEquals(partition.getCO2Concentration(), repeated.getCO2Concentration(), 0.0);
    assertEquals(partition.getCarbonicAcidConcentration(), repeated.getCarbonicAcidConcentration(), 0.0);
  }

  @Test
  void testPitzerEquilibriumUsesLumpedMolecularCO2WithoutExplicitH2CO3() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 0.99);
    system.chemicalReactionInit();

    assertEquals(ChemicalReactionDataSource.PITZER, system.getChemicalReactionDataSource());
    assertFalse(system.hasComponent("H2CO3"));
    ChemicalReaction reaction = system.getChemicalReactionOperations().getReactionList().getReaction("CO2water");
    assertNotNull(reaction);
    assertEquals(-1.0, stoichiometricCoefficient(reaction, "CO2"), 0.0);
    assertEquals(1.0, stoichiometricCoefficient(reaction, "HCO3-"), 0.0);
    assertEquals(1.0, stoichiometricCoefficient(reaction, "H3O+"), 0.0);
  }

  @Test
  void testPublishedDomainAndInvalidStatesFailClosed() {
    assertTrue(
        AqueousCO2HydrationKinetics.hydrationRateConstant(AqueousCO2HydrationKinetics.MINIMUM_TEMPERATURE_K) > 0.0);
    assertTrue(
        AqueousCO2HydrationKinetics.dehydrationRateConstant(AqueousCO2HydrationKinetics.MAXIMUM_TEMPERATURE_K) > 0.0);

    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationKinetics.hydrationRateConstant(288.14));
    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationKinetics.dehydrationRateConstant(305.66));
    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationKinetics.advance(-1.0, 0.0, 1.0, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.advance(1.0, 0.0, Double.NaN, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.partitionLumpedMolecularCO2(-1.0, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.partitionLumpedMolecularCO2(Double.NaN, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.partitionLumpedMolecularCO2(1.0, 305.66));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.collapseExplicitPairToLumpedCO2(Double.MAX_VALUE, Double.MAX_VALUE));

    AqueousCO2HydrationKinetics.SpeciationBridgeResult zero = AqueousCO2HydrationKinetics
        .partitionLumpedMolecularCO2(0.0, AqueousCO2HydrationKinetics.MINIMUM_TEMPERATURE_K);
    assertEquals(0.0, zero.getCO2Concentration(), 0.0);
    assertEquals(0.0, zero.getCarbonicAcidConcentration(), 0.0);
  }

  private static double stoichiometricCoefficient(ChemicalReaction reaction, String componentName) {
    String[] componentNames = reaction.getNames();
    double[] coefficients = reaction.getStocCoefs();
    for (int index = 0; index < componentNames.length; index++) {
      if (componentName.equals(componentNames[index])) {
        return coefficients[index];
      }
    }
    throw new AssertionError("Missing reaction component " + componentName);
  }
}
