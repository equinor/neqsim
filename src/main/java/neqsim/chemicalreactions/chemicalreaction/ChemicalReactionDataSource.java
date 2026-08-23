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
  /** General NeqSim reaction data used by electrolyte EOS and electrolyte GE systems. */
  STANDARD("standard", "reactiondata"),

  /** Apparent-equilibrium-constant data used by the Kent-Eisenberg model. */
  KENT_EISENBERG("kent-eisenberg", "reactiondatakenteisenberg");

  private final String identifier;
  private final String databaseTableName;

  /**
   * Create a reaction-data source descriptor.
   *
   * @param identifier stable diagnostic identifier
   * @param databaseTableName internal NeqSim database table name
   */
  ChemicalReactionDataSource(String identifier, String databaseTableName) {
    this.identifier = identifier;
    this.databaseTableName = databaseTableName;
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
}
