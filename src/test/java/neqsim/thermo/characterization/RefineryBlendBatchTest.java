package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests auditable source receipts for refinery blend batches. */
public class RefineryBlendBatchTest {
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;

  @Test
  public void publicOediEndpointsProduceClosedMassAndAdditiveVolumeReceipt() {
    RefineryBlendBatch batch = RefineryBlendBatch.fromMassFractions(10000.0, new double[] { 0.60, 0.40 },
        new double[] { 0.847, 0.771 });

    double[] expectedMassesKg = { 6000.0, 4000.0 };
    double[] expectedVolumesM3 = { 6000.0 / (0.847 * WATER_DENSITY_60F_KG_M3),
        4000.0 / (0.771 * WATER_DENSITY_60F_KG_M3) };
    double expectedTotalVolumeM3 = expectedVolumesM3[0] + expectedVolumesM3[1];
    double expectedSpecificGravity = 10000.0 / (expectedTotalVolumeM3 * WATER_DENSITY_60F_KG_M3);

    assertEquals(10000.0, batch.getTotalMassKg(), 0.0);
    assertArrayEquals(new double[] { 0.60, 0.40 }, batch.getSourceMassFractions(), 1.0e-15);
    assertArrayEquals(expectedMassesKg, batch.getSourceMassesKg(), 1.0e-12);
    assertArrayEquals(expectedVolumesM3, batch.getSourceVolumesM3At60F(), 1.0e-12);
    assertEquals(expectedTotalVolumeM3, batch.getTotalAdditiveVolumeM3At60F(), 1.0e-12);
    assertEquals(expectedSpecificGravity, batch.getSpecificGravity(), 1.0e-15);
    assertFalse(batch.hasCost());
    assertThrows(IllegalStateException.class, batch::getTotalCost);
  }

  @Test
  public void optimizationFactoryPreservesRecipeAndCostClosure() {
    double[] sourceSpecificGravities = { 0.85, 0.80, 0.75 };
    RefineryLinearBlendOptimizer.Result optimization = RefineryLinearBlendOptimizer.optimizeMinimumCost(
        new double[] { 1.0, 2.0, 3.0 }, sourceSpecificGravities, new double[] { 0.030, 0.010, 0.002 },
        new double[] { 0.003, 0.001, 0.0002 }, new double[] { 600.0, 300.0, 50.0 }, 50.0, 30.0, 60.0, 0.015, 0.01, 50.0,
        600.0);

    RefineryBlendBatch batch = RefineryBlendBatch.fromOptimization(10000.0, optimization, sourceSpecificGravities);

    assertArrayEquals(new double[] { 0.25, 0.75, 0.0 }, batch.getSourceMassFractions(), 1.0e-9);
    assertArrayEquals(new double[] { 2500.0, 7500.0, 0.0 }, batch.getSourceMassesKg(), 1.0e-6);
    assertTrue(batch.hasCost());
    assertEquals(1.75, batch.getUnitCostPerMass(), 1.0e-9);
    assertEquals(17500.0, batch.getTotalCost(), 1.0e-5);
    assertEquals(optimization.getAssayBlend().getSpecificGravity(), batch.getSpecificGravity(), 1.0e-12);
  }

  @Test
  public void scaleOrderAndDefensiveCopyInvariantsHold() {
    RefineryBlendBatch base = RefineryBlendBatch.fromMassFractions(10.0, new double[] { 0.25, 0.75 },
        new double[] { 0.90, 0.80 });
    RefineryBlendBatch scaled = RefineryBlendBatch.fromMassFractions(100.0, new double[] { 0.25, 0.75 },
        new double[] { 0.90, 0.80 });
    RefineryBlendBatch reversed = RefineryBlendBatch.fromMassFractions(10.0, new double[] { 0.75, 0.25 },
        new double[] { 0.80, 0.90 });

    assertEquals(base.getSpecificGravity(), scaled.getSpecificGravity(), 0.0);
    assertEquals(base.getSpecificGravity(), reversed.getSpecificGravity(), 0.0);
    assertEquals(10.0 * base.getTotalAdditiveVolumeM3At60F(), scaled.getTotalAdditiveVolumeM3At60F(), 1.0e-14);

    double[] fractions = base.getSourceMassFractions();
    double[] masses = base.getSourceMassesKg();
    double[] volumes = base.getSourceVolumesM3At60F();
    fractions[0] = 1.0;
    masses[0] = 10.0;
    volumes[0] = 10.0;
    assertArrayEquals(new double[] { 0.25, 0.75 }, base.getSourceMassFractions(), 0.0);
    assertArrayEquals(new double[] { 2.5, 7.5 }, base.getSourceMassesKg(), 0.0);
    assertTrue(base.getSourceVolumesM3At60F()[0] < 1.0);
  }

  @Test
  public void zeroContributionDoesNotRequireFabricatedSpecificGravity() {
    RefineryBlendBatch batch = RefineryBlendBatch.fromMassFractions(10.0, new double[] { 1.0, 0.0 },
        new double[] { 0.82, Double.NaN });

    assertArrayEquals(new double[] { 10.0, 0.0 }, batch.getSourceMassesKg(), 0.0);
    assertArrayEquals(new double[] { 10.0 / (0.82 * WATER_DENSITY_60F_KG_M3), 0.0 }, batch.getSourceVolumesM3At60F(),
        1.0e-15);
    assertEquals(0.82, batch.getSpecificGravity(), Math.ulp(0.82));
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendBatch.fromMassFractions(0.0, new double[] { 1.0 }, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendBatch.fromMassFractions(1.0, null, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendBatch.fromMassFractions(1.0, new double[] { 0.5, 0.5 }, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendBatch.fromMassFractions(1.0, new double[] { 0.5, 0.4 }, new double[] { 0.8, 0.9 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendBatch.fromMassFractions(1.0, new double[] { 1.0 }, new double[] { Double.NaN }));
    assertThrows(NullPointerException.class,
        () -> RefineryBlendBatch.fromOptimization(1.0, null, new double[] { 0.8 }));
  }
}
