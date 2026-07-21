package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, explicit edition of a catalogued design standard.
 *
 * <p>
 * Unlike the legacy global version override, an edition travels with a selection and can therefore
 * be reproduced independently of process-wide mutable state.
 * </p>
 */
public final class StandardEdition implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final StandardType standardType;
  private final String edition;
  private final List<String> amendments;

  private StandardEdition(StandardType standardType, String edition, List<String> amendments) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    this.standardType = standardType;
    this.edition = requireText(edition, "edition");

    List<String> amendmentCopy = new ArrayList<String>();
    if (amendments != null) {
      for (String amendment : amendments) {
        amendmentCopy.add(requireText(amendment, "amendment"));
      }
    }
    this.amendments = Collections.unmodifiableList(amendmentCopy);
  }

  /**
   * Use the catalogued default edition for a standard.
   *
   * @param standardType standard type
   * @return explicit standard edition
   */
  public static StandardEdition defaultEdition(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    return of(standardType, standardType.getDefaultVersion());
  }

  /**
   * Create an explicit standard edition without project amendments.
   *
   * @param standardType standard type
   * @param edition edition or revision identifier
   * @return explicit standard edition
   */
  public static StandardEdition of(StandardType standardType, String edition) {
    return new StandardEdition(standardType, edition, Collections.<String>emptyList());
  }

  /**
   * Create an explicit standard edition with project amendments.
   *
   * @param standardType standard type
   * @param edition edition or revision identifier
   * @param amendments project amendments, corrigenda, or supplements
   * @return explicit standard edition
   */
  public static StandardEdition of(StandardType standardType, String edition, List<String> amendments) {
    return new StandardEdition(standardType, edition, amendments);
  }

  /** @return standard type */
  public StandardType getStandardType() {
    return standardType;
  }

  /** @return edition or revision identifier */
  public String getEdition() {
    return edition;
  }

  /** @return immutable list of project amendments */
  public List<String> getAmendments() {
    return amendments;
  }

  /**
   * Get a traceable name suitable for the legacy {@link DesignStandard} object.
   *
   * @return standard code, edition, and amendments
   */
  public String getDisplayName() {
    StringBuilder name = new StringBuilder(standardType.getCode()).append(' ').append(edition);
    if (!amendments.isEmpty()) {
      name.append(" [amendments: ");
      for (int index = 0; index < amendments.size(); index++) {
        if (index > 0) {
          name.append("; ");
        }
        name.append(amendments.get(index));
      }
      name.append(']');
    }
    return name.toString();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StandardEdition)) {
      return false;
    }
    StandardEdition other = (StandardEdition) object;
    return standardType == other.standardType && edition.equals(other.edition)
        && amendments.equals(other.amendments);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(standardType, edition, amendments);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return getDisplayName();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(fieldName + " cannot be null or blank");
    }
    return value.trim();
  }
}
