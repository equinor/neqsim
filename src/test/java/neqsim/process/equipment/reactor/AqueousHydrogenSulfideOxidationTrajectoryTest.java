package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests exact piecewise propagation of the published aqueous H2S/O2 screening model. */
public class AqueousHydrogenSulfideOxidationTrajectoryTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double TEMPERATURE_K = 298.15;
  private static final double PH = 8.0;
  private static final double IONIC_STRENGTH = 0.723;
  private static final double AIR_SATURATED_OXYGEN_MOLALITY = 250.0e-6;

  @Test
  void testTwoHalfLivesGiveExactInventoryAndClosure() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(
        AIR_SATURATED_OXYGEN_MOLALITY, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Segment first = referenceSegment(halfLife);
    AqueousHydrogenSulfideOxidationTrajectory.Segment second = referenceSegment(halfLife);

    AqueousHydrogenSulfideOxidationTrajectory.Result result =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(first, second));

    assertEquals(2.0 * halfLife, result.getTotalTimeHours(), 1.0e-14);
    assertEquals(2.0 * Math.log(2.0), result.getNominalExposure(), 1.0e-14);
    assertEquals(0.25, result.getNominalRemainingFraction(), 1.0e-15);
    assertEquals(6.25e-6, result.getFinalTotalSulfideMolality(), 1.0e-20);
    assertEquals(18.75e-6, result.getReactedTotalSulfideMolality(), 1.0e-20);
    assertEquals(0.0, result.getTotalSulfideClosureResidual(), 0.0);
    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY,
        result.getFinalTotalSulfideMolality() + result.getReactedTotalSulfideMolality(),
        0.0);
  }

  @Test
  void testSegmentSplittingIsInvariant() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(
        AIR_SATURATED_OXYGEN_MOLALITY, TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.singletonList(referenceSegment(2.0 * halfLife)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY,
            Arrays.asList(referenceSegment(halfLife), referenceSegment(halfLife)));

    assertEquals(unsplit.getNominalExposure(), split.getNominalExposure(), 1.0e-15);
    assertEquals(unsplit.getFinalTotalSulfideMolality(),
        split.getFinalTotalSulfideMolality(), 1.0e-20);
    assertEquals(unsplit.getLowerRateExposure(), split.getLowerRateExposure(), 1.0e-15);
    assertEquals(unsplit.getUpperRateExposure(), split.getUpperRateExposure(), 1.0e-15);
  }

  @Test
  void testVaryingSegmentsReuseAuthoritativeSingleStateRates() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment first =
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(
            3.0, 288.15, 5.0, 0.1, 300.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.Segment second =
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(
            7.0, 310.15, 7.0, 1.5, 220.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.Result result =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(first, second));

    double expectedFirstExposure =
        AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(
            first.getAirSaturatedOxygenMolality(), first.getTemperatureK(), first.getPH(),
            first.getIonicStrengthMolPerKgWater()) * first.getDurationHours();
    double expectedSecondExposure =
        AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(
            second.getAirSaturatedOxygenMolality(), second.getTemperatureK(), second.getPH(),
            second.getIonicStrengthMolPerKgWater()) * second.getDurationHours();

    assertEquals(expectedFirstExposure,
        result.getSegmentResults().get(0).getNominalExposure(), 0.0);
    assertEquals(expectedFirstExposure + expectedSecondExposure,
        result.getNominalExposure(), 1.0e-15);
    assertEquals(result.getNominalExposure(),
        result.getSegmentResults().get(1).getCumulativeNominalExposure(), 0.0);
    assertTrue(result.getSegmentResults().get(1).getNominalSecondOrderRate()
        > result.getSegmentResults().get(0).getNominalSecondOrderRate());
  }

  @Test
  void testFitScatterEnvelopeHasCorrectPhysicalOrdering() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.singletonList(referenceSegment(24.0)));

    assertTrue(result.getLowerRateExposure() < result.getNominalExposure());
    assertTrue(result.getNominalExposure() < result.getUpperRateExposure());
    assertTrue(result.getFinalTotalSulfideMolalityAtUpperRate()
        < result.getFinalTotalSulfideMolality());
    assertTrue(result.getFinalTotalSulfideMolality()
        < result.getFinalTotalSulfideMolalityAtLowerRate());
    assertTrue(result.getUpperRateRemainingFraction()
        < result.getNominalRemainingFraction());
    assertTrue(result.getNominalRemainingFraction()
        < result.getLowerRateRemainingFraction());
  }

  @Test
  void testZeroDurationAndDeterministicRepeat() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment zero = referenceSegment(0.0);

    AqueousHydrogenSulfideOxidationTrajectory.Result first =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(zero));
    AqueousHydrogenSulfideOxidationTrajectory.Result second =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(zero));

    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY,
        first.getFinalTotalSulfideMolality(), 0.0);
    assertEquals(0.0, first.getReactedTotalSulfideMolality(), 0.0);
    assertEquals(0.0, first.getNominalExposure(), 0.0);
    assertEquals(1.0, first.getNominalRemainingFraction(), 0.0);
    assertEquals(first.getFinalTotalSulfideMolality(),
        second.getFinalTotalSulfideMolality(), 0.0);
    assertEquals(first.getNominalExposure(), second.getNominalExposure(), 0.0);
  }

  @Test
  void testResultsAreDefensiveAndSourceOrdered() {
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> source =
        new ArrayList<AqueousHydrogenSulfideOxidationTrajectory.Segment>();
    source.add(referenceSegment(1.0));
    source.add(referenceSegment(2.0));
    AqueousHydrogenSulfideOxidationTrajectory.Result result =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, source);
    source.clear();

    assertEquals(2, result.getSegmentResults().size());
    assertEquals(0, result.getSegmentResults().get(0).getIndex());
    assertEquals(1, result.getSegmentResults().get(1).getIndex());
    assertEquals(1.0,
        result.getSegmentResults().get(0).getSegment().getDurationHours(), 0.0);
    assertEquals(2.0,
        result.getSegmentResults().get(1).getSegment().getDurationHours(), 0.0);
    assertThrows(UnsupportedOperationException.class,
        () -> result.getSegmentResults().add(null));
  }

  @Test
  void testEvidenceAndNumericalInputsFailClosed() {
    assertEquals(20.0e-6,
        AqueousHydrogenSulfideOxidationTrajectory.MINIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY,
        1.0e-20);
    assertEquals(30.0e-6,
        AqueousHydrogenSulfideOxidationTrajectory.MAXIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY,
        1.0e-20);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            19.999e-6, Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            30.001e-6, Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.<AqueousHydrogenSulfideOxidationTrajectory.Segment>emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class,
        () -> new AqueousHydrogenSulfideOxidationTrajectory.Segment(
            -1.0, TEMPERATURE_K, PH, IONIC_STRENGTH,
            AIR_SATURATED_OXYGEN_MOLALITY));
    assertThrows(IllegalArgumentException.class,
        () -> new AqueousHydrogenSulfideOxidationTrajectory.Segment(
            1.0, TEMPERATURE_K, 8.01, IONIC_STRENGTH,
            AIR_SATURATED_OXYGEN_MOLALITY));

    AqueousHydrogenSulfideOxidationTrajectory.Segment overflow =
        referenceSegment(Double.MAX_VALUE);
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(overflow)));
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(
      double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(
        durationHours, TEMPERATURE_K, PH, IONIC_STRENGTH,
        AIR_SATURATED_OXYGEN_MOLALITY);
  }
}
