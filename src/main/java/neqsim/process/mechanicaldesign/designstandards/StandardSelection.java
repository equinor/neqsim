package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.Objects;

/** Immutable request to select a specific design-standard edition. */
public final class StandardSelection implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Selection behavior. */
  public enum Mode {
    /** Preserve the permissive behavior of the legacy registry. */
    LEGACY_COMPATIBLE,
    /** Reject unsupported, disconnected, or inapplicable selections. */
    STRICT
  }

  /** Minimum executable implementation required by a checked selection. */
  public enum ExecutionRequirement {
    /** Require only the legacy category registry adapter. */
    REGISTRY_FACTORY,
    /** Require a cross-equipment calculation and review capability pack. */
    REQUIREMENT_PACK,
    /** Require an edition-specific typed engineering kernel. */
    COMMON_KERNEL
  }

  private final StandardEdition edition;
  private final Mode mode;
  private final ExecutionRequirement executionRequirement;
  private final boolean historicalEditionAllowed;

  private StandardSelection(StandardEdition edition, Mode mode, ExecutionRequirement executionRequirement,
      boolean historicalEditionAllowed) {
    if (edition == null) {
      throw new IllegalArgumentException("edition cannot be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode cannot be null");
    }
    if (executionRequirement == null) {
      throw new IllegalArgumentException("executionRequirement cannot be null");
    }
    this.edition = edition;
    this.mode = mode;
    this.executionRequirement = executionRequirement;
    this.historicalEditionAllowed = historicalEditionAllowed;
  }

  /**
   * Select the catalogued default edition with strict checks.
   *
   * @param standardType standard type
   * @return strict selection
   */
  public static StandardSelection strict(StandardType standardType) {
    return strict(StandardEdition.defaultEdition(standardType));
  }

  /**
   * Select an explicit edition with strict checks.
   *
   * @param edition explicit edition
   * @return strict selection
   */
  public static StandardSelection strict(StandardEdition edition) {
    return new StandardSelection(edition, Mode.STRICT, ExecutionRequirement.COMMON_KERNEL, false);
  }

  /**
   * Select an explicit historical edition while retaining applicability and executable-kernel checks.
   *
   * @param edition explicit historical edition
   * @return checked historical selection
   */
  public static StandardSelection historical(StandardEdition edition) {
    return new StandardSelection(edition, Mode.STRICT, ExecutionRequirement.COMMON_KERNEL, true);
  }

  /**
   * Select a current edition using the transitional legacy registry implementation.
   *
   * <p>
   * This mode is intended for migration only. It does not assert that a typed, edition-specific calculation exists.
   * </p>
   *
   * @param standardType standard type
   * @return checked registry-factory selection
   */
  public static StandardSelection strictRegistry(StandardType standardType) {
    return new StandardSelection(StandardEdition.defaultEdition(standardType), Mode.STRICT,
        ExecutionRequirement.REGISTRY_FACTORY, false);
  }

  /**
   * Select a current cross-equipment standard requirement pack.
   *
   * @param standardType standard type
   * @return checked requirement-pack selection
   */
  public static StandardSelection strictRequirements(StandardType standardType) {
    return new StandardSelection(StandardEdition.defaultEdition(standardType), Mode.STRICT,
        ExecutionRequirement.REQUIREMENT_PACK, false);
  }

  /**
   * Select the catalogued default edition with legacy-compatible behavior.
   *
   * @param standardType standard type
   * @return legacy-compatible selection
   */
  public static StandardSelection legacy(StandardType standardType) {
    return legacy(StandardEdition.defaultEdition(standardType));
  }

  /**
   * Select an explicit edition with legacy-compatible behavior.
   *
   * @param edition explicit edition
   * @return legacy-compatible selection
   */
  public static StandardSelection legacy(StandardEdition edition) {
    return new StandardSelection(edition, Mode.LEGACY_COMPATIBLE, ExecutionRequirement.REGISTRY_FACTORY, true);
  }

  /** @return explicit edition */
  public StandardEdition getEdition() {
    return edition;
  }

  /** @return selected standard type */
  public StandardType getStandardType() {
    return edition.getStandardType();
  }

  /** @return selection behavior */
  public Mode getMode() {
    return mode;
  }

  /** @return minimum executable implementation required by this selection */
  public ExecutionRequirement getExecutionRequirement() {
    return executionRequirement;
  }

  /** @return whether a non-current explicit edition may be selected */
  public boolean isHistoricalEditionAllowed() {
    return historicalEditionAllowed;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StandardSelection)) {
      return false;
    }
    StandardSelection other = (StandardSelection) object;
    return edition.equals(other.edition) && mode == other.mode && executionRequirement == other.executionRequirement
        && historicalEditionAllowed == other.historicalEditionAllowed;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(edition, mode, executionRequirement, Boolean.valueOf(historicalEditionAllowed));
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return mode + "/" + executionRequirement + (historicalEditionAllowed ? "/HISTORICAL: " : ": ") + edition;
  }
}
