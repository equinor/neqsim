package neqsim.chemicalreactions.chemicalreaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * Audits the active chemical-reaction set and parameter provenance of a thermodynamic system.
 *
 * <p>
 * Electrolyte EOS and electrolyte GE models may legitimately share a reaction table when their activity/standard-state
 * conventions are compatible, but sharing a table is not evidence that the same active reactions or parameters have
 * been independently validated for both model families. This utility provides an explicit, side-effect-free comparison
 * surface before changing reaction activation or splitting parameter tables.
 * </p>
 *
 * <p>
 * The audit only reads an already initialized {@link ChemicalReactionOperations} object. It does not initialize
 * reactions, run equilibrium calculations, change a thermodynamic model, or add work to ordinary neutral calculations.
 * </p>
 *
 * @author OpenAI Codex
 * @version 1.0
 */
public final class ChemicalReactionModelAudit {
  private ChemicalReactionModelAudit() {
  }

  /**
   * Capture the active reaction set and equilibrium-parameter provenance for a system.
   *
   * @param system system with initialized chemical reactions
   * @return immutable audit snapshot
   * @throws IllegalStateException if chemical reactions have not been initialized
   */
  public static AuditSnapshot inspect(SystemInterface system) {
    Objects.requireNonNull(system, "system");
    ChemicalReactionOperations operations = system.getChemicalReactionOperations();
    if (operations == null) {
      throw new IllegalStateException(
          "Chemical reactions are not initialized. Call chemicalReactionInit() before auditing reaction data.");
    }

    List<ReactionParameterSnapshot> reactions = new ArrayList<ReactionParameterSnapshot>();
    for (ChemicalReaction reaction : operations.getReactionList().getChemicalReactionList()) {
      reactions.add(new ReactionParameterSnapshot(reaction));
    }
    Collections.sort(reactions);
    return new AuditSnapshot(system.getModelName(), operations.getReactionDataSource(),
        system.getChemicalReactionConcentrationBasis(), reactions);
  }

  /**
   * Compare two initialized systems without running either model.
   *
   * @param first first system
   * @param second second system
   * @return immutable comparison of selected source, active reactions, and reaction parameters
   */
  public static AuditComparison compare(SystemInterface first, SystemInterface second) {
    return compare(inspect(first), inspect(second));
  }

  /**
   * Compare two previously captured audit snapshots.
   *
   * @param first first audit snapshot
   * @param second second audit snapshot
   * @return immutable comparison
   */
  public static AuditComparison compare(AuditSnapshot first, AuditSnapshot second) {
    Objects.requireNonNull(first, "first");
    Objects.requireNonNull(second, "second");

    Map<String, ReactionParameterSnapshot> firstByName = first.asMap();
    Map<String, ReactionParameterSnapshot> secondByName = second.asMap();
    List<String> onlyInFirst = new ArrayList<String>();
    List<String> onlyInSecond = new ArrayList<String>();
    List<String> parameterDifferences = new ArrayList<String>();

    for (Map.Entry<String, ReactionParameterSnapshot> entry : firstByName.entrySet()) {
      ReactionParameterSnapshot other = secondByName.get(entry.getKey());
      if (other == null) {
        onlyInFirst.add(entry.getKey());
      } else if (!entry.getValue().hasSameParameters(other)) {
        parameterDifferences.add(entry.getKey());
      }
    }
    for (String reactionName : secondByName.keySet()) {
      if (!firstByName.containsKey(reactionName)) {
        onlyInSecond.add(reactionName);
      }
    }

    return new AuditComparison(first.getReactionDataSource() == second.getReactionDataSource(),
        first.getReactionConcentrationBasis() == second.getReactionConcentrationBasis(), onlyInFirst, onlyInSecond,
        parameterDifferences);
  }

  /** Immutable snapshot of one system's selected reaction source and active reactions. */
  public static final class AuditSnapshot {
    private final String modelName;
    private final ChemicalReactionDataSource reactionDataSource;
    private final ChemicalReactionConcentrationBasis reactionConcentrationBasis;
    private final List<ReactionParameterSnapshot> reactions;

    private AuditSnapshot(String modelName, ChemicalReactionDataSource reactionDataSource,
        ChemicalReactionConcentrationBasis reactionConcentrationBasis, List<ReactionParameterSnapshot> reactions) {
      this.modelName = modelName;
      this.reactionDataSource = reactionDataSource;
      this.reactionConcentrationBasis = reactionConcentrationBasis;
      this.reactions = Collections.unmodifiableList(new ArrayList<ReactionParameterSnapshot>(reactions));
    }

    /** @return thermodynamic model name recorded by the system */
    public String getModelName() {
      return modelName;
    }

    /** @return selected reaction-data source */
    public ChemicalReactionDataSource getReactionDataSource() {
      return reactionDataSource;
    }

    /** @return concentration basis used to evaluate reaction quotients */
    public ChemicalReactionConcentrationBasis getReactionConcentrationBasis() {
      return reactionConcentrationBasis;
    }

    /** @return immutable active-reaction snapshots in deterministic name order */
    public List<ReactionParameterSnapshot> getReactions() {
      return Collections.unmodifiableList(new ArrayList<ReactionParameterSnapshot>(reactions));
    }

    /** @return number of active independent reactions retained after initialization */
    public int getReactionCount() {
      return reactions.size();
    }

    /**
     * Get active reactions whose selected data row lacks declared model-specific validation.
     *
     * @return immutable reaction-name list in deterministic order
     */
    public List<String> getReactionsWithoutValidatedEvidence() {
      List<String> names = new ArrayList<String>();
      for (ReactionParameterSnapshot reaction : reactions) {
        if (reaction.getValidationStatus() != ChemicalReactionValidationStatus.VALIDATED) {
          names.add(reaction.getName());
        }
      }
      return Collections.unmodifiableList(names);
    }

