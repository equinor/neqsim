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
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Segment first = referenceSegment(halfLife);
    AqueousHydrogenSulfideOxidationTrajectory.Segment second = referenceSegment(halfLife);

    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(first, second));

    assertEquals(2.0 * halfLife, result.getTotalTimeHours(), 1.0e-14);
    assertEquals(2.0 * Math.log(2.0), result.getNominalExposure(), 1.0e-14);
    assertEquals(0.25, result.getNominalRemainingFraction(), 1.0e-15);
    assertEquals(6.25e-6, result.getFinalTotalSulfideMolality(), 1.0e-20);
    assertEquals(18.75e-6, result.getReactedTotalSulfideMolality(), 1.0e-20);
    assertEquals(0.0, result.getTotalSulfideClosureResidual(), 0.0);
    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY,
        result.getFinalTotalSulfideMolality() + result.getReactedTotalSulfideMolality(), 0.0);
  }

  @Test
  void testSegmentSplittingIsInvariant() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(2.0 * halfLife)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(halfLife), referenceSegment(halfLife)));

    assertEquals(unsplit.getNominalExposure(), split.getNominalExposure(), 1.0e-15);
    assertEquals(unsplit.getFinalTotalSulfideMolality(), split.getFinalTotalSulfideMolality(), 1.0e-20);
    assertEquals(unsplit.getLowerRateExposure(), split.getLowerRateExposure(), 1.0e-15);
    assertEquals(unsplit.getUpperRateExposure(), split.getUpperRateExposure(), 1.0e-15);
  }

  @Test
  void testVaryingSegmentsReuseAuthoritativeSingleStateRates() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment first = new AqueousHydrogenSulfideOxidationTrajectory.Segment(3.0,
        288.15, 5.0, 0.1, 300.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.Segment second = new AqueousHydrogenSulfideOxidationTrajectory.Segment(
        7.0, 310.15, 7.0, 1.5, 220.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(first, second));

    double expectedFirstExposure = AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(
        first.getAirSaturatedOxygenMolality(), first.getTemperatureK(), first.getPH(),
        first.getIonicStrengthMolPerKgWater()) * first.getDurationHours();
    double expectedSecondExposure = AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(
        second.getAirSaturatedOxygenMolality(), second.getTemperatureK(), second.getPH(),
        second.getIonicStrengthMolPerKgWater()) * second.getDurationHours();

    assertEquals(expectedFirstExposure, result.getSegmentResults().get(0).getNominalExposure(), 0.0);
    assertEquals(expectedFirstExposure + expectedSecondExposure, result.getNominalExposure(), 1.0e-15);
    assertEquals(result.getNominalExposure(), result.getSegmentResults().get(1).getCumulativeNominalExposure(), 0.0);
    assertTrue(result.getSegmentResults().get(1).getNominalSecondOrderRate() > result.getSegmentResults().get(0)
        .getNominalSecondOrderRate());
  }

  @Test
  void testFitScatterEnvelopeHasCorrectPhysicalOrdering() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(24.0)));

    assertTrue(result.getLowerRateExposure() < result.getNominalExposure());
    assertTrue(result.getNominalExposure() < result.getUpperRateExposure());
    assertTrue(result.getFinalTotalSulfideMolalityAtUpperRate() < result.getFinalTotalSulfideMolality());
    assertTrue(result.getFinalTotalSulfideMolality() < result.getFinalTotalSulfideMolalityAtLowerRate());
    assertTrue(result.getUpperRateRemainingFraction() < result.getNominalRemainingFraction());
    assertTrue(result.getNominalRemainingFraction() < result.getLowerRateRemainingFraction());
  }

  @Test
  void testZeroDurationAndDeterministicRepeat() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment zero = referenceSegment(0.0);

    AqueousHydrogenSulfideOxidationTrajectory.Result first = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(zero));
    AqueousHydrogenSulfideOxidationTrajectory.Result second = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(zero));

    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY, first.getFinalTotalSulfideMolality(), 0.0);
    assertEquals(0.0, first.getReactedTotalSulfideMolality(), 0.0);
    assertEquals(0.0, first.getNominalExposure(), 0.0);
    assertEquals(1.0, first.getNominalRemainingFraction(), 0.0);
    assertEquals(first.getFinalTotalSulfideMolality(), second.getFinalTotalSulfideMolality(), 0.0);
    assertEquals(first.getNominalExposure(), second.getNominalExposure(), 0.0);
  }

  @Test
  void testResultsAreDefensiveAndSourceOrdered() {
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> source = new ArrayList<AqueousHydrogenSulfideOxidationTrajectory.Segment>();
    source.add(referenceSegment(1.0));
    source.add(referenceSegment(2.0));
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, source);
    source.clear();

    assertEquals(2, result.getSegmentResults().size());
    assertEquals(0, result.getSegmentResults().get(0).getIndex());
    assertEquals(1, result.getSegmentResults().get(1).getIndex());
    assertEquals(1.0, result.getSegmentResults().get(0).getSegment().getDurationHours(), 0.0);
    assertEquals(2.0, result.getSegmentResults().get(1).getSegment().getDurationHours(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> result.getSegmentResults().add(null));
  }

  @Test
  void testEvidenceAndNumericalInputsFailClosed() {
    assertEquals(20.0e-6, AqueousHydrogenSulfideOxidationTrajectory.MINIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY, 1.0e-20);
    assertEquals(30.0e-6, AqueousHydrogenSulfideOxidationTrajectory.MAXIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY, 1.0e-20);

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory.advance(19.999e-6,
        Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory.advance(30.001e-6,
        Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(INITIAL_TOTAL_SULFIDE_MOLALITY, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.advance(INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.<AqueousHydrogenSulfideOxidationTrajectory.Segment>emptyList()));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class, () -> new AqueousHydrogenSulfideOxidationTrajectory.Segment(-1.0,
        TEMPERATURE_K, PH, IONIC_STRENGTH, AIR_SATURATED_OXYGEN_MOLALITY));
    assertThrows(IllegalArgumentException.class, () -> new AqueousHydrogenSulfideOxidationTrajectory.Segment(1.0,
        TEMPERATURE_K, 8.01, IONIC_STRENGTH, AIR_SATURATED_OXYGEN_MOLALITY));

    AqueousHydrogenSulfideOxidationTrajectory.Segment overflow = referenceSegment(Double.MAX_VALUE);
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(overflow, overflow)));
  }

  @Test
  void testSegmentInventoryTelescopesAndClosesForEveryRatePath() {
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments = Arrays.asList(referenceSegment(3.0),
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(7.0, 310.15, 7.0, 1.5, 220.0e-6), referenceSegment(5.0));
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, segments);

    double lowerReacted = 0.0;
    double nominalReacted = 0.0;
    double upperReacted = 0.0;
    List<AqueousHydrogenSulfideOxidationTrajectory.SegmentResult> evidence = result.getSegmentResults();
    for (int index = 0; index < evidence.size(); index++) {
      AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = evidence.get(index);
      assertEquals(segment.getLowerRateInletTotalSulfideMolality(),
          segment.getLowerRateOutletTotalSulfideMolality() + segment.getLowerRateReactedTotalSulfideMolality(),
          1.0e-20);
      assertEquals(segment.getNominalInletTotalSulfideMolality(),
          segment.getNominalOutletTotalSulfideMolality() + segment.getNominalReactedTotalSulfideMolality(), 1.0e-20);
      assertEquals(segment.getUpperRateInletTotalSulfideMolality(),
          segment.getUpperRateOutletTotalSulfideMolality() + segment.getUpperRateReactedTotalSulfideMolality(),
          1.0e-20);
      assertTrue(segment.getLowerRateOutletTotalSulfideMolality() >= segment.getNominalOutletTotalSulfideMolality());
      assertTrue(segment.getNominalOutletTotalSulfideMolality() >= segment.getUpperRateOutletTotalSulfideMolality());
      if (index > 0) {
        AqueousHydrogenSulfideOxidationTrajectory.SegmentResult previous = evidence.get(index - 1);
        assertEquals(previous.getLowerRateOutletTotalSulfideMolality(), segment.getLowerRateInletTotalSulfideMolality(),
            0.0);
        assertEquals(previous.getNominalOutletTotalSulfideMolality(), segment.getNominalInletTotalSulfideMolality(),
            0.0);
        assertEquals(previous.getUpperRateOutletTotalSulfideMolality(), segment.getUpperRateInletTotalSulfideMolality(),
            0.0);
      }
      lowerReacted += segment.getLowerRateReactedTotalSulfideMolality();
      nominalReacted += segment.getNominalReactedTotalSulfideMolality();
      upperReacted += segment.getUpperRateReactedTotalSulfideMolality();
    }

    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY - result.getFinalTotalSulfideMolalityAtLowerRate(), lowerReacted,
        1.0e-20);
    assertEquals(result.getReactedTotalSulfideMolality(), nominalReacted, 1.0e-20);
    assertEquals(INITIAL_TOTAL_SULFIDE_MOLALITY - result.getFinalTotalSulfideMolalityAtUpperRate(), upperReacted,
        1.0e-20);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult last = evidence.get(evidence.size() - 1);
    assertEquals(result.getFinalTotalSulfideMolalityAtLowerRate(), last.getLowerRateOutletTotalSulfideMolality(), 0.0);
    assertEquals(result.getFinalTotalSulfideMolality(), last.getNominalOutletTotalSulfideMolality(), 0.0);
    assertEquals(result.getFinalTotalSulfideMolalityAtUpperRate(), last.getUpperRateOutletTotalSulfideMolality(), 0.0);
  }

  @Test
  void testZeroDurationSegmentPreservesEveryInventoryPath() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory.advance(
        INITIAL_TOTAL_SULFIDE_MOLALITY,
        Arrays.asList(referenceSegment(halfLife), referenceSegment(0.0), referenceSegment(halfLife)));
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult identity = result.getSegmentResults().get(1);

    assertEquals(identity.getLowerRateInletTotalSulfideMolality(), identity.getLowerRateOutletTotalSulfideMolality(),
        0.0);
    assertEquals(identity.getNominalInletTotalSulfideMolality(), identity.getNominalOutletTotalSulfideMolality(), 0.0);
    assertEquals(identity.getUpperRateInletTotalSulfideMolality(), identity.getUpperRateOutletTotalSulfideMolality(),
        0.0);
    assertEquals(0.0, identity.getLowerRateReactedTotalSulfideMolality(), 0.0);
    assertEquals(0.0, identity.getNominalReactedTotalSulfideMolality(), 0.0);
    assertEquals(0.0, identity.getUpperRateReactedTotalSulfideMolality(), 0.0);
  }

  @Test
  void testSegmentInventoryIsSplitInvariant() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(2.0 * halfLife)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(halfLife), referenceSegment(halfLife)));

    double splitLowerReacted = 0.0;
    double splitNominalReacted = 0.0;
    double splitUpperReacted = 0.0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : split.getSegmentResults()) {
      splitLowerReacted += segment.getLowerRateReactedTotalSulfideMolality();
      splitNominalReacted += segment.getNominalReactedTotalSulfideMolality();
      splitUpperReacted += segment.getUpperRateReactedTotalSulfideMolality();
    }
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult unsplitSegment = unsplit.getSegmentResults().get(0);

    assertEquals(unsplitSegment.getLowerRateReactedTotalSulfideMolality(), splitLowerReacted, 1.0e-20);
    assertEquals(unsplitSegment.getNominalReactedTotalSulfideMolality(), splitNominalReacted, 1.0e-20);
    assertEquals(unsplitSegment.getUpperRateReactedTotalSulfideMolality(), splitUpperReacted, 1.0e-20);
    assertEquals(unsplit.getFinalTotalSulfideMolality(), split.getFinalTotalSulfideMolality(), 1.0e-20);
  }

  @Test
  void testSegmentEndpointLossRatesMatchDifferentialEquation() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(3.0),
            new AqueousHydrogenSulfideOxidationTrajectory.Segment(7.0, 310.15, 7.0, 1.5, 220.0e-6)));

    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : result.getSegmentResults()) {
      assertEquals(segment.getLowerPseudoFirstOrderRate() * segment.getLowerRateInletTotalSulfideMolality(),
          segment.getLowerRateInletLossRateMolalityPerHour(), 0.0);
      assertEquals(segment.getLowerPseudoFirstOrderRate() * segment.getLowerRateOutletTotalSulfideMolality(),
          segment.getLowerRateOutletLossRateMolalityPerHour(), 0.0);
      assertEquals(segment.getNominalPseudoFirstOrderRate() * segment.getNominalInletTotalSulfideMolality(),
          segment.getNominalInletLossRateMolalityPerHour(), 0.0);
      assertEquals(segment.getNominalPseudoFirstOrderRate() * segment.getNominalOutletTotalSulfideMolality(),
          segment.getNominalOutletLossRateMolalityPerHour(), 0.0);
      assertEquals(segment.getUpperPseudoFirstOrderRate() * segment.getUpperRateInletTotalSulfideMolality(),
          segment.getUpperRateInletLossRateMolalityPerHour(), 0.0);
      assertEquals(segment.getUpperPseudoFirstOrderRate() * segment.getUpperRateOutletTotalSulfideMolality(),
          segment.getUpperRateOutletLossRateMolalityPerHour(), 0.0);
      assertTrue(
          segment.getLowerRateOutletLossRateMolalityPerHour() <= segment.getLowerRateInletLossRateMolalityPerHour());
      assertTrue(segment.getNominalOutletLossRateMolalityPerHour() <= segment.getNominalInletLossRateMolalityPerHour());
      assertTrue(
          segment.getUpperRateOutletLossRateMolalityPerHour() <= segment.getUpperRateInletLossRateMolalityPerHour());
    }
  }

  @Test
  void testSegmentMeanLossRatesCloseInventoryAndAreBounded() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(3.0),
            new AqueousHydrogenSulfideOxidationTrajectory.Segment(7.0, 310.15, 7.0, 1.5, 220.0e-6)));

    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : result.getSegmentResults()) {
      double durationHours = segment.getSegment().getDurationHours();
      assertMeanLossRate(segment.getLowerRateReactedTotalSulfideMolality(), durationHours,
          segment.getLowerRateMeanLossRateMolalityPerHour(), segment.getLowerRateInletLossRateMolalityPerHour(),
          segment.getLowerRateOutletLossRateMolalityPerHour());
      assertMeanLossRate(segment.getNominalReactedTotalSulfideMolality(), durationHours,
          segment.getNominalMeanLossRateMolalityPerHour(), segment.getNominalInletLossRateMolalityPerHour(),
          segment.getNominalOutletLossRateMolalityPerHour());
      assertMeanLossRate(segment.getUpperRateReactedTotalSulfideMolality(), durationHours,
          segment.getUpperRateMeanLossRateMolalityPerHour(), segment.getUpperRateInletLossRateMolalityPerHour(),
          segment.getUpperRateOutletLossRateMolalityPerHour());
    }
  }

  @Test
  void testSegmentRetentionFactorsCloseInventoryUpdate() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(3.0),
            new AqueousHydrogenSulfideOxidationTrajectory.Segment(7.0, 310.15, 7.0, 1.5, 220.0e-6)));

    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : result.getSegmentResults()) {
      assertEquals(segment.getLowerRateOutletTotalSulfideMolality(),
          segment.getLowerRateInletTotalSulfideMolality() * segment.getLowerRateRetentionFactor(), 1.0e-20);
      assertEquals(segment.getNominalOutletTotalSulfideMolality(),
          segment.getNominalInletTotalSulfideMolality() * segment.getNominalRetentionFactor(), 1.0e-20);
      assertEquals(segment.getUpperRateOutletTotalSulfideMolality(),
          segment.getUpperRateInletTotalSulfideMolality() * segment.getUpperRateRetentionFactor(), 1.0e-20);
      assertTrue(segment.getLowerRateRetentionFactor() >= 0.0);
      assertTrue(segment.getLowerRateRetentionFactor() <= 1.0);
      assertTrue(segment.getNominalRetentionFactor() >= 0.0);
      assertTrue(segment.getNominalRetentionFactor() <= 1.0);
      assertTrue(segment.getUpperRateRetentionFactor() >= 0.0);
      assertTrue(segment.getUpperRateRetentionFactor() <= 1.0);
    }
  }

  @Test
  void testZeroDurationEndpointRatesAndRetentionAreExact() {
    AqueousHydrogenSulfideOxidationTrajectory.Result result = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(3.0), referenceSegment(0.0)));
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult identity = result.getSegmentResults().get(1);

    assertEquals(1.0, identity.getLowerRateRetentionFactor(), 0.0);
    assertEquals(1.0, identity.getNominalRetentionFactor(), 0.0);
    assertEquals(1.0, identity.getUpperRateRetentionFactor(), 0.0);
    assertEquals(identity.getLowerRateInletLossRateMolalityPerHour(),
        identity.getLowerRateOutletLossRateMolalityPerHour(), 0.0);
    assertEquals(identity.getNominalInletLossRateMolalityPerHour(), identity.getNominalOutletLossRateMolalityPerHour(),
        0.0);
    assertEquals(identity.getUpperRateInletLossRateMolalityPerHour(),
        identity.getUpperRateOutletLossRateMolalityPerHour(), 0.0);
    assertEquals(identity.getLowerRateInletLossRateMolalityPerHour(),
        identity.getLowerRateMeanLossRateMolalityPerHour(), 0.0);
    assertEquals(identity.getNominalInletLossRateMolalityPerHour(), identity.getNominalMeanLossRateMolalityPerHour(),
        0.0);
    assertEquals(identity.getUpperRateInletLossRateMolalityPerHour(),
        identity.getUpperRateMeanLossRateMolalityPerHour(), 0.0);
  }

  @Test
  void testRetentionFactorProductIsSplitInvariant() {
    double halfLife = AqueousHydrogenSulfideOxidationKinetics.halfLifeHours(AIR_SATURATED_OXYGEN_MOLALITY,
        TEMPERATURE_K, PH, IONIC_STRENGTH);
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(2.0 * halfLife)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(halfLife), referenceSegment(halfLife)));

    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult whole = unsplit.getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult first = split.getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult second = split.getSegmentResults().get(1);
    assertEquals(whole.getLowerRateRetentionFactor(),
        first.getLowerRateRetentionFactor() * second.getLowerRateRetentionFactor(), 1.0e-15);
    assertEquals(whole.getNominalRetentionFactor(),
        first.getNominalRetentionFactor() * second.getNominalRetentionFactor(), 1.0e-15);
    assertEquals(whole.getUpperRateRetentionFactor(),
        first.getUpperRateRetentionFactor() * second.getUpperRateRetentionFactor(), 1.0e-15);
    assertEquals(unsplit.getFinalTotalSulfideMolality(), split.getFinalTotalSulfideMolality(), 1.0e-20);
  }

  @Test
  void testDurationWeightedMeanLossRateIsSplitInvariant() {
    double durationHours = 10.0;
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(durationHours)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory.advance(
        INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(durationHours - 4.0)));

    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult whole = unsplit.getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult first = split.getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult second = split.getSegmentResults().get(1);

    assertEquals(whole.getLowerRateMeanLossRateMolalityPerHour() * durationHours,
        first.getLowerRateMeanLossRateMolalityPerHour() * 4.0
            + second.getLowerRateMeanLossRateMolalityPerHour() * (durationHours - 4.0),
        1.0e-20);
    assertEquals(whole.getNominalMeanLossRateMolalityPerHour() * durationHours,
        first.getNominalMeanLossRateMolalityPerHour() * 4.0
            + second.getNominalMeanLossRateMolalityPerHour() * (durationHours - 4.0),
        1.0e-20);
    assertEquals(whole.getUpperRateMeanLossRateMolalityPerHour() * durationHours,
        first.getUpperRateMeanLossRateMolalityPerHour() * 4.0
            + second.getUpperRateMeanLossRateMolalityPerHour() * (durationHours - 4.0),
        1.0e-20);
    assertEquals(unsplit.getFinalTotalSulfideMolality(), split.getFinalTotalSulfideMolality(), 1.0e-20);
  }

  @Test
  void testPiecewiseTargetCrossingMatchesSingleStateInverse() {
    AqueousHydrogenSulfideOxidationKinetics.TargetTimeRangeResult single = AqueousHydrogenSulfideOxidationKinetics
        .timeToRemainingFractionRange(AIR_SATURATED_OXYGEN_MOLALITY, 0.5, TEMPERATURE_K, PH, IONIC_STRENGTH);
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments = Collections
        .singletonList(referenceSegment(single.getLongestRequiredTimeHours()));

    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult crossing = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, segments);

    assertEquals(0.5, crossing.getTargetRemainingFraction(), 0.0);
    assertEquals(Math.log(2.0), crossing.getRequiredExposure(), 0.0);
    assertEquals(single.getShortestRequiredTimeHours(), crossing.getShortestTimeHours(), 1.0e-14);
    assertEquals(single.getNominalRequiredTimeHours(), crossing.getNominalTimeHours(), 1.0e-14);
    assertEquals(single.getLongestRequiredTimeHours(), crossing.getLongestTimeHours(), 1.0e-14);
    assertEquals(0, crossing.getShortestCrossingSegmentIndex());
    assertEquals(0, crossing.getNominalCrossingSegmentIndex());
    assertEquals(0, crossing.getLongestCrossingSegmentIndex());
    assertEquals(single.getLongestRequiredTimeHours(), crossing.getSuppliedTrajectoryTimeHours(), 0.0);
  }

  @Test
  void testPiecewiseTargetCrossingMatchesForwardExposure() {
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments = Arrays.asList(referenceSegment(10.0),
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(100.0, 310.15, 7.0, 1.5, 220.0e-6));
    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult crossing = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, segments);

    assertEquals(crossing.getRequiredExposure(), exposureAtTime(segments, crossing.getShortestTimeHours(), 2), 1.0e-14);
    assertEquals(crossing.getRequiredExposure(), exposureAtTime(segments, crossing.getNominalTimeHours(), 1), 1.0e-14);
    assertEquals(crossing.getRequiredExposure(), exposureAtTime(segments, crossing.getLongestTimeHours(), 0), 1.0e-14);
    assertTrue(crossing.getShortestTimeHours() < crossing.getNominalTimeHours());
    assertTrue(crossing.getNominalTimeHours() < crossing.getLongestTimeHours());
    assertTrue(crossing.getShortestCrossingSegmentIndex() <= crossing.getNominalCrossingSegmentIndex());
    assertTrue(crossing.getNominalCrossingSegmentIndex() <= crossing.getLongestCrossingSegmentIndex());
  }

  @Test
  void testTargetCrossingIsSplitInvariantMonotonicAndDeterministic() {
    double longestHalfLife = AqueousHydrogenSulfideOxidationKinetics
        .timeToRemainingFractionRange(AIR_SATURATED_OXYGEN_MOLALITY, 0.5, TEMPERATURE_K, PH, IONIC_STRENGTH)
        .getLongestRequiredTimeHours();
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> unsplit = Collections
        .singletonList(referenceSegment(2.0 * longestHalfLife));
    List<AqueousHydrogenSulfideOxidationTrajectory.Segment> split = Arrays.asList(referenceSegment(longestHalfLife),
        referenceSegment(longestHalfLife));

    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult loose = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.8, split);
    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult strict = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, split);
    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult repeat = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, split);
    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult unsplitResult = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, unsplit);

    assertTrue(loose.getShortestTimeHours() < strict.getShortestTimeHours());
    assertTrue(loose.getNominalTimeHours() < strict.getNominalTimeHours());
    assertTrue(loose.getLongestTimeHours() < strict.getLongestTimeHours());
    assertEquals(unsplitResult.getShortestTimeHours(), strict.getShortestTimeHours(), 1.0e-14);
    assertEquals(unsplitResult.getNominalTimeHours(), strict.getNominalTimeHours(), 1.0e-14);
    assertEquals(unsplitResult.getLongestTimeHours(), strict.getLongestTimeHours(), 1.0e-14);
    assertEquals(strict.getShortestTimeHours(), repeat.getShortestTimeHours(), 0.0);
    assertEquals(strict.getNominalTimeHours(), repeat.getNominalTimeHours(), 0.0);
    assertEquals(strict.getLongestTimeHours(), repeat.getLongestTimeHours(), 0.0);
  }

  @Test
  void testTargetCrossingIdentityAndInvalidTrajectoryFailClosed() {
    AqueousHydrogenSulfideOxidationTrajectory.TargetCrossingRangeResult identity = AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(1.0, Collections.singletonList(referenceSegment(0.0)));

    assertEquals(0.0, identity.getRequiredExposure(), 0.0);
    assertEquals(0.0, identity.getShortestTimeHours(), 0.0);
    assertEquals(0.0, identity.getNominalTimeHours(), 0.0);
    assertEquals(0.0, identity.getLongestTimeHours(), 0.0);
    assertEquals(0, identity.getShortestCrossingSegmentIndex());
    assertEquals(0, identity.getNominalCrossingSegmentIndex());
    assertEquals(0, identity.getLongestCrossingSegmentIndex());

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.0, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(1.01, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(Double.NaN, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationTrajectory.timeToRemainingFractionRange(0.5, null));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationTrajectory
        .timeToRemainingFractionRange(0.5, Collections.<AqueousHydrogenSulfideOxidationTrajectory.Segment>emptyList()));
  }

  private static void assertMeanLossRate(double reactedMolality, double durationHours, double meanLossRate,
      double inletLossRate, double outletLossRate) {
    assertEquals(reactedMolality / durationHours, meanLossRate, 0.0);
    assertEquals(reactedMolality, meanLossRate * durationHours, 1.0e-20);
    assertTrue(meanLossRate <= inletLossRate);
    assertTrue(meanLossRate >= outletLossRate);
  }

  private static double exposureAtTime(List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments,
      double timeHours, int rateCase) {
    double remainingTime = timeHours;
    double exposure = 0.0;
    for (AqueousHydrogenSulfideOxidationTrajectory.Segment segment : segments) {
      double duration = Math.min(remainingTime, segment.getDurationHours());
      AqueousHydrogenSulfideOxidationKinetics.RateConstantRange range = AqueousHydrogenSulfideOxidationKinetics
          .secondOrderRateConstantRange(segment.getTemperatureK(), segment.getPH(),
              segment.getIonicStrengthMolPerKgWater());
      double secondOrderRate = rateCase == 0 ? range.getLower() : rateCase == 1 ? range.getNominal() : range.getUpper();
      exposure += secondOrderRate * segment.getAirSaturatedOxygenMolality() * duration;
      remainingTime -= duration;
      if (remainingTime <= 0.0) {
        break;
      }
    }
    return exposure;
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, TEMPERATURE_K, PH, IONIC_STRENGTH,
        AIR_SATURATED_OXYGEN_MOLALITY);
  }
}
