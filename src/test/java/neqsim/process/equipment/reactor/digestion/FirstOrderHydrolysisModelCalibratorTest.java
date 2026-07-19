package neqsim.process.equipment.reactor.digestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.digestion.FirstOrderHydrolysisModelCalibrator.CalibrationResult;
import neqsim.thermo.characterization.BioFeedstock;

/** Tests deterministic fitting and qualification of the first-order digestion model. */
class FirstOrderHydrolysisModelCalibratorTest {

  @Test
  void syntheticMultiCaseDataRecoverKnownKineticParameters() {
    double maximumDestruction = 0.68;
    double hydrolysisRate = 0.11;
    FirstOrderHydrolysisModelCalibrator calibrator = new FirstOrderHydrolysisModelCalibrator();
    for (double retentionTime : new double[] {5.0, 10.0, 20.0, 35.0}) {
      double measured = maximumDestruction * (1.0 - Math.exp(-hydrolysisRate * retentionTime));
      calibrator.addObservation(retentionTime, 308.15, measured);
    }

    CalibrationResult calibration = calibrator.calibrate("synthetic regression data set");
    CalibratedFirstOrderHydrolysisDigestionModel model = calibration.getModel();

    assertEquals(maximumDestruction, model.getMaximumVsDestruction(), 1.0e-8);
    assertEquals(hydrolysisRate, model.getHydrolysisRatePerDay(), 1.0e-8);
    assertEquals(4, calibration.getObservationCount());
    assertTrue(calibration.getRootMeanSquaredError() < 1.0e-10);
    assertEquals(1.0, calibration.getRSquared(), 1.0e-10);
    assertFalse(calibration.isHydrolysisRateAtSearchBoundary());
    assertEquals(ModelFidelity.CALIBRATED, model.getFidelity());
    assertEquals("synthetic regression data set", model.getEvidenceReference());
  }

  @Test
  void calibratedModelDoesNotMutateFeedstockAndWarnsOnExtrapolation() {
    FirstOrderHydrolysisModelCalibrator calibrator = new FirstOrderHydrolysisModelCalibrator();
    calibrator.addObservation(10.0, 308.15, 0.40).addObservation(30.0, 308.15, 0.62);
    CalibratedFirstOrderHydrolysisDigestionModel model = calibrator.calibrate("pilot campaign A").getModel();
    BioFeedstock feedstock = BioFeedstock.library("crop_residue");
    double originalRate = feedstock.getHydrolysisRatePerDay();

    AnaerobicDigestionResult result = model.calculate(new AnaerobicDigestionInput(feedstock, 100.0, 40.0, 318.15,
        Double.NaN, Double.NaN, Double.NaN, 0.10));

    assertEquals(originalRate, feedstock.getHydrolysisRatePerDay(), 0.0);
    assertEquals(ModelFidelity.CALIBRATED, result.getFidelity());
    assertEquals(model.getModelIdentifier(), result.getModelIdentifier());
    assertEquals("pilot campaign A", result.getModelEvidenceReference());
    assertEquals("pilot campaign A", result.toMap().get("modelEvidenceReference"));
    assertEquals(2, result.getWarnings().size());
    assertTrue(result.getWarnings().get(0).contains("retention time"));
    assertTrue(result.getWarnings().get(1).contains("temperature"));
  }

  @Test
  void calibrationRequiresEvidenceAndDistinctOperatingCases() {
    FirstOrderHydrolysisModelCalibrator calibrator = new FirstOrderHydrolysisModelCalibrator();
    calibrator.addObservation(20.0, 308.15, 0.50).addObservation(20.0, 308.15, 0.55);

    assertThrows(IllegalArgumentException.class, () -> calibrator.calibrate(""));
    assertThrows(IllegalStateException.class, () -> calibrator.calibrate("duplicate conditions"));
  }
}
