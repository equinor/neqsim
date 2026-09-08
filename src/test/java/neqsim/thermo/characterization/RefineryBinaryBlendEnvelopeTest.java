package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests binary refinery quality-envelope and minimum-cost planning. */
public class RefineryBinaryBlendEnvelopeTest {
  private static final double[] SOURCE_SPECIFIC_GRAVITIES = { 0.847, 0.771 };
  private static final double[] SOURCE_SULFUR_MASS_FRACTIONS = { 0.020, 0.005 };
  private static final double[] SOURCE_NITROGEN_MASS_FRACTIONS = { 0.0020, 0.0005 };
  private static final double[] SOURCE_VISCOSITIES_CST = { 550.0, 375.0 };

  @Test
  public void independentConstraintsRecoverAnalyticalFeasibleIntervalAndCostEndpoints() {
    double minimumFirstFraction = 0.25;
    double maximumFirstFraction = 0.60;
    double minimumViscosity = viscosityAtFirstFraction(minimumFirstFraction);
    double maximumSulfur = sulfurAtFirstFraction(maximumFirstFraction);

    RefineryBinaryBlendEnvelope envelope = RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
        SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0,
        Math.min(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)),
        Math.max(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)), maximumSulfur, 0.01, minimumViscosity, 550.0);

    assertEquals(minimumFirstFraction, envelope.getMinimumFirstSourceMassFraction(), 1.0e-14);
    assertEquals(maximumFirstFraction, envelope.getMaximumFirstSourceMassFraction(), 1.0e-14);
    assertEquals(50.0, envelope.getTemperatureCelsius(), 0.0);

    RefineryBinaryBlendEnvelope.Plan firstSourceCheaper = envelope.planMinimumCost(1.0, 2.0);
    RefineryBinaryBlendEnvelope.Plan secondSourceCheaper = envelope.planMinimumCost(3.0, 2.0);
    assertPlan(firstSourceCheaper, maximumFirstFraction, 1.4);
    assertPlan(secondSourceCheaper, minimumFirstFraction, 2.25);
  }

  @Test
  public void sourceOrderReversalReversesEnvelopeAndPreservesPhysicalPlans() {
    RefineryBinaryBlendEnvelope forward = createEnvelope(0.25, 0.60);
    RefineryBinaryBlendEnvelope reversed = RefineryBinaryBlendEnvelope.fromQualityConstraints(
        reverse(SOURCE_SPECIFIC_GRAVITIES), reverse(SOURCE_SULFUR_MASS_FRACTIONS),
        reverse(SOURCE_NITROGEN_MASS_FRACTIONS), reverse(SOURCE_VISCOSITIES_CST), 50.0,
        Math.min(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)),
        Math.max(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)), sulfurAtFirstFraction(0.60), 0.01,
        viscosityAtFirstFraction(0.25), 550.0);

    assertEquals(0.40, reversed.getMinimumFirstSourceMassFraction(), 1.0e-14);
    assertEquals(0.75, reversed.getMaximumFirstSourceMassFraction(), 1.0e-14);
    RefineryBinaryBlendEnvelope.Plan forwardPlan = forward.evaluateAtFirstSourceMassFraction(0.60);
    RefineryBinaryBlendEnvelope.Plan reversedPlan = reversed.evaluateAtFirstSourceMassFraction(0.40);
    assertEquals(forwardPlan.getAssayBlend().getSpecificGravity(), reversedPlan.getAssayBlend().getSpecificGravity(),
        1.0e-15);
    assertEquals(forwardPlan.getAssayBlend().getSulfurMassFraction(),
        reversedPlan.getAssayBlend().getSulfurMassFraction(), 1.0e-15);
    assertEquals(forwardPlan.getViscosityBlend().getKinematicViscosityCSt(),
        reversedPlan.getViscosityBlend().getKinematicViscosityCSt(), 1.0e-12);
  }

  @Test
  public void exactConstraintCanProduceSinglePointAndEqualCostPlan() {
    double fraction = 0.40;
    double exactApi = apiAtFirstFraction(fraction);
    RefineryBinaryBlendEnvelope envelope = RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
        SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0, exactApi, exactApi,
        1.0, 1.0, 375.0, 550.0);

    assertEquals(fraction, envelope.getMinimumFirstSourceMassFraction(), 1.0e-14);
    assertEquals(fraction, envelope.getMaximumFirstSourceMassFraction(), 1.0e-14);
    RefineryBinaryBlendEnvelope.Plan plan = envelope.planMinimumCost(2.0, 2.0);
    assertEquals(fraction, plan.getFirstSourceMassFraction(), 1.0e-14);
    assertEquals(2.0, plan.getUnitCostPerMass(), 0.0);
  }

  @Test
  public void returnedSourcesAreDefensiveAndUncostedPlansFailClosedForCost() {
    RefineryBinaryBlendEnvelope envelope = createEnvelope(0.25, 0.60);
    double[] gravities = envelope.getSourceSpecificGravities();
    double[] sulfur = envelope.getSourceSulfurMassFractions();
    double[] nitrogen = envelope.getSourceNitrogenMassFractions();
    double[] viscosities = envelope.getSourceKinematicViscositiesCSt();
    gravities[0] = 1.0;
    sulfur[0] = 1.0;
    nitrogen[0] = 1.0;
    viscosities[0] = 1.0;

    assertArrayEquals(SOURCE_SPECIFIC_GRAVITIES, envelope.getSourceSpecificGravities(), 0.0);
    assertArrayEquals(SOURCE_SULFUR_MASS_FRACTIONS, envelope.getSourceSulfurMassFractions(), 0.0);
    assertArrayEquals(SOURCE_NITROGEN_MASS_FRACTIONS, envelope.getSourceNitrogenMassFractions(), 0.0);
    assertArrayEquals(SOURCE_VISCOSITIES_CST, envelope.getSourceKinematicViscositiesCSt(), 0.0);

    RefineryBinaryBlendEnvelope.Plan plan = envelope.evaluateAtFirstSourceMassFraction(0.40);
    assertFalse(plan.hasUnitCost());
    assertThrows(IllegalStateException.class, plan::getUnitCostPerMass);
    assertArrayEquals(new double[] { 0.40, 0.60 }, plan.getAssayBlend().getMassFractions(), 1.0e-15);
  }

  @Test
  public void infeasibleInvalidAndNonUniqueInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
            SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0, 60.0, 70.0, 1.0,
            1.0, 375.0, 550.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBinaryBlendEnvelope.fromQualityConstraints(new double[] { 0.8 }, SOURCE_SULFUR_MASS_FRACTIONS,
            SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0, -100.0, 100.0, 1.0, 1.0, 375.0, 550.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
            SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, Double.NaN, -100.0,
            100.0, 1.0, 1.0, 375.0, 550.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
            SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0, 10.0, 0.0, 1.0,
            1.0, 375.0, 550.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES,
            SOURCE_SULFUR_MASS_FRACTIONS, SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0, -100.0, 100.0,
            1.1, 1.0, 375.0, 550.0));

    RefineryBinaryBlendEnvelope envelope = createEnvelope(0.25, 0.60);
    assertThrows(IllegalArgumentException.class, () -> envelope.evaluateAtFirstSourceMassFraction(0.20));
    assertThrows(IllegalArgumentException.class, () -> envelope.evaluateAtFirstSourceMassFraction(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> envelope.planMinimumCost(-1.0, 2.0));
    assertThrows(IllegalArgumentException.class, () -> envelope.planMinimumCost(2.0, 2.0));
  }

  private static RefineryBinaryBlendEnvelope createEnvelope(double minimumFirstFraction, double maximumFirstFraction) {
    return RefineryBinaryBlendEnvelope.fromQualityConstraints(SOURCE_SPECIFIC_GRAVITIES, SOURCE_SULFUR_MASS_FRACTIONS,
        SOURCE_NITROGEN_MASS_FRACTIONS, SOURCE_VISCOSITIES_CST, 50.0,
        Math.min(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)),
        Math.max(apiAtFirstFraction(0.0), apiAtFirstFraction(1.0)), sulfurAtFirstFraction(maximumFirstFraction), 0.01,
        viscosityAtFirstFraction(minimumFirstFraction), 550.0);
  }

  private static void assertPlan(RefineryBinaryBlendEnvelope.Plan plan, double expectedFirstFraction,
      double expectedCost) {
    assertTrue(plan.hasUnitCost());
    assertEquals(expectedFirstFraction, plan.getFirstSourceMassFraction(), 1.0e-14);
    assertEquals(1.0 - expectedFirstFraction, plan.getSecondSourceMassFraction(), 1.0e-14);
    assertEquals(expectedCost, plan.getUnitCostPerMass(), 1.0e-14);
    assertEquals(apiAtFirstFraction(expectedFirstFraction), plan.getAssayBlend().getApiGravity(), 1.0e-13);
    assertEquals(sulfurAtFirstFraction(expectedFirstFraction), plan.getAssayBlend().getSulfurMassFraction(), 1.0e-15);
    assertEquals(viscosityAtFirstFraction(expectedFirstFraction), plan.getViscosityBlend().getKinematicViscosityCSt(),
        1.0e-12);
    assertEquals(50.0, plan.getViscosityBlend().getTemperatureCelsius(), 0.0);
  }

  private static double apiAtFirstFraction(double firstFraction) {
    return firstFraction * (141.5 / SOURCE_SPECIFIC_GRAVITIES[0] - 131.5)
        + (1.0 - firstFraction) * (141.5 / SOURCE_SPECIFIC_GRAVITIES[1] - 131.5);
  }

  private static double sulfurAtFirstFraction(double firstFraction) {
    return firstFraction * SOURCE_SULFUR_MASS_FRACTIONS[0] + (1.0 - firstFraction) * SOURCE_SULFUR_MASS_FRACTIONS[1];
  }

  private static double viscosityAtFirstFraction(double firstFraction) {
    double firstBlendNumber = RefineryViscosityBlend.calculateViscosityBlendingNumber(SOURCE_VISCOSITIES_CST[0]);
    double secondBlendNumber = RefineryViscosityBlend.calculateViscosityBlendingNumber(SOURCE_VISCOSITIES_CST[1]);
    return RefineryViscosityBlend
        .calculateKinematicViscosityCSt(firstFraction * firstBlendNumber + (1.0 - firstFraction) * secondBlendNumber);
  }

  private static double[] reverse(double[] values) {
    return new double[] { values[1], values[0] };
  }
}
