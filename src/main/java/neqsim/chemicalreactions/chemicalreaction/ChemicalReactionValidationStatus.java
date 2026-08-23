package neqsim.chemicalreactions.chemicalreaction;

/**
 * Records the model-specific validation state declared by a reaction-data row.
 *
 * <p>
 * The status qualifies the stored equilibrium-constant correlation for its selected reaction-data source, concentration
 * basis and documented validity range. It does not by itself validate a complete speciation, flash or process
 * calculation.
 * </p>
 *
 * @author OpenAI Codex
 * @version 1.0
 */
public enum ChemicalReactionValidationStatus {
  /** Independently checked against the public evidence declared for this data source. */
  VALIDATED,

  /** Retained for compatibility without model-specific validation evidence. */
  UNVALIDATED,

  /** The legacy data source does not declare a validation status. */
  UNSPECIFIED;

  /**
   * Parse the optional database value.
   *
   * @param value database value, possibly {@code null} or empty for legacy tables
   * @return parsed status, or {@link #UNSPECIFIED} when no value is declared
   * @throws IllegalArgumentException when a non-empty value is not a supported status
   */
  public static ChemicalReactionValidationStatus fromDatabaseValue(String value) {
    if (value == null || value.trim().isEmpty()) {
      return UNSPECIFIED;
    }
    return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
  }
}
