package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests complete label-addressable receipts for optimized refinery blend plans. */
public class RefineryBlendOptimizationPlanTest {
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;

  @Test
  public void publicOediSourcesCloseMassVolumeCostAndQualityReceipts() {
    String[] identifiers = { "DOE/OEDI sample 50146", "DOE/OEDI sample 56337" };
    double[] costsPerMass = { 1.0, 2.0 };
    double[] specificGravities = { 0.847, 0.771 };
    double[] sulfur = { 0.020, 0.005 };
    double[] nitrogen = { 0.0020, 0.0005 };
    double[] viscosities = { 550.0, 375.0 };
    double firstApi = 141.5 / specificGravities[0] - 131.5;
    double secondApi = 141.5 / specificGravities[1] - 131.5;

    RefineryLinearBlendOptimizer.Result optimization = RefineryLinearBlendOptimizer.optimizeMinimumCost(costsPerMass,
        specificGravities, sulfur, nitrogen, viscosities, 50.0, Math.min(firstApi, secondApi),
        Math.max(firstApi, secondApi), 0.014, 0.01, 375.0, 550.0);

    RefineryBlendOptimizationPlan plan = RefineryBlendOptimizationPlan.fromOptimization(identifiers, 10000.0,
        specificGravities, costsPerMass, optimization);
    RefineryBlendOptimizationPlan.SourceCostReceipt[] receipts = plan.getSourceCostReceipts();

    assertSame(optimization, plan.getOptimization());
    assertSame(optimization.getQualityConstraintReceipt(), plan.getQualityConstraintReceipt());
    assertSame(plan.getBatch(), plan.getSourceLedger().getBatch());
    assertArrayEquals(new double[] { 0.60, 0.40 }, plan.getBatch().getSourceMassFractions(), 1.0e-9);
    assertEquals(6000.0, receipts[0].getMassKg(), 1.0e-6);
    assertEquals(4000.0, receipts[1].getMassKg(), 1.0e-6);
    assertEquals(6000.0 / (0.847 * WATER_DENSITY_60F_KG_M3), receipts[0].getAdditiveVolumeM3At60F(), 1.0e-12);
    assertEquals(1.0, receipts[0].getUnitCostPerMass(), 0.0);
    assertEquals(6000.0, receipts[0].getTotalCost(), 1.0e-6);
    assertEquals(8000.0, receipts[1].getTotalCost(), 1.0e-6);
    assertEquals(1.4, plan.getBatch().getUnitCostPerMass(), 1.0e-9);
    assertEquals(14000.0, plan.getBatch().getTotalCost(), 1.0e-5);
    assertSame(receipts[1], plan.getSourceCostReceipt("DOE/OEDI sample 56337"));
    assertTrue(plan.getQualityConstraintReceipt().isSulfurBinding());

    identifiers[0] = "changed";
    costsPerMass[0] = 99.0;
    receipts[0] = null;
    assertEquals("DOE/OEDI sample 50146", plan.getSourceCostReceipts()[0].getSourceIdentifier());
    assertEquals(1.0, plan.getSourceCostReceipt("DOE/OEDI sample 50146").getUnitCostPerMass(), 0.0);
  }

  @Test
  public void zeroContributionRetainsIdentityWithZeroVolumeAndCost() {
    double[] costs = { 1.0, 2.0, 3.0 };
    double[] gravities = { 0.85, 0.80, 0.75 };
    RefineryLinearBlendOptimizer.Result optimization = RefineryLinearBlendOptimizer.optimizeMinimumCost(costs,
        gravities, new double[] { 0.030, 0.010, 0.002 }, new double[] { 0.003, 0.001, 0.0002 },
        new double[] { 600.0, 300.0, 50.0 }, 50.0, 30.0, 60.0, 0.015, 0.01, 50.0, 600.0);

    RefineryBlendOptimizationPlan plan = RefineryBlendOptimizationPlan
        .fromOptimization(new String[] { "heavy", "middle", "unused" }, 10000.0, gravities, costs, optimization);
    RefineryBlendOptimizationPlan.SourceCostReceipt unused = plan.getSourceCostReceipt("unused");

    assertFalse(unused.isContributing());
    assertEquals(0.0, unused.getMassFraction(), 0.0);
    assertEquals(0.0, unused.getMassKg(), 0.0);
    assertEquals(0.0, unused.getAdditiveVolumeM3At60F(), 0.0);
    assertEquals(3.0, unused.getUnitCostPerMass(), 0.0);
    assertEquals(0.0, unused.getTotalCost(), 0.0);
  }

  @Test
  public void invalidOrCostInconsistentInputsFailClosed() {
    double[] costs = { 1.0, 2.0, 3.0 };
    double[] gravities = { 0.85, 0.80, 0.75 };
    RefineryLinearBlendOptimizer.Result optimization = RefineryLinearBlendOptimizer.optimizeMinimumCost(costs,
        gravities, new double[] { 0.030, 0.010, 0.002 }, new double[] { 0.003, 0.001, 0.0002 },
        new double[] { 600.0, 300.0, 50.0 }, 50.0, 30.0, 60.0, 0.015, 0.01, 50.0, 600.0);
    String[] identifiers = { "a", "b", "c" };

    assertThrows(NullPointerException.class,
        () -> RefineryBlendOptimizationPlan.fromOptimization(identifiers, 10000.0, gravities, costs, null));
    assertThrows(IllegalArgumentException.class, () -> RefineryBlendOptimizationPlan
        .fromOptimization(new String[] { "a", "b" }, 10000.0, gravities, costs, optimization));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendOptimizationPlan.fromOptimization(identifiers, 10000.0, gravities, null, optimization));
    assertThrows(IllegalArgumentException.class, () -> RefineryBlendOptimizationPlan.fromOptimization(identifiers,
        10000.0, gravities, new double[] { 1.0, Double.NaN, 3.0 }, optimization));
    assertThrows(IllegalArgumentException.class, () -> RefineryBlendOptimizationPlan.fromOptimization(identifiers,
        10000.0, gravities, new double[] { 1.0, -2.0, 3.0 }, optimization));
    assertThrows(IllegalArgumentException.class, () -> RefineryBlendOptimizationPlan.fromOptimization(identifiers,
        10000.0, gravities, new double[] { 1.1, 2.0, 3.0 }, optimization));

    RefineryBlendOptimizationPlan plan = RefineryBlendOptimizationPlan.fromOptimization(identifiers, 10000.0, gravities,
        costs, optimization);
    assertThrows(IllegalArgumentException.class, () -> plan.getSourceCostReceipt("missing"));
  }
}

