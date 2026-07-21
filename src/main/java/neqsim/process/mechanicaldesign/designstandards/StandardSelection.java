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

  private final StandardEdition edition;
  private final Mode mode;

  private StandardSelection(StandardEdition edition, Mode mode) {
    if (edition == null) {
      throw new IllegalArgumentException("edition cannot be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode cannot be null");
    }
    this.edition = edition;
    this.mode = mode;
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
    return new StandardSelection(edition, Mode.STRICT);
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
    return new StandardSelection(edition, Mode.LEGACY_COMPATIBLE);
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
    return edition.equals(other.edition) && mode == other.mode;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(edition, mode);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return mode + ": " + edition;
  }
}
