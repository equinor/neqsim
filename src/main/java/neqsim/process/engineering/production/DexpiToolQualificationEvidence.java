package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Accountable import/export/semantic-difference evidence from one named DEXPI-capable tool. */
public final class DexpiToolQualificationEvidence implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String product;
  private final String version;
  private final String exchangeProfile;
  private final boolean importSucceeded;
  private final boolean exportSucceeded;
  private final int unresolvedSemanticDifferences;
  private final String evidenceReference;
  private final String accountableReviewer;

  public DexpiToolQualificationEvidence(String product, String version, String exchangeProfile, boolean importSucceeded,
      boolean exportSucceeded, int unresolvedSemanticDifferences, String evidenceReference,
      String accountableReviewer) {
    this.product = text(product, "product");
    this.version = text(version, "version");
    this.exchangeProfile = text(exchangeProfile, "exchangeProfile");
    if (unresolvedSemanticDifferences < 0) {
      throw new IllegalArgumentException("unresolvedSemanticDifferences must be non-negative");
    }
    this.importSucceeded = importSucceeded;
    this.exportSucceeded = exportSucceeded;
    this.unresolvedSemanticDifferences = unresolvedSemanticDifferences;
    this.evidenceReference = text(evidenceReference, "evidenceReference");
    this.accountableReviewer = text(accountableReviewer, "accountableReviewer");
  }

  public boolean isQualified() {
    return importSucceeded && exportSucceeded && unresolvedSemanticDifferences == 0;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("product", product);
    result.put("version", version);
    result.put("exchangeProfile", exchangeProfile);
    result.put("importSucceeded", Boolean.valueOf(importSucceeded));
    result.put("exportSucceeded", Boolean.valueOf(exportSucceeded));
    result.put("unresolvedSemanticDifferences", Integer.valueOf(unresolvedSemanticDifferences));
    result.put("evidenceReference", evidenceReference);
    result.put("accountableReviewer", accountableReviewer);
    result.put("qualified", Boolean.valueOf(isQualified()));
    return result;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