    /**
     * Check whether every active reaction declares model-specific validation evidence.
     *
     * @return true when all active reactions are marked {@link ChemicalReactionValidationStatus#VALIDATED}
     */
    public boolean hasValidatedEvidenceForAllActiveReactions() {
      return getReactionsWithoutValidatedEvidence().isEmpty();
    }

    private Map<String, ReactionParameterSnapshot> asMap() {
      Map<String, ReactionParameterSnapshot> values = new LinkedHashMap<String, ReactionParameterSnapshot>();
      for (ReactionParameterSnapshot reaction : reactions) {
        values.put(reaction.getName(), reaction);
      }
      return values;
    }
  }

  /** Immutable per-reaction parameter/provenance snapshot. */
  public static final class ReactionParameterSnapshot implements Comparable<ReactionParameterSnapshot> {
    private final String name;
    private final String reference;
    private final ChemicalReactionValidationStatus validationStatus;
    private final double referenceTemperature;
    private final double[] equilibriumConstantCoefficients;
    private final String[] componentNames;
    private final double[] stoichiometricCoefficients;

    private ReactionParameterSnapshot(ChemicalReaction reaction) {
      this.name = reaction.getName();
      this.reference = reaction.getReference();
      this.validationStatus = reaction.getValidationStatus();
      this.referenceTemperature = reaction.getReferenceTemperature();
      this.equilibriumConstantCoefficients = reaction.getEquilibriumConstantCoefficients();
      this.componentNames = reaction.getNames().clone();
      this.stoichiometricCoefficients = reaction.getStocCoefs().clone();
    }

    /** @return reaction name */
    public String getName() {
      return name;
    }

    /** @return stored literature/data reference identifier */
    public String getReference() {
      return reference;
    }

    /**
     * Get the validation status declared by the selected model-specific reaction-data row.
     *
     * @return declared validation status
     */
    public ChemicalReactionValidationStatus getValidationStatus() {
      return validationStatus;
    }

    /** @return reference temperature in kelvin */
    public double getReferenceTemperature() {
      return referenceTemperature;
    }

    /** @return defensive copy of equilibrium-constant correlation coefficients */
    public double[] getEquilibriumConstantCoefficients() {
      return equilibriumConstantCoefficients.clone();
    }

    /** @return defensive copy of reaction component names */
    public String[] getComponentNames() {
      return componentNames.clone();
    }

    /** @return defensive copy of stoichiometric coefficients */
    public double[] getStoichiometricCoefficients() {
      return stoichiometricCoefficients.clone();
    }

    /**
     * Check whether another reaction snapshot uses the same stoichiometry and stored parameterization.
     *
     * @param other other snapshot
     * @return true when all auditable reaction parameters are equal
     */
    public boolean hasSameParameters(ReactionParameterSnapshot other) {
      return other != null && validationStatus == other.validationStatus && Objects.equals(reference, other.reference)
          && Double.doubleToLongBits(referenceTemperature) == Double.doubleToLongBits(other.referenceTemperature)
          && Arrays.equals(equilibriumConstantCoefficients, other.equilibriumConstantCoefficients)
          && Arrays.equals(componentNames, other.componentNames)
          && Arrays.equals(stoichiometricCoefficients, other.stoichiometricCoefficients);
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(ReactionParameterSnapshot other) {
      return name.compareTo(other.name);
    }
  }

  /** Immutable difference summary between two reaction-model audit snapshots. */
  public static final class AuditComparison {
    private final boolean sameReactionDataSource;
    private final boolean sameReactionConcentrationBasis;
    private final List<String> reactionsOnlyInFirst;
    private final List<String> reactionsOnlyInSecond;
    private final List<String> parameterDifferences;

    private AuditComparison(boolean sameReactionDataSource, boolean sameReactionConcentrationBasis,
        List<String> reactionsOnlyInFirst, List<String> reactionsOnlyInSecond, List<String> parameterDifferences) {
      this.sameReactionDataSource = sameReactionDataSource;
      this.sameReactionConcentrationBasis = sameReactionConcentrationBasis;
      this.reactionsOnlyInFirst = immutableCopy(reactionsOnlyInFirst);
      this.reactionsOnlyInSecond = immutableCopy(reactionsOnlyInSecond);
      this.parameterDifferences = immutableCopy(parameterDifferences);
    }

    /** @return true when both systems selected the same typed reaction-data source */
    public boolean hasSameReactionDataSource() {
      return sameReactionDataSource;
    }

    /** @return true when both systems use the same reaction-quotient concentration basis */
    public boolean hasSameReactionConcentrationBasis() {
      return sameReactionConcentrationBasis;
    }

    /** @return immutable reaction names active only in the first system */
    public List<String> getReactionsOnlyInFirst() {
      return reactionsOnlyInFirst;
    }

    /** @return immutable reaction names active only in the second system */
    public List<String> getReactionsOnlyInSecond() {
      return reactionsOnlyInSecond;
    }

    /** @return immutable common reaction names whose stoichiometry or parameters differ */
    public List<String> getParameterDifferences() {
      return parameterDifferences;
    }

    /**
     * @return true when data source, active reaction names, stoichiometry, and auditable parameters are identical
     */
    public boolean isEquivalent() {
      return sameReactionDataSource && sameReactionConcentrationBasis && reactionsOnlyInFirst.isEmpty()
          && reactionsOnlyInSecond.isEmpty() && parameterDifferences.isEmpty();
    }

    private static List<String> immutableCopy(List<String> values) {
      return Collections.unmodifiableList(new ArrayList<String>(values));
    }
  }
}
