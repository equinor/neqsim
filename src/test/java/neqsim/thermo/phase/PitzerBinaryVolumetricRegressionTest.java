package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests parameter-neutral weighted regression for the binary volumetric Pitzer kernel. */
class PitzerBinaryVolumetricRegressionTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 323.15;
  private static final double PRESSURE_PA = 20.0e6;
  private static final double DEBYE_HUCKEL_VOLUME_SLOPE = 1.2e-6;
  private static final double LIMITING_VOLUME = 17.6e-6;
  private static final double BETA0_DERIVATIVE = 2.0e-10;
  private static final double BETA1_DERIVATIVE = -0.7e-10;
  private static final double CPHI_DERIVATIVE = 1.1e-11;
  private static final double STANDARD_UNCERTAINTY = 2.0e-8;
  private static final double[] MOLALITIES = { 0.02, 0.08, 0.2, 0.5, 1.0, 2.0, 3.5, 5.0, 6.0 };

  @Test
  void recoversExactSyntheticParametersAndCovariance() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricRegression regression = new PitzerBinaryVolumetricRegression(model);

    PitzerBinaryVolumetricRegression.FitResult result = regression.fit(syntheticObservations(model, null),
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE);

    assertEquals(LIMITING_VOLUME, result.getLimitingApparentMolarVolume(), 1.0e-17);
    assertEquals(BETA0_DERIVATIVE, result.getStateParameters().getBeta0PressureDerivative(), 1.0e-21);
    assertEquals(BETA1_DERIVATIVE, result.getStateParameters().getBeta1PressureDerivative(), 1.0e-21);
    assertEquals(CPHI_DERIVATIVE, result.getStateParameters().getCphiPressureDerivative(), 1.0e-21);
    assertEquals(DEBYE_HUCKEL_VOLUME_SLOPE, result.getStateParameters().getDebyeHuckelVolumeSlope(), 0.0);
    assertEquals(MOLALITIES.length, result.getObservationCount());
    assertEquals(MOLALITIES.length - 4, result.getDegreesOfFreedom());
    assertTrue(result.getChiSquare() < 1.0e-20);
    assertTrue(result.getConditionNumber() < 100.0);

    double[][] covariance = result.getParameterCovariance();
    double[] standardUncertainties = result.getParameterStandardUncertainties();
    for (int row = 0; row < covariance.length; row++) {
      assertTrue(Double.isFinite(standardUncertainties[row]));
      assertTrue(standardUncertainties[row] > 0.0);
      assertEquals(standardUncertainties[row] * standardUncertainties[row], covariance[row][row], 1.0e-30);
      for (int column = 0; column < covariance.length; column++) {
        assertEquals(covariance[row][column], covariance[column][row], 1.0e-30);
      }
    }

    covariance[0][0] = -1.0;
    standardUncertainties[0] = -1.0;
    assertTrue(result.getParameterCovariance()[0][0] > 0.0);
    assertTrue(result.getParameterStandardUncertainties()[0] > 0.0);
  }

  @Test
  void reportsDeterministicSourceGroupedResidualsAndOrderInvariantFit() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricRegression regression = new PitzerBinaryVolumetricRegression(model);
    double[] noiseInSigma = { 0.2, -0.5, 0.4, -0.1, 0.0, 0.3, -0.2, 0.1, -0.4 };
    List<PitzerBinaryVolumetricRegression.Observation> observations = syntheticObservations(model, noiseInSigma);

    PitzerBinaryVolumetricRegression.FitResult forward = regression.fit(observations, TEMPERATURE_K, PRESSURE_PA,
        DEBYE_HUCKEL_VOLUME_SLOPE);
    Collections.reverse(observations);
    PitzerBinaryVolumetricRegression.FitResult reversed = regression.fit(observations, TEMPERATURE_K, PRESSURE_PA,
        DEBYE_HUCKEL_VOLUME_SLOPE);

    assertEquals(forward.getLimitingApparentMolarVolume(), reversed.getLimitingApparentMolarVolume(), 1.0e-15);
    assertEquals(forward.getStateParameters().getBeta0PressureDerivative(),
        reversed.getStateParameters().getBeta0PressureDerivative(), 1.0e-20);
    assertEquals(forward.getStateParameters().getBeta1PressureDerivative(),
        reversed.getStateParameters().getBeta1PressureDerivative(), 1.0e-20);
    assertEquals(forward.getStateParameters().getCphiPressureDerivative(),
        reversed.getStateParameters().getCphiPressureDerivative(), 1.0e-20);
    assertEquals(forward.getChiSquare(), reversed.getChiSquare(), 1.0e-12);

    assertEquals(2, forward.getGroupStatistics().size());
    assertEquals("laboratory-a", forward.getGroupStatistics().get(0).getSourceGroup());
    assertEquals("laboratory-b", forward.getGroupStatistics().get(1).getSourceGroup());
    assertEquals(5, forward.getGroupStatistics().get(0).getCount());
    assertEquals(4, forward.getGroupStatistics().get(1).getCount());
    assertTrue(forward.getReducedChiSquare() > 0.0);
    assertTrue(forward.getWeightedRootMeanSquareResidual() > 0.0);
    assertTrue(forward.getMaximumAbsoluteStandardizedResidual() > 0.0);
    for (PitzerBinaryVolumetricRegression.GroupStatistics group : forward.getGroupStatistics()) {
      assertTrue(Double.isFinite(group.getMeanResidual()));
      assertTrue(group.getWeightedRootMeanSquareResidual() >= 0.0);
      assertTrue(group.getMaximumAbsoluteStandardizedResidual() >= 0.0);
    }
  }

  @Test
  void rejectsRankDeficientConcentrationDesign() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricRegression regression = new PitzerBinaryVolumetricRegression(model);
    List<PitzerBinaryVolumetricRegression.Observation> observations = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();
    for (int index = 0; index < 6; index++) {
      observations
          .add(new PitzerBinaryVolumetricRegression.Observation(1.0, 25.0e-6, STANDARD_UNCERTAINTY, "same-state"));
    }

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> regression.fit(observations, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
    assertTrue(exception.getMessage().contains("rank deficient"));
  }

  @Test
  void rejectsInvalidModelsObservationsAndFitState() {
    assertThrows(IllegalArgumentException.class, () -> new PitzerBinaryVolumetricRegression(null));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricRegression.Observation(-0.1, 20.0e-6, STANDARD_UNCERTAINTY, "source"));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricRegression.Observation(0.1, 20.0e-6, 0.0, "source"));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricRegression.Observation(0.1, 20.0e-6, STANDARD_UNCERTAINTY, " "));

    PitzerBinaryVolumetricRegression regression = new PitzerBinaryVolumetricRegression(calciumChlorideModel());
    List<PitzerBinaryVolumetricRegression.Observation> tooFew = Arrays.asList(
        new PitzerBinaryVolumetricRegression.Observation(0.1, 20.0e-6, STANDARD_UNCERTAINTY, "source"),
        new PitzerBinaryVolumetricRegression.Observation(0.2, 21.0e-6, STANDARD_UNCERTAINTY, "source"),
        new PitzerBinaryVolumetricRegression.Observation(0.3, 22.0e-6, STANDARD_UNCERTAINTY, "source"),
        new PitzerBinaryVolumetricRegression.Observation(0.4, 23.0e-6, STANDARD_UNCERTAINTY, "source"));
    assertThrows(IllegalArgumentException.class,
        () -> regression.fit(tooFew, TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
    assertThrows(IllegalArgumentException.class, () -> regression
        .fit(syntheticObservations(calciumChlorideModel(), null), 0.0, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE));
  }

  private static List<PitzerBinaryVolumetricRegression.Observation> syntheticObservations(
      PitzerBinaryVolumetricModel model, double[] noiseInSigma) {
    PitzerBinaryVolumetricModel.StateParameters parameters = new PitzerBinaryVolumetricModel.StateParameters(
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE, BETA0_DERIVATIVE, BETA1_DERIVATIVE, CPHI_DERIVATIVE);
    List<PitzerBinaryVolumetricRegression.Observation> observations = new ArrayList<PitzerBinaryVolumetricRegression.Observation>();
    for (int index = 0; index < MOLALITIES.length; index++) {
      double volume = model.calculateApparentMolarVolume(MOLALITIES[index], LIMITING_VOLUME, parameters);
      if (noiseInSigma != null) {
        volume += noiseInSigma[index] * STANDARD_UNCERTAINTY;
      }
      String group = index % 2 == 0 ? "laboratory-a" : "laboratory-b";
      observations.add(
          new PitzerBinaryVolumetricRegression.Observation(MOLALITIES[index], volume, STANDARD_UNCERTAINTY, group));
    }
    return observations;
  }

  private static PitzerBinaryVolumetricModel calciumChlorideModel() {
    return new PitzerBinaryVolumetricModel(1, 2, 2, -1);
  }
}
