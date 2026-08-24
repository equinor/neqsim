package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable diagnostic describing whether a Pitzer parameter dataset covers the active ionic topology of a phase.
 *
 * <p>
 * The lists distinguish absent parameters from parameters that are explicitly defined as zero. This is important for
 * mixed electrolytes because an absent same-sign or ternary interaction is not scientifically equivalent to a fitted
 * zero.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class PitzerParameterCoverage implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String datasetId;
  private final List<String> activeCations;
  private final List<String> activeAnions;
  private final List<String> missingBinaryPairs;
  private final List<String> missingThetaPairs;
  private final List<String> missingPsiTuples;

  /**
   * Creates an immutable Pitzer parameter coverage result.
   *
   * @param datasetId stable identity of the parameter dataset
   * @param activeCations active positively charged species
   * @param activeAnions active negatively charged species
   * @param missingBinaryPairs missing cation-anion parameter pairs
   * @param missingThetaPairs missing same-sign theta parameter pairs
   * @param missingPsiTuples missing same-sign/opposite-sign psi parameter tuples
   */
  public PitzerParameterCoverage(String datasetId, List<String> activeCations, List<String> activeAnions,
      List<String> missingBinaryPairs, List<String> missingThetaPairs, List<String> missingPsiTuples) {
    this.datasetId = datasetId;
    this.activeCations = immutableSortedCopy(activeCations);
    this.activeAnions = immutableSortedCopy(activeAnions);
    this.missingBinaryPairs = immutableSortedCopy(missingBinaryPairs);
    this.missingThetaPairs = immutableSortedCopy(missingThetaPairs);
    this.missingPsiTuples = immutableSortedCopy(missingPsiTuples);
  }

  /**
   * Gets the stable identity of the audited parameter dataset.
   *
   * @return parameter dataset identity
   */
  public String getDatasetId() {
    return datasetId;
  }

  /**
   * Gets the active cations in deterministic order.
   *
   * @return unmodifiable active-cation list
   */
  public List<String> getActiveCations() {
    return activeCations;
  }

  /**
   * Gets the active anions in deterministic order.
   *
   * @return unmodifiable active-anion list
   */
  public List<String> getActiveAnions() {
    return activeAnions;
  }

  /**
   * Gets missing cation-anion binary parameter pairs.
   *
   * @return unmodifiable missing-pair list
   */
  public List<String> getMissingBinaryPairs() {
    return missingBinaryPairs;
  }

  /**
   * Gets missing same-sign theta parameter pairs.
   *
   * @return unmodifiable missing-theta list
   */
  public List<String> getMissingThetaPairs() {
    return missingThetaPairs;
  }

  /**
   * Gets missing ternary psi parameter tuples.
   *
   * @return unmodifiable missing-psi list
   */
  public List<String> getMissingPsiTuples() {
    return missingPsiTuples;
  }

  /**
   * Reports whether every interaction required by the active ionic topology is defined.
   *
   * @return {@code true} when no required interaction is missing
   */
  public boolean isComplete() {
    return missingBinaryPairs.isEmpty() && missingThetaPairs.isEmpty() && missingPsiTuples.isEmpty();
  }

  /**
   * Formats a deterministic diagnostic suitable for exceptions and logs.
   *
   * @return coverage diagnostic
   */
  public String formatDiagnostic() {
    return "Pitzer parameter coverage incomplete for dataset '" + datasetId + "': activeCations=" + activeCations
        + ", activeAnions=" + activeAnions + ", missingBinary=" + missingBinaryPairs + ", missingTheta="
        + missingThetaPairs + ", missingPsi=" + missingPsiTuples;
  }

  /**
   * Copies and sorts a list before exposing it as immutable state.
   *
   * @param values values to copy
   * @return immutable sorted copy
   */
  private static List<String> immutableSortedCopy(List<String> values) {
    List<String> copy = new ArrayList<String>(values);
    Collections.sort(copy);
    return Collections.unmodifiableList(copy);
  }
}
