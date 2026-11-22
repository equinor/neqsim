package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests recovered-reformer-hydrogen allocation to hydrocracking demand. */
public class RefineryHydrocrackingHydrogenSupplyBalanceTest {
  @Test
  public void recoveredHydrogenOffsetsImportedHydrogenDemand() {
    RefineryHydrocrackingYieldBalance hydrocracking = createHydrocrackingBalance(50.0);
    RefineryCatalyticReformingHydrogenRecoveryBalance reforming = createReformingRecovery();
    RefineryHydrocrackingHydrogenSupplyBalance supply = RefineryHydrocrackingHydrogenSupplyBalance
        .calculate(hydrocracking, reforming);

    assertSame(hydrocracking, supply.getHydrocrackingBalance());
    assertSame(reforming, supply.getReformingRecoveryBalance());
    assertEquals(50.0, supply.getHydrocrackingHydrogenDemandKgPerHour(), 1.0e-12);
    assertEquals(34.673136, supply.getRecoveredReformerHydrogenAvailableKgPerHour(), 1.0e-10);
    assertEquals(34.673136, supply.getRecoveredReformerHydrogenAllocatedKgPerHour(), 1.0e-10);
    assertEquals(15.326864, supply.getImportedHydrogenRequiredKgPerHour(), 1.0e-10);
    assertEquals(0.0, supply.getRecoveredReformerHydrogenSurplusKgPerHour(), 0.0);
    assertEquals(0.69346272, supply.getHydrogenDemandCoverageFraction(), 1.0e-10);
    assertEquals(15.326864, supply.getImportedHydrogenRequiredKgPerTonneHydrocrackerFeed(), 1.0e-10);
    assertEquals(0.0, supply.getHydrogenDemandBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, supply.getRecoveredHydrogenSupplyBalanceResidualKgPerHour(), 1.0e-12);
  }

  @Test
  public void excessAndZeroDemandReportRecoveredHydrogenSurplus() {
    RefineryCatalyticReformingHydrogenRecoveryBalance reforming = createReformingRecovery();
    RefineryHydrocrackingHydrogenSupplyBalance surplus = RefineryHydrocrackingHydrogenSupplyBalance
        .calculate(createHydrocrackingBalance(20.0), reforming);
    RefineryHydrocrackingHydrogenSupplyBalance zero = RefineryHydrocrackingHydrogenSupplyBalance
        .calculate(createHydrocrackingBalance(0.0), reforming);

    assertEquals(20.0, surplus.getRecoveredReformerHydrogenAllocatedKgPerHour(), 1.0e-12);
    assertEquals(14.673136, surplus.getRecoveredReformerHydrogenSurplusKgPerHour(), 1.0e-10);
    assertEquals(0.0, surplus.getImportedHydrogenRequiredKgPerHour(), 0.0);
    assertEquals(1.0, surplus.getHydrogenDemandCoverageFraction(), 0.0);
    assertEquals(0.0, zero.getRecoveredReformerHydrogenAllocatedKgPerHour(), 0.0);
    assertEquals(34.673136, zero.getRecoveredReformerHydrogenSurplusKgPerHour(), 1.0e-10);
    assertEquals(1.0, zero.getHydrogenDemandCoverageFraction(), 0.0);
  }

  @Test
  public void nullReceiptsFailClosed() {
    assertThrows(NullPointerException.class,
        () -> RefineryHydrocrackingHydrogenSupplyBalance.calculate(null, createReformingRecovery()));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrocrackingHydrogenSupplyBalance.calculate(createHydrocrackingBalance(20.0), null));
  }

  private static RefineryHydrocrackingYieldBalance createHydrocrackingBalance(
      double hydrogenConsumptionKgPerTonneFeed) {
    return RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.80, hydrogenConsumptionKgPerTonneFeed, 0.10, 0.30,
        0.60);
  }

  private static RefineryCatalyticReformingHydrogenRecoveryBalance createReformingRecovery() {
    RefineryCatalyticReformingFeedBalance feed = RefineryCatalyticReformingFeedBalance.calculate(new int[] {6, 8},
        new double[] {1.0, 2.0}, new double[] {2.0, 3.0}, new double[] {0.5, 1.0}, new double[] {0.25, 1.0});
    return RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(feed, 0.80);
  }
}
