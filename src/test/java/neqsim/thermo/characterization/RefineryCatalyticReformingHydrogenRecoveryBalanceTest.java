package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests catalytic-reforming hydrogen recovery allocation and closure. */
public class RefineryCatalyticReformingHydrogenRecoveryBalanceTest {
  @Test
  public void eightyPercentRecoveryClosesHydrogenAndOverallMass() {
    RefineryCatalyticReformingFeedBalance feedBalance = createFeedBalance();
    RefineryCatalyticReformingHydrogenRecoveryBalance recovery = RefineryCatalyticReformingHydrogenRecoveryBalance
        .calculate(feedBalance, 0.80);

    assertSame(feedBalance, recovery.getFeedBalance());
    assertEquals(0.80, recovery.getHydrogenRecoveryFraction(), 0.0);
    assertEquals(21.5, recovery.getProducedHydrogenFlowKmolPerHour(), 0.0);
    assertEquals(17.2, recovery.getRecoveredHydrogenFlowKmolPerHour(), 1.0e-14);
    assertEquals(4.3, recovery.getUnrecoveredHydrogenFlowKmolPerHour(), 1.0e-14);
    assertEquals(43.34142, recovery.getProducedHydrogenMassFlowKgPerHour(), 1.0e-10);
    assertEquals(34.673136, recovery.getRecoveredHydrogenMassFlowKgPerHour(), 1.0e-10);
    assertEquals(8.668284, recovery.getUnrecoveredHydrogenMassFlowKgPerHour(), 1.0e-10);
    assertEquals(42.09752036342549, recovery.getRecoveredHydrogenKgPerTonneFeed(), 1.0e-12);
    assertEquals(10.52438009085637, recovery.getUnrecoveredHydrogenKgPerTonneFeed(), 1.0e-12);
    assertEquals(0.0, recovery.getHydrogenMolarBalanceResidualKmolPerHour(), 1.0e-12);
    assertEquals(0.0, recovery.getHydrogenMassBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, recovery.getOverallMassBalanceResidualKgPerHour(), 1.0e-10);
  }

  @Test
  public void zeroAndCompleteRecoveryPreserveEndpoints() {
    RefineryCatalyticReformingFeedBalance feedBalance = createFeedBalance();
    RefineryCatalyticReformingHydrogenRecoveryBalance zeroRecovery = RefineryCatalyticReformingHydrogenRecoveryBalance
        .calculate(feedBalance, 0.0);
    RefineryCatalyticReformingHydrogenRecoveryBalance completeRecovery = RefineryCatalyticReformingHydrogenRecoveryBalance
        .calculate(feedBalance, 1.0);

    assertEquals(0.0, zeroRecovery.getRecoveredHydrogenMassFlowKgPerHour(), 0.0);
    assertEquals(feedBalance.getHydrogenProductMassFlowKgPerHour(),
        zeroRecovery.getUnrecoveredHydrogenMassFlowKgPerHour(), 0.0);
    assertEquals(feedBalance.getHydrogenProductMassFlowKgPerHour(),
        completeRecovery.getRecoveredHydrogenMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, completeRecovery.getUnrecoveredHydrogenMassFlowKgPerHour(), 0.0);
  }

  @Test
  public void invalidReceiptAndRecoveryFractionsFailClosed() {
    RefineryCatalyticReformingFeedBalance feedBalance = createFeedBalance();

    assertThrows(NullPointerException.class,
        () -> RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(null, 0.8));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(feedBalance, -0.01));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(feedBalance, 1.01));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(feedBalance, Double.NaN));
  }

  private static RefineryCatalyticReformingFeedBalance createFeedBalance() {
    return RefineryCatalyticReformingFeedBalance.calculate(new int[] {6, 8}, new double[] {1.0, 2.0},
        new double[] {2.0, 3.0}, new double[] {0.5, 1.0}, new double[] {0.25, 1.0});
  }
}
