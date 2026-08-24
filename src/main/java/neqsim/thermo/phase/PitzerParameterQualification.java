package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scientific qualification metadata for a versioned Pitzer parameter dataset.
 *
 * <p>
 * Parameter coverage and scientific qualification are deliberately separate. A dataset can contain every interaction
 * required by an active topology while still lacking independent experimental validation for that exact mixture.
 * </p>
 */
public final class PitzerParameterQualification implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Qualification level of the complete named dataset. */
  public enum Level {
    /** No reviewed source/convention mapping is registered. */
    UNQUALIFIED,
    /** Source values and equation conventions are mapped, but independent validation is incomplete. */
    SOURCE_MAPPED,
    /** Some named subsystems have independent experimental validation; the full dataset does not. */
    PARTIALLY_EXPERIMENTALLY_VALIDATED,
    /** The named dataset is validated inside its explicitly documented envelope. */
    VALIDATED_WITHIN_DECLARED_ENVELOPE
  }

  private final String datasetId;
  private final Level level;
  private final List<String> validatedSystems;
  private final List<String> limitations;

  /**
   * Creates immutable qualification metadata.
   *
   * @param datasetId stable parameter-dataset identity
   * @param level overall qualification level
   * @param validatedSystems independently checked systems and observables
   * @param limitations unresolved validation or applicability boundaries
   */
  public PitzerParameterQualification(String datasetId, Level level, List<String> validatedSystems,
      List<String> limitations) {
    if (datasetId == null || datasetId.trim().isEmpty()) {
      throw new IllegalArgumentException("Pitzer parameter dataset identity must not be empty");
    }
    if (level == null) {
      throw new IllegalArgumentException("Pitzer parameter qualification level must not be null");
    }
    this.datasetId = datasetId;
    this.level = level;
    this.validatedSystems = immutableCopy(validatedSystems);
    this.limitations = immutableCopy(limitations);
  }

  /** @return stable parameter-dataset identity */
  public String getDatasetId() {
    return datasetId;
  }

  /** @return overall qualification level */
  public Level getLevel() {
    return level;
  }

  /** @return immutable descriptions of independently checked systems and observables */
  public List<String> getValidatedSystems() {
    return validatedSystems;
  }

  /** @return immutable descriptions of unresolved validation or applicability boundaries */
  public List<String> getLimitations() {
    return limitations;
  }

  /**
   * Reports whether the complete named dataset is validated for use inside its declared envelope.
   *
   * @return {@code true} only for complete declared-envelope qualification
   */
  public boolean isValidatedWithinDeclaredEnvelope() {
    return level == Level.VALIDATED_WITHIN_DECLARED_ENVELOPE;
  }

  private static List<String> immutableCopy(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }
}
