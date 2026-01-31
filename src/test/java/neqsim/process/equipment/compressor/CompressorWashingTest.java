package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.CompressorWashing.FoulingType;
import neqsim.process.equipment.compressor.CompressorWashing.WashingMethod;

/**
 * Unit tests for CompressorWashing class.
 */
public class CompressorWashingTest {

  private CompressorWashing washing;

  @BeforeEach
  public void setUp() {
    washing = new CompressorWashing(FoulingType.SALT);
  }

  @Test
  public void testInitialState() {
    assertEquals(0.0, washing.getCurrentFoulingFactor(), 1e-6);
    assertEquals(0.0, washing.getHoursSinceLastWash(), 1e-6);
    assertFalse(washing.isWashingRecommended());
    assertFalse(washing.isWashingCritical());
  }

  @Test
  public void testFoulingAccumulation() {
    // Operate for 1000 hours
    washing.updateFouling(1000.0);

    assertTrue(washing.getCurrentFoulingFactor() > 0);
    assertEquals(1000.0, washing.getHoursSinceLastWash(), 1e-6);
    assertEquals(1000.0, washing.getTotalOperatingHours(), 1e-6);
  }

  @Test
  public void testHeadLossCalculation() {
    // Set fouling directly
    washing.setCurrentFoulingFactor(0.5);

    double headLoss = washing.getHeadLossFactor();
    // Head loss = fouling^2 * 0.20 = 0.25 * 0.20 = 0.05
    assertEquals(0.05, headLoss, 0.01);
  }

  @Test
  public void testEfficiencyDegradation() {
    washing.setCurrentFoulingFactor(0.5);

    double effLoss = washing.getEfficiencyDegradation();
    // Efficiency loss = fouling * 0.10 = 0.05
    assertEquals(0.05, effLoss, 0.01);
  }

  @Test
  public void testOnlineWash() {
    washing.setCurrentFoulingFactor(0.3);
    double initialFouling = washing.getCurrentFoulingFactor();

    double recovery = washing.performOnlineWash();

    assertTrue(recovery > 0);
    assertTrue(washing.getCurrentFoulingFactor() < initialFouling);
    assertEquals(0.0, washing.getHoursSinceLastWash(), 1e-6);
    assertEquals(1, washing.getWashHistory().size());
  }

  @Test
  public void testOfflineWash() {
    washing.setCurrentFoulingFactor(0.3);
    double initialFouling = washing.getCurrentFoulingFactor();

    double recovery = washing.performOfflineWash();

    assertTrue(recovery > 0);
    // Offline should recover more than online
    assertTrue(washing.getCurrentFoulingFactor() < initialFouling * 0.5);
  }

  @Test
  public void testWashMethodEffectiveness() {
    // Offline soak should be more effective than online
    assertTrue(WashingMethod.OFFLINE_SOAK.getRecoveryEffectiveness() > WashingMethod.ONLINE_WET
        .getRecoveryEffectiveness());

    // Crank wash should be most effective
    assertTrue(WashingMethod.CRANK_WASH.getRecoveryEffectiveness() > WashingMethod.OFFLINE_SOAK
        .getRecoveryEffectiveness());
  }

  @Test
  public void testWashingRecommendation() {
    washing.setMaxAllowableFouling(0.15);

    // Below 50% of threshold - no recommendation
    washing.setCurrentFoulingFactor(0.05);
    assertFalse(washing.isWashingRecommended());

    // Above 50% of threshold - recommended
    washing.setCurrentFoulingFactor(0.10);
    assertTrue(washing.isWashingRecommended());

    // Above threshold - critical
    washing.setCurrentFoulingFactor(0.20);
    assertTrue(washing.isWashingCritical());
  }

