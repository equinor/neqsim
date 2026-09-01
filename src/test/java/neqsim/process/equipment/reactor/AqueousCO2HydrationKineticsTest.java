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
  void testResidenceTimeScreenMatchesAnalyticalPairRelaxation() {
    double temperatureK = 298.15;
    double relaxationTime = AqueousCO2HydrationKinetics.relaxationTimeSeconds(temperatureK);
    AqueousCO2HydrationKinetics.TimescaleResult screen = AqueousCO2HydrationKinetics.screenResidenceTime(relaxationTime,
        temperatureK);

    assertEquals(0.038439871369051845, relaxationTime, 1.0e-15);
    assertEquals(1.0, screen.getDamkohlerNumber(), 1.0e-15);
    assertEquals(Math.exp(-1.0), screen.getRemainingDeviationFraction(), 1.0e-15);
    assertEquals(1.0 - Math.exp(-1.0), screen.getRelaxedFraction(), 1.0e-15);
    assertEquals(1.0, screen.getRemainingDeviationFraction() + screen.getRelaxedFraction(), 1.0e-15);
    assertEquals(KineticReactionDiagnostics.Regime.COUPLED, screen.getRegime());
    assertEquals(temperatureK, screen.getTemperatureK(), 0.0);
    assertEquals(relaxationTime, screen.getResidenceTimeSeconds(), 0.0);
    assertEquals(screen.getHydrationRateConstant() + screen.getDehydrationRateConstant(),
        screen.getRelaxationRateConstant(), 0.0);

    double initialCO2 = 1000.0;
    double initialCarbonicAcid = 50.0;
    AqueousCO2HydrationKinetics.SpeciationBridgeResult equilibrium = AqueousCO2HydrationKinetics
        .partitionLumpedMolecularCO2(initialCO2 + initialCarbonicAcid, temperatureK);
    AqueousCO2HydrationKinetics.Result advanced = AqueousCO2HydrationKinetics.advance(initialCO2, initialCarbonicAcid,
        relaxationTime, temperatureK);
    double analyticalRemaining = (advanced.getCarbonicAcidConcentration() - equilibrium.getCarbonicAcidConcentration())
        / (initialCarbonicAcid - equilibrium.getCarbonicAcidConcentration());
    assertEquals(screen.getRemainingDeviationFraction(), analyticalRemaining, 1.0e-15);
  }

  @Test
  void testResidenceTimeRegimesAndLimitsAreDeterministic() {
    double temperatureK = 298.15;
    double relaxationTime = AqueousCO2HydrationKinetics.relaxationTimeSeconds(temperatureK);

    AqueousCO2HydrationKinetics.TimescaleResult zero = AqueousCO2HydrationKinetics.screenResidenceTime(0.0,
        temperatureK);
    assertEquals(0.0, zero.getDamkohlerNumber(), 0.0);
    assertEquals(1.0, zero.getRemainingDeviationFraction(), 0.0);
    assertEquals(0.0, zero.getRelaxedFraction(), 0.0);
    assertEquals(KineticReactionDiagnostics.Regime.TRANSPORT_DOMINATED, zero.getRegime());

    assertEquals(KineticReactionDiagnostics.Regime.COUPLED,
        AqueousCO2HydrationKinetics.screenResidenceTime(0.1 * relaxationTime, temperatureK).getRegime());
    assertEquals(KineticReactionDiagnostics.Regime.COUPLED,
        AqueousCO2HydrationKinetics.screenResidenceTime(10.0 * relaxationTime, temperatureK).getRegime());
    AqueousCO2HydrationKinetics.TimescaleResult longResidence = AqueousCO2HydrationKinetics.screenResidenceTime(1.0,
        temperatureK);
    assertEquals(KineticReactionDiagnostics.Regime.REACTION_DOMINATED, longResidence.getRegime());
    assertEquals(26.01465521045176, longResidence.getDamkohlerNumber(), 1.0e-13);
    assertEquals(5.034760235515951e-12, longResidence.getRemainingDeviationFraction(), 1.0e-24);

    AqueousCO2HydrationKinetics.TimescaleResult repeated = AqueousCO2HydrationKinetics.screenResidenceTime(1.0,
        temperatureK);
    assertEquals(longResidence.getRemainingDeviationFraction(), repeated.getRemainingDeviationFraction(), 0.0);
    assertTrue(AqueousCO2HydrationKinetics
        .relaxationTimeSeconds(AqueousCO2HydrationKinetics.MINIMUM_TEMPERATURE_K) > relaxationTime);
    assertTrue(AqueousCO2HydrationKinetics
        .relaxationTimeSeconds(AqueousCO2HydrationKinetics.MAXIMUM_TEMPERATURE_K) < relaxationTime);
  }

  @Test
  void testTimeToSelectedRemainingDeviationAndInvalidInputs() {
    double temperatureK = 298.15;
    double onePercentTime = AqueousCO2HydrationKinetics.timeToRemainingDeviationFraction(0.01, temperatureK);
    assertEquals(0.17702214958197476, onePercentTime, 1.0e-15);
    assertEquals(0.01,
        AqueousCO2HydrationKinetics.screenResidenceTime(onePercentTime, temperatureK).getRemainingDeviationFraction(),
        1.0e-15);
    assertEquals(0.0, AqueousCO2HydrationKinetics.timeToRemainingDeviationFraction(1.0, temperatureK), 0.0);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.screenResidenceTime(-1.0, temperatureK));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.screenResidenceTime(Double.NaN, temperatureK));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.screenResidenceTime(Double.MAX_VALUE, temperatureK));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.timeToRemainingDeviationFraction(0.0, temperatureK));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.timeToRemainingDeviationFraction(1.01, temperatureK));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationKinetics.timeToRemainingDeviationFraction(Double.NaN, temperatureK));
    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationKinetics.screenResidenceTime(1.0, 305.66));
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
