package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests repository-admissible provenance for volumetric Pitzer datasets. */
class PitzerBinaryVolumetricDatasetProvenanceTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 323.15;
  private static final double PRESSURE_PA = 20.0e6;
  private static final double DEBYE_HUCKEL_VOLUME_SLOPE = 1.2e-6;
  private static final double LIMITING_VOLUME = 17.6e-6;
  private static final double BETA0_DERIVATIVE = 2.0e-10;
  private static final double BETA1_DERIVATIVE = -0.7e-10;
  private static final double CPHI_DERIVATIVE = 1.1e-11;
  private static final double STANDARD_UNCERTAINTY = 2.0e-8;
  private static final double[] CALIBRATION_MOLALITIES = { 0.02, 0.08, 0.2, 0.5, 1.0, 2.0, 3.5, 5.0, 6.0 };
  private static final double[] VALIDATION_MOLALITIES = { 0.1, 1.5, 4.0 };

  @Test
  void validatesPermittedRepositoryDatasetsBeforeGroupedEvaluation() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(model, CALIBRATION_MOLALITIES,
        "calibration-laboratory");
    List<PitzerBinaryVolumetricRegression.Observation> validation = observations(model, VALIDATION_MOLALITIES,
        "validation-laboratory");
    PitzerBinaryVolumetricDatasetProvenance provenance = new PitzerBinaryVolumetricDatasetProvenance(Arrays.asList(
        source("calibration-laboratory", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, CALIBRATION_MOLALITIES.length, 0.02,
            6.0, 'A'),
        source("validation-laboratory", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.VALIDATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, VALIDATION_MOLALITIES.length, 0.1,
            4.0, 'B')));

    PitzerBinaryVolumetricGroupedValidation.ValidationResult result = new PitzerBinaryVolumetricGroupedValidation(model)
        .validateRepositoryDatasets(provenance, calibration, validation, TEMPERATURE_K, PRESSURE_PA,
            DEBYE_HUCKEL_VOLUME_SLOPE);

    assertEquals(LIMITING_VOLUME, result.getCalibrationFit().getLimitingApparentMolarVolume(), 1.0e-17);
    assertEquals(VALIDATION_MOLALITIES.length, result.getObservationCount());
    assertEquals(0.0, result.getChiSquare(), 1.0e-15);
  }

  @Test
  void rejectsRestrictedOrUnknownRedistributionBeforeFitting() {
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(calciumChlorideModel(),
        CALIBRATION_MOLALITIES, "restricted-source");
    PitzerBinaryVolumetricDatasetProvenance provenance = new PitzerBinaryVolumetricDatasetProvenance(Collections
        .singletonList(source("restricted-source", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.RESTRICTED, CALIBRATION_MOLALITIES.length,
            0.02, 6.0, 'C')));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> provenance.validateRepositoryObservations(calibration,
            PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, TEMPERATURE_K, PRESSURE_PA));
    assertTrue(exception.getMessage().contains("not permitted"));
  }

  @Test
  void rejectsUndeclaredRoleCountAndEnvelopeMismatches() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    List<PitzerBinaryVolumetricRegression.Observation> calibration = observations(model, CALIBRATION_MOLALITIES,
        "calibration-laboratory");
    PitzerBinaryVolumetricDatasetProvenance wrongCount = manifest(
        source("calibration-laboratory", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, CALIBRATION_MOLALITIES.length - 1,
            0.02, 6.0, 'D'));
    assertThrows(IllegalArgumentException.class, () -> wrongCount.validateRepositoryObservations(calibration,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, TEMPERATURE_K, PRESSURE_PA));

    PitzerBinaryVolumetricDatasetProvenance wrongRole = manifest(
        source("calibration-laboratory", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.VALIDATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, CALIBRATION_MOLALITIES.length, 0.02,
            6.0, 'E'));
    assertThrows(IllegalArgumentException.class, () -> wrongRole.validateRepositoryObservations(calibration,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, TEMPERATURE_K, PRESSURE_PA));

    PitzerBinaryVolumetricDatasetProvenance narrowEnvelope = manifest(
        source("calibration-laboratory", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, CALIBRATION_MOLALITIES.length, 0.02,
            5.0, 'F'));
    assertThrows(IllegalArgumentException.class, () -> narrowEnvelope.validateRepositoryObservations(calibration,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, TEMPERATURE_K, PRESSURE_PA));

    List<PitzerBinaryVolumetricRegression.Observation> undeclared = observations(model, CALIBRATION_MOLALITIES,
        "different-lineage");
    assertThrows(IllegalArgumentException.class, () -> narrowEnvelope.validateRepositoryObservations(undeclared,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, TEMPERATURE_K, PRESSURE_PA));
  }

  @Test
  void rejectsInvalidChecksumsDuplicatesAndIncompletePermittedLicenses() {
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricDatasetProvenance.SourceRecord("source",
            PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, "Synthetic source",
            "https://example.test/source", "CC-BY-4.0", "https://creativecommons.org/licenses/by/4.0/",
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, "not-a-checksum",
            "Absolute one-sigma synthetic uncertainty", 1, 0.0, 1.0, 298.15, 323.15, 1.0e5, 20.0e6));

    PitzerBinaryVolumetricDatasetProvenance.SourceRecord duplicate = source("duplicate",
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
        PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, 1, 0.0, 1.0, '1');
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricDatasetProvenance(Arrays.asList(duplicate, duplicate)));

    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricDatasetProvenance.SourceRecord("source",
            PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, "Synthetic source",
            "https://example.test/source", "CC-BY-4.0", "",
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, checksum('2'),
            "Absolute one-sigma synthetic uncertainty", 1, 0.0, 1.0, 298.15, 323.15, 1.0e5, 20.0e6));
  }

  @Test
  void exposesImmutableSortedRecordsAndNormalizedChecksum() {
    PitzerBinaryVolumetricDatasetProvenance provenance = new PitzerBinaryVolumetricDatasetProvenance(Arrays.asList(
        source("z-source", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.VALIDATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, 1, 0.0, 1.0, 'A'),
        source("a-source", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
            PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, 1, 0.0, 1.0, 'B')));

    assertEquals("a-source", provenance.getSources().get(0).getSourceGroup());
    assertEquals(checksum('b'), provenance.getSource("a-source").getSha256());
    assertNotSame(provenance.getSources(), provenance.getSources());
    assertThrows(UnsupportedOperationException.class,
        () -> provenance.getSources()
            .add(source("new-source", PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION,
                PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED, 1, 0.0, 1.0, 'C')));
  }

  private static PitzerBinaryVolumetricDatasetProvenance manifest(
      PitzerBinaryVolumetricDatasetProvenance.SourceRecord source) {
    return new PitzerBinaryVolumetricDatasetProvenance(Collections.singletonList(source));
  }

  private static PitzerBinaryVolumetricDatasetProvenance.SourceRecord source(String sourceGroup,
      PitzerBinaryVolumetricDatasetProvenance.DatasetRole role,
      PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus status, int observationCount, double minimumMolality,
      double maximumMolality, char checksumCharacter) {
    return new PitzerBinaryVolumetricDatasetProvenance.SourceRecord(sourceGroup, role,
        "Synthetic source for provenance tests", "https://example.test/" + sourceGroup,
        status == PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED ? "CC-BY-4.0" : "RESTRICTED",
        status == PitzerBinaryVolumetricDatasetProvenance.RedistributionStatus.PERMITTED
            ? "https://creativecommons.org/licenses/by/4.0/"
            : "",
        status, checksum(checksumCharacter), "Absolute one-sigma synthetic uncertainty", observationCount,
        minimumMolality, maximumMolality, TEMPERATURE_K, TEMPERATURE_K, PRESSURE_PA, PRESSURE_PA);
  }

  private static List<PitzerBinaryVolumetricRegression.Observation> observations(PitzerBinaryVolumetricModel model,
      double[] molalities, String sourceGroup) {
    PitzerBinaryVolumetricModel.StateParameters parameters = new PitzerBinaryVolumetricModel.StateParameters(
        TEMPERATURE_K, PRESSURE_PA, DEBYE_HUCKEL_VOLUME_SLOPE, BETA0_DERIVATIVE, BETA1_DERIVATIVE, CPHI_DERIVATIVE);
    PitzerBinaryVolumetricRegression.Observation[] observations = new PitzerBinaryVolumetricRegression.Observation[molalities.length];
    for (int index = 0; index < molalities.length; index++) {
      double volume = model.calculateApparentMolarVolume(molalities[index], LIMITING_VOLUME, parameters);
      observations[index] = new PitzerBinaryVolumetricRegression.Observation(molalities[index], volume,
          STANDARD_UNCERTAINTY, sourceGroup);
    }
    return Arrays.asList(observations);
  }

  private static String checksum(char value) {
    char[] characters = new char[64];
    Arrays.fill(characters, value);
    return new String(characters);
  }

  private static PitzerBinaryVolumetricModel calciumChlorideModel() {
    return new PitzerBinaryVolumetricModel(1, 2, 2, -1);
  }
}
