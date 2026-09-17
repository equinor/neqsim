package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Tests multi-source refinery quality and cost optimization. */
public class RefineryLinearBlendOptimizerTest {
  @Test
  public void threeSourceCaseSelectsAnalyticalSulfurBoundCostVertex() {
    RefineryLinearBlendOptimizer.Result result = RefineryLinearBlendOptimizer.optimizeMinimumCost(
        new double[] { 1.0, 2.0, 3.0 }, new double[] { 0.85, 0.80, 0.75 }, new double[] { 0.030, 0.010, 0.002 },
        new double[] { 0.003, 0.001, 0.0002 }, new double[] { 600.0, 300.0, 50.0 }, 50.0, 30.0, 60.0, 0.015, 0.01, 50.0,
        600.0);

    assertArrayEquals(new double[] { 0.25, 0.75, 0.0 }, result.getSourceMassFractions(), 1.0e-9);
    assertEquals(1.75, result.getUnitCostPerMass(), 1.0e-9);
    assertEquals(0.015, result.getAssayBlend().getSulfurMassFraction(), 1.0e-9);
    assertEquals(0.0015, result.getAssayBlend().getNitrogenMassFraction(), 1.0e-9);
    assertEquals(50.0, result.getViscosityBlend().getTemperatureCelsius(), 0.0);
    assertEquals(1.0, Arrays.stream(result.getSourceMassFractions()).sum(), 1.0e-12);
  }

  @Test
  public void binaryResultMatchesQualifiedEnvelopeEndpoint() {
    double[] specificGravity = { 0.847, 0.771 };
    double[] sulfur = { 0.020, 0.005 };
    double[] nitrogen = { 0.0020, 0.0005 };
    double[] viscosity = { 550.0, 375.0 };
    double minimumViscosity = viscosityAtFirstFraction(0.25, viscosity);
    double maximumSulfur = 0.60 * sulfur[0] + 0.40 * sulfur[1];
    double firstApi = 141.5 / specificGravity[0] - 131.5;
    double secondApi = 141.5 / specificGravity[1] - 131.5;
    double minimumApi = Math.min(firstApi, secondApi);
    double maximumApi = Math.max(firstApi, secondApi);

    RefineryBinaryBlendEnvelope binary = RefineryBinaryBlendEnvelope.fromQualityConstraints(specificGravity, sulfur,
        nitrogen, viscosity, 50.0, minimumApi, maximumApi, maximumSulfur, 0.01, minimumViscosity, 550.0);
    RefineryBinaryBlendEnvelope.Plan binaryPlan = binary.planMinimumCost(1.0, 2.0);
    RefineryLinearBlendOptimizer.Result linear = RefineryLinearBlendOptimizer.optimizeMinimumCost(
        new double[] { 1.0, 2.0 }, specificGravity, sulfur, nitrogen, viscosity, 50.0, minimumApi, maximumApi,
        maximumSulfur, 0.01, minimumViscosity, 550.0);

    assertArrayEquals(
        new double[] { binaryPlan.getFirstSourceMassFraction(), binaryPlan.getSecondSourceMassFraction() },
        linear.getSourceMassFractions(), 1.0e-9);
    assertEquals(binaryPlan.getUnitCostPerMass(), linear.getUnitCostPerMass(), 1.0e-9);
    assertEquals(binaryPlan.getAssayBlend().getSpecificGravity(), linear.getAssayBlend().getSpecificGravity(), 1.0e-12);
    assertEquals(binaryPlan.getViscosityBlend().getKinematicViscosityCSt(),
        linear.getViscosityBlend().getKinematicViscosityCSt(), 1.0e-9);
  }

