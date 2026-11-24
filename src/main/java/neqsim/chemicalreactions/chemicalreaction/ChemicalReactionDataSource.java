package neqsim.chemicalreactions.chemicalreaction;

/**
 * Identifies the database source used to load a thermodynamic system's chemical reactions.
 *
 * <p>
 * A source identifies a reaction set and its equilibrium-constant parameters. It does not imply that the parameters are
 * valid for another thermodynamic model or standard-state convention.
 * </p>
 *
 * @author OpenAI Codex
 * @version 1.0
 */
public enum ChemicalReactionDataSource {
  /** General NeqSim reaction data used by electrolyte EOS systems. */
  STANDARD("standard", "reactiondata", false),

  /** Molality-standard-state reaction data used by the Pitzer electrolyte GE model. */
  PITZER("pitzer", "reactiondatapitzer", true),

  /** Water and carbonate subset of the PHREEQC-derived molality data for aqueous electrolyte CPA. */
  MOLAL_CARBONATE("molal-carbonate", "reactiondatapitzer", true),

  /** Apparent-equilibrium-constant data used by the Kent-Eisenberg model. */
  KENT_EISENBERG("kent-eisenberg", "reactiondatakenteisenberg", false);

  private final String identifier;
  private final String databaseTableName;
  private final boolean requireValidatedActiveReactions;

  /**
   * Create a reaction-data source descriptor.
   *
   * @param identifier stable diagnostic identifier
   * @param databaseTableName internal NeqSim database table name
   * @param requireValidatedActiveReactions whether initialization must reject relevant unvalidated rows
   */
  ChemicalReactionDataSource(String identifier, String databaseTableName, boolean requireValidatedActiveReactions) {
    this.identifier = identifier;
    this.databaseTableName = databaseTableName;
    this.requireValidatedActiveReactions = requireValidatedActiveReactions;
  }

  /**
   * Get the stable, implementation-independent source identifier.
   *
   * @return source identifier suitable for diagnostics and serialized output
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Get the internal NeqSim database table containing this reaction set.
   *
   * @return database table name
   */
  public String getDatabaseTableName() {
    return databaseTableName;
  }

  /**
   * Identify the reactions in the aqueous carbonate subset.
   *
   * @param name database reaction name
   * @return true for water autoprotolysis or either carbonate acid dissociation
   */
  public static boolean isCarbonateReaction(String name) {
    return "waterreac".equals(name) || "CO2water".equals(name) || "carbonate".equals(name);
  }

  /**
   * Check whether this source must fail closed when a relevant active row lacks validated evidence.
   *
   * @return true when reaction initialization requires every relevant active row to be validated
   */
  public boolean requiresValidatedActiveReactions() {
    return requireValidatedActiveReactions;
  }
}
