package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable diagnostic for sparse neutral-solute Pitzer parameter coverage. */
public final class PitzerNeutralParameterCoverage implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String datasetId;
  private final List<String> activeNeutralSolutes;
  private final List<String> missingLambdaPairs;
  private final List<String> missingZetaTuples;
  private final List<String> missingMuTuples;
  private final List<String> missingEtaTuples;

  /**
   * Creates an immutable coverage diagnostic.
   *
   * @param datasetId parameter-dataset identity
   * @param activeNeutralSolutes active non-water neutral solutes
   * @param missingLambdaPairs missing neutral-neutral or neutral-ion pairs
   * @param missingZetaTuples missing neutral-cation-anion tuples
   * @param missingMuTuples missing neutral triples when the mu family is enabled
   * @param missingEtaTuples missing neutral/same-sign-ion tuples when eta is enabled
   */
  public PitzerNeutralParameterCoverage(String datasetId, List<String> activeNeutralSolutes,
      List<String> missingLambdaPairs, List<String> missingZetaTuples, List<String> missingMuTuples,
      List<String> missingEtaTuples) {
    this.datasetId = datasetId;
    this.activeNeutralSolutes = immutableSortedCopy(activeNeutralSolutes);
    this.missingLambdaPairs = immutableSortedCopy(missingLambdaPairs);
    this.missingZetaTuples = immutableSortedCopy(missingZetaTuples);
    this.missingMuTuples = immutableSortedCopy(missingMuTuples);
    this.missingEtaTuples = immutableSortedCopy(missingEtaTuples);
  }

  /** @return parameter-dataset identity */
  public String getDatasetId() {
    return datasetId;
  }

  /** @return active non-water neutral solutes */
  public List<String> getActiveNeutralSolutes() {
    return activeNeutralSolutes;
  }

  /** @return missing neutral-neutral or neutral-ion lambda pairs */
  public List<String> getMissingLambdaPairs() {
    return missingLambdaPairs;
  }

  /** @return missing neutral-cation-anion zeta tuples */
  public List<String> getMissingZetaTuples() {
    return missingZetaTuples;
  }

  /** @return missing neutral mu tuples */
  public List<String> getMissingMuTuples() {
    return missingMuTuples;
  }

  /** @return missing neutral/same-sign-ion eta tuples */
  public List<String> getMissingEtaTuples() {
    return missingEtaTuples;
  }

  /** @return {@code true} when every required tuple is explicitly defined */
  public boolean isComplete() {
    return missingLambdaPairs.isEmpty() && missingZetaTuples.isEmpty() && missingMuTuples.isEmpty()
        && missingEtaTuples.isEmpty();
  }

  /** @return deterministic diagnostic suitable for an exception */
  public String formatDiagnostic() {
    return "Neutral Pitzer parameter coverage incomplete for dataset '" + datasetId + "': activeNeutralSolutes="
        + activeNeutralSolutes + ", missingLambda=" + missingLambdaPairs + ", missingZeta=" + missingZetaTuples
        + ", missingMu=" + missingMuTuples + ", missingEta=" + missingEtaTuples;
  }

  private static List<String> immutableSortedCopy(List<String> values) {
    List<String> copy = new ArrayList<String>(values);
    Collections.sort(copy);
    return Collections.unmodifiableList(copy);
  }
}
