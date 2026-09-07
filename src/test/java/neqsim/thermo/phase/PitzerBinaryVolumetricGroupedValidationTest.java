package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests independent source-group holdout evaluation for volumetric Pitzer regression. */
class PitzerBinaryVolumetricGroupedValidationTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 323.15;
  private static final double PRESSURE_PA = 20.0e6;
  private static final double DEBYE_HUCKEL_VOLUME_SLOPE = 1.2e-6;
  private static final double LIMITING_VOLUME = 17.6e-6;
  private static final double BETA0_DERIVATIVE = 2.0e-10;
  private static final double BETA1_DERIVATIVE = -0.7e-10;
  private static final double CPHI_DERIVATIVE = 1.1e-11;
  private static final double STANDARD_UNCERTAINTY = 2.0e-8;
  private static final double[] CALIBRATION_MOLALITIES = { 0.02, 0.08, 0.2, 0.5, 1.0, 2.0, 3.5, 5.0, 6.0 };

  @Test
  void fitsCalibrationOnlyAndReportsUntouchedHoldoutResiduals() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricGroupedValidation validation = new PitzerBinaryVolumetricGroupedValidation(model);
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(model, CALIBRATION_MOLALITIES,
        new double[CALIBRATION_MOLALITIES.length], "calibration-laboratory");
    List<PitzerBinaryVolumetricRegression.Observation> holdout = observations(model, new double[] { 0.1, 1.5, 4.0 },
        new double[] { 1.0, -2.0, 0.5 }, "validation-laboratory");

    PitzerBinaryVolumetricGroupedValidation.ValidationResult result = validation.validate(calibration, holdout,
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE);

    assertEquals(LIMITING_VOLUME, result.getCalibrationFit().getLimitingApparentMolarVolume(), 1.0e-17);
    assertEquals(3, result.getObservationCount());
    assertEquals(5.25, result.getChiSquare(), 1.0e-8);
    assertEquals(Math.sqrt(1.75), result.getWeightedRootMeanSquareResidual(), 1.0e-8);
    assertEquals(2.0, result.getMaximumAbsoluteStandardizedResidual(), 1.0e-8);
    assertEquals(1, result.getGroupStatistics().size());
    assertEquals("validation-laboratory", result.getGroupStatistics().get(0).getSourceGroup());
    assertEquals(3, result.getGroupStatistics().get(0).getCount());
    assertEquals(-STANDARD_UNCERTAINTY / 6.0, result.getGroupStatistics().get(0).getMeanResidual(), 1.0e-17);
  }

  @Test
  void keepsGroupedHoldoutDiagnosticsSortedAndOrderInvariant() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricGroupedValidation validation = new PitzerBinaryVolumetricGroupedValidation(model);
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(model, CALIBRATION_MOLALITIES,
        new double[CALIBRATION_MOLALITIES.length], "calibration");
    List<PitzerBinaryVolumetricRegression.Observation> holdout = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();
    holdout.addAll(observations(model, new double[] { 0.3, 2.5 }, new double[] { 0.4, -0.2 }, "laboratory-b"));
    holdout.addAll(observations(model, new double[] { 0.6, 4.5 }, new double[] { -0.5, 0.1 }, "laboratory-a"));

    PitzerBinaryVolumetricGroupedValidation.ValidationResult forward = validation.validate(calibration, holdout,
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE);
    Collections.reverse(holdout);
    PitzerBinaryVolumetricGroupedValidation.ValidationResult reversed = validation.validate(calibration, holdout,
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE);

    assertEquals(forward.getChiSquare(), reversed.getChiSquare(), 1.0e-12);
    assertEquals(forward.getWeightedRootMeanSquareResidual(), reversed.getWeightedRootMeanSquareResidual(), 1.0e-12);
    assertEquals(2, forward.getGroupStatistics().size());
    assertEquals("laboratory-a", forward.getGroupStatistics().get(0).getSourceGroup());
    assertEquals("laboratory-b", forward.getGroupStatistics().get(1).getSourceGroup());
    for (PitzerBinaryVolumetricGroupedValidation.GroupStatistics group : forward.getGroupStatistics()) {
      assertEquals(2, group.getCount());
      assertTrue(Double.isFinite(group.getMeanResidual()));
      assertTrue(group.getWeightedRootMeanSquareResidual() > 0.0);
      assertTrue(group.getMaximumAbsoluteStandardizedResidual() > 0.0);
    }
  }

  @Test
  void rejectsSourceLineageLeakage() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricGroupedValidation validation = new PitzerBinaryVolumetricGroupedValidation(model);
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(model, CALIBRATION_MOLALITIES,
        new double[CALIBRATION_MOLALITIES.length], "shared-lineage");
    List<PitzerBinaryVolumetricRegression.Observation> holdout = observations(model, new double[] { 0.1 },
        new double[] { 0.0 }, "shared-lineage");

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> validation.validate(calibration, holdout, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
    assertTrue(exception.getMessage().contains("shared-lineage"));
  }

  @Test
  void rejectsInvalidInputsBeforeEvaluation() {
    assertThrows(IllegalArgumentException.class, () -> new PitzerBinaryVolumetricGroupedValidation(null));
    PitzerBinaryVolumetricGroupedValidation validation = new PitzerBinaryVolumetricGroupedValidation(
        calciumChlorideModel());
    List<PitzerBinaryVolumetricRegression.Observation> calibration = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();
    List<PitzerBinaryVolumetricRegression.Observation> holdout = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();

    assertThrows(IllegalArgumentException.class,
        () -> validation.validate(null, holdout, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
    assertThrows(IllegalArgumentException.class,
        () -> validation.validate(calibration, holdout, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
    holdout.add(null);
    assertThrows(IllegalArgumentException.class,
        () -> validation.validate(calibration, holdout, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
  }

  private static List<PitzerBinaryVolumetricRegression.Observation> observations(PitzerBinaryVolumetricModel model,
      double[] molalities, double[] noiseInSigma, String sourceGroup) {
    PitzerBinaryVolumetricModel.StateParameters parameters = new PitzerBinaryVolumetricModel.StateParameters(
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE, BETA0_DERIVATIVE, BETA1_DERIVATIVE, CPHI_DERIVATIVE);
    List<PitzerBinaryVolumetricRegression.Observation> observations = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();
    for (int index = 0; index < molalities.length; index++) {
      double volume = model.calculateApparentMolarVolume(molalities[index], LIMITING_VOLUME, parameters)
          + noiseInSigma[index] * STANDARD_UNCERTAINTY;
      observations.add(new PitzerBinaryVolumetricRegression.Observation(molalities[index], volume, STANDARD_UNCERTAINTY,
          sourceGroup));
    }
    return observations;
  }

  private static PitzerBinaryVolumetricModel calciumChlorideModel() {
    return new PitzerBinaryVolumetricModel(1, 2, 2, -1);
  }
}
