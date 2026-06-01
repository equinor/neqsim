package neqsim.process.equipment.watertreatment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.watertreatment.OilInWaterDoseOptimizer.DoseRecommendation;
import neqsim.process.equipment.watertreatment.OilInWaterMonthlyComplianceMonitor.ComplianceStatus;
import neqsim.process.equipment.watertreatment.OilInWaterMonthlyComplianceMonitor.MonthlyStatus;

/**
 * Tests for oil-in-water decision support models.
 *
 * @author ESOL
 * @version 1.0
 */
public class OilInWaterDecisionSupportTest {

  @Test
  public void testDoseResponseIncludesOverdosePenalty() {
    DemulsifierDoseResponseModel model = new DemulsifierDoseResponseModel();
    model.setMaxRemovalFraction(0.80);
    model.setHalfEffectDosePpm(12.0);
    model.setHillCoefficient(1.8);
    model.setOptimumDosePpm(40.0);
    model.setOverdoseSensitivity(2.0);

    double untreated = model.predictOilInWater(100.0, 0.0);
    double nearOptimum = model.predictOilInWater(100.0, 40.0);
    double overdosed = model.predictOilInWater(100.0, 160.0);

    assertEquals(100.0, untreated, 1.0);
    assertTrue(nearOptimum < untreated, "Demulsifier should reduce OIW near optimum dose");
    assertTrue(overdosed > nearOptimum, "Overdosing should worsen the predicted OIW response");
    assertTrue(model.toJson().contains("halfEffectDosePpm"));
  }

  @Test
  public void testDoseResponseCalibrationReducesError() {
    DemulsifierDoseResponseModel model = new DemulsifierDoseResponseModel();
    double[] doses = {0.0, 10.0, 20.0, 40.0, 80.0};
    double[] observedOiw = {120.0, 82.0, 58.0, 40.0, 48.0};

    double rmse = model.calibrate(doses, observedOiw, 120.0);
    double noChemical = model.predictOilInWater(120.0, 0.0);
    double recommendedRange = model.predictOilInWater(120.0, 40.0);

    assertTrue(rmse < 25.0, "Calibrated model should fit the synthetic field trend");
    assertTrue(recommendedRange < noChemical, "Calibrated model should predict dose benefit");
    assertTrue(model.getLastRootMeanSquareError() < 25.0);
  }

  @Test
  public void testChemicalLagModelDelaysDoseChange() {
    ChemicalDoseLagModel lag = new ChemicalDoseLagModel();
    lag.setHoldUpVolumeM3(100.0);
    lag.setDecayHalfLifeHours(Double.POSITIVE_INFINITY);

    double earlyDose = lag.step(30.0, 100.0, 0.25);
    for (int i = 0; i < 20; i++) {
      lag.step(30.0, 100.0, 0.25);
    }
    double lateDose = lag.getEffectiveDosePpm();

    assertTrue(earlyDose > 0.0);
    assertTrue(earlyDose < 30.0, "Hold-up should delay the setpoint response");
    assertTrue(lateDose > earlyDose, "Effective dose should accumulate toward the setpoint");

    lag.resetToSteadyState(20.0, 100.0);
    assertEquals(20.0, lag.getEffectiveDosePpm(), 1.0e-6);
    assertTrue(lag.toJson().contains("chemicalMassKg"));
  }

  @Test
  public void testAnalyzerDriftMeasurementAndCorrection() {
    OilInWaterAnalyzerDriftModel analyzer = new OilInWaterAnalyzerDriftModel();
    analyzer.setZeroOffsetMgL(2.0);
    analyzer.setSpanFactor(1.10);
    analyzer.setZeroDriftMgLPerDay(0.10);
    analyzer.setSpanDriftFractionPerDay(0.005);
    analyzer.setNoiseStandardDeviationMgL(2.0);
    analyzer.setCalibrationIntervalDays(7.0);

    double measured = analyzer.measure(20.0, 10.0);
    double corrected = analyzer.correctMeasuredValue(measured, 10.0);
    double conservative = analyzer.measureConservative(20.0, 10.0, 1.64);

    assertEquals(26.0, measured, 0.1);
    assertEquals(20.0, corrected, 0.1);
    assertTrue(conservative > measured);
    assertTrue(analyzer.isCalibrationDue(10.0));
    assertTrue(analyzer.toJson().contains("spanDriftFractionPerDay"));
  }