  @Test
  public void sourceOrderReversalPreservesPhysicalOptimum() {
    RefineryLinearBlendOptimizer.Result forward = threeSourceResult(false);
    RefineryLinearBlendOptimizer.Result reversed = threeSourceResult(true);

    assertArrayEquals(reverse(forward.getSourceMassFractions()), reversed.getSourceMassFractions(), 1.0e-9);
    assertEquals(forward.getUnitCostPerMass(), reversed.getUnitCostPerMass(), 1.0e-9);
    assertEquals(forward.getAssayBlend().getSpecificGravity(), reversed.getAssayBlend().getSpecificGravity(), 1.0e-12);
    assertEquals(forward.getAssayBlend().getSulfurMassFraction(), reversed.getAssayBlend().getSulfurMassFraction(),
        1.0e-12);
    assertEquals(forward.getViscosityBlend().getKinematicViscosityCSt(),
        reversed.getViscosityBlend().getKinematicViscosityCSt(), 1.0e-9);
  }

  @Test
  public void resultFractionsAreDefensive() {
    RefineryLinearBlendOptimizer.Result result = threeSourceResult(false);
    double[] first = result.getSourceMassFractions();
    first[0] = 1.0;
    assertArrayEquals(new double[] { 0.25, 0.75, 0.0 }, result.getSourceMassFractions(), 1.0e-9);
  }

  @Test
  public void resultRetainsAuditableQualityConstraintReceipt() {
    RefineryLinearBlendOptimizer.Result result = threeSourceResult(false);
    RefineryLinearBlendOptimizer.QualityConstraintReceipt receipt = result.getQualityConstraintReceipt();

    assertEquals(result.getAssayBlend().getApiGravity(), receipt.getApiGravity(), 0.0);
    assertEquals(30.0, receipt.getMinimumApiGravity(), 0.0);
    assertEquals(60.0, receipt.getMaximumApiGravity(), 0.0);
    assertEquals(receipt.getApiGravity() - 30.0, receipt.getApiLowerMargin(), 0.0);
    assertEquals(60.0 - receipt.getApiGravity(), receipt.getApiUpperMargin(), 0.0);
    assertEquals(result.getAssayBlend().getSulfurMassFraction(), receipt.getSulfurMassFraction(), 0.0);
    assertEquals(0.015, receipt.getMaximumSulfurMassFraction(), 0.0);
    assertEquals(0.0, receipt.getSulfurMargin(), 1.0e-12);
    assertTrue(receipt.isSulfurBinding());
    assertEquals(result.getAssayBlend().getNitrogenMassFraction(), receipt.getNitrogenMassFraction(), 0.0);
    assertEquals(0.01, receipt.getMaximumNitrogenMassFraction(), 0.0);
    assertEquals(0.01 - receipt.getNitrogenMassFraction(), receipt.getNitrogenMargin(), 0.0);
    assertFalse(receipt.isNitrogenBinding());
    assertEquals(result.getViscosityBlend().getKinematicViscosityCSt(), receipt.getKinematicViscosityCSt(), 0.0);
    assertEquals(50.0, receipt.getMinimumKinematicViscosityCSt(), 0.0);
    assertEquals(600.0, receipt.getMaximumKinematicViscosityCSt(), 0.0);
    assertEquals(receipt.getKinematicViscosityCSt() - 50.0, receipt.getViscosityLowerMarginCSt(), 0.0);
    assertEquals(600.0 - receipt.getKinematicViscosityCSt(), receipt.getViscosityUpperMarginCSt(), 0.0);
    assertEquals(50.0, receipt.getTemperatureCelsius(), 0.0);
  }

  @Test
  public void sourceOrderReversalPreservesQualityConstraintReceipt() {
    RefineryLinearBlendOptimizer.QualityConstraintReceipt forward = threeSourceResult(false)
        .getQualityConstraintReceipt();
    RefineryLinearBlendOptimizer.QualityConstraintReceipt reversed = threeSourceResult(true)
        .getQualityConstraintReceipt();

    assertEquals(forward.getApiGravity(), reversed.getApiGravity(), 1.0e-12);
    assertEquals(forward.getApiLowerMargin(), reversed.getApiLowerMargin(), 1.0e-12);
    assertEquals(forward.getSulfurMargin(), reversed.getSulfurMargin(), 1.0e-12);
    assertEquals(forward.getNitrogenMargin(), reversed.getNitrogenMargin(), 1.0e-12);
    assertEquals(forward.getKinematicViscosityCSt(), reversed.getKinematicViscosityCSt(), 1.0e-9);
    assertEquals(forward.getViscosityUpperMarginCSt(), reversed.getViscosityUpperMarginCSt(), 1.0e-9);
  }

