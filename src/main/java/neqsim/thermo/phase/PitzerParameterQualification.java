package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

  /** Observable-specific scientific targets that require independent evidence. */
  public enum ValidationTarget {
    /** Solute and ion activity coefficients on the declared activity scale. */
    AQUEOUS_ACTIVITY_COEFFICIENTS,
    /** Water activity and the corresponding osmotic coefficient. */
    WATER_ACTIVITY_AND_OSMOTIC_COEFFICIENT,
    /** Gas-aqueous vapor-liquid equilibrium or solubility. */
    GAS_AQUEOUS_VLE,
    /** Coupled aqueous reaction equilibrium and ionic speciation. */
    REACTIVE_SPECIATION,
    /** Mineral saturation, precipitation, and dissolution equilibrium. */
    MINERAL_SATURATION_AND_PRECIPITATION
  }

  private final String datasetId;
  private final Level level;
  private final List<String> validatedSystems;
  private final List<String> limitations;
  private final Set<ValidationTarget> validatedTargets;

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
    this(datasetId, level, validatedSystems, limitations, Collections.<ValidationTarget>emptyList());
  }

  /**
   * Creates immutable qualification metadata with machine-readable validation targets.
   *
   * @param datasetId stable parameter-dataset identity
   * @param level overall qualification level
   * @param validatedSystems independently checked systems and observables
   * @param limitations unresolved validation or applicability boundaries
   * @param validatedTargets independently validated property targets for the complete named dataset
   */
  public PitzerParameterQualification(String datasetId, Level level, List<String> validatedSystems,
      List<String> limitations, List<ValidationTarget> validatedTargets) {
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
    this.validatedTargets = immutableTargetCopy(validatedTargets);
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
    return Collections.unmodifiableList(new ArrayList<String>(validatedSystems));
  }

  /** @return immutable descriptions of unresolved validation or applicability boundaries */
  public List<String> getLimitations() {
    return Collections.unmodifiableList(new ArrayList<String>(limitations));
  }

  /**
   * Returns the property targets independently validated for the complete named dataset.
   *
   * @return immutable, deterministic set of validated targets
   */
  public Set<ValidationTarget> getValidatedTargets() {
    if (validatedTargets.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(EnumSet.copyOf(validatedTargets));
  }

  /**
   * Reports whether the complete named dataset is validated for use inside its declared envelope.
   *
   * @return {@code true} only for complete declared-envelope qualification
   */
  public boolean isValidatedWithinDeclaredEnvelope() {
    return level == Level.VALIDATED_WITHIN_DECLARED_ENVELOPE;
  }

  /**
   * Reports whether one property target has independent evidence for the complete named dataset.
   *
   * <p>
   * This does not check the current state against a temperature, pressure, or composition envelope. The appropriate
   * dataset-specific range helper remains a separate gate.
   * </p>
   *
   * @param target requested scientific target
   * @return {@code true} only when the dataset level and requested target are both qualified
   */
  public boolean isValidatedFor(ValidationTarget target) {
    return target != null && isValidatedWithinDeclaredEnvelope() && validatedTargets.contains(target);
  }

  /**
   * Formats a deterministic diagnostic for publication and engineering gates.
   *
   * @return dataset identity, qualification level, validated systems, and limitations
   */
  public String formatDiagnostic() {
    return "Pitzer parameter dataset '" + datasetId + "' qualification: level=" + level + ", validatedSystems="
        + validatedSystems + ", validatedTargets=" + validatedTargets + ", limitations=" + limitations;
  }

  /**
   * Requires independent qualification for one property target.
   *
   * @param target requested scientific target
   * @throws IllegalArgumentException when the requested target is null
   * @throws IllegalStateException when the dataset is not qualified for the requested target
   */
  public void requireValidationFor(ValidationTarget target) {
    if (target == null) {
      throw new IllegalArgumentException("Pitzer validation target must not be null");
    }
    if (!isValidatedFor(target)) {
      throw new IllegalStateException(formatDiagnostic() + ", requestedTarget=" + target);
    }
  }

  /**
   * Requires complete scientific qualification of the named dataset.
   *
   * <p>
   * This dataset-level gate is intentionally stricter than interaction coverage. It does not test whether the current
   * temperature, pressure, or composition is inside a subsystem-specific validation range; callers must also apply the
   * appropriate range helper for their exact use case.
   * </p>
   *
   * @throws IllegalStateException when the complete named dataset is not validated within its declared envelope
   */
  public void requireCompleteDatasetQualification() {
    if (!isValidatedWithinDeclaredEnvelope()) {
      throw new IllegalStateException(formatDiagnostic());
    }
  }

  private static List<String> immutableCopy(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  private static Set<ValidationTarget> immutableTargetCopy(List<ValidationTarget> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptySet();
    }
    EnumSet<ValidationTarget> copy = EnumSet.noneOf(ValidationTarget.class);
    copy.addAll(values);
    return Collections.unmodifiableSet(copy);
  }
}
