package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/** Immutable publisher-provenance record for one catalogued design standard. */
public final class StandardCatalogEntry implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final StandardType standardType;
  private final StandardLifecycleStatus lifecycleStatus;
  private final StandardType supersededBy;
  private final String publisherSourceUrl;
  private final String verifiedOn;

  StandardCatalogEntry(StandardType standardType, StandardLifecycleStatus lifecycleStatus, StandardType supersededBy,
      String publisherSourceUrl, String verifiedOn) {
    if (standardType == null || lifecycleStatus == null) {
      throw new IllegalArgumentException("standardType and lifecycleStatus must not be null");
    }
    this.standardType = standardType;
    this.lifecycleStatus = lifecycleStatus;
    this.supersededBy = supersededBy;
    this.publisherSourceUrl = publisherSourceUrl == null ? "" : publisherSourceUrl.trim();
    this.verifiedOn = verifiedOn == null ? "" : verifiedOn.trim();
  }

  /** @return catalogued standard */
  public StandardType getStandardType() {
    return standardType;
  }

  /** @return lifecycle status established from the publisher source */
  public StandardLifecycleStatus getLifecycleStatus() {
    return lifecycleStatus;
  }

  /** @return replacement standard, or {@code null} when none is recorded */
  public StandardType getSupersededBy() {
    return supersededBy;
  }

  /** @return authoritative publisher catalogue or product-page URL, or an empty string */
  public String getPublisherSourceUrl() {
    return publisherSourceUrl;
  }

  /** @return ISO date on which the publisher source was checked, or an empty string */
  public String getVerifiedOn() {
    return verifiedOn;
  }

  /**
   * Check whether the requested edition equals the publisher-verified current default edition.
   *
   * @param edition explicit edition
   * @return {@code true} for the verified current default edition
   */
  public boolean isCurrentEdition(StandardEdition edition) {
    return lifecycleStatus == StandardLifecycleStatus.CURRENT && edition != null
        && edition.getStandardType() == standardType
        && standardType.getDefaultVersion().equalsIgnoreCase(edition.getEdition());
  }
}