  @Test
  public void testEnvironmentalSeverity() {
    washing.setEnvironmentalSeverity(2.0); // Harsh offshore
    washing.updateFouling(1000.0);
    double harshFouling = washing.getCurrentFoulingFactor();

    CompressorWashing normalWashing = new CompressorWashing(FoulingType.SALT);
    normalWashing.setEnvironmentalSeverity(1.0);
    normalWashing.updateFouling(1000.0);
    double normalFouling = normalWashing.getCurrentFoulingFactor();

    assertTrue(harshFouling > normalFouling);
  }

  @Test
  public void testInletFilterEffect() {
    washing.setInletFilterEfficiency(0.99); // High efficiency filter
    washing.updateFouling(1000.0);
    double filteredFouling = washing.getCurrentFoulingFactor();

    CompressorWashing unfilteredWashing = new CompressorWashing(FoulingType.SALT);
    unfilteredWashing.setInletFilterEfficiency(0.0); // No filter
    unfilteredWashing.updateFouling(1000.0);
    double unfilteredFouling = unfilteredWashing.getCurrentFoulingFactor();

    assertTrue(filteredFouling < unfilteredFouling);
  }

  @Test
  public void testCorrectedHead() {
    washing.setCurrentFoulingFactor(0.5);
    double cleanHead = 100.0;

    double correctedHead = washing.getCorrectedHead(cleanHead);

    assertTrue(correctedHead < cleanHead);
    assertTrue(correctedHead > 0);
  }

  @Test
  public void testCorrectedEfficiency() {
    washing.setCurrentFoulingFactor(0.5);
    double cleanEff = 0.85;

    double correctedEff = washing.getCorrectedEfficiency(cleanEff);

    assertTrue(correctedEff < cleanEff);
    assertTrue(correctedEff > 0);
  }

  @Test
  public void testWashIntervalEstimation() {
    double maxHeadLoss = 0.05; // 5% max head loss

    double interval = washing.estimateWashInterval(maxHeadLoss);

    assertTrue(interval > 0);
    assertTrue(interval < 100000); // Reasonable upper bound
  }

  @Test
  public void testWaterConsumptionEstimate() {
    double onlineWater = washing.estimateWaterConsumption(WashingMethod.ONLINE_WET);
    double offlineWater = washing.estimateWaterConsumption(WashingMethod.OFFLINE_SOAK);
    double dryIceWater = washing.estimateWaterConsumption(WashingMethod.DRY_ICE_BLAST);

    assertTrue(onlineWater > 0);
    assertTrue(offlineWater > 0);
    assertEquals(0.0, dryIceWater, 1e-6); // Dry ice uses no water
  }

  @Test
  public void testWashHistory() {
    washing.setCurrentFoulingFactor(0.3);
    washing.performOnlineWash();
    washing.setCurrentFoulingFactor(0.25);
    washing.performOfflineWash();

    assertEquals(2, washing.getWashHistory().size());

    CompressorWashing.WashEvent firstWash = washing.getWashHistory().get(0);
    assertEquals(WashingMethod.ONLINE_WET, firstWash.getMethod());
    assertTrue(firstWash.getRecovery() > 0);
  }

  @Test
  public void testFoulingTypes() {
    // Salt has highest fouling rate among common types
    assertTrue(
        FoulingType.SALT.getFoulingRatePerHour() > FoulingType.PARTICULATE.getFoulingRatePerHour());

    // Corrosion is harder to wash
    assertTrue(
        FoulingType.SALT.getWashabilityFactor() > FoulingType.CORROSION.getWashabilityFactor());
  }

  @Test
  public void testNoNegativeFouling() {
    washing.setCurrentFoulingFactor(0.1);
    washing.performWash(WashingMethod.CHEMICAL_CLEAN);

    assertTrue(washing.getCurrentFoulingFactor() >= 0.0);
  }

  @Test
  public void testMaxFoulingCap() {
    washing.setCurrentFoulingFactor(1.5); // Try to set above 1.0

    assertEquals(1.0, washing.getCurrentFoulingFactor(), 1e-6);
  }
}
