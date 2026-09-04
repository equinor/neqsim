package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests the primary-source aqueous total-sulfide oxidation screening correlation. */
public class AqueousHydrogenSulfideOxidationKineticsTest extends NeqSimTest {
  private static final double TEMPERATURE_K = 298.15;
  private static final double PH = 8.0;
  private static final double IONIC_STRENGTH = 0.723;
  private static final double AIR_SATURATED_OXYGEN_MOLALITY = 250.0e-6;

  @Test
  void testPrimarySourceMetadataAndReferenceEquation() {
    assertEquals("doi:10.1021/es00159a003", AqueousHydrogenSulfideOxidationKinetics.SOURCE_IDENTIFIER);
    assertEquals(25.0e-6, AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY);
    assertEquals(5.0e-6, AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_SPREAD);

    double rate = AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, PH, IONIC_STRENGTH);

    assertEquals(123.61753672611606, rate, 1.0e-10);
    assertEquals(10.50 + 0.16 * PH - 3000.0 / TEMPERATURE_K + 0.44 * Math.sqrt(IONIC_STRENGTH), Math.log10(rate),
        1.0e-12);
  }

  @Test
  void testPublishedBoundariesAreInclusiveAndExtrapolationFailsClosed() {
    assertTrue(AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(278.15, 4.0, 0.0) > 0.0);
    assertTrue(AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(338.15, 8.0, 6.0) > 0.0);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(278.15 - 1.0e-9, 4.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(338.15 + 1.0e-9, 8.0, 6.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, 3.999, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, 8.001, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, PH, -1.0e-9));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, PH, 6.0 + 1.0e-9));
  }

  @Test
  void testRateIsMonotonicWithinThePublishedCorrelation() {
    double base = AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, 6.0, 1.0);

    assertTrue(AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K + 1.0, 6.0, 1.0) > base);
    assertTrue(AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, 6.1, 1.0) > base);
    assertTrue(AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(TEMPERATURE_K, 6.0, 1.1) > base);
  }

  @Test
  void testReportedLogRateScatterIsAppliedMultiplicatively() {
    AqueousHydrogenSulfideOxidationKinetics.RateConstantRange range = AqueousHydrogenSulfideOxidationKinetics
        .secondOrderRateConstantRange(TEMPERATURE_K, PH, IONIC_STRENGTH);
    double factor = Math.pow(10.0, 0.18);

    assertEquals(range.getNominal() / factor, range.getLower(), 1.0e-12);
    assertEquals(range.getNominal() * factor, range.getUpper(), 1.0e-12);
    assertEquals(factor, range.getUpper() / range.getNominal(), 1.0e-12);
    assertEquals(factor, range.getNominal() / range.getLower(), 1.0e-12);
  }

  @Test
  void testConstantOxygenExposureUsesExactPseudoFirstOrderSolution() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ScreeningResult zero = AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(AIR_SATURATED_OXYGEN_MOLALITY, 0.0, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ScreeningResult oneHalfLife = AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(AIR_SATURATED_OXYGEN_MOLALITY, halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ScreeningResult twoHalfLives = AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(AIR_SATURATED_OXYGEN_MOLALITY, 2.0 * halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);

    assertEquals(22.428765332726698, halfLife, 1.0e-11);
    assertEquals(1.0, zero.getRemainingFraction(), 0.0);
    assertEquals(0.0, zero.getReactedFraction(), 0.0);
    assertEquals(0.5, oneHalfLife.getRemainingFraction(), 1.0e-15);
    assertEquals(0.5, oneHalfLife.getReactedFraction(), 1.0e-15);
    assertEquals(0.25, twoHalfLives.getRemainingFraction(), 1.0e-15);
    assertEquals(0.75, twoHalfLives.getReactedFraction(), 1.0e-15);
    assertEquals(oneHalfLife.getSecondOrderRateConstant() * AIR_SATURATED_OXYGEN_MOLALITY,
        oneHalfLife.getPseudoFirstOrderRateConstant(), 1.0e-15);
    assertEquals(Math.log(2.0), oneHalfLife.getExposure(), 1.0e-15);
  }

  @Test
  void testResidenceTimeRangePropagatesPublishedFitScatter() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ResidenceTimeRangeResult result = AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);
    double factor = Math.pow(10.0, AqueousHydrogenSulfideOxidationKinetics.LOG10_RATE_STANDARD_DEVIATION);

    assertEquals(result.getNominalPseudoFirstOrderRate() / factor, result.getLowerPseudoFirstOrderRate(), 1.0e-15);
    assertEquals(result.getNominalPseudoFirstOrderRate() * factor, result.getUpperPseudoFirstOrderRate(), 1.0e-15);
    assertEquals(1.0 / result.getLowerPseudoFirstOrderRate(), result.getLowerRateChemicalTimeHours(), 1.0e-12);
    assertEquals(1.0 / result.getNominalPseudoFirstOrderRate(), result.getNominalChemicalTimeHours(), 1.0e-12);
    assertEquals(1.0 / result.getUpperPseudoFirstOrderRate(), result.getUpperRateChemicalTimeHours(), 1.0e-12);
    assertEquals(Math.log(2.0) / factor, result.getLowerRateDamkohlerNumber(), 1.0e-15);
    assertEquals(Math.log(2.0), result.getNominalDamkohlerNumber(), 1.0e-15);
    assertEquals(Math.log(2.0) * factor, result.getUpperRateDamkohlerNumber(), 1.0e-15);
    assertEquals(0.5, result.getNominalRemainingFraction(), 1.0e-15);
    assertTrue(result.getLowerRateRemainingFraction() > result.getNominalRemainingFraction());
    assertTrue(result.getNominalRemainingFraction() > result.getUpperRateRemainingFraction());
  }

  @Test
  void testResidenceTimeRangeIsMonotonicDeterministicAndExactAtZero() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ResidenceTimeRangeResult zero = AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, 0.0, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ResidenceTimeRangeResult shortResidence = AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, 0.5 * halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ResidenceTimeRangeResult longResidence = AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, 2.0 * halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationKinetics.ResidenceTimeRangeResult repeated = AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, 2.0 * halfLife, TEMPERATURE_K, PH, IONIC_STRENGTH);

    assertEquals(0.0, zero.getLowerRateDamkohlerNumber(), 0.0);
    assertEquals(0.0, zero.getNominalDamkohlerNumber(), 0.0);
    assertEquals(0.0, zero.getUpperRateDamkohlerNumber(), 0.0);
    assertEquals(1.0, zero.getLowerRateRemainingFraction(), 0.0);
    assertEquals(1.0, zero.getNominalRemainingFraction(), 0.0);
    assertEquals(1.0, zero.getUpperRateRemainingFraction(), 0.0);
    assertTrue(longResidence.getLowerRateRemainingFraction() < shortResidence.getLowerRateRemainingFraction());
    assertTrue(longResidence.getNominalRemainingFraction() < shortResidence.getNominalRemainingFraction());
    assertTrue(longResidence.getUpperRateRemainingFraction() < shortResidence.getUpperRateRemainingFraction());
    assertEquals(longResidence.getNominalDamkohlerNumber(), repeated.getNominalDamkohlerNumber(), 0.0);
    assertEquals(longResidence.getNominalRemainingFraction(), repeated.getNominalRemainingFraction(), 0.0);
  }

  @Test
  void testResidenceTimeRangeFailsClosedOnInvalidOrOverflowingInputs() {
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY, -1.0, TEMPERATURE_K, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.screenResidenceTimeRange(AIR_SATURATED_OXYGEN_MOLALITY,
            Double.POSITIVE_INFINITY, TEMPERATURE_K, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(Double.MIN_VALUE, 1.0, TEMPERATURE_K, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .screenResidenceTimeRange(1.0, Double.MAX_VALUE, TEMPERATURE_K, PH, IONIC_STRENGTH));
  }

  @Test
  void testLongExposureRemainsBoundedAndInputValidationFailsClosed() {
    AqueousHydrogenSulfideOxidationKinetics.ScreeningResult longExposure = AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(AIR_SATURATED_OXYGEN_MOLALITY, 1.0e6, TEMPERATURE_K, PH, IONIC_STRENGTH);

    assertEquals(0.0, longExposure.getRemainingFraction(), 0.0);
    assertEquals(1.0, longExposure.getReactedFraction(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(0.0, 1.0, TEMPERATURE_K, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .screenAirSaturatedExposure(AIR_SATURATED_OXYGEN_MOLALITY, -1.0, TEMPERATURE_K, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationKinetics.secondOrderRateConstant(Double.NaN, PH, IONIC_STRENGTH));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationKinetics
        .pseudoFirstOrderRateConstant(Double.MAX_VALUE, TEMPERATURE_K, PH, IONIC_STRENGTH));
  }
}