  @Test
  public void testMonthlyWeightedBudgetStatus() {
    OilInWaterMonthlyComplianceMonitor monitor = new OilInWaterMonthlyComplianceMonitor();
    monitor.setMonthlyLimitMgL(30.0);
    monitor.setDaysInMonth(30);
    monitor.setProjectedDailyWaterVolumeM3(1000.0);
    monitor.addSample(20.0, 1000.0);
    monitor.addSample(35.0, 1000.0);

    MonthlyStatus status = monitor.calculateStatus(2);

    assertEquals(27.5, monitor.getWeightedAverageMgL(), 0.01);
    assertEquals(ComplianceStatus.WARNING, status.getStatus());
    assertTrue(status.getRemainingAllowedAverageMgL() > 0.0);
    assertTrue(monitor.toJson().contains("weightedAverageMgL"));
  }

  @Test
  public void testOptimizerFindsLowestFeasibleDose() {
    OilInWaterDoseOptimizer optimizer = new OilInWaterDoseOptimizer();
    optimizer.setDoseRange(0.0, 80.0, 1.0);
    optimizer.setOptimizationHorizonHours(2.0);
    optimizer.setSafetyMarginMgL(3.0);
    optimizer.getChemicalLagModel().setHoldUpVolumeM3(10.0);
    optimizer.getMonthlyMonitor().setProjectedDailyWaterVolumeM3(2400.0);
    optimizer.getMonthlyMonitor().addSample(20.0, 2400.0);
    optimizer.getDoseResponseModel().setMaxRemovalFraction(0.85);
    optimizer.getDoseResponseModel().setHalfEffectDosePpm(10.0);
    optimizer.getDoseResponseModel().setOptimumDosePpm(60.0);
    optimizer.getDoseResponseModel().setOverdoseSensitivity(0.2);

    DoseRecommendation recommendation = optimizer.recommendDose(120.0, 100.0, 5);

    assertNotNull(recommendation);
    assertTrue(recommendation.isFeasible(), recommendation.getMessage());
    assertTrue(recommendation.getSetpointDosePpm() > 0.0);
    assertTrue(recommendation.getPredictedMeasuredOilInWaterMgL() <= recommendation
        .getTargetOilInWaterMgL());
    assertTrue(optimizer.toJson().contains("lastRecommendation"));
  }

  @Test
  public void testTreatmentTrainExposesDoseOptimizer() {
    ProducedWaterTreatmentTrain train = new ProducedWaterTreatmentTrain("PWTT-OPT");
    train.getOilInWaterDoseOptimizer().setDoseRange(0.0, 80.0, 2.0);
    train.getOilInWaterDoseOptimizer().getChemicalLagModel().setHoldUpVolumeM3(10.0);
    train.getOilInWaterDoseOptimizer().getDoseResponseModel().setMaxRemovalFraction(0.85);
    train.getOilInWaterDoseOptimizer().getDoseResponseModel().setHalfEffectDosePpm(10.0);
    train.getOilInWaterDoseOptimizer().getMonthlyMonitor().setProjectedDailyWaterVolumeM3(2400.0);

    DoseRecommendation recommendation = train.recommendDemulsifierDose(100.0, 100.0, 1);

    assertNotNull(recommendation);
    assertFalse(recommendation.getStatus().trim().isEmpty());
    assertTrue(recommendation.getSetpointDosePpm() >= 0.0);
  }
}