  @Test
  public void invalidAndInfeasibleProblemsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryLinearBlendOptimizer.optimizeMinimumCost(new double[] { 1.0 }, new double[] { 0.8 },
            new double[] { 0.01 }, new double[] { 0.001 }, new double[] { 100.0 }, 50.0, 0.0, 100.0, 1.0, 1.0, 1.0,
            1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryLinearBlendOptimizer.optimizeMinimumCost(new double[] { 1.0, 2.0 }, new double[] { 0.8, 0.9 },
            new double[] { 0.01 }, new double[] { 0.001, 0.002 }, new double[] { 100.0, 200.0 }, 50.0, 0.0, 100.0, 1.0,
            1.0, 1.0, 1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryLinearBlendOptimizer.optimizeMinimumCost(new double[] { Double.NaN, 2.0 },
            new double[] { 0.8, 0.9 }, new double[] { 0.01, 0.02 }, new double[] { 0.001, 0.002 },
            new double[] { 100.0, 200.0 }, 50.0, 0.0, 100.0, 1.0, 1.0, 1.0, 1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryLinearBlendOptimizer.optimizeMinimumCost(new double[] { 1.0, 2.0 }, new double[] { 0.8, 0.9 },
            new double[] { 0.01, 0.02 }, new double[] { 0.001, 0.002 }, new double[] { 100.0, 200.0 }, 50.0, 100.0, 0.0,
            1.0, 1.0, 1.0, 1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryLinearBlendOptimizer.optimizeMinimumCost(new double[] { 1.0, 2.0 }, new double[] { 0.8, 0.9 },
            new double[] { 0.01, 0.02 }, new double[] { 0.001, 0.002 }, new double[] { 100.0, 200.0 }, 50.0, 0.0, 100.0,
            0.001, 1.0, 1.0, 1000.0));
  }

  private static RefineryLinearBlendOptimizer.Result threeSourceResult(boolean reversed) {
    double[] costs = { 1.0, 2.0, 3.0 };
    double[] gravities = { 0.85, 0.80, 0.75 };
    double[] sulfur = { 0.030, 0.010, 0.002 };
    double[] nitrogen = { 0.003, 0.001, 0.0002 };
    double[] viscosities = { 600.0, 300.0, 50.0 };
    if (reversed) {
      costs = reverse(costs);
      gravities = reverse(gravities);
      sulfur = reverse(sulfur);
      nitrogen = reverse(nitrogen);
      viscosities = reverse(viscosities);
    }
    return RefineryLinearBlendOptimizer.optimizeMinimumCost(costs, gravities, sulfur, nitrogen, viscosities, 50.0, 30.0,
        60.0, 0.015, 0.01, 50.0, 600.0);
  }

  private static double viscosityAtFirstFraction(double firstFraction, double[] viscosities) {
    double firstBlendNumber = RefineryViscosityBlend.calculateViscosityBlendingNumber(viscosities[0]);
    double secondBlendNumber = RefineryViscosityBlend.calculateViscosityBlendingNumber(viscosities[1]);
    return RefineryViscosityBlend
        .calculateKinematicViscosityCSt(firstFraction * firstBlendNumber + (1.0 - firstFraction) * secondBlendNumber);
  }

  private static double[] reverse(double[] values) {
    double[] reversed = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      reversed[i] = values[values.length - i - 1];
    }
    return reversed;
  }
}
