package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

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
  }
}
